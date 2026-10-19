/** Một phương thức thanh toán. Mỗi cách thanh toán là một lớp riêng cài đặt giao diện này (đa hình). */
public interface PaymentStrategy {
    /** Tên hiển thị, ví dụ "Tiền mặt". */
    String getName();

    /** Hướng dẫn / thông tin cần hiển thị cho khách để thanh toán hóa đơn này. */
    String prepare(Invoice invoice);

    /** Nội dung để tạo mã QR; phương thức không dùng QR (tiền mặt) trả về null. */
    default String getQrPayload(Invoice invoice) { return null; }
}
