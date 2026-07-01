/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cdptech.cdpclient.proto.StudioAPI;

import org.junit.Test;

import java.util.List;

public class HelloProtocolTest {

  @Test
  public void suggestedUsers_areParsedFromHello() {
    StudioAPI.Hello hello = StudioAPI.Hello.newBuilder()
        .setSystemName("Sys")
        .setCompatVersion(3)
        .setIncrementalVersion(0)
        .addSuggestedUsers(StudioAPI.Hello.SuggestedUser.newBuilder()
            .setUserId("operator").setFirstName("Olaf").setLastName("Nordmann"))
        .addSuggestedUsers(StudioAPI.Hello.SuggestedUser.newBuilder()
            .setUserId("guest")) // names unset: proto defaults are empty strings
        .build();

    HelloProtocol protocol = new HelloProtocol(() -> { });
    protocol.parse(hello.toByteArray());

    List<AuthRequest.SuggestedUser> users = protocol.getSuggestedUsers();
    assertEquals(2, users.size());
    assertEquals("operator", users.get(0).getUsername());
    assertEquals("Olaf", users.get(0).getFirstName());
    assertEquals("Nordmann", users.get(0).getLastName());
    assertEquals("guest", users.get(1).getUsername());
    assertEquals("", users.get(1).getFirstName());
    assertEquals("", users.get(1).getLastName());
  }

  @Test
  public void suggestedUsers_emptyWhenHelloCarriesNone() {
    StudioAPI.Hello hello = StudioAPI.Hello.newBuilder()
        .setSystemName("Sys").setCompatVersion(3).setIncrementalVersion(0).build();

    HelloProtocol protocol = new HelloProtocol(() -> { });
    protocol.parse(hello.toByteArray());

    assertTrue(protocol.getSuggestedUsers().isEmpty());
  }

  @Test
  public void versionAccessors_reportTheHelloValues() {
    StudioAPI.Hello hello = StudioAPI.Hello.newBuilder()
        .setSystemName("Sys").setCompatVersion(4).setIncrementalVersion(0)
        .setCdpVersionMajor(4).setCdpVersionMinor(12).setCdpVersionPatch(10)
        .build();

    HelloProtocol protocol = new HelloProtocol(() -> { });
    protocol.parse(hello.toByteArray());

    assertEquals(4, protocol.getCompatVersion());
    assertEquals(4, protocol.getCDPVersionMajor());
    assertEquals(12, protocol.getCDPVersionMinor());
    assertEquals(10, protocol.getCDPVersionPatch());
  }

  @Test
  public void idleLockoutPeriod_isDecodedAsUnsigned() {
    // idle_lockout_period is uint32 on the wire; a value above Integer.MAX_VALUE must not read negative.
    StudioAPI.Hello hello = StudioAPI.Hello.newBuilder()
        .setSystemName("Sys").setCompatVersion(3).setIncrementalVersion(0)
        .setIdleLockoutPeriod(0x80000000) // 2^31, negative as a signed int
        .build();

    HelloProtocol protocol = new HelloProtocol(() -> { });
    protocol.parse(hello.toByteArray());

    assertEquals(2147483648L, protocol.getIdleLockoutPeriod());
  }
}
