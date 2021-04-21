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

class Connection {

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

  private AuthRequest.UserAuthResult userAuthResult = new AuthRequest.UserAuthResult();
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
      client.requestAuthentication(new ConnectionAuthRequest(AuthRequest.RequestType.APPLICATION_ACCEPTANCE));
    });
  }

  private void setUpAuthHandler() {
    authHandler = new AuthenticationProtocol(transport, () -> {
      AuthRequest.AuthResultCode code = authHandler.getUserAuthResult().getCode();
      if (code == AuthRequest.AuthResultCode.GRANTED
          || code == AuthRequest.AuthResultCode.GRANTED_PASSWORD_WILL_EXPIRE_SOON) {
        client.requestAuthentication(new ConnectionAuthRequest(AuthRequest.RequestType.HANDSHAKE_ACCEPTANCE));
      } else {
        client.requestAuthentication(new ConnectionAuthRequest(AuthRequest.RequestType.CREDENTIALS));
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

  private class ConnectionAuthRequest implements AuthRequest {

    private final RequestType requestType;

    ConnectionAuthRequest(RequestType requestType) {
      this.requestType = requestType;
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
    public Certificate[] getTlsCertificates() {
      if (transport.getSocket() instanceof SSLSocket) {
        try {
          return ((SSLSocket)transport.getSocket()).getSession().getPeerCertificates();
        } catch (SSLPeerUnverifiedException ignored) {
        }
      }
      return new Certificate[0];
    }

    @Override
    public RequestType getType() {
      return requestType;
    }

    @Override
    public UserAuthResult getAuthResult() {
      return authHandler.getUserAuthResult();
    }

    @Override
    public void accept(Map<String, String> data) {
      if ((requestType == RequestType.APPLICATION_ACCEPTANCE && helloHandler.getChallenge().isEmpty())
          || requestType == RequestType.HANDSHAKE_ACCEPTANCE) {
        switchToIOHandler();
      } else if (requestType == RequestType.APPLICATION_ACCEPTANCE) {
        activeProtocol = authHandler;
        client.requestAuthentication(new ConnectionAuthRequest(RequestType.CREDENTIALS));
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
