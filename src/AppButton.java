import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Nút bấm tự vẽ, hiển thị giống nhau trên mọi hệ điều hành (nút mặc định của Windows bỏ qua màu nền
 * nên chữ trắng trên nền trắng sẽ không đọc được).
 * Nút chính: nền xanh, chữ trắng. Nút phụ: nền trắng, viền và chữ xanh.
 */
public class AppButton extends JButton {
    private static final Color PINE = new Color(0x0E5A45), PINE_HOVER = new Color(0x0A4533);
    private boolean primary = false;
    private boolean hover = false;

    public AppButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
        });
    }

    public void setPrimary(boolean primary) { this.primary = primary; repaint(); }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(getFont().deriveFont(Font.BOLD));
        return new Dimension(fm.stringWidth(getText()) + 32, 38);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight();
        boolean on = isEnabled();
        Color fill, border, text;
        if (primary) {
            fill = on ? (hover ? PINE_HOVER : PINE) : new Color(0xD5DBD7);
            border = fill;
            text = on ? Color.WHITE : new Color(0x6F7A73);
        } else {
            fill = on ? (hover ? new Color(0xE3F0EA) : Color.WHITE) : new Color(0xF2F4F1);
            border = on ? PINE : new Color(0xC3CBC6);
            text = on ? PINE : new Color(0x8A948E);
        }
        g.setColor(fill);
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(border);
        g.drawRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setFont(getFont().deriveFont(Font.BOLD));
        FontMetrics fm = g.getFontMetrics();
        g.setColor(text);
        g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}
