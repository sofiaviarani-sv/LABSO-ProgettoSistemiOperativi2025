package Master;

import java.io.*;
import java.net.Socket;

//Gestisce la comunicazione tra un singolo peer e il master
//Ogni connessione con un peer viene servita da un thread dedicato (istanza di ClientHandler)
//cosi il master può gestire più peer contemporaneamente
public class ClientHandler implements Runnable {
    private final Socket clientSocket;  //rappresenta la connessione attiva tra master e peer
    private final ResourceService resourceService;//riferimento all'oggetto che gestisce le risorse
    private final boolean running = true; //flag per verificare eventuali errori di connessione

    //Costruttore
    public ClientHandler(Socket clientSocket, ResourceService resourceService) {
        this.clientSocket = clientSocket;
        this.resourceService = resourceService;
    }

    @Override
    public void run() {
        try (//si usa try per assicurarsi che in e out si chiudano automaticamente
             //crea due stream di comunicazione:
             //in serve per leggere ciò che il Peer invia al master
             //out serve per inviare risposte dal master al Peer
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line; // variabile per memorizzare temporaneamente ogni comando ricevuto
            // ciclo principale del thread: rimane in esecuzione finché il peer è connesso
            while ((line = in.readLine()) != null) {
                // divide la stringa ricevuta in token separati da spazi
                String[] parts = line.split("\\s+");
                //se la riga è vuota o malformata, passa oltre
                if (parts.length == 0) continue;

                // --- DOWNLOAD_RESULT ---
                // gestisce il messaggio di conferma del completamento di un download
                if (parts[0].equalsIgnoreCase("DOWNLOAD_RESULT")) {
                    // il comando deve contenere almeno 5 parametri
                    if (parts.length >= 5) {
                        String resource = parts[1]; // estrae il nome della risorsa
                        String sourcePeer = parts[2]; // estrae il Peer sorgente
                        String targetPeer = parts[3]; // estrae il Peer richiedente
                        String stato = parts[4]; // esito del download
                        // aggiorna il registro dei download nel ResourceService
                        resourceService.updateDownloadResult(resource, sourcePeer, targetPeer, stato);
                    }
                    continue;
                }

                // --- ADD ---
                // un Peer comunica al master di aggiungere (o registrare) una risorsa
                if (parts.length >= 3 && parts[0].equalsIgnoreCase("add")) {
                    String resourceName = parts[1]; // nome della risorsa
                    String peerName = parts[2]; // nome del Peer
                    // aggiorna la tabella delle risorse
                    resourceService.addResource(resourceName, peerName, out);

                    // --- DOWNLOAD ---
                    // Un peer chiede di scaricare una risorsa
                } else if (parts.length >= 3 && parts[0].equalsIgnoreCase("download")) {
                    String resourceName = parts[1]; // nome della risorsa
                    String peerName = parts[2]; // nome del peer richiedente
                    // delega la logica di gestione al resourceService
                    resourceService.handleDownload(resourceName, peerName, out);

                    // --- LISTDATA ---
                    //Il peer chiede l'elenco di tutte le risorse note al master
                } else if (parts[0].equalsIgnoreCase("listdata")) {
                    // per ogni risorsa nella tabella, stampa i peer associati
                    resourceService.getAllResources().forEach((res, peers) ->
                            out.println(res + ": " + String.join(", ", peers))
                    );
                    out.println("END");

                    // --- QUIT ---
                    // il Peer comunica la disconnessione volontaria
                } else if (parts[0].equalsIgnoreCase("quit")) {
                    break; // esce dal ciclo e termina il thread

                    // --- CHECK ---
                    // controlla se un peer è associato a una determinata risorsa
                } else if (parts.length >= 3 && parts[0].equalsIgnoreCase("check")) {
                    String resourceName = parts[1];
                    String peerName = parts[2];
                    // verifica tramite resourceService
                    boolean associated = resourceService.isAssociated(resourceName, peerName);
                    // invio il risultato al peer
                    out.println(associated ? "ASSOCIATED" : "NOT_ASSOCIATED");
                    out.println("END");

                    // --- HELLO ---
                    // primo messaggio inviato dal peer al momento della connessione: serve per registrarsi
                } else if (parts[0].equalsIgnoreCase("hello") && parts.length >= 3) {
                    String peerName = parts[1]; //nome del peer
                    int peerPort = Integer.parseInt(parts[2]); //porta sulla quale ascolta
                    String peerIP = clientSocket.getInetAddress().getHostAddress(); //IP
                    //registra il peer nel resourceService
                    resourceService.registerPeer(peerName, peerIP, peerPort);
                    //invia conferma al peer
                    out.println("REGISTERED " + peerName);
                    out.println("END");

                    // --- UPDATEFAIL ---
                    // segnala che un download è fallito per colpa di un peer non disponibile
                } else if (parts[0].equalsIgnoreCase("updatefail") && parts.length >= 3) {
                    String fileName = parts[1]; //risorsa
                    String peerName = parts[2]; //nome peer
                    // rimuove l'associazione tra peer e risorsa
                    resourceService.unregisterResource(fileName, peerName);
                    // invia conferma
                    out.println("OK: Risorsa rimossa dal peer " + peerName);
                    out.println("END");

                    // --- UNKNOWN COMMAND ---
                    // caso di comando non riconosciuto
                } else {
                    out.println("Comando sconosciuto.");
                    out.println("END");
                }
            }
        } // se la connessione si interrompe in modo imprevisto
        catch (IOException e) {
            if (running) {
                e.printStackTrace(); // stampa solo se non è stata una chiusura controllata
            }
        } // blocco finale eseguito sempre, anche in caso di errore
        finally {
            try { // chiude il socket se ancora aperto
                clientSocket.close();
            } catch (IOException ignored) {}
        }
    }
}
