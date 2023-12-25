/*
 HashBlocklistManager.java / Frost-Next
 Copyright (C) 2015  "The Kitty@++U6QppAbIb1UBjFBRtcIBZg6NU"

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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.garret.perst.*;

import frost.Core;
import frost.SettingsClass;
import frost.fileTransfer.download.HashBlocklistTypes.MD5FileHash;
import frost.storage.ExitSavable;
import frost.storage.StorageException;
import frost.util.Mixed;

/**
 * Fully thread-safe and extremely fast hash-based blocklist manager.
 * Currently uses MD5s but is easily extensible to other formats.
 */
public class HashBlocklistManager
    implements ExitSavable, PropertyChangeListener
{
    private static final String STORAGE_FILENAME = "hashblocklist.dbs";


    /* singleton global instance */
    private volatile static HashBlocklistManager fUniqueInstance = null;
    private static final Logger logger = Logger.getLogger(HashBlocklistManager.class.getName());

    public static HashBlocklistManager getInstance()
    {
        // perform double-checked locking initialization of singleton
        HashBlocklistManager result = fUniqueInstance;
        if( result == null ) {
            synchronized(HashBlocklistManager.class) {
                result = fUniqueInstance;
                if( result == null )
                    fUniqueInstance = result = new HashBlocklistManager();
            }
        }
        return result;
    }


    /* per-instance variables */
    private volatile boolean fIsEnabled;
    private volatile boolean fIsPopulating = false;
    private volatile DBPtr<HashBlocklistStorageRoot> fDB = null;

    /**
     * Constructor which sets up the "enabled" flag and registers some listeners,
     * but doesn't actually open/initialize the database. That's done in Core.java
     * via "getInstance().initStorage()" *after* this constructor has executed.
     *
     * NOTE: We don't use any listeners for when the .md5 filename changes,
     * so by default it only opens/updates the database at startup. It's up
     * to the GUI to perform a manual rebuildDB() whenever the user chooses
     * a different .md5 path (or forces a rebuild of current file).
     */
    protected HashBlocklistManager()
    {
        fIsEnabled = Core.frostSettings.getBoolValue(SettingsClass.HASHBLOCKLIST_ENABLED);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.HASHBLOCKLIST_ENABLED, this);
    }


    /* High-level external interface... */

    /**
     * Looks up an MD5 string in the database, if db is currently open and ready and the hash
     * manager is enabled.
     *
     * @param   aHashStr  the 32-character MD5 string; is treated as case-insensitive base16 (hex)
     * @return  the disk path to the file matching that MD5 if the database query succeeded,
     *    otherwise null in all other cases (hashing disabled, db closed, db error, no match
     *    in db or invalid string input).
     */
    public String getMD5FilePath(
            final String aHashStr)
    {
        if( !fIsEnabled || aHashStr == null )
            return null;
        final DBPtr<HashBlocklistStorageRoot> db = fDB; // save local reference to avoid changes
        if( db != null && db.isReadyForExternalQuery ) {
            try {
                final MD5FileHash md5Obj = new MD5FileHash(aHashStr, /*aFilePath=*/null);
                final MD5FileHash foundHash = queryDBforMD5(db,
                        md5Obj.getTopMD5Bits(),
                        md5Obj.getBottomMD5Bits());
                if( foundHash != null )
                    return foundHash.getFilePath();
            } catch( final IllegalArgumentException ex ) {} // ignore invalid MD5 string
        }
        return null;
    }

    /**
     * Checks how many unique MD5 hashes are currently in the database.
     *
     * @return  number of hashes
     */
    public long getMD5HashCount()
    {
        final DBPtr<HashBlocklistStorageRoot> db = fDB; // save local reference to avoid changes
        if( db != null && db.isReadyForExternalQuery ) {
            final long md5HashCount = string_to_long(readDBSetting(db, "MD5_HASHCOUNT"));
            return md5HashCount;
        }
        return 0L;
    }

    /**
     * Gets the modification timestamp of the last time the MD5 database was updated.
     *
     * @return  unix timestamp in milliseconds, or 0 if the database contains no MD5 hashes
     */
    public long getLastMD5UpdateTime()
    {
        final DBPtr<HashBlocklistStorageRoot> db = fDB; // save local reference to avoid changes
        if( db != null && db.isReadyForExternalQuery ) {
            final long md5DatabaseUpdateTime = string_to_long(readDBSetting(db, "MD5_LASTUPDATED"));
            return md5DatabaseUpdateTime;
        }
        return 0L;
    }

    /**
     * Determines whether the database is busy being populated (which means no other external
     * user-triggered rebuildDB() calls should be started; although further calls would run sequentially).
     *
     * @return  true if it's currently busy populating the database, otherwise false
     */
    public boolean isPopulating()
    {
        return fIsPopulating;
    }

    /**
     * Closes and deletes the old database, then opens and populates a new database with
     * the data from the most up-to-date hashes (if missing, an empty db is created).
     *
     * @return  true if the database was deleted *and* successfully rebuilt (opened); otherwise
     *     false which means there was a problem with either deletion or opening. if false, the
     *     database may or may not be open afterwards (can happen if deletion's close-attempt fails).
     */
    public synchronized boolean rebuildDB()
    {
        if( !deleteDB() )
            return false;
        return openDB();
    }


    /* High-level ".md5 file" parsing... */

    /**
     * Determines if the MD5 database needs to be updated.
     *
     * @param   db  reference to a database
     * @return  true if rebuild is necessary, false if not (or if db is not a valid instance)
     */
    private boolean md5NeedsUpdate(
            final DBPtr<HashBlocklistStorageRoot> db)
    {
        if( db != null ) {
            // look for the user's .md5 file; if we find it and it's *older* than the database
            // update time, then no update is necessary. in all other cases we need to update.
            // NOTE: if the database was rebuilt with 0 records last time, it'll have no update
            // time value, hence "0", which means that whatever the .md5 text file timestamp is,
            // it will be newer than the database "0".
            try {
                final File md5TextFile = new File(getMD5TextFilePath());
                if( md5TextFile.isFile() ) {
                    final long md5TextModified = md5TextFile.lastModified();
                    final long md5DatabaseUpdateTime = string_to_long(readDBSetting(db, "MD5_LASTUPDATED"));
                    if( md5TextModified < md5DatabaseUpdateTime )
                        return false; // database is up to date
                }
            } catch( final Exception ex ) {}
        }
        return true;
    }

    /**
     * Populates the database with all new (unique) MD5 values from the user's current ".md5 file".
     * MUST ONLY BE CALLED FROM populateDB()!
     *
     * @param  db  reference to a database
     */
    private void populateDBwithMD5(
            final DBPtr<HashBlocklistStorageRoot> db)
    {
        if( db == null )
            return;

        try {
            final File md5TextFile = new File(getMD5TextFilePath());
            if( !md5TextFile.isFile() || md5TextFile.length() == 0 )
                return;

            final ArrayList<MD5FileHash> hashBuffer = new ArrayList<MD5FileHash>(25000);
            try( final BufferedReader br = new BufferedReader(new InputStreamReader(
                            new FileInputStream(md5TextFile), "UTF-8")) )
            {
                String line;
                while( true ) {
                    line = br.readLine();
                    if( line != null ) {
                        final MD5FileHash md5Obj = parseMD5Line(line);
                        if( md5Obj != null )
                            hashBuffer.add(md5Obj);
                    }
                    // if we've reached the end of the file, or we've got 20,000 records,
                    // then write them to the database and flush the current buffer.
                    // this is done to save memory, so that there's never more than a handful
                    // of megabytes of MD5 hash objects in memory at a time, even if the user
                    // has a massive .md5 text file with millions of lines.
                    if( line == null || hashBuffer.size() >= 20000 ) {
                        if( hashBuffer.size() > 0 ) {
                            // wait for an exclusive write-lock so that all readers are done before we write
                            db.readWriteTransaction(() -> {
                                // add all unique hash entries to the database (only the 1st seen
                                // instance of each unique MD5 hash is added).
                                long hashesAdded = 0L;
                                for( final MD5FileHash md5Obj : hashBuffer ) {
                                    if( md5Obj == null )
                                        continue;
                                    final MD5FileHash foundHash = work_queryDBforMD5(db,
                                        md5Obj.getTopMD5Bits(),
                                        md5Obj.getBottomMD5Bits());
                                    if( foundHash == null && work_addUniqueMD5toDB(db, md5Obj) )
                                        hashesAdded++;
                                }
                                if( hashesAdded > 0L ) {
                                    long dbHashCount = string_to_long(work_readDBSetting(db, "MD5_HASHCOUNT"));
                                    dbHashCount += hashesAdded;
                                    work_writeDBSetting(db, "MD5_HASHCOUNT", Long.toString(dbHashCount));
                                    work_writeDBSetting(db, "MD5_LASTUPDATED", Long.toString(System.currentTimeMillis()));
                                }
                                return true; // commit
                            });
                            hashBuffer.clear();
                        }
                    }
                    if( line == null )
                        break; // end of file reached
                }
            } catch( final Exception ex ) {} // ignore file opening/reading errors
        } catch( final Exception ex ) {
            // ignore various runtime errors
        }
    }

    /**
     * Parses a line of text in "[md5][space][filepath]" format.
     *
     * @param   aLine  the line of text
     * @return  a file hash object if line was valid, otherwise null
     */
    private MD5FileHash parseMD5Line(
            final String aLine)
    {
        // parse the line of text, expecting md5sum format:
        // "[md5][space][binaryflag(" "=ascii or "*"=binary)][filepath]"
        // the minimum length for valid line is 35 (32 md5 hex, 1 space, 1 flag, 1 filename letter).
        // the 33rd character (32nd index) must be whitespace (the space separator),
        // and the 34th character (33rd index) must be space or an asterisk.
        if( aLine != null && aLine.length() >= 35 && Character.isWhitespace(aLine.charAt(32))
                && ( Character.isWhitespace(aLine.charAt(33)) || aLine.charAt(33) == '*' ) ) {
            final String md5Hex = aLine.substring(0, 32);
            final String filePath = aLine.substring(34).trim();
            if( !filePath.isEmpty() ) {
                // this might be a valid line, so let's try parsing it...
                try {
                    final MD5FileHash md5Obj = new MD5FileHash(md5Hex, filePath);
                    return md5Obj;
                } catch( final IllegalArgumentException ex ) {} // ignore invalid MD5 string
            }
        }
        return null;
    }


    /* High-level database handling INTERNALS (not to be used externally or carelessly!)... */

    /**
     * High-level interface for extracting a String value from a database.
     *
     * @see  #work_readDBSetting
     */
    private String readDBSetting(
            final DBPtr<HashBlocklistStorageRoot> db,
            final String aSettingKey)
    {
        final OTransaction<String> t = new OTransaction<String>() {
            public boolean doWork() {
                final String val = work_readDBSetting(db, aSettingKey);
                this.setObj(val);
                return true; // commit
            }
        };
        db.readOnlyTransaction(t); // invoke transaction
        return t.getObj();
    }

    /**
     * High-level interface for looking up an MD5 long:long pair in the database.
     *
     * @see  #work_queryDBforMD5
     */
    private MD5FileHash queryDBforMD5(
            final DBPtr<HashBlocklistStorageRoot> db,
            final long aTopMD5Bits,
            final long aBottomMD5Bits)
    {
        final OTransaction<MD5FileHash> t = new OTransaction<MD5FileHash>() {
            public boolean doWork() {
                final MD5FileHash foundHash = work_queryDBforMD5(db,
                        aTopMD5Bits,
                        aBottomMD5Bits);
                this.setObj(foundHash);
                return true; // commit
            }
        };
        db.readOnlyTransaction(t); // invoke transaction
        return t.getObj();
    }

    /**
     * String to boolean value converter.
     */
    private boolean string_to_boolean(
            final String aValue)
    {
        boolean val = false;
        if( aValue != null && aValue.equalsIgnoreCase("true") ) {
            val = true;
        }
        return val;
    }

    /**
     * String to long value converter.
     */
    private long string_to_long(
            final String aValue)
    {
        long val = 0L;
        if( aValue != null ) {
            try {
                val = Long.parseLong(aValue);
            } catch( final Exception ex ) {} // ignore number formatting errors
        }
        return val;
    }

    /**
     * String to int value converter.
     */
    private int string_to_int(
            final String aValue)
    {
        int val = 0;
        if( aValue != null ) {
            try {
                val = Integer.parseInt(aValue);
            } catch( final Exception ex ) {} // ignore number formatting errors
        }
        return val;
    }

    /**
     * String to float value converter.
     */
    private float string_to_float(
            final String aValue)
    {
        float val = 0.0f;
        if( aValue != null ) {
            try {
                val = Float.parseFloat(aValue);
            } catch( final Exception ex ) {} // ignore number formatting errors
        }
        return val;
    }

    /**
     * Inner doWork() helper. Reads a string value from the settings-storage.
     *
     * @param   db  reference to a database
     * @param   aSettingKey  the key name of the setting
     * @return  a non-null object if the database query succeeded; otherwise null in all other
     *     cases (db closed, db error, or no match in db).
     */
    private String work_readDBSetting(
            final DBPtr<HashBlocklistStorageRoot> db,
            final String aSettingKey)
    {
        if( db != null ) {
            try {
                final String foundSetting = (String) db.root.settingsIndex.get(aSettingKey);
                return foundSetting; // null if not found
            } catch( final Exception ex ) {} // ignore database read errors
        }
        return null;
    }

    /**
     * Inner doWork() helper. Writes a string value to the settings-storage.
     * Overwrites any existing value at that key.
     *
     * @param   db  reference to a database
     * @param   aSettingKey  the key name of the setting
     * @param   aSettingValue  the string value for the setting (will not be written if null!);
     *     note that to avoid trying to store non-persistence capable objects, *all* database
     *     setting-values are forced to be strings!
     * @return  true if the database write succeeded; otherwise false in all other
     *     cases (db closed, db error).
     */
    private boolean work_writeDBSetting(
            final DBPtr<HashBlocklistStorageRoot> db,
            final String aSettingKey,
            final String aSettingValue)
    {
        if( db != null && aSettingValue != null ) {
            try {
                db.root.settingsIndex.set(aSettingKey, aSettingValue);
                return true;
            } catch( final Exception ex ) {
                // write errors will fall through to the false below
            }
        }
        return false;
    }

    /**
     * Inner doWork() helper. Deletes a setting from the settings-storage.
     *
     * @param   db  reference to a database
     * @param   aSettingKey  the key name of the setting
     * @return  true if the setting is deleted; otherwise false in all other
     *     cases (db closed, db error, SETTING KEY DID NOT EXIST (important to note)).
     */
    private boolean work_deleteDBSetting(
            final DBPtr<HashBlocklistStorageRoot> db,
            final String aSettingKey,
            final Object aSettingValue)
    {
        if( db != null && aSettingValue != null ) {
            try {
                db.root.settingsIndex.remove(aSettingKey);
                return true;
            } catch( final Exception ex ) {
                // "key not found" errors will fall through to the false below
            }
        }
        return false;
    }

    /**
     * Inner doWork() helper. Looks up an MD5 long:long pair in the database.
     *
     * @param   db  reference to a database
     * @param   aTopMD5Bits  the top 64-bit half of the 128-bit MD5.
     * @param   aBottomMD5Bits  the bottom 64-bit half of the 128-bit MD5.
     * @return  the MD5 file object if the database query succeeded; otherwise null in all other
     *     cases (db closed, db error, or no match in db).
     */
    private MD5FileHash work_queryDBforMD5(
            final DBPtr<HashBlocklistStorageRoot> db,
            final long aTopMD5Bits,
            final long aBottomMD5Bits)
    {
        if( db != null ) {
            try {
                final MD5FileHash foundHash = (MD5FileHash) db.root.md5HashIndex.get(new Key(new Object[]{
                    new Long(aTopMD5Bits),
                    new Long(aBottomMD5Bits)
                }));
                return foundHash; // null if not found
            } catch( final Exception ex ) {} // ignore database read errors
        }
        return null;
    }

    /**
     * Inner doWork() helper. Adds a MD5 hash object to the database.
     *
     * @param   db  reference to a database
     * @param   aMD5Hash  the object to add
     * @return  true if the hash was unique (not already in db) and is now inserted; otherwise false
     */
    private boolean work_addUniqueMD5toDB(
            final DBPtr<HashBlocklistStorageRoot> db,
            final MD5FileHash aMD5Hash)
    {
        if( db != null && aMD5Hash != null ) {
            try {
                return db.root.md5HashIndex.put(aMD5Hash);
            } catch( final Exception ex ) {} // ignore db write errors (should never happen).
        }
        return false;
    }


    /* Frost initialization and shutdown... */

    /**
     * Called at Frost's startup to open/initialize/rebuild the database.
     * For GUI sync reasons, loading is performed even if the hash manager is disabled.
     */
    public boolean initStorage() {
        return openDB();
    }

    /**
     * Called whenever the user toggles the "enabled" setting, and simply updates the
     * internal "enabled?" state (which affects several of the outward-facing methods).
     */
    @Override
    public void propertyChange(
            final PropertyChangeEvent evt)
    {
        if( evt.getPropertyName().equals(SettingsClass.HASHBLOCKLIST_ENABLED) ) {
            fIsEnabled = Core.frostSettings.getBoolValue(SettingsClass.HASHBLOCKLIST_ENABLED);
        }
    }

    /**
     * Closes the hash database when Frost is shutting down.
     */
    @Override
    public void exitSave()
        throws StorageException
    {
        try {
            closeDB();
            System.out.println("INFO: HashBlocklistStorage closed.");
        } catch( final Exception ex ) {
            logger.log(Level.SEVERE, "Error closing hashblocklist database", ex);
            throw new StorageException("Error closing hashblocklist database");
        }
    }


    /* Low-level, internal database handlers, mutually exclusive (synchronized)... */

    /**
     * Opens the database (if not already open), and initializes it if necessary.
     * It also automatically rebuilds your old database unless the .md5 file exists
     * and is older than the "last update" of the database.
     *
     * @return  true if the database is now open and populated with the latest data, otherwise false
     */
    private synchronized boolean openDB()
    {
        if( fDB != null )
            return true; // ignore since db is already open

        final String databaseFilePath = getStoragePath();
        final long pagePoolSize = getPagePoolSize(SettingsClass.PERST_PAGEPOOLSIZE_HASHBLOCKLIST);

        // open database with UTF-8 encoding of strings (halves disk space requirements), and concurrent
        // iterator support (so that iterators will try to keep position during threaded modification).
        // NOTE: we open using a temporary variable to avoid affecting fDB until initialization is complete.
        final Storage openedStorage = StorageFactory.getInstance().createStorage();
        openedStorage.setProperty("perst.string.encoding", "UTF-8");
        openedStorage.setProperty("perst.concurrent.iterator", Boolean.TRUE);
        openedStorage.open(databaseFilePath, pagePoolSize);

        // retrieve existing root, or initialize root object and all indexes (if new db)
        boolean isNewDB = false;
        HashBlocklistStorageRoot openedRoot = (HashBlocklistStorageRoot)openedStorage.getRoot();
        if( openedRoot == null ) {
            isNewDB = true;
            openedRoot = new HashBlocklistStorageRoot(openedStorage);
            openedStorage.setRoot(openedRoot);
            openedStorage.commit(); // save changes to disk
        }

        // update the database pointer with a new pointer (marked as "not yet ready for external queries")
        fDB = new DBPtr<HashBlocklistStorageRoot>(openedStorage, openedRoot);

        // populate new db, or rebuild/update old db (if necessary)
        if( isNewDB ) {
            populateDB(fDB); // populate new database immediately
        } else {
            // "auto" rebuild: since this is an old database, we'll determine if a rebuild is necessary
            if( md5NeedsUpdate(fDB) /*|| otherFutureFormatNeedsUpdate(fDB)*/ ) {
                return rebuildDB(); // delete the main fDB and open a new db to populate again from scratch
            }
        }

        // ready! mark the database pointer so that external callers can now begin reading the database
        fDB.isReadyForExternalQuery = true;
//        System.out.println("INFO: HashBlocklistStorage initialized.");

        return true;
    }

    /**
     * This is the root object for the database, containing all indexes (pointers to stored objects).
     */
    private class HashBlocklistStorageRoot
            extends Persistent
    {
        // stores general settings as String key -> Object value pairs
        public Index<Object> settingsIndex;
        // index on MD5FileHash's long:long (128 bit) md5 hash
        public FieldIndex<MD5FileHash> md5HashIndex;

        /**
         * Default constructor used by Perst when loading root object from disk.
         */
        public HashBlocklistStorageRoot() {}

        /**
         * Creates a new root object and its required indexes.
         *
         * @param  db  reference to the database for which we'll become the root object
         */
        public HashBlocklistStorageRoot(
                final Storage db)
        {
            super(db);
            settingsIndex = db.createIndex(String.class, // key type
                    true); // unique index
            md5HashIndex = db.<MD5FileHash>createFieldIndex(
                    MD5FileHash.class, // indexed class
                    new String[]{ "fTopMD5Bits", "fBottomMD5Bits" }, // names of indexed fields
                    true); // unique index
        }
    }

    /**
     * Populates the database with all hash types. Currently only MD5.
     * Uses a stable reference to a database which allows it to work on newly opened databases.
     *
     * @param  db  reference to a database
     */
    private void populateDB(
            final DBPtr<HashBlocklistStorageRoot> db)
    {
        if( db == null )
            return;
        fIsPopulating = true;
        populateDBwithMD5(db);
        fIsPopulating = false;
    }

    /**
     * Deletes the current database (good for performing a complete rebuild):
     * - Closes the old database if necessary.
     * - Deletes the old .dbs file.
     *
     * @return  true if the database was deleted, false if it couldn't be deleted.
     */
    private synchronized boolean deleteDB()
    {
        // gracefully close (if open) and delete the old .dbs file (if it exists)
        try {
            closeDB();
            final File dbsFile = new File(getStoragePath());
            if( dbsFile.isFile() )
                return dbsFile.delete();
        } catch( final Exception ex ) {
            logger.log(Level.SEVERE, "Unable to delete old hashblocklist database", ex);
            ex.printStackTrace();
        }
        return false;
    }

    /**
     * Safely closes the current database (if open).
     */
    private synchronized void closeDB()
    {
        final DBPtr<HashBlocklistStorageRoot> db = fDB; // save local reference to avoid changes
        if( db != null ) {
            // wait for an exclusive write-lock so that all readers are done before we close
            db.readWriteTransaction(() -> {
                // we have lock; disable and unset the global variable immediately to prevent new queued reads
                fDB.isReadyForExternalQuery = false;
                fDB = null;
                // commit pending transactions (if needed) and close the old database (via old pointer)
                db.storage.close();
                // SUPER IMPORTANT: workaround for Java 4 Windows bug JDK-4715154. the problem is that
                // Java's OS file handles aren't *always* released when closing files (usually waits until
                // garbage collection runs). on Windows that's a problem since that OS prevents delete,
                // move or rename until all file handles are released. Oracle/Sun blames Windows for that.
                // forced GC here is a workaround used by lots of Java software (the only fix that works).
                if( Mixed.getOSName().equals("Windows") )
                    System.gc();
                // it doesn't matter what "commit" value we return here, since the db is closed now
                return true;
            });
        }
    }

    /**
     * Provides an immutable, atomic reference to a database and its root object.
     *
     * This is necessary because we have the "delete and reopen" database mechanism,
     * which means that both our storage *and* our root will be changing simultaneously
     * and must be properly read by every thread as a single update (instead of two).
     *
     * NOTE: The shared pointer to this object itself must be marked volatile to ensure
     * that all threads see the same shared DBPtr object.
     *
     * NOTE: Always check "isReadyForExternalQuery" in user-facing functions (like public
     * hash lookup functions) before trying a transaction, to make sure that the database
     * has been fully opened and populated with data.
     */
    private static class DBPtr<T>
    {
        public final Storage storage;
        public final T root;
        public volatile boolean isReadyForExternalQuery;

        public DBPtr(
                final Storage s,
                final T r)
            throws NullPointerException
        {
            if( s == null || r == null )
                throw new NullPointerException("Null pointers to database are not allowed.");
            this.storage = s;
            this.root = r;
            this.isReadyForExternalQuery = false;
        }

        /**
         * Performs a database transaction using an exclusive lock (resolves when
         * there is no writer and no readers). Guarantees that you have exclusive
         * access to both read and write the database, without causing any corruption
         * or race conditions. This is the ONLY mode in which you're allowed to
         * perform writes during your transaction task!
         *
         * For more info, see transaction().
         */
        public boolean readWriteTransaction(
                final Transaction aTransaction)
            throws NullPointerException
        {
            // use exclusive lock: resolves when there are no other readers or writers
            return transaction(Storage.EXCLUSIVE_TRANSACTION, aTransaction);
        }

        /**
         * Performs a database transaction using a cooperative lock (resolves when
         * there is no exclusive writer). This allows you to perform parallel
         * reads with certainty that they won't be corrupted by any writers.
         * You are NOT UNDER ANY CIRCUMSTANCES allowed to write anything to the
         * database in your readOnlyTransaction.
         *
         * For more info, see transaction().
         */
        public boolean readOnlyTransaction(
                final Transaction aTransaction)
            throws NullPointerException
        {
            return transaction(Storage.COOPERATIVE_TRANSACTION, aTransaction);
        }

        /**
         * Represents a thread-safe, logical unit of work (such as adding records to a database).
         *
         * Executes immediately in the caller's thread, and blocks until lock is attained.
         * It then performs all the work in the transaction task, and finally commits.
         *
         * Performance is incredibly fast and it handles all errors, so you should *never* do
         * regular, unmanaged database access unless you know exactly why you want to do that.
         *
         * All thread-synchronization and commits/rollbacks happen on the database pointer
         * that this function belongs to. So it is vitally important that your doWork()
         * implementation maintains a consistent pointer to the same database instance.
         *
         *
         * - Proper usage (incl. Java 8 lambdas to reduce boilerplate code):
         * Be aware that your "doWork()" implementation is a separately invoked function,
         * which inherits *final* variables in the current scope. It is also extremely important
         * that you save a local reference to the database object, so that it doesn't change
         * globally while you're in the middle of work.
         *
         * final DBPtr<HashBlocklistStorageRoot> db = fDB; // save local reference to avoid changes
         * if( db != null ) { // also check "db.isReadyForExternalQuery" if fully-ready db is needed
         *     // wait for an exclusive write-lock so that all readers are done before we close
         *     db.readWriteTransaction(() -> {
         *         // do something...
         *         db.root.doSomething();
         *         // tell transaction worker to commit (as opposed to rollback)
         *         return true;
         *     });
         * }
         *
         *
         * - Warning: All new database records are kept in memory until committed to disk, so you
         * may run out of memory if adding a LOT of records during your transaction worker's
         * doWork() execution. You are NOT allowed to manually call commit() in your worker,
         * since that interferes with rollback protection and the threading model. So if you're
         * going to be adding a *lot* of memory-heavy database objects, it's best to split the
         * work into several transactions that each handle some objects. One way to achieve
         * that would be to do something like the following, to chunk the job into appropriately
         * sized units (in this case, ten thousand records per invocation). This code has not
         * been compiled or tested but demonstrates the idea:
         * for( int i=0; i < nRecords; i += 10000 ) {
         *     final int start=i; // "final" required for scoping
         *     db.readWriteTransaction(() -> {
         *         // do work for all records between start and start+10000 or nRecords (whichever ends first)
         *         for( int record=start, end=Math.min((start+10000), nRecords); record<end; ++record ) {
         *             //>do per-record work here...
         *         }
         *         return true; // commit
         *     });
         * }
         *
         *
         * - Error handling and committing or rolling back your transaction's work:
         * If your doWork() function throws any runtime exceptions, all work will be rolled
         * back and your worker aborted. To prevent this situation, it's recommended that
         * you wrap your inner doWork() code in try-catch, and then use the boolean return
         * system to tell us whether we should commit or roll back your work. If doWork()
         * returns false or an uncaught exception occurs, we will roll back the work;
         * otherwise (*only* if doWork() returns true), the work will be committed.
         * The exceptions will *not* be bubbled up, so you can't put any try-catch around
         * the transaction() invocation. It must be caught inside of the doWork() code.
         *
         *
         * - Special case:
         * Your read-only transactions will of course never write to the database, but you may
         * have many concurrent readers. For that reason, your doWork() should always return
         * true, so that "end transaction" is performed (instead of "rollback"). Rolling back
         * in cooperative mode ("check for changes and roll them back") is a lot more work than
         * just ending a transaction ("decrement cooperative lock count, and if 0, finally check
         * for and commit any changes"). So for speed reasons, always return true in read-only!
         *
         *
         * - MAJOR warning about rollbacks:
         * If your transaction worker adds entries to any of the Index objects, they will NOT
         * be rolled back by a Perst transaction rollback; the only thing that happens is that
         * the inserted/modified OBJECTS will not be saved into the database. But the index itself
         * has now got a reference to the new object, which will cause StorageException: "Invalid
         * object reference" if you try to retrieve those keys. It's up to YOU to remove any
         * new objects from your indexes if you decide to abort the transaction.
         * There is a Perst option, "perst.reload.objects.on.rollback", but it's very slow (reloads
         * ALL objects from disk) and very risky since it doesn't invalidate all old memory
         * references. It's also possible to simply re-fetch the root object, but that's risky
         * during multithreading since a read-only thread that accidentally sends a "rollback"
         * command ("return false") would switch the root for all other reading threads, and
         * it is likewise slow since it unloads all cached objects in memory. Manual care is better.
         * Furthermore, you should be very careful when adding items to your indexes since any
         * exceptions during the doWork() call will not remove the new entries. So depending on
         * what work you do, you need to very carefully handle exceptions inside of your doWork()
         * so that no index modifications leak out before the rollback; and THEN send "return
         * false" to roll back the OBJECTS too. This caveat is the same REGARDLESS of whether
         * you use this transaction() wrapper or direct database access, by the way.
         * The fact that this wrapper performs proper lock-releasing and rollbacks just makes
         * it even more explicit: You always have to diligently catch exceptions and undo work in
         * case of runtime errors, when you want to ensure database consistency.
         * The best way to take care of this is to make contained "work_*" helpers that check
         * all exceptions and return a failure/success code, to let you undo your index changes
         * if the work failed (see below).
         *
         *
         * - WARNING about nesting:
         * You CANNOT nest calls to functions that all use this wrapper, because the result
         * will be that the inner function calls commit on the unfinished work from the outer doWork().
         * If you want to make "work modules" for specific tasks inside your "doWork()" handler,
         * you should make them operate on the database instantly and manually (without checking
         * any locks). Just make sure your modules take a "DBPtr db" argument, to ensure that
         * they work on the exact same database instance. It is also recommended to name the functions
         * in some way (such as "work_*") to make it clear that they are worker modules. Also highly
         * recommended to ensure that workers handle all errors and never throw exceptions, to make
         * them easy to use.
         *
         *
         * @param   aTransactionMode  either Storage.EXCLUSIVE_TRANSACTION for read/write
         *     lock, or Storage.COOPERATIVE_TRANSACTION for read-only lock. you are not
         *     under ANY circumstances allowed to perform any database writes or object
         *     modifications during a read-only transaction.
         * @param   Transaction  the transaction worker; must properly implement doWork()
         * @return  true if database is open, false otherwise; even if true, no guarantee
         *     is made that your work finished without exception-rollback (see notes above).
         *     if you're working with multiple instances of a database, it's worth checking
         *     the return value so that you know whether the database was open or closed.
         *     you could then perhaps check if there's a new "fDB" global object and retry.
         */
        private boolean transaction(
                final int aTransactionMode,
                final Transaction aTransaction)
            throws NullPointerException
        {
            if( aTransaction == null )
                throw new NullPointerException("Invalid null transaction.");
            // acquire an exclusive (read/write) or cooperative (read-only) lock
            storage.beginThreadTransaction(aTransactionMode);
            // we now have lock; make sure the database is still open (because we may be an old
            // database reference to a now-closed database, if we waited on closeDB()).
            if( !storage.isOpened() ) {
                storage.endThreadTransaction(); // release lock again
                return false;
            }
            // perform the caller's requested workload, with rollback support
            boolean commit = false;
            try {
                // if doWork() throws an exception or returns false, the work will be rolled back
                commit = aTransaction.doWork();
                if( commit && storage.isOpened() )
                    storage.endThreadTransaction(); // commit to disk and release lock
            } catch( final Exception ex ) {
                System.out.println("ERROR: Runtime exception in transaction worker, rolling back latest transaction.");
                ex.printStackTrace();
            }
            if( !commit && storage.isOpened() ) {
                // NOTE: if they're in read-only mode, this actually rolls back the unsaved changes
                // from *all* threads, but that's okay since *nobody* is supposed to manipulate the
                // database during execution of the shared readOnlyTransaction() type.
                storage.rollbackThreadTransaction(); // roll back all pending disk writes and release lock
                // NOTE: this line would remove partial, invalid index changes, but is too risky when
                // multithreading is involved. see main comment block about this function.
                //root = (T) storage.getRoot(); // re-fetch root object to restore index changes to normal
            }
            return true;
        }
    }

    /**
     * (Internal) Java 8 lambda interface for database transactions.
     *
     * TIP: You can subclass this via "extends Transaction", and add extra fields such
     * as internal return values, etc, so that you can easily return data from the worker.
     */
    @FunctionalInterface
    private interface Transaction
    {
        public abstract boolean doWork();
    }

    /**
     * Simple extension of the Transaction interface which allows you to send/return an Object
     * to and from the worker. You can't use Java 8 lambdas together with regular classes, but that
     * actually serves as a way to visually differentiate these "object transaction" workers.
     *
     * This helper is most useful for getting non-final data *out* of the worker. Note that you
     * normally won't need to use "t.setObj()" to send data *into* the worker, since the variable
     * scope will allow the worker to access any "final" variables anyway. But there are times
     * when setting the object to some non-final value can help you at input too.
     *
     * Example usage:
     * final OTransaction<String> t = new OTransaction<String>() {
     *     public boolean doWork() {
     *         System.out.println("inside:"+this.getObj()); // "inside:going into the worker"
     *         this.setObj("coming out of the worker");
     *         return true; // commit
     *     }
     * };
     * t.setObj("going into the worker");
     * db.readWriteTransaction(t); // invoke transaction
     * System.out.println("outside:"+t.getObj()); // "outside:coming out of the worker"
     */
    private abstract class OTransaction<T>
            implements Transaction
    {
        public abstract boolean doWork();

        private T fObj;
        public void setObj(
                final T aValue)
        {
            fObj = aValue;
        }
        public T getObj()
        {
            return fObj;
        }
    }


    /* Helpers... */

    /**
     * Retrieves the current value from the user's ".md5 file" configuration option.
     *
     * @return  the path to the user's ".md5" hash-file (even if missing)
     */
    private String getMD5TextFilePath()
    {
        return Core.frostSettings.getValue(SettingsClass.HASHBLOCKLIST_MD5FILE);
    }

    /**
     * Get the path to the hashblocklist.dbs database file.
     *
     * @return  the path (usually "store/hashblocklist.dbs")
     */
    private String getStoragePath()
    {
        return buildStoragePath(STORAGE_FILENAME);
    }

    /**
     * Retrieves the configured page pool size (in bytes) for the provided key.
     *
     * @return  page pool size in bytes
     */
    private long getPagePoolSize(
            final String configKey)
    {
        long pagePoolSize = Core.frostSettings.getLongValue(configKey);
        if( pagePoolSize <= 0 )
            pagePoolSize = 1024L;
        pagePoolSize *= 1024L; // provided pagePoolSize is in KiB, we want bytes
        return pagePoolSize;
    }

    /**
     * Generate a path to a database file, using Frost's database storage folder.
     *
     * @return  path to the given database filename
     */
    private String buildStoragePath(
            final String filename)
    {
        final String storeDir = Core.frostSettings.getValue(SettingsClass.DIR_STORE);
        return storeDir + filename;
    }
}
