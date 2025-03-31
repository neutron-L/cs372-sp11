
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class AdiskTest {
  //-------------------------------------------------------
  // main() -- ADisk test
  //-------------------------------------------------------
   public static void main(String[] args) throws InterruptedException {
        sequentialTest();
        System.out.println("All Tests Passed!");
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
          ADisk aDisk = new ADisk(false);

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
          System.out.println(readBuffer[0] + " " + buffer1[1]);
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

          System.out.println("Test 1 Passed!");
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }
}