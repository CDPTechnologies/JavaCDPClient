/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.AuthRequest.ChallengeResponse;
import com.cdptech.cdpclient.proto.StudioAPI.AuthResponse;
import com.google.protobuf.ByteString;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Authenticator {

  private static final String ENCRYPTED_PASSWORD_TYPE = "EncryptedPassword";
  private static final String PASSWORD_ENCRYPTION_PUBLIC_KEY_PARAM = "PasswordEncryptionPublicKey";
  // The client encrypts with the server-supplied 2048-bit RSA public key; RSA/PKCS#1 v1.5 fits at most
  // keyBytes - 11 = 245 bytes per block, so the plaintext is chunked at that size before encryption.
  private static final int RSA_KEY_LENGTH_BITS = 2048;
  private static final int RSA_MAX_CHUNK_SIZE = RSA_KEY_LENGTH_BITS / 8 - 11;

  private AuthResponse authMessage;
  private MessageDigest digest;
  private AuthRequest.UserAuthResult userAuthResult;
  private Map<String, String> lastCredentials;

  Authenticator() {
    try {
      digest = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
    userAuthResult = new AuthRequest.UserAuthResult();
    userAuthResult.setCode(AuthRequest.AuthResultCode.CREDENTIALS_REQUIRED);
    userAuthResult.setText("Credentials required");
  }

  void updateUserAuthResult(AuthResponse authMessage) {
    this.authMessage = authMessage;
    userAuthResult = new AuthRequest.UserAuthResult();
    userAuthResult.setCode(getResultCode());
    userAuthResult.setText(getResultText());
    userAuthResult.setAdditionalCredentials(getAdditionalChallenges());
    userAuthResult.setRolesAssigned(new ArrayList<>(authMessage.getRoleAssignedList()));
  }

  StudioAPI.AuthRequest createAuthMessage(String challenge, Map<String, String> data) {
    // Cache a copy of the credentials so a subsequent EncryptedPassword challenge can be answered without
    // re-prompting; a copy because the caller owns the map and may scrub or reuse it after accept() returns.
    lastCredentials = new HashMap<>(data);
    String user = data.get(AuthRequest.USER);
    if (user == null || user.isEmpty()) {
      notifyOfMissingUserName();
      return null;
    }
    StudioAPI.AuthRequest.Builder authRequest = StudioAPI.AuthRequest.newBuilder().setUserId(user);
    String password = data.get(AuthRequest.PASSWORD);
    if (password != null && !password.isEmpty()) {
      addPasswordResponse(challenge, data, authRequest, user);
      String encryptionPublicKey = getEncryptionPublicKey();
      if (encryptionPublicKey != null) {
        addEncryptedPasswordResponse(challenge, data, authRequest, encryptionPublicKey);
      }
    }
    String newPassword = data.get(AuthRequest.NEW_PASSWORD);
    if (newPassword != null && !newPassword.isEmpty()) {
      addNewPasswordResponse(data, authRequest, user);
    }
    return authRequest.build();
  }

  /** The RSA public key (PEM) from the last AuthResponse's EncryptedPassword challenge, or null if not requested. */
  private String getEncryptionPublicKey() {
    for (AuthRequest.Credential credential : userAuthResult.getAdditionalCredentials()) {
      if (ENCRYPTED_PASSWORD_TYPE.equals(credential.getType())) {
        return credential.getParameters().get(PASSWORD_ENCRYPTION_PUBLIC_KEY_PARAM);
      }
    }
    return null;
  }

  private boolean isEncryptedPasswordRequested() {
    return getEncryptionPublicKey() != null;
  }

  /** Drop the cached credentials once the attempt is granted, so the password isn't retained for the connection's lifetime. */
  void clearCachedCredentials() {
    lastCredentials = null;
  }

  /**
   * The AuthRequest to re-send when the last AuthResponse requested an EncryptedPassword response, answering
   * the given (latest) challenge with the cached credentials, so the caller can resubmit without re-prompting;
   * null when no reissue is needed. Not capped at one round-trip.
   */
  StudioAPI.AuthRequest encryptedPasswordReissueRequest(String challenge) {
    // Reissue only when the cache can actually answer the challenge: an empty cached password would
    // rebuild a request with no responses and loop against the server instead of re-prompting the user.
    if (isEncryptedPasswordRequested() && hasCachedPassword()) {
      return createAuthMessage(challenge, lastCredentials);
    }
    return null;
  }

  private boolean hasCachedPassword() {
    if (lastCredentials == null) {
      return false;
    }
    String password = lastCredentials.get(AuthRequest.PASSWORD);
    return password != null && !password.isEmpty();
  }

  private void addEncryptedPasswordResponse(String challenge, Map<String, String> data,
      StudioAPI.AuthRequest.Builder authRequest, String publicKeyPem) {
    String password = data.get(AuthRequest.PASSWORD);
    ChallengeResponse challengeResponse = ChallengeResponse.newBuilder()
        .setType(ENCRYPTED_PASSWORD_TYPE)
        .setResponse(ByteString.copyFrom(encryptPassword(challenge, password, publicKeyPem)))
        .build();
    authRequest.addChallengeResponse(challengeResponse);
  }

  /** RSA-encrypt {@code challenge + password} with the server-supplied key, chunked to fit PKCS#1 blocks. */
  private byte[] encryptPassword(String challenge, String password, String publicKeyPem) {
    byte[] data = (challenge + password).getBytes(StandardCharsets.UTF_8);
    try {
      Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
      cipher.init(Cipher.ENCRYPT_MODE, parseRsaPublicKey(publicKeyPem));
      ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
      for (int offset = 0; offset < data.length; offset += RSA_MAX_CHUNK_SIZE) {
        int length = Math.min(RSA_MAX_CHUNK_SIZE, data.length - offset);
        encrypted.write(cipher.doFinal(data, offset, length));
      }
      return encrypted.toByteArray();
    } catch (GeneralSecurityException | IOException e) {
      throw new RuntimeException("Failed to RSA-encrypt password for EncryptedPassword authentication", e);
    }
  }

  private PublicKey parseRsaPublicKey(String pem) throws GeneralSecurityException {
    String base64 = pem.replaceAll("-----BEGIN [^-]*-----", "")
        .replaceAll("-----END [^-]*-----", "")
        .replaceAll("\\s", "");
    byte[] der = Base64.getDecoder().decode(base64);
    return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
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
    userAuthResult.setText("Username required");
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
    // A response with no result_code is a denial (fail closed): the proto field default would otherwise read
    // as eCredentialsRequired (retry), but an AuthResponse that omits the code is not an invitation to retry.
    if (!authMessage.hasResultCode())
      return AuthRequest.AuthResultCode.INVALID_CHALLENGE_RESPONSE;
    switch (authMessage.getResultCode()) {
      case eCredentialsRequired:
        return AuthRequest.AuthResultCode.CREDENTIALS_REQUIRED;
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
      case eTemporarilyBlocked:
        return AuthRequest.AuthResultCode.TEMPORARILY_BLOCKED;
      case eReauthenticationRequired:
        return AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED;
    }
    return AuthRequest.AuthResultCode.UNKNOWN;
  }

  private String getResultText() {
    return authMessage.getResultText();
  }

  private List<AuthRequest.Credential> getAdditionalChallenges() {
    List<AuthRequest.Credential> challenges = new ArrayList<>();
    for (StudioAPI.AdditionalChallengeResponseRequired item : authMessage.getAdditionalChallengeResponseRequiredList()) {
      if (!item.hasType() || !item.hasPrompt())
        continue;
      AuthRequest.Credential c = new AuthRequest.Credential();
      c.setType(item.getType());
      c.setPrompt(item.getPrompt());
      for (StudioAPI.AdditionalChallengeResponseRequired.Parameter parameter : item.getParameterList()) {
        if (parameter.hasName() && parameter.hasValue())
          c.getParameters().put(parameter.getName(), parameter.getValue());
      }
      challenges.add(c);
    }
    return challenges;
  }

}
