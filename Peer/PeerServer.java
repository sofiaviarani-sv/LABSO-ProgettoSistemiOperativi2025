package Peer;
import java.io.*;
import java.net.*;
import java.util.concurrent.Semaphore;

/**
 * PeerServer
 * ----------
 * Server interno del peer che fornisce le risorse ad altri peer.
 * Usa un Semaphore per garantire che solo una richiesta venga servita alla volta.
 */
public class PeerServer implements Runnable {

    private final int port;
    private final String resourcesPath = "Peer/resources";
    private final Semaphore mutex = new Semaphore(1);
    private boolean running = true;
    private ServerSocket serverSocket;

    public PeerServer(int port) {
        this.port = port;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            this.serverSocket = serverSocket;
            System.out.println("[PeerServer] In ascolto sulla porta " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try {
            mutex.acquire();
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);

            String fileName = in.readLine();
            File file = new File(resourcesPath + "/" + fileName);

            if (!file.exists()) {
                out.println("NOT_FOUND");
            } else {
                out.println("OK");
                try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        out.println(line);
                    }
                }
                out.println("END");
            }

        } catch (Exception e) {
            System.err.println("[PeerServer] Errore: " + e.getMessage());
        } finally {
            mutex.release();
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (IOException ignored) {}
    }
}
