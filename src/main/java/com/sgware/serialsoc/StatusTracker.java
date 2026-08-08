package com.sgware.serialsoc;

import java.util.concurrent.CountDownLatch;

/**
 * A tool for tracking the {@link Status} of a {@link SerialServerSocket}.
 * 
 * @author Stephen G. Ware
 */
class StatusTracker {
	
	/**
	 * Used to detect if a status has occurred or cannot occur.
	 */
	private final CountDownLatch[] latches;
	
	/**
	 * Records which statuses have occurred.
	 */
	private final boolean[] occurred;
	
	/**
	 * The server's current status.
	 */
	private Status current = null;
	
	/**
	 * Creates a new status tracker.
	 */
	public StatusTracker() {
		latches = new CountDownLatch[Status.values().length];
		for(int i = 0; i < latches.length; i++)
			latches[i] = new CountDownLatch(1);
		occurred = new boolean[latches.length];
	}
	
	/**
	 * Returns the current status.
	 * 
	 * @return the current status
	 */
	public synchronized Status get() {
		return current;
	}
	
	/**
	 * Checks whether or not a status has ever occurred.
	 * 
	 * @param status the status in question
	 * @return true if that status has ever occurred, false otherwise
	 */
	public synchronized boolean has(Status status) {
		return occurred[status.ordinal()];
	}
	
	/**
	 * Sets the current status. Once a status has been set, it has {@link
	 * #has(Status) occurred}, and all statuses before it are no longer
	 * {@link #await(Status) awaited}. The status can only be set to a
	 * later status. This method does nothing if the given status is earlier
	 * than the current status.
	 * 
	 * @param status the new current status
	 */
	synchronized void set(Status status) {
		// Status can only go up.	
		if(current != null && status.ordinal() <= current.ordinal())
			return;
		// Set the new current status.
		current = status;
		// This and all earlier status are no longer awaited.
		for(int i = 0; i <= status.ordinal(); i++)
			latches[i].countDown();
		// This status has now occurred.
		occurred[status.ordinal()] = true;
	}
	
	/**
	 * Waits for a given status (or a status after it) to be {@link #set(Status)
	 * set}.
	 * 
	 * @param status the status to wait for
	 * @throws InterruptedException if the thread is interrupted while waiting
	 */
	public void await(Status status) throws InterruptedException {
		latches[status.ordinal()].await();
	}
}