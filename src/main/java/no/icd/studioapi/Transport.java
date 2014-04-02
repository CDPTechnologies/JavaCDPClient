package no.icd.studioapi;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;

public class Transport extends org.java_websocket.client.WebSocketClient {
  
  private BlockingQueue<byte[]> queue;
  
  /** Create a transport with an URI and received data queue. */
  Transport(URI serverURI, BlockingQueue<byte[]> queue) {
    super(serverURI, new org.java_websocket.drafts.Draft_17());
    this.queue = queue;
  }
  
  @Override
  public void onOpen(org.java_websocket.handshake.ServerHandshake arg0) {
    System.err.println("Connection to " + this.uri + " established.");
  }

  @Override
  public void onClose(int arg0, String arg1, boolean arg2) {
    System.err.println("Connection to " + this.uri + " closed.");
  }

  /** All thrown exceptions propagate here. */
  @Override
  public void onError(Exception e) {
    // TODO If a userspace callback throws, it is caught by Java-WebSocket
    // and forwarded here. We should probably let the user know they were
    // a bad citizen.
    e.printStackTrace();
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
