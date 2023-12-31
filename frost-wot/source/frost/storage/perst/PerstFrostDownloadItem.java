/*
  PerstFrostDownloadItem.java / Frost
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

import java.util.logging.Logger;

import org.garret.perst.Persistent;

import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.FrostFileListFileObject;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.storage.perst.filelist.FileListStorage;

/**
 * Class to make FrostDownloadItem persistent.
 * FrostDownloadItem itself extends ModelItem and cannot extend Persistent.
 */
public class PerstFrostDownloadItem extends Persistent {

    public String fileName;
    public String prefix;
    public String downloadDir;
    public long fileSize;
    public String key;

    public boolean enabled;
    public int state;
    public long downloadAddedTime;
    public long downloadStartedTime;
    public long downloadLastActivityTime;
    public long downloadFinishedTime;
    public int retries;
    public long lastDownloadStopTime;
    public String gqIdentifier;

    public String fileListFileSha;

    public boolean isLoggedToFile;
    public boolean isTracked;
    public boolean isCompletionProgRun;

    private FreenetPriority priority;

    public String associatedBoardName;
    public String associatedMessageId;
    
    public PerstFrostDownloadItem() {}

    public PerstFrostDownloadItem(final FrostDownloadItem dlItem) {
        fileName = dlItem.getUnprefixedFilename();
        prefix = dlItem.getFilenamePrefix();
        downloadDir = dlItem.getDownloadDir();
        fileSize = dlItem.getFileSize();
        key = dlItem.getKey();
        enabled = (dlItem.isEnabled()==null?true:dlItem.isEnabled().booleanValue());
        state = dlItem.getState();
        downloadAddedTime = dlItem.getDownloadAddedMillis();
        downloadStartedTime = dlItem.getDownloadStartedMillis();
        downloadLastActivityTime = dlItem.getLastActivityMillis();
        downloadFinishedTime = dlItem.getDownloadFinishedMillis();
        retries = dlItem.getRetries();
        lastDownloadStopTime = dlItem.getLastDownloadStopTime();
        gqIdentifier = dlItem.getGqIdentifier();
        fileListFileSha = (dlItem.getFileListFileObject()==null?null:dlItem.getFileListFileObject().getSha());
        isLoggedToFile = dlItem.isLoggedToFile();
        isTracked = dlItem.isTracked();
        isCompletionProgRun = dlItem.isCompletionProgRun();
        associatedBoardName = dlItem.getAssociatedBoardName();
        associatedMessageId = dlItem.getAssociatedMessageId();
        priority = dlItem.getPriority();
    }

    public FrostDownloadItem toFrostDownloadItem(final Logger logger) {

        FrostFileListFileObject sharedFileObject = null;
        if( fileListFileSha != null && fileListFileSha.length() > 0 ) {
            sharedFileObject = FileListStorage.inst().getFileBySha(fileListFileSha);
            if( sharedFileObject == null && key == null ) {
                // no fileobject and no key -> we can't continue to download this file
                logger.warning("Download items file list file object does not exist, and there is no key. " +
                               "Removed from download files: "+fileName);
                return null;
            }
        }

        final FrostDownloadItem dlItem = new FrostDownloadItem(
                fileName,
                prefix,
                downloadDir,
                (fileSize<=0 ? -1 : fileSize),
                key,
                enabled,
                state,
                downloadAddedTime,
                downloadStartedTime,
                downloadLastActivityTime,
                downloadFinishedTime,
                retries,
                lastDownloadStopTime,
                gqIdentifier,
                isLoggedToFile,
                isTracked,
                isCompletionProgRun,
                associatedBoardName,
                associatedMessageId,
                priority
        );

        dlItem.setFileListFileObject(sharedFileObject);

        return dlItem;
    }
}
