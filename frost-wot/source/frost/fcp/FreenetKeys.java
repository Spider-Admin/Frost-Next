/*
  FreenetKeys.java / Frost
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
package frost.fcp;

public class FreenetKeys {

    // public = others are allowed to read this property, but you MUST *NEVER* modify it!
    public static final String[] FREENETKEYTYPES = new String[]{
        "CHK@", // idx 0
        "KSK@", // idx 1
        "SSK@", // idx 2
        "USK@", // idx 3
    };

    // these correspond to the array indexes above; it's recommended that you save the matching
    // array index when looping above, and compare them against these, instead of doing
    // "startsWith("CHK@")" etc. you'll get more speed with a simple int comparison and it's safer.
    public final static int KEYTYPE_CHK = 0;
    public final static int KEYTYPE_KSK = 1;
    public final static int KEYTYPE_SSK = 2;
    public final static int KEYTYPE_USK = 3;

    // the required lengths of CHKs, USKs and SSKs; as for KSKs they're always valid if they have at least 1 character after the "KSK@".
    protected final static int KEYLEN_07_CHK = 99;
    protected final static int KEYLEN_07_SSK_PUB = 99;
    protected final static int KEYLEN_07_SSK_PRIV = 91;

    /**
     * Checks if the provided String is a freesite key. Meaning: A USK@ or SSK@ link, which follows
     * either the "USK@../name/-0/" or "SSK@../name-0/" patterns (trailing slash optional for both),
     * and optionally ends with a (possibly deep) path to a .htm or .html file. We also support URL
     * fragments, such as "USK@../name/8/index.htm#something", and detect those as site links too.
     * NOTE: There cannot be any leading/trailing garbage (such as whitespace) around the provided key!
     * NOTE: Keys without any slashes whatsoever are not detected as Freesite keys. That's because
     * they *aren't* valid Freesite keys. So this will return false for any malformed Freesite keys.
     * If you want to check general key validity, you should use the KeyParser.parseKeyFromLine()
     * function, which will decode/clean up the key and validate that it has a filename (if required
     * for that keytype) as well as automatically running the "isValidKey" function on it to verify
     * that the crypto key part is valid too.
     * NOTE: This regex is very strict. The USK-matching is absolutely perfect, and the SSK-matching
     * can only give a false positive if a file ends in "-#" (# being a number), which they *never*
     * do, since *all* files have file extensions. We can't improve this any further, since "dash-number"
     * is exactly what a Freesite edition looks like in SSK format. And we can't check the filename
     * before the dash-number, since all filename characters are valid in Freesites. But it doesn't
     * matter! This will *never* give a false positive! Nobody would ever call a file "SSK@../example.jpg-4"!
     * I've literally *never* seen a file without some sort of proper file extension, and *never*
     * expect to see *anything* ending in "dash-number". And remember that SSKs are rare. People
     * usually use CHKs for their files, which of course would never be mistaken for a Freesite.
     */
    public static boolean isFreesiteKey(final String key) {
        if( key == null || key.length() < 5 ) { // at least USK@x
            return false;
        }
        return key.matches("^(?:USK@(?:[^\\/]+\\/){2}-?[0-9]+|SSK@[^\\/]+\\/[^\\/]+?-[0-9]+)(?:\\/?|.+?\\.[hH][tT][mM][lL]?)(?:#.*?)?$");
    }

    /**
     * Checks if provided String is valid. Valid means the String must
     * start with a supported keytype (CHK,...) and must have the
     * correct crypto key length for this freenet version.
     *
     * Note that you do NOT have to strip the slashes/filename parts of the input!
     * We ignore trailing garbage when calculating the crypto key length.
     *
     * CHK keylength on 0.7 is 99 (including the CHK@).
     *
     * SSK/USK keylength on 0.7 is: pubkey:99  privkey:91
     *
     * KSK at least KSK@x : 5
     */
    public static boolean isValidKey(final String key) {

        if( key == null || key.length() < 5 ) { // at least KSK@x
            return false;
        }

        // find the key type
        // NOTE: the key types all have the @ suffix, like "CHK@", "SSK@", etc, which avoids false positives
        int keyType = -1;
        for( int i = 0; i < FREENETKEYTYPES.length; ++i ) {
            if( key.startsWith(FREENETKEYTYPES[i]) ) {
                // keytype found
                if( i == KEYTYPE_KSK ) {
                    return true; // KSK key is ok, length was at least 5
                }
                keyType = i; // keytype is the index in the FREENETKEYTYPES array
                break;
            }
        }
        if( keyType < 0 ) {
            // no valid keytype found
            return false;
        }

        // get real keylength (skip everything after the first slash, if found)
        int length = key.length();
        final int pos = key.indexOf("/");
        if( pos > 0 ) {
            // remove the length of slash and all subsequent characters
            // example: "abc/defg" has a total length of 8 and a slash at offset 3 (zero-indexed)
            // so 8(len)-3(pos) = 5. subtract 5 from 8, and you get 3, which is indeed the total
            // length of characters before the slash.
            length -= (length-pos);
        }

        // check length (not done for KSKs, since those always return true above if they were 5+ chars)
        boolean isKeyLenOk = false;
        if( keyType == KEYTYPE_CHK ) {
            if( length == KEYLEN_07_CHK ) {
                isKeyLenOk = true;
            }
        } else if( keyType == KEYTYPE_SSK || keyType == KEYTYPE_USK ) {
            if( length == KEYLEN_07_SSK_PRIV || length == KEYLEN_07_SSK_PUB ) {
                isKeyLenOk = true;
            }
        }
        return isKeyLenOk;
    }
}
