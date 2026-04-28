import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// Petit serveur HTTP sur le port 8887.
// Python lui transmet les requêtes du navigateur et renvoie ses réponses JSON.
public class PeerAPI implements Runnable {

    static final int PORT = 8887;

    Config    config;
    PeerState state;
    Map<String, DlState> downloads = new ConcurrentHashMap<>();

    // suivi d'un téléchargement en cours
    static class DlState {
        volatile String  filename, key, error;
        volatile int     total, done;
        volatile double  pct;
        volatile boolean complete;
    }

    public PeerAPI(Config config, PeerState state) {
        this.config = config;
        this.state  = state;
    }

    // démarre le serveur : on attend des connexions, un thread par requête
    public void run() {
        try {
            ServerSocket serveur = new ServerSocket(PORT);
            System.out.println("[PeerAPI] port " + PORT);
            while (true) {
                Socket client = serveur.accept();
                new Thread(() -> traiter(client)).start();
            }
        } catch (Exception e) { System.err.println("[PeerAPI] " + e.getMessage()); }
    }

    // lit la requête HTTP, appelle la bonne méthode, renvoie du JSON
    void traiter(Socket client) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream  out = client.getOutputStream();

            // première ligne HTTP : dans "POST /search HTTP/1.1" recupération de "/search"
            String route = in.readLine().split(" ")[1];

            // on cherche Content-Length dans les headers
            int taille = 0;
            String ligne;
            while (!(ligne = in.readLine()).isEmpty())
                if (ligne.toLowerCase().startsWith("content-length:"))
                    taille = Integer.parseInt(ligne.split(":")[1].trim());

            // on lit le body JSON (ce que Python a envoyé)
            String body = "";
            if (taille > 0) { char[] buf = new char[taille]; in.read(buf); body = new String(buf); }

            String json;
            if      (route.equals("/status"))          json = status();
            else if (route.equals("/tracker-status"))  json = trackerStatus();
            else if (route.equals("/search"))          json = search(body);
            else if (route.equals("/download"))        json = lancerTelechargement(body);
            else                                       json = "{}";

            repondre(out, json);
            client.close();
        } catch (Exception e) {}
    }

    // LES ROUTES

    // renvoie les fichiers qu'on partage + les téléchargements en cours
    String status() {
        StringBuilder sb = new StringBuilder("{\"downloads\":[");
        boolean premier = true;
        for (DlState d : downloads.values()) {
            if (!premier) sb.append(","); premier = false;
            sb.append("{\"filename\":\"").append(d.filename).append("\",\"key\":\"").append(d.key)
              .append("\",\"total\":").append(d.total).append(",\"done\":").append(d.done)
              .append(",\"pct\":").append(d.pct).append(",\"complete\":").append(d.complete)
              .append(",\"error\":").append(d.error == null ? "null" : "\"" + d.error + "\"").append("}");
        }
        sb.append("],\"local\":["); premier = true;
        for (DownloadState ds : state.downloads.values()) {
            if (!premier) sb.append(","); premier = false;
            FileInfo fi = ds.file;
            int tot = fi.totalPieces(), done = ds.getBufferMap().cardinality();
            sb.append("{\"filename\":\"").append(fi.getFilename()).append("\",\"key\":\"").append(fi.getKey())
              .append("\",\"total\":").append(tot).append(",\"done\":").append(done)
              .append(",\"pct\":").append(String.format("%.1f", tot > 0 ? done * 100.0 / tot : 0.0))
              .append(",\"complete\":").append(ds.isComplete()).append("}");
        }
        return sb.append("]}").toString();
    }

    // essaie de se connecter au tracker pour vérifier qu'il est en ligne
    String trackerStatus() {
        try {
            new Socket(config.trackerHost, Integer.parseInt(config.trackerPort)).close();
            return "{\"ok\":true,\"addr\":\"" + config.trackerHost + ":" + config.trackerPort + "\",\"error\":null}";
        } catch (Exception e) {
            return "{\"ok\":false,\"addr\":\"" + config.trackerHost + ":" + config.trackerPort + "\",\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    // envoie "look" puis "getfile" au tracker et renvoie les peers trouvés en JSON
    String search(String body) {
        String nomFichier = extraire(body, "filename");
        if (nomFichier == null) return "{\"found\":false}";
        try {
            Socket s = new Socket(config.trackerHost, Integer.parseInt(config.trackerPort));
            s.setSoTimeout(5000);
            PrintWriter pw = new PrintWriter(s.getOutputStream(), true);
            InputStream  in = s.getInputStream();

            pw.println("look [ " + nomFichier + " ]");                          // cherche le fichier
            List<FileInfo> fichiers = TrackerResponse.parseListResponse(lireLigne(in));
            if (fichiers.isEmpty()) { s.close(); return "{\"found\":false}"; }
            FileInfo fi = fichiers.get(0);

            pw.println("getfile " + fi.getKey());                                // demande les peers
            PeersResult res = TrackerResponse.parsePeersResponse(lireLigne(in));
            s.close();

            StringBuilder peers = new StringBuilder("[");
            if (res != null) {
                boolean premier = true;
                for (PeerInfo p : res.peers) {
                    if (!premier) peers.append(","); premier = false;
                    peers.append("{\"ip\":\"").append(p.ip).append("\",\"port\":\"").append(p.port).append("\"}");
                }
            }
            return "{\"found\":true,\"filename\":\"" + fi.getFilename() + "\",\"size\":" + fi.getLength()
                 + ",\"piece_size\":" + fi.getPieceSize() + ",\"key\":\"" + fi.getKey() + "\",\"peers\":" + peers + "]}";
        } catch (Exception e) { return "{\"found\":false,\"error\":\"" + e.getMessage() + "\"}"; }
    }

    // démarre le téléchargement dans un thread séparé et répond tout de suite
    String lancerTelechargement(String body) {
        String ip   = extraire(body, "ip"),   port = extraire(body, "port");
        String key  = extraire(body, "key"),  fn   = extraire(body, "filename");
        if (ip == null || port == null || key == null)
            return "{\"ok\":false,\"error\":\"params manquants\"}";
        DlState existant = downloads.get(key);
        if (existant != null && !existant.complete && existant.error == null)
            return "{\"ok\":false,\"error\":\"deja en cours\"}";
        DlState dl = new DlState();
        dl.filename = (fn != null && !fn.isEmpty()) ? fn : key;
        dl.key = key;
        downloads.put(key, dl);
        int portInt = Integer.parseInt(port);
        new Thread(() -> telecharger(ip, portInt, key, dl.filename, dl)).start();
        return "{\"ok\":true,\"error\":null}";
    }

    // téléchargement (protocole pair) 

    void telecharger(String ip, int port, String key, String filename, DlState dl) {
        try {
            Socket s = new Socket(ip, port);
            s.setSoTimeout(30000);
            InputStream  in = s.getInputStream();
            PrintWriter  pw = new PrintWriter(s.getOutputStream(), true);

            // étape 1 : on demande quelles pièces le pair possède
            pw.println("interested " + key);
            String[] reponse = lireLigne(in).split(" ", 3); // "have <key> <buffermap>"
            if (reponse.length < 3 || !reponse[0].equals("have")) {
                dl.error = "réponse inattendue"; s.close(); return;
            }

            // le buffermap est en base64 : bit i = 1 → pièce i disponible
            byte[] bm = Base64.getDecoder().decode(reponse[2]);
            List<Integer> pieces = new ArrayList<>();
            for (int i = 0; i < bm.length * 8; i++)
                if ((bm[i/8] & (0x80 >> (i%8))) != 0) pieces.add(i);
            dl.total = pieces.size();

            // étape 2 : on demande toutes les pièces
            StringBuilder req = new StringBuilder("getpieces " + key + " [ ");
            for (int i : pieces) req.append(i).append(" ");
            pw.println(req + "]");

            // étape 3 : on reçoit l'en-tête : "data <key> [ <idx> <taille> <idx> <taille> ... ]"
            String entete = lireLigne(in);
            String[] tokens = entete.substring(entete.indexOf('[')+1, entete.lastIndexOf(']')).trim().split("\\s+");

            // étape 4 : on lit les octets binaires de chaque pièce
            Map<Integer, byte[]> data = new LinkedHashMap<>();
            for (int i = 0; i+1 < tokens.length; i += 2) {
                int idx    = Integer.parseInt(tokens[i]);
                int nbOcts = Integer.parseInt(tokens[i+1]);
                data.put(idx, lireExactement(in, nbOcts));
                dl.done = data.size();
                dl.pct  = Math.round(data.size() * 1000.0 / (tokens.length/2)) / 10.0;
            }
            s.close();

            // étape 5 : on assemble les pièces dans l'ordre et on sauvegarde
            new File("data/complete").mkdirs();
            FileOutputStream fos = new FileOutputStream("data/complete/" + filename);
            for (int idx : new TreeSet<>(data.keySet())) fos.write(data.get(idx));
            fos.close();
            dl.complete = true;
            dl.pct = 100.0;

        } catch (Exception e) { dl.error = e.getMessage(); }
    }

    // TOOLS

    // lit une ligne depuis un InputStream brut
    String lireLigne(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        return buf.toString("UTF-8");
    }

    // lit exactement n octets (bloque jusqu'à tout avoir reçu)
    byte[] lireExactement(InputStream in, int n) throws IOException {
        byte[] data = new byte[n]; int lu = 0;
        while (lu < n) { int r = in.read(data, lu, n-lu); if (r == -1) throw new EOFException(); lu += r; }
        return data;
    }

    // extrait la valeur d'une clé dans un JSON simple : {"cle":"valeur"}
    String extraire(String json, String cle) {
        String tag = "\"" + cle + "\":\"";
        int debut = json.indexOf(tag); if (debut < 0) return null;
        debut += tag.length();
        int fin = json.indexOf('"', debut);
        return fin < 0 ? null : json.substring(debut, fin);
    }

    // envoie la réponse HTTP avec un body JSON
    void repondre(OutputStream out, String json) throws IOException {
        byte[] corps = json.getBytes("UTF-8");
        out.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                   + corps.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(corps);
        out.flush();
    }
}
