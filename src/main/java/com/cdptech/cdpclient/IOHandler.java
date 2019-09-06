/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.protobuf.InvalidProtocolBufferException;
import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.CDPValueType;
import com.cdptech.cdpclient.proto.StudioAPI.Container;

/**
 * IOHandler polls the WebSocket thread for new data and deserializes and 
 * creates events based on it. It also takes requests, serializes them and
 * forwards them to WebSocket thread.
 */
class IOHandler {

  private BlockingQueue<byte[]> queue;
  private Transport transport;
  private IOListener listener;
  private TimeSync timeSync;
  private boolean initInProgress;

  /** Initialize an IOHandler with the given server URI. */
  IOHandler(URI serverUri) {
    queue = new LinkedBlockingQueue<byte[]>();
    transport = new Transport(serverUri, queue);
    listener = null;
    timeSync = new TimeSync(this::timeRequest);
    initInProgress = false;
  }

  /** Create the WebSocket connection and return true if succeeded. */
  void init(IOListener listener) {
    this.listener = listener;
    transport.connect();
    initInProgress = true;
  }

  public void close() {
    transport.close();
  }

  void setTimeSyncEnabled(boolean enabled) {
    timeSync.setEnabled(enabled);
  }
  
  /** Check the incoming queue and parse any messages in it. */
  void pollEvents() {
    updateState();
    while (!queue.isEmpty()) {  // TODO do we want to service the whole queue?
      byte[] buffer = queue.poll();
      parse(buffer);
      timeSync.refreshDeltaIfNeeded();
    }
  }
  
  /** Call back state updates if monitored transport state has changed. */
  void updateState() {
    if (initInProgress) {
      if (transport.getState() == Transport.State.CONNECTED) {
        initInProgress = false;
        timeSync.refreshDeltaIfNeeded();
        listener.initReady(true);
      } else if (transport.getState() == Transport.State.DROPPED) {
        initInProgress = false;
        listener.initReady(false);
      }
    }
    // drop is always forwarded
    if (transport.getState() == Transport.State.DROPPED)
      listener.initReady(false);
  }

  /** Create and send a time request. */
  void timeRequest() {
    Container.Builder pb = Container.newBuilder()
            .setMessageType(Container.Type.eCurrentTimeRequest);
    transport.send(pb.build().toByteArray());
  }

  /** Create and send a structure request of a Node. */
  void nodeRequest(Node node) {
    Container.Builder pb = Container.newBuilder()
        .setMessageType(Container.Type.eStructureRequest);

    if (node != null)
      pb.addStructureRequest(node.getNodeID());

    transport.send(pb.build().toByteArray());
  }
  
  /** Create a value request for a Node. Nonzero fs indicates subscription. */
  void valueRequest(Node node, double fs) {
    StudioAPI.ValueRequest.Builder pbv = StudioAPI.ValueRequest.newBuilder()
        .setNodeId(node.getNodeID());
    if (fs != 0.0)
      pbv.setFs(fs);
    
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eGetterRequest)
        .addGetterRequest(pbv)
        .build()
        .toByteArray());
  }
  
  /** Cancel a value subscrition to a Node. */
  void cancelValueSubscription(Node node) {
    StudioAPI.ValueRequest.Builder pbv = StudioAPI.ValueRequest.newBuilder()
        .setNodeId(node.getNodeID())
        .setStop(true);
    
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eGetterRequest)
        .addGetterRequest(pbv)
        .build()
        .toByteArray());
  }
  
  /** Create a value change request for @a node, setting it to @a value. */
  void setRemoteValue(Node node, Variant value) {
    
    StudioAPI.VariantValue.Builder pbv = StudioAPI.VariantValue.newBuilder();
    pbv.setNodeId(node.getNodeID());
    
    switch (node.getValueType()) {
    case eUNDEFINED:
      return;
    case eDOUBLE:
      pbv.setDValue((Double) value.getValue());
      break;
    case eUINT64:
      pbv.setUi64Value((Long) value.getValue());
      break;
    case eINT64:
      pbv.setI64Value((Long) value.getValue());
      break;
    case eFLOAT:
      pbv.setFValue((Float) value.getValue());
      break;
    case eUINT:
      pbv.setUiValue((Integer) value.getValue());
      break;
    case eINT:
      pbv.setIValue((Integer) value.getValue());
      break;
    case eUSHORT:
      pbv.setUsValue((Short) value.getValue());
      break;
    case eSHORT:
      pbv.setSValue((Short) value.getValue());
      break;
    case eUCHAR:
      pbv.setUcValue((Short) value.getValue());
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
    
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eSetterRequest)
        .addSetterRequest(pbv)
        .build()
        .toByteArray());
  }
  
  /** Start a structure subscription. */
  void startStructureSubscription(int nodeId) {
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eStructureRequest)
        .addStructureRequest(nodeId)
        .build()
        .toByteArray());
  }
  
  /** Cancel a structure subscription. */
  void cancelStructureSubscription(Node node) {
    // TODO (kar): Not allowed by protocol anymore?
  }

  /** Parse a message from a buffer read from the RX queue and call events. */
  private void parse(byte[] buf) {
    try {
      Container pb = Container.parseFrom(buf);

      switch (pb.getMessageType()) {
        case eStructureResponse:
        /* Create a Node and forward it to listener. */
        for (StudioAPI.Node pbNode : pb.getStructureResponseList()) {
          Node node = parseNodeData(pbNode);
          listener.nodeReceived(node);
        }
        break;

        case eGetterResponse:
        for (StudioAPI.VariantValue pbv : pb.getGetterResponseList()) {
          Variant value = createVariant(pbv, timeSync.getDeltaNs());
          listener.valueReceived(pbv.getNodeId(), value);
        }
        break;

        case eStructureChangeResponse:
        for (Integer nodeId : pb.getStructureChangeResponseList()) {
          startStructureSubscription(nodeId);
        }
        break;

        case eCurrentTimeResponse:
          timeSync.responseReceived(pb.getCurrentTimeResponse());
          break;

        case eRemoteError:
          if (pb.getError().hasCode() || pb.getError().hasText())
            System.err.println("CDP Client received following error (code " + pb.getError().getCode() + "): "
                    + pb.getError().getText());
          break;
      default:
        System.err.println("CDP Client received unparseable data from server: " + pb.getMessageType().toString());
        break;
      }

    } catch (InvalidProtocolBufferException e) {
      System.err.println("Failed to parse server data!");
    }
  }

  /** Recursively parse a StudioAPI.Node into a StudioAPI Node. */
  private Node parseNodeData(StudioAPI.Node pb) {
    
    StudioAPI.Info info = pb.getInfo();
    Node node = new Node(
        info.getNodeId(),
        info.getNodeType(),
        info.getValueType(),
        info.getName(),
        info.getFlags());
    if (info.hasTypeName())
      node.setTypeName(info.getTypeName());

    if (info.hasIsLocal()) {
      if (info.getIsLocal()) {
        node.setConnectionData(new Node.ConnectionData());
      } else {
        node.setConnectionData(new Node.ConnectionData(
            info.getServerAddr(),
            info.getServerPort()));
      }
    } else {
      node.setConnectionData(new Node.ConnectionData());
    }

    for (StudioAPI.Node child : pb.getNodeList()) {
      node.addChild(parseNodeData(child));
    }
    
    return node;
  }
  
  
  /** Create a StudioAPI Variant from a StudioAPI.VariantValue. */
  static Variant createVariant(StudioAPI.VariantValue pbv, long timeDiff) {
    long ts = pbv.hasTimestamp() ? pbv.getTimestamp() + timeDiff : 0;
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
      value = new Variant(CDPValueType.eUNDEFINED, "<no value>", 0);
    return value;
  }

}
