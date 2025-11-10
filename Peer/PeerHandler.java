package Peer;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.Semaphore;

//Gestore delle richieste individuali che arrivano da altri Peer
public class PeerHandler implements Runnable { // gestisce le richieste in arrivo da altri peer

    private final Socket clientSocket; //socket di connessione del peer richiedente
    private final String resourcesPath; //percorso della cartella delle risorse
    private final Semaphore mutex; //lock per garantire mutua esclusione

    // Costruttore per la gestione lato server
    public PeerHandler(Socket clientSocket, String resourcesPath, Semaphore mutex) {
        this.clientSocket = clientSocket;
        this.resourcesPath = resourcesPath;
        this.mutex = mutex;
    }

    // Costruttore statico per la gestione lato client (downloadFromPeer)
    public PeerHandler() {
        this.clientSocket = null;
        this.resourcesPath = null;
        this.mutex = null;
    }

    @Override
    public void run() {
        handleClient(clientSocket); //avvia la logica di gestione della richiesta nel thread
    }
    // gestisce la richiesta di un peer (eseguito in un thread separato)
    private void handleClient(Socket clientSocket) {
        try {
            mutex.acquire();// garantisce la mutua esclusione
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); //legge le richieste inviate dal peer
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true); //invia le risposte al peer richiedente

            String fileName = in.readLine();// legge dal peer il nome del file richiesto
            File file = new File(resourcesPath + "/" + fileName);// costruisce il percorso del file

            if (!file.exists()) { //se il file non esiste
                out.println("NOT_FOUND");
            } else { //altrimenti, risponde OK
                out.println("OK");
                //apre il file e lo legge riga per riga
                try (BufferedReader fileReader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = fileReader.readLine()) != null) {
                        //manda ogni riga al peer richiedente
                        out.println(line);
                    }
                }
                out.println("END");
            }

        } catch (Exception e) {
            //se si verifica errore, lo stampa
            System.err.println("[PeerServer] Errore: " + e.getMessage());
        } finally {
            mutex.release();// rilascia il semaforo e chiude il socket del client
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    //Metodo per stabilire connessione diretta tra due peer
    //prende in input l'IP del peer sorgente, la porta del peer sorgente, il nome della risorsa e il path della cartella dove salvare il file
    public static boolean downloadFromPeer(String peerAddress, int peerPort, String resourceName, String destinationPath) {//
        //crea socket TCP verso il peer sorgente, il try assicura che tutto venga chiuso alla fine automaticamente
        try (Socket socket = new Socket(peerAddress, peerPort);// connessione al peer remoto
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true); //crea lo stream per inviare dati
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) { //crea stream per leggere le risposte inviate dal peer sorgente

            out.println(resourceName);//il peer richiedente invia riga con nome della risorsa
            String response = in.readLine();// il peer sorgente risponde con NOT_FOUND o OK

            //se la risposta è NOT FOUND o qualsiasi cosa diversa da OK, allora il download è fallito
            if ("NOT_FOUND".equals(response)) return false;
            if (!"OK".equals(response)) return false;

            //crea un nuovo file (se esiste già lo sovrascrive) nella cartella resources, con il nome della risorsa richiesta
            File file = new File(destinationPath + "/" + resourceName);
            try (FileWriter fw = new FileWriter(file)) {// scrive il contenuto ricevuto, contenuto nel file, riga per riga fino a "end"
                String line;
                while ((line = in.readLine()) != null && !line.equals("END")) {//
                    fw.write(line + System.lineSeparator());
                }
            }
            return true;

        } catch (IOException e) { //per qualsiasi eccezione, il metodo fallisce
            return false;
        }
    }
}