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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Vector;

public class ADisk {

  // -------------------------------------------------------
  // The size of the redo log in sectors
  // -------------------------------------------------------
  public static final int REDO_LOG_SECTORS = 1024;

  // 一些磁盘分区的定义，包括super block、log region的范围定义
  // 磁盘大小为8MB = 2^23，扇区大小 = 512B = 2^9 总共16K个扇区
  // 其中1024=1K个日志块
  // 由于元数据等都由ADisk自定义，超级块用logStatus替代
  // 暂时设计布局为
  // 1个超级扇区 + 1K个日志扇区 + 剩余的数据扇区
  private static final int LOG_STATUS_SECTOR_NUMBER = 0;
  private static final int LOG_REGION = 1;

  // -------------------------------------------------------
  // member variables
  // -------------------------------------------------------
  private ActiveTransactionList activeTransactionList;
  private WriteBackList writeBackList;
  private LogStatus logStatus;
  private CallbackTracker callbackTracker;
  private Disk disk;

  private Vector<Integer> committedOrder;
  // internal class都做了并发控制，貌似不需要控制并发了
  // 但是测试时希望能获取到事务提交的顺序
  private SimpleLock lock;
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
      committedOrder = new Vector<>();

      // 格式化磁盘或者日志恢复
      if (format) {
        formatDisk();
      } else {
        recovery();
      }
      // 启动一个线程完成writeback工作
      Thread writeBackThread = new Thread(() -> {
        writeBack(writeBackList, disk, callbackTracker);
      });
      writeBackThread.start();
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
    Transaction transaction = null;
    int tag = 0;
    int logStart = 0, logSectors = 0;

    try {
      // lock.lock();

      transaction = activeTransactionList.remove(tid);

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
      System.arraycopy(transLog, 0, headerBuffer, 0, Disk.SECTOR_SIZE);
      tag = genTag(transaction.getTransID(), Disk.WRITE, logStart);
      tags.add(tag);
      disk.startRequest(Disk.WRITE, tag, logIndex2secNum(logStart, 0), headerBuffer);

      // tag的构造方法直接采用transID + sec num，也能确保全局唯一和可读，header默认为0
      for (int i = 0; i < logSectors - 2; ++i) {
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        transaction.getUpdateI(i, buffer);
        tag = genTag(transaction.getTransID(), Disk.WRITE, logStart + i + 1);
        tags.add(tag);
        disk.startRequest(Disk.WRITE, tag, logIndex2secNum(logStart, i + 1), buffer);
      }

      // 添加barrier
      disk.addBarrier();

      // 最后写入commit sector
      byte[] commitBuffer = new byte[Disk.SECTOR_SIZE];
      System.arraycopy(transLog, (logSectors - 1) * Disk.SECTOR_SIZE, commitBuffer, 0, Disk.SECTOR_SIZE);
      tag = genTag(transaction.getTransID(), Disk.WRITE, logStart + logSectors - 1);
      tags.add(tag);
      disk.startRequest(Disk.WRITE, tag, logIndex2secNum(logStart, logSectors - 1), commitBuffer);

      // 这里持有锁等待，所有事务提交全变成顺序了
      callbackTracker.dontWaitForTags(tags);

      // committedOrder.add(transaction.getTransID().toInt());

      // // transact移入写回队列
      // writeBackList.addCommitted(transaction);
      // 在运行过程中不更新磁盘中的log status的head位置
      // tail由writeback线程负责更新，并作为start point
    } catch (Exception e) {
      e.printStackTrace();
      // } finally {
      // lock.unlock();
    }

    try {
      lock.lock();
      committedOrder.add(transaction.getTransID().toInt());

      // transact移入写回队列
      writeBackList.addCommitted(transaction);
    } finally {
      lock.unlock();
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
      Common.debugPrintln("trans id: ", transaction.getTransID().toInt(), "; sector num: ", sectorNum,
          "; read from own updated sectors");
      return;
    }
    // 查写回队列
    if (writeBackList.checkRead(sectorNum, buffer)) {
      Common.debugPrintln("trans id: ", transaction.getTransID().toInt(), "; sector num: ", sectorNum,
          "; read from write back list");
      return;
    }

    // 向disk发起请求并等待
    int tag = genTag(tid, Disk.READ, sectorNum);
    disk.startRequest(Disk.READ, tag, sectorNum, buffer);

    callbackTracker.waitForTag(tag);
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

    Common.debugPrintln("trans id: ", transaction.getTransID().toInt(), "; sector num: ", sectorNum);
    transaction.addWrite(sectorNum, buffer);
    // } finally {
    // lock.unlock();
    // }
  }

  public Vector<Integer> committedOrder() {
    return committedOrder;
  }

  public static void writeBack(WriteBackList writeBackList, Disk disk, CallbackTracker callbackTracker) {
    Transaction transaction = null;
    int logSectors = 0;
    int sectorNum = 0;
    int tag = 0;

    while (true) {
      continue;
      // transaction = writeBackList.getNextWriteback();
      // // 将该事务的更新块写入磁盘，不必等待
      // for (int i = 0; i < logSectors - 2; ++i) {
      // try {
      // byte[] buffer = new byte[Disk.SECTOR_SIZE];
      // sectorNum = transaction.getUpdateISecNum(i);
      // tag = genTag(transaction.getTransID(), Disk.WRITE, sectorNum);
      // callbackTracker.dontWaitForTag(tag);
      // disk.startRequest(Disk.WRITE, tag, sectorNum, buffer);
      // } catch (Exception e) {
      // e.printStackTrace();
      // }
      // }

      // // 更新磁盘上的logstatus，必须等待，这里的写入必须是顺序的

      // writeBackList.removeNextWriteback();
    }
  }

  // 希望构造具有一定意义的tag
  // 高16位为事务id，后8位为 R(0)/W(1)，后八位为扇区号
  private static int genTag(TransID transID, int op, int sectorNum) {
    return (transID.toInt() & 0xFFFF0000) | ((op & 0x1) << 8) | (sectorNum & 0xFF);
  }

  private void formatDisk() {

  }

  private void recovery() {
    byte[] buffer = new byte[Disk.SECTOR_SIZE];
    byte[] commitBuffer = new byte[Disk.SECTOR_SIZE];
    Vector<Integer> tags = new Vector<>();
    int tail = 0;
    int logLength = 0;
    int offset = 0;
    int nSectors = 0;

    try {
      // 读取第一个扇区初始化logStatus
      disk.startRequest(Disk.READ, 0, LOG_STATUS_SECTOR_NUMBER, buffer);
      callbackTracker.waitForTag(0);
      ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
      byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致
      tail = byteBuffer.getInt();
      logLength = byteBuffer.getInt();
      logStatus.recoverySectorsInUse(tail, logLength);

      // 临时用一个“假”事务，主要用于构造tag
      TransID transID = new TransID(0);
      int tag = 0;
      
      TransactionHeader[] headerList = new TransactionHeader[1];
      LinkedHashMap<Integer, byte[]> sectorWriteRecords = null;

      while (true) {
        offset = logStatus.logStartPoint();
        // 读取下一个事务的首个扇区并判断是否合法
        tag = genTag(transID, Disk.READ, logIndex2secNum(0, offset));
        disk.startRequest(Disk.READ, tag, logIndex2secNum(0, offset), buffer);
        callbackTracker.waitForTag(tag);

        nSectors = Transaction.parseHeader(buffer, headerList);
        if (nSectors == -1 || headerList[0].status != Transaction.COMMITTED) {
          break;
        }

        sectorWriteRecords = new LinkedHashMap<>();
        tags.clear();
        for (Integer sectorNum : headerList[0].sectorNumList) {
          ++offset;
          tag = genTag(transID, Disk.READ, logIndex2secNum(0, offset));
          tags.add(tag);
          sectorWriteRecords.put(sectorNum, new byte[Disk.SECTOR_SIZE]);
          disk.startRequest(Disk.READ, tag, logIndex2secNum(0, offset), sectorWriteRecords.get(sectorNum));
        }
        callbackTracker.waitForTags(tags);

        // 读取commit sector并验证合法性
        ++offset;
        tag = genTag(transID, Disk.READ, logIndex2secNum(0, offset));
        disk.startRequest(Disk.READ, tag, logIndex2secNum(0, offset), buffer);
        callbackTracker.waitForTag(tag);

        Transaction transaction = new Transaction(headerList[0], sectorWriteRecords);
        transaction.writeCommit(commitBuffer);
        if (!Arrays.equals(buffer, commitBuffer)) {
          // 非法事务
          break;
        }
        Common.debugPrintln("Trans id: ", transaction.getTransID().toInt());
        writeBackList.addCommitted(transaction);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  // logStart是在磁盘日志区为事务分配的空间的逻辑起始位置
  // index是指定的扇区的逻辑下标
  // 日志是循环的
  // 通过该方法计算出该扇区在磁盘的物理扇区号
  private int logIndex2secNum(int logStart, int index) {
    return LOG_REGION + (logStart + index) % ADisk.REDO_LOG_SECTORS;
  }

}
