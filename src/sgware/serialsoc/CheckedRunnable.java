package sgware.serialsoc;

import java.util.concurrent.Callable;

/**
 * A functional interface similar with {@link Runnable}, except that it declares
 * a checked exception, and similar to {@link Callable} except that it returns
 * void.
 * 
 * @author Stephen G. Ware
 */
@FunctionalInterface
public interface CheckedRunnable extends Callable<Void> {
	
	/**
	 * Perform the operation, possibly throwing an exception in the process.
	 * 
	 * @throws Exception if the operation threw an exception
	 */
	public void run() throws Exception;
	
	@Override
	public default Void call() throws Exception {
		run();
		return null;
	}
}