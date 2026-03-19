import java.io.*;
import java.net.*;

public class Announce 
{
    public String announce (Config config){
        StringBuilder announce = new StringBuilder();

        announce.append("announce listen");
        announce.append(config.listenPort);
        announce.append("seed [");
        boolean b=true;

        for(File f : config.files){
            if (f.isFile()){
                if (!b) {
                    announce.append(" ");
                }
                String name = f.getName();
                long length = f.length();
                String md5 = computeMD5(f);
                announce.append(name);
                announce.append(" ");
                announce.append(length);
                announce.append(" ");
                announce.append(PIECE_SIZE);
                announce.append(" ");
                announce.append(md5);

                b = false;

            }
        }
        
        announce.append("] leech []");
        return announce.toString();


    }

    
    private String computeMD5(File file) {
    
}


}



