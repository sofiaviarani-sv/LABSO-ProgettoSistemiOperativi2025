import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static final String resourcesPath = "resources";
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
                            receiveResponse();
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


    private static void receiveResponse() throws IOException {
        String line;
        while ((line = in.readLine()) != null && !line.equals("END")) {
            System.out.println(line);
        }
    }

    /**
     * Scarica una risorsa (file) dalla rete P2P.
     *
     * Flusso logico:
     *  1️⃣ Chiede al Master quale peer possiede la risorsa richiesta.
     *  2️⃣ Il Master risponde indicando IP e porta del peer che la possiede.
     *  3️⃣ Il peer contatta direttamente quel peer tramite PeerHandler per scaricare il file.
     *  4️⃣ Se il download riesce, il peer notifica il Master che ora possiede anche lui quella risorsa.
     *  5️⃣ Se fallisce, il peer avvisa il Master che il peer segnalato non aveva effettivamente il file.
     */
    private static void downloadResource(String fileName) throws IOException {
        boolean success = false;

        while (!success) {

            // 🟩 1️⃣ Chiede al master chi possiede la risorsa richiesta
            // Il master risponde con qualcosa tipo:
            // "PEER peerB 127.0.0.1 6000"
            out.println("download " + fileName + " " + peerName);
            out.flush();

            String response = in.readLine();

            if (response == null || !response.startsWith("PEER")) {
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                return;
            }
            // 🟥 Se il master non risponde o segnala errore, si interrompe
            if (response == null || response.startsWith("ERRORE")) {
                System.out.println("Risorsa non disponibile sulla rete.");
                receiveResponse(); // svuota eventuali messaggi pendenti
                break;
            }

            // 🟨 Controlla che la risposta sia valida e ben formata
            // Attenzione: se la risposta è malformata, evitare crash!
            // Esempio atteso: "PEER peerB 127.0.0.1 6000"
            String[] parts = response.split("\\s+");
            if (parts.length < 4) { // 🔒 controllo di sicurezza
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                break;
            }

            String peerTarget = parts[1];       // nome del peer che ha la risorsa
            String peerAddress = parts[2];      // IP del peer target
            int peerPort = Integer.parseInt(parts[3]); // porta TCP del peer target

            receiveResponse(); // legge eventuale "END" dal master

            // 🟩 2️⃣ Tenta di scaricare il file direttamente dal peer indicato
            success = PeerHandler.downloadFromPeer(peerAddress, peerPort, fileName, resourcesPath);

            if (success) {
                // 🟩 3️⃣ Se il download ha avuto successo:
                // notifica al master che questo peer ora possiede la risorsa
                out.println("add " + fileName + " " + peerName);
                out.flush();
                receiveResponse();
                System.out.println("✅ Download completato con successo da " + peerTarget);
                break;
            } else {
                // 🟥 4️⃣ Se fallisce, comunica al master che quel peer era inconsistente
                out.println("updatefail " + fileName + " " + peerTarget);
                out.flush();
                receiveResponse();
                System.out.println("⚠️  Il peer " + peerTarget + " non ha fornito la risorsa. Tentativo fallito.");
            }
        }
    }

}
