/*
 * ActiveTransaction.java
 *
 * List of active transactions.
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */

import java.util.LinkedList;

public class ActiveTransactionList{

    /*
     * You can alter or add to these suggested methods.
     */
    private LinkedList<String> transactions = new LinkedList<>();
    private SimpleLock lock = new SimpleLock();

    public void put(Transaction trans){
        lock.lock();
        transactions.add(trans);
        lock.unlock();
        // System.exit(-1); // TBD
    }

    public Transaction get(TransID tid){
        Transaction trans = null;

        lock.lock();
        for (Transaction elem : transactions) {
            if (elem.getTransID() == tid) {
                trans = elem;
                break;
            }
        }
        lock.unlock();
        // System.exit(-1); // TBD
        return trans;
    }

    public Transaction remove(TransID tid){
        Transaction trans = null;
        int index = 0;

        lock.lock();
        for (Transaction elem : transactions) {
            if (elem.getTransID() == tid) {
                trans = elem;
                break;
            }
            ++index;
        }
        lock.unlock();
        // System.exit(-1); // TBD
        if (trans != null) {
            transactions.remove(index);
        }
        
        return trans;
    }


}