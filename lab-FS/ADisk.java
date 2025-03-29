/*
 * ADisk.java
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007, 2010 Mike Dahlin
 *
 */

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Vector;
import java.util.concurrent.locks.Condition;

public class ADisk {

  // -------------------------------------------------------
  // The size of the redo log in sectors
  // -------------------------------------------------------
  public static final int REDO_LOG_SECTORS = 1024;

  // 一些磁盘分区的定义，包括super block、log region的范围定义

  // -------------------------------------------------------
  // member variables
  // -------------------------------------------------------
  private ActiveTransactionList activeTransactionList;
  private WriteBackList writeBackList;
  private LogStatus logStatus;
  private CallbackTracker callbackTracker;
  private Disk disk;
  // internal class都做了并发控制，貌似不需要控制并发了
  // private SimpleLock lock;
  // private Condition writeBackCond;

  // -------------------------------------------------------
  //
  // Allocate an ADisk that stores its data using
  // a Disk.
  //
  // If format is true, wipe the current disk
  // and initialize data structures for an empty
  // disk.
  //
  // Otherwise, initialize internal state, read the log,
  // and redo any committed transactions.
  //
  // -------------------------------------------------------
  public ADisk(boolean format) {
    try {
      activeTransactionList = new ActiveTransactionList();
      writeBackList = new WriteBackList();
      logStatus = new LogStatus();
      callbackTracker = new CallbackTracker();
      disk = new Disk(callbackTracker);
      lock = new SimpleLock();
      writeBackCond = lock.newCondition();
    } catch (FileNotFoundException e) {
      e.printStackTrace();
    }
  }

  // -------------------------------------------------------
  //
  // Return the total number of data sectors that
  // can be used *not including space reseved for
  // the log or other data sructures*. This
  // number will be smaller than Disk.NUM_OF_SECTORS.
  //
  // -------------------------------------------------------
  public int getNSectors() {
    return -1; // Fixme
  }

  // -------------------------------------------------------
  //
  // Begin a new transaction and return a transaction ID
  //
  // -------------------------------------------------------
  public TransID beginTransaction() {
      Transaction transaction = new Transaction();
      activeTransactionList.put(transaction);
      return transaction.getTransID(); // Fixme
  }

  // -------------------------------------------------------
  //
  // First issue writes to put all of the transaction's
  // writes in the log.
  //
  // Then issue a barrier to the Disk's write queue.
  //
  // Then, mark the log to indicate that the specified
  // transaction has been committed.
  //
  // Then wait until the "commit" is safely on disk
  // (in the log).
  //
  // Then take some action to make sure that eventually
  // the updates in the log make it to their final
  // location on disk. Do not wait for these writes
  // to occur. These writes should be asynchronous.
  //
  // Note: You must ensure that (a) all writes in
  // the transaction are in the log *before* the
  // commit record is in the log and (b) the commit
  // record is in the log before this method returns.
  //
  // Throws
  // IOException if the disk fails to complete
  // the commit or the log is full.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  //
  // -------------------------------------------------------
  public void commitTransaction(TransID tid)
      throws IOException, IllegalArgumentException {
    int offset = 0;
    int tag = 0;
    int logStart = 0, logSectors = 0;

    try {
      // lock.lock();

      Transaction transaction = activeTransactionList.remove(tid);

      if (transaction == null) {
        throw new IllegalArgumentException("Bad transaction id");
      }
      transaction.commit();
      // 为事务在log status中申请空间
      logSectors = 2 + transaction.getNUpdatedSectors();
      logStart = logStatus.reserveLogSectors(logSectors);
      transaction.rememberLogSectors(logStart, logSectors);

      // 获取事务的log sector list
      byte[] transLog = transaction.getSectorsForLog();

      // 将header + [updated sector]写入磁盘log并等待返回
      Vector<Integer> tags = new Vector<>();

      byte[] headerBuffer = new byte[Disk.SECTOR_SIZE];
      System.arraycopy(transLog, offset, headerBuffer, 0, Disk.SECTOR_SIZE);
      tag = (transaction.getTransID().toInt() & 0xFFFF0000) | 0;
      disk.startRequest(Disk.WRITE, tag, logStart, headerBuffer);
      tags.add(tag);

      // tag的构造方法直接采用transID + sec num，也能确保全局唯一和可读，header默认为0
      for (int i = 0; i < logSectors - 2; ++i) {
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        transaction.getUpdateI(i, buffer);
        tag = (transaction.getTransID().toInt() & 0xFFFF0000) | transaction.getUpdateISecNum(i);
        tags.add(tag);
        disk.startRequest(Disk.WRITE, tag, logStart + i + 1, buffer);
      }

      // 添加barrier, tag不重要
      disk.startRequest(Disk.BARRIER, 0, 0, null);

      // 最后写入commit sector, commit tag设为0xFF
      tag = (transaction.getTransID().toInt() & 0xFFFF0000) | 0xFF;
      tags.add(tag);
      disk.startRequest(Disk.WRITE, 0, logStart + logSectors - 1, null);

      // 这里持有锁等待，所有事务提交全变成顺序了
      callbackTracker.dontWaitForTags(tags);

      // transact移入写回队列
      writeBackList.addCommitted(transaction);
      // 在运行过程中不更新磁盘中的log status的head位置
      // tail由writeback线程负责更新，并作为start point
    } catch (Exception e) {
      e.printStackTrace();
    // } finally {
      // lock.unlock();
    }
  }

  // -------------------------------------------------------
  //
  // Free up the resources for this transaction without
  // committing any of the writes.
  //
  // Throws
  // IllegalArgumentException if tid does not refer
  // to an active transaction.
  //
  // -------------------------------------------------------
  public void abortTransaction(TransID tid)
      throws IllegalArgumentException {
    try {
      // lock.lock();

      Transaction transaction = activeTransactionList.remove(tid);
      if (transaction == null) {
        throw new IllegalArgumentException("Bad transaction id");
      }
      transaction.abort();
    } catch (Exception e) {
      e.printStackTrace();
    // } finally {
      // lock.unlock();
    }

  }

  // -------------------------------------------------------
  //
  // Read the disk sector numbered sectorNum and place
  // the result in buffer. Note: the result of a read of a
  // sector must reflect the results of all previously
  // committed writes as well as any uncommitted writes
  // from the transaction tid. The read must not
  // reflect any writes from other active transactions
  // or writes from aborted transactions.
  //
  // Throws
  // IOException if the disk fails to complete
  // the read.
  //
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  //
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  // -------------------------------------------------------
  public void readSector(TransID tid, int sectorNum, byte buffer[])
      throws IOException, IllegalArgumentException,
      IndexOutOfBoundsException {
    // try {
      // lock.lock();

      if (sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS) {
        throw new IndexOutOfBoundsException("Bad sec num");
      }
      if (buffer == null || buffer.length != Disk.SECTOR_SIZE) {
        throw new IllegalArgumentException("Bad buffer");
      }
      Transaction transaction = activeTransactionList.get(tid);
      if (transaction == null) {
        throw new IllegalArgumentException("Bad transaction id");
      }
      // 首先查事务是否更新过该sector
      if (transaction.checkRead(sectorNum, buffer)) {
        return;
      }
      // 查写回队列
      writeBackList.checkRead(sectorNum, buffer);
    // } finally {
      // lock.unlock();
    // }
  }

  // -------------------------------------------------------
  //
  // Buffer the specified update as part of the in-memory
  // state of the specified transaction. Don't write
  // anything to disk yet.
  //
  // Concurrency: The final value of a sector
  // must be the value written by the transaction that
  // commits the latest.
  //
  // Throws
  // IllegalArgumentException if tid does not refer
  // to an active transaction or buffer is too small
  // to hold a sector.
  //
  // IndexOutOfBoundsException if sectorNum is not
  // a valid sector number
  //
  // -------------------------------------------------------
  public void writeSector(TransID tid, int sectorNum, byte buffer[])
      throws IllegalArgumentException,
      IndexOutOfBoundsException {
    // try {
      // lock.lock();

      if (sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS) {
        throw new IndexOutOfBoundsException("Bad sec num");
      }
      if (buffer == null || buffer.length != Disk.SECTOR_SIZE) {
        throw new IllegalArgumentException("Bad buffer");
      }
      Transaction transaction = activeTransactionList.get(tid);
      if (transaction == null) {
        throw new IllegalArgumentException("Bad trans id");
      }

      transaction.addWrite(sectorNum, buffer);
    // } finally {
      // lock.unlock();
    // }
  }

}
