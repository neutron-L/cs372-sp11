
/*
 * Transaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.zip.CRC32;

public class Transaction {
    private static final int INPROGRESS = 0xaabb;
    private static final int COMMITTED = 0xaabc;
    private static final int ABORTED = 0xaabd;

    //
    // You can modify and add to the interfaces
    //
    private final TransID transID;
    private SimpleLock lock;
    private int logStart;
    private int logSectors;
    private int status;
    private LinkedList<Integer> sectorNumList;

    private LinkedHashMap<Integer, byte[]> sectorWriteRecords;

    // 用于构造commit sector
    private long checkSum;

    public Transaction() {
        transID = new TransID();
        lock = new SimpleLock();
        sectorWriteRecords = new LinkedHashMap<>();
        sectorNumList = new LinkedList<>();
        status = INPROGRESS;
    }

    public Transaction(TransactionHeader header, LinkedHashMap<Integer, byte[]> sectorWriteRecords) {
        this.transID = header.transID;
        this.logStart = header.logStart;
        this.logSectors = header.logSectors;
        this.status = header.status;
        this.sectorNumList = header.sectorNumList;
        this.sectorWriteRecords = sectorWriteRecords;
        this.lock = new SimpleLock();
    }

    public void addWrite(int sectorNum, byte buffer[])
            throws IllegalArgumentException,
            IndexOutOfBoundsException {
        checkSectorNum(sectorNum, 0, Disk.NUM_OF_SECTORS);
        checkBuffer(buffer, 1);

        try {
            lock.lock();

            if (!sectorWriteRecords.containsKey(sectorNum)) {
                sectorWriteRecords.put(sectorNum, new byte[Disk.SECTOR_SIZE]);
                sectorNumList.add(sectorNum);
            }
            System.arraycopy(buffer, 0, sectorWriteRecords.get(sectorNum), 0, Disk.SECTOR_SIZE);
        } finally {
            lock.unlock();
        }
    }

    //
    // Return true if this transaction has written the specified
    // sector; in that case update buffer[] with the written value.
    // Return false if this transaction has not written this sector.
    //
    public boolean checkRead(int sectorNum, byte buffer[])
            throws IllegalArgumentException,
            IndexOutOfBoundsException {
        checkSectorNum(sectorNum, 0, Disk.NUM_OF_SECTORS);
        checkBuffer(buffer, 1);

        boolean ret = false;

        try {
            lock.lock();
            if ((ret = sectorWriteRecords.containsKey(sectorNum))) {
                System.arraycopy(sectorWriteRecords.get(sectorNum), 0, buffer, 0, Disk.SECTOR_SIZE);
            }
        } finally {
            lock.unlock();
        }

        return ret;
    }

    public void commit()
            throws IOException, IllegalArgumentException {
    }

    public void abort()
            throws IOException, IllegalArgumentException {
    }

    //
    // These methods help get a transaction from memory to
    // the log (on commit), get a committed transaction's writes
    // (for writeback), and get a transaction from the log
    // to memory (for recovery).
    //

    //
    // For a committed transaction, return a byte
    // array that can be written to some number
    // of sectors in the log in order to place
    // this transaction on disk. Note that the
    // first sector is the header, which lists
    // which sectors the transaction updaets
    // and the last sector is the commit.
    // The k sectors between should contain
    // the k writes by this transaction.
    //
    // 个人理解是构造[meta header [log sector]* commit sector]
    // 获得的结果可以通过parseLogBytes构造出当前事务
    public byte[] getSectorsForLog() {
        byte[] transLog = new byte[2 + logSectors];
        byte[] headerBuffer = new byte[Disk.SECTOR_SIZE];
        byte[] commitBuffer = new byte[Disk.SECTOR_SIZE];
        int offset = 0;;

        try {
            // 可重入锁
            lock.lock();

            // 写header
            writeHeader(headerBuffer);
            System.arraycopy(headerBuffer, 0, transLog, offset, Disk.SECTOR_SIZE);

            for (int sectorNum : sectorNumList) {
                offset += Disk.SECTOR_SIZE;
                System.arraycopy(sectorWriteRecords.get(sectorNum), 0, transLog, offset, Disk.SECTOR_SIZE);
            }

            // 写入commit sector
            // 具体需要保存什么未知，考虑后决定写入（事务id，状态，校验和）
            writeCommit(commitBuffer);
            offset += Disk.SECTOR_SIZE;
            assert logSectors == sectorNumList.size();
            assert offset == (1 + logSectors) * Disk.SECTOR_SIZE;
            System.arraycopy(headerBuffer, 0, transLog, offset, Disk.SECTOR_SIZE);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            transLog = null;
        } finally {
            lock.unlock();
        }

        return transLog;
    }

    //
    // You'll want to remember where a Transactions
    // log record ended up in the log so that
    // you can free this part of the log when
    // writeback is done.
    //
    public void rememberLogSectors(int start, int nSectors) {
        try {
            lock.lock();
            logStart = start;
            logSectors = nSectors;
        } finally {
            lock.unlock();
        }
    }

    public int recallLogSectorStart() {
        int start = -1;

        try {
            lock.lock();
            start = logStart;
        } finally {
            lock.unlock();
        }

        return start;
    }

    public int recallLogSectorNSectors() {
        int nSectors = -1;

        try {
            lock.lock();
            nSectors = logSectors;
        } finally {
            lock.unlock();
        }

        return nSectors;
    }

    //
    // For a committed transaction, return
    // the number of sectors that this
    // transaction updates. Used for writeback.
    //
    public int getNUpdatedSectors() {
        int totalSectors = 0;

        try {
            lock.lock();
            totalSectors = sectorWriteRecords.size();
            assert(totalSectors == sectorNumList.size());
        } finally {
            lock.unlock();
        }

        return totalSectors;
    }

    //
    // For a committed transaction, return
    // the sector number and body of the
    // ith sector to be updated. Return
    // a secNum and put the body of the
    // write in byte array. Used for writeback.
    //
    public int getUpdateI(int i, byte buffer[]) {
        checkBuffer(buffer, 1);

        try {
            lock.lock();

            if (i < 0 || i >= sectorNumList.size()) {
                i = -1;
            } else {
                i = sectorNumList.get(i);
                System.arraycopy(sectorWriteRecords.get(i), 0, buffer, 0, Disk.SECTOR_SIZE);
            }
        } finally {
            lock.unlock();
        }

        return i;
    }

    public TransID getTransID() {
        return transID;
    }

    private static void checkSectorNum(int sectorNum, int start, int end)
            throws IllegalArgumentException {
        if (sectorNum < start || sectorNum >= end) {
            throw new IndexOutOfBoundsException("Bad sector number");
        }
    }

    private static void checkBuffer(byte[] buffer, int nSectors)
            throws IllegalArgumentException {
        if (buffer == null || buffer.length != nSectors * Disk.SECTOR_SIZE) {
            throw new IllegalArgumentException("Bad buffer");
        }
    }

    //
    // Parse a sector from the log that *may*
    // be a transaction header. If so, return
    // the total number of sectors in the
    // log that should be read (including the
    // header and commit) to get the full transaction.
    //
    // 为了不修改返回值，把构造的header存入参数列表
    public static int parseHeader(byte buffer[], TransactionHeader[] headerList) {
        TransactionHeader header = null;
        int ret = -1;
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

            // 读取 TransID 对象 logStart, logSectors, status
            int id = byteBuffer.getInt();
            int logStart = byteBuffer.getInt();
            int logSectors = byteBuffer.getInt();
            int status = byteBuffer.getInt();

            // 读取 LinkedList<Integer> 对象
            LinkedList<Integer> secLinkedList = new LinkedList<>();
            int totSector = byteBuffer.getInt();
            for (int i = 0; i < totSector; ++i) {
                secLinkedList.add(byteBuffer.getInt());
            }
            ret = logSectors + 2;
            if (headerList != null && headerList.length > 0) {
                headerList[0] = new TransactionHeader(new TransID(id), logStart, logSectors, status, secLinkedList);
            }

            System.out.println("transID: " + id);
            System.out.print("sector number " + totSector + ": ");
            for (int i = 0; i < totSector; ++i) {
                System.out.print(secLinkedList.get(i) + " ");
            }
            System.out.println();
            System.out.println("start: " + logStart);
            System.out.println("log sectors: " + logSectors);
            System.out.println("status: " + status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public static int parseCommit(byte buffer[], long[] meta) {
        checkBuffer(buffer, 1);

        int ret = -1;
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

            // 读取 TransID 对象 logStart, logSectors, status
            long id = byteBuffer.getInt();
            long status = byteBuffer.getInt();
            long checkSum = byteBuffer.getLong();

            meta[0] = id;
            meta[1] = status;
            meta[2] = checkSum;

            ret = 1;
            System.out.println("transID: " + id);
            System.out.println("status: " + status);
            System.out.println("checksum: " + checkSum);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ret;
    }

    public int writeHeader(byte buffer[])
    throws IllegalArgumentException
     {
        checkBuffer(buffer, 1);

        // fill 0
        Arrays.fill(buffer, (byte)0);

        int ret = -1;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            lock.lock();

             ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.BIG_ENDIAN); // 根据需要选择字节序
            

            // 序列化 transID
            byteBuffer.putInt(transID.toInt()); // 再写入 transID 的数据


            // 序列化 logStart, logSectors, status
            byteBuffer.putInt(logStart);
            byteBuffer.putInt(logSectors);
            byteBuffer.putInt(status);

            byteBuffer.putInt(sectorNumList.size());
            for (int secnum : sectorNumList) {
                byteBuffer.putInt(secnum);
            }

            ret = 1;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return ret;
    }

    public int writeCommit(byte buffer[])
    throws IllegalArgumentException
     {
        checkBuffer(buffer, 1);

        // fill 0
        Arrays.fill(buffer, (byte)0);

        int ret = -1;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            lock.lock();

             ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.BIG_ENDIAN); // 根据需要选择字节序
            

            // 序列化 transID
            byteBuffer.putInt(transID.toInt()); // 再写入 transID 的数据
            byteBuffer.putInt(status);


            // 计算checksum写入
            checkSum = calculateCheckSum(sectorNumList, sectorWriteRecords);
            byteBuffer.putLong(checkSum);

            ret = 1;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
        return ret;
    }


    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public static Transaction parseLogBytes(byte buffer[]) {
        // 首先，验证其长度为sector size的整数倍
        if (buffer == null || buffer.length % Disk.SECTOR_SIZE != 0) {
            return null;
        }

        byte[] headerBuffer = new byte[Disk.SECTOR_SIZE];
        byte[] commitBuffer = new byte[Disk.SECTOR_SIZE];
        long[] meta = new long[3];
        TransactionHeader[] headerArr = new TransactionHeader[1];
        Transaction transaction = null;
        int offset = 0;
        int ret = 0;


        System.arraycopy(buffer, 0, headerBuffer, 0, Disk.SECTOR_SIZE);
        ret = parseHeader(headerBuffer, headerArr);
        if (ret * Disk.SECTOR_SIZE != buffer.length) {
            return null;
        }

        try {
            // 读取commit sector并验证
            System.arraycopy(buffer, (ret - 1) * Disk.SECTOR_SIZE, headerBuffer, 0, Disk.SECTOR_SIZE);
            if (parseCommit(commitBuffer, meta) != 1 || !(meta[0] == (long)headerArr[0].transID.toInt() && meta[1] == (long)headerArr[0].status)) {
                return transaction;
            }
            
            // 构造HashMap
            LinkedHashMap<Integer, byte[]> sectorWriteRecords = new LinkedHashMap<>();
            for (int sectorNum : headerArr[0].sectorNumList) {
                byte[] sector = new byte[Disk.SECTOR_SIZE];
                offset += Disk.SECTOR_SIZE;
                System.arraycopy(buffer, offset, sector, 0, Disk.SECTOR_SIZE);
                sectorWriteRecords.put(sectorNum, sector);
            }
            assert offset == buffer.length - Disk.SECTOR_SIZE;

            // 计算校验和
            long checkSum = calculateCheckSum(headerArr[0].sectorNumList, sectorWriteRecords);
            if (checkSum != meta[2]) {
                return transaction;
            }

            transaction = new Transaction(headerArr[0], sectorWriteRecords);
        } catch (Exception e) {
            e.printStackTrace();
            transaction = null;
        }

        return transaction;
    }


    private static long calculateCheckSum(LinkedList<Integer> sectorNumList,LinkedHashMap<Integer, byte[]> sectorWriteRecords ) {
        CRC32 crc32 = new CRC32();
        for (int sectorNum : sectorNumList) {
            checkBuffer(sectorWriteRecords.get(sectorNum), 1);
            crc32.update(sectorWriteRecords.get(sectorNum), 0, Disk.SECTOR_SIZE);
        }
        return crc32.getValue();
    }
}


class TransactionHeader {
    public final TransID transID;
    public SimpleLock lock;
    public int logStart;
    public int logSectors;
    public int status;
    public LinkedList<Integer> sectorNumList;

    TransactionHeader(TransID transID, int logStart, int logSectors, int status, LinkedList<Integer> sectorNumList) {
        this.transID = transID;
        this.logStart = logStart;
        this.logSectors = logSectors;
        this.status = status;
        this.sectorNumList = sectorNumList;
    }
}