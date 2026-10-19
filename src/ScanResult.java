import java.util.Collections;
import java.util.List;

/** Kết quả quét một đơn thuốc: văn bản đọc được, danh sách thuốc nhận dạng và các cảnh báo. */
public class ScanResult {
    private final String rawText;
    private final Prescription prescription;
    private final List<String> warnings;

    public ScanResult(String rawText, Prescription prescription, List<String> warnings) {
        this.rawText = rawText;
        this.prescription = prescription;
        this.warnings = Collections.unmodifiableList(warnings);
    }

    public String getRawText() { return rawText; }
    public Prescription getPrescription() { return prescription; }
    public List<String> getWarnings() { return warnings; }
}
