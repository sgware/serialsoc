package com.sgware.serialsoc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A {@link SerialServerSocket} that uses a {@link ServerSocket} to accept
 * basic {@link Socket}s, reads and writes to those sockets using text, and
 * separates incoming and outgoing messages with new line characters.
 * 
 * @author Stephen G. Ware
 */
public class SimpleSerialServerSocket extends SerialServerSocket {
	
	/**
	 * The server socket that accepts new incoming sockets.
	 */
	private final ServerSocket server;
	
	/**
	 * The network port to which the server socket will bind.
	 */
	private final int port;
	
	/**
	 * Creates a new simple serial server socket from a given server socket that
	 * will bind to a given port when {@link #run()} is called.
	 * 
	 * @param server the server socket that will be used to accept incoming
	 * sockets
	 * @param port the network port on which to listen for incoming sockets
	 */
	protected SimpleSerialServerSocket(ServerSocket server, int port) {
		this.server = server;
		this.port = port;
	}
	
	/**
	 * Creates a new simple serial server socket from a default {@link
	 * ServerSocket} that will bind to a given port when {@link #run()} is
	 * called.
	 * 
	 * @param port the network port on which to listen for incoming sockets
	 * @throws IOException if a network problem occurs while creating the server
	 * socket
	 */
	public SimpleSerialServerSocket(int port) throws IOException {
		this(new ServerSocket(), port);
	}
	
	/**
	 * Creates a new simple serial server socket from a default {@link
	 * ServerSocket} that will bind to any available local port when {@link
	 * #run()} is called.
	 * 
	 * @throws IOException if a network problem occurs while creating the server
	 * socket
	 */
	public SimpleSerialServerSocket() throws IOException {
		this(0);
	}
	
	/**
	 * Returns the local address of this server socket.
	 * 
	 * @return the address to which this socket is bound, or null if the server
	 * has not yet {@link #connect() connected}
	 * @see ServerSocket#getInetAddress()
	 */
	public InetAddress getAddress() {
		return server.getInetAddress();
	}
	
	/**
	 * Returns the port number on which this socket is listening or will listen
	 * once it {@link #connect() connects}.
	 * 
	 * @return the port number on which this socket is listening or -1 if the
	 * socket is not bound yet
	 * @see ServerSocket#getLocalPort()
	 */
	public int getPort() {
		return port;
	}
	
	@Override
	protected void connect() throws IOException {
		server.bind(new InetSocketAddress(port));
	}
	
	@Override
	protected Socket accept() throws IOException {
		return server.accept();
	}
	
	@Override
	protected SimpleSerialSocket create(Socket socket) throws IOException {
		return new SimpleSerialSocket(this, socket);
	}
	
	@Override
	protected void disconnect() throws IOException {
		server.close();
	}
}