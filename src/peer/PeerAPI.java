import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

// Serveur HTTP interne (port 8887) : Python lui délègue toutes les requêtes du navigateur
public class PeerAPI implements Runnable {

    static final int PORT = 8887;
    private final Config    config;
    private final PeerState state;
    private final ConcurrentHashMap<String, DlState> downloads = new ConcurrentHashMap<>();

    // état d'un téléchargement (volatile = visible par tous les threads)
    static class DlState {
        volatile String filename, key, error;
        volatile int total, done;
        volatile double pct;
        volatile boolean complete;
    }

    public PeerAPI(Config config, PeerState state) { this.config = config; this.state = state; }

    @Override
    public void run() {
        try (ServerSocket srv = new ServerSocket(PORT)) {
            System.out.println("[PeerAPI] port " + PORT);
            while (true) { try { Socket c = srv.accept(); new Thread(() -> handle(c)).start(); } catch (IOException e) { break; } }
        } catch (Exception e) { System.err.println("[PeerAPI] " + e.getMessage()); }
    }

    // lit la requête HTTP et appelle le bon handler
    private void handle(Socket client) {
        try (client) {
            BufferedReader in  = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream   out = client.getOutputStream();
            String[] rp = in.readLine().split(" ");
            String path = rp[1];

            int len = 0;
            String line;
            while (!(line = in.readLine()).isEmpty())
                if (line.toLowerCase().startsWith("content-length:")) len = Integer.parseInt(line.split(":")[1].trim());

            String body = "";
            if (len > 0) { char[] buf = new char[len]; in.read(buf, 0, len); body = new String(buf); }

            switch (path) {
                case "/status":         respond(out, 200, status());        break;
                case "/tracker-status": respond(out, 200, trackerStatus()); break;
                case "/search":         respond(out, 200, search(body));    break;
                case "/download":
                    String[] r = startDownload(body);
                    respond(out, "ok".equals(r[0]) ? 200 : 409,
                            "ok".equals(r[0]) ? "{\"ok\":true,\"error\":null}"
                                              : "{\"ok\":false,\"error\":\"" + esc(r[1]) + "\"}");
                    break;
                default: respond(out, 404, "{}");
            }
        } catch (Exception ignored) {}
    }

    // renvoie l'état des téléchargements + les fichiers locaux partagés
    private String status() {
        StringBuilder sb = new StringBuilder("{\"downloads\":[");
        boolean f = true;
        for (DlState d : downloads.values()) {
            if (!f) sb.append(","); f = false;
            sb.append("{\"filename\":\"").append(esc(d.filename)).append("\",\"key\":\"").append(esc(d.key))
              .append("\",\"total\":").append(d.total).append(",\"done\":").append(d.done)
              .append(",\"pct\":").append(d.pct).append(",\"complete\":").append(d.complete)
              .append(",\"error\":").append(d.error == null ? "null" : "\"" + esc(d.error) + "\"").append("}");
        }
        sb.append("],\"local\":["); f = true;
        for (DownloadState ds : state.downloads.values()) {
            if (!f) sb.append(","); f = false;
            FileInfo fi = ds.file; int tot = fi.totalPieces(), done = ds.getBufferMap().cardinality();
            sb.append("{\"filename\":\"").append(esc(fi.getFilename())).append("\",\"key\":\"").append(esc(fi.getKey()))
              .append("\",\"total\":").append(tot).append(",\"done\":").append(done)
              .append(",\"pct\":").append(String.format("%.1f", tot > 0 ? done * 100.0 / tot : 0))
              .append(",\"complete\":").append(ds.isComplete()).append("}");
        }
        return sb.append("]}").toString();
    }

    // ping le tracker pour voir s'il est joignable
    private String trackerStatus() {
        try {
            Socket s = new Socket(); s.connect(new InetSocketAddress(config.trackerHost, Integer.parseInt(config.trackerPort)), 3000); s.close();
            return "{\"ok\":true,\"addr\":\"" + config.trackerHost + ":" + config.trackerPort + "\",\"error\":null}";
        } catch (Exception e) {
            return "{\"ok\":false,\"addr\":\"" + config.trackerHost + ":" + config.trackerPort + "\",\"error\":\"" + esc(e.getMessage()) + "\"}";
        }
    }

    // envoie look + getfile au tracker, renvoie les peers qui ont le fichier
    private String search(String body) {
        String filename = jsonStr(body, "filename");
        if (filename == null || filename.isEmpty()) return "{\"found\":false}";
        try {
            Socket s = new Socket(); s.connect(new InetSocketAddress(config.trackerHost, Integer.parseInt(config.trackerPort)), 5000); s.setSoTimeout(5000);
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);
            InputStream in = s.getInputStream();

            pw.print("look [ " + filename + " ]\n"); pw.flush();
            List<FileInfo> files = TrackerResponse.parseListResponse(readLine(in));
            if (files.isEmpty()) { s.close(); return "{\"found\":false}"; }
            FileInfo fi = files.get(0);

            pw.print("getfile " + fi.getKey() + "\n"); pw.flush();
            PeersResult pr = TrackerResponse.parsePeersResponse(readLine(in));
            s.close();

            StringBuilder peers = new StringBuilder("[");
            if (pr != null) { boolean f = true; for (PeerInfo p : pr.peers) { if (!f) peers.append(","); f = false; peers.append("{\"ip\":\"").append(p.ip).append("\",\"port\":\"").append(p.port).append("\"}"); } }
            return "{\"found\":true,\"filename\":\"" + esc(fi.getFilename()) + "\",\"size\":" + fi.getLength()
                 + ",\"piece_size\":" + fi.getPieceSize() + ",\"key\":\"" + esc(fi.getKey()) + "\",\"peers\":" + peers + "]}";
        } catch (Exception e) { return "{\"found\":false,\"error\":\"" + esc(e.getMessage()) + "\"}"; }
    }

    // démarre le téléchargement dans un thread séparé
    private String[] startDownload(String body) {
        String ip = jsonStr(body, "ip"), port = jsonStr(body, "port"), key = jsonStr(body, "key"), fn = jsonStr(body, "filename");
        if (ip == null || port == null || key == null) return new String[]{"error", "params manquants"};
        DlState ex = downloads.get(key);
        if (ex != null && !ex.complete && ex.error == null) return new String[]{"error", "deja en cours"};
        DlState dl = new DlState(); dl.filename = (fn != null && !fn.isEmpty()) ? fn : key; dl.key = key;
        downloads.put(key, dl);
        int iport = Integer.parseInt(port);
        new Thread(() -> doDownload(ip, iport, key, dl.filename, dl)).start();
        return new String[]{"ok", null};
    }

    // protocole pair : interested → have (buffermap) → getpieces → data (binaire)
    private void doDownload(String ip, int port, String key, String filename, DlState dl) {
        try {
            Socket s = new Socket(ip, port); s.setSoTimeout(30000);
            InputStream in = s.getInputStream();
            PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(s.getOutputStream())), true);

            pw.print("interested " + key + "\n"); pw.flush();
            String[] hp = readLine(in).split(" ", 3);
            if (hp.length < 3 || !"have".equals(hp[0])) { dl.error = "réponse inattendue"; s.close(); return; }

            // le buffermap est en base64 : bit i = 1 signifie que la pièce i est disponible
            byte[] bm = Base64.getDecoder().decode(hp[2]);
            List<Integer> pieces = new ArrayList<>();
            for (int i = 0; i < bm.length * 8; i++) if ((bm[i/8] & (0x80 >> (i%8))) != 0) pieces.add(i);
            dl.total = pieces.size();

            StringBuilder req = new StringBuilder("getpieces " + key + " [ ");
            for (int idx : pieces) req.append(idx).append(" ");
            pw.print(req.append("]\n")); pw.flush();

            String dataHdr = readLine(in);
            String[] tok = dataHdr.substring(dataHdr.indexOf('[')+1, dataHdr.lastIndexOf(']')).trim().split("\\s+");

            // on lit les pièces binaires une par une
            Map<Integer, byte[]> data = new LinkedHashMap<>();
            for (int i = 0; i+1 < tok.length; i += 2) {
                data.put(Integer.parseInt(tok[i]), readExactly(in, Integer.parseInt(tok[i+1])));
                dl.done = data.size(); dl.pct = Math.round(data.size() * 1000.0 / (tok.length/2)) / 10.0;
            }
            s.close();

            // on assemble les pièces dans l'ordre puis on écrit le fichier
            new File("data/complete").mkdirs();
            try (FileOutputStream fos = new FileOutputStream("data/complete/" + filename))
                { for (int idx : new TreeSet<>(data.keySet())) fos.write(data.get(idx)); }
            dl.complete = true; dl.pct = 100.0;
        } catch (Exception e) { dl.error = e.getMessage(); }
    }




    
    // TOOLS

    // lis une ligne depuis un InputStream brut (sans perdre d'octets binaires après)
    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b; while ((b = in.read()) != -1) { if (b == '\n') break; if (b != '\r') buf.write(b); }
        return buf.toString("UTF-8");
    }

    private static byte[] readExactly(InputStream in, int n) throws IOException {
        byte[] d = new byte[n]; int off = 0;
        while (off < n) { int r = in.read(d, off, n-off); if (r == -1) throw new EOFException(); off += r; }
        return d;
    }

    private static String jsonStr(String json, String key) {
        String tag = "\"" + key + "\":\""; int s = json.indexOf(tag); if (s < 0) return null;
        s += tag.length(); int e = json.indexOf('"', s); return e < 0 ? null : json.substring(s, e);
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }

    private static void respond(OutputStream out, int code, String json) throws IOException {
        byte[] body = json.getBytes("UTF-8");
        out.write(("HTTP/1.1 " + code + " OK\r\nContent-Type: application/json\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n").getBytes());
        out.write(body); out.flush();
    }
}
