/*
 * (c)2019 CDP Technologies AS
 */

package no.icd.studioapi;

/** 
 * StudioAPI Client main event handler interface.
 */
public interface NotificationListener {

  /** Called when all connections have been created and requests can be made. */
  public void clientReady(Client client);
  
  /**
   * Called when all connections are lost and the client is reset.
   *
   * @apiNote Will not be called if automatic reconnect is true.
   */
  public void clientClosed(Client client);
  
}
