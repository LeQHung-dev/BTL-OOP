import java.awt.image.BufferedImage;

/** Chuyển một chuỗi văn bản thành ảnh mã QR (dùng bộ mã hóa QrCode viết sẵn trong project). */
public final class QrImage {
    private QrImage() { }

    /** @param size kích thước ảnh mong muốn (pixel); ảnh thật có thể nhỏ hơn một chút vì làm tròn theo ô. */
    public static BufferedImage generate(String text, int size) {
        QrCode qr = QrCode.encodeText(text);
        int border = 4;                                   // vùng trắng xung quanh, giúp app quét dễ hơn
        int cells = qr.getSize() + border * 2;
        int scale = Math.max(1, size / cells);
        BufferedImage img = new BufferedImage(cells * scale, cells * scale, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                boolean dark = qr.getModule(x / scale - border, y / scale - border);
                img.setRGB(x, y, dark ? 0x000000 : 0xFFFFFF);
            }
        }
        return img;
    }
}
