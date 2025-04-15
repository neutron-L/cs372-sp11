import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RFSInode extends Inode {
  // 用两个静态常量指代文件类型
  // 为了简单性，也可使用枚举类型
  public static final int FILE = 0;
  public static final int DIRECTORY = 1;
  public static final int DIRECTORY_NAME_OFFSET = PTree.MAX_FILE_SIZE;

  private int fileSize;
  private int fileType;
  // 如果文件类型是目录
  // 这两个值指向最后一个目录项的下一个位置以及存放文件名的下一个位置
  // 这两个值是相向增长的
  private int heapOffset;
  private int nextItemOffset;
  
  public static RFSInode parseInode(byte[] buffer) 
  throws IllegalArgumentException
  {
    checkBuffer(buffer);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    RFSInode inode = new RFSInode();
    inode.fileSize = byteBuffer.getInt();
    inode.fileType = byteBuffer.getInt();
    inode.heapOffset = byteBuffer.getInt();
    inode.nextItemOffset = byteBuffer.getInt();

    return inode;
  }

  public void setFileSize(int fileSize) {
    this.fileSize = fileSize;
  }

  public int getFileSize() {
    return fileSize;
  }

  public void setFileType(int fileType) {
    this.fileType = fileType;
  }

  public int getFileType() {
    return fileType;
  }

  public void setHeapOffset(int heapOffset) {
    this.heapOffset = heapOffset;
  }

  public int getHeapOffset() {
    return heapOffset;
  }

  public void setNextItemOffset(int nextItemOffset) {
    this.nextItemOffset = nextItemOffset;
  }

  public int getNextItemOffset() {
    return nextItemOffset;
  }

  @Override
  public void writeInode(byte[] buffer) 
  throws IllegalArgumentException
  {
    checkBuffer(buffer);

    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    byteBuffer.putInt(fileSize);
    byteBuffer.putInt(fileType);
    byteBuffer.putInt(heapOffset);
    byteBuffer.putInt(nextItemOffset);
  }
}
