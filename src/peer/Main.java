import java.io.*;
import java.net.*;
import java.util.*;

/**
 * MainAuto — teste toutes les commandes dans l'ordre automatiquement.
 *
 * Ordre des tests :
 *  1. Démarrer PeerServer local
 *  2. Connexion Tracker
 *  3. announce
 *  4. look
 *  5. getfile
 *  6. Connexion pair → interested
 *  7. getpieces
 *  8. have (échange périodique)
 *  9. update (mise à jour Tracker)
 */
public class Main {

    private static final String DOWNLOADS_DIR = "data/downloads";
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        new File("data/downloads").mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        // ─────────────────────────────────────────────────────────────────
        step("CHARGEMENT CONFIG");
        // ─────────────────────────────────────────────────────────────────
        Config config = Config.load("config.ini");
        log("trackerHost = " + config.trackerHost);
        log("trackerPort = " + config.trackerPort);
        log("listenPort  = " + config.listenPort);

        // ─────────────────────────────────────────────────────────────────
        step("CHARGEMENT FICHIERS LOCAUX");
        // ─────────────────────────────────────────────────────────────────
        PeerState state = new PeerState();
        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        String firstKey = null;
        String firstFilename = null;
        int    firstPieces = 0;

        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                FileChunker  chunker = new FileChunker(f);
                FileMetadata meta    = chunker.getMetadata();
                FileInfo fi = new FileInfo(
                    meta.getFilename(), meta.getLength(),
                    meta.getPieceSize(), meta.getKey());
                DownloadState ds = new DownloadState(fi);
                for (int i = 0; i < fi.totalPieces(); i++) ds.markPiece(i);
                state.addDownload(ds);
                log("Fichier chargé : " + fi.getFilename()
                    + " | pieces=" + fi.totalPieces()
                    + " | key=" + fi.getKey());
                if (firstKey == null) {
                    firstKey      = fi.getKey();
                    firstFilename = fi.getFilename();
                    firstPieces   = fi.totalPieces();
                }
            }
        }
        check("Au moins un fichier dans data/downloads", firstKey != null);
        if (firstKey == null) {
            log("ARRET : créez un fichier dans data/downloads/ et relancez.");
            log("  dd if=/dev/urandom of=data/downloads/test.dat bs=1024 count=5");
            System.exit(1);
        }

        // ─────────────────────────────────────────────────────────────────
        step("DEMARRAGE PEERSERVER");
        // ─────────────────────────────────────────────────────────────────
        Config serverConfig = new Config();
        serverConfig.listenPort = config.listenPort;
        Thread serverThread = new Thread(new PeerServer(serverConfig, state));
        serverThread.setDaemon(true);
        serverThread.start();
        Thread.sleep(300);
        log("PeerServer démarré sur le port " + config.listenPort);
        check("PeerServer démarré", serverThread.isAlive());

        // ─────────────────────────────────────────────────────────────────
        step("CONNEXION AU TRACKER");
        // ─────────────────────────────────────────────────────────────────
        Socket trackerSocket = new Socket(config.trackerHost,
                Integer.parseInt(config.trackerPort));
        BufferedReader trackerReader = new BufferedReader(
                new InputStreamReader(trackerSocket.getInputStream()));
        PrintWriter trackerWriter = new PrintWriter(
                new BufferedWriter(
                    new OutputStreamWriter(trackerSocket.getOutputStream())), true);
        check("Connexion Tracker établie", trackerSocket.isConnected());

        // ─────────────────────────────────────────────────────────────────
        step("ANNOUNCE");
        // ─────────────────────────────────────────────────────────────────
        StringBuilder announceMsg = new StringBuilder("announce listen ");
        announceMsg.append(config.listenPort).append(" seed [");
        for (DownloadState ds : state.downloads.values()) {
            FileInfo fi = ds.file;
            announceMsg.append(fi.getFilename()).append(" ")
                       .append(fi.getLength()).append(" ")
                       .append(fi.getPieceSize()).append(" ")
                       .append(fi.getKey()).append(" ");
        }
        announceMsg.append("] leech []");

        log("→ " + announceMsg);
        trackerWriter.println(announceMsg.toString());
        String okAnnounce = trackerReader.readLine();
        log("← " + okAnnounce);
        check("Tracker répond 'ok' à announce", "ok".equals(okAnnounce));

        // ─────────────────────────────────────────────────────────────────
        step("LOOK");
        // ─────────────────────────────────────────────────────────────────
        String lookMsg = "look [filename=" + firstFilename + "]";
        log("→ " + lookMsg);
        trackerWriter.println(lookMsg);
        String listResponse = trackerReader.readLine();
        log("← " + listResponse);
        check("Tracker répond 'list [...]' à look",
                listResponse != null && listResponse.startsWith("list"));

        List<FileInfo> fichiersTrouves = TrackerResponse.parseListResponse(listResponse);
        check("Fichier trouvé dans la réponse look", !fichiersTrouves.isEmpty());

        // ─────────────────────────────────────────────────────────────────
        step("GETFILE");
        // ─────────────────────────────────────────────────────────────────
        String getfileMsg = "getfile " + firstKey;
        log("→ " + getfileMsg);
        trackerWriter.println(getfileMsg);
        String peersResponse = trackerReader.readLine();
        log("← " + peersResponse);
        check("Tracker répond 'peers ...' à getfile",
                peersResponse != null && peersResponse.startsWith("peers"));

        PeersResult peersResult = TrackerResponse.parsePeersResponse(peersResponse);
        check("Au moins un pair dans la réponse",
                peersResult != null && !peersResult.peers.isEmpty());

        // ─────────────────────────────────────────────────────────────────
        step("CONNEXION PAIR → INTERESTED → HAVE");
        // ─────────────────────────────────────────────────────────────────
        // On se connecte à nous-mêmes (loopback) pour tester
        int listenPort = Integer.parseInt(config.listenPort);
        Socket peerSocket = new Socket("127.0.0.1", listenPort);
        BufferedReader peerReader = new BufferedReader(
                new InputStreamReader(peerSocket.getInputStream()));
        PrintWriter peerWriter = new PrintWriter(
                new BufferedWriter(
                    new OutputStreamWriter(peerSocket.getOutputStream())), true);
        check("Connexion au pair (loopback) établie", peerSocket.isConnected());

        String interestedMsg = "interested " + firstKey;
        log("→ " + interestedMsg);
        peerWriter.println(interestedMsg);
        String haveResponse = peerReader.readLine();
        log("← " + haveResponse);
        check("Pair répond 'have ...' à interested",
                haveResponse != null && haveResponse.startsWith("have"));
        check("have contient la bonne key",
                haveResponse != null && haveResponse.contains(firstKey));

        // Décoder le buffermap
        int piecesDisponibles = 0;
        if (haveResponse != null) {
            String[] tokens = haveResponse.split("\\s+", 3);
            if (tokens.length == 3) {
                byte[] bm = Base64.getDecoder().decode(tokens[2]);
                for (byte b : bm) piecesDisponibles += Integer.bitCount(b & 0xFF);
            }
        }
        log("Pièces disponibles chez le pair : " + piecesDisponibles + "/" + firstPieces);
        check("Buffermap non vide", piecesDisponibles > 0);

        // ─────────────────────────────────────────────────────────────────
        step("GETPIECES");
        // ─────────────────────────────────────────────────────────────────
        // Demander les 3 premières pièces (ou moins si le fichier est plus petit)
        int nbPiecesADemander = Math.min(3, firstPieces);
        StringBuilder getPiecesMsg = new StringBuilder("getpieces " + firstKey + " [");
        for (int i = 0; i < nbPiecesADemander; i++) {
            if (i > 0) getPiecesMsg.append(" ");
            getPiecesMsg.append(i);
        }
        getPiecesMsg.append("]");

        log("→ " + getPiecesMsg);
        peerWriter.println(getPiecesMsg.toString());
        String dataHeader = peerReader.readLine();
        log("← Header : " + dataHeader);
        check("Pair répond 'data ...' à getpieces",
                dataHeader != null && dataHeader.startsWith("data"));
        check("Header contient la key",
                dataHeader != null && dataHeader.contains(firstKey));

        // Lire la payload binaire
        if (dataHeader != null && dataHeader.startsWith("data")) {
            int open  = dataHeader.indexOf('[');
            int close = dataHeader.indexOf(']');
            if (open >= 0 && close > open) {
                String[] tokens = dataHeader.substring(open + 1, close).trim().split("\\s+");
                int totalBytes  = 0;
                for (int i = 1; i < tokens.length; i += 2) {
                    totalBytes += Integer.parseInt(tokens[i]);
                }
                if (totalBytes > 0) {
                    byte[] payload = readExactly(peerSocket.getInputStream(), totalBytes);
                    log("Payload reçue : " + payload.length + " octets");
                    check("Taille payload correcte (" + totalBytes + " o.)",
                            payload.length == totalBytes);
                    // Afficher les premiers octets en hex
                    System.out.print("     Premiers octets (hex) : ");
                    for (int i = 0; i < Math.min(16, payload.length); i++) {
                        System.out.printf("%02x ", payload[i]);
                    }
                    System.out.println();
                }
            }
        }

        // ─────────────────────────────────────────────────────────────────
        step("HAVE PERIODIQUE (pair ↔ pair)");
        // ─────────────────────────────────────────────────────────────────
        DownloadState dl = state.downloads.get(firstKey);
        String bmBase64  = encodeBuffermap(dl.getBufferMap(), firstPieces);
        String haveMsg   = "have " + firstKey + " " + bmBase64;
        log("→ have " + firstKey + " <buffermap>");
        peerWriter.println(haveMsg);
        String haveBack = peerReader.readLine();
        log("← " + (haveBack != null ? haveBack.substring(0, Math.min(60, haveBack.length())) + "..." : "null"));
        check("Pair répond 'have' à notre have",
                haveBack != null && haveBack.startsWith("have"));

        // ─────────────────────────────────────────────────────────────────
        step("UPDATE TRACKER");
        // ─────────────────────────────────────────────────────────────────
        String updateMsg = "update seed [" + firstKey + "] leech []";
        log("→ " + updateMsg);
        trackerWriter.println(updateMsg);
        String okUpdate = trackerReader.readLine();
        log("← " + okUpdate);
        check("Tracker répond 'ok' à update", "ok".equals(okUpdate));

        // ─────────────────────────────────────────────────────────────────
        step("NETTOYAGE");
        // ─────────────────────────────────────────────────────────────────
        peerSocket.close();
        trackerSocket.close();
        log("Connexions fermées.");

        // ─────────────────────────────────────────────────────────────────
        System.out.println();
        System.out.println("══════════════════════════════════════════");
        System.out.println("  RÉSULTATS : " + passed + " PASSED  /  " + failed + " FAILED");
        System.out.println("══════════════════════════════════════════");
        if (failed == 0) System.out.println("  Tous les tests sont OK !");
        else             System.out.println("  Corrigez les FAILED ci-dessus.");
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    static void step(String titre) {
        System.out.println();
        System.out.println("──────────────────────────────────────────");
        System.out.println("  TEST : " + titre);
        System.out.println("──────────────────────────────────────────");
    }

    static void log(String msg) {
        System.out.println("  " + msg);
    }

    static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASSED] " + label);
            passed++;
        } else {
            System.out.println("  [FAILED] " + label);
            failed++;
        }
    }

    static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int offset = 0;
        while (offset < n) {
            int read = in.read(buf, offset, n - offset);
            if (read < 0) throw new EOFException("Flux fermé après " + offset + " octets");
            offset += read;
        }
        return buf;
    }

    static String encodeBuffermap(java.util.BitSet bm, int totalPieces) {
        int nbBytes = (totalPieces + 7) / 8;
        byte[] bytes = new byte[nbBytes];
        for (int i = 0; i < totalPieces; i++) {
            if (bm.get(i)) bytes[i / 8] |= (byte)(1 << (7 - (i % 8)));
        }
        return Base64.getEncoder().encodeToString(bytes);
    }
}