package no.icd.studioapi;

import no.icd.studioapi.proto.StudioAPI.StructureChangeType;

/** 
 * Data container for a structure change event. 
 * @author kpu@icd.no
 */
public class StructureChange {

  /** Get the type of the change event. */
  public StructureChangeType getChangeType() {
    return changeType;
  }
  
  /** Get the author of the change. */
  public String getChangeAuthor() {
    return author;
  }
  
  /** Get the relative timestamp of the change. */
  public double getTimestamp() {
    return timestamp;
  }
  
  /** Get the node which was added or removed. */
  public Node getChangedNode() {
    return changedNode;
  }
  
  private StructureChangeType changeType;
  private String author;
  private double timestamp;
  private Node changedNode;
  private int changedNodeID;
  
  /** StructureChange events are constructed internally. */
  StructureChange(
      StructureChangeType type,
      String author,
      double timestamp) {
    this.changeType = type;
    this.author = author;
    this.timestamp = timestamp;
    this.changedNode = null;
  }
  
  /** Changed node is set after event is found in the cache. */
  void setChangedNode(Node node) {
    changedNode = node;
  }

  public int getChangedNodeID() {
    return changedNodeID;
  }

  public void setChangedNodeID(int changedNodeID) {
    this.changedNodeID = changedNodeID;
  }
  
  
}
