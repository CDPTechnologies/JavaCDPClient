package no.icd.studioapi;

public class Request {
  
  public enum Status {
    PENDING,
    RESOLVED,
    ERROR
  }
  
  private Status status;
  private RequestListener listener;
  private Node node;

  Request() {
    this.status = Status.PENDING;
    this.listener = null;
    this.node = null;
  }
  
  Status getStatus() {
    return status;
  }
  
  void setStatus(Status status) {
    this.status = status;
    if (listener != null) {
      listener.requestComplete(node, status);
    }
  }
  
  public void setListener(RequestListener listener) {
    this.listener = listener;
    if (status != Status.PENDING) {
      listener.requestComplete(node, status);
    }
  }
    
  public Node getNode() {
    return node;
  }

  void setNode(Node node) {
    this.node = node;
  }

}
