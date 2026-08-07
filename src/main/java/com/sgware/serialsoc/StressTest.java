package com.sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.Random;

class StressTest {
	
	public static final int PORT = 1234;
	public static final int CLIENTS = 100000;
	public static final int MIN_MESSAGES = 0;
	public static final int MAX_MESSAGES = 20;
	public static final int MIN_MESSAGE_LENGTH = 5;
	public static final int MAX_MESSAGE_LENGTH = 40;
	public static final int MIN_DELAY = 0;
	public static final int MAX_DELAY = 100;
	public static final Random RANDOM = new Random(0);
	public static final String POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
	
	private static class TestClient extends Thread {
		
		private static int nextID = 0;
		public final int id = nextID++;
		private final String[] messages;
		private final long[] delays;
		private boolean connected = false;
		private boolean disconnected = false;
		
		public TestClient() {
			messages = new String[random(MIN_MESSAGES, MAX_MESSAGES)];
			for(int i = 0; i < messages.length; i++) {
				int length = random(MIN_MESSAGE_LENGTH, MAX_MESSAGE_LENGTH);
				StringWriter string = new StringWriter();
				for(int j = 0; j < length; j++)
					string.append(POOL.charAt(RANDOM.nextInt(POOL.length())));
				messages[i] = string.toString();
			}
			delays = new long[messages.length + 1];
			for(int i = 0; i < delays.length; i++)
				delays[i] = random(MIN_DELAY, MAX_DELAY);
		}
		
		@Override
		public String toString() {
			return "Client " + id;
		}
		
		public boolean launch() {
			start();
			while(!connected && !disconnected)
				pause(10);
			return connected;
		}
		
		@Override
		public void run() {
			System.out.println(this + " started.");
			TestClientListener listener = null;
			int index = 0;
			try(
				Socket socket = new Socket("localhost", PORT);
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			) {
				connected = true;
				pause(delays[0]);
				listener = new TestClientListener(this, in);
				listener.start();
				while(index < messages.length) {
					out.append(messages[index]);
					out.append("\n");
					out.flush();
					System.out.println(this + " sent: " + messages[index]);
					pause(delays[index]);
					index++;
				}
			}
			catch(SocketException exception) {
				System.out.println(this + " crashed with " + (messages.length - index) + " of " + messages.length + " message unsent.");
			}
			catch(Exception exception) {
				throw new RuntimeException(this + " crashed.", exception);
			}
			finally {
				disconnected = true;
			}
			try {
				if(listener != null)
					listener.join();
			}
			catch(InterruptedException exception) {
				throw new RuntimeException(this + " crashed.", exception);
			}
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
				System.out.println(this + " crashed.");
			}
		}
	}
	
	private static final int random(int min, int max) {
		return min + RANDOM.nextInt(max - min + 1);
	}
	
	private static final void pause(long ms) {
		try {
			Thread.sleep(ms);
		}
		catch(InterruptedException exception) {
			throw new RuntimeException("Pause interrupted.", exception);
		}
	}
	
	public static void main(String[] args) throws Exception {
		TestSerialServerSocket server = new TestSerialServerSocket(PORT);
		TestClient[] clients = new TestClient[CLIENTS];
		for(int i = 0; i <  clients.length; i++)
			clients[i] = new TestClient();
		long[] delays = new long[clients.length];
		for(int i = 0; i <  clients.length; i++)
			delays[i] = random(MIN_DELAY, MAX_DELAY);
		Thread clientThread = new Thread(() -> {
			pause(500);
			System.out.println("Begin starting clients.");
			int index = 0;
			for(index = 0; index < clients.length; index++) {
				if(clients[index].launch())
					pause(delays[index]);
				else
					break;
			}
			System.out.println("Started " + index + " of " + clients.length + " clients.");
			pause(500);
			server.close();
			try {
				for(TestClient client : clients)
					client.join();
			}
			catch(InterruptedException exception) {
				throw new RuntimeException("Client thread interrupted.", exception);
			}
		});
		clientThread.start();
		Exception uncaught = null;
		try {
			server.run();
		}
		catch(Exception exception) {
			uncaught = exception;
		}
		clientThread.join();
		if(uncaught == null)
			server.verify();
		else
			uncaught.printStackTrace();
	}
}