
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Set;

public class ADiskTest {
  // -------------------------------------------------------
  // main() -- ADisk test
  // -------------------------------------------------------
  public static void main(String[] args) throws InterruptedException {
    sequentialTest();
    concurrentTest(50, 100, 0);
    recoveryTest();
    System.out.println("All Tests Passed!");
    System.exit(0);
  }

  // 顺序执行一些事务
  // 测试commit abort的影响
  // 测试从自身更新过的扇区、已经提交扇区读取功能
  // 测试最后提交事务更新的扇区功能
  private static void sequentialTest() {
    System.out.println("Test 1: test sequential write & read & commit / abort");

    byte b = 0;
    byte[] readBuffer = new byte[Disk.SECTOR_SIZE];

    try {
      ADisk aDisk = new ADisk(true);

      // 事务1读写两个扇区并提交
      TransID transID1 = aDisk.beginTransaction();

      byte[] buffer1 = new byte[Disk.SECTOR_SIZE];
      b = 0x10;
      Common.setBuffer(b, buffer1);
      aDisk.writeSector(transID1, 2100, buffer1);
      aDisk.readSector(transID1, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer1);

      byte[] buffer2 = new byte[Disk.SECTOR_SIZE];
      b = 0x11;
      Common.setBuffer(b, buffer2);
      aDisk.writeSector(transID1, 2101, buffer2);
      aDisk.readSector(transID1, 2101, readBuffer);
      assert Arrays.equals(readBuffer, buffer2);

      aDisk.commitTransaction(transID1);

      TransID transID2 = aDisk.beginTransaction();

      aDisk.readSector(transID2, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer1);
      aDisk.readSector(transID2, 2101, readBuffer);
      assert Arrays.equals(readBuffer, buffer2);

      byte[] buffer3 = new byte[Disk.SECTOR_SIZE];
      b = 0x20;
      Common.setBuffer(b, buffer3);
      aDisk.writeSector(transID2, 2101, buffer3);
      aDisk.readSector(transID2, 2101, readBuffer);
      assert Arrays.equals(readBuffer, buffer3);

      aDisk.readSector(transID2, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer1);

      aDisk.writeSector(transID2, 2100, buffer3);
      aDisk.readSector(transID2, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer3);

      aDisk.commitTransaction(transID2);

      TransID transID3 = aDisk.beginTransaction();

      byte[] buffer4 = new byte[Disk.SECTOR_SIZE];

      aDisk.readSector(transID3, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer3);

      b = 0x30;
      Common.setBuffer(b, buffer4);
      aDisk.writeSector(transID3, 2100, buffer4);
      aDisk.readSector(transID3, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer4);

      byte[] buffer5 = new byte[Disk.SECTOR_SIZE];
      b = 0x31;
      Common.setBuffer(b, buffer5);
      aDisk.writeSector(transID3, 2101, buffer5);
      aDisk.readSector(transID3, 2101, readBuffer);
      assert Arrays.equals(readBuffer, buffer5);

      aDisk.abortTransaction(transID3);

      TransID transID4 = aDisk.beginTransaction();

      aDisk.readSector(transID4, 2100, readBuffer);
      assert Arrays.equals(readBuffer, buffer3);
      aDisk.readSector(transID4, 2101, readBuffer);
      assert Arrays.equals(readBuffer, buffer3);

      aDisk.commitTransaction(transID4);
    } catch (Exception e) {
      e.printStackTrace();
    }
    System.out.println("Test 1 Passed!");
  }

  private static void concurrentTest(int threadCount, int totSector, double prob) {
    System.out.println("Test 2: test concurrent write & read & commit / abort");

    try {
      ADisk aDisk = new ADisk(false);
      CountDownLatch latch = new CountDownLatch(threadCount);
      HashMap<Integer, HashMap<Integer, byte[]>> globalUpdatedDict = new HashMap<Integer, HashMap<Integer, byte[]>>();

      if (totSector > 20) {
        totSector = 20;
      }
      // 线程各自更新指定范围内的扇区
      Vector<Integer> sectors = new Vector<>();
      for (int i = 0; i < totSector; ++i) {
        sectors.add(2100 + i);
      }
      for (int i = 0; i < threadCount; i++) {
        int times = 3 * totSector / threadCount;
        Thread thread = new Thread(() -> {
          threadTransactioin(aDisk, latch, sectors, globalUpdatedDict, times, prob);
        });
        thread.start();
      }

      latch.await();
      // 获取commit顺序
      Vector<Integer> order = aDisk.committedOrder();
      HashMap<Integer, byte[]> committedSectors = new HashMap<Integer, byte[]>();
      System.out.println(order.size() + " transactions committed; " +
          (threadCount - order.size()) + " transactions abort");
      Common.debugPrintln(order.size(), " ", globalUpdatedDict.size());
      assert order.size() == globalUpdatedDict.size();
      for (Integer id : order) {
        HashMap<Integer, byte[]> dict = globalUpdatedDict.get(id);

        Set<Integer> keys = dict.keySet();

        for (Integer key : keys) {
          committedSectors.put(key, dict.get(key));
        }
      }

      // 合成最终更新的sector集合并判断是否相等
      Set<Integer> keys = committedSectors.keySet();

      TransID transID = aDisk.beginTransaction();
      byte[] buffer = new byte[Disk.SECTOR_SIZE];
      for (Integer key : keys) {
        aDisk.readSector(transID, key, buffer);
        Common.debugPrintln(key, " expect ", committedSectors.get(key)[0], " actual ", buffer[0]);
        assert Arrays.equals(buffer, committedSectors.get(key));
      }
      aDisk.abortTransaction(transID);
    } catch (Exception e) {
      e.printStackTrace();
    }

    System.out.println("Test 2 Passed!");
  }

  private static void threadTransactioin(ADisk aDisk, CountDownLatch latch, Vector<Integer> sectors,
      HashMap<Integer, HashMap<Integer, byte[]>> globalUpdatedDict, int times, double prob) {
    int sectorNum = 0;
    int n = sectors.size();

    HashMap<Integer, byte[]> localUpdatedDict = new HashMap<Integer, byte[]>();

    try {
      TransID transID = aDisk.beginTransaction();

      while (times-- > 0) {
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        Common.setBuffer((byte) ThreadLocalRandom.current().nextInt(0, n), buffer);
        sectorNum = sectors.get(ThreadLocalRandom.current().nextInt(0, n));
        aDisk.writeSector(transID, sectorNum, buffer);
        localUpdatedDict.put(sectorNum, buffer);
      }

      // 本地检查
      Set<Integer> keys = localUpdatedDict.keySet();

      for (Integer key : keys) {
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        aDisk.readSector(transID, key, buffer);
        assert Arrays.equals(buffer, localUpdatedDict.get(key));
      }

      Random rand = new Random(); // 使用默认种子
      if (rand.nextDouble() >= prob) {
        globalUpdatedDict.put(transID.toInt(), localUpdatedDict);

        aDisk.commitTransaction(transID);
      } else {
        aDisk.abortTransaction(transID);
      }

      latch.countDown();
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  // 指定log的最初起始处
  // 测试一系列事务提交后在日志区域是否占据了写入时指定的位置和长度
  // 需要确保abort对日志区域无影响，且跨越日志区域边界无影响

  // NOTE: 只验证了恢复的时候能够正确读出已提交事务日志信息
  // 暂时没有想到模拟abort的更好的方法，abort应该杀死并重启写回线程
  private static void recoveryTest() {
    System.out.println("Test 3: test recovery");

    byte b = 0;
    Random random = new Random();

    try {
      // 第一次操作，格式化磁盘
      ADisk aDisk = new ADisk(true);
      LinkedList<TestCommittedInfo> expected = new LinkedList<>();
      int head = 0;
      long seq = 0;
      int times = 100;

      while (times-- > 0) {
        // 事务1读写两个扇区并提交
        TransID transID = aDisk.beginTransaction();

        int n = 2;
        int temp = n;
        while (n-- > 0) {
          byte[] buffer = new byte[Disk.SECTOR_SIZE];
          b = 0x10;
          Common.setBuffer(b, buffer);
          aDisk.writeSector(transID, 2100 + n, buffer);
        }
        if (random.nextDouble() >= 0.3) {
          aDisk.commitTransaction(transID);
          expected.add(new TestCommittedInfo(transID.toInt(), seq, head, temp + 2));
          ++seq;
          head += temp + 2;
          head %= Disk.ADISK_REDO_LOG_SECTORS;
        } else {
          aDisk.abortTransaction(transID);
        }
        
      }


      aDisk.abort();
      LinkedList<TestCommittedInfo> result = aDisk.recovery();

      while (result.size() < expected.size()) {
        expected.removeFirst();
      }
      // if (result.size() != expected.size()) {
      //   TestCommittedInfo info = result.getLast();
      //   Common.debugPrintln(info.commitSeq, " ", info.logSectors);
      //   assert false;
      // }
      while (!result.isEmpty()) {
        TestCommittedInfo info1 = result.remove();
        TestCommittedInfo info2 = expected.remove();

       
        if (!info1.equals(info2)) {
          Common.debugPrintln(info1.id, " ", info2.id);
          Common.debugPrintln(info1.commitSeq, " ", info2.commitSeq);
          Common.debugPrintln(info1.logStart, " ", info2.logStart);
          Common.debugPrintln(info1.logSectors, " ", info2.logSectors);   
          assert false;
        }
      }

      System.out.println("Test 3 Passed!");
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}