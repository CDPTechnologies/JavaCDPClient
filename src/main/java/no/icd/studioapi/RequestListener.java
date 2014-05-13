/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

public interface RequestListener {
  
  /**
   * Called back when a request has been resolved or failed.
   * @param node
   * @param status
   */
  public void requestComplete(Node node, Request.Status status);

}
