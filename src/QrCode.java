import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Bộ mã hóa mã QR viết thuần Java (chế độ byte, mức sửa lỗi M, phiên bản 1-10).
 * Đủ cho nội dung VietQR (khoảng 100-150 ký tự). Không cần thư viện ngoài.
 *
 * Các bước: chọn phiên bản -> mã hóa dữ liệu -> sinh mã sửa lỗi Reed-Solomon
 * -> xếp các khối vào ma trận -> chọn mặt nạ tốt nhất -> ghi thông tin định dạng.
 */
public final class QrCode {
    private static final int[] ECC_PER_BLOCK = {10, 16, 26, 18, 24, 16, 18, 22, 22, 26};  // mức M, phiên bản 1..10
    private static final int[] NUM_BLOCKS    = { 1,  1,  1,  2,  2,  4,  4,  4,  5,  5};
    public static final int MAX_VERSION = 10;

    private final int version;
    private final int size;
    private final boolean[][] modules;     // modules[y][x] = true là ô đen
    private final boolean[][] isFunction;  // ô thuộc vùng chức năng (định vị, đồng bộ...), không bị đổi bởi mặt nạ

    private QrCode(int version, byte[] dataCodewords) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];

        drawFunctionPatterns();
        drawCodewords(addEccAndInterleave(dataCodewords));

        int bestMask = 0;
        int bestPenalty = Integer.MAX_VALUE;
        for (int m = 0; m < 8; m++) {
            applyMask(m);
            drawFormatBits(m);
            int p = penalty();
            if (p < bestPenalty) { bestPenalty = p; bestMask = m; }
            applyMask(m);   // áp dụng lần nữa = hoàn tác
        }
        applyMask(bestMask);
        drawFormatBits(bestMask);
    }

    /** Mã hóa một chuỗi văn bản (UTF-8). Ném IllegalArgumentException nếu quá dài. */
    public static QrCode encodeText(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        for (int ver = 1; ver <= MAX_VERSION; ver++) {
            int capacityBits = (rawCodewords(ver) - ECC_PER_BLOCK[ver - 1] * NUM_BLOCKS[ver - 1]) * 8;
            int countBits = ver <= 9 ? 8 : 16;
            int needed = 4 + countBits + data.length * 8;
            if (needed > capacityBits) continue;

            List<Boolean> bits = new ArrayList<>();
            appendBits(bits, 0x4, 4);                 // chế độ byte
            appendBits(bits, data.length, countBits); // số ký tự
            for (byte b : data) appendBits(bits, b & 0xFF, 8);
            appendBits(bits, 0, Math.min(4, capacityBits - bits.size()));   // kết thúc
            while (bits.size() % 8 != 0) bits.add(false);
            for (int pad = 0xEC; bits.size() < capacityBits; pad ^= 0xEC ^ 0x11) appendBits(bits, pad, 8);

            byte[] cw = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++)
                if (bits.get(i)) cw[i >>> 3] |= 1 << (7 - (i & 7));
            return new QrCode(ver, cw);
        }
        throw new IllegalArgumentException("Nội dung quá dài để tạo mã QR (tối đa khoảng 213 ký tự)");
    }

    public int getSize() { return size; }
    public boolean getModule(int x, int y) { return x >= 0 && y >= 0 && x < size && y < size && modules[y][x]; }

    // ---------- Dữ liệu và mã sửa lỗi ----------

    private static void appendBits(List<Boolean> list, int val, int len) {
        for (int i = len - 1; i >= 0; i--) list.add(((val >>> i) & 1) != 0);
    }

    private static int rawDataModules(int ver) {
        int result = (16 * ver + 128) * ver + 64;
        if (ver >= 2) {
            int numAlign = ver / 7 + 2;
            result -= (25 * numAlign - 10) * numAlign - 55;
            if (ver >= 7) result -= 36;
        }
        return result;
    }

    private static int rawCodewords(int ver) { return rawDataModules(ver) / 8; }

    private byte[] addEccAndInterleave(byte[] data) {
        int numBlocks = NUM_BLOCKS[version - 1];
        int blockEcc = ECC_PER_BLOCK[version - 1];
        int raw = rawCodewords(version);
        int numShort = numBlocks - raw % numBlocks;
        int shortLen = raw / numBlocks;

        byte[][] blocks = new byte[numBlocks][];
        int[] divisor = rsDivisor(blockEcc);
        for (int i = 0, k = 0; i < numBlocks; i++) {
            int datLen = shortLen - blockEcc + (i < numShort ? 0 : 1);
            byte[] dat = new byte[datLen];
            System.arraycopy(data, k, dat, 0, datLen);
            k += datLen;
            byte[] ecc = rsRemainder(dat, divisor);
            byte[] block = new byte[shortLen + 1];
            System.arraycopy(dat, 0, block, 0, datLen);
            System.arraycopy(ecc, 0, block, block.length - blockEcc, blockEcc);
            blocks[i] = block;
        }
        byte[] result = new byte[raw];
        for (int i = 0, r = 0; i < blocks[0].length; i++) {
            for (int j = 0; j < numBlocks; j++) {
                if (i != shortLen - blockEcc || j >= numShort) result[r++] = blocks[j][i];
            }
        }
        return result;
    }

    // Reed-Solomon trên trường GF(256), đa thức sinh 0x11D
    private static int gfMul(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static int[] rsDivisor(int degree) {
        int[] result = new int[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < degree; j++) {
                result[j] = gfMul(result[j], root);
                if (j + 1 < degree) result[j] ^= result[j + 1];
            }
            root = gfMul(root, 0x02);
        }
        return result;
    }

    private static byte[] rsRemainder(byte[] data, int[] divisor) {
        int[] result = new int[divisor.length];
        for (byte b : data) {
            int factor = (b & 0xFF) ^ result[0];
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) result[i] ^= gfMul(divisor[i], factor);
        }
        byte[] out = new byte[result.length];
        for (int i = 0; i < out.length; i++) out[i] = (byte) result[i];
        return out;
    }

    // ---------- Vẽ ma trận ----------

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {                 // đường đồng bộ
            setFunction(6, i, i % 2 == 0);
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);

        int[] pos = alignmentPositions();
        for (int i = 0; i < pos.length; i++) {
            for (int j = 0; j < pos.length; j++) {
                boolean overlapFinder = (i == 0 && j == 0) || (i == 0 && j == pos.length - 1) || (i == pos.length - 1 && j == 0);
                if (!overlapFinder) drawAlignment(pos[i], pos[j]);
            }
        }
        drawFormatBits(0);   // giữ chỗ, sẽ ghi lại sau khi chọn mặt nạ
        drawVersionBits();
    }

    private int[] alignmentPositions() {
        if (version == 1) return new int[0];
        int numAlign = version / 7 + 2;
        int step = (version * 4 + numAlign * 2 + 1) / (numAlign * 2 - 2) * 2;
        int[] result = new int[numAlign];
        result[0] = 6;
        for (int i = numAlign - 1, pos = size - 7; i >= 1; i--, pos -= step) result[i] = pos;
        return result;
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) setFunction(x, y, dist != 2 && dist != 4);
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++)
            for (int dx = -2; dx <= 2; dx++)
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
    }

    private void drawFormatBits(int mask) {
        int data = (0 << 3) | mask;          // mức sửa lỗi M có mã định dạng 00
        int rem = data;
        for (int i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
        int bits = ((data << 10) | rem) ^ 0x5412;

        for (int i = 0; i <= 5; i++) setFunction(8, i, bit(bits, i));
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) setFunction(14 - i, 8, bit(bits, i));

        for (int i = 0; i < 8; i++) setFunction(size - 1 - i, 8, bit(bits, i));
        for (int i = 8; i < 15; i++) setFunction(8, size - 15 + i, bit(bits, i));
        setFunction(8, size - 8, true);      // ô đen cố định
    }

    private void drawVersionBits() {
        if (version < 7) return;
        int rem = version;
        for (int i = 0; i < 12; i++) rem = (rem << 1) ^ ((rem >>> 11) * 0x1F25);
        int bits = (version << 12) | rem;
        for (int i = 0; i < 18; i++) {
            boolean b = bit(bits, i);
            int a = size - 11 + i % 3, c = i / 3;
            setFunction(a, c, b);
            setFunction(c, a, b);
        }
    }

    private static boolean bit(int x, int i) { return ((x >>> i) & 1) != 0; }

    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - vert : vert;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    // ---------- Mặt nạ ----------

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean invert;
                switch (mask) {
                    case 0: invert = (x + y) % 2 == 0; break;
                    case 1: invert = y % 2 == 0; break;
                    case 2: invert = x % 3 == 0; break;
                    case 3: invert = (x + y) % 3 == 0; break;
                    case 4: invert = (x / 3 + y / 2) % 2 == 0; break;
                    case 5: invert = x * y % 2 + x * y % 3 == 0; break;
                    case 6: invert = (x * y % 2 + x * y % 3) % 2 == 0; break;
                    default: invert = ((x + y) % 2 + x * y % 3) % 2 == 0; break;
                }
                if (!isFunction[y][x] && invert) modules[y][x] = !modules[y][x];
            }
        }
    }

    /** Điểm phạt: càng thấp thì mã càng dễ quét. Gồm 4 quy tắc của chuẩn QR. */
    private int penalty() {
        int result = 0;
        for (int pass = 0; pass < 2; pass++) {            // pass 0: theo hàng, pass 1: theo cột
            for (int a = 0; a < size; a++) {
                int run = 1;
                StringBuilder line = new StringBuilder();
                for (int b = 0; b < size; b++) {
                    boolean cur = pass == 0 ? modules[a][b] : modules[b][a];
                    line.append(cur ? '1' : '0');
                    if (b > 0) {
                        boolean prev = pass == 0 ? modules[a][b - 1] : modules[b - 1][a];
                        if (cur == prev) { run++; if (run == 5) result += 3; else if (run > 5) result++; }
                        else run = 1;
                    }
                }
                String s = line.toString();
                for (String pat : new String[]{"10111010000", "00001011101"}) {   // mẫu giống ô định vị
                    for (int idx = s.indexOf(pat); idx >= 0; idx = s.indexOf(pat, idx + 1)) result += 40;
                }
            }
        }
        for (int y = 0; y < size - 1; y++)                // khối 2x2 cùng màu
            for (int x = 0; x < size - 1; x++)
                if (modules[y][x] == modules[y][x + 1] && modules[y][x] == modules[y + 1][x] && modules[y][x] == modules[y + 1][x + 1])
                    result += 3;
        int dark = 0;
        for (boolean[] row : modules) for (boolean m : row) if (m) dark++;
        int total = size * size;
        int k = (Math.abs(dark * 20 - total * 10) + total - 1) / total - 1;   // độ lệch tỉ lệ ô đen khỏi 50%
        result += Math.max(0, k) * 10;
        return result;
    }
}
