/*
ExecuteDocument.java / Frost
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
package frost.ext;

import java.io.*;

import frost.util.DesktopUtils;
import frost.util.Mixed;

public abstract class ExecuteDocument
{

    /**
     * @param {String} document - the name of the file to open
     */
    public static void openDocument(
            final String document)
        throws IOException
    {
        openDocument(new File(document));
    }

    /**
     * opens a document via its default associated application (if available).
     * has perfect support for every platform that Java 7+ runs on (Windows XP or later, Mac OS,
     * and all versions of Linux). on Mac and Linux it uses their native, OS-specific ways of
     * opening documents with their associated applications. on Windows it uses Java's internal
     * method for finding and launching the associated application.
     * @param {File} document - the file to open
     * @throws IOException
     */
    public static void openDocument(
            final File document)
        throws IOException
    {
        // first do a quick check to make sure the file exists
        if( document == null || !document.exists() ) {
            throw new IOException("File doesn't exist");
        }

        // determine what operating system they're using
        String osName = Mixed.getOSName();

        // mac and linux: we'll prefer opening the document using the OS-specific methods.
        // their respective "open" commands nicely spawn the associated application and instantly
        // quits the launcher, thus freeing up the async thread immediately. and the running app
        // won't connect its pipes to the stdout of Java and spam us like "DesktopUtils.openFile()".
        // it's also a bit more "correct", since these launchers are guaranteed to do the right thing.
        // NOTE: this *does* mean that we don't get any "there is no available associated application"
        // feedback (as openFile() would have given us), but that's worth the peace of mind of not
        // spamming the terminal log. and most people download images/videos/music/text and always
        // have applications for every format they use on Frost. of course, we could use run_wait
        // and check for return code 0, but that would risk waiting forever on a frozen launcher.
        String[] cmd = null;
        if( osName.equals("Mac") ) {
            // Mac: the "open" command is always available
            cmd = new String[] { "open", document.getCanonicalPath() };
        }
        else if( osName.equals("Linux") ) {
            // Linux: there are many ways to open files, so scan the system in order of popularity...
            // first test for "xdg-open", the modern, desktop-agnostic method of opening files on Linux
            // works on pretty much any distribution and desktop environment
            if( which("xdg-open") ) {
                cmd = new String[] { "xdg-open", document.getCanonicalPath() };
            }
            // test for gnome (modern version)
            else if( which("gvfs-open") ) {
                cmd = new String[] { "gvfs-open", document.getCanonicalPath() };
            }
            // test for gnome (older version)
            else if( which("gnome-open") ) {
                cmd = new String[] { "gnome-open", document.getCanonicalPath() };
            }
            // test for xfce
            else if( which("exo-open") ) {
                cmd = new String[] { "exo-open", document.getCanonicalPath() };
            }
            // test for kde
            else if( which("kfmclient") ) {
                cmd = new String[] { "kfmclient", "exec", document.getCanonicalPath() };
            }
            // test for gnustep
            else if( which("gopen") ) {
                cmd = new String[] { "gopen", document.getCanonicalPath() };
            }
        }

        // if there's a Mac/Linux command to execute, then run it asynchronously now
        // NOTE: if we run this synchronously or with a callback, then we could check for
        // a returnCode of "0" which indicates that the program successfully executed, but
        // there are a lot of risks with such an approach, so Mac/Linux users will simply
        // not see any error at all if there's no associated app or if it failed to launch.
        // and there's no point in us checking "res.error" for an exception since that would
        // only tell us if the "open" command itself failed to launch, which it never will.
        if( cmd != null ) {
             Execute.run_async(cmd);
             return;
        }

        // if they've come this far, they're either on Windows, or they're using some very broken
        // version of Linux which doesn't have any of the open-commands above.
        // in either case, we'll now attempt to launch the file using Java's native "open with
        // associated app" support. the reason we didn't do this earlier is that Linux users may
        // frequently be starting Frost via "frost.sh", and the DesktopUtils way of opening files
        // causes the launched application's stdout to hook to Java's stdout, which spams the terminal.
        // but for Windows users, this is the correct method and avoids having to spawn "cmd /k start".
        if( DesktopUtils.canPerformOpen() ) {
            try {
                final boolean openedNatively = DesktopUtils.openFile(document);
                if( openedNatively ) { return; } // success!
            } catch( final Exception e ) {
                // we've already checked that the file *does* exist, that we support the open()
                // command, and that our filename isn't null; so all that remains are the
                // "no associated application", "app failed to launch", and "no access rights",
                // so let's encapsulate those three remaining errors in one generic message:
                throw new IOException("Failed to find an application for this filetype");
            }
        }

        // ...and if we've come this far, there's no way we can open the file. all systems should
        // support the "openFile" method above, so all that remains is to throw an error and abort.
        // this should never, ever happen, since we require Java 7+ and support all of its systems.
        throw new IOException("Opening files isn't supported on your operating system");
    }

    /**
     * [INTERNAL] Helper for checking if a Mac, Unix or Linux terminal program is installed.
     */
    private static boolean which(
            final String appName)
    {
        final ExecResult res = Execute.run_wait(new String[] { "which", appName }, "UTF-8", false);
        return ( res.returnCode == 0 );
    }
}
