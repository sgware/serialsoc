package com.sgware.serialsoc;

import java.util.Objects;

/**
 * A thread that, once {@link #start() started}, calls {@link
 * SerialServerSocket#tick()} on a server and {@link SerialSocket#tick()} on
 * each of its connected sockets at a regular interval on the same thread that
 * called {@link SerialServerSocket#run()}.
 * <p>
 * There is no guarantee that ticks will always occur at the right time, because
 * it is possible that other operations running on that server's main thread
 * will not finish in time to allow the next tick to occur.
 * <p>
 * A clock automatically and silently stops ticking when its server is {@link
 * SerialServerSocket#close() closed}.
 * 
 * @author Stephen G. Ware
 */
public class Clock extends Thread {
	
	/**
	 * The delay used by a default clock.
	 */
	public static final long DEFAULT_DELAY = 1000; // 1 second
	
	/**
	 * The server that will tick regularly.
	 */
	private final SerialServerSocket server;
	
	/**
	 * The amount of time (in milliseconds) between calls to {@link
	 * SerialServerSocket#tick()} and {@link SerialSocket#tick()}.
	 */
	public final long delay;
	
	/**
	 * Creates (but does not start) a new clock thread for a given server with
	 * a given interval between ticks.
	 * 
	 * @param server the server that will tick regularly
	 * @param delay the delay before the first tick and between later ticks
	 */
	public Clock(SerialServerSocket server, long delay) {
		Objects.requireNonNull(server);
		this.server = server;
		this.delay = delay;
	}
	
	/**
	 * Creates (but does not start) a new clock thread for a given server with
	 * the {@link #DEFAULT_DELAY default interval} between ticks.
	 * 
	 * @param server the server that will tick regularly
	 */
	public Clock(SerialServerSocket server) {
		this(server, DEFAULT_DELAY);
	}
	
	@Override
	/**
	 * {@link SerialServerSocket#await(Status) Waits} for the server to connect
	 * and then regularly calls {@link SerialServerSocket#tick()} and {@link
	 * SerialSocket#tick()}, {@link #delay waiting} between each one, until the
	 * server closes.
	 */
	public void run() {
		try {
			server.await(Status.CONNECTED);
			while(!server.has(Status.CLOSED)) {
				Thread.sleep(delay);
				server.execute(() -> {
					if(!server.has(Status.CLOSED)) {
						server.tick();
						for(SerialSocket socket : server.sockets) {
							if(!socket.hasBeenClosed())
								socket.safely(() -> socket.tick());
						}
					}
				});
			}
		}
		catch(Exception exception) {
			// Ignore exceptions from execute.
		}
	}
}