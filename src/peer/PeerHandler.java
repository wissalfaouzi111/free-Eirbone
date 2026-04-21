import java.io.*;
import java.net.*;
import java.util.*;


public class PeerHandler implements Runnable {

    private Socket socket;
    private PeerState state;
    private BufferedReader reader;
    private PrintWriter writer;
    private OutputStream rawOut; //envoie les bytes exactement tels quels 

    public PeerHandler(Socket socket, PeerState state) {
        this.socket = socket;
        this.state  = state;
    }

    public void run() {
        reader =  new BufferedReader ( new InputStreamReader (this.socket. getInputStream ()));
        writer = new PrintWriter(
                new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream())), true);
        rawOut = socket.getOutputStream(); 
        
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("interested")) {
                handleInterested(line);
            } else if (line.startsWith("getpieces")) {
                handleGetpieces(line);
            } else if (line.startsWith("have")) {
                handleHave(line);
            }
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
        String base64 = encoderBuffermap(dl.localBufferMap, dl.file.totalPieces());
        writer.println("have " + key + " " + base64);
        // System.out.println("[PeerHandler] have envoyé pour " + key);

    }
    private void handleGetpieces(String line){
        String[] tokens = line.split("\\s+");
        if (tokens.length < 3) return;
        String key = tokens[1];

        DownloadState dl = state.downloads.get(key);
        if (dl == null) {
            return;
        }  
        StringBuilder header = new StringBuilder();
        header.append("data ").append(key).append(" [");
        List<Integer> listeIndices = new ArrayList<>();
        for (int i = 0; i < indices.length; i++) {
            int idx = Integer.parseInt(indices[i].trim());
            // vérifier qu'on possede bien cette pièce
            if (!dl.localBufferMap.get(idx)) {
                // System.out.println("[PeerHandler] Pièce " + idx + " non disponible");
                continue;
            }
            listeIndices.add(idx);
            if (i > 0) header.append(" ");
            header.append(idx).append(" 1024");
        }
        header.append("]");
        writer.println(header.toString());
        writer.flush();

        RandomAccessFile raf = new RandomAccessFile(dl.file.filename, "r");
        for (int idx : listeIndices) {
            byte[] piece = new byte[1024];
            raf.seek((long) idx * 1024);
            raf.read(piece); // lit 1024 octets 
            rawOut.write(piece);
        }
        rawOut.flush();
        raf.close();

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

        String notreBase64 = encoderBuffermap(dl.localBufferMap, dl.file.totalPieces());
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