

public class PeerInfo {
    String ip;
    String port;
    PeerInfo(String ip , String port){
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
