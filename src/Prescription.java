import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Đơn thuốc: danh sách các dòng thuốc. */
public class Prescription {
    private final List<PrescriptionItem> items = new ArrayList<>();

    /** Thêm thuốc vào đơn. Nếu thuốc đã có trong đơn thì cộng dồn số lượng. */
    public void addItem(String name, int quantity) {
        PrescriptionItem exist = findItem(name);
        if (exist != null) {
            exist.increaseQuantity(quantity);
        } else {
            items.add(new PrescriptionItem(name, quantity));
        }
    }

    /** Sửa số lượng của dòng thứ [index]. */
    public void updateQuantity(int index, int quantity) { items.get(index).setQuantity(quantity); }

    /** Xóa dòng thứ [index] khỏi đơn. */
    public void removeAt(int index) { items.remove(index); }

    public void clear() { items.clear(); }

    private PrescriptionItem findItem(String name) {
        for (PrescriptionItem it : items) {
            if (it.getMedicineName().equalsIgnoreCase(name.trim())) return it;
        }
        return null;
    }

    /** Trả về danh sách chỉ-đọc để bên ngoài không sửa trực tiếp được. */
    public List<PrescriptionItem> getItems() { return Collections.unmodifiableList(items); }

    public boolean isEmpty() { return items.isEmpty(); }
    public int size() { return items.size(); }
}
