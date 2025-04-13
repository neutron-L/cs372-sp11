/*
 * NOTE: 
 * This file represents the constants and basic data struct to your 
 * file system.
 */

import java.io.UnsupportedEncodingException;

public class Common {

  public static final int ADISK_REDO_LOG_SECTORS = 1024;
  public static final int TREE_METADATA_SIZE = 32;

  public static final int MAX_TREES = 512;

  public static final int MAX_CONCURRENT_TRANSACTIONS = 8;
  public static final int MAX_WRITES_PER_TRANSACTION = 32;

  public static final int FS_MAX_NAME = 32;
  public static final int MAX_PATH = 128;

  public static final int MAX_FD = 32;

  public static final int READ = 0;
  public static final int WRITE = 1;

  // 自定义的常量和方法
  private static final boolean DEBUG = true;

  public static void checkSectorNum(int sectorNum, int start, int end)
      throws IndexOutOfBoundsException {
    if (sectorNum < start || sectorNum >= end) {
      throw new IndexOutOfBoundsException("Bad sector number");
    }
  }

  public static void checkBuffer(byte[] buffer, int nSectors)
      throws IllegalArgumentException {
    if (buffer == null || buffer.length != nSectors * Disk.SECTOR_SIZE) {
      throw new IllegalArgumentException("Bad buffer");
    }
  }

  public static void debugPrintln(Object... objects) {
    if (DEBUG) {
      for (Object obj : objects) {
        System.out.print(obj + " ");
      }
      System.out.println();
    }
  }

  public static void setBuffer(byte value, byte b[]) {
    int ii;
    for (ii = 0; ii < b.length; ii++) {
      b[ii] = value;
    }
    return;
  }

  public static void setBuffer(byte value, byte b[], int count) 
  throws IllegalArgumentException
  {
    if (count > b.length) {
      throw new IllegalArgumentException("Bad buffer");
    }
    int ii;
    for (ii = 0; ii < count; ii++) {
      b[ii] = value;
    }
    return;
  }

  // 使用utf-8字符集
  public static String byteArr2String(byte[] bytes) {
    if (bytes == null) {
      return null;
    }
    String str = null;
    
    try {
        // 指定 UTF-8 字符集将字节数组转换为字符串
        str = new String(bytes, "UTF-8"); 
        System.out.println(str);
    } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
        return null;
    } 

    return str;
  }

  // 使用utf-8字符集
  public static byte[] String2byteArr(String str) {
    byte[] bytes = null;

    if (str == null) {
      return bytes;
    }
    
    try {
        // 指定 UTF-8 字符集将字节数组转换为字符串
        bytes = str.getBytes("UTF-8"); 
    } catch (UnsupportedEncodingException e) {
        e.printStackTrace();
        return null;
    } 

    return bytes;
  }

}
