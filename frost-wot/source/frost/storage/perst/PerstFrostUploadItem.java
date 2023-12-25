/*
  PerstFrostUploadItem.java / Frost
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
package frost.storage.perst;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import javax.swing.*;

import org.garret.perst.*;

import frost.*;
import frost.fileTransfer.FreenetCompatibilityManager; // the modern compatibility mode handler
import frost.fileTransfer.sharing.*;
import frost.fileTransfer.upload.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;

public class PerstFrostUploadItem extends Persistent {

    public String filePath;
    public String fileName;
    public String fileNamePrefix;
    public long fileSize;
    public String chkKey;
    public String cryptoKey; // new CHK crypto-key override field
    public boolean enabled;
    public int state;
    public long uploadAddedMillis;
    public long uploadStartedMillis;
    public long uploadLastActivityMillis;
    public long uploadFinishedMillis;
    public int retries;
    public long lastUploadStopTimeMillis;
    public String gqIdentifier;
    public String sharedFilesSha;
    public boolean compress;
    /**
     * A note about migration from legacy Frost databases:
     * They contain a legacy field, "public FreenetCompatibilityMode freenetCompatibilityMode",
     * which was the basic, old and useless compatibility mode enum.
     * It was a static list of four ancient COMPAT_125*-series modes and "COMPAT_CURRENT". Since
     * everyone used the modern modes for their uploads, there was no point in performing any
     * data-migration from the old field value. Those uploads will simply be initialized to the
     * default value ("COMPAT_CURRENT") under the new system instead. It only affects waiting
     * uploads that hadn't yet been started.
     * However, we can't re-use the old variable name, since that would cause the Perst database
     * loader to try to read the old object type into the new object type, which would fail
     * horribly. So that's why we're named freenetCompatibilityMode_v2 now.
     * When old data is loaded, the old field is thrown away and ignored. When new data is saved
     * to the database, it only uses this new field/name.
     */
    public String freenetCompatibilityMode_v2;

    public boolean isLoggedToFile;
    public boolean isCompletionProgRun;

    public PerstFrostUploadItem() {}

    /**
     * Turns a FrostUploadItem into an object ready for storing in the database.
     */
    public PerstFrostUploadItem(final FrostUploadItem ulItem) {
        filePath = ulItem.getFile().getPath();
        fileName = ulItem.getUnprefixedFileName();
        fileNamePrefix = ulItem.getFileNamePrefix();
        fileSize = ulItem.getFileSize();
        chkKey = ulItem.getKey();
        cryptoKey = ulItem.getCryptoKey(); // null if no custom key, otherwise a String
        enabled = (ulItem.isEnabled()==null?true:ulItem.isEnabled().booleanValue());
        state = ulItem.getState();
        uploadAddedMillis = ulItem.getUploadAddedMillis();
        uploadStartedMillis = ulItem.getUploadStartedMillis();
        uploadLastActivityMillis = ulItem.getLastActivityMillis();
        uploadFinishedMillis = ulItem.getUploadFinishedMillis();
        retries = ulItem.getRetries();
        lastUploadStopTimeMillis = ulItem.getLastUploadStopTimeMillis();
        gqIdentifier = ulItem.getGqIdentifier();
        isLoggedToFile = ulItem.isLoggedToFile();
        isCompletionProgRun = ulItem.isCompletionProgRun();
        compress = ulItem.getCompress();
        freenetCompatibilityMode_v2 = ulItem.getFreenetCompatibilityMode();
        sharedFilesSha = (ulItem.getSharedFileItem()==null?null:ulItem.getSharedFileItem().getSha());
    }

    /**
     * Turns a database item back into a real FrostUploadItem object.
     */
    public FrostUploadItem toFrostUploadItem(final List<FrostSharedFileItem> sharedFiles, final Logger logger, final Language language) {

        // make sure the file still exists and is still the same size
        // explicitly only check items that are waiting to be uploaded, or that are waiting to be
        // pre-encoded. we do not care about files that are finished, failed, or were in progress
        // last time (since that means the Freenet node has the file).
        // NOTE: if a file was in "progress" here but is missing from the global queue, it will
        // become "waiting" in the GUI instead, and from there the GUI will take over and do its
        // own missing/changed check, so every missing file will be taken care of no matter what.
        // NOTE: no need to check "isExternal", since we don't store external files in the perst database
        final File file = new File(filePath); // may point to a missing/modified file; we don't know yet
        if( state == FrostUploadItem.STATE_WAITING // upload hasn't started yet
         || state == FrostUploadItem.STATE_ENCODING_REQUESTED ) { // encoding hasn't started yet
            boolean isMissing = false;
            if( !file.exists() ) { // file is missing
                final StartupMessage sm = new StartupMessage(
                        StartupMessage.MessageType.UploadFileNotFound,
                        language.getString("StartupMessage.uploadFile.uploadFileNotFound.title"),
                        language.formatMessage("StartupMessage.uploadFile.uploadFileNotFound.text", filePath),
                        MiscToolkit.ERROR_MESSAGE,
                        true);
                Core.enqueueStartupMessage(sm);
                logger.severe("Upload items file does not exist, marked as failed: "+filePath);
                isMissing = true;
            }
            else if( file.length() != fileSize ) { // file has changed size on disk
                final StartupMessage sm = new StartupMessage(
                        StartupMessage.MessageType.UploadFileSizeChanged,
                        language.getString("StartupMessage.uploadFile.uploadFileSizeChanged.title"),
                        language.formatMessage("StartupMessage.uploadFile.uploadFileSizeChanged.text", filePath),
                        MiscToolkit.ERROR_MESSAGE,
                        true);
                Core.enqueueStartupMessage(sm);
                logger.severe("Upload items file size changed, marked as failed: "+filePath);
                isMissing = true;
            }

            // mark the file as failed in the uploads table
            // NOTE: normally we would set the exact failure reason as the "error code description"
            // string as well, but the perst storage doesn't store that non-persistent field,
            // and it's not a necessary field in any way.
            if( isMissing ) {
                state = FrostUploadItem.STATE_FAILED;
                enabled = false; // ensures that the file won't try uploading again unless the
                                 // user explicitly restarts it (they can do that if they put
                                 // the original file back)
            }
        }

        // validate shared files
        FrostSharedFileItem sharedFileItem = null;
        if( sharedFilesSha != null && sharedFilesSha.length() > 0 ) {
            for( final FrostSharedFileItem s : sharedFiles ) {
                if( s.getSha().equals(sharedFilesSha) ) {
                    sharedFileItem = s;
                    break;
                }
            }
            if( sharedFileItem == null ) {
                logger.severe("Upload items shared file object does not exist, removed from upload files: "+filePath);
                return null;
            }
            if( !sharedFileItem.isValid() ) {
                logger.severe("Upload items shared file is invalid, removed from upload files: "+filePath);
                return null;
            }
        }

        // now reconstruct the upload item from the stored object data
        final FrostUploadItem ulItem = new FrostUploadItem(
                file,
                fileName,
                fileNamePrefix,
                fileSize,
                chkKey,
                cryptoKey,
                enabled,
                state,
                uploadAddedMillis,
                uploadStartedMillis,
                uploadLastActivityMillis,
                uploadFinishedMillis,
                retries,
                lastUploadStopTimeMillis,
                gqIdentifier,
                isLoggedToFile,
                isCompletionProgRun,
                compress,
                freenetCompatibilityMode_v2); // construct using the modern compatibility mode
                                              // value; if null (in case of old databases), it
                                              // will default to COMPAT_CURRENT

        ulItem.setSharedFileItem(sharedFileItem);

        return ulItem;
    }
}
