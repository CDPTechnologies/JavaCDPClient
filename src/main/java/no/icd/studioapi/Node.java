/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import no.icd.studioapi.proto.Studioapi.CDPNodeType;
import no.icd.studioapi.proto.Studioapi.CDPValueType;

public class Node {
  
  /** Connection data structure for Application nodes. */
  static class ConnectionData {
    
    boolean correspondsToConnection;
    String serverAddr;
    int serverPort;
    
    ConnectionData() {
      correspondsToConnection = true;
    }
    
    ConnectionData(String addr, int port) {
      correspondsToConnection = false;
      serverAddr = addr;
      serverPort = port;
    }
  }
  
  private int nodeID;
  private CDPNodeType nodeType;
  private CDPValueType valueType;
  private String name;
  private String typeName;
  private boolean isReadOnly;
  private boolean isSaveOnChange;
  private List<Node> children;
  private Node parent;
  private boolean polledChildren;
  private Variant value;
  private RequestDispatch dispatch;
  private ConnectionData connectionData = null;
  private Set<ValueListener> valueListeners;
  boolean hasValueSubscription = false;
  
  /** (asynchronous) Send a request for this node's child nodes. */
  public Request requestChildNodes() {
    return dispatch.requestChildrenForNode(this);
  }
  
  /** (asynchronous) Subscribe to this node's value changes with @a listener. */
  public void subscribeToValueChanges(double fs, ValueListener listener) {
    valueListeners.add(listener);
    if (!hasValueSubscription)
      dispatch.subscribeToNodeValues(this, fs);
  }
  
  /** Remove a previously registered value @a listener. */
  public void removeValueListener(ValueListener listener) {
    boolean success = valueListeners.remove(listener);
    if (success && valueListeners.size() == 0)
      dispatch.unsubcribeFromNodeValues(this);
  }
  
  /** Request a single value for this node. */
  public void requestValue(ValueListener listener) {
    // TODO
  }
  
  /** (asynchronous) Set the remote value of this node to @a value. */
  public void postValue(Variant value) {
    dispatch.postValueForNode(this, value);
  }
  
  /** Get the number of cached children this Node has. */
  public int getChildCount() {
    return children.size();
  }
  
  /** Get a cached child at index @a n. */
  public Node getCachedChild(int n) {
    return children.get(n);
  }
  
  /** Returns the most recent value that this node has received. */
  public Variant getCachedValue() {
    return value;
  }
  
  /** Get the node type of this Node. */
  public CDPNodeType getNodeType() {
    return nodeType;
  }
  
  /** Get the value type of this Node. */
  public CDPValueType getValueType() {
    return valueType;
  }
  
  /** Get the short name of this node. */
  public String getName() {
    return name;
  }
  
  /** Get the class name that this node represents. */
  public String getTypeName() {
    return typeName;
  }
  
  /** Get the long name of this node. */
  public String getLongName() {
    if (parent == null || parent.isRoot())
      return name;
    return parent.getLongName() + "." + name;
  }
  
  public boolean valueIsReadOnly() {
    return isReadOnly;
  }
  
  public boolean valueIsSavedOnChange() {
    return isSaveOnChange;
  }

  /** Check if the node has it's sub-structure polled. */
  public boolean hasPolledChildren() {
    return polledChildren;
  }
  
  /** Nodes are constructed by StudioAPI only. */
  Node(int id, CDPNodeType ntype, CDPValueType vtype, String name) {
    this.nodeID = id;
    this.nodeType = ntype;
    this.valueType = vtype;
    this.name = name;
    this.children = new ArrayList<Node>();
    this.polledChildren = false;
    this.value = new Variant(CDPValueType.eUNDEFINED, "", 0.0);
    this.valueListeners = new HashSet<ValueListener>();
    this.isReadOnly = false;
    this.isSaveOnChange = false;
  }
  
  /** Add a child node to this node. NodeID must be unique. */
  void addChild(Node child) {
    for (Node existingChild : children) {
      if (existingChild.nodeID == child.nodeID) {
        return;
      }
    }
    children.add(child);
    child.setParent(this);
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
  
  /* Accessor methods */
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

  public Node getParent() {
    return parent;
  }

  void setParent(Node parent) {
    this.parent = parent;
  }
  
  void setValue(Variant variant) {
    this.value = variant;
    for (ValueListener listener : valueListeners) {
      listener.valueChanged(this);
    }
  }
  
  boolean isRoot() {
    return nodeType == CDPNodeType.CDP_SYSTEM;
  }
  
  public ConnectionData getConnectionData() {
    return connectionData;
  }

  public void setConnectionData(ConnectionData connectionData) {
    this.connectionData = connectionData;
  }

  @Override
  public String toString() {
    return "Node(" + nodeID + ", " + name + ")\n" + children;
  }

}
