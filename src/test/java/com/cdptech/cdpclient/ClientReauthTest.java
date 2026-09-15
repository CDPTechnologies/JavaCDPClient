/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import org.junit.Test;

import java.lang.reflect.Field;
import java.net.URI;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientReauthTest {

  private static final Map<String, String> NO_USERNAME = new HashMap<>();
  private static final Map<String, String> CREDENTIALS = credentials("user", "secret");
  private static final Map<String, String> WRONG = credentials("user", "wrong");
  private static final Map<String, String> WRONG_AGAIN = credentials("user", "wrong2");

  /** A username-less answer fails this client's own check during delivery, which opens one correction prompt. */
  @Test
  public void reauthWithMissingUsername_opensOneCorrectionPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(NO_USERNAME);

    assertEquals(2, prompts.size());
    assertTrue(a.accepted.isEmpty());
  }

  /** Two connections fail on the same username-less answer. The second joins the correction prompt the first opened. */
  @Test
  public void reauthWithMissingUsername_twoConnectionsShareTheCorrectionPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    assertEquals(1, prompts.size());

    prompts.get(0).accept(NO_USERNAME);
    assertEquals(2, prompts.size());

    prompts.get(1).accept(CREDENTIALS);
    assertEquals(Arrays.asList("A:secret"), a.accepted);
    assertEquals(Arrays.asList("B:secret"), b.accepted);
  }

  /** The correction prompt is answered at once, while the username-less answer is still being delivered to the second connection. */
  @Test
  public void reauthWithMissingUsername_correctionAnsweredDuringDelivery_servesBothConnections() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client, 2, CREDENTIALS);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(NO_USERNAME);

    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("A:secret"), a.accepted);
    assertEquals(Arrays.asList("B:secret"), b.accepted);
  }

  /** The correction prompt is rejected at once during the first delivery. Both connections are rejected. */
  @Test
  public void reauthWithMissingUsername_correctionRejectedDuringDelivery_rejectsBothConnections() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client, 2, null);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(NO_USERNAME);

    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("A"), a.rejected);
    assertEquals(Arrays.asList("B"), b.rejected);
    assertTrue(a.accepted.isEmpty() && b.accepted.isEmpty());
  }

  /** The correction prompt is answered without a username as well. A third prompt takes over and serves both connections. */
  @Test
  public void reauthWithMissingUsername_correctionAnsweredWithoutUsernameAgain_thirdPromptServesBoth() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client, 2, NO_USERNAME);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(NO_USERNAME);
    assertEquals(3, prompts.size());

    prompts.get(2).accept(CREDENTIALS);
    assertEquals(Arrays.asList("A:secret"), a.accepted);
    assertEquals(Arrays.asList("B:secret"), b.accepted);
  }

  /** The server rejects the answer later. A correction prompt opens and the rejected credentials stay unsent. */
  @Test
  public void reauthAnswerRejectedByServer_opensCorrectionPromptWithoutReplay() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(WRONG);
    assertEquals(Arrays.asList("A:wrong"), a.accepted);

    client.requestReauthentication(a.serverRejection(client));
    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("A:wrong"), a.accepted);

    prompts.get(1).accept(CREDENTIALS);
    assertEquals(Arrays.asList("A:wrong", "A:secret"), a.accepted);
  }

  /** Two connections share the rejected answer. The second rejection joins the correction prompt the first opened. */
  @Test
  public void reauthAnswerRejectedByServer_secondConnectionJoinsTheCorrectionPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(WRONG);
    client.requestReauthentication(a.serverRejection(client));
    client.requestReauthentication(b.serverRejection(client));
    assertEquals(2, prompts.size());

    prompts.get(1).accept(CREDENTIALS);
    assertEquals(Arrays.asList("A:wrong", "A:secret"), a.accepted);
    assertEquals(Arrays.asList("B:wrong", "B:secret"), b.accepted);
  }

  /** The correction prompt is answered before the second connection's rejection arrives. That connection gets the new answer. */
  @Test
  public void reauthAnswerRejectedByServer_afterCorrectionAnswered_servesTheNewAnswer() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(WRONG);
    client.requestReauthentication(a.serverRejection(client));
    prompts.get(1).accept(CREDENTIALS);
    client.requestReauthentication(b.serverRejection(client));

    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("B:wrong", "B:secret"), b.accepted);
  }

  /** A connection whose session locks out after the correction prompt was answered gets that answer at once. */
  @Test
  public void lateJoinerAfterCorrectionAnswered_getsThatAnswer() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection c = new StubConnection("C");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(WRONG);
    client.requestReauthentication(a.serverRejection(client));
    prompts.get(1).accept(CREDENTIALS);

    client.requestReauthentication(c.cycleStart(client));
    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("C:secret"), c.accepted);
  }

  /**
   * A connection that was granted since it last received an answer carries no answering prompt. Its
   * later verdict opens the next prompt instead of taking the answer of the cycle that followed.
   */
  @Test
  public void verdictWithNoAnsweringPrompt_opensNextPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection c = new StubConnection("C");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(WRONG);
    a.answeringPrompt = null;                 // the grant clears it, Connection.setUpReauthentication
    expire((CompositeAuthRequest) prompts.get(0));
    client.process();

    client.requestReauthentication(c.cycleStart(client));
    prompts.get(1).accept(CREDENTIALS);
    client.requestReauthentication(a.serverRejection(client));

    assertEquals(3, prompts.size());
    assertEquals(Arrays.asList("A:wrong"), a.accepted);
    assertEquals(Arrays.asList("C:secret"), c.accepted);
  }

  /**
   * Two corrections follow each other before the second connection's delayed rejection arrives. That
   * connection gets the newest answer, never the superseded wrong one.
   */
  @Test
  public void delayedRejection_afterTwoCorrections_getsTheNewestAnswer() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(WRONG);
    client.requestReauthentication(a.serverRejection(client));
    prompts.get(1).accept(WRONG_AGAIN);
    client.requestReauthentication(a.serverRejection(client));
    prompts.get(2).accept(CREDENTIALS);
    client.requestReauthentication(b.serverRejection(client));

    assertEquals(3, prompts.size());
    assertEquals(Arrays.asList("B:wrong", "B:secret"), b.accepted);
  }

  /** A delayed rejection arriving after the answered correction prompt expired opens the next prompt instead of handing the expired answer over. */
  @Test
  public void delayedRejection_afterCorrectionExpired_opensNextPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    client.requestReauthentication(b.cycleStart(client));
    prompts.get(0).accept(WRONG);
    client.requestReauthentication(a.serverRejection(client));
    prompts.get(1).accept(CREDENTIALS);
    expire((CompositeAuthRequest) prompts.get(1));
    client.process();
    client.requestReauthentication(b.serverRejection(client));

    assertEquals(3, prompts.size());
    assertEquals(Arrays.asList("B:wrong"), b.accepted);
  }

  /**
   * The rejected connection's prompt expired and another connection has started and answered the next
   * cycle's prompt. The delayed rejection takes that fresh answer instead of opening a third prompt.
   */
  @Test
  public void delayedRejection_afterNextPromptAnswered_takesThatAnswer() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection c = new StubConnection("C");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(WRONG);
    expire((CompositeAuthRequest) prompts.get(0));
    client.process();
    client.requestReauthentication(c.cycleStart(client));
    prompts.get(1).accept(CREDENTIALS);
    client.requestReauthentication(a.serverRejection(client));

    assertEquals(2, prompts.size());
    assertEquals(Arrays.asList("A:wrong", "A:secret"), a.accepted);
  }

  /**
   * The answered prompt expired and another connection has already opened the next cycle's prompt when a
   * late rejection arrives. The rejected connection joins that open prompt instead of opening a second one.
   */
  @Test
  public void rejectionAfterExpiryWhileNextPromptIsOpen_joinsThatPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection c = new StubConnection("C");

    client.requestReauthentication(a.cycleStart(client));
    client.process();
    prompts.get(0).accept(WRONG);
    expire((CompositeAuthRequest) prompts.get(0));
    client.process();
    client.requestReauthentication(c.cycleStart(client));
    assertEquals(2, prompts.size());

    client.requestReauthentication(a.serverRejection(client));
    assertEquals(2, prompts.size());

    prompts.get(1).accept(CREDENTIALS);
    assertEquals(Arrays.asList("C:secret"), c.accepted);
    assertEquals(Arrays.asList("A:wrong", "A:secret"), a.accepted);
  }

  /** Backdates the prompt's answer to the edge of the five-second cache window. */
  /**
   * The answered prompt's cache window has passed but the event loop has not run since, so the prompt is
   * still the current one. A cycle start arriving now opens the next prompt instead of taking the stale answer.
   */
  @Test
  public void cycleStartAfterCacheExpired_opensNextPrompt() throws Exception {
    Client client = new Client();
    List<AuthRequest> prompts = record(client);
    StubConnection a = new StubConnection("A");
    StubConnection b = new StubConnection("B");

    client.requestReauthentication(a.cycleStart(client));
    prompts.get(0).accept(CREDENTIALS);
    expire((CompositeAuthRequest) prompts.get(0));
    client.requestReauthentication(b.cycleStart(client));

    assertEquals(Arrays.asList(), b.accepted);
    assertEquals(2, prompts.size());
  }

  private static void expire(CompositeAuthRequest prompt) throws Exception {
    Field field = CompositeAuthRequest.class.getDeclaredField("readyTimestamp");
    field.setAccessible(true);
    field.set(prompt, Instant.now().minusSeconds(5));
  }

  private static Map<String, String> credentials(String user, String password) {
    Map<String, String> data = new HashMap<>();
    data.put(AuthRequest.USER, user);
    data.put(AuthRequest.PASSWORD, password);
    return data;
  }

  /** Records every prompt the client opens. */
  private static List<AuthRequest> record(Client client) throws Exception {
    return record(client, 0, null);
  }

  /**
   * Records every prompt and answers prompt number {@code answerAtOnce} inside the callback, with
   * {@code answer}, or rejects it when {@code answer} is null.
   */
  private static List<AuthRequest> record(Client client, int answerAtOnce, Map<String, String> answer) throws Exception {
    List<AuthRequest> prompts = new ArrayList<>();
    Field field = Client.class.getDeclaredField("listener");
    field.setAccessible(true);
    field.set(client, new NotificationListener() {
      @Override public void clientReady(Client c) {}
      @Override public void clientClosed(Client c) {}
      @Override public void credentialsRequested(AuthRequest request) {
        prompts.add(request);
        if (prompts.size() == answerAtOnce) {
          if (answer == null) {
            request.reject();
          } else {
            request.accept(answer);
          }
        }
      }
    });
    return prompts;
  }

  /** Stands in for one Connection: its name, what it was handed, and the prompt that last answered it. */
  private static class StubConnection {
    final String name;
    final List<String> accepted = new ArrayList<>();
    final List<String> rejected = new ArrayList<>();
    CompositeAuthRequest answeringPrompt;

    StubConnection(String name) {
      this.name = name;
    }

    ReauthRequest cycleStart(Client client) {
      return new ReauthStub(client, this, AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED);
    }

    ReauthRequest serverRejection(Client client) {
      return new ReauthStub(client, this, AuthRequest.AuthResultCode.INVALID_CHALLENGE_RESPONSE);
    }
  }

  /**
   * Stands in for Connection.ConnectionAuthRequest. Accepting it models IOHandler.reauthenticate: a
   * username-less map fails validation and the client re-requests with USERNAME_REQUIRED at once, a map
   * with a username is recorded as accepted. The server's verdict on an accepted answer arrives later as
   * a new request built with {@link StubConnection#serverRejection}.
   */
  private static class ReauthStub implements ReauthRequest {
    private final Client client;
    private final StubConnection connection;
    private final AuthResultCode code;

    ReauthStub(Client client, StubConnection connection, AuthResultCode code) {
      this.client = client;
      this.connection = connection;
      this.code = code;
    }

    @Override public void accept(Map<String, String> data) {
      String user = data.get(USER);
      if (user == null || user.isEmpty()) {
        client.requestReauthentication(new ReauthStub(client, connection, AuthResultCode.USERNAME_REQUIRED));
      } else {
        connection.accepted.add(connection.name + ":" + data.get(PASSWORD));
      }
    }

    @Override public void reject() {
      connection.rejected.add(connection.name);
    }

    @Override public CompositeAuthRequest getAnsweringPrompt() {
      return connection.answeringPrompt;
    }

    @Override public void setAnsweringPrompt(CompositeAuthRequest prompt) {
      connection.answeringPrompt = prompt;
    }

    @Override public UserAuthResult getAuthResult() {
      UserAuthResult result = new UserAuthResult();
      result.setCode(code);
      return result;
    }

    @Override public String getSystemName() { return "Sys"; }
    @Override public String getApplicationName() { return connection.name; }
    @Override public URI getServerURI() { return null; }
    @Override public CDPVersion getCDPVersion() { return null; }
    @Override public Certificate[] getPeerCertificates() { return new Certificate[0]; }
    @Override public long getIdleLockoutPeriod() { return 0; }
    @Override public String getSystemUseNotification() { return ""; }
    @Override public List<SuggestedUser> getSuggestedUsers() { return new ArrayList<>(); }
  }
}
