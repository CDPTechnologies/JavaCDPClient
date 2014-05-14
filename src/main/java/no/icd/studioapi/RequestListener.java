/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

/**
 * Callback interface that can be optionally set for Request objects.
 * @author kpu@icd.no
 */
public interface RequestListener {
  
  /**
   * Called back when a request has been resolved or failed.
   * @param node
   * @param status
   */
  public void requestComplete(Node node, Request.Status status);

}
