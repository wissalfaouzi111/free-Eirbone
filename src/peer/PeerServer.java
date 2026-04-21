package peer;
import java.io.*;
import java.net.*;

public class PeerServer{
    private PeerState state;
    private Config config;


    public PeerServer(Config config, PeerState state) {
        this.config = config;
        this.state  = state;
    }
    public void run() throws IOException {
        ServerSocket serverSocket = new ServerSocket(
            Integer.parseInt(config.listenPort));
        
        while (true) {
            Socket client = serverSocket.accept();
            //on cree un handler pour le nouveau pair
            new Thread(new PeerHandler(client, state)).start();
        }
    }
}