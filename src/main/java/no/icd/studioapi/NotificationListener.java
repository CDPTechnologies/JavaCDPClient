/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

/** StudioAPI Client main event handler interface. */
public interface NotificationListener {

  public void clientReady(Client client);
  
  public void notificationReceived(int opcode);
  
}
