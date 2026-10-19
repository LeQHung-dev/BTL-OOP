/** Thanh toán chuyển khoản bằng mã VietQR. Muốn đổi tài khoản nhận tiền thì sửa 4 dòng khai báo bên dưới. */
public class BankTransferPayment implements PaymentStrategy {
    private final String bankName = "Vietcombank";
    private final String bankBin = "970436";            // mã BIN của Vietcombank
    private final String accountNumber = "1032888573";
    private final String accountName = "NGUYEN QUANG HOA";

    @Override
    public String getName() { return "Chuyển khoản"; }

    /** Nội dung chuyển khoản, gắn với mã hóa đơn để đối soát. */
    public String getTransferContent(Invoice invoice) {
        return "THANHTOAN " + invoice.getCode();
    }

    public String getBankName() { return bankName; }
    public String getAccountNumber() { return accountNumber; }
    public String getAccountName() { return accountName; }

    @Override
    public String getQrPayload(Invoice invoice) {
        return VietQR.build(bankBin, accountNumber, Math.round(invoice.getTotal()), getTransferContent(invoice));
    }

    @Override
    public String prepare(Invoice invoice) {
        return "Quét mã QR bằng ứng dụng ngân hàng để thanh toán\n"
             + "Ngân hàng: " + bankName + "\n"
             + "Chủ tài khoản: " + accountName + " - " + accountNumber + "\n"
             + "Số tiền: " + Invoice.formatVnd(invoice.getTotal()) + "\n"
             + "Nội dung: " + getTransferContent(invoice);
    }
}
