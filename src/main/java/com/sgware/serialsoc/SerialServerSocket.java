package com.sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A wrapper around {@link ServerSocket} that accepts new {@link SerialSocket}s
 * and ensures all events happen on the same thread.
 * <p>
 * When {@link #run()} is called, all of the following events will happen on the
 * thread which called that {@link #run()}:
 * <ul>
 * <li>{@link #createServer()} will be called to bind a {@link
 * java.net.ServerSocket ServerSocket}. If that method throws an exception, it
 * is thrown immediately and no other events will happen.</li>
 * <li>{@link #onStart() onStart} is called exactly once and before any other
 * events.</li>
 * <li>Each time the server socket accepts a new connection, {@link
 * #createSocket(java.net.Socket)} will be called to make a new instance of
 * {@link SerialSocket}.</li>
 * <li>When the server is closed, either because {@link #close()} was called,
 * because the thread was interrupted, because the server socket was
 * disconnected, or because an uncaught exception was thrown, {@link #onClose()
 * onClose} is called exactly once. Then all open sockets will also be
 * {@link SerialSocket#close() closed}.</li>
 * <li>After all other events, {@link #onStop() onStop} is called exactly once.
 * </li>
 * <li>If at any time an uncaught exception is thrown, {@link
 * #onException(Exception) onException} is called immediately, then the server
 * will close and stop, and then, after all events have finished, the uncaught
 * exception will be thrown by {@link #run()}. If more than one uncaught
 * exception is thrown, {@link #onException(Exception) onException} will be
 * called for all of them, but only the first uncaught exception will be thrown
 * by {@link #run()}.</li>
 * </ul>
 * <p>
 * If an operation needs to run on the same thread as these events but not in
 * response to an event (such as a regular tick or status check) any thread can
 * use {@link #execute(CheckedRunnable)} to submit an operation to be run on the
 * main thread.
 * 
 * @author Stephen G. Ware
 * @version 1
 */
public class SerialServerSocket implements CheckedRunnable, AutoCloseable {
	
	/**
	 * A thread that accepts new sockets until the server is closed.
	 */
	private final class Accepter extends Thread {
		
		@Override
		public final void run() {
			try {
				// Accept new sockets until closed.
				while(!closed) {
					Socket socket = server.accept();
					execute(() -> {
						if(closed)
							socket.close();
						else
							createSocket(socket).listener.start();
					});
				}
			}
			catch(Exception exception) {
				// If the exception was caused by the server socket closing,
				// ignore it; otherwise, register the uncaught exception.
				if(!(closed && exception instanceof SocketException))
					execute(() -> fail(exception));
			}
		}
	}
	
	/**
	 * A blocking queue which stores operations that will run on the main
	 * thread.
	 */
	final LinkedBlockingQueue<CheckedRunnable> queue = new LinkedBlockingQueue<>();
	
	/**
	 * A list of all currently open sockets. Because this list will only be used
	 * on the main thread, it does not need to be synchronized.
	 */
	final List<SerialSocket> sockets = new ArrayList<>();
	
	/**
	 * The server socket from which new connections will be accepted.
	 */
	private ServerSocket server = null;
	
	/**
	 * A flag indicating that the server has been closed.
	 */
	private boolean closed = false;
	
	/**
	 * The first uncaught exception thrown on the main thread.
	 */
	private Exception uncaught = null;
	
	/**
	 * Constructs a new serial server socket. The {@link #getServerSocket()
	 * server socket} is not created or bound until {@link #run()} is called.
	 */
	public SerialServerSocket() {
		// Ensure onStart() is the first method called.
		execute(() -> onStart());
	}
	
	@Override
	public final void run() throws Exception {
		// Create and bind the server socket.
		// Throw an exception immediately if it happens.
		server = createServer();
		// Start a thread to listen for new connections.
		Accepter accepter = new Accepter();
		accepter.start();
		// Run until closed or an exception is thrown.
		// If the thread is interrupted while taking from the queue, it will be
		// handled like any other exception.
		do {		
			CheckedRunnable runnable = call(() -> queue.take());
			run(runnable);
		} while(!closed && uncaught == null);
		// Ensure the close flag is set.
		close();
		// Ensure the server socket is closed.
		execute(() -> server.close());
		// Wait for the new connection accepter thread to finish.
		execute(() -> accepter.join());
		// Ensure onClose() is called.
		execute(() -> onClose());
		drain();
		// Close all open sockets.
		for(SerialSocket socket : sockets)
			execute(() -> socket.close());
		drain();
		// Wait for all socket listener threads to finish.
		for(SerialSocket socket : sockets)
			execute(() -> socket.listener.join());
		drain();
		// Ensure onStop() is called.
		execute(() -> onStop());
		drain();
		// If an uncaught exception occurred at any time, throw it now.
		if(uncaught != null)
			throw uncaught;
	}
	
	/**
	 * Execute all operations that are waiting to run on the main thread.
	 */
	private final void drain() {
		CheckedRunnable runnable = queue.poll();
		while(runnable != null) {
			run(runnable);
			runnable = queue.poll();
		}
	}
	
	/**
	 * {@inheritDoc}
	 * <p>
	 * Begins the process of stopping the server. After this method is called,
	 * {@link #onClose()} will run on the main thread, all open sockets will be
	 * {@link SerialSocket#close() closed}, and the server will eventually
	 * {@link #onStop() stop}.
	 * <p>
	 * It is safe to call this method from any thread; it does not need to be
	 * called from the main thread.
	 */
	@Override
	public void close() {
		execute(() -> closed = true);
	}
	
	/**
	 * Bind and return a {@link ServerSocket}. This method is called once at the
	 * start of {@link #run()}.
	 * <p>
	 * By default, this method is equivalent to:
	 * <p>
	 * <code>return new ServerSocket(0);</code>
	 * <p>
	 * Overriding this method allows the server to be configured. For example,
	 * it can be bound to a specific port, or a subclass of {@link ServerSocket}
	 * can be returned, such as {@link javax.net.ssl.SSLServerSocket}.
	 * 
	 * @return a bound server socket on which this server will listen for new
	 * connections
	 * @throws Exception if an exception occurs while creating or binding the
	 * server socket
	 */
	protected ServerSocket createServer() throws Exception {
		return new ServerSocket(0);
	}
	
	/**
	 * Create an instance of {@link SerialSocket} from a {@link Socket} accepted
	 * by this server.
	 * <p>
	 * By default, this method is equivalent to:
	 * <p>
	 * <code>return new SerialSocket(this, socket);</code>
	 * <p>
	 * Overriding this method allows the socket to be configured. For example,
	 * a subclass of {@link SerialSocket} can be returned, or an {@link
	 * javax.net.ssl.SSLSocket} can perform its handshake.
	 * 
	 * @param socket the socket to used when creating a {@link SerialSocket}
	 * @return an instance of {@link SerialSocket}
	 * @throws Exception if an exception occurs when creating the {@link
	 * SerialSocket}
	 */
	protected SerialSocket createSocket(Socket socket) throws Exception {
		return new SerialSocket(this, socket);
	}
	
	/**
	 * Creates an instance of {@link BufferedReader} to read from a {@link
	 * Socket}.
	 * <p>
	 * By default, this method is equivalent to:
	 * <p>
	 * <code>return new BufferedReader(new InputStreamReader(socket.getInputStream()));</code>
	 * <p>
	 * Overriding this method allows the reader to be configured. For example,
	 * the reader could be configured to limit the maximum amount of input to
	 * read to avoid an out of memory attack.
	 * 
	 * @param socket the socket whose input should be read from
	 * @return a {@link BufferedReader} on the socket's input
	 * @throws Exception if an exception occurs while creating the reader
	 */
	protected BufferedReader createReader(Socket socket) throws Exception {
		return new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}
	
	/**
	 * Creates an instance of {@link BufferedWriter} to write to a {@link
	 * Socket}.
	 * <p>
	 * By default, this method is equivalent to:
	 * <p>
	 * <code>return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));</code>
	 * <p>
	 * Overriding this method allows the writer to be configured. For example,
	 * the writer could be given a larger or smaller buffer size.
	 * 
	 * @param socket the socket whose output should be written to
	 * @return a {@link BufferedWriter} for the socket's output
	 * @throws Exception if an exception occurs while creating the writer
	 */
	protected BufferedWriter createWriter(Socket socket) throws Exception {
		return new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
	}
	
	/**
	 * Returns the {@link ServerSocket} this server is using, or throws an
	 * exception if it has not yet been bound.
	 * 
	 * @return the server socket
	 * @throws IllegalStateException if {@link #createSocket(Socket)} has not
	 * yet been called to create and bind the server socket
	 */
	protected ServerSocket getServerSocket() {
		if(server == null)
			throw new IllegalStateException("The server has not been created.");
		else
			return server;
	}
	
	/**
	 * Runs an an operation on the main thread which called {@link #run()}.
	 * This method can be used when an operation needs to run on the same thread
	 * as the server's events but not in response to one of those events.
	 * 
	 * @param runnable an operation to fun on the main thread
	 */
	protected void execute(CheckedRunnable runnable) {
		queue.add(runnable);
	}
	
	/**
	 * This method runs exactly once on the main thread after the {@link
	 * #createSocket(Socket) server socket has been created and bound} and
	 * before all other events.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onStart() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method runs on the main thread when an uncaught exception is thrown
	 * on the main thread. This method runs immediately after the exception is
	 * thrown. It provides an opportunity to log the exception. After an
	 * uncaught exception is thrown, the server is {@link #close() closed} and
	 * begins to shut down. After the server {@link #onStop() stops}, the first
	 * uncaught exception will be thrown from {@link #run()}, but if any other
	 * uncaught exceptions occur while the server is shutting down, they will
	 * each be passed to this method.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * <p>
	 * It is strongly recommended that this method not throw an exception. If it
	 * does, that exception will be caught and {@link Exception#printStackTrace()
	 * printed to standard error}, but it will not be reported to this method to
	 * avoid creating an infinite loop.
	 * 
	 * @param exception the uncaught exception
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onException(Exception exception) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method runs exactly once on the main thread when the server is
	 * closed, which can happen when the {@link #close()} method is called, when
	 * the main thread is interrupted, when the server socket is disconnected,
	 * or when an uncaught exception is thrown on the main thread. After this
	 * event, the server will {@link SerialSocket#close() close} all open
	 * sockets and eventually {@link #onStop() stop}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onClose() throws Exception  {
		// This method is meant to be overridden.
	}
	
	/**
	 * This method is called exactly once after all other events. All open
	 * sockets will have been {@link SerialSocket#close() closed} and {@link
	 * SerialSocket#onDisconnect() disconnected} before this method is called.
	 * This method provides an opportunity for cleanup.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if an exception is thrown by the method
	 */
	protected void onStop() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Runs a {@link Callable} and returns the result of {@link
	 * Callable#call()}. If an exception is thrown, it is passed to {@link
	 * #fail(Exception)}.
	 * 
	 * @param <T> the return type of the callable
	 * @param callable the callable to call
	 * @return the result of the call
	 */
	private final <T> T call(Callable<T> callable) {
		try {
			return callable.call();
		}
		catch(Exception exception) {
			fail(exception);
			return null;
		}
	}
	
	/**
	 * Runs a {@link CheckedRunnable}. If an exception is thrown, it is passed
	 * to {@link #fail(Exception)}.
	 * 
	 * @param runnable the runnable to run
	 */
	private final void run(CheckedRunnable runnable) {
		call(runnable);
	}
	
	/**
	 * This method should be called with each uncaught exception that is thrown
	 * on the main thread. The first time it is called, it stores the exception
	 * to be thrown at the end of {@link #run()}. Each time it is called, it
	 * passes the exception to {@link #onException(Exception)}.
	 * 
	 * @param exception the uncaught exception
	 */
	final void fail(Exception exception) {
		if(uncaught == null)
			uncaught = exception;
		try {
			onException(exception);
		}
		catch(Exception other) {
			// If onException throws an exception, we don't want to call it
			// again and risk creating an infinite loop, so just print the
			// exception to standard error.
			other.printStackTrace();
		}
	}
}