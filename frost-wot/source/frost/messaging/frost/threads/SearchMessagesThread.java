/*
  SearchMessagesThread.java / Frost
  Copyright (C) 2006  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.frost.threads;

import java.util.*;
import java.util.logging.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.joda.time.*;

import frost.*;
import frost.gui.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.storage.*;
import frost.storage.perst.messagearchive.*;
import frost.storage.perst.messages.*;
import frost.util.*;

public class SearchMessagesThread extends Thread implements MessageCallback {

    private static final Logger logger = Logger.getLogger(SearchMessagesThread.class.getName());

    SearchMessagesDialog searchDialog; // used to add found messages
    SearchMessagesConfig searchConfig;

    private final TrustStates trustStates = new TrustStates();

    private boolean stopRequested = false;

    public SearchMessagesThread(final SearchMessagesDialog searchDlg, final SearchMessagesConfig searchCfg) {
        searchDialog = searchDlg;
        searchConfig = searchCfg;
    }

    @Override
    public void run() {

        try {
            // select board dirs
            List<Board> boardsToSearch;
            if( searchConfig.searchBoards == SearchMessagesConfig.BOARDS_DISPLAYED ) {
                boardsToSearch = MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards();
            } else if( searchConfig.searchBoards == SearchMessagesConfig.BOARDS_CHOSED ) {
                boardsToSearch = searchConfig.chosedBoards;
            } else {
                boardsToSearch = Collections.emptyList(); // paranoia
            }

            final DateRange dateRange = new DateRange();

            for( final Board board : boardsToSearch ) {

                if( isStopRequested() ) {
                    break;
                }

                // build date and trust state info for this board
                updateDateRangeForBoard(board, dateRange);
                updateTrustStatesForBoard(board, trustStates);

                searchBoard(board, dateRange);

                if( isStopRequested() ) {
                    break;
                }
            }
        } catch(final Throwable t) {
            logger.log(Level.SEVERE, "Catched exception:", t);
        }
        searchDialog.notifySearchThreadFinished();
    }


    public boolean messageRetrieved(final FrostMessageObject mo) {
        // search this xml file
        searchMessage(mo);

        return isStopRequested();
    }

    // Format: boards\2006.3.1\2006.3.1-boards-0.xml
    private void searchBoard(final Board board, final DateRange dr) {
//System.out.println("startDate="+dr.startDate);
//System.out.println("endDate="+dr.endDate);
        if( searchConfig.searchInKeypool ) {
            try {
                // if we search displayed messages, we must search all new and flagged/starred too
                final boolean retrieveDisplayedMessages = (searchConfig.searchDates == SearchMessagesConfig.DATE_DISPLAYED);
                MessageStorage.inst().retrieveMessagesForSearch(
                        board,
                        dr.startDate,
                        dr.endDate,
                        retrieveDisplayedMessages,
                        ((searchConfig.contentString==null||searchConfig.contentString.length()==0)?false:true), // withContent
                        false, // withAttachment
                        false, // showDeleted
                        this);
            } catch(final Throwable e) {
                logger.log(Level.SEVERE, "Catched exception during getMessageTable().retrieveMessagesForSearch:", e);
            }
        }
        if( searchConfig.searchInArchive ) {
            try {
                ArchiveMessageStorage.inst().retrieveMessagesForSearch(
                        board,
                        dr.startDate,
                        dr.endDate,
                        this);
            } catch(final Throwable e) {
                logger.log(Level.SEVERE, "Catched exception during getMessageArchiveTable().retrieveMessagesForSearch:", e);
            }
        }
    }

    private void searchMessage(final FrostMessageObject mo) {

        // check private, flagged, starred, replied only
        if( searchConfig.searchPrivateMsgsOnly != null ) {
            if( mo.getRecipientName() == null || mo.getRecipientName().length() == 0 ) {
                return;
            }
        }
        if( searchConfig.searchFlaggedMsgsOnly != null ) {
            if( mo.isFlagged() != searchConfig.searchFlaggedMsgsOnly.booleanValue() ) {
                return;
            }
        }
        if( searchConfig.searchStarredMsgsOnly != null ) {
            if( mo.isStarred() != searchConfig.searchStarredMsgsOnly.booleanValue() ) {
                return;
            }
        }
        if( searchConfig.searchRepliedMsgsOnly != null ) {
            if( mo.isReplied() != searchConfig.searchRepliedMsgsOnly.booleanValue() ) {
                return;
            }
        }

        // check trust states
        if( matchesTrustStates(mo, trustStates) == false ) {
            return;
        }

        // check attachments
        if( searchConfig.msgMustContainBoards && !mo.hasBoardAttachments() ) {
            return;
        }
        if( searchConfig.msgMustContainFiles && !mo.hasFileAttachments() ) {
            return;
        }

        // test the user-provided search fields (sender/subject/content), if valid patterns exist
        // if no pattern exists for a field, that check is simply skipped
        if( searchConfig.senderPattern != null && !matchText(mo.getFromName(), searchConfig.senderPattern) ) {
            return;
        }
        if( searchConfig.subjectPattern != null && !matchText(mo.getSubject(), searchConfig.subjectPattern)) {
            return;
        }
        if( searchConfig.contentPattern != null && !matchText(mo.getContent(), searchConfig.contentPattern) ) {
            return;
        }

        // match, add to result table
        searchDialog.addFoundMessage(new FrostSearchResultMessageObject(mo));
    }

    /**
     * Perform a regular expression search against the given text.
     * @return  true if there was a match in the text (or if there was no pattern provided), false if there was a pattern and it didn't match
     */
    private boolean matchText(final String text, final Pattern regexPattern)
    {
        if( regexPattern == null ) {
            return true; // if there's no pattern, we'll just return "yes, the text matches"
        }

        Matcher m = regexPattern.matcher(text);
        return ( m.find() ? true : false ); // find() just looks for the 1st match in the string, which means that the check is super-fast.
    }

    private boolean matchesTrustStates(final FrostMessageObject msg, final TrustStates ts) {

        if( msg.isMessageStatusFRIEND() && ts.trust_FRIEND == false ) {
            return false;
        }
        if( msg.isMessageStatusGOOD() && ts.trust_GOOD == false ) {
            return false;
        }
        if( msg.isMessageStatusNEUTRAL() && ts.trust_NEUTRAL == false ) {
            return false;
        }
        if( msg.isMessageStatusBAD() && ts.trust_BAD == false ) {
            return false;
        }
        if( msg.isMessageStatusNONE() && ts.trust_NONE == false ) {
            return false;
        }
        if( msg.isMessageStatusTAMPERED() && ts.trust_TAMPERED == false ) {
            return false;
        }

        return true;
    }

    private void updateTrustStatesForBoard(final Board b, final TrustStates ts) {
        if( searchConfig.searchTruststates == SearchMessagesConfig.TRUST_ALL ) {
            // use all trust states
            ts.trust_FRIEND = true;
            ts.trust_GOOD = true;
            ts.trust_NEUTRAL = true;
            ts.trust_BAD = true;
            ts.trust_NONE = true;
            ts.trust_TAMPERED = true;
        } else if( searchConfig.searchTruststates == SearchMessagesConfig.TRUST_CHOSED ) {
            // use specified trust states
            ts.trust_FRIEND = searchConfig.trust_FRIEND;
            ts.trust_GOOD = searchConfig.trust_GOOD;
            ts.trust_NEUTRAL = searchConfig.trust_NEUTRAL;
            ts.trust_BAD = searchConfig.trust_BAD;
            ts.trust_NONE = searchConfig.trust_NONE;
            ts.trust_TAMPERED = searchConfig.trust_TAMPERED;
        } else if( searchConfig.searchTruststates == SearchMessagesConfig.TRUST_DISPLAYED ) {
            // use trust states configured for board
            ts.trust_FRIEND = true;
            ts.trust_GOOD = !b.getHideGOOD();
            ts.trust_NEUTRAL = !b.getHideNEUTRAL();
            ts.trust_BAD = !b.getHideBAD();
            ts.trust_NONE = !b.getHideUnsigned();
            ts.trust_TAMPERED = !b.getHideUnsigned();
        }
    }

    private void updateDateRangeForBoard(final Board b, final DateRange dr) {
        // now = current UTC time; used for "visible posts (date_displayed)", "X days backwards", and "all posts until today".
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final long todayMillis = now.plusDays(1).withTimeAtStartOfDay().getMillis();
        if( searchConfig.searchDates == SearchMessagesConfig.DATE_DISPLAYED ) {
            dr.startDate = now.minusDays(b.getMaxMessageDisplay()).withTimeAtStartOfDay().getMillis();
            dr.endDate = todayMillis;
        } else if( searchConfig.searchDates == SearchMessagesConfig.DATE_DAYS_BACKWARD ) {
            dr.startDate = now.minusDays(searchConfig.daysBackward).withTimeAtStartOfDay().getMillis();
            dr.endDate = todayMillis;
        } else if( searchConfig.searchDates == SearchMessagesConfig.DATE_BETWEEN_DATES ) {
            // NOTE: we do NOT specify that these DateTime()s are in the UTC timezone; that way the input is treated as the local timezone.
            // that's important, since the user chooses a local start/end date for the search here, and we want those local dates interpreted
            // as the local timezone (at midnight/start of day). the getMillis() in turn returns the UTC milliseconds since the UNIX epoch.
            // this means that the user will only see posts between the selected local dates, properly taking their timezone into account.
            dr.startDate = new DateTime(searchConfig.startDate).withTimeAtStartOfDay().getMillis();
            dr.endDate = new DateTime(searchConfig.endDate).plusDays(1).withTimeAtStartOfDay().getMillis();
        } else {
            // all dates
            dr.startDate = 0;
            dr.endDate = todayMillis;
        }
    }

    public synchronized boolean isStopRequested() {
        return stopRequested;
    }
    public synchronized void requestStop() {
        stopRequested = true;
    }

    private class DateRange {
        long startDate;
        long endDate;
    }

    private class TrustStates {
        // current trust status to search into
        public boolean trust_FRIEND = false;
        public boolean trust_GOOD = false;
        public boolean trust_NEUTRAL = false;
        public boolean trust_BAD = false;
        public boolean trust_NONE = false;
        public boolean trust_TAMPERED = false;
    }
}
