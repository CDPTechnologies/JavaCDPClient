/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.util.*;

import com.cdptech.cdpclient.proto.StudioAPI;

/**
 * The Node class represents a single element of the CDP System hierarchy.
 */
public class Node {
  
  /** Connection data structure for top-level nodes. */
  static class ConnectionData {
    
    boolean isLocal;
    String serverAddr;
    int serverPort;
    
    ConnectionData() {
      isLocal = true;
    }
    
    ConnectionData(String addr, int port) {
      isLocal = false;
      serverAddr = addr;
      serverPort = port;
    }
  }
  
  private int nodeID;
  private StudioAPI.CDPNodeType nodeType;
  private StudioAPI.CDPValueType valueType;
  private String name;
  private String typeName;
  private boolean isReadOnly;
  private boolean isPersistent;
  private boolean isLeaf;
  private boolean isImportant;
  private boolean isInternal;
  private List<Node> children;
  private Node parent;
  private boolean polledChildren;
  private Variant value;
  private RequestDispatch dispatch;
  private ConnectionData connectionData = null;
  private Map<ValueListener, Double> valueListenerFsMap;
  private Set<ValueListener> singleListeners;
  private Set<SubtreeListener> subtreeListeners;
  boolean hasValueSubscription = false;
  boolean hasStructureSubscription = false;
  
  /** (asynchronous) Send a request for this node's child nodes. */
  public Request requestChildNodes() {
    return dispatch.requestChildrenForNode(this);
  }

  /** (asynchronous) Subscribe to this node's value changes with @a listener. */
  public void subscribeToValueChanges(ValueListener listener) {
    subscribeToValueChanges(listener, 10);
  }

  /**
   * (asynchronous) Subscribe to this node's value changes with @a listener.
   * @param listener Callback which starts receiving value changes.
   * @param fs Frequency. Sets how often packets containing value changes are received. Note that
   *           still all value changes are received, larger packets simply improve performance.
   */
  public void subscribeToValueChanges(ValueListener listener, double fs) {
    if (valueType != StudioAPI.CDPValueType.eUNDEFINED)
      valueListenerFsMap.put(listener, fs);
    if (!hasValueSubscription)
      dispatch.subscribeToNodeValues(this, fs);
  }
  
  /** Remove a previously registered value @a listener. */
  public void removeValueListener(ValueListener listener) {
    Double removed = valueListenerFsMap.remove(listener);
    if (removed != null && valueListenerFsMap.size() == 0)
      dispatch.unsubscribeFromNodeValues(this);
  }
  
  /** Request a single value for this node. */
  public void requestValue(ValueListener listener) {
    singleListeners.add(listener);
    dispatch.requestValueForNode(this);
  }
  
  /**
   * (asynchronous) Set the remote value of this node to @a value.
   *
   * Example: '{@code node.postValue(new Variant.Builder(StudioAPI.CDPValueType.eDOUBLE).parse("4").build());}'
   */
  public void postValue(Variant value) {
    dispatch.postValueForNode(this, value);
  }
  
  /** 
   * (asynchronous) Subscribe to the remote structure changes of this node.
   * @param listener Callback listener informed of structure changes. 
   */
  public void addSubtreeListener(SubtreeListener listener) {
    subtreeListeners.add(listener);
    if (!hasStructureSubscription)
      dispatch.subscribeToNodeStructure(this);
  }
  
  /** (asynchronous) Remove a previously registered structure listener. */
  public void removeSubtreeListener(SubtreeListener listener) {
    boolean success = subtreeListeners.remove(listener);
    if (success && subtreeListeners.isEmpty())
      dispatch.cancelNodeStructureSubscription(this);
  }

  /**
   * (asynchronous) Request node with the provided path.
   * @param nodePath Should contain dot separated path to target node.
   */
  public Request find(String nodePath) {
    return dispatch.find(this, nodePath);
  }

  /**
   * (asynchronous) Request child node with provided name.
   * @param name Name of the child node.
   */
  public Request getChild(String name) {
    return find(name);
  }
  
  /** Get the number of cached children this Node has. 0 unless {@link #requestChildNodes()} has been called. */
  public int getChildCount() {
    return children.size();
  }
  
  /** Get a cached child at index @a n. Note that {@link #requestChildNodes()} must be called first. */
  public Node getCachedChild(int n) {
    return children.get(n);
  }
  
  /**
   * Returns the most recent value that this node has received. Remember to first subscribe to value changes.
   *
   * @link {#subscribeToValueChanges(ValueListener)}, {@link #requestValue(ValueListener)}
   */
  public Variant getCachedValue() {
    return value;
  }
  
  /** Get the node type of this Node. */
  public StudioAPI.CDPNodeType getNodeType() {
    return nodeType;
  }
  
  /** Get the value type of this Node. */
  public StudioAPI.CDPValueType getValueType() {
    return valueType;
  }
  
  /** Get the short name of this node. */
  public String getName() {
    return name;
  }

  /** Get the parent of this node. */
  public Node getParent() {
    return parent;
  }
  
  /** Get the model name that this node represents. */
  public String getTypeName() {
    return typeName;
  }
  
  /** Get the long name of this node (e.g "MyApp.MyComponent.MySignal"). */
  public String getLongName() {
    if (parent == null || parent.isRoot())
      return name;
    return parent.getLongName() + "." + name;
  }
  
  /** Check if this node's value can be changed. */
  public boolean isValueReadOnly() {
    return isReadOnly;
  }
  
  /** Check if this node's value is saved to XML when it changes. */
  public boolean isValuePersistent() {
    return isPersistent;
  }

  /** Check if the node has it's sub-structure polled. */
  public boolean hasPolledChildren() {
    return polledChildren;
  }

  /** Check if this node has any children. */
  public boolean isLeaf() {
    return isLeaf;
  }

  /** Check if this node has display hint "Important". Nodes marked important should be more prominent in the UI. */
  public boolean isImportant() {
    return isImportant;
  }

  /**
   * Check if this node has display hint "Internal". Nodes marked internal are implementation details
   * which are generally hidden from the UI.
   */
  public boolean isInternal() {
    return isInternal;
  }

  /** Nodes are constructed by StudioAPI only. */
  Node(int id, StudioAPI.CDPNodeType ntype, StudioAPI.CDPValueType vtype, String name, int flags) {
    this.nodeID = id;
    this.nodeType = ntype;
    this.valueType = vtype;
    this.name = name;
    this.children = new ArrayList<Node>();
    this.polledChildren = false;
    this.value = new Variant(StudioAPI.CDPValueType.eUNDEFINED, "", 0);
    this.valueListenerFsMap = new HashMap<>();
    this.singleListeners = new HashSet<ValueListener>();
    this.subtreeListeners = new HashSet<SubtreeListener>();
    this.isReadOnly = (flags & StudioAPI.Info.Flags.eValueIsReadOnly.getNumber()) != 0;
    this.isPersistent = (flags & StudioAPI.Info.Flags.eValueIsPersistent.getNumber()) != 0;
    this.isLeaf = (flags & StudioAPI.Info.Flags.eNodeIsLeaf.getNumber()) != 0;
    this.isImportant = (flags & StudioAPI.Info.Flags.eNodeIsImportant.getNumber()) != 0;
    this.isInternal = (flags & StudioAPI.Info.Flags.eNodeIsInternal.getNumber()) != 0;
  }

  Node getCachedChild(String name) {
    for (int i = 0; i < getChildCount(); i++)
      if (getCachedChild(i).getName().equals(name))
        return getCachedChild(i);
    return null;
  }
  
  /** Add a child with an unique ID to this node. Returns true on success. */
  boolean addChild(Node child) {
    for (Node existingChild : children) {
      if (existingChild.nodeID == child.nodeID) {
        return false;
      }
    }
    children.add(child);
    child.setParent(this);
    propagateSubtreeChange(child, SubtreeChangeType.eChildAdded);
    return true;
  }
  
  /** Steal all child nodes from the given parent. */
  void takeChildrenFrom(Node parent) {
    this.children = parent.children;
    parent.children = null;

    for (Node child : children) {
      child.setParent(this);
      child.setDispatch(this.dispatch);
    }
  }
  
  /** Search the node tree recursively by the supplied nodeID. */
  Node findChildByID(int nodeID) {
    if (this.nodeID == nodeID)
      return this;
    for (Node child : children) {
      Node ret = child.findChildByID(nodeID);
      if (ret != null)
        return ret;
    }
    return null;
  }
  
  /** Notify this node's subtree listeners of an event. */
  private void notifySubtreeChanged(Node changedNode, SubtreeChangeType changeType) {
    for (SubtreeListener listener : subtreeListeners) {
      listener.subtreeChanged(changedNode, changeType);
    }
  }

  void notifyNodeIsLost() {
    notifySubtreeChanged(this, SubtreeChangeType.eSubscribedNodeLost);
    dispatch = null;
    for (Node child : children)
      child.notifyNodeIsLost();
  }

  private void propagateSubtreeChange(Node changedNode, SubtreeChangeType changeType)
  {
    notifySubtreeChanged(changedNode, changeType);
    if (getParent() != null)
      getParent().propagateSubtreeChange(changedNode, changeType);
  }
  
  /** Remove all child nodes and unset the polled flag. */
  void invalidateCache() {
    children.clear();
    polledChildren = false;
  }
  
  void removeChildWithID(int nodeID) {
    ListIterator<Node> iter = children.listIterator();
    while (iter.hasNext()) {
      Node next = iter.next();
      if (next.nodeID == nodeID) {
        propagateSubtreeChange(next, SubtreeChangeType.eChildRemoved);
        next.notifyPendingDeletion();
        iter.remove();
        break;
      }
    }
  }

  private void notifyPendingDeletion() {
    notifySubtreeChanged(this, SubtreeChangeType.eChildRemoved);
    for (Node child : getChildList())
      child.notifyPendingDeletion();
  }
  
  /* Internal accessor methods */
  
  void setTypeName(String typeName) {
    this.typeName = typeName;
  }
  
  int getNodeID() {
    return nodeID;
  }

  void setPolledChildren(boolean polledChildren) {
    this.polledChildren = polledChildren;
  }

  RequestDispatch getDispatch() {
    return dispatch;
  }

  void setDispatch(RequestDispatch dispatch) {
    this.dispatch = dispatch;
  }

  void updateDispatch(RequestDispatch dispatch) {
    this.dispatch = dispatch;
    if (dispatch != null) {
      if (hasPolledChildren()) {
        polledChildren = false;  // Must refresh children after dispatch change
        requestChildNodes().then((node, status) -> {
          if (status == Request.Status.RESOLVED)
            for (Node child : children)
              child.updateDispatch(dispatch);
        });
      }
      if (hasStructureSubscription) {
        dispatch.subscribeToNodeStructure(this);
      }
      if (!valueListenerFsMap.isEmpty()) {
        dispatch.subscribeToNodeValues(this, Collections.max(valueListenerFsMap.values()));
      }
      notifySubtreeChanged(this, SubtreeChangeType.eSubscribedNodeReconnected);
    } else {
      for (Node child : children)
        child.updateDispatch(null);
    }
  }

  void setNodeID(int nodeID) {
    this.nodeID = nodeID;
  }

  void setParent(Node parent) {
    this.parent = parent;
  }
  
  void setValue(Variant variant) {
    this.value = variant;
    for (ValueListener listener : valueListenerFsMap.keySet()) {
      listener.valueChanged(variant);
    }
    for (ValueListener listener : singleListeners) {
      listener.valueChanged(variant);
    }
    singleListeners.clear();
  }
  
  boolean isRoot() {
    return nodeType == StudioAPI.CDPNodeType.CDP_SYSTEM;
  }
  
  ConnectionData getConnectionData() {
    return connectionData;
  }

  void setConnectionData(ConnectionData connectionData) {
    this.connectionData = connectionData;
  }
  
  List<Node> getChildList() {
    return children;
  }

  @Override
  public String toString() {
    return "Node(" + nodeID + ", " + name + ")\n" + children;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    Node node = (Node) o;

    return nodeID == node.nodeID;
  }

  @Override
  public int hashCode() {
    return nodeID;
  }
}
