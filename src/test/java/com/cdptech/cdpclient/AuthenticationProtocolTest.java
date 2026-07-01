/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AuthenticationProtocolTest {

  private FakeTransport transport;
  private AtomicInteger finishedCount;
  private AuthenticationProtocol protocol;
  private String publicKeyPem;

  @Before
  public void setUp() throws Exception {
    transport = new FakeTransport();
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

    assertEquals("server's EncryptedPassword request should auto re-issue", 2, transport.sent.size());
    assertEquals("re-issue must not prompt the user / finish", 0, finishedCount.get());
    assertTrue(hasChallengeType(transport.sent.get(1), "PasswordHash"));
    assertTrue(hasChallengeType(transport.sent.get(1), "EncryptedPassword"));

    protocol.parse(grantedResponse().toByteArray());

    assertEquals("granted response should not send anything more", 2, transport.sent.size());
    assertEquals(1, finishedCount.get());
  }

  @Test
  public void encryptedPassword_reissuesEachTimeTheServerRequests() throws Exception {
    protocol.authenticate("challenge", credentials());
    protocol.parse(encryptedPasswordRequest().toByteArray()); // first auto re-issue
    assertEquals(2, transport.sent.size());

    protocol.parse(encryptedPasswordRequest().toByteArray()); // server requests EncryptedPassword again

    assertEquals("re-issues again on a repeated EncryptedPassword request", 3, transport.sent.size());
    assertTrue(hasChallengeType(transport.sent.get(2), "EncryptedPassword"));
    assertEquals("a re-issue must not finish the handshake", 0, finishedCount.get());
  }

  @Test
  public void clearCachedCredentials_stopsAutomaticReissue() throws Exception {
    protocol.authenticate("challenge", credentials());
    assertEquals(1, transport.sent.size());

    protocol.clearCachedCredentials(); // the attempt was granted; cached credentials must not answer a later challenge

    protocol.parse(encryptedPasswordRequest().toByteArray());

    assertEquals("cleared credentials must not auto-reissue", 1, transport.sent.size());
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
            .setPrompt("") // the server always sets prompt (empty for EncryptedPassword); its presence is required
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

  /** A Transport that records sent frames instead of writing them to a socket. */
  private static class FakeTransport extends Transport {
    final List<byte[]> sent = new ArrayList<>();

    FakeTransport() throws URISyntaxException {
      super(new URI("ws://localhost:1"), new LinkedBlockingQueue<>(), e -> { });
    }

    @Override
    public void send(byte[] data) {
      sent.add(data);
    }
  }
}
