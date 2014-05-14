/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import no.icd.studioapi.RequestDispatch.State;
import no.icd.studioapi.proto.StudioAPI.StructureChangeType;

/**
 * Main Client class for intializing StudioAPI Java
 * @author kpu@icd.no
 */
public class Client implements Runnable {
  
  private List<RequestDispatch> connections;
  private Node globalCache;
  private NotificationListener listener;
  boolean cleanupConnections;
	
  /** Create a new StudioAPI Client instance. */
	public Client() {
		connections = new ArrayList<RequestDispatch>();
		globalCache = null;
		listener = null;
		cleanupConnections = false;
	}
	
	/** Initialise the client. Connects to server and notifies @a listener. */
	public void init(String addr, int port, NotificationListener listener) {
	  this.listener = listener;
    try {
      URI wsURI = new URI("ws", null, addr, port, null, null, null);
      IOHandler handler = new IOHandler(wsURI);
      RequestDispatch d = new RequestDispatch(this, handler);
      d.init();
      connections.add(d);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to parse server URI");
    }
	}

	/** Get a reference to the global node cache. */
	public Node getGlobalCache() {
	  return globalCache;
	}
	
	/** Event-loop method for use in single-threaded applications. */
	public void process() {
	  for (RequestDispatch d : connections)
	    d.service();
	  if (cleanupConnections)
	    removeDroppedConnections();
	}

	/** Runnable-interface auto-creates event loop in a new Thread. */
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

	/** Called internally to set the root node of the system. */
	void setGlobalCache(Node node) {
	  openOtherConnections();
	  globalCache = node;
	}
	
	/** Called by a dispatch to notify it's ready. */
	void dispatchReady(RequestDispatch dispatch) {
	  for (RequestDispatch d : connections) {
	    if (d.getState() != State.ESTABLISHED)
	      break;
	  }
    listener.clientReady(this);
	}
	
	/** Called by a dispatch to notify it's connection was lost. */
	void dispatchDropped(RequestDispatch dispatch) {
	  // notify all involved nodes if they have listeners
	  for (int i = 0; i < globalCache.getChildCount(); i++) {
	    Node n = globalCache.getCachedChild(i);
	    if (n.getDispatch() == dispatch) {
	      if (n.hasStructureSubscription)
	        n.notifyStructureChanged(new StructureChange(
	            StructureChangeType.eSubscribedNodeLost, "StudioAPI", 0.0d));
	      else if (globalCache.hasStructureSubscription) {
	        StructureChange sc = new StructureChange(
	            StructureChangeType.eChildRemoved, "StudioAPI", 0.0d);
	        sc.setChangedNode(n);
	        globalCache.notifyStructureChanged(sc);
	      }
	    }
	  }
	  
	  cleanupConnections = true;
	  connections.remove(dispatch);
	  if (connections.isEmpty()) {
	    listener.clientClosed(this);
	  }
	}
	
	/** Open connections to unconnected top-level nodes. */
	void openOtherConnections() {
	  for (int i = 0; i < globalCache.getChildCount(); i++) {
	    Node child = globalCache.getCachedChild(i);
	    
	    if (child.getDispatch() == null) {
	      Node.ConnectionData data = child.getConnectionData();
	      try {
	        URI wsURI = new URI("ws", null, 
	            data.serverAddr, data.serverPort,
	            null, null, null);
	        IOHandler io = new IOHandler(wsURI);
	        RequestDispatch d = new RequestDispatch(this, io);
	        d.init(child);
	        connections.add(d);
	      } catch (URISyntaxException e) { 
	        cleanupConnections = true; 
	      }
	    }
	  }
	}
	
	/** Post-process cleanup method. */
	void removeDroppedConnections() {
	  // remove dispatches
    ListIterator<RequestDispatch> it = connections.listIterator();
    while (it.hasNext()) {
      if (it.next().getState() != State.ESTABLISHED) {
        it.remove();
      }
    }
	  // remove nodes left without dispatches
	  ListIterator<Node> iter = globalCache.getChildList().listIterator();
	  while (iter.hasNext()) {
	    Node n = iter.next();
	    if (n.getDispatch() == null || !connections.contains(n.getDispatch())) {
	      n.setParent(null);
	      iter.remove();
	    }
	  }
	  cleanupConnections = false;
	}

}
