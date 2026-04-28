import java.io.*;
import java.net.*;
import java.util.*;

public class newMain {

    public static void main(String[] args) throws Exception {
        new File("data/downloads").mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        Config config = Config.load("config.ini");

        // Charger les fichiers locaux dans PeerState
        PeerState state = new PeerState();
        File dir = new File("data/downloads");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                FileMetadata meta = new FileChunker(f).getMetadata();
                FileInfo fi = new FileInfo(
                    meta.getFilename(), meta.getLength(),
                    meta.getPieceSize(), meta.getKey()
                );
                DownloadState ds = new DownloadState(fi);
                loadPieces(f, ds);
                state.addDownload(ds);
                System.out.println("[Peer] fichier: " + fi.getFilename()
                    + " | pieces=" + fi.totalPieces() + " | key=" + fi.getKey());
            }
        }

        // Démarrer le serveur pair (sert les fichiers aux autres peers)
        Thread peerServer = new Thread(new PeerServer(config, state));
        peerServer.setDaemon(true);
        peerServer.start();
        System.out.println("[Peer] PeerServer sur port " + config.listenPort);

        // Démarrer l'API HTTP pour le dashboard
        Thread apiThread = new Thread(new PeerAPI(config, state));
        apiThread.setDaemon(true);
        apiThread.start();

        

        // Annoncer au tracker
        try {
            Socket ts = new Socket(config.trackerHost, Integer.parseInt(config.trackerPort));
            ts.setSoTimeout(5000);
            PrintWriter tw = new PrintWriter(
                new BufferedWriter(new OutputStreamWriter(ts.getOutputStream())), true);

            StringBuilder announce = new StringBuilder("announce listen ")
                .append(config.listenPort).append(" seed [ ");
            for (DownloadState ds : state.downloads.values()) {
                FileInfo fi = ds.file;
                announce.append(fi.getFilename()).append(" ")
                        .append(fi.getLength()).append(" ")
                        .append(fi.getPieceSize()).append(" ")
                        .append(fi.getKey()).append(" ");
            }
            announce.append("] leech [ ]");

            tw.println(announce.toString());
            tw.flush();
            ts.close();
            System.out.println("[Peer] announce envoyé au tracker");
        } catch (Exception e) {
            System.err.println("[Peer] announce échoué: " + e.getMessage());
        }

        // Attendre indéfiniment
        Thread.currentThread().join();
    }

    private static void loadPieces(File file, DownloadState ds) throws IOException {
        int pieceSize = ds.file.getPieceSize();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[pieceSize];
            int read, index = 0;
            while ((read = fis.read(buffer)) != -1) {
                ds.writePiece(index++, Arrays.copyOf(buffer, read));
            }
        }
    }
}
