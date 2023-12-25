/*
  RunningBoardUpdateThreads.java / Frost
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

import org.joda.time.*;

import java.util.logging.*;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import frost.SettingsClass;
import frost.messaging.frost.UnsentMessagesManager;
import frost.messaging.frost.boards.Board;
import frost.messaging.frost.boards.BoardUpdateInformation;
import frost.messaging.frost.boards.BoardUpdateThread;
import frost.messaging.frost.boards.BoardUpdateThreadListener;

/**
 * This class maintains the message download and upload threads.
 * Listeners for thread started and thread finished are provided.
 */
public class RunningBoardUpdateThreads implements BoardUpdateThreadListener {
	
	private static final Logger logger = Logger.getLogger(MessageThread.class.getName());

    // listeners are notified of each finished thread
    Hashtable<String,Vector<BoardUpdateThreadListener>> threadListenersForBoard = null; // contains all listeners registered for 1 board
    Vector<BoardUpdateThreadListener> threadListenersForAllBoards = null; // contains all listeners for all boards

    // contains key=board, data=vector of BoardUpdateThread's, (max. 1 of a kind (MSG_DOWNLOAD_TODAY,...)
    Hashtable<String,Vector<BoardUpdateThread>> runningDownloadThreads = null;

    public RunningBoardUpdateThreads() {
        threadListenersForBoard = new Hashtable<String,Vector<BoardUpdateThreadListener>>();
        threadListenersForAllBoards = new Vector<BoardUpdateThreadListener>();

        runningDownloadThreads = new Hashtable<String,Vector<BoardUpdateThread>>();
    }

    /**
     * if you specify a listener and the method returns true (thread is started), the listener
     * will be notified if THIS thread is finished
     * before starting a thread you should check if it is'nt updating already.
     */
    public boolean startMessageDownloadToday( // "Today" message downloader
        final Board board,
        final SettingsClass config,
        final BoardUpdateThreadListener listener) {

        final MessageThread tofd = new MessageThread(
                /*downloadToday=*/true,
                board,
                board.getMaxMessageDownload(),
                0); // start at day 0 (today)

        // register listener and this class as listener
        tofd.addBoardUpdateThreadListener(this);
        if (listener != null) {
            tofd.addBoardUpdateThreadListener(listener);
        }
        // store thread in threads list
        getVectorFromHashtable(runningDownloadThreads, board).add(tofd);

        // start thread
        tofd.start();
        return true;
    }
    /**
     * if you specify a listener and the method returns true (thread is started), the listener
     * will be notified if THIS thread is finished
     * before starting a thread you should check if it is'nt updating already.
     */
    public boolean startMessageDownloadBack( // "All Days Back" message downloader
        final Board board,
        final SettingsClass config,
        final BoardUpdateThreadListener listener,
        final boolean resume)
    {
        int maxDaysBack = board.getMaxMessageDownload();
        // the default start-day value is 1 ("today"), but the user can change it.
        // NOTE: we subtract 1 because the value is in the human-readable GUI
        // range, where 1 means "today" (aka a start-day offset of 0 days back),
        // and where the maximum value is equal to maxDaysBack.
        // so it's -1 when we *read* the start-day, and +1 when we *display* it in the GUI.
        // the reason for the use of a GUI-centric value input-range is that
        // a range of "Day 1/30 (2015-11-17)" to "Day 30/30 (2015-10-19)" makes a LOT
        // more sense to users than "Day 0/30" to "Day 29/30" (being the final day).
        int startDay = board.getStartDaysBack() - 1;
        if( startDay < 0 )
            startDay = 0;

        if( resume ) {
            // figure out how many days ago they pressed the "stop refresh" button to stop their scan.
            DateTime today = new DateTime(DateTimeZone.UTC);
            DateTime abortDay = board.getLastDayBoardUpdatedObj(); // UTC time of when they pressed "Stop Refresh"
            int differenceDays = Days.daysBetween(abortDay.withTimeAtStartOfDay(), today.withTimeAtStartOfDay()).getDays();
            logger.info(board.getName()+ ". RESUME: abortDay="+abortDay.toString("yyyy-MM-dd") + ", today="+today.toString("yyyy-MM-dd") + ", differenceDays="+differenceDays);

            /* EXPLANATION:
             * Imagine that they aborted the scan on abortDay = 2015-11-15, and that they were
             *   currently downloading "day 2" (2015-11-13) when they aborted it.
             * Now imagine that they're resuming on today = 2015-11-18. That's a difference of
             *   3 days. So to properly resume from the day of 2015-11-13, we need:
             *   lastDayStarted (2) + differenceDays (3) = start downloading Day 5.
             *   What is day 5? It's 2015-11-18 - 5 = 2015-11-13.
             * We must also extend the "max days backwards" by the amount of days that has passed,
             *   so that a "resume from day 32" will properly run even if max days is i.e. 30.
             * Since we're extending both start and max by the differenceDays, we'll download
             *   exactly as many days as the user wants for their "all days back" scan.
             */
            startDay = board.getLastAllDayStarted() + differenceDays;
            maxDaysBack += differenceDays;
            logger.info(board.getName() + ". lastAllDayStarted: " + board.getLastAllDayStarted());
            logger.info(board.getName() + ". (adjusted) startDay: " + startDay);
            logger.info(board.getName() + ". (adjusted) maxDaysBack: " + maxDaysBack);
        }

        final MessageThread backload = new MessageThread(
                /*downloadToday=*/false,
                board,
                maxDaysBack,
                startDay);

        // register listener and this class as listener
        backload.addBoardUpdateThreadListener(this);
        if (listener != null) {
            backload.addBoardUpdateThreadListener(listener);
        }
        // store thread in threads list
        getVectorFromHashtable(runningDownloadThreads, board).add(backload);

        // start thread
        backload.start();

        return true;
	}

    /**
     * Gets an Vector from a Hashtable with given key. If key is not contained
     * in Hashtable, an empty Vector will be created and put in the Hashtable.
     */
    private <T> Vector<T> getVectorFromHashtable(final Hashtable<String,Vector<T>> t, final Board key) {
        Vector<T> retval = null;
        synchronized( t ) {
            retval = t.get(key.getName());
            if( retval == null ) {
                retval = new Vector<T>();
                t.put(key.getName(), retval);
            }
        }
        return retval;
    }

    /**
     * Returns the list of current download threads for a given board. Returns an empty list of no thread is running.
     */
    public Vector<BoardUpdateThread> getDownloadThreadsForBoard(final Board board) {
        return getVectorFromHashtable( runningDownloadThreads, board );
    }

    /**
     * Adds a listener that gets notified if any thread for a given board did update its state.
     * For supported states see BoardUpdateThreadListener methods.
     */
    public void addBoardUpdateThreadListener(final Board board, final BoardUpdateThreadListener listener) {
        getVectorFromHashtable(threadListenersForBoard, board).remove(listener); // no doubles allowed
        getVectorFromHashtable(threadListenersForBoard, board).add(listener);
    }

    /**
     * Adds a listener that gets notified if any thread for any board did update its state.
     * For supported states see BoardUpdateThreadListener methods.
     */
    public void addBoardUpdateThreadListener(final BoardUpdateThreadListener listener) {
        threadListenersForAllBoards.remove( listener ); // no doubles allowed
        threadListenersForAllBoards.add( listener );
    }
    /**
     * Removes a listener that gets notified if any thread for a given board did update its state.
     * For supported states see BoardUpdateThreadListener methods.
     * Method will do nothing if listener is null or not contained in the list of listeners.
     */
    public void removeBoardUpdateThreadListener(final Board board, final BoardUpdateThreadListener listener) {
        getVectorFromHashtable(threadListenersForBoard, board).remove(listener);
    }

    /**
     * Removes a listener that gets notified if any thread for any board did update its state.
     * For supported states see BoardUpdateThreadListener methods.
     * Method will do nothing if listener is null or not contained in the list of listeners.
     */
    public void removeBoardUpdateThreadListener(final BoardUpdateThreadListener listener) {
        threadListenersForAllBoards.remove( listener );
    }

    /**
     * Implementing the listener for thread finished.
     * Notifies all interested listeners for change of the thread state.
     * @see frost.messaging.frost.boards.BoardUpdateThreadListener#boardUpdateThreadFinished(frost.messaging.frost.boards.BoardUpdateThread)
     */
    public void boardUpdateThreadFinished(final BoardUpdateThread thread) {
        // remove from thread list
        Vector<BoardUpdateThread> threads = getVectorFromHashtable(runningDownloadThreads, thread.getTargetBoard());

        if( threads != null ) {
            threads.remove(thread);
        }

        synchronized( threadListenersForAllBoards ) {
            // notify listeners
            Iterator<BoardUpdateThreadListener> i = threadListenersForAllBoards.iterator();
            while( i.hasNext() ) {
                i.next().boardUpdateThreadFinished(thread);
            }
            i = getVectorFromHashtable(threadListenersForBoard, thread.getTargetBoard()).iterator();
            while( i.hasNext() ) {
                i.next().boardUpdateThreadFinished(thread);
            }
        }
    }

    /**
     * Implementing the listener for thread started. Notifies all interested listeners for change of the thread state.
     * @see frost.messaging.frost.boards.BoardUpdateThreadListener#boardUpdateThreadStarted(frost.messaging.frost.boards.BoardUpdateThread)
     */
    public void boardUpdateThreadStarted(final BoardUpdateThread thread) {
        synchronized( threadListenersForAllBoards ) {
            Iterator<BoardUpdateThreadListener> i = threadListenersForAllBoards.iterator();
            while( i.hasNext() ) {
                (i.next()).boardUpdateThreadStarted(thread);
            }
            i = getVectorFromHashtable(threadListenersForBoard, thread.getTargetBoard()).iterator();
            while( i.hasNext() ) {
                i.next().boardUpdateThreadStarted(thread);
            }
        }
    }

    public void boardUpdateInformationChanged(final BoardUpdateThread thread, final BoardUpdateInformation bui) {
        synchronized( threadListenersForAllBoards ) {
            Iterator<BoardUpdateThreadListener> i = threadListenersForAllBoards.iterator();
            while( i.hasNext() ) {
                i.next().boardUpdateInformationChanged(thread, bui);
            }
            i = getVectorFromHashtable(threadListenersForBoard, thread.getTargetBoard()).iterator();
            while( i.hasNext() ) {
                i.next().boardUpdateInformationChanged(thread, bui);
            }
        }
    }

    /**
     * Returns the count of ALL running download threads (of all boards).
     */
    public int getRunningDownloadThreadCount() { // msg_today, msg_back, files_update, update_id
        int downloadingThreads = 0;

        synchronized( runningDownloadThreads ) {
            final Iterator<Vector<BoardUpdateThread>> i = runningDownloadThreads.values().iterator();
            while( i.hasNext() ) {
                final Vector<BoardUpdateThread> v = i.next();
                if( v.size() > 0 ) {
                    downloadingThreads += v.size();
                }
            }
        }
        return downloadingThreads;
    }

    /**
     * Returns the count of boards that currently have running download threads.
     */
    public int getDownloadingBoardCount() {
        int downloadingBoards = 0;

        synchronized( runningDownloadThreads ) {
            final Iterator<Vector<BoardUpdateThread>> i = runningDownloadThreads.values().iterator();
            while( i.hasNext() ) {
                final Vector<BoardUpdateThread> v = i.next();
                if( v.size() > 0 ) {
                    downloadingBoards++;
                }
            }
        }
        return downloadingBoards;
    }

    /**
     * Returns all information together, faster than calling all single methods.
     *
     * @return a new information class containing status informations
     * @see RunningMessageThreadsInformation
     */
    public RunningMessageThreadsInformation getRunningMessageThreadsInformation() {

        final RunningMessageThreadsInformation info = new RunningMessageThreadsInformation();

        final int uploadingMessages = UnsentMessagesManager.getRunningMessageUploads();
        info.setUploadingMessageCount(uploadingMessages);
        // the manager count uploading messages as unsent, we show Uploading/Waiting in statusbar,
        // hence we decrease the unsent by uploading messages
        info.setUnsentMessageCount(UnsentMessagesManager.getUnsentMessageCount() - uploadingMessages);

        info.addToAttachmentsToUploadRemainingCount(FileAttachmentUploadThread.getInstance().getQueueSize());

        synchronized( runningDownloadThreads ) {
            final Iterator<Vector<BoardUpdateThread>> i = runningDownloadThreads.values().iterator();
            while( i.hasNext() ) {
                final Vector<BoardUpdateThread> v = i.next();
                final int vsize = v.size();
                if( vsize > 0 ) {
                    info.addToDownloadingBoardCount(1);
                    info.addToRunningDownloadThreadCount(vsize);
                }
            }
        }
        return info;
    }

    /**
     * Returns true if the given board have running download threads.
     */
    public boolean isUpdating(final Board board) {
        if( getVectorFromHashtable(runningDownloadThreads, board).size() > 0 ) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isThreadOfTypeRunning(final Board board, final int type) {
        final List<BoardUpdateThread> threads = getDownloadThreadsForBoard(board);
        for( int x = 0; x < threads.size(); x++ ) {
            if( threads.get(x).getThreadType() == type ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Allows you to stop all running download-threads for a certain board.
     * There will often be one "all days" thread and one or more "today"-threads,
     * and this takes care of interrupting *all* threads for the given board.
     *
     * This will "gently" tell the threads to stop, by setting a flag on all
     * threads for that board, telling them to stop after they're done with
     * the current day they're working on.
     */
    public boolean stopAllDownloadThreadsForBoard(final Board board) {
        boolean hadThreads = false;
        final List<BoardUpdateThread> threads = getDownloadThreadsForBoard(board);
        for( int x = 0; x < threads.size(); x++ ) {
            hadThreads = true;
            threads.get(x).setStopUpdatingFlag(true);
        }
        return hadThreads;
    }
}
