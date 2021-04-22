/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import lombok.Data;

import java.net.URI;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface AuthRequest {

  String USER = "User";
  String PASSWORD = "Password";
  String NEW_PASSWORD = "NewPassword";
  // TODO: add key-based authentication when CDP starts supporting it

  enum AuthResultCode {
    UNKNOWN,
    CREDENTIALS_REQUIRED,
    // OK results:
    GRANTED, // is also set when no authentication required
    GRANTED_PASSWORD_WILL_EXPIRE_SOON, // user should be notified about coming soon password expiry with suggestion to set a new password ASAP

    // negative results:
    /**
     * Password was OK but is expired, so new AuthenticationRequest with additional response with
     * new password hash is required, and new password complexity rules should be read from
     * additionalCredentials parameters
     */
    NEW_PASSWORD_REQUIRED,
    INVALID_CHALLENGE_RESPONSE,
    ADDITIONAL_RESPONSE_REQUIRED,
    // results, that are sent by the client (not by the remote server)
    USERNAME_REQUIRED
  }

  @Data
  class Credential {
    private String type;
    private String prompt;
    private Map<String, String> parameters = new HashMap<>();
  }

  @Data
  class UserAuthResult {
    private AuthResultCode code;
    private String text;
    private List<Credential> additionalCredentials = new ArrayList<>();
  }

  @Data
  class CDPVersion {
    private final int major;
    private final int minor;
  }

  /** System name (from Hello message sent by StudioAPI application connected) */
  String getSystemName();
  /** Application name (from Hello message sent by StudioAPI application connected) */
  String getApplicationName();
  /** Application StudioAPI hostname or IP address and port */
  URI getServerURI();
  /** Application CDP version (major,minor) */
  CDPVersion getCDPVersion();
  /** Application certificates */
  Certificate[] getPeerCertificates();
  /** State of the authentication  */
  UserAuthResult getAuthResult();

  /**
   * Method to call to accept the application and provide requested credentials.
   * <p>Example: {@code accept(AuthResponse.password(user, pwd));}</p>
   *
   * @see AuthResponse
   */
  void accept(Map<String, String> data);
  /** Method to call to reject the application */
  void reject();

  /** Method to call to accept the application or handshake result. */
  default void accept() {
    accept(new HashMap<>());
  }
}
