package Master;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gestisce le connessioni dai peer.
 */
public class SocketListener implements Runnable {
    private final ServerSocket serverSocket;
    private final ResourceService resourceService;
    private final List<Socket> clients = new ArrayList<>();
    private boolean running = true;

    public SocketListener(ServerSocket serverSocket, ResourceService resourceService) {
        this.serverSocket = serverSocket;
        this.resourceService = resourceService;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                synchronized (clients) {
                    clients.add(clientSocket);
                }
                new Thread(() -> handleClient(clientSocket)).start();
            } catch (IOException e) {
                if (running) { // solo se non stiamo chiudendo intenzionalmente
                    e.printStackTrace();
                }
                // se stiamo chiudendo, ignora
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3 && parts[0].equalsIgnoreCase("add")) {
                    String resourceName = parts[1];
                    String peerName = parts[2];
                    resourceService.addResource(resourceName, peerName, out); // <-- usa il PrintWriter locale
                } else if (parts.length >= 2 && parts[0].equalsIgnoreCase("download")) {
                    String resourceName = parts[1];
                    String peerName = parts[2];
                    resourceService.handleDownload(resourceName, peerName, out);
                } else if (parts[0].equalsIgnoreCase("listdata")) {
                    resourceService.getAllResources().forEach((res, peers) ->
                            out.println(res + ": " + String.join(", ", peers))
                    );
                    out.println("END");
                } else if (parts[0].equalsIgnoreCase("quit")) {
                    break;
                } else if (parts.length >= 3 && parts[0].equalsIgnoreCase("check")) {
                    String resourceName = parts[1];
                    String peerName = parts[2];
                    boolean associated = resourceService.isAssociated(resourceName, peerName);
                    out.println(associated ? "ASSOCIATED" : "NOT_ASSOCIATED");
                    out.println("END");
                } else if (parts[0].equalsIgnoreCase("hello") && parts.length >= 3) {
                    String peerName = parts[1];
                    int peerPort = Integer.parseInt(parts[2]);
                    String peerIP = clientSocket.getInetAddress().getHostAddress();
                    resourceService.registerPeer(peerName, peerIP, peerPort);
                    out.println("REGISTERED " + peerName);
                    out.println("END");
                } else {
                    out.println("Comando sconosciuto");
                    out.println("END");
                }
            }
        } catch (IOException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }


    public void closeAllClients() {
        running = false;
        synchronized (clients) {
            for (Socket s : clients) {
                try { s.close(); } catch (IOException ignored) {}
            }
        }
        try {
            serverSocket.close();
        } catch (IOException ignored) {}
    }

}
