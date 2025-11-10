package Peer;
import java.io.*;
import java.net.*;
import java.util.concurrent.Semaphore;

//Ogni peer ha un server interno che può accettare richieste da altri peer
//implementa runnable cosi da farlo partire in un thread separato perchè possa girare in parallelo al resto del peer
public class PeerServer implements Runnable {

    private final int port; //porta del peer
    private final String resourcesPath = "Peer/resources"; //cartella locale delle risorse
    private final Semaphore mutex = new Semaphore(1);// semaforo binario per garantire che venga eseguito un solo download per volta
    private boolean running = true; //controlla se il server continua a funzionare o si chiude
    private ServerSocket serverSocket; //oggetto che accetta connessioni TCP da altri peer

    public PeerServer(int port) { //
        this.port = port;
    }

    @Override
    public void run() {
        //crea un serverSocket che ascolta sulla porta del peer
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            //assegna l'oggetto alla variabile
            this.serverSocket = serverSocket;
            //stampa sul terminale che il server è attivo
            System.out.println("[PeerServer] In ascolto sulla porta " + port);

            //resta attivo finchè running è true
            while (running) {
                //accept() blocca il thread finchè un altro peer non si connette
                Socket clientSocket = serverSocket.accept();
                //quando qualcuno si connette, si crea un nuovo thread che esegue handleClient(clientSocket)
                new Thread(new PeerHandler(clientSocket, resourcesPath, mutex)).start(); // delega la gestione al PeerHandler
            }
        } catch (IOException e) {
            System.err.println(e.getMessage());
        }
    }

    //Chiude il server del peer
    //viene chiamato quando il peer si disconnette o termina
    public void stopServer() {
        running = false; //imposta running = false per terminare il ciclo
        try {
            if (serverSocket != null) serverSocket.close(); //chiude il serverSocket, sbloccando accept() in attesa
        } catch (IOException ignored) {}
    }
}