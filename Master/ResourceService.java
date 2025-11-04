package Master;

import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


/**
 * Gestisce risorse condivise e log dei download in modo thread-safe.
 */
public class ResourceService {
    private final Map<String, List<String>> resourceTable;
    private final List<String> downloadLog;

    // Nuova mappa per memorizzare info dei peer
    private final Map<String, PeerInfo> peers;

    public ResourceService() {
        this.resourceTable = new HashMap<>();
        this.downloadLog = new ArrayList<>();
        this.peers = new HashMap<>();
    }

    // Classe interna per memorizzare IP e porta
    private static class PeerInfo {
        String ip;
        int port;

        PeerInfo(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }

    // Metodo per registrare un peer
    public synchronized void registerPeer(String name, String ip, int port) {
        peers.put(name, new PeerInfo(ip, port));
    }

    /** Gestisce un comando di download da un peer */
    public synchronized void handleDownload(String resourceName, String requestingPeer, PrintWriter out) {
        List<String> resourcePeers = resourceTable.get(resourceName);
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        if (resourcePeers == null || resourcePeers.isEmpty()) {
            out.println("ERRORE: Risorsa non trovata");
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: N/A a: " + requestingPeer + " [fallito - non disponibile]");
            out.println("END");
            return;
        }

        if (resourcePeers.contains(requestingPeer)) {
            out.println("ERRORE: Il peer possiede già la risorsa");
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: " + requestingPeer + " a: " + requestingPeer + " [fallito - già posseduta]");
            out.println("END");
            return;
        }

        // Trova un peer sorgente diverso da chi richiede
        String sourcePeer = resourcePeers.stream()
                .filter(p -> !p.equals(requestingPeer))
                .findFirst()
                .orElse(null);

        if (sourcePeer == null) {
            out.println("ERRORE: Nessun altro peer disponibile");
            downloadLog.add("[" + timestamp + "] " + resourceName + " da: N/A a: " + requestingPeer + " [fallito - nessun sorgente]");
            out.println("END");
            return;
        }

        // Recupera IP e porta del peer sorgente dalle variabili
        PeerInfo info = peers.get(sourcePeer);
        if (info == null) {
            out.println("ERRORE: Il peer sorgente non è registrato correttamente");
            out.println("END");
            return;
        }

        // ✅ Risposta dinamica con IP e porta reali
        out.println("PEER " + sourcePeer + " " + info.ip + " " + info.port);
        out.println("END");

        downloadLog.add("[" + timestamp + "] " + resourceName + " da: " + sourcePeer + " a: " + requestingPeer + " [OK]");
    }

    /**
     * Aggiunge una risorsa a un peer e stampa il messaggio corretto.
     * Copre tutti i casi:
     * 1. File esistente in rete, nuovo peer → "Risorsa già esistente in resources, ora associata al peer ..."
     * 2. File esistente in rete, peer già associato → "Risorsa già associata a questo peer"
     * 3. File non esistente → master lo registra come nuova risorsa → "Nuova risorsa creata e associata a peer ..."
     *
     * @param resourceName nome risorsa
     * @param peerName     peer che aggiunge la risorsa
     * @param out          stream verso il peer
     */
    public synchronized void addResource(String resourceName, String peerName, PrintWriter out) {
        resourceTable.putIfAbsent(resourceName, new ArrayList<>());
        List<String> peers = resourceTable.get(resourceName);

        if (peers.contains(peerName)) {
            out.println("Risorsa '" + resourceName + "' è già associata al peer '" + peerName + "'.");
        } else {
            peers.add(peerName);
            out.println("Risorsa '" + resourceName + "' ora associata al peer '" + peerName + "'.");
        }

        out.println("END");
    }


    public boolean isAssociated(String resourceName, String peerName) {
        List<String> peers = resourceTable.get(resourceName);
        return peers != null && peers.contains(peerName);
    }





    /** Restituisce tutti i peer di una risorsa. */
    public synchronized List<String> getPeers(String resourceName) {
        return resourceTable.getOrDefault(resourceName, new ArrayList<>());
    }

    /** Restituisce tutte le risorse. */
    public synchronized Map<String, List<String>> getAllResources() {
        Map<String, List<String>> copy = new HashMap<>();
        for (String res : resourceTable.keySet()) {
            copy.put(res, new ArrayList<>(resourceTable.get(res)));
        }
        return copy;
    }


    public synchronized void logDownload(String resource, String from, String to, String outcome) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        downloadLog.add("[" + timestamp + "] - " + resource + " da: " + from + " a: " + to + " [" + outcome + "]");
    }
    // Rimuove l'associazione tra una risorsa e un peer
    public synchronized void unregisterResource(String resourceName, String peerName) {
        List<String> list = resourceTable.get(resourceName);
        if (list != null) {
            list.remove(peerName);
            if (list.isEmpty()) {
                resourceTable.remove(resourceName); // se nessun peer la possiede più
            }
        }
    }
    /** Restituisce il log dei download. */
    public synchronized List<String> getDownloadLog() {
        return new ArrayList<>(downloadLog);
    }
}
