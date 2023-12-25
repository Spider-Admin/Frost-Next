/*
  FcpMultiRequestConnectionTools.java / Frost
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import frost.Core;
import frost.SettingsClass;
import frost.fcp.FcpNoAnswerException;
import frost.fileTransfer.ExtraInserts;
import frost.fileTransfer.FreenetPriority;
import frost.util.FileAccess;
import frost.util.Mixed;
import frost.ext.DefaultMIMETypes;

public class FcpMultiRequestConnectionFileTransferTools {

    private static final Logger logger = Logger.getLogger(FcpMultiRequestConnectionFileTransferTools.class.getName());

    private final FcpListenThreadConnection fcpPersistentConnection;

    public FcpMultiRequestConnectionFileTransferTools(final FcpListenThreadConnection fcpPersistentConnection) {
        this.fcpPersistentConnection = fcpPersistentConnection;
    }

    public FcpListenThreadConnection getFcpPersistentConnection() {
        return fcpPersistentConnection;
    }

    /**
     * No answer from node is expected.
     */
    public void changeRequestPriority(final String id, final FreenetPriority newPrio) {
        final List<String> msg = new LinkedList<String>();
        msg.add("ModifyPersistentRequest");
        msg.add("Global=true");
        msg.add("Identifier="+id);
        msg.add("PriorityClass=" + newPrio.getNumber());
        fcpPersistentConnection.sendMessage(msg);
    }

    /**
     * No answer from node is expected.
     */
    public void removeRequest(final String id) {
        final List<String> msg = new LinkedList<String>();
        msg.add("RemoveRequest");
        msg.add("Global=true");
        msg.add("Identifier="+id);
        fcpPersistentConnection.sendMessage(msg);
    }

    /**
     * Watches status updates for all persistent (global queue) transfers; both
     * ones that we initiated, and external transfers. The verbosity mask for watching
     * the queue must match the verbosity level of our persistent transfers, if we
     * want to be able to see those messages.
     *
     * No answer from node is expected.
     */
    public void watchGlobal(final boolean enabled) {
        final List<String> msg = new LinkedList<String>();

        msg.add("WatchGlobal");
        msg.add("Enabled="+enabled); // enables or disables global queue messages
        // NOTE: VERBOSITYMASK IS *CRITICAL*! It decides what bits our individual "Verbosity="
        // transfer masks are allowed to set, since their status updates go through the
        // global queue and are governed by what our WatchGlobal is set to.
        // If we set the global VerbosityMask to "1" for instance, then we can only get
        // SimpleProgress for our persistent transfers. So we set it to EXACTLY the same mask
        // that we use for all our transfers: 1023, which puts 1's in the first 10 bits,
        // so that we get all FCP status messages as of 2015, but won't be annoyed by
        // any future ones unless we explicitly want to support them later.
        msg.add("VerbosityMask=1023");
        fcpPersistentConnection.sendMessage(msg);
    }

    /**
     * Starts a persistent put.
     * First tests DDA and does NOT start a request if DDA is not possible!
     * 
     * @return true when DDA is possible and a request was started. 
     *         false when DDA is not possible and nothing was started 
     */
    public boolean startPersistentPutUsingDda(
            final String id,
            final File sourceFile,
            final String fileName,
            final boolean doMime,
            final boolean setTargetFileName,
            final boolean compress,
            final String freenetCompatibilityMode,
            final String cryptoKey, // if non-null value allows custom CHK crypto keys to be applied
            final FreenetPriority prio
    ) {
        final File uploadDir = sourceFile.getParentFile();
        final boolean isDda = TestDDAHelper.isDDAPossiblePersistent(FcpSocket.DDAModes.WANT_UPLOAD, uploadDir, fcpPersistentConnection);
        if (!isDda) {
            return false;
        }
        
        final List<String> msg = getDefaultPutMessage(id, sourceFile, fileName, doMime, setTargetFileName, compress, freenetCompatibilityMode, cryptoKey, prio);
        msg.add("UploadFrom=disk");
        msg.add("Filename=" + sourceFile.getAbsolutePath());

        fcpPersistentConnection.sendMessage(msg);
        return true;
    }

    /**
     * Starts a new persistent get.
     * Uses TestDDA to figure out if DDA can be used or not.
     * If DDA=false then the request is enqueued as DIRECT and must be retrieved manually
     * after the get completed successfully. Use startDirectPersistentGet to fetch the data.
     * 
     * @return true when the download was started using DDA, false if it is using DIRECT
     */
    public boolean startPersistentGet(final String key, final String id, final File targetFile, final FreenetPriority priority) {
        // start the persistent get. if DDA=false, then we have to fetch the file after the successful get from node
        final File downloadDir = targetFile.getParentFile();
        final boolean isDda = TestDDAHelper.isDDAPossiblePersistent(FcpSocket.DDAModes.WANT_DOWNLOAD, downloadDir, fcpPersistentConnection);

        final List<String> msg = new LinkedList<String>();
        msg.add("ClientGet");
        msg.add("IgnoreDS=false");
        msg.add("DSOnly=false");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED SINCE THE FREENET NODE ITSELF CONVERTS THE
        // STRING INTO A "FreenetURI" OBJECT WHICH STRIPS ALL ILLEGAL (UNENCODED) CHARS AND DECODES THE REST.
        msg.add("URI=" + Mixed.rawUrlEncode(key));
        msg.add("Identifier="+id );
        msg.add("MaxRetries=-1");
        // bitmask; puts 1's in the first 10 bits, so that we get all FCP status messages
        // as of 2015, but won't be annoyed by any future ones unless we explicitly want
        // to support them later
        msg.add("Verbosity=1023");
        msg.add("Persistence=forever");
        msg.add("Global=true");
        msg.add("PriorityClass=" + priority.getNumber());

        if (isDda) {
            msg.add("ReturnType=disk");
            msg.add("Filename=" + targetFile.getAbsolutePath());
            final File ddaTempFile = new File( targetFile.getAbsolutePath() + "-f");
            if( ddaTempFile.isFile() ) {
                // delete before download, else download fails, node will not overwrite anything!
                ddaTempFile.delete();
            }
            msg.add("TempFilename=" + ddaTempFile.getAbsolutePath());
         } else {
             msg.add("ReturnType=direct");
        }

        fcpPersistentConnection.sendMessage(msg);
        
        return isDda;
    }

    public void listPersistentRequests() {
        final List<String> msg = new LinkedList<String>();
        msg.add("ListPersistentRequests");
        fcpPersistentConnection.sendMessage(msg);
    }

    /**
     * Returns the common part of a put request, used for a put with type DIRECT and DISK together.
     * NOTE: The settings provided by this function cause the file uploads to use "forever"
     * persistence, so that the uploads continue even when if Frost is closed.
     *
     *   This function is only called for:
     *   1. File uploads via the uploads table.
     *   ----> EXCEPT is persistence is disabled, in which case it uses FcpConnecton.putKeyFromFile() instead.
     *   2. It is POSSIBLY used for the "my shared files" feature too (not checked; nobody uses that feature)
     *      UPDATE: Actually, turns out that shared files used the regular upload table, so probably falls under 1 above.
     *
     *   It is NOT used for:
     *   1. Key-generation (the "Pre-calculate CHKs for selected files" feature in the uploads table (Uses FcpConnection.putKeyFromFile())
     *   2. Uploading of files when you attach files to Frost messages (Uses FcpConnection.putKeyFromFile())
     *   3. Message uploading. (Uses startDirectPut() below)
     *   ----> However, it MIGHT be like regular file uploads above and may be using putKeyFromFile() instead if persistence is disabled.
     *         There's some indication for that (such as putKeyFromFile supporting TYPE_MESSAGE). I haven't checked, since it doesn't matter.
     *
     */
    private List<String> getDefaultPutMessage(
            final String id,
            final File sourceFile,
            final String fileName,
            final boolean doMime, // THIS PARAM IS NO LONGER USED, SO ITS VALUE DOESN'T MATTER
            final boolean setTargetFileName,
            final boolean compress,
            final String freenetCompatibilityMode,
            final String cryptoKey, // if non-null value allows custom CHK crypto keys to be applied
            final FreenetPriority prio
    ) {
        final LinkedList<String> lst = new LinkedList<String>();
        lst.add("ClientPut");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED, BUT THIS ISN'T A FULL KEY SO NOTHING IS NECESSARY HERE.
        lst.add("URI=CHK@");
        lst.add("Identifier=" + id);
        // bitmask; puts 1's in the first 10 bits, so that we get all FCP status messages
        // as of 2015, but won't be annoyed by any future ones unless we explicitly want
        // to support them later
        lst.add("Verbosity=1023");
        lst.add("MaxRetries=-1");

        // since this is a file upload, tell Freenet to insert important blocks multiple times
        // (using the user-setting amount), to improve the number of nodes they get stored on and
        // dramatically improve the data persistence so that your files live longer
        // NOTE: if the user has enabled quickheal mode, we'll use 0 extra inserts + EarlyEncode instead
        if( Core.frostSettings.getBoolValue( SettingsClass.UPLOAD_QUICKHEAL_MODE ) ) {
            // quickheal = 0 extra inserts (1x metadata, 1x main data) + early encoding (insert metadata first)
            // this means that people can quickly and easily heal files with dead metadata without doing a full insert
            lst.add("EarlyEncode=true");
            lst.add("ExtraInsertsSingleBlock=0");
            lst.add("ExtraInsertsSplitfileHeaderBlock=0");
        } else {
            // non-quickheal mode sessions should use the specified extrainserts setting from the user's options
            final int extraInserts = ExtraInserts.getExtraInserts( Core.frostSettings.getValue( SettingsClass.UPLOAD_EXTRA_INSERTS ) );
            // inserts small <32kb files (ones that fit in a single block) additional times
            lst.add("ExtraInsertsSingleBlock=" + Integer.toString(extraInserts));
            // inserts blocks above splitfiles (i.e. the metadata/headers for the CHK key itself) additional times
            lst.add("ExtraInsertsSplitfileHeaderBlock=" + Integer.toString(extraInserts));
        }

        // determine which compression, compatibility mode and crypto key settings to use...
        // first specify the desired compression state; the weird inversion is because "DontCompress=true" equals "compress=false"
        lst.add("DontCompress=" + (compress ? "false" : "true"));
        // use the chosen compatibility mode for this file, as requested
        lst.add("CompatibilityMode=" + freenetCompatibilityMode);
        // if a custom crypto key is provided, then apply it, otherwise we'll use a regular auto-key
        if( cryptoKey != null ) {
            lst.add("OverrideSplitfileCryptoKey=" + cryptoKey);
        }

        // determine whether we should set a filename for the uploaded file or not
        if( setTargetFileName ) {
            // NOTE:XXX: this filename comes from the user's ulItem, where they might have renamed the file compared to the on-disk name
            // that's why we don't simply use "getName()" on the actual file-object. we need the intended final name instead.
            lst.add("TargetFilename="+fileName);
        } else {
            lst.add("TargetFilename="); // default for shared files: no name, since we always want the same key for the same content!
        }

        // If we specify the Metadata.ContentType field, it tells Freenet exactly what content type
        // to use for the upload. If the field isn't provided, then Freenet guesses (that's a lot
        // more sane and consistent). The final ContentType value is encoded as part of the metadata
        // and affects the resulting CHK. Frost was really stupid for specifying one, since it doesn't
        // always guess the correct type, and that meant that keys inserted via Frost would sometimes
        // differ from keys inserted via Fuqid or the Web Interface (neither of which specify any
        // content type). So this block has been commented out, but preserved as a reminder of what
        // NOT to do EVER again! ;) -Kitty
        /*IDIOTCODE:
        if (doMime) {
            // NOTE: This is an outdated version of Freenet's "DefaultMIMETypes" library, and produces incorrect results compared to the latest Freenet.
            lst.add("Metadata.ContentType=" + DefaultMIMETypes.guessMIMEType(sourceFile.getAbsolutePath()));
        } else {
            lst.add("Metadata.ContentType=application/octet-stream"); // force this to prevent the node from filename guessing in dda mode!
        }*/

        // lastly, determine the upload priority and make sure it's a persistent upload on the global queue
        lst.add("PriorityClass=" + prio.getNumber());
        lst.add("Persistence=forever");
        lst.add("Global=true");

        return lst;
    }

    /**
     * Adds a file to the global queue DIRECT, means the file is transferred into the node completely.
     * Returns the first NodeMessage sent by the node after the transfer (PersistentPut or any error message).
     * After this method was called this connection is unuseable.
     */
    public NodeMessage startDirectPersistentPut(
            final String id,
            final File sourceFile,
            final String fileName,
            final boolean doMime,
            final boolean setTargetFileName,
            final boolean compress,
            final String freenetCompatibilityMode,
            final String cryptoKey, // if non-null value allows custom CHK crypto keys to be applied
            final FreenetPriority prio)
    throws IOException
    {
        final FcpSocket newSocket = FcpSocket.create(fcpPersistentConnection.getNodeAddress());
        if( newSocket == null ) {
            return null;
        }
        try {
            // NOTE:XXX: EXTREMELY IMPORTANT - this output stream must be closed *last*. we only need it while
            // writing the file to the socket. but if we use try-with-resources, it'll also autoclose the underlying
            // socket due to the close() call cascading. so we handle closing this stream separately.
            final BufferedOutputStream dataOutput = new BufferedOutputStream(newSocket.getFcpSock().getOutputStream());
            try {
                final List<String> msg = getDefaultPutMessage(id, sourceFile, fileName, doMime, setTargetFileName, compress, freenetCompatibilityMode, cryptoKey, prio);
                msg.add("UploadFrom=direct");
                msg.add("DataLength=" + Long.toString(sourceFile.length()));
                msg.add("Data");

                for( final String line : msg ) {
                    newSocket.getFcpOut().println(line);
                }
                newSocket.getFcpOut().flush();

                // write complete file to socket
                try (
                        // NOTE: Java 7+ try-with-resources (autocloseable)
                        final FileInputStream fileInputStream = new FileInputStream(sourceFile);
                        final BufferedInputStream fileInput = new BufferedInputStream(fileInputStream);
                    )
                {
                    while( true ) {
                        final int d = fileInput.read();
                        if( d < 0 ) {
                            break; // EOF
                        }
                        dataOutput.write(d);
                    }
                    dataOutput.flush();
                }

                // XXX: For some reason Freenet never responds with an OK-message?

                // wait for a message from node
                // good: PersistentPut
                // -> IdentifierCollision {Global=true, Identifier=myid1} EndMessage
                // NOTE:XXX: this is the part that would fail if we'd allowed the dataOutput buffer to cascade its close() call to the socket above
                //final NodeMessage nodeMsg = NodeMessage.readMessageDebug(newSocket.getFcpIn());

                //System.out.println("*PPUT** INFO - NodeMessage:");
                //System.out.println((nodeMsg==null)?"(null)":nodeMsg.toString());
            } finally {
                // NOTE:XXX: now it's finally safe to close the buffer (and underlying socket) we used for this transfer
                dataOutput.close();
            }
        } finally {
            // ensure that the temporary socket is closed despite returns/exceptions above, to avoid resource leaks
            newSocket.close();
        }

        //return nodeMsg;
        return new NodeMessage("Dummy");
    }

    /**
     * Retrieves a completed DIRECT Get's data from the node buffer, and writes it to disk.
     * After this method was called this connection is unuseable.
     * NOTE: This is the file-writer used by non-DDA gets via the persistent (global) queue.
     * If persistence is disabled in Frost, it uses FcpRequest.java:getFile() instead.
     */
    public NodeMessage startDirectPersistentGet(final String id, final File targetFile) throws IOException, FcpNoAnswerException {

        //
        // [FREENET BUG WORKAROUND!]
        //
        // When we request the AllData for a file, we normally get an immediate answer.
        // However, there is a serious Freenet bug where the node connection bugs out and never replies.
        // In that case, we'll sit here and wait for the readMessage's read() forever, until our own
        // read() times out.
        //
        // The problem:
        // - There's a serious bug in Freenet's "AllData" message; it can deadlock and infinitely stall
        //   the FCP socket's message queue, which means that the node's AllData reply will *never* be
        //   sent to us. I've tried with a 90 minute timeout with a non-loaded node and it *still* timed out.
        // - The AllData message is clearly queued, because we don't get any error replies or anything else;
        //   the node simply goes silent and never replies to us again, since its message-queue is frozen.
        // - The *only* way to fix it is to break the connection and try again with a new connection.
        // - The question is how we can know if the AllData stalling has happened; and the answer is that
        //   we can't... We can only enforce smart timeouts to try to work around it...
        // - All FCP messages in the Freenet node are handled with per-connection threads, which means
        //   that other FCP clients cannot stall the answers to our queries, so that's good news...
        // - The issue isn't with the socket connection itself. The FCP handshake actually succeeds
        //   and we receive the NodeHello every time. It's the "AllData" message that freezes Freenet's
        //   FCPConnectionOutputHandler's queue in the node. So we *cannot* look at the ability to answer
        //   other messages and try to deduce whether AllData will arrive.
        // - We know that the AllData binary file-data is sent in a streaming manner (using a "Bucket"
        //   file-stream object in the Freenet node), which means on average 100-300ms response time
        //   regardless of filesize. That's the time it takes the node to send the AllData message.
        // - It is slightly longer for compressed data. A 1.5 gigabyte, compressed file sent its AllData
        //   in 7.8 seconds, which means there's no way it decompressed everything before replying;
        //   it's clearly doing live-decompression on the fly, and this 7.8 second delay for a huge file
        //   gives us a good idea of how long we can expect for a maximum-size compressed file.
        //   It may take more than this and it may take less; I've observed pretty-small compressed files
        //   of ~150 MB start streaming their data within half a second, and the 7.8 seconds for a 1.5gb
        //   file is probably the upper limit of what I'd expect *any* compressed file to take, since it's
        //   doing streaming decompression and therefore isn't correlated to filesize.
        // - As soon as the "AllData" message arrives, we'll *always* get the first bytes of the actual
        //   Data just a few milliseconds later, which means that the message itself is sent as soon as
        //   the on-the-fly streaming is ready to begin, so we cannot check if the AllData itself arrives
        //   but no data; the socket-queue deadlock means that *no* AllData message arrives *whatsoever*.
        // - The best defense against the Freenet bug is to quickly break our connection and retry with
        //   a new one to hopefully get a deadlock-free connection. That usually fixes it with a single
        //   reconnect.
        // - Interestingly, if the first connection was given only 1 millisecond to reply (in other words,
        //   so low that it always gives up the first attempt), then the node still becomes "primed" to answer
        //   the request quickly next time. It seems like the first request causes the node to queue some
        //   type of data-decode job. So if we send a request for the AllData, then instantly break the
        //   connection and connect again (it takes about 1.5 seconds to connect+handshake), then the reply
        //   to the 2nd AllData request will usually come within 10ms after querying the node.
        //   Compare that to the time it normally takes to wait for the AllData during the *first* connection;
        //   about 100-300ms (never less than 60ms). It's obvious that the node is preparing the data for us
        //   even if we abort the first request. For that reason, it's a viable strategy to abort quickly
        //   on the first attempt (to avoid the deadlock), and to give the second attempt plenty of time
        //   to deal with heavily overloaded downloads.
        // - As for heavily overloaded downloads... Well, sometimes the node *will* reply but takes ~2 minutes
        //   due to heavy load (that's the longest time I've observed).
        //
        // The criteria:
        // - We need to quickly recover from the AllData deadlock, which is usually solved with a single reconnect.
        // - We know that the initial connection "primes" the next response to be faster, so aborting quickly
        //   is a viable strategy to get out of the infinite deadlock as fast as possible.
        // - But we also need to be able to handle the situation where a transfer *legitimately* stalls
        //   for 2 minutes due to heavy load. As well as the situation where large, compressed files take
        //   ~0.5-10 seconds to "warm up" before they start sending data (or much longer in case of heavy load).
        //
        // The solution:
        // - Set the socket read() timeout to 20 seconds.
        // - Send the GetRequestStatus with OnlyData=true (which means the node's first queued reply is the AllData).
        // - If timeout without any reply from Freenet, close the connection and open a new one
        //   with a 4 minute timeout. This closing + reopening fixes the "infinite deadlock" issue
        //   almost 100% of the time, since it's very unlikely that this 2nd attempt also deadlocks.
        //   The reason for jumping to 4 minutes on the 2nd try is that some legitimate stalling *can*
        //   happen during a very overloaded node. So we need to allow those transfers to have a chance
        //   to begin, without wasting more than the 20 seconds from our first timeout, so that's why
        //   we jump immediately to 4 minutes.
        // - If the 4 minute attempt times out too, try ONE LAST TIME with a new 20 second timeout,
        //   to catch any stragglers if we were ultra-unlucky and got the 2nd connection to deadlock
        //   infinitely too.
        // - At any point that we get an AllData reply as intended, set the socket read() timeout
        //   to 10 minutes, then start reading the actual data.
        //   Ten minutes is reasonable, because as long as the node is able to pump data in a streaming
        //   manner from the "Bucket", then it won't really pause for long even while heavily loaded,
        //   and 10 minutes is a *very* long time to give it even if it *does* stall a bit mid-datatransfer.
        //
        // This is the best we can do without actually patching Freenet itself. It efficiently takes
        // care of infinite deadlocks while still quickly allowing legitimate AllData transfers
        // to stall for a few minutes during heavy load.
        //
        // And this is a far cry above the old Frost behavior, which was to stall for 90 minutes
        // (that was the default timeout), which meant that the *entire* transfer-queue was frozen
        // until that stalled connection gave up. Hehe. Now it will take 20 seconds to fix itself
        // on average, and at most 4 minutes and 40 seconds if three ultra-unlucky infinite stallings
        // happen in a row (but I'd wager that that would never, ever happen, since AllData stalling
        // is pretty rare).
        //
        // PS:
        // If all three attempts time out (should NEVER happen even during HEAVY load), then our caller
        // (PersistenceManager.java) will take care of simply restarting the *whole* transfer and thereby
        // trying to retrieve the data again later, when the node is less overloaded.
        //
        //
        final int maxAttempts = 3;
        for( int antiDeadlockAttempt = 1; antiDeadlockAttempt <= maxAttempts; ++antiDeadlockAttempt ) {
            // attempt to connect to the node
            final FcpSocket newSocket = FcpSocket.create(fcpPersistentConnection.getNodeAddress());
            if( newSocket == null ) {
                return null; // no connection; give up instantly
            }

            try {
                // determine what read() timeout to use:
                //   second attempt = 4 minutes
                //   all other attempts (1st and 3rd) = 20 seconds
                if( antiDeadlockAttempt == 2 ) {
                    newSocket.setCustomTimeout((4 * 60) * 1000);
                } else {
                    newSocket.setCustomTimeout(20 * 1000);
                }

                // send the request for the AllData
                newSocket.getFcpOut().println("GetRequestStatus");
                newSocket.getFcpOut().println("Global=true");
                newSocket.getFcpOut().println("Identifier=" + id);
                newSocket.getFcpOut().println("OnlyData=true");
                newSocket.getFcpOut().println("EndMessage");
                newSocket.getFcpOut().flush();

                // wait for a response from the Freenet node.
                // this is the part that can deadlock and never reply, and that's
                // why the read() timeout is so important, so that we abort quickly.
                final NodeMessage nodeMsg = NodeMessage.readMessage(newSocket.getFcpIn());
                if( nodeMsg == null ) {
                    // we didn't get any answer (timed out), so do one of the following:
                    // - if we're not yet on the last retry, then retry (continue loop)...
                    // - if we're on the last retry, then throw an error for the caller to handle
                    if( antiDeadlockAttempt < maxAttempts ) { // 1 and 2 are < 3, but 3 (last attempt) isn't < 3
                        continue; // executes "finally"-cleanup of the current socket, and continues
                    } else {
                        // the last attempt has timed out too; let the caller deal with the error
                        logger.severe("All " + maxAttempts + " attempts to retrieve the data for '" + id + "' from the node have failed to respond in time, giving up...");
                        throw new FcpNoAnswerException();
                    }
                }

                System.out.println("*PGET** INFO - NodeMessage:");
                System.out.println(nodeMsg.toString());

                // make sure the message is well-formed and has an end marker (EndMessage or Data)
                final String endMarker = nodeMsg.getMessageEnd();
                if( endMarker == null ) {
                    // should never happen
                    logger.severe("*PGET** ENDMARKER is NULL! "+nodeMsg.toString());
                    return null;
                }

                // we now expect to receive the data, since the 1st response to GetRequestStatus
                // is the AllData message.
                // NOTE: if any unexpected runtime-exceptions happen here, it'll break out of this
                // function and notify the caller. it will *not* do any further retry-looping.
                if( nodeMsg.isMessageName("AllData") && endMarker.equals("Data") ) {
                    // data follows, but first get the expected datalength
                    final long dataLength = nodeMsg.getLongValue("DataLength");

                    // ensure that the parent directory exists
                    final File parentDir = targetFile.getParentFile();
                    if( parentDir != null ) { // path specified a parent directory
                        FileAccess.createDir(parentDir); // attempts to create if missing
                    }

                    // write the file to disk
                    long bytesWritten = 0;
                    try (
                            // NOTE: Java 7+ try-with-resources (autocloseable)
                            final FileOutputStream fileOutputStream = new FileOutputStream(targetFile);
                            final BufferedOutputStream fileOut = new BufferedOutputStream(fileOutputStream);
                        )
                    {
                        // boost the socket read() timeout to 10 minutes, so that we give the node
                        // plenty of time to keep feeding us with data even when it's overloaded.
                        // NOTE: the timeout is reset every time we receive at least 1 byte.
                        newSocket.setCustomTimeout((10 * 60) * 1000);

                        // read the data from the socket and write it to the file
                        final byte[] b = new byte[4096];
                        long bytesLeft = dataLength; // avoids reading beyond end of file (other FCP messages etc after file)
                        while( bytesLeft > 0 ) {
                            final int count = newSocket.getFcpIn().read(b, 0, ((bytesLeft > b.length)?b.length:(int)bytesLeft));
                            if( count < 0 ) {
                                break; // end of socket-stream reached... should never happen
                            } else {
                                bytesLeft -= count;
                            }
                            fileOut.write(b, 0, count);
                            bytesWritten += count;
                        }
                    } finally {
                        // switch back to the default read()-timeout between requests
                        newSocket.setDefaultTimeout();
                    }
                    System.out.println("*GET** Wrote "+bytesWritten+" of "+dataLength+" bytes to file.");

                    if( bytesWritten == dataLength ) {
                        return nodeMsg; // success!
                    } else {
                        try {
                            targetFile.delete(); // delete the partially written file
                        } catch( final Throwable t ) {}
                        logger.severe("Did not receive the expected amount of data for file '" + id + "'... (Got " + bytesWritten + " of " + dataLength + " bytes).");
                        return null; // didn't get the whole file...
                    }
                } else {
                    logger.severe("Invalid node answer, expected AllData: "+nodeMsg);
                    return null; // got something other than an "AllData" reply
                }
            } finally {
                // ensure that the temporary socket is closed despite returns/continues/exceptions above, to avoid resource leaks
                newSocket.close();
            }
        }

        // this is actually unreachable; if all attempts time out, we throw an exception higher up,
        // and in all other cases (where we *got* a response), we deal with that above.
        return null;
    }

///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Methods to get/put messages ////////////////////////////////////////////////////////////////////////////////////
///////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	// Used for downloading messages. Nothing else.
    public void startDirectGet(final String id, final String key, final FreenetPriority prio, final int maxSize, int maxRetries) {
        final List<String> msg = new LinkedList<String>();
        msg.add("ClientGet");
        msg.add("IgnoreDS=false");
        msg.add("DSOnly=false");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED SINCE THE FREENET NODE ITSELF CONVERTS THE
        // STRING INTO A "FreenetURI" OBJECT WHICH STRIPS ALL ILLEGAL (UNENCODED) CHARS AND DECODES THE REST.
        msg.add("URI=" + Mixed.rawUrlEncode(key));
        msg.add("Identifier=" + id );
        if( maxRetries <= 0 ) {
            maxRetries = 1;
        }
        msg.add("MaxRetries=" + Integer.toString(maxRetries));
        // 0 = Only report when download is complete; no progress messages
        msg.add("Verbosity=0");
        msg.add("ReturnType=direct");
        msg.add("Persistence=connection");
        msg.add("PriorityClass="+ prio.getNumber());
        if( maxSize > 0 ) {
            msg.add("MaxSize="+maxSize);
        }
        fcpPersistentConnection.sendMessage(msg);
    }

    // NOTE: This function is ONLY used for ONE thing: The uploading of the "message" portion of all
    // your outgoing messages. It is NOT used for any file attachments in the message, or any regular
    // file uploads, etc. Attached files use FcpConnection.putKeyFromFile(), and all attachments are
    // uploaded before the message itself is uploaded. Actual files-table uploads use the settings
    // from getDefaultPutMessage() above.
    public void startDirectPut(final String id, final String key, final FreenetPriority prio, final File sourceFile) {
        final List<String> msg = new LinkedList<String>();
        msg.add("ClientPut");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED SINCE THE FREENET NODE ITSELF CONVERTS THE
        // STRING INTO A "FreenetURI" OBJECT WHICH STRIPS ALL ILLEGAL (UNENCODED) CHARS AND DECODES THE REST.
        msg.add("URI=" + Mixed.rawUrlEncode(key));
        msg.add("Identifier=" + id );
        // 0 = Report completion and SimpleProgress messages (because it's impossible to turn them off for ClientPuts)
        msg.add("Verbosity=0");
        msg.add("MaxRetries=1");
        // Early encoding generates the key first for inserted messages, that way Frost can detect
        // key collisions 6 times faster than without early encoding
        msg.add("EarlyEncode=true");
        msg.add("DontCompress=false"); // tell the node that we WANT the text of messages to be compressed :)
        msg.add("PriorityClass=" + prio.getNumber());
        msg.add("Persistence=connection");
        msg.add("UploadFrom=direct");

        fcpPersistentConnection.sendMessageAndData(msg, sourceFile);
    }
}
