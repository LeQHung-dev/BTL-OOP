/** Thanh toán tiền mặt trực tiếp tại quầy. */
public class CashPayment implements PaymentStrategy {
    @Override
    public String getName() { return "Tiền mặt"; }

    @Override
    public String prepare(Invoice invoice) {
        return "Vui lòng thanh toán " + Invoice.formatVnd(invoice.getTotal()) + " bằng tiền mặt.";
    }
}
