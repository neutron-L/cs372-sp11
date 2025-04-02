/*
 * WriteBackList.java
 *
 * List of commited transactions with pending writebacks.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */

 
import java.util.LinkedList;
import java.util.concurrent.locks.Condition;


// 必须是单个消费者
public class WriteBackList{

    // 
    // You can modify and add to the interfaces
    //
    private LinkedList<Transaction> transactions;

    public WriteBackList() {
        transactions = new LinkedList<>();
    }

    // Once a transaction is committed in the log,
    // move it from the ActiveTransactionList to 
    // the WriteBackList
    public void addCommitted(Transaction t){
        transactions.add(t);
    }

    //
    // A write-back thread should process
    // writebacks in FIFO order.
    //
    // NOTE: Don't remove the Transaction from
    // the list until the writeback is done
    // (reads need to see them)!
    //
    // NOTE: Service transactions in FIFO order
    // so that if there are multiple writes
    // to the same sector, the write that is
    // part of the last-committed transaction "wins".
    //
    // NOTE: you need to use log order for commit
    // order -- the transaction IDs are assigned
    // when transactions are created, so commit
    // order may not match transaction ID order.
    //    
    public Transaction getNextWriteback(){
        Transaction transaction = null;

        if (!transactions.isEmpty()) {
            transaction = transactions.peek();
        }

        return transaction;
    }

    //
    // Remove a transaction -- its writebacks
    // are now safely on disk.
    //
    public Transaction removeNextWriteback(){
        Transaction transaction = null;

        if (!transactions.isEmpty()) {
            transaction = transactions.remove();
        }

        return transaction;
    }

    //
    // Check to see if a sector has been written
    // by a committed transaction. If there
    // are multiple writes to the same sector,
    // be sure to return the last-committed write.
    //
    public boolean checkRead(int secNum, byte buffer[])
    throws IllegalArgumentException, 
           IndexOutOfBoundsException
    {
        Common.checkSectorNum(secNum, 0, Disk.NUM_OF_SECTORS);
        Common.checkBuffer(buffer, 1);
        boolean ret = false;

        for (int i = transactions.size() - 1; i >= 0; --i) {
            Transaction transaction = transactions.get(i);
            if ((ret = transaction.checkRead(secNum, buffer))) {
                break;
            }
        }

        return ret;
    }
}
