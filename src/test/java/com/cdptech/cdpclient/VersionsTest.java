/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import org.junit.Test;

public class VersionsTest {

  @Test
  public void events_requireCompatVersion2() {
    assertFalse(Versions.eventsSupported(1));
    assertTrue(Versions.eventsSupported(2));
    assertTrue(Versions.eventsSupported(4));
  }

  @Test
  public void requestId_requiresCompatVersion3() {
    assertFalse(Versions.requestIdSupported(2));
    assertTrue(Versions.requestIdSupported(3));
  }

  @Test
  public void servicesAndMetadata_requireCompatVersion4() {
    assertFalse(Versions.servicesSupported(3));
    assertTrue(Versions.servicesSupported(4));
    assertFalse(Versions.metadataSupported(3));
    assertTrue(Versions.metadataSupported(4));
  }

  @Test
  public void sampling_gatedOnCdpVersion() {
    assertTrue(Versions.samplingSupported(5, 0, 0));
    assertTrue(Versions.samplingSupported(4, 12, 10));
    assertTrue(Versions.samplingSupported(4, 11, 15));
    assertFalse(Versions.samplingSupported(4, 12, 9));
    assertFalse(Versions.samplingSupported(4, 11, 14));
    assertFalse(Versions.samplingSupported(4, 10, 99));
    assertFalse(Versions.samplingSupported(3, 99, 99));
  }

  @Test
  public void sampling_supportedForMinorAboveTheFixPoints() {
    // Once past a fix point, a higher patch stays supported: a 4.x minor >= 13 postdates both fix lines
    // unconditionally, and 4.12 / 4.11 above their patch thresholds (10 / 15) remain supported too.
    assertTrue(Versions.samplingSupported(4, 13, 0));
    assertTrue(Versions.samplingSupported(4, 13, 9));
    assertTrue(Versions.samplingSupported(4, 14, 0));
    assertTrue(Versions.samplingSupported(4, 12, 20));
    assertTrue(Versions.samplingSupported(4, 11, 20));
  }
}
