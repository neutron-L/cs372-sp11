
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class LogStatusTest {
  //-------------------------------------------------------
  // main() -- LogStatus test
  //-------------------------------------------------------
   public static void main(String[] args) throws InterruptedException {
        int threadCount = 100; // 指定线程数量
        LogStatus logStatus = new LogStatus();
        CountDownLatch latch = new CountDownLatch(threadCount);

        logStatus.recoverySectorsInUse(120, 128);
        assert(logStatus.logStartPoint() == 120);

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
              int times = ThreadLocalRandom.current().nextInt(1, 8);
              performLogOperations(logStatus, latch, times);
            });
            thread.start();
        }

        // 等待所有线程执行完毕
        latch.await();
        int[] meta = logStatus.getMeta();
        assert(meta[0] == 0 && meta[1] == 0 && meta[2] == 0);

        logStatus.recoverySectorsInUse(1000, 128);
        meta = logStatus.getMeta();
        assert(meta[0] == 1000 && meta[1] == (1000 + 128) % Disk.ADISK_REDO_LOG_SECTORS && meta[2] == 128);

        System.out.println("All threads have completed.");
    }

    private static void performLogOperations(LogStatus logStatus, CountDownLatch latch, int times) {
        try {
          while (times-- > 0) {
              int randomLength = ThreadLocalRandom.current().nextInt(128, 256);
              int startSector = logStatus.reserveLogSectors(randomLength);
              // System.out.println(Thread.currentThread().getName() + " reserved sectors starting at: " + startSector + " with length: " + randomLength);
              int sleepTime = ThreadLocalRandom.current().nextInt(1, 5);
              Thread.sleep(sleepTime * 1000);
              int result = logStatus.writeBackDone(startSector, randomLength);
              // System.out.println(Thread.currentThread().getName() + " write back done for sectors starting at: " + result);
          }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
        }
    }
}