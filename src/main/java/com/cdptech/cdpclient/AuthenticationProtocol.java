/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;
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
  private AuthRequest.UserAuthResult userAuthResult;

  AuthenticationProtocol(Transport transport, Runnable finishedCallback) {
    this.transport = transport;
    this.finishedCallback = finishedCallback;

    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }

    userAuthResult = new AuthRequest.UserAuthResult();
    userAuthResult.setCode(AuthRequest.AuthResultCode.CREDENTIALS_REQUIRED);
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
    userAuthResult = new AuthRequest.UserAuthResult();
    userAuthResult.setCode(getResultCode());
    userAuthResult.setText(getResultText());
    userAuthResult.setAdditionalCredentials(getAdditionalChallenges());
  }

  void authenticate(String challenge, Map<String, String> data) {
    if (!data.containsKey(AuthRequest.USER)) {
      notifyOfMissingUserName();
      return;
    }

    String user = data.get(AuthRequest.USER);
    StudioAPI.AuthRequest.Builder authRequest = StudioAPI.AuthRequest.newBuilder().setUserId(user);
    if (data.containsKey(AuthRequest.PASSWORD)) {
      addPasswordResponse(challenge, data, authRequest, user);
    }
    if (data.containsKey(AuthRequest.NEW_PASSWORD)) {
      addNewPasswordResponse(data, authRequest, user);
    }
    transport.send(authRequest.build().toByteArray());
  }

  private void addPasswordResponse(String challenge, Map<String, String> data, StudioAPI.AuthRequest.Builder authRequest, String user) {
    String password = data.get(AuthRequest.PASSWORD);
    ChallengeResponse challengeResponse = ChallengeResponse.newBuilder()
        .setType("PasswordHash")
        .setResponse(ByteString.copyFrom(challengeHash(challenge, passwordHash(user, password))))
        .build();
    authRequest.addChallengeResponse(challengeResponse);
  }

  private void addNewPasswordResponse(Map<String, String> data, StudioAPI.AuthRequest.Builder authRequest, String user) {
    String password = data.get(AuthRequest.NEW_PASSWORD);
    ChallengeResponse challengeResponse = ChallengeResponse.newBuilder()
        .setType("NewPasswordHash")
        .setResponse(ByteString.copyFrom(passwordHash(user, password)))
        .build();
    authRequest.addChallengeResponse(challengeResponse);
  }

  private void notifyOfMissingUserName() {
    userAuthResult = new AuthRequest.UserAuthResult();
    userAuthResult.setCode(AuthRequest.AuthResultCode.USERNAME_REQUIRED);
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

  AuthRequest.UserAuthResult getUserAuthResult() {
    return userAuthResult;
  }

  private AuthRequest.AuthResultCode getResultCode() {
    switch (authMessage.getResultCode()) {
      case eUnknown:
        return AuthRequest.AuthResultCode.UNKNOWN;
      case eGranted:
        return AuthRequest.AuthResultCode.GRANTED;
      case eGrantedPasswordWillExpireSoon:
        return AuthRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON;
      case eNewPasswordRequired:
        return AuthRequest.AuthResultCode.NEW_PASSWORD_REQUIRED;
      case eInvalidChallengeResponse:
        return AuthRequest.AuthResultCode.INVALID_CHALLENGE_RESPONSE;
      case eAdditionalResponseRequired:
        return AuthRequest.AuthResultCode.ADDITIONAL_RESPONSE_REQUIRED;
    }
    return AuthRequest.AuthResultCode.UNKNOWN;
  }

  private String getResultText() {
    return authMessage.getResultText();
  }

  private List<AuthRequest.Credential> getAdditionalChallenges() {
    List<AuthRequest.Credential> challenges = new ArrayList<>();
    for (StudioAPI.AdditionalChallengeResponseRequired item : authMessage.getAdditionalChallengeResponseRequiredList()) {
      AuthRequest.Credential c = new AuthRequest.Credential();
      c.setType(item.getType());
      c.setPrompt(item.getPrompt());
      for (StudioAPI.AdditionalChallengeResponseRequired.Parameter parameter : item.getParameterList()) {
        c.getParameters().put(parameter.getName(), parameter.getValue());
      }
    }
    return challenges;
  }

}
