import com.sun.net.httpserver.*;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

public class ChatServer {
    private static final int PORT = 8080;
    private static final String PUBLIC_DIR = "public";
    private static final Map<String, List<String>> rooms = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        // HTTPS Setup
        HttpsServer server = HttpsServer.create(new InetSocketAddress(PORT), 0);
        char[] pass = "123456".toCharArray(); // ⚠

        KeyStore ks = KeyStore.getInstance("JKS");
        FileInputStream fis = new FileInputStream("C:\\Users\\mar20\\OneDrive\\Desktop\\Progetto_fine\\ChatServer\\src\\keystore.jks");
        ks.load(fis, pass);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, pass);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), null, null);

        server.setHttpsConfigurator(new HttpsConfigurator(ssl));

        // /send
        server.createContext("/send", exchange -> {
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String[] parts = body.split("&");
                String room = getValue(parts, "room");
                String message = getValue(parts, "message");

                rooms.computeIfAbsent(room, k -> new CopyOnWriteArrayList<>()).add(message);
                sendText(exchange, "OK");
            }
        });

        // /poll
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

        // /login
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
                            sendText(exchange, "Cartella data inesistente", 500);
                            return;
                        }

                        File userFile = new File(dataDir, "utenti.txt");
                        if (!userFile.exists()) {
                            sendText(exchange, "File utenti.txt inesistente", 500);
                            return;
                        }

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

                        if (users.containsKey(username)) {
                            if (users.get(username).equals(passwordHash)) {
                                sendText(exchange, "✔️ Login avvenuto con successo");
                            } else {
                                sendText(exchange, "❌ Username o Password errati", 401);
                            }
                        } else {
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter(userFile, true))) {
                                writer.write(username + ":" + passwordHash);
                                writer.newLine();
                            }
                            sendText(exchange, "☑️ Utente registrato con successo");
                        }
                    }
                }
            }
        });

        // Serve file statici
        server.createContext("/", exchange -> {
            URI uri = exchange.getRequestURI();
            String path = uri.getPath();
            if (path.equals("/")) path = "/login.html";
            File file = new File(PUBLIC_DIR, path);
            if (file.exists() && !file.isDirectory()) {
                String mime = Files.probeContentType(file.toPath());
                exchange.getResponseHeaders().set("Content-Type", mime != null ? mime : "application/octet-stream");
                exchange.sendResponseHeaders(200, file.length());
                try (OutputStream os = exchange.getResponseBody(); FileInputStream fis2 = new FileInputStream(file)) {
                    fis2.transferTo(os);
                }
            } else {
                sendText(exchange, "404 Not Found", 404);
            }
        });

        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println("✅ Server HTTPS in esecuzione su https://" + getLocalIP() + ":" + PORT);
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
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
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