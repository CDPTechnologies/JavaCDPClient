/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/** A Transport that records sent frames instead of writing them to a socket. */
class CapturingTransport extends Transport {
  final List<byte[]> sent = new ArrayList<>();
  byte[] last;

  CapturingTransport() throws URISyntaxException {
    super(new URI("ws://localhost:1"), new LinkedBlockingQueue<>(), e -> { });
  }

  @Override
  public void send(byte[] data) {
    sent.add(data);
    last = data;
  }
}
