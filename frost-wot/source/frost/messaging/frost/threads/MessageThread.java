/*
  MessageDownloadThread.java / Frost
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

package frost.messaging.frost.threads;

import java.io.*;
import java.util.logging.*;
import java.lang.Number;

import org.joda.time.*;

import frost.*;
import frost.SettingsClass;
import frost.identities.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.messaging.frost.transfer.*;
import frost.storage.perst.*;
import frost.util.*;

/**
 * Download and upload messages for a board.
 */
public class MessageThread extends BoardUpdateThreadObject implements BoardUpdateThread, MessageUploaderCallback {

    private final boolean downloadToday;
    private final Board board;
    private int maxDaysBack;
    private int startDay;
    private int lastAllDayStarted;
    private volatile boolean stopUpdatingFlag = false;

    private static final Logger logger = Logger.getLogger(MessageThread.class.getName());

    public MessageThread(final boolean downloadToday, final Board board, final int maxDaysBack, final int startDay) {
        super(board);
        this.downloadToday = downloadToday;
        this.board = board;
        this.maxDaysBack = maxDaysBack;
        this.startDay = startDay;
        this.lastAllDayStarted = -1;
    }

    /**
     * Nothing uses this function, and it's pretty pointless since we're already directly updating
     * the global "last all-day scan day started" setting whenever we perform an all-days thread.
     *
     * @return  -1 if the message downloading has not yet begun or if this is just a "today" thread,
     *     otherwise a number from 0 and up representing what "all days back" day it has currently
     *     started downloading (0 (today), 1 (yesterday), 2, etc).
     */
    public int getLastAllDayStarted()
    {
        return lastAllDayStarted;
    }

    public int getThreadType() {
        if (downloadToday) {
            return BoardUpdateThread.MSG_DNLOAD_TODAY;
        } else {
            return BoardUpdateThread.MSG_DNLOAD_BACK;
        }
    }

    /**
     * If set to true, the thread will stop downloading messages
     * after it's done with the current day it's working on.
     */
    public void setStopUpdatingFlag(final boolean flag) {
        stopUpdatingFlag = flag;
    }

    public boolean isStopUpdatingFlag() {
        return stopUpdatingFlag;
    }

    @Override
    public void run() {

        notifyThreadStarted(this);

        try {
            String tofType;
            if (downloadToday) {
                tofType = "TOF Download (TODAY)";
            } else {
                tofType = "TOF Download (ALLDAYS)";
            }

            // wait a max. of 5 seconds between start of threads
            Mixed.waitRandom(5000);

            logger.info("TOFDN: " + tofType + " Thread started for board " + board.getName());

            // NOTE: despite the isInterrupted() checks here and below, there's
            // actually no code in Frost which interrupts the threads manually.
            // it's only done automatically by Java at Frost's GUI shutdown.
            // but there IS code which sets the "stopUpdating" flag, which tells
            // the thread to stop downloading messages and abort as soon as possible.
            if( isInterrupted() || isStopUpdatingFlag() ) {
                notifyThreadFinished(this);
                return;
            }

            // #MIDNIGHTBUGFIX 1 of 2: allowing active conversations at UTC midnight.
            // if we're doing a "today" scan, we will download today (0) plus yesterday (1).
            // this solves the huge problem that conversations at midnight completely die,
            // because as soon as the day switches over, Frost used to only load messages
            // for the new day, so any messages sent near midnight would not be seen by anybody
            // until their clients ran an "all days back" scan (which by default only happens
            // every 12 hours). this fix ensures that we do a QUICK "any new messages after
            // yesterday's last index?" check after we're done grabbing today's actual messages.
            // we don't limit this additional "yesterday" check to any specific time; we always do it,
            // for protection against clock skew, etc. it's a fast check, so it doesn't matter.
            if( this.downloadToday ) {
                // "today" scan grabs today (0) + yesterday (1)
                startDay = 0;
                maxDaysBack = 2;
            } else {
                // "all days back" scans need to indicate that they're in progress (this is what
                // makes the "Day x/y" GUI indicator appear; it's not used for regular day-updates).
                board.setAllDaysUpdating(true);
            }

            // now just perform the today/all days message download:
            // "startTime" = the exact time that the whole job began; cached to avoid slipping during jobs that take several days to complete
            // "theDate" = constantly updated reference to the date we'll download each iteration
            final DateTime startTime = new DateTime(DateTimeZone.UTC);
            DateTime theDate = null;
            final int boardId = board.getPerstFrostBoardObject().getBoardId();
            for( int currentDay = startDay; currentDay < maxDaysBack; ++currentDay ) {
                if( isInterrupted() || isStopUpdatingFlag() ) {
                    break; // we've been told to stop downloading messages
                }

                // figure out which date we'll be downloading (such as "2015-11-04")
                theDate = startTime.minusDays(currentDay).withTimeAtStartOfDay();
                logger.info("TOF Download (" + (this.downloadToday ? "TODAY" : "ALLDAYS") + "): '" + board.getName() + "', downloading day: " + (currentDay + 1) + "/" + maxDaysBack + " (" + theDate.toString("yyyy-MM-dd") + ")"); // we add +1 to the current day for log-readability (so that 10/10 is the final day instead of 9/10), just like when we display days in the GUI.

                // if this is an "all days back" scan, we'll set the current progress for GUI/resume purposes
                if( !this.downloadToday ) {
                    lastAllDayStarted = currentDay; // thread-local var not used anywhere, but we update it anyway

                    // these are used in GUI as "Downloading Day x/y (yyyy-MM-dd)"
                    board.setLastAllDayStarted(currentDay); // x
                    board.setLastAllMaxDays(maxDaysBack); // y
                    board.setLastAllDayStartedDate(theDate); // yyyy-MM-dd

                    // if the message downloading was stopped without finishing all maxDays,
                    // this is used for determining how long ago the scan was interrupted.
                    // it's used in the "Resume"-offset calculations.
                    board.setLastDayBoardUpdated(new DateTime(DateTimeZone.UTC));
                }

                // get slot-index for the current day's date
                final long dateMillis = theDate.withTimeAtStartOfDay().getMillis();
                final IndexSlot gis = IndexSlotsStorage.inst().getSlotForDateMidnightMillis(boardId, dateMillis);

                // download the messages for the current day (updates the slotindex)
                final BoardUpdateInformation bui = downloadDate(theDate, gis, dateMillis);

                // do special post-processing *only* if the day-download was completed without interruption
                if( !isInterrupted() && !isStopUpdatingFlag() ) {
                    // if we just finished loading the messages for "today" (day 0), regardless
                    // of whether this is a "today" scan or an "all days back" scan, we should
                    // now try uploading messages (if allowed). we need to send messages *now*
                    // since having downloaded today means we know which slot indexes are open.
                    if( bui.isBoardUpdateAllowed() && currentDay == 0 ) {
                        logger.info("TOF Download (" + (this.downloadToday ? "TODAY" : "ALLDAYS") + "): '" + board.getName() + "', uploading today's messages...");

                        // #MIDNIGHTBUGFIX 2 of 2:
                        // this will now ONLY upload messages while it's STILL the
                        // same day as the index slot. so if the user updated
                        // the board near midnight (so that it finished grabbing
                        // "today"'s messages *after* midnight), or if they sent
                        // multiple messages near midnight, then one or more of
                        // the messages may be skipped and have to wait until the
                        // next board refresh instead. in the past, the messages
                        // used to be uploaded anyway, but with yesterday's ("today")
                        // slotindex above, which meant they may be uploaded with
                        // extremely high slot indexes, and if nobody posted enough
                        // messages to reach that index, the message would be lost
                        // forever. it was a very rare occurrence since most people
                        // stopped talking at midnight, but with #MIDNIGHTBUGFIX1
                        // taking care of talking at midnight, it became extra
                        // critical to take care of this "black hole" midnight bug too.
                        final int result = uploadMessages(gis); // NOTE: doesn't tell us if message upload is disabled
                        if( result == UPLOADMESSAGES_NEED_UPDATED_SLOTINDEX ) {
                            // okay... so... the slotindex for "today" is too old,
                            // which means that the board refresh has taken long
                            // enough to push the clock past midnight, *or* that
                            // multiple messages were queued and the uploading
                            // of the first message(s) delayed the remaining messages
                            // past midnight, so they've refused to use the old slotindex...
                            //
                            // we could now wait until next board refresh, or we *could*
                            // forcibly start a separate "today" board update thread,
                            // but here's an even better idea:
                            //
                            // - let's get the latest timestamp and download today's
                            //   latest slotindex *right now* and try *ONE* more time
                            //   to upload the messages that were meant for today.
                            // - it causes some code duplication, but the benefits are great:
                            //   no need to start extra threads, no need to wait until
                            //   the next board refresh, *and* of course the fact that
                            //   this method has an *extremely* high chance of successfully
                            //   updating "today" and uploading *all* remaining messages.
                            logger.log(Level.SEVERE, "We tried to upload messages for '" + board.getName() + "' using an outdated slotindex; we'll try downloading today's latest slotindex again and try one more time... otherwise, the uploads will be deferred until next board refresh...");
                            final DateTime nowDate = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay();
                            final long nowDateMillis = nowDate.getMillis();
                            final IndexSlot nowGis = IndexSlotsStorage.inst().getSlotForDateMidnightMillis(boardId, nowDateMillis);
                            final BoardUpdateInformation nowBui = downloadDate(nowDate, nowGis, nowDateMillis);
                            uploadMessages(nowGis); // try to upload again, and ignore return code
                        }
                    }

                    // if this is an "all days back" scan and we finished at least 1 day without
                    // interruption, we'll store the current time as the "last time an all days
                    // back scan ran". this is used for the code that checks "has it been 12+ hours
                    // since the last 'all days' scan? then run an all days back scan".
                    if( !this.downloadToday ) {
                        board.setLastBackloadUpdateFinishedMillis(System.currentTimeMillis());
                    }
                }
            }
            logger.info("TOFDN: " + tofType + " Thread stopped for board " + board.getName());
        } catch (final Throwable t) {
            logger.log(Level.SEVERE, Thread.currentThread().getName() + ": Oo. Exception in MessageDownloadThread:", t);
        }
        if( !this.downloadToday ) { // indicate to the GUI that we're done with the "all days" scan
            board.setAllDaysUpdating(false);
        }
        notifyThreadFinished(this);
    }

    protected String composeDownKey(final int index, final String dirdate) {
        String downKey = null;
        // switch public / secure board
        if (board.isPublicBoard() == false) {
            downKey = new StringBuilder()
                    .append(board.getPublicKey())
                    .append("/")
                    .append(board.getBoardFilename())
                    .append("/")
                    .append(dirdate)
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        } else {
            downKey = new StringBuilder()
                    .append("KSK@frost/message/")
                    .append(Core.frostSettings.getValue(SettingsClass.MESSAGE_BASE))
                    .append("/")
                    .append(dirdate)
                    .append("-")
                    .append(board.getBoardFilename())
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        }
        return downKey;
    }

    protected BoardUpdateInformation downloadDate(final DateTime theDate, final IndexSlot gis, final long dateMillis) {

        final String dirDateString = DateFun.FORMAT_DATE.print(theDate);

        final BoardUpdateInformation boardUpdateInformation = board.getOrCreateBoardUpdateInformationForDay(dirDateString, dateMillis);

        // new run, reset subsequentFailures
        boardUpdateInformation.resetSubsequentInvalidMsgs();

        int index = -1;
        int failures = 0;
        int configMaxFailures = Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DOWNLOADDATE_FAILURES); // default: 2
        if( configMaxFailures > 5 ) {
            configMaxFailures = 5;
        } else if( configMaxFailures < 2 ) {
            configMaxFailures = 2;
        }
        final int maxFailures = configMaxFailures; // skip a maximum of 2-5 empty slots at the end of known message indices for that day

        while (failures < maxFailures) {

            if( isInterrupted() || isStopUpdatingFlag() ) {
                break;
            }

            // check if allowed state changed
            if( !boardUpdateInformation.checkBoardUpdateAllowedState() ) {
                break;
            }

            if( index < 0 ) {
                index = gis.findFirstDownloadSlot();
            } else {
                index = gis.findNextDownloadSlot(index);
            }

            String logInfo = null;

            try { // we don't want to die for any reason

                Mixed.waitRandom(2000); // don't hurt node

                final String downKey = composeDownKey(index, dirDateString);
                logInfo = new StringBuilder()
                            .append(" board=")
                            .append(board.getName())
                            .append(", key=")
                            .append(downKey)
                            .toString();

                final boolean quicklyFailOnAdnf;
                final int maxRetries;
                if( Core.frostSettings.getBoolValue(SettingsClass.FCP2_QUICKLY_FAIL_ON_ADNF) ) {
                    quicklyFailOnAdnf = true;
                    maxRetries = 2;
                } else {
                    // default
                    quicklyFailOnAdnf = false;
                    maxRetries = -1;
                }

                boardUpdateInformation.setCurrentIndex(index);
                notifyBoardUpdateInformationChanged(this, boardUpdateInformation);

                final long millisBefore = System.currentTimeMillis();

                final MessageDownloaderResult mdResult = MessageDownloader.downloadMessage(downKey, index, maxRetries, logInfo);

                boardUpdateInformation.incCountTriedIndices();
                boardUpdateInformation.addNodeTime(System.currentTimeMillis() - millisBefore);

                if( mdResult == null ) {
                    // file not found
                    if( gis.isDownloadIndexBehindLastSetIndex(index) ) {
                        // we stop if we tried maxFailures indices behind the last known index
                        failures++;
                    }
                    boardUpdateInformation.incCountDNF(); notifyBoardUpdateInformationChanged(this, boardUpdateInformation);
                    continue;
                }

                failures = 0;

                if( mdResult.isFailure()
                        && mdResult.getErrorMessage() != null
                        && mdResult.getErrorMessage().equals(MessageDownloaderResult.ALLDATANOTFOUND) )
                {
                    boardUpdateInformation.incCountADNF(); notifyBoardUpdateInformationChanged(this, boardUpdateInformation);
                    if( quicklyFailOnAdnf ) {
                        System.out.println("TOFDN: Index "+index+" got ADNF, will never try this index again.");
                        gis.setDownloadSlotUsed(index);
                        IndexSlotsStorage.inst().storeSlot(gis); // remember each progress
                    } else {
                        // don't set slot used, try to retrieve the file again
                        System.out.println("TOFDN: Skipping index "+index+" for now, will try again later.");
                    }
                    continue;
                }

                gis.setDownloadSlotUsed(index);

                if( mdResult.isFailure() ) {
                    // some error occured, don't try this file again
                    receivedInvalidMessage(board, theDate, index, mdResult.getErrorMessage());
                    boardUpdateInformation.incCountInvalid(); notifyBoardUpdateInformationChanged(this, boardUpdateInformation);
                } else if( mdResult.getMessage() != null ) {
                    // message is loaded, delete underlying received file
                    mdResult.getMessage().getFile().delete();
                    // basic validation, isValid() of FrostMessageObject was already called during instanciation of MessageXmlFile
                    if (isValidFormat(mdResult.getMessage(), theDate, board)) {
                        receivedValidMessage(
                                mdResult.getMessage(),
                                mdResult.getOwner(),
                                board,
                                index);

                        boardUpdateInformation.incCountValid();
                        boardUpdateInformation.updateMaxSuccessfulIndex(index);

                        notifyBoardUpdateInformationChanged(this, boardUpdateInformation);
                    } else {
                        receivedInvalidMessage(board, theDate, index, MessageDownloaderResult.INVALID_MSG);
                        logger.warning("TOFDN: Message was dropped, format validation failed: "+logInfo);
                        boardUpdateInformation.incCountInvalid(); notifyBoardUpdateInformationChanged(this, boardUpdateInformation);
                    }
                }

                IndexSlotsStorage.inst().storeSlot(gis); // remember each progress

            } catch(final Throwable t) {
                logger.log(Level.SEVERE, "TOFDN: Exception thrown in downloadDate: "+logInfo, t);
                // download failed, try next file
            }
        } // end-of: while

        boardUpdateInformation.setCurrentIndex(-1);
        boardUpdateInformation.updateBoardUpdateAllowedState();
        notifyBoardUpdateInformationChanged(this, boardUpdateInformation);

        return boardUpdateInformation;
    }

    private void receivedInvalidMessage(final Board b, final DateTime calDL, final int index, final String reason) {
        TOF.getInstance().receivedInvalidMessage(b, calDL.withTimeAtStartOfDay(), index, reason);
    }

    private void receivedValidMessage(
            final MessageXmlFile mo,
            final Identity owner, // maybe null if unsigned
            final Board b,
            final int index)
    {
        TOF.getInstance().receivedValidMessage(mo, owner, b, index);
    }

    //////////////////////////////////////////////////
    ///  validation after receive
    //////////////////////////////////////////////////

    /**
     * First time verify.
     */
    public boolean isValidFormat(final MessageXmlFile mo, final DateTime dirDate, final Board b) {
        try {
            final DateTime dateTime;
            try {
                dateTime = mo.getDateAndTime();
            } catch(final Throwable ex) {
                logger.log(Level.SEVERE, "Exception in isValidFormat() - skipping message.", ex);
                return false;
            }

            // e.g. "E6936D085FC1AE75D43275161B50B0CEDB43716C1CE54E420F3C6FEB9352B462" (len=64)
            if( mo.getMessageId() == null || mo.getMessageId().length() < 60 || mo.getMessageId().length() > 68 ) {
                logger.log(Level.SEVERE, "Message has no unique message id - skipping Message: "+dirDate+";"+dateTime);
                return false;
            }

            // ensure that time/date of msg is max. 1 day before/after dirDate
            final DateTime dm = dateTime.withTimeAtStartOfDay();
            if( dm.isAfter(dirDate.plusDays(1).withTimeAtStartOfDay())
                    || dm.isBefore(dirDate.minusDays(1).withTimeAtStartOfDay()) )
            {
                logger.log(Level.SEVERE, "Invalid date - skipping Message: "+dirDate+";"+dateTime);
                return false;
            }

            // ensure that board inside xml message is the board we currently download
            if( mo.getBoardName() == null ) {
                logger.log(Level.SEVERE, "No boardname in message - skipping message: (null)");
                return false;
            }
            final String boardNameInMsg = mo.getBoardName().toLowerCase();
            final String downloadingBoardName = b.getName().toLowerCase();
            if( boardNameInMsg.equals(downloadingBoardName) == false ) {
                logger.log(Level.SEVERE, "Different boardnames - skipping message: "+mo.getBoardName().toLowerCase()+";"+b.getName().toLowerCase());
                return false;
            }

        } catch (final Throwable t) {
            logger.log(Level.SEVERE, "Exception in isValidFormat() - skipping message.", t);
            return false;
        }
        return true;
    }

    private static final int UPLOADMESSAGES_OK = 0;
    private static final int UPLOADMESSAGES_NEED_UPDATED_SLOTINDEX = 1;

    /**
     * Upload pending messages for this board.
     */
    private int uploadMessages(final IndexSlot gis) {

        FrostUnsentMessageObject unsendMsg = UnsentMessagesManager.getUnsentMessage(board);
        if( unsendMsg == null ) {
            // currently no msg to send for this board
            return UPLOADMESSAGES_OK;
        }

        final String fromName = unsendMsg.getFromName();
        while( unsendMsg != null ) {

            // create a MessageXmlFile, sign, and send

            Identity recipient = null;
            if( unsendMsg.getRecipientName() != null && unsendMsg.getRecipientName().length() > 0) {
                recipient = Core.getIdentities().getIdentity(unsendMsg.getRecipientName());
                if( recipient == null ) {
                    logger.severe("Can't send Message '" + unsendMsg.getSubject() + "', the recipient is not longer in your identites list!");
                    UnsentMessagesManager.deleteMessage(unsendMsg);
                    continue;
                }
            }

            UnsentMessagesManager.incRunningMessageUploads();

            final int result = uploadMessage(unsendMsg, recipient, gis);
            boolean skipRemainingUploads = false;
            if( result == UPLOADMESSAGE_FAILED_SLOTINDEX_OUTDATED ) {
                // #MIDNIGHTBUGFIX 2 of 2:
                // this is the only unrecoverable failure! if we've got an outdated
                // slotindex (for another day than "now"'s timestamp) then we CANNOT
                // upload ANY messages for this board without waiting for a fresh
                // index for TODAY instead.
                logger.severe("Cannot upload messages for board '" + board.getName() + "', the slotindex is outdated and doesn't match today's date. Deferring upload until next board refresh...");
                skipRemainingUploads = true;
            }

            UnsentMessagesManager.decRunningMessageUploads();

            if( skipRemainingUploads ) {
                return UPLOADMESSAGES_NEED_UPDATED_SLOTINDEX;
            }

            Mixed.waitRandom(5000); // wait some time

            // get next message to upload
            unsendMsg = UnsentMessagesManager.getUnsentMessage(board, fromName);
        }

        return UPLOADMESSAGES_OK;
    }

    // everything went ok
    private static final int UPLOADMESSAGE_OK = 0;
    // there was some fatal error during upload
    private static final int UPLOADMESSAGE_FAILED = 1;
    // the slotindex is not for the "now" timestamp; we can't upload any messages
    // using that old index, we need a new one for "now" (today).
    private static final int UPLOADMESSAGE_FAILED_SLOTINDEX_OUTDATED = 2;
    // the user has deleted the identity used to send the message; we cannot send
    // it, so we've simply deleted the message.
    private static final int UPLOADMESSAGE_FAILED_IDENTITY_MISSING = 3;

    private int uploadMessage(final FrostUnsentMessageObject mo, final Identity recipient, final IndexSlot gis) {

        logger.info("Preparing upload of message to board '" + board.getName() + "'");

        mo.setCurrentUploadThread(this);

        try {
            // prepare upload

            LocalIdentity senderId = null;
            if( mo.getFromName().indexOf("@") > 0 ) {
                // not anonymous
                if( mo.getFromIdentity() instanceof LocalIdentity ) {
                    senderId = (LocalIdentity) mo.getFromIdentity();
                } else {
                    // apparently the LocalIdentity used to write the msg was deleted
                    logger.severe("The LocalIdentity used to write this unsent msg was deleted: "+mo.getFromName());
                    mo.setCurrentUploadThread(null); // must be marked as not uploading before delete!
                    UnsentMessagesManager.deleteMessage(mo);
                    return UPLOADMESSAGE_FAILED_IDENTITY_MISSING;
                }
            }

            // turns the message into an XML file; the actual "envelope date"
            // of the message will be set further down...
            final MessageXmlFile message = new MessageXmlFile(mo);

            // #MIDNIGHTBUGFIX 2 of 2: we'll get the "now" timestamp
            // and stamp the message envelope with that time. however,
            // if the slotindex we've got is for another day (not for "now"'s
            // day), then we can't and WON'T do anything, since we don't
            // want to upload "now"'s message using another day's index
            // (which was the cause of the serious "black hole" midnight bug).
            final DateTime now = new DateTime(DateTimeZone.UTC);
            final long nowMidnight = now.withTimeAtStartOfDay().getMillis();
            final long slotMidnight = gis.getMsgDate(); // the millis at start of day for the slotindex date
            if( slotMidnight != nowMidnight ) {
                logger.severe("Aborted attempt to upload message using outdated slotindex!");
                mo.setCurrentUploadThread(null); // must be marked as not uploading before we return!
                return UPLOADMESSAGE_FAILED_SLOTINDEX_OUTDATED;
            }

            // #MIDNIGHTBUGFIX 2 of 2:
            // alright, everything is fine; we'll be uploading it with the "now"
            // timestamp as the envelope date (which determines what day it's going
            // to be uploaded under), and the current slotindex (which determines
            // which slot it's uploaded under for that day).
            //
            // NOTE: NOTHING else modifies the envelope date after this, which
            // ensures that the message *will* be uploaded to THAT exact date and
            // the next free slot in the given slotindex.
            //
            // NOTE: the MessageUploader will call composeUploadKey(), which composes
            // a key from the message's "now" date-string (such as 2015-11-19),
            // and the next free "index" slot for the slotindex associated with the
            // MessageUploader.uploadMessage() "work area" object. so, what happens
            // is that the uploader will try consecutive indexes for the envelope-date
            // and slotindex it was given, until it finds a free slot. It will never
            // try any other days or other indexes. So we can be sure that it never
            // messes with the envelope-date or its associated slotindex for that day.
            message.setDateAndTime(now);

            final File unsentMessageFile = FileAccess.createTempFile("unsendMsg", ".xml");
            message.setFile(unsentMessageFile);
            if (!message.save()) {
                logger.severe("This was a HARD error and the file to upload is lost, please report to a dev!");
                mo.setCurrentUploadThread(null); // must be marked as not uploading before delete!
                return UPLOADMESSAGE_FAILED;
            }
            unsentMessageFile.deleteOnExit();

            // start upload, this signs and encrypts if needed

            final MessageUploaderResult result = MessageUploader.uploadMessage(
                    message,
                    recipient,
                    senderId,
                    this,
                    gis,
                    MainFrame.getInstance(),
                    board.getName());

            // file is not any longer needed
            message.getFile().delete();

            if( !result.isSuccess() ) {
                // upload failed, unsend message was handled by MessageUploader (kept or deleted, user choosed)
                mo.setCurrentUploadThread(null); // must be marked as not uploading before delete!
                if( !result.isKeepMessage() ) {
                    // user choosed to drop the message
                    UnsentMessagesManager.deleteMessage(mo);
                } else {
                    // user choosed to retry after next startup, dequeue message now and find it again on next startup
                    UnsentMessagesManager.dequeueMessage(mo);
                }
                return UPLOADMESSAGE_FAILED;
            }

            // success, store used slot
            IndexSlotsStorage.inst().storeSlot(gis);

            final int index = result.getUploadIndex();

            // upload was successful, store message in sentmessages database
            final FrostMessageObject sentMo = new FrostMessageObject(message, senderId, board, index);

            if( !SentMessagesManager.addSentMessage(sentMo) ) {
                // not added to gui, perst obj is not needed
                sentMo.setPerstFrostMessageObject(null);
            }

            // save own private messages into the message table
            if( sentMo.getRecipientName() != null && sentMo.getRecipientName().length() > 0 ) {
                // maybe sentMo has a perst obj set
                final FrostMessageObject moForMsgTable;
                if( sentMo.getPerstFrostMessageObject() != null ) {
                    // create a new FrostMessageObject
                    moForMsgTable = new FrostMessageObject(message, senderId, board, index);
                } else {
                    // reuseable
                    moForMsgTable = sentMo;
                }
                moForMsgTable.setSignatureStatusVERIFIED_V2();
                TOF.getInstance().receivedValidMessage(moForMsgTable, board, index);
            }

            // finally delete the message in unsend messages db table
            mo.setCurrentUploadThread(null); // must be marked as not uploading before delete!
            UnsentMessagesManager.deleteMessage(mo);

        } catch (final Throwable t) {
            logger.log(Level.SEVERE, "Catched exception", t);
            return UPLOADMESSAGE_FAILED;
        } finally { // NOTE: "finally" execs on both success and failure (catch)
            mo.setCurrentUploadThread(null); // paranoia
        }

        logger.info("Message upload finished");
        return UPLOADMESSAGE_OK;
    }

    /**
     * This method composes the downloading key for the message, given a
     * certain index number
     * @param index index number to use to compose the key
     * @return they composed key
     */
    public String composeDownloadKey(final MessageXmlFile message, final int index) {
        String key;
        if (board.isWriteAccessBoard()) {
            key = new StringBuilder()
                    .append(board.getPublicKey())
                    .append("/")
                    .append(board.getBoardFilename())
                    .append("/")
                    .append(message.getDateStr())
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        } else {
            key = new StringBuilder()
                    .append("KSK@frost/message/")
                    .append(Core.frostSettings.getValue(SettingsClass.MESSAGE_BASE))
                    .append("/")
                    .append(message.getDateStr())
                    .append("-")
                    .append(board.getBoardFilename())
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        }
        return key;
    }

    /**
     * This method composes the uploading key for the message, given a
     * certain index number
     * @param index index number to use to compose the key
     * @return they composed key
     */
    public String composeUploadKey(final MessageXmlFile message, final int index) {
        String key;
        if (board.isWriteAccessBoard()) {
            key = new StringBuilder()
                    .append(board.getPrivateKey())
                    .append("/")
                    .append(board.getBoardFilename())
                    .append("/")
                    .append(message.getDateStr())
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        } else {
            key = new StringBuilder()
                    .append("KSK@frost/message/")
                    .append(Core.frostSettings.getValue(SettingsClass.MESSAGE_BASE))
                    .append("/")
                    .append(message.getDateStr())
                    .append("-")
                    .append(board.getBoardFilename())
                    .append("-")
                    .append(index)
                    .append(".xml")
                    .toString();
        }
        return key;
    }
}
