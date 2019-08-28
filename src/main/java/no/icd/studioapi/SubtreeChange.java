/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

/** 
 * Data container for a structure change event.
 */
public class SubtreeChange {

  /** Get the type of the change event. */
  public SubtreeChangeType getChangeType() {
    return changeType;
  }
  
  /** Get the node which was added or removed. */
  public Node getChangedNode() {
    return changedNode;
  }

  private SubtreeChangeType changeType;
  private Node changedNode;
  
  /** SubtreeChange events are constructed internally. */
  SubtreeChange(
      SubtreeChangeType type,
      Node changedNode) {
    this.changeType = type;
    this.changedNode = changedNode;
  }
  
  /** Changed node is set after event is found in the cache. */
  void setChangedNode(Node node) {
    changedNode = node;
  }

  
  
}
