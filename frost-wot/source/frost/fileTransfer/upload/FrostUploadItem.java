/*
  FrostUploadItem.java / Frost
  Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>

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
package frost.fileTransfer.upload;

import java.io.*;
import java.util.Random;

import frost.*;
import frost.fileTransfer.BlocksPerMinuteCounter;
import frost.fileTransfer.FreenetCompatibilityManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.LastActivityTracker;
import frost.fileTransfer.sharing.*;
import frost.util.*;
import frost.util.model.*;

/**
 * Represents a file to upload.
 */
public class FrostUploadItem extends ModelItem<FrostUploadItem> implements CopyToClipboardItem {

    // the constants representing upload states
    public final static int STATE_DONE       = 1;   // the upload is complete
//    public final static int STATE_UPLOADING  = 3;
    public final static int STATE_PROGRESS   = 4;   // upload runs, shows "... kb"
    public final static int STATE_ENCODING_REQUESTED  = 5; // an encoding of file is requested
    public final static int STATE_ENCODING   = 6;   // the encode is running
    public final static int STATE_WAITING    = 7;   // waiting until the next retry
    public final static int STATE_FAILED     = 8;

    private File file = null;
    private String fileName = null;
    private String fileNamePrefix = null;
    private long fileSize = 0;
    private String chkKey = null;
    private String cryptoKey = null; // used for manually overriding CHK crypto key
    private Boolean enabled = Core.frostSettings.getBoolValue(SettingsClass.UPLOAD_ENABLED_DEFAULT);
    private int state;
    private long uploadAddedMillis = 0;
    private long uploadStartedMillis = 0;
    private long uploadFinishedMillis = 0;
    private int retries = 0;
    private long lastUploadStopTimeMillis = 0; // millis when upload stopped the last time, needed to schedule uploads
    private String gqIdentifier = null;
    
    // NOTE: compression is off by default for all uploads, since it is HARMFUL. most uploads are already
    // compressed multimedia formats, and compression doubles the disk space and memory usage during both
    // uploading and downloading due to the extra de/compression step. it also adds extra CPU usage and
    // slows down uploads, for no gain whatsoever.
    private boolean compress = false;
    private String freenetCompatibilityMode = FreenetCompatibilityManager.getDefaultMode();

    private boolean isLoggedToFile = false;
    private boolean isCompletionProgRun = false;

    // non-persistent fields (not stored in database)
    private int totalBlocks = -1;
    private int doneBlocks = -1;
    private Boolean isFinalized = null;
    private String errorCodeDescription = null;
    private FreenetPriority priority = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_UPLOAD));

    // is only set if this uploaditem is a shared file
    private FrostSharedFileItem sharedFileItem = null;

    private boolean isExternal = false;

    private transient boolean internalRemoveExpected = false;
    private transient boolean internalRemoveExpectedThreadStarted = false;
    private transient boolean stateShouldBeProgress = false;

    // speed and activity measurement; non-persistent (not saved in the database)
    private LastActivityTracker activityTracker = null; // the activity time is stored in db as a long, but not this object
    private BlocksPerMinuteCounter bpmCounter = null;

    /**
     * Dummy to use for uploads of attachments. Is never saved.
     * Attachment uploads must never be persistent on 0.7.
     * We indicate this with gqIdentifier == null
     * Also used for external global queue items on 0.7.
     */
    public FrostUploadItem() {
    }
    
    public FrostUploadItem(final File file) {
        this(file, /*compress = */false);
    }

    /**
     * Used to add a new file to upload.
     * Either manually added via "Add new uploads" or a shared file.
     */
    public FrostUploadItem(final File file, final boolean compress) {

        this.file = file;
        this.fileName = Mixed.makeFilename(file.getName());
        this.fileSize = file.length();

        this.compress = compress;

        gqIdentifier = buildGqIdentifier(this.fileName);

        uploadAddedMillis = System.currentTimeMillis();

        state = STATE_WAITING;
    }

    /**
     * Constructor used by loadUploadTable. In other words, it's used
     * by PerstFrostUploadItem.java when loading objects from database.
     */
    public FrostUploadItem(
            final File newFile,
            final String newFileName,
            final String newFileNamePrefix,
            final long newFilesize,
            final String newKey,
            final String newCryptoKey,
            final boolean newIsEnabled,
            final int newState,
            final long newUploadAdded,
            final long newUploadStarted,
            final long newUploadLastActivity,
            final long newUploadFinished,
            final int newRetries,
            final long newLastUploadStopTimeMillis,
            final String newGqIdentifier,
            final boolean newIsLoggedToFile,
            final boolean newIsCompletionProgRun,
            final boolean newCompress,
            final String newFreenetCompatibilityMode // migration: will be null if the user comes
                                                     // from Frost's old database format, in which
                                                     // case we'll default it to COMPAT_CURRENT instead
    ) {
        file = newFile;
        fileName = newFileName;
        fileNamePrefix = newFileNamePrefix;
        fileSize = newFilesize;
        chkKey = newKey;
        // when we load a custom cryptokey from the database, we use the set-function
        // to ensure that the loaded cryptokey format is still valid. if null in db, the
        // cryptokey simply remains null, correctly.
        setCryptoKey(newCryptoKey);
        enabled = Boolean.valueOf(newIsEnabled);
        state = newState;
        uploadAddedMillis = newUploadAdded;
        uploadStartedMillis = newUploadStarted;
        // when restoring objects from the database, we need to force-set the last activity timestamp
        forceActivityTime(newUploadLastActivity);
        uploadFinishedMillis = newUploadFinished;
        retries = newRetries;
        lastUploadStopTimeMillis = newLastUploadStopTimeMillis;
        gqIdentifier = newGqIdentifier;
        isLoggedToFile = newIsLoggedToFile;
        isCompletionProgRun = newIsCompletionProgRun;
        compress = newCompress;
        /**
         * Migration: When the user loads a database from legacy Frost, this field will be null.
         * To handle that situation, we'll pass the value to the setFreenetCompatibilityMode()
         * function instead, to let *it* validate the value and set it to the default if it's null.
         *
         * Furthermore, that validation was necessary anyway, since the user may load items with
         * dynamically added modes that no longer exist in their Frost.ini, in which case we'll
         * reset those items back to COMPAT_CURRENT.
         *
         * That latter point only applies if the item still *hadn't started* transferring when the
         * database was saved last time. If the transfer had begun, the client will detect the mode
         * via FCP and will re-add it automatically, so the item will accept the same mode again.
         */
        setFreenetCompatibilityMode(newFreenetCompatibilityMode);

        if( this.state == FrostUploadItem.STATE_PROGRESS ) {
            // upload was running at end of last shutdown
            stateShouldBeProgress = true;
        }

        // reset unfinished uploads to "waiting" just in case Freenet no longer has it
        if( this.state != FrostUploadItem.STATE_DONE && this.state != FrostUploadItem.STATE_FAILED ) {
            this.state = FrostUploadItem.STATE_WAITING;
        }
    }

    // creates a speed counter object tuned for uploads
    private BlocksPerMinuteCounter getBPMCounter() {
        if( bpmCounter == null ) {
            bpmCounter = new BlocksPerMinuteCounter(
                    BlocksPerMinuteCounter.UPLOAD_MAX_MEASUREMENT_AGE,
                    BlocksPerMinuteCounter.UPLOAD_SMOOTHING_FACTOR);
        }
        return bpmCounter;
    }

    // creates an activity tracker parented to this object
    private LastActivityTracker getActivityTracker() {
        if( activityTracker == null ) {
            activityTracker = new LastActivityTracker(FrostUploadItem.this);
        }
        return activityTracker;
    }

    // this function is only called *once*, whenever loading items from database
    private void forceActivityTime(final long newLastActivityMillis) {
        getActivityTracker().setLastActivityMillis(newLastActivityMillis);
    }

    // retrieves the latest activity timestamp from the tracker
    // see LastActivityTracker.java:getLastActivityMillis() for return value documentation
    public long getLastActivityMillis() {
        return getActivityTracker().getLastActivityMillis();
    }

    public boolean isSharedFile() {
        return getSharedFileItem() != null;
    }

    public long getFileSize() {
        return fileSize;
    }
    public void setFileSize(final Long newFileSize) {
        fileSize = newFileSize.longValue();
        fireChange();
    }

    public String getKey() {
        return chkKey;
    }
    public void setKey(final String newKey) {
        chkKey = newKey;
        fireChange();
    }

    public int getState() {
        return state;
    }
    public void setState(final int newState) {
        final int oldState = state;
        state = newState;

        // determine if a transfer has now begun
        if( oldState != newState && newState == FrostUploadItem.STATE_PROGRESS ) {
            // unset the "last activity" timestamp when the upload begins, but *only* if this
            // wasn't an expected state change. when we load a previously-in-progress item from
            // the database, then the state is "WAITING" but the "PROGRESS" state is *expected*, so
            // that's how we know that we've loaded an upload from the database. we definitely
            // don't want to reset the activity time during database loading, since that would
            // mean constantly losing the last activity at every startup. ;) all other progress
            // changes (such as starting new uploads during the session, or restarting old ones)
            // are always unexpected. so this *only* resets the state during *actual* (re)starts.
            if( !stateShouldBeProgress ) {
                // NOTE: we don't need to unset this at other state changes, since the tracker's getter function
                // always returns 0 ("no measurement value") when an upload is in any non-progress state
                getActivityTracker().unsetLastActivity();
            }
            stateShouldBeProgress = true; // we expect this item to be in "progress" state from now on

            // do all other maintenance resetting regardless of whether this was loaded from database
            uploadFinishedMillis = 0; // reset the "upload finished at" time when the transfer begins
            getBPMCounter().resetBlocksPerMinute(0); // reset blocks-per-minute counter to zero when the transfer begins
        } else {
            stateShouldBeProgress = false; // we expect this item to be in a non-progress state
        }

        fireChange();
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }
    public void setTotalBlocks(final int newTotalBlocks) {
        totalBlocks = newTotalBlocks;
    }

    public int getRetries() {
        return retries;
    }
    public void setRetries(final int newRetries) {
        retries = newRetries;
        fireChange();
    }
    
    public boolean getCompress() {
        return compress;
    }

    public void setCompress(final boolean compress) {
        this.compress = compress;
    }

    public String getFreenetCompatibilityMode() {
        return freenetCompatibilityMode;
    }

    public void setFreenetCompatibilityMode(final String freenetCompatibilityMode) {
        if( freenetCompatibilityMode == null ) {
            // no mode provided. this only happens when the user is migrating from an old Frost database.
            // in that case, we just set the mode to the default ("COMPAT_CURRENT").
            // (NOTE: the only other invocations are from the "add new uploads" right-click menu
            // and from the PersistenceManager, and neither of those send null values)
            this.freenetCompatibilityMode = FreenetCompatibilityManager.getDefaultMode();
        } else {
            // validate the mode, and fall back to "COMPAT_CURRENT" if the compatibility manager
            // doesn't know about the provided mode. the reason for this is that people may be loading
            // databases containing items that were set to one of the dynamically added Frost.ini
            // compatibility modes, and they may then have cleared their Frost.ini so that it no longer
            // knows about it. it's an obscure edge-case, but instead of "assuming" that the user
            // *wants* that deleted mode back, we'll just set it to the default.
            if( Core.getCompatManager().isKnownMode(freenetCompatibilityMode) ) {
                this.freenetCompatibilityMode = freenetCompatibilityMode;
            } else {
                this.freenetCompatibilityMode = FreenetCompatibilityManager.getDefaultMode();
            }
        }
    }

    public int getDoneBlocks() {
        return doneBlocks;
    }
    public void setDoneBlocks(final int newDoneBlocks) {
        // possibly update the "last activity" timestamp
        getActivityTracker().updateLastActivity(doneBlocks, newDoneBlocks);

        // now just set the new progress
        doneBlocks = newDoneBlocks;
        getBPMCounter().updateBlocksPerMinute(newDoneBlocks); // update the blocks-per-minute counter
    }

    /**
     * @param enabled new enable status of the item. If null, the current status is inverted
     */
    public void setEnabled(Boolean newEnabled) {
        if (newEnabled == null) {
            enabled = ! enabled;
        } else {
            enabled = newEnabled;
        }
        fireChange();
    }

    public Boolean isEnabled() {
        return enabled;
    }

    public long getLastUploadStopTimeMillis() {
        return lastUploadStopTimeMillis;
    }
    public void setLastUploadStopTimeMillis(final long lastUploadStopTimeMillis) {
        this.lastUploadStopTimeMillis = lastUploadStopTimeMillis;
    }

    public long getUploadAddedMillis() {
        return uploadAddedMillis;
    }
    public long getUploadStartedMillis() {
        return uploadStartedMillis;
    }
    public void setUploadStartedMillis(final long v) {
        uploadStartedMillis = v;
        fireChange();
    }

    public long getUploadFinishedMillis() {
        return uploadFinishedMillis;
    }
    public void setUploadFinishedMillis(final long v) {
        uploadFinishedMillis = v;
        fireChange();
    }

    public String getGqIdentifier() {
        return gqIdentifier;
    }
    public void setGqIdentifier(final String i) {
        gqIdentifier = i;
    }

    public FrostSharedFileItem getSharedFileItem() {
        return sharedFileItem;
    }

    public void setSharedFileItem(final FrostSharedFileItem sharedFileItem) {
        this.sharedFileItem = sharedFileItem;
    }

    public String getFileName() {
        if( fileNamePrefix == null || fileNamePrefix.length() == 0 ) {
            return fileName;
        }
        return fileNamePrefix + "_" + fileName;
    }

    public String getUnprefixedFileName() {
        return fileName;
    }

    public String getFileNamePrefix() {
        return fileNamePrefix;
    }

    /**
     * Mainly used when the user triggers the "rename file" menu feature before adding the upload.
     */
    public void setFileName(String newFileName) {
        if( newFileName != null ) {
            // replace illegal characters with underscores
            newFileName = Mixed.makeFilename(newFileName);
        }
        this.fileName = newFileName;
    }

    public void setFileNamePrefix(String newPrefix) {
        if( newPrefix != null ) {
            // replace illegal characters with underscores
            newPrefix = Mixed.makeFilename(newPrefix);
        }
        this.fileNamePrefix = newPrefix;
    }

    public File getFile() {
        return file;
    }
    public void setFile(final File f) {
        file = f;
    }

    public Boolean isFinalized() {
        return isFinalized;
    }
    public void setFinalized(final boolean finalized) {
        if( finalized ) {
            isFinalized = Boolean.TRUE;
        } else {
            isFinalized = Boolean.FALSE;
        }
    }

    // Called by various Frost features that update the ulItem and then tell the GUI to update.
    public void fireValueChanged() {
        super.fireChange();
    }

    /**
     * Builds a global queue identifier.
     */
    private String buildGqIdentifier(final String filename) {
        return new StringBuilder()
            .append("Frost-")
            .append(filename.replace(' ', '_'))
            .append("-")
            .append(System.currentTimeMillis())
            .append(Core.getCrypto().getSecureRandom().nextInt(10)) // 0-9
            .toString();
    }

    public String getErrorCodeDescription() {
        return errorCodeDescription;
    }
    public void setErrorCodeDescription(final String errorCodeDescription) {
        this.errorCodeDescription = errorCodeDescription;
    }

    /**
     * @return  true if this item is an external global queue item
     */
    public boolean isExternal() {
        return isExternal;
    }
    public void setExternal(final boolean e) {
        isExternal = e;
    }

    public FreenetPriority getPriority() {
        return priority;
    }

    public void setPriority(final FreenetPriority priority) {
        this.priority = priority;
        super.fireChange();
    }

    /**
     * @return  true if the remove of this request was expected and item should not be removed from the table
     */
    public boolean isInternalRemoveExpected() {
        return internalRemoveExpected;
    }
    /**
     * onPersistentRequestRemoved method checks this and does not remove the request
     * from the table if the remove was expected.
     */
    public void setInternalRemoveExpected(final boolean internalRemoveExpected) {
        this.internalRemoveExpected = internalRemoveExpected;
    }

    /**
     * This is used by the thread that clears the "expected" flag, to avoid starting multiple threads.
     */
    public boolean isInternalRemoveExpectedThreadStarted() {
        return internalRemoveExpectedThreadStarted;
    }
    public void setInternalRemoveExpectedThreadStarted(final boolean val) {
        this.internalRemoveExpectedThreadStarted = val;
    }

    @Override
    public String toString() {
        return getFileName();
    }

    public boolean isLoggedToFile() {
        return isLoggedToFile;
    }

    public void setLoggedToFile(final boolean isLoggedToFile) {
        this.isLoggedToFile = isLoggedToFile;
    }

    public boolean isCompletionProgRun() {
        return isCompletionProgRun;
    }

    public void setCompletionProgRun(final boolean isCompletionProgRun) {
        this.isCompletionProgRun = isCompletionProgRun;
    }

    /**
     * Simply clears the crypto key for this object.
     */
    public void clearCryptoKey() {
        cryptoKey = null;
    }

    /**
     * Gets the currently configured crypto key for this object.
     * @return - null if there is no crypto key, otherwise a lowercase 64-character hex String.
     */
    public String getCryptoKey() {
        if( cryptoKey == null || cryptoKey.length() != 64 ) {
            return null;
        }
        return cryptoKey;
    }

    /**
     * Tells this upload object to use a new, user-provided crypto key.
     * The key will be strictly validated. Always check the return value!
     * NOTE: Passing in a null/empty string will NOT clear the key. Use clearCryptoKey() for that.
     * @param {String} inputKey - the user's key input; does not need to be sanitized.
     * @return - null if the key was invalid/rejected, otherwise a String object representing
     * the CLEAN version of the *saved* key, for display in your GUI. Use this instead of user-input.
     */
    public String setCryptoKey(final String inputKey) {
        // validate and clean up the input key
        final String cleanKey = FrostUploadItem.validateCryptoKey(inputKey);
        if( cleanKey == null ) {
            return null; // key rejected
        }
        // key is valid! 64 hex characters found in input
        cryptoKey = cleanKey;
        return cryptoKey;
    }

    /**
     * Generates a random crypto key, saves it as the new key for this object,
     * and returns the result for easy displaying in the GUI.
     *
     * WARNING: NEVER call this function *after* the user has added the files to the uploads table.
     * ONLY call it while they're still working in the "add new uploads" dialog. Otherwise you
     * could get unexpected results, such as setting a new key for a file that's already in progress,
     * in which case the new key WILL NOT BE USED for the transfer since it's already begun.
     * @return - the new, random crypto key as a 64-character lowercase hex string
     */
    public String setRandomCryptoKey() {
        // generate and save a new key and return the result
        cryptoKey = FrostUploadItem.generateRandomCryptoKey();
        return cryptoKey;
    }

    /**
     * This is a static helper function which lets you validate a key and retrieve a clean
     * version, if you want to check user input without applying the key to any items yet.
     * @param {String} inputKey - the user's key input; does not need to be sanitized.
     * @return - null if the key was invalid/rejected, otherwise a String object representing
     * the CLEAN version of the *input* key. Use this in your GUI instead of user-input.
     */
    public static String validateCryptoKey(final String inputKey) {
        if( inputKey == null ) { return null; }
        // remove leading/trailing whitespace and make sure the new key is lowercase
        final String cleanKey = inputKey.trim().toLowerCase();
        // validate key and make sure it contains exactly 64 hex characters (0-9a-f), for a total of 32 bytes
        if( !cleanKey.matches("^[0-9a-f]{64}$") ) {
            return null;
        }
        // key is valid! 64 hex characters found in input
        return cleanKey;
    }

    /**
     * This is a static helper function which is useful if you want to generate a random key
     * to use when simultaneously applying the same key to multiple upload items at once.
     */
    public static String generateRandomCryptoKey() {
        // allocate 32 bytes (256 bits) of storage
        final byte[] cryptoKeyBytes = new byte[32];
        // create a random number generator, let Java seed it with a unique value, and then generate 32 random bytes
        new Random().nextBytes(cryptoKeyBytes);
        // convert the random byte array to a 64-character lowercase hex string
        final String randomCryptoKey = Mixed.bytesToHex(cryptoKeyBytes).toLowerCase();
        return randomCryptoKey;
    }

    // see BlocksPerMinuteCounter.java:getAverageBlocksPerMinute() for return value documentation
    public double getAverageBlocksPerMinute() {
        return getBPMCounter().getAverageBlocksPerMinute();
    }

    // see BlocksPerMinuteCounter.java:getAverageBytesPerMinute() for return value documentation
    public long getAverageBytesPerMinute() {
        return getBPMCounter().getAverageBytesPerMinute();
    }

    // see BlocksPerMinuteCounter.java:getAverageBytesPerSecond() for return value documentation
    public long getAverageBytesPerSecond() {
        return getBPMCounter().getAverageBytesPerSecond();
    }

    // see BlocksPerMinuteCounter.java:getMillisSinceLastMeasurement() for return value documentation
    public long getMillisSinceLastMeasurement() {
        return getBPMCounter().getMillisSinceLastMeasurement();
    }

    // see BlocksPerMinuteCounter.java:getLastMeasurementBlocks() for return value documentation
    public int getLastMeasurementBlocks() {
        return getBPMCounter().getLastMeasurementBlocks();
    }

    // see BlocksPerMinuteCounter.java:getEstimatedMillisRemaining() for return value documentation
    public long getEstimatedMillisRemaining(final int aCurrentDoneBlocks, final int aTotalBlocks, final int aTransferType) {
        return getBPMCounter().getEstimatedMillisRemaining(aCurrentDoneBlocks, aTotalBlocks, aTransferType);
    }
}
