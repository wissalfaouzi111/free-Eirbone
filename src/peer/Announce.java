import java.io.*;
import java.security.MessageDigest;
import java.util.*;


public class Announce 
{


    private static final int PIECE_SIZE = 1024;


    public String look ( String... criteres)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("look [");
        for(String critere : criteres){
             sb.append(critere + " ");
        }
        if( criteres.length > 0){
             sb.deleteCharAt(sb.length() - 1);
        }
        sb.append("]");
        sb.append("\n");
        return sb.toString();
    }

    public String announce (Config config){
        StringBuilder announce = new StringBuilder();

        announce.append("announce listen ");
        announce.append(config.listenPort);
        announce.append( " seed [ ");
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
        announce.append("\n");
        return announce.toString();

    }


    
    public String getfile(String key){
        StringBuilder getfile = new StringBuilder();
        getfile.append("getfile");
        getfile.append(" ");
        getfile.append(key);
        getfile.append("\n");
        return getfile.toString();


    }

    public String interested(String key){
        StringBuilder interested = new StringBuilder();
        interested.append("interested");
        interested.append(" ");
        interested.append(key);
        interested.append("\n");
        return interested.toString();


    }


    public String getpieces(String key, List<Integer> indices){
        StringBuilder sb = new StringBuilder();
        sb.append("getpieces ");
        sb.append(key);
        sb.append(" [");
        for (int i=0; i<indices.size(); i++){
            if(i>0){
                sb.append(" ");
            }
            sb.append(indices.get(i));
            
        }
        sb.append("]\n");
        return sb.toString();

    }

  
    public String computeMD5(File fichier) {

        try {

            MessageDigest md = MessageDigest.getInstance("MD5");
            FileInputStream fis = new FileInputStream(fichier);
            
            byte[] buffer = new byte[1024];
            int nbOctetsLus;

            while ((nbOctetsLus = fis.read(buffer)) != -1) {
                md.update(buffer, 0, nbOctetsLus);
            }
            
            fis.close();
            
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            
            return sb.toString(); 
            
        } catch (Exception e) {
            System.out.println("Erreur calcul MD5 : " + e.getMessage());
            return null;
        }
}

}



