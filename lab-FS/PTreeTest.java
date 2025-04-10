
import java.io.IOError;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class PTreeTest {
  //-------------------------------------------------------
  // main() -- PTree test
  //-------------------------------------------------------
   public static void main(String[] args) throws Exception {
        testTree();
        testRWSimple();
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
        assert 0 == ptree.checkUsedBlocks(xid);
        ptree.deleteTree(xid, tnum1);
        ptree.deleteTree(xid, tnum2);

        assert 0 == ptree.checkUsedBlocks(xid);
        ptree.commitTrans(xid);

        System.out.println("Test 1 Passed!");
    }

    private static void testRWSimple() 
    throws IOException
    {
        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

        byte[] meta1 = new byte[PTree.METADATA_SIZE];
        byte[] meta2 = new byte[PTree.METADATA_SIZE];
        byte[] readMeta = new byte[PTree.METADATA_SIZE];
        int blockId = 0;

        Common.setBuffer((byte)0, readBuffer);
        Common.setBuffer((byte)0, writeBuffer);
        Random random = new Random();

        System.out.println("Test 2: test data read & write simple");

        PTree ptree = new PTree(true);

        TransID xid = ptree.beginTrans();
        int tnum1 = ptree.createTree(xid);
        assert 0 == ptree.checkUsedBlocks(xid);

        /* 读写一个direct未分配块 */ 
        blockId = 1;
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.debugPrintln("null", ptree.checkUsedBlocks(xid));
        assert 1 == ptree.checkUsedBlocks(xid);


        /* 读写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 3 == ptree.checkUsedBlocks(xid);

        /* 读写一个double indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 6 == ptree.checkUsedBlocks(xid);

         /* 再读写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 7 == ptree.checkUsedBlocks(xid);

        /* 再读写一个double indirect未分配块，其和之前那个块在同一个间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 8 == ptree.checkUsedBlocks(xid);

        /* 再读写一个double indirect未分配块，其和之前那个块在不同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 10 == ptree.checkUsedBlocks(xid);

        /* 再读写一个double indirect未分配块，其和刚刚那个块在相同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        assert 11 == ptree.checkUsedBlocks(xid);

        ptree.deleteTree(xid, tnum1);
        assert 0 == ptree.checkUsedBlocks(xid);

        tnum1 = ptree.createTree(xid);
        int tnum2 = ptree.createTree(xid);

        /* 测试meta数据的读写 */ 
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), meta1);
        ptree.writeTreeMetadata(xid, tnum1, meta1);
        ptree.readTreeMetadata(xid, tnum1, readMeta);
        assert Arrays.equals(meta1, readMeta);
        Common.setBuffer((byte)random.nextInt(Byte.MAX_VALUE + 1), meta2);
        ptree.writeTreeMetadata(xid, tnum2, meta2);
        ptree.readTreeMetadata(xid, tnum2, readMeta);
        assert Arrays.equals(meta2, readMeta);

        ptree.deleteTree(xid, tnum1);
        ptree.deleteTree(xid, tnum2);
        assert 0 == ptree.checkUsedBlocks(xid);


        ptree.commitTrans(xid);

        System.out.println("Test 2 Passed!");
    }

   
}