/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.Before;
import org.junit.Test;

import javax.crypto.Cipher;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthenticatorTest {

  private static final String USER = "operator";
  private static final String PASSWORD = "s3cret-pässw0rd";
  private static final String CHALLENGE = "challenge-bytes-1234";

  private Authenticator authenticator;
  private KeyPair rsaKeyPair;
  private String publicKeyPem;

  @Before
  public void setUp() throws Exception {
    authenticator = new Authenticator();
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    rsaKeyPair = generator.generateKeyPair();
    publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getMimeEncoder().encodeToString(rsaKeyPair.getPublic().getEncoded())
        + "\n-----END PUBLIC KEY-----\n";
  }

  /** Regression for the dropped {@code challenges.add(c)} in getAdditionalChallenges(). */
  @Test
  public void additionalChallenges_areParsedFromResponse() {
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());

    List<AuthRequest.Credential> additional = authenticator.getUserAuthResult().getAdditionalCredentials();
    assertEquals(AuthRequest.AuthResultCode.ADDITIONAL_RESPONSE_REQUIRED,
        authenticator.getUserAuthResult().getCode());
    assertEquals(1, additional.size());
    assertEquals("EncryptedPassword", additional.get(0).getType());
    assertEquals(publicKeyPem, additional.get(0).getParameters().get("PasswordEncryptionPublicKey"));
  }

  @Test
  public void encryptedPasswordResponse_decryptsToChallengePlusPassword() throws Exception {
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, credentials(PASSWORD));

    byte[] cipherText = encryptedPasswordResponseBytes(request);
    assertNotNull("EncryptedPassword challenge response expected", cipherText);
    assertEquals(CHALLENGE + PASSWORD, decrypt(cipherText));
    // The PasswordHash response is still sent alongside the encrypted one.
    assertTrue(hasChallengeResponseOfType(request, "PasswordHash"));
  }

  @Test
  public void encryptedPassword_chunksInputLongerThanOneRsaBlock() throws Exception {
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());
    String longPassword = repeat("p", 400); // challenge + password > 245 bytes -> multiple RSA blocks

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, credentials(longPassword));

    byte[] cipherText = encryptedPasswordResponseBytes(request);
    assertEquals(0, cipherText.length % 256); // 2048-bit key -> 256-byte ciphertext blocks
    assertTrue("expected more than one RSA block", cipherText.length > 256);
    assertEquals(CHALLENGE + longPassword, decrypt(cipherText));
  }

  @Test
  public void noEncryptedPasswordResponse_whenServerDidNotRequestIt() {
    // No additional challenge -> only PasswordHash, never EncryptedPassword.
    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, credentials(PASSWORD));

    assertNull(encryptedPasswordResponseBytes(request));
    assertTrue(hasChallengeResponseOfType(request, "PasswordHash"));
  }

  @Test
  public void clearCachedCredentials_stopsSilentChallengeReissue() {
    // Prime a reauth round: the server requests EncryptedPassword and the credentials are cached so the
    // within-round challenge can be answered without re-prompting.
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());
    authenticator.createAuthMessage(CHALLENGE, credentials(PASSWORD)); // caches the attempt
    assertNotNull("within the round the challenge is answered from the cache",
        authenticator.encryptedPasswordReissueRequest(CHALLENGE));

    authenticator.clearCachedCredentials(); // the round was granted

    assertNull("a later idle-lock challenge must prompt the user, not reuse cached credentials",
        authenticator.encryptedPasswordReissueRequest(CHALLENGE));
  }

  @Test
  public void reissue_answersFromSnapshotWhenCallerScrubsTheMap() throws Exception {
    // The caller owns the credentials map and may scrub it after accept() returns; the reissue must
    // answer from the cached copy, not the caller's map.
    Map<String, String> data = credentials(PASSWORD);
    authenticator.createAuthMessage(CHALLENGE, data);
    data.clear(); // caller scrubs the password

    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());
    StudioAPI.AuthRequest reissue = authenticator.encryptedPasswordReissueRequest(CHALLENGE);

    assertNotNull("reissue must be answered from the cached snapshot", reissue);
    assertEquals(CHALLENGE + PASSWORD, decrypt(encryptedPasswordResponseBytes(reissue)));
  }

  @Test
  public void additionalChallenge_missingPrompt_isSkipped() {
    // The server always sets a prompt; a challenge that omits it is malformed and must not become a Credential.
    StudioAPI.AuthResponse response = StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
        .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
            .setType("EncryptedPassword"))
        .build();

    authenticator.updateUserAuthResult(response);

    assertTrue("a challenge missing prompt must be skipped",
        authenticator.getUserAuthResult().getAdditionalCredentials().isEmpty());
  }

  @Test
  public void additionalChallenge_missingType_isSkipped() {
    StudioAPI.AuthResponse response = StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
        .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
            .setPrompt("Enter code"))
        .build();

    authenticator.updateUserAuthResult(response);

    assertTrue("a challenge missing type must be skipped",
        authenticator.getUserAuthResult().getAdditionalCredentials().isEmpty());
  }

  @Test
  public void challengeParameter_missingNameOrValue_isExcluded() {
    StudioAPI.AuthResponse response = StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
        .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
            .setType("EncryptedPassword")
            .setPrompt("")
            .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                .setName("NoValue"))
            .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                .setValue("no-name"))
            .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                .setName("Complete")
                .setValue("both-set")))
        .build();

    authenticator.updateUserAuthResult(response);

    Map<String, String> parameters =
        authenticator.getUserAuthResult().getAdditionalCredentials().get(0).getParameters();
    assertEquals(1, parameters.size());
    assertEquals("both-set", parameters.get("Complete"));
  }

  @Test
  public void emptyCachedPassword_doesNotReissue() {
    // An empty cached password cannot answer the EncryptedPassword challenge; reissuing would send
    // zero-response requests in a loop instead of re-prompting the user.
    authenticator.createAuthMessage(CHALLENGE, credentials("")); // caches the empty-password attempt
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());

    assertNull(authenticator.encryptedPasswordReissueRequest(CHALLENGE));
  }

  @Test
  public void emptyPassword_sendsNoPasswordResponses() {
    // An empty password sends neither PasswordHash nor EncryptedPassword, so the server re-prompts
    // instead of rejecting a hash of "".
    authenticator.updateUserAuthResult(encryptedPasswordChallengeResponse());

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, credentials(""));

    assertEquals(0, request.getChallengeResponseCount());
  }

  @Test
  public void newPassword_sendsNewPasswordHashAlongsidePasswordHash() throws Exception {
    // NewPasswordHash carries passwordHash(user, newPassword) directly; only the current password is
    // challenge-hashed.
    Map<String, String> data = credentials(PASSWORD);
    data.put(AuthRequest.NEW_PASSWORD, "new-s3cret");

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, data);

    byte[] response = challengeResponseOfType(request, "NewPasswordHash");
    assertNotNull("NewPasswordHash response expected", response);
    assertArrayEquals(sha256(USER + ":" + "new-s3cret"), response);
    assertTrue(hasChallengeResponseOfType(request, "PasswordHash"));
  }

  @Test
  public void emptyNewPassword_sendsNoNewPasswordHash() {
    // An empty new password would otherwise be hashed and set on the account; the server re-prompts instead.
    Map<String, String> data = credentials(PASSWORD);
    data.put(AuthRequest.NEW_PASSWORD, "");

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, data);

    assertFalse(hasChallengeResponseOfType(request, "NewPasswordHash"));
  }

  @Test
  public void noResultCode_isDeniedNotRetried() {
    // An AuthResponse that omits result_code must fail closed, not read as the proto default
    // eCredentialsRequired (which would silently retry).
    authenticator.updateUserAuthResult(StudioAPI.AuthResponse.newBuilder().build());

    assertEquals(AuthRequest.AuthResultCode.INVALID_CHALLENGE_RESPONSE,
        authenticator.getUserAuthResult().getCode());
  }

  @Test
  public void rolesAssigned_arePopulatedFromResponse() {
    StudioAPI.AuthResponse response = StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eGranted)
        .addRoleAssigned("operator")
        .addRoleAssigned("admin")
        .build();

    authenticator.updateUserAuthResult(response);

    assertEquals(Arrays.asList("operator", "admin"), authenticator.getUserAuthResult().getRolesAssigned());
  }

  @Test
  public void missingUserName_yieldsUsernameRequired() {
    Map<String, String> noUser = new HashMap<>();
    noUser.put(AuthRequest.PASSWORD, PASSWORD);

    StudioAPI.AuthRequest request = authenticator.createAuthMessage(CHALLENGE, noUser);

    assertNull("no user id -> no AuthRequest is built", request);
    assertEquals(AuthRequest.AuthResultCode.USERNAME_REQUIRED, authenticator.getUserAuthResult().getCode());
    assertEquals("Username required", authenticator.getUserAuthResult().getText());
  }

  private StudioAPI.AuthResponse encryptedPasswordChallengeResponse() {
    return StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
        .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
            .setType("EncryptedPassword")
            .setPrompt("") // the server always sets prompt (empty for EncryptedPassword); its presence is required
            .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                .setName("PasswordEncryptionPublicKey")
                .setValue(publicKeyPem)))
        .build();
  }

  private Map<String, String> credentials(String password) {
    Map<String, String> data = new HashMap<>();
    data.put(AuthRequest.USER, USER);
    data.put(AuthRequest.PASSWORD, password);
    return data;
  }

  private static byte[] encryptedPasswordResponseBytes(StudioAPI.AuthRequest request) {
    return challengeResponseOfType(request, "EncryptedPassword");
  }

  private static byte[] challengeResponseOfType(StudioAPI.AuthRequest request, String type) {
    for (StudioAPI.AuthRequest.ChallengeResponse cr : request.getChallengeResponseList()) {
      if (type.equals(cr.getType())) {
        return cr.getResponse().toByteArray();
      }
    }
    return null;
  }

  private static byte[] sha256(String text) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(text.getBytes());
  }

  private static boolean hasChallengeResponseOfType(StudioAPI.AuthRequest request, String type) {
    for (StudioAPI.AuthRequest.ChallengeResponse cr : request.getChallengeResponseList()) {
      if (type.equals(cr.getType())) {
        return true;
      }
    }
    return false;
  }

  private String decrypt(byte[] cipherText) throws Exception {
    Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
    cipher.init(Cipher.DECRYPT_MODE, rsaKeyPair.getPrivate());
    ByteArrayOutputStream plain = new ByteArrayOutputStream();
    for (int offset = 0; offset < cipherText.length; offset += 256) {
      plain.write(cipher.doFinal(cipherText, offset, 256));
    }
    return new String(plain.toByteArray(), StandardCharsets.UTF_8);
  }

  private static String repeat(String s, int times) {
    StringBuilder sb = new StringBuilder(s.length() * times);
    for (int i = 0; i < times; i++) {
      sb.append(s);
    }
    return sb.toString();
  }
}
