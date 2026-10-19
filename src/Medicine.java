/** Thuốc trong kho. Dữ liệu được đóng gói (private) và kiểm tra hợp lệ khi tạo / thay đổi. */
public class Medicine {
    private final String name;
    private final String unit;
    private final double price;      // giá bán cho 1 đơn vị (VND)
    private int stockQuantity;

    public Medicine(String name, double price, int stockQuantity) {
        this(name, "viên", price, stockQuantity);
    }

    public Medicine(String name, String unit, double price, int stockQuantity) {
        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Tên thuốc không được để trống");
        if (price < 0)
            throw new IllegalArgumentException("Giá thuốc không được âm");
        if (stockQuantity < 0)
            throw new IllegalArgumentException("Số lượng tồn không được âm");
        this.name = name.trim();
        this.unit = unit;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public String getName() { return name; }
    public String getUnit() { return unit; }
    public double getPrice() { return price; }
    public int getStockQuantity() { return stockQuantity; }

    /** Kho có đủ [amount] đơn vị không? */
    public boolean hasEnough(int amount) { return stockQuantity >= amount; }

    /** Trừ kho. Không cho phép trừ quá số lượng đang có (kho không bao giờ âm). */
    public void reduceStock(int amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Số lượng trừ phải lớn hơn 0");
        if (amount > stockQuantity)
            throw new IllegalStateException("Không đủ " + name + " trong kho (còn " + stockQuantity + ")");
        stockQuantity -= amount;
    }

    /** Nhập thêm hàng vào kho. */
    public void addStock(int amount) {
        if (amount <= 0)
            throw new IllegalArgumentException("Số lượng nhập phải lớn hơn 0");
        stockQuantity += amount;
    }
}
