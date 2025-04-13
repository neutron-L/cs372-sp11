/*
 * RFS -- reliable file system
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */
import java.io.IOException;
import java.io.EOFException;
public class RFS{
  private FlatFS flatFS;
  private final int iroot = 0;

  private File[] openFiles;

  public RFS(boolean doFormat)
    throws IOException
  {
    openFiles = new File[Common.MAX_FD];
    flatFS = new FlatFS(doFormat);
    if (doFormat) {
      formatRFS();
    }
  }

  public int createFile(String filename, boolean openIt)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);

    // 创建事务
    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在文件，非法参数
    // 创建文件
    // 写入父目录
    // 如果openIt则关联一个文件描述符
    return -1;
  }

  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    checkFilename(dirname);

    // 创建事务
    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在目录，非法参数
    // 创建目录
    // 写入父目录
    // 如果openIt则关联一个文件描述符
  }


  public void unlink(String filename)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);

    // 创建事务
    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在目录，非法参数
    // 修改父目录
  }

  public void rename(String oldName, String newName)
    throws IOException, IllegalArgumentException
  {
    checkFilename(oldName);
    checkFilename(newName);

    // 创建事务
    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在文件，非法参数
    // 更新父目录
  }


  public int open(String filename)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);

    // 创建事务
    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在文件，非法参数
    // 关联一个文件描述符
    return -1;
  }


  public void close(int fd)
    throws IOException, IllegalArgumentException
  {
  }


  public int read(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    return -1;
  }


  public void write(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
  }

  public String[] readDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    return null;
  }

  public int size(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }

  public int space(int fd)
    throws IOException, IllegalArgumentException
  {
    return -1;
  }

  private void formatRFS() 
  throws IOException
  {
    assert openFiles[0] == null;
    TransID xid = flatFS.beginTrans();
    assert iroot == flatFS.createFile(xid);
    openFiles[iroot] = new File(xid, iroot);
  }

  private void checkFilename(String filename) 
  throws IllegalArgumentException 
  {
    if (filename == null || filename.length() == 0 || filename.charAt(0) != '/') {
      throw new IllegalArgumentException("Bad filename");
    }
  }

}

/* 打开文件表 */
class File {
  public TransID xid;
  public int inumber;

  public File() {
    // 赋予一个非法transid，不能使用默认构造非法以防浪费业务id
    this.xid = new TransID(-1);
  }

  public File(TransID xid, int inumber) {
    this.xid = xid;
    this.inumber = inumber;
  }
}
