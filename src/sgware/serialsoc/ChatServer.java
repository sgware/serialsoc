package sgware.serialsoc;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

class ChatServer extends SerialServerSocket {
	
	public static final void main(String[] args) throws Exception {
		int port;
		if(args.length > 0)
			port = Integer.parseInt(args[0]);
		else
			port = 1234;
		try(ChatServer server = new ChatServer(port)) {
			server.run();
		}
	}
	
	public final int port;
	final List<ChatUser> users = new ArrayList<>();
	
	public ChatServer(int port) {
		this.port = port;
	}
	
	@Override
	protected ServerSocket createServer() throws IOException {
		return new ServerSocket(port);
	}
	
	@Override
	protected ChatUser createSocket(Socket socket) throws Exception {
		return new ChatUser(this, socket);
	}
	
	@Override
	protected void onStart() {
		System.out.println("The chat server has started.");
	}
	
	@Override
	protected void onException(Exception exception) {
		System.out.println("The chat server has crashed.");
		exception.printStackTrace();
	}
	
	@Override
	protected void onClose() {
		System.out.println("The chat server has been closed.");
	}
	
	@Override
	protected void onStop() {
		System.out.println("The chat server has stopped.");
	}
	
	public void broadcast(String message) throws Exception {
		System.out.println(message);
		for(ChatUser user : users)
			user.send(message);
	}
}