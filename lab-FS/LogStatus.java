/*
 * LogStatus.java
 *
 * Keep track of where head of log is (where should next
 * committed transactio go); where tail is (where
 * has write-back gotten); and where recovery
 * should start.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */


import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogStatus {
    private int head;
    private int tail;
    private int usedSectors;
    private long nextCommitSeq;

    // private SimpleLock lock;
    // private Condition freeSpace;

    private static final Logger LOGGER = Logger.getLogger(LogStatusTest.class.getName());

    public LogStatus() {
        // lock = new SimpleLock();
        // freeSpace = lock.newCondition();
        head = tail = 0;
        usedSectors = 0;
        nextCommitSeq = 0;

        // 设置日志级别为 FINE，用于调试信息输出
        LOGGER.setLevel(Level.WARNING);

        // 添加控制台处理器
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINE);
        consoleHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(consoleHandler);
    }

    public LogStatus(int tail, int usedSectors, long nextCommitSeq) {
        // lock = new SimpleLock();
        // freeSpace = lock.newCondition();
        this.tail = tail;
        this.usedSectors = usedSectors;
        this.nextCommitSeq = nextCommitSeq;
        this.head = (this.tail + usedSectors) % Disk.ADISK_REDO_LOG_SECTORS;

        // 设置日志级别为 FINE，用于调试信息输出
        LOGGER.setLevel(Level.WARNING);

        // 添加控制台处理器
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINE);
        consoleHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(consoleHandler);
    }

    //
    // Return the index of the log sector where
    // the next transaction should go.
    //
    public int reserveLogSectors(int nSectors) {
        int start = -1;

        start = head;
        LOGGER.fine(Thread.currentThread().getName()
                + String.format(" reserve: tail = %d; head = %d; logLength = %d", tail, head, usedSectors));
        head = (head + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
        usedSectors += nSectors;

        return start;
    }

    //
    // The write back for the specified range of
    // sectors is done. These sectors may be safely
    // reused for future transactions. (Circular log)
    //
    public int writeBackDone(int startSector, int nSectors) {
        int start = -1;

        // Common.debugPrintln("--------------------------");
        // Common.debugPrintln("expect ", tail, " actual ", startSector, " nSectors: ", nSectors);
        start = tail;
        if (tail != startSector) {
            System.exit(1);
        }
        tail = (tail + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
        // Common.debugPrintln("new tail ", tail);
        // Common.debugPrintln("--------------------------");
        usedSectors -= nSectors;

        return start;
    }

    //
    // During recovery, we need to initialize the
    // LogStatus information with the sectors
    // in the log that are in-use by committed
    // transactions with pending write-backs
    //
    public void recoverySectorsInUse(int startSector, int nSectors) {
        tail = startSector;
        head = (startSector + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
        usedSectors = nSectors;
    }

    //
    // On recovery, find out where to start reading
    // log from. LogStatus should reserve a sector
    // in a well-known location. (Like the log, this sector
    // should be "invisible" to everything above the
    // ADisk interface.) You should update this
    // on-disk information at appropriate times.
    // Then, on recovery, you can read this information
    // to find out where to start processing the log from.
    //
    // NOTE: You can update this on-disk info
    // when you fininish write-back for a transaction.
    // But, you don't need to keep this on-disk
    // sector exactly in sync with the tail
    // of the log. It can point to a transaction
    // whose write-back is complete (there will
    // be a bit of repeated work on recovery, but
    // not a big deal.) On the other hand, you must
    // make sure of three things: (1) it should always
    // point to a valid header record; (2) if a
    // transaction T's write back is not complete,
    // it should point to a point no later than T's
    // header; (3) reserveLogSectors must block
    // until the on-disk log-start-point points past
    // the sectors about to be reserved/reused.
    //
    public int logStartPoint() {
        return tail;
    }


    public int freeSpace() {
        return Disk.ADISK_REDO_LOG_SECTORS - usedSectors;
    }

    // FOR TEST!!
    public int[] getMeta() {
        int[] ret = new int[3];

        ret[0] = tail;
        ret[1] = head;
        ret[2] = usedSectors;

        return ret;
    }

    public int getTail() { return this.tail; }
    public int getHead() { return this.head; }
    public int getUsedSectors() { return this.usedSectors; }
    public long getNextCommitSeq() { return this.nextCommitSeq; }
    public void setNextCommitSeq(long nextCommitSeq) { this.nextCommitSeq = nextCommitSeq; }
    public long getAndIncrementSeq() { return this.nextCommitSeq++; }
    

    public void writeLogStatus(byte[] buffer) 
    throws IllegalArgumentException
    {
        Common.checkBuffer(buffer, 1);

        // fill 0
        Common.setBuffer((byte) 0, buffer);
        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        byteBuffer.order(ByteOrder.BIG_ENDIAN); // 根据需要选择字节序

        // 序列化 transID
        byteBuffer.putInt(tail);
        byteBuffer.putInt(usedSectors);
        byteBuffer.putLong(nextCommitSeq);
    }

    public static LogStatus parseLogStatus(byte[] buffer) 
    throws IllegalArgumentException
    {
        Common.checkBuffer(buffer, 1);

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
        byteBuffer.order(ByteOrder.BIG_ENDIAN); // 根据需要选择字节序

        // 序列化 transID
        int tail = byteBuffer.getInt();
        int usedSectors = byteBuffer.getInt();
        long nextCommitSeq = byteBuffer.getLong();

        return new LogStatus(tail, usedSectors, nextCommitSeq);
    }
}