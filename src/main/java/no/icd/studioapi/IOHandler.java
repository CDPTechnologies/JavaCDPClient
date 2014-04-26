package no.icd.studioapi;

import no.icd.studioapi.Node.ConnectionData;
import no.icd.studioapi.proto.Studioapi.*;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.protobuf.InvalidProtocolBufferException;

/**
 * IOHandler polls the WebSocket thread for new data and deserializes and 
 * creates events based on it. It also takes requests, serializes them and
 * forwards them to WebSocket thread.
 */
class IOHandler {

  private BlockingQueue<byte[]> queue;
  private Transport transport;
  private IOListener listener;

  /** Initialize an IOHandler with the given server URI. */
  IOHandler(URI serverUri) {
    queue = new LinkedBlockingQueue<byte[]>();
    transport = new Transport(serverUri, queue);
    listener = null;
  }

  /** Create the WebSocket connection and return true if succeeded. */
  boolean init(IOListener listener) throws Exception {
    this.listener = listener;
    return transport.connectBlocking();
  }
  
  /** Check the incoming queue and parse any messages in it. */
  void pollEvents() {
    while (!queue.isEmpty()) {  // TODO do we want to service the whole queue?
      byte[] buffer = queue.poll();
      parse(buffer);
    }
  }

  /** Create and send a structure request of a Node. */
  void nodeRequest(Node node) {
    PBContainer.Builder pb = PBContainer.newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeStructureRequest);

    if (node != null)
      pb.addStructureRequest(node.getNodeID());

    transport.send(pb.build().toByteArray());
  }
  
  /** Create a value request for a Node. Nonzero fs indicates subscription. */
  void valueRequest(Node node, double fs) {
    
    PBValueRequest.Builder pbv = PBValueRequest.newBuilder()
        .addNodeID(node.getNodeID());
    if (fs != 0.0)
      pbv.setFs(fs);
    
    PBContainer.Builder pb = PBContainer.newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeValueGetterRequest)
        .setGetterRequest(pbv);
    
    transport.send(pb.build().toByteArray());
  }

  /** Parse a message from a buffer read from the RX queue and call events. */
  private void parse(byte[] buf) {
    try {
      PBContainer pb = PBContainer.parseFrom(buf);

      switch (pb.getMessageType()) {
      case eMessageTypeStructureResponse:

        /* Create a Node and forward it to listener. */
        for (PBNode pbNode : pb.getStructureResponseList()) {
          Node node = parseNodeData(pbNode);
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
  
  /** Recursively parse a PBNode into a StudioAPI Node. */
  private Node parseNodeData(PBNode pb) {
    
    PBInfo info = pb.getInfo();
    Node node = new Node(
        info.getNodeID(),
        info.getNodeType(),
        info.getValueType(),
        info.getName());
    
    if (info.getNodeType() == CDPNodeType.CDP_APPLICATION) {
      if (info.getIsResponder()) {
        node.setConnectionData(new Node.ConnectionData());
      } else {
        node.setConnectionData(new Node.ConnectionData(
            info.getServerAddr(),
            info.getServerPort()));
      }
    }

    for (PBNode child : pb.getNodeList()) {
      node.addChild(parseNodeData(child));
    }
    
    return node;
  }

}
