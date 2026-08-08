package com.sgware.serialsoc;

import java.io.IOException;
import java.net.Socket;

class TestSerialSocket extends SimpleSerialSocket {
	
	private static int nextID = 0;
	public final int id = nextID++;
	private final TestSerialServerSocket server;
	private boolean connected = false;
	private boolean closed = false;
	private boolean disconnected = false;
	
	protected TestSerialSocket(TestSerialServerSocket server, Socket socket) throws IOException {
		super(server, socket);
		this.server = server;
		server.sockets.add(this);
	}
	
	@Override
	public String toString() {
		return "Socket " + id;
	}
	
	@Override
	protected void onConnect() {
		server.checkMainThread(this, "connected");
		if(connected)
			throw new IllegalStateException(this + " connected twice.");
		connected = true;
		System.out.println(this + " connected.");
	}
	
	@Override
	protected void receive(String message) {
		server.checkMainThread(this, "received a message");
		if(!connected)
			throw new IllegalStateException(this + " received a message before connecting.");
		if(closed)
			throw new IllegalStateException(this + " received a message after closing.");
		if(disconnected)
			throw new IllegalStateException(this + " received a message after disconnecting.");
		System.out.println(this + " received: " + message);
		send(message);
	}
	
	@Override
	protected void send(String message) {
		if(!connected)
			throw new IllegalStateException(this + " sent a message before connecting.");
		if(disconnected)
			throw new IllegalStateException(this + " sent a message after disconnecting.");
		super.send(message);
		System.out.println(this + " sent: " + message);
	}
	
	@Override
	protected void tick() {
		server.checkMainThread(this, "ticked");
		if(closed)
			throw new IllegalStateException(this + " ticked after it closed.");
	}
	
	@Override
	protected void onException(Exception exception) throws Exception {
		throw new IllegalStateException(this + " threw an uncaught exception.", exception);
	}
	
	@Override
	protected void onClose() {
		server.checkMainThread(this, "closed");
		if(!connected)
			throw new IllegalStateException(this + " closed before it connected.");
		if(closed)
			throw new IllegalStateException(this + " closed twice.");
		closed = true;
		System.out.println(this + " closed.");
	}
	
	@Override
	protected void disconnect() throws IOException {
		server.checkMainThread(this, "disconnected");
		if(!closed)
			throw new IllegalStateException(this + " disconnected before it closed.");
		super.disconnect();
	}
	
	@Override
	protected void onDisconnect() {
		server.checkMainThread(this, "disconnected");
		if(!closed)
			throw new IllegalStateException(this + " disconnected before it closed.");
		if(disconnected)
			throw new IllegalStateException(this + " disconnected twice.");
		disconnected = true;
		System.out.println(this + " disconnected.");
	}
	
	public void verify() {
		if(!connected)
			throw new IllegalStateException(this + " never connected.");
		if(!closed)
			throw new IllegalStateException(this + " never closed.");
		if(!disconnected)
			throw new IllegalStateException(this + " never disconnected.");
		System.out.println(this + " verified that all methods were called correctly.");
	}
}