/*
 * DirEnt -- fields of a directory entry. Feel free
 * to modify this class as desired.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DirEnt {
  // 每个目录项占据32个字节空间
  public final static int DIR_ENT_META_SIZE = 16;
  public final static int MAX_NAME_LEN_CHAR = 16;
  private boolean valid;
  private int inum;
  // private char name[] = new char[MAX_NAME_LEN_CHAR];
  /* 目录下的文件名保存在该目录空间的最后的块中
   * 并倒序排列，重命名的文件名如果较小或者可以容纳在原位置则原地修改
   * 否则存放在最前面
   * 删除的文件名位置清零
   */
  private int nameOffset;
  private int nameLength;
  //
  // Feel free to modify DirEnt as desired
  //

  // valid 字段的 get 和 set 方法
  public boolean isValid() {
      return valid;
  }

  public void setValid(boolean valid) {
      this.valid = valid;
  }

  // inum 字段的 get 和 set 方法
  public int getInum() {
      return inum;
  }

  public void setInum(int inum) {
      this.inum = inum;
  }

  // nameOffset 字段的 get 和 set 方法
  public int getNameOffset() {
      return nameOffset;
  }

  public void setNameOffset(int nameOffset) {
      this.nameOffset = nameOffset;
  }

  // nameLength 字段的 get 和 set 方法
  public int getNameLength() {
      return nameLength;
  }

  public void setNameLength(int nameLength) {
      this.nameLength = nameLength;
  }

  public static DirEnt parseDirEnt(byte[] buffer) 
  throws IllegalArgumentException
  {
    checkBuffer(buffer);
    
    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    DirEnt dirEnt = new DirEnt();
    dirEnt.valid = byteBuffer.getInt() == 1;
    dirEnt.inum = byteBuffer.getInt();
    dirEnt.nameOffset = byteBuffer.getInt();
    dirEnt.nameLength = byteBuffer.getInt();

    return dirEnt;
  }


  public void writeDirEnt(byte[] buffer) 
  throws IllegalArgumentException
  {
    checkBuffer(buffer);

    ByteBuffer byteBuffer = ByteBuffer.wrap(buffer);
    byteBuffer.order(ByteOrder.BIG_ENDIAN); // 与序列化时一致

    byteBuffer.putInt(valid ? 1 : 0);
    byteBuffer.putInt(inum);
    byteBuffer.putInt(nameOffset);
    byteBuffer.putInt(nameLength);
  }

  protected static void checkBuffer(byte[] buffer) 
  throws IllegalArgumentException
  {
    if (buffer == null || buffer.length != DIR_ENT_META_SIZE) {
      throw new IllegalArgumentException("Bad Buffer");
    }
  }
}    