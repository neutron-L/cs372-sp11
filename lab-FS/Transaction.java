
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
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
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
    private LinkedList<Integer> sectorNumList;
    private LinkedHashMap<Integer, byte[]> sectorWriteRecords;
    private int logStart;
    private int logSectors;
    private int status;

    // 用于构造commit sector
    private long checkSum;

    public Transaction() {
        transID = new TransID();
        lock = new SimpleLock();
        sectorWriteRecords = new LinkedHashMap<>();
        sectorNumList = new LinkedList<>();
        status = INPROGRESS;
    }

    public Transaction(TransID transID, LinkedList<Integer> sectorNumList, 
    LinkedHashMap<Integer, byte[]> sectorWriteRecords, int logStart, int logSectors, int status) {
        this.transID = transID;
        this.lock = new SimpleLock();
        this.sectorNumList = sectorNumList;
        this.sectorWriteRecords = sectorWriteRecords;
        this.logStart = logStart;
        this.logSectors = logSectors;
        this.status = status;
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
        return null;
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

    private void checkSectorNum(int sectorNum, int start, int end)
            throws IllegalArgumentException {
        if (sectorNum < start || sectorNum >= end) {
            throw new IndexOutOfBoundsException("Bad sector number");
        }
    }

    private void checkBuffer(byte[] buffer, int nSectors)
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
    public static int parseHeader(byte buffer[]){
        int ret = -1;
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
            byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

            // 读取 TransID 对象
            // 读取 LinkedList<Integer> 对象
             // 读取 logStart, logSectors, status
            int id = byteBuffer.getInt();
             int logStart = byteBuffer.getInt();
             int logSectors = byteBuffer.getInt();
             int status = byteBuffer.getInt();

            ret = logSectors + 2;
            System.out.println("transID: " + id);
            System.out.println("start: " + logStart);
            System.out.println("log sectors: " + logSectors);
            System.out.println("status: " + status);
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

            // 如果需要序列化 sectorNumList，可以按如下方式（注意：这会改变数据结构）
            // 但为了与 parseHeader 方法匹配，这里暂时不序列化它
            /*
            byte[] listBytes = serializeList(sectorNumList);
            byteBuffer.putInt(listBytes.length);
            byteBuffer.put(listBytes);
            */

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
        // // 首先，验证其长度为sector size的整数倍
        // if (buffer == null || buffer.length % Disk.SECTOR_SIZE != 0) {
        //     return null;
        // }

        // try {
            
        //     ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
        //     ObjectInputStream ois = new ObjectInputStream(bis)

        //     // 读取 TransID 对象
        //     TransID transID = (TransID) ois.readObject();
        //     // 读取 LinkedList<Integer> 对象
        //     LinkedList<Integer> linkedList = (LinkedList<Integer>) ois.readObject();
        //     // 读取一个 int 值
        //     int start = ois.readInt();
        //     int totLogSectors = ois.readInt();
        //     int status = ois.readInt();

        //     ret = totLogSectors + 2;
        //     System.out.println("transID: " + transID.toInt());
        //     System.out.println("LinkedList<Integer> 对象: " + linkedList);
        //     System.out.println("start: " + start);
        //     System.out.println("log sectors: " + totLogSectors);
        //     System.out.println("status: " + status);
        // } catch (IOException | ClassNotFoundException e) {
        //     e.printStackTrace();
        // }

        // this.transID = transID;
        // this.lock = new SimpleLock();
        // this.sectorNumList = sectorNumList;
        // this.sectorWriteRecords = sectorWriteRecords;
        // this.logStart = logStart;
        // this.logSectors = logSectors;
        // this.status = status;

        return null;
    }

    public int writeLogBytes(byte buffers[][])
    throws IllegalArgumentException
    {
        int ret = logSectors + 2;

        if (status != COMMITTED) {
            return -1;
        }

        if (buffers.length != logSectors + 2) {
            throw new IllegalArgumentException("Bad buffers");
        }

        for (byte[] buffer : buffers) {
            checkBuffer(buffer, 1);
            // fill 0
            Arrays.fill(buffer, (byte)0);
        }


        try {
            lock.lock();

            // 写入header
            // 可重入锁
            writeHeader(buffers[0]);

            // 复制written sectors
            int index =1;
            for (int secNum : sectorNumList) {
                System.arraycopy(sectorWriteRecords.get(secNum), 0, buffers[index], 0, Disk.SECTOR_SIZE);
                ++index;
            }

            // 写入一个commit sector
            // 具体需要保存什么未知，考虑后决定写入（事务id，状态，校验和）

            // 计算指定范围内数据的 CRC32 校验和
            CRC32 crc32 = new CRC32();
            for (int i = 0; i <= logSectors; ++i) {
                crc32.update(buffers[i], 0, Disk.SECTOR_SIZE);
            }
            checkSum = crc32.getValue();

           
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);

            oos.writeObject(transID);
            oos.writeInt(status);
            oos.writeLong(checkSum);
            byte[] serializedBytes = bos.toByteArray();
            // 确保目标数组足够大
            if (buffers[buffers.length - 1].length < serializedBytes.length) {
                throw new IllegalArgumentException("目标数组长度不足以存储序列化后的对象。");
            }
            // 将序列化后的字节复制到目标数组
            System.arraycopy(serializedBytes, 0, buffers[buffers.length - 1], 0, serializedBytes.length);
        } catch (IOException e) {
            e.printStackTrace();
            ret = -1;
        } finally {
            lock.unlock();
        }

        return ret;
    }

}
