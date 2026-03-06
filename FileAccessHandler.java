import java.io.File;
import java.io.FileInputStream;
import java.util.Base64;

public class FileAccessHandler {

    public static void handleMessage(String message) {

        System.out.println("FileAccessHandler received: " + message);

        try {

            // Android requests PC folder
            if (message.startsWith("FILE_REQ_LIST|")) {

                String path = message.substring("FILE_REQ_LIST|".length());
                File dir = new File(path);

                if (!dir.exists() || !dir.isDirectory())
                    return;

                StringBuilder response = new StringBuilder("FILE_RES_LIST|");

                for (File f : dir.listFiles()) {
                    response.append(f.getName())
                            .append(",")
                            .append(f.isDirectory() ? "DIR" : "FILE")
                            .append(";");
                }

                ClipboardServer.sendToAndroid(response.toString());
            }

            else if (message.startsWith("FILE_REQ_DOWNLOAD|")) {

                String path = message.substring("FILE_REQ_DOWNLOAD|".length());
                File file = new File(path);

                if (!file.exists() || !file.isFile())
                    return;

                System.out.println("Sending file to Android: " + file.getName());

                byte[] data = new byte[(int) file.length()];
                FileInputStream fis = new FileInputStream(file);
                fis.read(data);
                fis.close();

                String base64 = java.util.Base64.getEncoder().encodeToString(data);

            ClipboardServer.sendToAndroid(
                    "FILE_RES_FILE|" + file.getName() + "|" + base64
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}