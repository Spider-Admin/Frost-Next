/*
 FrostLauncher.java / Frost-Next
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

package frost;

import java.lang.reflect.Method;

import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

/**
 * This is the main entry point of Frost. We use minimal dependencies here,
 * and compile THIS class ONLY for Java 1.6+, so that people on older versions
 * can run the launcher and nicely be told that their Java version is too old.
 */
public class FrostLauncher
{
    public static void main(
            final String[] args)
    {
        System.out.println();
        System.out.println("Frost-Next, Copyright (C) 2015 Frost-Next Project");
        System.out.println("This software comes with ABSOLUTELY NO WARRANTY!");
        System.out.println("This is free software, and you are welcome to");
        System.out.println("redistribute it under the GPL conditions.");
        System.out.println("Frost uses code from bouncycastle.org (BSD license),");
        System.out.println("Kai Toedter (LGPL license), Volker H. Simonis (GPL v2 license)");
        System.out.println("and McObject LLC (GPL v2 license).");
        System.out.println();

        // make sure they've got the required version of Java (1.8+, with JavaFX 2.1+)
        final int versionResult = checkMinimumJavaVersion();
        if( versionResult != CHECK_VERSION_OK ) {
            System.out.println("*** User has an ancient, unsupported version of Java. Exiting...");
            ImageIcon frostIcon = null;
            try {
                frostIcon = new ImageIcon(FrostLauncher.class.getResource("/data/frost.png"));
            } catch( final Exception e ) {}
            JOptionPane.showMessageDialog(
                    null,
                    // if they lack JavaFX, we give some extra info so that they can troubleshoot their system,
                    // otherwise they may think "but I already installed Java 8! this isn't working! bah!".
                    // the thing is that JavaFX is bundled on *every* system; even OpenJDK (the open source Java
                    // alternative) has it, *but* there are a few rare forks of OpenJDK which exclude "OpenJFX".
                    ( versionResult == CHECK_VERSION_NOJAVAFX ? "Detected Java 8+, but you don't have JavaFX 2.1+!\n\n" : "" )
                    + "Frost-Next requires Java 8+ with JavaFX 2.1+ (or newer) for security\n"
                    + "and performance reasons. The minimum Java version was released\n"
                    + "on April 27th of 2012, so you've had plenty of time to upgrade.\n\n"

                    + "Support for Java 6 and 7 was dropped for several major reasons:\n"
                    + "1) Because they are dead products and contain hundreds of extremely\n"
                    + "serious security exploits which can lead to compromising your identity.\n"
                    + "2) Fresh, new Java 8 features helped make Frost-Next even better.\n\n"

                    + "Please download the latest Java \"JRE\" Runtime Environment here:\n"
                    + "http://www.oracle.com/technetwork/java/javase/downloads/\n\n"

                    + "Special Notes:\n"
                    + "- Linux: You can often easily install Java via your system's package\n"
                    + "manager. Please search the internet for how to install Java.\n"
                    + "- Mac: Install the version from Oracle's website.\n"
                    + "- Windows: Install the version from Oracle's website.\n"
                    + "- Windows XP: You may need to get Java 8 in case future versions\n"
                    + "such as Java 9 no longer have support for XP. Better yet, switch OS.\n\n"

                    + "This program will now exit. Please upgrade your Java and try again.",
                    "Ancient Java version detected",
                    JOptionPane.ERROR_MESSAGE,
                    frostIcon);
            System.exit(3);
        }

        // magic: we perform runtime reflection to look up the "Frost" class (in the "frost" package),
        // get its string-array constructor, and call the constructor with the command line args array.
        // this is what allows us to have a Java 1.8+ core started by this 1.6-compatible launcher,
        // without the launcher having any idea about the implementation of the class it's loading.
        try {
            // NOTE: the Frost class takes care of instantiating the rest of Frost and its GUI
            Class.forName("frost.Frost").getConstructor(String[].class).newInstance(new Object[]{args});
        } catch( final Throwable t ) {
            System.out.println("*** Unable to find or launch the core Frost class. Exiting...");
            System.out.println("DEBUG INFORMATION:");
            t.printStackTrace();

            ImageIcon frostIcon = null;
            try {
                frostIcon = new ImageIcon(FrostLauncher.class.getResource("/data/frost.png"));
            } catch( final Exception e ) {}
            JOptionPane.showMessageDialog(
                    null,
                    "Unable to launch the Frost core. Exiting...",
                    "Fatal launcher error",
                    JOptionPane.ERROR_MESSAGE,
                    frostIcon);
            System.exit(3);
        }
    }

    /**
     * Verifies that the user is running Java 1.8+ and JavaFX 2.1+, which is what
     * we need for Frost-Next. This means Java from April 27, 2012 or later.
     */
    private static final int CHECK_VERSION_OK = 0;
    private static final int CHECK_VERSION_TOOLOW = 1;
    private static final int CHECK_VERSION_NOJAVAFX = 2;
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

        // check for JavaFX 2.1 (available as of April 27, 2012),
        // which is required for the file/directory choosers
        try {
            // attempt to look up the javafx version class (this fails on older Java versions without FX)
            final Class<?> jfxClass = Class.forName("com.sun.javafx.runtime.VersionInfo"); // "?" = determine type at runtime

            // now attempt to look up and call the static "getRuntimeVersion" function. we achieve this
            // using runtime reflection to dynamically call the function even though we're compiled on 1.6.
            final Method jfvRvMethod = jfxClass.getMethod("getRuntimeVersion");
            final String javaFXVersionString = (String)jfvRvMethod.invoke(null); // null "this" for static method

            // if we've gotten this far, we can now check the FX version (gives array like [8, 0, 60-b27]).
            // NOTE: "...runtime.VersionInfo" is a preconstructed java class created by Oracle/OpenJFX
            // when they build the JRE, and isn't officially documented, but it's used in all official Oracle
            // code too. it's literally the internal class that sets the "javafx.runtime.version" system
            // property *if* you initialize your application as a JavaFX app (which we DON'T), which is why
            // we must read it manually. all that their function does is return a static, pre-compiled string.
            final String[] javaFXVersion = javaFXVersionString.split("\\.");
            final int majorVersion = Integer.parseInt(javaFXVersion[0]);
            if( majorVersion < 2 ) { // JavaFX 1.#
                return CHECK_VERSION_NOJAVAFX; // they're on JavaFX 1.#
            } else if( majorVersion == 2 ) { // JavaFX 2.#
                if( javaFXVersion.length >= 2 ) { // JavaFX 2.#
                    final int minorVersion = Integer.parseInt(javaFXVersion[1]);
                    if( minorVersion < 1 ) {
                        return CHECK_VERSION_NOJAVAFX; // they're on JavaFX 2.0 or earlier
                    }
                } else {
                    return CHECK_VERSION_NOJAVAFX; // Java FX 2 (no subversion)
                }
            }
        } catch( final Throwable e ) {
            return CHECK_VERSION_NOJAVAFX; // they don't have JavaFX whatsoever
        }

        // they have at least Java 1.8+ and JavaFX 2.1+
        return CHECK_VERSION_OK;
    }
}
