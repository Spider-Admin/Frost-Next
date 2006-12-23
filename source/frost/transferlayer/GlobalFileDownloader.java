/*
  GlobalFileDownloader.java / Frost
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
package frost.transferlayer;

import java.io.*;
import java.util.logging.*;

import frost.fcp.*;
import frost.util.*;

public class GlobalFileDownloader {

    private static Logger logger = Logger.getLogger(GlobalFileDownloader.class.getName());

    /**
     * Returns null if file not found.
     * Returns a GlobalFileDownloaderResult if File was downloaded, or if key was invalid. 
     */
    public static GlobalFileDownloaderResult downloadFile(String downKey) {
        
        try {
            File tmpFile = FileAccess.createTempFile("frost_",".tmp");
            tmpFile.deleteOnExit();

            FcpResultGet fcpresults = FcpHandler.inst().getFile(
                    FcpHandler.TYPE_FILE,
                    downKey,
                    null,
                    tmpFile,
                    false); // doRedirect, like in uploadIndexFile()
    
            if( fcpresults == null || !fcpresults.isSuccess() ) {
                // download failed
                tmpFile.delete();
                if( fcpresults != null 
                        && fcpresults.getReturnCode() == 28 
                        && downKey.startsWith("KSK@") ) 
                {
                    return new GlobalFileDownloaderResult(false); // invalid KSK key
                } else {
                    return null; // file not found
                }
            }
            return new GlobalFileDownloaderResult(tmpFile);
            
        } catch (Throwable t) {
            logger.log(Level.SEVERE, "Error in downloadFile", t);
        }
        return null;
    }
}
