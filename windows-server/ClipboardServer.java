import java.io.*;
import java.net.*;

import javax.swing.SwingUtilities;

public class ClipboardServer {

    private static volatile boolean clipboardEnabled = true;
    private static volatile PrintWriter androidWriter = null;
    private static int lastBattery = -1;
    private static String lastMode = "";
    private static boolean lastCharging = false;
    private static boolean lastBt = false;

    public static void setClipboardEnabled(boolean val) {
        clipboardEnabled = val;
    }

    public static void sendToAndroid(String message) {
        if (androidWriter != null) {
            androidWriter.println(message);
            System.out.println("Sent to Android: " + message);
        } else {
            System.err.println("No Android connection");
        }
    }

    public static void start(int port) {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                serverSocket.setReuseAddress(true);
                System.out.println("Server listening on port " + port);
                while (true) {
                    Socket client = serverSocket.accept();
                    new Thread(() -> handleClient(client)).start();
                }
            } catch (BindException e) {
                System.err.println("Port already in use!");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void handleClient(Socket client) {
        String incomingIp = client.getInetAddress().getHostAddress();
        String pairedFingerprint = WindowsServer.getPairedFingerprint();

        try {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(client.getInputStream()));

            String hello = in.readLine();
            if (hello == null || !hello.startsWith("HELLO|")) {
                System.out.println("Unknown client — no handshake");
                return;
            }

            String[] helloParts = hello.split("\\|", 3);
            String phoneName = helloParts.length > 1 ? helloParts[1] : "Unknown";
            String fingerprint = helloParts.length > 2 ? helloParts[2] : "";

            if (pairedFingerprint != null && !pairedFingerprint.equals(fingerprint)) {
                System.out.println("Rejected unknown phone: " + phoneName);
                return;
            }

            WindowsServer.onPhoneConnected(incomingIp, phoneName, fingerprint);
            androidWriter = new PrintWriter(client.getOutputStream(), true);
            System.out.println("Android persistent connection established from: " + incomingIp);
            String line;
            while ((line = in.readLine()) != null) {

                System.out.println("Received line: " + line);

                if (line.startsWith("CLIPBOARD=")) {

                    if (clipboardEnabled) {
                        String text = line.substring("CLIPBOARD=".length());
                        System.out.println("Clipboard: " + text);
                    }

                } else if (line.startsWith("NOTIF|")) {

                    NotificationServer.handle(line);

                } else if (line.startsWith("STATUS|")) {

                    String[] parts = line.split("\\|");

                    int battery = -1;
                    String mode = "UNKNOWN";
                    boolean charging = false;
                    boolean bt = false;

                    for (String part : parts) {

                        if (part.startsWith("BATTERY:"))
                            battery = Integer.parseInt(part.substring(8));

                        if (part.startsWith("MODE:"))
                            mode = part.substring(5);

                        if (part.startsWith("CHARGING:"))
                            charging = Boolean.parseBoolean(part.substring(9));

                        if (part.startsWith("BT:"))
                            bt = Boolean.parseBoolean(part.substring(3));
                    }

                    if (battery == lastBattery &&
                            mode.equals(lastMode) &&
                            charging == lastCharging &&
                            bt == lastBt)
                        return;

                    lastBattery = battery;
                    lastMode = mode;
                    lastCharging = charging;
                    lastBt = bt;

                    final int b = battery;
                    final String m = mode;
                    final boolean c = charging;
                    final boolean bluetooth = bt;

                    SwingUtilities.invokeLater(() -> {

                        if (WindowsServer.batteryLabel != null)
                            WindowsServer.batteryLabel.setText(
                                    "Battery: " + b + "%" + (c ? " Not Charging" : " Charging"));

                        if (WindowsServer.modeLabel != null)
                            WindowsServer.modeLabel.setText(
                                    "Mode: " + m + (bluetooth ? "  |  BT ON" : "  |  BT OFF"));
                    });
                } else if (line.startsWith("MOUSE_MOVE:")) {

                    String[] parts = line.substring("MOUSE_MOVE:".length()).split(",");
                    int dx = Integer.parseInt(parts[0]);
                    int dy = Integer.parseInt(parts[1]);
                    MouseController.move(dx, dy);

                } else if (line.equals("MOUSE_CLICK")) {

                    MouseController.click();

                } else if (line.equals("MOUSE_DOWN")) {

                    MouseController.mouseDown();

                } else if (line.equals("MOUSE_UP")) {

                    MouseController.mouseUp();

                } else if (line.equals("MOUSE_RIGHT_CLICK")) {

                    MouseController.rightClick();

                } else if (line.startsWith("MOUSE_SCROLL:")) {

                    int amount = Integer.parseInt(line.substring("MOUSE_SCROLL:".length()));
                    MouseController.scroll(amount);

                }
            }

        } catch (Exception e) {
            // Silent disconnect
        } finally {
            androidWriter = null;
            WindowsServer.onPhoneDisconnected();
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
    }
}