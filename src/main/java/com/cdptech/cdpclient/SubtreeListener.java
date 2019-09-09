package com.cdptech.cdpclient;

/**
 * Callback interface for a subtree change listener.
 */
public interface SubtreeListener {
  
  /** 
   * Called when a remote structure change affects the subscribed subtree.
   */
  public void subtreeChanged(Node changedNode, SubtreeChangeType changeType);
  
}
