/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

/** Describes which event changed the subtree. */
public enum SubtreeChangeType {
  /** Node has been removed from the tree. */
  eChildRemoved,
  /** A new node has been added to the tree. Also called when another CDP application comes up. */
  eChildAdded,
  /** Connection to the application which included this node has been lost. */
  eSubscribedNodeLost,
  /** Connection to the application which included this node has been restored. */
  eSubscribedNodeReconnected
}
