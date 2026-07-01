/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import org.junit.Test;

import java.net.URI;
import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompositeAuthRequestTest {

  @Test
  public void getSuggestedUsers_comesFromTheFirstConnection() {
    StubAuthRequest first = new StubAuthRequest();
    AuthRequest.SuggestedUser user = new AuthRequest.SuggestedUser();
    user.setUsername("operator");
    first.suggestedUsers = Arrays.asList(user);

    CompositeAuthRequest composite = new CompositeAuthRequest(first);
    composite.add(new StubAuthRequest());

    List<AuthRequest.SuggestedUser> result = composite.getSuggestedUsers();
    assertEquals(1, result.size());
    assertEquals("operator", result.get(0).getUsername());
  }

  @Test
  public void accept_isForwardedToEveryPendingConnection() {
    StubAuthRequest first = new StubAuthRequest();
    StubAuthRequest second = new StubAuthRequest();
    CompositeAuthRequest composite = new CompositeAuthRequest(first);
    composite.add(second);

    composite.accept(new HashMap<>());

    assertTrue(first.accepted);
    assertTrue(second.accepted);
  }

  @Test
  public void connectionAddedAfterAcceptance_isAcceptedImmediately() {
    CompositeAuthRequest composite = new CompositeAuthRequest(new StubAuthRequest());
    composite.accept(new HashMap<>());

    StubAuthRequest late = new StubAuthRequest();
    composite.add(late);

    assertTrue(late.accepted);
  }

  @Test
  public void reject_isForwardedToEveryPendingConnection() {
    StubAuthRequest first = new StubAuthRequest();
    StubAuthRequest second = new StubAuthRequest();
    CompositeAuthRequest composite = new CompositeAuthRequest(first);
    composite.add(second);

    composite.reject();

    assertTrue(first.rejected);
    assertTrue(second.rejected);
  }

  @Test
  public void connectionAddedAfterRejection_isRejectedImmediately() {
    CompositeAuthRequest composite = new CompositeAuthRequest(new StubAuthRequest());
    composite.reject();

    StubAuthRequest late = new StubAuthRequest();
    composite.add(late);

    assertTrue(late.rejected);
  }

  /** Minimal AuthRequest that records accept/reject and returns a settable suggested-user list. */
  private static class StubAuthRequest implements AuthRequest {
    boolean accepted;
    boolean rejected;
    List<SuggestedUser> suggestedUsers = new ArrayList<>();

    @Override public String getSystemName() { return "Sys"; }
    @Override public String getApplicationName() { return "App"; }
    @Override public URI getServerURI() { return null; }
    @Override public CDPVersion getCDPVersion() { return null; }
    @Override public Certificate[] getPeerCertificates() { return new Certificate[0]; }
    @Override public long getIdleLockoutPeriod() { return 0; }
    @Override public String getSystemUseNotification() { return ""; }
    @Override public UserAuthResult getAuthResult() { return new UserAuthResult(); }
    @Override public List<SuggestedUser> getSuggestedUsers() { return suggestedUsers; }
    @Override public void accept(Map<String, String> data) { accepted = true; }
    @Override public void reject() { rejected = true; }
  }
}
