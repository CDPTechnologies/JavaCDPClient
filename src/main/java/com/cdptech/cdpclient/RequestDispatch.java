/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.util.*;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.Request.Status;

/**
 * RequestDispatch is responsible for storing and updating a single connection's 
 * section of the node cache.
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

  /** Initialise with a preset Application node. */
  void init(Node presetRoot) {
    presetRoot.setDispatch(this);
    handler.setDispatch(this);
  }

  void close() {}

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

  public Request find(Node parent, String nodePath) {
    List<String> tokens = Arrays.asList(nodePath.split("\\."));
    Iterator<String> it = tokens.iterator();

    Node node = parent;
    while (node != null && node.hasPolledChildren() && it.hasNext()) {
      node = node.getCachedChild(it.next());
    }
    if (node == null) {
      Request r = new Request();
      r.setStatus(Status.ERROR);
      return r;
    } else if (!it.hasNext()) {
      Request r = new Request();
      r.setNode(node);
      r.setStatus(Status.RESOLVED);
      return r;
    }

    Queue<String> remainingTokens = new LinkedList<>();
    it.forEachRemaining(remainingTokens::add);
    URIRequest r = new URIRequest(remainingTokens);
    r.setExpectedNodeID(node.getNodeID());
    pendingRequests.add(r);
    node.requestChildNodes();
    return r;
  }
  
  /** 
   * Send a periodic value request for a node.
   * @param node The node whose value was requested.
   * @param fs The maximum frequency at which values are expected
   */
  void subscribeToNodeValues(Node node, double fs) {
    if (node.getValueType() == StudioAPI.CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    node.hasValueSubscription = true;
    handler.valueRequest(node, fs);
  }
  
  /** Cancel a previous value subscription. */
  void unsubscribeFromNodeValues(Node node) {
    node.hasValueSubscription = false;
    handler.cancelValueSubscription(node);
  }
  
  /** Request a single value for a node. */
  void requestValueForNode(Node node) {
    if (node.getValueType() == StudioAPI.CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    handler.valueRequest(node, 0);
  }
  
  /** Set the remote value for a node to the given variant. */
  void postValueForNode(Node node, Variant value) {
    if (node.getValueType() == StudioAPI.CDPValueType.eUNDEFINED)
      throw new UnsupportedOperationException("Node has no value type");
    if (node.getValueType() != value.getValueType())
      throw new UnsupportedOperationException("Variant value type mismatch");
    handler.setRemoteValue(node, value);
  }
  
  /** Subscribe to the remote structure changes for a node. */
  void subscribeToNodeStructure(Node node) {
    node.hasStructureSubscription = true;
    if (node.isRoot() && node.getDispatch() == this)
      client.broadcastStructureSubscription();
    handler.startStructureSubscription(node.getNodeID());
  }
  
  /** Cancel a previous structure subscription. */
  void cancelNodeStructureSubscription(Node node) {
    node.hasStructureSubscription = false;
    handler.cancelStructureSubscription(node);
  }

  public void initReady(boolean success) {
    if (success) {
      handler.nodeRequest(null);
    }
    else {
      state = State.DROPPED;
      client.dispatchDropped(this);
    }
  }

  public void nodeReceived(Node node) {
    // if no cache has been created or supplied, RequestDispatch is responsible
    // for boostrapping the entire Client
    if (state == State.PENDING) {
      if (connectionCache.isEmpty() && client.getRootNode() == null)
        handleInitialResponse(node);
      else
        addTopLevelStructure(node);
      
    } else {
      Node found = findNodeByID(node.getNodeID());
      if (found == null) {
        System.err.println("Could not place received node in tree!");
        return;
      }

      if (!found.hasPolledChildren() && found.getChildCount() == 0) {
        found.setPolledChildren(true);
        found.takeChildrenFrom(node);
      } else {
        found.setPolledChildren(true);
        if (found.equals(client.getRootNode()))
          updateAppIDOnHandleChange(found, node);
        handleRemovedNodes(found, node);
        handleNewNodes(found, node);
      }
      interceptNode(found);
    }
  }

  private void updateAppIDOnHandleChange(Node cachedNode, Node receivedNode) {
    for (Node cachedChild : cachedNode.getChildList())
      for (Node receivedChild : receivedNode.getChildList())
        if (cachedChild.getName().equals(receivedChild.getName()))
          cachedChild.setNodeID(receivedChild.getNodeID());
  }

  private void handleRemovedNodes(Node cachedNode, Node receivedNode) {
    HashSet<Node> received = new HashSet<>(receivedNode.getChildList());
    List<Integer> idsToRemove = new ArrayList<>();
    for (Node child : cachedNode.getChildList()) {
      if (!received.contains(child)) {
        idsToRemove.add(child.getNodeID());
      }
    }
    for (Integer id : idsToRemove)
      cachedNode.removeChildWithID(id);
  }

  private void handleNewNodes(Node cachedNode, Node receivedNode) {
    HashSet<Node> cached = new HashSet<>(cachedNode.getChildList());
    for (Node child : receivedNode.getChildList()) {
      if (!cached.contains(child)) {
        if (cachedNode.equals(client.getRootNode())) {
          client.openConnection(child);
          // TODO: copy listeners
        } else if (receivedNode.getConnectionData().isLocal) {
          child.setDispatch(this);
          cachedNode.addChild(child);
        }
      }
    }
  }

  public void valueReceived(int nodeID, Variant value) {
    Node node = findNodeByID(nodeID);
    
    if (node != null) {
      node.setValue(value); // fires PropertyChangeEvent
    } else {
      System.err.println("Received value for unknown Node.");
    }
  }

  /** Handle the first CDP_SYSTEM node that this Client receives. */
  private void handleInitialResponse(Node node) {
    List<Node> remoteApps = new ArrayList<>();
    for (int i = node.getChildCount() - 1; i >= 0 ; i--) {
      if (node.getCachedChild(i).getConnectionData().isLocal) {
        connectionCache.add(node.getCachedChild(i));
        node.getCachedChild(i).setDispatch(this);
      } else {
        remoteApps.add(node.getCachedChild(i));
        node.getChildList().remove(i);
      }
    }
    // Since we are the first connection, we'll handle the system node for now
    node.setDispatch(this);
    node.setPolledChildren(true);
    
    state = State.ESTABLISHED;
    client.setRootNode(node);
    client.dispatchReady(this);

    for (Node app : remoteApps)
      client.openConnection(app);
  }
  
  
  /** Add missing top-level components and set the connection dispatch. */
  private void addTopLevelStructure(Node node) {
    for (int i = 0; i < node.getChildCount(); i++) {
      Node receivedAppNode = node.getCachedChild(i);
      if (receivedAppNode.getConnectionData().isLocal) {
        if (!addExistingAppNode(receivedAppNode)) {
          addNewAppNode(receivedAppNode);
        }
      }
    }
    node.setPolledChildren(true);
    state = State.ESTABLISHED;
  }

  private void addNewAppNode(Node receivedAppNode) {
    Node root = client.getRootNode();
      receivedAppNode.setDispatch(this);
    if (root.addChild(receivedAppNode)) {
      connectionCache.add(receivedAppNode);
    }
  }

  private boolean addExistingAppNode(Node receivedAppNode) {
    Node root = client.getRootNode();
    for (Node lostApp : client.getLostApps()) {
      if (lostApp.getName().equals(receivedAppNode.getName())) {
        lostApp.setNodeID(receivedAppNode.getNodeID());
        if (!root.getChildList().contains(lostApp)) {
          lostApp.setParent(root);
          root.getChildList().add(lostApp);
          lostApp.updateDispatch(this);
          connectionCache.add(lostApp);
          client.getLostApps().remove(lostApp);
          return true;
        }
      }
    }
    return false;
  }


  /** Match incoming node against pending structure requests. */
  private void interceptNode(Node node) {
    for (Request req : new ArrayList<Request>(pendingRequests))
      req.offer(node);
    pendingRequests.removeIf(req -> req.getStatus() != Status.PENDING);
  }
  
  /** Find a node from this connection's cache. */
  private Node findNodeByID(int nodeID) {
    if (nodeID == client.getRootNode().getNodeID())
      return client.getRootNode();
    for (Node app : client.getRootNode().getChildList()) {
      if (app.getNodeID() == nodeID) {
        return app;
      }
    }
    Node found = null;
    for (Node node : connectionCache) {
      if ((found = node.findChildByID(nodeID)) != null)
        break;
    }
    return found;
  }

  State getState() {
    return state;
  }

}
