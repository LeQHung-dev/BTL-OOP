import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Lưu và đọc kho thuốc từ file CSV (cột: tên;đơn vị;giá;tồn kho) để dữ liệu không mất khi tắt chương trình. */
public final class InventoryStorage {
    private InventoryStorage() { }

    public static void save(Inventory inventory, Path file) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("ten;don_vi;gia;ton_kho");
        for (Medicine m : inventory.getAllMedicines()) {
            lines.add(m.getName().replace(';', ',') + ";" + m.getUnit() + ";"
                    + Math.round(m.getPrice()) + ";" + m.getStockQuantity());
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
    }

    /** Đọc kho từ file; nếu chưa có file hoặc file lỗi thì dùng kho mẫu. */
    public static Inventory loadOrSample(Path file) {
        if (Files.exists(file)) {
            try {
                Inventory inv = new Inventory();
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty()) continue;
                    String[] p = line.split(";");
                    inv.addMedicine(new Medicine(p[0], p[1], Double.parseDouble(p[2]), Integer.parseInt(p[3])));
                }
                if (!inv.getAllMedicines().isEmpty()) {
                    // Bản mới có thêm thuốc mẫu: bổ sung các thuốc còn thiếu, giữ nguyên số liệu đã lưu
                    for (Medicine m : PharmacySystem.createSampleInventory().getAllMedicines())
                        if (inv.findMedicine(m.getName()) == null) inv.addMedicine(m);
                    return inv;
                }
            } catch (IOException | RuntimeException e) {
                System.err.println("Không đọc được " + file + ", dùng kho mẫu: " + e.getMessage());
            }
        }
        return PharmacySystem.createSampleInventory();
    }
}
