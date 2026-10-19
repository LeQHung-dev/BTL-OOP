import java.awt.image.BufferedImage;
import java.io.IOException;

/** Dịch vụ quét đơn thuốc: ảnh -> (OCR) -> văn bản -> (phân tích) -> danh sách thuốc. */
public class ScannerService {
    private final OcrEngine ocr;
    private final PrescriptionParser parser;

    public ScannerService(OcrEngine ocr, PrescriptionParser parser) {
        this.ocr = ocr;
        this.parser = parser;
    }

    public boolean isOcrAvailable() { return ocr.isAvailable(); }
    public String getOcrName() { return ocr.getName(); }

    /** Quét từ ảnh (ảnh upload hoặc chụp từ camera). */
    public ScanResult scan(BufferedImage image) throws IOException {
        return parser.parse(ocr.readText(image));
    }

    /** Phân tích từ văn bản (dược sĩ gõ tay hoặc sửa lại kết quả OCR). */
    public ScanResult scanText(String text) { return parser.parse(text); }

    /** Đơn mẫu nhỏ cho bản console. */
    public static Prescription samplePrescription() {
        Prescription p = new Prescription();
        p.addItem("Paracetamol", 2);
        p.addItem("Amoxicillin", 1);
        return p;
    }

    /** Văn bản đơn mẫu để thử nghiệm giao diện (có thuốc thiếu, hết hàng). */
    public static String demoText() {
        return "ĐƠN THUỐC\n"
             + "1. Paracetamol 500mg ........ 10 viên\n"
             + "2. Amoxicillin 500mg ........ 14 viên\n"
             + "3. Loratadin 10mg ........... 10 viên\n"
             + "4. Salbutamol 2mg ........... 7 viên\n"
             + "5. Vitamin C 500mg .......... 20 viên\n"
             + "Bác sĩ: Nguyễn Văn A\n";
    }
}
