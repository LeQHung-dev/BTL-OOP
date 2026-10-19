import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/** Dịch vụ thanh toán: quản lý các phương thức và xác nhận thanh toán cho hóa đơn. */
public class PaymentService {
    private final BankTransferPayment bankTransfer = new BankTransferPayment();
    private final CashPayment cash = new CashPayment();

    public BankTransferPayment getBankTransfer() { return bankTransfer; }
    public CashPayment getCash() { return cash; }

    public List<PaymentStrategy> getMethods() {
        return Collections.unmodifiableList(Arrays.asList(bankTransfer, cash));
    }

    /** Gọi khi đã nhận được tiền (thu ngân bấm xác nhận hoặc ngân hàng báo có). */
    public void confirmPayment(Invoice invoice, PaymentStrategy method) {
        invoice.markPaid(method.getName());
    }

    /** Bản console: hỏi người dùng chọn cách thanh toán. Trả về true nếu thanh toán xong. */
    public boolean processPayment(Invoice invoice, Scanner sc) {
        List<PaymentStrategy> methods = getMethods();
        System.out.println("\n--- THANH TOÁN ---");
        System.out.println("Tổng tiền: " + Invoice.formatVnd(invoice.getTotal()));
        for (int i = 0; i < methods.size(); i++)
            System.out.println((i + 1) + ". " + methods.get(i).getName());
        System.out.print("Chọn (1-" + methods.size() + "): ");
        if (!sc.hasNextInt()) { System.out.println("❌ Lựa chọn không hợp lệ!"); return false; }
        int choice = sc.nextInt();
        if (choice < 1 || choice > methods.size()) { System.out.println("❌ Lựa chọn không hợp lệ!"); return false; }
        PaymentStrategy method = methods.get(choice - 1);
        System.out.println(method.prepare(invoice));
        if (method.getQrPayload(invoice) != null)
            System.out.println("Nội dung mã QR: " + method.getQrPayload(invoice));
        System.out.println("✅ Đã nhận tiền (mô phỏng).");
        confirmPayment(invoice, method);
        return true;
    }
}
