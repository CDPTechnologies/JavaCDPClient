package no.icd.studioapi;

import java.util.ArrayList;

import no.icd.studioapi.Request.Status;
import no.icd.studioapi.proto.Studioapi.CDPNodeType;

class RequestDispatch implements IOListener {
  
  private Client client;
  private IOHandler handler;
  private Node connectionCache;
  private ArrayList<Request> pendingRequests;
  
  /**
   * Construct a RequestDispatch instance.
   * @param handler - The underlying connection handler.
   */
  RequestDispatch(Client client, IOHandler handler) throws StudioAPIException {
    
    this.client = client;
    this.handler = handler;
    this.pendingRequests = new ArrayList<Request>();
    connectionCache = null;
    boolean success = false;
    
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
   * callbacks
   */
  void service() {
    // TODO queued resolved requests should maybe be resolved here?
    handler.pollEvents();
  }
  
  /**
   * Send a request for the children of a given node.
   * @param node The node to poll for.
   * @return
   */
  Request requestChildrenForNode(Node node) {
    System.out.println(connectionCache);
    Request req = new Request();
    pendingRequests.add(req);
    handler.nodeRequest(node);
    return req;
  }

  @Override
  public void nodeReceived(Node node) {
    
    // if no cache has been created or supplied, RequestDispatch is responsible
    // for boostrapping the entire Client
    if (connectionCache == null) {
      
      connectionCache = node.getChild(0); // TODO corresponding...
      connectionCache.setDispatch(this);
      
      // Give root to Client.
      client.setGlobalCache(node);
      return;
      
    } else {
      // find the node from the tree and replace it.
      Node found = connectionCache.findChildByID(node.getNodeID());
      
      if (found == null) {
        // TODO log this
        System.out.println(connectionCache);
        return;
      }
      
      if (!found.hasPolledChildren()) {
        found.getParent().replaceChild(node);
        if (found.getParent().getNodeType() == CDPNodeType.CDP_SYSTEM)
          connectionCache = node;
        System.out.println("replaced " + found.getParent());
        // TODO intercept node
        for (Request req : pendingRequests) {
          pendingRequests.remove(req);
          req.setNode(node);
          req.setStatus(Status.RESOLVED);
        }
      }

    }
  }

  @Override
  public void valueReceived(int nodeID) {
    // TODO Auto-generated method stub
    
  }

}
