import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Hóa đơn của một đơn thuốc: mã, thời gian, các dòng, tổng tiền, trạng thái thanh toán. */
public class Invoice {

    public enum Status {
        PENDING("Chờ thanh toán"), PAID("Đã thanh toán");
        private final String label;
        Status(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    private static int counter = 0;

    private final String code;
    private final LocalDateTime createdAt;
    private final List<InvoiceLine> lines;
    private Status status = Status.PENDING;
    private String paymentMethodName;   // null cho tới khi thanh toán

    private Invoice(List<InvoiceLine> lines) {
        this.code = nextCode();
        this.createdAt = LocalDateTime.now();
        this.lines = lines;
    }

    private static synchronized String nextCode() {
        counter++;
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("HD%s-%03d", day, counter);
    }

    /** Tạo hóa đơn từ đơn thuốc. Chỉ tạo được khi kho đủ thuốc. */
    public static Invoice create(Prescription prescription, Inventory inventory) {
        if (!inventory.checkAvailability(prescription))
            throw new IllegalStateException("Kho không đủ thuốc, không thể lập hóa đơn");
        List<InvoiceLine> lines = new ArrayList<>();
        for (PrescriptionItem item : prescription.getItems()) {
            Medicine med = inventory.findMedicine(item.getMedicineName());
            lines.add(new InvoiceLine(med.getName(), med.getUnit(), med.getPrice(), item.getQuantity()));
        }
        return new Invoice(Collections.unmodifiableList(lines));
    }

    public String getCode() { return code; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<InvoiceLine> getLines() { return lines; }
    public Status getStatus() { return status; }
    public boolean isPaid() { return status == Status.PAID; }
    public String getPaymentMethodName() { return paymentMethodName; }

    public double getTotal() {
        double total = 0;
        for (InvoiceLine l : lines) total += l.getSubtotal();
        return total;
    }

    /** Đánh dấu đã thanh toán. Mỗi hóa đơn chỉ được thanh toán một lần. */
    public void markPaid(String methodName) {
        if (isPaid()) throw new IllegalStateException("Hóa đơn " + code + " đã được thanh toán rồi");
        this.status = Status.PAID;
        this.paymentMethodName = methodName;
    }

    public static String formatVnd(double amount) {
        return String.format("%,.0f VND", amount);
    }

    /** Nội dung hóa đơn dạng văn bản để in ra console hoặc hiển thị trên GUI. */
    public String toReceiptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("HÓA ĐƠN ").append(code).append("\n");
        sb.append("Thời gian: ").append(createdAt.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))).append("\n");
        sb.append("----------------------------------------\n");
        for (InvoiceLine l : lines) {
            sb.append(String.format("%-14s %3d x %,8.0f = %,10.0f%n",
                    l.getMedicineName(), l.getQuantity(), l.getUnitPrice(), l.getSubtotal()));
        }
        sb.append("----------------------------------------\n");
        sb.append("TỔNG TIỀN: ").append(formatVnd(getTotal())).append("\n");
        sb.append("Trạng thái: ").append(status.getLabel());
        if (paymentMethodName != null) sb.append(" (").append(paymentMethodName).append(")");
        sb.append("\n");
        return sb.toString();
    }
}
