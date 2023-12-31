/*
  FrostFilesStorage.java / Frost
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

import org.garret.perst.*;

import frost.*;
import frost.fileTransfer.download.*;
import frost.fileTransfer.sharing.*;
import frost.fileTransfer.upload.*;
import frost.gui.*;
import frost.messaging.frost.boards.*;
import frost.storage.*;
import frost.util.gui.translation.*;

/**
 * A Storage for FrostDownloadFiles, FrostUploadFiles and SharedFiles.
 * Loaded during startup, and saved during shutdown (or during autosave).
 */
public class FrostFilesStorage extends AbstractFrostStorage implements ExitSavable {

    private static final Logger logger = Logger.getLogger(FrostFilesStorage.class.getName());

    private static final String STORAGE_FILENAME = "filesStore.dbs";

    private FrostFilesStorageRoot storageRoot = null;

    private static FrostFilesStorage instance = new FrostFilesStorage();

    protected FrostFilesStorage() {
        super();
    }

    public static FrostFilesStorage inst() {
        return instance;
    }

    @Override
    public String getStorageFilename() {
        return STORAGE_FILENAME;
    }

    @Override
    public boolean initStorage() {
        final String databaseFilePath = buildStoragePath(getStorageFilename()); // path to the database file
        final long pagePoolSize = getPagePoolSize(SettingsClass.PERST_PAGEPOOLSIZE_FILES);

        open(databaseFilePath, pagePoolSize, true, true, false);

        storageRoot = (FrostFilesStorageRoot)getStorage().getRoot();
        if (storageRoot == null) {
            // Storage was not initialized yet
            storageRoot = new FrostFilesStorageRoot();

            storageRoot.downloadFiles = getStorage().createScalableList();
            storageRoot.uploadFiles = getStorage().createScalableList();
            storageRoot.sharedFiles = getStorage().createScalableList();
            storageRoot.newUploadFiles = getStorage().createScalableList();

            storageRoot.hiddenBoardNames = getStorage().createScalableList();
            storageRoot.knownBoards = getStorage().createIndex(String.class, true);

            getStorage().setRoot(storageRoot);
            commit(); // commit transaction
        } else if( storageRoot.hiddenBoardNames == null ) {
            // add new root items
            storageRoot.hiddenBoardNames = getStorage().createScalableList();
            storageRoot.knownBoards = getStorage().createIndex(String.class, true);
            storageRoot.modify();
            commit(); // commit transaction
        }

        // delete the *dead* legacy Frost announcement board at startup if it's in our database.
        // furthermore, also delete the obnoxious "no subject" spam-board, since it's only spammed
        // as message attachments by losers with too much time on their hands.
        // this prevents them from keeping it in their Frost instance.
        ArrayList<KnownBoard> deleteBoards = null;
        for( final PerstKnownBoard pkb : storageRoot.knownBoards ) {
            final String boardName = pkb.getBoardName();
            if( boardName != null ) {
                if( boardName.toLowerCase().equals("frost-announce") || boardName.toLowerCase().contains("no subject") ) {
                    if( deleteBoards == null ) { deleteBoards = new ArrayList<KnownBoard>(); }
                    final KnownBoard kb = new KnownBoard(pkb.getBoardName(), pkb.getPublicKey(), pkb.getPrivateKey(), pkb.getDescription());
                    deleteBoards.add(kb);
                }
            }
        }
        if( deleteBoards != null ) {
            for( final KnownBoard kb : deleteBoards ) {
                deleteKnownBoard(kb);
            }
        }

        return true;
    }

    public void exitSave() throws StorageException {
        close();
        storageRoot = null;
        System.out.println("INFO: FrostFilesStorage closed.");
    }

    // only used for migration
    public void savePerstFrostDownloadFiles(final List<PerstFrostDownloadItem> downloadFiles) {
        beginExclusiveThreadTransaction();
        try {
            for( final PerstFrostDownloadItem pi : downloadFiles ) {
                pi.makePersistent(getStorage());
                storageRoot.downloadFiles.add(pi);
            }
            storageRoot.downloadFiles.modify();
        } finally {
            endThreadTransaction();
        }
    }

    /**
     * Removes all items from the given List and deallocates each item from Storage.
     * @param plst  IPersistentList of persistent items
     */
    private void removeAllFromStorage(final IPersistentList<? extends Persistent> plst) {
        for(final Iterator<? extends Persistent> i=plst.iterator(); i.hasNext(); ) {
            final Persistent pi = i.next();
            i.remove(); // remove from List
            pi.deallocate(); // remove from Storage
        }
        plst.clear(); // paranoia
    }

    public void saveDownloadFiles(final List<FrostDownloadItem> downloadFiles) {
        beginExclusiveThreadTransaction();
        try {
            removeAllFromStorage(storageRoot.downloadFiles); // delete all old items
            for( final FrostDownloadItem dlItem : downloadFiles ) {
                if( dlItem.isExternal() ) {
                    continue;
                }
                final PerstFrostDownloadItem pi = new PerstFrostDownloadItem(dlItem);
                storageRoot.downloadFiles.add(pi);
            }
        } finally {
            endThreadTransaction();
        }
    }

    public List<FrostDownloadItem> loadDownloadFiles() {
        final LinkedList<FrostDownloadItem> downloadItems = new LinkedList<FrostDownloadItem>();
        beginCooperativeThreadTransaction();
        try {
            for( final PerstFrostDownloadItem pi : storageRoot.downloadFiles ) {
                final FrostDownloadItem dlItem = pi.toFrostDownloadItem(logger);
                if( dlItem != null ) {
                    downloadItems.add(dlItem);
                }
            }
        } finally {
            endThreadTransaction();
        }
        return downloadItems;
    }

    // old comment from legacy Frost: "only used for migration".
    // new comment: this code is actually not called from ANYWHERE and can be deleted, but is kept in case it ever comes in handy...
    public void savePerstFrostUploadFiles(final List<PerstFrostUploadItem> uploadFiles) {
        beginExclusiveThreadTransaction();
        try {
            for( final PerstFrostUploadItem pi : uploadFiles ) {
                storageRoot.uploadFiles.add(pi);
            }
        } finally {
            endThreadTransaction();
        }
    }

    public void saveUploadFiles(final List<FrostUploadItem> uploadFiles) {
        beginExclusiveThreadTransaction();
        try {
            removeAllFromStorage(storageRoot.uploadFiles); // delete all old items
            for( final FrostUploadItem ulItem : uploadFiles ) {
                if( ulItem.isExternal() ) {
                    continue;
                }
                final PerstFrostUploadItem pi = new PerstFrostUploadItem(ulItem);
                storageRoot.uploadFiles.add(pi);
            }
        } finally {
            endThreadTransaction();
        }
    }

    public List<FrostUploadItem> loadUploadFiles(final List<FrostSharedFileItem> sharedFiles) {
        final LinkedList<FrostUploadItem> uploadItems = new LinkedList<FrostUploadItem>();
        final Language language = Language.getInstance();
        beginCooperativeThreadTransaction();
        try {
            for( final PerstFrostUploadItem pi : storageRoot.uploadFiles ) {
                final FrostUploadItem ulItem = pi.toFrostUploadItem(sharedFiles, logger, language);
                if( ulItem != null ) {
                    uploadItems.add(ulItem);
                }
            }
        } finally {
            endThreadTransaction();
        }
        return uploadItems;
    }

    // only used for migration
    public void savePerstFrostSharedFiles(final List<PerstFrostSharedFileItem> sfFiles) {
        beginExclusiveThreadTransaction();
        try {
            for( final PerstFrostSharedFileItem pi : sfFiles ) {
                pi.makePersistent(getStorage());
                storageRoot.sharedFiles.add(pi);
            }
            storageRoot.sharedFiles.modify();
        } finally {
            endThreadTransaction();
        }
    }

    public void saveSharedFiles(final List<FrostSharedFileItem> sfFiles) {
        beginExclusiveThreadTransaction();
        try {
            removeAllFromStorage(storageRoot.sharedFiles);
            for( final FrostSharedFileItem sfItem : sfFiles ) {
                final PerstFrostSharedFileItem pi = new PerstFrostSharedFileItem(sfItem);
                storageRoot.sharedFiles.add(pi);
            }
        } finally {
            endThreadTransaction();
        }
    }

    public List<FrostSharedFileItem> loadSharedFiles() {
        final LinkedList<FrostSharedFileItem> sfItems = new LinkedList<FrostSharedFileItem>();
        final Language language = Language.getInstance();
        beginCooperativeThreadTransaction();
        try {
            for( final PerstFrostSharedFileItem pi : storageRoot.sharedFiles ) {
                final FrostSharedFileItem sfItem = pi.toFrostSharedFileItem(logger, language);
                if( sfItem != null ) {
                    sfItems.add(sfItem);
                }
            }
        } finally {
            endThreadTransaction();
        }
        return sfItems;
    }

    public void saveNewUploadFiles(final List<NewUploadFile> newUploadFiles) {
        beginExclusiveThreadTransaction();
        try {
            removeAllFromStorage(storageRoot.newUploadFiles);
            for( final NewUploadFile nuf : newUploadFiles ) {
                nuf.makePersistent(getStorage());
                nuf.modify(); // for already persistent items

                storageRoot.newUploadFiles.add(nuf);
            }
        } finally {
            endThreadTransaction();
        }
    }

    public LinkedList<NewUploadFile> loadNewUploadFiles() {

        final LinkedList<NewUploadFile> newUploadFiles = new LinkedList<NewUploadFile>();
        beginCooperativeThreadTransaction();
        try {
            for( final NewUploadFile nuf : storageRoot.newUploadFiles ) {
                final File f = new File(nuf.getFilePath());
                if( !f.isFile() ) {
                    logger.warning("File (" + nuf.getFilePath() + ") is missing. File removed.");
                    continue;
                }
                newUploadFiles.add(nuf);
            }
        } finally {
            endThreadTransaction();
        }
        return newUploadFiles;
    }

    /**
     * Load all hidden board names.
     */
    public HashSet<String> loadHiddenBoardNames() {
        final HashSet<String> result = new HashSet<String>();
        beginCooperativeThreadTransaction();
        try {
            for( final PerstHiddenBoardName hbn : storageRoot.hiddenBoardNames ) {
                result.add(hbn.getHiddenBoardName());
            }
        } finally {
            endThreadTransaction();
        }
        return result;
    }

    /**
     * Clear table and save all hidden board names.
     */
    public void saveHiddenBoardNames(final HashSet<String> names) {
        beginExclusiveThreadTransaction();
        try {
            removeAllFromStorage(storageRoot.hiddenBoardNames);
            for( final String s : names ) {
                final PerstHiddenBoardName h = new PerstHiddenBoardName(s);
                storageRoot.hiddenBoardNames.add(h);
            }
        } finally {
            endThreadTransaction();
        }
    }

    private String buildBoardIndex(final Board b) {
        final StringBuilder sb = new StringBuilder();
        sb.append(b.getNameLowerCase());
        if( b.getPublicKey() != null ) {
            sb.append(b.getPublicKey());
        }
        if( b.getPrivateKey() != null ) {
            sb.append(b.getPrivateKey());
        }
        return sb.toString();
    }

    /**
     * @return  List of KnownBoard
     */
    public List<KnownBoard> getKnownBoards() {
        beginCooperativeThreadTransaction();
        final List<KnownBoard> lst;
        try {
            lst = new ArrayList<KnownBoard>();
            for( final PerstKnownBoard pkb : storageRoot.knownBoards ) {
                final KnownBoard kb = new KnownBoard(pkb.getBoardName(), pkb.getPublicKey(), pkb.getPrivateKey(), pkb
                        .getDescription());
                lst.add(kb);
            }
        } finally {
            endThreadTransaction();
        }
        return lst;
    }

    public synchronized boolean deleteKnownBoard(final Board b) {
        final String newIx = buildBoardIndex(b);
        beginExclusiveThreadTransaction();
        final boolean retval;
        try {
            final PerstKnownBoard pkb = storageRoot.knownBoards.get(newIx);
            if( pkb != null ) {
                storageRoot.knownBoards.remove(newIx, pkb);
                pkb.deallocate();
                retval = true;
            } else {
                retval = false;
            }
        } finally {
            endThreadTransaction();
        }
        return retval;
    }

    /**
     * Called with a list of Board, should add all boards that are not contained already
     * @param {List<Board} lst - List of Board objects to add
     * @param {boolean} forceDescriptions - if true, we'll forcibly update descriptions
     * of any boards that are already in storage (this flag should only be used by the
     * "import defaults" feature, to update people's outdated boards)
     * @return - the number of uniquely added boards (doesn't count merely updated descriptions)
     */
    public synchronized int addNewKnownBoards(final List<? extends Board> lst, final boolean forceDescriptions) {
        if( lst == null || lst.size() == 0 ) {
            return 0;
        }
        beginExclusiveThreadTransaction();
        int added = 0;
        try {
            for( final Board b : lst ) {
                // avoid the *dead* legacy Frost announce board, as well as the obnoxious "no subject"
                // board. the latter is spammed as message attachments by losers with too much time
                // on their hands. this prevents us from auto-learning about that useless board from their spam.
                final String boardName = b.getName();
                if( boardName != null ) {
                    if( boardName.toLowerCase().equals("frost-announce") || boardName.toLowerCase().contains("no subject") ) {
                        continue;
                    }
                }

                // NOTE: the board-"index" is just a key-string generated by concatening its lowercase
                // name and its public and private keys, so if the user already has that exact board
                // with those exact public/private keys (or lack of them), then this "index" exists.
                final String newIx = buildBoardIndex(b);

                // construct a new known board object with the board's name, keys and description
                final PerstKnownBoard pkb = new PerstKnownBoard(b.getName(), b.getPublicKey(), b.getPrivateKey(), b.getDescription());

                // NOTE: storageRoot.knownBoards is a Perst "Index<PerstKnownBoard>", with "keys must
                // be unique" enabled; so the "put" will be rejected (false) if the index/key exists.
                if( storageRoot.knownBoards.put(newIx, pkb) ) {
                    // key wasn't in the index and has now been added!
                    added++;
                } else if( forceDescriptions ) {
                    // the key already exists in the index, but we're told to force-update board descriptions
                    // NOTE: "set" removes any existing key and associates the new value
                    //
                    // ALSO NOTE: this does NOT update the user's descriptions for boards they've added
                    // in their board-tree (to the left in the GUI), because those descriptions are
                    // stored in their boards.xml file. but if they delete the board from their tree,
                    // it WILL show up in the "known boards manager" with the official description.
                    // that's because when they delete a board from the tree, it runs "addNewKnownBoards"
                    // without "forceDescriptions", so it won't overwrite our correct description entry.
                    // furthermore; Frost-Next user's databases will always *either* already know about
                    // the default, included boards, *or* will import them later (and correct all descriptions).
                    // so it doesn't matter if some people share boards with the wrong descriptions etc,
                    // since "addNewKnownBoards" will reject the wrong descriptions for any boards it already
                    // knows about. these facts combine to ensure that our default descriptions stick!
                    storageRoot.knownBoards.set(newIx, pkb);
                }
            }
        } finally {
            endThreadTransaction();
        }
        return added;
    }
}
