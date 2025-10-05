import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class hold {
    // ----- CHANGE THIS: put your folder where server files are stored -----
    private static final Path BASE_DIR = Paths.get("/media/kevin/256GB/code/malware/server/files").toAbsolutePath().normalize();
    private static final int PREVIEW_SIZE = 1024; // bytes

    public static void main(String[] args) throws IOException {
        int port = 8080;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("HTTP server running on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(() -> handleHttpClient(clientSocket)).start();
            }
        }
    }

    private static void handleHttpClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream out = clientSocket.getOutputStream()) {

            // --- Read HTTP request ---
            String line = in.readLine();
            if (line == null || line.isEmpty()) return;
            System.out.println("Request: " + line);

            // Parse only GET requests for simplicity
            if (line.startsWith("GET")) {
                String path = line.split(" ")[1];
                if (path.startsWith("/list")) {
                    handleListRequest(path, out);
                } else if (path.startsWith("/get")) {
                    handleFileDownload(path, out);
                } else if (path.startsWith("/preview")) {
                    handleFilePreview(path, out);
                } else if (path.startsWith("/encode")) {
                    handleEncodeCommand(path, out);
                } else {
                    sendHttpResponse(out, 404, "text/plain", "Unknown command");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    // ---------------- ENCODING ----------------
    private static void handleEncodeCommand(String path, OutputStream out) throws IOException {
        try {
            // Expect: /encode?cmd=001%20download%20-t%20"arg"
            String query = URLDecoder.decode(path.replace("/encode?", ""), "UTF-8");
            String encoded = encodeCommand(query);
            sendHttpResponse(out, 200, "text/plain", encoded);
        } catch (Exception e) {
            sendHttpResponse(out, 500, "text/plain", "Error encoding command: " + e.getMessage());
        }
    }

    public static String encodeCommand(String input) throws Exception {
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
        while (m.find()) tokens.add(m.group(1) != null ? m.group(1) : m.group(2));

        String code = tokens.size() > 0 ? tokens.get(0) : "000";
        String command = tokens.size() > 1 ? tokens.get(1) : null;

        List<String> flags = new ArrayList<>();
        String argument = null;
        for (int i = 2; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("-")) flags.add(t);
            else argument = t;
        }

        String part1 = code;
        String part2 = encodeCommandType(command);
        String part3 = encodeFlags(flags);
        String part4 = encodeArgument(argument);

        return String.join(" ", Arrays.asList(part1, part2, part3, part4));
    }

    private static String encodeCommandType(String cmd) {
        Map<String, Integer> commandIndex = Map.of(
            "download", 1,
            "update", 2,
            "ping", 3,
            "get", 4
        );
        int index = commandIndex.getOrDefault(cmd, 0);
        return "2" + String.format("%02d", index);
    }

    private static String encodeFlags(List<String> flags) {
        if (flags.isEmpty()) return "0000";
        StringBuilder sb = new StringBuilder("1");
        sb.append(flags.size());
        for (int i = 0; i < flags.size(); i++) {
            sb.append(String.format("%02d", i + 1));
        }
        return sb.toString();
    }

    private static String encodeArgument(String arg) throws Exception {
        if (arg == null) return "0"; // 0 = no argument
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(arg.getBytes());
        StringBuilder hex = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            hex.append(String.format("%02x", hash[i]));
        }
        return "1" + hex.toString();
    }

    // ----------------- HTTP HELPERS -----------------
    private static void sendHttpResponse(OutputStream out, int status, String type, String body) throws IOException {
        PrintWriter pw = new PrintWriter(out, false);
        pw.printf("HTTP/1.1 %d OK\r\n", status);
        pw.printf("Content-Type: %s\r\n", type);
        pw.printf("Content-Length: %d\r\n", body.getBytes().length);
        pw.print("\r\n");
        pw.print(body);
        pw.flush();
    }

    // ----------------- FILE OPERATIONS -----------------
    private static void handleListRequest(String path, OutputStream out) throws IOException {
        String dirParam = path.replace("/list", "").trim();
        if (dirParam.startsWith("?")) dirParam = dirParam.substring(1);
        String dir = URLDecoder.decode(dirParam, "UTF-8");

        Path requested = BASE_DIR.resolve(dir).normalize();
        if (!requested.startsWith(BASE_DIR) || !Files.exists(requested)) {
            sendHttpResponse(out, 404, "application/json", "{\"error\":\"NOTFOUND\"}");
            return;
        }

        File[] files = requested.toFile().listFiles();
        StringBuilder sb = new StringBuilder("{\"files\":[");
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                sb.append("{\"name\":\"").append(f.getName()).append("\",")
                  .append("\"type\":\"").append(f.isDirectory() ? "dir" : "file").append("\"}");
                if (i < files.length - 1) sb.append(",");
            }
        }
        sb.append("]}");
        sendHttpResponse(out, 200, "application/json", sb.toString());
    }

    private static void handleFileDownload(String path, OutputStream out) throws IOException {
        String fileName = URLDecoder.decode(path.replace("/get?", ""), "UTF-8");
        Path requested = BASE_DIR.resolve(fileName).normalize();
        if (!requested.startsWith(BASE_DIR) || !Files.exists(requested)) {
            sendHttpResponse(out, 404, "text/plain", "NOTFOUND");
            return;
        }
        byte[] bytes = Files.readAllBytes(requested);
        sendHttpResponse(out, 200, "application/octet-stream", new String(bytes));
    }

    private static void handleFilePreview(String path, OutputStream out) throws IOException {
        String fileName = URLDecoder.decode(path.replace("/preview?", ""), "UTF-8");
        Path requested = BASE_DIR.resolve(fileName).normalize();
        if (!requested.startsWith(BASE_DIR) || !Files.exists(requested)) {
            sendHttpResponse(out, 404, "text/plain", "NOTFOUND");
            return;
        }
        try (InputStream in = Files.newInputStream(requested)) {
            byte[] buffer = in.readNBytes(PREVIEW_SIZE);
            sendHttpResponse(out, 200, "text/plain", new String(buffer));
        }
    }
}


