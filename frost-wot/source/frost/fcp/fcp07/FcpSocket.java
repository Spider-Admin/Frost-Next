/*
  FcpSocket.java / Frost
  Copyright (C) 2007  Frost Project <jtcfrost.sourceforge.net>

  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License as
  published by the Free Software Foundation; either version 2 of
  the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
  General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
*/
package frost.fcp.fcp07;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

import frost.*;
import frost.fcp.*;

public class FcpSocket {

    private static final Logger logger = Logger.getLogger(FcpSocket.class.getName());

    // These are the timeouts set in Socket.setSoTimeout(), for non-infinite connections.
    private final static int TIMEOUT_READ_DEFAULT = 3 * 60 * 1000; // 3 minutes
    private final static int TIMEOUT_READ_NONPERSISTENT = 90 * 60 * 1000; // 90 minutes

    private final NodeAddress nodeAddress;
    private final boolean infiniteTimeout;

    private Socket fcpSock;
    private BufferedInputStream fcpIn;
    private PrintStream fcpOut;
    private BufferedOutputStream fcpRawOut;

    private final Set<String> checkedDirectories = Collections.synchronizedSet(new HashSet<String>());
    
    private static long fcpidentifierPart1 = Core.getCrypto().getSecureRandom().nextLong();
    private static long fcpidentifierPart2 = 0L;

    public enum DDAModes {
        WANT_DOWNLOAD,
        WANT_UPLOAD,
        WANT_DOWNLOAD_AND_UPLOAD
    };

    public static synchronized String getNextFcpId() {
        return new StringBuilder()
            .append("FcpSocket-")
            .append(fcpidentifierPart1)
            .append("-")
            .append(fcpidentifierPart2++)
            .toString();
    }

    /**
     * Create a connection to a host using FCP
     *
     * @exception UnknownHostException if the FCP host is unknown
     * @exception IOException if there is a problem with the connection to the FCP host.
     */
    public FcpSocket(final NodeAddress na) throws UnknownHostException, IOException {
        this(na, false);
    }

    /**
     * Create a connection to a host using FCP
     *
     * @exception UnknownHostException if the FCP host is unknown
     * @exception IOException if there is a problem with the connection to the FCP host.
     */
    public FcpSocket(final NodeAddress na, final boolean infiniteTimeout) throws UnknownHostException, IOException {
        nodeAddress = na;
        this.infiniteTimeout = infiniteTimeout;
        fcpSock = new Socket(nodeAddress.getHost(), nodeAddress.getPort());
        if( !this.infiniteTimeout ) {
            setDefaultTimeout();
        } else {
            fcpSock.setSoTimeout(0); // 0 = infinite read()-wait
        }
        fcpSock.setKeepAlive(true);

        fcpIn = new BufferedInputStream(fcpSock.getInputStream());
        fcpRawOut = new BufferedOutputStream(fcpSock.getOutputStream());
        fcpOut = new PrintStream(fcpSock.getOutputStream(), false, "UTF-8");

        doHandshake();
    }

    /**
     * If the socket is valid, returns the current timeout value.
     * 
     * @return  -1 if socket invalid, otherwise 0 (infinite) or 1+ (millis).
     */
    public int getCurrentTimeout() {
        if( fcpSock != null ) {
            try {
                return fcpSock.getSoTimeout();
            } catch( final Throwable t ) {}
        }
        return -1;
    }

    /**
     * Used internally, but can also be used externally to override the timeout
     * for certain situations. For example, you could use this to only give the
     * node a single minute to reply to the request for data.
     */
    public boolean setCustomTimeout(final int timeoutMillis) {
        if( !infiniteTimeout && fcpSock != null ) {
            try {
                fcpSock.setSoTimeout(timeoutMillis);
                return true;
            } catch( final Throwable t ) {}
        }
        return false;
    }

    /**
     * Sets the 90 minute timeout. Use this for FcpConnection.java (the non-persistent
     * transfer mode), where the transfers may have long periods of zero activity
     * in the block counts, and where we need to ensure the socket stays alive
     * for as long as possible.
     */
    public boolean setNonPersistentTimeout() {
        return setCustomTimeout(TIMEOUT_READ_NONPERSISTENT);
    }

    /**
     * Sets the default 3 minute timeout. This should be used while waiting for
     * node-replies to queries, but certain cases may need longer/shorter timeouts.
     *
     * NOTE: Some instances of FcpSocket use an infinite timeout instead, in
     * which case this function has no effect. In fact, MOST of Frost uses
     * infinite timeouts (its persistent queue-watching thread, its persistent
     * message uploader/downloader in single-threaded message transfer mode, etc),
     * but the most important things use timeouts: The AllData retrieval after
     * a persistent transfer is complete; and the whole transfer during
     * a non-persistent transfer, and the multi-threaded message retrieval mode.
     * Those are the areas where we need reasonable timeouts, to avoid hanging
     * forever waiting for a response.
     * However, you may want to setCustomTimeout() instead, to give the node
     * even less time to respond to the initial message, so that your downloads
     * don't stall for very long if the node is frozen.
     */
    public boolean setDefaultTimeout() {
        return setCustomTimeout(TIMEOUT_READ_DEFAULT);
    }

    /**
     * Factory method to get a socket without to catch an Exception.
     */
    public static FcpSocket create(final NodeAddress na) {
        try {
            final FcpSocket newSocket = new FcpSocket(na);
            return newSocket;
        } catch(final Throwable t) {
            logger.log(Level.SEVERE, "Exception catched", t);
            return null;
        }
    }

    public Set<String> getCheckedDirectories() {
        return checkedDirectories;
    }

    public NodeAddress getNodeAddress() {
        return nodeAddress;
    }

    public BufferedInputStream getFcpIn() {
        return fcpIn;
    }

    public PrintStream getFcpOut() {
        return fcpOut;
    }

    public BufferedOutputStream getFcpRawOut() {
        return fcpRawOut;
    }

    public Socket getFcpSock() {
        return fcpSock;
    }

    public void close() {
        if( fcpIn != null ) {
            try {
                fcpIn.close();
            } catch (final Throwable e) {
            }
            fcpIn = null;
        }
        if( fcpOut != null ) {
            try {
                fcpOut.close();
            } catch (final Throwable e) {
            }
            fcpOut = null;
        }
        if( fcpRawOut != null ) {
            try {
                fcpRawOut.close();
            } catch (final Throwable e) {
            }
            fcpRawOut = null;
        }
        if( fcpSock != null ) {
            try {
                fcpSock.close();
            } catch (final Throwable e) {
            }
            fcpSock = null;
        }
    }

    /**
     * Performs a handshake using this FcpConnection
     */
    public void doHandshake() throws IOException, ConnectException {
        
        fcpOut.println("ClientHello");
        fcpOut.println("Name=hello-" + getNextFcpId());
        fcpOut.println("ExpectedVersion=2.0");
        fcpOut.println("EndMessage");
        fcpOut.flush();

        // receive and process node messages
        boolean isSuccess = false;
        while(true) {
            final NodeMessage nodeMsg = NodeMessage.readMessage(fcpIn);
            if( nodeMsg == null ) {
                break;
            }

            if( nodeMsg.isMessageName("NodeHello") ) {
                isSuccess = true;
                break;
            }
            // any other message means error here
            break;
        }

        if( !isSuccess ) {
            throw new ConnectException();
        }
    }
}
