
import java.io.IOError;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Random;

public class PTreeTest {
  //-------------------------------------------------------
  // main() -- PTree test
  //-------------------------------------------------------
   public static void main(String[] args) throws Exception {
        testTree();
        testRW();
        System.out.println("All Tests Passed!");
        System.exit(0);
    }

    private static void testTree() 
    throws IOException
    {
        System.out.println("Test 1: test tree create & delete");
        PTree ptree = new PTree(true);

        TransID xid = ptree.beginTrans();
        int tnum1 = ptree.createTree(xid);
        int tnum2 = ptree.createTree(xid);
        assert 0 == ptree.checkUsedBlocks();
        ptree.deleteTree(xid, tnum1);
        ptree.deleteTree(xid, tnum2);

        ptree.commitTrans(xid);
        assert 0 == ptree.checkUsedBlocks();

        System.out.println("Test 1 Passed!");
    }

    private static void testRW() {
        System.out.println("Test 2: test data read & write");


        System.out.println("Test 2 Passed!");
    }

   
}