/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.time.Instant;
import java.util.ArrayList;

class TimeSync {
  private static final int SAMPLE_COUNT = 3;
  private static final double DELTA_REFRESH_RATE_NS = 10e9;
  private static final double DELTA_CHANGE_THRESHOLD_NS = 20e6;

  static class Sample {
    long packetSentTimeNs;
    long packetReceivedTimeNs;
    long remoteTimeNs;

    long getRoundTripTime() {
      return packetReceivedTimeNs - packetSentTimeNs;
    }

    long getDelta() {
      long adjustedRemote = remoteTimeNs + getRoundTripTime() / 2;
      return packetReceivedTimeNs - adjustedRemote;
    }
  }

  private ArrayList<Sample> samples = new ArrayList<>();
  private long lastRefreshTimeNs;
  private long deltaNs;
  private Runnable requestSampleFunction;
  private boolean enabled = true;

  TimeSync(Runnable requestSampleFunction) {
    this.requestSampleFunction = requestSampleFunction;
  }

  void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  void refreshDeltaIfNeeded() {
    long now = getCurrentNanoTime();
    if (enabled && now - lastRefreshTimeNs > DELTA_REFRESH_RATE_NS) {
      lastRefreshTimeNs = now;
      requestNewSample();
    }
  }

  void responseReceived(long timeNs) {
    if (samples.isEmpty()) {
      requestNewSample();
      return;
    }
    Sample sample = samples.get(samples.size() - 1);
    sample.remoteTimeNs = timeNs;
    sample.packetReceivedTimeNs = getCurrentNanoTime();
    if (samples.size() < SAMPLE_COUNT)
      requestNewSample();
    else
      updateDelta();
  }

  private void requestNewSample() {
    Sample s = new Sample();
    s.packetSentTimeNs = getCurrentNanoTime();
    samples.add(s);
    requestSampleFunction.run();
  }

  private void updateDelta() {
    Sample best = samples.get(0);
    for (int i = 1; i < samples.size(); i++)
      if (samples.get(i).getRoundTripTime() < best.getRoundTripTime())
        best = samples.get(i);

    samples.clear();
    if (deltaNs == 0 || isChangeOverThreshold(best)) {
      deltaNs = best.getDelta();
    }
  }

  long getDeltaNs() {
    return enabled ? deltaNs : 0;
  }

  private boolean isChangeOverThreshold(Sample bestSample) {
    return Math.abs(deltaNs - bestSample.getDelta()) > DELTA_CHANGE_THRESHOLD_NS;
  }

  private long getCurrentNanoTime() {
    Instant i = Instant.now();
    return i.getEpochSecond() * (long) 1e9 + i.getNano();
  }

}
