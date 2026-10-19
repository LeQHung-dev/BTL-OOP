import javax.swing.*;
import javax.swing.plaf.FontUIResource;
import java.awt.*;
import java.nio.file.Paths;
import java.util.Enumeration;

/** Cửa sổ chính của chương trình. Chạy file này để mở giao diện. */
public class PharmacyGUI extends JFrame {

    public PharmacyGUI() {
        super("Hệ thống Bán thuốc Tự động - Nhóm 09");
        java.nio.file.Path file = Paths.get("kho_thuoc.csv");
        setContentPane(new PharmacyPanel(InventoryStorage.loadOrSample(file), file));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
    }

    private static void setUIFont(Font f) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, new FontUIResource(f));
        }
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) { }
        setUIFont(new Font("Segoe UI", Font.PLAIN, 14));
        SwingUtilities.invokeLater(() -> new PharmacyGUI().setVisible(true));
    }
}
