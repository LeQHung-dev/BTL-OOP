import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * OCR bằng chương trình Tesseract cài sẵn trên máy (gọi qua dòng lệnh, không cần thư viện Java).
 * Cài Tesseract: https://github.com/UB-Mannheim/tesseract/wiki (Windows) và tick chọn ngôn ngữ Vietnamese.
 */
public class TesseractOcr implements OcrEngine {
    private final String command;   // đường dẫn hoặc tên lệnh tìm được, null nếu chưa cài

    public TesseractOcr() { this.command = findCommand(); }

    @Override public String getName() { return "Tesseract OCR"; }
    @Override public boolean isAvailable() { return command != null; }

    private static String findCommand() {
        String[] candidates = {
            System.getenv("TESSERACT_CMD"),
            "tesseract",
            "C:\\Program Files\\Tesseract-OCR\\tesseract.exe",
            "C:\\Program Files (x86)\\Tesseract-OCR\\tesseract.exe"
        };
        for (String c : candidates) {
            if (c == null || c.isEmpty()) continue;
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                drain(p.getInputStream());
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (IOException | InterruptedException e) {
                // thử ứng viên tiếp theo
            }
        }
        return null;
    }

    @Override
    public String readText(BufferedImage image) throws IOException {
        if (command == null) throw new IOException("Chưa cài Tesseract OCR trên máy này");
        File in = File.createTempFile("donthuoc_", ".png");
        File err = File.createTempFile("donthuoc_", ".err");
        try {
            ImageIO.write(prepare(image), "png", in);
            // Ưu tiên tiếng Việt + Anh; nếu máy chưa có gói tiếng Việt thì dùng tiếng Anh
            for (String lang : new String[]{"vie+eng", "eng"}) {
                Process p = new ProcessBuilder(command, in.getAbsolutePath(), "stdout", "-l", lang, "--psm", "6")
                        .redirectError(err).start();
                byte[] out = drain(p.getInputStream());
                try {
                    if (!p.waitFor(90, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new IOException("OCR chạy quá lâu"); }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("OCR bị ngắt");
                }
                if (p.exitValue() == 0) return removeNoiseLines(new String(out, StandardCharsets.UTF_8));
            }
            throw new IOException("Tesseract không đọc được ảnh");
        } finally {
            in.delete();
            err.delete();
        }
    }

    /** Bỏ các dòng rác (toàn ký hiệu lạ) do OCR đọc nhầm từ nền ảnh, để ô văn bản dễ đọc hơn. */
    static String removeNoiseLines(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\r?\\n")) {
            String t = line.replaceAll("\\s+", "");
            if (t.isEmpty()) continue;
            int good = 0;
            for (char c : t.toCharArray()) if (Character.isLetterOrDigit(c)) good++;
            if (good >= 3 && good >= 0.6 * t.length()) sb.append(line.trim()).append('\n');
        }
        return sb.toString();
    }

    private static byte[] drain(InputStream is) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    /**
     * Chuẩn bị ảnh cho OCR: chuyển sang ảnh xám, phóng to nếu nhỏ, rồi "san phẳng ánh sáng"
     * (chia mỗi điểm ảnh cho độ sáng nền xung quanh) để bỏ bóng đổ và vùng sáng tối không đều
     * thường gặp khi chụp đơn thuốc bằng điện thoại.
     */
    static BufferedImage prepare(BufferedImage src) {
        double scale = src.getWidth() < 1500 ? Math.min(3.0, 1500.0 / src.getWidth()) : 1.0;
        int w = (int) Math.round(src.getWidth() * scale), h = (int) Math.round(src.getHeight() * scale);
        BufferedImage gray = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        flattenIllumination(gray);
        return gray;
    }

    static void flattenIllumination(BufferedImage gray) {
        int w = gray.getWidth(), h = gray.getHeight();
        byte[] px = ((java.awt.image.DataBufferByte) gray.getRaster().getDataBuffer()).getData();
        int cell = 8;                                         // ước lượng nền trên ảnh thu nhỏ 8 lần
        int sw = Math.max(1, w / cell), sh = Math.max(1, h / cell);
        float[][] small = new float[sh][sw];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                long sum = 0; int n = 0;
                for (int yy = y * cell; yy < Math.min(h, (y + 1) * cell); yy++)
                    for (int xx = x * cell; xx < Math.min(w, (x + 1) * cell); xx++) { sum += px[yy * w + xx] & 0xFF; n++; }
                small[y][x] = n == 0 ? 255 : (float) sum / n;
            }
        }
        int r = 4;                                            // làm mờ hộp 9x9 trên ảnh nhỏ
        float[][] blur = new float[sh][sw];
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                float sum = 0; int n = 0;
                for (int yy = Math.max(0, y - r); yy <= Math.min(sh - 1, y + r); yy++)
                    for (int xx = Math.max(0, x - r); xx <= Math.min(sw - 1, x + r); xx++) { sum += small[yy][xx]; n++; }
                blur[y][x] = sum / n;
            }
        }
        for (int y = 0; y < h; y++) {
            float fy = Math.min(sh - 1, Math.max(0, (y + 0.5f) / cell - 0.5f));
            int y0 = (int) fy, y1 = Math.min(sh - 1, y0 + 1);
            float ty = fy - y0;
            for (int x = 0; x < w; x++) {
                float fx = Math.min(sw - 1, Math.max(0, (x + 0.5f) / cell - 0.5f));
                int x0 = (int) fx, x1 = Math.min(sw - 1, x0 + 1);
                float tx = fx - x0;
                float bg = (blur[y0][x0] * (1 - tx) + blur[y0][x1] * tx) * (1 - ty)
                         + (blur[y1][x0] * (1 - tx) + blur[y1][x1] * tx) * ty;
                int v = Math.round((px[y * w + x] & 0xFF) * 255f / Math.max(bg, 1f));
                px[y * w + x] = (byte) Math.min(255, v);
            }
        }
    }
}
