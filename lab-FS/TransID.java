/*
 * TransId.java
 *
 * Interface to ADisk
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2007 Mike Dahlin
 *
 */

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicInteger;

public class TransID implements Serializable {

  //
  // Implement this class
  //
  private static final AtomicInteger nextTransID = new AtomicInteger(0);
  public int transID;


  public TransID()
  {
    transID = nextTransID.getAndIncrement();
  }

  public TransID(int id) {
    transID = id;
  }

  public int toInt() 
  {
    return transID;
  }

  // 重写 equals 方法
  @Override
  public boolean equals(Object obj) {
      // 检查是否为同一个引用
      if (this == obj) {
          return true;
      }
      // 检查对象是否为 null 或者类型不匹配
      if (obj == null || getClass() != obj.getClass()) {
          return false;
      }
      TransID other = (TransID) obj;
      return transID == other.transID;
  }

    
}
