package sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Create a serial server socket, then have many clients connect to it, send a
 * random number of random messages, and disconnect. All methods check that they
 * are called in the right order from the right thread.
 * 
 * @author Stephen G. Ware
 */
class StressTest {
	
	private static final int PORT = 1234;
	private static final int CLIENTS = 10000;
	private static final Random RANDOM = new Random(0);
	private static Thread SERVER_THREAD = null;
	
	public static void main(String[] args) throws Exception {
		// Create clients.
		TestClient[] clients = new TestClient[CLIENTS];
		for(int i = 0; i < clients.length; i++)
			clients[i] = new TestClient();
		// Start server.
		TestServer server = new TestServer();
		SERVER_THREAD = new Thread(() -> {
			try {
				server.run();
			}
			catch(Exception exception) {
				exception.printStackTrace();
			}
		});
		SERVER_THREAD.start();
		// Start each client after a random delay.
		for(TestClient client : clients) {
			pause();
			client.start();
		}
		// Close the server and wait for it to finish.
		server.close();
		SERVER_THREAD.join();
	}
	
	private static class TestServer extends SerialServerSocket {
		
		public final List<TestSocket> sockets = new ArrayList<>();
		public boolean started = false;
		public boolean closed = false;
		public boolean stopped = false;
		
		@Override
		public String toString() {
			return "[Test Server: sockets=" + sockets.size() + "; started=" + started + "; closed=" + closed + "; stopped=" + stopped + "]";
		}
		
		@Override
		protected ServerSocket createServer() throws IOException {
			checkThread();
			return new ServerSocket(PORT);
		}
		
		@Override
		protected TestSocket createSocket(Socket socket) throws Exception {
			checkThread();
			return new TestSocket(this, socket);
		}
		
		@Override
		protected void onStart() {
			checkThread();
			if(started || closed || stopped)
				throw new RuntimeException("Server started out of order: " + this);
			started = true;
			System.out.println("Server started.");
		}
		
		@Override
		protected void onException(Exception exception) {
			checkThread();
			exception.printStackTrace();
			System.exit(1);
		}
		
		@Override
		protected void onClose() {
			checkThread();
			if(!started || closed || stopped)
				throw new RuntimeException("Server closed out of order: " + this);
			closed = true;
			System.out.println("Server closed.");
		}
		
		@Override
		protected void onStop() {
			checkThread();
			if(!started || !closed || stopped)
				throw new RuntimeException("Server stopped out of order: " + this);
			stopped = true;
			System.out.println("Server stopped.");
		}
		
		public void broadcast(String message) throws Exception {
			checkThread();
			for(TestSocket socket : sockets)
				socket.send(message);
		}
	}
	
	private static class TestSocket extends SerialSocket {
		
		private static int nextID = 0;
		
		public final TestServer server;
		public final int id = nextID++;
		public boolean connected = false;
		public boolean closed = false;
		public boolean disconnected = false;
		
		protected TestSocket(TestServer server, Socket socket) throws Exception {
			super(server, socket);
			this.server = server;
			checkThread();
		}
		
		@Override
		public String toString() {
			return "[Test Socket: id=" + id + "; connected=" + connected + "; closed=" + closed + "; disconnected=" + disconnected + "]";
		}
		
		@Override
		protected void onConnect() throws Exception {
			checkThread();
			if(connected || closed || disconnected)
				throw new RuntimeException("Socket connected out of order: " + this);
			if(!server.started || server.closed || server.stopped)
				throw new RuntimeException("Socket connected out of order: " + server);
			connected = true;
			server.broadcast(id + " has connected.");
			server.sockets.add(this);
			send("You have connected with ID number " + id + ".");
			System.out.println("Socket " + id + " has connected.");
		}
		
		@Override
		protected void receive(String message) throws Exception {
			checkThread();
			if(!connected || disconnected)
				throw new RuntimeException("Socket received out of order: " + this);
			if(!server.started || server.stopped)
				throw new RuntimeException("Socket received out of order: " + server);
			server.broadcast(id + ": " + message);
		}
		
		@Override
		protected void onClose() throws Exception {
			checkThread();
			if(!connected || closed || disconnected)
				throw new RuntimeException("Socket closed out of order: " + this);
			if(!server.started || server.stopped)
				throw new RuntimeException("Socket closed out of order: " + server);
			closed = true;
			send("Your connection has been closed.");
			System.out.println("Client " + id + " has been closed.");
		}
		
		@Override
		protected void onDisconnect() throws Exception {
			checkThread();
			if(!connected || !closed || disconnected)
				throw new RuntimeException("Socket disconnected out of order: " + this);
			if(!server.started || server.stopped)
				throw new RuntimeException("Socket disconnected out of order: " + server);
			disconnected = true;
			server.sockets.remove(this);
			server.broadcast(id + " has disconnected.");
			System.out.println("Client " + id + " has disconnected.");
		}
	}
	
	private static class TestClient extends Thread {
		
		public Socket socket = null;
		private final String[] messages;
		
		public TestClient() throws IOException {
			this.messages = new String[RANDOM.nextInt(100)];
			for(int i = 0; i < messages.length; i++)
				this.messages[i] = message();
		}
		
		@Override
		public void run() {
			try {
				socket = new Socket("localhost", PORT);
				Thread listener = new TestClientListener(this);
				listener.start();
				BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				for(String message : messages) {
					pause();
					output.append(message);
					output.append("\n");
					output.flush();
				}
				socket.close();
				listener.join();
			}
			catch(Exception exception) {
				// Ignore client exceptions.
			}
		}
	}
	
	private static class TestClientListener extends Thread {
		
		public final TestClient client;
		
		public TestClientListener(TestClient client) {
			this.client = client;
		}
		
		@Override
		public void run() {
			try {
				BufferedReader input = new BufferedReader(new InputStreamReader(client.socket.getInputStream()));
				String line = input.readLine();
				while(line != null)
					line = input.readLine();
			}
			catch(IOException exception) {
				// Ignore client exceptions.
			}
		}
	}
	
	private static final void checkThread() {
		if(Thread.currentThread() != SERVER_THREAD)
			throw new RuntimeException("Method is not running on the server thread.");
	}
	
	private static final void pause() {
		long milliseconds = RANDOM.nextLong(1000);
		if(milliseconds < 10)
			return;
		try {
			Thread.sleep(RANDOM.nextLong(milliseconds));
		}
		catch(InterruptedException exception) {
			// Ignore
		}
	}
	
	private static final String message() {
		int length = 1 + RANDOM.nextInt(100);
		String string = "";
		for(int i = 0; i < length; i++)
			string += Character.toString(65 + RANDOM.nextInt(26));
		return string;
	}
}