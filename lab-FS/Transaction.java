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

public class Transaction{
    private static final int INPROGRESS = 0xaabb;
    private static final int COMMITTED = 0xaabc;
    private static final int ABORTED = 0xaabd;

    // 
    // You can modify and add to the interfaces
    //
    private final TransId transID;
    private SimpleLock lock;
    private HashMap<Integer, byte[]> sectorWriteRecords;
    private LinkedList<Integer> sectorNumList;
    private int logStart;
    private int logSectors;
    private int status;

    public Transaction() {
        transID = new TransID();
        lock = new SimpleLock();
        sectorWriteRecords = new HashMap<>();
        sectorNumList = new LinkedList<>();
    }

    public void addWrite(int sectorNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        checkSectorNum(sectorNum);
        checkBuffer(buffer);

        try {
            lock.lock();

            if (!sectorWriteRecords.containsKey(sectorNum)) {
                sectorWriteRecords.put(sectorNum, new byte[Disk.SECTOR_SIZE]);
                sectorNumList.add(sectorNum);
            }
            System.arraycopy(buffer, 0, sectorWriteRecords[sectorNum], 0, Disk.SECTOR_SIZE);
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
           IndexOutOfBoundsException
    {
        checkSectorNum(sectorNum);
        checkBuffer(buffer);

        boolean ret = false;

        try {
            lock.lock();
            if ((ret = sectorWriteRecords.containsKey(sectorNum))) {
                System.arraycopy(sectorWriteRecords[sectorNum], 0, buffer, 0, Disk.SECTOR_SIZE);
            }
        } finally {
            lock.unlock();
        }
        
        return ret;
    }


    public void commit()
    throws IOException, IllegalArgumentException
    {
    }

    public void abort()
    throws IOException, IllegalArgumentException
    {
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
    public byte[] getSectorsForLog(){
        return null;
    }

    //
    // You'll want to remember where a Transactions
    // log record ended up in the log so that
    // you can free this part of the log when
    // writeback is done.
    //
    public void rememberLogSectors(int start, int nSectors){
        try {
            lock.lock();
            logStart = start;
            logSectors = nSectors;
        } finally {
            lock.unlock();
        }
    }
    public int recallLogSectorStart(){
        int start = -1;

        try {
            lock.lock();
            start = logStart;
        } finally {
            lock.unlock();
        }

        return start;
    }
    public int recallLogSectorNSectors(){
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
    public int getNUpdatedSectors(){
        int totalSectors  = 0;

        try {
            lock.lock();
            totalSectors = sectorWriteRecords.size();
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
        try {
            lock.lock();

            if (i < 0 || i >= sectorNumList.length) {
                i = -1;
            } else {
                i = sectorNumList.get(i);
                System.arraycopy(sectorWriteRecords[i], 0, buffer, 0, Disk.SECTOR_SIZE);
            }
        } finally {
            lock.unlock();
        }
        
        return i;
    }

    public TransID getTransID() {
        return transID;
    }

    private void checkSectorNum(int sectorNum)
     throws IllegalArgumentException {
        if(sectorNum < 0 || sectorNum >= Disk.NUM_OF_SECTORS) {
            throw new IndexOutOfBoundsException("Bad sector number");
        }
     }

    private void checkBuffer(byte[] buffer)
     throws IllegalArgumentException {
        if(buffer == null || buffer.length != Disk.SECTOR_SIZE) {
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
        return -1;
    }

    //
    // Parse k+2 sectors from disk (header + k update sectors + commit).
    // If this is a committed transaction, construct a
    // committed transaction and return it. Otherwise
    // throw an exception or return null.
    public static Transaction parseLogBytes(byte buffer[]){
        return null;
    }
    
    
}

class TransLogHeader {
    private int logSectorNum;

}

