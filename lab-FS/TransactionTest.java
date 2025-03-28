
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

public class TransactionTest {
  //-------------------------------------------------------
  // main() -- LogStatus test
  //-------------------------------------------------------
   public static void main(String[] args) throws InterruptedException {
        Transaction transaction = new Transaction();
        
        testMeta(transaction);
        testRW(transaction, 100);
        System.out.println("All threads have completed.");
    }

    private static void testMeta(Transaction transaction) {
        System.out.println("Test 1: test meta set");
        int start = 123, nSectors = 321;
        transaction.getTransID();
        transaction.rememberLogSectors(start, nSectors);
        assert(transaction.recallLogSectorStart() == start);
        assert(transaction.recallLogSectorNSectors() == nSectors);
        nSectors *= 2;
        transaction.rememberLogSectors(start, nSectors);
        assert(transaction.recallLogSectorStart() == start);
        assert(transaction.recallLogSectorNSectors() == nSectors);
        start *= 2;
        transaction.rememberLogSectors(start, nSectors);
        assert(transaction.recallLogSectorStart() == start);
        assert(transaction.recallLogSectorNSectors() == nSectors);
        System.out.println("Test 1 Passed!");
    }

    private static void testRW(Transaction transaction, int times) {
        System.out.println("Test 2: test sector read & write");

        Random random = new Random();
        HashMap<Integer, byte[]> writtenSectors = new HashMap<>();
        byte[] buffer = new byte[Disk.SECTOR_SIZE];
        byte b;

        // 定义一个函数式接口用于封装重复的逻辑
        java.util.function.Consumer<HashMap<Integer, byte[]>> verifySectors = sectors -> {
            for (int i = 0; i < Disk.NUM_OF_SECTORS; ++i) {
                if (sectors.containsKey(i)) {
                    assert transaction.checkRead(i, buffer);
                    assert Arrays.equals(sectors.get(i), buffer);
                } else {
                    assert !transaction.checkRead(i, buffer);
                }
            }
        };

        // 初始验证
        verifySectors.accept(writtenSectors);

        while (times-- > 0) {
            b = (byte) random.nextInt(Byte.MAX_VALUE + 1);
            setBuffer((byte) b, buffer);
            int sector = random.nextInt(Disk.NUM_OF_SECTORS);
            transaction.addWrite(sector, Arrays.copyOf(buffer, buffer.length));
            writtenSectors.put(sector, Arrays.copyOf(buffer, buffer.length));

            // 每次写入后验证
            verifySectors.accept(writtenSectors);
        }
        System.out.println("Test 2 Passed!");
    }

    private static void setBuffer(byte value, byte b[])
    {
        int ii;
        for(ii = 0; ii < Disk.SECTOR_SIZE; ii++){
            b[ii] = value;
        }
        return;
  }
}