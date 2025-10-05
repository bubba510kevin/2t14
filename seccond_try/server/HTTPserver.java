import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class HTTPserver {

    // ---- CHANGE THIS ----
    private static final Path BASE_DIR = Paths.get("/media/kevin/256GB/code/malware/server/files")
            .toAbsolutePath().normalize();
    private static final int PREVIEW_SIZE = 1024;
    private static final int PORT = 8080; // HTTP default-ish port

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Register endpoints
        server.createContext("/list", HTTPserver::handleList);
        server.createContext("/download", HTTPserver::handleDownload);
        server.createContext("/preview", HTTPserver::handlePreview);
        server.createContext("/command", HTTPserver::handleCommand);

        server.setExecutor(Executors.newFixedThreadPool(10)); // concurrent clients
        server.start();

        System.out.println("HTTP server started on port " + PORT);
        System.out.println("Serving from: " + BASE_DIR);
    }

    // ----------------- LIST -----------------
    private static void handleList(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Only GET supported");
            return;
        }

        String pathParam = getQueryParam(exchange, "path");
        Path requested = BASE_DIR.resolve(pathParam == null ? "" : pathParam).normalize();

        if (!requested.startsWith(BASE_DIR) || !Files.isDirectory(requested)) {
            sendJson(exchange, 404, "{\"error\":\"NOTFOUND\"}");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("{\"files\":[");
        File[] files = requested.toFile().listFiles();
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                sb.append("{\"name\":\"").append(f.getName())
                        .append("\",\"type\":\"").append(f.isDirectory() ? "dir" : "file").append("\"}");
                if (i < files.length - 1) sb.append(",");
            }
        }
        sb.append("]}");

        sendJson(exchange, 200, sb.toString());
    }

    // ----------------- DOWNLOAD -----------------
    private static void handleDownload(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Only GET supported");
            return;
        }

        String fileParam = getQueryParam(exchange, "file");
        if (fileParam == null) {
            sendText(exchange, 400, "Missing ?file parameter");
            return;
        }

        Path requested = BASE_DIR.resolve(fileParam).normalize();
        if (!requested.startsWith(BASE_DIR) || !Files.exists(requested) || !Files.isRegularFile(requested)) {
            sendText(exchange, 404, "NOTFOUND");
            return;
        }

        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + requested.getFileName() + "\"");
        exchange.sendResponseHeaders(200, Files.size(requested));

        try (OutputStream os = exchange.getResponseBody()) {
            Files.copy(requested, os);
        }

        System.out.println("File downloaded: " + requested.getFileName());
    }

    // ----------------- PREVIEW -----------------
    private static void handlePreview(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Only GET supported");
            return;
        }

        String fileParam = getQueryParam(exchange, "file");
        if (fileParam == null) {
            sendText(exchange, 400, "Missing ?file parameter");
            return;
        }

        Path requested = BASE_DIR.resolve(fileParam).normalize();
        if (!requested.startsWith(BASE_DIR) || !Files.exists(requested) || !Files.isRegularFile(requested)) {
            sendText(exchange, 404, "NOTFOUND");
            return;
        }

        long sizeToSend = Math.min(Files.size(requested), PREVIEW_SIZE);
        exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
        exchange.sendResponseHeaders(200, sizeToSend);

        try (InputStream in = Files.newInputStream(requested);
             OutputStream out = exchange.getResponseBody()) {
            byte[] buf = new byte[8192];
            long remaining = sizeToSend;
            int read;
            while (remaining > 0 && (read = in.read(buf, 0, (int)Math.min(buf.length, remaining))) != -1) {
                out.write(buf, 0, read);
                remaining -= read;
            }
        }

        System.out.println("Preview served: " + requested.getFileName());
    }

    // ----------------- COMMAND -----------------
    private static void handleCommand(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Only POST supported");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes());

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "python3",
                    "/media/kevin/256GB/code/malware/server/api/command_processor.py", // Python decoder
                    body
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            sendJson(exchange, 200, output.toString());

        } catch (Exception e) {
            e.printStackTrace();
            sendText(exchange, 500, "Error executing command: " + e.getMessage());
        }
    }

    // ----------------- Helpers -----------------
    private static String getQueryParam(HttpExchange exchange, String key) {
        URI uri = exchange.getRequestURI();
        String query = uri.getQuery();
        if (query == null) return null;
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && kv[0].equalsIgnoreCase(key)) {
                return URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void sendText(HttpExchange ex, int code, String msg) throws IOException {
        byte[] bytes = msg.getBytes();
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] bytes = json.getBytes();
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
