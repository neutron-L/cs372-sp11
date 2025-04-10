/*
 * FlatFS -- flat file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.IOException;
import java.net.FileNameMap;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.io.EOFException;
import java.io.File;

/* 是PTree的包装，简单地将一个tree作为文件，文件inode == tnum
 * 可以定义一个保存文件inode信息的结构体，将其存储在tnode的meta中
 * 
 */
public class FlatFS{

  public static final int ASK_MAX_FILE = 2423;
  public static final int ASK_FREE_SPACE_BLOCKS = 29542;
  public static final int ASK_FREE_FILES = 29545;
  public static final int ASK_FILE_METADATA_SIZE = 3502;

  private PTree ptree;
  private int fileMetaSize;

  public FlatFS(boolean doFormat)
    throws IOException
  {
    ptree = new PTree(doFormat);
    fileMetaSize = ptree.getParam(ASK_FILE_METADATA_SIZE);
  }

  public TransID beginTrans()
  {
    return ptree.beginTrans();
  }

  public void commitTrans(TransID xid)
    throws IOException, IllegalArgumentException
  {
    ptree.commitTrans(xid);
  }

  public void abortTrans(TransID xid)
    throws IOException, IllegalArgumentException
  {
    ptree.abortTrans(xid);
  }

  public int createFile(TransID xid)
    throws IOException, IllegalArgumentException
  {
    // 新创建的file的所有数据都被置零，不需要额外处理
    return ptree.createTree(xid);
  }

  public void deleteFile(TransID xid, int inumber)
    throws IOException, IllegalArgumentException
  {
    ptree.deleteTree(xid, inumber);
  }

  public int read(TransID xid, int inumber, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException, EOFException
  {
    byte[] blockBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

    // 检查参数
    if (offset < 0) {
      throw new IllegalArgumentException("Bad offset");
    }

    if (buffer == null || buffer.length < count) {
      throw new IllegalArgumentException("Bad buffer");
    }
    // 利用meta存储file size
    byte[] inodeBuffer = new byte[fileMetaSize];
    ptree.readTreeMetadata(xid, inumber, inodeBuffer);
    FileInode inode = FileInode.parseTNode(inodeBuffer);

    if (offset >= inode.fileSize) {
      throw new EOFException("Bad offset");
    }

    int tot = Math.min(count, inode.fileSize - offset);
    int n = 0;
    int blockId = offset / PTree.BLOCK_SIZE_BYTES;
    int num = 0;
    while (n < tot) {
      ptree.readData(xid, inumber, blockId, blockBuffer);
      offset %= PTree.BLOCK_SIZE_BYTES;
      num = Math.min(tot - n, PTree.BLOCK_SIZE_BYTES - offset);
      System.arraycopy(blockBuffer, offset, buffer, n, num);
      n += num;
      offset += num;
      ++blockId;
    }
    return n;
  }
    

  public void write(TransID xid, int inumber, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    // 利用meta读写file size
    byte[] blockBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

    // 检查参数
    if (offset < 0) {
      throw new IllegalArgumentException("Bad offset");
    }

    if (buffer == null || buffer.length < count) {
      throw new IllegalArgumentException("Bad buffer");
    }
    // 利用meta存储file size
    byte[] inodeBuffer = new byte[fileMetaSize];
    ptree.readTreeMetadata(xid, inumber, inodeBuffer);
    FileInode inode = FileInode.parseTNode(inodeBuffer);

    int tot = Math.min(count, inode.fileSize - offset);
    int n = 0;
    int blockId = offset / PTree.BLOCK_SIZE_BYTES;
    int old_offset = offset;
    int num = 0;
    while (n < tot) {
      offset %= PTree.BLOCK_SIZE_BYTES;
      if (offset != 0) {
        ptree.readData(xid, inumber, blockId, blockBuffer);
      }
      num = Math.min(tot - n, PTree.BLOCK_SIZE_BYTES - offset);
      System.arraycopy(buffer, n, blockBuffer, offset, num);
      ptree.writeData(xid, inumber, blockId, blockBuffer);
      n += num;
      offset += num;
      ++blockId;
    }
    offset = old_offset;

    if (offset + count > inode.fileSize) {
      inode.fileSize = offset + count;
      inode.writeFileInode(inodeBuffer);
      ptree.writeTreeMetadata(xid, inumber, inodeBuffer);
    }
  }

  public void readFileMetadata(TransID xid, int inumber, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    ptree.readTreeMetadata(xid, inumber, buffer);
  }


  public void writeFileMetadata(TransID xid, int inumber, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    ptree.writeTreeMetadata(xid, inumber, buffer);
  }

  public int getParam(int param)
    throws IOException, IllegalArgumentException
  {
    if (param == ASK_MAX_FILE ) {
      return ptree.getParam(PTree.ASK_MAX_TREES);
    } else if (param == ASK_FREE_SPACE_BLOCKS ) {
      return ptree.getParam(PTree.ASK_FREE_SPACE);
    } else if (param == ASK_FREE_FILES ) {
      return ptree.getParam(PTree.ASK_FREE_TREES);
    } else if (param == ASK_FILE_METADATA_SIZE) {
      return PTree.METADATA_SIZE;
    } else {
      throw new IllegalArgumentException("Bad Param");
    }
  }
    

  
  

}

class FileInode {
  public int fileSize;
  
  public static FileInode parseTNode(byte[] buffer) 
  throws IllegalArgumentException
  {
    FileInode.checkBuffer(buffer);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    FileInode inode = new FileInode();
    inode.fileSize = byteBuffer.getInt();

    return inode;
  }

  public void writeFileInode(byte[] buffer) 
  throws IllegalArgumentException
  {
    FileInode.checkBuffer(buffer);

    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    byteBuffer.putInt(fileSize);
  }

  private static void checkBuffer(byte[] buffer) 
  throws IllegalArgumentException
  {
    if (buffer == null || buffer.length != PTree.METADATA_SIZE) {
      throw new IllegalArgumentException("Bad Buffer");
    }
  }
}
