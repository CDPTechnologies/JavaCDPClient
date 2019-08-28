/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

/**
 * Callback interface used by IOHandler.
 */
interface IOListener {
  
  /** 
   * Called after connection init is done. 
   * @param success Denotes whether or not the connection was created.
   */
  void initReady(boolean success);
  
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
