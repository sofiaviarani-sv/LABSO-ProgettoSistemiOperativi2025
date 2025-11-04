package Master;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Scanner;


public class Master {
    private static boolean running = true;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Uso corretto: java Master <porta>");
            return;
        }

        int port = Integer.parseInt(args[0]);
        ResourceService resourceService = new ResourceService();

        try (ServerSocket serverSocket = new ServerSocket(port);
             Scanner scanner = new Scanner(System.in)) {

            System.out.println("Master in ascolto sulla porta " + port);

            SocketListener listener = new SocketListener(serverSocket, resourceService);
            Thread listenerThread = new Thread(listener);
            listenerThread.start();

            while (running) {
                System.out.print("> ");
                String command = scanner.nextLine().trim();

                switch (command.toLowerCase()) {
                    case "listdata":
                        if (resourceService.getAllResources().isEmpty()) {
                            System.out.println("Nessuna risorsa disponibile.");
                        } else {
                            resourceService.getAllResources().forEach((res, peers) ->
                                    System.out.println(res + ": " + String.join(", ", peers))
                            );
                        }
                        break;

                    case "log":
                        System.out.println("Risorse scaricate:");
                        if (resourceService.getDownloadLog().isEmpty()) {
                            System.out.println("Nessun download effettuato.");
                        } else {
                            resourceService.getDownloadLog().forEach(System.out::println);
                        }
                        break;

                    case "quit":
                        running = false;
                        listener.closeAllClients();  // chiude client e serverSocket
                        listenerThread.interrupt();  // interrompe il listener se era bloccato
                        System.out.println("Master terminato.");
                        break;


                    default:
                        System.out.println("Comando sconosciuto.");
                }
            }

        } catch (IOException e) {
            if (running) e.printStackTrace();
        }
    }
}
