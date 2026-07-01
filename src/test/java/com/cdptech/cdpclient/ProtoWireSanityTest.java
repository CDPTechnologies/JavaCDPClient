/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.Container;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.Test;

/**
 * Guards that the build-time-generated StudioAPI proto exposes the message types and fields later
 * migration phases will use (events, services, metadata, resilience) and that they round-trip on the
 * wire, so a regression of studioapi.proto to an older schema is caught early. The client does not
 * construct these messages yet; this is purely a schema-regression guard.
 */
public class ProtoWireSanityTest {

  @Test
  public void eventRequest_andRequestIds_roundTrip() throws InvalidProtocolBufferException {
    Container original = Container.newBuilder()
        .setMessageType(Container.Type.eEventRequest)
        .addEventRequest(StudioAPI.EventRequest.newBuilder()
            .setNodeId(42)
            .setStartingFrom(1234567890L)
            .setInactivityResendInterval(120))
        .addRequestIds(7)
        .build();

    Container parsed = Container.parseFrom(original.toByteArray());
    assertEquals(Container.Type.eEventRequest, parsed.getMessageType());
    assertEquals(42, parsed.getEventRequest(0).getNodeId());
    assertEquals(1234567890L, parsed.getEventRequest(0).getStartingFrom());
    assertEquals(120, parsed.getEventRequest(0).getInactivityResendInterval());
    assertEquals(7, parsed.getRequestIds(0));
  }

  @Test
  public void valueRequest_sampleRateAndInactivity_roundTrip() throws InvalidProtocolBufferException {
    Container original = Container.newBuilder()
        .setMessageType(Container.Type.eGetterRequest)
        .addGetterRequest(StudioAPI.ValueRequest.newBuilder()
            .setNodeId(3)
            .setFs(10.0)
            .setSampleRate(100.0)
            .setInactivityResendInterval(30))
        .build();

    StudioAPI.ValueRequest parsed = Container.parseFrom(original.toByteArray()).getGetterRequest(0);
    assertEquals(10.0, parsed.getFs(), 0.0);
    assertEquals(100.0, parsed.getSampleRate(), 0.0);
    assertEquals(30, parsed.getInactivityResendInterval());
  }

  @Test
  public void servicesAndMetadata_messagesExist() throws InvalidProtocolBufferException {
    Container original = Container.newBuilder()
        .setMessageType(Container.Type.eServicesRequest)
        .setServicesRequest(StudioAPI.ServicesRequest.newBuilder().setSubscribe(true))
        .addMetadataRequest(StudioAPI.MetadataRequest.newBuilder().setNodeId(9))
        .build();

    Container parsed = Container.parseFrom(original.toByteArray());
    assertTrue(parsed.getServicesRequest().getSubscribe());
    assertEquals(9, parsed.getMetadataRequest(0).getNodeId());
  }
}
