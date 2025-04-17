
abstract public class Inode {
  public static Inode parseInode(byte[] buffer, Class<? extends Inode> type) {
    if (type == FlatFSInode.class) {
        return FlatFSInode.parseInode(buffer);
    } else if (type == RFSInode.class) {
        return RFSInode.parseInode(buffer);
    }
    throw new IllegalArgumentException("Unsupported Inode type");
}
  

  public abstract void writeInode(byte[] buffer) 
  throws IllegalArgumentException;


  protected static void checkBuffer(byte[] buffer) 
  throws IllegalArgumentException
  {
    if (buffer == null || buffer.length != PTree.METADATA_SIZE) {
      throw new IllegalArgumentException("Bad Buffer");
    }
  }

}
