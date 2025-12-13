/**
 * Wrappers for {@link java.net.ServerSocket ServerSocket} and {@link
 * java.net.Socket Socket} that provide useful events, guarantee the order of
 * those events, and ensure all events happen on the same thread.
 * <p>
 * A {@link SerialServerSocket} simplifies a common design pattern in
 * socket-based networking applications where a server waits for and accepts new
 * sockets, and then each new socket waits for and reports each line of input it
 * receives. When you call {@link SerialServerSocket#run()}, all of the
 * following events will happen <i>on the thread that called {@link
 * SerialServerSocket#run()}</i>:
 * <ul>
 * <li>The server will call {@link SerialServerSocket#createServer()} to bind a
 * {@link java.net.ServerSocket ServerSocket}. If that method throws an
 * exception, it is thrown immediately and no other events will happen.</li>
 * <li>The server's {@link SerialServerSocket#onStart() onStart} method is
 * called exactly once and before any other events.</li>
 * <li>Each time the server socket accepts a new connection, the server will
 * call {@link SerialServerSocket#createSocket(java.net.Socket)} to make a new
 * instance of {@link SerialSocket}.</li>
 * <li>When a new socket connects, its {@link SerialSocket#onConnect()
 * onConnect} method is called exactly once and before any other events for that
 * socket.</li>
 * <li>Each time a socket {@link java.io.BufferedReader#readLine() receives a
 * line of input}, its {@link SerialSocket#receive(String) receive} method is
 * called with that input as a string.</li>
 * <li>When a socket is closed, either because something on the server side
 * called {@link SerialSocket#close()} or because the client closed the
 * connection, its {@link SerialSocket#onClose() onClose} method is called
 * exactly once.</li>
 * <li>After a socket has disconnected, its {@link SerialSocket#onDisconnect()
 * onDisconnect} method is called exactly once, and it will always be the last
 * event for that socket.</li>
 * <li>When the server is closed, either because {@link
 * SerialServerSocket#close()} was called, because the thread was interrupted,
 * because the server socket was disconnected, or because an uncaught exception
 * was thrown, the server's {@link SerialServerSocket#onClose() onClose} method
 * is called exactly once.</li>
 * <li>When a server closes, all of its currently connected sockets will be
 * closed, and their {@link SerialSocket#onClose() onClose} and {@link
 * SerialSocket#onDisconnect() onDisconnect} methods will be called before the
 * server stops.</li>
 * <li>After all other events, the server's {@link SerialServerSocket#onStop()
 * onStop} method will be called exactly once.</li>
 * <li>If at any time an uncaught exception is thrown, the server's {@link
 * SerialServerSocket#onException(Exception) onException} method will be called
 * immediately, then the server will close and stop, and then, after all events
 * have finished, the uncaught exception will be thrown by {@link
 * SerialServerSocket#run()}. If more than one uncaught exception is thrown,
 * {@link SerialServerSocket#onException(Exception) onException} will be called
 * for all of them, but only the first uncaught exception will be thrown by
 * {@link SerialServerSocket#run()}.</li>
 * </ul>
 * Because all of these events happen on the same thread, the server may be able
 * to avoid synchronizing its data structures. Because events like {@link
 * SerialSocket#onClose() onClose} and {@link SerialServerSocket#onStop() onStop}
 * will always happen, even when an uncaught exception is thrown, the server can
 * be sure to perform any cleanup required before shutting down gracefully.
 * 
 * @author Stephen G. Ware
 * @version 1
 */
package sgware.serialsoc;