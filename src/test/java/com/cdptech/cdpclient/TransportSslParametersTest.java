/*
 * (c)2026 CDP Technologies AS
 */

package com.cdptech.cdpclient;

import static org.junit.Assert.*;

import org.junit.Test;

import javax.net.ssl.SSLParameters;
import java.io.File;
import java.io.FileWriter;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiConsumer;

public class TransportSslParametersTest {

  /** Self-signed certificate (CN=wronghost) used only as trust-anchor input to setTrustedCertificates. */
  private static final String TEST_CERTIFICATE_PEM = "-----BEGIN CERTIFICATE-----\n"
      + "MIIC5DCCAcygAwIBAgIJAPvc08DcCM6KMA0GCSqGSIb3DQEBDAUAMBQxEjAQBgNV\n"
      + "BAMTCXdyb25naG9zdDAgFw0yNjA3MTYxNzI0MjJaGA8yMTI2MDYyMjE3MjQyMlow\n"
      + "FDESMBAGA1UEAxMJd3Jvbmdob3N0MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n"
      + "CgKCAQEA5mB07aPpNzmSB/0FZn3zCZAY3cEvZoA/3bGLQL2u+PPV5sojrugS6b26\n"
      + "zXvCR0YYBF6/pq5GET/7xu78+bwYtJ1f22DuhDyEYpBPaYYRqvtuj3HCi8/QVjIH\n"
      + "qxvRoa/oiUSRoRJvJViRvCFEvlCVWHazy54+hGc0cHdXlJInxbdC+eGRq6+C3lC5\n"
      + "tl2uYRlKqamJ93kGYxP7cRXNc6G8GaTd9N1GTPqeXRRLuRFosRpqb7kRAscPBKHB\n"
      + "IDu+zn9ZQH/85aC6gL5iMnMMV1rZWVcO/cHsdlGtAdX1XRw+mr4DQ+P9ABJE99Qn\n"
      + "O3Cph62XQsjMtjYZebhjhu+tKUsDWQIDAQABozcwNTAdBgNVHQ4EFgQUjbME3qvH\n"
      + "nS9CHuRvmrZCFIor/p0wFAYDVR0RBA0wC4IJd3Jvbmdob3N0MA0GCSqGSIb3DQEB\n"
      + "DAUAA4IBAQBJU1nbcwlQUVFUp+2m52Ta9QA2sDdGZih1ku1Y4aXdtGyi/0asu0vn\n"
      + "kCsTxI6RzAP/J9IG+AleQ5QFTm0pE2y5Ajq+id/yg2E5EoSN6OAJzIlgfgxKOmzl\n"
      + "8NwGVpD9DenoEthPM8sZS6jrV2ndac325JP7rQ7DHjesGOxDCJSr4Rdumk8bCO23\n"
      + "D0xQ4c0BPtWTovBqRBZreiyw3mART3AZr/eVyFNcX0x5h1fprs/ERZSfuNl4HyJt\n"
      + "oItOcde0djqKx7+jK9NVTLAz7eBkJFZI9gI8NOqEGYcV9QbrZUjCuNx48cESVCfm\n"
      + "elgaGU1ZVd3/qYaksqCYfBQcKTMv1YAC\n"
      + "-----END CERTIFICATE-----\n";

  @Test
  public void transportWithoutHandler_enablesEndpointIdentification() {
    SSLParameters parameters = new SSLParameters();

    newTransport().onSetSSLParameters(parameters);

    assertEquals("HTTPS", parameters.getEndpointIdentificationAlgorithm());
  }

  @Test
  public void setTrustedCertificates_endpointIdentificationEnabled_keepsEndpointIdentification() throws Exception {
    Client client = new Client();
    client.setTrustedCertificates(Collections.singletonList(certificateFile()), true);

    SSLParameters parameters = applySslParameterHandling(client);

    assertEquals("HTTPS", parameters.getEndpointIdentificationAlgorithm());
  }

  @Test
  public void setTrustedCertificates_endpointIdentificationDisabled_disablesHostnameVerification() throws Exception {
    Client client = new Client();
    client.setTrustedCertificates(Collections.singletonList(certificateFile()), false);

    SSLParameters parameters = applySslParameterHandling(client);

    assertNull(parameters.getEndpointIdentificationAlgorithm());
  }

  /** Runs the client's configured socket parameter handler through the real Transport callback. */
  private static SSLParameters applySslParameterHandling(Client client) throws Exception {
    Field field = Client.class.getDeclaredField("socketParameterHandler");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    BiConsumer<URI, SSLParameters> handler = (BiConsumer<URI, SSLParameters>) field.get(client);

    Transport transport = newTransport();
    transport.setSocketParameterHandler(handler);
    SSLParameters parameters = new SSLParameters();
    transport.onSetSSLParameters(parameters);
    return parameters;
  }

  private static Transport newTransport() {
    try {
      return new Transport(new URI("wss://localhost:1"), new LinkedBlockingQueue<>(), e -> { });
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static File certificateFile() throws Exception {
    File file = File.createTempFile("cdpclient-test-cert", ".crt");
    file.deleteOnExit();
    try (FileWriter writer = new FileWriter(file)) {
      writer.write(TEST_CERTIFICATE_PEM);
    }
    return file;
  }
}
