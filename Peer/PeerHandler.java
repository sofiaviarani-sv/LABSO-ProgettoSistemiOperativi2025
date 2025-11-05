package Peer;
import java.io.*;
import java.net.Socket;

public class PeerHandler {
    public static boolean downloadFromPeer(String peerAddress, int peerPort, String resourceName, String destinationPath) {
        try (Socket socket = new Socket(peerAddress, peerPort);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(resourceName);
            String response = in.readLine();

            if ("NOT_FOUND".equals(response)) return false;
            if (!"OK".equals(response)) return false;

            File file = new File(destinationPath + "/" + resourceName);
            try (FileWriter fw = new FileWriter(file)) {
                String line;
                while ((line = in.readLine()) != null && !line.equals("END")) {
                    fw.write(line + System.lineSeparator());
                }
            }
            return true;

        } catch (IOException e) {
            return false;
        }
    }
}
