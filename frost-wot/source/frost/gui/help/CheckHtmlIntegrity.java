/*
  CheckHtmlIntegrity.java / Frost
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
package frost.gui.help;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.zip.*;

/**
 * Checks all HTML files in a zip file for 'http://', 'https://', 'ftp://' and 'nntp://' links.
 * If those strings are found then the zip file is rejected as unsafe.
 * 
 * @author bback
 */
public class CheckHtmlIntegrity {

    private static final Logger logger = Logger.getLogger(CheckHtmlIntegrity.class.getName());

    /**
     * Tells you whether a zip file contains any unsafe .htm/.html files with external web
     * links or resources, which could leak your IP. You can call this function multiple times
     * on different zip files, to get the answers about all of them.
     * @param {String} fileName - the path to the file you want to check
     * @return - true if safe, false otherwise (or if file doesn't exist/is 0 bytes/couldn't be parsed)
     */
    public boolean scanZipFile(String fileName) {

        File file = new File(fileName);
        
        if( !file.isFile() || file.length() == 0 ) {
            logger.log(Level.SEVERE, "Zip file is invalid or doesn't exist: \""+fileName+"\".");
            return false; // not secure
        }

        final byte[] zipData = new byte[4096];

        try (
            // NOTE: Java 7+ autocloseable takes care of automatically close()'ing these
            // in reverse order after we're done, to free up all memory resources.
            ZipFile zipFile = new ZipFile(file);
        ) {
            final Enumeration<? extends ZipEntry> zipFileEntryEnumeration = zipFile.entries();
            while( zipFileEntryEnumeration.hasMoreElements() ) {
                final ZipEntry zipFileEntry = zipFileEntryEnumeration.nextElement();
                
                // SECURITY NOTE: We must lowercase the filename, so that even ".HTM" and ".HTML" are validated
                final String zipFileEntryName = zipFileEntry.getName().toLowerCase();
                if( zipFileEntryName.endsWith(".html") || zipFileEntryName.endsWith(".htm") ) {
                    
                    try (
                        // NOTE: Java 7+ try-with-resources (autocloseable)
                        InputStream zipFileEntryInputStream = zipFile.getInputStream(zipFileEntry);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream((int)zipFileEntry.getSize());
                    ) {
                        while( true ) {
                            int len = zipFileEntryInputStream.read(zipData);
                            if( len < 0 ) {
                                break;
                            }
                            byteArrayOutputStream.write(zipData, 0, len);
                        }

                        // SECURITY NOTE: Once again we must lowercase the string so they can't get around the validation
                        String htmlStr = new String(byteArrayOutputStream.toByteArray(), "UTF-8").toLowerCase();
                        if( htmlStr.indexOf("http://") > -1 ||
                                htmlStr.indexOf("https://") > -1 ||
                                htmlStr.indexOf("ftp://") > -1 ||
                                htmlStr.indexOf("nntp://") > -1 )
                        {
                            logger.log(Level.SEVERE, "Found an unsafe HTML file in \""+fileName+"\": "+zipFileEntryName);
                            return false; // not secure
                        }
                    }
                }
            }
        } catch( final Exception e ) {
            logger.log(Level.SEVERE, "Exception while reading \""+fileName+"\". File is invalid.", e);
            return false; // not secure
        }

        // all files scanned, no unsafe web links found
        logger.log(Level.WARNING, "No unsafe HTML files found in \""+fileName+"\", everything is ok.");
        return true; // secure
    }
}
