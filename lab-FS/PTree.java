/*
 * PTree -- persistent tree
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.IOException;
import java.util.concurrent.locks.Condition;

public class PTree{
  public static final int METADATA_SIZE = 64;
  public static final int MAX_TREES = 512;
  public static final int MAX_BLOCK_ID = Integer.MAX_VALUE; 

  //
  // Arguments to getParam
  //
  public static final int ASK_FREE_SPACE = 997;
  public static final int ASK_MAX_TREES = 13425;
  public static final int ASK_FREE_TREES = 23421;

  //
  // TNode structure
  //
  public static final int TNODE_DIRECT = 8;
  public static final int TNODE_INDIRECT = 1;
  public static final int TNODE_DOUBLE_INDIRECT = 1;
  public static final int BLOCK_SIZE_BYTES = 1024;
  public static final int POINTERS_PER_INTERNAL_NODE = 256;

  // 自定义类元数据
  public static final int FIRST_AVAILABLE_SECTOR = ADisk.getFirstAvailableSector();
  public static final int FREE_MAP_SECTORS = 2;
  public static final int FREE_MAP_SECTOR_START = FIRST_AVAILABLE_SECTOR;
  public static final int TNODE_SIZE = 128;
  public static final int FREE_TNODE_MAP_SECTORS = ((MAX_TREES / (Disk.SECTOR_SIZE / TNODE_SIZE)) + (8 * Disk.SECTOR_SIZE) - 1) / (8 * Disk.SECTOR_SIZE);
  public static final int FREE_TNODE_MAP_SECTOR_START = FREE_MAP_SECTOR_START + FREE_MAP_SECTORS;
  public static final int TNODE_SECTORS = MAX_TREES / (Disk.SECTOR_SIZE / TNODE_SIZE);
  public static final int TNODE_SECTOR_START = FREE_TNODE_MAP_SECTOR_START + FREE_TNODE_MAP_SECTORS;

  // 数据成员
  private ADisk aDisk;
  private SimpleLock lock;
  private Condition newTransCond;
  private boolean noOutstandingTrans;
  


  public PTree(boolean doFormat)
  {
    aDisk = new ADisk(doFormat);
    lock = new SimpleLock();
    newTransCond = lock.newCondition();
    noOutstandingTrans = true;
  }

  public TransID beginTrans()
  {
    try {
      lock.lock();
      while (!noOutstandingTrans) {
        newTransCond.awaitUninterruptibly();
      }
      TransID transID = aDisk.beginTransaction();
      noOutstandingTrans = false;
      return transID;
    } finally {
      lock.unlock();
    }
  }

  public void commitTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
    try {
      lock.lock();
      aDisk.commitTransaction(xid);
      noOutstandingTrans = true;
      newTransCond.signal();
    } finally {
      lock.unlock();
    }
  }

  public void abortTrans(TransID xid) 
    throws IOException, IllegalArgumentException
  {
    try {
      lock.lock();
      aDisk.abortTransaction(xid);
      noOutstandingTrans = true;
      newTransCond.signal();
    } finally {
      lock.unlock();
    }
  }


  public int createTree(TransID xid) 
    throws IOException, IllegalArgumentException, ResourceException
  {
    return -1;
  }

  public void deleteTree(TransID xid, int tnum) 
    throws IOException, IllegalArgumentException
  {
  }

  public void getMaxDataBlockId(TransID xid, int tnum)
    throws IOException, IllegalArgumentException
  {
  }

  public void readData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }


  public void writeData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }

  public void readTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }


  public void writeTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }

  public int getParam(int param)
    throws IOException, IllegalArgumentException
  {
    if (param == ASK_FREE_SPACE) {
      return -1;
    } else if (param == ASK_FREE_TREES) {

    } else if (param == ASK_MAX_TREES) {

    } else {
      throw new IllegalArgumentException("Bad Param");
    }
  }

  
}
