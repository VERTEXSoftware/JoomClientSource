package org.slvl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ManagerPack {

    private ManagerPack() {}

    public static final long  SLVL_MAGIC         = 0x4C564C5373696854L;
    public static final int   SLVL_VERSION_MAJOR = 1;
    public static final int   SLVL_VERSION_MINOR = 2;
    public static final int   SLVL_VERSION_BUGFIX = 0;
    public static final int   SLVL_VERSION_HOTFIX = 0;
    public static final int   SLVL_VERSION        = (SLVL_VERSION_MAJOR << 24) | (SLVL_VERSION_MINOR << 16) | (SLVL_VERSION_BUGFIX << 8) | SLVL_VERSION_HOTFIX;

    public static final int  SLVL_BUFFER_SIZE = 4096;
    public static final int  NODE_NAME_SIZE   = 512;
    public static final int  NODE_UNIT_SIZE   = 8 + 8 + 8 + NODE_NAME_SIZE; // 536
    public static final long MAX_FILE_SIZE     = 1073741824L; // 1GB

    private static final int  HEADER_SIZE = 8 + 4 + 2 + 8; // magic + version + fcount + crcTable
    private static final long MAP_CHUNK   = 1L << 30;      // размер сегмента mmap: 1 ГБ
    private static final int  PAGE_SIZE   = 4096;

    /** Аварийное отключение mmap: -Dslvl.nomap=true — чтение через FileChannel, как в старой версии. */
    private static boolean mmapEnabled() {
        return !"true".equalsIgnoreCase(System.getProperty("slvl.nomap"));
    }

    /** Отключение zero-copy: -Dslvl.zerocopy=false — loadFileFromMemory всегда возвращает копию. */
    private static boolean zerocopyEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("slvl.zerocopy"));
    }

    // Unsafe для принудительной выгрузки mmap при close() (JDK 9+: invokeCleaner)
    private static final Object UNSAFE;
    private static final Method INVOKE_CLEANER;
    static {
        Object u = null;
        Method ic = null;
        try {
            Class<?> cls = Class.forName("sun.misc.Unsafe");
            Field f = cls.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = f.get(null);
            ic = cls.getMethod("invokeCleaner", ByteBuffer.class);
            ic.setAccessible(true);
        } catch (Throwable t) {
            u = null;
            ic = null;
        }
        UNSAFE = u;
        INVOKE_CLEANER = ic;
    }

    private static void unmapQuietly(MappedByteBuffer m) {
        if (m == null) return;
        try {
            if (UNSAFE != null && INVOKE_CLEANER != null) {
                INVOKE_CLEANER.invoke(UNSAFE, m);
                return;
            }
        } catch (Throwable ignored) {}
        // Резервный путь для старых JDK (через sun.nio.ch.DirectBuffer)
        try {
            Object cleaner = Class.forName("sun.nio.ch.DirectBuffer").getMethod("cleaner").invoke(m);
            if (cleaner != null) cleaner.getClass().getMethod("clean").invoke(cleaner);
        } catch (Throwable ignored) {}
    }


    private static final long[] CRC64_TAB = {
            0x0000000000000000L, 0x7ad870c830358979L, 0xf5b0e190606b12f2L, 0x8f689158505e9b8bL,
            0xc038e5739841b68fL, 0xbae095bba8743ff6L, 0x358804e3f82aa47dL, 0x4f50742bc81f2d04L,
            0xab28ecb46814fe75L, 0xd1f09c7c5821770cL, 0x5e980d24087fec87L, 0x24407dec384a65feL,
            0x6b1009c7f05548faL, 0x11c8790fc060c183L, 0x9ea0e857903e5a08L, 0xe478989fa00bd371L,
            0x7d08ff3b88be6f81L, 0x07d08ff3b88be6f8L, 0x88b81eabe8d57d73L, 0xf2606e63d8e0f40aL,
            0xbd301a4810ffd90eL, 0xc7e86a8020ca5077L, 0x4880fbd87094cbfcL, 0x32588b1040a14285L,
            0xd620138fe0aa91f4L, 0xacf86347d09f188dL, 0x2390f21f80c18306L, 0x594882d7b0f40a7fL,
            0x1618f6fc78eb277bL, 0x6cc0863448deae02L, 0xe3a8176c18803589L, 0x997067a428b5bcf0L,
            0xfa11fe77117cdf02L, 0x80c98ebf2149567bL, 0x0fa11fe77117cdf0L, 0x75796f2f41224489L,
            0x3a291b04893d698dL, 0x40f16bccb908e0f4L, 0xcf99fa94e9567b7fL, 0xb5418a5cd963f206L,
            0x513912c379682177L, 0x2be1620b495da80eL, 0xa489f35319033385L, 0xde51839b2936bafcL,
            0x9101f7b0e12997f8L, 0xebd98778d11c1e81L, 0x64b116208142850aL, 0x1e6966e8b1770c73L,
            0x8719014c99c2b083L, 0xfdc17184a9f739faL, 0x72a9e0dcf9a9a271L, 0x08719014c99c2b08L,
            0x4721e43f0183060cL, 0x3df994f731b68f75L, 0xb29105af61e814feL, 0xc849756751dd9d87L,
            0x2c31edf8f1d64ef6L, 0x56e99d30c1e3c78fL, 0xd9810c6891bd5c04L, 0xa3597ca0a188d57dL,
            0xec09088b6997f879L, 0x96d1784359a27100L, 0x19b9e91b09fcea8bL, 0x636199d339c963f2L,
            0xdf7adabd7a6e2d6fL, 0xa5a2aa754a5ba416L, 0x2aca3b2d1a053f9dL, 0x50124be52a30b6e4L,
            0x1f423fcee22f9be0L, 0x659a4f06d21a1299L, 0xeaf2de5e82448912L, 0x902aae96b271006bL,
            0x74523609127ad31aL, 0x0e8a46c1224f5a63L, 0x81e2d7997211c1e8L, 0xfb3aa75142244891L,
            0xb46ad37a8a3b6595L, 0xceb2a3b2ba0eececL, 0x41da32eaea507767L, 0x3b024222da65fe1eL,
            0xa2722586f2d042eeL, 0xd8aa554ec2e5cb97L, 0x57c2c41692bb501cL, 0x2d1ab4dea28ed965L,
            0x624ac0f56a91f461L, 0x1892b03d5aa47d18L, 0x97fa21650afae693L, 0xed2251ad3acf6feaL,
            0x095ac9329ac4bc9bL, 0x7382b9faaaf135e2L, 0xfcea28a2faafae69L, 0x8632586aca9a2710L,
            0xc9622c4102850a14L, 0xb3ba5c8932b0836dL, 0x3cd2cdd162ee18e6L, 0x460abd1952db919fL,
            0x256b24ca6b12f26dL, 0x5fb354025b277b14L, 0xd0dbc55a0b79e09fL, 0xaa03b5923b4c69e6L,
            0xe553c1b9f35344e2L, 0x9f8bb171c366cd9bL, 0x10e3202993385610L, 0x6a3b50e1a30ddf69L,
            0x8e43c87e03060c18L, 0xf49bb8b633338561L, 0x7bf329ee636d1eeaL, 0x012b592653589793L,
            0x4e7b2d0d9b47ba97L, 0x34a35dc5ab7233eeL, 0xbbcbcc9dfb2ca865L, 0xc113bc55cb19211cL,
            0x5863dbf1e3ac9decL, 0x22bbab39d3991495L, 0xadd33a6183c78f1eL, 0xd70b4aa9b3f20667L,
            0x985b3e827bed2b63L, 0xe2834e4a4bd8a21aL, 0x6debdf121b863991L, 0x1733afda2bb3b0e8L,
            0xf34b37458bb86399L, 0x8993478dbb8deae0L, 0x06fbd6d5ebd3716bL, 0x7c23a61ddbe6f812L,
            0x3373d23613f9d516L, 0x49aba2fe23cc5c6fL, 0xc6c333a67392c7e4L, 0xbc1b436e43a74e9dL,
            0x95ac9329ac4bc9b5L, 0xef74e3e19c7e40ccL, 0x601c72b9cc20db47L, 0x1ac40271fc15523eL,
            0x5594765a340a7f3aL, 0x2f4c0692043ff643L, 0xa02497ca54616dc8L, 0xdafce7026454e4b1L,
            0x3e847f9dc45f37c0L, 0x445c0f55f46abeb9L, 0xcb349e0da4342532L, 0xb1eceec59401ac4bL,
            0xfebc9aee5c1e814fL, 0x8464ea266c2b0836L, 0x0b0c7b7e3c7593bdL, 0x71d40bb60c401ac4L,
            0xe8a46c1224f5a634L, 0x927c1cda14c02f4dL, 0x1d148d82449eb4c6L, 0x67ccfd4a74ab3dbfL,
            0x289c8961bcb410bbL, 0x5244f9a98c8199c2L, 0xdd2c68f1dcdf0249L, 0xa7f41839ecea8b30L,
            0x438c80a64ce15841L, 0x3954f06e7cd4d138L, 0xb63c61362c8a4ab3L, 0xcce411fe1cbfc3caL,
            0x83b465d5d4a0eeceL, 0xf96c151de49567b7L, 0x76048445b4cbfc3cL, 0x0cdcf48d84fe7545L,
            0x6fbd6d5ebd3716b7L, 0x15651d968d029fceL, 0x9a0d8ccedd5c0445L, 0xe0d5fc06ed698d3cL,
            0xaf85882d2576a038L, 0xd55df8e515432941L, 0x5a3569bd451db2caL, 0x20ed197575283bb3L,
            0xc49581ead523e8c2L, 0xbe4df122e51661bbL, 0x3125607ab548fa30L, 0x4bfd10b2857d7349L,
            0x04ad64994d625e4dL, 0x7e7514517d57d734L, 0xf11d85092d094cbfL, 0x8bc5f5c11d3cc5c6L,
            0x12b5926535897936L, 0x686de2ad05bcf04fL, 0xe70573f555e26bc4L, 0x9ddd033d65d7e2bdL,
            0xd28d7716adc8cfb9L, 0xa85507de9dfd46c0L, 0x273d9686cda3dd4bL, 0x5de5e64efd965432L,
            0xb99d7ed15d9d8743L, 0xc3450e196da80e3aL, 0x4c2d9f413df695b1L, 0x36f5ef890dc31cc8L,
            0x79a59ba2c5dc31ccL, 0x037deb6af5e9b8b5L, 0x8c157a32a5b7233eL, 0xf6cd0afa9582aa47L,
            0x4ad64994d625e4daL, 0x300e395ce6106da3L, 0xbf66a804b64ef628L, 0xc5bed8cc867b7f51L,
            0x8aeeace74e645255L, 0xf036dc2f7e51db2cL, 0x7f5e4d772e0f40a7L, 0x05863dbf1e3ac9deL,
            0xe1fea520be311aafL, 0x9b26d5e88e0493d6L, 0x144e44b0de5a085dL, 0x6e963478ee6f8124L,
            0x21c640532670ac20L, 0x5b1e309b16452559L, 0xd476a1c3461bbed2L, 0xaeaed10b762e37abL,
            0x37deb6af5e9b8b5bL, 0x4d06c6676eae0222L, 0xc26e573f3ef099a9L, 0xb8b627f70ec510d0L,
            0xf7e653dcc6da3dd4L, 0x8d3e2314f6efb4adL, 0x0256b24ca6b12f26L, 0x788ec2849684a65fL,
            0x9cf65a1b368f752eL, 0xe62e2ad306bafc57L, 0x6946bb8b56e467dcL, 0x139ecb4366d1eea5L,
            0x5ccebf68aecec3a1L, 0x2616cfa09efb4ad8L, 0xa97e5ef8cea5d153L, 0xd3a62e30fe90582aL,
            0xb0c7b7e3c7593bd8L, 0xca1fc72bf76cb2a1L, 0x45775673a732292aL, 0x3faf26bb9707a053L,
            0x70ff52905f188d57L, 0x0a2722586f2d042eL, 0x854fb3003f739fa5L, 0xff97c3c80f4616dcL,
            0x1bef5b57af4dc5adL, 0x61372b9f9f784cd4L, 0xee5fbac7cf26d75fL, 0x9487ca0fff135e26L,
            0xdbd7be24370c7322L, 0xa10fceec0739fa5bL, 0x2e675fb4576761d0L, 0x54bf2f7c6752e8a9L,
            0xcdcf48d84fe75459L, 0xb71738107fd2dd20L, 0x387fa9482f8c46abL, 0x42a7d9801fb9cfd2L,
            0x0df7adabd7a6e2d6L, 0x772fdd63e7936bafL, 0xf8474c3bb7cdf024L, 0x829f3cf387f8795dL,
            0x66e7a46c27f3aa2cL, 0x1c3fd4a417c62355L, 0x935745fc4798b8deL, 0xe98f353477ad31a7L,
            0xa6df411fbfb21ca3L, 0xdc0731d78f8795daL, 0x536fa08fdfd90e51L, 0x29b7d047efec8728L,
    };
    public static long crc64(long crc, byte[] buf, int off, int len) {
        for (int j = 0; j < len; j++) {
            int b = buf[off + j] & 0xFF;
            crc = CRC64_TAB[(int)(crc & 0xFF) ^ b] ^ (crc >>> 8);
        }
        return crc;
    }

    public static long crc64(long crc, ByteBuffer buf, int len) {
        int pos = buf.position();
        for (int j = 0; j < len; j++) {
            int b = buf.get() & 0xFF;
            crc = CRC64_TAB[(int)(crc & 0xFF) ^ b] ^ (crc >>> 8);
        }
        buf.position(pos + len);
        return crc;
    }

    ///////////////////////////////////////////////////////////////////////////
    // NodeFile / NodeTable
    ///////////////////////////////////////////////////////////////////////////

    public static final class NodeFile {
        public long offset;
        public long size;
        public long crc64;

        public NodeFile() {}
        public NodeFile(long o, long s, long c) { offset = o; size = s; crc64 = c; }
        @Override public String toString() {
            return String.format("NodeFile{off=%d, size=%d, crc=0x%016X}", offset, size, crc64);
        }
    }

    public static final class NodeTable {
        private final Map<String, NodeFile> ft = new LinkedHashMap<>();
        private String arch = "";
        private int    ver  = 0;

        // Удерживаемый канал файла для многократного чтения
        private FileChannel channel;

        // mmap-сегменты всего архива (null, если mmap недоступен/отключён)
        private MappedByteBuffer[] maps;
        private long mappedBytes;   // суммарный размер отображённой области
        boolean zeroCopyUsed;       // выдавались ли наружу zero-copy срезы

        public Map<String, NodeFile> getMap()             { return ft; }
        public boolean isExist(String path)              { return ft.containsKey(path); }
        public int     getCount()                         { return ft.size(); }
        public NodeFile getNode(String path)              { return ft.get(path); }
        public void    setNode(String name, NodeFile nd)  { ft.put(name, nd); }
        public void    setPathArch(String a)              { arch = a; }
        public void    setVerArch(int v)                  { ver  = v; }
        public String  getPathArch()                      { return arch; }
        public int     getVerArch()                       { return ver; }

        public FileChannel getChannel()                   { return channel; }
        public void    setChannel(FileChannel ch)         { this.channel = ch; }

        /** true, если архив отображён в память (mmap). */
        public boolean isMapped()                         { return maps != null; }

        /** Закрывает удерживаемый файл и освобождает память. Вызывать при выгрузке архива! */
        public void close() {
            if (maps != null) {
                if (!zeroCopyUsed) {
                    // zero-copy срезы наружу не выдавались — выгружаем отображение немедленно
                    for (MappedByteBuffer m : maps) unmapQuietly(m);
                }
                // иначе память освободит GC/Cleaner, когда станут недостижимы все выданные срезы
                maps = null;
                mappedBytes = 0L;
            }
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    System.err.println("Ошибка закрытия файла архива: " + e.getMessage());
                }
                channel = null;
            }
        }
    }

    public static final class MemoryFile {
        public ByteBuffer buffer;
        public int        size;
        public MemoryFile(ByteBuffer b, int s) { buffer = b; size = s; }
    }

    public static final class FileStream {
        public RandomAccessFile stream;
        public long             size;
    }

    ///////////////////////////////////////////////////////////////////////////
    // OpenPack
    ///////////////////////////////////////////////////////////////////////////

    public static boolean openPack(NodeTable tbl, String archPath) {
        RandomAccessFile raf = null;
        FileChannel ch = null;
        MappedByteBuffer[] maps = null;
        try {
            raf = new RandomAccessFile(archPath, "r");
            ch = raf.getChannel();

            long fileSize = raf.length();
            if (fileSize < HEADER_SIZE) { failOpen(ch, raf, null); return false; }

            // mmap: весь архив отображается в память (страницы подтягиваются лениво).
            // При сбое (нестандартная ФС, нехватка адресного пространства) автоматически
            // используется резервный путь через FileChannel.
            if (mmapEnabled()) maps = mapSegments(ch, fileSize);

            long mappedBytes = 0L;
            if (maps != null) for (MappedByteBuffer m : maps) mappedBytes += m.capacity();

            // ---- Заголовок ----
            long magic; int version; int fcount; long crcTable; ByteBuffer tb;

            if (maps != null) {
                ByteBuffer h = maps[0].duplicate();
                h.order(ByteOrder.LITTLE_ENDIAN);
                h.limit(HEADER_SIZE);
                magic = h.getLong(); version = h.getInt();
                fcount = h.getShort() & 0xFFFF; crcTable = h.getLong();

                long tableBytes = (long) NODE_UNIT_SIZE * fcount;
                if (fcount == 0 || HEADER_SIZE + tableBytes > mappedBytes) { failOpen(ch, raf, maps); return false; }

                // Таблица всегда лежит в начале файла => целиком в первом сегменте (<= 35 МБ)
                tb = maps[0].duplicate();
                tb.order(ByteOrder.LITTLE_ENDIAN);
                tb.position(HEADER_SIZE);
                tb.limit((int) (HEADER_SIZE + tableBytes));
            } else {
                ByteBuffer h = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
                readFully(ch, h); h.flip();
                magic = h.getLong(); version = h.getInt();
                fcount = h.getShort() & 0xFFFF; crcTable = h.getLong();

                long tableBytes = (long) NODE_UNIT_SIZE * fcount;
                if (fcount == 0 || tableBytes > MAX_FILE_SIZE) { failOpen(ch, raf, null); return false; }

                tb = ByteBuffer.allocate((int) tableBytes).order(ByteOrder.LITTLE_ENDIAN);
                readFully(ch, tb); tb.flip();
            }

            if (magic != SLVL_MAGIC || version != SLVL_VERSION) { failOpen(ch, raf, maps); return false; }

            // CRC считается прямо по буферу таблицы — без промежуточной копии raw[]
            if (crc64(0L, tb.duplicate(), tb.remaining()) != crcTable) { failOpen(ch, raf, maps); return false; }

            tbl.setPathArch(archPath);
            tbl.setVerArch(version);

            Map<String, NodeFile> ft = tbl.getMap();
            byte[] nameBuf = new byte[NODE_NAME_SIZE];

            for (int i = 0; i < fcount; i++) {
                long offset = tb.getLong();
                long size   = tb.getLong();
                long crc    = tb.getLong();
                tb.get(nameBuf);

                int n = 0;
                while (n < NODE_NAME_SIZE && nameBuf[n] != 0) n++;
                String name = (n == 0) ? ("unknown_file_" + i)
                        : new String(nameBuf, 0, n, StandardCharsets.UTF_8);

                if (offset > fileSize || size > fileSize || offset > fileSize - size) {
                    failOpen(ch, raf, maps);
                    return false;
                }
                ft.put(name, new NodeFile(offset, size, crc));
            }

            // Канал и mmap-сегменты остаются в таблице до close()
            tbl.setChannel(ch);
            tbl.maps         = maps;
            tbl.mappedBytes  = mappedBytes;
            tbl.zeroCopyUsed = false;
            return true;
        } catch (IOException e) {
            if (maps != null) for (MappedByteBuffer m : maps) unmapQuietly(m);
            closeQuietly(ch, raf);
            return false;
        }
    }

    private static MappedByteBuffer[] mapSegments(FileChannel ch, long size) {
        MappedByteBuffer[] maps = null;
        try {
            int n = (int) ((size + MAP_CHUNK - 1) / MAP_CHUNK);
            maps = new MappedByteBuffer[n];
            for (int i = 0; i < n; i++) {
                long pos = (long) i * MAP_CHUNK;
                long len = Math.min(MAP_CHUNK, size - pos);
                MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_ONLY, pos, len);
                m.order(ByteOrder.LITTLE_ENDIAN);
                maps[i] = m;
            }
            return maps;
        } catch (IOException | OutOfMemoryError e) {
            // mmap недоступен — работаем через FileChannel (как в старой версии)
            if (maps != null) for (MappedByteBuffer m : maps) if (m != null) unmapQuietly(m);
            return null;
        }
    }

    /** Чтение канала до полного заполнения буфера (Channel.read может прочитать часть). */
    private static void readFully(FileChannel ch, ByteBuffer dst) throws IOException {
        while (dst.hasRemaining()) {
            int n = ch.read(dst);
            if (n < 0) throw new IOException("Неожиданный конец файла архива");
        }
    }

    /** Копирование диапазона из mmap-сегментов (в т.ч. через границу сегментов). */
    private static void copyFromMaps(MappedByteBuffer[] maps, long offset, ByteBuffer dst, int size) {
        long pos = offset;
        int remaining = size;
        while (remaining > 0) {
            int seg = (int) (pos / MAP_CHUNK);
            MappedByteBuffer m = maps[seg];
            int inOff = (int) (pos - (long) seg * MAP_CHUNK);
            int can = (int) Math.min((long) remaining, (long) m.capacity() - inOff);
            ByteBuffer s = m.duplicate();
            s.position(inOff);
            s.limit(inOff + can);
            dst.put(s);
            pos += can;
            remaining -= can;
        }
    }

    private static void failOpen(FileChannel ch, RandomAccessFile raf, MappedByteBuffer[] maps) {
        if (maps != null) for (MappedByteBuffer m : maps) unmapQuietly(m);
        closeQuietly(ch, raf);
    }

    private static void closeQuietly(FileChannel ch, RandomAccessFile raf) {
        if (ch != null) { try { ch.close(); } catch (IOException ignored) {} }
        if (raf != null) { try { raf.close(); } catch (IOException ignored) {} }
    }

    ///////////////////////////////////////////////////////////////////////////
    // LoadFileStream (Оставлен для совместимости, если нужен потоковый доступ)
    ///////////////////////////////////////////////////////////////////////////

    public static boolean loadFileStream(NodeTable tbl, String file, FileStream out) {
        out.stream = null;
        out.size   = 0L;

        if (tbl.getCount() == 0) return false;

        NodeFile nd = tbl.getNode(file);
        if (nd == null || nd.crc64 == 0 || nd.size == 0) return false;

        try {
            RandomAccessFile raf = new RandomAccessFile(tbl.getPathArch(), "r");
            raf.seek(nd.offset);
            out.stream = raf;
            out.size   = nd.size;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // LoadFileFromMemory
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Загрузка файла из архива (БЫСТРЫЙ ПУТЬ).
     *
     * По умолчанию возвращает read-only срез mmap-отображения (zero-copy):
     *  - ноль аллокаций и копий — буфер готов мгновенно;
     *  - изменять буфер НЕЛЬЗЯ (put() бросит ReadOnlyBufferException);
     *  - буфер действителен, пока пак открыт (до NodeTable.close()).
     *
     * Копия возвращается автоматически, если: mmap отключён (-Dslvl.nomap=true),
     * mmap не удался, zero-copy отключён (-Dslvl.zerocopy=false) или файл
     * пересекает границу сегмента 1 ГБ.
     *
     * Если нужна изменяемая копия — используйте loadFileFromMemoryCopy.
     */
    public static boolean loadFileFromMemory(NodeTable tbl, String file, MemoryFile out) {
        if (tbl.getCount() == 0 || tbl.getChannel() == null) return false;

        NodeFile nd = tbl.getNode(file);
        if (nd == null || nd.crc64 == 0 || nd.size == 0) return false;

        if (zerocopyEnabled()) {
            ByteBuffer slice = zeroSlice(tbl, nd);
            if (slice != null) {
                tbl.zeroCopyUsed = true;
                out.buffer = slice;
                out.size   = (int) nd.size;
                return true;
            }
        }
        return loadFileFromMemoryCopy(tbl, file, out);
    }

    /** Read-only срез mmap-отображения или null, если файл нельзя отдать срезом. */
    private static ByteBuffer zeroSlice(NodeTable tbl, NodeFile nd) {
        MappedByteBuffer[] maps = tbl.maps;
        if (maps == null) return null;
        if (nd.size > (long) Integer.MAX_VALUE - 8) return null;

        long end = nd.offset + nd.size;
        if (end > tbl.mappedBytes) return null;

        int seg   = (int) (nd.offset / MAP_CHUNK);
        int inOff = (int) (nd.offset - (long) seg * MAP_CHUNK);
        if ((long) inOff + nd.size > maps[seg].capacity()) return null; // пересекает границу сегмента

        ByteBuffer ro = maps[seg].asReadOnlyBuffer();
        ro.position(inOff);
        ro.limit((int) (inOff + nd.size));
        ByteBuffer slice = ro.slice();
        slice.order(ByteOrder.LITTLE_ENDIAN);
        return slice;
    }

    /**
     * Загрузка с копированием в свежий direct-буфер (изменяемый).
     * Чтение идёт из mmap-отображения без системных вызовов; при отсутствии mmap —
     * позиционным чтением из канала (поведение старой версии).
     */
    public static boolean loadFileFromMemoryCopy(NodeTable tbl, String file, MemoryFile out) {
        if (tbl.getCount() == 0 || tbl.getChannel() == null) return false;

        NodeFile nd = tbl.getNode(file);
        if (nd == null || nd.crc64 == 0 || nd.size == 0) return false;

        if (nd.size >= MAX_FILE_SIZE) return false;

        int size = (int) nd.size;
        ByteBuffer buff = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);

        try {
            MappedByteBuffer[] maps = tbl.maps;
            if (maps != null && nd.offset + nd.size <= tbl.mappedBytes) {
                copyFromMaps(maps, nd.offset, buff, size);
            } else {
                tbl.getChannel().read(buff, nd.offset);
            }
            buff.flip();
            out.buffer = buff;
            out.size = size;
            return true;
        } catch (IOException e) {
            System.err.println("Ошибка чтения из архива: " + e.getMessage());
            return false;
        }
    }

    public static ByteBuffer loadFileFromMemory(NodeTable tbl, String file) {
        MemoryFile mf = new MemoryFile(null, 0);
        if (!loadFileFromMemory(tbl, file, mf)) return null;
        return mf.buffer;
    }

    public static ByteBuffer loadFileFromMemoryCopy(NodeTable tbl, String file) {
        MemoryFile mf = new MemoryFile(null, 0);
        if (!loadFileFromMemoryCopy(tbl, file, mf)) return null;
        return mf.buffer;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Прогрев
    ///////////////////////////////////////////////////////////////////////////

    /**
     * Прогрев одного файла в страничном кэше ОС (касание каждой страницы региона).
     * Потокобезопасно. Для массового прогрева используйте ResourceManager.preloadResources.
     */
    public static void warmUp(NodeTable tbl, String file) {
        NodeFile nd = tbl.getNode(file);
        if (nd == null || nd.size == 0) return;

        MappedByteBuffer[] maps = tbl.maps;
        try {
            if (maps != null && nd.offset + nd.size <= tbl.mappedBytes) {
                long pos = nd.offset & ~((long) PAGE_SIZE - 1);
                long end = nd.offset + nd.size;
                while (pos < end) {
                    int seg = (int) (pos / MAP_CHUNK);
                    int in  = (int) (pos - (long) seg * MAP_CHUNK);
                    maps[seg].get(in); // абсолютное чтение 1 байта = касание страницы
                    pos += PAGE_SIZE;
                }
            } else if (tbl.getChannel() != null) {
                ByteBuffer tmp = ByteBuffer.allocateDirect(PAGE_SIZE);
                tbl.getChannel().read(tmp, nd.offset);
            }
        } catch (Throwable ignored) {}
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetNodeTable
    ///////////////////////////////////////////////////////////////////////////

    public static List<String> getNodeTable(NodeTable tbl) {
        return getNodeTable(tbl, "");
    }

    public static List<String> getNodeTable(NodeTable tbl, String root) {
        List<String> list = new ArrayList<>();
        if (tbl.getCount() == 0) return list;

        String rootRaw = (root == null) ? "" : root;
        String norm = rootRaw.replace('\\', '/');
        while (!norm.isEmpty() && norm.charAt(0) == '/') {
            norm = norm.substring(1);
        }

        boolean all      = norm.isEmpty() && rootRaw.isEmpty();
        boolean rootOnly = norm.isEmpty() && !rootRaw.isEmpty();

        if (!norm.isEmpty() && norm.charAt(norm.length() - 1) != '/') {
            norm += '/';
        }

        for (Map.Entry<String, NodeFile> e : tbl.getMap().entrySet()) {
            String path = e.getKey().replace('\\', '/');
            if (all) {
                list.add(path);
            } else if (rootOnly) {
                if (path.indexOf('/') < 0) list.add(path);
            } else {
                if (path.startsWith(norm)) {
                    String sub = path.substring(norm.length());
                    if (sub.indexOf('/') < 0) list.add(sub);
                }
            }
        }
        return list;
    }

    ///////////////////////////////////////////////////////////////////////////
    // NodeTablePrint
    ///////////////////////////////////////////////////////////////////////////

    public static void nodeTablePrint(NodeTable tbl) {
        nodeTablePrint(tbl, "");
    }

    public static void nodeTablePrint(NodeTable tbl, String root) {
        System.out.println();
        System.out.println("[INFO]");

        if (tbl.getCount() == 0) {
            System.out.println("File archive: " + tbl.getPathArch() + " Empty");
            return;
        }

        List<String> list = getNodeTable(tbl, root);

        System.out.println("Archive file: " + tbl.getPathArch());
        if (root == null || root.isEmpty())
            System.out.println("Root: [ALL]");
        else
            System.out.println("Root: " + root);

        System.out.println("Count files: " + list.size());
        System.out.println("Mapped (mmap): " + tbl.isMapped());
        System.out.println();
        System.out.println("[START]");
        for (String name : list) {
            System.out.println(name);
        }
        System.out.println("[END]");
    }
}