import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;


public class ChatServer {
    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public";

    // Mappa dei messaggi per stanza
    private static final Map<String, List<String>> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Endpoint per inviare messaggi
        server.createContext("/send", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String[] parts = body.split("&");
                String room = getValue(parts, "room");
                String message = getValue(parts, "message");
                System.out.println(parts[1] + " " + parts[2]);

                if (room != null && message != null) {
                    rooms.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>()).add(message);
                    sendText(exchange, "OK");
                } else {
                    sendText(exchange, "Missing room or message", 400);
                }
            }
        });

        // Endpoint per ricevere messaggi
        server.createContext("/poll", exchange -> {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String query = exchange.getRequestURI().getQuery();
                String room = null;
                if (query != null) {
                    for (String pair : query.split("&")) {
                        String[] kv = pair.split("=");
                        if (kv.length == 2 && kv[0].equals("room")) {
                            room = URLDecoder.decode(kv[1], "UTF-8");
                        }
                    }
                }

                if (room != null) {
                    List<String> messages = rooms.getOrDefault(room, List.of());
                    String response = String.join("\n", messages);
                    sendText(exchange, response);
                } else {
                    sendText(exchange, "Missing room", 400);
                }
            } else {
                exchange.sendResponseHeaders(405, -1);
            }
        });

        // Endpoint login e registrazione
        server.createContext("/login", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String[] parts = body.split("&");
                String username = getValue(parts, "username");
                String password = getValue(parts, "password");

                if (username != null && password != null) {
                    synchronized (ChatServer.class) {
                        File dataDir = new File("data");
                        if (!dataDir.exists()) {
                            System.out.println("Cartella data inesistente ");
                            return;
                        }

                        // File utenti.txt
                        File userFile = new File(dataDir, "utenti.txt");
                        if (!userFile.exists()) {
                            System.out.println("File utenti.txt inesistente ");
                            return;
                        }

                        // Leggo utenti esistenti
                        Map<String, String> users = new HashMap<>();
                        try (BufferedReader reader = new BufferedReader(new FileReader(userFile))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] kv = line.split(":");
                                if (kv.length == 2) {
                                    users.put(kv[0], kv[1]);
                                }
                            }
                        }

                        String passwordHash = hashPassword(password);

                        if (users.containsKey(username)) { //Utente gia registrato
                            if (users.get(username).equals(passwordHash)) {
                                sendText(exchange, "✔️ Login avvenuto con successo");
                            } else {
                                sendText(exchange, "❌ Username o Password errati", 401);
                            }
                        } else { //Utente non registrato
                            System.out.println("Aggiungo utente: " + username);
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile, true))) {
                                writer.write(username + ":" + passwordHash);
                                writer.newLine();
                            }
                            sendText(exchange, "☑️ User registered successfully");
                        }
                    }
                }
            }
        });

        // Serve file statici (html, js, css)
        server.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path.equals("/")) path = "/login.html";
            File file = new File(PUBLIC_DIR, path);
            if (file.exists() && !file.isDirectory()) {
                String mime = Files.probeContentType(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", mime != null ? mime : "application/octet-stream");
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis = new FileInputStream(file)) {
                    fis.transferTo(os);
                }
            } else {
                sendText(exchange, "404 Not Found", 404);
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ Server in esecuzione su http://" + getLocalIP() + ":" + PORT);

    }

    private static String getValue(String[] pairs, String key) {
        for (String pair : pairs) {
            String[] kv = pair.split("=");
            if (kv.length == 2 && kv[0].equals(key)) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendText(HttpExchange exchange, String response) throws IOException {
        sendText(exchange, response, 200);
    }

    private static void sendText(HttpExchange exchange, String response, int statusCode) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*"); // CORS
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String getLocalIP() throws IOException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            return socket.getLocalAddress().getHostAddress();
        }
    }

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Errore hash password", e);
        }
    }
}
