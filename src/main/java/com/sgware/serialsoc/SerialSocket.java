package com.sgware.serialsoc;

import java.io.Closeable;
import java.net.SocketException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A serial socket ensures its setup, input, and shut down events all run one at
 * a time on the same thread as other sockets created by the same {@link
 * SerialServerSocket} even if an exception is thrown.
 * <p>
 * A serial socket guarantees the following:
 * <ul>
 * <li>{@link #onConnect()} runs first and on the same thread that called {@link
 * SerialServerSocket#run()}.</li>
 * <li>A separate thread continuously calls {@link #read()} to read a new
 * message from the socket's input stream as a string. Each time a message is
 * read, {@link #receive(String)} is called from the thread that called {@link
 * SerialServerSocket#run()} with that message.</li>
 * <li>{@link #send(String)} can be called from any thread to {@link
 * #write(String)} a message to the socket's output stream.</li>
 * <li>If any {@link SerialSocket} methods throw an exception, the exception is
 * reported to {@link #onException(Exception)} on the same thread that called
 * {@link SerialServerSocket#run()}, and the socket will close gracefully.</li>
 * <li>If this socket's server has a {@link Clock}, {@link #tick()} will be
 * called regularly from the same thread that called {@link
 * SerialServerSocket#run()}.</li>
 * <li>A socket can be closed by the client, by a network problem, because one
 * of its methods threw an exception, or by calling {@link #close()} from any
 * thread. Regardless of how the socket closed, {@link #onClose()} and then
 * {@link #disconnect()} are always called in that order on the same thread that
 * called {@link SerialServerSocket#run()}.</li>
 * <li>{@link #onDisconnect()} is always called last on the same thread that
 * called {@link SerialServerSocket#run()}.</li>
 * </ul>
 * 
 * @author Stephen G. Ware
 */
public abstract class SerialSocket implements Closeable {
	
	/**
	 * A thread that calls {@link #onConnect()}, then repeatedly {@link #read()
	 * reads} and {@link #receive(String) receives} new messages, and finally
	 * calls {@link #onDisconnect()}. 
	 */
	final class Listener extends Thread {
		
		@Override
		public void run() {
			// Add myself to the server's list of sockets and do setup.
			server.execute(() -> {
				server.sockets.add(SerialSocket.this);
				safely(() -> onConnect());
			});
			// Receive messages until closed or crash.
			try {
				while(!closed) {
					// Runs on this thread because it blocks.
					String message = read();
					CountDownLatch received = new CountDownLatch(1);
					// Empty message means the socket has closed.
					if(message == null)
						break;
					// Report the message to the socket on the main thread.
					server.execute(() -> {
						try {
							if(!closed)
								safely(() -> receive(message));
						}
						finally {
							received.countDown();
						}
					});
					// Wait until the current message is done processing
					// before reading the next one.
					received.await();
				}
			}
			catch(SocketException exception) {
				// Ignore exceptions thrown because the socket closed.
			}
			catch(Exception exception) {
				// Process other exceptions on the main thread.
				server.execute(() -> safely(() -> { throw exception; }));
			}
			// Ensure the connection is closed.
			close();
			// Shut down.
			server.execute(() -> {
				server.sockets.remove(SerialSocket.this);
				safely(() -> onDisconnect());
			});
		}
	}
	
	/**
	 * The server which created this socket and whose main thread called {@link
	 * SerialServerSocket#run()}
	 */
	protected final SerialServerSocket server;
	
	/**
	 * A thread which listens for input from the socket's input stream.
	 */
	final Listener listener;
	
	/**
	 * A flag indicating that the server has been closed.
	 */
	private boolean closed = false;
	
	/**
	 * Constructs a new serial socket that reports its events to the server
	 * socket that created it.
	 * 
	 * @param server the serial server socket that created this socket and on
	 * whose main thread most events will run
	 */
	protected SerialSocket(SerialServerSocket server) {
		Objects.requireNonNull(server);
		this.server = server;
		this.listener = new Listener();
	}
	
	/**
	 * Returns true if this socket has been closed.
	 * 
	 * @return true if the socket's close operation has run
	 */
	final boolean hasBeenClosed() {
		return closed;
	}
	
	/**
	 * Runs a {@link CheckedRunnable}. If it throws an exception, the exception
	 * is reported to {@link #onException(Exception)} and the socket is closed.
	 * This method should only be called from the main thread.
	 * 
	 * @param runnable the CheckedRunnable to run
	 * @throws Exception if the runnable throws a first exception and then
	 * {@link #onException(Exception)} throws a second exception when the first
	 * is reported to it 
	 */
	final void safely(CheckedRunnable runnable) throws Exception {
		try {
			runnable.run();
		}
		catch(Exception exception) {
			try {
				onException(exception);
			}
			finally {
				close();
			}
		}
	}
	
	/**
	 * Always runs first when the socket connects to the server. It runs on the
	 * same thread that called {@link SerialServerSocket#run()}. If this method
	 * throws an exception, this socket will {@link #close()} and {@link
	 * #disconnect()} without accepting any input.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onConnect() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Blocks until a new message is received by the socket's input stream.
	 * This method <em>does not</em> run on the same thread that called {@link
	 * SerialServerSocket#run()}. Once this method returns a message, it does
	 * not run again until {@link #receive(String)} has finished processing the
	 * message. If this method throws an exception, the socket closes.
	 * <p>
	 * This method is meant to be called only by the socket's listener thread
	 * and should not be called from other contexts.
	 * 
	 * @return a newly read message from the socket's input stream
	 * @throws Exception if a problem occurs while reading from the socket
	 */
	protected abstract String read() throws Exception;
	
	/**
	 * Runs each time the socket {@link #read() reads} a new message. This
	 * method always runs on the same thread that called {@link
	 * SerialServerSocket#run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @param message the message read by the socket's input stream
	 * @throws Exception if a problem occurs while processing the message
	 */
	protected void receive(String message) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Sends a message over the socket. This method can be called from any
	 * thread. If sending the message causes an exception, the exception will be
	 * reported to {@link #onException(Exception)} from the same thread that
	 * called {@link SerialServerSocket#run()} (regardless of what thread called
	 * this method), and the socket will close.
	 * 
	 * @param message the message to send
	 */
	protected void send(String message) {
		try {
			write(message);
		}
		catch(SocketException exception) {
			// Ignore exceptions caused by a closed socket.
		}
		catch(Exception exception) {
			server.execute(() -> safely(() -> { throw exception; }));
		}
	}
	
	/**
	 * Writes a message to the socket's output stream. This method may be called
	 * from any thread.
	 * <p>
	 * This method is meant to be called only by {@link #send(String)} and
	 * should not be called from other contexts.
	 * 
	 * @param string the message to write to the socket's output stream
	 * @throws Exception if a problem occurs while writing to the socket
	 */
	protected abstract void write(String string) throws Exception;
		
	/**
	 * If a {@link Clock} is started for this socket's server, this method is
	 * called regularly on each interval by the same thread that called {@link
	 * SerialServerSocket#run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void tick() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * If an exception is thrown at any time by one of the socket's methods,
	 * it is reported to this method from the same thread that called {@link 
	 * SerialServerSocket#run()} (regardless of which thread the exception was
	 * thrown on).
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * <p>
	 * If this method throws an exception, it will be handled by {@link
	 * SerialServerSocket#onException(Exception)} and the server will shut
	 * down. So if this method does nothing, only this socket will shut down
	 * when an exception is thrown. However, if this method throws a new
	 * exception or re-throws the same exception it was given, it will cause
	 * both this socket and the whole server to shut down.
	 * 
	 * @param exception the exception that was thrown by one of the server
	 * methods
	 * @throws Exception if a problem occurs while processing the exception
	 */
	protected void onException(Exception exception) throws Exception {
		// This method is meant to be overridden.
	}
	
	@Override
	/**
	 * Begins the socket's shutdown process. This method can be called from any
	 * thread.
	 */
	public final void close() {
		server.execute(() -> {
			if(!closed) {
				closed = true;
				safely(() -> onClose());
				safely(() -> disconnect());
			}
		});
	}
	
	/**
	 * Runs after the socket is closed, either because a method threw an
	 * exception, because the network disconnected the socket, or because {@link
	 * #close()} was called. This method runs on the same thread that called
	 * {@link SerialServerSocket#run()}.
	 * <p>
	 * No new messages will be {@link #receive(String) received} after this
	 * method runs; however, if this method runs because of a non-network
	 * exception or because {@link #close()} was called, it will still be
	 * possible to {@link #send(String)} messages. If the socket closed because
	 * it was disconnected, any messages sent will not be received.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onClose() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Disconnects the socket from the network such that it can no longer send
	 * or receive new messages. This method should cause {@link #read()} to stop
	 * blocking if it is waiting. This method runs after {@link #onClose()} on
	 * the same thread that called {@link SerialServerSocket#run()}. It is
	 * possible the socket is already disconnected by the time this method runs
	 * if the socket was closed unexpectedly by the network.
	 * 
	 * @throws Exception if a problem occurs while disconnecting the socket
	 */
	protected abstract void disconnect() throws Exception;
	
	/**
	 * Always runs last just before the socket stops. It runs on the same thread
	 * that called {@link SerialServerSocket#run()}. By the time this method
	 * runs, the socket will have been disconnected, so it is no longer possible
	 * to {@link #send(String)} or {@link #receive(String)} messages.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onDisconnect() throws Exception {
		// This method is meant to be overridden.
	}
}