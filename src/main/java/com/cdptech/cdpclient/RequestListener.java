/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

/**
 * Callback interface that can be optionally set for Request objects.
 */
public interface RequestListener {
  
  /**
   * Called back when a request has been resolved or failed.
   * @param node
   * @param status
   */
  public void requestComplete(Node node, Request.Status status);

}
