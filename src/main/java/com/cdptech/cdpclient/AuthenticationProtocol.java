/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.AuthResponse;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Map;

class AuthenticationProtocol implements Protocol {

  private Authenticator authenticator = new Authenticator();
  private Transport transport;
  private Runnable finishedCallback;
  private String challenge;

  AuthenticationProtocol(Transport transport, Runnable finishedCallback) {
    this.transport = transport;
    this.finishedCallback = finishedCallback;
  }

  @Override
  public void parse(byte[] buf) {
    try {
      authenticator.updateUserAuthResult(AuthResponse.parseFrom(buf));
      // The server asks for an EncryptedPassword response after the PasswordHash round. Answer it from
      // the cached credentials.
      StudioAPI.AuthRequest reissue = authenticator.encryptedPasswordReissueRequest(challenge);
      if (reissue != null) {
        transport.send(reissue.toByteArray());
      } else {
        finishedCallback.run();
      }
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  void authenticate(String challenge, Map<String, String> data) {
    this.challenge = challenge;
    StudioAPI.AuthRequest authMessage = authenticator.createAuthMessage(challenge, data);
    if (authMessage == null) {
      finishedCallback.run();
    } else {
      transport.send(authMessage.toByteArray());
    }
  }

  AuthRequest.UserAuthResult getUserAuthResult() {
    return authenticator.getUserAuthResult();
  }

  /** Forget the credentials cached for this attempt (call once the attempt is granted). */
  void clearCachedCredentials() {
    authenticator.clearCachedCredentials();
  }

}
