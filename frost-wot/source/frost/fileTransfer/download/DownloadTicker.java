/*
  DownloadTicker.java / Frost

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

import java.io.*;

import frost.*;
import frost.fileTransfer.*;
import frost.util.*;

// NOTE: This download ticker starts new downloads every second *if*
// persistence (global queue) is disabled. But if the global queue is
// used, then the persistence manager is responsible for downloads instead.
public class DownloadTicker extends Thread {

	/**
	 * The number of allocated threads is used to limit the total of threads
	 * that can be running at a given time, whereas the number of running
	 * threads is the number of threads that are actually running.
	 */
	private int allocatedThreads = 0;
	private int runningThreads = 0;

	private final Object threadCountLock = new Object();

	public DownloadTicker() {
		super("Download");
	}

	/**
	 * This method is called to find out if a new thread can start. It temporarily
	 * allocates it and it will have to be relased when it is no longer
	 * needed (no matter whether the thread was actually used or not).
	 * @return true if a new thread can start. False otherwise.
	 */
	private void allocateDownloadThread() {
		synchronized (threadCountLock) {
			allocatedThreads++;
		}
	}

    private boolean canAllocateDownloadThread() {
         synchronized (threadCountLock) {
             if (allocatedThreads < Core.frostSettings.getIntValue(SettingsClass.DOWNLOAD_MAX_THREADS)) {
                 return true;
             }
         }
         return false;
    }

	/**
	 * This method is used to release a thread.
	 */
	private void releaseThread() {
		synchronized (threadCountLock) {
			if (allocatedThreads > 0) {
				allocatedThreads--;
			}
		}
	}

	/**
	 * This method returns the number of threads that are running
	 * @return the number of threads that are running
	 */
	public int getRunningThreads() {
		return runningThreads;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
    @Override
    public void run() {
        super.run();
        while (true) {
            Mixed.wait(1000);

            if( PersistenceManager.isPersistenceEnabled() == false ) {
                startDownloadThread();
            } else {
                // since persistence is enabled, we don't need to check every second if we should
                // start a non-persistent download thread... so wait half a minute instead...
                Mixed.wait(29000);
            }
        }
    }

	/**
	 * This method is usually called from a thread to notify the ticker that
	 * the thread has finished (so that it can notify its listeners of the fact). It also
	 * releases the thread so that new threads can start if needed.
	 */
	void threadFinished() {
		runningThreads--;
		releaseThread();
	}

	/**
	 * This method is called from a thread to notify the ticker that
	 * the thread has started (so that it can notify its listeners of the fact)
	 */
	void threadStarted() {
		runningThreads++;
	}

    /**
     * Maybe start a new download automatically.
     */
    private void startDownloadThread() {
        if( ! Core.frostSettings.getBoolValue(SettingsClass.DOWNLOADING_ACTIVATED) ||
            ! Core.isFreenetOnline() ||
            ! canAllocateDownloadThread() ) {
            return;
        }

        final FrostDownloadItem dlItem = FileTransferManager.inst().getDownloadManager().selectNextDownloadItem();
        startDownload(dlItem);
    }

    /**
     * Performs a download using Frost's own internal, non-persistent queue. This function is
     * normally not called, since Frost has the persistent queue enabled by default.
     * The persistent queue downloads are handled by PersistenceManager.java:startDownload() instead.
     *
     * NOTE: The synchronized attribute ensures that the user's "start selected downloads now" and
     * "restart selected downloads" features don't trigger startDownload multiple times for the same
     * file (autostart thread+the menu item). They'll still trigger, but they'll all execute in
     * series thanks to the synchronization, which means that the first execution takes care of
     * setting the state for the file. the additional calls will then see the new state and won't
     * waste any time trying to download the same file again.
     *
     * @param {FrostDownloadItem} dlItem - the model item to begin downloading
     * @return - false if the download cannot start (such as if the item is missing,
     * or isn't a waiting item, etc), true if the download began
     */
    public synchronized boolean startDownload(final FrostDownloadItem dlItem) {
        if( ! Core.frostSettings.getBoolValue(SettingsClass.DOWNLOADING_ACTIVATED) ||
            ! Core.isFreenetOnline() ||
            ! canAllocateDownloadThread() ) {
            return false;
        }

        if( dlItem == null || dlItem.getState() != FrostDownloadItem.STATE_WAITING ) {
            return false;
        }

        // increase allocated threads
        allocateDownloadThread();

        // start the download
        dlItem.setDownloadStartedTime(System.currentTimeMillis());
        dlItem.setState(FrostDownloadItem.STATE_TRYING);

        final File targetFile = new File(dlItem.getDownloadFilename());
        final DownloadThread newRequest = new DownloadThread(this, dlItem, targetFile);
        newRequest.start();
        return true;
    }
}
