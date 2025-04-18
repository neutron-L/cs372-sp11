
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;


public class FlatFSTest {
  //-------------------------------------------------------
  // main() -- FlatFS test
  //-------------------------------------------------------
   public static void main(String[] args) throws Exception {
        // testFlatFS();
        // testRWSimple();
        testRWMiddle();
        testRWComplex();
        // testPersistence();
        System.out.println("All Tests Passed!");
        System.exit(0);
    }

    private static void testFlatFS() 
    throws IOException
    {
        System.out.println("Test 1: test file create & delete");
        // PTree ptree = new PTree(true);
        FlatFS flatFS = new FlatFS(true);

        TransID xid = flatFS.beginTrans();
        int inumber1 = flatFS.createFile(xid);
        int inumber2 = flatFS.createFile(xid);
        flatFS.deleteFile(xid, inumber1);
        flatFS.deleteFile(xid, inumber2);

        flatFS.commitTrans(xid);
        flatFS.close();

        System.out.println("Test 1 Passed!");
    }

    private static void testRWSimple() 
    throws IOException
    {
        System.out.println("Test 2: test file data read & write simple");
        
        int offset = 0;
        int count = 0;
        byte[] expectBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];
        byte[] writeBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];

        FlatFS flatFS = new FlatFS(true);

        TransID xid = flatFS.beginTrans();
        int inumber = flatFS.createFile(xid);

        /* 检查方法是准备一块缓冲区，模拟期望的文件内容
          每次写入了文件的部分内容后，读取整个文件内容和
          修改后的缓冲区对比 */ 
        // 初始化文件
        Common.setBuffer((byte)0, expectBuffer);
        offset = 0;
        count = expectBuffer.length;
        flatFS.write(xid, inumber, offset, count, expectBuffer);
        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, expectBuffer);

        // 一个块的读写
        offset = 2 * PTree.BLOCK_SIZE_BYTES;
        count = PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0xab, writeBuffer, count);
        
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);


        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 多个块的读写
        offset = 4 * PTree.BLOCK_SIZE_BYTES;
        count = 3 * PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0xcd, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 一个块内的读写 */ 
        // 左半边块写
        offset = 0;
        count = PTree.BLOCK_SIZE_BYTES / 2;
        Common.setBuffer((byte)0x10, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 右3/4块写
        offset = PTree.BLOCK_SIZE_BYTES / 4;
        count = PTree.BLOCK_SIZE_BYTES / 4 * 3;
        Common.setBuffer((byte)0x20, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 中间1/2块写
        offset = PTree.BLOCK_SIZE_BYTES;
        count = PTree.BLOCK_SIZE_BYTES / 2;
        Common.setBuffer((byte)0x30, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 跨越块的读写 */ 
        // 中间没有块
        offset = 7 * PTree.BLOCK_SIZE_BYTES + PTree.BLOCK_SIZE_BYTES / 2;
        count = PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0x40, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 中间隔着一个块
        offset = 2 * PTree.BLOCK_SIZE_BYTES + PTree.BLOCK_SIZE_BYTES / 2;
        count = 2 * PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0x50, writeBuffer, count);
        flatFS.write(xid, inumber, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 检查文件整体内容
        offset = 0;
        count = expectBuffer.length;

        flatFS.read(xid, inumber, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, expectBuffer);

        // 写入导致文件extend
        byte[] expect = new byte[2 * PTree.BLOCK_SIZE_BYTES];
        byte[] extendBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] buffer = new byte[2 * PTree.BLOCK_SIZE_BYTES];

        int base = 9 * PTree.BLOCK_SIZE_BYTES;
        offset = base + PTree.BLOCK_SIZE_BYTES / 2;
        count = PTree.BLOCK_SIZE_BYTES;

        // 构造expect
        Common.setBuffer((byte)0, expect);
        Common.setBuffer((byte)0xea, extendBuffer, count);

        flatFS.read(xid, inumber, base, count, expect);
        System.arraycopy(extendBuffer, 0, expect, offset - base, count);
        System.arraycopy(extendBuffer, 0, expectBuffer, offset, count / 2);

        flatFS.write(xid, inumber, offset, count, extendBuffer);

        // flatFS.read(xid, inumber, offset, count, buffer);
        // assert Arrays.equals(buffer, extendBuffer);

        Common.setBuffer((byte)0xae, extendBuffer, count);
        System.arraycopy(extendBuffer, 0, expect, PTree.BLOCK_SIZE_BYTES, count);

        flatFS.write(xid, inumber, 10 * PTree.BLOCK_SIZE_BYTES, count, extendBuffer);

        flatFS.read(xid, inumber, base, 2 * PTree.BLOCK_SIZE_BYTES, buffer);

        assert Arrays.equals(buffer, expect);
        flatFS.read(xid, inumber, 0, expectBuffer.length, readBuffer);
        assert Arrays.equals(readBuffer, expectBuffer);

        flatFS.deleteFile(xid, inumber);
        flatFS.commitTrans(xid);
        flatFS.close();

        System.out.println("Test 2 Passed!");
    }

    /* 打开一个文件，在文件的最后依次写入指定长度的字符串（byte）并读取
     * RFS的目录项的文件名称会依据这种方式存储
     */
    private static void testRWMiddle() 
    throws IOException
    {
        System.out.println("Test 3: test file data read & write middle");
        FlatFS flatFS = new FlatFS(false);

        TransID xid = flatFS.beginTrans();
        int inumber = flatFS.createFile(xid);


        LinkedList<String> strList = new LinkedList<>();
        strList.add("afewfwaefwa11324232.txt");
        strList.add("hello.txt");
        strList.add("world123.txt");
        strList.add("abc.txt");

        int offset = (PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + PTree.POINTERS_PER_INTERNAL_NODE * PTree.POINTERS_PER_INTERNAL_NODE) * PTree.BLOCK_SIZE_BYTES;

        for (String file : strList) {
            byte[] buffer = Common.String2byteArr(file);
            offset -= buffer.length;
            flatFS.write(xid, inumber, offset, buffer.length, buffer);
            // assert s.equals(str);
        }

        // 反转链表
        java.util.Collections.reverse(strList);
        for (String file : strList) {
            byte[] buffer = new byte[file.length()];
            flatFS.read(xid, inumber, offset, file.length(), buffer);
            assert Common.byteArr2String(buffer).equals(file);
            offset += file.length();
        }
        assert offset == (PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + PTree.POINTERS_PER_INTERNAL_NODE * PTree.POINTERS_PER_INTERNAL_NODE) * PTree.BLOCK_SIZE_BYTES;
        flatFS.deleteFile(xid, inumber);

        flatFS.commitTrans(xid);
        flatFS.close();

        System.out.println("Test 3 Passed!");
    }

    /* 比较复杂的读写场景
     * 创建多个文件，我们多次写入（可能覆盖性）一些字符文本并读取
     * 更接近平时对文件系统功能的使用
     * 为了简单性使用utf-8字符集，因此文件名和文件内容不能包括中文
     */
    private static void testRWComplex() 
    throws IOException
    {
        System.out.println("Test 4: test file data read & write complex");

        String filePath = "example.txt";
        // String filePath = "ADisk.java";
        String content = null;

        try {
            byte[] bytes = Files.readAllBytes(Paths.get(filePath));
            content = new String(bytes);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        FlatFS flatFS = new FlatFS(false);

        TransID xid = flatFS.beginTrans();
        int inumber = flatFS.createFile(xid);

        flatFS.write(xid, inumber, 0, content.length(), Common.String2byteArr(content));

        byte[] buffer = new byte[content.length()];

        flatFS.read(xid, inumber, 0, content.length(), buffer);

        String readStr = Common.byteArr2String(buffer);
        // Common.debugPrintln(readStr.length(), (content).length());
        assert readStr.length() == (content).length();
        assert readStr.equals(content);

        flatFS.commitTrans(xid);
        flatFS.close();

        System.out.println("Test 4 Passed!");
    }

    private static void testPersistence() 
    throws IOException
    {
        // 先执行writePersistence
        // writePersistence();

        // 再执行该方法，检查块是否被写入持久化
        System.out.println("Test 5: test data write-persistence-read");

        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[PTree.BLOCK_SIZE_BYTES];

        LinkedList<Integer> blockIds = new LinkedList<>();

         
        blockIds.add(1);  // 写一个direct未分配块 
        blockIds.add(PTree.TNODE_DIRECT); //  写一个indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE);  // 写一个double indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1); // 写一个indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1); // 写一个double indirect未分配块，其和之前那个块在同一个间接目录
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE); // 写一个double indirect未分配块，其和之前那个块在不同间接目录
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2); // 写一个double indirect未分配块，其和刚刚那个块在相同间接目录

        FlatFS flatFS = new FlatFS(false);

        TransID xid = flatFS.beginTrans();
        // int inumber = flatFS.createFile(xid);
        int inumber = 0;

        for (int blockId : blockIds) {
            flatFS.read(xid, inumber, blockId * PTree.BLOCK_SIZE_BYTES, PTree.BLOCK_SIZE_BYTES, readBuffer);
            Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
            assert Arrays.equals(readBuffer, writeBuffer);
        }

        flatFS.commitTrans(xid);
        flatFS.close();

        System.out.println("Test 5 Passed!");
    }

    private static void writePersistence() 
    throws IOException
    {
        byte[] writeBuffer = new byte[PTree.BLOCK_SIZE_BYTES];
        LinkedList<Integer> blockIds = new LinkedList<>();

         
        blockIds.add(1);  // 写一个direct未分配块 
        blockIds.add(PTree.TNODE_DIRECT); //  写一个indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE);  // 写一个double indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE - 1); // 写一个indirect未分配块
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE +  PTree.POINTERS_PER_INTERNAL_NODE - 1); // 写一个double indirect未分配块，其和之前那个块在同一个间接目录
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE); // 写一个double indirect未分配块，其和之前那个块在不同间接目录
        blockIds.add(PTree.TNODE_DIRECT + PTree.POINTERS_PER_INTERNAL_NODE + 2 * PTree.POINTERS_PER_INTERNAL_NODE + 2); // 写一个double indirect未分配块，其和刚刚那个块在相同间接目录

        FlatFS flatFS = new FlatFS(true);

        TransID xid = flatFS.beginTrans();
        int inumber = flatFS.createFile(xid);

        for (int blockId : blockIds) {
            Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
            flatFS.write(xid, inumber, blockId * PTree.BLOCK_SIZE_BYTES, PTree.BLOCK_SIZE_BYTES, writeBuffer);
        }

        flatFS.commitTrans(xid);
        flatFS.close();
    }

   
   
}