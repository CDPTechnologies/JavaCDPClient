package no.icd.studioapi;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import no.icd.studioapi.Request.Status;
import no.icd.studioapi.proto.Studioapi.CDPNodeType;

class RequestDispatch implements IOListener {
  
  enum State {
    PENDING,
    ESTABLISHED,
    DROPPED
  }
  
  private Client client;
  private IOHandler handler;
  private Node connectionCache;
  private List<Request> pendingRequests;
  private State state;
  
  /**
   * Construct a RequestDispatch instance.
   * @param handler - The underlying connection handler.
   */
  RequestDispatch(Client client, IOHandler handler) throws StudioAPIException {
    
    this.client = client;
    this.handler = handler;
    this.pendingRequests = new LinkedList<Request>();
    connectionCache = null;
    boolean success = false;
    state = State.PENDING;
    
    try {
      success = handler.init(this);
    } catch (Exception e) {
      throw new StudioAPIException("Failed to connect!");
    }
    
    if (!success)
      throw new StudioAPIException("Faield to connect!");
    
    handler.nodeRequest(null);
  }
  
  /**
   * Event loop mechanism. Polls underlying connection and calls queued 
   * callbacks.
   */
  void service() {
    // TODO queued resolved requests should maybe be resolved here?
    handler.pollEvents();
  }
  
  /**
   * Send a request for the children of a given node.
   * @param  node The node to poll for.
   * @return A request object to track the result.
   */
  Request requestChildrenForNode(Node node) {
    Request req = new Request();
    if (node.hasPolledChildren()) {
      req.setNode(node);
      req.setStatus(Status.RESOLVED);
    } else {
      req.setExpectedNodeID(node.getNodeID());
      pendingRequests.add(req);
      handler.nodeRequest(node);
    }
    return req;
  }
  
  void subscribeToNodeValues(Node node, double fs) {
    // TODO verify/hold state
    handler.valueRequest(node, fs);
  }

  @Override
  public void nodeReceived(Node node) {
    
    // if no cache has been created or supplied, RequestDispatch is responsible
    // for boostrapping the entire Client
    if (state == State.PENDING) {
      if (connectionCache == null)
        handleInitialResponse(node);
      else
        addTopLevelStructure(node);
      
    } else {
      // find the node from the tree and replace it.
      Node found = connectionCache.findChildByID(node.getNodeID());
      
      if (found == null) {
        // TODO log this
        System.out.println(connectionCache);
        return;
      }
      
      if (!found.hasPolledChildren()) {
        found.takeChildrenFrom(node);
        System.out.println("replaced " + found.getParent());
        
        interceptNode(node);
      }
    }
  }

  
  @Override
  public void valueReceived(int nodeID, Variant value) {
    Node node = connectionCache.findChildByID(nodeID);
    
    if (node != null) {
      node.setValue(value); // fires PropertyChangeEvent
    } else {
      System.err.println("Received value for unknown Node.");
    }
    
  }
  
  
  /** Handle the first CDP_SYSTEM node that this Client receives. */
  private void handleInitialResponse(Node node) {
    
    for (int i = 0; i < node.getChildCount(); i++) {
      if (node.getCachedChild(i).getConnectionData().correspondsToConnection) {
        connectionCache = node.getCachedChild(i);
        connectionCache.setDispatch(this);
        break;
      }   
    }
    // Since we are the first connection, we'll handle the system node for now
    node.setDispatch(this);
    state = State.ESTABLISHED;
    client.setGlobalCache(node);
  }
  
  
  /** TODO Add missing top-level components and set the connection dispatch. */
  private void addTopLevelStructure(Node node) {
    state = State.ESTABLISHED;
  }
  
  
  /** Match incoming node against pending structure requests. */
  private void interceptNode(Node node) {
    for (Request req : pendingRequests) {
      if (req.getExpectedNodeID() == node.getNodeID()) {
        pendingRequests.remove(req);
        req.setNode(node);
        req.setStatus(Status.RESOLVED);
      }
    }
  }

}
