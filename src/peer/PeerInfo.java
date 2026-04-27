import java.util.BitSet;

public class PeerInfo {
    String ip;
    int port;
    BitSet bufferMap;
    
    PeerInfo(String ip , int port){
        this.ip = ip;
        this.port = port;
        this.bufferMap = new BitSet();

    }

    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
