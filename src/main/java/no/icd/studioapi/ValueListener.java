/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

/** Callback interface for registering to Node values. */
public interface ValueListener {
  
  /** Called when the remote value of @a node has changed. */
  public void valueChanged(Node node);

}
