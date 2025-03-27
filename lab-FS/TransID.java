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

import java.util.concurrent.atomic.AtomicInteger;

public class TransID{

  //
  // Implement this class
  //
  private static final AtomicInteger nextTransID = new AtomicInteger(0);
  public int transID;


  public TransID()
  {
    transID = nextTransID.getAndIncrement();
  }

  
}
