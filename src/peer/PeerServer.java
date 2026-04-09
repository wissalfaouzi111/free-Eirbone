
public Class PeerServer{
    private PeerState state;
    private Config config;


    public PeerServer(Config config, PeerState state) {
        this.config = config;
        this.state  = state;
    }
    public void run() {
        ServerSocket serverSocket = new ServerSocket(
            Integer.parseInt(config.listenPort));
        
        while (true) {
            Socket client = serverSocket.accept();
            #on cree un handler pour le nouveau pair
            new Thread(new PeerHandler(client, state)).start();
        }
    }
}