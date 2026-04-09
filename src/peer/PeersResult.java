import java.util.List;

public class PeersResult {
    String key;
    List<PeerInfo> peers;

    public PeersResult(String key, List<PeerInfo> peers) {
        this.key = key;
        this.peers = peers;
    }
}
