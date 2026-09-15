package com.cdptech.cdpclient;

import java.net.URI;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CompositeAuthRequest implements AuthRequest {

  private List<ReauthRequest> requests = new ArrayList<>();
  private Map<String, String> cachedData = new HashMap<>();

  private Instant readyTimestamp;
  private boolean accepted;
  private boolean rejected;

  CompositeAuthRequest(ReauthRequest firstRequest) {
    requests.add(firstRequest);
  }

  void add(ReauthRequest request) {
    if (accepted) {
      deliver(request, true);
    } else if (rejected) {
      deliver(request, false);
    } else {
      requests.add(request);
    }
  }

  /** Hands this composite's answer to one connection, which remembers where it came from. */
  private void deliver(ReauthRequest request, boolean accept) {
    request.setAnsweringPrompt(this);
    if (accept) {
      request.accept(cachedData);
    } else {
      request.reject();
    }
  }

  @Override
  public String getSystemName() {
    return requests.get(0).getSystemName();
  }

  @Override
  public String getApplicationName() {
    return requests.get(0).getApplicationName();
  }

  @Override
  public URI getServerURI() {
    return requests.get(0).getServerURI();
  }

  @Override
  public CDPVersion getCDPVersion() {
    return requests.get(0).getCDPVersion();
  }

  @Override
  public Certificate[] getPeerCertificates() {
    return requests.get(0).getPeerCertificates();
  }

  @Override
  public long getIdleLockoutPeriod() {
    return requests.get(0).getIdleLockoutPeriod();
  }

  @Override
  public String getSystemUseNotification() {
    return requests.get(0).getSystemUseNotification();
  }

  @Override
  public UserAuthResult getAuthResult() {
    return requests.get(0).getAuthResult();
  }

  @Override
  public List<SuggestedUser> getSuggestedUsers() {
    return requests.get(0).getSuggestedUsers();
  }

  @Override
  public void accept(Map<String, String> data) {
    cachedData = data;
    accepted = true;
    readyTimestamp = Instant.now();
    for (ReauthRequest r : requests) {
      deliver(r, true);
    }
  }

  @Override
  public void reject() {
    rejected = true;
    readyTimestamp = Instant.now();
    for (ReauthRequest r : requests) {
      deliver(r, false);
    }
  }

  boolean isReady() {
    return readyTimestamp != null;
  }

  public Instant getReadyTimestamp() {
    return readyTimestamp;
  }

  public boolean isAccepted() {
    return accepted;
  }

  public boolean isRejected() {
    return rejected;
  }

}
