package com.sgware.serialsoc;

import java.net.Socket;

class ChatUser extends SerialSocket {
	
	private final ChatServer server;
	private String name = null;
	
	protected ChatUser(ChatServer server, Socket socket) throws Exception {
		super(server, socket);
		this.server = server;
	}
	
	@Override
	protected void onConnect() throws Exception {
		server.users.add(this);
		send("Type your name and press enter.");
	}
	
	@Override
	protected void receive(String message) throws Exception {
		if(name == null) {
			name = message;
			server.broadcast(name + " has connected.");
		}
		else if(message.equals("halt"))
			server.close();
		else if(message.equals("catch fire"))
			throw new Exception("Fire!");
		else
			server.broadcast(name + ": " + message);
	}
	
	@Override
	protected void onClose() throws Exception {
		send("You are being disconnected.");
	}
	
	@Override
	protected void onDisconnect() throws Exception {
		server.users.remove(this);
		server.broadcast(name + " has disconnected.");
	}
}