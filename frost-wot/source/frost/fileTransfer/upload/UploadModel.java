/*
  UploadModel.java / Frost
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

import java.util.*;
import java.util.logging.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;

import javax.swing.*;

import frost.*;
import frost.fileTransfer.*;
import frost.fileTransfer.sharing.*;
import frost.storage.*;
import frost.storage.perst.*;
import frost.util.gui.translation.*;
import frost.util.gui.MiscToolkit;
import frost.util.model.*;
import frost.util.Mixed;

/**
 * This is the model that stores all FrostUploadItems.
 *
 * Its implementation is thread-safe (subclasses should synchronize against
 * protected attribute data when necessary). It is also assumed that the load
 * and save methods will not be used while other threads are under way.
 */
public class UploadModel extends SortedModel<FrostUploadItem> implements ExitSavable {

    private static final Logger logger = Logger.getLogger(UploadModel.class.getName());

    public UploadModel(final SortedTableFormat<FrostUploadItem> f) {
        super(f);
    }

    public boolean addNewUploadItemFromSharedFile(final FrostSharedFileItem sfi) {
        // NOTE: we always disable upload-compression for files added via the "My shared files" tab,
        // since shared files are mostly multimedia which is already compressed. however, this doesn't
        // really matter either way since filesharing is gone in Frost-Next, since nobody used it.
        final FrostUploadItem newUlItem = new FrostUploadItem(sfi.getFile(), false);
        newUlItem.setSharedFileItem(sfi);
        return addNewUploadItem(newUlItem);
    }

    public void addExternalItem(final FrostUploadItem i) {
        addItem(i);
    }

    /**
     * Will add this item to the model if not already in the model.
     * The new item must only have 1 FrostUploadItemOwnerBoard in its list.
     * @return - true if the item was added, false if it was not added because it was a duplicate
     * of an existing upload (checked via the file's disk-path).
     */
    public synchronized boolean addNewUploadItem(final FrostUploadItem itemToAdd) {

        final String pathToAdd = itemToAdd.getFile().getPath();

        for (int x = 0; x < getItemCount(); x++) {
            final FrostUploadItem item = getItemAt(x);
            // add if file is not already in list (path)
            // if we add a shared file and the same file is already in list (manually added), we connect them
            if( pathToAdd.equals(item.getFile().getPath()) ) {
                // file with same path is already in list
                if( itemToAdd.isSharedFile() && !item.isSharedFile() ) {
                    // to shared file to manually added upload item
                    item.setSharedFileItem( itemToAdd.getSharedFileItem() );
                    return true;
                } else {
                    return false; // don't add 2 files with same path
                }
            }
        }
        // not in model, add
        addItem(itemToAdd);
        return true;
    }
    
    
    /**
     * Adds multiple items to the upload model. Displays a combined error message in case
     * one or more of the files already existed in the queue.
     * NOTE: This is currently only called from the "Add new uploads" dialog.
     */
    public void addUploadItemList(List<FrostUploadItem> itemsToAddList) {
        ArrayList<FrostUploadItem> failedList = new ArrayList<FrostUploadItem>();

        // attempt to add every new item
        for( final FrostUploadItem ulItem : itemsToAddList ) {
            final boolean success = this.addNewUploadItem(ulItem);
            // keep track of items that already existed in queue
            if( !success ) {
                failedList.add(ulItem);
            }
        }

        // display a message regarding any files that were already queued
        if( failedList.size() > 0 ) {
            showAlreadyQueuedMultiDialog(failedList);
        }
    }

    /**
     * Displays a compound list of the paths of failed upload items.
     */
    private void showAlreadyQueuedMultiDialog(final List<FrostUploadItem> failedList) {
        if( failedList == null || failedList.size() == 0 ) { return; }

        // chunk the input into groups of 10 files at a time (to not overwhelm the user)
        final int partitionSize = 10;
        final List<List<FrostUploadItem>> partitions = new LinkedList<List<FrostUploadItem>>();
        for( int i=0; i < failedList.size(); i += partitionSize ) {
            partitions.add(failedList.subList(i,
                Math.min(i + partitionSize, failedList.size()) // to end of partition or end of original list, whichever is smaller
            ));
        }

        // now display individual error messages for each chunk of files
        final Language language = Language.getInstance();
        int currentChunk = 0;
        for( final List<FrostUploadItem> failedChunk : partitions ) {
            ++currentChunk;

            // build a newline-separated string of all filenames in this chunk
            final StringBuilder sb = new StringBuilder();
            for( final FrostUploadItem ulItem : failedChunk ) {
                sb.append(ulItem.getFile().getPath()).append("\n");
            }
            final String thisChunkStr = sb.toString().trim(); // remove trailing newline

            // now just display the message
            MiscToolkit.showMessageDialog(
                    null, // parented to the main Frost window
                    language.formatMessage("UploadPane.alreadyQueuedMultiDialog.body",
                        currentChunk, // "1"
                        partitions.size(), // "2"
                        thisChunkStr), // "[keys]"
                    language.getString("UploadPane.alreadyQueuedMultiDialog.title"),
                    MiscToolkit.ERROR_MESSAGE);
        }
    }

    /**
     * Will add this item to the model, no check for dups.
     * NOTE: This is only used by the (removed/killed) filesharing feature which is no longer in Frost-Next.
     */
    private synchronized void addConsistentUploadItem(final FrostUploadItem itemToAdd) {
        addItem(itemToAdd);
    }

    /**
     * if upload was successful, remove item from uploadtable
     */
    public void notifySharedFileUploadWasSuccessful(final FrostUploadItem frostUploadItemToRemove) {
        for (int i = getItemCount() - 1; i >= 0; i--) {
            
        	final FrostUploadItem ulItem =  getItemAt(i);

            if( ulItem == frostUploadItemToRemove ) {
            	// remove this item
            	final List<FrostUploadItem> frostUploadiItems = new ArrayList<FrostUploadItem>();
            	frostUploadiItems.add(frostUploadItemToRemove);
                removeItems(frostUploadiItems );
                break;
            }
        }
    }

    /**
     * This method marks as "failed" all upload items whose associated files are missing
     * or have changed size, but only if the files are in a waiting/pre-encoding
     * state. If the files are already finished, failed or in progress, it
     * doesn't care about the original files on disk anymore, since Freenet
     * has already received the file data for uploading.
     *
     * It also notifies the user about the files, so that they have a chance to react.
     *
     * It is optimized to only do a full check of items that are waiting,
     * but it still loops through every item in the model, so you shouldn't
     * call this function too often.
     *
     * Fully thread-safe (via running in EDT), so that multiple invocations will always run in series.
     */
    public void setMissingFilesToFailedAndNotifyUser(final ArrayList<FrostUploadItem> itemsToCheck) {
        // if we're not running in the AWT GUI thread, then execute there instead to remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                // if they did not provide a list of items to check, we'll check all of them
                final ArrayList<FrostUploadItem> missingItems = new ArrayList<FrostUploadItem>();
                if( itemsToCheck == null ) {
                    for( int i = getItemCount() - 1; i >= 0; i-- ) {
                        final FrostUploadItem ulItem = getItemAt(i);

                        final int uploadValidity = checkPendingUploadValidityAndNotifyUser(ulItem);
                        if( uploadValidity > 0 ) {
                            missingItems.add(ulItem);
                        }
                    }
                } else {
                    // check the provided list to see if they're missing, and notify the user about any errors
                    for( final FrostUploadItem ulItem : itemsToCheck ) {
                        final int uploadValidity = checkPendingUploadValidityAndNotifyUser(ulItem);
                        if( uploadValidity > 0 ) {
                            missingItems.add(ulItem);
                        }
                    }
                }

                // set all missing items to "failed" and disable them
                if( !missingItems.isEmpty() ) {
                    for( final FrostUploadItem ulItem : missingItems ) {
                        ulItem.setState(FrostUploadItem.STATE_FAILED);
                        ulItem.setErrorCodeDescription("Original file is missing.");
                        ulItem.setEnabled(false); // ensures that the file won't try uploading again unless
                                                  // the user explicitly restarts it (they can do that if they
                                                  // put the original file back).
                        ulItem.fireValueChanged();
                    }
                }
            }
        });
    }

    /**
     * Checks the given FrostUploadItem to make sure the underlying file still exists on disk,
     * and that the filesize is still identical. It only checks files that are waiting for upload
     * or waiting to be pre-encoded. All other files get an automatic "pass" so that they
     * don't annoy the user, since it's common to upload a few files and then delete the originals
     * when the upload is in progress. We only need the files to exist until the moment the upload begins.
     * @param {FrostUploadItem} ulItem - the upload item to check
     * @return -1 if file is valid, 1 if file is missing, 2 if file has changed size
     */
    private static int checkPendingUploadValidity(final FrostUploadItem ulItem) {
        // externally queued items don't matter to us since we don't have the file anyway
        if( ulItem.isExternal() ) {
            return -1;
        }

        // we do not care about checking files that are already finished, uploading or failed
        if( ulItem.getState() == FrostUploadItem.STATE_DONE // finished
         || ulItem.getState() == FrostUploadItem.STATE_PROGRESS // upload in progress
         || ulItem.getState() == FrostUploadItem.STATE_FAILED
         || ulItem.getState() == FrostUploadItem.STATE_ENCODING ) { // encoding in progress
            return -1;
        }

        // explicitly only check items that are waiting to be uploaded, or that are waiting to be pre-encoded
        if( ulItem.getState() == FrostUploadItem.STATE_WAITING // upload hasn't started yet
         || ulItem.getState() == FrostUploadItem.STATE_ENCODING_REQUESTED ) { // encoding hasn't started yet
            if( !ulItem.getFile().exists() ) {
                return 1; // file is missing
            }
            else if( ulItem.getFileSize() != ulItem.getFile().length() ) {
                return 2; // file has changed size on disk
            }
        }

        // any other file states (which is impossible, since the above covers all of them) will just return "no problems"
        return -1;
    }

    /**
     * Helper function which does exactly the same thing as the regular checkPendingUploadValidity(),
     * but also displays a message to the user and logs the error. If you use this function, you
     * must remain consistent and actually do what the message says: Mark the item as failed
     * in the uploads table if the return value is > 0.
     */
    private static int checkPendingUploadValidityAndNotifyUser(final FrostUploadItem ulItem) {
        final int uploadValidity = checkPendingUploadValidity(ulItem);
        if( uploadValidity == 1 ) {
            final Language language = Language.getInstance();
            MiscToolkit.showMessageDialog(
                    null, // parented to the main Frost window
                    language.formatMessage("UploadPane.uploadFile.uploadFileNotFound.body", ulItem.getFile().getPath()),
                    language.getString("UploadPane.uploadFile.uploadFileNotFound.title"),
                    MiscToolkit.ERROR_MESSAGE);
            logger.severe("Upload items file does not exist, marked as failed: "+ulItem.getFile().getPath());
        } else if( uploadValidity == 2 ){
            final Language language = Language.getInstance();
            MiscToolkit.showMessageDialog(
                    null, // parented to the main Frost window
                    language.formatMessage("UploadPane.uploadFile.uploadFileSizeChanged.body", ulItem.getFile().getPath()),
                    language.getString("UploadPane.uploadFile.uploadFileSizeChanged.title"),
                    MiscToolkit.ERROR_MESSAGE);
            logger.severe("Upload items file size changed, marked as failed: "+ulItem.getFile().getPath());
        }

        return uploadValidity;
    }

    /**
     * Restarts all of the given upload items (if they are *NOT EXTERNAL* and their current state allows it)
     */
    public void restartItems(final List<FrostUploadItem> items) {
        final LinkedList<FrostUploadItem> toRestart = new LinkedList<FrostUploadItem>();
        final PersistenceManager pm = FileTransferManager.inst().getPersistenceManager();

        // make a list of all the upload items that can be restarted
        for( final FrostUploadItem ulItem : items ) {
            if( ulItem.isExternal() || ulItem.getState() == FrostUploadItem.STATE_ENCODING || ulItem.getState() == FrostUploadItem.STATE_ENCODING_REQUESTED ) {
                // we can't restart items that were externally queued outside of this frost
                // instance (since we don't know the filename) or that are currently doing
                // pre-encoding (since those are busy)
                continue;
            }
            // don't restart items whose data transfer to the node is currently in progress.
            // otherwise, we risk orphaning the upload in the global upload-queue since we haven't de-queued it properly.
            if( FileTransferManager.inst().getPersistenceManager() != null ) {
                if( FileTransferManager.inst().getPersistenceManager().isDirectTransferInProgress(ulItem) ) {
                    continue;
                }
            }
            // we explicitly define the exact states that we can perform a restart from
            if( ulItem.getState() == FrostUploadItem.STATE_FAILED
                    || ulItem.getState() == FrostUploadItem.STATE_WAITING
                    || ulItem.getState() == FrostUploadItem.STATE_PROGRESS
                    || ulItem.getState() == FrostUploadItem.STATE_DONE ) {
                // if this item currently exists in the global queue, then we must prepare it for removal
                if( pm != null && pm.isItemInGlobalQueue(ulItem) ) {
                    // tells the persistencemanager that we're expecting this item to be removed from the global queue
                    ulItem.setInternalRemoveExpected(true);
                }

                // add this valid item to the list of items to restart
                toRestart.add(ulItem);
            }
        }

        // abort now if there's no work to do
        if( toRestart.isEmpty() ) {
            return;
        }

        // now remove the items from the model, which causes them to be removed from the uploads
        // table *and* from the global queue (if they were there)
        removeItems(toRestart);

        // lastly, start a thread with a 1.5 second timer which will reset the progress state
        // of all items and re-add them to the uploads table
        new Thread() {
            @Override
            public void run() {
                // TODO: (slightly ugly) wait until item is removed from global queue
                // before starting upload with same gq identifier
                // this hardcoded wait is a bit ugly but totally fine, because all "remove upload"
                // FCP requests will definitely have been sent after 1.5 seconds,
                // and it doesn't matter if they've been removed yet; what matters is the
                // order of FCP commands, and the fact that the removal request has been sent
                // before our next upload FCP command begins, since the Freenet node will process
                // commands in the order they were received!
                Mixed.wait(1500);
                // we must perform the actual GUI updates in the GUI thread, so queue the update now
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        for( final FrostUploadItem ulItem : toRestart ) {
                            // reset the most important variables that will allow the upload to restart itself
                            ulItem.setRetries(0);
                            ulItem.setLastUploadStopTimeMillis(0);
                            ulItem.setEnabled(true); // this is important; it ensures that the uploads begin when re-added
                            ulItem.setState(FrostUploadItem.STATE_WAITING);

                            // now we reset the progress of the item to the default "unstarted" status.
                            // technically we could leave these as-is and let it automatically reset
                            // everything when the upload actually begins, but that's ugly since
                            // queued items would stay in a weird, mixed old-and-new "Waiting 85% 18/21"
                            // state until then... so we explicitly clear all the progress variables
                            // before re-adding the item, so that it looks nicer in the uploads table.
                            ulItem.setFinalized(false); // means this upload's metadata transfer is not complete
                            ulItem.setUploadFinishedMillis(0); // reset the "upload finished at" timestamp
                            ulItem.setDoneBlocks(-1); // reset the current/total block counts to the default
                            ulItem.setTotalBlocks(-1);
                            ulItem.setErrorCodeDescription(null); // remove any existing "failure reason" error string
                            ulItem.setCompletionProgRun(false); // ensures that the "exec on completion" program can run on completion

                            // now just add the upload to the uploads model and table
                            addNewUploadItem(ulItem);
                        }
                    }
                });
            }
        }.start();
    }

    /**
     * This method tells items passed as a parameter to generate their chks
     * (if their current state allows it).
     * For shared files no CHK will be generated until we uploaded the file.
     */
    public void generateChkItems(final List<FrostUploadItem> items) {
        for( final FrostUploadItem ulItem : items ) {
            // Since it is difficult to identify the states where we are allowed to
            // start an upload, we decide based on the states in which we are not allowed,
            // so start gen chk only for internally queued items and only if
            // IDLE (Waiting) and there is no key yet!
            if ( !ulItem.isExternal()
                 && ulItem.getState() == FrostUploadItem.STATE_WAITING
                 && ulItem.getKey() == null
                 && !ulItem.isSharedFile() )
            {
                // this state is detected by UploadTicker.java, which looks for these states every second and then starts a GenerateChkThread
                ulItem.setState(FrostUploadItem.STATE_ENCODING_REQUESTED);
            }
        }
    }

    /**
     * Removes finished *non-external* uploads from the model.
     */
    public synchronized void removeFinishedUploads() {
        final ArrayList<FrostUploadItem> items = new ArrayList<FrostUploadItem>();
        
        for (int i = getItemCount() - 1; i >= 0; i--) {
            final FrostUploadItem ulItem = getItemAt(i);
            if (!ulItem.isExternal() && ulItem.getState() == FrostUploadItem.STATE_DONE) {
                items.add(ulItem);
            }
        }
        if (items.size() > 0) {
            removeItems(items);
        }
    }

    /**
     * Removes external uploads from the model.
     */
    public synchronized void removeExternalUploads() {
        final ArrayList<FrostUploadItem> items = new ArrayList<FrostUploadItem>();
        for (int i = getItemCount() - 1; i >= 0; i--) {
            final FrostUploadItem ulItem = getItemAt(i);
            if (ulItem.isExternal()) {
                items.add(ulItem);
            }
        }
        if (items.size() > 0) {
            removeItems(items);
        }
    }

    /**
     * Initializes and loads the model
     */
    public void initialize(final List<FrostSharedFileItem> sharedFiles) throws StorageException {
        final List<FrostUploadItem> uploadItems;
        try {
            uploadItems = FrostFilesStorage.inst().loadUploadFiles(sharedFiles);
        } catch (final Throwable e) {
            logger.log(Level.SEVERE, "Error loading upload items", e);
            throw new StorageException("Error loading upload items");
        }
        for( final FrostUploadItem di : uploadItems ) {
            addConsistentUploadItem(di); // no check for dups
        }
    }

    /**
     * Saves the upload model to database.
     */
    public void exitSave() throws StorageException {
        final List<FrostUploadItem> itemList = getItems();
        try {
            FrostFilesStorage.inst().saveUploadFiles(itemList);
        } catch (final Throwable e) {
            logger.log(Level.SEVERE, "Error saving upload items", e);
            throw new StorageException("Error saving upload items");
        }
    }

    /**
     * This method enables / disables *non-external* upload items in the model. If the
     * enabled parameter is null, the current state of the item is inverted.
     * @param enabled new state of the items. If null, the current state
     *        is inverted
     */
    public synchronized void setAllItemsEnabled(final Boolean enabled) {
        for (int x = 0; x < getItemCount(); x++) {
            final FrostUploadItem ulItem = getItemAt(x);
            if (!ulItem.isExternal() && ulItem.getState() != FrostUploadItem.STATE_DONE) {
                ulItem.setEnabled(enabled);
                FileTransferManager.inst().getUploadManager().notifyUploadItemEnabledStateChanged(ulItem);
            }
        }
    }

    /**
     * This method enables / disables *non-external* upload items in the model. If the
     * enabled parameter is null, the current state of the item is inverted.
     * @param enabled new state of the items. If null, the current state
     *        is inverted
     * @param items items to modify
     */
    public void setItemsEnabled(final Boolean enabled, final List<FrostUploadItem> items) {
        for( final FrostUploadItem item : items ) {
            if (!item.isExternal() && item.getState() != FrostUploadItem.STATE_DONE) {
                item.setEnabled(enabled);
                FileTransferManager.inst().getUploadManager().notifyUploadItemEnabledStateChanged(item);
            }
        }
    }
}
