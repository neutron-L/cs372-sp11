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
public class RFS implements AutoCloseable {
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
    // 创建事务
    TransID xid = flatFS.beginTrans();
    int inumber = create(xid, filename, RFSInode.FILE);

    if (inumber == -1) {
      Common.debugPrintln("create fail", filename);
      flatFS.abortTrans(xid);
      return -1;
    }
    Common.debugPrintln("create succ", filename);
    
    // 如果openIt则关联一个文件描述符
    if (openIt) {
      int fd = getDescriptor();
      if (fd != -1) {
        openFiles[fd] = new File(xid, inumber);
      }
      return fd;
    } else {
      flatFS.commitTrans(xid);
      return -1;
    }
  }

  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    // 创建事务
    TransID xid = flatFS.beginTrans();

    if (create(xid, dirname, RFSInode.DIRECTORY) == -1) {
      Common.debugPrintln("fail", dirname);
      flatFS.abortTrans(xid);
    } else {
      Common.debugPrintln("succ", dirname);
      flatFS.commitTrans(xid);
    }
    
    return;
  }


  public void unlink(String filename)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);

    String[] pathItems = parseFilename(filename);
    // 不支持删除根目录
    if (pathItems == null) {
      return;
    }
    
    int inumber = -1;
    int fatherInumber = -1;
    DirEnt dirEnt = null;

    // 创建事务
    TransID xid = flatFS.beginTrans();

    // 解析文件名，获取其父目录的inumber
    if ((fatherInumber = lookupFatherDir(xid, pathItems)) == -1) {
      return;
    }

    // - 查看是否存在文件
    if ((inumber = lookupDir(xid, fatherInumber, pathItems[pathItems.length - 1], RFSInode.ANY)) == -1) {
      return;
    }

    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    
    flatFS.readFileMetadata(xid, inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);

    // 非空目录不能删除
    if (inode.getFileType() == RFSInode.DIRECTORY && inode.getNextItemOffset() != 2 * DirEnt.DIR_ENT_META_SIZE) {
      flatFS.abortTrans(xid);
      return;
    } 

    flatFS.readFileMetadata(xid, fatherInumber, inodeBuffer);
    inode = RFSInode.parseInode(inodeBuffer);
    
    int i;
    for (i = 0; i < inode.getNextItemOffset(); i += DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
      assert dirEnt.isValid();
      byte[] dirEntNameBuffer = new byte[dirEnt.getNameLength()];
      flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      if (Common.byteArr2String(dirEntNameBuffer).equals(pathItems[pathItems.length - 1])) {
        break;
      }
    }

    assert i != inode.getNextItemOffset();
    flatFS.deleteFile(xid, inumber);

    // 更新父目录
    if (i < inode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, fatherInumber, inode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      flatFS.write(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, inodeBuffer);
    }
    if (inode.getHeapOffset() < dirEnt.getNameOffset()) {
      byte[] zeroBuffer = new byte[dirEnt.getNameLength()];
      flatFS.write(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), zeroBuffer);
    } else {
      inode.setHeapOffset(inode.getHeapOffset() + dirEnt.getNameLength());
    }
    inode.setNextItemOffset(inode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE);
    inode.writeInode(inodeBuffer);
    flatFS.writeFileMetadata(xid, fatherInumber, inodeBuffer);

    flatFS.commitTrans(xid);
    
    return;
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

    int fd = -1;
    int inumber = -1;

    // 创建事务
    TransID xid = flatFS.beginTrans();

    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在文件，非法参数
    if ((inumber = exist(xid, filename, RFSInode.FILE)) == -1) {
      flatFS.abortTrans(xid);
      return fd;
    }
    // 关联一个文件描述符
    if ((fd = getDescriptor()) == -1) {
      return -1;
    }
    openFiles[fd] = new File(xid, inumber);
    return fd;
  }


  public void close(int fd)
    throws IOException, IllegalArgumentException
  {
    checkFd(fd);
    flatFS.commitTrans(openFiles[fd].xid);
    putDescriptor(fd);
  }


  public int read(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    checkFd(fd);
    
    return flatFS.read(openFiles[fd].xid, openFiles[fd].inumber, offset, count, buffer);
  }


  public void write(int fd, int offset, int count, byte buffer[])
    throws IOException, IllegalArgumentException
  {
    checkFd(fd);
    
    flatFS.write(openFiles[fd].xid, openFiles[fd].inumber, offset, count, buffer);
  }

  public String[] readDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    checkFilename(dirname);

    int inumber = -1;

    // 创建事务
    TransID xid = flatFS.beginTrans();

    // 解析文件名，获取其父目录的inumber
    // - 查看是否存在文件，非法参数
    if ((inumber = exist(xid, dirname, RFSInode.DIRECTORY)) == -1) {
      flatFS.abortTrans(xid);
      return null;
    }

    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    flatFS.readFileMetadata(xid, inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);

    String[] result = new String[inode.getNextItemOffset() / DirEnt.DIR_ENT_META_SIZE];
    for (int i = 0; i < inode.getNextItemOffset(); i += DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, inumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      DirEnt dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
      assert dirEnt.isValid();
      byte[] dirEntNameBuffer = new byte[dirEnt.getNameLength()];
      flatFS.read(xid, inumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      result[i / DirEnt.DIR_ENT_META_SIZE] = Common.byteArr2String(dirEntNameBuffer);
    }
    flatFS.commitTrans(xid);

    return result;
  }

  public int size(int fd)
    throws IOException, IllegalArgumentException
  {
    checkFd(fd);
    
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    flatFS.readFileMetadata(openFiles[fd].xid, openFiles[fd].inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);

    return inode.getFileSize();
  }

  public int space(int fd)
    throws IOException, IllegalArgumentException
  {
    checkFd(fd);
    return flatFS.space(openFiles[fd].xid, openFiles[fd].inumber);
  }

  private void formatRFS() 
  throws IOException
  {
    assert openFiles[0] == null;
    TransID xid = flatFS.beginTrans();
    assert iroot == flatFS.createFile(xid);

    // 创建两个特殊条目.和..
    initDir(xid, iroot, iroot);
    flatFS.commitTrans(xid);
    openFiles[iroot] = new File(xid, iroot);
  }

  private void checkFilename(String filename) 
  throws IllegalArgumentException 
  {
    if (filename == null || filename.length() == 0 || filename.charAt(0) != '/') {
      throw new IllegalArgumentException("Bad filename");
    }
  }

  private int getDescriptor() {
    for (int i = 0; i < openFiles.length; ++i) {
      if (openFiles[i] == null) {
        return i;
      }
    }
    return -1;
  }

  private void putDescriptor(int fd) {
    checkFd(fd);
    openFiles[fd] = null;
  }


  private int exist(TransID xid, String filename, int expectedFileType) 
  throws IOException, IllegalArgumentException, EOFException
  {
    String[] pathItems = parseFilename(filename);
    if (pathItems == null) {
      return iroot;
    }
    int fatherInumber = lookupFatherDir(xid, pathItems);
    if (fatherInumber != -1) {
      return lookupDir(xid, fatherInumber, pathItems[pathItems.length - 1], expectedFileType);
    }
    return -1;
  }

  private int lookupFatherDir(TransID xid, String[] pathItems) 
  throws IOException, IllegalArgumentException, EOFException
  {
    int fatherInumber = iroot;
    for (int i = 0; i < pathItems.length - 1; ++i) {
      if (pathItems[i].isEmpty()) {
        continue;
      }
      if ((fatherInumber = lookupDir(xid, fatherInumber, pathItems[i], RFSInode.DIRECTORY)) == -1) {
        break;
      }
    }

    return fatherInumber;
  }

  private int lookupDir(TransID xid, int fatherInumber, String filename, int expectedFileType) 
  throws IOException, IllegalArgumentException, EOFException
  {
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    flatFS.readFileMetadata(xid, fatherInumber, inodeBuffer);

    int inumber = -1;

    RFSInode inode = RFSInode.parseInode(inodeBuffer);
    int nextItemOffset = inode.getNextItemOffset();

    byte[] buffer = new byte[nextItemOffset];
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    byte[] dirEntNameBuffer = new byte[DirEnt.MAX_NAME_LEN_CHAR];
    flatFS.read(xid, fatherInumber, 0, nextItemOffset, buffer);

    for (int i = 0; i < nextItemOffset; i += DirEnt.DIR_ENT_META_SIZE) {
      Common.setBuffer((byte)0, dirEntNameBuffer);
      System.arraycopy(buffer, i, dirEntBuffer, 0, DirEnt.DIR_ENT_META_SIZE);
      DirEnt dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
      assert dirEnt.isValid();
      flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      if (Common.byteArr2String(dirEntNameBuffer).equals(filename)) {
        inumber = dirEnt.getInum();
        flatFS.readFileMetadata(xid, inumber, inodeBuffer);
        inode = RFSInode.parseInode(inodeBuffer);
        if (inode.getFileType() == expectedFileType || expectedFileType == RFSInode.ANY) {
          break;
        }
      }
    }

    return inumber;
  }

  public int create(TransID xid, String filename, int fileType)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);
    int inumber = -1;

    byte[] fatherInodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];


    // 解析文件名，获取其父目录的inumber
    String[] pathItems = parseFilename(filename);
    if (pathItems == null) {
      throw new IllegalArgumentException("Bad filename");
    }

    int fatherInumber = lookupFatherDir(xid, pathItems);

    if (fatherInumber == -1) {
      return -1;
    }

    // - 查看是否存在文件，非法参数
    // 用ANY，则该目录下如果存在同名目录或文件直接失败
    if (lookupDir(xid, fatherInumber, pathItems[pathItems.length - 1], RFSInode.ANY) != -1) {
      return inumber;
    }
    // 创建文件
    if ((inumber = flatFS.createFile(xid)) == -1) {
      return inumber;
    }

   
    // 目录需要创建两个条目
    if (fileType == RFSInode.DIRECTORY) {
      initDir(xid, inumber, fatherInumber);
    } else {
      initFile(xid, inumber);
    }
    


    // 写入父目录
    flatFS.readFileMetadata(xid, fatherInumber, fatherInodeBuffer);
    RFSInode fatherInode = RFSInode.parseInode(fatherInodeBuffer);


    int nameOffset = fatherInode.getHeapOffset() - pathItems[pathItems.length - 1].length();
    int nameLength = pathItems[pathItems.length - 1].length();


    DirEnt dirEnt = new DirEnt(true, inumber, nameOffset, nameLength);
    dirEnt.writeDirEnt(dirEntBuffer);

    flatFS.write(xid, fatherInumber, nameOffset, nameLength, Common.String2byteArr(pathItems[pathItems.length - 1]));
    flatFS.write(xid, fatherInumber,fatherInode.getNextItemOffset(), DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);

    fatherInode.setHeapOffset(nameOffset);
    fatherInode.setNextItemOffset(fatherInode.getNextItemOffset() + DirEnt.DIR_ENT_META_SIZE);

    fatherInode.writeInode(fatherInodeBuffer);
    flatFS.writeFileMetadata(xid, fatherInumber, fatherInodeBuffer);
    

    return inumber;
  }

  private void initDir(TransID xid, int inumber, int fatherInumber) 
  throws IOException
  {
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    int offset = RFSInode.DIRECTORY_NAME_OFFSET;
    int itemOffset = 0;

    flatFS.readFileMetadata(xid, inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);
    inode.setFileType(RFSInode.DIRECTORY);


    Common.setBuffer((byte)0, dirEntBuffer);
    DirEnt dot = new DirEnt();
    DirEnt doubleDot = new DirEnt();

    dot.setValid(true);
    dot.setInum(inumber);
    dot.setNameLength(1);
    offset -= dot.getNameLength();
    dot.setNameOffset(offset);

    dot.writeDirEnt(dirEntBuffer);
    flatFS.write(xid, inumber, itemOffset, dirEntBuffer.length, dirEntBuffer);
    flatFS.write(xid, inumber, offset, dot.getNameLength(), Common.String2byteArr("."));
    itemOffset += DirEnt.DIR_ENT_META_SIZE;

    doubleDot.setValid(true);
    doubleDot.setInum(inumber);
    doubleDot.setNameLength(2);
    offset -= doubleDot.getNameLength();
    doubleDot.setNameOffset(offset);

    doubleDot.writeDirEnt(dirEntBuffer);
    flatFS.write(xid, inumber, itemOffset, dirEntBuffer.length, dirEntBuffer);
    flatFS.write(xid, inumber, offset, doubleDot.getNameLength(), Common.String2byteArr(".."));
    itemOffset += DirEnt.DIR_ENT_META_SIZE;

    inode.setNextItemOffset(itemOffset);
    inode.setHeapOffset(offset);
    inode.setFileSize(PTree.MAX_FILE_SIZE);
    inode.writeInode(inodeBuffer);
    flatFS.writeFileMetadata(xid, inumber, inodeBuffer);
  }

  private void initFile(TransID xid, int inumber) 
  throws IOException
  {
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];

    flatFS.readFileMetadata(xid, inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);
    inode.setFileType(RFSInode.FILE);

    inode.writeInode(inodeBuffer);
    flatFS.writeFileMetadata(xid, inumber, inodeBuffer);
  }

  private String[] parseFilename(String filename) {
    String[] pathItems = filename.split("/");

    int n = 0;
    for (int i = 0; i < pathItems.length; ++i) {
      if (!pathItems[i].isEmpty()) {
        ++n;
      }
    }

    if (n == 0) {
      return null;
    }
    String[] result = new String[n];
    int j = 0;
    for (int i = 0; i < pathItems.length; ++i) {
      if (!pathItems[i].isEmpty()) {
        result[j] = pathItems[i];
        ++j;
      }
    }

    return result;
  }

  private void checkFd(int fd)
  throws IllegalArgumentException
  {
    if (fd < 0 || fd >= Common.MAX_FD || openFiles[fd] == null) {
      throw new IllegalArgumentException("Bad fd");
    }
  }
  

  @Override
  public void close() {
    flatFS.close();
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
