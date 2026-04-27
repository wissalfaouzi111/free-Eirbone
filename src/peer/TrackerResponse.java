import java.util.*;

public class TrackerResponse {

    public static List<FileInfo> parseListResponse(String line) {
        List<FileInfo> files = new ArrayList<>();
        if (line == null ||!line.startsWith("list ")) {
            return files;
        }
        int start = line.indexOf('[');
        int end = line.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            return files;
        }

        String inside = line.substring(start + 1, end).trim();
        if (inside.isEmpty()) {
            return files;
        }

        String[]tokens = inside.split("\\s+");//coupe la chaîne selon les espaces

        for (int i = 0; i + 3 < tokens.length; i += 4) {
            String filename = tokens[i];
            long length = Long.parseLong(tokens[i + 1]);
            int pieceSize = Integer.parseInt(tokens[i + 2]);
            String key = tokens[i + 3];

            files.add(new FileInfo(filename, length, pieceSize, key));
        }
        return files;
    }



    public static PeersResult parsePeersResponse(String line) {
        if (line == null ||!line.startsWith("peers ")) {
            return null;
        }
        String[] firstSplit = line.split("\\s+", 3);
        if (firstSplit.length < 3) {
            return null;
        }

        String key = firstSplit[1];
        String rest = firstSplit[2];

        int start = rest.indexOf('[');
        int end = rest.lastIndexOf(']');

        List<PeerInfo> peers = new ArrayList<>();

        if (start != -1 && end != -1 && end > start) {
            String inside = rest.substring(start + 1, end).trim();

            if (!inside.isEmpty()) {
                String[] entries = inside.split("\\s+");

                for (String entry : entries) {
                    String[] parts = entry.split(":");
                    if (parts.length == 2) {
                        String ip = parts[0];
                        int port = Integer.parseInt(parts[1]);
                        peers.add(new PeerInfo(ip, port));
                    }
                }
            }
        }

        return new PeersResult(key, peers);
    }

}