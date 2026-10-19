import java.text.Normalizer;

/**
 * Tạo chuỗi nội dung VietQR (chuẩn EMVCo + NAPAS 247) để chuyển thành ảnh mã QR.
 * Quét bằng app của bất kỳ ngân hàng nào, số tiền và nội dung sẽ được điền sẵn.
 */
public final class VietQR {
    private VietQR() { }

    /** Ghép một trường theo dạng: mã (2 ký tự) + độ dài (2 chữ số) + giá trị. */
    private static String tlv(String id, String value) {
        return id + String.format("%02d", value.length()) + value;
    }

    /**
     * @param bankBin       mã BIN ngân hàng nhận tiền (Vietcombank = 970436)
     * @param accountNumber số tài khoản nhận
     * @param amount        số tiền (VND). Nếu <= 0 thì tạo QR tĩnh, khách tự nhập số tiền
     * @param content       nội dung chuyển khoản (tự bỏ dấu, tối đa 25 ký tự)
     */
    public static String build(String bankBin, String accountNumber, long amount, String content) {
        String account = tlv("00", bankBin) + tlv("01", accountNumber);
        String napas = tlv("00", "A000000727") + tlv("01", account) + tlv("02", "QRIBFTTA");

        StringBuilder sb = new StringBuilder();
        sb.append(tlv("00", "01"));                          // phiên bản
        sb.append(tlv("01", amount > 0 ? "12" : "11"));      // 12 = QR động (có số tiền), 11 = QR tĩnh
        sb.append(tlv("38", napas));                         // thông tin tài khoản nhận
        sb.append(tlv("53", "704"));                         // đơn vị tiền tệ: VND
        if (amount > 0) sb.append(tlv("54", Long.toString(amount)));
        sb.append(tlv("58", "VN"));                          // quốc gia
        String info = sanitize(content);
        if (!info.isEmpty()) sb.append(tlv("62", tlv("08", info)));
        sb.append("6304");                                   // CRC tính trên toàn bộ chuỗi, gồm cả "6304"
        sb.append(String.format("%04X", crc16(sb.toString())));
        return sb.toString();
    }

    /** CRC-16/CCITT-FALSE: đa thức 0x1021, giá trị đầu 0xFFFF. */
    static int crc16(String data) {
        int crc = 0xFFFF;
        for (byte b : data.getBytes(java.nio.charset.StandardCharsets.US_ASCII)) {
            crc ^= (b & 0xFF) << 8;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 0x8000) != 0) ? ((crc << 1) ^ 0x1021) : (crc << 1);
                crc &= 0xFFFF;
            }
        }
        return crc;
    }

    /** Bỏ dấu tiếng Việt, chỉ giữ chữ cái, số, khoảng trắng; cắt tối đa 25 ký tự. */
    static String sanitize(String s) {
        if (s == null) return "";
        String t = Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D')
                .replaceAll("[^A-Za-z0-9 ]", "").trim();
        return t.length() > 25 ? t.substring(0, 25).trim() : t;
    }
}
