/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.net.URI;

/**
 * StudioAPI Client main event handler interface.
 */
public interface NotificationListener {

  /** Called when all connections have been created and requests can be made. */
  void clientReady(Client client);
  
  /**
   * Called when all connections are lost and the client is reset.
   *
   * Will not be called if automatic reconnect is enabled.
   */
  void clientClosed(Client client);

  /**
   * Notifies of errors when connecting to a CDP application. Note that this will not close the client
   * if there are multiple CDP applications in the system.
   */
  default void connectionError(URI serverURI, Exception e) {}

  /**
   * Called by the Client when authentication is needed.
   * Implementation must call either accept(data) or reject() on the request either immediately or asynchronously.
   */
  default void authenticationRequested(AuthenticationRequest request) {
    if (request.getType() == AuthenticationRequest.RequestType.APPLICATION_ACCEPTANCE
        || request.getType() == AuthenticationRequest.RequestType.HANDSHAKE_ACCEPTANCE) {
      request.accept();
    } else {
      throw new UnsupportedOperationException("Please implement authenticationRequested() method to connect to "
          + request.getApplication().getApplicationName());
    }
  }

}
