import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class helper {
    public static void main(String[] args) {
        
    }
        public static String encodeCommand(String input) throws Exception {
        // --- 1. Tokenize ---
        List<String> tokens = new ArrayList<>();
        var matcher = java.util.regex.Pattern.compile("\"([^\"]*)\"|(\\S+)").matcher(input);
        while (matcher.find()) {
            tokens.add(matcher.group(1) != null ? matcher.group(1) : matcher.group(2));
        }

        // --- 2. Extract parts ---
        String code = tokens.size() > 0 ? tokens.get(0) : "000";
        String command = tokens.size() > 1 ? tokens.get(1) : null;

        List<String> flags = new ArrayList<>();
        String argument = null;
        for (int i = 2; i < tokens.size(); i++) {
            String t = tokens.get(i);
            if (t.startsWith("-")) flags.add(t);
            else argument = t;
        }

        // --- 3. Encode each section ---
        String part1 = code;  // raw
        String part2 = encodeCommandType(command);
        String part3 = encodeFlags(flags);
        String part4 = encodeArgument(argument);

        return String.join(" ", Arrays.asList(part1, part2, part3, part4));
    }

    // Example rule: custom commands = 2 + index (hardcoded or from map)
    private static String encodeCommandType(String cmd) {
        Map<String, Integer> commandIndex = Map.of(
            "download", 1,
            "update", 2,
            "ping", 3,
            "get", 4
        );
        int index = commandIndex.getOrDefault(cmd, 0);
        return "2" + String.format("%02d", index); // example: "download" → "201"
    }

    // Flags: 1 if exist, + count + flag index (for now just numeric)
    private static String encodeFlags(List<String> flags) {
        if (flags.isEmpty()) return "0000";
        StringBuilder sb = new StringBuilder("1");
        sb.append(flags.size());
        for (int i = 0; i < flags.size(); i++) {
            sb.append(String.format("%02d", i + 1));
        }
        return sb.toString(); // ex: one flag -> "1101"
    }

    // Argument: encode into numeric ID (e.g. hash)
    private static String encodeArgument(String arg) throws Exception {
        if (arg == null) return "0000";
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] hash = md.digest(arg.getBytes());
        StringBuilder sb = new StringBuilder("12");
        for (int i = 0; i < 6; i++) { // first few bytes only
            sb.append(Math.abs(hash[i]));
        }
        return sb.toString();
    }
}
