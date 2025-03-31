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

import java.util.concurrent.locks.Condition;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class LogStatus {
    private int head;
    private int tail;
    private int logLength;
    private SimpleLock lock;
    private Condition freeSpace;

    private static final Logger LOGGER = Logger.getLogger(LogStatusTest.class.getName());

    public LogStatus() {
        lock = new SimpleLock();
        freeSpace = lock.newCondition();
        head = tail = 0;
        logLength = 0;

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

        try {
            lock.lock();
            while (Disk.ADISK_REDO_LOG_SECTORS - logLength < nSectors) {
                freeSpace.awaitUninterruptibly();
            }
            start = head;
            LOGGER.fine(Thread.currentThread().getName()
                    + String.format(" reserve: tail = %d; head = %d; logLength = %d", tail, head, logLength));
            head = (head + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
            logLength += nSectors;
        } finally {
            lock.unlock();
        }
        return start;
    }

    //
    // The write back for the specified range of
    // sectors is done. These sectors may be safely
    // reused for future transactions. (Circular log)
    //
    public int writeBackDone(int startSector, int nSectors) {
        int start = -1;

        try {
            lock.lock();
            LOGGER.fine(Thread.currentThread().getName()
                    + String.format(" writeBack: tail = %d; head = %d; logLength = %d", tail, head, logLength));
            start = tail;
            assert tail == startSector;
            tail = (tail + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
            logLength -= nSectors;
            freeSpace.signalAll();
        } finally {
            lock.unlock();
        }
        return start;
    }

    //
    // During recovery, we need to initialize the
    // LogStatus information with the sectors
    // in the log that are in-use by committed
    // transactions with pending write-backs
    //
    public void recoverySectorsInUse(int startSector, int nSectors) {
        try {
            lock.lock();
            tail = startSector;
            head = (startSector + nSectors) % Disk.ADISK_REDO_LOG_SECTORS;
            logLength = nSectors;
        } finally {
            lock.unlock();
        }
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
        int start = -1;

        try {
            lock.lock();
            start = tail;
        } finally {
            lock.unlock();
        }

        return start;
    }

    // FOR TEST!!
    public int[] getMeta() {
        int[] ret = new int[3];

        try {
            lock.lock();
            ret[0] = tail;
            ret[1] = head;
            ret[2] = logLength;
        } finally {
            lock.unlock();
        }

        return ret;
    }
}