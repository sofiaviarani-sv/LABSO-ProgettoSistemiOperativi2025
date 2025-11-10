package Master;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.List;

//Accetta le connessioni dai peer tramite ServerSocket
//Crea un thread dedicato per ogni peer per gestire le richieste in modo concorrente
//Smista i comandi del peer a ResourceService, che mantiene lo stato delle risorse e dei peer associati

public class SocketListener implements Runnable {
    //socket del master che ascolta le connessioni in entrata
    private final ServerSocket serverSocket;
    //riferimento alla classe ResourceService
    private final ResourceService resourceService;
    //lista dei socket attivi, usata per chiuderli tutti in caso di quit
    private final List<Socket> clients = new ArrayList<>();
    //booleano per controllare se il listener deve continuare ad accettare connesioni
    private boolean running = true;

    //Costruttore
    public SocketListener(ServerSocket serverSocket, ResourceService resourceService) {
        this.serverSocket = serverSocket;
        this.resourceService = resourceService;
    }
    @Override
    //metodo eseguito su un thread separato: gestisce in modo continuo le nuove connessioni in arrivo dai peer
    public void run() {
        //Continua ad accettare peer finchè il master non chiude il server
        while (running) {
            try {
                //Blocca l'esecuzione fino a quando un peer non prova a connettersi al master
                //A quel punto, accept() crea un nuovo oggetto Socket che rappresenta quella specifica connessione
                Socket clientSocket = serverSocket.accept();
                //Solo un thread alla volta può modificare la lista clients
                synchronized (clients) {
                    //Aggiunge il nuovo socket del peer alla lista dei socket attivi
                    clients.add(clientSocket);
                }
                //Crea un nuovo oggetto ClientHandler per gestire la comunicazione con il peer appena connesso
                //gli passa il socket del peer (clientSocket) e il riferimento a resourceService
                //che contiene lo stato condiviso (peer registrati e risorse disponibili)
                ClientHandler handler = new ClientHandler(clientSocket, resourceService);
                //crea un nuovo thread che eseguirà in parallelo il codice del ClientHandler
                //questo consente al master di gestire più peer contemporaneamente, senza bloccare
                //l'accettazione di nuove connessioni
                Thread clientThread = new Thread(handler);
                //avvia il thread del ClientHandler, che comincerà a leggere i comandi del peer e a rispondergli.
                //Dopo questa chiamata, il Master torna immediatamente in attesa di nuovi peer su accept()
                clientThread.start();

            } catch (IOException e) {
                if (running) {//Se running è true, significa che non stiamo chiudendo il server intenzionalmente
                    e.printStackTrace();//stampiamo per capire l'errore
                }
                //Se running è false, significa che il master sta chiudendo volontariamente, quindi possiamo ignorare l'eccezione
            }
        }
    }

    //Metodo per spegnere tutti i socket attivi
    public void closeAllClients() {
        running = false; //socketListener non deve accettare più connessioni
        //modifica la lista dei socket attivi
        synchronized (clients) {
            for (Socket s : clients) {
                //cicla e chiude tutti i socket uno per uno
                try { s.close(); } catch (IOException ignored) {}
            }
        }
        try {//chiude il serverSocket del master, così che non accetti più connessioni
            serverSocket.close();
        } catch (IOException ignored) {}
    }

}
