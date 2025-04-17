
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;


public class RFSTest {
  //-------------------------------------------------------
  // main() -- RFS test
  //-------------------------------------------------------
   public static void main(String[] args) throws Exception {
        testFileOpenClose();
        testRWSimple();
        testRWMiddle();
        testRWComplex();
        testUnlinkRename();;
        testPersistence();
        System.out.println("All Tests Passed!");
        System.exit(0);
    }

    private static void testFileOpenClose() 
    throws IOException
    {
        System.out.println("Test 1: test file create & open & close");
        RFS rfs = new RFS(true);
        int fd = -1;

        fd = rfs.createFile("/a.txt", false);
        assert fd == -1;

        String[] result;
        fd = rfs.open("/a.txt");
        assert fd != -1 && rfs.space(fd) == 0;
        rfs.close(fd);

        fd = rfs.createFile("/b.txt", true);
        assert fd != -1 && rfs.space(fd) == 0;
        rfs.close(fd);

        assert -1 == rfs.createFile("/Downloads/c.txt", true);

        rfs.createDir("/Downloads");

        fd = rfs.createFile("/Downloads/c.txt", true);
        assert fd != -1 && rfs.space(fd) == 0;
        rfs.close(fd);

        fd = rfs.createFile("/Downloads/d.txt", true);
        assert fd != -1 && rfs.space(fd) == 0;
        rfs.close(fd);

        fd = rfs.createFile("/Downloads/e.txt", false);
        assert fd == -1;

        assert -1 == rfs.createFile("/Downloads/temp/c.txt", true);

        rfs.createDir("/Downloads/temp");

        fd = rfs.createFile("/Downloads/temp/c.txt", true);
        assert fd != -1 && rfs.space(fd) == 0;
        rfs.close(fd);

        // 在指定目录下创建一系列子目录并读取子目录名字
        rfs.createDir("/Downloads/temp/temp");
        String[] files = new String[5];
        for (int i = 0; i < 5; ++i) {
            files[i] = "file" + i;
            rfs.createFile("/Downloads/temp/temp/" + files[i], false);
        }
        result = rfs.readDir("/Downloads/temp/temp");
        assert result.length == files.length + 2;
        assert result[0].equals(".") && result[1].equals("..");
        for (int i = 0; i < files.length; ++i) {
            assert result[i + 2].equals(files[i]);
        }
        
        rfs.close();
        System.out.println("Test 1 Passed!");
    }

    private static void testRWSimple() 
    throws IOException
    {
        System.out.println("Test 2: test file data read & write simple");
        
        int fd = -1;
        int offset = 0;
        int count = 0;
        byte[] expectBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];
        byte[] writeBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];
        byte[] readBuffer = new byte[10 * PTree.BLOCK_SIZE_BYTES];

        RFS rfs = new RFS(true);
        assert (fd = rfs.createFile("/a.txt", true)) != -1;


        /* 检查方法是准备一块缓冲区，模拟期望的文件内容
          每次写入了文件的部分内容后，读取整个文件内容和
          修改后的缓冲区对比 */ 
        // 初始化文件
        Common.setBuffer((byte)0, expectBuffer);
        offset = 0;
        count = expectBuffer.length;
        rfs.write(fd, offset, count, expectBuffer);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, expectBuffer);

        // 一个块的读写
        offset = 2 * PTree.BLOCK_SIZE_BYTES;
        count = PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0xab, writeBuffer, count);
        
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 多个块的读写
        offset = 4 * PTree.BLOCK_SIZE_BYTES;
        count = 3 * PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0xcd, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 一个块内的读写 */ 
        // 左半边块写
        offset = 0;
        count = PTree.BLOCK_SIZE_BYTES / 2;
        Common.setBuffer((byte)0x10, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 右3/4块写
        offset = PTree.BLOCK_SIZE_BYTES / 4;
        count = PTree.BLOCK_SIZE_BYTES / 4 * 3;
        Common.setBuffer((byte)0x20, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 中间1/2块写
        offset = PTree.BLOCK_SIZE_BYTES;
        count = PTree.BLOCK_SIZE_BYTES / 2;
        Common.setBuffer((byte)0x30, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        /* 跨越块的读写 */ 
        // 中间没有块
        offset = 7 * PTree.BLOCK_SIZE_BYTES + PTree.BLOCK_SIZE_BYTES / 2;
        count = PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0x40, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 中间隔着一个块
        offset = 2 * PTree.BLOCK_SIZE_BYTES + PTree.BLOCK_SIZE_BYTES / 2;
        count = 2 * PTree.BLOCK_SIZE_BYTES;
        Common.setBuffer((byte)0x50, writeBuffer, count);
        rfs.write(fd, offset, count, writeBuffer);
        System.arraycopy(writeBuffer, 0, expectBuffer, offset, count);
        rfs.read(fd, offset, count, readBuffer);
        assert Arrays.equals(readBuffer, writeBuffer);

        // 检查文件整体内容
        offset = 0;
        count = expectBuffer.length;

        rfs.read(fd, offset, count, readBuffer);
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

        rfs.read(fd, base, count, expect);
        System.arraycopy(extendBuffer, 0, expect, offset - base, count);
        System.arraycopy(extendBuffer, 0, expectBuffer, offset, count / 2);

        rfs.write(fd, offset, count, extendBuffer);

        // flatFS.read(xid, inumber, offset, count, buffer);
        // assert Arrays.equals(buffer, extendBuffer);

        Common.setBuffer((byte)0xae, extendBuffer, count);
        System.arraycopy(extendBuffer, 0, expect, PTree.BLOCK_SIZE_BYTES, count);

        rfs.write(fd, 10 * PTree.BLOCK_SIZE_BYTES, count, extendBuffer);

        rfs.read(fd, base, 2 * PTree.BLOCK_SIZE_BYTES, buffer);

        assert Arrays.equals(buffer, expect);
        rfs.read(fd, 0, expectBuffer.length, readBuffer);
        assert Arrays.equals(readBuffer, expectBuffer);

        rfs.close();

        System.out.println("Test 2 Passed!");
    }

    /* 打开一个文件，在文件的最后依次写入指定长度的字符串（byte）并读取
     */
    private static void testRWMiddle() 
    throws IOException
    {
        System.out.println("Test 3: test file data read & write middle");
        RFS rfs = new RFS(true);

        int fd = rfs.createFile("/a.txt", true);

        LinkedList<String> strList = new LinkedList<>();
        strList.add("afewfwaefwa11324232.txt");
        strList.add("hello.txt");
        strList.add("world123.txt");
        strList.add("abc.txt");

        int offset = PTree.MAX_FILE_SIZE;

        String content = "";
        while (offset >= PTree.MAX_FILE_SIZE - 10 * PTree.BLOCK_SIZE_BYTES) {
            for (String file : strList) {
                byte[] buffer = Common.String2byteArr(file);
                offset -= buffer.length;
                rfs.write(fd, offset, buffer.length, buffer);
                content = file + content;
                // assert s.equals(str);
            }
        }
        byte[] buffer = new byte[PTree.MAX_FILE_SIZE - offset];
        rfs.read(fd, offset, PTree.MAX_FILE_SIZE - offset, buffer);
        assert Common.byteArr2String(buffer).equals(content);

        rfs.close();
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

        RFS rfs = new RFS(true);
        int fd = rfs.createFile("/a.txt", true);

        rfs.write(fd, 0, content.length(), Common.String2byteArr(content));

        byte[] buffer = new byte[content.length()];

        rfs.read(fd, 0, content.length(), buffer);

        String readStr = Common.byteArr2String(buffer);
        // Common.debugPrintln(readStr.length(), (content).length());
        assert readStr.length() == (content).length();
        assert readStr.equals(content);

        rfs.close();

        System.out.println("Test 4 Passed!");
    }


    private static void testUnlinkRename() 
    throws IOException
    {
        System.out.println("Test 5: test file unlink & rename");

        RFS rfs = new RFS(true);
        int fd = -1;
        String[] result;
        
        /* 删除文件 */ 
        fd = rfs.createFile("/a.txt", false);
        assert fd == -1;
        rfs.unlink("/a.txt");
        assert -1 == rfs.open("/a.txt");

        String dirname = "/temp";
        rfs.createDir(dirname);
        result = rfs.readDir("/");
        Common.debugPrintln(result.length);
        for (String i : result) {
            Common.debugPrintln(i);
        }
        String[] files = new String[5];
        for (int i = 0; i < 5; ++i) {
            files[i] = "file" + i;
            rfs.createFile(dirname + "/" + files[i], false);
        }

        for (int i = 0; i < 5; ++i) {
            assert -1 != (fd = rfs.open(dirname + "/" + files[i]));
            rfs.close(fd);
        }
        
        for (int i = 2; i >= 0; --i) {
            rfs.unlink(dirname + "/" + files[i]);  
            result = rfs.readDir(dirname);
        }
        for (int i = 3; i < 5; ++i) {
            rfs.unlink(dirname + "/" + files[i]);            
        }
        for (int i = 0; i < 5; ++i) {
            assert -1 == (fd = rfs.open(dirname + "/" + files[i]));
        }

        result = rfs.readDir(dirname);
        assert result.length == 2;
        assert result[0].equals(".") && result[1].equals("..");

        /* 删除目录 */
        rfs.unlink(dirname);
        assert null == rfs.readDir(dirname);
        assert 2 == rfs.readDir("/").length;

        rfs.createDir("/a");
        rfs.createDir("/a/b");
        rfs.unlink("/a");
        result = rfs.readDir("/a");
        assert result != null && result.length == 3;
        rfs.unlink("/a/b");
        assert rfs.readDir("/a/b") == null;
        result = rfs.readDir("/a");
        assert result != null && result.length == 2;
        rfs.unlink("/a");
        result = rfs.readDir("/a");
        assert result == null;

        /* 文件重命名 */ 

        rfs.close();
        System.out.println("Test 5 Passed!");
    }
   


    private static void testPersistence() 
    throws IOException
    {
        // 先执行writePersistence
        writePersistence();

        // 再执行该方法，检查块是否被写入持久化
        System.out.println("Test 6: test data write-persistence-read");

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

        RFS rfs = new RFS(false);
        int fd = rfs.open("/a.txt");
        assert fd != -1;

        for (int blockId : blockIds) {
            rfs.read(fd, blockId * PTree.BLOCK_SIZE_BYTES, PTree.BLOCK_SIZE_BYTES, readBuffer);
            Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
            assert Arrays.equals(readBuffer, writeBuffer);
        }

        rfs.close();

        System.out.println("Test 6 Passed!");
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

        RFS rfs = new RFS(true);
        int fd = rfs.createFile("/a.txt", true);
        for (int blockId : blockIds) {
            Common.setBuffer((byte)(blockId & 0xFF), writeBuffer);
            rfs.write(fd, blockId * PTree.BLOCK_SIZE_BYTES, PTree.BLOCK_SIZE_BYTES, writeBuffer);
        }
        rfs.close(fd);
        rfs.close();
    }

    
}