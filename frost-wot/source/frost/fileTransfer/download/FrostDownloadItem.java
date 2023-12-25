/*
  FrostDownloadItem.java / Frost

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
package frost.fileTransfer.download;

import frost.*;
import frost.fileTransfer.BlocksPerMinuteCounter;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.FrostFileListFileObject;
import frost.fileTransfer.LastActivityTracker;
import frost.storage.perst.filelist.*;
import frost.util.*;
import frost.util.model.*;
import frost.messaging.frost.*;

public class FrostDownloadItem extends ModelItem<FrostDownloadItem> implements CopyToClipboardItem {

//    private transient static final Logger logger = Logger.getLogger(FrostDownloadItem.class.getName());

    // the constants representing download states
    public transient final static int STATE_WAITING    = 1; // wait for start
    public transient final static int STATE_TRYING     = 2; // download fcp message sent; this state is only used in non-persistent mode
    public transient final static int STATE_DONE       = 3;
    public transient final static int STATE_FAILED     = 4;
    public transient final static int STATE_PROGRESS   = 5; // download request was accepted by Freenet and is running
    public transient final static int STATE_DECODING   = 6; // decoding runs

	private String fileName = null;
	private String prefix = null;
    private String downloadDir = null;
	private long fileSize = -1;
	private String key = null;
	private String associatedMessageId = null;
	private String associatedBoardName = null;

    private Boolean enabled = Core.frostSettings.getBoolValue(SettingsClass.DOWNLOAD_ENABLED_DEFAULT);
    private int state = STATE_WAITING;
    private long downloadAddedTime = 0;
    private long downloadStartedTime = 0;
    private long downloadFinishedTime = 0;
	private int retries = 0;
    private long lastDownloadStopTime = 0;
    private String gqIdentifier = null;

    private boolean isLoggedToFile = false;
    private boolean isTracked = false;
    private boolean isCompletionProgRun = false;

    // if this downloadfile is a shared file then this object is set
    private transient FrostFileListFileObject fileListFileObject = null;

    private FreenetPriority priority = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD));

    // non-persistent fields (not stored in database)
    private transient int doneBlocks = 0;
    private transient int requiredBlocks = 0;
    private transient int totalBlocks = 0;
    private transient Boolean isFinalized = null;
    private transient String errorCodeDescription = null;

    // if DDA (Direct Disk Access) is used instead, then DIRECT (aka "AllData" socket message) is FALSE.
    // so just initialize the default download "DIRECT" state to whatever the user has chosen;
    // if they've enabled "use DDA" in the preferences, then we default to direct = false, otherwise
    // use direct = true. this isDirect variable is purely cosmetic and is always corrected when transfer begins.
    private transient boolean isDirect = ! Core.frostSettings.getBoolValue(SettingsClass.FCP2_USE_DDA); // we invert the value
    private transient boolean isExternal = false;

    private transient boolean internalRemoveExpected = false;
    private transient boolean internalRemoveExpectedThreadStarted = false;
    private transient boolean stateShouldBeProgress = false;

    // speed and activity measurement; non-persistent (not saved in the database)
    private LastActivityTracker activityTracker = null; // the activity time is stored in db as a long, but not this object
    private BlocksPerMinuteCounter bpmCounter = null;

    /**
     * Add a file from download text box.
     */
    public FrostDownloadItem(String newFileName, final String key) {

        if( newFileName != null ) {
            newFileName = Mixed.makeFilename(newFileName);
        }
        this.fileName = newFileName;
        this.key = key;

        gqIdentifier = buildGqIdentifier(this.fileName);

        downloadAddedTime = System.currentTimeMillis();

        state = STATE_WAITING;
    }

    /**
     * Add a file attachment.
     */
    public FrostDownloadItem(final String fileName, final String key, final long s) {
        this(fileName, key);
        this.fileSize = s;
    }

    /**
     * Add a shared file from filelist (user searched file and choosed one of the names).
     */
    public FrostDownloadItem(final FrostFileListFileObject newSfo, String newFileName) {

        FrostFileListFileObject sfo = null;

        // update the shared file object from database (key, owner, sources, ... may have changed)
        final FrostFileListFileObject updatedSfo = FileListStorage.inst().getFileBySha(newSfo.getSha());
        if( updatedSfo != null ) {
            sfo = updatedSfo;
        } else {
            // paranoia fallback
            sfo = newSfo;
        }

        if( newFileName != null ) {
            newFileName = Mixed.makeFilename(newFileName);
        }
        this.fileName = newFileName;
        this.fileSize = sfo.getSize();
        this.key = sfo.getKey();

        gqIdentifier = buildGqIdentifier(this.fileName);

        setFileListFileObject(sfo);

        downloadAddedTime = System.currentTimeMillis();

        state = STATE_WAITING;
    }

    /**
     * Constructor used when loading files from the database at startup.
     */
    public FrostDownloadItem(
            final String newFilename,
            final String newFilenamePrefix,
            final String newDownloadDir,
            final long newSize,
            final String newKey,
            final boolean newIsEnabled,
            final int newState,
            final long newDownloadAddedTime,
            final long newDownloadStartedTime,
            final long newDownloadLastActivityTime,
            final long newDownloadFinishedTime,
            final int newRetries,
            final long newLastDownloadStopTime,
            final String newGqId,
            final boolean newIsLoggedToFile,
            final boolean newIsTracked,
            final boolean newIsCompletionProgRun,
            final String newAssociatedBoardName,
            final String newAssociatedMessageId,
            final FreenetPriority newPriority
            
    ) {
        fileName = newFilename;
        prefix = newFilenamePrefix;
        // Always set the download dir via the setter, so that we convert absolute paths to relative
        // if required (such as when loading old legacy objects from the old legacy Frost database),
        // as well as verifying that the folder exists (will attempt to create if missing, and will
        // fall back to default dir if creation fails). this is just supplementary, since Frost-Next
        // always creates the dir if missing during the final file-writing stage too.
        // We *cannot* avoid creating the folders for finished items, since Frost will *also* want to
        // *save* those finished files to disk at startup if they're missing, so people who have lingering
        // Finished items will have to live with the folders re-appearing and the files being saved again,
        // since that's a security feature which allows finished files to be saved to disk again at
        // the next startup if the user accidentally deletes them or if Frost somehow crashed.
        // Simple solution for users: They should clear finished downloads when they're done. That also
        // frees up the global Freenet queue and their node's temporary storage, so it's important anyway.
        setDownloadDir(newDownloadDir);
        fileSize = newSize;
        key = newKey;
        enabled = Boolean.valueOf(newIsEnabled);
        state = newState;
        downloadAddedTime = newDownloadAddedTime;
        downloadStartedTime = newDownloadStartedTime;
        // when restoring objects from the database, we need to force-set the last activity timestamp
        forceActivityTime(newDownloadLastActivityTime);
        downloadFinishedTime = newDownloadFinishedTime;
        retries = newRetries;
        lastDownloadStopTime = newLastDownloadStopTime;
        gqIdentifier = newGqId;
        isLoggedToFile= newIsLoggedToFile;
        isTracked= newIsTracked;
        isCompletionProgRun= newIsCompletionProgRun;
        associatedBoardName = newAssociatedBoardName;
        associatedMessageId = newAssociatedMessageId;
        if( newPriority == null ) {
            priority = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD));
        } else {
            priority = newPriority;
        }

        if( this.state == FrostDownloadItem.STATE_PROGRESS ) {
            // download was running at end of last shutdown
            stateShouldBeProgress = true;
        }

        // reset unfinished downloads to "waiting" just in case Freenet no longer has it
        if( this.state != FrostDownloadItem.STATE_DONE && this.state != FrostDownloadItem.STATE_FAILED ) {
            this.state = FrostDownloadItem.STATE_WAITING;
        }
    }

    // creates a speed counter object tuned for downloads
    private BlocksPerMinuteCounter getBPMCounter() {
        if( bpmCounter == null ) {
            bpmCounter = new BlocksPerMinuteCounter(
                    BlocksPerMinuteCounter.DOWNLOAD_MAX_MEASUREMENT_AGE,
                    BlocksPerMinuteCounter.DOWNLOAD_SMOOTHING_FACTOR);
        }
        return bpmCounter;
    }

    // creates an activity tracker parented to this object
    private LastActivityTracker getActivityTracker() {
        if( activityTracker == null ) {
            activityTracker = new LastActivityTracker(FrostDownloadItem.this);
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

    // helper which retrieves whole seconds from the activity tracker (used by filesharing feature)
    public long getRuntimeSecondsWithoutProgress() {
        final long timeDiff = getActivityTracker().getRuntimeMillisWithoutProgress();
        if( timeDiff < 1 ) {
            return 0;
        }
        final long timeDiffSec = Math.round( ((double)timeDiff / 1000) );
        return timeDiffSec;
    }

    public boolean isSharedFile() {
        return getFileListFileObject() != null;
    }

    /**
     * Mainly used when the user triggers the "rename file" menu feature before adding the download.
     */
    public void setFileName(String newFileName) {
        if( newFileName != null ) {
            // replace illegal characters with underscores
            newFileName = Mixed.makeFilename(newFileName);
        }
        this.fileName = newFileName;
    }
	public String getFileName() {
		if (prefix == null || prefix.length() == 0) {
			return fileName;
		}
		return prefix + "_" + fileName;
	}
	public String getUnprefixedFilename() {
		return fileName;
	}

    public long getFileSize() {
		return fileSize;
	}
	public void setFileSize(final Long newFileSize) {
		fileSize = newFileSize;
        fireChange();
	}

	public String getKey() {
		return key;
	}
	public void setKey(final String newKey) {
	    setKey(newKey, true);
	}
	public void setKey(final String newKey, final boolean fireChange) {
	    key = newKey;
	    if (fireChange) {
	        fireChange();
	    }
	}

	public int getState() {
		return state;
	}
    public void setState(final int newState) {
        final int oldState = state;
        state = newState;

        // determine if a transfer has now begun
        if( oldState != newState && newState == FrostDownloadItem.STATE_PROGRESS ) {
            // unset the "last activity" timestamp when the download begins, but *only* if this
            // wasn't an expected state change. when we load a previously-in-progress item from
            // the database, then the state is "WAITING" but the "PROGRESS" state is *expected*, so
            // that's how we know that we've loaded a download from the database. we definitely
            // don't want to reset the activity time during database loading, since that would
            // mean constantly losing the last activity at every startup. ;) all other progress
            // changes (such as starting new downloads during the session, or restarting old ones)
            // are always unexpected. so this *only* resets the state during *actual* (re)starts.
            if( !stateShouldBeProgress ) {
                // NOTE: we don't need to unset this at other state changes, since the tracker's getter function
                // always returns 0 ("no measurement value") when a download is in any non-progress state
                getActivityTracker().unsetLastActivity();
            }
            stateShouldBeProgress = true; // we expect this item to be in "progress" state from now on

            // do all other maintenance resetting regardless of whether this was loaded from database
            downloadFinishedTime = 0; // reset the "download finished at" time when the transfer begins
            getBPMCounter().resetBlocksPerMinute(0); // reset blocks-per-minute counter to zero when the transfer begins
        } else {
            stateShouldBeProgress = false; // we expect this item to be in a non-progress state
        }

        fireChange();
    }

	public long getLastDownloadStopTime() {
		return lastDownloadStopTime;
	}
	public void setLastDownloadStopTime(final long val) {
        lastDownloadStopTime = val;
	}

	public int getRetries() {
		return retries;
	}
	public void setRetries(final int newRetries) {
		retries = newRetries;
        fireChange();
	}

	public Boolean isEnabled() {
		return enabled;
	}
	/**
	 * @param enabled new enable status of the item. If null, the current status is inverted
	 */
	public void setEnabled(Boolean newEnabled) {
		if (newEnabled == null && enabled != null) {
			//Invert the enable status
			final boolean enable = enabled.booleanValue();
			newEnabled = new Boolean(!enable);
		}
		enabled = newEnabled;
        fireChange();
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

	public int getRequiredBlocks() {
		return requiredBlocks;
	}
	public void setRequiredBlocks(final int newRequiredBlocks) {
	    requiredBlocks = newRequiredBlocks;
	}

	public int getTotalBlocks() {
		return totalBlocks;
	}
	public void setTotalBlocks(final int newTotalBlocks) {
		totalBlocks = newTotalBlocks;
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

    public long getDownloadAddedMillis() {
        return downloadAddedTime;
    }

    public long getDownloadFinishedMillis() {
        return downloadFinishedTime;
    }

    public void setDownloadFinishedTime(final long downloadFinishedTime) {
        this.downloadFinishedTime = downloadFinishedTime;
    }

    public long getDownloadStartedMillis() {
        return downloadStartedTime;
    }

    public void setDownloadStartedTime(final long downloadStartedTime) {
        this.downloadStartedTime = downloadStartedTime;
    }

    public String getGqIdentifier() {
        return gqIdentifier;
    }

    public void setGqIdentifier(final String gqId) {
        this.gqIdentifier = gqId;
    }

    public String getDownloadFilename() {
        return getDownloadDir() + getFileName();
    }

    public String getDownloadDir() {
        if (downloadDir == null || downloadDir.isEmpty()) {
            return getDefaultDownloadDir();
        }
        return downloadDir;
    }

    // NOTE: always contains a trailing slash, so that the caller doesn't have to add one
    public static String getDefaultDownloadDir() {
        String defaultDlDir = Core.frostSettings.getValue(SettingsClass.DIR_DOWNLOAD);
        if( defaultDlDir != null && !defaultDlDir.isEmpty() ) {
            defaultDlDir = Core.pathRelativizer.relativize(defaultDlDir);
            if( defaultDlDir != null ) {
                defaultDlDir = FileAccess.appendSeparator(defaultDlDir);
            }
        }
        if( defaultDlDir == null || defaultDlDir.isEmpty() ) {
            // if there's something wrong with the syntax of the path the user has set as download
            // directory, or if it's missing, then we'll just return the built-in untouched default
            // instead ("downloads/"), without running it through the relativizer (it's already relative).
            defaultDlDir = FileAccess.appendSeparator(Core.frostSettings.getDefaultValue(SettingsClass.DIR_DOWNLOAD));
        }

        return defaultDlDir;
    }

    // NOTE: always adds a trailing slash, so that the caller doesn't have to add one
    public boolean setDownloadDir(final String downloadDirDirty) {
        if( downloadDirDirty == null ){ return false; }

        // clean up and validate the input, and turn absolute paths that point to subfolders
        // of the Frost directory, into relative paths instead (this migrates users from older
        // versions of Frost which stored absolute paths, as well as ensures that any future
        // paths are always relative where needed)
        String downloadDirClean = Core.pathRelativizer.relativize(downloadDirDirty);
        if( downloadDirClean == null ) { return false; } // skip invalid paths
        downloadDirClean = FileAccess.appendSeparator(downloadDirClean); // append trailing slash

        // now attempt to create the folder if it doesn't exist, and reject if we couldn't create it
        if( FileAccess.createDir(new java.io.File(downloadDirClean)) ) {
            this.downloadDir = downloadDirClean;
            return true;
        }
        return false;
    }

    public void setFilenamePrefix(String newPrefix) {
        if( newPrefix != null ) {
            // replace illegal characters with underscores
            newPrefix = Mixed.makeFilename(newPrefix);
        }
        prefix = newPrefix;
    }

	public final String getFilenamePrefix() {
		return prefix;
    }

    public long getLastReceived() {
        if( getFileListFileObject() == null ) {
            return 0;
        } else {
            return getFileListFileObject().getLastReceived();
        }
    }

    public long getLastUploaded() {
        if( getFileListFileObject() == null ) {
            return 0;
        } else {
            return getFileListFileObject().getLastUploaded();
        }
    }

    public FrostFileListFileObject getFileListFileObject() {
        return fileListFileObject;
    }

    public void setFileListFileObject(final FrostFileListFileObject sharedFileObject) {
        if( this.fileListFileObject != null ) {
            this.fileListFileObject.removeListener(this);
        }

        int newState = -1;

        // if lastUploaded value changed, maybe restart failed download
        if( sharedFileObject != null && this.fileListFileObject != null ) {
//            if( sharedFileObject.getLastUploaded() > this.fileListFileObject.getLastUploaded() ) {
                if( getState() == STATE_FAILED ) {
                    newState = STATE_WAITING;
                }
//            }
        }

        this.fileListFileObject = sharedFileObject;

        if( this.fileListFileObject != null ) {
            this.fileListFileObject.addListener(this);
        }
        // take over key and update gui
        fireValueChanged();

        if( newState > -1 ) {
            setState(newState);
        }
    }

    /**
     * Called by a FrostFileListFileObject if a value interesting for FrostDownloadItem was set.
     * Also called by various Frost features that update the dlItem and then tell the GUI to update.
     */
    public void fireValueChanged() {
        // maybe take over the key, or set new key
        // NOTE: once a key is set, the ticker will allow to start this item!
        if( this.fileListFileObject != null ) {
            if( this.fileListFileObject.getKey() != null && this.fileListFileObject.getKey().length() > 0 ) {
                setKey( this.fileListFileObject.getKey(), false );
            }
        }

        // remaining values are dynamically fetched from FrostFileListFileObject
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

    // if an item is "direct" it means the data will be returned directly via an AllData message,
    // as opposed to written to the disk by the node. so if DDA (direct disk access) is on, then direct is FALSE!
    public boolean isDirect() {
        return isDirect;
    }
    public void setDirect(final boolean d) {
        isDirect = d;
        super.fireChange();
    }

    public FreenetPriority getPriority() {
        return priority;
    }
    
    public void setPriority(final FreenetPriority priority) {
        this.priority = priority;
        super.fireChange();
    }

    /**
     * @return  true if the remove of this request was expected and item should not be removed from the table (or marked as "Disappeared from global queue")
     */
    public boolean isInternalRemoveExpected() {
        return internalRemoveExpected;
    }
    /**
     * Set to true if we restart a download (code=11 or 27).
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
    
    public boolean isTracked() {
        return isTracked;
    }

    public void setLoggedToFile(final boolean isLoggedToFile) {
        this.isLoggedToFile = isLoggedToFile;
    }
    
    public void setTracked(final boolean isTracked) {
        this.isTracked = isTracked;
    }

    public String getAssociatedMessageId() {
		return associatedMessageId;
	}

    public void setAssociatedMessageId(final String messageId) {
		associatedMessageId = messageId;
	}

	public String getAssociatedBoardName() {
		return associatedBoardName;
	}

	public void setAssociatedBoardName(final String boardName) {
		associatedBoardName = boardName;
	}

    public void associateWithFrostMessageObject(FrostMessageObject associatedFrostMessageObject) {
        if( associatedFrostMessageObject == null ) {
            associatedBoardName = null;
            associatedMessageId = null;
        } else {
            associatedBoardName = associatedFrostMessageObject.getBoard().getName();
            associatedMessageId = associatedFrostMessageObject.getMessageId();
        }
    }

    public boolean isCompletionProgRun() {
        return isCompletionProgRun;
    }

    public void setCompletionProgRun(final boolean isCompletionProgRun) {
        this.isCompletionProgRun = isCompletionProgRun;
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
