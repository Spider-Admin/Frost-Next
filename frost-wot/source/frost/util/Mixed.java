/*
  Mixed.java / Frost
  Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>

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

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URI;
import java.net.URL;
import java.util.logging.*;

import javax.swing.SwingUtilities;

import org.joda.time.*;

import frost.*;

public final class Mixed {

    private static final Logger logger = Logger.getLogger(Mixed.class.getName());

    private static final char[] invalidChars = { '/', '\\', '?', '*', '<', '>', '\"', ':', '|'};

    /**
     * Compute the absolute file path to the frost.jar file.
     * 
     * @return  A File object for the directory in which the frost.jar file resides,
     *     Returns null in case the correct path couldn't be found.
     */
    public static File getJarDir() {
        try {
            final Class aclass = Core.class; // hardcoded to look for Frost's Core class
            URL url;
            String extURL; // url.toExternalForm();

            // get a url pointing to the class file
            try {
                url = aclass.getProtectionDomain().getCodeSource().getLocation();
                // url is in one of two forms
                //        ./build/classes/   before jar compilation
                //        jardir/JarName.jar  running Frost from a jar
            } catch( final SecurityException ex ) {
                url = aclass.getResource(aclass.getSimpleName() + ".class");
                // url is in one of two forms:
                //          file:/U:/java/Tools/UI/build/classes
                //          jar:file:/U:/java/Tools/UI/dist/UI.jar!
            }

            // convert to external form
            extURL = url.toExternalForm();

            // prune for various cases
            if( extURL.endsWith(".jar") ) { // from getCodeSource
                extURL = extURL.substring(0, extURL.lastIndexOf("/"));
            } else {  // from getResource
                String suffix = "/"+(aclass.getName()).replace(".", "/")+".class";
                extURL = extURL.replace(suffix, "");
                if( extURL.startsWith("jar:") && extURL.endsWith(".jar!") )
                    extURL = extURL.substring(4, extURL.lastIndexOf("/"));
            }

            // convert back to url
            try {
                url = new URL(extURL);
            } catch( MalformedURLException ex ) {
                // leave url unchanged; probably does not happen
            }

            // convert url to File
            File jarDir = null;
            try {
                jarDir = new File(url.toURI());
            } catch( URISyntaxException ex ) {
                jarDir = new File(url.getPath());
            }

            // Frost-specific: check for "frost.jar" to make sure it's found the correct folder
            // NOTE: before compilation, there is no jar and it would point to the Core.class file's folder.
            if( jarDir != null ) {
                final File frostJar = new File(jarDir, "frost.jar");
                if( frostJar.isFile() )
                    return jarDir;
            }
        } catch( final Exception ex ) {} // do not throw exceptions under any circumstances!
        return null;
    }

    /**
     * Takes care of giving the appropriate *absolute* path to a named file. This is necessary
     * in a few cases where people double-click frost.jar, which gives it an incorrect "current
     * working directory" which prevents it from finding important files like help.zip.
     *
     * If the frost.jar cannot be found, or if the desired file cannot be found (if mustExist==true),
     * it returns the input as-is.
     *
     * NOTE: The desiredFile must use FORWARD SLASHES (help/help.zip), since that's the platform
     * independent way of writing paths in Java. If you use a backslash it won't work on Linux/Mac/Unix.
     */
    public static String getFrostDirFile(final String desiredFile, final boolean mustExist) {
        final File frostDir = getJarDir();
        if( frostDir != null ) {
            try {
                final File absolutePath = new File(frostDir, desiredFile);
                if( !mustExist || absolutePath.exists() )
                    return absolutePath.getPath(); // uses system-specific path separator
            } catch( final Exception ex ) {} // ignore errors
        }
        return desiredFile;
    }

    public static void main(final String[] args) {
        System.out.println(createUniqueId());
    }

    /**
     * Creates a new unique ID.
     */
    public static String createUniqueId() {

        final StringBuilder idStrSb = new StringBuilder();
        idStrSb.append(Long.toString(System.currentTimeMillis())); // millis
        idStrSb.append(DateFun.FORMAT_DATE_EXT.print(new DateTime()));
        idStrSb.append(Long.toString(Runtime.getRuntime().freeMemory())); // free java mem
        idStrSb.append(DateFun.FORMAT_TIME_EXT.print(new DateTime()));
        final byte[] idStrPart = idStrSb.toString().getBytes();

        // finally add some random bytes
        final byte[] idRandomPart = new byte[64];
        Core.getCrypto().getSecureRandom().nextBytes(idRandomPart);

        // concat both parts
        final byte[] idBytes = new byte[idStrPart.length + idRandomPart.length];
        System.arraycopy(idStrPart, 0, idBytes, 0, idStrPart.length);
        System.arraycopy(idRandomPart, 0, idBytes, idStrPart.length-1, idRandomPart.length);

        final String uniqueId = Core.getCrypto().computeChecksumSHA256(idBytes);

        return uniqueId;
    }

    /**
     * Compares strings using regular String.compareTo() or String.compareToIgnoreCase(),
     * but supports null values. Nulls are sorted higher (later in the list) than values
     * that actually exist.
     * This function is easier than having to check for nulls everywhere that you want to compare strings.
     */
    public static int compareStringWithNullSupport(final String s1, final String s2, final boolean ignoreCase) {
        // null objects are sorted higher (later/further down) than non-nulls
        int result;
        if( s1 == s2 ) { result = 0; } // compare object reference (memory address)
        else if( s1 == null ) { result = 1; }
        else if( s2 == null ) { result = -1; }
        else { // two non-null objects; compare using string comparator
            result = ( ignoreCase ? s1.compareToIgnoreCase(s2) : s1.compareTo(s2) );
        }
        return result;
    }

    /**
     * Works like Boolean.compareTo(), but without needing to create Boolean objects.
     * If equal, returns 0. If b1 is true, returns 1. If b1 is false, returns -1.
     */
    public static int compareBool(final boolean b1, final boolean b2) {
        return ( b1 == b2 ? 0 : ( b1 ? 1 : -1 ) );
    }

    /**
     * Works like Integer.compareTo(), but without needing to create Integer objects.
     */
    public static int compareInt(final int i1, final int i2) {
        if( i1 < i2 ) {
            return -1;
        } else if( i1 > i2 ) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Works like Long.compareTo(), but without needing to create Long objects.
     */
    public static int compareLong(final long i1, final long i2) {
        if( i1 < i2 ) {
            return -1;
        } else if( i1 > i2 ) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Works like Double.compareTo(), but without needing to create Double objects.
     */
    public static int compareDouble(final double i1, final double i2) {
        if( i1 < i2 ) {
            return -1;
        } else if( i1 > i2 ) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Returns a H:MM:SS string representation of the given milliseconds value.
     * Useful when showing amount of time elapsed between two calls to System.currentTimeMillis().
     * @param {long} millis - the number of milliseconds elapsed
     * @param {optional String} customFormat - if non-null, this is used as the format string; remember to always include three %d statements
     * @return - the string representation 
     */
    public static String convertMillisToHMmSs(final long millis, final String customFormat) {
        long s = (long)(millis / 1000) % 60; // converts millis to seconds, drops the ms decimals, and gets the remainder after removing minutes/hours
        long m = (long)(millis / 60000) % 60; // converts millis to minutes, drops the s decimals, and gets the remainder after removing hours
        long h = (long)(millis / 3600000); // 3.6m = 1 hour in milliseconds; drops the decimals
        return String.format(
                ( customFormat instanceof String ? customFormat : "%d:%02d:%02d" ),
                h, m, s);
    } 
 
    /**
     * Converts the number of bytes to human readable (B, KiB or MiB)
     * @param {long} bytes - the number of bytes
     * @return - the human readable string representation
     */
    public static String convertBytesToHuman(final long bytes) {
        if( bytes < 1024 ) { // less than 1 KiB
            return bytes + " B";
        } else if( bytes < 1048576 ) { // less than 1 MiB
            final double result = (double)bytes / 1024;
            return String.format("%.2f KiB", result);
        } else { // otherwise treat it as MiB
            final double result = (double)bytes / 1048576;
            return String.format("%.2f MiB", result);
        }
    }

    /**
     * Safely URL-decodes strings, by converting all invalid % symbols to literal %25's
     * before trying to decode it. We only decode %-sequences if they're followed by
     * two hex characters. All other %'s are treated as literal. This prevents Java
     * from throwing a runtime exception due to a decoding error. Never use the raw
     * URLDecode function. Always use this wrapper! Furthermore, we do *NOT* decode "+"
     * symbols to spaces, since that'd be "query/form input" decoding. We're doing *URL*
     * decoding, of the path part of the string, NOT the query after the "?" symbol.
     * 
     * This is tuned for safe decoding of Freenet URIs (keys) to real, readable keys.
     *
     * NOTE: Performance is great, since it won't apply any processing if the string doesn't
     * contain any % signs, so never fear using this function liberally whenever needed!
     * @param {String} input - the text to decode; is allowed to be null
     * @return - the decoded text; or the undecoded input if there was a runtime problem (should never happen)
     */
    public static String rawUrlDecode(final String input) {
        // if there are no % symbols in the string, return it as-is
        if( input == null || input.indexOf("%") < 0 ) {
            return input;
        }
        // decode any valid HTML character escape sequences
        String text;
        try {
            // this regexp replaces any % sign that isn't followed by 00-FF hex,
            // with a literal % sign, so that Java won't throw an exception about
            // illegal % characters while parsing the input string.
            text = input.replaceAll("%(?![0-9a-fA-F]{2})", "%25"); // %25 = literal %
            // next, we replace all + signs with literal + signs, so that the URL-decoder
            // converts it back to a real plus (instead of a space). this is necessary
            // because Freenet uses raw URL encoding (only % sequences), NOT form-encoding,
            // and Java's URL decoder expects form-encoding (which only differs in that it
            // treats both %20 and + as space; whereas raw URL encoding only uses %20).
            text = text.replace("+", "%2B"); // %2B = literal +
            text = java.net.URLDecoder.decode(text, "UTF-8");
        } catch( final Exception e ) {
            logger.log(Level.SEVERE, "Failed to rawUrlDecode() URI string", e);
            e.printStackTrace();
            return input;
        }
        return text;
    }

    /**
     * Performs a raw URL encode where special characters are encoded with "%XX", and spaces are
     * encoded as "%20". This is *not* a query-encoding, so you *cannot* use this as the "query/form input"
     * part of a URL (after the ? symbol), since *those* need spaces to be encoded as "+". We don't
     * need "query/form"-encoding/decoding anywhere in Frost, so it's not implemented.
     *
     * NOTE: This should ALWAYS be used before passing URI parameters to the node via FCP socket commands,
     * since the node *strips* illegal characters from the received filename; i.e. if you send it a key
     * with a "?" symbol in it (non-encoded), it will actually strip everything after the ?. That's why
     * it is ultra-important to *always* encode outgoing URI-requests. And yes, this function escapes
     * every important character (such as "?" -> "%3F"), without needlessly converting safe UTF-8 chars,
     * i.e. Japanese ones (which can be sent as either % sequences in a URL, or as actual UTF characters;
     * this function opts for the latter, since that's faster and valid as well).
     *
     * @param {String} input - the tet to encode; is allowed to be null
     * @return - the encoded text; or the unencoded input if there was a runtime problem (should never happen)
     */
    public static String rawUrlEncode(final String input) {
        if( input == null || input.length() == 0 ) {
            return input;
        }
        try {
            // convert the input to URL-encoded fragments (using regular, raw URL "path" encoding
            // with "%20" spaces, NOT form-encoding/query-encoding with "+" spaces).
            final URI uri = new URI(
                    null, // scheme (i.e. "http")
                    null, // host (i.e. "127.0.0.1:8888")
                    input, // path: "CHK@..." (spaces and special chars become raw-urlencoded)
                    null); // fragment
            final String encodedString = uri.getRawPath(); // gets the raw, URL-encoded value
            return encodedString;
        } catch( final Exception e ) {
            // just skip the failed item; this should never even be able to happen,
            // but might possibly happen due to inability to generate URL (URISyntaxException).
            // however, the URI() class is capable of generating path resources for *anything*, and will
            // properly URL-encode *any* special characters, so this *should* never, ever be able to fail.
            logger.log(Level.SEVERE, "Failed to rawUrlEncode() string", e);
            e.printStackTrace();
            return input;
        }
    }

    /**
     * Makes the input safe for use in HTML, by escaping characters with special meaning.
     * NOTE: It doesn't escape single or double quotes, since we don't need that in Frost (see comment below).
     */
    public static String htmlSpecialChars(
            final String aText)
    {
        if( aText == null ) { return null; }

        // simply escape all of the special HTML/XHTML tag/entity characters; note
        // that we must do "&" first to avoid escaping our own escaped characters.
        //
        // NOTE: this is identical to PHP's htmlspecialchars(), but without converting
        // single or double quotes, since we're never using this escape-function within
        // any HTML attribute tags in Frost.
        // '&' becomes '&amp;'
        // '<' becomes '&lt;'
        // '>' becomes '&gt;'
        String escapedText = aText.replace("&", "&amp;");
        escapedText = escapedText.replace("<", "&lt;");
        escapedText = escapedText.replace(">", "&gt;");
        return escapedText;
    }

    /**
     * Converts an input byte array to an uppercase hexadecimal string representation,
     * where each byte consists of two hex characters (00 to FF). Extremely fast.
     *
     * @param {byte[]} bytes - the input byte array
     * @return - a hex representation of the input, or null if the input array was null/empty
     */
    final private static char[] hexArray = "0123456789ABCDEF".toCharArray(); // character indexes are from 0-15 (4 bits)
    public static String bytesToHex(byte[] bytes) {
        if( bytes == null || bytes.length < 1 ) { return null; }
        char[] hexChars = new char[bytes.length * 2]; // we need 2 hex letters per original byte
        for( int i=0; i<bytes.length; ++i ) {
            int v = bytes[i] & 0xFF; // force the value to a single byte (8 bit) integer
            hexChars[i * 2] = hexArray[v >>> 4]; // shift 4 bits to the right, which means we get the value of the upper 4 bits (0-15)
            hexChars[i * 2 + 1] = hexArray[v & 0x0F]; // get the lower 4 bits (0-15)
        }
        return new String(hexChars);
    }

    /**
     * This helper function guarantees that your Runnable is executed in the event dispatch thread.
     * If called from the EDT, it instantly executes the runnable. Otherwise it invokes the runnable
     * via "invokeAndWait" to ensure that it has executed fully before control is returned to the caller.
     *
     * If you need something to happen "right now" on the EDT, then use this instead of code repetition!
     * This function is extremely fast. Creating a new Runnable object is actually "free" in Java,
     * because it's simply a pointer to a static, anonymous subclass of Runnable (since Runnables don't
     * have any internal per-instance data variables, there is no need for instance initialization).
     * They're simply a pointer to the appropriate Runnable class, a pointer back to the "this" that created
     * the anonymous Runnable subclass, and some other glue.
     * And allocating an Object in Java is simply incrementing a pointer in the "available heap".
     * Of course there's some slight garbage collection to deal with, but it's unnoticeable, because
     * short-lived objects sit in the "young generation" stack, which is periodically wiped completely
     * (only the few objects that actually stay alive are moved to the main stack for normal GC).
     * So doing "new Runnable()" is really just pointing Java to the exact same "run()" function code
     * over and over again in memory, and is therefore completely free and can be performed *billions*
     * of times per second (in my tests, 9 billion "new Runnable()" per second is no problem at all),
     * so don't worry about it. Object allocation is much faster in Java than in languages like C++,
     * since Java has a pre-allocated heap whereas C++ needs to actually fetch real memory.
     *
     * This helper was added to Frost since the original legacy code was very brittle and didn't
     * properly invoke all GUI redrawing on the EDT, and this helper simplifies the act of ensuring
     * that all critical GUI code always runs on the EDT, regardless of how the GUI-manipulating
     * functions are called in the rest of Frost's code. -- Kitty.
     *
     *
     * EXCEPTION HANDLING PRE-REQUISITE KNOWLEDGE:
     * Java has two types of exceptions; checked (must be declared in function signature or caught),
     * and unchecked (runtime exceptions such as encountering a NullPointerException).
     * The nice thing about RuntimeException is that they don't have to be caught/handled by the caller.
     * Any code you put in the Runnable must catch any checked exceptions, but is allowed to throw
     * and/or ignore all unchecked exceptions (RuntimeException).
     *
     * EXCEPTION HANDLING IN THIS PARTICULAR FUNCTION:
     * - If we're on the EDT, we invoke instantly via regular Runnable.run(), and thus the
     * RuntimeException will bubble out of the run() invocation and out of this function too,
     * back to the caller of invokeNowInDispatchThread().
     * - If we're not on the EDT, we'll use SwingUtilities.invokeAndWait(), which internally executes
     * Runnable.run() and re-throws any runtime exceptions wrapped as InvocationTargetException.
     * But we unwrap those back to a regular RuntimeException, so that it's as if you hadn't even
     * executed on the EDT at all.
     * - So in short: All runtime exceptions in your Runnable's run() code will be re-thrown by this
     * "invokeNow..." function, thus ensuring that you can wrap your invokeNow in a try-catch
     * if you want to catch any RuntimeExceptions.
     *
     * This function will never throw anything other than RuntimeException, so the proper handler is:
     * try {
     *     Mixed.invokeNowInDispatchThread(etc etc etc);
     * } catch( final RuntimeException ex ) {
     *     // handle exception
     * }
     *
     * However, an even better handler is often to simply write your Runnable.run() code to just deal
     * with the appropriate runtime exceptions in the appropriate code locations. It's up to you.
     *
     *
     * Tip: If you want to return some value from the Runnable, just derive a new custom class from
     * the Runnable class, and give it some public field where you can store a return value. Alternatively,
     * use a separate Object marked "final", which can be accessed both inside and outside the runnable.
     *
     *
     * One more tip: If you want to convert a function that used to refer to "this" in the code, then
     * you can refer to the exact same "this" instance simply by replacing it with "YourClassName.this":
     * - before:
     * public class FooBar {
     *     public void foo() {
     *         this.doSomething();
     *     }
     * }
     * - after:
     * public class FooBar {
     *     public void foo() {
     *         Mixed.invokeNowInDispatchThread(new Runnable() {
     *             public void run() {
     *                 FooBar.this.doSomething();
     *             }
     *         });
     *     }
     * }
     *
     * WARNING: It is EXTREMELY important that you get "this" right if you were using "this" in special
     * ways such as "synchronized(this)", because such uses will compile (but will synchronize on the 
     * Runnable, instead of on the parent object you intended). The compiler will only catch your "this"
     * errors if you try to access fields/methods on the object, or passing "this" into some method that
     * expects an instance of your actual parent object. So be very aware that *some* uses of "this" will
     * need to be manually seen and rewritten before safely wrapping your code in this helper!
     *
     *
     * @param   runnable  the job to execute
     */
    public static void invokeNowInDispatchThread(
            final Runnable runnable)
        throws RuntimeException
    {
        if( SwingUtilities.isEventDispatchThread() ) {
            runnable.run(); // will throw RuntimeException in case of error
        } else {
            try {
                SwingUtilities.invokeAndWait(runnable);
            } catch( final InterruptedException | InvocationTargetException ex ) {
                // InterruptedException means:
                // the GUI/EDT thread is shutting down, which will only happen during application
                // shutdown, so we don't need to re-throw this, but we do it anyway.
                // InvocationTargetException means:
                // some of the code inside the caller's runnable.run() threw a runtime exception, and it has
                // been wrapped in this exception, so re-throw the real RuntimeException to let caller deal with it.
                throw new RuntimeException(ex.getCause()); // any Throwable can be wrapped!
            }
        }
    }

    /**
     * Almost identical to "invokeNowInDispatchThread()", but performs the job *now* if we're the EDT,
     * or *later* if we're not the EDT. IMPORTANT: The exception handling differs (see below).
     *
     * Use this if you don't *know* if you are the EDT or not, *and* you don't *care* about the exact
     * timing of events (meaning you don't mind the fact that this function will queue an EDT job
     * and return instantly if you're *not* on the EDT).
     *
     * IMPORTANT: If you don't care if you're on the EDT or not and you DON'T want it to invoke "now"
     * if we're on the EDT (meaning you ALWAYS want to invoke it later), then you must ALWAYS use
     * the regular SwingUtilities.invokeLater() instead of this wrapper.
     *
     * EXCEPTION HANDLING IN THIS PARTICULAR FUNCTION:
     * - If "invokeNowOrLaterInDispatchThread()" is being called by the EDT, any RuntimeException in
     * Runnable.run() will bubble up to the code in the EDT that called this function.
     * If you want to catch those runtime exceptions, use the same try-catch as for invokeNowInDispatchThread.
     * - If we're not on the EDT, the Runnable will be queued on the EDT via invokeLater, which means
     * that the caller CANNOT see that an exception took place. Any runtime exceptions that happen
     * will instead be handled by the EDT's default exception handler, which simply displays an error
     * in the console log.
     */
    public static void invokeNowOrLaterInDispatchThread(
            final Runnable runnable)
        throws RuntimeException
    {
        if( SwingUtilities.isEventDispatchThread() ) {
            runnable.run(); // will throw RuntimeException in case of error
        } else {
            SwingUtilities.invokeLater(runnable); // will handle any RuntimeException via EDT's default handler
        }
    }

    /**
     * Waits for a specific number of ms
     * @param time Time to wait in ms
     */
    public static void wait(final int time) {
        try {
            Thread.sleep(time);
        } catch (final InterruptedException e) {
        }
    }

    /**
     * Waits for a random number of millis in the range from 0 to maxMillis.
     * @param maxMillis  maximum wait time in ms
     */
    public static void waitRandom(final int maxMillis) {
        Mixed.wait( (int) (Math.random() * maxMillis) );
    }

    /**
     * Replaces characters that are not 0-9, a-z or in 'allowedCharacters'
     * with '_'.
     *
     * NEW: does not allow the '#' char, because that will be used for internal folders
     *      in keypool, e.g. '#unsent#'
     *
     * @param text original String
     * @return modified String
     */
    public static String makeFilename(String text) {
        if (text == null) {
            logger.severe("ERROR: mixed.makeFilename() was called with NULL!");
            return null;
        }

        final StringBuilder newText = new StringBuilder();

        if (text.startsWith(".")) {
            newText.append("_"); // dont allow a boardfilename to start with "." since that hides files on Unix
        }

        for( final char element : invalidChars ) {
            text = text.replace(element, '_');
        }

        newText.append(text);

        return newText.toString();
    }

    /**
     * Filters all non-english characters as well as those filtered by makeFilename
     * @param text the text to be filtered
     * @return the filtered text
     */
    public static String makeASCIIFilename(final String text){

        final StringBuilder newText = new StringBuilder();
        final String allowedCharacters = "()-!.";
        for (int i = 0; i < text.length(); i++)
              {
                   final int value = Character.getNumericValue(text.charAt(i));
                   final char character = text.charAt(i);
                   if ((value >= 0 && value < 36)
                       || allowedCharacters.indexOf(character) != -1) {
                    newText.append(character);
                } else {
                    newText.append("_");
                }
             }
        return makeFilename(newText.toString());  //run through the other filter just in case
    }

    public static boolean binaryCompare(final byte[] src, final int offs, final String searchTxt)
    {
        final int searchLen = searchTxt.length();
        for(int x=0; x < searchLen; x++)
        {
            final byte a = (byte)searchTxt.charAt(x);
            final byte b = src[offs+x];
            if( a != b )
            {
                return false;
            }
        }
        return true;
    }

    /**
     * @return - a string if the clipboard contained a non-empty string, otherwise null
     */
    public static String getClipboardText()
    {
        Transferable transferable = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
        if( transferable == null ) { return null; }

        // try to get data from clipboard
        String clipboardText;
        try {
            if( !transferable.isDataFlavorSupported(DataFlavor.stringFlavor) ) {
                return null;
            }
            clipboardText = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        } catch( final Exception e ) {
            return null;
        }

        if( clipboardText == null || clipboardText.length() == 0 ) {
            return null;
        }

        return clipboardText;
    }

    public static String getOSName()
    {
        // determine what operating system they're using
        String osName = System.getProperty("os.name").toLowerCase();
        if( osName.startsWith("win") ) { osName = "Windows"; }
        else if( osName.indexOf("mac") > -1 ) { osName = "Mac"; }
        else { osName = "Linux"; } // actually Linux, Unix or Solaris, but we only support Linux
        return osName;
    }

    /**
     * A highly optimized algorithm for turning a string into an array of codepoints.
     * Codepoints are necessary when dealing with Unicode strings in Java. All Java strings
     * are UTF-16, but functions like "str.charAt" and "str.length" only count "chars", which
     * in Java is a pair of 2 bytes (up to U+FFFF). If you ever need to directly analyze the
     * contents of a Unicode string, then it's no longer safe to work with that assumption
     * since the Unicode standard has grown so much over the years. The solution is to
     * convert the string to a codepoint array, which represents each character as an integer
     * and therefore supports *all* Unicode characters.
     *
     * NOTE: To get the result back into a string after you're done processing it, you simply
     * have to do the following (arg2 is what array offset to start at (inclusive), and arg3
     * is how many of the elements you want - and you of course want all of them):
     * String str = new String(acp, 0, acp.length);
     *
     * @param {String} str - the string to break down into individual codepoints
     * @return - an array of integers (Unicode codepoints), exactly corresponding to every character.
     */
    public static int[] toCodePointArray(final String str) {
        final char[] ach = str.toCharArray();   // a char array copied from str
        final int len = ach.length;             // the length of ach
        final int[] acp = new int[Character.codePointCount(ach, 0, len)];
        int j = 0;                              // an index for acp

        for( int i=0, cp; i < len; i += Character.charCount(cp) ) {
            cp = Character.codePointAt(ach, i);
            acp[j++] = cp;
        }

        return acp;
    }

    /**
     * Cleans up the given string so that the first encountered alphabetical character
     * is always uppercase, and so that the string ends in a period if no valid
     * "end-of-sentence" punctuation was provided.
     *
     * NOTE: This properly handles right-to-left languages, because *all* text is stored in
     * writing order, so if an Arabic person types some <1><2><3><4> characters, that's how
     * they're stored in memory even if the screen displays them as <4><3><2><1>.
     * So when we append punctuation to the end of the string, it will be correct even for
     * RTL languages (will be drawn on the left). That's because punctuation is considered
     * neutral-direction in Unicode and will inherit the direction of the preceding character,
     * so an RTL language will see the period on the left as intended.
     *
     * @param {String} dirtyStr - your possibly dirty input string
     * @param {boolean} acceptSmileys - if true, we'll accept smileys to end the sentence (such
     * as "Hello! ;)") - otherwise we'll reject smileys and enforce ".!?" as the end-of-sentence.
     * @return - the clean resulting string (same as input, if no change was necessary)
     */
    public static String makeCleanString(final String dirtyStr, final boolean acceptSmileys) {
        // skip processing if there isn't at least 1 character
        if( dirtyStr == null || dirtyStr.length() == 0 ) {
            return dirtyStr;
        }

        // these track the state of the scanning
        boolean caseChanged = false;
        boolean addPeriod = true;

        // first convert the string to an array of codepoints so that we support Unicode above U+FFFF.
        final int[] acp = Mixed.toCodePointArray(dirtyStr);

        // now loop until we find the first alphabetical character and uppercase it if necessary
        int codePoint;
        for( int i=0; i<acp.length; ++i ) {
            codePoint = acp[i];

            // check if the current Unicode character is part of *any* human alphabet
            if( Character.isAlphabetic(codePoint) ) {
                if( ! Character.isUpperCase(codePoint) ) {
                    codePoint = Character.toUpperCase(codePoint);
                    acp[i] = codePoint; // save the uppercased character
                    caseChanged = true; // our input string's case has been changed
                }
                break; // stop scanning after the first alphabetic character
            }
        }

        // now check the final character and make sure that it's one of the punctuations we accept
        //
        // this was based on a small subset of the Regex "\p{Punct}" class: !"#$%&'()*+,-./:;<=>?@[]^_`{|}~
        // here's *OUR* ACCEPTED "end-of-sentence" punctuation:
        //   .!? --> always accepted
        //   ()<>[]{} --> only accepted if they're preceded by ':', ';', ':-', or ';-' (smileys)
        //   CDOPSXcopsx --> same smiley rule as above; and note that ':D' is a smiley but not ':d'
        //   anything else --> we will add a trailing period (which correctly renders either LTR or RTL).
        codePoint = acp[acp.length - 1];
        switch( codePoint ) {
            case '.':
            case '!':
            case '?':
                // accepted!
                addPeriod = false;
                break;
            case '(': // normal smiley mouths
            case ')':
            case '<':
            case '>':
            case '[':
            case ']':
            case '{':
            case '}':
            case 'C': // alternative smiley mouths
            case 'D': // <-- this one is only accepted in uppercase (not in lowercase)
            case 'O':
            case 'P':
            case 'S':
            case 'X':
            case 'c':
            case 'o':
            case 'p':
            case 's':
            case 'x':
                // if the caller doesn't want end-of-sentence smileys then reject these characters
                if( ! acceptSmileys ) {
                    break;
                }

                // caller accepts smileys; but only valid if preceded by smiley eyes and (optional) nose
                if( acp.length >= 2 ) { // length needs to be at least 2 to get ':)'
                    final int left1CodePoint = acp[acp.length - 2];
                    if( left1CodePoint == ':' || left1CodePoint == ';' ) {
                        // found ':)' or ';)' style smiley, accepted!
                        addPeriod = false;
                        break;
                    }
                    else if( left1CodePoint == '-' ) { // potential smiley nose
                        if( acp.length >= 3 ) { // length needs to be at least 3 to get ':-)'
                            final int left2CodePoint = acp[acp.length - 3];
                            if( left2CodePoint == ':' || left2CodePoint == ';' ) {
                                // found ':-)' or ';-)' style smiley, accepted!
                                addPeriod = false;
                                break;
                            }
                        }
                    }
                }
                break;
        }

        // now determine which string to return; the different situations are separated
        // into scenarios to optimize performance by doing the minimum amount of work.

        // [case, !period]
        // scenario 1 (easiest): case has changed, but no period is needed
        // solution: convert the codepoint array back to a string and return it
        if( caseChanged && !addPeriod ) {
            return new String(acp, 0, acp.length);
        }

        // scenario 2&3: a period needs to be added to the original OR modified string
        if( addPeriod ) {
            // pre-allocate space for all chars of original string + 2 extra chars
            final StringBuilder sb = new StringBuilder(dirtyStr.length() + 2);

            // [!case, period]
            // scenario 2: the period needs to be added to the original string
            // solution: just append the original string to the stringbuilder
            if( !caseChanged ) {
                sb.append(dirtyStr);
            }

            // [case, period]
            // (otherwise) scenario 3: the period needs to be added to the case-modified string
            // solution: convert the codepoint array back to a string and append it
            // NOTE: unfortunately, stringbuilders can't directly append a codepoint array
            // without using the intermediary string, but it doesn't waste much memory.
            else {
                sb.append(new String(acp, 0, acp.length));
            }

            // add a trailing period and return the result
            sb.append('.');
            return sb.toString();
        }

        // [!case, !period]
        // scenario 4: if we've come this far, the input passed validation, so return it as-is
        return dirtyStr;
    }
}
