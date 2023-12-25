/*
  DownloadModel.java / Frost

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
import java.util.*;
import java.util.logging.*;

import javax.swing.*;

import frost.*;
import frost.fileTransfer.*;
import frost.storage.*;
import frost.storage.perst.*;
import frost.util.gui.translation.*;
import frost.util.gui.MiscToolkit;
import frost.util.model.*;
import frost.util.FileAccess;
import frost.util.Mixed;

/**
 * This is the model that stores all FrostDownloadItems.
 * 
 * Its implementation is thread-safe (subclasses should synchronize against protected attribute data when necessary). It
 * is also assumed that the load and save methods will not be used while other threads are under way.
 */
public class DownloadModel extends SortedModel<FrostDownloadItem> implements ExitSavable {

	private static final Logger logger = Logger.getLogger(DownloadModel.class.getName());

	public DownloadModel(final SortedTableFormat<FrostDownloadItem> f) {
		super(f);
	}

	/**
	 * Error dialogs for when one or more of the new items are already queued (by key).
	 */
	private void showAlreadyQueuedSingleDialog(final FrostDownloadItem failedItem) {
		final Language language = Language.getInstance();
		MiscToolkit.showMessageDialog(
				null, // parented to the main Frost window
				language.formatMessage("DownloadPane.alreadyQueuedSingleDialog.body", failedItem.getKey()),
				language.getString("DownloadPane.alreadyQueuedSingleDialog.title"),
				MiscToolkit.ERROR_MESSAGE);
	}
	private void showAlreadyQueuedMultiDialog(final List<FrostDownloadItem> failedList) {
		if( failedList == null || failedList.size() == 0 ) { return; }

		// chunk the input into groups of 10 keys at a time (to not overwhelm the user)
		final int partitionSize = 10;
		final List<List<FrostDownloadItem>> partitions = new LinkedList<List<FrostDownloadItem>>();
		for( int i=0; i < failedList.size(); i += partitionSize ) {
			partitions.add(failedList.subList(i,
						Math.min(i + partitionSize, failedList.size()) // to end of partition or end of original list, whichever is smaller
			));
		}

		// now display individual error messages for each chunk of keys
		final Language language = Language.getInstance();
		int currentChunk = 0;
		for( final List<FrostDownloadItem> failedChunk : partitions ) {
			++currentChunk;

			// build a newline-separated string of all keys in this chunk
			final StringBuilder sb = new StringBuilder();
			for( final FrostDownloadItem dlItem : failedChunk ) {
				sb.append(dlItem.getKey()).append("\n");
			}
			final String thisChunkStr = sb.toString().trim(); // remove trailing newline

			// now just display the message
			MiscToolkit.showMessageDialog(
					null, // parented to the main Frost window
					language.formatMessage("DownloadPane.alreadyQueuedMultiDialog.body",
						currentChunk, // "1"
						partitions.size(), // "2"
						thisChunkStr), // "[keys]"
					language.getString("DownloadPane.alreadyQueuedMultiDialog.title"),
					MiscToolkit.ERROR_MESSAGE);
		}
	}

	/**
	 * Just used by addDownloadItemList() to be able to remember the user's dialog choice
	 * between invocations of the "addDownloadItem()" function for each item in the list,
	 * without needing to muddy up the return-value of "addDownloadItem()".
	 */
	private class IntRef
	{
		public int value;
		public IntRef(int value) { this.value = value; }
	}
	
	/**
	 * Adds multiple items to the download model. Displays a combined error message in case
	 * one or more of the keys already existed in the queue.
	 * NOTE: This is currently only called from the "Add new downloads" dialog.
	 * @param {List} itemsToAddList - a list of FrostDownloadItem objects to add
	 * @param {boolean} askBeforeRedownload - if true, it will ask before redownloading keys
	 * that you've already downloaded before (matched via the download tracker). even if this
	 * is set to true, the dialogs can be avoided by the user by pressing the "Yes to All"
	 * or "No to All" buttons, which means that all subsequent dialogs from *this* "itemlist"
	 * will be automatically choosing yes or no as decided by the user.
	 */
	public synchronized boolean addDownloadItemList(
			final List<FrostDownloadItem> itemsToAddList,
			final boolean askBeforeRedownload)
	{
		ArrayList<FrostDownloadItem> failedList = new ArrayList<FrostDownloadItem>();

		// remembers the state of their "redownload?" choices for this batch of files,
		// but is ONLY used if "askBeforeRedownload" is true.
		final IntRef redownloadChoice = new IntRef(-1); // "-1" = "ask for every file"

		// attempt to add every new item
		for(final FrostDownloadItem frostDownloadItem : itemsToAddList ) {
			// false = we do NOT want it to display individual error dialogs if the key was already in the model
			final int success = this.addDownloadItem(frostDownloadItem, askBeforeRedownload, redownloadChoice, false);
			// keep track of items that already existed in queue (0 = already in queue, 1 = added,
			// -1 = previously downloaded and user chose not to download it again)
			if( success == 0 ) {
				failedList.add(frostDownloadItem);
			}
		}

		// display a message regarding any keys that were already queued
		if( failedList.size() > 0 ) {
			showAlreadyQueuedMultiDialog(failedList);
		}

		return true;
	}

	/**
	 * The public interface to addDownloadItem; always runs with showErrors = true, to display
	 * errors in case the key is already queued.
	 * @param {FrostDownloadItem} itemToAdd - a single FrostDownloadItem object to add
	 * @param {boolean} askBeforeRedownload - if true, it will ask before redownloading keys
	 * that you've already downloaded before (matched via the download tracker).
	 */
	public synchronized int addDownloadItem(
			final FrostDownloadItem itemToAdd,
			final boolean askBeforeRedownload)
	{
		return addDownloadItem(itemToAdd, askBeforeRedownload, null, /*showErrors=*/true);
	}

	/**
	 * Will add this item to model if not already in model.
	 * @param {FrostDownloadItem} itemToAdd - a single FrostDownloadItem object to add
	 * @param {boolean} askBeforeRedownload - if true, it will ask before redownloading keys
	 * that you've already downloaded before (matched via the download tracker).
	 * @param {IntRef} redownloadChoice - only used if askBeforeRedownload is true. if this IntRef
	 * is a non-null value, it will read/store the user's "Yes to All" or "No to All" choice from it.
	 * you can then keep passing the same IntRef variable in during subsequent calls, to avoid asking
	 * the "redownload this file?" question over and over for a group of related files. currently
	 * this feature is only used by "addDownloadItemList()", via the "Add new downloads" dialog.
	 * @param {boolean} showErrors - this is used internally; if true, it tells the user if the
	 * new key was already queued and therefore ignored (this should only be true during single-key
	 * adding). if false, it is silent and allows the batch adder ("addDownloadItemList") to
	 * display its own compound message instead of multiple small ones. note that we *always* show
	 * the "this key has already been downloaded, do you want to download it again?" message if it applies.
	 * @return - 1 if the item was added, 0 if it was not added because it was a duplicate of an
	 * existing download, -1 if it was not added because the user has previously downloaded it
	 * and chose to not download it again.
	 */
	private synchronized int addDownloadItem(
			final FrostDownloadItem itemToAdd,
			final boolean askBeforeRedownload,
			final IntRef redownloadChoice,
			final boolean showErrors)
	{

		final FrostFileListFileObject flfToAdd = itemToAdd.getFileListFileObject(); 

		// If download tracking is enabled, check if file has already been downloaded
		if (Core.frostSettings.getBoolValue(SettingsClass.TRACK_DOWNLOADS_ENABLED)) {

			// Only check tracker if the file is not lingering around in finished state (is already tracked).
			// Meaning: If our new item is already marked "isTracked", it means that *this* exact
			// instance of the download object has been added to the download tracker already.
			if (!itemToAdd.isTracked()) {
				final TrackDownloadKeysStorage trackDownloadKeysStorage = TrackDownloadKeysStorage.inst();
				if (trackDownloadKeysStorage.searchItemKey(itemToAdd.getKey())) {
					final Language language = Language.getInstance();

					// This file has already been download, so ask the user whether they want to redownload it.
					// NOTE: at Frost startup, all items are always added with "askBeforeRedownload = false",
					// which means that the user will not be asked about re-adding items they've already queued.
					if( askBeforeRedownload ) {
						// Determine if the user has specified a "Yes to All" or "No to All" for a group of files.
						// The states are as follows: -1 = Ask for every file, 0 = "No to All", 1 = "Yes to All".
						int globalChoice = -1;
						if( redownloadChoice != null ) {
							globalChoice = redownloadChoice.value;
						}

						// determine the user's choice for this file
						int fileChoice = globalChoice;
						if( fileChoice == -1 ) {
							final String yesStr = language.getString("Common.yes");
							final String yesToAllStr = language.getString("Common.yesToAll");
							final String noStr = language.getString("Common.no");
							final String noToAllStr = language.getString("Common.noToAll");
							final Object[] options = { yesStr, yesToAllStr, noStr, noToAllStr }; // NOTE: the L&F determines the button order

							final int answer = MiscToolkit.showOptionDialog(
									null, // parented to the main Frost window
									language.formatMessage("DownloadPane.alreadyTrackedDialog.body", itemToAdd.getKey()),
									language.getString("DownloadPane.alreadyTrackedDialog.title"),
									MiscToolkit.DEFAULT_OPTION,
									MiscToolkit.QUESTION_MESSAGE,
									null,
									options,
									options[0]); // default highlight on "Yes" button

							if( answer == 0 ) { // user clicked "Yes"
								fileChoice = 1;
							} else if( answer == 1 ) { // user clicked "Yes to All"
								fileChoice = 1;
								globalChoice = 1;
							} else if( answer == 2 ) { // user clicked "No"
								fileChoice = 0;
							} else if( answer == 3 ) { // user clicked "No to All"
								fileChoice = 0;
								globalChoice = 0;
							} else { // "-1" = user closed dialog without making a choice
								fileChoice = 0; // treat as "no" (single file)
							}
						}

						// store the global choice
						if( redownloadChoice != null ) {
							redownloadChoice.value = globalChoice;
						}

						// skip the file?
						if( fileChoice == 0 ) {
							return -1; // user doesn't want to download this key again
						}
					}

					// Alright, they want to download the same key again. We must now mark this
					// *new* download key as "tracked" since it was already in the key storage.
					// It wouldn't have been added anyway, since the storage is indexed by unique
					// keys, but this just makes it even more explicitly clear (especially if we
					// ever need to change the behavior of the storage key index later).
					itemToAdd.setTracked(true);
				}
			}
		}

		// first check if the SHA or key already exist in the model, in which case we'll refuse to queue this one
		for (int x = 0; x < getItemCount(); x++) {
			final FrostDownloadItem item = getItemAt(x);

			// maybe null of manually added
			final FrostFileListFileObject flf = item.getFileListFileObject();

			if (flfToAdd != null && flf != null) {
				if (flfToAdd.getSha().equals(flf.getSha())) {
					// already in model (compared by SHA)
					if( showErrors ) {
						showAlreadyQueuedSingleDialog(itemToAdd);
					}
					return 0; // not added (duplicate)
				}
			}

			// FIXME: 0.7: if we add a new uri chk/name also check if we already download chk!
			// Problem: what if CHK is wrong, then we have to add chk/name. But in the reverse case we add chk/name and
			// name gets stripped because node reports rc=11, then we have 2 with same chk! ==> if node reports 11 then
			// check if we have already same plain chk.

			if (itemToAdd.getKey() != null && item.getKey() != null && item.getKey().equals(itemToAdd.getKey())) {
				// already in model (compared by key)
				if( showErrors ) {
					showAlreadyQueuedSingleDialog(itemToAdd);
				}
				return 0; // not added (duplicate)
			}
		}

		// now make sure that the name (/path/prefix_filename.ext) doesn't clash with any other files
		// in our model -- or on disk!
		// NOTE: since existing queued downloads are added to the model at Frost startup, this check
		// means that any *unfinished* downloads with clashing names will be made unique at the
		// next startup.
		ensureUniqueFilename(itemToAdd); // will update filename if required

		// add directory of item to recent used dirs
		FileTransferManager.inst().getDownloadManager().addRecentDownloadDir(itemToAdd.getDownloadDir());

		// not in model, add
		addItem(itemToAdd);
		return 1; // added
	}

	/**
	 * Pass in a download item and it will ensure that there's no file on disk or in the queue
	 * that clashes with that filename, and if so, it will generate a new (unique) name.
	 */
	public void ensureUniqueFilename(final FrostDownloadItem itemToCheck) {
		// do *nothing* if the item is already done.
		// NOTE: that's because we don't want finished downloads to clash against their own on-disk files.
		if( itemToCheck.getState() == FrostDownloadItem.STATE_DONE ) {
			return;
		}

		// begin by calculating the name *without* potential "(frost_#)" prefix, thus giving
		// us a clean name again in case the user is moving a file that previously needed a prefix
		String cleanPrefix = itemToCheck.getFilenamePrefix();
		// delete any previous "(frost_#)" prefix. this ensures that if we restart frost,
		// and the old "(frost_#)" prefix was taken too, we don't end up with "(frost_#) (frost_#)".
		if( cleanPrefix != null && !cleanPrefix.isEmpty() ) {
			cleanPrefix = cleanPrefix.replaceFirst("^\\(frost_[0-9]+\\)_?", "");
		}

		// begin by re-applying the clean prefix if it's not already clean
		// NOTE: this might be an empty prefix (empty string), which is fine; the frostdownloaditem
		// understands that an empty string means that we want no prefix at all
		if( cleanPrefix != null && !itemToCheck.getFilenamePrefix().equals(cleanPrefix) ) {
			itemToCheck.setFilenamePrefix(cleanPrefix);
		}

		// loop until we have a unique name
		int count = 1;
		while( true ) {
			boolean nameClashes = false;

			// first check if a file already exists on disk with that exact name
			// NOTE: this will not happen during the "add new downloads" dialog, since it refuses
			// to queue new items if they exist on disk. but it *can* happen if the user clicks the
			// "set prefix" and "set download dir" buttons on their already-queued downloads.
			// NOTE: getDownloadFilename() returns the path *with* the prefix, so that we check the final disk name.
			// NOTE: the "exists()" check under the hood knows if you're on a case-sensitive filesystem,
			// and will see "Cg_10" and "CG_10" as different filenames in that situation.
			if( fileAlreadyExists(itemToCheck.getDownloadFilename()) != null ) {
				nameClashes = true;
			}

			// it doesn't exist on disk, so check if we have a download queue item with that exact path/name
			else {
				final String newItemPath = itemToCheck.getDownloadFilename();
				for( int x = 0; x < getItemCount(); ++x ) {
					final FrostDownloadItem item = getItemAt(x);

					// don't compare against ourselves (for many reasons; first of all, we don't care
					// about our own name since we're the one being changed, and secondly we'd be in
					// an infinite loop if we constantly compare to our own updated name)
					if( item == itemToCheck ) {
						continue;
					}

					// we've already queued a different key with the exact same /path/prefix_filename.ext
					// NOTE: we do a case-insensitive check since the user's filesystem may be case-insensitive
					final String thisItemPath = item.getDownloadFilename();
					if( newItemPath != null && thisItemPath != null && newItemPath.equalsIgnoreCase(thisItemPath) ) {
						// same "/path/prefix_filename.ext", but different key
						nameClashes = true;
						break; // no need to loop through any more model items for now
					}
				}
			}

			if( nameClashes ) {
				// quietly rename to next possibly unique name. it will be checked during the next iteration.
				// we build the new filename using this pattern: "/path/(frost_2)_prefix_filename.ext".
				++count; // increment count (first clash = "2")

				// base "(frost_#)" prefix
				String nextNewPrefix = "(frost_" + count + ")";
				// add the user's old prefix if they had one
				if( cleanPrefix != null && !cleanPrefix.isEmpty() ) {
					nextNewPrefix = nextNewPrefix + "_" + cleanPrefix;
				}

				// now just set the new prefix, so that it will be tested on the next iteration
				// NOTE: since we grabbed the clean original prefix before manipulation,
				// we'll never accidentally do "(frost_2)_(frost_3)".
				itemToCheck.setFilenamePrefix(nextNewPrefix);
			}

			else {
				// there's no clash, so we can stop generating new names now
				break;
			}
		}
	}

	/**
	 * Checks if a path already exists on disk.
	 * NOTE: the "exists()" check under the hood knows if you're on a case-sensitive filesystem,
	 * and will see "Cg_10" and "CG_10" as different filenames in that situation.
	 * @param {String} downloadDir - the download path
	 * @param {String} fileName - the name of the file you want to download; be sure that it includes
	 * the prefix. If you're using FrostDownloadItem.getFileName() then the prefix is included.
	 * @return - a File object if a file or directory exists at that path, otherwise null
	 */
	public static java.io.File fileAlreadyExists(final String fullPath) {
		if( fullPath == null || fullPath.length() == 0 ) { return null; }
		final File file = new java.io.File(fullPath);
		return ( file.exists() ? file : null );
	}


	public void addExternalItem(final FrostDownloadItem i) {
		addItem(i);
	}

	/**
	 * Returns true if the model contains an item with the given sha.
	 */
	public synchronized boolean containsItemWithSha(final String sha) {
		for (int x = 0; x < getItemCount(); x++) {
			final FrostFileListFileObject flf = getItemAt(x).getFileListFileObject();
			if (flf != null) {
				if (flf.getSha().equals(sha)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Removes finished *non-external* downloads from the download model.
	 */
	public synchronized void removeFinishedDownloads() {
		final ArrayList<FrostDownloadItem> items = new ArrayList<FrostDownloadItem>();
		for (int i = getItemCount() - 1; i >= 0; i--) {
			final FrostDownloadItem dlItem = getItemAt(i);
			if (!dlItem.isExternal() && dlItem.getState() == FrostDownloadItem.STATE_DONE) {
				items.add(dlItem);
			}
		}
		if (items.size() > 0) {
			removeItems(items);
		}
	}

	/**
	 * Removes external downloads from the download model.
	 */
	public synchronized void removeExternalDownloads() {
		final ArrayList<FrostDownloadItem> items = new ArrayList<FrostDownloadItem>();
		for (int i = getItemCount() - 1; i >= 0; i--) {
			final FrostDownloadItem dlItem = getItemAt(i);
			if (dlItem.isExternal()) {
				items.add(dlItem);
			}
		}
		if (items.size() > 0) {
			removeItems(items);
		}
	}

	/**
	 * Restarts all of the given download items (if they are *NOT EXTERNAL* and their current state allows it)
	 */
	public void restartItems(final List<FrostDownloadItem> items) {
		final LinkedList<FrostDownloadItem> toRestart = new LinkedList<FrostDownloadItem>();
		final PersistenceManager pm = FileTransferManager.inst().getPersistenceManager();

		// make a list of all the download items that can be restarted
		for( final FrostDownloadItem dlItem : items ) {
			if( dlItem.isExternal() || dlItem.getState() == FrostDownloadItem.STATE_TRYING || dlItem.getState() == FrostDownloadItem.STATE_DECODING ) {
				// we can't restart items that were externally queued outside of this frost
				// instance (since we don't know the filename) or that are currently either
				// waiting to start via non-persistent FCP ("trying") or that are being decoded.
				continue;
			}
			// don't restart items whose data transfer from the node is currently in progress.
			// otherwise, we risk partially written or orphaned files, inconsistent dlItem states, etc.
			if( FileTransferManager.inst().getPersistenceManager() != null ) {
				if( FileTransferManager.inst().getPersistenceManager().isDirectTransferInProgress(dlItem) ) {
					continue;
				}
			}
			// we explicitly define the exact states that we can perform a restart from
			if( dlItem.getState() == FrostDownloadItem.STATE_FAILED
					|| dlItem.getState() == FrostDownloadItem.STATE_WAITING
					|| dlItem.getState() == FrostDownloadItem.STATE_PROGRESS
					|| dlItem.getState() == FrostDownloadItem.STATE_DONE ) {
				// if this item currently exists in the global queue, then we must prepare it for removal
				if( pm != null && pm.isItemInGlobalQueue(dlItem) ) {
					// tells the persistencemanager that we're expecting this item to be removed from the global queue
					dlItem.setInternalRemoveExpected(true);
				}

				// add this valid item to the list of items to restart
				toRestart.add(dlItem);
			}
		}

		// check if any files already exist in their download folder; in that case we can't
		// proceed without user interaction. the file would reach 100% downloaded and then
		// be unable to finish since Frost refuses to overwrite existing files, so ask the
		// user if they want to delete those files before proceeding.
		final Language language = Language.getInstance();
		int globalChoice = -1; // -1 = ask every time, 0 = skip all, 1 = restart (replace) all
		final ListIterator<FrostDownloadItem> iter = toRestart.listIterator();
		while( iter.hasNext() ) {
			final FrostDownloadItem dlItem = iter.next();

			final File targetFile = new File(dlItem.getDownloadFilename());
			if( targetFile.exists() ) {
				// determine the user's choice for this file
				int fileChoice = globalChoice;
				if( fileChoice == -1 ) {
					// the file already exists on disk, so ask the user if they want to restart it (delete the file),
					// or skip this file (not restart it); also has "restart all"/"skip all" options for convenience
					final String restartStr = language.getString("DownloadPane.restartAlreadyExists.restartOption");
					final String restartAllStr = language.getString("DownloadPane.restartAlreadyExists.restartAllOption");
					final String skipStr = language.getString("DownloadPane.restartAlreadyExists.skipOption");
					final String skipAllStr = language.getString("DownloadPane.restartAlreadyExists.skipAllOption");
					final Object[] options = { restartStr, restartAllStr, skipStr, skipAllStr }; // NOTE: the L&F determines the button order
					final int answer = MiscToolkit.showOptionDialog(
							null, // parented to the main Frost window
							language.formatMessage("DownloadPane.restartAlreadyExists.body", dlItem.getDownloadFilename()),
							language.getString("DownloadPane.restartAlreadyExists.title"),
							MiscToolkit.DEFAULT_OPTION,
							MiscToolkit.WARNING_MESSAGE,
							null,
							options,
							options[2]); // default highlight on "Skip" button

					if( answer == 0 ) { // user clicked "restart"
						fileChoice = 1;
					} else if( answer == 1 ) { // user clicked "restart all"
						fileChoice = 1;
						globalChoice = 1;
					} else if( answer == 2 ) { // user clicked "skip"
						fileChoice = 0;
					} else if( answer == 3 ) { // user clicked "skip all"
						fileChoice = 0;
						globalChoice = 0;
					} else { // "-1" = user closed dialog without making a choice
						fileChoice = 0; // treat as "skip" (single file)
					}
				}

				// skip the file? in that case just remove it from the list of files to restart
				if( fileChoice == 0 ) {
					iter.remove(); // safely removes the last item returned by the iterator's .next(); in this case the current item
				} else { // choice 1 = restart (delete) the duplicate
					boolean success = targetFile.delete();
					if( !success ){
						// failed to delete the file from disk, probably a permissions error
						iter.remove(); // skip this file
						MiscToolkit.showMessageDialog(
								null, // parented to the main Frost window
								language.formatMessage("DownloadPane.restartAlreadyExists.couldNotDelete.body", dlItem.getDownloadFilename()),
								language.getString("DownloadPane.restartAlreadyExists.couldNotDelete.title"),
								MiscToolkit.ERROR_MESSAGE);
					}
				}
			}
		}

		// abort now if there's no work to do
		if( toRestart.isEmpty() ) {
			return;
		}

		// now remove the items from the model, which causes them to be removed from the
		// downloads table *and* from the global queue (if they were there)
		removeItems(toRestart);

		// lastly, start a thread with a 1.5 second timer which will reset the progress state
		// of all items and re-add them to the downloads table
		new Thread() {
			@Override
			public void run() {
				// TODO: (slightly ugly) wait until item is removed from global queue
				// before starting download with same gq identifier
				// this hardcoded wait is a bit ugly but totally fine, because all "remove download"
				// FCP requests will definitely have been sent after 1.5 seconds,
				// and it doesn't matter if they've been removed yet; what matters is the
				// order of FCP commands, and the fact that the removal request has been sent
				// before our next download FCP command begins, since the Freenet node will process
				// commands in the order they were received!
				Mixed.wait(1500);
				// we must perform the actual GUI updates in the GUI thread, so queue the update now
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						for( final FrostDownloadItem dlItem : toRestart ) {
							// reset the most important variables that will allow the download to restart itself
							dlItem.setRetries(0);
							dlItem.setLastDownloadStopTime(0);
							dlItem.setEnabled(true); // this is important; it ensures that the downloads begin when re-added
							dlItem.setState(FrostDownloadItem.STATE_WAITING);

							// now we reset the progress of the item to the default "unstarted" status.
							// technically we could leave these as-is and let it automatically reset
							// everything when the download actually begins, but that's ugly since
							// queued items would stay in a weird, mixed old-and-new "Waiting 85% 18/21"
							// state until then... so we explicitly clear all the progress variables
							// before re-adding the item, so that it looks nicer in the downloads table.
							dlItem.setFinalized(false); // means this download's metadata transfer is not complete
							dlItem.setDownloadFinishedTime(0); // reset the "download finished at" timestamp
							dlItem.setDoneBlocks(0); // reset the current/required/total block counts to the default
							dlItem.setRequiredBlocks(0);
							dlItem.setTotalBlocks(0);
							dlItem.setErrorCodeDescription(null); // remove any existing "failure reason" error string
							dlItem.setCompletionProgRun(false); // ensures that the "exec on completion" program can run on completion

							// now just add the download to the downloads model and table
							// NOTE: false, false = we do NOT want it ask if the user wants to
							// re-download already downloaded keys, and we do NOT want it to
							// display error dialogs if the key was already in the model.
							addDownloadItem(dlItem, false, null, false);
						}
					}
				});
			}
		}.start();
	}

	//#DIEFILESHARING: The following is the old, broken "restart selected downloads" feature.
	// This function is no longer called from *anywhere*, *except* from "FileListManager.java"
	// which is related to the "shared files/my shared files" Frost feature that nobody uses,
	// and which has been disabled in Frost-Next. it's not worth wasting time rewriting that
	// function too; because screw the "shared files" feature. the code below was the old one
	// which risked stalling downloads (or even a hung/crashed GUI) if they tried to restart
	// their downloads.
	public boolean restartRunningDownloads(final List<FrostDownloadItem> dlItems) {

		// tells the persistencemanager that we're expecting this item to be removed from the global queue
		for (final FrostDownloadItem dlItem : dlItems) {
			dlItem.setInternalRemoveExpected(true);
		}

		removeItems(dlItems);

		new Thread() {
			@Override
			public void run() {
				// TODO: (ugly) wait until item is removed from global queue
				// before starting download with same gq identifier
				Mixed.wait(1500);
				for (final FrostDownloadItem dlItem : dlItems) {
					dlItem.setState(FrostDownloadItem.STATE_WAITING);
					dlItem.setRetries(0);
					dlItem.setLastDownloadStopTime(0);
					// NOTE: false, false = we do NOT want it ask if the user wants to
					// re-download already downloaded keys, and we do NOT want it to
					// display error dialogs if the key was already in the model.
					addDownloadItem(dlItem, false, null, false);
				}
			}
		}.start();
		return true;
	}

	/**
	 * This method enables / disables *non-external* download items in the model. If the enabled parameter is null, the current state
	 * of the item is inverted.
	 * 
	 * @param enabled
	 *            new state of the items. If null, the current state is inverted
	 */
	public synchronized void setAllItemsEnabled(final Boolean enabled) {
		for (int x = 0; x < getItemCount(); x++) {
			final FrostDownloadItem dlItem = getItemAt(x);
			if (!dlItem.isExternal() && dlItem.getState() != FrostDownloadItem.STATE_DONE) {
				dlItem.setEnabled(enabled);
				FileTransferManager.inst().getDownloadManager().notifyDownloadItemEnabledStateChanged(dlItem);
			}
		}
	}

	/**
	 * This method enables / disables *non-external* download items in the model. If the enabled parameter is null, the current state
	 * of the item is inverted.
	 * 
	 * @param enabled
	 *            new state of the items. If null, the current state is inverted
	 * @param items
	 *            items to modify
	 */
	public void setItemsEnabled(final Boolean enabled, final List<FrostDownloadItem> items) {
		for (final FrostDownloadItem item : items) {
			if (!item.isExternal() && item.getState() != FrostDownloadItem.STATE_DONE) {
				item.setEnabled(enabled);
				FileTransferManager.inst().getDownloadManager().notifyDownloadItemEnabledStateChanged(item);
			}
		}
	}

	/**
	 * Saves the download model to database.
	 */
	public void exitSave() throws StorageException {

		final List<FrostDownloadItem> itemList = getItems();
		try {
			FrostFilesStorage.inst().saveDownloadFiles(itemList);
		} catch (final Throwable e) {
			logger.log(Level.SEVERE, "Error saving download items", e);
			throw new StorageException("Error saving download items");
		}
	}

	/**
	 * Initializes the model
	 */
	public void initialize() throws StorageException {

		List<FrostDownloadItem> downloadItems;
		try {
			downloadItems = FrostFilesStorage.inst().loadDownloadFiles();
		} catch (final Throwable e) {
			logger.log(Level.SEVERE, "Error loading download items", e);
			throw new StorageException("Error loading download items");
		}
		for (final FrostDownloadItem dlItem : downloadItems) {
			// these items are being loaded from the model at startup, so we don't
			// want to interrupt the user with any questions.
			// NOTE: false, false = we do NOT want it ask if the user wants to
			// re-download already downloaded keys, and we do NOT want it to
			// display error dialogs if the key was already in the model.
			addDownloadItem(dlItem, false, null, false);
		}
	}
}
