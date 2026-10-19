import java.util.Scanner;

/** Bản chạy trên console: ghép nối toàn bộ luồng hệ thống. */
public class PharmacySystem {

    /** Tạo kho mẫu dùng chung cho cả bản console và bản GUI. */
    public static Inventory createSampleInventory() {
        Inventory inventory = new Inventory();
        inventory.addMedicine(new Medicine("Paracetamol", 5000, 100));
        inventory.addMedicine(new Medicine("Amoxicillin", 12000, 50));
        inventory.addMedicine(new Medicine("Vitamin C", 2000, 200));
        inventory.addMedicine(new Medicine("Loratadin", 2500, 8));
        inventory.addMedicine(new Medicine("Omeprazol", 4000, 40));
        inventory.addMedicine(new Medicine("Cetirizin", 2000, 100));
        inventory.addMedicine(new Medicine("Hightamine", 3000, 200));
        inventory.addMedicine(new Medicine("Kẽm gluconat", "ống", 6000, 15));
        inventory.addMedicine(new Medicine("Mometason furoat", "tuýp", 45000, 10));
        inventory.addMedicine(new Medicine("Salbutamol", 1500, 0));
        inventory.addMedicine(new Medicine("Ibuprofen", 2800, 90));
        inventory.addMedicine(new Medicine("Oresol", "gói", 3000, 25));
        return inventory;
    }

    /**
     * Chạy console. Cách dùng:
     *   java PharmacySystem                 -> dùng đơn mẫu
     *   java PharmacySystem duong_dan.png   -> quét ảnh đơn thuốc bằng OCR
     */
    public static void main(String[] args) throws Exception {
        java.nio.file.Path file = java.nio.file.Paths.get("kho_thuoc.csv");
        Inventory inventory = InventoryStorage.loadOrSample(file);
        ScannerService scanner = new ScannerService(new TesseractOcr(), new PrescriptionParser(inventory));
        PaymentService payment = new PaymentService();

        System.out.println("=== HỆ THỐNG BÁN THUỐC TỰ ĐỘNG (NHÓM 09) ===");

        Prescription prescription;
        if (args.length > 0) {
            if (!scanner.isOcrAvailable()) {
                System.out.println("Chưa cài Tesseract OCR nên không đọc được ảnh. Xem HUONG_DAN_CHAY.txt.");
                return;
            }
            ScanResult result = scanner.scan(javax.imageio.ImageIO.read(new java.io.File(args[0])));
            prescription = result.getPrescription();
            result.getWarnings().forEach(w -> System.out.println("⚠ " + w));
        } else {
            prescription = ScannerService.samplePrescription();
        }

        System.out.println("\n📋 Danh sách thuốc theo đơn:");
        for (StockCheckResult r : inventory.checkItems(prescription)) {
            System.out.println("- " + r.getItem().getMedicineName() + " x" + r.getItem().getQuantity()
                    + "  [" + r.getStatus().getLabel() + "]");
        }
        if (prescription.isEmpty()) { System.out.println("Không nhận dạng được thuốc nào."); return; }

        Prescription payable = prescription;
        if (!inventory.checkAvailability(prescription)) {
            payable = inventory.availablePortion(prescription);
            if (payable.isEmpty()) { System.out.println("Xin lỗi, kho không có thuốc nào trong đơn."); return; }
            System.out.println("Kho không đủ toàn bộ đơn, chỉ bán phần còn đủ.");
        }

        Invoice invoice = Invoice.create(payable, inventory);
        System.out.println("\n" + invoice.toReceiptText());

        if (payment.processPayment(invoice, new java.util.Scanner(System.in))) {
            inventory.dispenseMedicines(payable);
            InventoryStorage.save(inventory, file);
            System.out.println("\n" + invoice.toReceiptText());
            System.out.println("🎉 Đã xuất thuốc. Cảm ơn quý khách!");
        } else {
            System.out.println("Giao dịch bị hủy do chưa thanh toán.");
        }
    }
}
