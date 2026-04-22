package peer;

import java.io.*;

public class StatusExporter {
    private static final String STATUS_FILE = "data/status.json";

    public static void export(PeerState state) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"downloads\":[");
        boolean first = true;
        for (DownloadState ds : state.downloads.values()) {
            if (!first) sb.append(",");
            first = false;
            FileInfo fi = ds.file;
            int total = fi.totalPieces();
            int done = ds.getBufferMap().cardinality();
            double pct = total > 0 ? (done * 100.0 / total) : 0.0;
            sb.append("{");
            sb.append("\"filename\":\"").append(escape(fi.getFilename())).append("\",");
            sb.append("\"key\":\"").append(escape(fi.getKey())).append("\",");
            sb.append("\"total\":").append(total).append(",");
            sb.append("\"done\":").append(done).append(",");
            sb.append("\"pct\":").append(String.format("%.1f", pct)).append(",");
            sb.append("\"complete\":").append(ds.isComplete());
            sb.append("}");
        }
        sb.append("]}");

        try (PrintWriter pw = new PrintWriter(new FileWriter(STATUS_FILE))) {
            pw.print(sb.toString());
        } catch (IOException e) {
            System.out.println("[StatusExporter] Erreur écriture : " + e.getMessage());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
