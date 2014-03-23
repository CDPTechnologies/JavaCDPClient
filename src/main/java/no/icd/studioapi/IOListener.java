package no.icd.studioapi;

public interface IOListener {
  
  /**
   * Called when IOHandler has received a node from the server.
   * @param node
   */
  void nodeReceived(Node node);
  
  /**
   * Called when a remote node value has been received from the server.
   * @param nodeID
   */
  void valueReceived(int nodeID);

}
