package org.johnnei.javatorrent.network.socket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * A socket facade to get multiple socket protocols work on the some functions<br>
 * @author Johnnei
 *
 */
public interface ISocket extends AutoCloseable {

	/**
	 * Connects the underlaying Socket to the endpoint
	 * @param endpoint The Address to connect to
	 * @throws IOException When connection fails
	 */
	void connect(InetSocketAddress endpoint) throws IOException;
	/**
	 * Gets the inputstream from the underlaying socket
	 * @return An InputStream which allows to read data from it
	 * @throws IOException when the stream could not be created
	 */
	InputStream getInputStream() throws IOException;
	/**
	 * Gets the outputstream from the underlaying socket
	 * @return An outputstream which allows to write data to the socket
	 * @throws IOException when the stream could not be created
	 */
	OutputStream getOutputStream() throws IOException;

	/**
	 * Formally closes the connection
	 * @throws IOException When the connection could not be closed
	 */
	@Override
	void close() throws IOException;

	/**
	 * Checks if the connection has been closed
	 * @return true if the connection is closing/closed
	 */
	boolean isClosed();

	/**
	 * Checks if there will be no more data incoming
	 * @return true if EOF has been found
	 */
	boolean isInputShutdown();

	/**
	 * Checks if no more data can be send on this socket
	 * @return true if EOF has been send
	 */
	boolean isOutputShutdown();

	/**
	 * Forces the socket to send all data
	 * @throws IOException When the flushing fails due to IO errors.
	 */
	void flush() throws IOException;

}
