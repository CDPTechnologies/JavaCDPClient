/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import no.icd.studioapi.RequestDispatch.State;

/**
 * Main Client class for initializing StudioAPI Java.
 *
 * Following is a small example:
 * <pre>
 * {@code
Client client = new Client();
client.init("127.0.0.1", 7689, new NotificationListener() {
    public void clientReady(Client client) {
        System.out.println("Client connected");
        client.findNode("myApp.CPULoad").then((node, status) -> {
            if (status == Request.Status.RESOLVED)
                node.subscribeToValueChanges(value -> System.out.println(value + "\t" + value.getTimestamp()), 10);
        });
        client.findNode("myApp.MySignal").then((node, status) -> {
            if (status == Request.Status.RESOLVED)
                node.postValue(new Variant.Builder(StudioAPI.CDPValueType.eDOUBLE).parse("4").build());
        });
    }
    public void clientClosed(Client client) {
        System.out.println("Client closed");
    }
});
client.run();
 * }
 * </pre>
 */
public class Client implements Runnable {
  
  private Map<URI, RequestDispatch> connections;
  private Set<URI> lostConnections = new HashSet<>();
  private Node rootNode;
  private Set<Node> lostApps = new HashSet<>();
  private NotificationListener listener;
  private IOHandler ioHandler;
  private long lastReconnectTimeMs = 0;
  private boolean cleanupConnections;
  private boolean timeSyncEnabled = true;
  private boolean autoReconnect = true;
  private boolean clientClosed = false;

  /** Create a new StudioAPI Client instance. */
	public Client() {
		connections = new HashMap<>();
		rootNode = null;
		listener = null;
		cleanupConnections = false;
		timeSyncEnabled = true;
	}

	/** Initialise the client. Connects to server and notifies @a listener. */
	public void init(String addr, int port, NotificationListener listener) {
	  this.listener = listener;
	  clientClosed = false;
    try {
      URI wsURI = new URI("ws", null, addr, port, null, null, null);
      if (connections.containsKey(wsURI))
      	return;
      ioHandler = new IOHandler(wsURI);
      ioHandler.setTimeSyncEnabled(timeSyncEnabled);
      RequestDispatch d = new RequestDispatch(this, ioHandler);
      d.init();
      connections.put(wsURI, d);
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException("Unable to parse server URI");
    }
	}

	/** Get a reference to the system node. CDP applications are children of this node. */
	public Node getRootNode() {
	  return rootNode;
	}

	/**
	 * (asynchronous) Request node with provided path.
	 * @param nodePath Should contain dot separated path to target node.
	 */
	public Request findNode(String nodePath) {
		return getRootNode().find(nodePath);
	}

	/**
	 * Sets whether to enable automatic and periodic time sync. When enabled, the timestamps
	 * received from remote machines (e.g. values received after calling subscribeToValueChanges())
	 * are adjusted for the clock difference between this machine and remote machine.
	 */
	public void setTimeSyncEnabled(boolean enabled) {
	  timeSyncEnabled = enabled;
	  if (ioHandler != null)
	    ioHandler.setTimeSyncEnabled(timeSyncEnabled);
	}

	public void setAutoReconnect(boolean enabled) {
		this.autoReconnect = enabled;
	}

	/** Event-loop method for use in single-threaded applications. */
	public void process() {
	  for (RequestDispatch dispatch : connections.values())
		  dispatch.service();
	  if (cleanupConnections)
	    removeDroppedConnections();
	  if (autoReconnect && !clientClosed  && (System.currentTimeMillis() - lastReconnectTimeMs > 200)) {
	    for (URI uri : lostConnections)
	      init(uri.getHost(), uri.getPort(), this.listener);
	    lastReconnectTimeMs = System.currentTimeMillis();
	  }
	}

	/** Runnable-interface auto-creates event loop in a new Thread. */
	public void run() {
    while (true) {
      process();
      if (clientClosed)
        break;
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    }
	}

	/** Closes all connection and disables automatic reconnect. */
	public void close() {
	  clientClosed = true;
	  for (RequestDispatch dispatch : connections.values())
	    dispatch.close();
	}

	/** Called internally to set the root node of the system. */
	void setRootNode(Node node) {
	  rootNode = node;
	}

	/** Called by a dispatch to notify it's ready. */
	void dispatchReady(RequestDispatch dispatch) {
	  for (RequestDispatch d : connections.values()) {
	    if (d.getState() != State.ESTABLISHED)
	      break;
	  }
    listener.clientReady(this);
	}

	/** Called by a dispatch to notify it's connection was lost. */
	void dispatchDropped(RequestDispatch dispatch) {
	  // notify all involved nodes if they have listeners
	  if (rootNode != null) {
	    for (int i = 0; i < rootNode.getChildCount(); i++) {
	  	  Node n = rootNode.getCachedChild(i);
		  if (n.getDispatch() == dispatch) {
		  	n.notifyNodeIsLost();
		  }
	    }
	  }

	  cleanupConnections = true;
	}

	/** Open connection to unconnected top-level node. */
    void openConnection(Node app) {
      if (app.getDispatch() == null) {
        Node.ConnectionData data = app.getConnectionData();
        try {
          URI wsURI = new URI("ws", null,
                  data.serverAddr, data.serverPort,
                  null, null, null);
          if (connections.containsKey(wsURI))
          	return;
          IOHandler io = new IOHandler(wsURI);
          ioHandler.setTimeSyncEnabled(timeSyncEnabled);
          RequestDispatch d = new RequestDispatch(this, io);
          d.init(app);
          connections.put(wsURI, d);
        } catch (URISyntaxException e) {
          cleanupConnections = true;
        }
      }
    }

	/** Post-process cleanup method. */
	private void removeDroppedConnections() {
	  // remove dispatches
    Iterator<Map.Entry<URI, RequestDispatch>> it = connections.entrySet().iterator();
    while (it.hasNext()) {
	  Map.Entry<URI, RequestDispatch> entry = it.next();
      if (entry.getValue().getState() != State.ESTABLISHED) {
      	lostConnections.add(entry.getKey());
        it.remove();
      }
    }

	  // remove nodes left without dispatches
	  if (rootNode != null) {
	    ListIterator<Node> iter = rootNode.getChildList().listIterator();
	    while (iter.hasNext()) {
		  Node n = iter.next();
		  if (n.getDispatch() == null || !connections.containsValue(n.getDispatch())) {
		    lostApps.add(n);
	  	    n.setParent(null);
			iter.remove();
		  }
		}
	  }
	  cleanupConnections = false;
	  if (connections.isEmpty()) {
	    if (!autoReconnect) {
	      clientClosed = true;
	      listener.clientClosed(this);
	    }
        return;
      }
	  if (rootNode != null && !connections.containsValue(rootNode.getDispatch()))
	    rootNode.setDispatch(connections.values().iterator().next());
	}

	/** Broadcast a root structure subscription to everyone but its handler. */
  void broadcastStructureSubscription() {
    for (RequestDispatch d : connections.values()) {
      if (rootNode.getDispatch() != d && d.getState() == State.ESTABLISHED)
        d.subscribeToNodeStructure(rootNode);
    }
  }

  Set<Node> getLostApps() {
  	return lostApps;
  }

}
