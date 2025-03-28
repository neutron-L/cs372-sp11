
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

public class TransactionTest {
  //-------------------------------------------------------
  // main() -- LogStatus test
  //-------------------------------------------------------
   public static void main(String[] args) throws InterruptedException {
        Transaction transaction = new Transaction();
        
        testMeta(transaction);
        testRW(transaction, 100);

        transaction = new Transaction();
        testHeaderParse(transaction, 5);
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
            assert(transaction.getNUpdatedSectors() == writtenSectors.size());
        }
        System.out.println("Test 2 Passed!");
    }

    private static void testHeaderParse(Transaction transaction, int times) {
        System.out.println("Test 3: test header write & parse");

        Random random = new Random();
        LinkedList<Integer> writtenSectorNums = new LinkedList<>();
        byte[] buffer = new byte[Disk.SECTOR_SIZE];

        for (int i = 0; i < times; ++i) {
            setBuffer((byte) 0, buffer);
            int sector = random.nextInt(Disk.NUM_OF_SECTORS);
            transaction.addWrite(sector, Arrays.copyOf(buffer, buffer.length));
            writtenSectorNums.add(sector);
        }

        transaction.rememberLogSectors(random.nextInt(Disk.NUM_OF_SECTORS), random.nextInt(Disk.ADISK_REDO_LOG_SECTORS));

        int id = transaction.getTransID().toInt();
        int logStart = transaction.recallLogSectorStart();
        int logSectors = transaction.recallLogSectorNSectors();

        System.out.println("Before write");
        System.out.println("transID: " + id);
        System.out.println("sector list: " + writtenSectorNums);
        System.out.println("start: " + logStart);
        System.out.println("log sectors: " + logSectors);

        assert 1 == transaction.writeHeader(buffer);
        Transaction.parseHeader(buffer);

        System.out.println("Test 3 Passed!");
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