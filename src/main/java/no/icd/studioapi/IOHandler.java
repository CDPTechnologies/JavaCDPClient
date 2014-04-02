package no.icd.studioapi;

import no.icd.studioapi.proto.Studioapi.*;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import com.google.protobuf.InvalidProtocolBufferException;

class IOHandler {

  private BlockingQueue<byte[]> queue;
  private Transport transport;
  private IOListener listener;

  IOHandler(URI serverUri) {
    queue = new LinkedBlockingQueue<byte[]>();
    transport = new Transport(serverUri, queue);
    listener = null;
  }

  boolean init(IOListener listener) throws Exception {
    this.listener = listener;
    return transport.connectBlocking();
  }
  
  /**
   * Check the incoming queue and parse any messages in it.
   */
  void pollEvents() {
    while (!queue.isEmpty()) {
      byte[] buffer = queue.poll();
      parse(buffer);
    }
  }

  void nodeRequest(Node node) {
    PBContainer.Builder pb = PBContainer.newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeStructureRequest);

    if (node != null)
      pb.addStructureRequest(node.getNodeID());

    transport.send(pb.build().toByteArray());
    System.out.println("<<<< OUT MESSAGE " + (node != null ? node.getNodeID() : ""));
  }

  /** 
   * Parse a message from a buffer.
   * @param buf - Byte array read from Transport queue. */
  void parse(byte[] buf) {
    try {
      PBContainer pb = PBContainer.parseFrom(buf);

      switch (pb.getMessageType()) {
      case eMessageTypeStructureResponse:

        /* Create a Node and forward it to listener. */
        for (PBNode pbNode : pb.getStructureResponseList()) {
          PBInfo info = pbNode.getInfo();
          Node node = new Node(
              info.getNodeID(),
              info.getNodeType(),
              info.getValueType(),
              info.getName());

          for (PBNode child : pbNode.getNodeList()) {
            info = child.getInfo();
            node.addChild(new Node(
                info.getNodeID(),
                info.getNodeType(),
                info.getValueType(),
                info.getName()));
          }

          listener.nodeReceived(node);
        }
        break;
      case eMessageTypeValueGetterResponse:
        
        for (PBVariantValue variant : pb.getGetterResponseList()) {
          if (variant.hasDValue())         // double
            break;
          else if (variant.hasUi64Value()) // long !
            break;
          else if (variant.hasI64Value())  // long
            break;
          else if (variant.hasFValue())    // float
            break;
          else if (variant.hasUiValue())   // int !
            break;
          else if (variant.hasIValue())    // int
            break;
          else if (variant.hasUsValue())   // int !
            break;
          else if (variant.hasSValue())    // int !
            break;
          else if (variant.hasUcValue())   // int !
            break;
          else if (variant.hasCValue())    // int !
            break;
          else if (variant.hasBValue())    // boolean
            break;
          else if (variant.hasStrValue())  // String
            break;
          else
            break;
            
        }
        
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

}
