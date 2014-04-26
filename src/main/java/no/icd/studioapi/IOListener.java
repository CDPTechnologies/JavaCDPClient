package no.icd.studioapi;

interface IOListener {
  
  /**
   * Called when IOHandler has received a node from the server.
   * @param node Received node.
   */
  void nodeReceived(Node node);
  
  /**
   * Called when a remote node value has been received from the server.
   * @param nodeID The value owner's nodeID
   * @param value  The variant value that was received.
   */
  void valueReceived(int nodeID, Variant value);

}
