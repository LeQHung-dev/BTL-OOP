import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Toàn bộ nội dung giao diện của hệ thống bán thuốc (đặt trong PharmacyGUI). */
public class PharmacyPanel extends JPanel {
    private static final Color PINE = new Color(0x0E5A45);
    private static final Color OK_BG = new Color(0xE2F3E7), OK_FG = new Color(0x1B6B3A);
    private static final Color LOW_BG = new Color(0xFFF3D6), LOW_FG = new Color(0x8A5A00);
    private static final Color BAD_BG = new Color(0xFDE8E6), BAD_FG = new Color(0xB3261E);

    // ----- Dữ liệu và dịch vụ (phần nghiệp vụ) -----
    final Inventory inventory;
    private final Path inventoryFile;
    final PaymentService payment = new PaymentService();
    private final ScannerService scanner;
    Prescription prescription = new Prescription();
    private BufferedImage currentImage;

    // ----- Thành phần giao diện -----
    private final ImagePanel imagePanel = new ImagePanel(this::loadImageFile);
    private final JTextArea rawText = new JTextArea(7, 24);
    private final JLabel statusLabel = new JLabel(" ");
    private final AppButton btnScan = new AppButton("Quét đơn (OCR)");
    private final RxModel rxModel = new RxModel();
    private final JTable rxTable = new JTable(rxModel);
    private final InvModel invModel = new InvModel();
    private final JLabel totalLabel = new JLabel("0 VND");
    private final JLabel noteLabel = new JLabel(" ");
    private final AppButton btnBank = new AppButton("Thanh toán chuyển khoản (QR)");
    private final AppButton btnCash = new AppButton("Thanh toán tiền mặt");
    private final JTextArea historyArea = new JTextArea();

    public PharmacyPanel(Inventory inventory, Path inventoryFile) {
        this.inventory = inventory;
        this.inventoryFile = inventoryFile;
        this.scanner = new ScannerService(new TesseractOcr(), new PrescriptionParser(inventory));

        setLayout(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(buildLeft(), BorderLayout.WEST);
        add(buildTabs(), BorderLayout.CENTER);
        refreshAll();
    }

    // =====================================================================
    //  Dựng giao diện
    // =====================================================================

    private JComponent buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(PINE);
        p.setBorder(BorderFactory.createEmptyBorder(12, 18, 12, 18));
        JLabel title = new JLabel("HỆ THỐNG BÁN THUỐC TỰ ĐỘNG");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JLabel sub = new JLabel("Lập trình hướng đối tượng · Nhóm 09 · Quét đơn → Kiểm tra kho → Tính tiền → Thanh toán");
        sub.setForeground(new Color(0xCFE6DC));
        p.add(title, BorderLayout.NORTH);
        p.add(sub, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildLeft() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 6));
        p.setPreferredSize(new Dimension(380, 100));

        JLabel h = sectionTitle("1. Đơn thuốc của bệnh nhân");
        p.add(h, BorderLayout.NORTH);

        JPanel mid = new JPanel(new BorderLayout(0, 8));
        mid.add(imagePanel, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 6, 6));
        JButton bFile = new AppButton("Chọn ảnh...");
        JButton bCam = new AppButton("Chụp bằng camera");
        JButton bPaste = new AppButton("Dán ảnh");
        JButton bDemo = new AppButton("Dùng đơn mẫu");
        bFile.addActionListener(e -> chooseFile());
        bCam.addActionListener(e -> captureCamera());
        bPaste.addActionListener(e -> pasteImage());
        bDemo.addActionListener(e -> { rawText.setText(ScannerService.demoText()); analyzeText(); });
        buttons.add(bFile); buttons.add(bCam); buttons.add(bPaste); buttons.add(bDemo);
        mid.add(buttons, BorderLayout.SOUTH);
        p.add(mid, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 6));
        btnScan.setEnabled(false);
        stylePrimary(btnScan);
        btnScan.addActionListener(e -> runScan());
        bottom.add(btnScan, BorderLayout.NORTH);

        rawText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        rawText.setLineWrap(true);
        JScrollPane sp = new JScrollPane(rawText);
        sp.setBorder(BorderFactory.createTitledBorder("Văn bản đọc được (có thể sửa hoặc tự gõ)"));
        bottom.add(sp, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(0, 4));
        JButton bAnalyze = new AppButton("Phân tích văn bản");
        bAnalyze.addActionListener(e -> analyzeText());
        south.add(bAnalyze, BorderLayout.NORTH);
        statusLabel.setForeground(new Color(0x5D6B64));
        south.add(statusLabel, BorderLayout.SOUTH);
        bottom.add(south, BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Đơn thuốc và thanh toán", buildRxTab());
        tabs.addTab("Kho thuốc", buildInventoryTab());
        tabs.addTab("Lịch sử bán", buildHistoryTab());
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBorder(BorderFactory.createEmptyBorder(12, 6, 12, 12));
        wrap.add(tabs, BorderLayout.CENTER);
        return wrap;
    }

    private JComponent buildRxTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        top.add(sectionTitle("2. Danh sách thuốc và tồn kho"));
        top.add(Box.createHorizontalStrut(20));
        JButton add = new AppButton("Thêm thuốc"), del = new AppButton("Xóa dòng chọn"), clear = new AppButton("Xóa cả đơn");
        add.addActionListener(e -> addMedicineToPrescription());
        del.addActionListener(e -> {
            int r = rxTable.getSelectedRow();
            if (r >= 0 && r < prescription.size()) { prescription.removeAt(r); refreshAll(); }
        });
        clear.addActionListener(e -> { prescription.clear(); refreshAll(); });
        top.add(add); top.add(del); top.add(clear);
        p.add(top, BorderLayout.NORTH);

        rxTable.setRowHeight(30);
        rxTable.getTableHeader().setReorderingAllowed(false);
        rxTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {40, 170, 70, 90, 60, 120, 110};
        for (int i = 0; i < widths.length; i++) rxTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        rxTable.getColumnModel().getColumn(5).setCellRenderer(new StatusRenderer());
        rxTable.getColumnModel().getColumn(0).setCellRenderer(centered());
        rxTable.getColumnModel().getColumn(2).setCellRenderer(centered());
        rxTable.getColumnModel().getColumn(3).setCellRenderer(rightAligned());
        rxTable.getColumnModel().getColumn(4).setCellRenderer(centered());
        rxTable.getColumnModel().getColumn(6).setCellRenderer(rightAligned());
        p.add(new JScrollPane(rxTable), BorderLayout.CENTER);

        // Khu thanh toán
        JPanel bill = new JPanel(new BorderLayout(10, 6));
        bill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xDDE2DB)),
                BorderFactory.createEmptyBorder(10, 0, 0, 0)));
        JPanel left = new JPanel(new BorderLayout());
        left.add(sectionTitle("3. Tổng tiền cần thanh toán"), BorderLayout.NORTH);
        totalLabel.setFont(totalLabel.getFont().deriveFont(Font.BOLD, 28f));
        totalLabel.setForeground(PINE);
        left.add(totalLabel, BorderLayout.CENTER);
        left.add(noteLabel, BorderLayout.SOUTH);
        bill.add(left, BorderLayout.CENTER);

        JPanel pay = new JPanel(new GridLayout(2, 1, 0, 6));
        stylePrimary(btnBank);
        btnBank.addActionListener(e -> checkout(payment.getBankTransfer()));
        btnCash.addActionListener(e -> checkout(payment.getCash()));
        pay.add(btnBank); pay.add(btnCash);
        bill.add(pay, BorderLayout.EAST);
        p.add(bill, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildInventoryTab() {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        JTable t = new JTable(invModel);
        t.setRowHeight(28);
        t.getColumnModel().getColumn(2).setCellRenderer(rightAligned());
        t.getColumnModel().getColumn(3).setCellRenderer(new StockRenderer());
        p.add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel b = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JButton restock = new AppButton("Nhập thêm hàng"), newMed = new AppButton("Thêm thuốc mới");
        restock.addActionListener(e -> {
            int r = t.getSelectedRow();
            if (r < 0) { JOptionPane.showMessageDialog(this, "Hãy chọn một thuốc trong bảng trước."); return; }
            Medicine m = invModel.get(r);
            Integer n = askNumber("Nhập thêm bao nhiêu " + m.getUnit() + " " + m.getName() + "?", 1, 100000, 50);
            if (n != null) { m.addStock(n); saveInventory(); refreshAll(); }
        });
        newMed.addActionListener(e -> addNewMedicine());
        b.add(restock); b.add(newMed);
        b.add(new JLabel("   Kho được lưu vào file " + inventoryFile.getFileName() + " sau mỗi thay đổi."));
        p.add(b, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildHistoryTab() {
        historyArea.setEditable(false);
        historyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        historyArea.setText("Chưa có hóa đơn nào trong phiên làm việc này.\n");
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        p.add(new JScrollPane(historyArea), BorderLayout.CENTER);
        return p;
    }

    private static JLabel sectionTitle(String s) {
        JLabel l = new JLabel(s);
        l.setFont(l.getFont().deriveFont(Font.BOLD, 15f));
        return l;
    }

    private static void stylePrimary(AppButton b) { b.setPrimary(true); }

    private static DefaultTableCellRenderer centered() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.CENTER);
        return r;
    }

    private static DefaultTableCellRenderer rightAligned() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }

    // =====================================================================
    //  Nhận ảnh đơn thuốc
    // =====================================================================

    private void chooseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Chọn ảnh đơn thuốc");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Ảnh (png, jpg, bmp, gif)", "png", "jpg", "jpeg", "bmp", "gif"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) loadImageFile(fc.getSelectedFile());
    }

    void loadImageFile(File f) {
        try {
            BufferedImage img = javax.imageio.ImageIO.read(f);
            if (img == null) throw new IOException("Định dạng ảnh không được hỗ trợ (dùng png, jpg, bmp, gif)");
            loadImage(img, f.getName());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không mở được ảnh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    void loadImage(BufferedImage img, String source) {
        currentImage = img;
        imagePanel.setImage(img);
        btnScan.setEnabled(true);
        setStatus("Đã nhận ảnh: " + source + ". Bấm \"Quét đơn\" để đọc.");
    }

    private void pasteImage() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
            Image im = (Image) data;
            BufferedImage bi = new BufferedImage(im.getWidth(null), im.getHeight(null), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = bi.createGraphics();
            g.drawImage(im, 0, 0, Color.WHITE, null);
            g.dispose();
            loadImage(bi, "ảnh dán từ clipboard");
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Clipboard chưa có ảnh. Hãy sao chép (Copy) một ảnh rồi bấm lại.");
        }
    }

    /** Chụp bằng camera: mở trang chụp ảnh trong trình duyệt, nhận ảnh gửi về rồi nạp vào chương trình. */
    private void captureCamera() {
        final CameraSession session = new CameraSession();
        final String url;
        try {
            url = session.start();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không khởi động được chức năng chụp ảnh: " + e.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Chụp bằng camera", Dialog.ModalityType.MODELESS);
        final boolean[] cancelled = {false};
        JTextArea info = new JTextArea("Trình duyệt sẽ mở trang chụp ảnh. Hãy cho phép dùng camera, đặt đơn thuốc vào khung,\n"
                + "bấm \"Chụp ảnh\" rồi \"Dùng ảnh này\". Ảnh sẽ tự hiện trong chương trình.\n\n"
                + "Nếu trình duyệt không tự mở, hãy mở địa chỉ sau (Chrome hoặc Edge):");
        info.setEditable(false);
        info.setOpaque(false);
        JTextField link = new JTextField(url);
        link.setEditable(false);
        AppButton reopen = new AppButton("Mở lại trình duyệt");
        AppButton cancel = new AppButton("Hủy");
        reopen.addActionListener(e -> openBrowser(url));
        cancel.addActionListener(e -> { cancelled[0] = true; session.close(); dlg.dispose(); });
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setBorder(BorderFactory.createEmptyBorder(14, 14, 6, 14));
        top.add(info, BorderLayout.CENTER);
        top.add(link, BorderLayout.SOUTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.add(reopen);
        buttons.add(cancel);
        dlg.add(top, BorderLayout.CENTER);
        dlg.add(buttons, BorderLayout.SOUTH);
        dlg.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancel.doClick(); }
        });
        dlg.pack();
        dlg.setSize(Math.max(dlg.getWidth(), 560), dlg.getHeight());
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);

        if (!openBrowser(url)) info.append("\n(Không mở được trình duyệt tự động.)");
        setStatus("Đang chờ ảnh từ camera (trong trình duyệt)...");
        new SwingWorker<BufferedImage, Void>() {
            @Override protected BufferedImage doInBackground() throws Exception { return session.waitForImage(300); }
            @Override protected void done() {
                dlg.dispose();
                session.close();
                try {
                    loadImage(get(), "camera");
                } catch (Exception e) {
                    if (cancelled[0]) { setStatus("Đã hủy chụp ảnh."); return; }
                    setStatus("Chụp camera thất bại.");
                    JOptionPane.showMessageDialog(PharmacyPanel.this, "Không nhận được ảnh từ camera: " + rootMessage(e), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static boolean openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
                return true;
            }
        } catch (Exception e) { /* người dùng tự mở địa chỉ */ }
        return false;
    }

    // =====================================================================
    //  Quét và phân tích đơn
    // =====================================================================

    private void runScan() {
        if (currentImage == null) return;
        if (!scanner.isOcrAvailable()) {
            int r = JOptionPane.showOptionDialog(this,
                "Máy chưa cài Tesseract OCR nên chưa đọc được chữ trong ảnh.\n"
              + "Cách khắc phục: cài Tesseract (xem HUONG_DAN_CHAY.txt), hoặc tự gõ nội dung đơn vào ô \"Văn bản đọc được\" rồi bấm \"Phân tích văn bản\".",
                "Chưa có OCR", JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                new String[]{"Đóng", "Điền văn bản mẫu"}, "Đóng");
            if (r == 1) { rawText.setText(ScannerService.demoText()); analyzeText(); }
            return;
        }
        btnScan.setEnabled(false);
        setStatus("Đang đọc đơn thuốc, vui lòng chờ...");
        new SwingWorker<ScanResult, Void>() {
            @Override protected ScanResult doInBackground() throws Exception { return scanner.scan(currentImage); }
            @Override protected void done() {
                btnScan.setEnabled(true);
                try { applyResult(get(), true); }
                catch (Exception e) {
                    setStatus("Đọc đơn thất bại.");
                    JOptionPane.showMessageDialog(PharmacyPanel.this, "Không đọc được đơn: " + rootMessage(e), "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    void analyzeText() { applyResult(scanner.scanText(rawText.getText()), false); }

    void applyResult(ScanResult r, boolean updateRawText) {
        if (updateRawText) rawText.setText(r.getRawText());
        prescription = r.getPrescription();
        refreshAll();
        StringBuilder msg = new StringBuilder();
        if (prescription.isEmpty()) msg.append("Không nhận dạng được thuốc nào. Hãy sửa văn bản rồi phân tích lại.");
        else msg.append("Nhận dạng được ").append(prescription.size()).append(" loại thuốc.");
        setStatus(msg.toString());
        if (!r.getWarnings().isEmpty()) {
            JOptionPane.showMessageDialog(this, String.join("\n", r.getWarnings()), "Cần kiểm tra lại", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void setStatus(String s) { statusLabel.setText("<html>" + s + "</html>"); }

    private static String rootMessage(Throwable e) {
        while (e.getCause() != null) e = e.getCause();
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    // =====================================================================
    //  Chỉnh sửa đơn và kho
    // =====================================================================

    private void addMedicineToPrescription() {
        JComboBox<String> box = new JComboBox<>();
        for (Medicine m : inventory.getAllMedicines()) box.addItem(m.getName());
        JSpinner spin = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JPanel p = new JPanel(new GridLayout(2, 2, 6, 6));
        p.add(new JLabel("Thuốc:")); p.add(box);
        p.add(new JLabel("Số lượng:")); p.add(spin);
        if (JOptionPane.showConfirmDialog(this, p, "Thêm thuốc vào đơn", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION
                && box.getSelectedItem() != null) {
            prescription.addItem((String) box.getSelectedItem(), (Integer) spin.getValue());
            refreshAll();
        }
    }

    private void addNewMedicine() {
        JTextField name = new JTextField(), unit = new JTextField("viên");
        JTextField price = new JTextField(), qty = new JTextField("100");
        JPanel p = new JPanel(new GridLayout(4, 2, 6, 6));
        p.add(new JLabel("Tên thuốc:")); p.add(name);
        p.add(new JLabel("Đơn vị:")); p.add(unit);
        p.add(new JLabel("Đơn giá (VND):")); p.add(price);
        p.add(new JLabel("Số lượng nhập:")); p.add(qty);
        if (JOptionPane.showConfirmDialog(this, p, "Thêm thuốc mới", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        try {
            Medicine m = new Medicine(name.getText(), unit.getText().trim(), Double.parseDouble(price.getText().trim()), Integer.parseInt(qty.getText().trim()));
            inventory.addMedicine(m);
            saveInventory();
            refreshAll();
        } catch (RuntimeException e) {
            JOptionPane.showMessageDialog(this, "Dữ liệu không hợp lệ: " + (e instanceof NumberFormatException ? "giá và số lượng phải là số" : e.getMessage()),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Integer askNumber(String message, int min, int max, int initial) {
        JSpinner spin = new JSpinner(new SpinnerNumberModel(initial, min, max, 1));
        int r = JOptionPane.showConfirmDialog(this, new Object[]{message, spin}, "Nhập số lượng", JOptionPane.OK_CANCEL_OPTION);
        return r == JOptionPane.OK_OPTION ? (Integer) spin.getValue() : null;
    }

    private void saveInventory() {
        try { InventoryStorage.save(inventory, inventoryFile); }
        catch (IOException e) { setStatus("Không lưu được kho: " + e.getMessage()); }
    }

    // =====================================================================
    //  Cập nhật màn hình
    // =====================================================================

    void refreshAll() {
        rxModel.reload();
        invModel.fireTableDataChanged();

        Prescription payable = inventory.availablePortion(prescription);
        double total = payable.isEmpty() ? 0 : inventory.calculateTotal(payable);
        totalLabel.setText(Invoice.formatVnd(total));

        int problems = 0;
        for (StockCheckResult r : rxModel.rows) if (!r.isEnough()) problems++;
        if (prescription.isEmpty()) noteLabel.setText("<html>Chưa có thuốc trong đơn.</html>");
        else if (payable.isEmpty()) noteLabel.setText("<html><font color='#B3261E'>Kho không có thuốc nào trong đơn này.</font></html>");
        else if (problems > 0) noteLabel.setText("<html><font color='#8A5A00'>" + problems + " thuốc không đủ hàng: chỉ tính tiền phần kho còn đáp ứng được.</font></html>");
        else noteLabel.setText("<html><font color='#1B6B3A'>Kho đáp ứng đủ toàn bộ đơn thuốc.</font></html>");

        boolean can = !payable.isEmpty();
        btnBank.setEnabled(can);
        btnCash.setEnabled(can);
    }

    // =====================================================================
    //  Thanh toán và xuất thuốc
    // =====================================================================

    private void checkout(PaymentStrategy method) {
        Prescription payable = inventory.availablePortion(prescription);
        if (payable.isEmpty()) return;
        if (!inventory.checkAvailability(prescription)) {
            int r = JOptionPane.showConfirmDialog(this,
                    "Kho không đủ một số thuốc trong đơn.\nChỉ thanh toán và xuất phần thuốc còn đủ trong kho?",
                    "Thuốc không đủ", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (r != JOptionPane.YES_OPTION) return;
        }
        Invoice invoice = Invoice.create(payable, inventory);
        if (!showPaymentDialog(method, invoice)) return;

        completeSale(payable, invoice, method);
        showDispenseDialog(invoice);
        startNewCustomer(invoice);
    }

    /** Phần nghiệp vụ sau khi đã nhận tiền: xác nhận thanh toán, TRỪ KHO, lưu kho, ghi lịch sử. */
    void completeSale(Prescription payable, Invoice invoice, PaymentStrategy method) {
        payment.confirmPayment(invoice, method);
        inventory.dispenseMedicines(payable);
        saveInventory();
        historyArea.append("\n" + invoice.toReceiptText() + "========================================\n");
    }

    /** Làm sạch màn hình để phục vụ khách tiếp theo. */
    void startNewCustomer(Invoice lastInvoice) {
        prescription = new Prescription();
        currentImage = null;
        imagePanel.setImage(null);
        rawText.setText("");
        btnScan.setEnabled(false);
        setStatus("Đã bán xong hóa đơn " + lastInvoice.getCode() + ".");
        refreshAll();
    }

    /** Hiện hướng dẫn thanh toán (kèm mã QR nếu có). Trả về true khi thu ngân xác nhận đã nhận tiền. */
    private boolean showPaymentDialog(PaymentStrategy method, Invoice invoice) {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        JTextArea info = new JTextArea(method.prepare(invoice));
        info.setEditable(false);
        info.setOpaque(false);
        info.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));

        String qrText = method.getQrPayload(invoice);
        if (qrText != null) {
            try {
                JLabel qrLabel = new JLabel(new ImageIcon(QrImage.generate(qrText, 280)));
                qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
                panel.add(qrLabel, BorderLayout.CENTER);
            } catch (RuntimeException ex) {
                info.append("\n(Không tạo được mã QR: " + ex.getMessage() + ")");
            }
        }
        panel.add(info, BorderLayout.SOUTH);

        String[] buttons = {"Đã nhận được tiền", "Hủy"};
        int r = JOptionPane.showOptionDialog(this, panel, method.getName() + " - " + invoice.getCode(),
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, buttons, buttons[1]);
        return r == 0;
    }

    /** Hộp thoại mô phỏng máy nhả thuốc: lần lượt báo từng loại thuốc đã xuất. */
    private void showDispenseDialog(Invoice invoice) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), "Đang xuất thuốc", Dialog.ModalityType.APPLICATION_MODAL);
        JTextArea area = new JTextArea(10, 34);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        area.setText("Thanh toán thành công. Đang nhả thuốc...\n\n");
        JButton close = new AppButton("Đóng");
        close.setEnabled(false);
        close.addActionListener(e -> d.dispose());
        d.add(new JScrollPane(area), BorderLayout.CENTER);
        JPanel south = new JPanel();
        south.add(close);
        d.add(south, BorderLayout.SOUTH);
        d.pack();
        d.setLocationRelativeTo(this);

        List<InvoiceLine> lines = invoice.getLines();
        int[] next = {0};
        Timer t = new Timer(600, null);
        t.addActionListener(e -> {
            if (next[0] < lines.size()) {
                InvoiceLine l = lines.get(next[0]++);
                area.append("✓ " + l.getMedicineName() + " × " + l.getQuantity() + " " + l.getUnit() + "\n");
            } else {
                t.stop();
                area.append("\nĐã xuất đủ thuốc. Cảm ơn quý khách!");
                close.setEnabled(true);
            }
        });
        t.start();
        d.setVisible(true);
    }

    // =====================================================================
    //  Mô hình dữ liệu cho các bảng
    // =====================================================================

    private class RxModel extends AbstractTableModel {
        private final String[] cols = {"STT", "Tên thuốc", "Số lượng", "Đơn giá", "Kho", "Tình trạng", "Thành tiền"};
        List<StockCheckResult> rows = new java.util.ArrayList<>();

        void reload() { rows = inventory.checkItems(prescription); fireTableDataChanged(); }
        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public boolean isCellEditable(int r, int c) { return c == 2; }   // chỉ sửa được cột Số lượng

        @Override public Object getValueAt(int r, int c) {
            StockCheckResult x = rows.get(r);
            Medicine m = x.getMedicine();
            int qty = x.getItem().getQuantity();
            switch (c) {
                case 0: return r + 1;
                case 1: return x.getItem().getMedicineName();
                case 2: return qty;
                case 3: return m == null ? "-" : String.format("%,.0f", m.getPrice());
                case 4: return m == null ? "-" : x.getInStock();
                case 5: return x.getStatus() == StockStatus.ENOUGH ? x.getStatus().getLabel()
                        : x.getStatus().getLabel() + (m == null ? "" : " (thiếu " + x.getMissing() + ")");
                default: return m == null ? "-" : String.format("%,.0f", m.getPrice() * Math.min(qty, x.getInStock()));
            }
        }

        @Override public void setValueAt(Object v, int r, int c) {
            try {
                int n = Integer.parseInt(String.valueOf(v).trim());
                if (n > 0) prescription.updateQuantity(r, n);
            } catch (NumberFormatException ignored) { /* giữ nguyên số cũ */ }
            refreshAll();
        }
    }

    private class InvModel extends AbstractTableModel {
        private final String[] cols = {"Tên thuốc", "Đơn vị", "Đơn giá (VND)", "Tồn kho"};
        Medicine get(int r) { return new java.util.ArrayList<>(inventory.getAllMedicines()).get(r); }
        @Override public int getRowCount() { return inventory.getAllMedicines().size(); }
        @Override public int getColumnCount() { return cols.length; }
        @Override public String getColumnName(int c) { return cols[c]; }
        @Override public Object getValueAt(int r, int c) {
            Medicine m = get(r);
            switch (c) {
                case 0: return m.getName();
                case 1: return m.getUnit();
                case 2: return String.format("%,.0f", m.getPrice());
                default: return m.getStockQuantity();
            }
        }
    }

    /** Tô màu ô "Tình trạng" theo trạng thái kho. */
    private class StatusRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(SwingConstants.CENTER);
            if (row < rxModel.rows.size() && !sel) {
                switch (rxModel.rows.get(row).getStatus()) {
                    case ENOUGH: setBackground(OK_BG); setForeground(OK_FG); break;
                    case INSUFFICIENT: setBackground(LOW_BG); setForeground(LOW_FG); break;
                    default: setBackground(BAD_BG); setForeground(BAD_FG); break;
                }
            }
            return this;
        }
    }

    /** Cảnh báo màu cho thuốc sắp hết (dưới 10) hoặc đã hết. */
    private static class StockRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setHorizontalAlignment(SwingConstants.RIGHT);
            int n = v instanceof Integer ? (Integer) v : 0;
            if (!sel) {
                if (n == 0) { setBackground(BAD_BG); setForeground(BAD_FG); }
                else if (n < 10) { setBackground(LOW_BG); setForeground(LOW_FG); }
                else { setBackground(Color.WHITE); setForeground(Color.BLACK); }
            }
            return this;
        }
    }
}
