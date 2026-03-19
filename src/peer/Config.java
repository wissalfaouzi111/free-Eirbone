import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;


class Config {


    String trackerHost;
    String trackerPort;
    String listenPort ;
    String mode;
    File[] files;



    public Config load(String fileName){
        Config config = new Config();

        BufferedReader buffer = new BufferedReader(new FileReader(fileName));
        String line = buffer.readline() ;
        line = line.trim();
        

        while(line != null ){
            Strings[]  elements = line.split("=",2);
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
            if(key.equals("mode")){
                config.mode = value;
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