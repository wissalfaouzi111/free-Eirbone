

public class PeerInfo {
    String ip;
    int port;
    PeerInfo(String ip , int port){
        this.ip = ip;
        this.port = port;

    }
    public String getIp() {
        return ip;
    }

    public int getPort() {
        return port;
    }
}
