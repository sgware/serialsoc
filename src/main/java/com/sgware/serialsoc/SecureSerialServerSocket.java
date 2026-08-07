package com.sgware.serialsoc;

import java.io.IOException;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

/**
 * A {@link SimpleSerialServerSocket} that uses an {@link SSLServerSocket} to
 * accept encrypted {@link SSLSocket}s, reads and writes to those sockets using
 * text, and separates incoming and outgoing messages with new line characters.
 * 
 * @author Stephen G. Ware
 */
public class SecureSerialServerSocket extends SimpleSerialServerSocket {
	
	/**
	 * Creates a new secure serial server socket from a given secure server
	 * socket that will bind to a given port when {@link #run()} is called.
	 * 
	 * @param server the secure server socket that will be used to accept
	 * incoming secure sockets
	 * @param port the network port on which to listen for incoming sockets
	 */
	protected SecureSerialServerSocket(SSLServerSocket server, int port) {
		super(server, port);
	}
	
	/**
	 * Creates a new secure serial server socket from a default {@link
	 * SSLServerSocket} that will bind to a given port when {@link #run()} is
	 * called.
	 * 
	 * @param port the network port on which to listen for incoming sockets
	 * @throws IOException if a network problem occurs while creating the secure
	 * server socket
	 */
	public SecureSerialServerSocket(int port) throws IOException {
		this((SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(), port);
	}
	
	/**
	 * Creates a new secure serial server socket from a default {@link
	 * SSLServerSocket} that will bind to any available local port when {@link
	 * #run()} is called.
	 * 
	 * @throws IOException if a network problem occurs while creating the secure
	 * server socket
	 */
	public SecureSerialServerSocket() throws IOException {
		this(0);
	}
	
	@Override
	protected SSLSocket accept() throws IOException {
		return (SSLSocket) super.accept();
	}
}
