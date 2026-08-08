package com.sgware.serialsoc;

import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * A serial server socket listens for incoming socket connections and ensures
 * all inputs from them are processed one at a time on a single thread. It also
 * guarantees that certain methods run in order, including setup and shutdown
 * methods for the server and each socket, even if an exception is thrown.
 * <p>
 * When {@link #run()} is called, all of the following events will happen:
 * <ul>
 * <li>{@link #onStart()} is called first on the same thread that called {@link
 * #run()}. If it throws an exception, no other methods will run.</li>
 * <li>{@link #connect()} is called on the same thread that called {@link
 * #run()} to establish a server socket. If it does not throw an exception,
 * {@link #onConnect()} is called on the same thread that called {@link #run()}.
 * </li>
 * <li>If {@link #connect()} did not throw an exception, the server starts a new
 * thread that continuously calls {@link #accept()} to wait for new connections.
 * Each time a new socket is accepted, the server calls {@link #create(Socket)}
 * on the same thread that called {@link #run()} to wrap a {@link SerialSocket}
 * around the new socket. If {@link #create(Socket)} throws an exception, the
 * socket is closed and never reported to the server. If {@link #create(Socket)}
 * does not throw an exception, the new socket is reported to the server's
 * {@link #onAccept(SerialSocket)} method, which runs on the same thread that
 * called {@link #run()}.</li>
 * <li>Each time a new {@link SerialSocket} is successfully created, its {@link
 * SerialSocket#onConnect()} method is called first on the same thread that
 * called {@link #run()}.</li>
 * <li>If {@link SerialSocket#onConnect()} did not throw an exception, a new
 * thread continuously calls {@link SerialSocket#read()} to listen for new
 * input. Each input that is successfully received is reported to {@link
 * SerialSocket#receive(String)}, which runs on the same thread that called
 * {@link #run()}.</li>
 * <li>If the server needs to run an operation regularly, you can {@link
 * Thread#start()} a {@link Clock} that will regularly call {@link #tick()} on
 * the server and {@link SerialSocket#tick()} on each of its sockets from the
 * same thread that called {@link #run()}.</li>
 * <li>A socket can be closed by the client, by a network problem, because one
 * of its methods threw an exception, or by calling {@link SerialSocket#close()}
 * from any thread. Regardless of how it is closed, {@link
 * SerialSocket#onClose()}, then {@link SerialSocket#disconnect()}, then {@link
 * SerialSocket#onDisconnect()} are always called in that order from the same
 * thread that called {@link #run()}. These methods always run even if an
 * earlier method threw an exception.</li>
 * <li>If any {@link SerialSocket} methods throw an exception, the exception is
 * reported to {@link SerialSocket#onException(Exception)} on the same thread
 * that called {@link #run()}, and then the socket will close gracefully. {@link
 * SerialSocket#onException(Exception)} can either ignore the exception or
 * re-throw it to cause the server to shut down.</li>
 * <li>If any {@link SerialServerSocket} methods throw an exception (or if
 * {@link SerialSocket#onException(Exception)} re-throws an exception), the
 * exception is reported to {@link #onException(Exception)} on the same thread
 * that called {@link #run()}, and then the server will shut down gracefully.
 * </li>
 * <li>A server shuts down when one of its methods throws an exception, when
 * a network problem causes the server to disconnect, when the JVM shuts down,
 * or when {@link #close()} is called from any thread. Regardless of how it is
 * closed, if {@link #connect()} did not throw an exception, {@link #onClose()},
 * then {@link #disconnect()}, then {@link #onDisconnect()} are always called in
 * that order from the same thread that called {@link #run()}.</li>
 * <li>As long as {@link #onStart()} did not throw an exception, {@link
 * #onStop()} is always called last from the same thread that called {@link
 * #run()}.</li>
 * <li>If an exception was thrown by any of the server's methods, it is thrown
 * by {@link #run()} after all of the shut down methods have completed.</li> 
 * </ul>
 * You can check the server's {@link Status} with {@link #getStatus()}. You can
 * also check if a server has ever had a status with {@link #has(Status)} or
 * wait for a status with {@link #await(Status)}. For example, you can check if
 * the server has {@link Status#CONNECTED} or wait for it to connect.
 * <p>
 * If another thread needs to execute something on the thread that called {@link
 * #run()} that cannot be written into one of the above methods, it can call
 * {@link #execute(CheckedRunnable)} to run a {@link CheckedRunnable} on the
 * main thread.
 * 
 * @author Stephen G. Ware
 */
public abstract class SerialServerSocket implements CheckedRunnable, AutoCloseable {
	
	/**
	 * A thread that repeatedly {@link #accept() accepts} and {@link #create()
	 * creates} new sockets then {@link #onAccept(SerialSocket) reports them to
	 * the server}.
	 */
	private final class Accepter extends Thread {
		
		/**
		 * Controls whether the main loop continues.
		 */
		private boolean running = true;
		
		@Override
		public final void run() {
			try {
				// Loop until server is closed.
				while(running) {
					// Runs on this thread because it blocks.
					Socket socket = accept();
					CountDownLatch next = new CountDownLatch(1);
					// Set up the socket on the main thread.
					execute(() -> {
						SerialSocket serial = safe(() -> create(socket));
						if(serial == null) {
							try {
								socket.close();
							}
							catch(Exception exception) {
								// Ignore exceptions from closing the socket,
								// since it was never reported to the server.
							}
						}
						else {
							safely(() -> onAccept(serial));
							serial.listener.start();
						}
						next.countDown();
					});
					// Wait for the current socket to finish processing before
					// accepting the next one.
					next.await();
				}
			}
			catch(Exception exception) {
				// Ignore exceptions from the server socket being closed.
				if(!running && exception instanceof SocketException)
					return;
				// Report other exceptions on the main thread.
				else
					execute(() -> { throw exception; });
			}
		}
	}
	
	/**
	 * Tracks the server's current and previous status.
	 */
	private final StatusTracker status = new StatusTracker();
	
	/**
	 * Stores operations that will run on the main thread.
	 */
	private final LinkedBlockingQueue<CheckedRunnable> queue = new LinkedBlockingQueue<>();
	
	/**
	 * A list of all current sockets. Because this list will only be used on the
	 * main thread, it does not need to be synchronized.
	 */
	final List<SerialSocket> sockets = new ArrayList<>();
		
	/**
	 * The first uncaught exception thrown on the main thread.
	 */
	private Exception uncaught = null;
	
	/**
	 * Constructs a new serial server socket. The server does not bind to a port
	 * until {@link #connect()} is called.
	 */
	public SerialServerSocket() {
		// Do nothing.
	}
	
	/**
	 * Returns the server's current {@link Status}. If {@link #run()} has not
	 * yet been called, this method returns null. Otherwise, it returns:
	 * <ul>
	 * <li>{@link Status#RUN} if {@link #run()} has been called but {@link
	 * #onStart()} has not yet completed.</li>
	 * <li>{@link Status#STARTED} if {@link #onStart()} completed successfully
	 * but the server has not yet connected.</li>
	 * <li>{@link Status#CONNECTED} if {@link #connect()} completed successfully
	 * and the server has not yet been closed.</li>
	 * <li>{@link Status#CLOSED} if {@link #close()} has been called, or if the
	 * server has closed automatically for another reason (like an exception or
	 * a network problem) but has not yet disconnected.</li>
	 * <li>{@link Status#DISCONNECTED} if the server previously connected and
	 * {@link #disconnect()} has completed (successfully or not).</li>
	 * <li>{@link Status#FINISHED} if the server previously disconnected, is
	 * shutting down, and is no longer accepting operations via {@link
	 * #execute(CheckedRunnable)}.</li>
	 * <li>{@link Status#STOPPED} if {@link #onStart()} threw an exception or
	 * after {@link #onStop()} has completed (successfully or not) and no more
	 * events will happen for this server.</li>
	 * </ul>
	 * 
	 * @return the server's current status
	 */
	public Status getStatus() {
		return status.get();
	}
	
	/**
	 * Checks whether the server has ever achieved the given status. For
	 * example, this method can be used to check whether the server ever
	 * {@link Status#CONNECTED}.
	 * 
	 * @param status the status in question
	 * @return true if the server has ever had that status, false otherwise
	 */
	public boolean has(Status status) {
		return this.status.has(status);
	}
	
	/**
	 * Waits for the server to have the given status, or for it to become
	 * impossible for the server to have that status. For example, you can await
	 * the {@link Status#CONNECTED} status. Once the server connects
	 * successfully, the method will return true. If the server fails to
	 * connect, this method will wait until the server fails to connect and
	 * return false.
	 * 
	 * @param status the status in question
	 * @return true when the status occurs, or false when it becomes impossible
	 * to achieve the status
	 * @throws InterruptedException if the thread that called this method is
	 * interrupted while waiting for the status
	 */
	public boolean await(Status status) throws InterruptedException {
		this.status.await(status);
		return has(status);
	}
	
	@Override
	/**
	 * Connects the server, listens for new sockets, processes inputs, and shuts
	 * down gracefully even if an exception is thrown. This method does not
	 * return until the server stops, and if an exception was thrown by any of
	 * the methods it will be thrown at the end of this method.
	 * <p>
	 * The thread which calls this method is considered the main thread, and
	 * most server and socket methods will run on that thread. See {@link
	 * SerialServerSocket} for details on which methods are called and in what
	 * order.
	 * <p>
	 * A server can only run once. If this method is called more than once, it
	 * throws an {@link IllegalStateException}.
	 */
	public void run() throws Exception {
		// Prevent this method from running more than once.
		synchronized(status) {
			if(status.has(Status.RUN))
				throw new IllegalStateException("The server has already run and cannot run again.");
			else
				status.set(Status.RUN);
		}		
		// This shutdown hook closes the server and waits for it to shut down.
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			close();
			try {
				status.await(Status.STOPPED);
			}
			catch(InterruptedException exception) {
				// Do nothing.
			}
		}));
		// If this throws an exception, do not try to connect.
		safely(() -> onStart());
		if(uncaught == null) {
			status.set(Status.STARTED);
			// This thread accepts new sockets.
			Accepter accepter = new Accepter();
			// Connect.
			safely(() -> {
				connect();
				status.set(Status.CONNECTED);
				accepter.start();
				onConnect();
			});
			// Run until closed or crash.
			try {
				while(!has(Status.CLOSED) && uncaught == null)
					safely(queue.take());
			}
			catch(InterruptedException exception) {
				// Treat being interrupted like an exception.
				if(uncaught == null)
					uncaught = exception;
			}
			// If the server connected, close and disconnect it.
			if(has(Status.CONNECTED)) {
				// Close.
				status.set(Status.CLOSED);
				safely(() -> onClose());
				// Disconnect and wait for the accepter thread to finish.
				accepter.running = false;
				safely(() -> disconnect());
				accepter.join();
				status.set(Status.DISCONNECTED);
				safely(() -> onDisconnect());
				// Close all connected sockets.
				for(SerialSocket socket : sockets)
					socket.close();
				// Run all waiting operations (including the ones that close the sockets).
				drain();
				// Wait for all socket threads to finish.
				for(SerialSocket socket : sockets)
					socket.listener.join();
				// Stop accepting operations and finish all current operations.
				status.set(Status.FINISHED);
				drain();
			}
		}
		// This is the last method to run.
		if(has(Status.STARTED))
			safely(() -> onStop());
		status.set(Status.STOPPED);
		// Clear the queue (in case it never ran).
		queue.clear();
		// Throw the first uncaught exception, if any.
		if(uncaught != null)
			throw uncaught;
	}
	
	/**
	 * Calls a {@link Callable} and returns what it returns. If it throws an
	 * exception, the exception is reported to {@link #onException(Exception)},
	 * the server is closed, the exception is recorded so it can be thrown at
	 * the end of {@link #run()}, and this method returns null. This method
	 * should only be called from the main thread.
	 * 
	 * @param <T> the type of thing returned by the Callable
	 * @param callable the Callable to run
	 * @return the value returned by the Callable, or null if the Callable threw
	 * an exception
	 */
	private <T> T safe(Callable<T> callable) {
		try {
			return callable.call();
		}
		catch(Exception exception) {
			if(uncaught == null)
				uncaught = exception;
			try {
				onException(exception);
			}
			catch(Exception other) {
				// Do nothing.
			}
			close();
			return null;
		}
	}
	
	/**
	 * Runs a {@link CheckedRunnable}. If it throws an exception, the exception
	 * is reported to {@link #onException(Exception)}, the server is closed, and
	 * the exception is recorded so it can be thrown at the end of {@link
	 * #run()}. This method should only be called from the main thread.
	 * 
	 * @param runnable the CheckedRunnable to run
	 */
	private void safely(CheckedRunnable runnable) {
		safe(runnable);
	}
	
	/**
	 * {@link #safely(CheckedRunnable) Safely} runs all operations waiting in
	 * the queue. This method should only be called from the main thread.
	 */
	private void drain() {
		CheckedRunnable runnable = queue.poll();
		while(runnable != null) {
			safely(runnable);
			runnable = queue.poll();
		}
	}
		
	/**
	 * Always runs first when the server starts. It runs on the same thread
	 * that called {@link #run()}. Unless this method throws an exception,
	 * {@link #onStop()} is guaranteed to run before the server stops.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onStart() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Binds the server socket to its network port. It runs after {@link
	 * #onStart()} on the same thread that called {@link #run()}. If this method
	 * does not throw an exception, {@link #onConnect()}, {@link #onClose()},
	 * {@link #disconnect()}, and {@link #onDisconnect()} are guaranteed to run
	 * before the server stops.
	 * 
	 * @throws Exception if a problem occurs when the server connects to the
	 * network
	 */
	protected abstract void connect() throws Exception;
	
	/**
	 * Runs after {@link #connect()} (if it did not throw an exception) on the
	 * same thread that called {@link #run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onConnect() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Blocks until a new socket connects to the server and then returns it.
	 * This method <em>does not</em> run on the same thread that called {@link
	 * #run()}. Once this method returns a socket, it does not run again until
	 * after {@link #create(Socket)} and {@link #onAccept(SerialSocket)} have
	 * finished processing the socket. If this method throws an exception, the
	 * server closes.
	 * 
	 * @return a newly accepted socket
	 * @throws Exception if a problem occurs while waiting for or accepting the
	 * new socket connection
	 */
	protected abstract Socket accept() throws Exception;
	
	/**
	 * Creates a {@link SerialSocket} from a {@link Socket}. This method is
	 * called after {@link #accept()} but on the same thread that called {@link
	 * #run()}. If this method throws an exception, the socket will be closed;
	 * otherwise, {@link #onAccept(SerialSocket)} will be called after.
	 * 
	 * @param socket the socket to make into a serial socket
	 * @return a serial socket
	 * @throws Exception if a problem occurs while creating the serial socket
	 */
	protected abstract SerialSocket create(Socket socket) throws Exception;
	
	/**
	 * Runs each time a new {@link SerialSocket} connects to the server on the
	 * same thread that called {@link #run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @param socket the newly created serial socket
	 * @throws Exception if a problem occurs
	 */
	protected void onAccept(SerialSocket socket) throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * If a {@link Clock} is started for this server, this method is called
	 * regularly on each interval by the same thread that called {@link #run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void tick() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * If an exception is thrown at any time by one of the server's methods,
	 * it is reported to this method from the same thread that called {@link 
	 * #run()} (regardless of which thread the exception was thrown on).
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * <p>
	 * While this method declares an exception for convenience, if it throws an
	 * exception that exception is ignored; otherwise, the server could get
	 * trapped in an infinite loop of this method throwing exceptions and then
	 * reporting them back to this method.
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
	 * Begins the server's shutdown process. This method can be called from any
	 * thread.
	 */
	public final void close() {
		status.set(Status.CLOSED);
		// Add an empty operation to the queue in case run() is blocked.
		queue.offer(() -> {});
	}
	
	/**
	 * Runs after the server is closed, either because a method threw an
	 * exception, because a network problem disconnected the server, because the
	 * JVM shut down, or because {@link #close()} was called. This method only
	 * runs if the server successfully {@link #connect() connected} and it runs
	 * on the same thread that called {@link #run()}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onClose() throws Exception  {
		// This method is meant to be overridden.
	}
	
	/**
	 * Disconnects the server from the network such that it can no longer accept
	 * any new connections. This method should cause {@link #accept()} to stop
	 * blocking if it is waiting. This method runs after {@link #onClose()} on
	 * the same thread that called {@link #run()}, but only if the server
	 * successfully {@link #connect() connected}. It is possible the server is
	 * already disconnected from the network by the time this method runs if a
	 * network error is what caused the server to close.
	 * 
	 * @throws Exception if a problem occurs while disconnecting the server from
	 * the network
	 */
	protected abstract void disconnect() throws Exception;
	
	/**
	 * Runs after {@link #disconnect()} on the same thread that called {@link
	 * #run()}. This method only runs if the server successfully {@link
	 * #connect() connected}, and it runs regardless of whether {@link
	 * #disconnect()} threw an exception. No new sockets will connect to the
	 * server after this method runs, but some sockets may still be connected
	 * when it runs.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onDisconnect() throws Exception  {
		// This method is meant to be overridden.
	}
	
	/**
	 * Always runs last just before the server stops. It runs on the same thread
	 * that called {@link #run()} but only if the server {@link #onStart()
	 * started}.
	 * <p>
	 * By default, this method does nothing. It is meant to be overridden.
	 * 
	 * @throws Exception if a problem occurs
	 */
	protected void onStop() throws Exception {
		// This method is meant to be overridden.
	}
	
	/**
	 * Submits an operation to run on the main thread.
	 * 
	 * @param runnable the operation to run on the main thread
	 * @throws IllegalStateException if the server has stopped
	 */
	protected synchronized void execute(CheckedRunnable runnable) {
		synchronized(status) {
			if(has(Status.FINISHED) || has(Status.STOPPED))
				throw new IllegalStateException("The server can not longer execute instructions.");
			else if(!queue.offer(runnable) && uncaught == null)
				uncaught = new IllegalStateException("The server's instruction queue is full.");
		}
	}
}