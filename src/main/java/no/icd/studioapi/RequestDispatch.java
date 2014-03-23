package no.icd.studioapi;

import java.util.ArrayList;

import no.icd.studioapi.Request.Status;
import no.icd.studioapi.proto.Studioapi.CDPNodeType;

public class RequestDispatch implements IOListener {
  
  private Client client;
  private IOHandler handler;
  private Node connectionCache;
  private ArrayList<Request> pendingRequests;
  
  /**
   * Construct a RequestDispatch instance.
   * @param handler - The underlying connection handler.
   */
  public RequestDispatch(Client client, IOHandler handler) {
    
    this.client = client;
    this.handler = handler;
    this.pendingRequests = new ArrayList<Request>();
    connectionCache = null;
    handler.setListener(this);
    
    try {
      boolean success = handler.connectBlocking();
      
      if (!success)
        throw new RuntimeException("Failed to connect!");
      
      handler.nodeRequest(null);
      
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Send a request for the children of a given node.
   * @param node The node to poll for.
   * @return
   */
  synchronized Request requestChildrenForNode(Node node) {
    System.out.println(connectionCache);
    Request req = new Request();
    pendingRequests.add(req);
    handler.nodeRequest(node);
    return req;
  }

  @Override
  public void nodeReceived(Node node) {
    System.out.println(connectionCache);
    // if no cache has been created or supplied, RequestDispatch is responsible
    // for boostrapping the entire Client
    if (connectionCache == null) {
      client.setGlobalCache(node);
      connectionCache = node.getChild(0); // TODO corresponding...
      connectionCache.setDispatch(this);
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
