
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
        testRW(transaction, 3);

        transaction = new Transaction();
        testHeaderParse(transaction, 5);
        testCommitParse(transaction);
        testTransactionParse(transaction);
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
        byte b = (byte)0;
        int sector = 0;

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

        LinkedList<Integer> secNumList = new LinkedList<>();
        while (times-- > 0) {
            b = (byte) random.nextInt(Byte.MAX_VALUE + 1);
            setBuffer((byte) b, buffer);
            sector = random.nextInt(Disk.NUM_OF_SECTORS);
            secNumList.add(sector);
            transaction.addWrite(sector, Arrays.copyOf(buffer, buffer.length));
            writtenSectors.put(sector, Arrays.copyOf(buffer, buffer.length));

            // 每次写入后验证
            verifySectors.accept(writtenSectors);
            assert(transaction.getNUpdatedSectors() == writtenSectors.size());
        }

        int n = transaction.getNUpdatedSectors();
        for (int i = 0; i < n; ++i) {
            sector = transaction.getUpdateISecNum(i);
            transaction.getUpdateI(i, buffer);
            assert Arrays.equals(buffer, writtenSectors.get(sector));
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
        Transaction.parseHeader(buffer, null);

        System.out.println("Test 3 Passed!");
    }
    private static void testCommitParse(Transaction transaction) {
        System.out.println("Test 4: test commit write & parse");

        byte[] buffer = new byte[Disk.SECTOR_SIZE];

        int id = transaction.getTransID().toInt();

        System.out.println("Before write");
        System.out.println("transID: " + id);

        assert 1 == transaction.writeCommit(buffer);

        long[] meta = new long[3];
        Transaction.parseCommit(buffer, meta);
        System.out.println("After parse");
        System.out.println("transID: " + meta[0]);
        System.out.println("status: " + meta[1]);
        System.out.printf("checksum: %d\n", meta[2]);

        System.out.println("Test 4 Passed!");
    }

    private static void testTransactionParse(Transaction transaction) {
        System.out.println("Test 5: test transaction write & parse");

        // 测试当预留的日志扇区不够时，无法构造写入日志的扇区数组
        int totUpdatedSector = transaction.getNUpdatedSectors();
        transaction.rememberLogSectors(12, totUpdatedSector);
        assert transaction.getSectorsForLog() == null;

        transaction.rememberLogSectors(12, totUpdatedSector + 2);
        byte[] transLog = transaction.getSectorsForLog();
        assert transLog != null;

        Transaction transaction1 = Transaction.parseLogBytes(transLog);
        assert transaction1 != null;
        byte[] transLog1 = transaction1.getSectorsForLog();
        assert Arrays.equals(transLog1, transLog) && transLog1.length == transLog.length;

        transaction.rememberLogSectors(transaction.recallLogSectorStart() + 1, transaction.recallLogSectorNSectors());
        byte[] transLog2 = transaction.getSectorsForLog();
        assert !Arrays.equals(transLog2, transLog) && transLog2.length == transLog.length;
        Transaction transaction2 = Transaction.parseLogBytes(transLog2);
        assert transaction2.recallLogSectorStart() == transaction.recallLogSectorStart() && transaction2.recallLogSectorStart() == transaction1.recallLogSectorStart() + 1;

        System.out.println("Test 5 Passed!");
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