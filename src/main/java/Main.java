import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import no.icd.studioapi.Client;
import no.icd.studioapi.Node;
import no.icd.studioapi.NotificationListener;
import no.icd.studioapi.Request.Status;
import no.icd.studioapi.proto.Studioapi.CDPValueType;
import no.icd.studioapi.RequestListener;
import no.icd.studioapi.Variant;

public class Main {
  
  static boolean firstValue = true;
	
	public static void main(String[] args) throws Exception {
	  
	  /* Start client in current thread. */
		Client client = new Client();
		client.init("192.168.1.5", 7681, new NotificationListener() {

      @Override
      public void clientReady(Client c) {
        
        Node firstChild = c.getGlobalCache().getChild(0);
        
        firstChild.requestChildNodes().setListener(new RequestListener() {
          
          @Override
          public void requestComplete(Node node, Status status) {
            System.out.println("Got node " + node.getLongName() + ", ");
            if (node.hasPolledChildren())
              throw new RuntimeException("Node has no children!!!!");
            if (node.getChildCount() > 0)
              node.getChild(0).requestChildNodes().setListener(this);
            
            if (node.getValueType() != CDPValueType.eUNDEFINED) {
              node.addPropertyChangeListener(new PropertyChangeListener() {

                @Override
                public void propertyChange(PropertyChangeEvent e) {
                  System.out.print(e.getSource() + " value changed.   old: ");
                  System.out.println(e.getOldValue() + " new: " + e.getNewValue());
                  
                }
                
              });
              firstValue = false;
            }
          }
        });
        
      }
		  
		});
		
		// Start client in separate thread.
		//new Thread(client).start();
		
		while (true) {
		  client.process();
		}
		/**/
	}

}