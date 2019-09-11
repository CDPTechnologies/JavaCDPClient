/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.util.Queue;

class URIRequest extends Request {

  private Queue<String> tokens;

  URIRequest(Queue<String> tokens) {
    this.tokens = tokens;
  }

  @Override
  void offer(Node node) {
    if (node.getNodeID() != getExpectedNodeID())
      return;

    Node child = node.getCachedChild(tokens.remove());
    if (child != null) {
      if (tokens.isEmpty()) {
        setNode(child);
        setStatus(Status.RESOLVED);
      } else {
        setExpectedNodeID(child.getNodeID());
        child.requestChildNodes();
      }
    } else {
      setStatus(Status.ERROR);
    }
  }
}
