package com.sgware.serialsoc;

import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.net.Socket;

class ChatServer extends SimpleSerialServerSocket {
	
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
	
	final List<ChatUser> users = new ArrayList<>();
	
	public ChatServer(int port) throws IOException {
		super(port);
	}
		
	@Override
	protected ChatUser create(Socket socket) throws IOException {
		return new ChatUser(this, socket);
	}
	
	@Override
	protected void onStart() {
		System.out.println("The chat server has started.");
	}
	
	@Override
	protected void onConnect() {
		System.out.println("The chat server is now accepting new connections.");
	}
	
	@Override
	protected void onException(Exception exception) {
		System.out.println("The chat server has crashed: " + exception.getMessage());
	}
	
	@Override
	protected void onClose() {
		System.out.println("The chat server has been closed.");
	}
	
	@Override
	protected void onDisconnect() {
		System.out.println("The chat server is no longer accepting new connections.");
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