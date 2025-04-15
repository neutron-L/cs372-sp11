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
import java.io.EOFException;

/* 是PTree的包装，简单地将一个tree作为文件，文件inode == tnum
 * 可以定义一个保存文件inode信息的结构体，将其存储在tnode的meta中
 * 
 */
public class FlatFS implements AutoCloseable {

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
    fileMetaSize = PTree.METADATA_SIZE;
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
    int inumber = -1;
    // 新创建的file的所有数据都被置零，不需要额外处理
    try {
      inumber = ptree.createTree(xid);
    } catch (ResourceException e) {
      assert inumber == -1;
    }

    return inumber;
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
    FlatFSInode inode = FlatFSInode.parseInode(inodeBuffer);

    if (offset >= inode.getFileSize()) {
      throw new EOFException("Bad offset");
    }

    int tot = Math.min(count, inode.getFileSize() - offset);
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
    FlatFSInode inode = FlatFSInode.parseInode(inodeBuffer);

    int tot = count;
    int n = 0;
    int blockId = offset / PTree.BLOCK_SIZE_BYTES;
    int old_offset = offset;
    int num = 0;
    while (n < tot) {
      if (offset < inode.getFileSize() && (offset % PTree.BLOCK_SIZE_BYTES != 0 || tot - n < PTree.BLOCK_SIZE_BYTES)) {
        ptree.readData(xid, inumber, blockId, blockBuffer);
      }
      num = Math.min(tot - n, PTree.BLOCK_SIZE_BYTES - offset % PTree.BLOCK_SIZE_BYTES);
      System.arraycopy(buffer, n, blockBuffer, offset % PTree.BLOCK_SIZE_BYTES, num);
      ptree.writeData(xid, inumber, blockId, blockBuffer);
      n += num;
      offset += num;
      ++blockId;
    }
    offset = old_offset;

    if (offset + count > inode.getFileSize()) {
      inode.setFileSize(offset + count);
      inode.writeInode(inodeBuffer);
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

  public int space(TransID xid, int inumber)
    throws IOException, IllegalArgumentException
  {
    return ptree.getDataBlockCount(xid, inumber);
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
    

  @Override
  public void close() {
      ptree.close();
  }
  

}
