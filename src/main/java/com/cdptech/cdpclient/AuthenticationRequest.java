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

public interface AuthenticationRequest {

  String USER = "User";
  String PASSWORD = "Password";
  String NEW_PASSWORD = "NewPassword";
  // TODO: add key-based authentication when CDP starts supporting it

  /** The request can ask for credentials or simply whether to connect to the found application */
  enum RequestType {
    /** Sent by StudioAPI::Client when new application TLS or plain TCP connection is established */
    APPLICATION_ACCEPTANCE,
    /** Sent by StudioAPI::Client when application requires credentials */
    CREDENTIALS,
    /**
     * Sent by StudioAPI::Client when application connection has been successfully authenticated
     * and it now asks for permission to continue with StudioAPI handshake.
     * This request can be used for:
     * <ul>
     *   <li>cache validated credentials for sibling application connections</li>
     *   <li>async prefetch model files, needed for structure, etc</li>
     * </ul>
     */
    HANDSHAKE_ACCEPTANCE
  }

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
    private AuthResultCode code = AuthResultCode.CREDENTIALS_REQUIRED;
    private String text;
    private List<Credential> additionalCredentials = new ArrayList<>();
  }

  @Data
  class CDPVersion {
    private final int major;
    private final int minor;
  }

  /** Additional information about the found application. */
  interface Application {
    /** System name (from Hello message sent by StudioAPI application connected) */
    String getSystemName();
    /** Application name (from Hello message sent by StudioAPI application connected) */
    String getApplicationName();
    /** Application StudioAPI hostname or IP address and port */
    URI getURI();
    /** Application CDP version (major,minor) */
    CDPVersion getCDPVersion();
    /** Application certificates */
    Certificate[] getCertificates();
    /** State of the authentication  */
    UserAuthResult getUserAuthResult();
  }

  /** The request can ask for credentials or simply whether to connect to the found application */
  RequestType getType();
  /** Additional information about the found application. */
  Application getApplication();

  /** Method to call to accept the application and provide requested credentials */
  void accept(Map<String, String> data);
  /** Method to call to reject the application */
  void reject();

  /** Method to call to accept the application or handshake result. */
  default void accept() {
    accept(new HashMap<>());
  }
}
