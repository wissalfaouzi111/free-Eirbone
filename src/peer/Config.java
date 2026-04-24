package peer;
import java.io.*;

class Config {


    String trackerHost;
    String trackerPort;
    String listenPort ;
    File[] files;



    public static Config load(String fileName) throws IOException{
        Config config = new Config();

        BufferedReader buffer = new BufferedReader(new FileReader(fileName));
        String line ;
        

        while((line = buffer.readLine()) != null ){
            line = line.trim();
            String[]  elements = line.split("=",2);
            String key = elements[0].trim();
            String value = elements[1].trim();
            if(key.equals("trackerHost")){
                config.trackerHost = value;
            }
            if(key.equals("trackerPort")){
                config.trackerPort = value;
            }
            if(key.equals("listenPort")){
                config.listenPort = value;
            }
            if(key.equals("dossier")){
                File dossier = new File(value);
                config.files = dossier.listFiles();
            }
          
            
            
        }
        buffer.close();
        return config;
    }

}