import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;

/** Khung xem trước ảnh đơn thuốc, co giãn vừa khung, hỗ trợ kéo-thả file ảnh vào. */
public class ImagePanel extends JPanel {
    private BufferedImage image;

    public ImagePanel(Consumer<File> onFileDropped) {
        setPreferredSize(new Dimension(320, 260));
        setBackground(new Color(0xF4F6F2));
        setBorder(BorderFactory.createLineBorder(new Color(0xB8C4BC)));
        setTransferHandler(new TransferHandler() {
            @Override public boolean canImport(TransferSupport s) { return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor); }
            @Override public boolean importData(TransferSupport s) {
                try {
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!files.isEmpty()) { onFileDropped.accept(files.get(0)); return true; }
                } catch (Exception e) { /* bỏ qua */ }
                return false;
            }
        });
    }

    public void setImage(BufferedImage img) { this.image = img; repaint(); }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        if (image == null) {
            g.setColor(new Color(0x5D6B64));
            String[] lines = {"Chưa có ảnh đơn thuốc", "Chọn ảnh, dán ảnh hoặc kéo-thả file vào đây"};
            FontMetrics fm = g.getFontMetrics();
            for (int i = 0; i < lines.length; i++)
                g.drawString(lines[i], (w - fm.stringWidth(lines[i])) / 2, h / 2 - 6 + i * (fm.getHeight() + 2));
        } else {
            double s = Math.min((w - 12.0) / image.getWidth(), (h - 12.0) / image.getHeight());
            int dw = (int) (image.getWidth() * s), dh = (int) (image.getHeight() * s);
            g.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
        }
        g.dispose();
    }
}
