package no.icd.studioapi;

/** 
 * Callback interface for a structure change listener. 
 * @author kpu@icd.no
 */
public interface StructureListener {
  
  /** 
   * Called when a remote structure change affects the cache of the client.
   * @param node The node which was subcribed to.
   * @param event Detailed structure change event.
   */
  public void structureChanged(Node node, StructureChange event);
  
}
