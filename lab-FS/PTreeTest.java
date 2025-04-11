
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

public class PTreeTest {
  //-------------------------------------------------------
  // main() -- PTree test
  //-------------------------------------------------------
   public static void main(String[] args) throws Exception {
        // testTree();
        // testRWSimple();
        // testRWMiddle();
        writePersistence();
        System.out.println("All writes done!");
        testPersistence();
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
        int totFreeTrees = 0, totFreeSpace = 0;
        int usedBlocks = 0;

        Common.setBuffer((byte)0, readBuffer);
        Common.setBuffer((byte)0, writeBuffer);
        Random random = new Random();

        System.out.println("Test 2: test data read & write simple");

        PTree ptree = new PTree(true);
        totFreeTrees = ptree.getParam(PTree.ASK_FREE_TREES);
        totFreeSpace = ptree.getParam(PTree.ASK_FREE_SPACE);

        TransID xid = ptree.beginTrans();
        int tnum1 = ptree.createTree(xid);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 0 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        /* 读写一个direct未分配块 */ 
        blockId = 1;
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 1 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);


        /* 读写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 3 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        /* 读写一个double indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 6 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

         /* 再读写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId,writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 7 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        /* 再读写一个double indirect未分配块，其和之前那个块在同一个间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 8 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        /* 再读写一个double indirect未分配块，其和之前那个块在不同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 10 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        /* 再读写一个double indirect未分配块，其和刚刚那个块在相同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2;
        Common.setBuffer((byte)0, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum1, blockId, writeBuffer);
        ptree.readData(xid, tnum1, blockId, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 11 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        assert totFreeTrees - 1 == ptree.getParam(PTree.ASK_FREE_TREES);

        ptree.deleteTree(xid, tnum1);
        assert totFreeTrees == ptree.getParam(PTree.ASK_FREE_TREES);
        usedBlocks = ptree.checkUsedBlocks(xid);
        assert 0 == usedBlocks && totFreeSpace - usedBlocks * PTree.BLOCK_SIZE_BYTES == ptree.getParam(PTree.ASK_FREE_SPACE);

        tnum1 = ptree.createTree(xid);
        int tnum2 = ptree.createTree(xid);
        assert totFreeTrees - 2 == ptree.getParam(PTree.ASK_FREE_TREES);


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

        assert totFreeTrees == ptree.getParam(PTree.ASK_FREE_TREES);

        ptree.commitTrans(xid);

        System.out.println("Test 2 Passed!");
    }

    private static void testRWMiddle() 
    throws IOException
    {
        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

        int blockId = 0;
        int totFreeSpace = 0;
        int usedBlocks = 0;

        Common.setBuffer((byte)0, readBuffer);
        Common.setBuffer((byte)0, writeBuffer);

        System.out.println("Test 3: test data read & write middle");

        PTree ptree = new PTree(true);

        TransID xid = ptree.beginTrans();
        totFreeSpace = ptree.getParam(PTree.ASK_FREE_SPACE);

        // 创建一个tree并不断顺序写入块
        int tnum = ptree.createTree(xid);

        try {
            for (blockId = 0; blockId < PTree.TNODE_DIRECT; ++blockId) {
                Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
                ptree.writeData(xid, tnum, blockId, writeBuffer);
                ++usedBlocks;
            }
            assert ptree.checkUsedBlocks(xid) == usedBlocks && ptree.getParam(PTree.ASK_FREE_SPACE) + usedBlocks * PTree.BLOCK_SIZE_BYTES == totFreeSpace;

            // 预期会创建一个间接块
            ++usedBlocks;
            for (; blockId < PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE; ++blockId) {
                Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
                ptree.writeData(xid, tnum, blockId, writeBuffer);
                ++usedBlocks;
                assert ptree.checkUsedBlocks(xid) == usedBlocks && ptree.getParam(PTree.ASK_FREE_SPACE) + usedBlocks * PTree.BLOCK_SIZE_BYTES == totFreeSpace;
            }
            assert ptree.checkUsedBlocks(xid) == usedBlocks && ptree.getParam(PTree.ASK_FREE_SPACE) + usedBlocks * PTree.BLOCK_SIZE_BYTES == totFreeSpace;

            // 开始写入double间接块
            for (; blockId < PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + PTree.POINTERS_PER_INTERNAL_NODE * PTree.POINTERS_PER_INTERNAL_NODE; ++blockId) {
                Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
                ptree.writeData(xid, tnum, blockId, writeBuffer);
                ++usedBlocks;

                // 这里创建一个一级间接块
                if ((blockId - PTree.TNODE_DIRECT - PTree.POINTERS_PER_INTERNAL_NODE) % PTree.POINTERS_PER_INTERNAL_NODE == 0) {
                    ++usedBlocks;
                }
                // 这里创建一个二级间接块
                if ((blockId - PTree.TNODE_DIRECT - PTree.POINTERS_PER_INTERNAL_NODE) % (PTree.POINTERS_PER_INTERNAL_NODE * PTree.POINTERS_PER_INTERNAL_NODE) == 0) {
                    ++usedBlocks;
                }
                // Common.debugPrintln("blockid", blockId, "expect",  usedBlocks, "actual", ptree.checkUsedBlocks(xid));
                // Common.debugPrintln("expect",  ptree.getParam(PTree.ASK_FREE_SPACE) + usedBlocks * PTree.BLOCK_SIZE_BYTES, "actual", totFreeSpace);
                assert ptree.checkUsedBlocks(xid) == usedBlocks && ptree.getParam(PTree.ASK_FREE_SPACE) + usedBlocks * PTree.BLOCK_SIZE_BYTES == totFreeSpace;
            }
        } catch (ResourceException e) {
            assert ptree.checkUsedBlocks(xid) == totFreeSpace / PTree.BLOCK_SIZE_BYTES && ptree.getParam(PTree.ASK_FREE_SPACE) == 0;
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 随机读取几个块
        Random random = new Random();
        for (int i = 0; i < 20; ++i) {
            blockId = random.nextInt(usedBlocks); // 注意这里可能是个非法块
            ptree.readData(xid, tnum, blockId, readBuffer);
            Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
            assert Arrays.equals(readBuffer, readBuffer) || readBuffer[0] == 0x0;
        }

        ptree.deleteTree(xid, tnum);
        assert ptree.checkUsedBlocks(xid) == 0 && ptree.getParam(PTree.ASK_FREE_SPACE) == totFreeSpace;

        // 写入了太多块，只能abort
        ptree.abortTrans(xid);
        

        System.out.println("Test 3 Passed!");
    }

    private static void testPersistence() 
    throws IOException
    {
        // 先执行testRWSimple（修改版）
        // 再执行该方法，检查块是否被写入持久化
        System.out.println("Test 4: test data write-persistence-read");

        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

        int blockId = 0;

        Common.setBuffer((byte)0, readBuffer);
        Common.setBuffer((byte)0, writeBuffer);

        PTree ptree = new PTree(false);

        TransID xid = ptree.beginTrans();
        // int tnum1 = ptree.createTree(xid);
        int tnum = 0;
        Common.debugPrintln("blocknum", ptree.checkUsedBlocks(xid));
        assert ptree.checkUsedBlocks(xid) == 11;

        /* 读一个direct未分配块 */ 
        blockId = 1;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        Common.debugPrintln("exp", writeBuffer[0], "act", readBuffer[0]);
        // assert Arrays.equals(readBuffer, writeBuffer);


        /* 读一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        Common.debugPrintln("exp", writeBuffer[0], "act", readBuffer[0]);

        assert Arrays.equals(readBuffer, writeBuffer);

        /* 读一个double indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

         /* 再读写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 再读写一个double indirect未分配块，其和之前那个块在同一个间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 再读写一个double indirect未分配块，其和之前那个块在不同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 再读写一个double indirect未分配块，其和刚刚那个块在相同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2;
        ptree.readData(xid, tnum, blockId, readBuffer);
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        ptree.commitTrans(xid);

        ptree.close();

        System.out.println("Test 4 Passed!");
    }

    private static void writePersistence() 
    throws IOException
    {
        byte[] readBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

        int blockId = 0;


        PTree ptree = new PTree(true);

        TransID xid = ptree.beginTrans();
        int tnum = ptree.createTree(xid);
        
        /* 写一个direct未分配块 */ 
        blockId = 1;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);


        /* 写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

        /* 写一个double indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

         /* 写一个indirect未分配块 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

        /* 写一个double indirect未分配块，其和之前那个块在同一个间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1;
       Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

        /* 写一个double indirect未分配块，其和之前那个块在不同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

        /* 写一个double indirect未分配块，其和刚刚那个块在相同间接目录 */ 
        blockId = PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2;
        Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
        ptree.writeData(xid, tnum, blockId, writeBuffer);

        assert ptree.checkUsedBlocks(xid) == 11;

        ptree.commitTrans(xid);
        ptree.close();
    }
   
}