import java.net.URI;
import java.net.URISyntaxException;

import org.java_websocket.drafts.Draft_17;



public class Main {
	
	public static void main(String[] args) throws URISyntaxException, InterruptedException
	{
		IOHandler c = new IOHandler(new URI("ws://192.168.1.5:7681"), new Draft_17());
		System.out.println("Starting connection");
		c.connectBlocking();
	}

}
