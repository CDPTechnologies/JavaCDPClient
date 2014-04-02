package no.icd.studioapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class Client implements Runnable {
  
  private List<RequestDispatch> connections;
  private Node globalCache;
  private NotificationListener listener;
	
  /**
   * Create a new StudioAPI Client instance.
   */
	public Client() {
		connections = new ArrayList<RequestDispatch>();
		globalCache = null;
		listener = null;
	}
	
	public void init(String addr, int port, NotificationListener listener) 
	    throws StudioAPIException {
	  this.listener = listener;
    try {
      URI wsURI = new URI("ws", null, addr, port, null, null, null);
      IOHandler handler = new IOHandler(wsURI);
      RequestDispatch d = new RequestDispatch(this, handler);
      connections.add(d);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to parse server URI");
    }
	}

	public Node getGlobalCache() {
	  return globalCache;
	}
	
	public void process() {
	  for (RequestDispatch d : connections) {
	    d.service();
	  }
	}

	@Override
	public void run() {
    while (true) {
      process();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
	}

	void setGlobalCache(Node n) {
	  globalCache = n;
	  // TODO open other connections
	  listener.clientReady(this);
	}

}
