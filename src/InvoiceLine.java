/** Một dòng trên hóa đơn. Bất biến: tạo xong không sửa được (giữ nguyên giá tại thời điểm bán). */
public class InvoiceLine {
    private final String medicineName;
    private final String unit;
    private final double unitPrice;
    private final int quantity;

    public InvoiceLine(String medicineName, String unit, double unitPrice, int quantity) {
        this.medicineName = medicineName;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public String getMedicineName() { return medicineName; }
    public String getUnit() { return unit; }
    public double getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public double getSubtotal() { return unitPrice * quantity; }
}
