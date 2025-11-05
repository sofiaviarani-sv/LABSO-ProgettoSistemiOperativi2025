package Peer;
import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static final String resourcesPath = "Peer/resources";
    private static String peerName;
    private static int localPort;


    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("[USO]: java Peer <host_master> <porta_master> <peerName> <porta_locale>");
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);
        peerName = args[2];
        localPort = Integer.parseInt(args[3]);

        try {
            // connessione al master
            socket = new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connesso al master su " + host + ":" + port + " come " + peerName);

            // avvia il PeerServer in un thread separato
            // Dichiarazione a livello di classe o metodo main
            PeerServer server = new PeerServer(localPort);

            // Avvia il server in un thread
            new Thread(server).start();


            // comunica al master la porta locale
            out.println("hello " + peerName + " " + localPort);
            out.flush();
            receiveResponse();

            // crea la cartella delle risorse locali
            File folder = new File(resourcesPath);
            if (!folder.exists()) folder.mkdir();


            Scanner scanner = new Scanner(System.in);
            boolean running = true;

            while (running) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();
                String[] parts = command.split("\\s+", 3);

                switch (parts[0].toLowerCase()) {
                    case "quit":
                        out.println("quit " + peerName);
                        socket.close();
                        server.stopServer(); // chiude correttamente il server locale
                        running = false;
                        scanner.close();
                        System.out.println("Client terminato.");
                        break;

                    case "listdata":
                        if (parts.length < 2) {
                            System.out.println("Uso: listdata local|remote");
                            break;
                        }
                        if (parts[1].equalsIgnoreCase("local")) {
                            listLocalResources();
                        } else if (parts[1].equalsIgnoreCase("remote")) {
                            out.println("listdata remote");
                            out.flush();
                            boolean hasRemote = receiveResponse();
                            if (!hasRemote) System.out.println("Nessuna risorsa remota.");
                        }
                        break;

                    case "add":
                        if (parts.length < 2) {
                            System.out.println("Uso: add <nome> [contenuto]");
                        } else if (parts.length == 2) {
                            addResource(parts[1]);
                        } else {
                            addResource(parts[1], parts[2]);
                        }
                        break;

                    case "download":
                        if (parts.length < 2) {
                            System.out.println("Uso: download <nome>");
                        } else {
                            downloadResource(parts[1]);
                        }
                        break;

                    default:
                        System.out.println("Comando sconosciuto.");
                }
            }
            scanner.close();
        } catch (IOException e) {
            System.err.println("Errore di connessione: " + e.getMessage());
        }
    }

    private static void listLocalResources() {
        File folder = new File(resourcesPath);
        File[] files = folder.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("Nessuna risorsa locale.");
            return;
        }
        System.out.println("Risorse locali:");
        for (File f : files) System.out.println("- " + f.getName());
    }

    private static void addResource(String name, String... content) {
        File file = new File(resourcesPath + "/" + name);

        // Caso 1: file già esistente in locale
        if (file.exists()) {
            System.out.println("Risorsa " + name + " già presente localmente. Verrà associata al master.");
            try {
                out.println("add " + name + " " + peerName);
                out.flush();
                receiveResponse();
            } catch (IOException e) {
                System.err.println("Errore nell'associare la risorsa: " + e.getMessage());
            }
            return;
        }

        // Caso 2: file non esistente → serve contenuto
        if (content.length == 0) {
            System.out.println("Errore: il file '" + name + "' non esiste e devi fornire il contenuto per crearlo.");
            return;
        }

        // Creazione del file
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content[0]);
            System.out.println("Risorsa " + name + " creata localmente.");

            // associazione al master
            out.println("add " + name + " " + peerName);
            out.flush();
            receiveResponse();
        } catch (IOException e) {
            System.err.println("Errore aggiunta risorsa: " + e.getMessage());
        }
    }


    private static boolean receiveResponse() throws IOException {
        String line;
        boolean printed = false;
        while ((line = in.readLine()) != null && !line.equals("END")) {
            System.out.println(line);
            printed = true;
        }
        return printed;
    }
    /**
     * Scarica una risorsa (file) dalla rete P2P.
     *
     * Flusso logico:
     *  1️Chiede al Master quale peer possiede la risorsa richiesta.
     *  2 Il Master risponde indicando IP e porta del peer che la possiede.
     *  3 Il peer contatta direttamente quel peer tramite PeerHandler per scaricare il file.
     *  4 Se il download riesce, il peer notifica il Master che ora possiede anche lui quella risorsa.
     *  5 Se fallisce, il peer avvisa il Master che il peer segnalato non aveva effettivamente il file.
     *  6 Peer A torna al passo 1 (chiede nuovo peer) Peer
     *  7 Se il master risponde “nessun peer disponibile” → termina Peerpackage Master;
     */

    private static void downloadResource(String fileName) throws IOException {
        Set<String> triedPeers = new HashSet<>(); // tiene traccia dei peer già tentati
        boolean success = false;

        while (!success) {
            //  Chiede al master un peer che possiede la risorsa
            out.println("download " + fileName + " " + peerName);
            out.flush();

            String response = in.readLine();

            // Gestione errori di protocollo
            if (response == null || response.startsWith("ERRORE")) {
                System.out.println("Nessun peer disponibile per la risorsa '" + fileName + "'.");
                receiveResponse(); // svuota eventuali "END"
                break; // esce dal ciclo
            }

            if (!response.startsWith("PEER")) {
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                break;
            }

            // Parsing sicuro
            String[] parts = response.split("\\s+");
            if (parts.length < 4) {
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                break;
            }

            String peerTarget = parts[1];
            String peerAddress = parts[2];
            int peerPort = Integer.parseInt(parts[3]);

            receiveResponse(); // legge "END"

            //  Evita di contattare lo stesso peer più volte
            if (triedPeers.contains(peerTarget)) {
                System.out.println("Tutti i peer per la risorsa '" + fileName + "' sono stati già provati.");
                break;
            }

            triedPeers.add(peerTarget);

            // 2. Prova a scaricare dal peer indicato
            System.out.println("Tentativo di download da " + peerTarget + " (" + peerAddress + ":" + peerPort + ")");
            success = PeerHandler.downloadFromPeer(peerAddress, peerPort, fileName, resourcesPath);

            if (success) {
                // 3. Se il download ha successo, notifica al master
                out.println("add " + fileName + " " + peerName);
                out.flush();
                receiveResponse();
                System.out.println(" Download completato con successo da " + peerTarget);
            } else {
                // 4. Se fallisce, notifica al master e ripeti il ciclo
                out.println("updatefail " + fileName + " " + peerTarget);
                out.flush();
                receiveResponse();
                System.out.println("Il peer " + peerTarget + " non ha fornito la risorsa. Richiedo un altro peer...");
            }
        }
    }


}
