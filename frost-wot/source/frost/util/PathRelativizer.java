/*
 PathRelativizer.java / Frost-Next
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

import java.io.IOError;
import java.lang.IllegalArgumentException;
import java.lang.SecurityException;
import java.lang.System;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Turns absolute paths into relative paths if they're subfolders of Frost's working directory.
 * This class is heavily commented to make it absolutely clear how it works.
 */
public class PathRelativizer
{
    private Path fFrostPath;
    private ConcurrentMap<String,String> fLookupMap;

    public PathRelativizer()
    {
        // get Frost's current working directory to figure out the directory where frost.jar is
        // located. as long as the user started frost via double-clicking the jar or running the
        // bat or sh files, then the working directory will be correct. if they did something
        // weird like manually running "java -jar /path/to/frost.jar" in a terminal, then they'll
        // possibly have a different working directory. but doing so would break a lot more in
        // Frost than just this path parser, so we don't care whatsoever about that scenario,
        // and won't even waste time checking that the working directory contains frost.jar.
        // it always will, if Frost was launched via all of the normal methods.
        // NOTE: this directory is platform-specific, like "C:\frost" or "/home/foo/frost", and
        // it usually doesn't have a trailing slash. but that doesn't matter for our needs since
        // we'll be converting it to a Path object instead.
        // NOTE: the path is always absolute (never "../" or other such path components)
        // NOTE: the working directory is where all relative paths (such as "downloads/") refer.
        final String curDir = System.getProperty("user.dir");

        // attempt to convert the Frost directory to an absolute, "../"-resolved Path object,
        // without resolving symlinks or checking if file exists!
        // NOTE: failure can only happen if the path contains invalid characters; it doesn't verify
        // that the file exists or not. so failure will never happen since we're getting a valid string.
        // NOTE: the conversion to a Path object cleans up the string, so that "/foo///" and "/foo" are equivalent.
        try {
            // all of these operations take place without checking if the file described by the path exists!
            // Paths.get() == converts a string to a Path object without checking if the file exists
            // toAbsolutePath() == convert relative paths like "downloads/" or "../" relative to user.dir
            // normalize() == resolve "." and ".." path components if they have special meaning on the filesystem (unix/mac/linux)
            fFrostPath = Paths.get(curDir).toAbsolutePath().normalize();
        } catch( final InvalidPathException | IOError | SecurityException e ) {
            fFrostPath = null;
        }

        // now just set up the path conversion lookup storage, which avoids repeating the same work.
        // it's a per-session storage, so it will produce correct results even if the user moves Frost later.
        // NOTE: this is thread-safe and allows infinite threads to read and a single thread to write.
        fLookupMap = new ConcurrentHashMap<String,String>(16, 0.75f, /*single writer at a time*/1);
    }

    /**
     * Converts a path to a relative path compared to the current Frost installation directory,
     * and also validates that the path is legal for the current operating system, but doesn't
     * check if the file exists on disk (since you may not have created it yet).
     *
     * The lookup result of each string is cached, so you can (and should) use this function liberally.
     *
     * @param {String} aInputPathStr - the path you want to convert
     * @return - null if the input path is null or illegal for the filesystem (i.e. containing characters
     *     that cannot appear in paths on that system). otherwise it returns a relative path *if* the input
     *     was a sub-path of the Frost directory (such as "/home/foo/frost/downloads/bar" -> "downloads/bar"),
     *     and "downloads/" -> "downloads". otherwise it returns the full input path *if* the input wasn't
     *     a sub-path *or* if it pointed directly at the frost directory instead of a sub-path
     *     (so "/home/foo/frost" -> "/home/foo/frost").
     *     also note that if you input a "." or "../" path on a filesystem (unix/mac/linux) which supports
     *     that, then it will return a path relative to the Frost directory, or a resolved absolute path,
     *     as appropriate.
     *     lastly, note that *you* are responsible for adding a trailing path separator slash to the
     *     return value if you need it. do that via the "FileAccess.appendSeparator()" class in Frost.
     *     in short: it returns a cleaned-up, absolute path if it's outside of the Frost installation
     *     directory or is the Frost directory itself; it returns a cleaned-up, relative path if it's
     *     a sub-path of the Frost directory; otherwise it returns null (in case of empty or illegal input).
     */
    public String relativize(
            final String aInputPathStr)
    {
        // no path provided = return null
        if( aInputPathStr == null ) {
            return null;
        }

        // if we were unable to determine the frost path earlier, just return the input as-is
        // NOTE: this should never, ever happen, since the pathfinding code is platform-independent.
        if( fFrostPath == null ) {
            return aInputPathStr;
        }

        // now check if the input already exists in the lookup map and if so return it instantly
        String outputPath = fLookupMap.get(aInputPathStr);
        if( outputPath != null ) {
            // empty string denotes a "null" value (since ConcurrentHashMap can't store actual nulls)
            return ( outputPath.isEmpty() ? null : outputPath );
        }

        // attempt to convert the input to an absolute, "../"-resolved Path object,
        // without resolving symlinks or checking if file exists!
        // NOTE: if the user gives a relative path such as "downloads/" or "../../", it will become an absolute
        // path relative to "user.dir" (the current working directory). we need to do that in order to compare it.
        Path inputPath;
        try {
            // all of these operations take place without checking if the file described by the path exists!
            // Paths.get() == converts a string to a Path object without checking if the file exists, but validates that
            // the path contains no invalid characters that cannot appear on that filesystem.
            // toAbsolutePath() == convert relative paths like "downloads/" or "../" relative to user.dir
            // normalize() == resolve "." and ".." path components if they have special meaning on the filesystem (unix/mac/linux)
            // note that multiple dot in a row (like ".../" or "..../") are treated as filenames since they have no special meaning
            inputPath = Paths.get(aInputPathStr).toAbsolutePath().normalize();
        } catch( final InvalidPathException | IOError | SecurityException e ) {
            inputPath = null;
        }

        // now determine which final path to return to the caller
        String finalPathStr;
        // illegal path? then we'll give them null back
        if( inputPath == null ) {
            finalPathStr = null;
        }
        // same directory as Frost directory? then give them the absolute path to Frost directory
        // NOTE: this checks path equality without accessing the filesystem, and since we've already
        // resolved "../" components and other relative components, we can be sure that the lookup
        // will be correct. if the filesystem is case-insensitive, then it will furthermore do a
        // case-insensitive comparison. the two paths will be seen as equal if they've got the same
        // root component (ie "/" or "C:\") and the exact same name elements (the rest of the path).
        else if( inputPath.equals(fFrostPath) ) {
            finalPathStr = fFrostPath.toString();
        }
        // now check if the input path is a sub-directory of the Frost directory. we need to do this
        // after the equals check, since "/foo" starts with "/foo". the check is done without seeing
        // if the files exist on disk, and simply checks if the path components of inputPath contains
        // all of the path components of the fFrostPath. so if frost path is "/foo/bar" then "/foo/bar/zoo"
        // starts with the frost path, but "/foo/b" doesn't (since partial name elements aren't equal).
        else if( inputPath.startsWith(fFrostPath) ) {
            try {
                final Path relativePath = fFrostPath.relativize(inputPath);
                finalPathStr = relativePath.toString();
            } catch( final IllegalArgumentException e ) {
                // only happens if inputPath cannot be relativized compared to fFrostPath,
                // but that will never be the case, since we *know* that inputPath starts with
                // the fFrostPath, and can always be relativized.
                finalPathStr = null;
            }
        }
        // alright, this path represents something that isn't a sub-directory of the Frost directory,
        // so we'll simply give them the cleaned-up, absolute path of whatever they gave us!
        else {
            finalPathStr = inputPath.toString();
        }

        // now just ensure that we've got a non-empty path string, otherwise set it to null
        // NOTE: this is just a precaution and should never be able to happen, since the only
        // operation above that can set an empty path is relativize, and only if the two
        // paths are equal (which we already check for above that).
        if( finalPathStr != null && finalPathStr.isEmpty() ) {
            finalPathStr = null;
        }

        // okay, we're done! we now have an absolute or relative, cleaned-up path string.
        // just store it in the lookup map (so this work won't be repeated again this session).
        fLookupMap.put(aInputPathStr, (finalPathStr == null ? "" : finalPathStr)); // empty string serves as "null"

        return finalPathStr;
    }
}
