/*
 * (c)2021 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.*;

/**
 * This class accepts all certificates from the server, even when hostname does not match
 * or the certificate is self-signed.
 */
class TrustingSSLSocketFactory extends SSLSocketFactory {

  private SSLContext sslContext = SSLContext.getInstance("TLS");

  public TrustingSSLSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
    super();

    TrustManager tm = new X509ExtendedTrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine) {
      }

      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }
    };

    sslContext.init(null, new TrustManager[] { tm }, null);
  }

  @Override
  public String[] getDefaultCipherSuites() {
    return sslContext.getSocketFactory().getDefaultCipherSuites();
  }

  @Override
  public String[] getSupportedCipherSuites() {
    return sslContext.getSocketFactory().getSupportedCipherSuites();
  }

  @Override
  public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
    return sslContext.getSocketFactory().createSocket(socket, host, port, autoClose);
  }

  @Override
  public Socket createSocket() throws IOException {
    return sslContext.getSocketFactory().createSocket();
  }

  @Override
  public Socket createSocket(String s, int i) throws IOException {
    return sslContext.getSocketFactory().createSocket(s, i);
  }

  @Override
  public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
    return sslContext.getSocketFactory().createSocket(s, i, inetAddress, i1);
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
    return sslContext.getSocketFactory().createSocket(inetAddress, i);
  }

  @Override
  public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
    return sslContext.getSocketFactory().createSocket(inetAddress, i, inetAddress1, i1);
  }
}