/*
  FcpConnection.java / Frost
  Copyright (C) 2003  Jan-Thomas Czornack <jantho@users.sourceforge.net>

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
import frost.ext.*;
import frost.fcp.*;
import frost.fileTransfer.ExtraInserts;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.download.*;
import frost.fileTransfer.upload.*;
import frost.util.FileAccess;
import frost.util.Logging;
import frost.util.Mixed;


/**
 * This class is a wrapper to simplify access to the FCP library.
 */
public class FcpConnection {

	private static final Logger logger = Logger.getLogger(FcpConnection.class.getName());

    private final FcpSocket fcpSocket;

    /**
     * Create a connection to a host using FCP
     *
     * @param host the host to which we connect
     * @param port the FCP port on the host
     * @exception UnknownHostException if the FCP host is unknown
     * @exception IOException if there is a problem with the connection to the FCP host.
     */
    public FcpConnection(final NodeAddress na) throws UnknownHostException, IOException {
        fcpSocket = new FcpSocket(na);
        // EXTREMELY IMPORTANT: use the 90 minute timeout on all socket read() calls,
        // to ensure that we'll stay alive even during very long periods of transfer-inactivity.
        fcpSocket.setNonPersistentTimeout();
    }

    public void close() {
        fcpSocket.close();
    }

    protected void sendMessage(final List<String> msg) {
        final boolean doLogging = Logging.inst().doLogFcp2Messages();
        if (doLogging) {
            System.out.println("### SEND >>>>>>>>> (FcpConnection)");
        }
        for (final String msgLine : msg) {
            if (doLogging) {
                System.out.println(msgLine);
            }
            fcpSocket.getFcpOut().println(msgLine);
        }
        fcpSocket.getFcpOut().flush();

        if (doLogging) {
            System.out.println("### SEND <<<<<<<<< (FcpConnection)");
        }
    }

    /**
     * Retrieves the specified key and saves it to the file specified.
     *
     * @param publicKey  the key to be retrieved
     * @param filename  the filename to which the data should be saved
     * @return the results filled with metadata
     */
    public FcpResultGet getKeyToFile(
            final int type,
            String keyString,
            final File targetFile,
            final int maxSize,
            int maxRetries,
            final FrostDownloadItem dlItem)
    throws IOException, FcpToolsException, InterruptedIOException {

        File ddaTempFile = null;

        keyString = stripSlashes(keyString);

        final FreenetKey key = new FreenetKey(keyString);
		logger.fine("KeyString = " + keyString + "\n" +
					"Key =       " + key + "\n" +
					"KeyType =   " + key.getKeyType());

        final boolean useDDA;
        if( type == FcpHandler.TYPE_MESSAGE ) {
            useDDA = false;
        } else {
            final File downloadDir = targetFile.getParentFile();
            useDDA = TestDDAHelper.isDDAPossibleDirect(FcpSocket.DDAModes.WANT_DOWNLOAD, downloadDir, fcpSocket);
        }

        if (useDDA) {
            // delete before download, else download fails, node will not overwrite anything!
            targetFile.delete();
        }

        final List<String> msg = new ArrayList<String>(20);
        msg.add("ClientGet");
        msg.add("IgnoreDS=false");
        msg.add("DSOnly=false");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED SINCE THE FREENET NODE ITSELF CONVERTS THE
        // STRING INTO A "FreenetURI" OBJECT WHICH STRIPS ALL ILLEGAL (UNENCODED) CHARS AND DECODES THE REST.
        msg.add("URI=" + Mixed.rawUrlEncode(key.toString())); // toString() is needed since this is a FreenetKey object
        msg.add("Identifier=get-" + FcpSocket.getNextFcpId() );
        if( maxRetries <= 0 ) {
            maxRetries = 1;
        }
        msg.add("MaxRetries=" + Integer.toString(maxRetries));
        // bitmask; puts 1's in the first 10 bits, so that we get all FCP status messages
        // as of 2015, but won't be annoyed by any future ones unless we explicitly want
        // to support them later
        msg.add("Verbosity=1023");

        // means that Freenet will stop handling this request if the user closes Frost (breaks socket connection)
        msg.add("Persistence=connection");

        if (useDDA) {
            msg.add("ReturnType=disk");
            msg.add("Filename=" + targetFile.getAbsolutePath());
            ddaTempFile = new File( targetFile.getAbsolutePath() + "-w");
            if( ddaTempFile.isFile() ) {
                // delete before download, else download fails, node will not overwrite anything!
                ddaTempFile.delete();
            }
            msg.add("TempFilename=" + ddaTempFile.getAbsolutePath());
         } else {
             msg.add("ReturnType=direct");
        }

        final FreenetPriority prio;
        if( type == FcpHandler.TYPE_FILE ) {
        	if( dlItem != null) {
        		prio = dlItem.getPriority();
        	} else {
        		prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD));
        	}
        
        } else if( type == FcpHandler.TYPE_MESSAGE ) {
            prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_MESSAGE_DOWNLOAD));

        } else {
        	if( dlItem != null) {
        		prio = dlItem.getPriority();
        	} else {
        		prio = FreenetPriority.MEDIUM; // fallback
        	}
        }
        msg.add("PriorityClass=" + prio.getNumber());

        if( maxSize > 0 ) {
            msg.add("MaxSize=" + Integer.toString(maxSize));
        }

        msg.add("EndMessage");
        sendMessage(msg);

        // receive and process node messages
        boolean isSuccess = false;
        int returnCode = -1;
        String codeDescription = null;
        boolean isFatal = false;
        String redirectURI = null;
        String hashMD5 = null; // keeps track of last-seen hash
        while(true) {
            final NodeMessage nodeMsg = NodeMessage.readMessage(fcpSocket.getFcpIn());
            if( nodeMsg == null ) {
                // socket closed
                break;
            }

            if(Logging.inst().doLogFcp2Messages()) {
                System.out.println("*GET** INFO - NodeMessage:");
                System.out.println(nodeMsg.toString());
            }

            final String endMarker = nodeMsg.getMessageEnd();
            if( endMarker == null ) {
                // should never happen
                System.out.println("*GET** ENDMARKER is NULL!");
                break;
            }

            // update the non-persistent transfer so it doesn't forever say "Trying" while downloading
            if( dlItem != null && dlItem.getState() == FrostDownloadItem.STATE_TRYING ) {
                dlItem.setState(FrostDownloadItem.STATE_PROGRESS);
            }

            if( !useDDA && nodeMsg.isMessageName("AllData") && endMarker.equals("Data") ) {
                // data follow, first get datalength
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
                ) {
                    final byte[] b = new byte[4096];
                    long bytesLeft = dataLength;
                    int count;
                    while( bytesLeft > 0 ) {
                        count = fcpSocket.getFcpIn().read(b, 0, ((bytesLeft > b.length)?b.length:(int)bytesLeft));
                        if( count < 0 ) {
                            break;
                        } else {
                            bytesLeft -= count;
                        }
                        fileOut.write(b, 0, count);
                        bytesWritten += count;
                    }
                }
                if(Logging.inst().doLogFcp2Messages()) {
                    System.out.println("*GET** Wrote "+bytesWritten+" of "+dataLength+" bytes to file.");
                }
                if( bytesWritten == dataLength ) {
                    isSuccess = true;
                    if( dlItem != null && dlItem.getRequiredBlocks() > 0 ) {
                        dlItem.setFinalized(true);
                        dlItem.setDoneBlocks(dlItem.getRequiredBlocks());
                        dlItem.fireValueChanged();
                    }
                } else {
                    try {
                        targetFile.delete(); // delete the partially written file
                    } catch( final Throwable t ) {}
                }
                break;
            }

            if( useDDA && nodeMsg.isMessageName("DataFound") ) {
                final long dataLength = nodeMsg.getLongValue("DataLength");
                isSuccess = true;
                System.out.println("*GET**: DataFound, len="+dataLength);
                if( dlItem != null && dlItem.getRequiredBlocks() > 0 ) {
                    dlItem.setFinalized(true);
                    dlItem.setDoneBlocks(dlItem.getRequiredBlocks());
                    dlItem.fireValueChanged();
                }
                break;
            }

            if( nodeMsg.isMessageName("ProtocolError") ) {
                returnCode = nodeMsg.getIntValue("Code");
                isFatal = nodeMsg.getBoolValue("Fatal");
                codeDescription = nodeMsg.getStringValue("CodeDescription");
                break;
            }
            if( nodeMsg.isMessageName("IdentifierCollision") ) {
                break;
            }
            if( nodeMsg.isMessageName("UnknownNodeIdentifier") ) {
                break;
            }
            if( nodeMsg.isMessageName("UnknownPeerNoteType") ) {
                break;
            }
            if( nodeMsg.isMessageName("GetFailed") ) {
                // get error code
                returnCode = nodeMsg.getIntValue("Code");
                codeDescription = nodeMsg.getStringValue("CodeDescription");
                isFatal = nodeMsg.getBoolValue("Fatal");
                // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
                redirectURI = Mixed.rawUrlDecode(nodeMsg.getStringValue("RedirectURI"));
                break;
            }
            if( dlItem != null && nodeMsg.isMessageName("SimpleProgress") ) {
                // eval progress and set to dlItem
                int doneBlocks;
                int requiredBlocks;
                int totalBlocks;
                boolean isFinalized;

                doneBlocks = nodeMsg.getIntValue("Succeeded");
                requiredBlocks = nodeMsg.getIntValue("Required");
                totalBlocks = nodeMsg.getIntValue("Total");
                isFinalized = nodeMsg.getBoolValue("FinalizedTotal");

                if( totalBlocks > 0 && requiredBlocks > 0 ) {
                    dlItem.setDoneBlocks(doneBlocks);
                    dlItem.setRequiredBlocks(requiredBlocks);
                    dlItem.setTotalBlocks(totalBlocks);
                    dlItem.setFinalized(isFinalized);
                    dlItem.fireValueChanged();
                }
                continue;
            }

            // if this is a download-table item and we receive a "hashes" message,
            // and the item isn't already finished or failed, then we'll check the hash...
            // NOTE: because of the fact that we only check hashes when a new hash
            // is received (at the start of a transfer), it means that the user
            // can disable hash checking, start an otherwise-blocked download,
            // and then re-enable hash checking without interrupting the now-started
            // download.
            // ALSO NOTE: this is based on PersistenceManager.java's "isHashChecked"
            // logic and behaves exactly the same way, but is done for non-persistent
            // downloads here, which required different handling.
            if( dlItem != null
                    && nodeMsg.isMessageName("ExpectedHashes")
                    && !isSuccess // paranoia: success means "done", but this check can't actually happen since those "break;" when setting this flag
                    && dlItem.getState() != FrostDownloadItem.STATE_DONE // more paranoia: the non-persistent downloader never changes state during download, so these never happen
                    && dlItem.getState() != FrostDownloadItem.STATE_FAILED )
            {
                // we use the MD5 to avoid downloading what the user already has.
                // note that the MD5 is only included on Freenet for files >=1 MiB,
                // and that were inserted with COMPAT_1255 or later.
                boolean foundNewMD5 = false;
                String newMD5 = nodeMsg.getStringValue("Hashes.MD5");
                if( newMD5 != null ) {
                    newMD5 = newMD5.trim();
                    if( newMD5.length() == 32 ) { // valid md5 must be 32 hex chars
                        // a single request can get its "ExpectedHashes" message multiple times, so only
                        // trigger a new check if the MD5 differs from the last-seen for this request.
                        if( hashMD5 == null || !hashMD5.equals(newMD5) ) {
                            hashMD5 = newMD5;
                            foundNewMD5 = true;
                        }
                    }
                }
                // only process this particular MD5 if the hash blocklist is enabled right now
                if( foundNewMD5
                        && Core.frostSettings.getBoolValue(SettingsClass.HASHBLOCKLIST_ENABLED) )
                {
                    final String localFilePath = HashBlocklistManager.getInstance().getMD5FilePath(hashMD5);
                    if( localFilePath != null ) {
                        // to cancel this non-persistent transfer, we simply
                        // have to set up the failure status variables, and then
                        // break out of the message processing loop so that the
                        // socket is "close()"'d, which aborts the transfer.
                        isSuccess = false; // takes care of marking the item "Failed" in the GUI
                        returnCode = -1; // just a random, non-Freenet error code
                        codeDescription = "[MD5] Exists on disk: " + localFilePath; // failure-reason message
                        isFatal = true; // treat this as a "non-recoverable" error

                        // however, we must manually set the item to "disabled" so that the user
                        // can't start the transfer again without explicitly restarting it.
                        dlItem.setEnabled(false);
                        break; // this magic jumps to "close()"
                    }
                }
            }
        }

        close();

        FcpResultGet result = null;

        if( !isSuccess ) {
            // failure
            if( targetFile.isFile() ) {
                targetFile.delete();
            }
            result = new FcpResultGet(false, returnCode, codeDescription, isFatal, redirectURI);
        } else {
            // success
            result = new FcpResultGet(true);
        }

        // in either case, remove dda temp file
        if( ddaTempFile != null && ddaTempFile.isFile() ) {
            ddaTempFile.delete();
        }

        return result;
    }

     /**
     * Inserts the specified key with the data from the file specified.
     * Uses a non-persistent insert which aborts the insert if Frost is closed.
     * NOTE: This function is capable of both inserting and key-only calculation.
     *
     *   However, due to peculiarities in Frost, this function is only called for:
     *   1. Key-generation (the "Pre-calculate CHKs for selected files" feature in the uploads table)
     *   2. Uploading of files when you attach files to Frost messages (but it's not used for the message itself)
     *   3. It is POSSIBLY used for the "my shared files" feature too (not checked; nobody uses that feature)
     *      UPDATE: Actually, turns out that shared files used the regular upload table, so probably falls under 1 below.
     *
     *   It is NOT used for:
     *   1. File uploads via the uploads table. (Uses the settings from FcpMultiRequestConnectionFileTransferTools.getDefaultPutMessage())
     *   ----> EXCEPT if the user has disabled persistence. In that case it DOES use this function for regular file uploads too!
     *   2. Message uploading. (Uses FcpMultiRequestConnectionFileTransferTools.startDirectPut())
     *   ----> However, it MIGHT be like above and may be using this one instead if persistence is disabled. I haven't checked, since it doesn't matter.
     *         There is some indication for it, such as supporting TYPE_MESSAGE below.
     *
     * @param type   either FcpHandler.TYPE_MESSAGE or FcpHandler.TYPE_FILE
     * @param keyString   the kind of key we want, such as "CHK@"
     * @param sourceFile   a File object representing the full path to the file we want
     * to insert/calculate the key for
     * @param getChkOnly   if true, it tells Freenet to *just* calculate the key (without actually
     * inserting any data), if false it tells Freenet to actually insert the file. if this is false
     * (=an actual insert) *and* ulItem!=null, we'll also update the ulItem's transfer status
     * continuously as the task progresses. (NOTE: as noted above; that doesn't happen though,
     * since this function is only used for key pre-generation in the uploads table)
     * @param doMime   if true, it guesses the mime-type, otherwise it uses "application/octet-stream"
     * // THIS DOMIME PARAM IS NO LONGER USED, SO ITS VALUE DOESN'T MATTER (instead, we let the node
     * guess the mime type); the param was simply kept for legacy code purposes, and to make it easy to
     * re-add the feature later if desired
     * @param ulItem   can be null; if provided, it indicates that the file is in the uploadTable
     * and that extended information is available for us (such as compatibility mode, compression
     * and crypto key settings). if null, we always insert with "COMPAT_CURRENT", compression DISABLED and auto-key.
     * @return the results filled with metadata and the CHK used to insert the data
     * @throws IOException
     */
    public FcpResultPut putKeyFromFile(final int type, String keyString, final File sourceFile, final boolean getChkOnly, final boolean doMime, final FrostUploadItem ulItem)
        throws IOException {

        keyString = stripSlashes(keyString);

        boolean useDDA;
        if( type == FcpHandler.TYPE_MESSAGE ) {
            useDDA = false;
        } else {
            final File uploadDir = sourceFile.getParentFile();
            useDDA = TestDDAHelper.isDDAPossibleDirect(FcpSocket.DDAModes.WANT_UPLOAD, uploadDir, fcpSocket);
        }

        // begin constructing the message
        final List<String> msg = new ArrayList<String>(20);
        msg.add("ClientPut");
        // NOTE:IMPORTANT: OUTGOING URIS MUST ALWAYS BE URLENCODED SINCE THE FREENET NODE ITSELF CONVERTS THE
        // STRING INTO A "FreenetURI" OBJECT WHICH STRIPS ALL ILLEGAL (UNENCODED) CHARS AND DECODES THE REST.
        // NOTE:XXX: IN THIS CASE WE ONLY PROVIDE A SMALL "CHK@" OR SIMILAR STRING WHICH DOESN'T NEED ENCODING
        // AND THEREFORE REMAINS IDENTICAL AFTER ENCODING, BUT IN THE FUTURE WE MAY SUPPORT INSERTING TO A
        // SPECIFIC SSK/KSK, SO THE CORRECT ENCODING STEP IS PRE-EMPTIVELY ADDED JUST SO IT'S NOT FORGOTTEN LATER!
        msg.add("URI=" + Mixed.rawUrlEncode(keyString));
        msg.add("Identifier=put-" + FcpSocket.getNextFcpId() );
        // bitmask; puts 1's in the first 10 bits, so that we get all FCP status messages
        // as of 2015, but won't be annoyed by any future ones unless we explicitly want
        // to support them later
        msg.add("Verbosity=1023");
        msg.add("MaxRetries=3");

        // if this is a file upload (not a regular message), then tell Freenet to insert important
        // blocks multiple times (using the user-setting amount), to improve the number of nodes
        // they get stored on and dramatically improve the data persistence so that your files live longer
        // NOTE: if the user has enabled quickheal mode, we'll use 0 extra inserts + EarlyEncode instead
        if( type == FcpHandler.TYPE_FILE ) {
            // if this is an upload-table item (non-null "ulItem" value, as opposed to a message attachment),
            // then check if quickheal mode is enabled and apply it. of course, the only time it'll be
            // an upload-table item is when we're doing key pre-calculation (no insert), but let's be consistent!
            if( ulItem != null && Core.frostSettings.getBoolValue( SettingsClass.UPLOAD_QUICKHEAL_MODE ) ) {
                // quickheal = 0 extra inserts (1x metadata, 1x main data) + early encoding (insert metadata first)
                // this means that people can quickly and easily heal files with dead metadata without doing a full insert
                msg.add("EarlyEncode=true");
                msg.add("ExtraInsertsSingleBlock=0");
                msg.add("ExtraInsertsSplitfileHeaderBlock=0");
            } else {
                // all non-uploadtable items (such as file attachments) as well as non-quickheal
                // mode sessions should use the specified extrainserts setting from the user's options
                final int extraInserts = ExtraInserts.getExtraInserts( Core.frostSettings.getValue( SettingsClass.UPLOAD_EXTRA_INSERTS ) );
                // inserts small <32kb files (ones that fit in a single block) additional times
                msg.add("ExtraInsertsSingleBlock=" + Integer.toString(extraInserts));
                // inserts blocks above splitfiles (i.e. the metadata/headers for the CHK key itself) additional times
                msg.add("ExtraInsertsSplitfileHeaderBlock=" + Integer.toString(extraInserts));
            }
        }

        // default compression to DISABLED since it's *harmful* in almost 100% of cases,
        // doubling download and upload disk space and memory usage for 0% savings.
        boolean compress = false;

        // if this is an upload-table item, then determine which compression, compatibility and crypto key settings to use
        if( ulItem != null ) {
            if( ulItem.getCompress() ) { // if compression is enabled
                compress = true;
            }
            // use the chosen compatibility mode for this file, as requested
            msg.add("CompatibilityMode=" + ulItem.getFreenetCompatibilityMode());
            final String cryptoKey = ulItem.getCryptoKey();
            // if a custom crypto key is provided, then apply it, otherwise we'll use a regular auto-key
            if( cryptoKey != null ) {
                msg.add("OverrideSplitfileCryptoKey=" + cryptoKey);
            }
        }

        // specify the desired compression state; the weird inversion is because "DontCompress=true" equals "compress=false"
        msg.add("DontCompress=" + (compress ? "false" : "true"));

        // determine the target filename
        if( keyString.equals("CHK@") ) {
            if( ulItem != null ) {
                if( ulItem.getSharedFileItem() != null ) {
                    // shared file, thus we want no filename
                    msg.add("TargetFilename=");
                } else {
                    // manual upload, set filename to the one provided by the user in the upload-table
                    // NOTE:XXX: this is identical to the behavior of source/frost/fileTransfer/PersistenceManager.java's use of startDirectPersistentPut()
                    msg.add("TargetFilename=" + ulItem.getFileName());
                }
            } else {
                // this is NOT an upload via the upload-table, so it's most likely a file-attachment for
                // a message. just use the basename from disk, but replace illegal chars with underscore.
                msg.add("TargetFilename=" + Mixed.makeFilename(sourceFile.getName()));
            }
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
        if( type == FcpHandler.TYPE_FILE ) {
            if (doMime) {
                // NOTE: This is an outdated version of Freenet's "DefaultMIMETypes" library, and produces incorrect results compared to the latest Freenet.
                msg.add("Metadata.ContentType=" + DefaultMIMETypes.guessMIMEType(sourceFile.getAbsolutePath()));
            } else {
                msg.add("Metadata.ContentType=application/octet-stream"); // force this to prevent the node from filename guessing in dda mode!
            }
        }*/

        if( getChkOnly ) {
            msg.add("GetCHKOnly=true"); // tells Freenet to only encode the file (without uploading it)
            msg.add("PriorityClass=3");
        } else {
            final FreenetPriority prio;
            if( type == FcpHandler.TYPE_FILE ) {
                if( ulItem != null) {
                    prio = ulItem.getPriority(); // use the chosen priority for this file, as requested
                } else {
                    prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_UPLOAD));
                }
            } else if( type == FcpHandler.TYPE_MESSAGE ) {
                prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_MESSAGE_UPLOAD));
                // generates key first for inserted messages, that way Frost can detect key collisions 6 times faster than without early encoding
                msg.add("EarlyEncode=true");
            } else { // some other type
                if( ulItem != null) {
                    prio = ulItem.getPriority(); // use the chosen priority for this file, as requested
                } else {
                    prio = FreenetPriority.MEDIUM;
                }
            }
            msg.add("PriorityClass=" + prio.getNumber());
        }

        // means that Freenet will stop handling this request if the user closes Frost (breaks socket connection)
        msg.add("Persistence=connection");

        // NOTE:XXX: EXTREMELY IMPORTANT - this output stream must be closed *last*. we only need it while
        // writing the file to the socket. but if we use try-with-resources, it'll also autoclose the underlying
        // socket due to the close() call cascading. so we handle closing this stream separately.
        BufferedOutputStream dataOutput = null;

        if (useDDA) {
            // direct file access
            msg.add("UploadFrom=disk");
            msg.add("Filename=" + sourceFile.getAbsolutePath());
            msg.add("EndMessage");
            sendMessage(msg);
        } else {
            // send data
            msg.add("UploadFrom=direct");
            msg.add("DataLength=" + Long.toString(sourceFile.length()));
            msg.add("Data");
            sendMessage(msg);

            // NOTE:XXX: we do not try-with-resources (autoclose) this dataOutput buffer since that would close the socket prematurely
            dataOutput = new BufferedOutputStream(fcpSocket.getFcpSock().getOutputStream());

            // write complete file to socket
            try (
                // NOTE: Java 7+ try-with-resources (autocloseable)
                final FileInputStream fileInputStream = new FileInputStream(sourceFile);
                final BufferedInputStream fileInput = new BufferedInputStream(fileInputStream);
            ) {
                while( true ) {
                    final int d = fileInput.read();
                    if( d < 0 ) {
                        break; // EOF
                    }
                    dataOutput.write(d);
                }
                dataOutput.flush();
            }
        }

        // receive and process node messages continuously until the upload is complete
        boolean isSuccess = false;
        int returnCode = -1;
        String codeDescription = null;
        boolean isFatal = false;
        String chkKey = null;
        while(true) {
            // NOTE:XXX: this is the part that would fail if we'd allowed the dataOutput buffer to cascade its close() call to the socket above
            final NodeMessage nodeMsg = NodeMessage.readMessage(fcpSocket.getFcpIn());
            if( nodeMsg == null ) {
                break;
            }

            if(Logging.inst().doLogFcp2Messages()) {
                System.out.println("*PUT** INFO - NodeMessage:");
                System.out.println(nodeMsg.toString());
            }

            if( getChkOnly == true && nodeMsg.isMessageName("URIGenerated") ) {
                // this is a key-only calculation, and the key has been generated
                isSuccess = true;
                // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
                chkKey = Mixed.rawUrlDecode(nodeMsg.getStringValue("URI"));
                break;
            }
            if( getChkOnly == false && nodeMsg.isMessageName("PutSuccessful") ) {
                // this is an actual insert, and the insert is complete
                isSuccess = true;
                // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
                chkKey = Mixed.rawUrlDecode(nodeMsg.getStringValue("URI"));
                if( ulItem != null && ulItem.getTotalBlocks() > 0 ) {
                    ulItem.setDoneBlocks(ulItem.getTotalBlocks()); // set the progress to 100%
                }
                break;
            }
            if( nodeMsg.isMessageName("PutFailed") ) {
                // get error code
                returnCode = nodeMsg.getIntValue("Code");
                isFatal = nodeMsg.getBoolValue("Fatal");
                codeDescription = nodeMsg.getStringValue("CodeDescription");
                break;
            }

            if( nodeMsg.isMessageName("ProtocolError") ) {
                returnCode = nodeMsg.getIntValue("Code");
                isFatal = nodeMsg.getBoolValue("Fatal");
                codeDescription = nodeMsg.getStringValue("CodeDescription");
                break;
            }
            if( nodeMsg.isMessageName("IdentifierCollision") ) {
                break;
            }
            if( nodeMsg.isMessageName("UnknownNodeIdentifier") ) {
                break;
            }
            if( nodeMsg.isMessageName("UnknownPeerNoteType") ) {
                break;
            }
            // if this is an actual insert, update the upload table item (if we have any) to the latest progress;
            // note that we technically don't have to check "getChkOnly", since we'll never receive
            // "SimpleProgress" messages from the nodes for key-only calculations, but we do that just
            // to avoid problems in case Freenet ever starts sending key calculation progress in the future
            if( getChkOnly == false && ulItem != null && nodeMsg.isMessageName("SimpleProgress") ) {
                // evaluate the progress and update the ulItem
                int doneBlocks;
                int totalBlocks;
                boolean isFinalized;

                doneBlocks = nodeMsg.getIntValue("Succeeded");
                totalBlocks = nodeMsg.getIntValue("Total");
                isFinalized = nodeMsg.getBoolValue("FinalizedTotal");

                if( totalBlocks > 0 ) {
                    ulItem.setDoneBlocks(doneBlocks);
                    ulItem.setTotalBlocks(totalBlocks);
                    ulItem.setFinalized(isFinalized);
                    ulItem.fireValueChanged();
                }
                continue;
            }
        }

        // NOTE:XXX: now it's finally safe to close the buffer (and underlying socket) we used for this transfer
        if( dataOutput != null ) {
            dataOutput.close();
        }

        close(); // close the socket

        if( !isSuccess ) {
            // failure
            if( returnCode == 9 ) {
                return new FcpResultPut(FcpResultPut.KeyCollision, returnCode, codeDescription, isFatal);
            } else if( returnCode == 5 ) {
                return new FcpResultPut(FcpResultPut.Retry, returnCode, codeDescription, isFatal);
            } else {
                return new FcpResultPut(FcpResultPut.Error, returnCode, codeDescription, isFatal);
            }
        } else {
            // success
            // check if the returned text contains the computed CHK key (key generation)
            final int pos = chkKey.indexOf("CHK@");
            if( pos > -1 ) {
                chkKey = chkKey.substring(pos).trim();
            }
            return new FcpResultPut(FcpResultPut.Success, chkKey);
        }
    }

    /**
     * Generates a CHK key for the given FrostUploadItem (with its compatibility mode,
     * crypto key and compression settings) without uploading it.
     */
    public String generateCHK(final FrostUploadItem ulItem) throws IOException {
        // generate the CHK in the exact same way as a regular file upload (using the correct mime
        // type, compatibility mode, compression and crypto key settings), but without actually uploading the file!
        final FcpResultPut result = putKeyFromFile(
            FcpHandler.TYPE_FILE, // we're dealing with a file (not a message)
            "CHK@", // we want a CHK-type key
            ulItem.getFile(), // the filesystem path to the file we're generating the key for
            true, // getChkOnly (no upload, just generate a key)
            true, // auto-detect the mime type of the file (exactly the same setting as when we're actually uploading the files)
            ulItem); // provide the uploadItem to indicate that the file is in the uploadTable
                     // and so that we can retrieve things like the compatmode, cryptokey and
                     // compression settings...
        if( result == null || result.isSuccess() == false ) {
            return null;
        } else {
            return result.getChkKey();
        }
    }

    /**
     * returns private and public key
     * @return String[] containing privateKey / publicKey
     */
    public String[] getKeyPair() throws IOException, ConnectException {

        final List<String> msg = new ArrayList<String>();
        msg.add("GenerateSSK");
        msg.add("Identifier=genssk-" + FcpSocket.getNextFcpId());
        msg.add("EndMessage");
        sendMessage(msg);

        // receive and process node messages
        String[] result = null;
        while(true) {
            final NodeMessage nodeMsg = NodeMessage.readMessage(fcpSocket.getFcpIn());
            if( nodeMsg == null ) {
                break;
            }

            System.out.println("*GENERATESSK** INFO - NodeMessage:");
            System.out.println(nodeMsg.toString());

            if( nodeMsg.isMessageName("SSKKeypair") ) {

                // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED,
                // BUT IN THIS CASE WE DON'T NEED TO DECODE THEM SINCE SSK KEYPAIRS LACK FILENAMES
                // AND THEREFORE NEVER CONTAIN ANYTHING THAT NEEDS DECODING, SO Mixed.rawUrlDecode()
                // WOULD JUST GIVE US THE EXACT SAME STRINGS BACK! ;)
                String insertURI = nodeMsg.getStringValue("InsertURI");
                String requestURI = nodeMsg.getStringValue("RequestURI");

                int pos;
                pos = insertURI.indexOf("SSK@");
                if( pos > -1 ) {
                    insertURI = insertURI.substring(pos).trim();
                }
                if( insertURI.endsWith("/") ) {
                    insertURI = insertURI.substring(0, insertURI.length()-1);
                }

                pos = requestURI.indexOf("SSK@");
                if( pos > -1 ) {
                    requestURI = requestURI.substring(pos).trim();
                }
                if( requestURI.endsWith("/") ) {
                    requestURI = requestURI.substring(0, requestURI.length()-1);
                }

                result = new String[2];
                result[0] = insertURI;
                result[1] = requestURI;

                break;
            }
            // any other message means error here
            break;
        }
        close();
        return result;
    }

    // replaces all / with | in url
    public static String stripSlashes(final String uri) {
    	if (uri.startsWith("KSK@")) {
    		String myUri = null;
    		myUri= uri.replace('/','|');
    		return myUri;
    	} else if (uri.startsWith("SSK@")) {
    		final String sskpart= uri.substring(0, uri.indexOf('/') + 1);
    		final String datapart = uri.substring(uri.indexOf('/')+1).replace('/','|');
    		return sskpart + datapart;
    	} else {
    		return uri;
        }
    }

    public NodeMessage getNodeInfo() throws IOException {

        final List<String> msg = new ArrayList<String>();
        msg.add("ClientHello");
        msg.add("Name=hello-"+FcpSocket.getNextFcpId());
        msg.add("ExpectedVersion=2.0");
        msg.add("EndMessage");
        sendMessage(msg);

        final NodeMessage response = NodeMessage.readMessage(fcpSocket.getFcpIn());

        if (response == null) {
            throw new IOException("No ClientHello response!");
        }
        if ("NodeHello".equals(response.getMessageName())) {
            throw new IOException("Wrong ClientHello response: "+response.getMessageName());
        }

        return response;
    }

    public boolean checkFreetalkPlugin() {

        final List<String> msg = new ArrayList<String>();
        msg.add("GetPluginInfo");
        msg.add("Identifier=initial-"+FcpSocket.getNextFcpId());
        msg.add("PluginName=plugins.Freetalk.Freetalk");
        msg.add("EndMessage");
        sendMessage(msg);

        // wait for a message from node
        // GOOD: Pong
        // BAD: ProtocolError: 32 - No such plugin
        final NodeMessage nodeMsg = NodeMessage.readMessage(fcpSocket.getFcpIn());

        if (nodeMsg == null) {
            logger.warning("No answer to GetPluginInfo command received");
            return false;
        }

        if (nodeMsg.isMessageName("ProtocolError")) {
            logger.warning("ProtocolError received: "+nodeMsg.toString());
            return false;
        }

        if (nodeMsg.isMessageName("PluginInfo")) {
            logger.warning("Freetalk plugin answered with PluginInfo: "+nodeMsg.toString());
            if (nodeMsg.getBoolValue("IsTalkable")) {
                return true;
            }
        } else {
            logger.warning("Unknown answer to GetPluginInfo command: "+nodeMsg.toString());
        }
        return false;
    }
}
