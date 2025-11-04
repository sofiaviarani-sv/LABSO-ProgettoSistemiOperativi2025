package Master;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ResourceService resourceService;
    private final int clientId;

    public ClientHandler(Socket socket, ResourceService resourceService, int clientId) {
        this.socket = socket;
        this.resourceService = resourceService;
        this.clientId = clientId;
    }

    @Override
    public void run() {
        try (Scanner from = new Scanner(socket.getInputStream());
             PrintWriter to = new PrintWriter(socket.getOutputStream(), true)) {

            while (!Thread.interrupted() && !socket.isClosed()) {
                if (!from.hasNextLine()) break;

                String request = from.nextLine().trim();
                if (request.isEmpty()) continue;

                String[] parts = request.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {

                    case "hello":
                        // Nuovo comando per registrare peer con IP e porta
                        if (parts.length >= 3) {
                            String peerName = parts[1];
                            int peerPort;
                            try {
                                peerPort = Integer.parseInt(parts[2]);
                            } catch (NumberFormatException e) {
                                to.println("ERRORE: porta non valida");
                                to.println("END");
                                break;
                            }
                            String peerIp = socket.getInetAddress().getHostAddress();
                            resourceService.registerPeer(peerName, peerIp, peerPort);
                            to.println("REGISTERED " + peerName);
                        } else {
                            to.println("ERRORE: uso corretto -> hello <peerName> <peerPort>");
                        }
                        to.println("END");
                        break;
                    case "updatefail":
                        if (parts.length >= 3) {
                            String fileName = parts[1];
                            String peerName = parts[2];
                            System.out.println("⚠️ Il peer " + peerName + " non ha fornito il file " + fileName);

                            //Aggiorna la tabella delle risorse rimuovendo l'associazione errata
                            resourceService.unregisterResource(fileName, peerName);

                            System.out.println("Tabella aggiornata: rimossa associazione " + peerName + " → " + fileName);
                            to.println("OK: Risorsa rimossa dal peer " + peerName);
                        } else {
                            to.println("ERRORE: uso corretto -> updatefail <file> <peer>");
                        }
                        to.println("END");
                        break;

                    case "listdata":
                        Map<String, List<String>> resources = resourceService.getAllResources();
                        if (resources == null || resources.isEmpty()) {
                            to.println("Nessuna risorsa disponibile nei peer.");
                        } else {
                            for (Map.Entry<String, List<String>> e : resources.entrySet()) {
                                to.println(e.getKey() + ": " + String.join(", ", e.getValue()));
                            }
                        }
                        to.println("END");
                        break;

                    case "check":
                        if (parts.length >= 3) {
                            String res = parts[1];
                            String peer = parts[2];
                            boolean associated = resourceService.isAssociated(res, peer);
                            to.println(associated ? "ASSOCIATED" : "NOT_ASSOCIATED");
                        } else {
                            to.println("ERRORE: uso corretto -> check <risorsa> <peer>");
                        }
                        to.println("END");
                        break;

                    case "add":
                        if (parts.length >= 3) {
                            String res = parts[1];
                            String peer = parts[2];
                            resourceService.addResource(res, peer, to);
                        } else {
                            to.println("ERRORE: uso corretto -> add <risorsa> <peer>");
                        }
                        to.println("END");
                        break;

                    case "download":
                        if (parts.length >= 3) {
                            // Ora handleDownload restituisce PEER <nome> <ip> <porta>
                            resourceService.handleDownload(parts[1], parts[2], to);
                        } else {
                            to.println("ERRORE: uso corretto -> download <risorsa> <peer>");
                            to.println("END");
                        }
                        break;

                    case "quit":
                        to.println("Connessione chiusa.");
                        to.println("END");
                        socket.close();
                        return;

                    default:
                        to.println("Comando non riconosciuto: " + cmd);
                        to.println("END");
                }
            }

        } catch (IOException e) {
            // Connessione interrotta: ignora
        } finally {
            closeConnection();
        }
    }

    public void closeConnection() {
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {}
    }


}
