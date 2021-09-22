/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI.Hello;
import com.google.protobuf.InvalidProtocolBufferException;

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

  int getCDPVersionMajor() {
    return helloMessage.getCdpVersionMajor();
  }

  int getCDPVersionMinor() {
    return helloMessage.getCdpVersionMinor();
  }
}
