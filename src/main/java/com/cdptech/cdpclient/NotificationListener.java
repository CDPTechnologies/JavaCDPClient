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
   *         } else if (request.getAuthResult().getCode() == REAUTHENTICATION_REQUIRED) {
   *             // Re-authentication is requested when the connection has been idle for too long.
   *             // It is strongly recommended to NOT use previously cached credentials. Prompt the user for a password.
   *             request.accept(AuthResponse.password(user, password));
   *         } else if (request.getAuthResult().getCode() == NEW_PASSWORD_REQUIRED) {
   *             request.accept(AuthResponse.newPassword(user, password, "NewPassword"));
   *         } else {
   *             System.out.println("Authentication failed: " + request.getAuthResult());
   *             request.reject(); // Or retry with different credentials
   *         }
   *     }
   * }
   * </pre>
   *
   *  Note, that when request.getAuthResult().getCode() == REAUTHENTICATION_REQUIRED, then user should be notified that the
   *  server requires re-authentication (e.g. because of being idle), and prompted for user approval to re-authenticate
   *  (must not silently send cached credentials). The idle timeout length is specified by
   *  {@link AuthRequest#getIdleLockoutPeriod()}.
   */
  default void credentialsRequested(AuthRequest request) {
    throw new UnsupportedOperationException("Please implement credentialsRequested() method to connect to "
        + request.getApplicationName());
  }

  /**
   * Called by the Client before authentication when either a TLS or a plain TCP connection is established
   * with a new application. Implementation should check {@link AuthRequest#getSystemUseNotification()} and
   * either call either accept() or reject() on the request either immediately or asynchronously.
   * Optionally save the certificate using
   * {@link AuthRequest#getPeerCertificates()} and verify it in future connections to detect
   * man-in-the-middle attacks.
   */
  default void applicationAcceptanceRequested(AuthRequest request) {
    request.accept();
  }

  /**
   * Sent by the Client when an application connection has been successfully authenticated
   * and it now asks for permission to continue with the StudioAPI handshake.
   * This request can be used to:
   * <ul>
   *   <li>Cache validated credentials for sibling application connections.</li>
   *   <li>
   *     View the result of successful authentication attempts. Optionally handle
   *     {@link AuthRequest.AuthResultCode#GRANTED_PASSWORD_WILL_EXPIRE_SOON} and notify the user.
   *   </li>
   * </ul>
   */
  default void handshakeAcceptanceRequested(AuthRequest request) {
    request.accept();
  }

}
