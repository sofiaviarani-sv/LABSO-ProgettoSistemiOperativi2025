package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Scanner;


public class Master {
    // rimane true finchè il master è in esecuzione
    private static boolean running = true;

    public static void main(String[] args) {
        //se l'utente non specifica la porta, stampa l'uso corretto e termina
        if (args.length != 1) {
            System.err.println("Uso corretto: java Master <porta>");
            return;
        }
        //al contrario, se l'utente inserisce la porta la legge e la converte in intero
        int port = Integer.parseInt(args[0]);

        //crea l'oggetto resourceService, che gestisce tutti i peer registrati e le risorse
        ResourceService resourceService = new ResourceService();

        //ServerSocket mette il master in ascolto di nuove connessioni
        //Scanner legge i comandi digitati dall'utente
        try (ServerSocket serverSocket = new ServerSocket(port);
             Scanner scanner = new Scanner(System.in)) {

            //Indica che il master è pronto
            System.out.println("Master in ascolto sulla porta " + port);

            //crea un oggetto SocketListener, cioè il thread che gestirà le connessioni in arrivo dai peer
            //prende nel costruttore serverSocket per chiamare accept(), creando cosi la connessione
            //e resourceService che tiene traccia dei peer registrati e delle risorse associate
            //Listener fa quindi da dispatcher, riceve connessioni e smista le richieste ai worker thread
            SocketListener listener = new SocketListener(serverSocket, resourceService);

            //crea un nuovo thread che eseguirà il codice del SocketListener in parallelo
            //così il master ascolta i peer in background mentre resta libero per ricevere comandi dalla console
            Thread listenerThread = new Thread(listener);

            //Avvia il thread e da questo momento può ricevere connessioni dai peer
            listenerThread.start();

            //Ciclo principale del master: legge i comandi dell'utente da console finchè running è true
            while (running) {
                System.out.print("> ");
                //Legge una riga dalla tastiera, trimo rimuove spazi superflui
                String command = scanner.nextLine().trim();

                //converte il comando in minuscolo
                switch (command.toLowerCase()) {

                    //Comando listdata
                    //Mostra tutte le risorse e per ognuna i peer associati
                    case "listdata":
                        if (resourceService.getAllResources().isEmpty()) { //se la mappa è vuota, stampa avviso
                            System.out.println("Nessuna risorsa disponibile.");
                        } else { //altrimenti, per ogni risorsa stampa la lista dei peer associati
                            resourceService.getAllResources().forEach((res, peers) ->
                                    System.out.println(res + ": " + String.join(", ", peers))
                            );
                        }
                        break;

                    //Comando log
                    //Mostra il registro dei download effettuati (avvenuti correttamente e non)
                    case "log":
                        System.out.println("Risorse scaricate:");
                        if (resourceService.getDownloadLog().isEmpty()) { //se l'arrayList è vuoto, stampa avviso
                            System.out.println("Nessun download effettuato.");
                        } else { //Altrimenti, stampa tutti i download
                            resourceService.getDownloadLog().forEach(System.out::println);
                        }
                        break;

                    //Comando quit
                    // termina l'esecuzione del master
                    case "quit":
                        running = false; //lo imposta per uscire dal ciclo
                        //chiude tutte le connessioni con i peer
                        listener.closeAllClients();

                        //Interrompe il thread listener (nel caso fosse bloccato in accept())
                        listenerThread.interrupt();
                        //stampa il messaggio di chiusura
                        System.out.println("Master terminato.");
                        break;


                    default:
                        //Gestisce comandi sconosciuti
                        System.out.println("Comando sconosciuto.");
                }
            }

        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }
}
