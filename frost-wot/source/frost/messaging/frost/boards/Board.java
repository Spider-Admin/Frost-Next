/*
  Board.java / Frost
  Copyright (C) 2003  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.frost.boards;

import java.util.*;

import javax.swing.*;

import frost.*;
import frost.storage.perst.messages.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;
import org.joda.time.*;
import org.joda.time.format.*;
import java.util.logging.*;

/**
 * Represents a board in the board tree.
 */
@SuppressWarnings("serial")
public class Board extends AbstractNode {

    private static Language language = Language.getInstance();

    private PerstFrostBoardObject perstFrostBoardObject = null;

    private Integer startDaysBack = null; // day to start downloading backlog from (allows user to skip recent days)
    private Integer lastAllDayStarted = null; // current day we've *started* downloading during "all days" scan
    private int lastAllMaxDays = 0; // the "max days" count of the current "all days" scan (used for GUI)
    private DateTime lastAllDayStartedDate = null; // the actual DateTime of the date lastAllDayStarted refers to
    private boolean allDaysUpdating = false; // true if doing "all days" scan; means we'll show the "Day x/y" GUI indicator
    
    private boolean autoUpdateEnabled = true; // must apply, no default
    private String boardDescription = null;
    private final String boardFileName;
    private Boolean hideUnsigned = null;
    private Boolean hideBAD = null;
    private Boolean hideNEUTRAL = null;
    private Boolean hideGOOD = null;
    private Integer hideMessageCount = null;
    private Boolean hideMessageCountExcludePrivate = null;

    private Boolean storeSentMessages = null;

    // if isConfigured=true then below options may apply
    private boolean isConfigured = false;

    private boolean isUpdating = false;
    private long lastUpdateStartMillis = -1; // never updated
    private long lastBackloadUpdateFinishedMillis = -1; // never finished
    // following: if set to null then the default will be returned
    private Integer maxMessageDisplay = null;
    private Integer maxMessageDownload = null;

    private int unreadMessageCount = 0;
    private int numberBlocked = 0; // number of blocked messages for this board
    private String privateKey = null;

    private String publicKey = null;

    private boolean spammed = false;

    private int timesUpdatedCount = 0;

    private boolean hasFlaggedMessages = false;
    private boolean hasStarredMessages = false;

	private DateTime lastDayBoardUpdated = null;

    // Long is the dateMillis used in MessageThread, a unique String per day
    private final Hashtable<Long,BoardUpdateInformation> boardUpdateInformations = new Hashtable<Long,BoardUpdateInformation>();

    private static final BoardUpdateInformationComparator boardUpdateInformationComparator = new BoardUpdateInformationComparator();

    private boolean dosForToday = false;
    private boolean dosForBackloadDays = false;
    private boolean dosForAllDays = false;

    private static final ImageIcon writeAccessIcon = MiscToolkit.loadImageIcon("/data/key.png");
    private static final ImageIcon writeAccessNewIcon = MiscToolkit.loadImageIcon("/data/key_add.png");
    private static final ImageIcon writeAccessSpammedIcon = MiscToolkit.loadImageIcon("/data/key_delete.png");
    private static final ImageIcon readAccessIcon = MiscToolkit.loadImageIcon("/data/lock.png");
    private static final ImageIcon readAccessNewIcon = MiscToolkit.loadImageIcon("/data/lock_add.png");
    private static final ImageIcon readAccessSpammedIcon = MiscToolkit.loadImageIcon("/data/lock_delete.png");
    private static final ImageIcon boardIcon = MiscToolkit.loadImageIcon("/data/comments.png");
    private static final ImageIcon boardNewIcon = MiscToolkit.loadImageIcon("/data/comments_add.png");
    private static final ImageIcon boardSpammedIcon = MiscToolkit.loadImageIcon("/data/comments_delete.png");

    /**
     * Constructs a new Board
     */
    public Board(final String name, final String description) {
        this(name, null, null, description);
    }

    /**
     * Constructs a new FrostBoardObject wich is a Board.
     * @param name
     * @param pubKey
     * @param privKey
     * @param description the description of the board, or null if none.
     */
    public Board(final String name, final String pubKey, final String privKey, final String description) {
        super(name);
        setDescription(description);
        boardFileName = Mixed.makeFilename(getNameLowerCase());
        setPublicKey(pubKey);
        setPrivateKey(privKey);
    }

    /**
     * This method returns true if this board has new messages. In case
     * this board is a folder, it recurses all folders and boards within
     * and returns true if any of them have new messages. It returns false
     * otherwise.
     * @return true if there are new messages. False otherwise.
     */
    @Override
    public boolean containsUnreadMessages() {
        if (getUnreadMessageCount() > 0) {
            return true;
        } else {
            return false;
        }
    }

    public void decUnreadMessageCount() {
        unreadMessageCount--;
    }

    public void incTimesUpdatedCount() {
        timesUpdatedCount++;
    }

    public boolean getAutoUpdateEnabled() {
        if (!isConfigured()) {
            return true;
        }
        return autoUpdateEnabled;
    }

    public int getBlockedCount() {
        return numberBlocked;
    }

    // returns a sanitized version of the board name (as opposed to getName() which is the real name)
    public String getBoardFilename() {
        return boardFileName;
    }

    public String getDescription() {
        return boardDescription;
    }

    // IMPORTANT: this is the only function that is allowed to set the boardDescription variable!
    // it cleans up the string so that the first encountered alphabetical character is uppercase,
    // and so that the string ends in a period if no valid end-of-sentence punctuation exists.
    public void setDescription(final String desc) {
        boardDescription = Mixed.makeCleanString(desc, true); // true = accept smileys
    }

    public boolean getStoreSentMessages() {
        if (!isConfigured() || storeSentMessages == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.STORAGE_STORE_SENT_MESSAGES);
        }
        return storeSentMessages.booleanValue();
    }

    public Boolean getStoreSentMessagesObj() {
        return storeSentMessages;
    }

    public boolean getHideUnsigned() {
        if (!isConfigured() || hideUnsigned == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_HIDE_UNSIGNED);
        }
        return hideUnsigned.booleanValue();
    }

    public Boolean getHideUnsignedObj() {
        return hideUnsigned;
    }

    public boolean getHideBAD() {
        if (!isConfigured() || hideBAD == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_HIDE_BAD);
        }
        return hideBAD.booleanValue();
    }

    public Boolean getHideBADObj() {
        return hideBAD;
    }

    public boolean getHideNEUTRAL() {
        if (!isConfigured() || hideNEUTRAL == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_HIDE_NEUTRAL);
        }
        return hideNEUTRAL.booleanValue();
    }

    public Boolean getHideNEUTRALObj() {
        return hideNEUTRAL;
    }

    public boolean getHideGOOD() {
        if (!isConfigured() || hideGOOD == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_HIDE_GOOD);
        }
        return hideGOOD.booleanValue();
    }

    public Boolean getHideGOODObj() {
        return hideGOOD;
    }

    public int getHideMessageCount() {
        if (!isConfigured() || hideMessageCount == null) {
            // return default
            return Core.frostSettings.getIntValue(SettingsClass.MESSAGE_HIDE_COUNT);
        }
    	return hideMessageCount.intValue();
    }

    public Integer getHideMessageCountObj() {
        return hideMessageCount;
    }

    public boolean getHideMessageCountExcludePrivate() {
        if (!isConfigured() || hideMessageCountExcludePrivate == null) {
            // return default
            return Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_HIDE_COUNT_EXCLUDE_PRIVATE);
        }
    	return hideMessageCountExcludePrivate.booleanValue();
    }

    public Boolean getHideMessageCountExcludePrivateObj() {
    	return hideMessageCountExcludePrivate;
    }

    public long getLastUpdateStartMillis() {
        return lastUpdateStartMillis;
    }
    public long getLastBackloadUpdateFinishedMillis() {
        return lastBackloadUpdateFinishedMillis;
    }

    public int getMaxMessageDisplay() {
        if (!isConfigured() || maxMessageDisplay == null) {
            // return default
            return Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DISPLAY);
        }
        return maxMessageDisplay.intValue();
    }
    public Integer getMaxMessageDisplayObj() {
        return maxMessageDisplay;
    }

    public int getMaxMessageDownload() {
        if (!isConfigured() || maxMessageDownload == null) {
            // return default
            return Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DOWNLOAD);
        }
        return maxMessageDownload.intValue();
    }
    public Integer getMaxMessageDownloadObj() {
        return maxMessageDownload;
    }

    public int getUnreadMessageCount() {
        return unreadMessageCount;
    }

    public int getTimesUpdatedCount() {
        return timesUpdatedCount;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public String getStateString() {
        if (isReadAccessBoard()) {
            return language.getString("Board.boardState.readAccess");
        } else if (isWriteAccessBoard()) {
            return language.getString("Board.boardState.writeAccess");
        } else if (isPublicBoard()) {
            return language.getString("Board.boardState.publicBoard");
        }
        return language.getString("Board.boardState.invalid");
    }

    public ImageIcon getStateIcon() {
        if (isReadAccessBoard()) {
            if( isSpammed() || isDosForToday() ) {
                return readAccessSpammedIcon;
            } else if( containsUnreadMessages() ) {
                return readAccessNewIcon;
            } else {
                return readAccessIcon;
            }
        } else if (isWriteAccessBoard()) {
            if( isSpammed() || isDosForToday() ) {
                return writeAccessSpammedIcon;
            } else if( containsUnreadMessages() ) {
                return writeAccessNewIcon;
            } else {
                return writeAccessIcon;
            }
        } else if (isPublicBoard()) {
            if( isSpammed() || isDosForToday() ) {
                return boardSpammedIcon;
            } else if( containsUnreadMessages() ) {
                return boardNewIcon;
            } else {
                return boardIcon;
            }
        }
        // fallback
        return boardIcon;
    }

    // number of days backwards to start downloading an "all days backwards" scan
    // from (allows us to skip recent days). range is 1 (today) to max days to download.
    public int getStartDaysBack() {
      if( !isConfigured() || startDaysBack == null )
          return 1;
      else
          return startDaysBack;
    }

    public Integer getStartDaysBackObj() {
        return startDaysBack;
    }

    // number of days back we are currently at in our All Days Backwards scan
    public int getLastAllDayStarted() {
        if( lastAllDayStarted != null )
            return lastAllDayStarted;
        else return 0;
    }

    public Integer getLastAllDayStartedObj() {
        return lastAllDayStarted;
    }

    // the most recent date of when we've performed an "all days" board update
    public String getLastDayBoardUpdated() {
        if( lastDayBoardUpdated != null )
            return lastDayBoardUpdated.toString("yyyy-MM-dd"); // return the date in "2008-12-24" ISO format
        else
            return "never";
    }

    public DateTime getLastDayBoardUpdatedObj() {
        return lastDayBoardUpdated;
    }

    /**
     * true if "all days backwards" scan was interrupted/stopped and can be resumed
     */
    public boolean isResumeable() {
        boolean resumable = false;

        /**
         * Resumable criteria:
         * - Board is not currently updating.
         * - We've got a known date of having last started/performed an "all days" scan.
         * - We know about the last day we started downloading during that "all days" scan.
         * - They hadn't yet *started* the *final* day (started must be less than maxdays-1)
         *   during that scan. If they had *started* the final day, we treat it as completed,
         *   since there is no way to interrupt an ongoing day-scan except by closing Frost,
         *   so in almost 100% of circumstances they would have finished that final day.
         *   NOTE: The reason for -1 is that dayStarted is 0-indexed and maxDays isn't.
         *   So started == 0 means that day 1 was started (day 1 is "today", and is a
         *   "days backwards" offset of 0, hence the need for this conversion). Meanwhile,
         *   the maxDays value is 1-indexed (so day 1 means today). Subtracting 1 turns
         *   the "maxDays" value into the correct "final day" value in "startedDay" terms.
         */
        if( !isUpdating()
                && (lastDayBoardUpdated != null)
                && (lastAllDayStarted != null)
                && (lastAllDayStarted < (getMaxMessageDownload() - 1))
        ) {
            resumable = true;
        }

        return resumable;
    }

    
    //////////////////////////////////////////////
    // From BoardStats

    public void incBlocked() {
        numberBlocked++;
    }

    public void incUnreadMessageCount() {
        unreadMessageCount++;
    }

    public boolean isConfigured() {
        return isConfigured;
    }

    @Override
    public boolean isBoard() {
        return true;
    }

    public boolean isPublicBoard() {
        if (publicKey == null && privateKey == null) {
            return true;
        }
        return false;
    }

    public boolean isReadAccessBoard() {
        if (publicKey != null && privateKey == null) {
            return true;
        }
        return false;
    }

    public boolean isWriteAccessBoard() {
        if (publicKey != null && privateKey != null) {
            return true;
        }
        return false;
    }

    public boolean isSpammed() {
        return spammed;
    }

    public boolean isUpdating() {
        return isUpdating;
    }

    public void resetBlocked() {
        numberBlocked = 0;
    }

    public void setAutoUpdateEnabled(final boolean val) {
        autoUpdateEnabled = val;
    }

    public void setConfigured(final boolean val) {
        isConfigured = val;
    }

    public void setStoreSentMessages(final Boolean val) {
        storeSentMessages = val;
    }

    public void setHideUnsigned(final Boolean val) {
        hideUnsigned = val;
    }

    public void setHideBAD(final Boolean val) {
        hideBAD = val;
    }

    public void setHideNEUTRAL(final Boolean val) {
        hideNEUTRAL = val;
    }

    public void setHideGOOD(final Boolean val) {
        hideGOOD = val;
    }

    public void setHideMessageCount(final Integer val) {
    	hideMessageCount = val;
    }

    public void setHideMessageCountExcludePrivate(final Boolean val) {
    	hideMessageCountExcludePrivate = val;
    }

    public void setLastUpdateStartMillis(final long millis) {
        lastUpdateStartMillis = millis;
    }
    public void setLastBackloadUpdateFinishedMillis(final long millis) {
        lastBackloadUpdateFinishedMillis = millis;
    }

    public void setMaxMessageDays(final Integer val) {
        maxMessageDisplay = val;
    }

    public void setMaxMessageDownload(final Integer val) {
        maxMessageDownload = val;
    }

    public void setUnreadMessageCount(final int val) {
        unreadMessageCount = val;
    }

    public void setPrivateKey(String val) {
        if (val != null) {
            val = val.trim();
            if( val.length() == 0 ) {
                val = null;
            }
        }
        privateKey = val;
    }

    public void setPublicKey(String val) {
        if (val != null) {
            val = val.trim();
            if( val.length() == 0 ) {
                val = null;
            }
        }
        publicKey = val;
    }

    public void setSpammed(final boolean val) {
        spammed = val;
    }

    public void setUpdating(final boolean val) {
        isUpdating = val;
    }

    //Number of days backwards to start downloading from
    //Range is 1 (today) to max days to download
    public void setStartDaysBack(final Integer val) {
        startDaysBack = val;
    }

    //Number of days back we are currently at
    public void setLastAllDayStarted(final Integer val) {
        lastAllDayStarted = val;
    }

    public void setLastAllMaxDays(final int val) {
        lastAllMaxDays = val;
    }
    public int getLastAllMaxDays() {
        return lastAllMaxDays;
    }
    public void setLastAllDayStartedDate(final DateTime val) {
        lastAllDayStartedDate = val;
    }
    public DateTime getLastAllDayStartedDate() {
        return lastAllDayStartedDate;
    }
    public void setAllDaysUpdating(final boolean val) {
        allDaysUpdating = val;
    }
    public boolean isAllDaysUpdating() { // true if an "All days backwards" scan is running
        return allDaysUpdating;
    }
    
    // The most recent day that the board was updating
    public void setLastDayBoardUpdated(String day) {
        if( day.equals("never") ) {
            lastDayBoardUpdated = null;
        } else {
            DateTimeFormatter df = DateTimeFormat.forPattern("yyyy-MM-dd").withZone(DateTimeZone.UTC); // ISO date format, i.e. "2008-12-24", treated as UTC time
            lastDayBoardUpdated = DateTime.parse(day, df);
        }
    }

    public void setLastDayBoardUpdated(DateTime day) {
        lastDayBoardUpdated = day;
    }

    /**
     * Returns true if board is allowed to be updated.
     * If a board is already updating only not running threads will be started.
     */
    public boolean isManualUpdateAllowed() {
        if ( !isBoard() || isSpammed() ) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Returns true if board is allowed to be updated.
     * Also checks if board update is already running.
     */
    public boolean isAutomaticUpdateAllowed() {
        if ( !isBoard() || isSpammed() || isUpdating() ) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Tells the board that a new message was received right now.
     * Needed for selective board update.
     * We can't use unreadMessageCount for this because this field is updated
     * also if a message is mark unread.
     */
    public void newMessageReceived() {
    }

    public boolean hasFlaggedMessages() {
        return hasFlaggedMessages;
    }
    public void setFlaggedMessages(final boolean newFlaggedMessages) {
        this.hasFlaggedMessages = newFlaggedMessages;
    }

    public boolean hasStarredMessages() {
        return hasStarredMessages;
    }
    public void setStarredMessages(final boolean newStarredMessages) {
        this.hasStarredMessages = newStarredMessages;
    }

    public PerstFrostBoardObject getPerstFrostBoardObject() {
        return perstFrostBoardObject;
    }

    public void setPerstFrostBoardObject(final PerstFrostBoardObject perstFrostBoardObject) {
        this.perstFrostBoardObject = perstFrostBoardObject;
    }

    
    /////// BoardUpdateInformation methods //////

    public BoardUpdateInformation getBoardUpdateInformationForDay(final long dateMillis) {
        return boardUpdateInformations.get(dateMillis);
    }

    public BoardUpdateInformation getOrCreateBoardUpdateInformationForDay(final String dateString, final long dateMillis) {
        BoardUpdateInformation bui = getBoardUpdateInformationForDay(dateMillis);
        if( bui == null ) {
            bui = new BoardUpdateInformation(this, dateString, dateMillis);
            boardUpdateInformations.put(dateMillis, bui);
        }
        return bui;
    }

    /**
     * @return  a List of all available BoardUpdateInformation object, sorted by day (latest first)
     */
    public List<BoardUpdateInformation> getBoardUpdateInformationList() {
        final List<BoardUpdateInformation> buiList = new ArrayList<BoardUpdateInformation>(boardUpdateInformations.size());
        buiList.addAll( boardUpdateInformations.values() );
        Collections.sort(buiList, boardUpdateInformationComparator);
        return buiList;
    }

    public boolean hasBoardUpdateInformations() {
        return boardUpdateInformations.size() > 0;
    }

    /**
     * Sort BoardUpdateInformation descending by dateMillis.
     */
    private static class BoardUpdateInformationComparator implements Comparator<BoardUpdateInformation> {
        public int compare(final BoardUpdateInformation o1, final BoardUpdateInformation o2) {
            return Mixed.compareLong(o2.getDateMillis(), o1.getDateMillis());
        }
    }

    public boolean isDosForToday() {
        return dosForToday;
    }

    private void setDosForToday(final boolean dosForToday) {
        this.dosForToday = dosForToday;
    }

    public boolean isDosForBackloadDays() {
        return dosForBackloadDays;
    }

    private void setDosForBackloadDays(final boolean dosForBackloadDays) {
        this.dosForBackloadDays = dosForBackloadDays;
    }

    public boolean isDosForAllDays() {
        return dosForAllDays;
    }

    private void setDosForAllDays(final boolean dosForAllDays) {
        this.dosForAllDays = dosForAllDays;
    }

    public void updateDosStatus(final boolean stopBoardUpdatesWhenDOSed, final long minDateTime, final long todayDateTime) {
        if( !stopBoardUpdatesWhenDOSed ) {
            setDosForToday(false);
            setDosForBackloadDays(false);
            setDosForAllDays(false);
            return;
        }
        // scan bui for this board, update board status for: dos today / dos for backload, but not all backload days / dos for all (today and all backload)
        final List<BoardUpdateInformation> buiList = getBoardUpdateInformationList();
        // only respect days that would be updated
        boolean newDosForToday = false;
        boolean newDosForBackloadDays = false;
        boolean newDosForAllDays = false;
        int dosBackloadDayCount = 0;
        for(final BoardUpdateInformation bui : buiList ) {
            final long buiDateMillis = bui.getDateMillis();
            if( buiDateMillis < minDateTime ) {
                // too old, not updated in backload
                continue;
            }
            // count stopped for today and backload (good/stopped)
            if( buiDateMillis == todayDateTime ) {
                // today
                if( !bui.isBoardUpdateAllowed() ) {
                    newDosForToday = true;
                }
            } else {
                // any backload day
                if( !bui.isBoardUpdateAllowed() ) {
                    dosBackloadDayCount++;
                }
            }

            if( dosBackloadDayCount == buiList.size()-1 ) {
                newDosForAllDays = true;
            } else {
                newDosForBackloadDays = true;
            }

            setDosForToday(newDosForToday);
            setDosForBackloadDays(newDosForBackloadDays);
            setDosForAllDays(newDosForAllDays);
        }
    }
}
