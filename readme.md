# Serial Server Sockets (Java)

A Serial Server Socket simplifies a common design pattern in socket-based
networking applications where a server waits for and accepts new sockets, and
then each new socket waits for and reports each line of input it receives.
A serial server socket ensures everything happens on one main thread and ensures
a clean start up and shut down even if an uncaught exception is thrown.

Note that "serialization" here means "make everything happen in order on one
thread." It is not referring to object serialization, which means to encode or
decode an object.

When you call `SerialServerSocket.run()`:
- `onStart()` is called first on the same thread that called `run()`. If it
  throws an exception, no other methods will run.
- `connect()` is called on the same thread that called `run()` to establish a
  server socket. If it does not throw an exception, `onConnect()` is called on
  the same thread that called `run()`.
- If `connect()` did not throw an exception, the server starts a new thread that
  continuously calls `accept()` to wait for new connections.
- Each time a new socket is accepted, the server calls `create(Socket)` on the
  same thread that called `run()` to wrap a `SerialSocket` around the new socket.
  If `create(Socket)` throws an exception, the socket is closed and never
  reported to the server. If `create(Socket)` does not throw an exception, the
  new socket is reported to the server's `onAccept(SerialSocket)` method, which
  runs on the same thread that called `run()`.
- Each time a new `SerialSocket` is successfully created, its `onConnect()`
  method is called first on the same thread that called `run()`.
- If the socket's `onConnect()` method did not throw an exception, a new thread
  continuously calls its `read()` method to listen for new input. Each input
  that is successfully received is reported to its `receive(String)` method,
  which runs on the same thread that called `run()`.
- A socket can be closed by the client, by a network problem, because one of its
  methods threw an exception, or by calling its `close()` method from any
  thread. Regardless of how it is closed, its `onClose()`, then `disconnect()`,
  then `onDisconnect()` methods are always called in that order from the same
  thread that called `run()`. These methods always run even if an earlier method
  threw an exception.
- If any `SerialSocket` methods throw an exception, the exception is reported to
  its `onException(Exception)` method on the same thread that called `run()`,
  and then the socket will close gracefully. `onException(Exception)` can either
  ignore the exception or re-throw it to cause the server to shut down.
- If any `SerialServerSocket` methods throw an exception (or if
  `SerialSocket.onException(Exception)` re-throws an exception), the exception
  is reported to `SerialServerSocket.onException(Exception)` on the same thread
  that called `run()`, and then the server will shut down gracefully.
- A server shuts down when one of its methods throws an exception, when a
  network problem causes the server to disconnect, when the JVM shuts down, or
  when `close()` is called from any thread. Regardless of how it is closed, if
  `connect()` did not throw an exception, its `onClose()`, then `disconnect()`,
  then `onDisconnect()` methods are always called in that order from the same
  thread that called `run()`.
- As long as `onStart()` did not throw an exception, `onStop()` is always called
  last from the same thread that called `run()`.
- If an exception was thrown by any of the server's methods, it is thrown by
  `run()` after all of the shut down methods have completed.

Because all of these events happen on the same thread, the server may be able to
avoid synchronizing its data structures. Because methods like `onClose()` and
`onStop()` will always be called, even when an uncaught exception is thrown, the
server can be sure to perform any cleanup required before shutting down
gracefully.

## Download

Download the [pre-built JAR file here](build/jar).

You can add this library a dependency to a Maven project like this:
```
<dependency>
    <groupId>com.sgware</groupId>
    <artifactId>serialsoc</artifactId>
    <version>2.0.0</version>
</dependency>
```

## Documentation

The [JavaDoc API is here](http://sgware.github.io/serialsoc).

## Compile and Test

Serial Socket Server is pure Java with no dependencies. To compile it from the
terminal with just the JDK:

```
git clone https://github.com/sgware/serialsoc.git
cd serialsoc
javac -sourcepath src -d bin src/main/java/com/sgware/serialsoc/*.java
```

A stress test is included in the project, which starts a server and then starts
many clients who each connect, send a random number of random messages, and
disconnect. Each method checks that it is called in the right order from the
right thread. Assuming you compiled it into the `bin` folder:

```
java -cp bin com.sgware.serialsoc.StressTest
```

If you have Maven installed, you can compile the source, generate the
documentation, package the JAR file, and add its to your local repository like
this:

```
git clone https://github.com/sgware/serialsoc.git
cd serialsoc
mvn clean install
```

## Example

Here's an example of implementing a basic chat room using Serial Server Sockets:

```
import java.util.ArrayList;
import java.util.List;
import java.io.IOException;
import java.net.Socket;
import com.sgware.serialsoc.*;

public class ChatServer extends SimpleSerialServerSocket {
	
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
	
	final List<ChatUser> users = new ArrayList<>();
	
	public ChatServer(int port) throws IOException {
		super(port);
	}
		
	@Override
	protected ChatUser create(Socket socket) throws IOException {
		return new ChatUser(this, socket);
	}
	
	@Override
	protected void onStart() {
		System.out.println("The chat server has started.");
	}
	
	@Override
	protected void onConnect() {
		System.out.println("The chat server is now accepting new connections.");
	}
	
	@Override
	protected void onException(Exception exception) {
		System.out.println("The chat server has crashed: " + exception.getMessage());
	}
	
	@Override
	protected void onClose() {
		System.out.println("The chat server has been closed.");
	}
	
	@Override
	protected void onDisconnect() {
		System.out.println("The chat server is no longer accepting new connections.");
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
import java.io.IOException;
import java.net.Socket;
import com.sgware.serialsoc.*;

public class ChatUser extends SimpleSerialSocket {
	
	private final ChatServer server;
	private String name = null;
	
	protected ChatUser(ChatServer server, Socket socket) throws IOException {
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
	protected void onException(Exception exception) throws Exception {
		System.out.println("User " + name + " has crashed: " + exception.getMessage());
		throw exception;
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

Even when the server crashes because of an uncaught exception, it still closes
all open connections and gracefully shuts down via `onClose()` and `onStop()`
before finally throwing the exception.

## Ownership and License

Serial Server Sockets was created in December 2025 by Stephen G. Ware, Ph.D.
Though he was faculty at the University of Kentucky at the time, this software
was created during a sabbatical, and no university resources were used during
development.

This software is released under the open source [MIT License](license.txt).

## Version History

- Version 2.0.0: Major revisions. `SerialServerSocket` and `SerialSocket` are
  now abstract classes, with `SimpleSerialServerSocket` and `SimpleSerialSocket`
  replacing the old concrete classes. `SecureSerialServerSocket` is now
  included, which creates `SSLSockets`. Some methods have been renamed and more
  event methods have been added. When the server accepts a socket, it waits for
  all its setup methods to run before accepting the next socket. When a socket
  reads input, it waits for it to be fully processed before reading the next
  message. Fixed several bugs, including ones where exceptions throw by the
  socket were not always reported to the socket's `onException(Exception)`
  method.
- Version 1.3.0: Added a shutdown hook when the server starts so that if the JVM
  is shut down (e.g. via SIGTERM) the server will close and stop gracefully.
- Version 1.2.0: Added an `onException` method to `SerialSocket` that can be
  used to log and possibly ignore exceptions caused by individual sockets.
- Version 1.1.0: Added a the `accept(ServerSocket)` method to
  `SerialServerSocket` so that servers using SSL can check for handshake success
  before returning a new socket.
- Version 1.0.0: First public release.