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
import java.util.concurrent.locks.Condition;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ActiveTransactionList{

    /*
     * You can alter or add to these suggested methods.
     */
    private LinkedList<Transaction> transactions;
    private SimpleLock lock;
    private Condition notFull;

    private static final Logger LOGGER = Logger.getLogger(LogStatusTest.class.getName());


    public ActiveTransactionList() {
        transactions = new LinkedList<>();
        lock = new SimpleLock();
        notFull = lock.newCondition();

         // 设置日志级别为 FINE，用于调试信息输出
         LOGGER.setLevel(Level.FINE);

         // 添加控制台处理器
         ConsoleHandler consoleHandler = new ConsoleHandler();
         consoleHandler.setLevel(Level.FINE);
         consoleHandler.setFormatter(new SimpleFormatter());
         LOGGER.addHandler(consoleHandler);
    }

    public void put(Transaction trans){
        try {
            lock.lock();
            while (transactions.size() == Common.MAX_CONCURRENT_TRANSACTIONS) {
                notFull.awaitUninterruptibly();;
            }
            transactions.add(trans);
        } finally {
            lock.unlock();
        }
        
        // System.exit(-1); // TBD
    }

    public Transaction get(TransID tid){
        Transaction trans = null;
        // LOGGER.fine(String.format(" try get tid = %d", tid.toInt()));

        try {
            lock.lock();
            for (Transaction elem : transactions) {
                if (elem.getTransID().equals(tid)) {
                    trans = elem;
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
        
        // System.exit(-1); // TBD
        return trans;
    }

    public Transaction remove(TransID tid){
        Transaction trans = null;
        int index = 0;

        try {
            lock.lock();
            for (Transaction elem : transactions) {
                if (elem.getTransID().equals(tid)) {
                    trans = elem;
                    break;
                }
                ++index;
            }

            if (trans != null) {
                transactions.remove(index);
                notFull.signalAll();
            }
        } finally {
            lock.unlock();
        }
        // System.exit(-1); // TBD
        return trans;
    }
}