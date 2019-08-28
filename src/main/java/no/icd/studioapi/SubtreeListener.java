package no.icd.studioapi;

/**
 * Callback interface for a structure change listener.
 */
public interface SubtreeListener {
  
  /** 
   * Called when a remote structure change affects the cache of the client.
   * @param node The node which was subscribed to.
   * @param event Detailed structure change event.
   */
  public void subtreeChanged(Node node, SubtreeChange event);
  
}
