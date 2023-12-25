/*
 UpdateMD5Launcher.java / UpdateMD5-Next
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

package updatemd5;

import java.lang.reflect.Method;

/**
 * This is the main entry point of UpdateMD5. We use minimal dependencies here,
 * and compile THIS class ONLY for Java 1.6+, so that people on older versions
 * can run the launcher and nicely be told that their Java version is too old.
 */
public class UpdateMD5Launcher
{
    public static void main(
            final String[] args)
    {
        // make sure they've got the required version of Java (1.8+, with JavaFX 2.1+)
        final int versionResult = checkMinimumJavaVersion();
        if( versionResult != CHECK_VERSION_OK ) {
            System.out.println("Error: UpdateMD5 requires Java 8 (or newer) for security and performance reasons. This program will now exit...");
            System.exit(1);
        }

        // magic: we perform runtime reflection to look up the "UpdateMD5" class (in the "updatemd5" package),
        // get its string-array constructor, and call the constructor with the command line args array.
        // this is what allows us to have a Java 1.8+ core started by this 1.6-compatible launcher,
        // without the launcher having any idea about the implementation of the class it's loading.
        try {
            // NOTE: the UpdateMD5 class constructor takes care of instantiating the rest of UpdateMD5
            Class.forName("updatemd5.UpdateMD5").getConstructor(String[].class).newInstance(new Object[]{args});
        } catch( final Throwable t ) {
            System.out.println("*** Unable to find or launch the core UpdateMD5 class. Exiting...");
            System.out.println("DEBUG INFORMATION:");
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Verifies that the user is running Java 1.8+, which is what we need for UpdateMD5.
     * This means Java from April 27, 2012 or later.
     */
    private static final int CHECK_VERSION_OK = 0;
    private static final int CHECK_VERSION_TOOLOW = 1;
    private static int checkMinimumJavaVersion()
    {
        // look for Java 1.8+ (gives array like [1, 8, 0_60] or [1, 5])
        // NOTE: accepts Java 1.8, 2.0 (future possibility), etc, but nothing lower.
        try {
            final String[] javaVersion = System.getProperty("java.version").split("\\.");
            final int leadVersion = Integer.parseInt(javaVersion[0]);
            if( leadVersion == 1 ) { // Java 1.#
                if( javaVersion.length >= 2 ) { // Java 1.#
                    final int majorVersion = Integer.parseInt(javaVersion[1]);
                    if( majorVersion < 8 ) {
                        return CHECK_VERSION_TOOLOW; // they're on Java 1.7 or earlier
                    }
                } else {
                    return CHECK_VERSION_TOOLOW; // Java 1 (no subversion)
                }
            }
        } catch( final Throwable e ) {
            return CHECK_VERSION_TOOLOW;
        }

        // they have at least Java 1.8+
        return CHECK_VERSION_OK;
    }
}
