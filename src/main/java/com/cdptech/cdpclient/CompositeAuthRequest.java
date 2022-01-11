package com.cdptech.cdpclient;

import java.net.URI;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class CompositeAuthRequest implements AuthRequest {

  private List<AuthRequest> requests = new ArrayList<>();
  private Map<String, String> cachedData = new HashMap<>();

  private Instant readyTimestamp;
  private boolean accepted;
  private boolean rejected;

  CompositeAuthRequest(AuthRequest firstRequest) {
    requests.add(firstRequest);
  }

  void add(AuthRequest request) {
    if (accepted) {
      request.accept(cachedData);
    } else if (rejected) {
      request.reject();
    } else {
      requests.add(request);
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
  public void accept(Map<String, String> data) {
    cachedData = data;
    accepted = true;
    readyTimestamp = Instant.now();
    for (AuthRequest r : requests) {
      r.accept(cachedData);
    }
  }

  @Override
  public void reject() {
    rejected = true;
    readyTimestamp = Instant.now();
    for (AuthRequest r : requests) {
      r.reject();
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
