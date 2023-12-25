/*
  FcpPersistentGet.java / Frost
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

public class FcpPersistentGet extends FcpPersistentRequest {

    // common
    private boolean isDirect = false;
    private String filename = null;
    private String uri = null;

    // hash-based anti-dupe system
    private String hashMD5 = null;
    private boolean hashChecked = true; // no point trying to check the initial null-hash

    // progress
    private int doneBlocks = -1;
    private int requiredBlocks = -1;
    private int totalBlocks = -1;
    private boolean isFinalized = false;

    // success
    private long filesize = -1;

    // failed
    private String redirectURI = null;

    public FcpPersistentGet(NodeMessage msg, String id) {
        super(msg, id);
        // PersistentGet message
        filename = msg.getStringValue("Filename");
        // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
        uri = Mixed.rawUrlDecode(msg.getStringValue("URI"));
        String isDirectStr = msg.getStringValue("ReturnType");
        if( isDirectStr.equalsIgnoreCase("disk") ) {
            isDirect = false;
        } else {
            isDirect = true;
        }
    }
    
    @Override
    public boolean isPut() {
        return false;
    }

    public void onExpectedHashes(NodeMessage msg) {
        // ExpectedHashes message
        // we use the MD5 to avoid downloading what the user already has.
        // note that the MD5 is only included on Freenet for files >=1 MiB,
        // and that were inserted with COMPAT_1255 or later.
        boolean foundNewMD5 = false;
        String newMD5 = msg.getStringValue("Hashes.MD5");
        if( newMD5 != null ) {
            newMD5 = newMD5.trim();
            if( newMD5.length() == 32 ) { // valid md5 must be 32 hex chars
                // a single request can get its "ExpectedHashes" message multiple times, so only
                // trigger a new check if the MD5 differs from the last-seen for this request.
                if( hashMD5 == null || !hashMD5.equals(newMD5) ) {
                    // the hash will be seen by PersistenceManager:applyState(),
                    // which is called after this FCP message is fully processed.
                    hashMD5 = newMD5;
                    hashChecked = false; // the new hash needs to be checked
                    foundNewMD5 = true;
                }
            }
        }
        if( !foundNewMD5 ) {
            // since this item lacked an MD5 hash, let's instantly mark it as
            // "checked" so that the applyState() loop won't constantly look
            // for an MD5 string which never comes.
            hashChecked = true;
        }
    }

    /**
     * Whether this persistent request's hash has been checked against the blocklist.
     */
    public boolean isHashChecked() {
        return hashChecked;
    }
    public void setHashChecked(final boolean val) {
        hashChecked = val;
    }
    
    public void setProgress(NodeMessage msg) {
        // SimpleProgress message
        doneBlocks = msg.getIntValue("Succeeded");
        requiredBlocks = msg.getIntValue("Required");
        totalBlocks = msg.getIntValue("Total");
        isFinalized = msg.getBoolValue("FinalizedTotal");
        super.setProgress();
    }
    
    public void setSuccess(NodeMessage msg) {
        // DataFound msg
        filesize = msg.getLongValue("DataLength");
        super.setSuccess();
    }
    
    @Override
    public void setFailed(NodeMessage msg) {
        super.setFailed(msg);
        // NOTE:IMPORTANT: INCOMING FREENET URIS FROM THE NODE ARE ALWAYS URLENCODED, SO WE DECODE THEM!
        redirectURI = Mixed.rawUrlDecode(msg.getStringValue("RedirectURI"));
    }

//    public void updateFrostDownloadItem(FrostDownloadItem item) {
//    }
//
    /**
     * @return  null if no MD5 is known for the file, otherwise a 32-character hex string
     */
    public String getMD5() {
        return hashMD5;
    }

    public int getDoneBlocks() {
        return doneBlocks;
    }

    public String getFilename() {
        return filename;
    }

    public long getFilesize() {
        return filesize;
    }

    public boolean isDirect() {
        return isDirect;
    }

    public boolean isFinalized() {
        return isFinalized;
    }

    public int getRequiredBlocks() {
        return requiredBlocks;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public String getUri() {
        return uri;
    }
    
    public String getRedirectURI() {
        return redirectURI;
    }
}
