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
import java.util.Arrays;
import java.util.concurrent.locks.Condition;

import javax.crypto.IllegalBlockSizeException;

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
      for (b = 0; b < TNODE_SIZE; b += 8 * Disk.SECTOR_SIZE) {
        aDisk.readSector(xid, FREE_TNODE_MAP_SECTOR_START + (b / (8 * Disk.SECTOR_SIZE)), buffer);

        for (bi = 0; bi < Disk.SECTOR_SIZE * 8 && b + bi < TNODE_SIZE; ++bi) {
          tnum = b + bi;
          // 将TNode Map中tnum对应的bit位职位1
          if ((buffer[bi / 8] & (1<<(bi % 8))) == 0) {
            buffer[bi / 8] |= 1<<(bi % 8);
            aDisk.writeSector(xid, FREE_TNODE_MAP_SECTOR_START + (tnum / (8 * Disk.SECTOR_SIZE)), buffer);
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
    byte[] buffer = new byte[BLOCK_SIZE_BYTES];
    byte[] tnodeBuffer = new byte[TNODE_SIZE];

    try {
      lock.lock();

      // 读取TNode
      readTNode(xid, tnum, tnodeBuffer);
      // aDisk.readSector(xid, TNODE_SECTOR_START + tnum / (Disk.SECTOR_SIZE / TNODE_SIZE), tnodeBuffer);
      TNode tnode = TNode.parseTNode(tnodeBuffer);

      for (int i = 0; i < TNODE_DIRECT; ++i) {
        freeBlock(xid, tnode.data_block_direct[i]);
      }
      // 读取一级间接块
      if (tnode.data_block_indirect != 0) {
        readBlock(xid, tnode.data_block_indirect, buffer);
        for (int i = 0; i < POINTERS_PER_INTERNAL_NODE; i += 2) {
          int blockNum = (buffer[i + 1] << 8) | buffer[i];
          freeBlock(xid, blockNum);
        }
      }
      freeBlock(xid, tnode.data_block_indirect);

      // 读取二级间接块
      if (tnode.data_block_double_indirect != 0) {
        readBlock(xid, tnode.data_block_double_indirect, buffer);
        for (int i = 0; i < POINTERS_PER_INTERNAL_NODE; i += 2) {
          int block_indirect = (buffer[i + 1] << 8) | buffer[i];

          if (block_indirect != 0) {
            readBlock(xid, tnode.data_block_indirect, buffer);
            for (int j = 0; j < POINTERS_PER_INTERNAL_NODE; j += 2) {
              int blockNum = (buffer[j + 1] << 8) | buffer[j];
              freeBlock(xid, blockNum);
            }
          }

          freeBlock(xid, block_indirect);
        }
      }
      freeBlock(xid, tnode.data_block_double_indirect);

      // 释放TNode，更新Map中对应的位
      freeTNode(xid,tnum);
    } finally {
      lock.unlock();
    }
  }

  public void getMaxDataBlockId(TransID xid, int tnum)
    throws IOException, IllegalArgumentException
  {
    try {
      lock.lock();


    } finally {
      lock.unlock();
    }
  }

  public void readData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    byte[] tnodeBuffer = new byte[TNODE_SIZE];

    try {
      lock.lock();
      // 读取TNode 
      readTNode(xid, tnum, tnodeBuffer);
      // aDisk.readSector(xid, TNODE_SECTOR_START + tnum / (Disk.SECTOR_SIZE / TNODE_SIZE), tnodeBuffer);
      TNode tnode = TNode.parseTNode(tnodeBuffer);
      int blockNum = 0;

      if (blockId < TNODE_DIRECT) {
        blockNum = tnode.data_block_direct[blockId];
      } else if (blockId < TNODE_DIRECT + POINTERS_PER_INTERNAL_NODE) {
        blockId -= TNODE_DIRECT;
        readBlock(xid, tnode.data_block_indirect, buffer);
        blockNum = (buffer[2 * blockId + 1] << 8) | buffer[2 * blockId];
      } else if (blockId < TNODE_DIRECT + POINTERS_PER_INTERNAL_NODE + POINTERS_PER_INTERNAL_NODE * POINTERS_PER_INTERNAL_NODE) {
        int i, j;

        blockId -= TNODE_DIRECT + POINTERS_PER_INTERNAL_NODE;
        readBlock(xid, tnode.data_block_double_indirect, buffer);

        i = blockId / POINTERS_PER_INTERNAL_NODE;
        blockNum = (buffer[2 * i + 1] << 8) | buffer[2 * i];
        readBlock(xid, blockNum, buffer);
        j = blockId % POINTERS_PER_INTERNAL_NODE;
        blockNum = (buffer[2 * j + 1] << 8) | buffer[2 * j];
      } else {
        // 虽然最大合法的blockId定义是Int最大值
        // 但是实现上我们不支持
        throw new IllegalArgumentException("Bad blockId");
      }

      // 判断blockNum是否合法
      if (blockNum != 0) {
        readBlock(xid, blockNum, buffer);
      } else {
        Common.setBuffer((byte)0, buffer);
      }
    } finally {
      lock.unlock();
    }
    
  }


  public void writeData(TransID xid, int tnum, int blockId, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }

  public void readTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    if (buffer == null || buffer.length < METADATA_SIZE) {
      throw new IllegalArgumentException("Bad buffer");
    }
    byte[] tnodeBuffer = new byte[TNODE_SIZE];

    // 读取TNode
    readTNode(xid, tnum, tnodeBuffer);
    TNode tnode = TNode.parseTNode(tnodeBuffer);

    System.arraycopy(tnode.tree_meta, 0, buffer, 0, METADATA_SIZE);
  }


  public void writeTreeMetadata(TransID xid, int tnum, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    if (buffer == null || buffer.length < METADATA_SIZE) {
      throw new IllegalArgumentException("Bad buffer");
    }
    byte[] tnodeBuffer = new byte[TNODE_SIZE];

    // 读取TNode
    readTNode(xid, tnum, tnodeBuffer);
    TNode tnode = TNode.parseTNode(tnodeBuffer);

    System.arraycopy(buffer, 0, tnode.tree_meta, 0, METADATA_SIZE);
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


  /* 4个方法都需要在持有锁的情况下使用 */
  // 读取对应的TNode的字节数组
  private void readTNode(TransID xid, int tnum, byte[] tnodeBuffer) 
  throws IOException, IllegalArgumentException
  {
    if (tnodeBuffer == null || tnodeBuffer.length < TNODE_SIZE) {
      throw new IllegalArgumentException("Bad buffer");
    }

    byte[] buffer = new byte[Disk.SECTOR_SIZE];
    
    int sectorNum = TNODE_SECTOR_START + (tnum / (Disk.SECTOR_SIZE / TNODE_SIZE));
    aDisk.readSector(xid, sectorNum, buffer);
    System.arraycopy(buffer, (tnum % (Disk.SECTOR_SIZE / TNODE_SIZE)) * TNODE_SIZE, tnodeBuffer, 0, TNODE_SIZE);
  }

  // 根据块号读取数据
  // 不同于readData的blockId
  private void readBlock(TransID xid, int blockNum, byte[] buffer) 
  throws IOException, IllegalArgumentException
  {
    if (buffer == null || buffer.length != BLOCK_SIZE_BYTES) {
      throw new IllegalArgumentException("Bad buffer");
    }
    byte[] buffer1 = new byte[Disk.SECTOR_SIZE];
    byte[] buffer2 = new byte[Disk.SECTOR_SIZE];
    aDisk.readSector(xid, blockNum * 2, buffer1);
    aDisk.readSector(xid, blockNum * 2 + 1, buffer2);
    System.arraycopy(buffer1, 0, buffer, 0, Disk.SECTOR_SIZE);
    System.arraycopy(buffer2, 0, buffer, Disk.SECTOR_SIZE, Disk.SECTOR_SIZE);
  }

  private void freeTNode(TransID xid, int tnum) 
  throws IOException, IllegalArgumentException
  {
    byte[] buffer = new byte[Disk.SECTOR_SIZE];

    int sectorNum = FREE_TNODE_MAP_SECTOR_START + (tnum / (8 * Disk.SECTOR_SIZE));
    aDisk.readSector(xid, sectorNum, buffer);
    if ((buffer[(tnum % (8 * Disk.SECTOR_SIZE)) / 8] & (1 << ((tnum % (8 * Disk.SECTOR_SIZE)) % 8))) != 0) {
      throw new IllegalArgumentException("Bad tnum");
    }
    buffer[(tnum % (8 * Disk.SECTOR_SIZE)) / 8] &= ~(1 << ((tnum % (8 * Disk.SECTOR_SIZE)) % 8));
    assert (buffer[(tnum % (8 * Disk.SECTOR_SIZE)) / 8] & (1 << ((tnum % (8 * Disk.SECTOR_SIZE)) % 8))) == 0;
    aDisk.writeSector(xid, sectorNum, buffer);
  }

  private void freeBlock(TransID xid, int blockNum) 
  throws IOException, IllegalArgumentException
  {
    if (blockNum == 0) {
      return;
    }
    byte[] buffer = new byte[Disk.SECTOR_SIZE];

    int sectorNum = FREE_MAP_SECTOR_START + (blockNum / (8 * Disk.SECTOR_SIZE));
    aDisk.readSector(xid, sectorNum, buffer);
    if ((buffer[(blockNum % (8 * Disk.SECTOR_SIZE)) / 8] & (1 << ((blockNum % (8 * Disk.SECTOR_SIZE)) % 8))) != 0) {
      throw new IllegalArgumentException("Bad tnum");
    }
    buffer[(blockNum % (8 * Disk.SECTOR_SIZE)) / 8] &= ~(1 << ((blockNum % (8 * Disk.SECTOR_SIZE)) % 8));
    assert (buffer[(blockNum % (8 * Disk.SECTOR_SIZE)) / 8] & (1 << ((blockNum % (8 * Disk.SECTOR_SIZE)) % 8))) == 0;
    aDisk.writeSector(xid, sectorNum, buffer);
  }

  
}

class TNode {
  // 用0指代空指针
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
