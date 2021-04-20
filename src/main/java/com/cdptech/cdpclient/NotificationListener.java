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
   * Called by the Client when authentication is needed. Implementation must call either
   * accept(data) or reject() on the request either immediately or asynchronously. Features:
   *
   * <ul>
   *   <li>Authenticate using username and password</li>
   *   <li>Set a new password when the previous has expired</li>
   *   <li>View the result of authentication attempts and handle errors</li>
   *   <li>
   *     Accept or reject connection to an application. Optionally check for a valid certificate using
   *     {@link AuthenticationRequest.Application#getCertificates()}
   *   </li>
   * </ul>
   *
   * Here is a sample implementation:
   * <pre>
   * {@code
   *     public void authenticationRequested(AuthenticationRequest request) {
   *         Application app = request.getApplication();
   *         AuthResultCode code = app.getUserAuthResult().getCode();
   *         Map<String, String> data = new HashMap<>();
   *         if (request.getType() == CREDENTIALS) {
   *             if (code == CREDENTIALS_REQUIRED || code == NEW_PASSWORD_REQUIRED) {
   *                 data.put(AuthenticationRequest.USER, "MyUserName");
   *                 data.put(AuthenticationRequest.PASSWORD, "MyPassword");
   *                 if (code == NEW_PASSWORD_REQUIRED) // The existing password of the user has expired
   *                     data.put(AuthenticationRequest.NEW_PASSWORD, "MyNewPassword");
   *                 request.accept(data);
   *             } else {
   *                 System.out.println("Authentication of " + app.getApplicationName()
   *                       + " failed: " + app.getUserAuthResult());
   *                 request.reject();
   *             }
   *         } else { // either authentication was not needed or provided credentials were correct
   *             request.accept(data); // Or request.reject() to cancel the connection
   *         }
   *     }
   * }
   * </pre>
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
