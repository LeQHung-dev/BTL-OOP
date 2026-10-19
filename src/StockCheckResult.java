/** Kết quả kiểm tra kho cho MỘT dòng thuốc trong đơn. */
public class StockCheckResult {
    private final PrescriptionItem item;
    private final Medicine medicine;   // null nếu thuốc không có trong kho
    private final StockStatus status;

    public StockCheckResult(PrescriptionItem item, Medicine medicine, StockStatus status) {
        this.item = item;
        this.medicine = medicine;
        this.status = status;
    }

    public PrescriptionItem getItem() { return item; }
    public Medicine getMedicine() { return medicine; }
    public StockStatus getStatus() { return status; }
    public boolean isEnough() { return status == StockStatus.ENOUGH; }

    /** Số lượng kho đang có (0 nếu không có thuốc). */
    public int getInStock() { return medicine == null ? 0 : medicine.getStockQuantity(); }

    /** Số lượng còn thiếu so với đơn (0 nếu đủ). */
    public int getMissing() { return Math.max(0, item.getQuantity() - getInStock()); }
}
