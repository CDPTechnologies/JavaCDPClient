package no.icd.studioapi;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

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
  private List<Node> children;
  private Node parent;
  private boolean polledChildren;
  private Variant value;
  private RequestDispatch dispatch;
  private ConnectionData connectionData = null;
  
  private PropertyChangeSupport changes = new PropertyChangeSupport(this);
  
  Node(int id, CDPNodeType ntype, CDPValueType vtype, String name) {
    this.nodeID = id;
    this.nodeType = ntype;
    this.valueType = vtype;
    this.name = name;
    this.children = new ArrayList<Node>();
    this.polledChildren = false;
  }
  
  /* API methods */
  
  public Request requestChildNodes() {
    System.out.println("dispatch " + dispatch);
    return dispatch.requestChildrenForNode(this);
  }
  
  public void subscribeToValueChanges(double fs) {
    dispatch.subscribeToNodeValues(this, fs);
  }
  
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    changes.addPropertyChangeListener(listener);
  }
  
  public int getChildCount() {
    return children.size();
  }
  
  public Node getCachedChild(int n) {
    return children.get(n);
  }
  
  public CDPNodeType getNodeType() {
    return nodeType;
  }
  
  public CDPValueType getValueType() {
    return valueType;
  }
  
  public String getName() {
    return name;
  }
  
  public String getTypeName() {
    return typeName;
  }
  
  public String getLongName() {
    if (parent == null || parent.isRoot())
      return name;
    return parent.getLongName() + "." + name;
  }

  public boolean hasPolledChildren() {
    return polledChildren;
  }
  
  /* Utility methods */
  
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
    changes.firePropertyChange("value", this.value, variant);
    this.value = variant;
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
