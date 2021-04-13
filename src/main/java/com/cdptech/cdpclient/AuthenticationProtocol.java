/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.AuthRequest;
import com.cdptech.cdpclient.proto.StudioAPI.AuthRequest.ChallengeResponse;
import com.cdptech.cdpclient.proto.StudioAPI.AuthResponse;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class AuthenticationProtocol implements Protocol {

  private AuthResponse authMessage;
  private Transport transport;
  private Runnable finishedCallback;
  private MessageDigest digest;
  private AuthenticationRequest.UserAuthResult userAuthResult;

  AuthenticationProtocol(Transport transport, Runnable finishedCallback) {
    this.transport = transport;
    this.finishedCallback = finishedCallback;

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    userAuthResult = new AuthenticationRequest.UserAuthResult();
    userAuthResult.setCode(AuthenticationRequest.AuthResultCode.CREDENTIALS_REQUIRED);
    userAuthResult.setText("Credentials required");
  }

  @Override
  public void parse(byte[] buf) {
    try {
      authMessage = AuthResponse.parseFrom(buf);
      updateUserAuthResult();
      finishedCallback.run();
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  private void updateUserAuthResult() {
    userAuthResult = new AuthenticationRequest.UserAuthResult();
    userAuthResult.setCode(getResultCode());
    userAuthResult.setText(getResultText());
    userAuthResult.setAdditionalCredentials(getAdditionalChallenges());
  }

  void authenticate(String challenge, Map<String, String> data) {
    if (!data.containsKey(AuthenticationRequest.USER)) {
      notifyOfMissingUserName();
      return;
    }

    String user = data.get(AuthenticationRequest.USER);
    AuthRequest.Builder authRequest = AuthRequest.newBuilder().setUserId(user);
    if (data.containsKey(AuthenticationRequest.PASSWORD)) {
      addPasswordResponse(challenge, data, authRequest, user);
    }
    if (data.containsKey(AuthenticationRequest.NEW_PASSWORD)) {
      addNewPasswordResponse(data, authRequest, user);
    }
    transport.send(authRequest.build().toByteArray());
  }

  private void addPasswordResponse(String challenge, Map<String, String> data, AuthRequest.Builder authRequest, String user) {
    String password = data.get(AuthenticationRequest.PASSWORD);
    ChallengeResponse challengeResponse = ChallengeResponse.newBuilder()
        .setType("PasswordHash")
        .setResponse(ByteString.copyFrom(challengeHash(challenge, passwordHash(user, password))))
        .build();
    authRequest.addChallengeResponse(challengeResponse);
  }

  private void addNewPasswordResponse(Map<String, String> data, AuthRequest.Builder authRequest, String user) {
    String password = data.get(AuthenticationRequest.NEW_PASSWORD);
    ChallengeResponse challengeResponse = ChallengeResponse.newBuilder()
        .setType("NewPasswordHash")
        .setResponse(ByteString.copyFrom(passwordHash(user, password)))
        .build();
    authRequest.addChallengeResponse(challengeResponse);
  }

  private void notifyOfMissingUserName() {
    userAuthResult = new AuthenticationRequest.UserAuthResult();
    userAuthResult.setCode(AuthenticationRequest.AuthResultCode.USERNAME_REQUIRED);
    userAuthResult.setText("Authentication failed: username not specified");
    finishedCallback.run();
  }

  private byte[] passwordHash(String user, String password) {
    return digest.digest((user.toLowerCase() + ":" + password).getBytes());
  }

  private byte[] challengeHash(String challenge, byte[]  data) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try {
      outputStream.write(challenge.getBytes());
      outputStream.write(':');
      outputStream.write(data);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return digest.digest(outputStream.toByteArray());
  }

  AuthenticationRequest.UserAuthResult getUserAuthResult() {
    return userAuthResult;
  }

  private AuthenticationRequest.AuthResultCode getResultCode() {
    switch (authMessage.getResultCode()) {
      case eUnknown:
        return AuthenticationRequest.AuthResultCode.UNKNOWN;
      case eGranted:
        return AuthenticationRequest.AuthResultCode.GRANTED;
      case eGrantedPasswordWillExpireSoon:
        return AuthenticationRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON;
      case eNewPasswordRequired:
        return AuthenticationRequest.AuthResultCode.NEW_PASSWORD_REQUIRED;
      case eInvalidChallengeResponse:
        return AuthenticationRequest.AuthResultCode.INVALID_CHALLENGE_RESPONSE;
      case eAdditionalResponseRequired:
        return AuthenticationRequest.AuthResultCode.ADDITIONAL_RESPONSE_REQUIRED;
    }
    return AuthenticationRequest.AuthResultCode.UNKNOWN;
  }

  private String getResultText() {
    return authMessage.getResultText();
  }

  private List<AuthenticationRequest.Credential> getAdditionalChallenges() {
    List<AuthenticationRequest.Credential> challenges = new ArrayList<>();
    for (StudioAPI.AdditionalChallengeResponseRequired item : authMessage.getAdditionalChallengeResponseRequiredList()) {
      AuthenticationRequest.Credential c = new AuthenticationRequest.Credential();
      c.setType(item.getType());
      c.setPrompt(item.getPrompt());
      for (StudioAPI.AdditionalChallengeResponseRequired.Parameter parameter : item.getParameterList()) {
        c.getParameters().put(parameter.getName(), parameter.getValue());
      }
    }
    return challenges;
  }

}
