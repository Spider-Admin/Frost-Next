/*
 BlocksPerMinuteCounter.java / Frost-Next
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

public class BlocksPerMinuteCounter
{
    private static final Object LOCK = new Object();

    /* Static variables */
    // After MAX_MEASUREMENT_AGE of zero activity, measurements will be considered stale and
    // we'll reset the speed calculations. This helps with rapidly reacting to changes in speed
    // when stalled/paused transfers become active again.
    // Downloads: 300k ms = 5 minutes (tuned for downloads, which always update quickly)
    public static final int DOWNLOAD_MAX_MEASUREMENT_AGE = 300000;
    // Uploads: 600k ms = 10 minutes (tuned for uploads, which usually update quickly but then
    // stall for a long time when they reach the final blocks)
    public static final int UPLOAD_MAX_MEASUREMENT_AGE = 600000;
    // The UPDATE_RATE determines how much time must pass before we accept a call to
    // updateBlocksPerMinute(); this is because Freenet sends the FCP SimpleProgress messages
    // extremely quickly, so we need to wait a while before we can extrapolate a speed.
    // Lower values cause more fluctuation in the estimations, since it becomes harder to
    // extrapolate the current sample's blocks/minute from shorter windows. The chosen value
    // of 15s (1/4 of a minute) has proven itself to be very accurate and responsive, and should
    // not be modified!
    // WARNING: Do NOT try 5s; accepting new samples with such a short delay is too fast for
    // Freenet and leads to wildly varying estimations. A value of 10s (1/6 of a minute) works
    // very well and is accurate and responsive, but is still too swingy when the desire is
    // a stable average to calculate remaining upload/download times.
    private static final int UPDATE_RATE = 15000; // 15k ms = 15 seconds
    // the SMOOTHING_FACTOR is a value from 0 to 1 and controls the importance of the previous
    // sample; higher number = faster discarding of old samples. if you've got a reasonably
    // accurately-guessing UPDATE_RATE, then you can use higher values so that it reacts quicker
    // to changes in speed
    // NOTE: to visualize this, imagine the measurements as peaks/valleys on a sine wave; by
    // lowering the factor, the wild spikes on the sine normalize into a smoothly rising/falling
    // line instead.
    // also important: the chosen smoothing factor is also dynamically adjusted based on the
    // speed-difference of the latest sample; if there's a sharp rise in speed, it will react
    // more strongly than the usual factor; and if there's a sharp drop in speed it will react
    // less strongly. the factor will only be used as-is if the latest speed is within +/- 40%
    // of the average. this ensures that the filter reacts quickly to rising speeds, and that
    // it doesn't suddenly drop towards the end of transfers where the final blocks start coming
    // in much less frequently.
    // Downloads: 30% of the new sample and 70% of the old sample (tuned for downloads, where we
    // have enough samples available at a rapid pace to be able to smooth the results very nicely
    // and ensure a stable measurement)
    public static final double DOWNLOAD_SMOOTHING_FACTOR = 0.30;
    // Uploads: 40% of the new sample and 60% of the old sample (tuned for uploads, which usually
    // vary a lot in speed over time and therefore need to react a bit more quickly to changes
    // in speed)
    public static final double UPLOAD_SMOOTHING_FACTOR = 0.40;
    // this constant is used in the calculation of blocks-to-bytes; all blocks in Freenet are 32KiB
    private static final long CONST_32k = 32 * 1024;

    /* Per-instance variables */
    private int fMaxMeasurementAge = BlocksPerMinuteCounter.DOWNLOAD_MAX_MEASUREMENT_AGE;
    private double fSmoothingFactor = BlocksPerMinuteCounter.DOWNLOAD_SMOOTHING_FACTOR;
    private volatile long fLastMeasurementTime = -1;
    private volatile int fLastMeasurementBlocks = 0;
    private volatile double fAverageBlocksPerMinute = -1;

    /**
     * The constructor optionally lets you set the sample discard rate and smoothing factor;
     * otherwise it defaults to the download-tuned values.
     * Remember to always use the provided UPLOAD constants if tracking uploads, since those are
     * much slower and need different tunings.
     * @param {optional int} aMaxMeasurementAge - the desired maximum sample age, in milliseconds;
     * uses DOWNLOAD defaults if not provided
     * @param {optional double} aSmoothingFactor - the smoothing factor, as a value from 0 to 1;
     * uses DOWNLOAD defaults if not provided
     */
    public BlocksPerMinuteCounter() {}
    public BlocksPerMinuteCounter(
        final int aMaxMeasurementAge,
        final double aSmoothingFactor)
    {
        fMaxMeasurementAge = aMaxMeasurementAge;
        fSmoothingFactor = aSmoothingFactor;
    }

    /**
     * Returns the calculated average blocks per minute, along with some status codes to help you
     * determine what to display in the GUI. If the value is outdated, it will not be returned.
     * Fully thread-safe.
     * @return - "-1" if no measurement exists yet, "-2" if the measurement is *very* outdated
     * and has been discarded (no recent transfer activity), otherwise a 0+ positive floating
     * point value describing the average blocks per minute.
     */
    public double getAverageBlocksPerMinute()
    {
        synchronized(LOCK) {
            final long currentMillis = System.currentTimeMillis();
            final long millisSinceLastMeasurement = ( currentMillis - fLastMeasurementTime );

            if( fLastMeasurementTime == -1 || fLastMeasurementBlocks == 0 || fAverageBlocksPerMinute == -1 ) {
                // -1 = No measurement exists yet; you may want to display something like
                // "Measuring..." or "Calculating..." in the GUI, or even "No activity..."
                return -1;
            } else if( millisSinceLastMeasurement > fMaxMeasurementAge ) {
                // -2 = Measurement is outdated (no activity in fMaxMeasurementAge); you may
                // want to display "0 blocks per minute" or "No activity..." in the GUI
                return -2;
            } else {
                return fAverageBlocksPerMinute;
            }
        }
    }

    /**
     * Returns the transfer speed as the number of bytes per minute, which is useful when
     * presenting more human-readable values. Fully thread-safe.
     * Tip: Use "Mixed.convertBytesToHuman()" to get a nicely formatted string out of this value.
     * @return - "-1" if no measurement/outdated measurement, otherwise the number of
     * bytes (0 or higher) as a rounded value
     */
    public long getAverageBytesPerMinute()
    {
        final double bpm = getAverageBlocksPerMinute();
        if( bpm < 0 ) {
            return -1;
        }
        return Math.round(bpm * CONST_32k);
    }

    /**
     * Returns the transfer speed as the number of bytes per second, which is useful when
     * presenting more human-readable values. Fully thread-safe.
     * Tip: Use "Mixed.convertBytesToHuman()" to get a nicely formatted string out of this value.
     * @return - "-1" if no measurement/outdated measurement, otherwise the number of
     * bytes (0 or higher) as a rounded value
     */
    public long getAverageBytesPerSecond()
    {
        final double bpm = getAverageBlocksPerMinute();
        if( bpm < 0 ) {
            return -1;
        }
        return Math.round((bpm / 60) * CONST_32k);
    }

    /**
     * Lets you determine the time of the most recent accepted update to the average speed.
     * Checking this value lets you do things like showing "No activity..." in the GUI at an
     * earlier rate than the fMaxMeasurementAge hard-cap in getAverageBlocksPerMinute(), and
     * is recommended instead of relying on the hard-cap. Fully thread-safe.
     * Also see getMillisSinceLastMeasurement().
     * @return - the time of measurement, relative to System.currentTimeMillis(). if the value
     * is -1, it means that the reset/update functions have not been called yet
     */
    public long getLastMeasurementTime()
    {
        return fLastMeasurementTime;
    }

    /**
     * If you only care about the actual amount of time elapsed since the last accepted update
     * (instead of the full timestamp), then use this function instead of getLastMeasurementTime().
     * Fully thread-safe.
     * @return - the number of milliseconds since the last accepted update, or -1
     * if the reset/update functions have not been called yet.
     */
    public long getMillisSinceLastMeasurement()
    {
        final long currentMillis = System.currentTimeMillis();
        return ( fLastMeasurementTime < 0 ? -1 : ( currentMillis - fLastMeasurementTime ) );
    }

    /**
     * Tells you how many "done" blocks were seen during the calculation of the last update.
     * Fully thread-safe.
     * NOTE: The main purpose of this is to allow you to calculate an estimated transfer completion
     * time, by looking at the last measurement's average download rate, block count and timestamp.
     * Subtract that (past) "done block count" from the file's "required (download) / total (upload)
     * block count" (the number of blocks the file consists of), then divide that by the average
     * blocks per minute to get the time until completion at that speed. Finally, subtract the time
     * since the last meaasurement, and you're done.
     * @return - the blocks seen when the last update was accepted; will be 0 if the transfer hasn't
     * started yet, but don't use it for that purpose (check if the last measurement time is < 0 instead).
     */
    public int getLastMeasurementBlocks()
    {
        return fLastMeasurementBlocks;
    }

    /**
     * Manually forces the block counter to reset its state. Fully thread-safe.
     * There are only *two* reasons why you'd *ever* want to manually call this: #1 is if you want
     * to restart the measurement and calculate a new average from that point in time onwards (kinda
     * pointless, since even an outdated value quickly stabilizes over time anyway), in which case
     * you'd send the current number of done blocks as the parameter. #2 (very important) is that
     * you *must* always trigger this function manually whenever your transfer changes state to
     * "in progress", so that you're guaranteed that its first sample is measuring from the start-time
     * of when the transfer actually began, otherwise the first sample will be wrong and it'll take
     * a while for it to correct itself due to the smoothing factor. When doing a "transfer now
     * starting" reset, always send a value of 0 as the current block count.
     * To perform a manual reset in scenario #2, the correct usage is to do the following in the
     * "setState()" function in the Frost[Upload/Download]Item classes:
     * "if( state != newState && newState == FrostDownloadItem.STATE_PROGRESS ) { bpmCounter.resetBlocksPerMinute(0); }"
     * NOTE: The order of calls to setState/setDoneBlocks in the other classes does not matter;
     * regardless of whether the reset to 0 happens before or after the first SimpleProgress
     * message, the end-result will be the same thanks to the static update-rate and the intelligent
     * "0"-baseline auto-adjustment.
     * @param {int} aCurrentDoneBlocks - the number of blocks to use as the new "baseline" after the reset
     */
    public void resetBlocksPerMinute(
            final int aCurrentDoneBlocks)
    {
        synchronized(LOCK) {
            final long currentMillis = System.currentTimeMillis();
            fLastMeasurementTime = currentMillis;
            fLastMeasurementBlocks = aCurrentDoneBlocks; // ensures that the new measurement will see the number of blocks since this reset
            fAverageBlocksPerMinute = -1;
        }
    }

    /**
     * Intelligently calculates the blocks-per-minute given the current input value and the history
     * of observed values. You almost never have to call resetBlocksPerMinute() manually, since this
     * detects all common reasons for needing a reset and performs one automatically. Fully thread-safe.
     * The most common usage is to call this with the "newDoneBlocks" value from the "setDoneBlocks()"
     * function in the Frost[Upload/Download]Item classes.
     * NOTE: It is resilient against "bursts" of data after the initial 0-reset, since it always waits
     * for a non-zero value to use as the new baseline before it measures anything. This is done
     * because Freenet transfers can sit at 0 internally in Frost but be much higher in Freenet
     * itself, so if we accepted the first value as a jump from 0 to 16434 blocks in 1 second, for
     * instance, then we'd calculate the wrong value. Furthermore, we're resilient against the
     * "rapid bursts" of data that happen when transfers are restarted and most of the data is
     * already local; we smooth the values and measure at regular intervals, which ensures that those
     * bursts barely influence the readings, and that they are quickly smoothed and corrected over time.
     * NOTE: If you repeat the same "aCurrentDoneBlocks" value several times in a row, and the
     * UPDATE_RATE requirement is fulfilled, then it will calculate a "current block rate" of 0, and
     * will cause the "average blocks per minute" counter to tend towards 0.00, at the speed of the
     * SMOOTHING_FACTOR. This is the correct behavior, but means that if you run a GUI thread which
     * forcibly updates the value every X seconds or whatever, then it will (correctly) reduce the
     * transfer rate for any transfers that still haven't gotten any new blocks since last update.
     * NOTE: It's safe to hook this directly into the "SimpleProgress" FCP messages; if a transfer
     * is stalled, you won't be getting any more SimpleProgress messages, so the average will stay
     * at the last calculated amount until the next SimpleProgress finally arrives. And the data
     * retrieval functions will take care of hard-filtering any really outdated measurements after
     * a while. Furthermore, you can manually check the age of the measurement and treat as "old=stalled".
     * @param {int} aCurrentDoneBlocks - the total number of done blocks right now
     * @return - true if the measurement was accepted, false if it was denied. the most common
     * reason for denying is that it hasn't yet been "UPDATE_RATE" seconds since the last measurement.
     * in that case, it's up to the user to decide if they want to re-send the measurement later.
     * if you're using SimpleProgress messages, then there's no need to do that manually, since they
     * come in at a very rapid rate. and if you're running some sort of timer to repeatedly check the
     * total blocks per minute throughput even when it hasn't changed, then feel free to simply send
     * the current value repeatedly and just ignore the return status from this function.
     */
    public boolean updateBlocksPerMinute(
            final int aCurrentDoneBlocks)
    {
        // ignore negative block counts
        if( aCurrentDoneBlocks < 0 ) {
            return false;
        }

        synchronized(LOCK) {
            final long currentMillis = System.currentTimeMillis();
            final long millisSinceLastMeasurement = ( currentMillis - fLastMeasurementTime );
            final int doneBlocksDifference = aCurrentDoneBlocks - fLastMeasurementBlocks;

            // if this is NOT a reset to 0 blocks, and the new block count is -1 or -2 lower than
            // last time, then we've encountered Freenet's bug where it sometimes sends SimpleProgress
            // messages with a block count that's 1 (maybe even 2) steps lower than its previous
            // message; in that case, just ignore this update...
            if( aCurrentDoneBlocks != 0 && doneBlocksDifference < 0 && doneBlocksDifference >= -2 ) {
                return false;
            }

            // first of all, reset the speed-measurement if it's no longer reliable:
            // if this is the first-ever progress update message, or if the previous block count
            // was "0" (in which case we may be looking at an ongoing transfer and will need to use
            // its *current* count as the baseline before we *really* start measuring), or if the block
            // count is lower than last time (indicates a restarted transfer), or if it's been longer
            // than fMaxMeasurementAge since the last incoming block...
            // NOTE: this catches resets to 0 blocks, as well as backwards jumps of any size; whereas
            // the other statement above only catches backward jumps of precisely -1 to -2.
            // NOTE: if the person's first-ever update (when fLastMeasurementTime is -1) is higher
            // than zero, or if the previous block count was "0", then the *current* block count is
            // used as the "starting point" for all measurements; that is intentional, since we don't
            // want a huge "first update = 3000" to *assume* that it has downloaded 3000 blocks in
            // a few milliseconds; instead, we make the first-ever non-zero update the new "baseline
            // value". as for resets back to zero, they're caught here as well.
            if( fLastMeasurementTime == -1 || fLastMeasurementBlocks == 0 || doneBlocksDifference < 0 || millisSinceLastMeasurement > fMaxMeasurementAge ) {
                resetBlocksPerMinute(aCurrentDoneBlocks);
                return true;
            } else if ( millisSinceLastMeasurement > BlocksPerMinuteCounter.UPDATE_RATE ) {
                // alright; blockdiff is 0+ and it has been at least UPDATE_RATE seconds since
                // the last progress update, so check the "blocks per minute" since last measurement
                double minutesElapsed = (double)millisSinceLastMeasurement / 60000; // 60k milliseconds = 1 minute
                final double latestBlocksPerMinute = (double)doneBlocksDifference / minutesElapsed; // exact bpm since last measurement; lots of decimals

                // now update the average blocks per minute, using a smoothing algorithm
                if( fAverageBlocksPerMinute == -1 ) {
                    // if there was no previous measurement, just save the new value as-is
                    fAverageBlocksPerMinute = latestBlocksPerMinute;
                } else {
                    // smooth the latest and previous averages together to produce a moving-average
                    double smartSmoothingFactor = fSmoothingFactor;
                    if( fAverageBlocksPerMinute > 0 ) { // if a non-zero average measurement exists...
                        final double relativeSpeedChange = ( latestBlocksPerMinute / fAverageBlocksPerMinute );
                        if( relativeSpeedChange < 0.6 ) {
                            // if the new measurement is < 60% of the average speed, we apply additional
                            // weighting to read 0.8x the usual factor from the new value. this ensures
                            // that the filter has a slow fall-time during very big dips in speed,
                            // such as towards the end of a download/upload
                            smartSmoothingFactor *= 0.8;
                        } else if( relativeSpeedChange > 1.7 ) {
                            // if the new measurement is > 70% faster than the average speed, we
                            // read 2.0x the usual factor from the new value. this gives the filter
                            // a very fast rise-time when the speeds go up suddenly, such as when a
                            // previously paused (priority 6) transfer is resumed
                            smartSmoothingFactor *= 2.0;
                        } else if( relativeSpeedChange > 1.4 ) {
                            // if the new measurement is > 40% faster than the average speed, we
                            // read 1.5x the usual factor from the new value
                            // this gives the filter a fast rise-time when the speeds begin going up
                            smartSmoothingFactor *= 1.5;
                        }
                        // otherwise, the measurement is within +/- 40% of the average speed, so
                        // we use the normal smoothing factor. this ensures that the filter settles
                        // smoothly once it has reached a correct measurement


                        // now let's also apply some importance-weighting to the smart smoothing factor:
                        // if 5 or less blocks were received since last measurement, then we've had
                        // a very low-volume (slow) transfer, which means that we can't really trust
                        // the transfer rate. so we'll further reduce the factor to 80% of its value.
                        // this idea is taken from stock trading, whose algorithms put more weight
                        // towards the "real value" of a stock whenever lots of trades happen, and
                        // less weight when only a few trades happen. the purpose is to help smooth out
                        // the huge dips/valleys in speed that happen during things like slow uploads.
                        if( doneBlocksDifference <= 5 ) {
                            smartSmoothingFactor *= 0.8;
                        }
                    }
                    fAverageBlocksPerMinute = ( smartSmoothingFactor * latestBlocksPerMinute ) + ( ( 1 - smartSmoothingFactor ) * fAverageBlocksPerMinute );
                }

                // if the caller has repeated the same block count twice (meaning there's been no
                // progress since the previous update), then we've just calculated a "latest blocks
                // per minute = 0" measurement above, and have caused the "average blocks per minute"
                // to start dropping at the rate of smartSmoothingFactor. the drops will be big at
                // first, but will get smaller each consecutive time that there's no difference,
                // since the factor is a relative scale. if the average has fallen below 3 blocks per
                // minute, then we'll just cut to the chase and jump directly to 0 to signify zero
                // transfer activity.
                if( doneBlocksDifference == 0 && fAverageBlocksPerMinute <= 3 ) {
                    fAverageBlocksPerMinute = 0.0;
                }

                // lastly, record the current measurement time and block count
                fLastMeasurementBlocks = aCurrentDoneBlocks;
                fLastMeasurementTime = currentMillis;

                return true;
            }

            // value not accepted (probably received sooner than UPDATE_RATE)
            return false;
        }
    }

    /**
     * Helper function which does an as-good-as-possible-on-Freenet job of calculating the transfer's
     * estimated time remaining in milliseconds. Very accurate for downloads, and does its best
     * to guess about the nearly-impossible-to-measure uploads (since those always stall towards
     * the end). Fully thread-safe.
     * NOTE: Multiple calls to this function can be performed without waiting for samples to
     * update, and it will continue to provide new completion time estimates.
     * Tip: Use "Mixed.convertMillisToHMmSs()" to get a nicely formatted hours/minutes/seconds
     * string out of this value.
     *
     * Time Estimate Accuracy Profile:
     *
     * - All Transfers:
     *   * Slow Start: Both downloads and uploads are slow in the beginning, and then gradually
     *     speed up their blocks/minute. This means that the first 1-2 minutes of measurement are
     *     not going to be very accurate, since the speed will keep rising until it stabilizes.
     *     The blocks/minute algorithm is tuned to quickly find the correct speed, but it's not going
     *     to be able to do miracles and "read minds" and predict what the future speed will be.
     *   * Network Conditions: Your peers are responsible for your transfer speed. If they're suddenly
     *     overloaded, your speeds will drop. If they're suddenly able to handle more blocks/minute,
     *     then your speeds will increase. The blocks/minute measurement is tuned to smooth out big
     *     swings and to stabilize around an accurate "average speed", but there's no way to predict
     *     that a "50 blocks/minute" will suddenly transfer at "600 blocks/minute", or vice versa.
     *     This will affect the time-estimate whenever Frost-Next notices the new blocks/minute speed.
     *   * Multiple Transfers: If you're running 2 or more transfers (uploads/downloads) in parallel,
     *     then Freenet will round-robin their transfers to advance a little bit of one file, and a little
     *     bit of another file, etc. This means that the individual transfer speeds for the various files
     *     will vary from minute to minute. The blocks/minute counter uses an advanced algorithm to smooth
     *     this out and still provide accurate measurements of both blocks/minute and time remaining.
     *     However, the accuracy *does* drop during multiple transfers, for obvious reasons: If Freenet
     *     doesn't move your transfers along at a consistent rate, then your transfers won't complete
     *     at a consistent rate. That's just logic. But we still do our best to smooth out the bumps
     *     and measure an accurate "average blocks/minute speed" and we then derive the "time remaining"
     *     estimate from that. This means that you'll still get *useful* estimates and BPM measurements,
     *     even though there's no way that they can be 100% correct since Freenet itself isn't transferring
     *     things at a consistent rate.
     *
     * - Downloads:
     *   * Small Downloads (0-50 MB): Somewhat accurate, but hasn't got enough time to measure the true speed.
     *   * Medium Downloads (50-100 MB): Accurate, thanks to having time to measure the blocks/minute speed.
     *   * Big Downloads (100+ MB): Very Accurate, due to the large amount of blocks giving us time to measure BPM.
     *   * Missing Blocks: If a download is missing blocks then they'll stall at the end, which we can't predict.
     *
     * - Uploads:
     *   * All Uploads: The blocks/minute measurement will be very accurate since uploads transfer at
     *     a consistent (albeit very slow) block rate. A rate of around 200 blocks/minute is common.
     *     However, the completion time estimate is difficult for uploads. The unpredictable factor
     *     is the insertion of metadata at the end of a transfer, which is extremely slow and takes
     *     a varying amount of time, which messes with our ability to predict an accurate completion time.
     *   * Small Uploads (0-15 MB): Impossible to predict. They transfer extremely slowly, at unpredictable BPM
     *     rates, and they stall at the final block (metadata) for usually around 1-20 minutes.
     *     Their predicted completion time will be useful as a guideline but nothing more.
     *   * Big Uploads (15+ MB): Somewhat accurate, since they're big enough that we can get a sense
     *     for their true blocks/minute speed (usually around 200 bpm), and therefore derive a useful
     *     completion time estimate from that. The metadata insertion is still an issue, so you may
     *     want to add a few minutes onto the time estimate. We don't do that automatically, since the
     *     metadata could take anywhere from 0 minutes to 30 minutes depending on all kinds of factors,
     *     including network conditions, transfer priority, and the "extra metadata inserts" setting.
     *
     * In short, enjoy the time estimate for what it is: The best that can be done on Freenet. It's
     * extremely useful for downloads, and somewhat accurate for uploads. Have fun! :-)
     *
     * @param {int} aCurrentDoneBlocks - how far the transfer has progressed right now.
     * @param {int} aTotalBlocks - the total number of blocks for the transfer (this is what we'll
     * compare the "current done blocks" against).
     * @param {int} aTransferType - either BlocksPerMinuteCounter.TRANSFERTYPE_DOWNLOAD or
     * BlocksPerMinuteCounter.TRANSFERTYPE_UPLOAD; may be used to affect this algorithm in the future,
     * since uploads are never finished when they reach 100% and still need more time after that.
     * @return - the number of milliseconds to completion; -1 if it cannot be estimated (usually
     * means speed is 0), 0 if it's already complete (or if it *should* have been complete by now
     * based on our guess), otherwise a 1+ number of estimated milliseconds remaining
     */
    public static final int TRANSFERTYPE_DOWNLOAD = 1; // these static constants are just related to this "estimated time left" helper,
    public static final int TRANSFERTYPE_UPLOAD = 2;   // ... so they don't really belong higher up with the general fields
    public long getEstimatedMillisRemaining(
            final int aCurrentDoneBlocks,
            final int aTotalBlocks,
            final int aTransferType)
    {
        if( aTotalBlocks <= 0 ) { return -1; }
        if( fLastMeasurementTime < 0 || fAverageBlocksPerMinute == -1 ) {
            return -1;
        }

        // alright, now calculate the estimated time remaining in milliseconds
        final int neededBlocks = ( aTotalBlocks - aCurrentDoneBlocks );
        if( neededBlocks > 0 ) {
            // NOTE: this is very swingy in the beginning before the average transfer rate has
            // been stabilized, but after that it's *very* accurate for downloads... as for
            // uploads, it's impossible to make an accurate measurement, but it's good enough
            // to give the user an idea of the transfer time (especially for big files)...
            // NOTE: we use the "average bpm" directly instead of going through the wrapper
            // function, so that we'll use the last value regardless of whether it's older than
            // max-age. that's because uploads will stall at the last block until the upload is
            // complete, so we need to re-use the last known measurement.
            long estimatedMillisRemaining = Math.round(( ( (double)neededBlocks / fAverageBlocksPerMinute ) * 60000 )); // 1 minute = 60k milliseconds
            if( estimatedMillisRemaining < 0 ) { estimatedMillisRemaining = 0; }
            return estimatedMillisRemaining;
        }
        return 0; // 0 = already complete
    }
}
