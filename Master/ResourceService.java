package Master;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ResourceService {
    //mappa per associare ad ogni risorsa la lista dei peer che la possiedono
    private final Map<String, List<String>> resourceTable;
    //lista che memorizza tutti i download effettuati dai peer (con orario ed esito)
    private final List<String> downloadLog;

    //mappa che associa ad ogni peer le sue informazioni
    private final Map<String, PeerInfo> peers;

    //costruttore
    public ResourceService() {
        this.resourceTable = new HashMap<>();
        this.downloadLog = new ArrayList<>();
        this.peers = new HashMap<>();
    }

    //classe interna per memorizzare informazioni di ogni Peer (IP, porta)
    private static class PeerInfo {
        String ip;
        int port;

        PeerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    //Metodo di registrazione di un peer
    //aggiunge un peer alla mappa peers, ognuno identificato da nome,IP,porta
    //essendo synchronized evita condizioni di race se più thread aggiungo peer contemporaneamente
    public synchronized void registerPeer(String name, String ip, int port) {
        peers.put(name, new PeerInfo(ip, port));
    }

    //Metodo per la gestione dei download
    //decide da quale peer scaricare la risorsa e invia le informazioni del peer sorgente al peer richiedente
    //prende in input il nome della risorsa, il peer richiedente e out come stream di output
    //essendo synchronized evita che più thread inviino richieste di download contemporaneamente
    public synchronized void handleDownload(String resourceName, String requestingPeer, PrintWriter out) {
        //cerca nella resourceTable chi possiede la risorsa richiesta e crea una lista dei peer possessori
        List<String> resourcePeers = resourceTable.get(resourceName);
        //Rimuove i peer non più registrati (disconnessi) dalla lista di chi ha la risorsa
        if (resourcePeers != null) {
            resourcePeers.removeIf(p -> !peers.containsKey(p));
            // Se la lista diventa vuota, elimina anche la risorsa dalla tabella
            if (resourcePeers.isEmpty()) {
                resourceTable.remove(resourceName);
            }
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        //se la risorsa non esiste o nessuno la possiede
        if (resourcePeers == null || resourcePeers.isEmpty()) {
            //invia al peer richiedente avviso
            out.println("ERRORE: Risorsa non trovata");
            //registra nel log del master il messaggio relativo al tentativo di download
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: N/A a: " + requestingPeer + " [FALLITO - non disponibile]");
            //invia al peer il messaggio di fine
            out.println("END");
            return;
        }
        //Se la lista resourcePeer contiene il nome del peer richiedente significa che il peer possiede già la risorsa
        if (resourcePeers.contains(requestingPeer)) {
            //invia al peer messaggio di errore
            out.println("ERRORE: Il peer possiede già la risorsa");
            //registra nel log il tentativo di download
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: " + requestingPeer + " a: " + requestingPeer + " [FALLITO - già posseduta]");
            //invia al peer il messaggio di fine
            out.println("END");
            return;
        }

        // apre uno stream sulla lista dei peer possessori e fa un ulteriore controllo
        String sourcePeer = resourcePeers.stream()
                //filtra la lista, eliminando il peer richiedente e quelli non ancora registrati nella lista dei peer attivi
                .filter(p -> !p.equals(requestingPeer) && peers.containsKey(p))
                //prende il primo elemento
                .findFirst()
                //se non trova nessun, assegna null al peer sorgente
                .orElse(null);

        //se il peer sorgente è null, nessun altro ha la risorsa
        if (sourcePeer == null) {
            //invia al peer richiedente messaggio di errore
            out.println("ERRORE: Nessun altro peer disponibile");
            //registra nel log il tentativo di download
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: N/A a: " + requestingPeer + " [FALLITO - nessun sorgente]");
            //messaggio di fine
            out.println("END");
            return;
        }
        //se è stato trovato un peer sorgente, recupera le sue informazioni
        PeerInfo info = peers.get(sourcePeer);
        if (info == null) { //se il peer sorgente non ha informazioni a lui associate in PeerInfo
            //messaggio di errore al peer richiedente
            out.println("ERRORE: Il peer sorgente non è registrato correttamente");
            //messaggio di fine
            out.println("END");
            return;
        }

        //altrimenti, invia le info del peer sorgente al richiedente
        out.println("PEER " + sourcePeer + " " + info.ip + " " + info.port);
        //messaggio di fine
        out.println("END");

    }

    //Metodo per l'aggiunta di una risorsa
    //prende in input il nome della risorsa che il peer vuole registrare, il nome del peer che fa la richiesta, lo strem per inviare messaggi al peer
    //essendo synchronized garantisce che un solo thread alla volta possa eseguire il metodo
    public synchronized void addResource(String resourceName, String peerName, PrintWriter out) {
        // Controlla se la risorsa è già presente nella tabella
        //Se non esiste, crea una lista vuota per i peer futuri
        resourceTable.putIfAbsent(resourceName, new ArrayList<>());

        //Prende la lista dei peer associati alla risorsa (anche se appena creata)
        List<String> peers = resourceTable.get(resourceName);

        //Controlla se il peer richiedente è presente nella lista dei peer associati alla risorsa
        if (peers.contains(peerName)) { //Se lo è, invia al peer un messaggio e non aggiunge il nome del peer alla lista
            out.println("Risorsa '" + resourceName + "' è già associata al peer '" + peerName + "'.");
        } else {
            peers.add(peerName); //Se non lo è, aggiunge il peer alla lista dei peer associati alla risorsa e manda un messaggio al peer
            out.println("Risorsa '" + resourceName + "' ora associata al peer '" + peerName + "'.");

        }
        //segnala la fine dell'operazione
        out.println("END");
    }

    //Metodo booleano per verificare se un peer possiede una risorsa
    //prende in input il nome della risorsa e il peer che vogliamo verificare
    public boolean isAssociated(String resourceName, String peerName) {
        //Prende dalla mappa la lista dei peer che possiedono la risorsa
        //Se la risorsa non esiste nella tabella, ritorna null
        List<String> peers = resourceTable.get(resourceName);

        //Se la risorsa esiste nella tabella e il peer è nella lista dei peer che possiedono la risorsa ritorna true, altrimenti false
        return peers != null && peers.contains(peerName);
    }

    //Metodo per restituire una copia di resourceTable, in modo che il chiamante non la modifichi accidentalmente
    //ritorna una mappa in cui per ogni risorsa si ha la lista dei peer che la possiedono
    //synchronized
    public synchronized Map<String, List<String>> getAllResources() {
        //crea una nuova mappa vuota
        Map<String, List<String>> copy = new HashMap<>();
        //cicla tutte le risorse nella mappa e per ognuna
        for (String res : resourceTable.keySet()) {
            //crea un nuovo arraylist dove copia la lista dei peer e inserisce tutto nella nuova mappa
            copy.put(res, new ArrayList<>(resourceTable.get(res)));
        }
        //restituisce la copia completa
        return copy;
    }

    //Metodo per rimuovere l'associazione tra una risorsa e un peer specifico
    //prende in input il nome della risorsa e il nome del peer
    //synchronized
    public synchronized void unregisterResource(String resourceName, String peerName) {
        //crea una lista con i peer associati alla risorsa
        List<String> list = resourceTable.get(resourceName);
        //se esiste, rimuove il peer dalla lista
        if (list != null) {
            list.remove(peerName);
            //se la lista diventa vuota, elimina completamente la risorsa dalla mappa
            if (list.isEmpty()) {
                resourceTable.remove(resourceName);
            }
        }
    }

    //Metodo che restituisce una copia del log dei download
    //crea un nuovo arraylist usando il contenuto di downloadLog
    //synchronized
    public synchronized List<String> getDownloadLog() {
        return new ArrayList<>(downloadLog);
    }

    // Aggiorna il log del download con l'esito finale di un'operazione di download
    //prende in input il nome della risorsa, il nome del peer sorgente, il nome del peer richiedente e lo stato del download
    //synchronized perchè più thread ClientHandler possono tentare di aggiornare il log contemporaneamente
    public synchronized void updateDownloadResult(String resourceName, String sourcePeer, String targetPeer, String stato) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
       //se il parametro stato è successo salva "OK", altrimenti "FALLITO"
        String newStatus = stato.equalsIgnoreCase("success") ? "OK" : "FALLITO";

        //se il peer sorgente è vuoto o null, usa "N/A" (caso in cui il master non ha trovato un peer sorgente)
        String logSource = (sourcePeer == null || sourcePeer.isEmpty()) ? "N/A" : sourcePeer;
        //se lo stato è "fallito", allora aggiunge una nota "non disponibile"
        String extra = newStatus.equals("FALLITO") ? " - non disponibile" : "";

        // aggiunge direttamente la riga finale nel log
        downloadLog.add("[" + timestamp + "] " + resourceName + " da: " + logSource + " a: " + targetPeer + " [" + newStatus + extra + "]");
    }

}

