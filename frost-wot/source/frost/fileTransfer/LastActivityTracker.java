/*
 LastActivityTracker.java / Frost-Next
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

package frost.fileTransfer;

import frost.Core;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.fileTransfer.upload.FrostUploadItem;

/**
 * Keeps track of the last activity time of a Freenet block transfer (download/upload).
 *
 * Fully thread-safe, which ensures that multithreaded calls to modify the activity time always
 * run in series (so that they don't step on each other's toes). Be aware that we synchronize on
 * the whole object itself, which means that callers should never do their own synchronization on us.
 */
public class LastActivityTracker
{
    private Object fParentObject; // the download or upload item that owns us
    private volatile long fLastActivityMillis = 0;
    private volatile boolean fSeenActivityThisSession = false;

    /**
     * @param {Object} aParentObject - the FrostDownloadItem or FrostUploadItem that owns this tracker
     */
    public LastActivityTracker(
            final Object aParentObject)
    {
        fParentObject = aParentObject;
    }

    /**
     * [INTERNAL] Tells the parent object that a value has changed (in this case the activity timestamp).
     */
    /* NOTE:XXX: NOT NEEDED due to how we're only updating the activity tracker when transfer status changes!
    private void fireValueChanged()
    {
        if( fParentObject instanceof FrostDownloadItem ) {
            ((FrostDownloadItem)fParentObject).fireValueChanged();
        }
        else if( fParentObject instanceof FrostUploadItem ) {
            ((FrostUploadItem)fParentObject).fireValueChanged();
        }
    }
    */

    /**
     * [INTERNAL] Determines whether the parent object's transfer is currently "in progress".
     * @return - true if the transfer is currently "in progress", otherwise false
     */
    private boolean isTransferActive()
    {
        if( fParentObject instanceof FrostDownloadItem ) {
            return ( ((FrostDownloadItem)fParentObject).getState() == FrostDownloadItem.STATE_PROGRESS );
        }
        else if( fParentObject instanceof FrostUploadItem ) {
            return ( ((FrostUploadItem)fParentObject).getState() == FrostUploadItem.STATE_PROGRESS );
        }
        return false;
    }

    /**
     * Determines whether the "last activity time" value comes from the database or has been observed
     * during this Frost session.
     * @return - true if accurate value seen during this session, otherwise false
     */
    public boolean hasSeenActivityThisSession()
    {
        return fSeenActivityThisSession;
    }

    /**
     * Retrieves the timestamp of the last "transfer progress" activity.
     *
     * NOTE: This tracker does an incredibly good job of determining what progress happened during
     * this Frost session. Any progress that happens while Frost is closed is ignored, and the "last
     * activity" timestamp remains the last activity timestamp of when *Frost* saw the progress increase.
     * So if you have a transfer at 950 blocks and close Frost, and start it again 5 hours later and
     * it's now at 1020 blocks, then Frost still uses the old "last activity" value. It only updates
     * the activity value when *Frost* sees an increase while Frost is open. This behavior ensures
     * that we don't get into a situation where we start Frost after 3 days, and see new progress
     * for a transfer that began 3 days and 2 hours ago, and incorrectly set "last activity" to the
     * current time even though it hasn't transferred in days.
     *
     * Unfortunately there is no Freenet API to just ask it when the last activity happened. So this
     * is the best we can do. It ensures that we're only ever going to be "outdated" compared to the
     * real "last activity" time *if* data has been transferred while Frost was closed. But as soon as
     * Freenet sends new progress while Frost is open, it will learn the correct "last activity" time.
     *
     * Most transfers either stall completely (in which case our old progress time is still correct),
     * or are happily in-progress and will very soon get more progress updates while Frost remains
     * open (in which case our measured progress time will be correct again very soon).
     *
     * If you want to know whether the current timestamp is outdated (from a previous Frost session)
     * or whether it's activity that we've seen during *this* Frost session, then simply check the
     * return value of "hasSeenActivityThisSession()". If it's false, you know that the value may not
     * be correct (it will only be wrong if there has been further progress since the last Frost session).
     *
     * @return - always 0 if the transfer isn't currently marked "in progress", or if the transfer
     * *is* in progress but hasn't had any activity. so simply check for a positive return value
     * to determine if the transfer is active and has had any recent activity. ALSO NOTE: this never
     * returns a negative value! it's either 0 (no recent activity) or a unix timestamp in millis.
     */
    public long getLastActivityMillis()
    {
        if( fLastActivityMillis < 1 ||
            ! isTransferActive() ) {
            return 0;
        } else {
            return fLastActivityMillis;
        } 
    }

    /**
     * [INTERNAL] Sets the last activity time and notifies the GUI of the update. Do NOT call this
     * manually, *except* when you're reconstructing an old transfer object from the database at startup.
     *
     * NOTE: This doesn't update the fSeenActivityThisSession boolean. That fact allows you to manually call
     * this when restoring an old database value, without incorrectly marking it as "active this session".
     */
    public synchronized void setLastActivityMillis(
            final long aLastActivityMillis)
    {
        fLastActivityMillis = aLastActivityMillis;

        // make sure that the GUI is updated to reflect the latest activity time
        // NOTE:XXX: NOT NEEDED since we're always called from setDoneBlocks() and setState(),
        // which means that the item will always update its display anyway. so calling this
        // manually would just lead to dual GUI updates for no reason whatsoever.
        //fireValueChanged();
    }

    /**
     * Updates the last activity timestamp and marks the fact that there has been activity during
     * this Frost session (meaning transfer activity since Frost was most recently started).
     *
     * Important: Only call this from the "setDoneBlocks()" function of the parent object! Do not call
     * it when initially loading an object from the database!
     *
     * @param {int} aOldDoneBlocks - the old block count for this transfer
     * @param {int} aNewDoneBlocks - the new block count for this transfer
     */
    public synchronized void updateLastActivity(
            final int aOldDoneBlocks,
            final int aNewDoneBlocks)
    {
        // if this is a reset back to 0 done blocks, then simply unset the last activity time
        if( aNewDoneBlocks == 0 ) {
            unsetLastActivity();
            return;
        }

        // ignore the first update (if the previous done block count was 0), to prevent issues
        // with Frost. basically, Frost doesn't store the "done blocks" in its persistent database.
        // so if you start a transfer and it stalls at 1000 blocks for 5 hours, and you restart Frost,
        // then it would see a new "setDoneBlocks()" message of "old = 0, new = 1000" at the first
        // startup and would incorrectly update itself to say that its most recent activity was
        // now (as opposed to 5 hours ago). so for that reason, we always ignore the first update.
        //
        // furthermore, this means that any progress that happened while Frost was closed (i.e. you
        // close it at 950 blocks done, and start Frost again at 1000 blocks done) will be ignored
        // without updating the timestamp. that's the best we can do, since we don't know the old
        // block count and we don't know when the new blocks arrived.
        //
        // but as soon as Freenet sends further progress updates, we'll be correctly learning about
        // the new (latest) progress instead.
        if( aOldDoneBlocks == 0 ) {
            return;
        }

        // if there hasn't been any change in the progress, then we won't update the activity time.
        // this protects us against status updates for stalled transfers, where Freenet sends
        // the same block count multiple times even though it hasn't transferred any more data.
        //
        // NOTE: this is especially important since Freenet sends the same status message multiple
        // times at Frost's startup; so Frost would see "old = 0, new = 1000" (ignored by the above)
        // and then "old = 1000, new = 1000" (ignored by this). we only want actual progress updates!
        if( aNewDoneBlocks == aOldDoneBlocks ) {
            return;
        }

        // ignore negative new block counts (should never be able to happen, just a precaution)
        if( aNewDoneBlocks < 0 ) {
            return;
        }

        // now calculate how many new blocks we've received since our last progress
        final int doneBlocksDifference = aNewDoneBlocks - aOldDoneBlocks;

        // if this non-zero new block count is -1 or -2 lower than last time, then we've encountered
        // Freenet's bug where it sometimes sends SimpleProgress messages with a block count that's 1
        // (maybe even 2) steps lower than its previous message; in that case, just ignore this update.
        if( doneBlocksDifference < 0 && doneBlocksDifference >= -2 ) {
            return;
        }

        // if the block count is much lower than last time (-3 or more) then there's clearly a mismatch
        // between what Frost believed the progress to be, and what Freenet says it is (such as
        // if Freenet itself restarts a transfer that used to have more progress during Frost's last
        // session). in that case, simply unset the last activity time.
        if( doneBlocksDifference < 0 ) {
            unsetLastActivity();
            return;
        }

        // mark the fact that we've had activity during this Frost session (so that the "runtime
        // millis without progress" function can determine if the activity time comes from the
        // database or from actual, recent activity during this Frost session).
        fSeenActivityThisSession = true;

        // alright, we've got a valid, new progress value with a positive block change (1+ new
        // blocks). so now we simply have to update the activity time to the current system time.
        // NOTE: this takes care of firing the GUI update event as well.
        setLastActivityMillis(System.currentTimeMillis()); // millis since 1970 UTC
    }

    /**
     * Unsets the last activity time; used internally whenever the new progress needs a reset,
     * but you should also call this manually whenever a Freenet transfer begins. Call this from
     * the parent object's "setState()" function whenever a transfer becomes "in progress" (starting).
     *
     * Important: Do not call this during the initial "WAITING -> PROGRESS" update of an item loaded
     * from database! They always initialize to WAITING, so be sure to check if the parent item *was*
     * in a progress state during the last Frost session and don't fire the unsetting in that case,
     * since it wasn't a real (re)starting of the transfer!
     */
    public synchronized void unsetLastActivity()
    {
        // see "updateLastActivityMillis()" for explanation:
        fSeenActivityThisSession = true;
        setLastActivityMillis(0);
    }

    /**
     * Calculates how long ago a running transfer made any transfer progress.
     * NOTE: There's a lot of logic to return 0 ("no stalling") if the transfer or Freenet isn't
     * active, so that you can trust the value to be recent and accurate.
     *
     * FIXME/NOTE: This function is ONLY used as a helper for the "file sharing" feature that nobody uses.
     *
     * @return - 0 if no stalling, otherwise a positive number of milliseconds without progress
     */
    public long getRuntimeMillisWithoutProgress()
    {
        // if the last activity time is stale (comes from database) and hasn't been updated
        // since Frost was started, then just return 0 ("no stalling since last progress") to
        // prevent the "is it stalled?" checks from incorrectly comparing to a much earlier time
        //
        // FIXME/NOTE: This "runtime millis without progress" function is only used by the file
        // sharing feature, which nobody uses... We MUST return 0 as the "seconds without progress"
        // whenever we've loaded a database-value, to prevent the file sharing feature from thinking
        // that existing transfers have been stalled. However, that means that if a transfer is totally
        // stalled and has no progress (i.e. loaded from db with 1000 blocks, and progress at startup
        // is still at 1000 blocks, aka no change and no tracker update), then the file sharing feature
        // will forever think that it's not stalled. The only way to "fix" this would be to literally
        // count seconds during active transfers and to stop counting while a transfer is paused.
        // That's just way too much work for a tiny edge case in a feature nobody uses (file sharing).
        // If even a single block transfers, there will be activity this session and the file sharing
        // feature will properly be able to calculate the time elapsed without progress again. It
        // was simply a matter of choosing the lesser evil. Ignoring outdated progress timestamps
        // from the database is more important than this tiny issue with the file sharing's ability
        // to determine if a transfer is stalled. Furthermore, most people don't restart Frost, so
        // even if someone uses filesharing for some reason, they'll have activity this session in
        // almost 100% of all cases.
        if( ! hasSeenActivityThisSession() ) {
            return 0;
        }

        // if we're not connected to Freenet, or the transfer isn't currently running, then return 0
        if( ! Core.isFreenetOnline() ||
            ! isTransferActive() ) {
            return 0;
        }

        // grab the current system timestamp
        final long nowTime = System.currentTimeMillis(); // millis since 1970 UTC

        // now determine what we'll need to compare the system timestamp to
        long thenTime = 0;

        // first check if there has been recent transfer activity, and compare to that if possible
        final long lastActive = getLastActivityMillis(); // may be 0, in case of no activity yet
        if( lastActive > 0 ) {
            thenTime = lastActive;
        } else {
            // there hasn't been any transfer activity, so compare to when the transfer was started
            if( fParentObject instanceof FrostDownloadItem ) {
                thenTime = ((FrostDownloadItem)fParentObject).getDownloadStartedMillis();
            }
            else if( fParentObject instanceof FrostUploadItem ) {
                thenTime = ((FrostUploadItem)fParentObject).getUploadStartedMillis();
            }
        }

        // (paranoia) if there isn't a valid "then" timestamp, just return 0 ("no stalling")
        if( thenTime < 1 ) {
            return 0;
        }

        // now just calculate the number of milliseconds elapsed between the two timestamps
        final long timeDiff = nowTime - thenTime;
        if( timeDiff < 1 ) {
            return 0;
        }

        return timeDiff;
    }
}
