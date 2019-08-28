/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

import java.util.Queue;

public class URIRequest extends Request {

  private Queue<String> tokens;

  public URIRequest(Queue<String> tokens) {
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
