/**
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.google.protobuf.ByteString;

import org.junit.Before;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.LinkedBlockingQueue;

public class IOHandlerTest {

  StudioAPI.VariantValue.Builder pbv;
  
  @Before
  public void setUp() throws Exception {
    pbv = StudioAPI.VariantValue.newBuilder().setNodeId(5);
  }

  @Test
  public void createVariant_shouldReadFloat() {
    Variant created = IOHandler.createVariant(pbv.setFValue(2.1234f).build(), 0);
    float value = created.getValue();
    assertEquals(2.1234f, value, 0.001);
  }
  
  @Test
  public void createVariant_shouldReadString() {
    Variant created = IOHandler.createVariant(pbv.setStrValue("test").build(), 0);
    String value = created.getValue();
    assertEquals("test", value);
  }

  @Test
  public void parse_unsignedChar_isNumeric_notFirstCharacterCodePoint() {
    Variant v = new Variant.Builder(StudioAPI.CDPValueType.eUCHAR).parse("255").build();
    assertEquals("eUCHAR parses the number 255, not the code point of '2'", Integer.valueOf(255), v.getValue());
  }

  @Test
  public void createVariant_presentZeroTimestamp_staysZeroNotBareDelta() {
    // A value carrying a present-but-zero timestamp must not be shifted by the clock delta into a spurious
    // non-zero time; the delta applies only to a non-zero remote timestamp.
    long delta = 500_000;
    Variant zeroTs = IOHandler.createVariant(
        StudioAPI.VariantValue.newBuilder().setNodeId(5).setDValue(1.0).setTimestamp(0).build(), delta);
    assertEquals(java.time.Instant.ofEpochSecond(0, 0), zeroTs.getTimestamp());

    Variant nonZeroTs = IOHandler.createVariant(
        StudioAPI.VariantValue.newBuilder().setNodeId(5).setDValue(1.0).setTimestamp(1000).build(), delta);
    assertEquals(java.time.Instant.ofEpochSecond(0, 1000 + delta), nonZeroTs.getTimestamp());
  }

  @Test
  public void setRemoteValue_narrowIntTypes_sendValueWithoutClassCastException() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));

    Node us = new Node(5, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eUSHORT, "Us", 0);
    ioHandler.setRemoteValue(us, new Variant.Builder(StudioAPI.CDPValueType.eUSHORT).parse("40000").build());
    assertEquals(40000, StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getUsValue());

    Node uc = new Node(6, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eUCHAR, "Uc", 0);
    ioHandler.setRemoteValue(uc, new Variant.Builder(StudioAPI.CDPValueType.eUCHAR).parse("200").build());
    assertEquals(200, StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getUcValue());

    Node s = new Node(7, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eSHORT, "S", 0);
    ioHandler.setRemoteValue(s, new Variant.Builder(StudioAPI.CDPValueType.eSHORT).parse("-30000").build());
    assertEquals(-30000, StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getSValue());

    Node c = new Node(8, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eCHAR, "C", 0);
    ioHandler.setRemoteValue(c, new Variant.Builder(StudioAPI.CDPValueType.eCHAR).parse("-100").build());
    assertEquals(-100, StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getCValue());
  }

  @Test
  public void setRemoteValue_roundTripsServerReceivedNarrowInt() throws Exception {
    // A narrow int received from the server is boxed as Integer by createVariant; posting it back must not crash.
    Variant received = IOHandler.createVariant(
        StudioAPI.VariantValue.newBuilder().setNodeId(5).setUsValue(40000).build(), 0);
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    Node node = new Node(5, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eUSHORT, "Us", 0);
    ioHandler.setRemoteValue(node, received);
    assertEquals(40000, StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getUsValue());
  }

  @Test
  public void parse_unsignedIntAndLong_acceptFullUnsignedRange() {
    // uint32/uint64 cover values above the signed maximum; they are stored with the sign bit as the top bit.
    Variant ui = new Variant.Builder(StudioAPI.CDPValueType.eUINT).parse("4000000000").build();
    assertEquals(4000000000L, Integer.toUnsignedLong(ui.getValue()));

    Variant ui64 = new Variant.Builder(StudioAPI.CDPValueType.eUINT64).parse("18446744073709551615").build();
    assertEquals("18446744073709551615", Long.toUnsignedString(ui64.getValue()));
  }

  @Test
  public void setRemoteValue_unsignedInt_sendsFullRangeValueOnWire() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    Node node = new Node(5, StudioAPI.CDPNodeType.CDP_OBJECT, StudioAPI.CDPValueType.eUINT, "Ui", 0);
    ioHandler.setRemoteValue(node, new Variant.Builder(StudioAPI.CDPValueType.eUINT).parse("4000000000").build());
    assertEquals(4000000000L,
        Integer.toUnsignedLong(StudioAPI.Container.parseFrom(transport.last).getSetterRequest(0).getUiValue()));
  }

  @Test
  public void toString_unsignedTypes_printUnsignedAndRoundTripThroughParse() {
    // A top-bit-set unsigned value prints its unsigned form, and parse(toString()) reproduces the value.
    Variant ui = IOHandler.createVariant(
        StudioAPI.VariantValue.newBuilder().setNodeId(5).setUiValue((int) 4000000000L).build(), 0);
    assertEquals("4000000000", ui.toString());
    assertEquals(ui.<Integer>getValue(),
        new Variant.Builder(StudioAPI.CDPValueType.eUINT).parse(ui.toString()).build().getValue());

    Variant ui64 = IOHandler.createVariant(
        StudioAPI.VariantValue.newBuilder().setNodeId(5).setUi64Value(-1L).build(), 0);
    assertEquals("18446744073709551615", ui64.toString());
    assertEquals(ui64.<Long>getValue(),
        new Variant.Builder(StudioAPI.CDPValueType.eUINT64).parse(ui64.toString()).build().getValue());
  }

  @Test(expected = IllegalArgumentException.class)
  public void parse_negativeIntoUnsigned_throws() {
    // A negative string is not a valid unsigned value; it is rejected like the ranged narrow types, not wrapped.
    new Variant.Builder(StudioAPI.CDPValueType.eUINT).parse("-1");
  }

  @Test
  public void reauthResponse_requestingEncryptedPassword_isAnsweredAutomatically() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    ioHandler.setCredentialsRequester(result -> { });
    ioHandler.parse(authResponseExpired());  // the server demands re-auth (stores the challenge)
    ioHandler.reauthenticate(credentials()); // sends the reauth request and caches the attempt

    ioHandler.parse(reauthResponseRequestingEncryptedPassword());

    StudioAPI.Container sent = StudioAPI.Container.parseFrom(transport.last);
    assertEquals(StudioAPI.Container.Type.eReauthRequest, sent.getMessageType());
    boolean carriesEncryptedPassword = false;
    for (StudioAPI.AuthRequest.ChallengeResponse cr : sent.getReAuthRequest().getChallengeResponseList()) {
      if ("EncryptedPassword".equals(cr.getType())) {
        carriesEncryptedPassword = true;
      }
    }
    assertTrue("the EncryptedPassword challenge is answered automatically", carriesEncryptedPassword);
  }

  @Test
  public void reauthResponse_afterClearedCredentials_promptsInsteadOfReissuing() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    java.util.concurrent.atomic.AtomicReference<AuthRequest.UserAuthResult> prompted =
        new java.util.concurrent.atomic.AtomicReference<>();
    ioHandler.setCredentialsRequester(prompted::set);
    ioHandler.parse(authResponseExpired());  // the server demands re-auth (stores the challenge)
    ioHandler.reauthenticate(credentials());
    ioHandler.clearCachedCredentials(); // the round was granted
    transport.last = null;
    prompted.set(null); // ignore the setup prompt; only the reissue-vs-prompt decision below matters

    ioHandler.parse(reauthResponseRequestingEncryptedPassword());

    assertNull("no automatic reissue once the credentials are cleared", transport.last);
    assertNotNull("the user is prompted instead", prompted.get());
  }

  @Test
  public void repeatedAuthExpired_raisesOnePromptPerCycle() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    java.util.concurrent.atomic.AtomicInteger prompts = new java.util.concurrent.atomic.AtomicInteger();
    // Count the re-prompts this cycle drives. Connection.setUpReauthentication re-prompts (requestReauthentication)
    // on any non-granting result; an eAUTH_RESPONSE_EXPIRED delivers REAUTHENTICATION_REQUIRED, so that is the
    // only code this cycle produces.
    ioHandler.setCredentialsRequester(result -> {
      if (result.getCode() == AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED) {
        prompts.incrementAndGet();
      }
    });

    // Several auth-expired errors within one cycle raise a single prompt: a burst arriving before the user
    // answers (the server rejects each in-flight request), then another after the reauth request is sent.
    ioHandler.parse(authResponseExpired());
    ioHandler.parse(authResponseExpired());
    ioHandler.reauthenticate(credentials());
    ioHandler.parse(authResponseExpired());
    assertEquals("one prompt per re-authentication cycle", 1, prompts.get());

    // The granted response ends the cycle; a later idle lockout starts a new one and prompts again.
    ioHandler.parse(reauthGranted());
    ioHandler.parse(authResponseExpired());
    assertEquals("a new lockout after the cycle is granted prompts again", 2, prompts.get());
  }

  @Test
  public void passwordExpiringGrant_alsoEndsReauthCycle() throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    java.util.concurrent.atomic.AtomicInteger prompts = new java.util.concurrent.atomic.AtomicInteger();
    ioHandler.setCredentialsRequester(result -> {
      if (result.getCode() == AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED) {
        prompts.incrementAndGet();
      }
    });

    ioHandler.parse(authResponseExpired());              // prompt 1, cycle armed
    ioHandler.parse(reauthGrantedPasswordExpiring());    // a password-expiry grant ends the cycle too
    ioHandler.parse(authResponseExpired());              // new cycle -> prompt 2
    assertEquals("a password-expiry grant clears the cycle like a plain grant", 2, prompts.get());
  }

  @Test
  public void reauthUsesLatestChallengeAfterSuppressedBurst() throws Exception {
    // The server issues a fresh challenge on every eAUTH_RESPONSE_EXPIRED. A burst before the user answers
    // is suppressed for prompting, but the re-auth must still answer the LATEST challenge, not the first.
    ByteString withA = reauthPasswordHashAfter("challenge-A");
    ByteString withB = reauthPasswordHashAfter("challenge-B");
    ByteString afterBurst = reauthPasswordHashAfter("challenge-A", "challenge-B");
    assertEquals("re-auth answers the latest challenge (B)", withB, afterBurst);
    assertNotEquals("re-auth must not answer the stale first challenge (A)", withA, afterBurst);
  }

  @Test
  public void encryptedPasswordReissueUsesLatestChallengeAfterSuppressedBurst() throws Exception {
    // The auto-answered EncryptedPassword reissue must also answer the latest challenge, not the one frozen
    // when the user first responded: a suppressed burst (B) after the first answer (A) must reissue for B.
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    ioHandler.setCredentialsRequester(result -> { });

    ioHandler.parse(authResponseExpired("challenge-A"));           // stores A, prompts
    ioHandler.reauthenticate(credentials());                       // answers A, caches the credentials
    ioHandler.parse(authResponseExpired("challenge-B"));           // stores B, prompt suppressed
    ioHandler.parse(reauthResponseRequestingEncryptedPassword());  // server asks EncryptedPassword -> reissue

    ByteString reissued = passwordHashOf(transport.last);
    assertEquals("the reissue answers the latest challenge (B)", reauthPasswordHashAfter("challenge-B"), reissued);
    assertNotEquals("the reissue must not answer the stale first challenge (A)",
        reauthPasswordHashAfter("challenge-A"), reissued);
  }

  /**
   * Drive a fresh IOHandler through the given expiry challenges (in order) then a re-authentication, and
   * return the PasswordHash response actually sent — which encodes whichever challenge the client used.
   */
  private static ByteString reauthPasswordHashAfter(String... challenges) throws Exception {
    CapturingTransport transport = new CapturingTransport();
    IOHandler ioHandler = new IOHandler(transport, new HelloProtocol(() -> { }));
    ioHandler.setTimeSyncEnabled(false);
    ioHandler.setCredentialsRequester(result -> { });
    for (String challenge : challenges) {
      ioHandler.parse(authResponseExpired(challenge));
    }
    ioHandler.reauthenticate(credentials());
    return passwordHashOf(transport.last);
  }

  /** The PasswordHash challenge-response bytes from a sent eReauthRequest container. */
  private static ByteString passwordHashOf(byte[] sent) throws Exception {
    for (StudioAPI.AuthRequest.ChallengeResponse cr :
        StudioAPI.Container.parseFrom(sent).getReAuthRequest().getChallengeResponseList()) {
      if ("PasswordHash".equals(cr.getType())) {
        return cr.getResponse();
      }
    }
    return null;
  }

  private static java.util.Map<String, String> credentials() {
    java.util.Map<String, String> data = new java.util.HashMap<>();
    data.put(AuthRequest.USER, "operator");
    data.put(AuthRequest.PASSWORD, "s3cret");
    return data;
  }

  /** An eReauthResponse asking for EncryptedPassword, carrying a freshly generated RSA public key. */
  private static byte[] reauthResponseRequestingEncryptedPassword() throws Exception {
    java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
        + java.util.Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPublic().getEncoded())
        + "\n-----END PUBLIC KEY-----\n";
    return StudioAPI.Container.newBuilder()
        .setMessageType(StudioAPI.Container.Type.eReauthResponse)
        .setReAuthResponse(StudioAPI.AuthResponse.newBuilder()
            .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eAdditionalResponseRequired)
            .addAdditionalChallengeResponseRequired(StudioAPI.AdditionalChallengeResponseRequired.newBuilder()
                .setType("EncryptedPassword")
                .setPrompt("")
                .addParameter(StudioAPI.AdditionalChallengeResponseRequired.Parameter.newBuilder()
                    .setName("PasswordEncryptionPublicKey")
                    .setValue(publicKeyPem))))
        .build()
        .toByteArray();
  }

  /** An eRemoteError carrying eAUTH_RESPONSE_EXPIRED — the server's re-authentication demand. */
  private static byte[] authResponseExpired() {
    return authResponseExpired("challenge-1");
  }

  /** As above, carrying a specific server-issued re-authentication challenge. */
  private static byte[] authResponseExpired(String challenge) {
    return StudioAPI.Container.newBuilder()
        .setMessageType(StudioAPI.Container.Type.eRemoteError)
        .setError(StudioAPI.Error.newBuilder()
            .setCode(StudioAPI.RemoteErrorCode.eAUTH_RESPONSE_EXPIRED.getNumber())
            .setText("Re-authentication required")
            .setChallenge(com.google.protobuf.ByteString.copyFromUtf8(challenge)))
        .build()
        .toByteArray();
  }

  /** An eReauthResponse granting the re-authentication, which resolves the in-flight cycle. */
  private static byte[] reauthGranted() {
    return StudioAPI.Container.newBuilder()
        .setMessageType(StudioAPI.Container.Type.eReauthResponse)
        .setReAuthResponse(StudioAPI.AuthResponse.newBuilder()
            .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eGranted))
        .build()
        .toByteArray();
  }

  /** An eReauthResponse granting with a password-expiry warning, which also resolves the cycle. */
  private static byte[] reauthGrantedPasswordExpiring() {
    return StudioAPI.Container.newBuilder()
        .setMessageType(StudioAPI.Container.Type.eReauthResponse)
        .setReAuthResponse(StudioAPI.AuthResponse.newBuilder()
            .setResultCode(StudioAPI.AuthResponse.AuthResultCode.eGrantedPasswordWillExpireSoon))
        .build()
        .toByteArray();
  }

  private static class CapturingTransport extends Transport {
    byte[] last;

    CapturingTransport() throws URISyntaxException {
      super(new URI("ws://localhost:1"), new LinkedBlockingQueue<>(), e -> { });
    }

    @Override
    public void send(byte[] data) {
      last = data;
    }
  }

}
