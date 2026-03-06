import javax.swing.*;
import java.awt.*;

public class AndroidFileBrowser {

    static JFrame frame;
    static DefaultListModel<String> model;
    static JList<String> list;
    static JLabel pathLabel;

    static String currentPath = "/storage/emulated/0";

    public static void open() {

        if (frame == null) {

            frame = new JFrame("Android Files");
            frame.setSize(450,550);
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

                        if (item.endsWith("/")) {

                            currentPath = currentPath + "/" + item.replace("/","");
                            requestFolder(currentPath);

                        } else {

                            ClipboardServer.sendToAndroid(
                                    "ANDROID_REQ_DOWNLOAD|" + currentPath + "/" + item
                            );
                        }
                    }
                }
            });

            frame.add(topBar, BorderLayout.NORTH);
            frame.add(new JScrollPane(list), BorderLayout.CENTER);
        }

        frame.setVisible(true);
        requestFolder(currentPath);
    }

    static void goBack() {

        if (currentPath.equals("/storage/emulated/0"))
            return;

        int lastSlash = currentPath.lastIndexOf("/");

        currentPath = currentPath.substring(0, lastSlash);

        requestFolder(currentPath);
    }

    static void requestFolder(String path) {

        pathLabel.setText(path);

        ClipboardServer.sendToAndroid("ANDROID_REQ_LIST|" + path);
    }

    public static void updateList(String message) {

        SwingUtilities.invokeLater(() -> {

            model.clear();

            String data = message.replace("ANDROID_RES_LIST|","");
            String[] items = data.split(";");

            for (String item : items) {

                if (item.isEmpty()) continue;

                String[] parts = item.split(",");

                String name = parts[0];
                String type = parts[1];

                if (type.equals("DIR"))
                    model.addElement(name + "/");
                else
                    model.addElement(name);
            }
        });
    }
}