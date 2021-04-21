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
   *   <li>Handle authentication errors</li>
   * </ul>
   *
   * Here is a sample implementation:
   * <pre>
   * {@code
   *     public void credentialsRequested(AuthRequest request) {
   *         if (request.getAuthResult().getCode() == CREDENTIALS_REQUIRED) {
   *             request.accept(AuthResponse.password(user, password));
   *         } else if (request.getAuthResult().getCode() == NEW_PASSWORD_REQUIRED) {
   *             request.accept(AuthResponse.newPassword(user, password, "NewPassword"));
   *         } else {
   *             System.out.println("Authentication failed: " + request.getAuthResult());
   *             request.reject(); // Or retry with different credentials
   *         }
   *     }
   * </pre>
   */
  default void credentialsRequested(AuthRequest request) {
    throw new UnsupportedOperationException("Please implement credentialsRequested() method to connect to "
        + request.getApplicationName());
  }

  /**
   * Called by the Client before and after authentication. Implementation must call either
   * accept() or reject() on the request either immediately or asynchronously. Features:
   *
   * <ul>
   *   <li>
   *     Accept or reject connection to an application. Optionally check for a valid certificate using
   *     {@link AuthRequest#getTlsCertificates()}
   *   </li>
   *   <li>
   *     View the result of successful authentication attempts. Optionally handle
   *     {@link AuthRequest.AuthResultCode#NEW_PASSWORD_REQUIRED}
   *   </li>
   * </ul>
   */
  default void acceptanceRequested(AuthRequest request) {
    request.accept();
  }

}
