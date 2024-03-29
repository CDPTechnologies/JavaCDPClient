/*
 * (c)2019 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;
import com.cdptech.cdpclient.proto.StudioAPI.CDPValueType;
import com.cdptech.cdpclient.proto.StudioAPI.Container;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import java.time.Instant;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static com.cdptech.cdpclient.proto.StudioAPI.RemoteErrorCode.eAUTH_RESPONSE_EXPIRED;

/**
 * IOHandler polls the WebSocket thread for new data and deserializes and 
 * creates events based on it. It also takes requests, serializes them and
 * forwards them to WebSocket thread.
 */
class IOHandler implements Protocol {

  private Authenticator authenticator = new Authenticator();
  private Transport transport;
  private IOListener listener;
  private TimeSync timeSync;
  private Consumer<Long> idleLockoutPeriodChangeCallback;
  private BiConsumer<AuthRequest.UserAuthResult, String> credentialsRequester;
  private Instant lastRequestTimestamp;

  /** Initialize an IOHandler with the given server URI. */
  IOHandler(Transport transport) {
    this.transport = transport;
    timeSync = new TimeSync(this::timeRequest);
  }

  void activate() {
    timeSync.refreshDeltaIfNeeded();
  }

  void setDispatch(IOListener listener) {
    this.listener = listener;
  }

  void setTimeSyncEnabled(boolean enabled) {
    timeSync.setEnabled(enabled);
  }

  /** Create and send a time request. */
  private void timeRequest() {
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
    updateLastRequestTimestamp();
  }

  void addChildRequest(Node parentNode, String childName, String childTypeName) {
    Container.Builder pb = Container.newBuilder()
        .setMessageType(Container.Type.eChildAddRequest);

    if (parentNode != null) {
      pb.addChildAddRequest(StudioAPI.ChildAdd.newBuilder()
          .setParentNodeId(parentNode.getNodeID())
          .setChildName(childName)
          .setChildTypeName(childTypeName).build());
    }

    transport.send(pb.build().toByteArray());
    updateLastRequestTimestamp();
  }

  void removeChildRequest(Node parentNode, String childName) {
    Container.Builder pb = Container.newBuilder()
        .setMessageType(Container.Type.eChildRemoveRequest);

    if (parentNode != null) {
      pb.addChildRemoveRequest(StudioAPI.ChildRemove.newBuilder()
          .setParentNodeId(parentNode.getNodeID())
          .setChildName(childName).build());
    }

    transport.send(pb.build().toByteArray());
    updateLastRequestTimestamp();
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
    updateLastRequestTimestamp();
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
    updateLastRequestTimestamp();
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
    updateLastRequestTimestamp();
  }
  
  /** Start a structure subscription. */
  void startStructureSubscription(int nodeId) {
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eStructureRequest)
        .addStructureRequest(nodeId)
        .build()
        .toByteArray());
    updateLastRequestTimestamp();
  }
  
  /** Cancel a structure subscription. */
  void cancelStructureSubscription(Node node) {
    // TODO (kar): Not allowed by protocol anymore?
  }

  /** Parse a message from a buffer read from the RX queue and call events. */
  public void parse(byte[] buf) {
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

        case eReauthResponse:
          authenticator.updateUserAuthResult(pb.getReAuthResponse());
          if (credentialsRequester != null) {
            credentialsRequester.accept(authenticator.getUserAuthResult(), null);
          }
          break;

        case eRemoteError:
          if (pb.getError().hasCode() || pb.getError().hasText()) {
            if (pb.getError().getCode() == eAUTH_RESPONSE_EXPIRED.getNumber()) {
              String challenge = pb.getError().getChallenge().toStringUtf8();
              if (idleLockoutPeriodChangeCallback != null) {
                idleLockoutPeriodChangeCallback.accept(Integer.toUnsignedLong(pb.getError().getIdleLockoutPeriod()));
              }
              AuthRequest.UserAuthResult userAuthResult = new AuthRequest.UserAuthResult();
              userAuthResult.setCode(AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED);
              userAuthResult.setText(pb.getError().getText());
              credentialsRequester.accept(userAuthResult, challenge.toString());
            } else {
              System.err.println("CDP Client received following error (code " + pb.getError().getCode() + "): "
                  + pb.getError().getText());
            }
          }
          break;
      default:
        System.err.println("CDP Client received unparseable data from server: " + pb.getMessageType().toString());
        break;
      }

    } catch (InvalidProtocolBufferException e) {
      System.err.println("Failed to parse server data!");
    }
    timeSync.refreshDeltaIfNeeded();
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

  void setIdleLockoutPeriodChangeCallback(Consumer<Long> idleLockoutPeriodChangeCallback) {
    this.idleLockoutPeriodChangeCallback = idleLockoutPeriodChangeCallback;
  }

  void setCredentialsRequester(BiConsumer<AuthRequest.UserAuthResult, String> credentialsRequester) {
    this.credentialsRequester = credentialsRequester;
  }

  void reauthenticate(String challenge, Map<String, String> data) {
    StudioAPI.AuthRequest authMessage = authenticator.createAuthMessage(challenge, data);
    if (authMessage == null) {
      credentialsRequester.accept(authenticator.getUserAuthResult(), challenge);
    } else {
      transport.send(Container.newBuilder()
          .setMessageType(Container.Type.eReauthRequest)
          .setReAuthRequest(authMessage)
          .build()
          .toByteArray());
      updateLastRequestTimestamp();
    }
  }

  Instant getLastRequestTimestamp() {
    return lastRequestTimestamp;
  }

  private void updateLastRequestTimestamp() {
    lastRequestTimestamp = Instant.now();
  }
}
