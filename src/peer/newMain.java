import java.io.*;
import java.net.*;
import java.util.*;

public class newMain {

    private static final String DOWNLOADS_DIR = "data/downloads";
    private static final int MAX_BATCH = 16;

    public static void main(String[] args) throws Exception {
        new File("data/downloads").mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        Announce proto = new Announce();

        Config config = Config.load("config.ini");

        PeerState state = new PeerState();

        File dir = new File(DOWNLOADS_DIR);
        File[] files = dir.listFiles();
        config.files = files;

        loadLocalFiles(state, files);

        Thread serverThread = new Thread(new PeerServer(config, state));
        serverThread.setDaemon(true);
        serverThread.start();

        Thread.sleep(500);

        Socket trackerSocket;
        try {
            trackerSocket = new Socket(config.trackerHost, Integer.parseInt(config.trackerPort));
        } catch (ConnectException e) {
            System.err.println("[ERREUR] Impossible de joindre le tracker "
                + config.trackerHost + ":" + config.trackerPort);
            System.err.println("         Lance d'abord : make tracker");
            return;
        }

        InputStream trackerIn = trackerSocket.getInputStream();

        PrintWriter trackerWriter = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(trackerSocket.getOutputStream())),
                true
        );

        System.out.println("[OK] Connecté au tracker");

        String announceMsg = proto.announce(config);
        trackerWriter.print(announceMsg);
        trackerWriter.flush();

        String ok = readAsciiLine(trackerIn);
        System.out.println("[TRACKER] " + ok);

        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.print("Nom du fichier à chercher/télécharger (ou quit) > ");
            String filename = scanner.nextLine().trim();

            if (filename.equalsIgnoreCase("quit") || filename.equalsIgnoreCase("exit")) {
                break;
            }

            if (filename.isEmpty()) {
                continue;
            }

            downloadByFilename(filename, proto, state, trackerWriter, trackerIn);
        }

        trackerSocket.close();
        scanner.close();

        System.out.println("Fin du programme.");
    }

    private static void downloadByFilename(
            String filename,
            Announce proto,
            PeerState state,
            PrintWriter trackerWriter,
            InputStream trackerIn
    ) throws Exception {

        System.out.println("\n===== LOOK =====");

        String lookMsg = proto.look(filename);
        System.out.println("→ " + lookMsg.trim());

        trackerWriter.print(lookMsg);
        trackerWriter.flush();

        String listResponse = readAsciiLine(trackerIn);
        System.out.println("← " + listResponse);

        List<FileInfo> files = TrackerResponse.parseListResponse(listResponse);

        if (files.isEmpty()) {
            System.out.println("[ERREUR] Aucun fichier trouvé avec ce nom.");
            return;
        }

        FileInfo file = files.get(0);

        System.out.println("[OK] Fichier trouvé : " + file.getFilename());
        System.out.println("     taille = " + file.getLength());
        System.out.println("     key    = " + file.getKey());

        DownloadState ds = state.getDownload(file.getKey());

        if (ds == null) {
            ds = new DownloadState(file);
            state.addDownload(ds);
        }

        if (ds.isComplete()) {
            System.out.println("[INFO] Tu as déjà ce fichier complet.");
            return;
        }

        System.out.println("\n===== GETFILE =====");

        String getfileMsg = proto.getfile(file.getKey());
        System.out.println("→ " + getfileMsg.trim());

        trackerWriter.print(getfileMsg);
        trackerWriter.flush();

        String peersResponse = readAsciiLine(trackerIn);
        System.out.println("← " + peersResponse);

        PeersResult peersResult = TrackerResponse.parsePeersResponse(peersResponse);

        if (peersResult == null || peersResult.peers.isEmpty()) {
            System.out.println("[ERREUR] Aucun peer ne possède ce fichier.");
            return;
        }

        state.setPeers(file.getKey(), peersResult.peers);

        System.out.println("[OK] Nombre de peers : " + peersResult.peers.size());

        for (PeerInfo peer : peersResult.peers) {
            if (ds.isComplete()) break;

            try {
                downloadFromPeer(peer, file, ds, proto);
            } catch (Exception e) {
                System.out.println("[WARN] Peer indisponible "
                        + peer.getIp() + ":" + peer.getPort()
                        + " : " + e.getMessage());
            }
        }

        if (ds.isComplete()) {
            ds.getStorage().finalizeDownload(file.getFilename());
            System.out.println("[OK] Téléchargement terminé : data/complete/" + file.getFilename());
        } else {
            int done = ds.getBufferMap().cardinality();
            int total = file.totalPieces();
            System.out.println("[INFO] Téléchargement partiel : " + done + "/" + total + " pièces.");
        }
    }

    private static void downloadFromPeer(
            PeerInfo peer,
            FileInfo file,
            DownloadState ds,
            Announce proto
    ) throws Exception {

        System.out.println("\n===== CONNEXION PEER " + peer.getIp() + ":" + peer.getPort() + " =====");

        Socket socket = new Socket(peer.getIp(), peer.getPort());

        InputStream in = socket.getInputStream();

        PrintWriter writer = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())),
                true
        );

        String interestedMsg = proto.interested(file.getKey());
        System.out.println("→ " + interestedMsg.trim());

        writer.print(interestedMsg);
        writer.flush();

        String haveResponse = readAsciiLine(in);
        System.out.println("← " + haveResponse);

        if (haveResponse == null || !haveResponse.startsWith("have")) {
            socket.close();
            return;
        }

        String[] parts = haveResponse.split("\\s+", 3);
        if (parts.length < 3) {
            socket.close();
            return;
        }

        BitSet remoteMap = decodeBuffermap(parts[2], file.totalPieces());

        while (!ds.isComplete()) {
            List<Integer> wanted = choosePiecesToDownload(remoteMap, ds, file.totalPieces());

            if (wanted.isEmpty()) {
                break;
            }

            String getpiecesMsg = proto.getpieces(file.getKey(), wanted);
            System.out.println("→ " + getpiecesMsg.trim());

            writer.print(getpiecesMsg);
            writer.flush();

            String dataHeader = readAsciiLine(in);
            System.out.println("← " + dataHeader);

            if (dataHeader == null || !dataHeader.startsWith("data")) {
                break;
            }

            List<int[]> piecesInfo = parseDataHeader(dataHeader);

            for (int[] info : piecesInfo) {
                int index = info[0];
                int length = info[1];

                byte[] data = readExactly(in, length);
                ds.writePiece(index, data);

                System.out.println("[OK] Pièce reçue : " + index
                        + " (" + ds.getBufferMap().cardinality()
                        + "/" + file.totalPieces() + ")");
            }
        }

        socket.close();
    }

    private static List<Integer> choosePiecesToDownload(BitSet remoteMap, DownloadState ds, int totalPieces) {
        List<Integer> wanted = new ArrayList<>();

        for (int i = 0; i < totalPieces; i++) {
            if (remoteMap.get(i) && !ds.hasPiece(i)) {
                wanted.add(i);
            }

            if (wanted.size() >= MAX_BATCH) {
                break;
            }
        }

        return wanted;
    }

    private static void loadLocalFiles(PeerState state, File[] files) throws IOException {
        if (files == null) return;

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

            System.out.println("[LOCAL] " + fi.getFilename()
                    + " | pieces=" + fi.totalPieces()
                    + " | key=" + fi.getKey());
        }
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

    private static BitSet decodeBuffermap(String base64, int totalPieces) {
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

    private static List<int[]> parseDataHeader(String header) {
        List<int[]> result = new ArrayList<>();

        int open = header.indexOf('[');
        int close = header.indexOf(']');

        if (open < 0 || close < 0 || close <= open) return result;

        String inside = header.substring(open + 1, close).trim();

        if (inside.isEmpty()) return result;

        String[] tokens = inside.split("\\s+");

        for (int i = 0; i + 1 < tokens.length; i += 2) {
            int index = Integer.parseInt(tokens[i]);
            int length = Integer.parseInt(tokens[i + 1]);
            result.add(new int[]{index, length});
        }

        return result;
    }
}