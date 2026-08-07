package com.sgware.serialsoc;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

class TestSerialServerSocket extends SimpleSerialServerSocket {
	
	final List<TestSerialSocket> sockets = new ArrayList<>();
	private Thread thread = null;
	private Exception uncaught = null;
	private boolean started = false;
	private boolean connected = false;
	private boolean disconnected = false;
	private boolean closed = false;
	private boolean stopped = false;
	
	public TestSerialServerSocket(int port) throws IOException {
		super(port);
	}
	
	@Override
	public String toString() {
		return "Server";
	}
	
	@Override
	protected void onStart() {
		checkMainThread(this, "started");
		if(started)
			throw new IllegalStateException(this + " started twice.");
		started = true;
		System.out.println(this + " started.");
	}
	
	@Override
	protected void connect() throws IOException {
		checkMainThread(this, "connected");
		super.connect();
	}
	
	@Override
	protected void onConnect() {
		checkMainThread(this, "connected");
		if(!started)
			throw new IllegalStateException(this + " connected before it started.");
		if(connected)
			throw new IllegalStateException(this + " connected twice.");
		connected = true;
		System.out.println(this + " connected.");
	}
	
	@Override
	protected TestSerialSocket create(Socket socket) throws IOException {
		checkMainThread(this, "created a socket");
		return new TestSerialSocket(this, socket);
	}
	
	@Override
	protected void onAccept(SerialSocket socket) {
		checkMainThread(this, "accepted a socket");
		if(!connected)
			throw new IllegalStateException(this + " accepted before it connected.");
		if(disconnected)
			throw new IllegalStateException(this + " accepted after it connected.");
	}
	
	@Override
	protected void onException(Exception exception) {
		fail(exception);
		try {
			checkMainThread(this, "processed an exception");
		}
		catch(Exception other) {
			fail(exception);
		}
	}
	
	private final void fail(Exception exception) {
		StringWriter string = new StringWriter();
		string.append(this + " threw an uncaught exception: ");
		exception.printStackTrace(new PrintWriter(string));
		System.err.println(string.toString());
		if(uncaught == null)
			uncaught = exception;
		close();
	}
	
	@Override
	protected void onClose() {
		checkMainThread(this, "closed");
		if(!connected)
			throw new IllegalStateException(this + " closed before it connected.");
		if(closed)
			throw new IllegalStateException(this + " closed twice.");
		closed = true;
		System.out.println(this + " closed.");
	}
	
	@Override
	protected void disconnect() throws IOException {
		checkMainThread(this, "disconnected");
		super.disconnect();
	}
	
	@Override
	protected void onDisconnect() {
		checkMainThread(this, "disconnected");
		if(!closed)
			throw new IllegalStateException(this + " disconnected before it closed.");
		if(disconnected)
			throw new IllegalStateException(this + " disconnected twice.");
		disconnected = true;
		System.out.println(this + " disconnected.");
	}
	
	@Override
	protected void onStop() {
		checkMainThread(this, "stopped");
		if(!started)
			throw new IllegalStateException(this + " stopped before it started.");
		if(connected && !disconnected)
			throw new IllegalStateException(this + " stopped before it disconnected.");
		if(stopped)
			throw new IllegalStateException(this + " stopped twice.");
		stopped = true;
		System.out.println(this + " stopped.");
	}
	
	void checkMainThread(Object subject, String verb) {
		if(thread == null)
			thread = Thread.currentThread();
		else if(thread != Thread.currentThread())
			throw new IllegalStateException(subject + " " + verb + " on the wrong thread.");
	}
	
	public void verify() {
		if(uncaught != null)
			throw new IllegalStateException(this + " threw an uncaught exception.", uncaught);
		if(!started)
			throw new IllegalStateException(this + " never started.");
		if(connected && !closed)
			throw new IllegalStateException(this + " connected but never closed.");
		if(connected && !disconnected)
			throw new IllegalStateException(this + " connected but never disconnected.");
		if(!stopped)
			throw new IllegalStateException(this + " never stopped.");
		System.out.println(this + " verified that all methods were called correctly.");
		for(TestSerialSocket socket : sockets)
			socket.verify();
	}
}