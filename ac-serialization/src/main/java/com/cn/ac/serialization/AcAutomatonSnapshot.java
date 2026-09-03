package com.cn.ac.serialization;

import com.cn.ac.*;
import com.cn.ac.exception.AcErrorCode;
import com.cn.ac.exception.CorruptAutomatonException;
import com.cn.ac.internal.AutomatonValidator;
import com.cn.ac.internal.KeywordTable;
import com.cn.ac.internal.PackedAutomatonData;

import java.io.*;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

/**
 * {@code ACAT} 二进制自动机快照持久化器。
 *
 * <p>本类负责将构建好的 {@link AcAutomaton} 平铺数组拓扑、元数据及 Payload 序列化为紧凑的二进制文件，
 * 或从输入流反序列化就绪。
 *
 * <h3>ACAT 文件格式布局：</h3>
 * <ol>
 *   <li><b>文件头（Header）</b>：Magic (0x41434154 'ACAT')、主次版本号、配置指纹、Unicode 版本、节点与边总数、Payload Codec ID。</li>
 *   <li><b>数据段（Sections）</b>：平铺整型数组（failure, edges, targets, outputLinks, keywords）。</li>
 *   <li><b>Payload 数据段</b>：通过 {@link PayloadCodec} 编码的业务自定义对象。</li>
 *   <li><b>校验尾（Footer）</b>：针对前序所有字节计算的 CRC32 64 位校验码，严防磁盘损坏或非法篡改。</li>
 * </ol>
 */
public final class AcAutomatonSnapshot {

    public static final int MAGIC = 0x41434154; // 'ACAT'
    public static final short FORMAT_MAJOR = 1;
    public static final short FORMAT_MINOR = 0;

    /**
     * 将自动机写入输出流持久化为 ACAT 二进制快照。
     *
     * @param automaton 待保存的不可变自动机
     * @param out       目标字节输出流
     * @param codec     自定义 Payload 编解码器（无 Payload 可传 null）
     * @throws IOException 写入 IO 异常
     */
    public static <T> void save(AcAutomaton<T> automaton, OutputStream out, PayloadCodec<T> codec) throws IOException {
        CRC32 crc = new CRC32();
        CheckedOutputStream cos = new CheckedOutputStream(out, crc);
        DataOutputStream dos = new DataOutputStream(cos);

        PackedAutomatonData data = automaton.data();
        KeywordTable<T> kwTable = automaton.keywords();
        AcStats stats = automaton.stats();
        TextTransformConfig config = automaton.transformConfig();

        int stateCount = stats.stateCount();
        int edgeCount = stats.edgeCount();
        int outputCount = stats.ownOutputCount();
        int keywordCount = stats.keywordCount();

        // 1. Header
        dos.writeInt(MAGIC);
        dos.writeShort(FORMAT_MAJOR);
        dos.writeShort(FORMAT_MINOR);
        dos.writeInt(0); // flags
        dos.writeUTF(config.unicodeVersion());
        dos.writeLong(config.fingerprint());
        dos.writeInt(stateCount);
        dos.writeInt(edgeCount);
        dos.writeInt(outputCount);
        dos.writeInt(keywordCount);
        dos.writeInt(stats.maxDepthCodePoint());
        dos.writeInt(stats.maxKeywordCodePointLength());

        String codecId = (codec != null) ? codec.codecId() : "NONE";
        dos.writeUTF(codecId);

        // 2. Sections
        writeIntArray(dos, data.failure);
        writeIntArray(dos, data.firstEdge);
        writeIntArray(dos, data.edgeCount);
        writeIntArray(dos, data.outputLink);
        writeIntArray(dos, data.ownOutputStart);
        writeIntArray(dos, data.ownOutputCount);
        writeIntArray(dos, data.edgeCodePoint);
        writeIntArray(dos, data.edgeTarget);
        writeIntArray(dos, data.ownOutputKeywordSlot);

        writeIntArray(dos, kwTable.keywordIdBySlot);
        writeIntArray(dos, kwTable.keywordLengthCodePoint);

        boolean hasExactUtf16 = (kwTable.keywordLengthUtf16Exact != null);
        dos.writeBoolean(hasExactUtf16);
        if (hasExactUtf16) {
            writeIntArray(dos, kwTable.keywordLengthUtf16Exact);
        }

        boolean hasOriginalKeywords = (kwTable.originalKeywordBySlot != null);
        dos.writeBoolean(hasOriginalKeywords);
        if (hasOriginalKeywords) {
            for (int i = 0; i < keywordCount; i++) {
                String orig = kwTable.originalKeywordBySlot[i];
                dos.writeBoolean(orig != null);
                if (orig != null) {
                    dos.writeUTF(orig);
                }
            }
        }

        boolean hasPayloads = (codec != null && kwTable.payloadBySlot != null);
        dos.writeBoolean(hasPayloads);
        if (hasPayloads) {
            for (int i = 0; i < keywordCount; i++) {
                @SuppressWarnings("unchecked")
                T payload = (T) kwTable.payloadBySlot[i];
                dos.writeBoolean(payload != null);
                if (payload != null) {
                    codec.encode(payload, dos);
                }
            }
        }

        // rootAsciiTarget
        writeIntArray(dos, data.rootAsciiTarget);

        // 3. Footer: CRC32
        dos.flush();
        long computedCrc = crc.getValue();
        DataOutputStream rawDos = new DataOutputStream(out);
        rawDos.writeLong(computedCrc);
        rawDos.flush();
    }

    public static <T> AcAutomaton<T> load(InputStream in, PayloadCodec<T> codec) throws IOException {
        CRC32 crc = new CRC32();
        CheckedInputStream cis = new CheckedInputStream(in, crc);
        DataInputStream dis = new DataInputStream(cis);

        int magic = dis.readInt();
        if (magic != MAGIC) {
            throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON, "Invalid magic number: " + Integer.toHexString(magic));
        }

        short major = dis.readShort();
        short minor = dis.readShort();
        if (major != FORMAT_MAJOR) {
            throw new CorruptAutomatonException(AcErrorCode.SNAPSHOT_VERSION_MISMATCH, "Incompatible snapshot major version: " + major);
        }

        int flags = dis.readInt();
        String unicodeVer = dis.readUTF();
        long fingerprint = dis.readLong();

        int stateCount = dis.readInt();
        int edgeCount = dis.readInt();
        int outputCount = dis.readInt();
        int keywordCount = dis.readInt();
        int maxDepth = dis.readInt();
        int maxKeywordCodePoints = dis.readInt();

        String codecId = dis.readUTF();
        if (!"NONE".equals(codecId)) {
            if (codec == null || !codec.codecId().equals(codecId)) {
                throw new CorruptAutomatonException(AcErrorCode.UNSUPPORTED_CONFIG, "Payload codec mismatch. Expected: " + codecId);
            }
        }

        int[] failure = readIntArray(dis, stateCount);
        int[] firstEdge = readIntArray(dis, stateCount);
        int[] edgeCounts = readIntArray(dis, stateCount);
        int[] outputLink = readIntArray(dis, stateCount);
        int[] ownOutputStart = readIntArray(dis, stateCount);
        int[] ownOutputCounts = readIntArray(dis, stateCount);
        int[] edgeCodePoint = readIntArray(dis, edgeCount);
        int[] edgeTarget = readIntArray(dis, edgeCount);
        int[] ownOutputKeywordSlot = readIntArray(dis, outputCount);

        int[] keywordIdBySlot = readIntArray(dis, keywordCount);
        int[] keywordLengthCodePoint = readIntArray(dis, keywordCount);

        boolean hasExactUtf16 = dis.readBoolean();
        int[] keywordLengthUtf16Exact = hasExactUtf16 ? readIntArray(dis, keywordCount) : null;

        boolean hasOriginalKeywords = dis.readBoolean();
        String[] originalKeywords = null;
        if (hasOriginalKeywords) {
            originalKeywords = new String[keywordCount];
            for (int i = 0; i < keywordCount; i++) {
                if (dis.readBoolean()) {
                    originalKeywords[i] = dis.readUTF();
                }
            }
        }

        boolean hasPayloads = dis.readBoolean();
        Object[] payloads = new Object[keywordCount];
        if (hasPayloads && codec != null) {
            for (int i = 0; i < keywordCount; i++) {
                if (dis.readBoolean()) {
                    payloads[i] = codec.decode(dis);
                }
            }
        }

        int[] rootAsciiTarget = readIntArray(dis, 128);

        long computedCrc = crc.getValue();
        DataInputStream rawDis = new DataInputStream(in);
        long storedCrc = rawDis.readLong();
        if (computedCrc != storedCrc) {
            throw new CorruptAutomatonException(AcErrorCode.CHECKSUM_MISMATCH,
                    "CRC32 mismatch. Computed: " + computedCrc + ", stored: " + storedCrc);
        }

        PackedAutomatonData data = new PackedAutomatonData(
                failure, firstEdge, edgeCounts, outputLink, ownOutputStart, ownOutputCounts,
                edgeCodePoint, edgeTarget, ownOutputKeywordSlot, rootAsciiTarget
        );

        // Reconstruct keywordIdSorted
        Integer[] sortIndices = new Integer[keywordCount];
        for (int i = 0; i < keywordCount; i++) sortIndices[i] = i;
        Arrays.sort(sortIndices, (a, b) -> Integer.compare(keywordIdBySlot[a], keywordIdBySlot[b]));
        int[] keywordIdSorted = new int[keywordCount];
        int[] keywordSlotBySortedId = new int[keywordCount];
        for (int i = 0; i < keywordCount; i++) {
            int slot = sortIndices[i];
            keywordIdSorted[i] = keywordIdBySlot[slot];
            keywordSlotBySortedId[i] = slot;
        }

        KeywordTable<T> kwTable = new KeywordTable<>(
                keywordIdBySlot, keywordLengthCodePoint, keywordLengthUtf16Exact,
                keywordIdSorted, keywordSlotBySortedId, payloads, originalKeywords
        );

        AutomatonValidator.validate(data, stateCount, null, kwTable);

        long estimatedBytes = (long) stateCount * 24L + (long) edgeCount * 8L +
                (long) outputCount * 4L + (long) keywordCount * 20L + 512L;
        AcStats stats = new AcStats(
                keywordCount, stateCount, edgeCount, outputCount,
                maxDepth, maxKeywordCodePoints, estimatedBytes,
                unicodeVer, fingerprint
        );

        TextTransformConfig config = TextTransformConfig.exact();
        return new AcAutomaton<>(data, kwTable, config, stats);
    }

    private static void writeIntArray(DataOutputStream dos, int[] arr) throws IOException {
        int len = (arr != null) ? arr.length : 0;
        dos.writeInt(len);
        for (int i = 0; i < len; i++) {
            dos.writeInt(arr[i]);
        }
    }

    private static int[] readIntArray(DataInputStream dis, int expectedLen) throws IOException {
        int len = dis.readInt();
        if (len != expectedLen) {
            throw new CorruptAutomatonException(AcErrorCode.CORRUPT_AUTOMATON,
                    "Array length mismatch: expected " + expectedLen + ", got " + len);
        }
        int[] arr = new int[len];
        for (int i = 0; i < len; i++) {
            arr[i] = dis.readInt();
        }
        return arr;
    }

    private AcAutomatonSnapshot() {}
}
