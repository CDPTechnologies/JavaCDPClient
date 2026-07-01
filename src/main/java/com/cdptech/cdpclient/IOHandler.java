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
import java.util.function.Consumer;

import static com.cdptech.cdpclient.proto.StudioAPI.RemoteErrorCode.eAUTH_RESPONSE_EXPIRED;

/**
 * IOHandler deserializes messages read from the WebSocket queue and creates events based on them.
 * It also takes requests, serializes them and forwards them to the WebSocket thread.
 */
class IOHandler implements Protocol {

  private Authenticator authenticator = new Authenticator();
  private Transport transport;
  private IOListener listener;
  private TimeSync timeSync;
  private Consumer<Long> idleLockoutPeriodChangeCallback;
  private Consumer<AuthRequest.UserAuthResult> credentialsRequester;
  private boolean reauthRequestPending;
  private String reauthChallenge;
  private Instant lastRequestTimestamp;
  private HelloProtocol helloProtocol;

  IOHandler(Transport transport, HelloProtocol helloProtocol) {
    this.transport = transport;
    this.helloProtocol = helloProtocol;
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

  boolean eventsSupported() {
    return Versions.eventsSupported(helloProtocol.getCompatVersion());
  }

  boolean requestIdSupported() {
    return Versions.requestIdSupported(helloProtocol.getCompatVersion());
  }

  boolean servicesSupported() {
    return Versions.servicesSupported(helloProtocol.getCompatVersion());
  }

  boolean metadataSupported() {
    return Versions.metadataSupported(helloProtocol.getCompatVersion());
  }

  boolean samplingSupported() {
    return Versions.samplingSupported(helloProtocol.getCDPVersionMajor(), helloProtocol.getCDPVersionMinor(),
        helloProtocol.getCDPVersionPatch());
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
      pbv.setUsValue(((Number) value.getValue()).intValue());
      break;
    case eSHORT:
      pbv.setSValue(((Number) value.getValue()).intValue());
      break;
    case eUCHAR:
      pbv.setUcValue(((Number) value.getValue()).intValue());
      break;
    case eCHAR:
      pbv.setCValue(((Number) value.getValue()).intValue());
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
  
  /** No-op: structure-subscription cancellation is not sent to the server. */
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
          AuthRequest.AuthResultCode reauthCode = authenticator.getUserAuthResult().getCode();
          if (reauthCode == AuthRequest.AuthResultCode.GRANTED
              || reauthCode == AuthRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON) {
            // The cycle is granted; a later idle lockout starts a fresh cycle that may prompt again. A
            // non-granting response keeps the cycle in progress so repeated errors stay suppressed.
            reauthRequestPending = false;
          }
          StudioAPI.AuthRequest reissue = authenticator.encryptedPasswordReissueRequest(reauthChallenge);
          if (reissue != null) {
            // Answer an EncryptedPassword challenge automatically instead of re-prompting the user.
            sendReauthMessage(reissue);
          } else if (credentialsRequester != null) {
            credentialsRequester.accept(authenticator.getUserAuthResult());
          }
          break;

        case eRemoteError:
          if (pb.getError().hasCode() || pb.getError().hasText()) {
            if (pb.getError().getCode() == eAUTH_RESPONSE_EXPIRED.getNumber()) {
              // Store the latest challenge on every error: the server issues a fresh one per expiry, so the
              // re-authentication must answer the most recent challenge even when the prompt below is suppressed.
              reauthChallenge = pb.getError().getChallenge().toStringUtf8();
              if (idleLockoutPeriodChangeCallback != null) {
                idleLockoutPeriodChangeCallback.accept(Integer.toUnsignedLong(pb.getError().getIdleLockoutPeriod()));
              }
              // Mark the re-authentication cycle in progress before prompting, so repeated
              // eAUTH_RESPONSE_EXPIRED errors (e.g. one per in-flight request during idle lockout) raise a
              // single prompt and a single re-auth request. The flag clears once the cycle is granted.
              if (!reauthRequestPending) {
                reauthRequestPending = true;
                AuthRequest.UserAuthResult userAuthResult = new AuthRequest.UserAuthResult();
                userAuthResult.setCode(AuthRequest.AuthResultCode.REAUTHENTICATION_REQUIRED);
                userAuthResult.setText(pb.getError().getText());
                credentialsRequester.accept(userAuthResult);
              }
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

  /** Recursively convert a protobuf StudioAPI.Node into this package's Node, including its children. */
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
    // The per-host clock delta applies only to a non-zero remote timestamp; a present-but-zero timestamp
    // stays zero rather than becoming the bare delta.
    long ts = (pbv.hasTimestamp() && pbv.getTimestamp() != 0) ? pbv.getTimestamp() + timeDiff : 0;
    Variant value;
    if (pbv.hasDValue())
      value = new Variant(CDPValueType.eDOUBLE, pbv.getDValue(), ts);
    else if (pbv.hasFValue())
      value = new Variant(CDPValueType.eFLOAT, pbv.getFValue(), ts);
    else if (pbv.hasUi64Value())
      value = new Variant(CDPValueType.eUINT64, pbv.getUi64Value(), ts);
    else if (pbv.hasI64Value())
      value = new Variant(CDPValueType.eINT64, pbv.getI64Value(), ts);
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
      value = new Variant(CDPValueType.eUNDEFINED, null, 0);
    return value;
  }

  void setIdleLockoutPeriodChangeCallback(Consumer<Long> idleLockoutPeriodChangeCallback) {
    this.idleLockoutPeriodChangeCallback = idleLockoutPeriodChangeCallback;
  }

  void setCredentialsRequester(Consumer<AuthRequest.UserAuthResult> credentialsRequester) {
    this.credentialsRequester = credentialsRequester;
  }

  void reauthenticate(Map<String, String> data) {
    StudioAPI.AuthRequest authMessage = authenticator.createAuthMessage(reauthChallenge, data);
    if (authMessage == null) {
      credentialsRequester.accept(authenticator.getUserAuthResult());
    } else {
      sendReauthMessage(authMessage);
    }
  }

  private void sendReauthMessage(StudioAPI.AuthRequest authMessage) {
    transport.send(Container.newBuilder()
        .setMessageType(Container.Type.eReauthRequest)
        .setReAuthRequest(authMessage)
        .build()
        .toByteArray());
    updateLastRequestTimestamp();
  }

  void clearCachedCredentials() {
    authenticator.clearCachedCredentials();
  }

  Instant getLastRequestTimestamp() {
    return lastRequestTimestamp;
  }

  private void updateLastRequestTimestamp() {
    lastRequestTimestamp = Instant.now();
  }
}
