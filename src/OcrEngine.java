import java.awt.image.BufferedImage;
import java.io.IOException;

/** Bộ nhận dạng chữ trong ảnh (OCR). Có thể thay bằng cài đặt khác mà không phải sửa phần còn lại. */
public interface OcrEngine {
    /** Tên hiển thị của bộ OCR. */
    String getName();

    /** Bộ OCR có dùng được trên máy này không (đã cài chưa). */
    boolean isAvailable();

    /** Đọc toàn bộ chữ trong ảnh, trả về văn bản nhiều dòng. */
    String readText(BufferedImage image) throws IOException;
}
