import java.net.URI;

import StudioAPIProtobuf.Studioapi.*;

import org.java_websocket.client.*;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;

public class IOHandler extends WebSocketClient {
	
	public IOHandler(URI serverUri) {
		super(serverUri);
	}

	public IOHandler(URI serverUri, Draft draft) {
		super(serverUri, draft);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void onClose(int arg0, String arg1, boolean arg2) {
		// TODO Auto-generated method stub
		System.out.println("onClose");
		
	}

	@Override
	public void onError(Exception arg0) {
		// TODO Auto-generated method stub
		System.out.println("onError " + arg0.getMessage());
		
	}

	@Override
	public void onMessage(String arg0) {
		// TODO Auto-generated method stub
		System.out.println("Got server message");
	}

	@Override
	public void onOpen(ServerHandshake arg0) {
		// TODO Auto-generated method stub
		PBContainer pb = PBContainer.newBuilder()
				.setMessageType(CDPMessageType.eMessageTypeStructureRequest)
				.build();
		byte[] serialized = pb.toByteArray();
		this.send(serialized);

		System.out.println("sent data to server");
	}

}
