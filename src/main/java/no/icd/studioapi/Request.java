/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

/** 
 * Request object used for tracking structure requests.
 */
public class Request {
  
  /** Describes the status of the request. */
  public enum Status {
    PENDING,
    RESOLVED,
    ERROR
  }
  
  /** Get the status of the request. */
  public Status getStatus() {
    return status;
  }
  
  /** Get the node resolved. Returns null if request is pending or in error. */
  public Node getNode() {
    return node;
  }
  
  /** 
   * Set the optional callback listener. If the request was already resolved,
   * the callback is called immediately.
   */
  public void then(RequestListener listener) {
    this.listener = listener;
    if (status != Status.PENDING) {
      listener.requestComplete(node, status);
    }
  }
  
  private Status status;
  private RequestListener listener;
  private Node node;
  private int expectedNodeID;

  Request() {
    this.status = Status.PENDING;
  }
  
  void setStatus(Status status) {
    this.status = status;
    if (listener != null) {
      listener.requestComplete(node, status);
    }
  }

  void offer(Node node) {
    if (node.getNodeID() == getExpectedNodeID()) {
      this.node = node;
      setStatus(Status.RESOLVED);
    }
  }

  void setNode(Node node) {
    this.node = node;
  }

  int getExpectedNodeID() {
    return expectedNodeID;
  }

  void setExpectedNodeID(int expectedNodeID) {
    this.expectedNodeID = expectedNodeID;
  }

}
