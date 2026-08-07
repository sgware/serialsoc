package com.sgware.serialsoc;

import java.util.concurrent.Callable;

/**
 * A functional interface similar to {@link Runnable}, except that it declares
 * a checked exception, and similar to {@link Callable} except that it returns
 * void.
 * 
 * @author Stephen G. Ware
 */
@FunctionalInterface
public interface CheckedRunnable extends Callable<Void> {
	
	/**
	 * Perform the operation, possibly throwing an exception.
	 * 
	 * @throws Exception if a problem occurred
	 */
	public void run() throws Exception;
	
	@Override
	public default Void call() throws Exception {
		run();
		return null;
	}
}