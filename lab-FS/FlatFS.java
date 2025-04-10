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

/* 是PTree的包装，简单地将一个tree作为文件，文件inode == tnum */
public class FlatFS{

  public static final int ASK_MAX_FILE = 2423;
  public static final int ASK_FREE_SPACE_BLOCKS = 29542;
  public static final int ASK_FREE_FILES = 29545;
  public static final int ASK_FILE_METADATA_SIZE = 3502;

  private PTree ptree;

  public FlatFS(boolean doFormat)
    throws IOException
  {
    ptree = new PTree(doFormat);
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
    return -1;
  }
    

  public void write(TransID xid, int inumber, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
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
