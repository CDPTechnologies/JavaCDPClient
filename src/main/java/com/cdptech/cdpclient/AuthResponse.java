/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.util.HashMap;
import java.util.Map;

/** Convenience methods to help fill {@link AuthRequest#accept(Map)} */
public class AuthResponse {

  /** Authenticates the user using a password */
  public static Map<String, String> password(String user, String password) {
    Map<String, String> data = new HashMap<>();
    data.put(AuthRequest.USER, user);
    data.put(AuthRequest.PASSWORD, password);
    return data;
  }

  /** Handles {@link AuthRequest.AuthResultCode#NEW_PASSWORD_REQUIRED} */
  public static Map<String, String> newPassword(String user, String password, String newPassword) {
    Map<String, String> data = new HashMap<>();
    data.put(AuthRequest.USER, user);
    data.put(AuthRequest.PASSWORD, password);
    data.put(AuthRequest.NEW_PASSWORD, newPassword);
    return data;
  }
}
