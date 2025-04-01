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

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class ActiveTransactionList {

    /*
     * You can alter or add to these suggested methods.
     */
    private LinkedList<Transaction> transactions;

    private static final Logger LOGGER = Logger.getLogger(LogStatusTest.class.getName());

    public ActiveTransactionList() {
        transactions = new LinkedList<>();

        // 设置日志级别为 FINE，用于调试信息输出
        LOGGER.setLevel(Level.WARNING);

        // 添加控制台处理器
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINE);
        consoleHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(consoleHandler);
    }

    public void put(Transaction trans) {
        transactions.add(trans);
    }

    public Transaction get(TransID tid) {
        for (Transaction elem : transactions) {
            if (elem.getTransID().equals(tid)) {
                return elem;
            }
        }
        return null;
    }

    public Transaction remove(TransID tid) {
        Transaction trans = null;
        int index = 0;

        for (Transaction elem : transactions) {
            if (elem.getTransID().equals(tid)) {
                trans = elem;
                break;
            }
            ++index;
        }

        if (trans != null) {
            transactions.remove(index);
        }
        return trans;
    }
}