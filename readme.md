# Serial Server Sockets (Java)

A Serial Server Socket simplifies a common design pattern in socket-based
networking applications where a server waits for and accepts new sockets, and
then each new socket waits for and reports each line of input it receives.
A serial server socket ensures everything happens on one main thread and ensures
a clean shut down even if an uncaught exception is thrown.

Note that "serialization" here means "make everything happen in order on one
thread." It is not referring to object serialization, which means to encode or
decode an object.

When you call `SerialServerSocket.run()`, all of the following events will
happen on the thread that called `run` in this order:
- A factory method will be called to create and bind a `java.net.ServerSocket`.
If that method throws an exception, it is thrown immediately and no other events
will happen.
- The server's `onStart` method is called exactly once and before any other
events.
- Each time the server socket accepts a new connection, the server will call a
factory method that creates a new `SerialSocket` instance.
- When a new socket connects, its `onConnect` method is called exactly once and
before any other events for that socket.
- Each time a socket reads a line of input, its `receive` method is called with
that input as a string.
- When a socket is closed, either because something on the server side called
its `close` method or because the client closed the connection, its `onClose`
method is called exactly once.
- After a socket has disconnected, its `onDisconnect` method is called exactly
once, and it will always be the last event for that socket.
- When the server is closed, either because its `close` method was called,
because the thread was interrupted, because the server socket was disconnected,
or because an uncaught exception was thrown, the server's `onClose` method is
called exactly once. When a server closes, all of its currently connected
sockets will be closed, and their `onClose` and `onDisconnect` methods will be
called before the server stops.
- After all other events, the server's `onStop` method will be called exactly
once.
- If at any time an uncaught exception is thrown, the server's `onException`
method will be called immediately, then the server will close and stop, and
then, after all events have finished, the uncaught exception will be thrown from
the server's main `run()` method. If more than one uncaught exception is thrown,
`onException` will be called for all of them, but only the first uncaught
exception will be thrown from `run`.

Because all of these events happen on the same thread, the server may be able to
avoid synchronizing its data structures. Because events like `onClose` and
`onStop` will always happen, even when an uncaught exception is thrown, the
server can be sure to perform any cleanup required before shutting down
gracefully.

## Download

Download the [JAR file](jar/serialsoc.jar) here.

## Compile and Test

Serial Socket Server is pure Java with no dependencies.

```
git clone https://github.com/sgware/serialsoc
cd serialsoc
javac -sourcepath src -d bin src/sgware/serialsoc/*.java
```

A stress test is included in the project, which starts a server and then starts
many clients who each connect, send a random number of random messages, and
disconnect. Each method checks that it is called in the right order from the
right thread.

```
java -cp bin sgware.serialsoc.StressTest
```

There is also an ANT build file included which will compile the source, create
the JavaDoc, and package the relevant files into a JAR.

## Example

Here's an example of implementing a basic chat room using Serial Server Sockets:

```
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class ChatServer extends SerialServerSocket {
	
	public static final void main(String[] args) throws Exception {
		int port;
		if(args.length > 0)
			port = Integer.parseInt(args[0]);
		else
			port = 1234;
		try(ChatServer server = new ChatServer(port)) {
			server.run();
		}
	}
	
	public final int port;
	final List<ChatUser> users = new ArrayList<>();
	
	public ChatServer(int port) {
		this.port = port;
	}
	
	@Override
	protected ServerSocket createServer() throws IOException {
		return new ServerSocket(port);
	}
	
	@Override
	protected ChatUser createSocket(Socket socket) throws Exception {
		return new ChatUser(this, socket);
	}
	
	@Override
	protected void onStart() {
		System.out.println("The chat server has started.");
	}
	
	@Override
	protected void onException(Exception exception) {
		System.out.println("The chat server has crashed.");
		exception.printStackTrace();
	}
	
	@Override
	protected void onClose() {
		System.out.println("The chat server has been closed.");
	}
	
	@Override
	protected void onStop() {
		System.out.println("The chat server has stopped.");
	}
	
	public void broadcast(String message) throws Exception {
		System.out.println(message);
		for(ChatUser user : users)
			user.send(message);
	}
}
```

```
import java.net.Socket;

public class ChatUser extends SerialSocket {
	
	private final ChatServer server;
	private String name = null;
	
	protected ChatUser(ChatServer server, Socket socket) throws Exception {
		super(server, socket);
		this.server = server;
	}
	
	@Override
	protected void onConnect() throws Exception {
		server.users.add(this);
		send("Type your name and press enter.");
	}
	
	@Override
	protected void receive(String message) throws Exception {
		if(name == null) {
			name = message;
			server.broadcast(name + " has connected.");
		}
		else if(message.equals("halt"))
			server.close();
		else if(message.equals("catch fire"))
			throw new Exception("Fire!");
		else
			server.broadcast(name + ": " + message);
	}
	
	@Override
	protected void onClose() throws Exception {
		send("You are being disconnected.");
	}
	
	@Override
	protected void onDisconnect() throws Exception {
		server.users.remove(this);
		server.broadcast(name + " has disconnected.");
	}
}
```

Run `ChatServer`, then connect to `localhost` on port 1234. Type your name,
press enter, and then type a message to send to the chat room. If you type
`halt` the server will shut down, and if you type `catch fire` an exception
will cause the server to crash.

## Ownership and License

Serial Server Sockets was created in December 2025 by Stephen G. Ware, Ph.D.
Though he was faculty at the University of Kentucky at the time, this software
was created during a sabbatical, and no university resources were used during
development.

This software is released under the open source MIT License.

## Version History

- Version 1: First public release.