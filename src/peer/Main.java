package peer;

import java.io.*;
import java.net.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {

        Config config = Config.load("config.ini");
        PeerState state = new PeerState();

        // ── 1. Démarrer le PeerServer en arrière-plan ─────────────────────
        Thread serverThread = new Thread(new PeerServer(config, state));
        serverThread.setDaemon(true);
        serverThread.start();
        System.out.println("[Main] PeerServer démarré sur le port " + config.listenPort);

        // ── 2. Se connecter au Tracker ────────────────────────────────────
        Client client = new Client(config);
        client.connect();

        // ── 3. Annoncer sa présence (sans fichier pour l'instant) ─────────
        Announce announce = new Announce();
        String announceMsg = announce.announce(config);
        System.out.println("[Main] Envoi announce : " + announceMsg.trim());
        client.send(announceMsg);
        String ok = client.receive();
        System.out.println("[Main] Tracker répond : " + ok);

        // ── 4. Chercher un fichier ─────────────────────────────────────────
        Scanner scanner = new Scanner(System.in);
        System.out.print("[Main] Nom du fichier à chercher : ");
        String filename = scanner.nextLine().trim();

        client.send("look [filename=" + filename + "]\n");
        String listResponse = client.receive();
        System.out.println("[Main] Tracker répond : " + listResponse);

        List<FileInfo> fichiers = TrackerResponse.parseListResponse(listResponse);
        if (fichiers.isEmpty()) {
            System.out.println("[Main] Fichier introuvable sur le réseau.");
            return;
        }

        FileInfo fi = fichiers.get(0);
        System.out.println("[Main] Fichier trouvé : " + fi);

        // ── 5. Demander la liste des pairs au Tracker ──────────────────────
        client.send("getfile " + fi.getKey() + "\n");
        String peersResponse = client.receive();
        System.out.println("[Main] Tracker répond : " + peersResponse);

        PeersResult result = TrackerResponse.parsePeersResponse(peersResponse);
        if (result == null || result.peers.isEmpty()) {
            System.out.println("[Main] Aucun pair disponible.");
            return;
        }

        // Stocker les pairs dans PeerState
        state.setPeers(fi.getKey(), result.peers);

        // Créer le DownloadState pour ce fichier
        DownloadState dl = new DownloadState(fi);
        state.addDownload(dl);

        // ── 6. Se connecter à chaque pair et envoyer interested ───────────
        int maxPeers = Integer.parseInt(config.maxPeers);
        List<PeerInfo> selected = result.peers.subList(
                0, Math.min(maxPeers, result.peers.size()));

        for (PeerInfo peer : selected) {
            try {
                System.out.println("[Main] Connexion au pair " 
                        + peer.ip + ":" + peer.port);

                Socket peerSocket = new Socket(peer.ip, peer.port);
                BufferedReader peerReader = new BufferedReader(
                        new InputStreamReader(peerSocket.getInputStream()));
                PrintWriter peerWriter = new PrintWriter(
                        new BufferedWriter(
                                new OutputStreamWriter(peerSocket.getOutputStream())), true);

                // Envoyer interested
                peerWriter.println("interested " + fi.getKey());
                System.out.println("[Main] → interested " + fi.getKey());

                // Lire have
                String haveResponse = peerReader.readLine();
                System.out.println("[Main] ← " + haveResponse);

                // Parser le buffermap et le stocker dans PeerInfo
                if (haveResponse != null && haveResponse.startsWith("have")) {
                    String[] tokens = haveResponse.split("\\s+", 3);
                    if (tokens.length == 3) {
                        peer.bufferMap = decodeBuffermap(tokens[2], fi.totalPieces());
                        System.out.println("[Main] Buffermap stocké pour " 
                                + peer.ip + " — pièces disponibles : "
                                + peer.bufferMap.cardinality() + "/" + fi.totalPieces());
                    }
                }

                // Lancer un thread de téléchargement sur ce pair
                Thread dlThread = new Thread(
                        new PeerHandler(peerSocket, state));
                dlThread.setDaemon(true);
                dlThread.start();

            } catch (IOException e) {
                System.out.println("[Main] Impossible de joindre " 
                        + peer.ip + ":" + peer.port + " — " + e.getMessage());
            }
        }

        // ── 7. Attendre (le téléchargement tourne en arrière-plan) ────────
        System.out.println("[Main] Téléchargement en cours... (Ctrl+C pour quitter)");
        Thread.currentThread().join();
    }

    private static java.util.BitSet decodeBuffermap(String base64, int totalPieces) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        java.util.BitSet bs = new java.util.BitSet(totalPieces);
        for (int i = 0; i < totalPieces; i++) {
            if (i / 8 < bytes.length && (bytes[i / 8] & (1 << (7 - (i % 8)))) != 0) {
                bs.set(i);
             }
        }
        return bs;
    }
}