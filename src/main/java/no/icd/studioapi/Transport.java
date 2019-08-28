/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

/** 
 * The Transport class wraps the WebSocket connection and writes all received
 * data into a thread-safe buffer queue. This is picked up by IOHandler for
 * data serialization.
 */
class Transport extends org.java_websocket.client.WebSocketClient {
  
  enum State {
    IDLE,
    CONNECTED,
    DROPPED
  }
  
  private BlockingQueue<byte[]> queue;
  private State state;
  
  /** Create a transport with an URI and received data queue. */
  Transport(URI serverURI, BlockingQueue<byte[]> queue) {
    super(serverURI, new org.java_websocket.drafts.Draft_17());
    this.queue = queue;
    this.state = State.IDLE;
  }
  
  /** Check if the socket is disconnected or failed. */
  State getState() {
    return state;
  }
  
  @Override
  public void onOpen(org.java_websocket.handshake.ServerHandshake arg0) {
    state = State.CONNECTED;
//    System.err.println("Connection to " + this.uri + " established.");
  }

  @Override
  public void onClose(int arg0, String arg1, boolean arg2) {
    state = State.DROPPED;
//    System.err.println("Connection to " + this.uri + " closed.");
  }

  /** All thrown exceptions propagate here. */
  @Override
  public void onError(Exception e) {
    state = State.DROPPED;
  }
  
  @Override
  public void onMessage(ByteBuffer buf) {
    queue.add(buf.array().clone()); // TODO is clone neccessary? costly even?
  }
  
  /** Unused but needs to be defined for java-websocket. */
  @Override
  public void onMessage(String s) {
    System.out.println("Got server message: " + s);
  }

}
