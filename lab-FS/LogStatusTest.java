
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Condition;
public class LogStatusTest {
  //-------------------------------------------------------
  // main() -- LogStatus test
  //-------------------------------------------------------
   public static void main(String[] args) throws InterruptedException {
        concurrencyTest(10, 10);

        System.out.println("All Tests Passed!");
    }

    private static void concurrencyTest(int threadCount, int times) throws InterruptedException {
        LogStatus logStatus = new LogStatus();
        CountDownLatch latch = new CountDownLatch(threadCount + 1);
        SimpleLock lock = new SimpleLock();
        Condition notEmpty = lock.newCondition();
        Condition notFull = lock.newCondition();
        LinkedList<LogInfo> queue = new LinkedList<>();

        logStatus.recoverySectorsInUse(120, 128);
        assert(logStatus.logStartPoint() == 120);
        logStatus.recoverySectorsInUse(120, 0);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
              performLogReserve(logStatus, lock, notEmpty, notFull, latch, queue, times);
            });
            thread.start();
        }

        // 一个线程负责write back
        Thread thread = new Thread(() -> {
            performLogWriteBack(logStatus, lock, notEmpty, notFull, latch, queue, times * threadCount);
        });
        thread.start();

        // 等待所有线程执行完毕
        latch.await();
        assert(logStatus.getUsedSectors() == 0);

        logStatus.recoverySectorsInUse(1000, 256);
        assert(logStatus.getTail() == 1000 && logStatus.getHead() == (1000 + 256) % Disk.ADISK_REDO_LOG_SECTORS && logStatus.getUsedSectors() == 256);
    }

    private static void performLogReserve(LogStatus logStatus, SimpleLock lock, Condition notEmpty, Condition notFull, CountDownLatch latch, LinkedList<LogInfo> queue, int times) {
        try {
          while (times-- > 0) {
              int randomLength = ThreadLocalRandom.current().nextInt(128, 256);
              lock.lock();
              while (logStatus.freeSpace() < randomLength) {
                notFull.awaitUninterruptibly();
              }
              int startSector = logStatus.reserveLogSectors(randomLength);
              queue.add(new LogInfo(startSector, randomLength));
              notEmpty.signal();
              lock.unlock();
          }
        } finally {
            latch.countDown();
        }
    }

    private static void performLogWriteBack(LogStatus logStatus, SimpleLock lock, Condition notEmpty, Condition notFull, CountDownLatch latch, LinkedList<LogInfo> queue, int times) {
        LogInfo logInfo = null;

        try {
          while (times-- > 0) {
            lock.lock();
              while (queue.isEmpty()) {
                notEmpty.awaitUninterruptibly();
              }
              logInfo = queue.removeFirst();
              Common.debugPrintln("start: ", logInfo.start, " n: ", logInfo.nSectors);
              logStatus.writeBackDone(logInfo.start, logInfo.nSectors);
              notFull.signalAll();
              lock.unlock();
          }
        } finally {
            latch.countDown();
        }
    }
}

// 定义一个类来模拟结构体
class LogInfo {
    int start;
    int nSectors;

    // 构造函数，用于初始化结构体的成员
    public LogInfo(int start, int nSectors) {
        this.start = start;
        this.nSectors = nSectors;
    }
}