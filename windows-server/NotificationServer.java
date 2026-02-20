import javax.swing.*;
import java.awt.*;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class NotificationServer {

    private static volatile boolean enabled = true;
    private static final Queue<JWindow> activeToasts = new ConcurrentLinkedQueue<>();
    private static final int TOAST_WIDTH  = 360;
    private static final int TOAST_HEIGHT = 120;
    private static final int TOAST_MARGIN = 10;

    public static void setEnabled(boolean val) { enabled = val; }

    // Called by ClipboardServer when a NOTIF| line arrives
    public static void handle(String line) {
        if (!enabled) return;

        String[] parts = line.split("\\|", 6);
        String appName = parts.length > 1 ? parts[1] : "Unknown";
        String title   = parts.length > 2 ? parts[2] : "";
        String text    = parts.length > 3 ? parts[3] : "";
        String key     = parts.length > 5 ? parts[5] : "";

        System.out.println("Notification: [" + appName + "] " + title + " — " + text);
        show(appName, title, text, key);
    }

    private static void show(String appName, String title, String text, String notifKey) {
        SwingUtilities.invokeLater(() -> {
            JWindow toast = new JWindow();
            toast.setAlwaysOnTop(true);

            JPanel panel = new JPanel(new BorderLayout(8, 4));
            panel.setBackground(Color.decode("#1E1E1E"));
            panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.decode("#555555"), 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
            ));

            JPanel accent = new JPanel();
            accent.setPreferredSize(new Dimension(4, TOAST_HEIGHT));
            accent.setBackground(getAppColor(appName));
            panel.add(accent, BorderLayout.WEST);

            JLabel titleLabel = new JLabel(appName + (title.isEmpty() ? "" : "  —  " + title));
            titleLabel.setForeground(Color.WHITE);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

            JLabel textLabel = new JLabel("<html><body style='width:280px'>" + text + "</body></html>");
            textLabel.setForeground(Color.decode("#BBBBBB"));
            textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));

            JPanel replyPanel = new JPanel(new BorderLayout(4, 0));
            replyPanel.setBackground(Color.decode("#1E1E1E"));
            replyPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

            JTextField replyField = new JTextField();
            replyField.setBackground(Color.decode("#2D2D2D"));
            replyField.setForeground(Color.WHITE);
            replyField.setCaretColor(Color.WHITE);
            replyField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            replyField.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

            JButton sendBtn = new JButton("Reply");
            sendBtn.setBackground(getAppColor(appName));
            sendBtn.setForeground(Color.WHITE);
            sendBtn.setBorderPainted(false);
            sendBtn.setFocusPainted(false);
            sendBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
            sendBtn.setPreferredSize(new Dimension(55, 28));

            replyPanel.add(replyField, BorderLayout.CENTER);
            replyPanel.add(sendBtn, BorderLayout.EAST);

            JPanel content = new JPanel(new BorderLayout(4, 4));
            content.setBackground(Color.decode("#1E1E1E"));
            content.add(titleLabel, BorderLayout.NORTH);
            content.add(textLabel, BorderLayout.CENTER);
            content.add(replyPanel, BorderLayout.SOUTH);

            panel.add(content, BorderLayout.CENTER);
            toast.add(panel);

            Runnable doReply = () -> {
                String replyText = replyField.getText().trim();
                if (replyText.isEmpty()) return;
                ClipboardServer.sendToAndroid("REPLY:" + notifKey + ":" + replyText);
                toast.dispose();
                activeToasts.remove(toast);
            };

            sendBtn.addActionListener(e -> doReply.run());
            replyField.addActionListener(e -> doReply.run());

            Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                                  .getDefaultScreenDevice()
                                  .getDefaultConfiguration()
            );
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

            int x = screen.width  - TOAST_WIDTH  - TOAST_MARGIN - insets.right;
            int y = screen.height - insets.bottom - TOAST_MARGIN
                    - (activeToasts.size() + 1) * (TOAST_HEIGHT + TOAST_MARGIN);

            toast.setBounds(x, y, TOAST_WIDTH, TOAST_HEIGHT);
            toast.setVisible(true);
            activeToasts.add(toast);

            Timer timer = new Timer(10000, e -> {
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
                    Math.abs((hash >> 8) % 200) + 55,
                    Math.abs((hash >> 16) % 200) + 55
                );
        }
    }
}