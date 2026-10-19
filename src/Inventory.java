import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Quản lý kho thuốc. */
public class Inventory {
    // Khóa là tên thuốc viết thường để tra cứu không phân biệt hoa/thường
    private final Map<String, Medicine> medicines = new LinkedHashMap<>();

    private static String key(String name) { return name.trim().toLowerCase(); }

    /** Thêm thuốc vào kho (dùng cho Admin / khởi tạo dữ liệu). */
    public void addMedicine(Medicine med) {
        medicines.put(key(med.getName()), med);
    }

    /** Tìm thuốc theo tên; trả về null nếu không có. */
    public Medicine findMedicine(String name) {
        return medicines.get(key(name));
    }

    public Collection<Medicine> getAllMedicines() {
        return Collections.unmodifiableCollection(medicines.values());
    }

    /** Kiểm tra từng dòng thuốc của đơn và trả về trạng thái chi tiết. KHÔNG in ra màn hình. */
    public List<StockCheckResult> checkItems(Prescription prescription) {
        List<StockCheckResult> results = new ArrayList<>();
        for (PrescriptionItem item : prescription.getItems()) {
            Medicine med = findMedicine(item.getMedicineName());
            StockStatus status;
            if (med == null) status = StockStatus.NOT_FOUND;
            else if (med.getStockQuantity() == 0) status = StockStatus.OUT_OF_STOCK;
            else if (!med.hasEnough(item.getQuantity())) status = StockStatus.INSUFFICIENT;
            else status = StockStatus.ENOUGH;
            results.add(new StockCheckResult(item, med, status));
        }
        return results;
    }

    /** true nếu TẤT CẢ thuốc trong đơn đều đủ hàng. */
    public boolean checkAvailability(Prescription prescription) {
        if (prescription.isEmpty()) return false;
        for (StockCheckResult r : checkItems(prescription)) {
            if (!r.isEnough()) return false;
        }
        return true;
    }

    /**
     * Phần của đơn mà kho còn đáp ứng được: mỗi thuốc lấy min(số lượng kê, số lượng tồn),
     * thuốc hết hàng hoặc không có trong kho thì bỏ qua. Dùng khi khách chỉ mua phần còn đủ.
     */
    public Prescription availablePortion(Prescription prescription) {
        Prescription result = new Prescription();
        for (StockCheckResult r : checkItems(prescription)) {
            int qty = Math.min(r.getItem().getQuantity(), r.getInStock());
            if (qty > 0) result.addItem(r.getMedicine().getName(), qty);
        }
        return result;
    }

    /** Tính tổng tiền cả đơn. Chỉ gọi sau khi đã kiểm tra kho. */
    public double calculateTotal(Prescription prescription) {
        double total = 0;
        for (PrescriptionItem item : prescription.getItems()) {
            Medicine med = findMedicine(item.getMedicineName());
            if (med == null)
                throw new IllegalStateException("Thuốc '" + item.getMedicineName() + "' không có trong kho");
            total += med.getPrice() * item.getQuantity();
        }
        return total;
    }

    /**
     * Xuất thuốc: trừ kho. Kiểm tra TOÀN BỘ đơn trước, nếu thiếu thì ném lỗi
     * và không trừ gì cả (tránh tình trạng trừ dở dang).
     */
    public void dispenseMedicines(Prescription prescription) {
        if (!checkAvailability(prescription))
            throw new IllegalStateException("Kho không đủ thuốc để xuất đơn này");
        for (PrescriptionItem item : prescription.getItems()) {
            findMedicine(item.getMedicineName()).reduceStock(item.getQuantity());
        }
    }
}
