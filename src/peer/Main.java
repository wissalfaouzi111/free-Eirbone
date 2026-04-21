package peer;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.Base64;

public class Main {
    private static final String DOWNLOADS_DIR = "data/downloads";
    private static final int SERVER_PORT = 2222;
    private static PeerState peerState;

    public static void main(String[] args) throws Exception {
        // Create directories if they do not exist
        new File(DOWNLOADS_DIR).mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        // Load local file state
        peerState = createPeerStateFromDownloads();

        // Start server
        Config config = new Config();
        config.listenPort = String.valueOf(SERVER_PORT);
        PeerServer server = new PeerServer(config, peerState);
        Thread serverThread = new Thread(() -> {
            try {
                System.out.println("[SERVER] Starting server on port " + SERVER_PORT);
                server.run();
            } catch (Exception e) {
                System.out.println("[SERVER] Error: " + e.getMessage());
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();

        // Wait a bit for it to start
        Thread.sleep(1000);

        if (args.length >= 1 && args[0].equals("debug_download") && args.length == 4) {
            // Debug mode full download: debug_download <ip> <port> <key>
            String ip = args[1];
            int port = Integer.parseInt(args[2]);
            String key = args[3];
            runDebugDownload(ip, port, key);
        } else {
            // Interactive mode
            runInteractiveMode();
        }
    }

    private static void runInteractiveMode() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.println("\n=== INTERACTIVE MENU ===");
            System.out.println("1. listen - Show served files and listen for connections");
            System.out.println("2. download <ip> <port> <key> - Download complete file");
            System.out.println("3. list - List locally available files");
            System.out.println("4. exit - Exit");
            System.out.print("Select option: ");
            String input = scanner.nextLine().trim();

            if (input.equals("1") || input.startsWith("listen")) {
                showServedFiles();
                System.out.println("[INFO] Server listening on port " + SERVER_PORT + ". Press Ctrl+C to exit.");
                // Stay listening (server is already running)
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    break;
                }
            } else if (input.startsWith("2") || input.startsWith("download ")) {
                String[] parts = input.split("\\s+");
                if (parts.length == 4) {
                    try {
                        String ip = parts[1];
                        int port = Integer.parseInt(parts[2]);
                        String key = parts[3];
                        runDebugDownload(ip, port, key);
                    } catch (Exception e) {
                        System.out.println("[ERROR] Invalid format. Use: download <ip> <port> <key>");
                    }
                } else {
                    System.out.println("[ERROR] Use: download <ip> <port> <key>");
                }
            } else if (input.equals("3") || input.startsWith("list")) {
                listAvailableFiles();
            } else if (input.equals("4") || input.equals("exit")) {
                break;
            } else {
                System.out.println("[ERROR] Invalid option.");
            }
        }
        scanner.close();
    }

    private static void showServedFiles() {
        System.out.println("[INFO] Files being served:");
        for (DownloadState ds : peerState.downloads.values()) {
            FileInfo fi = ds.file;
            System.out.println("- " + fi.getFilename() + " | key=" + fi.getKey() + " | pieces=" + fi.totalPieces() + " | complete=" + ds.isComplete());
        }
        if (peerState.downloads.isEmpty()) {
            System.out.println("  (None)");
        }
    }

    private static void listAvailableFiles() {
        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            System.out.println("[DEBUG] No files in " + DOWNLOADS_DIR);
            return;
        }

        System.out.println("[DEBUG] Available files:");
        for (File file : files) {
            if (file.isFile()) {
                try {
                    FileChunker chunker = new FileChunker(file);
                    FileMetadata meta = chunker.getMetadata();
                    System.out.println("- " + meta.getFilename() + " | size=" + meta.getLength() +
                                       " | pieces=" + meta.getNumberOfPieces() + " | key=" + meta.getKey());
                } catch (IOException e) {
                    System.out.println("- " + file.getName() + " | Error: " + e.getMessage());
                }
            }
        }
    }

    private static void runDebugDownload(String ip, int port, String key) throws IOException {
        System.out.println("[CLIENT] Connecting to " + ip + ":" + port + " for key=" + key);

        try (Socket socket = new Socket(ip, port);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // Send interested
            writer.println("interested " + key);
            System.out.println("[CLIENT] Sent: interested " + key);

            // Receive have
            String response = reader.readLine();
            System.out.println("[CLIENT] Received: " + response);
            if (!response.startsWith("have " + key + " ")) {
                System.out.println("[CLIENT] Unexpected response");
                return;
            }

            String base64 = response.substring(("have " + key + " ").length());
            BitSet remoteBufferMap = decodeBufferMap(base64, 10000); // Assume max pieces
            System.out.println("[CLIENT] Remote BufferMap: " + remoteBufferMap.cardinality() + " pieces available");

            // Request all available pieces
            List<Integer> toRequest = new ArrayList<>();
            for (int i = 0; i < remoteBufferMap.length(); i++) {
                if (remoteBufferMap.get(i)) {
                    toRequest.add(i);
                }
            }

            if (toRequest.isEmpty()) {
                System.out.println("[CLIENT] No pieces available");
                return;
            }

            // Send getpieces
            StringBuilder sb = new StringBuilder("getpieces " + key + " [");
            for (int i = 0; i < toRequest.size(); i++) {
                if (i > 0) sb.append(" ");
                sb.append(toRequest.get(i));
            }
            sb.append("]");
            writer.println(sb.toString());
            System.out.println("[CLIENT] Sent: getpieces with " + toRequest.size() + " pieces");

            // Receive data header
            String dataHeader = reader.readLine();
            System.out.println("[CLIENT] Received header: " + dataHeader);

            // Parse header to get list of (idx, size)
            List<int[]> piecesInfo = parseDataHeader(dataHeader, key);
            if (piecesInfo.isEmpty()) {
                System.out.println("[CLIENT] Invalid header");
                return;
            }

            // Create DownloadState for the file
            int totalSize = piecesInfo.stream().mapToInt(p -> p[1]).sum();
            FileInfo fileInfo = new FileInfo("downloaded_" + key, totalSize, 1024, key);
            DownloadState ds = new DownloadState(fileInfo);

            // Read and write each piece
            InputStream in = socket.getInputStream();
            for (int[] piece : piecesInfo) {
                int idx = piece[0];
                int size = piece[1];
                byte[] data = new byte[size];
                int read = in.read(data);
                if (read == size) {
                    ds.writePiece(idx, data);
                    System.out.println("[CLIENT] Written piece " + idx + " (" + size + " bytes)");
                } else {
                    System.out.println("[CLIENT] Error reading piece " + idx + ", bytes read: " + read);
                }
            }

            // Check if complete
            if (ds.isComplete()) {
                ds.getStorage().finalizeDownload("downloaded_" + key + ".complete");
                System.out.println("[CLIENT] Download completed and file finalized in data/complete/");
            } else {
                System.out.println("[CLIENT] Partial download completed (" + ds.getBufferMap().cardinality() + "/" + fileInfo.totalPieces() + " pieces)");
            }
        }
    }

    private static List<int[]> parseDataHeader(String header, String expectedKey) {
        List<int[]> pieces = new ArrayList<>();
        // Format: data <key> [<idx> <size> ...]
        String[] parts = header.split("\\s+");
        if (parts.length < 3 || !parts[0].equals("data") || !parts[1].equals(expectedKey)) {
            return pieces;
        }

        // Find [ and ]
        int start = header.indexOf('[');
        int end = header.lastIndexOf(']');
        if (start == -1 || end == -1 || end <= start) {
            return pieces;
        }

        String inside = header.substring(start + 1, end).trim();
        String[] tokens = inside.split("\\s+");
        for (int i = 0; i < tokens.length; i += 2) {
            if (i + 1 < tokens.length) {
                try {
                    int idx = Integer.parseInt(tokens[i]);
                    int size = Integer.parseInt(tokens[i + 1]);
                    pieces.add(new int[]{idx, size});
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return pieces;
    }

    private static PeerState createPeerStateFromDownloads() throws IOException {
        PeerState state = new PeerState();
        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    FileChunker chunker = new FileChunker(file);
                    FileMetadata meta = chunker.getMetadata();
                    FileInfo fileInfo = new FileInfo(meta.getFilename(), meta.getLength(), meta.getPieceSize(), meta.getKey());
                    DownloadState ds = new DownloadState(fileInfo);
                    // Mark all pieces as available
                    for (int i = 0; i < fileInfo.totalPieces(); i++) {
                        ds.markPiece(i);
                    }
                    state.addDownload(ds);
                }
            }
        }
        return state;
    }

    private static BitSet decodeBufferMap(String base64, int totalPieces) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        BitSet bs = new BitSet(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            if (i / 8 < bytes.length) {
                if ((bytes[i / 8] & (1 << (7 - (i % 8)))) != 0) {
                    bs.set(i);
                }
            }
        }
        return bs;
    }
}