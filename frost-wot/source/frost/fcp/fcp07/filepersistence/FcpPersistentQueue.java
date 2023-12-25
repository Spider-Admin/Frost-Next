/*
  FcpPersistentQueue.java / Frost
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
package frost.fcp.fcp07.filepersistence;

import java.util.*;

import frost.Core;
import frost.fcp.fcp07.*;
import frost.fileTransfer.FreenetPriority;
import frost.util.*;

public class FcpPersistentQueue implements NodeMessageListener {

//    private static final Logger logger = Logger.getLogger(FcpPersistentQueue.class.getName());

    final private FcpMultiRequestConnectionFileTransferTools fcpTools;
    final private IFcpPersistentRequestsHandler persistenceHandler;

    // we hold all requests, gui shows only the wanted requests (all or own)
    final private HashMap<String,FcpPersistentPut> uploadRequests = new HashMap<String,FcpPersistentPut>();
    final private HashMap<String,FcpPersistentGet> downloadRequests = new HashMap<String,FcpPersistentGet>();

    // keeps track of all seen compatibility modes (from the CompatibilityMode messages sent when downloading files)
    // this speeds up processing since we don't have to re-check ones we've already seen during this session.
    // NOTE: we pre-allocate room for 10 unique values; will grow dynamically if more is needed.
    final private HashSet<String> seenCompatibilityModes = new HashSet<String>(10);

    public FcpPersistentQueue(final FcpMultiRequestConnectionFileTransferTools tools, final IFcpPersistentRequestsHandler pman) {
        fcpTools = tools;
        persistenceHandler = pman;
    }

    public void startThreads() {
        fcpTools.getFcpPersistentConnection().addNodeMessageListener(this);
        fcpTools.watchGlobal(true);
        fcpTools.listPersistentRequests();
    }

    public Map<String,FcpPersistentPut> getUploadRequests() {
        return getUploadRequestsCopy();
    }
    public Map<String,FcpPersistentGet> getDownloadRequests() {
        return getDownloadRequestsCopy();
    }

    public boolean isIdInGlobalQueue(final String id) {
        if( downloadRequests.containsKey(id) ) {
            return true;
        }
        if( uploadRequests.containsKey(id) ) {
            return true;
        }
        return false;
    }


    @SuppressWarnings("unchecked")
    public synchronized Map<String,FcpPersistentPut> getUploadRequestsCopy() {
        return (Map<String,FcpPersistentPut>)uploadRequests.clone();
    }
    @SuppressWarnings("unchecked")
    public synchronized Map<String,FcpPersistentGet> getDownloadRequestsCopy() {
        return (Map<String,FcpPersistentGet>)downloadRequests.clone();
    }

    public void connected() {
        persistenceHandler.connected();
        // we are reconnected
        fcpTools.watchGlobal(true);
        fcpTools.listPersistentRequests();
    }

    public void disconnected() {
        persistenceHandler.disconnected();

        uploadRequests.clear();
        downloadRequests.clear();
    }

    public void handleNodeMessage(final NodeMessage nm) {
        // handle a NodeMessage without identifier
    }

    public void handleNodeMessage(final String id, final NodeMessage nm) {

        if(Logging.inst().doLogFcp2Messages()) {
            System.out.println(">>>RCV>>>>");
            System.out.println("MSG="+nm);
            System.out.println("<<<<<<<<<<");
        }

        if( nm.isMessageName("PersistentGet") ) {
            onPersistentGet(id, nm);
        } else if( nm.isMessageName("DataFound") ) {
            onDataFound(id, nm);
        } else if( nm.isMessageName("GetFailed") ) {
            onGetFailed(id, nm);
        } else if( nm.isMessageName("PersistentPut") ) {
            onPersistentPut(id, nm);
        } else if( nm.isMessageName("PutSuccessful") ) {
            onPutSuccessful(id, nm);
        } else if( nm.isMessageName("PutFailed") ) {
            onPutFailed(id, nm);
        } else if( nm.isMessageName("SimpleProgress") ) {
            onSimpleProgress(id, nm);
        } else if( nm.isMessageName("PersistentRequestRemoved") ) {
            onPersistentRequestRemoved(id, nm);
        } else if( nm.isMessageName("PersistentRequestModified") ) {
            onPersistentRequestModified(id, nm);
        } else if( nm.isMessageName("IdentifierCollision") ) {
            onIdentifierCollision(id, nm);
        } else if( nm.isMessageName("ProtocolError") ) {
            onProtocolError(id, nm);
        } else if( nm.isMessageName("CompatibilityMode") ) {
            onCompatibilityMode(id, nm);
        } else if( nm.isMessageName("URIGenerated") ) {
            // indicates the final URI of the data you inserted; is sent before the upload is complete
            onURIGenerated(id, nm);
        } else if( nm.isMessageName("ExpectedDataLength") ) {
            // ignore; this newish message is just the node pointlessly guessing
            // about the final filesize of a download before completion
        } else if( nm.isMessageName("ExpectedHashes") ) {
            onExpectedHashes(id, nm);
        } else if( nm.isMessageName("ExpectedMIME") ) {
            // ignore; we don't care what filetype Freenet guesses for downloads
        } else if( nm.isMessageName("SendingToNetwork") ) {
            // ignore; we don't care if the node lets us know that it has to route a request
            // out to the network instead of fulfilling it from our datastore
        } else if( nm.isMessageName("EnterFiniteCooldown") ) {
            // ignore; means that Freenet has requested all blocks of a download 3 times
            // and that the remaining blocks are waiting for 30 minutes before they
            // will be tried again. we don't care!
        } else if( nm.isMessageName("StartedCompression") ) {
            // ignore; the node is telling us that it's started compressing a compressed upload. so what?!
        } else if( nm.isMessageName("FinishedCompression") ) {
            // ignore; the node is telling us that it's finished compressing a compressed upload. so what?!
        } else if( nm.isMessageName("PutFetchable") ) {
            // ignore; means that enough data has been inserted into Freenet that the key should
            // now be fetchable, but the node never seems to send this message even though our
            // Verbosity level asks for it, so let's just ignore it in case they ever decide to
            // send it properly, so that it doesn't show up in logs. it'd be a useless message
            // anyway, since we don't care if it's fetchable. we always do the complete insert.
            // UPDATE: It probably didn't show up since Frost's WatchGlobal VerbosityMask was wrong,
            // and that's been corrected now, but we still don't want this message anyway.
        } else {
            // unhandled msg
            System.out.println("### INFO - Unhandled msg: "+nm);
        }
    }

    protected void onCompatibilityMode(final String id, final NodeMessage nm) {
        // the "CompatibilityMode" message is only sent for downloads, and it can be sent
        // multiple times with various early guesses, until the final msg with Definitive=true,
        // so we wait until the node has decided what insertion modes the file used!
        // NOTE: for Uploads, we handle their compatibility mode in PersistenceManager.java
        // instead, since we also update the GUI in their case.
        final String isDefinitive = nm.getStringValue("Definitive");
        if( isDefinitive != null && isDefinitive.equals("true") ) {
            // grab the Min/Max compatibility mode guesses. if we're seeing the modes for the
            // first time this session, then trigger the auto-learning. this will dynamically
            // train and expand the compatibility mode manager with new, previously unseen
            // compatibility modes over time, without having to recompile Frost to add them
            // manually. pure magic! ;-)
            final String compatMin = nm.getStringValue("Min");
            final String compatMax = nm.getStringValue("Max");
            if( compatMin != null && !seenCompatibilityModes.contains(compatMin) ) {
                seenCompatibilityModes.add(compatMin);
                Core.getCompatManager().learnNewUserMode(compatMin);
            }
            if( compatMax != null && !seenCompatibilityModes.contains(compatMax) ) {
                seenCompatibilityModes.add(compatMax);
                Core.getCompatManager().learnNewUserMode(compatMax);
            }
        }
    }

    protected void onExpectedHashes(final String id, final NodeMessage nm) {
        // we only care about the hashes for DOWNLOADS, where it's used
        // to avoid downloading what the user already has on their disk.
        if( downloadRequests.containsKey(id) ) {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.onExpectedHashes(nm);
            persistenceHandler.persistentRequestUpdated(pg); // forces GUI to see the MD5 (and possibly abort the transfer)
        }
    }

    protected void onPersistentGet(final String id, final NodeMessage nm) {
        if( downloadRequests.containsKey(id) ) {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.setRequest(nm);
            persistenceHandler.persistentRequestUpdated(pg);
            return;
        } else {
            final FcpPersistentGet fpg = new FcpPersistentGet(nm, id);
            downloadRequests.put(id, fpg);
            persistenceHandler.persistentRequestAdded(fpg);
        }
    }
    protected void onDataFound(final String id, final NodeMessage nm) {
        if( !downloadRequests.containsKey(id) ) {
            System.out.println("No item in download queue: "+nm);
        } else {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.setSuccess(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        }
    }
    protected void onGetFailed(final String id, final NodeMessage nm) {
        if( !downloadRequests.containsKey(id) ) {
            System.out.println("No item in download queue: "+nm);
        } else {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.setFailed(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        }
    }
    protected void onPersistentPut(final String id, final NodeMessage nm) {
        if( uploadRequests.containsKey(id) ) {
            final FcpPersistentPut pg = uploadRequests.get(id);
            pg.setRequest(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        } else {
            final FcpPersistentPut fpg = new FcpPersistentPut(nm, id);
            uploadRequests.put(id, fpg);
            persistenceHandler.persistentRequestAdded(fpg);
        }
    }
    protected void onURIGenerated(final String id, final NodeMessage nm) {
        if( !uploadRequests.containsKey(id) ) {
            System.out.println("No item in upload queue: "+nm);
            return;
        } else {
            final FcpPersistentPut pg = uploadRequests.get(id);
            pg.onURIGenerated(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        }
    }
    protected void onPutSuccessful(final String id, final NodeMessage nm) {
        if( !uploadRequests.containsKey(id) ) {
            System.out.println("No item in upload queue: "+nm);
            return;
        } else {
            final FcpPersistentPut pg = uploadRequests.get(id);
            pg.setSuccess(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        }
    }
    protected void onPutFailed(final String id, final NodeMessage nm) {
        if( !uploadRequests.containsKey(id) ) {
            System.out.println("No item in upload queue: "+nm);
            return;
        } else {
            final FcpPersistentPut pp = uploadRequests.get(id);
            pp.setFailed(nm);
            persistenceHandler.persistentRequestUpdated(pp);
        }
    }
    protected void onSimpleProgress(final String id, final NodeMessage nm) {
        if( downloadRequests.containsKey(id) ) {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.setProgress(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        } else if( uploadRequests.containsKey(id) ) {
            final FcpPersistentPut pg = uploadRequests.get(id);
            pg.setProgress(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        } else {
            System.out.println("No item in queue: "+nm);
            return;
        }
    }
    protected void onPersistentRequestRemoved(final String id, final NodeMessage nm) {
        if( downloadRequests.containsKey(id) ) {
            final FcpPersistentGet pg = downloadRequests.remove(id);
            persistenceHandler.persistentRequestRemoved(pg);
        } else if( uploadRequests.containsKey(id) ) {
            final FcpPersistentPut pg = uploadRequests.remove(id);
            persistenceHandler.persistentRequestRemoved(pg);
        } else {
            System.out.println("No item in queue: "+nm);
            return;
        }
    }
    protected void onPersistentRequestModified(final String id, final NodeMessage nm) {
        // check if the priorityClass changed, ignore other changes
        if( nm.isValueSet("PriorityClass") ) {
            final FreenetPriority newPriorityClass = FreenetPriority.getPriority(nm.getIntValue("PriorityClass"));
            if( downloadRequests.containsKey(id) ) {
                final FcpPersistentGet pg = downloadRequests.get(id);
                pg.setPriority(newPriorityClass);
                persistenceHandler.persistentRequestModified(pg);
            } else if( uploadRequests.containsKey(id) ) {
                final FcpPersistentPut pg = uploadRequests.get(id);
                pg.setPriority(newPriorityClass);
                persistenceHandler.persistentRequestModified(pg);
            } else {
                System.out.println("No item in queue: "+nm);
                return;
            }
        }
    }
    protected void onProtocolError(final String id, final NodeMessage nm) {
        if( downloadRequests.containsKey(id) ) {
            final FcpPersistentGet pg = downloadRequests.get(id);
            pg.setFailed(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        } else if( uploadRequests.containsKey(id) ) {
            final FcpPersistentPut pg = uploadRequests.get(id);
            pg.setFailed(nm);
            persistenceHandler.persistentRequestUpdated(pg);
        } else {
            System.out.println("No item in queue, calling error handler: "+nm);
            persistenceHandler.persistentRequestError(id, nm);
        }
    }
    protected void onIdentifierCollision(final String id, final NodeMessage nm) {
        // since we use the same unique gqid, most likly this request already runs!
        System.out.println("### ATTENTION ###: "+nm);
    }
}
