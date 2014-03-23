import java.net.URISyntaxException;

import no.icd.studioapi.Client;
import no.icd.studioapi.Node;
import no.icd.studioapi.Request.Status;
import no.icd.studioapi.RequestListener;

public class Main {
	
	public static void main(String[] args) throws URISyntaxException {
		Client client = new Client();
		
		client.connect("192.168.1.5", 7681);
		
		while (true) {
		  if (client.getGlobalCache() != null)
		    break;
		  System.out.print(".");
		}
		System.out.println();
		
		
		/* TODO none of this is thread safe! */
		
		Node firstChild = client.getGlobalCache().getChild(0);
		
		firstChild.requestChildNodes().setListener(new RequestListener() {
      @Override
      public void requestComplete(Node node, Status status) {
        // This callback should actually be called in the WS thread context.
        System.out.println("Got node " + node.getLongName() + ", ");
        //assert(node.hasPolledChildren());
        try {
          if (node.getChildCount() > 0)
            node.getChild(0).requestChildNodes().setListener(this);
          else
            System.out.println("no more children to poll");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
		  
		});
	}

}