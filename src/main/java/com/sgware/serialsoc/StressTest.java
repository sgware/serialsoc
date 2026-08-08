package com.sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

class StressTest {
	
	public static final int PORT = 1234;
	public static final int SERVERS = 100;
	public static final int CLIENTS = 100;
	public static final int MIN_MESSAGES = 0;
	public static final int MAX_MESSAGES = 20;
	public static final int MIN_MESSAGE_LENGTH = 5;
	public static final int MAX_MESSAGE_LENGTH = 40;
	public static final int MIN_DELAY = 0;
	public static final int MAX_DELAY = 100;
	public static final Random RANDOM = new Random(0);
	public static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	private static Exception uncaught = null;
	
	private static abstract class SafeThread extends Thread {
		
		@Override
		public final void run() {
			try {
				call();
			}
			catch(Exception exception) {
				if(uncaught == null)
					uncaught = exception;
			}
		}
		
		public abstract void call() throws Exception;
	}
	
	private static class TestServerThread extends SafeThread {
		
		private static int nextID = 0;
		public final int id = nextID++;
		public final TestSerialServerSocket server;
		private final TestClient[] clients;
		private final long[] delays;
		
		public TestServerThread() throws IOException {
			server = new TestSerialServerSocket(PORT);
			clients = new TestClient[CLIENTS];
			for(int i = 0; i <  clients.length; i++)
				clients[i] = new TestClient();
			delays = new long[clients.length + 1];
			for(int i = 0; i <  delays.length; i++)
				delays[i] = random(MIN_DELAY, MAX_DELAY);
		}
		
		@Override
		public String toString() {
			return "Server " + id;
		}
		
		@Override
		public void call() throws Exception {
			System.out.println(this + " has started.");
			new SafeThread() {
				public void call() throws Exception {
					server.await(Status.CONNECTED);
					System.out.println("Starting " + clients.length + " clients for " + TestServerThread.this + ".");
					Thread.sleep(delays[0]);
					int index = 0;
					while(index < clients.length && uncaught == null) {
						clients[index].start();
						Thread.sleep(delays[index + 1]);
						index++;
					}
					System.out.println("Closing " + TestServerThread.this + ".");
					server.close();
					System.out.println("Started " + index + " of " + clients.length + " clients for " + TestServerThread.this + ".");
					for(TestClient client : clients)
						client.join();
				}
			}.start();
			new Clock(server, 10).start();
			server.run();
			System.out.println(this + " has stopped.");
		}
	}
	
	private static class TestClient extends SafeThread {
		
		private static int nextID = 0;
		public final int id = nextID++;
		private final String[] messages;
		private final long[] delays;
		
		public TestClient() {
			messages = new String[random(MIN_MESSAGES, MAX_MESSAGES)];
			for(int i = 0; i < messages.length; i++) {
				int length = random(MIN_MESSAGE_LENGTH, MAX_MESSAGE_LENGTH);
				StringWriter string = new StringWriter();
				for(int j = 0; j < length; j++)
					string.append(POOL.charAt(RANDOM.nextInt(POOL.length())));
				messages[i] = string.toString();
			}
			delays = new long[Math.max(0, messages.length - 1)];
			for(int i = 0; i < delays.length; i++)
				delays[i] = random(MIN_DELAY, MAX_DELAY);
		}
		
		@Override
		public String toString() {
			return "Client " + id;
		}
		
		@Override
		public void call() throws Exception {
			System.out.println(this + " started.");
			TestClientListener listener = null;
			int index = 0;
			try(
				Socket socket = new Socket("localhost", PORT);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			) {
				listener = new TestClientListener(this, in);
				listener.start();
				while(index < messages.length) {
					out.append(messages[index]);
					out.append("\n");
					out.flush();
					System.out.println(this + " sent message " + index + ": " + messages[index]);
					if(index < delays.length)
						Thread.sleep(delays[index]);
					index++;
				}
			}
			catch(SocketException exception) {
				System.out.println(this + " was disconnected with " + index + " of " + messages.length + " message sent.");
			}
			if(listener != null)
				listener.join();
			System.out.println(this + " stopped.");
		}
	}
	
	private static class TestClientListener extends Thread {
		
		private final TestClient client;
		private final BufferedReader in;
		
		private TestClientListener(TestClient client, BufferedReader in) {
			this.client = client;
			this.in = in;
		}
		
		@Override
		public String toString() {
			return client + " Listener";
		}
		
		@Override
		public void run() {
			try {
				while(true) {
					String message = in.readLine();
					if(message == null)
						return;
					else
						System.out.println(client + " received: " + message);
				}
			}
			catch(Exception exception) {
				System.out.println(this + " stopped.");
			}
		}
	}
	
	private static final int random(int min, int max) {
		return min + RANDOM.nextInt(max - min + 1);
	}
	
	public static void main(String[] args) throws Exception {
		TestServerThread[] servers = new TestServerThread[SERVERS];
		for(int i = 0; i < servers.length; i++)
			servers[i] = new TestServerThread();
		int index = 0;
		while(index < servers.length && uncaught == null) {
			servers[index].start();
			servers[index].join();
			if(uncaught == null)
				servers[index].server.verify();
			else
				throw uncaught;
			index++;
		}
	}
}