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

  AuthenticationProtocol(Transport transport, Runnable finishedCallback) {
    this.transport = transport;
    this.finishedCallback = finishedCallback;
  }

  @Override
  public void parse(byte[] buf) {
    try {
      authenticator.updateUserAuthResult(AuthResponse.parseFrom(buf));
      finishedCallback.run();
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  void authenticate(String challenge, Map<String, String> data) {
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

}
