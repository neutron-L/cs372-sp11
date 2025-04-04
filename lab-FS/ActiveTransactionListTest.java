
import java.util.LinkedList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class ActiveTransactionListTest {
    // -------------------------------------------------------
    // main() -- ActiveTransactionList test
    // 有三个方法，put / get / remove
    // 准备一系列的TransID，其中部分有对应的事务
    // 一个线程put事务，另外两个随机get/remove
    // 只有id对应的事务存在操作才会成功
    // -------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        sequentialTest(Common.MAX_CONCURRENT_TRANSACTIONS);
        concurrencyTest(4, Common.MAX_CONCURRENT_TRANSACTIONS / 4);
        System.out.println("All Tests Passed!");

    }

    private static void sequentialTest(int times) {
        System.out.println("Test 1: test sequential op");

        ActiveTransactionList activeTransactionList = new ActiveTransactionList();

        for (int i = 0; i < times; ++i) {
            if (i % 2 == 0) {
                activeTransactionList.put(new Transaction(new TransID(i)));
            }
        }

        for (int i = 0; i < times; ++i) {
            if (i % 2 == 0) {
                assert activeTransactionList.get(new TransID(i)) != null;
            } else {
                assert activeTransactionList.get(new TransID(i)) == null;
            }
        }

        for (int i = 0; i < times; ++i) {
            if (i % 2 == 0) {
                assert activeTransactionList.remove(new TransID(i)) != null;
            } else {
                assert activeTransactionList.remove(new TransID(i)) == null;
            }
        }
        System.out.println("Test 1 Passed!");
    }

    private static void concurrencyTest(int threadCount, int times)
     throws InterruptedException {
        System.out.println("Test 2: test concurrency op");

        ActiveTransactionList activeTransactionList = new ActiveTransactionList();
        CountDownLatch latch = new CountDownLatch(threadCount);
        SimpleLock lock = new SimpleLock();

        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                threadPutAndRemove(activeTransactionList, lock, latch, times);
              });
              thread.start();
        }

        // 等待所有线程执行完毕
        latch.await();
        System.out.println("Test 2 Passed!");
    }

    private static void threadPutAndRemove(ActiveTransactionList activeTransactionList, SimpleLock lock, 
    CountDownLatch latch, int times) {
        try {
            LinkedList<TransID> idList = new LinkedList<>();

            for (int i = 0; i < times; ++i) {
                Transaction transaction = new Transaction();
                lock.lock();
                activeTransactionList.put(transaction);
                lock.unlock();
                idList.add(transaction.getTransID());
            }
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5) * 1000);

            for (TransID transID : idList) {
                lock.lock();
                assert activeTransactionList.remove(transID) != null;
                lock.unlock();
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        } finally {
            latch.countDown();
        }
    }
}