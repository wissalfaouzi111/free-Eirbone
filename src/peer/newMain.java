import java.io.*;
import java.net.*;
import java.util.*;

public class newMain {

    private static final String DOWNLOADS_DIR = "data/downloads";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        new File("data/downloads").mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        Announce proto = new Announce();

        step("CHARGEMENT CONFIG");
        Config config = Config.load("config.ini");

        log("trackerHost = " + config.trackerHost);
        log("trackerPort = " + config.trackerPort);
        log("listenPort  = " + config.listenPort);

        step("CHARGEMENT FICHIERS LOCAUX");

        PeerState state = new PeerState();

        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();

        String firstKey = null;
        String firstFilename = null;
        int firstPieces = 0;

        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;

                FileMetadata meta = new FileChunker(f).getMetadata();

                FileInfo fi = new FileInfo(
                        meta.getFilename(),
                        meta.getLength(),
                        meta.getPieceSize(),
                        meta.getKey()
                );

                DownloadState ds = new DownloadState(fi);
                loadRealPiecesIntoStorage(f, ds);
                state.addDownload(ds);

                log("Fichier chargé : " + fi.getFilename()
                        + " | pieces=" + fi.totalPieces()
                        + " | key=" + fi.getKey());

                if (firstKey == null) {
                    firstKey = fi.getKey();
                    firstFilename = fi.getFilename();
                    firstPieces = fi.totalPieces();
                }
            }
        }

        check("Au moins un fichier dans data/downloads", firstKey != null);

        if (firstKey == null) {
            log("Crée un fichier avec :");
            log("echo bonjour > data/downloads/test.txt");
            return;
        }

        step("DEMARRAGE PEER SERVER");

        Thread serverThread = new Thread(new PeerServer(config, state));
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);

        check("PeerServer démarré", serverThread.isAlive());

        step("CONNEXION TRACKER");

        Socket trackerSocket = new Socket(
                config.trackerHost,
                Integer.parseInt(config.trackerPort)
        );

        InputStream trackerIn = trackerSocket.getInputStream();

        PrintWriter trackerWriter = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(trackerSocket.getOutputStream())),
                true
        );

        check("Connexion Tracker établie", trackerSocket.isConnected());

        step("ANNOUNCE");

        String announceMsg = proto.announce(config);

        log("→ " + announceMsg.trim());
        trackerWriter.print(announceMsg);
        trackerWriter.flush();

        String okAnnounce = readAsciiLine(trackerIn);
        log("← " + okAnnounce);

        check("Tracker répond ok à announce", "ok".equals(okAnnounce));

        step("LOOK");

        String lookMsg = proto.look(firstFilename);

        log("→ " + lookMsg.trim());
        trackerWriter.print(lookMsg);
        trackerWriter.flush();

        String listResponse = readAsciiLine(trackerIn);
        log("← " + listResponse);

        check("Réponse list reçue", listResponse != null && listResponse.startsWith("list"));

        List<FileInfo> foundFiles = TrackerResponse.parseListResponse(listResponse);
        check("Fichier trouvé avec look", !foundFiles.isEmpty());

        step("GETFILE");

        String getfileMsg = proto.getfile(firstKey);

        log("→ " + getfileMsg.trim());
        trackerWriter.print(getfileMsg);
        trackerWriter.flush();

        String peersResponse = readAsciiLine(trackerIn);
        log("← " + peersResponse);

        check("Réponse peers reçue", peersResponse != null && peersResponse.startsWith("peers"));

        PeersResult peersResult = TrackerResponse.parsePeersResponse(peersResponse);
        check("Peers parsés", peersResult != null);

        if (peersResult != null) {
            log("Nombre de peers reçus : " + peersResult.peers.size());
        }

        step("CONNEXION PEER LOCAL + INTERESTED");

        Socket peerSocket = new Socket("127.0.0.1", Integer.parseInt(config.listenPort));

        InputStream peerIn = peerSocket.getInputStream();

        PrintWriter peerWriter = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(peerSocket.getOutputStream())),
                true
        );

        check("Connexion au peer local", peerSocket.isConnected());

        String interestedMsg = proto.interested(firstKey);

        log("→ " + interestedMsg.trim());
        peerWriter.print(interestedMsg);
        peerWriter.flush();

        String haveResponse = readAsciiLine(peerIn);
        log("← " + haveResponse);

        check("Réponse have reçue", haveResponse != null && haveResponse.startsWith("have"));
        check("have contient la bonne key", haveResponse != null && haveResponse.contains(firstKey));

        step("GETPIECES");

        List<Integer> indices = new ArrayList<>();
        int nbPieces = Math.min(3, firstPieces);

        for (int i = 0; i < nbPieces; i++) {
            indices.add(i);
        }

        String getpiecesMsg = proto.getpieces(firstKey, indices);

        log("→ " + getpiecesMsg.trim());
        peerWriter.print(getpiecesMsg);
        peerWriter.flush();

        String dataHeader = readAsciiLine(peerIn);
        log("← " + dataHeader);

        check("Header data reçu", dataHeader != null && dataHeader.startsWith("data"));

        int totalBytes = totalBytesFromDataHeader(dataHeader);
        log("Octets binaires à lire = " + totalBytes);

        if (totalBytes > 0) {
            byte[] payload = readExactly(peerIn, totalBytes);
            check("Payload reçue", payload.length == totalBytes);
            log("Premiers octets : " + bytesToHex(payload, Math.min(16, payload.length)));
        } else {
            check("Payload non vide", false);
        }

        step("HAVE PERIODIQUE");

        DownloadState dl = state.getDownload(firstKey);
        String bm = encodeBuffermap(dl.getBufferMap(), firstPieces);
        String haveMsg = "have " + firstKey + " " + bm + "\n";

        log("→ have " + firstKey + " <buffermap>");
        peerWriter.print(haveMsg);
        peerWriter.flush();

        String haveBack = readAsciiLine(peerIn);
        log("← " + haveBack);

        check("Réponse have périodique reçue", haveBack != null && haveBack.startsWith("have"));

        step("UPDATE TRACKER");

        String updateMsg = "update seed [ " + firstKey + " ] leech [ ]\n";

        log("→ " + updateMsg.trim());
        trackerWriter.print(updateMsg);
        trackerWriter.flush();

        String okUpdate = readAsciiLine(trackerIn);
        log("← " + okUpdate);

        check("Tracker répond ok à update", "ok".equals(okUpdate));

        peerSocket.close();
        trackerSocket.close();

        System.out.println();
        System.out.println("════════════════════════════════════");
        System.out.println("RESULTAT : " + passed + " PASSED / " + failed + " FAILED");
        System.out.println("════════════════════════════════════");
    }

    private static void loadRealPiecesIntoStorage(File file, DownloadState ds) throws IOException {
        int pieceSize = ds.file.getPieceSize();

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[pieceSize];
            int read;
            int index = 0;

            while ((read = fis.read(buffer)) != -1) {
                byte[] piece = Arrays.copyOf(buffer, read);
                ds.writePiece(index, piece);
                index++;
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        int b;

        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buffer.write(b);
        }

        if (b == -1 && buffer.size() == 0) return null;

        return buffer.toString("UTF-8");
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] data = new byte[n];
        int offset = 0;

        while (offset < n) {
            int read = in.read(data, offset, n - offset);

            if (read == -1) {
                throw new EOFException("Connexion fermée après " + offset + " octets");
            }

            offset += read;
        }

        return data;
    }

    private static int totalBytesFromDataHeader(String header) {
        if (header == null) return 0;

        int open = header.indexOf('[');
        int close = header.indexOf(']');

        if (open < 0 || close < 0 || close <= open) return 0;

        String inside = header.substring(open + 1, close).trim();

        if (inside.isEmpty()) return 0;

        String[] tokens = inside.split("\\s+");

        int total = 0;

        for (int i = 1; i < tokens.length; i += 2) {
            total += Integer.parseInt(tokens[i]);
        }

        return total;
    }

    private static String encodeBuffermap(BitSet bm, int totalPieces) {
        int nbBytes = (totalPieces + 7) / 8;
        byte[] bytes = new byte[nbBytes];

        for (int i = 0; i < totalPieces; i++) {
            if (bm.get(i)) {
                bytes[i / 8] |= (byte) (1 << (7 - (i % 8)));
            }
        }

        return Base64.getEncoder().encodeToString(bytes);
    }

    private static String bytesToHex(byte[] data, int max) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < max; i++) {
            sb.append(String.format("%02x ", data[i]));
        }

        return sb.toString();
    }

    private static void step(String title) {
        System.out.println();
        System.out.println("────────────────────────────────────");
        System.out.println("TEST : " + title);
        System.out.println("────────────────────────────────────");
    }

    private static void log(String msg) {
        System.out.println("  " + msg);
    }

    private static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASSED] " + label);
            passed++;
        } else {
            System.out.println("  [FAILED] " + label);
            failed++;
        }
    }
}