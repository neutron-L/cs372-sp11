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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
  private int totAvailableSectors;
  private int totAvailableTrees;
  


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
    byte[] buffer = new byte[Disk.SECTOR_SIZE];
    int tnum = -1;

    try {
      lock.lock();

      // 读取TNode Map找到第一个未用过的tnum
      int b, bi;
      for (b = 0; b < TNODE_SIZE; ++b) {
        aDisk.readSector(xid, FREE_TNODE_MAP_SECTOR_START + (b / (8 * Disk.SECTOR_SIZE)), buffer);

        for (bi = 0; bi < Disk.SECTOR_SIZE * 8 && b + bi < TNODE_SIZE; ++bi) {
          tnum = b + bi;
          // 更新TNode Map
          if ((buffer[tnum / 8] & (1<<(tnum % 8))) == 0) {
            buffer[tnum / 8] |= 1<<(tnum % 8);
            aDisk.writeSector(xid, FREE_TNODE_MAP_SECTOR_START + (b / (8 * Disk.SECTOR_SIZE)), buffer);
            return tnum;
          }
        }
      }

    throw new ResourceException("No enough Tnode");
    } finally {
      lock.unlock();
    }
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
      return totAvailableSectors * Disk.SECTOR_SIZE;
    } else if (param == ASK_FREE_TREES) {
      return totAvailableTrees;
    } else if (param == ASK_MAX_TREES) {
      return MAX_TREES;
    } else {
      throw new IllegalArgumentException("Bad Param");
    }
  }

  
}

class TNode {
  public short[] data_block_direct;
  public short data_block_indirect;
  public short data_block_double_indirect;
  public byte[] tree_meta;

  public TNode() {
    data_block_direct = new short[PTree.TNODE_DIRECT];
    tree_meta = new byte[PTree.METADATA_SIZE];
  }

  public static TNode parseTNode(byte[] buffer) 
  throws IllegalArgumentException
  {
    TNode.checkBuffer(buffer);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    TNode tnode = new TNode();
    for (int i = 0; i < PTree.TNODE_DIRECT; ++i) {
      tnode.data_block_direct[i] = byteBuffer.getShort();
    }
    tnode.data_block_indirect = byteBuffer.getShort();
    tnode.data_block_double_indirect = byteBuffer.getShort();

    return tnode;
  }

  public void writeTNode(byte[] buffer) 
  throws IllegalArgumentException
  {
    TNode.checkBuffer(buffer);

    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    for (int i = 0; i < PTree.TNODE_DIRECT; ++i) {
      byteBuffer.putShort(data_block_direct[i]);
    }
    byteBuffer.putShort(data_block_indirect);
    byteBuffer.putShort(data_block_double_indirect);
  }

  private static void checkBuffer(byte[] buffer) 
  throws IllegalArgumentException
  {
    if (buffer == null || buffer.length != PTree.TNODE_SIZE) {
      throw new IllegalArgumentException("Bad Buffer");
    }
  }
}
