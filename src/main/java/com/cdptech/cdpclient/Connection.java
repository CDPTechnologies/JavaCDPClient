/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import com.cdptech.cdpclient.proto.StudioAPI;

import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

class Connection {

  private static final long SECONDS_BEFORE_IDLE_LOCKOUT_TO_RESEND_ACTIVITY = 10;

  private Client client;
  private URI serverUri;
  private SocketFactory socketFactory;
  private BiConsumer<URI, SSLParameters> socketParameterHandler;
  private BlockingQueue<byte[]> queue;
  private Transport transport;

  private Protocol activeProtocol;
  private HelloProtocol helloHandler;
  private AuthenticationProtocol authHandler;
  private IOHandler ioHandler;

  private RequestDispatch dispatch;
  private Instant lastActivityNotificationTimestamp = Instant.now();
  private long idleLockoutPeriod;
  private boolean initInProgress;

  /** Initialize an IOHandler with the given server URI. */
  Connection(Client client, URI serverUri, SocketFactory socketFactory,
             BiConsumer<URI, SSLParameters> socketParameterHandler) {
    this.client = client;
    this.serverUri = serverUri;
    this.socketFactory = socketFactory;
    this.socketParameterHandler = socketParameterHandler;
    initInProgress = false;
  }

  /** Create the WebSocket connection and return true if succeeded. */
  void init() {
    setUpTransport();
    setUpHelloHandler();
    setUpAuthHandler();
    setUpIOHandler();
    setUpReauthentication();
    transport.connect();
    initInProgress = true;
  }

  void close() {
    dispatch.close();
    transport.close();
  }

  private void setUpTransport() {
    queue = new LinkedBlockingQueue<>();
    transport = new Transport(serverUri, queue, (e -> client.connectionError(serverUri, e)));
    if (socketFactory != null) {
      transport.setSocketFactory(socketFactory);
    }
    transport.setSocketParameterHandler(socketParameterHandler);
  }

  private void setUpHelloHandler() {
    helloHandler = new HelloProtocol(() -> {
      idleLockoutPeriod = helloHandler.getIdleLockoutPeriod();
      client.requestApplicationAcceptance(new ConnectionAuthRequest(authHandler.getUserAuthResult(), ignored ->  {
        if (helloHandler.getChallenge().isEmpty()) {
          switchToIOHandler();
        } else {
          activeProtocol = authHandler;
          requestCredentials();
        }
      }));
    });
  }

  private void requestCredentials() {
    client.requestCredentials(new ConnectionAuthRequest(authHandler.getUserAuthResult(), data -> {
      authHandler.authenticate(helloHandler.getChallenge(), data);
    }));
  }

  private void setUpAuthHandler() {
    authHandler = new AuthenticationProtocol(transport, () -> {
      AuthRequest.AuthResultCode code = authHandler.getUserAuthResult().getCode();
      if (code == AuthRequest.AuthResultCode.GRANTED
          || code == AuthRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON) {
        client.requestHandshakeAcceptance(new ConnectionAuthRequest(authHandler.getUserAuthResult(), data -> switchToIOHandler()));
      } else {
        requestCredentials();
      }
    });
  }

  private void setUpReauthentication() {
    ioHandler.setIdleLockoutPeriodChangeCallback((Long idleLockoutPeriod) -> this.idleLockoutPeriod = idleLockoutPeriod);
    ioHandler.setCredentialsRequester((userAuthResult, challenge) -> {
      AuthRequest.AuthResultCode code = userAuthResult.getCode();
      if (code != AuthRequest.AuthResultCode.GRANTED
          && code != AuthRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON) {
        client.requestReauthentication(new ConnectionAuthRequest(userAuthResult, data -> {
          ioHandler.reauthenticate(challenge, data);
        }));
      }
    });
  }

  private void setUpIOHandler() {
    ioHandler = new IOHandler(transport);
    dispatch = new RequestDispatch(client, ioHandler);
    ioHandler.setDispatch(dispatch);
  }

  private void switchToIOHandler() {
    dispatch.initReady(true);
    ioHandler.activate();
    activeProtocol = ioHandler;
  }

  void setTimeSync(boolean enabled) {
    if (ioHandler != null)
      ioHandler.setTimeSyncEnabled(enabled);
  }

  RequestDispatch.State getState() {
    return dispatch.getState();
  }

  RequestDispatch getDispatch() {
    return dispatch;
  }

  /** Check the incoming queue and parse any messages in it. */
  void service() {
    updateState();
    while (!queue.isEmpty()) {  // TODO do we want to service the whole queue?
      byte[] buffer = queue.poll();
      activeProtocol.parse(buffer);
    }
  }

  /** Call back state updates if monitored transport state has changed. */
  private void updateState() {
    if (initInProgress) {
      if (transport.getState() == Transport.State.CONNECTED) {
        initInProgress = false;
        activeProtocol = helloHandler;
      } else if (transport.getState() == Transport.State.DROPPED) {
        if (!serverUri.getScheme().equals("wss")) {
          tryEncryptedConnection();
        } else {
          initInProgress = false;
          dispatch.initReady(false);
        }
      }
    }
    // drop is always forwarded
    if (transport.getState() == Transport.State.DROPPED)
      dispatch.initReady(false);
  }

  private void tryEncryptedConnection() {
    try {
      serverUri = new URI("wss", null, serverUri.getHost(), serverUri.getPort(), null, null, null);
      init();
    } catch (URISyntaxException e) { // Should never happen
      throw new RuntimeException(e);
    }
  }

  URI getURI() {
    return serverUri;
  }

  void notifySiblingConnectionHadRequest(Instant siblingRequestTime) {
    if (activeProtocol != ioHandler || !isUserAuthRequired() || idleLockoutPeriod == 0) {
      return;
    }
    Instant lastRequestOrActivityTimestamp = getLastRequestOrActivityTimestamp();
    long resendTimeout = getResendTimeout();

    if (siblingRequestTime.getEpochSecond() > lastRequestOrActivityTimestamp.getEpochSecond()
        && Instant.now().getEpochSecond() - lastRequestOrActivityTimestamp.getEpochSecond() >= resendTimeout) {
      transport.send(StudioAPI.Container.newBuilder()
          .setMessageType(StudioAPI.Container.Type.eActivityNotification)
          .build()
          .toByteArray());
      lastActivityNotificationTimestamp = Instant.now();
    }
  }

  private Instant getLastRequestOrActivityTimestamp() {
    return lastActivityNotificationTimestamp.getEpochSecond() > getLastRequestTimestamp().getEpochSecond()
        ? lastActivityNotificationTimestamp : getLastRequestTimestamp();
  }

  private long getResendTimeout() {
    long resendTimeout = 1;
    if (idleLockoutPeriod > SECONDS_BEFORE_IDLE_LOCKOUT_TO_RESEND_ACTIVITY) {
      resendTimeout = idleLockoutPeriod - SECONDS_BEFORE_IDLE_LOCKOUT_TO_RESEND_ACTIVITY;
    }
    return resendTimeout;
  }

  private boolean isUserAuthRequired() {
    return !helloHandler.getChallenge().isEmpty();
  }

  Instant getLastRequestTimestamp() {
    if (activeProtocol == ioHandler) {
      return ioHandler.getLastRequestTimestamp();
    }
    return Instant.EPOCH;
  }

  private class ConnectionAuthRequest implements AuthRequest {

    private final UserAuthResult userAuthResult;
    private final Consumer<Map<String, String>> onAccept;

    ConnectionAuthRequest(UserAuthResult userAuthResult, Consumer<Map<String, String>> onAccept) {
      this.userAuthResult = userAuthResult;
      this.onAccept = onAccept;
    }

    @Override
    public String getSystemName() {
      return helloHandler.getSystemName();
    }

    @Override
    public String getApplicationName() {
      return helloHandler.getApplicationName();
    }

    @Override
    public URI getServerURI() {
      return serverUri;
    }

    @Override
    public AuthRequest.CDPVersion getCDPVersion() {
      return new AuthRequest.CDPVersion(helloHandler.getCDPVersionMajor(), helloHandler.getCDPVersionMinor());
    }

    @Override
    public Certificate[] getPeerCertificates() {
      if (transport.getSocket() instanceof SSLSocket) {
        try {
          return ((SSLSocket)transport.getSocket()).getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException ignored) {
        }
      }
      return new Certificate[0];
    }

    @Override
    public long getIdleLockoutPeriod() {
      return idleLockoutPeriod;
    }

    @Override
    public String getSystemUseNotification() {
      return helloHandler.getSystemUseNotification();
    }

    @Override
    public UserAuthResult getAuthResult() {
      return userAuthResult;
    }

    @Override
    public void accept(Map<String, String> data) {
      if (onAccept != null) {
        onAccept.accept(data);
      }
    }

    @Override
    public void reject() {
      close();
    }
  }
}
