import java.io.*;
import java.net.*;
import java.util.*;

public class ChatApplication {
    private static Set<PrintWriter> clientWriters = new HashSet<>();

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("server")) {
            startServer();
        } else {
            startClient();
        }
    }

    // Method to start the chat server
    public static void startServer() {
        System.out.println("Chat server started...");
        try (ServerSocket serverSocket = new ServerSocket(8000)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    // Method to start the chat client
    public static void startClient() {
        final int MAX_RETRIES = 5;
        final int RETRY_DELAY = 2000; // 2 seconds

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try (Socket socket = new Socket("localhost", 8000);
                 BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));
                 PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                System.out.println("Connected to server. Type your messages (type 'exit' to quit):");

                // Start a thread to receive messages from the server
                new Thread(new MessageReceiver(in)).start();

                String message;
                while (true) {
                    message = userInput.readLine();
                    if (message == null || message.equalsIgnoreCase("exit")) {
                        break;
                    }
                    out.println(message);
                }
                return; // Exit the method if connection was successful
            } catch (ConnectException e) {
                System.out.println("Connection attempt " + (attempt + 1) + " failed. Retrying in 2 seconds...");
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted while trying to connect");
                    return;
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
                return;
            }
        }
        System.err.println("Failed to connect to the server after " + MAX_RETRIES + " attempts");
    }

    // Inner class to handle communication with each connected client
    private static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                out = new PrintWriter(socket.getOutputStream(), true);

                synchronized (clientWriters) {
                    clientWriters.add(out);
                }

                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                    broadcast(message);
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                if (out != null) {
                    synchronized (clientWriters) {
                        clientWriters.remove(out);
                    }
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Error closing client socket: " + e.getMessage());
                }
            }
        }

        private void broadcast(String message) {
            synchronized (clientWriters) {
                for (PrintWriter writer : clientWriters) {
                    writer.println(message);
                }
            }
        }
    }

    // Inner class to receive messages from the server
    private static class MessageReceiver implements Runnable {
        private BufferedReader in;

        public MessageReceiver(BufferedReader in) {
            this.in = in;
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = in.readLine()) != null) {
                    System.out.println("Received: " + message);
                }
            } catch (IOException e) {
                System.err.println("Error receiving message: " + e.getMessage());
            }
        }
    }
}

