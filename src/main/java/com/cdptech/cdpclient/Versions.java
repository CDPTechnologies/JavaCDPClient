/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

/** Feature availability from the server-advertised Hello.compat_version (and the CDP runtime version for sampling). */
final class Versions {

  // Compat-version thresholds at which each StudioAPI feature became available on the server.
  private static final int COMPAT_VERSION_WITH_EVENTS_SUPPORT = 2;
  private static final int COMPAT_VERSION_WITH_REQUEST_ID_SUPPORT = 3;
  private static final int COMPAT_VERSION_WITH_SERVICES_SUPPORT = 4;
  private static final int COMPAT_VERSION_WITH_METADATA_SUPPORT = 4;

  private Versions() {
  }

  /** Event subscriptions (eEventRequest/eEventResponse). */
  static boolean eventsSupported(int compatVersion) {
    return compatVersion >= COMPAT_VERSION_WITH_EVENTS_SUPPORT;
  }

  /**
   * TCP-resilience features: request_ids echo, inactivity_resend_interval, and setter responses
   * carrying the actual value. These all became available at the same server compat version.
   */
  static boolean requestIdSupported(int compatVersion) {
    return compatVersion >= COMPAT_VERSION_WITH_REQUEST_ID_SUPPORT;
  }

  /** Generic custom services and proxy (eServicesRequest/eServicesNotification/eServiceMessage). */
  static boolean servicesSupported(int compatVersion) {
    return compatVersion >= COMPAT_VERSION_WITH_SERVICES_SUPPORT;
  }

  /** Metadata subscriptions (eMetadataRequest/eMetadataResponse). */
  static boolean metadataSupported(int compatVersion) {
    return compatVersion >= COMPAT_VERSION_WITH_METADATA_SUPPORT;
  }

  /**
   * Value sampling (ValueRequest.sample_rate), gated on the CDP runtime version, not compat_version:
   * fixed in the 4.11 line at 4.11.15 and in the 4.12 line at 4.12.10, so supported from 4.11.15,
   * from 4.12.10, and unconditionally from 4.13.0 onward (and on 5.x).
   */
  static boolean samplingSupported(int cdpVersionMajor, int cdpVersionMinor, int cdpVersionPatch) {
    if (cdpVersionMajor >= 5) {
      return true;
    }
    if (cdpVersionMajor >= 4) {
      if (cdpVersionMinor >= 13) {
        return true;
      }
      if (cdpVersionMinor == 12) {
        return cdpVersionPatch >= 10;
      }
      if (cdpVersionMinor == 11) {
        return cdpVersionPatch >= 15;
      }
    }
    return false;
  }
}
