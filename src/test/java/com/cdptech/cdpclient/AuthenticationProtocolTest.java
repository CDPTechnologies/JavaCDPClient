/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthenticationProtocolTest {

  private CapturingTransport transport;
  private AtomicInteger finishedCount;
  private AuthenticationProtocol protocol;
  private String publicKeyPem;

  @Before
  public void setUp() throws Exception {
    transport = new CapturingTransport();
    finishedCount = new AtomicInteger();
    protocol = new AuthenticationProtocol(transport, finishedCount::incrementAndGet);

    java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
        + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded())
        + "\n-----END PUBLIC KEY-----\n";
  }

  @Test
  public void encryptedPasswordChallenge_reissuesAutomaticallyWithoutFinishing() throws Exception {
    protocol.authenticate("challenge", credentials());

    assertEquals(1, transport.sent.size());
    assertTrue(hasChallengeType(transport.sent.get(0), "PasswordHash"));
    assertFalse(hasChallengeType(transport.sent.get(0), "EncryptedPassword"));
    assertEquals("must not finish before the encrypted-password round", 0, finishedCount.get());

    protocol.parse(encryptedPasswordRequest().toByteArray());

    assertEquals("server's EncryptedPassword request should auto reissue", 2, transport.sent.size());
    assertEquals("reissue must not prompt the user / finish", 0, finishedCount.get());
    assertTrue(hasChallengeType(transport.sent.get(1), "PasswordHash"));
    assertTrue(hasChallengeType(transport.sent.get(1), "EncryptedPassword"));

    protocol.parse(grantedResponse().toByteArray());

    assertEquals("granted response should not send anything more", 2, transport.sent.size());
    assertEquals(1, finishedCount.get());
  }

  private Map<String, String> credentials() {
    Map<String, String> data = new HashMap<>();
    data.put(AuthRequest.USER, "operator");
    data.put(AuthRequest.PASSWORD, "pw");
    return data;
  }

  private StudioAPI.AuthResponse encryptedPasswordRequest() {
    return StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
        .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
            .setType("EncryptedPassword")
            .setPrompt("") // the server always sets prompt (empty for EncryptedPassword) and its presence is required
            .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                .setName("PasswordEncryptionPublicKey")
                .setValue(publicKeyPem)))
        .build();
  }

  private StudioAPI.AuthResponse grantedResponse() {
    return StudioAPI.AuthResponse.newBuilder()
        .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eGranted)
        .build();
  }

  private static boolean hasChallengeType(byte[] authRequestBytes, String type) throws Exception {
    StudioAPI.AuthRequest request = StudioAPI.AuthRequest.parseFrom(authRequestBytes);
    for (StudioAPI.AuthRequest.ChallengeResponse cr : request.getChallengeResponseList()) {
      if (type.equals(cr.getType())) {
        return true;
      }
    }
    return false;
  }
}
