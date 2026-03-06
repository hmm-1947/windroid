import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;

// ─────────────────────────────────────────────
// Handles PC-side file requests from Android
// (Android browsing PC files)
// ─────────────────────────────────────────────
class FileAccessHandler {

    public static void handleMessage(String message) {
        if (message.equals("FILE_REQ_DRIVES")) {
            StringBuilder response = new StringBuilder("FILE_RES_LIST|");
            for (File root : File.listRoots()) {
                String drive = root.getAbsolutePath().replace("\\", "");
                response.append(drive).append(",DIR;");
            }
            ClipboardServer.sendToAndroid(response.toString());
            return;
        }
        System.out.println("FileAccessHandler received: " + message);

        try {
            // Android requests PC folder listing
            if (message.startsWith("FILE_REQ_LIST|")) {
                String path = message.substring("FILE_REQ_LIST|".length());
                File dir = new File(path);

                if (!dir.exists() || !dir.isDirectory())
                    return;

                StringBuilder response = new StringBuilder("FILE_RES_LIST|");
                // NEW
File[] files = dir.listFiles();
if (files != null) {
    // Sort: folders first, then files, both alphabetically
    java.util.Arrays.sort(files, (a, b) -> {
        if (a.isDirectory() && !b.isDirectory()) return -1;
        if (!a.isDirectory() && b.isDirectory()) return 1;
        return a.getName().compareToIgnoreCase(b.getName());
    });
    for (File f : files) {
        try {
            response.append(f.getName())
                    .append(",")
                    .append(f.isDirectory() ? "DIR" : "FILE")
                    .append(";");
        } catch (Exception ignored) {}
    }
}
                ClipboardServer.sendToAndroid(response.toString());
            }

            // Android requests a file download from PC
            else if (message.startsWith("FILE_REQ_DOWNLOAD|")) {
                String path = message.substring("FILE_REQ_DOWNLOAD|".length());
                File file = new File(path);

                if (!file.exists() || !file.isFile())
                    return;

                System.out.println("Sending file to Android: " + file.getName());

                byte[] data = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    fis.read(data);
                }

                String base64 = java.util.Base64.getEncoder().encodeToString(data);
                ClipboardServer.sendToAndroid("FILE_RES_FILE|" + file.getName() + "|" + base64);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// ─────────────────────────────────────────────
// PC-side browser for Android files
// (PC browsing Android files)
// ─────────────────────────────────────────────
public class AndroidFileBrowser {

    private static JFrame frame;
    private static DefaultListModel<String> model;
    private static JList<String> list;
    private static JLabel pathLabel;
    private static String currentPath = "/storage/emulated/0";
private static String downloadPath = System.getProperty("user.home") + "\\Downloads";
    public static void open() {
        if (frame == null) {
            frame = new JFrame("Android Files");
            frame.setSize(450, 550);
            frame.setLocationRelativeTo(null);
            frame.setLayout(new BorderLayout());

            model = new DefaultListModel<>();
            list = new JList<>(model);
            pathLabel = new JLabel(currentPath);

            JButton backButton = new JButton("⬅ Back");
            backButton.addActionListener(e -> goBack());

            JPanel topBar = new JPanel(new BorderLayout());
            topBar.add(backButton, BorderLayout.WEST);
            topBar.add(pathLabel, BorderLayout.CENTER);

            list.addMouseListener(new java.awt.event.MouseAdapter() {
                public void mouseClicked(java.awt.event.MouseEvent evt) {
                    if (evt.getClickCount() == 2) {
                        String item = list.getSelectedValue();
                        if (item == null)
                            return;

                        if (item.endsWith("/")) {
                            currentPath = currentPath + "/" + item.replace("/", "");
                            requestFolder(currentPath);
                        }
                    }
                }

                public void mousePressed(java.awt.event.MouseEvent evt) {
                    if (SwingUtilities.isRightMouseButton(evt)) {
                        int index = list.locationToIndex(evt.getPoint());
                        if (index < 0)
                            return;
                        list.setSelectedIndex(index);
                        String item = list.getSelectedValue();
                        if (item == null || item.endsWith("/"))
                            return;

                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem downloadItem = new JMenuItem("Download");
                        downloadItem.addActionListener(e -> ClipboardServer.sendToAndroid(
                                "ANDROID_REQ_DOWNLOAD|" + currentPath + "/" + item));
                        menu.add(downloadItem);
                        menu.show(list, evt.getX(), evt.getY());
                    }
                }
            });

            frame.add(topBar, BorderLayout.NORTH);
            frame.add(new JScrollPane(list), BorderLayout.CENTER);
        }

        frame.setVisible(true);
        requestFolder(currentPath);
    }
public static void openSettings(Component parent) {
    JPanel panel = new JPanel(new BorderLayout(8, 8));
    JLabel label = new JLabel("Download location:");
    JTextField pathField = new JTextField(downloadPath, 30);
    JButton browseBtn = new JButton("Browse...");

    browseBtn.addActionListener(e -> {
        JFileChooser chooser = new JFileChooser(downloadPath);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION) {
            pathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    });

    JPanel row = new JPanel(new BorderLayout(4, 0));
    row.add(pathField, BorderLayout.CENTER);
    row.add(browseBtn, BorderLayout.EAST);

    panel.add(label, BorderLayout.NORTH);
    panel.add(row, BorderLayout.CENTER);

    int result = JOptionPane.showConfirmDialog(
        parent, panel, "File Browser Settings",
        JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE
    );

    if (result == JOptionPane.OK_OPTION) {
        downloadPath = pathField.getText().trim();
    }
}
    private static void goBack() {
        if (currentPath.equals("/storage/emulated/0"))
            return;
        int lastSlash = currentPath.lastIndexOf("/");
        currentPath = currentPath.substring(0, lastSlash);
        requestFolder(currentPath);
    }

    private static void requestFolder(String path) {
        pathLabel.setText(path);
        ClipboardServer.sendToAndroid("ANDROID_REQ_LIST|" + path);
    }
public static String getDownloadPath() {
    return downloadPath;
}
    public static void updateList(String message) {
        SwingUtilities.invokeLater(() -> {
            model.clear();
            String data = message.replace("ANDROID_RES_LIST|", "");
            String[] items = data.split(";");

            for (String item : items) {
                if (item.isEmpty())
                    continue;
                String[] parts = item.split(",", 2);
                if (parts.length < 2)
                    continue;

                String name = parts[0];
                String type = parts[1];
                model.addElement(type.equals("DIR") ? name + "/" : name);
            }
        });
    }
}