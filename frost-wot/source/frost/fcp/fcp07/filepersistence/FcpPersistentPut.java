/*
  FcpPersistentPut.java / Frost
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

import frost.fcp.fcp07.*;
import frost.util.Mixed;

// IMPORTANT NOTE: the FcpPersistentQueue.java implementation ensures that it always grabs and
// re-uses the same put/get request objects and *updates* them when "SimpleProgress" messages
// arrive. that means that the initial "PersistentPut" message will create the object and set its
// compatibility mode/compression settings (which are specified in that initial message). further
// progress updates will just refresh the object's progress but won't touch those variables,
// so they can be safely relied on by *anything* called by FcpPersistentQueue.
public class FcpPersistentPut extends FcpPersistentRequest {

    // common
    private boolean isDirect = false;
    private String filename = null;
    private String uri = null;
    private long fileSize = -1;
    // NOTE: DontCompress=false means upload-compression was ENABLED by the user; this just lets
    // you know what option the user chose, but note that even if compression is enabled Freenet
    // may still choose to upload a file without compression (it does that if the space savings
    // are too small). so *only* use this value for determining the insert settings that were used!
    private boolean isDontCompress = false;
    // NOTE: a string such as "COMPAT_1468", as provided by the node. it will NEVER be
    // "COMPAT_CURRENT", since the node always translates that into the real latest value!
    private String compatibilityMode = null;
    // NOTE: the Freenet source only adds the "SplitfileCryptoKey" field if cryptokey != null during the
    // initial upload specification. so it only exists for uploads that used "OverrideSplitfileCryptoKey".
    //
    // VERY IMPORTANT: Freenet sends *two* "PersistentPut" messages *instantly* for every upload
    // that you *start*, and only the *second* one contains the "SplitfileCryptoKey" key. *Ongoing*
    // uploads will only send a single message and have the key immediately. But either way, Frost
    // will only run the constructor on the first PersistentPut since the 2nd has an identical gqid.
    // For newly started uploads, the key will therefore be retrieved during the setRequest() call
    // instead, which is used for updating existing request objects instances when new PersistentPut
    // messages arrive.
    private String splitfileCryptoKey = null;
    
    // progress
    private int doneBlocks = -1;
    private int totalBlocks = -1;
    private boolean isFinalized = false;

    public FcpPersistentPut(NodeMessage msg, String id) {
        super(msg, id);
        // PersistentPut message
        filename = msg.getStringValue("Filename");
        tryToSetURI(msg);
        fileSize = msg.getLongValue("DataLength");
        isDontCompress = msg.getBoolValue("DontCompress");
        compatibilityMode = msg.getStringValue("CompatibilityMode");
        splitfileCryptoKey = msg.getStringValue("SplitfileCryptoKey"); // null if message didn't contain it
        String isDirectStr = msg.getStringValue("UploadFrom");
        if( isDirectStr.equalsIgnoreCase("disk") ) {
            isDirect = false;
        } else {
            isDirect = true;
        }
        if( filename == null ) {
            filename = getIdentifier();
        }
    }
    
    @Override
    public boolean isPut() {
        return true;
    }

    // grabs the URI from messages that contain it, and only sets the URI if it's a valid string
    private void tryToSetURI(NodeMessage msg) {
        // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
        String newUri = Mixed.rawUrlDecode(msg.getStringValue("URI"));
        if( newUri != null ) {
            int pos = newUri.indexOf("CHK@"); // gets rid of the "freenet:" junk prefix if it exists
            if( pos > -1 ) {
                newUri = newUri.substring(pos).trim();
                if( newUri.length() >= 5 ) { // at least 5 characters ("CHK@#"); ignores the initial "CHK@" unknown key
                    uri = newUri;
                }
            }
        }
    }

    public void onURIGenerated(NodeMessage msg) {
        // URIGenerated msg
        tryToSetURI(msg);
    }

    public void setRequest(NodeMessage msg) {
        // this function is called on the subsequent (2nd and later) PersistentPut messages
        // and retrieves the crypto key. the value will be null if the message didn't contain it,
        // which means that the upload definitely didn't use any crypto key since Freenet always
        // knows the key by the time the 2nd PersistentPut message arrives if a key was set.
        splitfileCryptoKey = msg.getStringValue("SplitfileCryptoKey");
        super.setRequest(msg);
    }
    
    public void setProgress(NodeMessage msg) {
        // SimpleProgress message
        doneBlocks = msg.getIntValue("Succeeded");
        totalBlocks = msg.getIntValue("Total");
        isFinalized = msg.getBoolValue("FinalizedTotal");
        super.setProgress();
    }
    
    public void setSuccess(NodeMessage msg) {
        // PutSuccessful msg
        tryToSetURI(msg);
        super.setSuccess();
    }
    
    @Override
    public void setFailed(NodeMessage msg) {
        super.setFailed(msg);
    }

    public int getDoneBlocks() {
        return doneBlocks;
    }

    public String getFilename() {
        return filename;
    }

    public String getCompatibilityMode() {
        return compatibilityMode;
    }

    public String getSplitfileCryptoKey() {
        return splitfileCryptoKey;
    }

    public long getFileSize() {
        return fileSize;
    }

    public boolean isDirect() {
        return isDirect;
    }

    public boolean isDontCompress() {
        return isDontCompress;
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public String getUri() {
        return uri;
    }
}
