package no.icd.studioapi;

import no.icd.studioapi.proto.Studioapi.*;

import java.net.URI;
import java.nio.ByteBuffer;

import org.java_websocket.client.*;
import org.java_websocket.drafts.Draft_17;
import org.java_websocket.handshake.ServerHandshake;

import com.google.protobuf.InvalidProtocolBufferException;

public class IOHandler extends WebSocketClient {

  private IOListener listener;

  public IOHandler(URI serverUri) {
    super(serverUri, new Draft_17());
    listener = null;
  }

  public void setListener(IOListener listener) {
    this.listener = listener;
  }

  public void nodeRequest(Node node) {
    PBContainer.Builder pb = PBContainer.newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeStructureRequest);

    if (node != null)
      pb.addStructureRequest(node.getNodeID());

    this.send(pb.build().toByteArray());
    System.out.println("<<<< OUT MESSAGE " + (node != null ? node.getNodeID() : ""));
  }

  /** Handle incoming server messages. StudioAPI only uses binary data. */
  @Override
  public void onMessage(ByteBuffer buf) {
    System.out.println(">>>> INC MESSAGE");
    try {
      PBContainer pb = PBContainer.parseFrom(buf.array());

      switch (pb.getMessageType()) {
      case eMessageTypeStructureResponse:

        /* Create a Node and forward it to listener. */
        for (PBNode pbNode : pb.getStructureResponseList()) {
          PBInfo info = pbNode.getInfo();
          Node node = new Node(
              info.getNodeID(), 
              info.getObjectHandle(),
              info.getNodeType(),
              info.getValueType(),
              info.getName());

          for (PBNode child : pbNode.getNodeList()) {
            info = child.getInfo();
            node.addChild(new Node(
                info.getNodeID(), 
                info.getObjectHandle(),
                info.getNodeType(),
                info.getValueType(),
                info.getName()));
          }

          listener.nodeReceived(node);
        }
        break;
      case eMessageTypeValueGetterResponse:
        // TODO
        break;
      case eMessageTypeStructureChangeResponse:
        // TODO
        break;
      case eMessageTypeOther:
        // TODO
        break;
      default:
        // unknown server data
        break;
      }

    } catch (InvalidProtocolBufferException e) {
      System.err.println("Failed to parse server data!");
    }
  }

  @Override
  public void onOpen(ServerHandshake arg0) {
    System.err.println("Connection to " + this.uri + " established.");
  }

  @Override
  public void onClose(int arg0, String arg1, boolean arg2) {
    System.err.println("Connection to " + this.uri + " closed.");
  }

  @Override
  public void onError(Exception e) {
    System.err.println("WebSocket thread caught exception!");
    e.printStackTrace();
  }

  /** Unused but needs to be defined for java-websocket. */
  @Override
  public void onMessage(String s) {
    System.out.println("Got server message: " + s);
  }

}
