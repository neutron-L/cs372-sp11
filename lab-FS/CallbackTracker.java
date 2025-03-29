/*
 * CallbackTracker.java
 *
 * Wait for a particular tag to finish...
 *
 * You must follow the coding standards distributed
 * on the class web page.
 *
 * (C) 2011 Mike Dahlin
 *
 */

import java.util.HashMap;
import java.util.Vector;
import java.util.Iterator;
import java.util.concurrent.locks.Condition;

public class CallbackTracker implements DiskCallback {
    private HashMap<Integer, DiskResult> doneTags;
    private SimpleLock lock;
    private Condition doneCond;

    public CallbackTracker() {
        doneTags = new HashMap<>();
        lock = new SimpleLock();
        doneCond = lock.newCondition();
    }

    @Override
    public void requestDone(DiskResult result) {
        // TBD
        try {
            lock.lock();
            doneTags.put(result.getTag(), result);
            doneCond.signalAll();
        } finally {
            lock.unlock();
        }
    }

    //
    // Wait for one tag to be done
    //
    public DiskResult waitForTag(int tag) {
        // TBD
        DiskResult result = null;

        try {
            lock.lock();
            while (!doneTags.containsKey(tag)) {
                doneCond.awaitUninterruptibly();
            }
            result = doneTags.remove(tag);
        } finally {
            lock.unlock();
        }
        return result;
    }

    //
    // Wait for a set of tags to be done
    //
    public Vector<DiskResult> waitForTags(Vector<Integer> tags) {
        // TBD
        Vector<DiskResult> resultVec = new Vector<>();

        try {
            lock.lock();
            while (tags.size() != 0) {
                doneCond.awaitUninterruptibly();
                Iterator<Integer> iterator = tags.iterator();
                while (iterator.hasNext()) {
                    Integer tag = iterator.next();
                    if (doneTags.containsKey(tag)) {
                        resultVec.add(doneTags.remove(tag));
                        iterator.remove();
                    }
                }
            }
        } finally {
            lock.unlock();
        }
        return resultVec;
    }

    //
    // To avoid memory leaks, need to tell CallbackTracker
    // if there are tags that we don't plan to wait for.
    // When these results arrive, drop them on the
    // floor.
    //
    public void dontWaitForTag(int tag) {
        // TBD
    }

    public void dontWaitForTags(Vector<Integer> tags) {
        // TBD
    }

}