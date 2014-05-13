/**
 * (c)2014 ICD Software AS
 */

package no.icd.studioapi;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import no.icd.studioapi.Request.Status;

public class Main { /*implements RequestListener {
  
  static boolean print = true;
  
  int mRequestCount = 1;
  Client client = new Client();
  
  public Main() throws Exception {
    
    client.init("192.168.1.127", 7681, new NotificationListener() {

      @Override
      public void clientReady(Client client) {
        
      }

      @Override
      public void notificationReceived(int opcode) {
      }
      
    });
    
  }
  
  void run() {
    while (client.getGlobalCache() == null) {
      client.process();
    }
    
    client
      .getGlobalCache()
      .getCachedChild(0)
      .requestChildNodes()
      .setListener(this);
    
    while (mRequestCount != 0)
      client.process();
    
    // now that that's done, let's do something else.
    
    client.getGlobalCache()
    .getCachedChild(0) // Application
    .getCachedChild(0) // TopLevel
    .getCachedChild(0) // Intproperty
    .subscribeToValueChanges(30, new PropertyChangeListener() {

      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        System.out.println(evt.getNewValue().toString());
      }
      
    });
    
    while (true) {
      client.process(); // and go on forever!
    }
  }
  
  public void requestComplete(Node node, Request.Status status) {
    --mRequestCount;

    for (int i = 0; i < node.getChildCount(); i++) {
      node.getCachedChild(i).requestChildNodes().setListener(this);
      ++mRequestCount;
    }
  }

  public static void main(String[] args) throws Exception {
    
    new Main().run();

  }*/

}
