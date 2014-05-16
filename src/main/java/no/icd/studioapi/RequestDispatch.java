/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import no.icd.studioapi.Request.Status;
import no.icd.studioapi.proto.StudioAPI.CDPNodeType;
import no.icd.studioapi.proto.StudioAPI.CDPValueType;
import no.icd.studioapi.proto.StudioAPI.StructureChangeType;

/**
 * RequestDispatch is responsible for storing and updating a single connection's 
 * section of the node cache.
 * @author kpu@icd.no
 */
class RequestDispatch implements IOListener {
  
  enum State {
    PENDING,
    ESTABLISHED,
    DROPPED
  }
  
  private Client client;
  private IOHandler handler;
  private List<Node> connectionCache;
  private List<Request> pendingRequests;
  private State state;
  
  /**
   * Construct a RequestDispatch instance.
   * @param handler - The underlying connection handler.
   */
  RequestDispatch(Client client, IOHandler handler) {
    
    this.client = client;
    this.handler = handler;
    this.pendingRequests = new LinkedList<Request>();
    connectionCache = new ArrayList<Node>();
    state = State.PENDING;
  }
  
  /** Initialise. */
  void init() {
    handler.init(this);
  }
  
  /** Initialise with a preset Application node. */
  void init(Node presetRoot) {
    connectionCache.add(presetRoot);
    presetRoot.setDispatch(this);
    handler.init(this);
  }
  
  /**
   * Event loop mechanism. Polls underlying connection and calls queued 
   * callbacks.
   */
  void service() {
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
  
  /** 
   * Send a periodic value request for a node.
   * @param node The node whose value was requested.
   * @param fs The maximum frequency at which values are expected
   */
  void subscribeToNodeValues(Node node, double fs) {
    if (node.getValueType() == CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    node.hasValueSubscription = true;
    handler.valueRequest(node, fs);
  }
  
  /** Cancel a previous value subscription. */
  void unsubcribeFromNodeValues(Node node) {
    node.hasValueSubscription = false;
    handler.cancelValueSubscription(node);
  }
  
  /** Request a single value for a node. */
  void requestValueForNode(Node node) {
    if (node.getValueType() == CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    handler.valueRequest(node, 0);
  }
  
  /** Set the remote value for a node to the given variant. */
  void postValueForNode(Node node, Variant value) {
    if (node.getValueType() == CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    if (node.getValueType() != value.getValueType())
      throw new UnsupportedOperationException("Variant value type mismatch");
    handler.setRemoteValue(node, value);
  }
  
  /** Subscribe to the remote structure changes for a node. */
  void subscribeToNodeStructure(Node node, int lvl) {
    if (lvl < 0)
      throw new IllegalArgumentException("Can't subscribe with negative depth");
    node.hasStructureSubscription = true;
    if (node.isRoot() && node.getDispatch() == this) {
      if (lvl == 1) {
        System.out.println("Subcription at level 1 only notifies of subscribed node loss");
        return;
      } else {
        client.broadcastStructureSubscription(lvl);
      }
    }
    handler.startStructureSubscription(node, lvl);
  }
  
  /** Cancel a previous structure subscription. */
  void cancelNodeStructureSubscription(Node node) {
    node.hasStructureSubscription = false;
    handler.cancelStructureSubscription(node);
  }
  
  @Override
  public void initReady(boolean success) {
    if (success)
      handler.nodeRequest(null);
    else {
      state = State.DROPPED;
      client.dispatchDropped(this);
    }
  }

  @Override
  public void nodeReceived(Node node) {
    // if no cache has been created or supplied, RequestDispatch is responsible
    // for boostrapping the entire Client
    if (state == State.PENDING) {
      if (connectionCache.isEmpty())
        handleInitialResponse(node);
      else
        addTopLevelStructure(node);
      
    } else {
      Node found = findNodeByID(node.getNodeID());
      
      if (found == null) {
        System.err.println("Could not place received node in tree!");
        return;
      }
      
      if (!found.hasPolledChildren()) {
        found.takeChildrenFrom(node);
        found.setPolledChildren(true);
        interceptNode(found);
      }
    }
  }
  
  @Override
  public void valueReceived(int nodeID, Variant value) {
    Node node = findNodeByID(nodeID);
    
    if (node != null) {
      node.setValue(value); // fires PropertyChangeEvent
    } else {
      System.err.println("Received value for unknown Node.");
    }
    
  }
  
  @Override
  public void structureChangeReceived(int nodeID, StructureChange event) {
    Node subscribed = findNodeByID(nodeID);
    if (subscribed == null) {
      System.err.println("Could not place received node in tree!");
      return;
    }
    if (event.getChangeType() == StructureChangeType.eSubscribedNodeLost) {
      nodeInvalidated(subscribed, event);
    } else {
      Node parent = subscribed.findChildByID(event
          .getChangedNode()
          .getNodeID());
      if (parent == null)
        System.err.println("Received structure change for uncached node.");
      else
        nodeAddedOrRemoved(subscribed, parent, event);
    }
  }

  /** Handle the first CDP_SYSTEM node that this Client receives. */
  private void handleInitialResponse(Node node) {
    
    for (int i = 0; i < node.getChildCount(); i++) {
      if (node.getCachedChild(i).getConnectionData().isLocal) {
        connectionCache.add(node.getCachedChild(i));
        node.getCachedChild(i).setDispatch(this);
      }   
    }
    // Since we are the first connection, we'll handle the system node for now
    node.setDispatch(this);
    node.setPolledChildren(true);
    
    state = State.ESTABLISHED;
    client.setGlobalCache(node);
    client.dispatchReady(this);
  }
  
  
  /** Add missing top-level components and set the connection dispatch. */
  private void addTopLevelStructure(Node node) {
    Node root = client.getGlobalCache();
    for (int i = 0; i < node.getChildCount(); i++) {
      if (root.addChild(node.getCachedChild(i))) {
        connectionCache.add(node.getCachedChild(i));
        node.getCachedChild(i).setDispatch(this);
      }
    }
    node.setPolledChildren(true);
    state = State.ESTABLISHED;
  }
  
  
  /** Match incoming node against pending structure requests. */
  private void interceptNode(Node node) {
    List<Request> resolved = new ArrayList<Request>();
    Iterator<Request> iter = pendingRequests.iterator();
    
    while (iter.hasNext()) {
      Request req = iter.next();
      if (req.getExpectedNodeID() == node.getNodeID()) {
        iter.remove();
        resolved.add(req);
      }
    }
    
    // callbacks are safe only when called from separate lists
    for (Request req : resolved) {
      req.setNode(node);
      req.setStatus(Status.RESOLVED);
    }
  }
  
  /** Find a node from this connection's cache. */
  private Node findNodeByID(int nodeID) {
    Node found = null;
    for (Node node : connectionCache) {
      if ((found = node.findChildByID(nodeID)) != null)
        break;
    }
    return found;
  }
  
  /** Handle an invalidated node. */
  private void nodeInvalidated(Node subscribed, StructureChange event) {
    subscribed.notifyStructureChanged(event);
    subscribed.getParent().invalidateCache();
  }
  
  /** Handle a subnode added or removed. */
  private void nodeAddedOrRemoved(Node subscribed, Node parent,
      StructureChange event) {
    if (!parent.hasPolledChildren() || 
        event.getChangeType() == StructureChangeType.eChildAdded) {
      // add all child nodes that didn't exist before or just the newest one
      for (int i = 0; i < event.getChangedNode().getChildCount(); i++)
        parent.addChild(event.getChangedNode().getCachedChild(i));
      parent.setPolledChildren(true);
    }
    // mark changed node in the structure.
    event.setChangedNode(parent.findChildByID(event.getChangedNodeID()));
    subscribed.notifyStructureChanged(event);
    if (event.getChangeType() == StructureChangeType.eChildRemoved)
      parent.removeChildWithID(event.getChangedNodeID());
    
  }
  
  State getState() {
    return state;
  }

}
