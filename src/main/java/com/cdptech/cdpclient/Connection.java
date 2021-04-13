/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import javax.net.SocketFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.Certificate;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

class Connection implements AuthenticationRequest.Application {

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

  private AuthenticationRequest.UserAuthResult userAuthResult = new AuthenticationRequest.UserAuthResult();
  private RequestDispatch dispatch;
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
      if (helloHandler.getChallenge().isEmpty()) {
        client.requestAuthentication(new ConnectionAuthRequest(AuthenticationRequest.RequestType.APPLICATION_ACCEPTANCE));
      } else {
        activeProtocol = authHandler;
        client.requestAuthentication(new ConnectionAuthRequest(AuthenticationRequest.RequestType.CREDENTIALS));
      }
    });
  }

  private void setUpAuthHandler() {
    authHandler = new AuthenticationProtocol(transport, () -> {
      if (getUserAuthResult().getCode() == AuthenticationRequest.AuthResultCode.GRANTED
          || getUserAuthResult().getCode() == AuthenticationRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON) {
        client.requestAuthentication(new ConnectionAuthRequest(AuthenticationRequest.RequestType.HANDSHAKE_ACCEPTANCE));
      } else {
        client.requestAuthentication(new ConnectionAuthRequest(AuthenticationRequest.RequestType.CREDENTIALS));
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

  /** Create the WebSocket connection and return true if succeeded. */
  void init() {
    setUpTransport();
    setUpHelloHandler();
    setUpAuthHandler();
    setUpIOHandler();
    transport.connect();
    initInProgress = true;
  }

  void close() {
    dispatch.close();
    transport.close();
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

  @Override
  public String getSystemName() {
    return helloHandler.getSystemName();
  }

  @Override
  public String getApplicationName() {
    return helloHandler.getApplicationName();
  }

  @Override
  public URI getURI() {
    return serverUri;
  }

  @Override
  public AuthenticationRequest.CDPVersion getCDPVersion() {
    return new AuthenticationRequest.CDPVersion(helloHandler.getCDPVersionMajor(), helloHandler.getCDPVersionMinor());
  }

  @Override
  public Certificate[] getCertificates() {
    if (transport.getSocket() instanceof SSLSocket) {
      try {
        return ((SSLSocket)transport.getSocket()).getSession().getPeerCertificates();
      } catch (SSLPeerUnverifiedException ignored) {
      }
    }
    return new Certificate[0];
  }

  @Override
  public AuthenticationRequest.UserAuthResult getUserAuthResult() {
    return authHandler.getUserAuthResult();
  }

  private class ConnectionAuthRequest implements AuthenticationRequest {

    private final RequestType requestType;

    ConnectionAuthRequest(RequestType requestType) {
      this.requestType = requestType;
    }

    @Override
    public RequestType getType() {
      return requestType;
    }

    @Override
    public Application getApplication() {
      return Connection.this;
    }

    @Override
    public void accept(Map<String, String> data) {
      if (requestType == RequestType.APPLICATION_ACCEPTANCE || requestType == RequestType.HANDSHAKE_ACCEPTANCE) {
        switchToIOHandler();
      } else {
        authHandler.authenticate(helloHandler.getChallenge(), data);
      }
    }

    @Override
    public void reject() {
      close();
    }
  }
}
