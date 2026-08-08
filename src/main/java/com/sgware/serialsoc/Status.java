package com.sgware.serialsoc;

/**
 * The possible states of a {@link SerialServerSocket}. These values are used
 * by {@link SerialServerSocket#getStatus()}, {@link
 * SerialServerSocket#has(Status)}, and {@link
 * SerialServerSocket#await(Status)}.
 * 
 * @author Stephen G. Ware
 */
public enum Status {
	
	/**
	 * A server's {@link SerialServerSocket#run()} method has been called, but
	 * {@link SerialServerSocket#onStart()} has not yet completed.
	 */
	RUN,
	
	/**
	 * A server's {@link SerialServerSocket#onStart()} method completed
	 * successfully, but the server has not yet {@link
	 * SerialServerSocket#connect() connected}.
	 */
	STARTED,
	
	/**
	 * A server's {@link SerialServerSocket#connect()} method completed
	 * successfully and the server has not yet been {@link
	 * SerialServerSocket#close() closed}.
	 */
	CONNECTED,
	
	/**
	 * A server's {@link SerialServerSocket#close()} method has been called, or
	 * the server has closed automatically for another reason (like an exception
	 * or a network problem), but it has not yet {@link
	 * SerialServerSocket#disconnect() disconnected}.
	 */
	CLOSED,
	
	/**
	 * A server previously {@link SerialServerSocket#connect() connected} and
	 * {@link SerialServerSocket#disconnect()} has run.
	 */
	DISCONNECTED,
	
	/**
	 * A server previously {@link SerialServerSocket#disconnect() disconnected},
	 * is shutting down, and is no longer accepting operations via {@link
	 * SerialServerSocket#execute(CheckedRunnable)}.
	 */
	FINISHED,
	
	/**
	 * Either a server's {@link SerialServerSocket#onStart()} method threw an
	 * exception or {@link SerialServerSocket#onStop()} has run and no more
	 * events will happen for this server.
	 */
	STOPPED
}