package peer;
import java.io.*;
import java.net.*;

public class Client 
{
	Config config_client;
	Socket socket;
	BufferedReader reader;
	PrintWriter writer;


	public Client(Config config){
		config_client =config;
	}

	public void connect() throws Exception{
		
		socket = new Socket(config_client.trackerHost,Integer.parseInt(config_client.trackerPort));
		reader = new BufferedReader( new InputStreamReader (socket.getInputStream()));
		writer = new PrintWriter( new BufferedWriter( new OutputStreamWriter ( socket.getOutputStream ())), true);
		System.out.println("connected to tracker !!!!");
		 
	}

	public void send(String message){

		writer.println (message);	

	}

	public String receive() throws Exception{

		return	reader.readLine();
		

	}

}