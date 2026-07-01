/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI.Hello;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.List;

class HelloProtocol implements Protocol {

  private Hello helloMessage;
  private Runnable finishedCallback;

  HelloProtocol(Runnable finishedCallback) {
    this.finishedCallback = finishedCallback;
  }

  @Override
  public void parse(byte[] buf) {
    try {
      helloMessage = Hello.parseFrom(buf);
      finishedCallback.run();
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException(e);
    }
  }

  String getSystemName() {
    return helloMessage.getSystemName();
  }

  String getApplicationName() {
    return helloMessage.getApplicationName();
  }

  String getChallenge() {
    return helloMessage.getChallenge().toStringUtf8();
  }

  int getCompatVersion() {
    return helloMessage.getCompatVersion();
  }

  int getCDPVersionMajor() {
    return helloMessage.getCdpVersionMajor();
  }

  int getCDPVersionMinor() {
    return helloMessage.getCdpVersionMinor();
  }

  int getCDPVersionPatch() {
    return helloMessage.getCdpVersionPatch();
  }

  public String getSystemUseNotification() {
    return helloMessage.getSystemUseNotification();
  }

  public long getIdleLockoutPeriod() {
    return Integer.toUnsignedLong(helloMessage.getIdleLockoutPeriod());
  }

  List<AuthRequest.SuggestedUser> getSuggestedUsers() {
    List<AuthRequest.SuggestedUser> users = new ArrayList<>();
    for (Hello.SuggestedUser u : helloMessage.getSuggestedUsersList()) {
      AuthRequest.SuggestedUser user = new AuthRequest.SuggestedUser();
      user.setUsername(u.getUserId());
      user.setFirstName(u.getFirstName());
      user.setLastName(u.getLastName());
      users.add(user);
    }
    return users;
  }
}
