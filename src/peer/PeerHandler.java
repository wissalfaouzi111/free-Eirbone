package peer;
import java.io.*;
import java.net.*;
import java.util.*;


public class PeerHandler implements Runnable {

    private Socket socket;
    private PeerState state;
    private BufferedReader reader;
    private PrintWriter writer;
    private OutputStream rawOut; //envoie les bytes exactement tels quels 

    public PeerHandler(Socket socket, PeerState state) throws IOException {
        this.socket = socket;
        this.state  = state;
        reader =  new BufferedReader ( new InputStreamReader (this.socket. getInputStream ()));
        writer = new PrintWriter(
                new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
        rawOut = socket.getOutputStream(); 
    }

    public void run() {
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("interested")) {
                    handleInterested(line);
                } else if (line.startsWith("getpieces")) {
                    handleGetpieces(line);
                } else if (line.startsWith("have")) {
                    handleHave(line);
                }
            }
        } catch (IOException e) {
            System.out.println("[PeerHandler] Error: " + e.getMessage());
        }
    }

    private void handleInterested(String line) {
        String[] tokens = line.split("\\s+");
        if(tokens.length<2) return;
        String key = tokens[1];

        DownloadState dl = state.downloads.get(key);
        if (dl == null) {
            // System.out.println("[PeerHandler] Fichier inconnu : " + key);
            return;
        }  
        String base64 = encoderBuffermap(dl.getBufferMap(), dl.file.totalPieces());
        writer.println("have " + key + " " + base64);
        // System.out.println("[PeerHandler] have envoyé pour " + key);

    }
    private void handleGetpieces(String line){
        String[] tokens = line.split("\\s+", 3);
        if (tokens.length < 3) return;
        String key = tokens[1];

        DownloadState dl = state.downloads.get(key);
        if (dl == null) {
            return;
        }

        String indicesSection = tokens[2].trim();
        int openBracket = indicesSection.indexOf('[');
        int closeBracket = indicesSection.lastIndexOf(']');
        if (openBracket < 0 || closeBracket < 0 || closeBracket <= openBracket) {
            return;
        }

        String[] indices = indicesSection.substring(openBracket + 1, closeBracket).trim().split("\\s+");
        StringBuilder header = new StringBuilder();
        header.append("data ").append(key).append(" [");
        List<Integer> listeIndices = new ArrayList<>();
        boolean first = true;
        for (String token : indices) {
            if (token.isEmpty()) {
                continue;
            }
            int idx = Integer.parseInt(token.trim());
            if (!dl.hasPiece(idx)) {
                continue;
            }
            if (!first) {
                header.append(" ");
            }
            int size = (idx == dl.file.totalPieces() - 1) ? (int)(dl.file.getLength() % 1024) : 1024;
            if (size == 0) size = 1024;
            header.append(idx).append(" ").append(size);
            listeIndices.add(idx);
            first = false;
        }
        header.append("]");
        writer.println(header.toString());
        writer.flush();

        try {
            for (int idx : listeIndices) {
                byte[] piece = dl.readPiece(idx);
                rawOut.write(piece);
            }
            rawOut.flush();
        } catch (IOException e) {
            System.out.println("[PeerHandler] Erreur en lisant une pièce : " + e.getMessage());
        }
    }
    private void handleHave(String line)       { 
        String[] tokens = line.split("\\s+", 3);
        if (tokens.length < 3) return;
        String key    = tokens[1];
        String base64 = tokens[2];

        DownloadState dl = state.downloads.get(key);
        if (dl == null) return;
    
        String ipPair = socket.getInetAddress().getHostAddress();
        List<PeerInfo> pairs = state.filePeers.get(key);

        if (pairs != null) {
            for (PeerInfo p : pairs) {
                if (p.ip.equals(ipPair)) {
                    p.bufferMap = decoderBuffermap(base64, dl.file.totalPieces());
                    System.out.println("[PeerHandler] Buffermap mis à jour pour " 
                        + ipPair);
                    break;
                }
            }
        }

        String notreBase64 = encoderBuffermap(dl.getBufferMap(), dl.file.totalPieces());
        writer.println("have " + key + " " + notreBase64);
    }
    
    private String encoderBuffermap(java.util.BitSet bm, int totalPieces) {
        // on crée un tableau de bytes de la bonne taille
        int nbBytes = (totalPieces + 7) / 8;
        byte[] bytes = new byte[nbBytes];

        for (int i = 0; i < totalPieces; i++) {
            if (bm.get(i)) {
                bytes[i / 8] |= (1 << (7 - (i % 8)));
            }
        }
        return Base64.getEncoder().encodeToString(bytes);
    }

    // Base64 → BitSet
    private java.util.BitSet decoderBuffermap(String base64, int totalPieces) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        java.util.BitSet bs = new java.util.BitSet(totalPieces);
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