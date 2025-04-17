import java.nio.ByteBuffer;
import java.nio.ByteOrder;


public class FlatFSInode extends Inode {
  private int fileSize;
  
  public static FlatFSInode parseInode(byte[] buffer) 
  throws IllegalArgumentException
  {
    FlatFSInode.checkBuffer(buffer);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    FlatFSInode inode = new FlatFSInode();
    inode.fileSize = byteBuffer.getInt();

    return inode;
  }

  public void setFileSize(int fileSize) {
    this.fileSize = fileSize;
  }

  public int getFileSize() {
    return fileSize;
  }

  @Override
  public void writeInode(byte[] buffer) 
  throws IllegalArgumentException
  {
    checkBuffer(buffer);

    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    byteBuffer.putInt(fileSize);
  }
}
