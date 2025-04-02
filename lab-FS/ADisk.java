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
import java.util.LinkedList;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;

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
  private AtomicLong nextCommitSeq;

  // internal class都做了并发控制，貌似不需要控制并发了
  // 但是测试时希望能获取到事务提交的顺序
  private SimpleLock lock;
  private Condition writeBackCond;
  private Condition activateListNotFullCond;

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
      activateListNotFullCond = lock.newCondition();

      committedOrder = new Vector<>();
      nextCommitSeq = new AtomicLong(0);

      // 格式化磁盘或者日志恢复
      if (format) {
        formatDisk();
        logStatus.recoverySectorsInUse(888, 0);
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        logStatus.writeLogStatus(buffer);
        disk.startRequest(Disk.WRITE, 0, LOG_STATUS_SECTOR_NUMBER, buffer);
        callbackTracker.waitForTag(0);
      } else {
        recovery();
      }
      // 启动一个线程完成writeback工作
      Thread writeBackThread = new Thread(() -> {
        writeBack(writeBackList, disk, callbackTracker);
      });
      writeBackThread.start();
    } catch (Exception e) {
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
    try {
      lock.lock();
      Transaction transaction = new Transaction();
      activeTransactionList.put(transaction);

      return transaction.getTransID(); 
    } finally {
      lock.unlock();
    }
    
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
      lock.lock();

      transaction = activeTransactionList.remove(tid);
      activateListNotFullCond.signalAll();

      if (transaction == null) {
        throw new IllegalArgumentException("Bad transaction id");
      }
      // 为事务在log status中申请空间
      logSectors = 2 + transaction.getNUpdatedSectors();
      logStart = logStatus.reserveLogSectors(logSectors);

      while (logStatus.freeSpace() < logSectors) {
        writeBackCond.awaitUninterruptibly();
      }
      transaction.rememberLogSectors(logStart, logSectors);
      transaction.commit(nextCommitSeq.getAndIncrement());
      

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


      // transact移入写回队列
      committedOrder.add(transaction.getTransID().toInt());
      writeBackList.addCommitted(transaction);
      Common.debugPrintln("trans id: ", transaction.getTransID().toInt(), " log sectors: ", transaction.recallLogSectorNSectors());

      // 批次更新head
    } catch (Exception e) {
      e.printStackTrace();
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
      lock.lock();

      Transaction transaction = activeTransactionList.remove(tid);
      if (transaction == null) {
        throw new IllegalArgumentException("Bad transaction id");
      }
      transaction.abort();
    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      lock.unlock();
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
    try {
      lock.lock();

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
    } finally {
      lock.unlock();
    }
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
    try {
      lock.lock();

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
    } finally {
      lock.unlock();
    }
  }

  public Vector<Integer> committedOrder() {
    return committedOrder;
  }

  public void writeBack(WriteBackList writeBackList, Disk disk, CallbackTracker callbackTracker) {
    Transaction transaction = null;
    int logSectors = 0;
    int sectorNum = 0;
    int tag = 0;
    Vector<Integer> tags = new Vector<>();

    while (true) {
      try {
          lock.lock();

          transaction = writeBackList.getNextWriteback();
          // 将该事务的更新块写入磁盘
          for (int i = 0; i < logSectors - 2; ++i) {
            byte[] buffer = new byte[Disk.SECTOR_SIZE];
            sectorNum = transaction.getUpdateISecNum(i);
            transaction.getUpdateI(i, buffer);
            tag = genTag(transaction.getTransID(), Disk.WRITE, sectorNum);
            tags.add(tag);
            disk.startRequest(Disk.WRITE, tag, sectorNum, buffer);
          }
          callbackTracker.waitForTags(tags);
          writeBackList.removeNextWriteback();

          // 判断是否需要更新tail
          // 为避免频繁更新tail
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          lock.unlock();
        }
      }

      // // 更新磁盘上的logstatus，必须等待，这里的写入必须是顺序的

  }

  // 希望构造具有一定意义的tag
  // 高16位为事务id，后8位为 R(0)/W(1)，后八位为扇区号
  private static int genTag(TransID transID, int op, int sectorNum) {
    return (transID.toInt() & 0xFFFF0000) | ((op & 0x1) << 8) | (sectorNum & 0xFF);
  }

  // 目前只需要清空super block（log status)
  // 和日志区域
  // 需要更新空闲扇区和inode位图
  private void formatDisk() {
    byte[] buffer = new byte[Disk.SECTOR_SIZE];
    Common.setBuffer((byte)0, buffer);
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 根据需要选择字节序

    byteBuffer.putInt(0); 
    byteBuffer.putInt(0);

    Vector<Integer> tags = new Vector<>();
    try {
      disk.startRequest(Disk.WRITE, 0, LOG_STATUS_SECTOR_NUMBER, buffer);
      tags.add(0);

      for (int i = LOG_REGION; i < Disk.ADISK_REDO_LOG_SECTORS; ++i) {
        disk.startRequest(Disk.WRITE, i, i, buffer);
        tags.add(i);
      }
      callbackTracker.waitForTags(tags);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public LinkedList<TestCommittedInfo> recovery() {
    Common.debugPrintln("Recovery...");

    // for test
    LinkedList<TestCommittedInfo> result = new LinkedList<>();

    byte[] buffer = new byte[Disk.SECTOR_SIZE];
    byte[] commitBuffer = new byte[Disk.SECTOR_SIZE];
    Vector<Integer> tags = new Vector<>();
    int head = 0;
    int tail = 0;
    int usedSectors = 0;
    int logSectors = 0;
    long prevCommitSeq = -1;

    try {
      // 读取第一个扇区获取logStatus记录的日志起点
      // 此时的log status不是最新的，只能保证tail之前的事务均已写回
      // 需要通过commit seq获取最新的head和usedSectors
      disk.startRequest(Disk.READ, 0, LOG_STATUS_SECTOR_NUMBER, buffer);
      callbackTracker.waitForTag(0);
      logStatus = LogStatus.parseLogStatus(buffer);
      tail = logStatus.getTail();
      
      // 临时用一个“假”事务，主要用于构造tag
      TransID transID = new TransID(0);
      int tag = 0;
      
      TransactionHeader[] headerList = new TransactionHeader[1];
      LinkedHashMap<Integer, byte[]> sectorWriteRecords = null;

      head = tail;
      Common.debugPrintln(head);
      while (true) {
        // 读取下一个事务的首个扇区并判断是否合法
        tag = genTag(transID, Disk.READ, logIndex2secNum(0, head));
        disk.startRequest(Disk.READ, tag, logIndex2secNum(0, head), buffer);
        callbackTracker.waitForTag(tag);

        logSectors = Transaction.parseHeader(buffer, headerList);
        if (logSectors == -1 || headerList[0].status != Transaction.COMMITTED || headerList[0].logStart != head) {
          Common.debugPrintln("Read header sector transaction, break", logSectors, " ", headerList[0].status, " : ", headerList[0].logStart, " ", logIndex2secNum(0, head));
          break;
        }

        if (prevCommitSeq == -1 || prevCommitSeq == headerList[0].commitSeq - 1) {
          prevCommitSeq = headerList[0].commitSeq;
        } else {
          Common.debugPrintln("invalid commitSeq: ", headerList[0].commitSeq , "; prev commitseq: ",  prevCommitSeq, ", break");
          break;
        }

        sectorWriteRecords = new LinkedHashMap<>();
        tags.clear();
        for (Integer sectorNum : headerList[0].sectorNumList) {
          ++head;
          tag = genTag(transID, Disk.READ, logIndex2secNum(0, head));
          tags.add(tag);
          sectorWriteRecords.put(sectorNum, new byte[Disk.SECTOR_SIZE]);
          disk.startRequest(Disk.READ, tag, logIndex2secNum(0, head), sectorWriteRecords.get(sectorNum));
        }
        callbackTracker.waitForTags(tags);

        // 读取commit sector并验证合法性
        ++head;
        tag = genTag(transID, Disk.READ, logIndex2secNum(0, head));
        disk.startRequest(Disk.READ, tag, logIndex2secNum(0, head), buffer);
        callbackTracker.waitForTag(tag);

        Transaction transaction = new Transaction(headerList[0], sectorWriteRecords);
        transaction.writeCommit(commitBuffer);
        if (!Arrays.equals(buffer, commitBuffer)) {
          // 非法事务
          Common.debugPrintln("Read invalid commit sector transaction, break");
          break;
        }
        Common.debugPrintln("Trans id: ", transaction.getTransID().toInt());
        Common.debugPrintln("log Start: ", transaction.recallLogSectorStart());
        Common.debugPrintln("log Sectors: ", transaction.recallLogSectorNSectors());
        writeBackList.addCommitted(transaction);
        assert transaction.getNUpdatedSectors() + 2 == transaction.recallLogSectorNSectors();
        usedSectors += transaction.getNUpdatedSectors() + 2;

        ++head;
        head %=  Disk.ADISK_REDO_LOG_SECTORS;
        result.add(new TestCommittedInfo(transaction.getTransID().toInt(), transaction.getCommittedSeq(), 
          transaction.recallLogSectorStart(), transaction.recallLogSectorNSectors()));
      }

      assert usedSectors >= logStatus.getUsedSectors();
      nextCommitSeq.set(prevCommitSeq + 1);
      logStatus.recoverySectorsInUse(tail, usedSectors);
    } catch (IOException e) {
      e.printStackTrace();
    }

    return result;
  }

  // logStart是在磁盘日志区为事务分配的空间的逻辑起始位置
  // index是指定的扇区的逻辑下标
  // 日志是循环的
  // 通过该方法计算出该扇区在磁盘的物理扇区号
  private int logIndex2secNum(int start, int index) {
    return LOG_REGION + (start + index) % ADisk.REDO_LOG_SECTORS;
  }

  /* For test */
  // ADisk abort, lose all state
  public void abort() {
    activeTransactionList = new ActiveTransactionList();
    writeBackList = new WriteBackList();
    logStatus = new LogStatus();
    committedOrder.clear();
  }

}

class TestCommittedInfo {
  public int id;
  public long commitSeq;
  public int logStart;
  public int logSectors;

  public TestCommittedInfo(int id, long commitSeq, int logStart, int logSectors) {
    this.id = id;
    this.commitSeq = commitSeq;
    this.logStart = logStart;
    this.logSectors = logSectors;
  }

  @Override
    public boolean equals(Object obj) {
        // 检查是否为同一个引用
        if (this == obj) {
            return true;
        }
        // 检查对象是否为 null 或者类型不匹配
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        TestCommittedInfo other = (TestCommittedInfo) obj;

        return id == other.id && commitSeq == other.commitSeq && logStart == other.logStart && logSectors == other.logSectors;
    }

}
