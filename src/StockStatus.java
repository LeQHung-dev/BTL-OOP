/** Trạng thái tồn kho của MỘT dòng thuốc trong đơn. */
public enum StockStatus {
    ENOUGH("Đủ hàng"),
    INSUFFICIENT("Thiếu hàng"),
    OUT_OF_STOCK("Hết hàng"),
    NOT_FOUND("Không có trong kho");

    private final String label;

    StockStatus(String label) { this.label = label; }

    public String getLabel() { return label; }
}
