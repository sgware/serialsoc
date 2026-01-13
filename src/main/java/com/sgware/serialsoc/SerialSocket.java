package com.sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.Objects;

/**
 * A wrapper around {@link Socket} that listens for lines of input and ensures
 * all events happen on the same thread. A serial socket represents the server
 * side of the connection between a client and server.
 * <p>
 * Each time a {@link SerialServerSocket} accepts a new socket connection, all
 * of the following events will happen on the thread which called {@link
 * SerialServerSocket#run()}:
 * <ul>
 * <li>{@link SerialSocket#SerialSocket(SerialServerSocket, Socket)
 * SerialSocket's constructor} is called on that thread. In the constructor,
 * {@link SerialServerSocket#createReader(Socket)} will be called to open a
 * {@link BufferedReader} on {@link Socket#getInputStream() the socekt's input
 * stream}, and {@link SerialServerSocket#createWriter(Socket)} will be called
 * to open a {@link BufferedWriter} on {@link Socket#getOutputStream() the
 * socekt's output stream}. If the constructor throws an exception, no other
 * events will happen for this socket.</li>
 * <li>{@link #onConnect()} is called exactly once and before any other events.
 * </li>
 * <li>Each time this socket {@link java.io.BufferedReader#readLine() receives a
 * line of input}, its {@link #receive(String) receive} method is called with
 * that input as a string.</li>
 * <li>When this socket is closed, either because {@link #close()} was called or
 * because the client closed the connection, {@link #onClose() onClose} is
 * called exactly once. If the socket is still open, this event provides an
 * opportunity to notify the client that the connection will be closed.</li>
 * <li>After the socket is closed, {@link #onDisconnect() onDisconnect} is
 * called exactly once, and it will always be the last event for this socket.
 * This event provides an opportunity to perform any required cleanup.</li>
 * </ul>
 * 
 * @author Stephen G. Ware
 * @version 1
 */
public class SerialSocket implements Closeable {
	
	/**
	 * A thread that performs all of the socket's events in order and on the
	 * sever thread.
	 */
	final class Listener extends Thread {
		
		@Override
		public void run() {
			// Add this socket to the server's list of open connections and
			// ensure onConnect is called.
			server.execute(() -> {
				server.sockets.add(SerialSocket.this);
				onConnect();
			});
			// Run until closed or an exception is thrown.
			try {
				while(!closed) {			
					String line = input.readLine();
					if(line == null)
						break;
					else
						server.execute(() -> receive(line));
				}
			}
			catch(Exception exception) {
				// If the exception was caused by the socket closing, ignore it;
				// otherwise, register the uncaught exception.
				if(!(exception instanceof SocketException))
					server.execute(() -> server.fail(exception));
			}
			// Ensure onClose() is called and the socket is closed.
			close();
			// Remove the socket from the server's list of open connections and
			// ensure onDisconnect() is called.
			server.execute(() -> {
				server.sockets.remove(SerialSocket.this);
				onDisconnect();
			});
		}
	}
	
	/**
	 * The server that created this serial socket.
	 */
	protected final SerialServerSocket server;
	
	/**
	 * The network socket used for input and output.
	 */
	protected final Socket socket;
	
	/**
	 * A thread that listens for input from the socket.
	 */
	final Listener listener;
	
	/**
	 * Reads the input from the socket.
	 */
	private final BufferedReader input;
	
	/**
	 * Writes output to the socket.
	 */
	private final BufferedWriter output;
	
	/**
	 * A flag indicating that the socket has been closed.
	 */
	private boolean closed = false;
	
	/**
	 * Constructs a new serial socket. This constructor should be called from
	 * {@link SerialServerSocket#createSocket(Socket)}, which will always run
	 * on the main thread. This constructor calls {@link
	 * SerialServerSocket#createReader(Socket)} and {@link
	 * SerialServerSocket#createWriter(Socket)} to create input and output
	 * streams for the socket.
	 * <p>
	 * If this constructor throws an exception, none of this socket's events
	 * will run.
	 * 
	 * @param server the serial server socket that created this serial socket
	 * @param socket the network socket to read from and write to
	 * @throws Exception if an exception occurs while constructing this socket
	 */
	protected SerialSocket(SerialServerSocket server, Socket socket) throws Exception {
		Objects.requireNonNull(server);
		this.server = server;
		Objects.requireNonNull(socket);
		this.socket = socket;
		this.listener = new Listener();
		this.input = server.createReader(socket);
		this.output = server.createWriter(socket);
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Begins the process of disconnecting this socket. After this method is
	 * called, {@link #onClose()} will run on the main thread and this socket
	 * will eventually {@link #onDisconnect() disconnect}.
	 * <p>
	 * It is safe to call this method from any thread; it does not need to be
	 * called from the main thread.
	 */
	@Override
	public void close() {
		server.execute(() -> {
			if(!closed) {
				closed = true;
				onClose();
			}
		});
		server.execute(() -> socket.close());
	}
	
	/**
	 * This method runs exactly once on the main thread after this socket has
	 * been created and before all other events.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onConnect() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method sends a string via this socket's output stream. If the string
	 * does not end in a new line character, one will be appended. The output
	 * stream will then be flushed.
	 * <p>
	 * This method ignores the {@link IOException} thrown if the socket is closed
	 * or closes during the write operation.
	 * 
	 * @param message the message to send via the socket's output stream
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void send(String message) throws Exception {
		try {
			output.append(message);
			if(!message.endsWith("\n") && !message.endsWith("\r"))
				output.append("\n");
			output.flush();
		}
		catch(IOException exception) {
			// Ignore exceptions that happen because the socket is closed
			// or closes during the write.
		}
	}
	
	/**
	 * This method is called from the main thread every time a new {@link
	 * BufferedReader#readLine() line of input is read} from the socket.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @param message the line of input read from the socket
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void receive(String message) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method runs exactly once on the main thread when the socket is
	 * closed, which can happen when the {@link #close()} method is called or
	 * when the socket is unexpectedly closed, for example by the client. If
	 * the socket was closed via the {@link #close()} method, the socket will
	 * remain open until after this method runs, providing an opportunity to
	 * send final messages to the client; however, if the socket was closed
	 * unexpectedly nothing can be sent (but {@link #send(String)} will not
	 * throw an exception). After this event and after the socket is definitely
	 * closed, {@link #onDisconnect()} will run.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onClose() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method runs exactly once on the main thread after the socket is
	 * {@link #close() closed} and after all other events for this socket. The
	 * socket is definitely disconnected before this method runs. This method
	 * provides an opportunity for cleanup.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onDisconnect() throws Exception {
		// This method is meant to be overridden.
	}
}