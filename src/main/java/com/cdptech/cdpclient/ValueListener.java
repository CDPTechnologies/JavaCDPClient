/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

/** 
 * Callback interface for registering to Node values. 
 * @author kpu@icd.no
 */
public interface ValueListener {
  
  /** Called when the remote value of @a node has changed. */
  public void valueChanged(Variant value);

}
