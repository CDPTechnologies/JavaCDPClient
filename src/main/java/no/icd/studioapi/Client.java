package no.icd.studioapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Client {
  
  private List<RequestDispatch> connections;
  private Node globalCache;
	
	public Client() {
		connections = new ArrayList<RequestDispatch>();
		globalCache = null;
	}
	
	public void connect(String addr, int port) throws URISyntaxException {
	  // TODO wss should be handled at this level
	  URI wsURI = new URI("ws", null, addr, port, null, null, null);
	  IOHandler handler = new IOHandler(wsURI);
	  RequestDispatch d = new RequestDispatch(this, handler);
	  connections.add(d);
	}

	public Node getGlobalCache() {
	  return globalCache;
	}

	public void setGlobalCache(Node n) {
	  globalCache = n;
	}

}
