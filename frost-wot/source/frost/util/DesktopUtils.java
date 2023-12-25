/*
 DesktopUtils.java / Frost-Next
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

package frost.util;

import java.awt.Desktop;
import java.io.File;
import java.net.URI;

public final class DesktopUtils
{
    /**
     * Check if Java can open directories (with the default explorer) and
     * files (with their associated application).
     */
    public static boolean canPerformOpen()
    {
        if( !Desktop.isDesktopSupported() ) { return false; }
        return Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }
    
    /**
     * Check if the user's default web browser can be launched by Java.
     */
    public static boolean canPerformBrowse()
    {
        if( !Desktop.isDesktopSupported() ) { return false; }
        return Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
    }

    /**
     * Attempt to launch the user's default browser and browse to the given URL.
     * WARNING: Always try "canPerformBrowse()" before calling this!
     * @param {URI} uri - the address to browse to
     * @return true if success, false on error (such as failing to launch browser,
     * browser not found, not enough security permissions to launch the process,
     * or the URI may have been malformed)
     */
    public static boolean browse(
            final URI uri)
    {
        try {
            Desktop.getDesktop().browse(uri);
        } catch ( Exception e ) {
            return false;
        }
        return true;
    }

    /**
     * Attempt to open the last valid directory of the given file path, using the
     * user's default file manager.
     * WARNING: Always try "canPerformOpen()" before calling this!
     * WARNING: Relative paths will not be opened if they don't name at least 1 existing directory.
     * @param {File} file - the path to open (can be a subfile of the folder you want to open)
     * @return true if success, false on error (such as an invalid file path, no
     * valid directories in the path, no security permissions to open the directory,
     * or the associated file manager failed to be launched)
     */
    public static boolean openDirectory(
            final File file)
    {
        if( file == null ) { return false; }

        // determine the last valid (existing) directory of the path
        // NOTE:XXX: if the path is relative (like "example.txt") then it has no parent;
        // we could solve that by always using "toAbsoluteFile()" first, but why bother?
        File lastDirectory = file;
        while( !lastDirectory.isDirectory() ) {
            lastDirectory = lastDirectory.getParentFile();
            if( lastDirectory == null ) {
                // no more parents to check...
                break;
            }
        }

        // make sure we've got a valid directory
        if( lastDirectory == null || !lastDirectory.isDirectory() ) {
            return false;
        }

        // now try to open the directory in the default file manager of the platform
        try {
            Desktop.getDesktop().open(lastDirectory);
        } catch ( Exception e ) {
            return false;
        }
        return true;
    }

    /**
     * Attempt to open the given file using its default associated application.
     * WARNING: Always try "canPerformOpen()" before calling this!
     * @param {File} file - the file to open
     * @return true if success, false on error (such as an invalid file path, no
     * security permissions to open the file, the associated application failed
     * to launch, or there is no associated application for that file type)
     */
    public static boolean openFile(
            final File file)
    {
        if( file == null || !file.isFile() ) { return false; }

        // now try to open the file with its default associated application (if any)
        try {
            Desktop.getDesktop().open(file);
        } catch ( Exception e ) {
            return false;
        }
        return true;
    }
}
