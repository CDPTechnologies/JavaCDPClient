/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import no.icd.studioapi.Node.ConnectionData;
import no.icd.studioapi.Transport.State;
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
  private boolean initInProgress;

  /** Initialize an IOHandler with the given server URI. */
  IOHandler(URI serverUri) {
    queue = new LinkedBlockingQueue<byte[]>();
    transport = new Transport(serverUri, queue);
    listener = null;
    initInProgress = false;
  }

  /** Create the WebSocket connection and return true if succeeded. */
  void init(IOListener listener) {
    this.listener = listener;
    transport.connect();
    initInProgress = true;
  }
  
  /** Check the incoming queue and parse any messages in it. */
  void pollEvents() {
    updateState();
    while (!queue.isEmpty()) {  // TODO do we want to service the whole queue?
      byte[] buffer = queue.poll();
      parse(buffer);
    }
  }
  
  /** Call back state updates if monitored transport state has changed. */
  void updateState() {
    if (initInProgress) {
      if (transport.getState() == State.CONNECTED) {
        initInProgress = false;
        listener.initReady(true);
      } else if (transport.getState() == State.DROPPED) {
        initInProgress = false;
        listener.initReady(false);
      }
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
  
  /** Cancel a value subscrition to a Node. */
  void cancelValueSubscription(Node node) {
    PBValueRequest.Builder pbv = PBValueRequest.newBuilder()
        .addNodeID(node.getNodeID())
        .setStop(true);
    
    PBContainer.Builder pb = PBContainer.newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeValueGetterRequest)
        .setGetterRequest(pbv);
    
    transport.send(pb.build().toByteArray());
  }
  
  /** Create a value change request for @a node, setting it to @a value. */
  void setRemoteValue(Node node, Variant value) {
    
    PBVariantValue.Builder pbv = PBVariantValue.newBuilder();
    pbv.setNodeID(node.getNodeID());
    
    switch (node.getValueType()) {
    case eUNDEFINED:
      return;
    case eDOUBLE:
      pbv.setDValue((Double) value.getValue());
      break;
    case eUINT64:
      pbv.setUi64Value((Long) value.getValue()); // TODO
      break;
    case eINT64:
      pbv.setI64Value((Long) value.getValue());
      break;
    case eFLOAT:
      pbv.setFValue((Float) value.getValue());
      break;
    case eUINT:
      pbv.setUiValue((Integer) value.getValue()); // TODO
      break;
    case eINT:
      pbv.setIValue((Integer) value.getValue());
      break;
    case eUSHORT:
      pbv.setUsValue((Short) value.getValue()); // TODO
      break;
    case eSHORT:
      pbv.setSValue((Short) value.getValue());
      break;
    case eUCHAR:
      pbv.setUcValue((Short) value.getValue()); // TODO
      break;
    case eCHAR:
      pbv.setCValue((Byte) value.getValue());
      break;
    case eBOOL:
      pbv.setBValue((Boolean) value.getValue());
      break;
    case eSTRING:
      pbv.setStrValue((String) value.getValue());
      break;
    }
    
    transport.send(PBContainer
        .newBuilder()
        .setMessageType(CDPMessageType.eMessageTypeValueSetterRequest)
        .addSetterRequest(pbv)
        .build()
        .toByteArray());
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
        
        //for (PBVariantValue pbv : pb.getGetterResponseList()) {
        PBVariantValue pbv = pb.getGetterResponse(0);
          Variant value = createVariant(pbv);
          listener.valueReceived(pbv.getNodeID(), value);  
        //}
        
        break;
      case eMessageTypeStructureChangeResponse:
        // TODO
        break;
      case eMessageTypeOther:
        // TODO Not yet implemented in protocol.
        break;
      default:
        System.err.println("StudioAPI received unparseable data from server.");
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
  
  
  /** Create a StudioAPI Variant from a PBVariantValue. */
  static Variant createVariant(PBVariantValue pbv) {
    double ts = pbv.hasTimeStamp() ? pbv.getTimeStamp() : 0.0;
    Variant value;
    if (pbv.hasDValue())
      value = new Variant(CDPValueType.eDOUBLE, pbv.getDValue(), ts);
    else if (pbv.hasUi64Value())
      value = new Variant(CDPValueType.eUINT64, pbv.getUi64Value(), ts);
    else if (pbv.hasI64Value())
      value = new Variant(CDPValueType.eINT64, pbv.getI64Value(), ts);
    else if (pbv.hasFValue())
      value = new Variant(CDPValueType.eFLOAT, pbv.getFValue(), ts);
    else if (pbv.hasUiValue())
      value = new Variant(CDPValueType.eUINT, pbv.getUiValue(), ts);
    else if (pbv.hasIValue())
      value = new Variant(CDPValueType.eINT, pbv.getIValue(), ts);
    else if (pbv.hasUsValue())
      value = new Variant(CDPValueType.eUSHORT, pbv.getUsValue(), ts);
    else if (pbv.hasSValue())
      value = new Variant(CDPValueType.eSHORT, pbv.getSValue(), ts);
    else if (pbv.hasUcValue())
      value = new Variant(CDPValueType.eUCHAR, pbv.getUcValue(), ts);
    else if (pbv.hasCValue())
      value = new Variant(CDPValueType.eCHAR, pbv.getCValue(), ts);
    else if (pbv.hasBValue())
      value = new Variant(CDPValueType.eBOOL, pbv.getBValue(), ts);
    else if (pbv.hasStrValue())
      value = new Variant(CDPValueType.eSTRING, pbv.getStrValue(), ts);
    else
      value = new Variant(CDPValueType.eUNDEFINED, "<no value>", 0.0);
    return value;
  }

}
