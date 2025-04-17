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
import java.util.Arrays;
import java.util.Stack;
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
    int fd = -1;
    // 创建事务
    TransID xid = flatFS.beginTrans();
    int inumber = create(xid, filename, RFSInode.FILE);

    if (inumber == -1) {
      flatFS.abortTrans(xid);
      return fd;
    }
    
    // 如果openIt则关联一个文件描述符
    if (openIt) {
      fd = getDescriptor();
      if (fd != -1) {
        openFiles[fd] = new File(xid, inumber);
      } 
    } else {
      flatFS.commitTrans(xid);
    }
    return fd;
  }

  public void createDir(String dirname)
    throws IOException, IllegalArgumentException
  {
    // 创建事务
    TransID xid = flatFS.beginTrans();

    if (create(xid, dirname, RFSInode.DIRECTORY) == -1) {
      flatFS.abortTrans(xid);
    } else {
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

    // 创建事务
    TransID xid = flatFS.beginTrans();

    // 解析文件名，获取其父目录的inumber
    if ((fatherInumber = lookupFatherDir(xid, pathItems)) == -1) {
      flatFS.abortTrans(xid);
      return;
    }

    // - 查看是否存在文件
    if ((inumber = lookupDir(xid, fatherInumber, pathItems[pathItems.length - 1], RFSInode.ANY)) == -1) {
      flatFS.abortTrans(xid);
      return;
    }

    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    
    flatFS.readFileMetadata(xid, inumber, inodeBuffer);
    RFSInode inode = RFSInode.parseInode(inodeBuffer);

    // 非空目录不能删除
    if (inode.getFileType() == RFSInode.DIRECTORY && inode.getNextItemOffset() != 2 * DirEnt.DIR_ENT_META_SIZE) {
      flatFS.abortTrans(xid);
      return;
    } 

    // 更新父目录
    assert inumber == clearDirEnt(xid, fatherInumber, pathItems[pathItems.length - 1]);
    flatFS.deleteFile(xid, inumber);
    flatFS.commitTrans(xid);
    
    return;
  }

  public void rename(String oldName, String newName)
    throws IOException, IllegalArgumentException
  {
    checkFilename(oldName);
    checkFilename(newName);

    int fatherInumber = -1;
    int stepfatherInumber = -1;

    String[] oldPathItems = parseFilename(oldName);
    String[] newPathItems = parseFilename(newName);

    if (Arrays.equals(oldPathItems, newPathItems)) {
      return;
    }

    String formerName = oldPathItems[oldPathItems.length - 1];
    String nowName = newPathItems[newPathItems.length - 1];

    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];

    RFSInode inode = null;

    // 创建事务
    TransID xid = flatFS.beginTrans();
    // 解析新旧文件名，获取其父目录的inumber
    if ((fatherInumber = lookupFatherDir(xid, oldPathItems)) == -1 || (stepfatherInumber = lookupFatherDir(xid, newPathItems)) == -1 ) {
      flatFS.abortTrans(xid);
      return;
    }
    // - 查看目标文件是否存在
    if (lookupDir(xid, stepfatherInumber, nowName, RFSInode.FILE) != -1) {
      flatFS.abortTrans(xid);
      return;
    }

    DirEnt dirEnt = null;
    flatFS.readFileMetadata(xid, fatherInumber, inodeBuffer);
    inode = RFSInode.parseInode(inodeBuffer);
    
    int i;
    for (i = 0; i < inode.getNextItemOffset(); i += DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
      assert dirEnt.isValid();
      byte[] dirEntNameBuffer = new byte[dirEnt.getNameLength()];
      flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      if (Common.byteArr2String(dirEntNameBuffer).equals(formerName)) {
        break;
      }
    }
    // - 查看源文件是否存在
    if (i == inode.getNextItemOffset()) {
      flatFS.abortTrans(xid);
      return;
    }
    // 没有移动到其他目录
    if (fatherInumber == stepfatherInumber) {
      // 原地修改文件名
      // 是否可以原地修改
      boolean flag = false;
      if (dirEnt.getNameOffset() + nowName.length() <= PTree.MAX_FILE_SIZE) {
        assert dirEnt.getNameLength() == formerName.length();
        byte[] nameBuffer = new byte[Math.max(formerName.length(), nowName.length())];
        Common.setBuffer((byte)0, nameBuffer);
        flatFS.write(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), nameBuffer);
        flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), nowName.length(), nameBuffer);

        int j = 0;
        while (j < nowName.length() && nameBuffer[j] == (byte)0) {
          ++j;
        }
        flag = j == nowName.length();
      }

      if (!flag) {
        inode.setHeapOffset(inode.getHeapOffset() - nowName.length());
        dirEnt.setNameOffset(inode.getHeapOffset());
      }
      dirEnt.setNameLength(nowName.length());
      flatFS.write(xid, fatherInumber, dirEnt.getNameOffset(), nowName.length(), Common.String2byteArr(nowName));
      dirEnt.writeDirEnt(dirEntBuffer);
      flatFS.write(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
    } else {
      // 删除目录项
      assert dirEnt.getInum() == clearDirEnt(xid, fatherInumber, formerName);
      // 在新的目录下添加目录项
      addDirEnt(xid, stepfatherInumber, nowName, dirEnt.getInum());
    }
    flatFS.commitTrans(xid);
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
      flatFS.abortTrans(xid);
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
      flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      if (Common.byteArr2String(dirEntNameBuffer).equals(filename)) {
        inumber = dirEnt.getInum();
        flatFS.readFileMetadata(xid, inumber, inodeBuffer);
        inode = RFSInode.parseInode(inodeBuffer);
        if (inode.getFileType() == expectedFileType || expectedFileType == RFSInode.ANY) {
          break;
        }
      }
      assert dirEnt.isValid();

    }

    return inumber;
  }

  public int create(TransID xid, String filename, int fileType)
    throws IOException, IllegalArgumentException
  {
    checkFilename(filename);
    int inumber = -1;

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
    addDirEnt(xid, fatherInumber, pathItems[pathItems.length - 1], inumber);
    
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
    doubleDot.setInum(fatherInumber);
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

  private int clearDirEnt(TransID xid, int fatherInumber, String filename)
  throws IOException, IllegalArgumentException
  {
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    byte[] inodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    int inumber = -1;
    RFSInode fatherInode = null;
    DirEnt dirEnt = null;

    flatFS.readFileMetadata(xid, fatherInumber, inodeBuffer);
    fatherInode = RFSInode.parseInode(inodeBuffer);
    
    int i;
    for (i = 0; i < fatherInode.getNextItemOffset(); i += DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
      assert dirEnt.isValid();
      byte[] dirEntNameBuffer = new byte[dirEnt.getNameLength()];
      flatFS.read(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), dirEntNameBuffer);
      if (Common.byteArr2String(dirEntNameBuffer).equals(filename)) {
        break;
      }
    }

    if (i == fatherInode.getNextItemOffset()) {
      return inumber;
    }
    inumber = dirEnt.getInum();

    // 更新父目录
    if (i < fatherInode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE) {
      flatFS.read(xid, fatherInumber, fatherInode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
      flatFS.write(xid, fatherInumber, i, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
    }
    if (fatherInode.getHeapOffset() < dirEnt.getNameOffset()) {
      byte[] zeroBuffer = new byte[dirEnt.getNameLength()];
      flatFS.write(xid, fatherInumber, dirEnt.getNameOffset(), dirEnt.getNameLength(), zeroBuffer);
    } else {
      fatherInode.setHeapOffset(fatherInode.getHeapOffset() + dirEnt.getNameLength());
    }
    fatherInode.setNextItemOffset(fatherInode.getNextItemOffset() - DirEnt.DIR_ENT_META_SIZE);
    fatherInode.writeInode(inodeBuffer);
    flatFS.writeFileMetadata(xid, fatherInumber, inodeBuffer);

    return inumber;
  }

  private void addDirEnt(TransID xid, int fatherInumber, String filename, int inumber)
  throws IOException, IllegalArgumentException
  {
    byte[] fatherInodeBuffer = new byte[flatFS.getParam(FlatFS.ASK_FILE_METADATA_SIZE)];
    byte[] dirEntBuffer = new byte[DirEnt.DIR_ENT_META_SIZE];
    byte[] temp = new byte[DirEnt.DIR_ENT_META_SIZE];
    DirEnt dirEnt = null;

    flatFS.readFileMetadata(xid, fatherInumber, fatherInodeBuffer);
    RFSInode fatherInode = RFSInode.parseInode(fatherInodeBuffer);
    int nameOffset = fatherInode.getHeapOffset() - filename.length();
    int nameLength = filename.length();

    flatFS.read(xid, fatherInumber, 0, DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
    dirEnt = DirEnt.parseDirEnt(dirEntBuffer);
    assert dirEnt.isValid();

    dirEnt = new DirEnt(true, inumber, nameOffset, nameLength);
    dirEnt.writeDirEnt(dirEntBuffer);

    flatFS.write(xid, fatherInumber, nameOffset, nameLength, Common.String2byteArr(filename));
    flatFS.write(xid, fatherInumber, fatherInode.getNextItemOffset(), DirEnt.DIR_ENT_META_SIZE, dirEntBuffer);
    
    fatherInode.setHeapOffset(nameOffset);
    fatherInode.setNextItemOffset(fatherInode.getNextItemOffset() + DirEnt.DIR_ENT_META_SIZE);

    fatherInode.writeInode(fatherInodeBuffer);
    flatFS.writeFileMetadata(xid, fatherInumber, fatherInodeBuffer);
  }

 private String[] parseFilename(String filename) 
 throws IllegalArgumentException 
 {
    if (filename == null) {
        throw new IllegalArgumentException("Filename bad");
    }

    // 使用 '/' 分割路径
    String[] pathItems = filename.split("/");

    Stack<String> stack = new Stack<>();

    for (String item : pathItems) {
        if (item.isEmpty() || item.equals(".")) {
            // 忽略空字符串和 "."
            continue;
        } else if (item.equals("..")) {
            // 处理 ".."，如果栈不为空则弹出
            if (!stack.isEmpty()) {
                stack.pop();
            } 
        } else if (item.length() > Common.FS_MAX_NAME) {
            throw new IllegalArgumentException("Bad filename");
        } else {
          // 添加普通目录或文件名到栈中
          stack.push(item);
        }
    }

    // 如果最终栈为空，返回 null
    if (stack.isEmpty()) {
        return null;
    }

    // 将栈中的元素转换为数组
    String[] result = new String[stack.size()];
    int i = stack.size() - 1;
    while (!stack.empty()) {
      result[i] = stack.pop();
      --i;
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
