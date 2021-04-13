/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import javax.net.ssl.SSLParameters;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

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

  private URI serverURI;
  private BlockingQueue<byte[]> queue;
  private State state;
  private Consumer<Exception> onError;
  private BiConsumer<URI, SSLParameters> socketParameterHandler;

  /** Create a transport with an URI and received data queue. */
  Transport(URI serverURI, BlockingQueue<byte[]> queue, Consumer<Exception> onError) {
    super(serverURI);
    this.serverURI = serverURI;
    this.queue = queue;
    this.state = State.IDLE;
    this.onError = onError;
  }

  void setSocketParameterHandler(BiConsumer<URI, SSLParameters> socketParameterHandler) {
    this.socketParameterHandler = socketParameterHandler;
  }
  
  /** Check if the socket is disconnected or failed. */
  State getState() {
    return state;
  }
  
  @Override
  public void onOpen(org.java_websocket.handshake.ServerHandshake arg0) {
    state = State.CONNECTED;
  }

  @Override
  public void onClose(int arg0, String arg1, boolean arg2) {
    state = State.DROPPED;
  }

  /** All thrown exceptions propagate here. */
  @Override
  public void onError(Exception e) {
    onError.accept(e);
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

  @Override
  protected void onSetSSLParameters(SSLParameters sslParameters) {
    if (socketParameterHandler != null) {
      socketParameterHandler.accept(serverURI, sslParameters);
    }
  }

}
