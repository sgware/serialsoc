/**
 * A serial server socket listens for incoming socket connections and ensures
 * all inputs from them are processed one at a time on a single thread. It also
 * guarantees that certain methods run in order, including setup and shutdown
 * methods for the server and each socket, even if an exception is thrown.
 * <p>
 * This package includes:
 * <ul>
 * <li>The abstract {@link SerialServerSocket} and {@link SerialSocket} classes.
 * </li>
 * <li>The server {@link Status} enum.</li>
 * <li>The {@link SimpleSerialServerSocket} and {@link SimpleSerialSocket}
 * classes that use a {@link java.net.ServerSocket ServerSocket} to accept
 * default {@link java.net.Socket Socket}s, read and write messages as strings,
 * and separate messages with new line characters.</li>
 * <li>The {@link SecureSerialServerSocket} class that extends {@link
 * SimpleSerialServerSocket} but uses an {@link javax.net.ssl.SSLServerSocket
 * SSLServerSocket} to accept {@link javax.net.ssl.SSLSocket SSLSocket}s.</li>
 * <li>The {@link Clock} class that can be used to call {@link
 * SerialServerSocket#tick()} and {@link SerialSocket#tick()} at regular
 * intervals.</li>
 * </ul>
 * 
 * @author Stephen G. Ware
 */
package com.sgware.serialsoc;