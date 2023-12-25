/*
  PersistenceManager.java / Frost
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
package frost.fileTransfer;

import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;

import javax.swing.*;

import frost.*;
import frost.fcp.*;
import frost.fcp.FcpNoAnswerException;
import frost.fcp.fcp07.*;
import frost.fcp.fcp07.filepersistence.*;
import frost.fileTransfer.download.*;
import frost.fileTransfer.download.HashBlocklistManager;
import frost.fileTransfer.upload.*;
import frost.fileTransfer.FreenetCompatibilityManager;
import frost.util.*;
import frost.util.model.*;

/**
 * This class starts/stops/monitors the persistent requests on Freenet 0.7.
 */
public class PersistenceManager implements IFcpPersistentRequestsHandler {

//FIXME    Problem: positiv abgleich klappt, aber woher weiss ich wann LIST durch ist um zu checken ob welche fehlen?

    private static final Logger logger = Logger.getLogger(PersistenceManager.class.getName());

    // this would belong to the models, but its not needed there without persistence, hence we maintain it here
    private final Hashtable<String,FrostUploadItem> uploadModelItems = new Hashtable<String,FrostUploadItem>();
    private final Hashtable<String,FrostDownloadItem> downloadModelItems = new Hashtable<String,FrostDownloadItem>();

    private final UploadModel uploadModel;
    private final DownloadModel downloadModel;

    private final DirectTransferQueue directTransferQueue;
    private final DirectTransferThread directTransferThread;

    private boolean showExternalItemsDownload;
    private boolean showExternalItemsUpload;

    private boolean isConnected = true; // we start in connected state

    private final FcpPersistentQueue persistentQueue;
    private final FcpListenThreadConnection fcpConn;
    private final FcpMultiRequestConnectionFileTransferTools fcpTools;


	private final Set<String> directGETsInProgress = new HashSet<String>();
    private final Set<String> directPUTsInProgress = new HashSet<String>();

    private final Set<String> directPUTsWithoutAnswer = new HashSet<String>();

    /**
     * @return  true if Frost is configured to use persistent uploads and downloads, false if not
     */
    public static boolean isPersistenceEnabled() {
    	return Core.frostSettings.getBoolValue(SettingsClass.FCP2_USE_PERSISTENCE);
    }

    /**
     * Must be called after the upload and download model is initialized!
     */
    public PersistenceManager(final UploadModel um, final DownloadModel dm) throws Throwable {

        showExternalItemsDownload = Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD);
        showExternalItemsUpload = Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD);

        if (FcpHandler.inst().getFreenetNode() == null) {
            throw new Exception("No freenet nodes defined");
        }
        final NodeAddress na = FcpHandler.inst().getFreenetNode();
        fcpConn = FcpListenThreadConnection.createInstance(na);
        fcpTools = new FcpMultiRequestConnectionFileTransferTools(fcpConn);

        Core.frostSettings.addPropertyChangeListener(new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if( evt.getPropertyName().equals(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD) ) {
                    showExternalItemsDownload = Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD);
                    if( showExternalItemsDownload ) {
                        // get external items
                        showExternalDownloadItems();
                    }
                } else if( evt.getPropertyName().equals(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD) ) {
                    showExternalItemsUpload = Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD);
                    if( showExternalItemsUpload ) {
                        // get external items
                        showExternalUploadItems();
                    }
                }
            }
        });

        uploadModel = um;
        downloadModel = dm;

        // initially get all of our internally queued (uploads/downloads via Frost) items from model
        for(int x=0; x < uploadModel.getItemCount(); x++) {
            final FrostUploadItem ul = (FrostUploadItem) uploadModel.getItemAt(x);
            if( ul.getGqIdentifier() != null ) {
                uploadModelItems.put(ul.getGqIdentifier(), ul);
            }
        }
        for(int x=0; x < downloadModel.getItemCount(); x++) {
            final FrostDownloadItem ul = (FrostDownloadItem) downloadModel.getItemAt(x);
            if( ul.getGqIdentifier() != null ) {
                downloadModelItems.put(ul.getGqIdentifier(), ul);
            }
        }

        // enqueue listeners to keep updated about the internally queued model items
        uploadModel.addOrderedModelListener(
                new SortedModelListener<FrostUploadItem>() {
                    public void modelCleared() {
                        for( final FrostUploadItem ul : uploadModelItems.values() ) {
                            if( ul.isExternal() == false ) {
                                fcpTools.removeRequest(ul.getGqIdentifier());
                            }
                        }
                        uploadModelItems.clear();
                    }
                    public void itemAdded(final int position, final FrostUploadItem item) {
                        uploadModelItems.put(item.getGqIdentifier(), item);
                        if( !item.isExternal() ) {
                            // maybe start immediately
                            startNewUploads();
                        }
                    }
                    public void itemChanged(final int position, final FrostUploadItem item) {
                    }
                    public void itemsRemoved(final int[] positions, final List<FrostUploadItem> items) {
                        for(final FrostUploadItem item : items) {
                            uploadModelItems.remove(item.getGqIdentifier());
                            if( item.isExternal() == false ) {
                                fcpTools.removeRequest(item.getGqIdentifier());
                            }
                        }
                    }
                });

        downloadModel.addOrderedModelListener(
                new SortedModelListener<FrostDownloadItem>() {
                    public void modelCleared() {
                        for( final FrostDownloadItem ul : downloadModelItems.values() ) {
                            if( ul.isExternal() == false ) {
                                fcpTools.removeRequest(ul.getGqIdentifier());
                            }
                        }
                        downloadModelItems.clear();
                    }
                    public void itemAdded(final int position, final FrostDownloadItem item) {
                        downloadModelItems.put(item.getGqIdentifier(), item);
                        if( !item.isExternal() ) {
                            // maybe start immediately
                            startNewDownloads();
                        }
                    }
                    public void itemChanged(final int position, final FrostDownloadItem item) {
                    }
                    public void itemsRemoved(final int[] positions, final List<FrostDownloadItem> items) {
                        for(final FrostDownloadItem item : items) {
                            downloadModelItems.remove(item.getGqIdentifier());
                            if( item.isExternal() == false ) {
                                fcpTools.removeRequest(item.getGqIdentifier());
                            }
                        }
                    }
                });

        directTransferQueue = new DirectTransferQueue();
        directTransferThread = new DirectTransferThread();

        persistentQueue = new FcpPersistentQueue(fcpTools, this);
    }

    public FcpMultiRequestConnectionFileTransferTools getFcpTools() {
    	return fcpTools;
    }

    public void startThreads() {
        directTransferThread.start();
        persistentQueue.startThreads();
        final TimerTask task = new TimerTask() {
            @Override
            public void run() {
                maybeStartRequests();
            }
        };
        Core.schedule(task, 3000, 3000);
    }

    public void removeRequests(final List<String> requests) {
        for( final String id : requests ) {
            fcpTools.removeRequest(id);
        }
    }

    
    /**
     * @param dlItem  items whose global identifier is to check
     * @return  true if this item is currently in the global queue, no matter in what state
     */
    public boolean isItemInGlobalQueue(final FrostDownloadItem dlItem) {
        return persistentQueue.isIdInGlobalQueue(dlItem.getGqIdentifier());
    }
    /**
     * @param ulItem  items whose global identifier is to check
     * @return  true if this item is currently in the global queue, no matter in what state
     */
    public boolean isItemInGlobalQueue(final FrostUploadItem ulItem) {
        return persistentQueue.isIdInGlobalQueue(ulItem.getGqIdentifier());
    }

    /**
     * Periodically check if we could start a new request.
     * This could be done better if we check if a request finished, but later...
     */
    private void maybeStartRequests() {
        // start new requests
        startNewUploads();
        startNewDownloads();
    }

    public void connected() {
        isConnected = true;
        MainFrame.getInstance().setConnected();
        logger.severe("now connected");
    }
    public void disconnected() {
        isConnected = false;

        MainFrame.getInstance().setDisconnected();

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                uploadModel.removeExternalUploads();
                downloadModel.removeExternalDownloads();
            }
        });
        logger.severe("disconnected!");
    }

    public boolean isConnected() {
        return isConnected;
    }

    /**
     * Enqueue a direct GET if not already enqueued, or already downloaded to download dir.
     * @return true if item was enqueued
     */
    public boolean maybeEnqueueDirectGet(final FrostDownloadItem dlItem, final long expectedFileSize) {
        if( !isDirectTransferInProgress(dlItem) ) {
            // no DIRECT save is in progress for this item; but before we enqueue it, we need to
            // verify that the file is missing locally OR that the local size differs from expected.
            // so first delete any existing, mismatching local file *if* the local size differs.
            final File targetFile = new File(dlItem.getDownloadFilename());
            if( targetFile.isFile() && targetFile.length() != expectedFileSize ) {
                targetFile.delete();
            }
            // now enqueue the fetch-job if no local file exists
            if( !targetFile.isFile() ) {
                directTransferQueue.appendItemToQueue(dlItem);
            } else {
                // we already have a local file of the exact right path+filesize, so now just notify
                // the download queue that the job is complete (using the same type of result object
                // as a finished DIRECT persistent-get). this ensures that the download doesn't get
                // incorrectly stuck in a "Waiting, 100%" state.
                final FcpResultGet result = new FcpResultGet(true);
                FileTransferManager.inst().getDownloadManager().notifyDownloadFinished(dlItem, result, targetFile);
            }
            return true; // DIRECT transfer-save now enqueued or file already existed locally
        }
        return false; // DIRECT transfer-save already in progress for this item
    }

    private void applyPriority(final FrostDownloadItem dlItem, final FcpPersistentGet getReq) {
        // apply externally changed priority
        if( dlItem.getPriority() != getReq.getPriority() ) {
            if (Core.frostSettings.getBoolValue(SettingsClass.FCP2_ENFORCE_FROST_PRIO_FILE_DOWNLOAD)) {
                // reset priority with our current value
                fcpTools.changeRequestPriority(getReq.getIdentifier(), dlItem.getPriority());
            } else {
                // apply to downloaditem
                dlItem.setPriority(getReq.getPriority());
            }
        }
    }

    /**
     * Apply the states of FcpRequestGet to the FrostDownloadItem.
     */
    private void applyState(final FrostDownloadItem dlItem, final FcpPersistentGet getReq) {
        // when cancelled and we expect this, don't set failed; don't even set the old priority!
        // NOTE: unlike upload items (see applyState() for FrostUploadItem), there aren't any
        // subsequent status updates that would set this download item to failed again
        if( dlItem.isInternalRemoveExpected() && getReq.isFailed() ) {
            final int returnCode = getReq.getCode();
            if( returnCode == 25 ) {
                return;
            }
        }

        applyPriority(dlItem, getReq);

        if( dlItem.isDirect() != getReq.isDirect() ) {
            dlItem.setDirect(getReq.isDirect());
        }

        if( !getReq.isProgressSet() && !getReq.isSuccess() && !getReq.isFailed() ) {
            if( dlItem.getState() == FrostDownloadItem.STATE_WAITING ) {
                dlItem.setState(FrostDownloadItem.STATE_PROGRESS);
            }
            return;
        }

        if( getReq.isProgressSet() ) {
            final int doneBlocks = getReq.getDoneBlocks();
            final int requiredBlocks = getReq.getRequiredBlocks();
            final int totalBlocks = getReq.getTotalBlocks();
            final boolean isFinalized = getReq.isFinalized();
            if( totalBlocks > 0 ) {
                dlItem.setDoneBlocks(doneBlocks);
                dlItem.setRequiredBlocks(requiredBlocks);
                dlItem.setTotalBlocks(totalBlocks);
                dlItem.setFinalized(isFinalized);
                dlItem.fireValueChanged();
            }
            if( dlItem.getState() != FrostDownloadItem.STATE_PROGRESS ) {
                dlItem.setState(FrostDownloadItem.STATE_PROGRESS);
            }
        }
        if( getReq.isSuccess() ) {
            // maybe progress was not completely sent
            dlItem.setFinalized(true);
            if( dlItem.getTotalBlocks() > 0 && dlItem.getDoneBlocks() < dlItem.getRequiredBlocks() ) {
                dlItem.setDoneBlocks(dlItem.getRequiredBlocks());
                dlItem.fireValueChanged();
            }
            if( dlItem.isExternal() ) {
                dlItem.setFileSize(getReq.getFilesize());
                dlItem.setState(FrostDownloadItem.STATE_DONE);
            } else {
                // this download is complete; now determine how to write the file to disk
                // if DIRECT mode is used, we must retrieve the data from the node manually
                // if DDA (direct disk access) was used, the node was responsible for writing it to disk
                if( dlItem.isDirect() ) { // DIRECT
                    maybeEnqueueDirectGet(dlItem, getReq.getFilesize());
                } else { // DDA
                    final FcpResultGet result = new FcpResultGet(true);
                    final File targetFile = new File(dlItem.getDownloadFilename());
                    FileTransferManager.inst().getDownloadManager().notifyDownloadFinished(dlItem, result, targetFile);
                }
            }
        }
        boolean reqRemoved = false;
        if( getReq.isFailed() ) {
            final String desc = getReq.getCodeDesc();
            if( dlItem.isExternal() ) {
                dlItem.setState(FrostDownloadItem.STATE_FAILED);
                dlItem.setErrorCodeDescription(desc);
            } else {
                // the download failed, so don't write anything to disk, just notify the manager about the failure
                final int returnCode = getReq.getCode();
                final boolean isFatal = getReq.isFatal();

                final String redirectURI = getReq.getRedirectURI();
                final FcpResultGet result = new FcpResultGet(false, returnCode, desc, isFatal, redirectURI);
                final File targetFile = new File(dlItem.getDownloadFilename());
                final boolean retry = FileTransferManager.inst().getDownloadManager().notifyDownloadFinished(dlItem, result, targetFile);
                if( retry ) {
                    fcpTools.removeRequest(getReq.getIdentifier());
                    reqRemoved = true; // prevents the hash-check from trying to de-queue this removed request
                    startDownload(dlItem); // restart immediately
                }
            }
        }
        // if the hash for this item hasn't been checked, then we'll need to ensure that the
        // user actually wants this file.
        // NOTE: we check after all other updates above, to ensure we're looking
        // at the latest "state" for the file.
        // IMPORTANT NOTE: the reason that we don't check "hashblocklist enabled?" here, is so that
        // the user can disable the blocklist, start a download, get its "unchecked" hash into the
        // "hash checked = true" state, and then re-enable the blocklist *without* their ongoing transfer
        // being instantly aborted by the existence of an "unchecked" hash that matches their blocklist.
        // however; it's a per-session state, which means that if the user restarts Frost, any unfinished
        // downloads that were granted "exception" during the previous session will be aborted. this is on purpose.
        // LAST NOTE: this code *only* runs for persistent downloads on the global/persistent queue. if Frost
        // has been set to the non-persistent transfer mode (almost nobody does that), then FcpConnection.java's
        // hash-checking is used instead, which uses the same logic but adapted for the non-persistent transfer mode.
        if( !getReq.isHashChecked() ) {
            try {
                // only perform the check if hashblocking is enabled, and the item is internally
                // queued by Frost, and isn't finished or failed. so in other words, we'll check the
                // hash if it's in WAITING, TRYING, PROGRESS or DECODING states.
                if( Core.frostSettings.getBoolValue(SettingsClass.HASHBLOCKLIST_ENABLED)
                        && !reqRemoved // ensures *this* persistent request ID is still the active one for this file
                        && !dlItem.isExternal()
                        && dlItem.getState() != FrostDownloadItem.STATE_DONE
                        && dlItem.getState() != FrostDownloadItem.STATE_FAILED )
                {
                    final String hashMD5 = getReq.getMD5();
                    if( hashMD5 != null ) {
                        final String localFilePath = HashBlocklistManager.getInstance().getMD5FilePath(hashMD5);
                        if( localFilePath != null ) {
                            // tell the persistencemanager that we're expecting this item to be removed
                            // from the global queue. this means that the item won't *automatically* be
                            // marked as "Failed: Disappeared from global queue" in the GUI when de-queued,
                            // and will instead be "stuck" in its latest state, ready for us to properly
                            // set its failure reason as "aborted due to MD5 match" instead.
                            dlItem.setInternalRemoveExpected(true);

                            // send a FCP message to remove the item from the persistent (global) queue
                            fcpTools.removeRequest(getReq.getIdentifier());

                            // lastly, start a thread which will update the state of the removed item
                            new Thread() {
                                @Override
                                public void run() {
                                    // NOTE/TODO: (slightly ugly) wait until the item has been de-queued from the
                                    // global queue. this wrapper thread waits for 1 second (without locking up
                                    // the GUI), so that the removal request above has a chance to execute and
                                    // so that the final "SimpleProgress" messages can be processed in time. after
                                    // waiting, it then starts a GUI update thread which sets the state to "FAILED".
                                    // this fixes a race condition where de-queuing the item would get it stuck in
                                    // a "Downloading" state but without any global queue item running anymore. it
                                    // happened whenever an FCP SimpleProgress message was waiting to be processed
                                    // at the exact moment that you de-queued the file... in that case, the delayed
                                    // progress msg incorrectly changed the status back to "in progress" again.
                                    Mixed.wait(1000);
                                    // we must perform the actual GUI updates on the GUI thread, so queue the update now
                                    SwingUtilities.invokeLater(new Runnable() {
                                        public void run() {
                                            // don't change the state to "Failed" if the item was already done
                                            if( dlItem.getState() != FrostDownloadItem.STATE_DONE ) {
                                                // now the fun part... display the MD5 failure reason in the GUI
                                                dlItem.setState(FrostDownloadItem.STATE_FAILED);
                                                dlItem.setErrorCodeDescription("[MD5] Exists on disk: " + localFilePath);
                                                // ensures that the file won't begin downloading again unless the user explicitly restarts it
                                                dlItem.setEnabled(false);
                                                dlItem.fireValueChanged();
                                            }
                                        }
                                    });
                                }
                            }.start();
                        }
                    }
                }
            } finally {
                // no matter WHAT happened above, we'll now mark the hash as "checked", so that
                // *this particular* MD5 is never checked again during *this* exact ClientGet request.
                // NOTE: the reason we store it on the "get request" and not on the dlItem itself,
                // is that the user must be able to enable or disable blocking and restart the item
                // and get a new hash-check on the new ClientGet-request.
                getReq.setHashChecked(true);
            }
        }
    }

    /**
     * Apply the states of FcpRequestPut to the FrostUploadItem.
     * This is called both for external (global queue) items, and for internal Frost items
     * we've added to the queue.
     * NOTE: In case of status updates for existing items, the caller must always re-use the initial
     * putReq object but with updated progress fields (all Frost code currently does that properly!).
     * This ensures that we can safely read values like "isDontCompress()" and "getCompatibilityMode()"
     * even during "SimpleProgress" updates, since the values of those fields were set properly in
     * the initial "PersistentPut" message that created the putReq object.
     */
    private void applyState(final FrostUploadItem ulItem, final FcpPersistentPut putReq) {

        // when cancelled and we expect this, don't set failed; don't even set the old priority!
        // NOTE: however, subsequent status updates (which only happen during uploads; not during
        // downloads) will pretty much instantly apply a "failed" state, but that's a good thing
        // since it ensures the upload won't go into a "waiting" state and restart itself
        if( ulItem.isInternalRemoveExpected() && putReq.isFailed() ) {
            final int returnCode = putReq.getCode();
            if( returnCode == 25 ) {
                return;
            }
        }

        if( directPUTsWithoutAnswer.contains(ulItem.getGqIdentifier()) ) {
            // we got an answer
            directPUTsWithoutAnswer.remove(ulItem.getGqIdentifier());
        }

        // apply externally changed priority
        if( ulItem.getPriority() != putReq.getPriority() ) {
            if (Core.frostSettings.getBoolValue(SettingsClass.FCP2_ENFORCE_FROST_PRIO_FILE_UPLOAD)) {
                // reset priority with our current value
                fcpTools.changeRequestPriority(putReq.getIdentifier(), ulItem.getPriority());
            } else {
                // apply to uploaditem
                ulItem.setPriority(putReq.getPriority());
            }
        }

        // if we don't already have a known key (such as a pre-calculated one) for this file, then
        // check if a key is known by the ongoing put-request. this only checks/sets a key if none
        // is available, so it's very optimized and only happens once during each upload, at the
        // "URIGenerated" stage (which is when the putReq object learns the key).
        // NOTE: we'll also read the final URI again when the file finishes ("isSuccess" further down).
        if( ulItem.getKey() == null ) {
            final String chkKey = putReq.getUri();
            if( chkKey != null ) {
                ulItem.setKey(chkKey);
            }
        }

        // update the compression state if it has changed
        // NOTE: if this is an internally queued item, the value of the ulItem's getCompress() will
        // already match the put-request's "isDontCompress" (as long as the Freenet node accepted
        // our value). if there's a mismatch between our beliefs and the actual put-request, we'll
        // update the internal state. for these reasons, this will only really be triggering for
        // externally queued items that have enabled compression; that's because the ulItem in
        // that case starts out with an assumption that DontCompress=true (aka compress=false),
        // since that's the Frost-Next default for uploads.
        if( ulItem.getCompress() != (!putReq.isDontCompress()) ) {
            // NOTE: we invert the value; because if the file has the DontCompress=true property,
            // it means compression is off (false), and vice versa
            ulItem.setCompress( !putReq.isDontCompress() );
        }
        
        // update the compatibility mode if it has changed
        // NOTE: the most common reason for triggering this is that the user started an upload
        // with "COMPAT_CURRENT", in which case the node returns the *actual* value that Freenet
        // ended up using (such as "COMPAT_1468"). the second most common reason for being called
        // is that this is an externally queued item, since their ulItem always starts out assuming
        // "COMPAT_CURRENT" as the compatibility mode. either way, we'll grab the actual
        // compatibility mode that Freenet used, so that the uploads table will always display
        // the real value! we will also *automatically learn* any new compatibility modes that
        // get added in future Freenet versions this way!
        // NOTE: if the mode claimed by the Freenet node failed validation, we'll set the mode
        // to "COMPAT_UNKNOWN" and never run this check again for that particular transfer.
        // NOTE: this is only for uploads. dynamic mode learning for *downloads* is done
        // in FcpPersistentQueue.java instead.
        final String currentCompatMode = ulItem.getFreenetCompatibilityMode();
        if( !currentCompatMode.equals("COMPAT_UNKNOWN") && !currentCompatMode.equals( putReq.getCompatibilityMode() ) ) {
            // this function creates a validated, clean version of the mode string,
            // and adds it to the mode manager if it's unknown
            final String activeCompatibilityMode = Core.getCompatManager().learnNewUserMode(putReq.getCompatibilityMode());
            if( activeCompatibilityMode != null ) {
                // the mode is valid and (now) exists in our compatibility mode manager;
                // so now just set the transfer to that mode!
                ulItem.setFreenetCompatibilityMode(activeCompatibilityMode);
            } else {
                // this mode was rejected -- either for being an empty string, or for not passing
                // the ASCII-only validation of the compatibility mode manager. this will never
                // happen with any normal Freenet node that sends healthy messages, so we will
                // silently ignore the failure and set the transfer to "COMPAT_UNKNOWN".
                ulItem.setFreenetCompatibilityMode("COMPAT_UNKNOWN");
            }
        }

        // update the crypto key if one was used for this upload; this is particularly important
        // for externally queued uploads, where we otherwise don't know if they used a crypto key.
        // NOTE: we don't need to validate the string, because setCryptoKey will refuse invalid ones.
        // NOTE: Freenet has a bug which sends two instant "PersistentPut" messages when an upload
        // is *first started*; only the 2nd message contains the key, so the first call briefly
        // clears the key, but it's so rapid that it's not even visible.
        final String cryptoKey = putReq.getSplitfileCryptoKey();
        if( cryptoKey == null ) { // clear key if none was used
            ulItem.clearCryptoKey();
        } else { // there is a key!
            // only update the crypto key if the new key differs from the old one; this prevents repeating
            // the "heavy" setCryptoKey validation code every time we update the progress of our uploads
            final String oldCryptoKey = ulItem.getCryptoKey();
            if( oldCryptoKey == null || !oldCryptoKey.equalsIgnoreCase(cryptoKey) ) {
                ulItem.setCryptoKey(cryptoKey);
            }
        }

        // now just process the current state of the upload
        if( !putReq.isProgressSet() && !putReq.isSuccess() && !putReq.isFailed() ) {
            if( ulItem.getState() == FrostUploadItem.STATE_WAITING ) {
                ulItem.setState(FrostUploadItem.STATE_PROGRESS);
            }
            return;
        }

        if( putReq.isProgressSet() ) {
            final int doneBlocks = putReq.getDoneBlocks();
            final int totalBlocks = putReq.getTotalBlocks();
            final boolean isFinalized = putReq.isFinalized();
            if( totalBlocks > 0 ) {
                ulItem.setDoneBlocks(doneBlocks);
                ulItem.setTotalBlocks(totalBlocks);
                ulItem.setFinalized(isFinalized);
                ulItem.fireValueChanged();
            }
            if( ulItem.getState() != FrostUploadItem.STATE_PROGRESS ) {
                ulItem.setState(FrostUploadItem.STATE_PROGRESS);
            }
        }
        if( putReq.isSuccess() ) {
            // maybe progress was not completely sent
            ulItem.setFinalized(true);
            if( ulItem.getTotalBlocks() > 0 && ulItem.getDoneBlocks() != ulItem.getTotalBlocks() ) {
                ulItem.setDoneBlocks(ulItem.getTotalBlocks());
            }
            final String chkKey = putReq.getUri();
            if( ulItem.isExternal() ) {
                ulItem.setState(FrostUploadItem.STATE_DONE);
                ulItem.setKey(chkKey);
            } else {
                final FcpResultPut result = new FcpResultPut(FcpResultPut.Success, chkKey);
                FileTransferManager.inst().getUploadManager().notifyUploadFinished(ulItem, result);
            }
        }
        if( putReq.isFailed() ) {
            final String desc = putReq.getCodeDesc();
            if( ulItem.isExternal() ) {
                ulItem.setState(FrostUploadItem.STATE_FAILED);
                ulItem.setErrorCodeDescription(desc);
            } else {
                final int returnCode = putReq.getCode();
                final boolean isFatal = putReq.isFatal();

                final FcpResultPut result;
                if( returnCode == 9 ) {
                    result = new FcpResultPut(FcpResultPut.KeyCollision, returnCode, desc, isFatal);
                } else if( returnCode == 5 ) {
                    result = new FcpResultPut(FcpResultPut.Retry, returnCode, desc, isFatal);
                } else {
                    result = new FcpResultPut(FcpResultPut.Error, returnCode, desc, isFatal);
                }
                FileTransferManager.inst().getUploadManager().notifyUploadFinished(ulItem, result);
            }
        }
    }

    private void startNewUploads() {
        // if we're not connected then don't even try to start a new upload!
        if( ! Core.isFreenetOnline() ) {
            return;
        }

        boolean isLimited = true;
        int currentAllowedUploadCount = 0;
        {
            final int allowedConcurrentUploads = Core.frostSettings.getIntValue(SettingsClass.UPLOAD_MAX_THREADS);
            if( allowedConcurrentUploads <= 0 ) {
                isLimited = false;
            } else {
                int runningUploads = 0;
                for(final FrostUploadItem ulItem : uploadModelItems.values() ) {
                    if( !ulItem.isExternal() && ulItem.getState() == FrostUploadItem.STATE_PROGRESS) {
                        runningUploads++;
                    }
                }
                currentAllowedUploadCount = allowedConcurrentUploads - runningUploads;
                if( currentAllowedUploadCount < 0 ) {
                    currentAllowedUploadCount = 0;
                }
            }
        }
        {
            while( !isLimited || currentAllowedUploadCount > 0 ) {
                final FrostUploadItem ulItem = FileTransferManager.inst().getUploadManager().selectNextUploadItem();
                if( ulItem == null ) {
                    break;
                }
                if( startUpload(ulItem) ) {
                    currentAllowedUploadCount--;
                }
            }
        }
    }

    /**
     * Performs an upload using the global, persistent queue in Freenet. That queue is enabled
     * by default in Frost.
     * If the user has disabled the persistent queue, then the upload is handled
     * by UploadTicker.java:startUpload() instead.
     *
     * NOTE: The "synchronized" attribute ensures that the user's "start selected uploads now" and
     * "restart selected uploads" features don't trigger startUpload multiple times for the same
     * file (autostart thread+the menu item). They'll still trigger, but they'll all execute in
     * series thanks to the synchronization, which means that the first execution takes care of
     * setting the state for the file. the additional calls will then see the new state and won't
     * waste any time trying to upload the same file again.
     *
     * @param {FrostUploadItem} ulItem - the model item to begin uploading
     * @return - false if the upload cannot start (such as if the item is missing,
     * or isn't a waiting item, etc), true if the upload began
     */
    public synchronized boolean startUpload(final FrostUploadItem ulItem) {
        if( !Core.isFreenetOnline() ) {
            return false;
        }
        if( ulItem == null || ulItem.isExternal() || ulItem.getState() != FrostUploadItem.STATE_WAITING ) {
            return false;
        }

        // now make sure the file still exists and hasn't changed size;
        // if so, then notify the user, mark the file as failed, and skip this upload
        final ArrayList<FrostUploadItem> itemsToCheck = new ArrayList<FrostUploadItem>();
        itemsToCheck.add(ulItem);
        uploadModel.setMissingFilesToFailedAndNotifyUser(itemsToCheck);
        if( ulItem.getState() != FrostUploadItem.STATE_WAITING ) {
            return false;
        }

        // start the upload
        ulItem.setUploadStartedMillis(System.currentTimeMillis());
        ulItem.setState(FrostUploadItem.STATE_PROGRESS);
        final boolean doMime;
        final boolean setTargetFileName;
        if( ulItem.isSharedFile() ) {
            // shared files are always inserted as octet-stream
            doMime = false;
            setTargetFileName = false;
        } else {
            doMime = true;
            setTargetFileName = true;
        }

        // try to start using DDA
        boolean isDda = fcpTools.startPersistentPutUsingDda(
                ulItem.getGqIdentifier(),
                ulItem.getFile(),
                ulItem.getFileName(),
                doMime,
                setTargetFileName,
                ulItem.getCompress(),
                ulItem.getFreenetCompatibilityMode(),
                ulItem.getCryptoKey(),
                ulItem.getPriority()
                );

        if( !isDda ) {
            // upload was not started because DDA is not allowed...
            // if UploadManager selected this file then it is not already in progress!
            directTransferQueue.appendItemToQueue(ulItem);
        }
        return true;
    }

    private void startNewDownloads() {
        // if downloading is paused or we're not connected then don't even try to start a new download!
        if( ! Core.frostSettings.getBoolValue(SettingsClass.DOWNLOADING_ACTIVATED) ||
            ! Core.isFreenetOnline() ) {
            return;
        }

        boolean isLimited = true;
        int currentAllowedDownloadCount = 0;
        {
            final int allowedConcurrentDownloads = Core.frostSettings.getIntValue(SettingsClass.DOWNLOAD_MAX_THREADS);
            if( allowedConcurrentDownloads <= 0 ) {
                isLimited = false;
            } else {
                int runningDownloads = 0;
                for(final FrostDownloadItem dlItem : downloadModelItems.values() ) {
                    if( !dlItem.isExternal() && dlItem.getState() == FrostDownloadItem.STATE_PROGRESS) {
                        runningDownloads++;
                    }
                }
                currentAllowedDownloadCount = allowedConcurrentDownloads - runningDownloads;
                if( currentAllowedDownloadCount < 0 ) {
                    currentAllowedDownloadCount = 0;
                }
            }
        }
        {
            while( !isLimited || currentAllowedDownloadCount > 0 ) {
                final FrostDownloadItem dlItem = FileTransferManager.inst().getDownloadManager().selectNextDownloadItem();
                if (dlItem == null) {
                    break;
                }
                // start the download
                if( startDownload(dlItem) ) {
                    currentAllowedDownloadCount--;
                }
            }
        }
    }

    /**
     * Performs a download using the global, persistent queue in Freenet. That queue is enabled
     * by default in Frost.
     * If the user has disabled the persistent queue, then the download is handled
     * by DownloadTicker.java:startDownload() instead.
     *
     * NOTE: The "synchronized" attribute ensures that the user's "start selected downloads now" and
     * "restart selected downloads" features don't trigger startDownload multiple times for the same
     * file (autostart thread+the menu item). They'll still trigger, but they'll all execute in
     * series thanks to the synchronization, which means that the first execution takes care of
     * setting the state for the file. the additional calls will then see the new state and won't
     * waste any time trying to download the same file again.
     *
     * @param {FrostDownloadItem} ulItem - the model item to begin downloading
     * @return - false if the download cannot start (such as if the item is missing,
     * or isn't a waiting item, etc), true if the download began
     */
    public synchronized boolean startDownload(final FrostDownloadItem dlItem) {
        if( ! Core.frostSettings.getBoolValue(SettingsClass.DOWNLOADING_ACTIVATED) ||
            ! Core.isFreenetOnline() ) {
            return false;
        }

        if( dlItem == null || dlItem.getState() != FrostDownloadItem.STATE_WAITING ) {
            return false;
        }

        dlItem.setDownloadStartedTime(System.currentTimeMillis());

        dlItem.setState(FrostDownloadItem.STATE_PROGRESS);

        final String gqid = dlItem.getGqIdentifier();
        final File targetFile = new File(dlItem.getDownloadFilename());
        // tell Freenet's global queue to start downloading the file
        // (it will notify us via FCP when it's done)
        boolean isDda = fcpTools.startPersistentGet(
                dlItem.getKey(),
                gqid,
                targetFile,
                dlItem.getPriority()
        );
        dlItem.setDirect( !isDda );

        return true;
    }

    private void showExternalUploadItems() {
        final Map<String,FcpPersistentPut> items = persistentQueue.getUploadRequests();
        for(final FcpPersistentPut uploadRequest : items.values() ) {
            if( !uploadModelItems.containsKey(uploadRequest.getIdentifier()) ) {
                addExternalItem(uploadRequest); // this will take care of also calling applyState()
            }
        }
    }

    private void showExternalDownloadItems() {
        final Map<String,FcpPersistentGet> items = persistentQueue.getDownloadRequests();
        for(final FcpPersistentGet downloadRequest : items.values() ) {
            if( !downloadModelItems.containsKey(downloadRequest.getIdentifier()) ) {
                addExternalItem(downloadRequest); // this will take care of also calling applyState()
            }
        }
    }

    // this function is only called *once* for global-queue items, at the moment their information
    // is received via fcp for the first time. after that it only happens each time the user
    // toggles the "show global uploads" checkbox on/off. it is never called during the ordinary
    // status updates for ongoing items.
    private void addExternalItem(final FcpPersistentPut uploadRequest) {
        // NOTE: these are upload queue items added outside of Frost, but thanks to major improvements
        // to the fcp parser we can now read the "DontCompress", "CompatibilityMode" and "SplitfileCryptoKey"
        // properties to determine if compression was enabled or not, as well as what compatibility
        // mode was used. you obviously can't switch the compression setting for an ongoing upload,
        // but there *are* two situations where we *do* need the real value: first off, it ensures
        // that we can always display the newly added item's compression on/off state and compatibility
        // mode columns in the new upload table view. secondly, the "copy extended information" menu
        // item uses the information to output the compatibility mode and compression setting that
        // was used. these new compression/compatmode settings are parsed in the "applyState" function.
        final FrostUploadItem ulItem = new FrostUploadItem();

        ulItem.setGqIdentifier(uploadRequest.getIdentifier());
        ulItem.setExternal(true);
        // direct uploads might not have a filename, use identifier in that case
        String fileName = uploadRequest.getFilename();
        if( fileName == null ) {
            fileName = uploadRequest.getIdentifier();
        } else if( fileName.indexOf('/') > -1 || fileName.indexOf('\\') > -1 ) {
            // filename contains directories, use only filename
            final String stmp = new File(fileName).getName();
            if( stmp.length() > 0 ) {
                fileName = stmp; // use plain filename
            }
        }
        ulItem.setFile(new File(fileName));
        ulItem.setFileName(fileName);
        ulItem.setFileSize(uploadRequest.getFileSize());
        ulItem.setPriority(uploadRequest.getPriority());
        
        // initialize to "in progress" ("Uploading"); will be fixed to actual state in applyState()
        ulItem.setState(FrostUploadItem.STATE_PROGRESS);

        // now tell the GUI thread to add the item to the uploads table; wait until it's complete.
        // this stalls our caller (the FCP message processor) so that it waits until the table item
        // has been added before it processes any further FCP messages. in the past, Frost used to
        // update the GUI via "invokeLater" here, which resulted in a serious race condition; the
        // initial FCP message of the existence of the upload would be processed, and its addition
        // to the table would be queued in an asynchronous GUI thread.
        // meanwhile, the "upload status/upload complete" messages would be rapidly coming in via
        // FCP (it sends the "status update" instantly after the initial message) and call an "update"
        // function with the news; that function, in turn, would see that the upload wasn't yet in
        // the uploads table (thanks to the slow asynchronous thread), which meant that it thought
        // it was an untracked upload, and therefore ignored the status update message. the result
        // was that if you started Frost with the recently fixed "show global uploads" checkboxes,
        // then they would forever show the items stuck in an "Uploading..." state, instead of properly
        // showing "Finished". so instead, we simply tell the GUI to update instantly and *wait* for
        // the update to finish before returning control back to the FCP parser. that takes care of
        // the race condition. this note is here to remind future readers to always check their sequence
        // of events and to make sure the GUI is updated synchronously when required!
        // NOTE: if this is running in the GUI thread (such as when the user clicks the "show global
        // uploads" checkbox to re-add them to the table), we'll need to instantly execute the
        // function (since we can't start a synchronous thread from the GUI thread).
        try {
            Mixed.invokeNowInDispatchThread(new Runnable() {
                public void run() {
                    uploadModel.addExternalItem(ulItem); // add to model
                }
            });
        } catch( Exception e ) {
            return; // abort if there was an error adding the item
        }

        applyState(ulItem, uploadRequest); // apply the currently known state
    }

    // this function is only called *once* for global-queue items, at the moment their information
    // is received via fcp for the first time. after that it only happens each time the user toggles
    // the "show global downloads" checkbox on/off. it is never called during the ordinary status
    // updates for ongoing items.
    private void addExternalItem(final FcpPersistentGet downloadRequest) {
        // direct downloads might not have a filename, use identifier in that case
        String fileName = downloadRequest.getFilename();
        if( fileName == null ) {
            fileName = downloadRequest.getIdentifier();
        } else if( fileName.indexOf('/') > -1 || fileName.indexOf('\\') > -1 ) {
            // filename contains directories, use only filename
            final String stmp = new File(fileName).getName();
            if( stmp.length() > 0 ) {
                fileName = stmp; // use plain filename
            }
        }
        final FrostDownloadItem dlItem = new FrostDownloadItem(
                fileName,
                downloadRequest.getUri());
        dlItem.setExternal(true);
        dlItem.setGqIdentifier(downloadRequest.getIdentifier());

        // initialize to "in progress" ("Downloading"); will be fixed to actual state in applyState()
        dlItem.setState(FrostDownloadItem.STATE_PROGRESS);

        // instantly add the download item to the model and file table, to solve the exact
        // same race condition described in detail in the "uploads" addExternalItem() above
        try {
            Mixed.invokeNowInDispatchThread(new Runnable() {
                public void run() {
                    downloadModel.addExternalItem(dlItem); // add to model
                }
            });
        } catch( Exception e ) {
            return; // abort if there was an error adding the item
        }

        applyState(dlItem, downloadRequest); // apply the currently known state
    }

    public boolean isDirectTransferInProgress(final FrostDownloadItem dlItem) {
        final String id = dlItem.getGqIdentifier();
        return directGETsInProgress.contains(id);
    }

    public boolean isDirectTransferInProgress(final FrostUploadItem ulItem) {
        final String id = ulItem.getGqIdentifier();
        if( directPUTsInProgress.contains(id) ) {
            return true;
        }
        if( directPUTsWithoutAnswer.contains(id) ) {
            return true;
        }
        return false;
    }

    private class DirectTransferThread extends Thread {

        @Override
        public void run() {

            final int maxAllowedExceptions = 5;
            int catchedExceptions = 0;

            while(true) {
                try {
                    // if there is no work in queue this call waits for a new queue item
                    final ModelItem<?> item = directTransferQueue.getItemFromQueue();

                    if( item == null ) {
                        // paranoia, should never happen
                        Mixed.wait(5*1000);
                        continue;
                    }

                    if( item instanceof FrostUploadItem ) {
                        // transfer bytes to node
                        final FrostUploadItem ulItem = (FrostUploadItem) item;
                        // FIXME: provide item, state=Transfer to node, % shows progress
                        final String gqid = ulItem.getGqIdentifier();
                        final boolean doMime;
                        final boolean setTargetFileName;
                        if( ulItem.isSharedFile() ) {
                            doMime = false;
                            setTargetFileName = false;
                        } else {
                            doMime = true;
                            setTargetFileName = true;
                        }
                        final NodeMessage answer = fcpTools.startDirectPersistentPut(
                                gqid,
                                ulItem.getFile(),
                                // NOTE:XXX: this is identical to the behavior of source/frost/fcp/fcp07/FcpConnection.java,
                                // which is used for non-persistent uploads and for pre-generating CHKs. for consistent key
                                // generation, both methods apply the user's provided filename as opposed to the on-disk name.
                                ulItem.getFileName(),
                                doMime,
                                setTargetFileName,
                                ulItem.getCompress(),
                                ulItem.getFreenetCompatibilityMode(),
                                ulItem.getCryptoKey(),
                                ulItem.getPriority()
                                );
                        if( answer == null ) {
                            final String desc = "Could not open a new FCP2 socket for direct persistent put!";
                            final FcpResultPut result = new FcpResultPut(FcpResultPut.Error, -1, desc, false);
                            FileTransferManager.inst().getUploadManager().notifyUploadFinished(ulItem, result);

                            logger.severe(desc);
                        } else {
                            // wait for an answer, don't start request again
                            directPUTsWithoutAnswer.add(gqid);
                        }

                        directPUTsInProgress.remove(gqid);

                    } else if( item instanceof FrostDownloadItem ) {
                        // the DIRECT (non-DDA) persistent get is complete; now attempt
                        // to transfer the bytes for this gqid from node
                        final FrostDownloadItem dlItem = (FrostDownloadItem) item;
                        // FIXME: provide item, state=Transfer from node, % shows progress
                        final String gqid = dlItem.getGqIdentifier();
                        final File targetFile = new File(dlItem.getDownloadFilename());

                        final boolean retryNow;
                        NodeMessage answer = null;

                        try {
                            // NOTE: this is the file-writer used by all persistently queued (global queue)
                            // downloads. if persistence is disabled in Frost, it uses DownloadThread.java instead
                            answer = fcpTools.startDirectPersistentGet(gqid, targetFile);
                        } catch( final FcpNoAnswerException e ) {
                            final String msg = "Freenet socket timed out while requesting '" + dlItem.getDownloadFilename() + "': " + e.getMessage();
                            System.out.println(msg);
                            logger.severe(msg);
                        } catch( final FileNotFoundException e ) {
                            final String msg = "Could not write to '" + dlItem.getDownloadFilename() + "': " + e.getMessage();
                            System.out.println(msg);
                            logger.severe(msg);
                        }

                        if( answer != null ) {
                            final FcpResultGet result = new FcpResultGet(true);
                            FileTransferManager.inst().getDownloadManager().notifyDownloadFinished(dlItem, result, targetFile);
                            retryNow = false;
                        } else {
                            logger.severe("Could not open a new FCP2 socket for direct persistent get, or failed to write file!");
                            final FcpResultGet result = new FcpResultGet(false);
                            retryNow = FileTransferManager.inst().getDownloadManager().notifyDownloadFinished(dlItem, result, targetFile);
                        }

                        directGETsInProgress.remove(gqid);

                        if( retryNow ) {
                            startDownload(dlItem);
                        }
                    }

                } catch(final Throwable t) {
                    logger.log(Level.SEVERE, "Exception catched",t);
                    catchedExceptions++;
                }

                if( catchedExceptions > maxAllowedExceptions ) {
                    logger.log(Level.SEVERE, "Stopping DirectTransferThread because of too much exceptions");
                    break;
                }
            }
        }
    }

    /**
     * A queue class that queues items waiting for its direct transfer (put to node or get from node).
     */
    private class DirectTransferQueue {

        private final LinkedList<ModelItem<?>> queue = new LinkedList<ModelItem<?>>();

        public synchronized ModelItem<?> getItemFromQueue() {
            try {
                // let dequeueing threads wait for work
                while( queue.isEmpty() ) {
                    wait();
                }
            } catch (final InterruptedException e) {
                return null; // waiting abandoned
            }

            if( queue.isEmpty() == false ) {
                return queue.removeFirst();
            }
            return null;
        }

        public synchronized void appendItemToQueue(final FrostDownloadItem item) {
            final String id = item.getGqIdentifier();
            directGETsInProgress.add(id);

            queue.addLast(item);
            notifyAll(); // notify all waiters (if any) of new record
        }

        public synchronized void appendItemToQueue(final FrostUploadItem item) {
            final String id = item.getGqIdentifier();
            directPUTsInProgress.add(id);

            queue.addLast(item);
            notifyAll(); // notify all waiters (if any) of new record
        }

    }

    public void persistentRequestError(final String id, final NodeMessage nm) {
        if( uploadModelItems.containsKey(id) ) {
            final FrostUploadItem item = uploadModelItems.get(id);
            item.setEnabled(false);
            item.setState(FrostUploadItem.STATE_FAILED);
            item.setErrorCodeDescription(nm.getStringValue("CodeDescription"));
        } else if( downloadModelItems.containsKey(id) ) {
            final FrostDownloadItem item = downloadModelItems.get(id);
            item.setEnabled(false);
            item.setState(FrostDownloadItem.STATE_FAILED);
            item.setErrorCodeDescription(nm.getStringValue("CodeDescription"));
        } else {
            System.out.println("persistentRequestError: ID not in any model: "+id);
        }
    }

    public void persistentRequestAdded(final FcpPersistentPut uploadRequest) {
        final FrostUploadItem ulItem = uploadModelItems.get(uploadRequest.getIdentifier()); // try to find the item in our list of known items
        if( ulItem != null ) {
            // own item became added to global queue, or this was an already known external item
            applyState(ulItem, uploadRequest);
        } else {
            // this was an unknown external item; begin tracking it if the user wants to see global uploads
            if( showExternalItemsUpload ) {
                addExternalItem(uploadRequest); // this will take care of also calling applyState()
            }
        }
    }

    public void persistentRequestAdded(final FcpPersistentGet downloadRequest) {
        final FrostDownloadItem dlItem = downloadModelItems.get(downloadRequest.getIdentifier()); // try to find the item in our list of known items
        if( dlItem != null ) {
            // own item became added to global queue, or this was an already known external item
            applyState(dlItem, downloadRequest);
        } else {
            // this was an unknown external item; begin tracking it if the user wants to see global downloads
            if ( showExternalItemsDownload ) {
                addExternalItem(downloadRequest); // this will take care of also calling applyState()
            }
        }
    }

    public void persistentRequestModified(final FcpPersistentPut uploadRequest) {
        if( uploadModelItems.containsKey(uploadRequest.getIdentifier()) ) {
            final FrostUploadItem ulItem = uploadModelItems.get(uploadRequest.getIdentifier());
            ulItem.setPriority(uploadRequest.getPriority());
        }
    }

    public void persistentRequestModified(final FcpPersistentGet downloadRequest) {
        if( downloadModelItems.containsKey(downloadRequest.getIdentifier()) ) {
            final FrostDownloadItem dlItem = downloadModelItems.get(downloadRequest.getIdentifier());
            applyPriority(dlItem, downloadRequest);
        }
    }

    public void persistentRequestRemoved(final FcpPersistentPut uploadRequest) {
        if( uploadModelItems.containsKey(uploadRequest.getIdentifier()) ) {
            final FrostUploadItem ulItem = uploadModelItems.get(uploadRequest.getIdentifier());
            if( ulItem.isExternal() ) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                    	List<FrostUploadItem> itemList = new ArrayList<FrostUploadItem>();
                    	itemList.add(ulItem);
                        uploadModel.removeItems(itemList);
                    }
                });
            } else {
                if( ulItem.isInternalRemoveExpected() ) {
                    if( !ulItem.isInternalRemoveExpectedThreadStarted() ) {
                        // NOTE/TODO: (very ugly) see persistentRequestRemoved(downloadRequest)
                        // below for why this is so ugly... but it's necessary...
                        new Thread() {
                            public void run() {
                                Mixed.wait(400);
                                ulItem.setInternalRemoveExpected(false); // clear flag
                                ulItem.setInternalRemoveExpectedThreadStarted(false);
                            }
                        }.start();
                        ulItem.setInternalRemoveExpectedThreadStarted(true);
                    }
                } else if( ulItem.getState() != FrostUploadItem.STATE_DONE ) {
                    ulItem.setEnabled(false);
                    ulItem.setState(FrostUploadItem.STATE_FAILED);
                    ulItem.setErrorCodeDescription("Disappeared from global queue.");
                }
            }
        }
    }

    public void persistentRequestRemoved(final FcpPersistentGet downloadRequest) {
        if( downloadModelItems.containsKey(downloadRequest.getIdentifier()) ) {
            final FrostDownloadItem dlItem = downloadModelItems.get(downloadRequest.getIdentifier());
            if( dlItem.isExternal() ) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                    	List<FrostDownloadItem> itemList = new ArrayList<FrostDownloadItem>();
                    	itemList.add(dlItem);
                        downloadModel.removeItems(itemList);
                    }
                });
            } else {
                if( dlItem.isInternalRemoveExpected() ) {
                    if( !dlItem.isInternalRemoveExpectedThreadStarted() ) {
                        // NOTE/TODO: (very ugly) we need to wait a short moment before clearing the
                        // flag, because further SimpleProgress messages can be waiting in the pipeline,
                        // and would mark it as "Disappeared from global queue" inadvertently.
                        // we only wait for 400ms, to ensure that we wait for a shorter time than the
                        // usual 1000ms used by various re-queue attempts, etc.
                        // this is a bit ugly (especially the fixed-length timing), but it's necessary
                        // because of Frost's awfully rigid spaghetti-code. at least we ensure we only
                        // start a single state-switch thread per missing item, to save some CPU...
                        new Thread() {
                            public void run() {
                                Mixed.wait(400);
                                dlItem.setInternalRemoveExpected(false); // clear flag
                                dlItem.setInternalRemoveExpectedThreadStarted(false);
                            }
                        }.start();
                        dlItem.setInternalRemoveExpectedThreadStarted(true);
                    }
                } else if( dlItem.getState() != FrostDownloadItem.STATE_DONE ) {
                    dlItem.setEnabled(false);
                    dlItem.setState(FrostDownloadItem.STATE_FAILED);
                    dlItem.setErrorCodeDescription("Disappeared from global queue.");
                }
            }
        }
    }

    public void persistentRequestUpdated(final FcpPersistentPut uploadRequest) {
        final FrostUploadItem ui = uploadModelItems.get(uploadRequest.getIdentifier());
        if( ui == null ) {
            // NOTE: this is a status update for an upload that's not tracked by our model, which
            // means it's an external request (added outside of Frost) and that it wasn't tracked
            // by us for one of two reasons: either A) because the user has turned off display of
            // external uploads, or B) because Freenet hasn't sent us any detailed information
            // about the upload yet (that should never be able to happen, since Freenet always
            // sends the details first and *then* the update event, but it's perhaps possible
            // that Freenet could malfunction in that regard).
            // we will simply ignore this update event. but note that the update is still tracked
            // by the underlying FCP queue, so the user will instantly see the updated state
            // if they choose to re-enable display of external uploads
            return;
        }
        // we're tracking this request, so update the model and file table with the latest status
        applyState(ui, uploadRequest);
    }

    public void persistentRequestUpdated(final FcpPersistentGet downloadRequest) {
        final FrostDownloadItem dl = downloadModelItems.get( downloadRequest.getIdentifier() );
        if( dl == null ) {
            // NOTE: this is a status update for a download that's not tracked by our model, which
            // means it's an external request that's either ignored by the user or we received a malformed
            // message from Freenet (in other words, the same note as "uploadRequest" above applies here)
            return; // ignore the update event; same note as "uploadRequest" above applies here
        }
        // we're tracking this request, so update the model and file table with the latest status
        applyState(dl, downloadRequest);
    }
}
