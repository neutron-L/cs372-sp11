
import java.util.Vector;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CountDownLatch;

public class CallbackTrackerTest {
    //-------------------------------------------------------
    // main() -- LogStatus test
    //-------------------------------------------------------
    public static void main(String[] args) throws InterruptedException {
        testSimpleAddWait(5, 20);
        testDontWait(5, 20);

        System.out.println("All Tests Passed!");
    }

    private static void testSimpleAddWait(int threadCount, int times)
            throws InterruptedException {
        System.out.println("Test 1: test simple wait");
        CallbackTracker callbackTracker = new CallbackTracker();
        CountDownLatch latch = new CountDownLatch(threadCount + 1);

        // 一个线程wait一组tag，另一个线程调用requestDone
        Thread thread = new Thread(() -> {
            performTagWait(callbackTracker, latch, threadCount, times);
        });
        thread.start();

        for (int i = 0; i < threadCount; ++i) {
            int tempI = i;
            thread = new Thread(() -> {
                performTagDone(callbackTracker, latch, tempI, times);
            });
            thread.start();
        }
        latch.await();
        System.out.println("Test 1 Passed!");
    }

    private static void testDontWait(int threadCount, int times)
    throws InterruptedException
    {
        System.out.println("Test 2: test ignore & wait");
        CallbackTracker callbackTracker = new CallbackTracker();
        CountDownLatch latch = new CountDownLatch(threadCount + 1);

        // 一个线程wait一组tag，另一个线程调用requestDone
        Thread thread = new Thread(() -> {
            performTagIgnore(callbackTracker, latch, threadCount, times);
        });
        thread.start();

        for (int i = 0; i < threadCount; ++i) {
            int tempI = i;
            thread = new Thread(() -> {
                performTagDone(callbackTracker, latch, tempI, times);
            });
            thread.start();
        }
        latch.await();
        System.out.println("Test 2 Passed!");
    }

    private static void performTagWait(CallbackTracker callbackTracker,
                                       CountDownLatch latch, int threadCount, int times) {
        for (int i = 0; i < threadCount; i += 2) {
            for (int j = 0; j < times; j += 2) {
                callbackTracker.waitForTag(i * times + j);
                System.out.println((i * times + j) + " done");
            }
        }
        Vector<Integer> tags = new Vector<>();
        for (int i = 1; i < threadCount; i += 2) {
            tags.clear();
            for (int j = 1; j < times; j += 2) {
                tags.add(i * times + j);
            }
            callbackTracker.waitForTags(tags);
        }
        latch.countDown();
    }

    private static void performTagIgnore(CallbackTracker callbackTracker,
                                       CountDownLatch latch, int threadCount, int times) {
        int tag = 0;
        for (int i = 0; i < threadCount / 2; ++i) {
            for (int j = 0; j < times; ++j) {
                tag = i * times + j;
                if (tag % 2 == 0) {
                    callbackTracker.dontWaitForTag(tag);
                }  else {
                    callbackTracker.waitForTag(tag);
                    System.out.println(tag + " done");
                }
            }
        }
        Vector<Integer> ignoreTags = new Vector<>();
        Vector<Integer> tags = new Vector<>();
        for (int i = threadCount / 2; i < threadCount; ++i) {
            for (int j = 0; j < times; ++j) {
                tag = i * times + j;
                if (tag % 2 == 0) {
                    ignoreTags.add(tag);
                } else {
                    tags.add(tag);
                }
            }
        }
        callbackTracker.dontWaitForTags(ignoreTags);
        callbackTracker.waitForTags(tags);
        latch.countDown();
    }

    private static void performTagDone(CallbackTracker callbackTracker,
                                       CountDownLatch latch, int i, int times)
        {
        for (int j = 0; j < times; ++j) {
            DiskResult result = new DiskResult(Disk.READ, i * times + j, 0, null);
            callbackTracker.requestDone(result);
        }
        latch.countDown();
    }
}    