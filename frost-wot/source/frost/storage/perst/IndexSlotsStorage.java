/*
  GlobalIndexSlotsStorage.java / Frost
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

import java.util.*;
import java.util.logging.*;

import org.garret.perst.*;
import org.joda.time.*;

import frost.*;
import frost.storage.*;

/**
 * Storage with an compound index of indexName and msgDate (int/long)
 */
public class IndexSlotsStorage extends AbstractFrostStorage implements ExitSavable {

    private static final Logger logger = Logger.getLogger(IndexSlotsStorage.class.getName());

    private static final String STORAGE_FILENAME = "gixSlots.dbs";

    // boards have positive indexNames (their primkey)
    public static final int FILELISTS = -1;
    public static final int REQUESTS  = -2;

    private IndexSlotsStorageRoot storageRoot = null;

    private static IndexSlotsStorage instance = new IndexSlotsStorage();

    protected IndexSlotsStorage() {
        super();
    }

    public static IndexSlotsStorage inst() {
        return instance;
    }

    private boolean addToIndices(final IndexSlot gis) {
        if( getStorage() == null ) {
            return false;
        }
        final boolean wasOk = storageRoot.slotsIndexIL.put(new Key(gis.getIndexName(), gis.getMsgDate()), gis);
        storageRoot.slotsIndexLI.put(new Key(gis.getMsgDate(), gis.getIndexName()), gis);
        return wasOk;
    }

    @Override
    public String getStorageFilename() {
        return STORAGE_FILENAME;
    }

    @Override
    public boolean initStorage() {
        final String databaseFilePath = buildStoragePath(getStorageFilename()); // path to the database file
        final long pagePoolSize = getPagePoolSize(SettingsClass.PERST_PAGEPOOLSIZE_INDEXSLOTS);

        open(databaseFilePath, pagePoolSize, false, true, true);

        storageRoot = (IndexSlotsStorageRoot)getStorage().getRoot();
        if (storageRoot == null) {
            // Storage was not initialized yet
            storageRoot = new IndexSlotsStorageRoot();
            // unique compound index of indexName and msgDate
            storageRoot.slotsIndexIL = getStorage().createIndex(new Class[] { Integer.class, Long.class }, true);
            // index for cleanup
            storageRoot.slotsIndexLI = getStorage().createIndex(new Class[] { Long.class, Integer.class }, true);
            getStorage().setRoot(storageRoot);
            commit(); // commit transaction
        }
        return true;
    }


    private static final Long minLongObj = new Long(Long.MIN_VALUE);
    private static final Integer minIntObj = new Integer(Integer.MIN_VALUE);
    private static final Integer maxIntObj = new Integer(Integer.MAX_VALUE);

    /**
     * Deletes any items with a date < maxDaysOld
     */
    public int cleanup(final int maxDaysOld) {

        // millis before maxDaysOld days
        // NOTE: we do not specify UTC here; we want the local day/time offset, then subtract the
        // number of days to keep messages, then convert that to the local start of day (midnight).
        // the result is a UTC timestamp representing the *local* start of that day, so if the user
        // is in GMT+7, the resulting timestamp would find any message older than X days -7 hours (UTC),
        // thereby deleting any messages that are X days older than the user's *local* time, exactly as we want it.
        final long date = new DateTime().minusDays(maxDaysOld + 1).withTimeAtStartOfDay().getMillis(); 
        final Long dateObj = new Long(date);

        // delete all items with msgDate < maxDaysOld
        int deletedCount = 0;

        beginExclusiveThreadTransaction();
        try {
            final Iterator<IndexSlot> i =
                storageRoot.slotsIndexLI.iterator(
                        new Key(
                                minLongObj,
                                minIntObj,
                                true),
                        new Key(
                                dateObj,
                                maxIntObj,
                                true),
                        GenericIndex.ASCENT_ORDER);
            while( i.hasNext() ) {
                final IndexSlot gis = i.next();
                storageRoot.slotsIndexIL.remove(gis); // also remove from IL index
                i.remove(); // remove from iterated LI index
                gis.deallocate(); // remove from Storage
                deletedCount++;
            }
        } finally {
            endThreadTransaction();
        }

        return deletedCount;
    }

    /**
     * Returns the slotindex for a particular day, or creates a new one if the index doesn't exist..
     * It is *crucial* that the date you pass in is the MIDNIGHT MILLISECOND TIMESTAMP
     * of the day you're interested in. To get that, use "x.withTimeAtStartOfDay().getMillis()"
     * on a *UTC* DateTime object.
     * NOTE: The "withTimeAtStartOfDay" is the replacement of the "midnight" methods in older
     * versions of Joda Time, and it doesn't have to refer to 00:00 due to things like daylight
     * savings time; however, it *always* refers to 00:00 midnight when regarding UTC timestamps.
     * As for the "indexName", it's always supposed to be the boardId of a Board object.
     */
    public IndexSlot getSlotForDateMidnightMillis(final int indexName, final long millisMidnight) {
        final Key dateKey = new Key(indexName, millisMidnight);
        if( !beginCooperativeThreadTransaction() ) {
            logger.severe("Failed to gather cooperative storage lock, returning new indexslot!");
            return new IndexSlot(indexName, millisMidnight);
        }
        IndexSlot gis;
        try {
            gis = storageRoot.slotsIndexIL.get(dateKey);
//        String s = "";
//        s += "getSlotForDateMidnightMillis: indexName="+indexName+", millisMidnight="+millisMidnight+"\n";
            if( gis == null ) {
                // not yet in storage
                gis = new IndexSlot(indexName, millisMidnight);
            }
        } finally {
            endThreadTransaction();
        }
        return gis;
    }

    public void storeSlot(final IndexSlot gis) {
        if( !beginExclusiveThreadTransaction() ) {
            logger.severe("Failed to gather exclusive storage lock, don't stored the indexslot!");
            return;
        }
        try {
            if( gis.getStorage() == null ) {
                gis.makePersistent(getStorage());
                addToIndices(gis);
            } else {
                gis.modify();
            }
        } finally {
            endThreadTransaction();
        }
    }

    public void exitSave() throws StorageException {
        close();
        storageRoot = null;
        System.out.println("INFO: GlobalIndexSlotsStorage closed.");
    }

    // tests
//    public static void main(String[] args) {
//        IndexSlotsStorage s = IndexSlotsStorage.inst();
//
//        s.initStorage();
//
//        IndexSlotsStorageRoot root = (IndexSlotsStorageRoot)s.getStorage().getRoot();
//
//        for( Iterator<IndexSlot> i = root.slotsIndexIL.iterator(); i.hasNext(); ) {
//            IndexSlot gi = i.next();
//            System.out.println("----GI-------");
//            System.out.println(gi);
//        }
//
//        s.getStorage().close();
//    }
}
