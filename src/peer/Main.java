import java.io.*;
import java.net.*;
import java.util.*;

// Lancé par le dashboard Python (make peer).
// Démarre PeerServer + PeerAPI et tourne indéfiniment.
public class Main {

    public static void main(String[] args) throws Exception {
        new File("data/downloads").mkdirs();
        new File("data/incomplete").mkdirs();
        new File("data/complete").mkdirs();

        Config config = Config.load("config.ini");

        // charger les fichiers locaux dans PeerState
        PeerState state = new PeerState();
        File[] files = new File("data/downloads").listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile()) continue;
                FileMetadata meta = new FileChunker(f).getMetadata();
                FileInfo fi = new FileInfo(meta.getFilename(), meta.getLength(), meta.getPieceSize(), meta.getKey());
                DownloadState ds = new DownloadState(fi);
                chargerPieces(f, ds);
                state.addDownload(ds);
                System.out.println("[Main] fichier: " + fi.getFilename() + " | key=" + fi.getKey());
            }
        }

        // démarrer le serveur pair (sert les fichiers aux autres peers)
        new Thread(new PeerServer(config, state)).start();
        System.out.println("[Main] PeerServer sur port " + config.listenPort);

        // démarrer l'API HTTP pour le dashboard Python
        new Thread(new PeerAPI(config, state)).start();

        // annoncer au tracker
        try {
            Socket ts = new Socket(config.trackerHost, Integer.parseInt(config.trackerPort));
            PrintWriter tw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(ts.getOutputStream())), true);
            StringBuilder ann = new StringBuilder("announce listen ").append(config.listenPort).append(" seed [ ");
            for (DownloadState ds : state.downloads.values()) {
                FileInfo fi = ds.file;
                ann.append(fi.getFilename()).append(" ").append(fi.getLength()).append(" ")
                   .append(fi.getPieceSize()).append(" ").append(fi.getKey()).append(" ");
            }
            tw.println(ann.append("] leech [ ]"));
            ts.close();
            System.out.println("[Main] announce envoyé");
        } catch (ConnectException e) {
            System.err.println("[Main] tracker injoignable, announce ignoré");
        }

        Thread.currentThread().join(); // attendre indéfiniment
    }

    private static void chargerPieces(File file, DownloadState ds) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[ds.file.getPieceSize()];
            int lu, idx = 0;
            while ((lu = fis.read(buf)) != -1) ds.writePiece(idx++, Arrays.copyOf(buf, lu));
        }
    }
}
