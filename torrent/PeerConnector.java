package torrent;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import org.johnnei.utils.ThreadUtils;

import torrent.download.peer.Peer;

public class PeerConnector extends Thread {
	
	private ServerSocket serverSocket;
	
	public PeerConnector() throws IOException {
		super("PeerConnector");
		serverSocket = new ServerSocket(27960);
	}
	
	public void run() {
		while(true) {
			try {
				Socket peerSocket = serverSocket.accept();
				Peer peer = new Peer();
				peer.setSocket(peerSocket);
				peer.log("Connected from outside");
				long handshakeStart = System.currentTimeMillis();
				while(!peer.canReadMessage() && (System.currentTimeMillis() - handshakeStart) < 10) {
					ThreadUtils.sleep(10);
				}
				if(peer.canReadMessage()) {
					peer.processHandshake();
					if(peer.getPassedHandshake()) {
						peer.sendHandshake();
						peer.getTorrent().addPeer(peer);
					}
				} else {
					peer.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}