package com.sgware.serialsoc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.Socket;

/**
 * A {@link SerialSocket} that reads and writes messages as text and separates
 * messages with new line characters. In other words, {@link #read()} reads text
 * from the socket until it encounters a new line and then returns that line.
 * {@link #write(String)} appends a new line to the end of every message.
 * 
 * @author Stephen G. Ware
 */
public class SimpleSerialSocket extends SerialSocket {
	
	/**
	 * The socket to which this serial socket will read and write.
	 */
	private final Socket socket;
	
	/**
	 * Used to read lines of text from the socket's input stream.
	 */
	private final BufferedReader reader;
	
	/**
	 * Used to write lines of text to the socket's output stream.
	 */
	private final BufferedWriter writer;
	
	/**
	 * Creates a simple serial socket from a socket with given {@link Reader}
	 * (for reading text) and {@link Writer} for writing text. If the reader is
	 * not already a {@link BufferedReader}, it will be wrapped in one. If the
	 * writer is not already a {@link BufferedWriter}, it will be wrapped in
	 * one.
	 * 
	 * @param server the serial server socket that created this socket and on
	 * whose main thread most events will run
	 * @param socket the socket to which this serial socket will read and write
	 * @param reader a reader for the socket's input stream
	 * @param writer a writer for the socket's output stream
	 */
	protected SimpleSerialSocket(SerialServerSocket server, Socket socket, Reader reader, Writer writer) {
		super(server);
		this.socket = socket;
		if(reader instanceof BufferedReader br)
			this.reader = br;
		else
			this.reader = new BufferedReader(reader);
		if(writer instanceof BufferedWriter bw)
			this.writer = bw;
		else
			this.writer = new BufferedWriter(writer);
	}
	
	/**
	 * Creates a simple serial socket from a given socket and uses a default
	 * {@link InputStreamReader} and {@link OutputStreamWriter} for its input
	 * and output channels.
	 * 
	 * @param server the serial server socket that created this socket and on
	 * whose main thread most events will run
	 * @param socket the socket to which this serial socket will read and write
	 * @throws IOException if a problem occurs while getting the socket's input
	 * and output streams
	 */
	protected SimpleSerialSocket(SerialServerSocket server, Socket socket) throws IOException {
		this(server, socket, new InputStreamReader(socket.getInputStream()), new OutputStreamWriter(socket.getOutputStream()));
	}
	
	/**
	 * Returns the remote address to which the socket is connected.
	 * 
	 * @return the remote IP address to which this socket is connected, or null
	 * if the socket is not connected
	 * @see Socket#getInetAddress()
	 */
	public InetAddress getRemoteAddress() {
		return socket.getInetAddress();
	}
	
	/**
	 * Gets the local address to which the socket is bound.
	 * 
	 * @return the local address to which the socket is bound, or the wildcard
	 * address if the socket is closed or not bound yet
	 * @see Socket#getLocalAddress()
	 */
	public InetAddress getLocalAddress() {
		return socket.getLocalAddress();
	}
	
	/**
	 * Returns the local port number to which this socket is bound.
	 * 
	 * @return the local port number to which this socket is bound or -1 if the
	 * socket is not bound yet
	 * @see Socket#getLocalPort()
	 */
	public int getLocalPort() {
		return socket.getLocalPort();
	}
	
	@Override
	/**
	 * {@inheritDoc}
	 * <p>
	 * This method {@link BufferedReader#readLine() reads until it encounters a
	 * new line character} and then returns the string read, without the
	 * trailing new line character at the end. 
	 */
	protected String read() throws IOException {
		return reader.readLine();
	}
	
	@Override
	/**
	 * {@inheritDoc}
	 * <p>
	 * This method writes the string to the socket's output stream. If the
	 * string does not end in a new line character one is appended. Then the
	 * output buffer is {@link BufferedWriter#flush() flushed}.
	 */
	protected void write(String string) throws IOException {
		writer.append(string);
		if(!string.endsWith("\n"))
			writer.append("\n");
		writer.flush();
	}
	
	@Override
	protected void disconnect() throws IOException {
		socket.close();
		reader.close();
		writer.close();
	}
}