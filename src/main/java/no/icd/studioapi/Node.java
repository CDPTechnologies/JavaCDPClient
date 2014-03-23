package no.icd.studioapi;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import no.icd.studioapi.proto.Studioapi.CDPNodeType;
import no.icd.studioapi.proto.Studioapi.CDPValueType;

public class Node {
  
  private int nodeID;
  private int objectHandle;
  private CDPNodeType nodeType;
  private CDPValueType valueType;
  private String name;
  private List<Node> children;
  private Node parent;
  private boolean polledChildren;
  private RequestDispatch dispatch;
  
  Node(int id, int handle, CDPNodeType ntype, CDPValueType vtype, String name) {
    this.nodeID = id;
    this.objectHandle = handle;
    this.nodeType = ntype;
    this.valueType = vtype;
    this.name = name;
    this.children = new ArrayList<Node>();
    this.setParent(null);
    this.polledChildren = false;
    this.dispatch = null;
  }
  
  /* API methods */
  
  public Request requestChildNodes() {
    System.out.println("dispatch " + dispatch);
    return dispatch.requestChildrenForNode(this);
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
  
  /** Replace a child with the same nodeID, fix parent and dispatch data */
  void replaceChild(Node child) {
    ListIterator<Node> i = children.listIterator();
    
    while (i.hasNext()) {
      Node n = i.next();
      if (n.nodeID == child.nodeID) {
        i.remove();
        i.add(child);
        child.setDispatch(n.dispatch);
        for (Node grandchild : child.children) {
          grandchild.setDispatch(n.dispatch);
        }
        child.setParent(this);
        break;
      }
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
  
  public int getChildCount() {
    return children.size();
  }
  
  public Node getChild(int n) {
    return children.get(n);
  }
  
  int getNodeID() {
    return nodeID;
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
  
  public String getLongName() {
    if (this.parent == null)
      return this.name;
    return parent.getLongName() + "." + name;
  }

  public boolean hasPolledChildren() {
    return polledChildren;
  }

  void setPolledChildren(boolean polledChildren) {
    this.polledChildren = polledChildren;
  }

  public RequestDispatch getDispatch() {
    return dispatch;
  }

  public void setDispatch(RequestDispatch dispatch) {
    this.dispatch = dispatch;
  }

  public Node getParent() {
    return parent;
  }

  void setParent(Node parent) {
    this.parent = parent;
  }
  
  @Override
  public String toString() {
    return "Node(" + nodeID + ", " + name + ")\n" + children;
  }

}
