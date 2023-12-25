/*
  DownloadManager.java / Frost

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

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.ext.Execute;
import frost.fcp.FcpResultGet;
import frost.fcp.FreenetKeys;
import frost.fileTransfer.FileTransferInformation;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.storage.ExitSavable;
import frost.storage.StorageException;
import frost.storage.perst.TrackDownloadKeys;
import frost.storage.perst.TrackDownloadKeysStorage;
import frost.storage.perst.filelist.FileListStorage;
import frost.util.ArgumentTokenizer;
import frost.util.CopyToClipboard;
import frost.util.DateFun;
import frost.util.FileAccess;
import frost.util.Mixed;

public class DownloadManager implements ExitSavable {

	private static final Logger logger = Logger.getLogger(DownloadManager.class
			.getName());

	private DownloadModel model;
	private DownloadPanel panel;
	private DownloadTicker ticker;

	private static final int MAX_RECENT_DOWNLOAD_DIRS = 20;
	private LinkedList<String> recentDownloadDirs;

	public DownloadManager() {
		super();
		loadRecentDownloadDirs();
	}

	public void initialize() throws StorageException {
		getPanel();
		getModel().initialize();
	}

	/**
	 * Start download now, using either the global queue persistence manager
	 * or the session-based download ticker thread.
	 */
	public boolean startDownload(final FrostDownloadItem dlItem) {
		if (FileTransferManager.inst().getPersistenceManager() != null) {
			return FileTransferManager.inst().getPersistenceManager().startDownload(dlItem);
		} else {
			return ticker.startDownload(dlItem);
		}
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
		mainFrame.addPanel("MainFrame.tabbedPane.downloads", getPanel());
	}

	/**
	 * Count running items in model.
	 */
	public void updateFileTransferInformation(
			final FileTransferInformation infos) {
		int waitingItems = 0;
		int runningItems = 0;
		for (int x = 0; x < model.getItemCount(); x++) {
			final FrostDownloadItem dlItem = (FrostDownloadItem) model
					.getItemAt(x);
			if (dlItem == null) {
				continue;
			}
			if (dlItem.getState() != FrostDownloadItem.STATE_DONE
					&& dlItem.getState() != FrostDownloadItem.STATE_FAILED) {
				waitingItems++;
			}
			if (dlItem.getState() == FrostDownloadItem.STATE_PROGRESS) {
				runningItems++;
			}
		}
		infos.setDownloadsRunning(runningItems);
		infos.setDownloadsWaiting(waitingItems);
	}

	public DownloadPanel getPanel() {
		if (panel == null) {
			panel = new DownloadPanel();
			panel.setModel(getModel());
			panel.initialize();
		}
		return panel;
	}

	public DownloadModel getModel() {
		if (model == null) {
			model = new DownloadModel(new DownloadTableFormat());
		}
		return model;
	}

	private DownloadTicker getTicker() {
		if (ticker == null) {
			ticker = new DownloadTicker();
		}
		return ticker;
	}

	private void loadRecentDownloadDirs() {
		recentDownloadDirs = new LinkedList<String>();

		for (int i = 0; i < MAX_RECENT_DOWNLOAD_DIRS; i++) {
			final String key = "DownloadManager.recentDownloadDir." + i;
			if (Core.frostSettings.getObjectValue(key) == null) {
				break;
			}
			// add it to the internal list (duplicates are handled properly), and check the clean return value
			final String cleanValue = addRecentDownloadDirInternal(Core.frostSettings.getValue(key));
			if( cleanValue != null ) { // null would mean it was rejected
				// save the cleaned-up version to the settings storage, to make sure Frost.ini is
				// cleaned up from any old, absolute paths from prior versions of Frost
				Core.frostSettings.setValue(key, cleanValue);
			}
		}
	}

	private void saveRecentDownloadDirs() {
		int i = 0;

		for (final String dir : recentDownloadDirs) {
			final String key = "DownloadManager.recentDownloadDir." + i++;
			Core.frostSettings.setValue(key, dir);
		}
	}

	// this internal version doesn't save to global settings storage (Frost.ini) when it's adding an entry,
	// and it returns the clean string that it added to the storage (or null if the entry was rejected)
	private String addRecentDownloadDirInternal(final String downloadDirDirty) {
		if( downloadDirDirty == null ) { return null; }

		// clean up and validate the input, and turn absolute paths that point to subfolders
		// of the Frost directory, into relative paths instead (this migrates users from older
		// versions of Frost which stored absolute paths, as well as ensures that any future
		// paths are always relative where needed)
		final String defaultDlDir = FrostDownloadItem.getDefaultDownloadDir();
		String downloadDirClean = Core.pathRelativizer.relativize(downloadDirDirty);
		if( downloadDirClean == null ) { return null; } // skip invalid paths
		downloadDirClean = FileAccess.appendSeparator(downloadDirClean); // append trailing slash
		if( downloadDirClean.equals(defaultDlDir) ){ return null; } // don't add the default download directory

		// now just add the directory to the linked list. they're added in oldest-first order, but if you
		// try to add the same entry again, it's moved to the tail of the list to make it "most recent".
		final ListIterator<String> i = recentDownloadDirs.listIterator();
		boolean found = false;

		// If the download directory is already in the list...
		while (i.hasNext()) {
			final String dir = i.next();
			if (dir.equals(downloadDirClean)) {
				// ... make it the most recently used.
				i.remove();
				recentDownloadDirs.add(downloadDirClean);
				found = true;
				break;
			}
		}

		// Otherwise just add it to the tail of the list (most recent)
		if (!found) {
			recentDownloadDirs.add(downloadDirClean);
		}

		// If we've exceeded the maximum number of dirs to remember, then
		// keep deleting head entries until we're at the right size again
		while (recentDownloadDirs.size() > MAX_RECENT_DOWNLOAD_DIRS) {
			recentDownloadDirs.remove();
		}

		// return the clean string we saved to the linked list
		return downloadDirClean;
	}

	// this public version adds a new/reused directory and then writes all dirs to the global settings storage
	public void addRecentDownloadDir(final String downloadDir) {
		addRecentDownloadDirInternal(downloadDir);
		saveRecentDownloadDirs();
	}

	public final LinkedList<String> getRecentDownloadDirs() {
		return recentDownloadDirs;
	}

	/* THIS SECTION HAS BEEN COMMENTED OUT: Nothing uses these functions, and they contain old code
	 * which doesn't properly validate/clean up the filenames and keys.
	 * All downloads are added via the "Add new downloads" dialog instead, as well as a few direct
	 * model manipulations in appropriate places (such as the "search for files" feature, and the
	 * download model itself when it loads files from the database at startup).
	 * So this old, useless and harmful code has been commented out. -- Kitty
	public FrostDownloadItem addNewDownload(final String key,
		final String fileName, final String dlDir, final String prefix) {
		// TODO: enhancement: search for key in shared files, maybe add as shared file
		final FrostDownloadItem dlItem = new FrostDownloadItem(fileName, key);
		dlItem.setDownloadDir(dlDir);
		dlItem.setFilenamePrefix(prefix);
		model.addDownloadItem(dlItem, true); // true = ask to redownload duplicates
		return dlItem;
	}

	public FrostDownloadItem addNewDownload(final String key,
			final String fileName, final String dlDir) {
		return addNewDownload(key, fileName, dlDir, null);
	}

	public FrostDownloadItem addNewDownload(final String key,
			final String fileName) {
		return addNewDownload(key, fileName, null, null);
	}
	*/

	/**
	 * Called by both the persistent queue and non-persistent mode downloads,
	 * to perform post-download maintenance.
	 *
	 * @return true if request should be retried
	 */
	public boolean notifyDownloadFinished(final FrostDownloadItem downloadItem,
			final FcpResultGet result, final File targetFile) {

		final String filename = downloadItem.getFileName();
		final String key = downloadItem.getKey();

		boolean retryImmediately = false;

		if (result == null || result.isSuccess() == false) {
			// download failed
			// if persistent mode was used, we may need to remove the old global-queue request;
			// but only if we're going to keep the item in a "waiting" state.
			String removeGqId = null;

			if (result != null) {
				downloadItem.setErrorCodeDescription(result
						.getCodeDescription());
			}

			if (result != null && result.getReturnCode() == 5
					&& key.startsWith("CHK@") && key.indexOf("/") > 0) {
				// 5 - Archive failure
				// node tries to access the file as a .zip file, try download
				// again without any path
				final String newKey = key.substring(0, key.indexOf("/"));
				downloadItem.setKey(newKey);
				downloadItem.setState(FrostDownloadItem.STATE_WAITING);
				removeGqId = downloadItem.getGqIdentifier();
				downloadItem.setLastDownloadStopTime(0);
				retryImmediately = true;

				logger.warning("Removed all path levels from key: " + key
						+ " ; " + newKey);

			} else if (result != null && result.getReturnCode() == 11
					&& key.startsWith("CHK@") && key.indexOf("/") > 0) {
				// 11 - The URI has more metastrings and I can't deal with them
				// remove one path level from CHK
				final String newKey = key.substring(0, key.lastIndexOf("/"));
				downloadItem.setKey(newKey);
				downloadItem.setState(FrostDownloadItem.STATE_WAITING);
				removeGqId = downloadItem.getGqIdentifier();
				downloadItem.setLastDownloadStopTime(0);
				retryImmediately = true;

				logger.warning("Removed one path level from key: " + key
						+ " ; " + newKey);

			} else if (result != null && result.getReturnCode() == 27
					&& result.getRedirectURI() != null) {
				// permanent redirect, use new uri
				downloadItem.setKey(result.getRedirectURI());
				downloadItem.setState(FrostDownloadItem.STATE_WAITING);
				removeGqId = downloadItem.getGqIdentifier();
				retryImmediately = true;

				logger.warning("Redirected to URI: " + result.getRedirectURI());

			} else if (result != null && result.isFatal()) {
				// fatal, don't retry
				// downloadItem.setEnabledfalse); // keep enabled to
				// allow sending of requests for shared files
				downloadItem.setState(FrostDownloadItem.STATE_FAILED);
				logger.warning("FILEDN: Download of " + filename
						+ " failed FATALLY.");
			} else {
				downloadItem.setRetries(downloadItem.getRetries() + 1);

				logger.warning("FILEDN: Download of " + filename + " failed.");
				// set new state -> failed or waiting for another try
				if (downloadItem.getRetries() > Core.frostSettings
						.getIntValue(SettingsClass.DOWNLOAD_MAX_RETRIES)) {
					// downloadItem.setEnabled(false); // keep
					// enabled to allow sending of requests for shared files
					downloadItem.setState(FrostDownloadItem.STATE_FAILED);
				} else {
					// the most common reasons for reaching this "else" is that
					// we failed to connect to the socket, or failed to write
					// all or part of the file to disk. we'll retry the transfer,
					// but won't retry immediately; instead, it will wait until
					// the user's "Wait-time after each try (minutes)" setting.
					downloadItem.setState(FrostDownloadItem.STATE_WAITING);
					removeGqId = downloadItem.getGqIdentifier();
				}
			}

			// remove the item from the global queue *if* persistence was used
			if( removeGqId != null ) {
				if( FileTransferManager.inst().getPersistenceManager() != null ) {
					final List<String> requests = new ArrayList<String>(1);
					requests.add(removeGqId);
					downloadItem.setInternalRemoveExpected(true);
					FileTransferManager.inst().getPersistenceManager().removeRequests(requests);
				}
			}
		} else {

			logger.info("FILEDN: Download of " + filename + " was successful.");

			// download successful
			downloadItem.setFileSize(new Long(targetFile.length()));
			downloadItem.setState(FrostDownloadItem.STATE_DONE);
			downloadItem.setEnabled(false);

			downloadItem.setDownloadFinishedTime(System.currentTimeMillis());

			// update lastDownloaded time in filelist
			if (downloadItem.isSharedFile()) {
				FileListStorage.inst()
						.updateFrostFileListFileObjectAfterDownload(
								downloadItem.getFileListFileObject().getSha(),
								System.currentTimeMillis());
			}

			// maybe add the finished download to the list of tracked downloads, if the user wants to
			// and the item hasn't already been added to the tracking
			if (Core.frostSettings
					.getBoolValue(SettingsClass.TRACK_DOWNLOADS_ENABLED)
					&& !downloadItem.isTracked()) {
				TrackDownloadKeysStorage trackDownloadKeysStorage = TrackDownloadKeysStorage
						.inst();
				// NOTE: storeItem() uses a unique key-based index, and won't add the same key more
				// than a single time, even if we try. that's good as it avoids duplicates.
				trackDownloadKeysStorage.storeItem(new TrackDownloadKeys(
						downloadItem.getKey(), downloadItem.getFileName(),
						downloadItem.getAssociatedBoardName(), downloadItem
								.getFileSize(), downloadItem
								.getDownloadFinishedMillis()));
				downloadItem.setTracked(true);
			}

			// if logging is enabled, then log the successful download to localdata/Frost-Downloads_2015-01.log (UTC year/month).
			// the reason that we use the current year+month in the filename is to avoid creating huge, unwieldy log files over time.
			if (Core.frostSettings.getBoolValue(SettingsClass.LOG_DOWNLOADS_ENABLED)
					&& !downloadItem.isLoggedToFile()) {
				// generate a basic information string (key-name only)
				final String infoStr = CopyToClipboard.getItemInformation(downloadItem, false, false);

				// append the log line to the file, along with the current date/time
				if( infoStr != null ) {
					// generate date string and the final line
					final DateTime now = new DateTime(DateTimeZone.UTC);
					final String dateStr = DateFun.FORMAT_DATE_EXT.print(now)
						+ " - "
						+ DateFun.FORMAT_TIME_EXT.print(now);
					// format: "[ 2015.09.24 - 21:34:19GMT ] CHK@.../filename.ext"
					final String line = "[ " + dateStr + " ] " + infoStr;

					// output to disk
					final String fileName = Core.frostSettings.getValue(SettingsClass.DIR_LOCALDATA)
						+ "Frost-Downloads_" + now.toString("yyyy-MM") + ".log";
					final File targetLogFile = new File(fileName);
					FileAccess.appendLineToTextfile(targetLogFile, line);
				}

				// mark the file as logged, even if we failed to generate an info string for it
				downloadItem.setLoggedToFile(true);
			}

			// execute an external program if the user has enabled that option
			final String execCmd = Core.frostSettings.getValue(SettingsClass.EXEC_ON_DOWNLOAD);
			if (execCmd != null && execCmd.length() > 0
					&& !downloadItem.isExternal()
					&& !downloadItem.isCompletionProgRun()) {

				// build a fixed-order list of the file's information and replace nulls with empty strings
				final String fileInfo[] = new String[5];
				fileInfo[0] = downloadItem.getFileName();
				fileInfo[1] = downloadItem.getFilenamePrefix();
				fileInfo[2] = downloadItem.getKey();
				fileInfo[3] = downloadItem.getAssociatedBoardName();
				fileInfo[4] = downloadItem.getAssociatedMessageId();
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
				extraEnvironment.put("FROST_FILENAME_PREFIX", fileInfo[1]);
				extraEnvironment.put("FROST_KEY", fileInfo[2]);
				extraEnvironment.put("FROST_ASSOC_BOARD_NAME", fileInfo[3]);
				extraEnvironment.put("FROST_ASSOC_MSG_ID", fileInfo[4]);

				// use the file transfer's folder as the working directory for the new process
				final File workingDirectory = new File(downloadItem.getDownloadDir());

				// execute the external program asynchronously (non-blocking)
				// NOTE: if the program couldn't be launched, we won't be notified about it since
				// it's asynchronous. but the most common failure reason is just that the program
				// doesn't exist, which is the user's fault (so who cares).
				Execute.run_async(args, workingDirectory, extraEnvironment);

				// mark the "has completion program executed" as true even if it possibly failed
				downloadItem.setCompletionProgRun(true);
			}

			// maybe remove finished download immediately
			if (Core.frostSettings
					.getBoolValue(SettingsClass.DOWNLOAD_REMOVE_FINISHED)) {
				FileTransferManager.inst().getDownloadManager().getModel()
						.removeFinishedDownloads();
			}
		}

		if (retryImmediately) {
			downloadItem.setLastDownloadStopTime(-1);
		} else {
			downloadItem.setLastDownloadStopTime(System.currentTimeMillis());
		}

		return retryImmediately;
	}

	public List<FrostDownloadItem> getDownloadItemList() {
		return getModel().getItems();
	}

	/**
	 * Chooses next download item to start from download table.
	 * 
	 * @return the next download item to start downloading or null if a suitable
	 *         one was not found.
	 */
	public FrostDownloadItem selectNextDownloadItem() {

		// get the item with state "Waiting"
		final ArrayList<FrostDownloadItem> waitingItems = new ArrayList<FrostDownloadItem>();
		for (int i = 0; i < model.getItemCount(); i++) {
			final FrostDownloadItem dlItem = model.getItemAt(i);
			
			final boolean itemIsEnabled = (dlItem.isEnabled() == null ? true
					: dlItem.isEnabled().booleanValue());
			if (!itemIsEnabled) {
				continue;
			}
			if (dlItem.isExternal()) {
				continue;
			}
			if (dlItem.getKey() == null) {
				// still no key, wait
				continue;
			}

			if (dlItem.getState() != FrostDownloadItem.STATE_WAITING) {
				continue;
			}

			// check if waittime is expired
			final long waittimeMillis = Core.frostSettings
					.getIntValue(SettingsClass.DOWNLOAD_WAITTIME) * 60L * 1000L;
			// min->millisec
			if (dlItem.getLastDownloadStopTime() == 0 // never started
					|| (System.currentTimeMillis() - dlItem
							.getLastDownloadStopTime()) > waittimeMillis) {
				waitingItems.add(dlItem);
			}
		}

		if (waitingItems.size() == 0) {
			return null;
		}

		if (waitingItems.size() > 1) { // performance issues
			Collections.sort(waitingItems, nextItemCmp);
		}
		return waitingItems.get(0);
	}

	public void notifyDownloadItemEnabledStateChanged(final FrostDownloadItem dlItem) {
		// for persistent items, set priority to 6 (pause) when disabled; and to
		// configured default if enabled
		if (dlItem.isExternal()) {
			return;
		}

		if (dlItem.getState() != FrostDownloadItem.STATE_PROGRESS) {
			// not running, not in queue
			return;
		}
		
		final boolean itemIsEnabled = (dlItem.isEnabled() == null ? true
				: dlItem.isEnabled().booleanValue());
		FreenetPriority prio = FreenetPriority.PAUSE;
		if (itemIsEnabled) {
			prio = FreenetPriority.getPriority(Core.frostSettings.getIntValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD));
		}
		
		List<FrostDownloadItem> frostDownloadItems = new ArrayList<FrostDownloadItem>();
		frostDownloadItems.add(dlItem);
		
		panel.changeItemPriorites(frostDownloadItems, prio);
	}

	/**
	 * Used to sort FrostDownloadItems by lastUpdateStartTimeMillis ascending.
	 */
	private static final Comparator<FrostDownloadItem> nextItemCmp = new Comparator<FrostDownloadItem>() {
		public int compare(final FrostDownloadItem value1,
				final FrostDownloadItem value2) {

			// choose item that with lowest addedTime
			final int cmp1 = Mixed.compareLong(value1.getDownloadAddedMillis(),
					value2.getDownloadAddedMillis());
			if (cmp1 != 0) {
				return cmp1;
			}

			// equal addedTimes, choose by blocksRemaining
			int blocksTodo1;
			int blocksTodo2;

			// compute remaining blocks
			if (value1.getRequiredBlocks() > 0 && value1.getDoneBlocks() > 0) {
				blocksTodo1 = value1.getRequiredBlocks()
						- value1.getDoneBlocks();
			} else {
				blocksTodo1 = Integer.MAX_VALUE; // never started
			}
			if (value2.getRequiredBlocks() > 0 && value2.getDoneBlocks() > 0) {
				blocksTodo2 = value2.getRequiredBlocks()
						- value2.getDoneBlocks();
			} else {
				blocksTodo2 = Integer.MAX_VALUE; // never started
			}

			final int cmp2 = Mixed.compareInt(blocksTodo1, blocksTodo2);
			if (cmp2 != 0) {
				return cmp2;
			}

			// equal remainingBlocks, choose smaller file (filesize can be -1)
			return Mixed
					.compareLong(value1.getFileSize(), value2.getFileSize());
		}
	};
}
