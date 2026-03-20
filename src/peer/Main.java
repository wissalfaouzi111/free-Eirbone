import java.io.*;
import java.net.*;
import java.security.MessageDigest;



public class Main
{
    public static void main(String[] args) throws Exception{
        Config config = Config.load("config.ini");
        Client client = new Client(config);
        client.connect();
        Announce announce = new Announce();
        String s = announce.announce(config);
        client.send(s);

    }

}