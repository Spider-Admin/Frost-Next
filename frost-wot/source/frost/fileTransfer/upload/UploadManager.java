/*
  UploadManager.java / Frost
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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.ext.Execute;
import frost.fcp.FcpResultPut;
import frost.fileTransfer.FileTransferInformation;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.sharing.FrostSharedFileItem;
import frost.storage.ExitSavable;
import frost.storage.StorageException;
import frost.util.ArgumentTokenizer;
import frost.util.CopyToClipboard;
import frost.util.DateFun;
import frost.util.FileAccess;
import frost.util.Mixed;

public class UploadManager implements ExitSavable {

    private static final Logger logger = Logger.getLogger(UploadManager.class.getName());

    private UploadModel model;
    private UploadPanel panel;
    private UploadTicker ticker;

    public UploadManager() {
        super();
    }

    public void initialize(final List<FrostSharedFileItem> sharedFiles) throws StorageException {
        getPanel();
        getModel().initialize(sharedFiles);
    }

    /**
     * Count running items in model.
     */
    public void updateFileTransferInformation(final FileTransferInformation infos) {
        int waitingItems = 0;
        int runningItems = 0;
        for (int x = 0; x < model.getItemCount(); x++) {
            final FrostUploadItem ulItem = (FrostUploadItem) model.getItemAt(x);
            if (ulItem.getState() != FrostUploadItem.STATE_DONE
                    && ulItem.getState() != FrostUploadItem.STATE_FAILED)
            {
                waitingItems++;
            }
            if (ulItem.getState() == FrostUploadItem.STATE_PROGRESS) {
                runningItems++;
            }
        }
        infos.setUploadsRunning(runningItems);
        infos.setUploadsWaiting(waitingItems);
    }

    public void startTicker() {
        if (Core.isFreenetOnline()) {
            getTicker().start();
        }
    }

    public void exitSave() throws StorageException {
        getPanel().getTableFormat().saveTableLayout();
        getModel().exitSave();
    }

    public void addPanelToMainFrame(final MainFrame mainFrame) {
        mainFrame.addPanel("MainFrame.tabbedPane.uploads", getPanel());
    }

    public UploadPanel getPanel() {
        if (panel == null) {
            panel = new UploadPanel();
            panel.setModel(getModel());
            panel.initialize();
        }
        return panel;
    }

    private UploadTicker getTicker() {
        if (ticker == null) {
            ticker = new UploadTicker(getModel());
        }
        return ticker;
    }

    public UploadModel getModel() {
        if (model == null) {
            model = new UploadModel(new UploadTableFormat());
        }
        return model;
    }

    /**
     * Handle a finished file upload, either successful or failed.
     */
    public void notifyUploadFinished(final FrostUploadItem uploadItem, final FcpResultPut result) {

        if (result != null && (result.isSuccess() || result.isKeyCollision()) ) {

            logger.info("Upload of " + uploadItem.getFile().getName() + " ("+ uploadItem.getFileName() + ") was successful.");

            // upload successful
            uploadItem.setKey(result.getChkKey());
            if( uploadItem.isSharedFile() ) {
                uploadItem.getSharedFileItem().notifySuccessfulUpload(result.getChkKey());
            }

            uploadItem.setEnabled(false);
            uploadItem.setState(FrostUploadItem.STATE_DONE);

            uploadItem.setUploadFinishedMillis(System.currentTimeMillis());

            // notify model that shared upload file can be removed
            if( uploadItem.isSharedFile() ) {
                // NOTE: the only thing this function does is remove the "shared file" upload
                // from the "Uploads" table regardless of the state of the "remove finished" checkbox.
                // and yes, this special-case for shared files means that we don't log finished
                // shared-file uploads or run the external program for them. that's intentional,
                // since they *aren't* normal uploads.
                getModel().notifySharedFileUploadWasSuccessful(uploadItem);
            } else {
                // if logging is enabled, then log the successful upload to localdata/Frost-Uploads_2015-01.log (UTC year/month).
                // the reason that we use the current year+month in the filename is to avoid creating huge, unwieldy log files over time.
                if( Core.frostSettings.getBoolValue(SettingsClass.LOG_UPLOADS_ENABLED) && !uploadItem.isLoggedToFile() ) {
                    // generate an extended information string (with all upload settings)
                    final String infoStr = CopyToClipboard.getItemInformation(uploadItem, true, false);

                    // append the extended information to the file, along with the current date/time
                    if( infoStr != null ) {
                        // generate date string and the final lines
                        final DateTime now = new DateTime(DateTimeZone.UTC);
                        final String dateStr = DateFun.FORMAT_DATE_EXT.print(now)
                            + " - "
                            + DateFun.FORMAT_TIME_EXT.print(now);
                        // format: "[ 2015.09.24 - 21:34:19GMT ]\n<Extended Information>\n\n"
                        final String lines = "[ " + dateStr + " ]\n" + infoStr + "\n"; // NOTE: only needs a single newline since appendLine adds one too

                        // output to disk
                        final String fileName = Core.frostSettings.getValue(SettingsClass.DIR_LOCALDATA)
                            + "Frost-Uploads_" + now.toString("yyyy-MM") + ".log";
                        final File targetLogFile = new File(fileName);
                        FileAccess.appendLineToTextfile(targetLogFile, lines);
                    }

                    // mark the file as logged, even if we failed to generate an info string for it
                    uploadItem.setLoggedToFile(true);
                }

                // execute an external program if the user has enabled that option
                final String execCmd = Core.frostSettings.getValue(SettingsClass.EXEC_ON_UPLOAD);
                if (execCmd != null && execCmd.length() > 0
                        && !uploadItem.isExternal()
                        && !uploadItem.isCompletionProgRun()) {

                    // build a fixed-order list of the file's information and replace nulls with empty strings
                    final String fileInfo[] = new String[5];
                    fileInfo[0] = uploadItem.getFileName();
                    fileInfo[1] = uploadItem.getKey();
                    fileInfo[2] = uploadItem.getFreenetCompatibilityMode();
                    fileInfo[3] = ( uploadItem.getCompress() ? "YES_AUTO" : "NO" );
                    final String cryptoKey = uploadItem.getCryptoKey(); // null if no custom key
                    fileInfo[4] = ( cryptoKey == null ? "AUTO" : cryptoKey );
                    for( int i=0; i<fileInfo.length; ++i ) {
                        if( fileInfo[i] == null ) {
                            fileInfo[i] = "";
                        }
                    }

                    // now parse the exec command into individual arguments, add the additional arguments
                    // to the end of the user's program/argument list, and convert it to a regular array
                    final List<String> argList = ArgumentTokenizer.tokenize(execCmd, false);
                    argList.addAll(Arrays.asList(fileInfo));
                    final String[] args = argList.toArray(new String[argList.size()]);

                    // construct additional environment variables describing the file transfer
                    final Map<String,String> extraEnvironment = new HashMap<String,String>();
                    extraEnvironment.put("FROST_FILENAME", fileInfo[0]);
                    extraEnvironment.put("FROST_KEY", fileInfo[1]);
                    extraEnvironment.put("FROST_MODE", fileInfo[2]);
                    extraEnvironment.put("FROST_COMPRESS", fileInfo[3]);
                    extraEnvironment.put("FROST_CRYPTOKEY", fileInfo[4]);

                    // use the file transfer's folder as the working directory for the new process
                    final File workingDirectory = uploadItem.getFile().getParentFile();

                    // execute the external program asynchronously (non-blocking)
                    // NOTE: if the program couldn't be launched, we won't be notified about it since
                    // it's asynchronous. but the most common failure reason is just that the program
                    // doesn't exist, which is the user's fault (so who cares).
                    Execute.run_async(args, workingDirectory, extraEnvironment);

                    // mark the "has completion program executed" as true even if it possibly failed
                    uploadItem.setCompletionProgRun(true);
                }
            }

            // maybe remove finished upload immediately
            if( Core.frostSettings.getBoolValue(SettingsClass.UPLOAD_REMOVE_FINISHED) ) {
                getModel().removeFinishedUploads();
            }
        } else {
            // upload failed
            logger.warning("Upload of " + uploadItem.getFile().getName() + " was NOT successful.");

            if( result != null && result.isFatal() ) {
                uploadItem.setEnabled(false);
                uploadItem.setState(FrostUploadItem.STATE_FAILED);
            } else {
                uploadItem.setRetries(uploadItem.getRetries() + 1);

                if (uploadItem.getRetries() > Core.frostSettings.getIntValue(SettingsClass.UPLOAD_MAX_RETRIES)) {
                    uploadItem.setEnabled(false);
                    uploadItem.setState(FrostUploadItem.STATE_FAILED);
                } else {
                    // retry
                    uploadItem.setState(FrostUploadItem.STATE_WAITING);
                }
            }
            if( result != null ) {
                uploadItem.setErrorCodeDescription(result.getCodeDescription());
            }
        }
        uploadItem.setLastUploadStopTimeMillis(System.currentTimeMillis());
    }

    /**
     * Start upload now (manually).
     * Automatically uses the global queue "persistence manager" if enabled (it is by default),
     * otherwise it uses the built-in Frost uploader instead.
     */
    public boolean startUpload(final FrostUploadItem ulItem) {
        if( FileTransferManager.inst().getPersistenceManager() != null ) {
            return FileTransferManager.inst().getPersistenceManager().startUpload(ulItem);
        } else {
            return ticker.startUpload(ulItem);
        }
    }

    /**
     * Chooses next upload item to start from upload table.
     * @return the next upload item to start uploading or null if a suitable one was not found.
     */
    public FrostUploadItem selectNextUploadItem() {

        final ArrayList<FrostUploadItem> waitingItems = new ArrayList<FrostUploadItem>();

        final long currentTime = System.currentTimeMillis();

        for (int i = 0; i < model.getItemCount(); i++) {
            final FrostUploadItem ulItem = (FrostUploadItem) model.getItemAt(i);

            // don't start disabled items
            final boolean itemIsEnabled = (ulItem.isEnabled()==null?true:ulItem.isEnabled().booleanValue());
            if( !itemIsEnabled ) {
                continue;
            }
            // don't start external 0.7 items
            if( ulItem.isExternal() ) {
                continue;
            }
            // don't start items whose direct transfer to the node is already in progress
            if( FileTransferManager.inst().getPersistenceManager() != null ) {
                if( FileTransferManager.inst().getPersistenceManager().isDirectTransferInProgress(ulItem) ) {
                    continue;
                }
            }
            // only start waiting items
            if (ulItem.getState() != FrostUploadItem.STATE_WAITING) {
                continue;
            }
            // check if items waittime between tries is expired so we could restart it
            final long waittimeMillis = Core.frostSettings.getIntValue(SettingsClass.UPLOAD_WAITTIME) * 60L * 1000L;
            if ((currentTime - ulItem.getLastUploadStopTimeMillis()) < waittimeMillis) {
                continue;
            }

            // we could start this item
            waitingItems.add(ulItem);
        }

        if (waitingItems.size() == 0) {
            return null;
        }

        if (waitingItems.size() > 1) {
            Collections.sort(waitingItems, nextItemCmp);
        }

        return waitingItems.get(0);
    }

    public void notifyUploadItemEnabledStateChanged(final FrostUploadItem ulItem) {
        // for persistent items, set priority to 6 (pause) when disabled; and to configured default if enabled
        if( FileTransferManager.inst().getPersistenceManager() == null ) {
            return;
        }
        if( ulItem.isExternal() ) {
            return;
        }
        if( ulItem.getState() != FrostUploadItem.STATE_PROGRESS ) {
            // not running, not in queue
            return;
        }
        final boolean itemIsEnabled = (ulItem.isEnabled() == null ? true : ulItem.isEnabled().booleanValue());
        List<FrostUploadItem> frostUploadItems = new ArrayList<FrostUploadItem>();
        frostUploadItems.add(ulItem);
        FreenetPriority prio = FreenetPriority.PAUSE;
        if( itemIsEnabled ) {
            prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_UPLOAD));
        }
        panel.changeItemPriorites(frostUploadItems, prio);
    }

    private static final Comparator<FrostUploadItem> nextItemCmp = new Comparator<FrostUploadItem>() {
        public int compare(final FrostUploadItem value1, final FrostUploadItem value2) {

            // choose item that with lowest addedTime
            final int cmp1 = Mixed.compareLong(value1.getUploadAddedMillis(), value2.getUploadAddedMillis());
            if( cmp1 != 0 ) {
                return cmp1;
            }

            // equal addedTimes, choose by blocksRemaining
            int blocksTodo1;
            int blocksTodo2;

            // compute remaining blocks
            if( value1.getTotalBlocks() > 0 && value1.getDoneBlocks() > 0 ) {
                blocksTodo1 = value1.getTotalBlocks() - value1.getDoneBlocks();
            } else {
                blocksTodo1 = Integer.MAX_VALUE; // never started
            }
            if( value2.getTotalBlocks() > 0 && value2.getDoneBlocks() > 0 ) {
                blocksTodo2 = value2.getTotalBlocks() - value2.getDoneBlocks();
            } else {
                blocksTodo2 = Integer.MAX_VALUE; // never started
            }

            final int cmp2 = Mixed.compareInt(blocksTodo1, blocksTodo2);
            if( cmp2 != 0 ) {
                return cmp2;
            }

            // equal remainingBlocks, choose smaller file
            return Mixed.compareLong(value1.getFileSize(), value2.getFileSize());
        }
    };
}
