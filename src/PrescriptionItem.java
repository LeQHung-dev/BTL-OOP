/** Một dòng trong đơn thuốc: tên thuốc + số lượng bác sĩ kê. */
public class PrescriptionItem {
    private final String medicineName;
    private int quantity;

    public PrescriptionItem(String medicineName, int quantity) {
        if (medicineName == null || medicineName.trim().isEmpty())
            throw new IllegalArgumentException("Tên thuốc trong đơn không được để trống");
        if (quantity <= 0)
            throw new IllegalArgumentException("Số lượng thuốc phải lớn hơn 0");
        this.medicineName = medicineName.trim();
        this.quantity = quantity;
    }

    public String getMedicineName() { return medicineName; }
    public int getQuantity() { return quantity; }

    /** Sửa số lượng (ví dụ dược sĩ chỉnh lại sau khi quét). */
    public void setQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Số lượng thuốc phải lớn hơn 0");
        this.quantity = quantity;
    }

    /** Dùng khi đơn có cùng một thuốc xuất hiện 2 lần: gộp số lượng lại. */
    void increaseQuantity(int amount) { this.quantity += amount; }
}
