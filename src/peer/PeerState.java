package peer;
import java.util.*;

public class PeerState {
    Map<String, DownloadState> downloads; //lier les keys des fichiers au downloads state
    Map<String, List<PeerInfo>> filePeers; //lier les keys aux peers qui ont le fichier 

    public PeerState() {
        downloads = new HashMap<>();
        filePeers = new HashMap<>();
    }

    public void addDownload(DownloadState ds) {
        downloads.put(ds.file.key, ds);
    }

    public DownloadState getDownload(String key) {
        return downloads.get(key);
    }

    public void setPeers(String key, List<PeerInfo> peers) {
        filePeers.put(key, peers);
    }

    public List<PeerInfo> getPeers(String key) {
        return filePeers.get(key);
    }
}