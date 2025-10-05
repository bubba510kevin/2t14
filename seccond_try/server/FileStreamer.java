import java.io.*;
import java.nio.file.*;

public class FileStreamer {

    // Stream file to any OutputStream (server socket)
    public static void stream(Path p, OutputStream out) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            out.flush();
        }
    }

    // Optional main() for standalone usage
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: FileStreamer <file>");
            System.exit(1);
        }
        stream(Paths.get(args[0]), System.out);
    }
}


