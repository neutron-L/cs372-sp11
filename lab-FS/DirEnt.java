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
public class DirEnt{
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
  /* 对于目录，记录开始存储文件名的偏移（初始化为文件偏移最大值）
   * 对于文件，为0，可以通过该字段判断文件类型
   * 对于特殊条目.和..，使用1和2表示
   */
  private int listOffset;
  //
  // Feel free to modify DirEnt as desired
  //
}
