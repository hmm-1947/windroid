import javax.swing.*;
import java.awt.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotificationServer {

    private static volatile boolean enabled = true;
    private static final Queue<JWindow> activeToasts = new ConcurrentLinkedQueue<>();
    private static final int TOAST_WIDTH  = 360;
    private static final int TOAST_HEIGHT = 90;
    private static final int TOAST_MARGIN = 10;

    public static void setEnabled(boolean val) { enabled = val; }

    public static void handle(String line) {
        if (!enabled) return;

        int lastPipe = line.lastIndexOf('|');
        if (lastPipe < 0) return;

        String iconBase64 = line.substring(lastPipe + 1).trim();
        String[] parts = line.substring(0, lastPipe).split("\\|", 7);

        String appName    = parts.length > 1 ? parts[1] : "Unknown";
        String chatTitle  = parts.length > 2 ? parts[2] : "";
        String senderName = parts.length > 3 ? parts[3] : "";
        String text       = parts.length > 4 ? parts[4] : "";

        show(appName, chatTitle, senderName, text, iconBase64);
    }

    private static void show(String appName, String chatTitle, String senderName,
                          String text, String iconBase64) {
    SwingUtilities.invokeLater(() -> {

        JWindow toast = new JWindow();
        toast.setAlwaysOnTop(true);

        boolean isGroup = !chatTitle.isEmpty() && !senderName.isEmpty()
                          && !chatTitle.equals(senderName);

        // ── Outer panel ─────────────────────────────────────────────────
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setBackground(Color.decode("#1E1E1E"));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#555555"), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        // ── Profile picture (left) ───────────────────────────────────────
        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(new Dimension(48, 48));
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        if (!iconBase64.isEmpty()) {
            try {
                byte[] imageBytes = java.util.Base64.getDecoder().decode(iconBase64);
                Image scaled = new ImageIcon(imageBytes).getImage()
                        .getScaledInstance(48, 48, Image.SCALE_SMOOTH);
                iconLabel.setIcon(new ImageIcon(scaled));
            } catch (Exception e) {
                System.err.println("Icon decode failed: " + e.getMessage());
            }
        }
        panel.add(iconLabel, BorderLayout.WEST);

        // ── Right side ───────────────────────────────────────────────────
        JPanel textPanel = new JPanel(new BorderLayout(0, 3));
        textPanel.setBackground(Color.decode("#1E1E1E"));

        // Group name at top (only for group messages)
        if (isGroup) {
            JLabel groupLabel = new JLabel("👥 " + chatTitle);
            groupLabel.setForeground(Color.decode("#888888"));
            groupLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            textPanel.add(groupLabel, BorderLayout.NORTH);
        }

        // Center: sender name + message
        JPanel centerPanel = new JPanel(new BorderLayout(0, 3));
        centerPanel.setBackground(Color.decode("#1E1E1E"));

        String displayName = !senderName.isEmpty() ? senderName
                           : !chatTitle.isEmpty()  ? chatTitle
                           : appName;

        JLabel nameLabel = new JLabel(displayName);
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        JLabel textLabel = new JLabel(
                "<html><body style='width:250px'>" + text + "</body></html>");
        textLabel.setForeground(Color.decode("#BBBBBB"));
        textLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));

        centerPanel.add(nameLabel, BorderLayout.NORTH);
        centerPanel.add(textLabel, BorderLayout.CENTER);

        textPanel.add(centerPanel, BorderLayout.CENTER);
        panel.add(textPanel, BorderLayout.CENTER);
        toast.add(panel);

        // ── Size: taller for group messages ─────────────────────────────
        int height = isGroup ? 105 : 90;

        // ── Position: bottom-right ───────────────────────────────────────
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int x = screen.width  - TOAST_WIDTH  - TOAST_MARGIN;
        int y = screen.height - height        - TOAST_MARGIN - 40;

        toast.setBounds(x, y, TOAST_WIDTH, height);
        toast.setVisible(true);
        activeToasts.add(toast);

        Timer timer = new Timer(5000, e -> {
            toast.dispose();
            activeToasts.remove(toast);
        });
        timer.setRepeats(false);
        timer.start();
    });
}

    private static Color getAppColor(String appName) {
        switch (appName.toLowerCase()) {
            case "whatsapp":  return Color.decode("#25D366");
            case "telegram":  return Color.decode("#0088CC");
            case "instagram": return Color.decode("#E1306C");
            case "gmail":     return Color.decode("#EA4335");
            case "messages":  return Color.decode("#1A73E8");
            default:
                int hash = appName.hashCode();
                return new Color(
                    Math.abs(hash % 200) + 55,
                    Math.abs((hash >> 8)  % 200) + 55,
                    Math.abs((hash >> 16) % 200) + 55
                );
        }
    }
}