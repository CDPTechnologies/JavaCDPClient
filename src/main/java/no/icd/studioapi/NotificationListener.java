/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

/** 
 * StudioAPI Client main event handler interface.
 * @author kpu@icd.no
 */
public interface NotificationListener {

  /** Called when all connections have been created and requests can be made. */
  public void clientReady(Client client);
  
  /** Called when all connections are lost and the client is reset. */
  public void clientClosed(Client client);
  
}
