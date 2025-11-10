package Peer;
import java.io.*;
import java.net.*;
import java.util.*;

public class Peer {// classe principale Peer
    private static Socket socket;// socket di connessione al master
    private static PrintWriter out;// stream di output verso il master
    private static BufferedReader in;// stream di input dal master
    private static boolean connectedToMaster = false; //booleano per verificare la connessione con il master
    private static final String resourcesPath = "Peer/resources";//cartella risorse locali
    private static String peerName; //nome del peer
    private static int localPort; //porta del peer

    public static void main(String[] args) {// avvio del peer
        if (args.length < 4) {//controlla che ci siano almeno 4 elementi
            System.err.println("[USO]: java Peer <host_master> <porta_master> <peerName> <porta_locale>");
            return;
        }

        String host = args[0];//estrae l'indirizzo IP
        int port = Integer.parseInt(args[1]);//estrae la porta del master e la converte in int
        peerName = args[2];//estrae nome del peer
        localPort = Integer.parseInt(args[3]);// estrae porta del peer e la converte in int

        try {
            // connessione al master
            socket = new Socket(host, port);// crea la socket di connessione al master
            out = new PrintWriter(socket.getOutputStream(), true);//stream di output verso il master
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));//stream di input dal master
            connectedToMaster = true; //imposto il booleano per la verifica della connessione su true
            System.out.println("Connesso al master su " + host + ":" + port + " come " + peerName); //messaggio informativo su console

            PeerServer server = new PeerServer(localPort);//istanzia il server locale del peer con la porta locale
            new Thread(server).start();// avvia PeerServer in un thread separato: il peer può servire richieste contemporaneamente all'interazione con il master


            // invia al master il messaggio di registrazione con nome peer e porta local
            out.println("hello " + peerName + " " + localPort);
            out.flush();
            receiveResponse(); //legge dal master la risposta

            // crea oggetto file relativo alla directory
            File folder = new File(resourcesPath);
            //se non esiste, la crea
            if (!folder.exists()) folder.mkdir();
            //Scanner per leggere i comandi da console
            Scanner scanner = new Scanner(System.in);
            //flag per il ciclo principale dei comandi
            boolean running = true;

            while (running) { //inizio del loop interattivo del peer
                System.out.print("> ");
                String command = scanner.nextLine().trim(); //legge la riga e rimuove gli spazi
                String[] parts = command.split("\\s+", 3); //spezza la riga massimo in 3 parti
                //comando + 2 parametri

                switch (parts[0].toLowerCase()) { //switch sul comando
                    //Comando quit
                    case "quit": // termina il peer
                        out.println("quit " + peerName); //invia al master il comando di disconnessione per questo peer
                        socket.close(); //chiude il socket verso il master
                        server.stopServer(); //chiama stopServer che termina il peerServer e chiude la sua ServerSocket
                        running = false; //imposta il flag per uscire dal loop
                        scanner.close();//chiude lo scanner
                        System.out.println("Client terminato."); //messaggio di conferma di terminazione del peer
                        break;

                    //Comando listdata (local o remote)
                    case "listdata":
                        if (parts.length < 2) {
                            //se manca l'argomento, ritorna l'uso corretto
                            System.out.println("Uso: listdata local|remote");
                            break;
                        }
                        if (parts[1].equalsIgnoreCase("local")) {
                            //se local chiama listLocalResources() per elencare le risorse nella cartella locale
                            listLocalResources();
                        } else if (parts[1].equalsIgnoreCase("remote")) {
                            //se remote
                            if (!connectedToMaster) {
                                //prova a connettersi al master, se non riesce da errore
                                System.err.println("[ERRORE] Il master non è raggiungibile. Operazione non disponibile.");
                                break;
                            }
                            out.println("listdata remote"); //invia il comando al master
                            out.flush();
                            boolean hasRemote = receiveResponse(); //legge la risposta con receiveResponse()
                            if (!hasRemote) System.out.println("Nessuna risorsa remota."); //se non stampa nulla, informa l'utente
                        }
                        break;

                    //Comando add per aggiungere o registrare nuova risorsa
                    case "add":
                        if (!connectedToMaster) {
                            //controlla connessione al master, se assente non procede
                            System.err.println("[ERRORE] Il master non è raggiungibile. Operazione non disponibile.");
                            break;
                        }
                        if (parts.length < 2) {
                            //controlla che ci siano almeno due elementi, se non ci sono ritorna l'uso corretto
                            System.out.println("Uso: add <nome> [contenuto]");
                        } else if (parts.length == 2) {
                            //se c'è solo il nome, chiama addResource(nomeRisorsa)
                            addResource(parts[1]);
                        } else {
                            //opzione in cui c'è anche il contenuto
                            addResource(parts[1], parts[2]);
                        }
                        break;

                    //Comando download
                    case "download":// scarica una risorsa dalla rete
                        if (!connectedToMaster) {
                            //se non è connesso al master, da errore
                            System.err.println("[ERRORE] Il master non è raggiungibile. Operazione non disponibile.");
                            break;
                        }
                        if (parts.length < 2) {
                            //controlla che ci siano due parti e se non ci sono ritorna uso corretto
                            System.out.println("Uso: download <nome>");
                        } else {
                            //chiama downloadResource(nomeRisorsa)
                            downloadResource(parts[1]);
                        }
                        break;

                    default:
                        System.out.println("Comando sconosciuto.");
                }
            }
            scanner.close(); //chiude scanner
        } catch (IOException e) { //cattura eccezioni
            System.err.println("Connessione al master persa o chiusa: " + e.getMessage());
            // chiusura della socket, ignorando eccezioni
            try {
                if (socket != null && !socket.isClosed()) socket.close();
            } catch (IOException ignored) {
            }
            System.out.println("Terminazione del peer a causa della disconnessione dal master.");
            System.exit(0); //ferma il loop e termina il programma
        }
    }
    //Metodo che mostra tutte le risorse salvate localmente nella cartella resources
    private static void listLocalResources() {
        File folder = new File(resourcesPath); //crea oggetto file
        File[] files = folder.listFiles(); //ottiene la lista di tutti i file presenti nella cartella
        if (files == null || files.length == 0) { //se la cartella è vuota o non esiste
            System.out.println("Nessuna risorsa locale."); //stampa avviso
            return;
        }
        //altrimenti, stampa il nome di ogni file trovato nella cartella locale
        System.out.println("Risorse locali:");
        for (File f : files) System.out.println("- " + f.getName());
    }

    //Metodo per creare o registrare una risorsa localmente e comunicarlo al master
    private static void addResource(String name, String... content) {
        File file = new File(resourcesPath + "/" + name); //crea oggetto file che rappresenta il percorso del file che si vuole aggiungere
        // Se il file esiste già localmente
        if (file.exists()) {
            System.out.println("Risorsa " + name + " già presente localmente."); //avvisa l'utente
            try { //invia messaggio al master per assicurarsi che la risorsa sia registrata anche nella sua tabella
                out.println("add " + name + " " + peerName);
                out.flush();
                receiveResponse();
            } catch (Exception e) { //se c'è errore nella comunicazione, lo stampa
                System.err.println("Errore nell'associare la risorsa: " + e.getMessage());
            }
            return;
        }

        // Se il file non esiste e non è stato fornito il contenuto
        if (content.length == 0) { //ritorna l'uso corretto
            System.out.println("Errore: il file '" + name + "' non esiste e devi fornire il contenuto per crearlo.");
            return;
        }

        // Se il file non esiste ma viene fornito il contenuto
        //crea un nuovo file con il contenuto passato e il nome indicato
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content[0]);
            System.out.println("Risorsa " + name + " creata localmente.");//avvisa l'utente che la creazione è andata a buon fine

            //invia add al master per registrare la risorsa
            out.println("add " + name + " " + peerName);
            out.flush();
            receiveResponse();
        } catch (Exception e) {//se ci sono eccezioni, stampa errore
            System.err.println("Errore aggiunta risorsa: " + e.getMessage());
        }
    }

    //Metodo che legge le risposte del master e restituisce true se ha stampato qualcosa
    private static boolean receiveResponse() {
        String line;
        boolean printed = false; //flag
        try {
            // Legge dal canale di input le righe ricevute fino a "END" e le stampa
            while ((line = in.readLine()) != null && !line.equals("END")) {
                System.out.println(line);
                printed = true;
            }
            // Ritorna true se almeno una riga è stata stampata
            return printed;
            //se si verifica un errore, segnala la perdita di connessione e ritorna false
        } catch (IOException e) {
            // Se si verifica un errore di I/O, significa che il master non è più raggiungibile
            System.err.println("[ERRORE] Connessione al master persa.");
            connectedToMaster = false;  //aggiorna lo stato di connessione
            return false;
        }
    }


    //Metodo che gestisce l'intero processo di download di una risorsa, memorizzando i peer già contattati e lo stato di successo
    private static void downloadResource(String fileName) throws IOException {
        Set<String> triedPeers = new HashSet<>(); // tiene traccia dei peer già contattati
        boolean success = false; //flag

        //invia al master la richiesta di download della risorsa
        while (!success) {
            out.println("download " + fileName + " " + peerName);
            out.flush();
            //legge la risposta dal master
            String response = in.readLine();

            // Gestione errori
            //se la risposta è nulla o è un errore
            if (response == null || response.startsWith("ERRORE")) {
                if (response != null && response.contains("possiede già la risorsa")) {
                    //se già il peer possiede la risorsa, si stampa avviso
                    System.out.println("Risorsa '" + fileName + "' già posseduta localmente. Download annullato.");
                } else {
                    // Questo gestisce il fallimento finale del Master
                    System.out.println("Nessun peer disponibile per la risorsa '" + fileName + "'.");
                }
                receiveResponse(); // svuota eventuali "END"
                break; // esce dal ciclo
            }
            //se la risposta non è valida, stampa errore
            if (!response.startsWith("PEER")) {
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                break;
            }

            // Parsing della risposta valida
            String[] parts = response.split("\\s+");
            if (parts.length < 4) { //controlla che ci siano almeno 4 parti
                //ritorna uso corretto
                System.err.println("[ERRORE] Risposta non valida dal master: " + response);
                receiveResponse();
                break;
            }

            String peerTarget = parts[1]; //estrae nome del peer richiedente
            String peerAddress = parts[2]; //estra l'IP
            int peerPort = Integer.parseInt(parts[3]);// estrae la porta del peer e la trasforma in int

            receiveResponse(); // legge "END" dal master

            //  Evita di contattare lo stesso peer più volte
            if (triedPeers.contains(peerTarget)) { //se il peer è nella lista dei peer già contattati, stampa avviso
                System.out.println("Tutti i peer per la risorsa '" + fileName + "' sono stati già provati.");
                break;
            }
            triedPeers.add(peerTarget);//se non è ancora nella lista, lo aggiunge

            //Tenta il download
            System.out.println("Tentativo di download da " + peerTarget + " (" + peerAddress + ":" + peerPort + ")");
            //chiama PeerHandler.downloadFromPeer per scaricare il file dal peer indicato
            success = PeerHandler.downloadFromPeer(peerAddress, peerPort, fileName, resourcesPath);

            if (success) {
                //Se il download ha successo, notifica al master
                out.println("DOWNLOAD_RESULT " + fileName + " " + peerTarget + " " + peerName + " success");
                //registra la nuova risorsa
                out.println("add " + fileName + " " + peerName);
                out.flush();
                receiveResponse();
                System.out.println("Download completato con successo da " + peerTarget);
            } else {
                //Se fallisce, notifica al master e ripete il ciclo
                out.println("updatefail " + fileName + " " + peerTarget);
                out.flush();
                receiveResponse();
                System.out.println("Il peer " + peerTarget + " non ha fornito la risorsa. Richiedo un altro peer...");
            }
        }

    }
}


