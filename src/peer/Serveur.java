package peer;
import java.io.*;
import java.net.*;
public class Serveur {
	static final int port = 8080;
	public static void main (String [] args) throws Exception {
		ServerSocket s = new ServerSocket (port);
		Socket soc = s.accept ();
	
		BufferedReader plec = new BufferedReader ( new InputStreamReader (soc. getInputStream ()));
		PrintWriter pred = new PrintWriter (new BufferedWriter (new OutputStreamWriter (soc.getOutputStream ())),true);
		while (true) {
			String str = plec.readLine ();
			if (str.equals ("END") ) break;
			System.out.println ("ECHO = " + str);
			pred.println (str);
	}
	plec.close ();
	pred.close ();
	soc.close ();
	}
}
