/*
 KeyParser.java / Frost-Next
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

package frost.fileTransfer;

import java.util.ArrayList;

import frost.fcp.FreenetKeys;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.util.Mixed;

public final class KeyParser
{
    /**
     * You should create an instance of this result class if you want to use the single-line parser.
     * Usage: "final KeyParser.ParseResult result = new KeyParser.ParseResult();"
     */
    public static class ParseResult {
        public String key;
        public String fileName;
    }

    /**
     * Parses a single line and extracts the first valid key on the line.
     * NOTE: We use Freenet's rules for rejecting/accepting keys and generating filenames. Read the
     *   code below to see the rules; but basically: KSK@ uses the name after the @ symbol. Nameless
     *   CHK@ keys fall back to "<first part of key before comma>.bin". Nameless USK and SSK are rejected.
     *   All other situations (USK, SSK and CHK) extract the filename after the last slash.
     * NOTE: any encoded keys in the input are always decoded, i.e. "%20" -> " ", which means that this
     * function can also be used to validate and clean up possibly-dirty/encoded keys.
     * @param {String} line - the line to parse; must be a single line
     * @param {boolean} includeSites - true if you want to include freesite keys, otherwise false
     * to strip all detected freesite keys except ones pointing to non-HTM/HTML resources; i.e.
     * "USK@../style.css" would still pass the validation.
     * NOTE: Freesite links always have a blank (empty, non-null string) filename, even if they
     * point to an HTML file. That's on purpose since the Freesite link parsing isn't meant to
     * be used for getting regular filenames. It's meant for things like opening site keys.
     * NOTE: You NEVER want to include sites if you're only parsing for actual downloadable keys!
     * @param {ParseResult} result - the ParseResult object where the parsing result will be placed;
     * to see if there was a key on the line, check if "result.key != null" and if so, you know that
     * the .key and .fileName properties are filled with the key extracted from the current line.
     * NOTE: You should re-use the same ParseResult object between multiple calls to this function,
     * to be economical with your memory usage.
     * @return - nothing; the result is placed in the ParseResult object
     */
    public static void parseKeyFromLine(
            final String line,
            final boolean includeSites,
            final ParseResult result)
    {
        if( result == null ) { return; }

        // clear the result object
        result.key = null;
        result.fileName = null;

        // make sure the user provided a line
        if( line == null ) { return; }

        // remove leading and trailing whitespace and skip line if it's too short to contain a key
        String key = line.trim();
        if( key.length() < 5 ) {
            return;
        }

        // if a Freenet link exists within a href="" HTML tag, then delete everything else on the line.
        // this *prevents* treating the non-tag content as a Freenet link (avoids seeing it as CHK@../file.jpg">click</a>)
        // and yes, this only grabs the *first* key on the line even if there are multiple keys.
        // that's intentional. Frost is only meant to receive one key per line.
        // this tag situation only happens when people paste HTML content into Frost via "paste keys from clipboard".
        // NOTE: we support both 'href="/CHK...' (prefix) and 'href="CHK..." formats. and we support
        // both "CHK@" and "CHK%40" (fully URL-encoded) formats. and since we only accept keys within href="" if
        // we see the @ or %40, we don't get false positives from 'href="about_CHK.htm"' or similar.
        key = key.replaceFirst("^.*?href=\"([^\"]*(?:CHK|USK|SSK|KSK)(?:@|%40)[^\"]*)\".*$", "$1");

        // convert any html escape sequences (e.g. "%2c" -> "," and "%40" -> "@" ), to get the real key
        key = Mixed.rawUrlDecode(key);

        // find key type (chk,ssk,...) if there is a key on this line
        // NOTE: the key types all have the @ suffix, like "CHK@", "SSK@", etc, which avoids false positives
        int keyType = -1;
        for( int i = 0; i < FreenetKeys.FREENETKEYTYPES.length; ++i ) {
            final int pos = key.indexOf(FreenetKeys.FREENETKEYTYPES[i]);
            if( pos >= 0 ) {
                // keytype found
                keyType = i; // keytype is the index in the FREENETKEYTYPES array

                // if key was not at pos 0, strip everything before the key type
                if( pos > 0 ) {
                    key = key.substring(pos);
                }

                // no need to scan for any other types
                break;
            }
        }
        if( keyType < 0 ) {
            // no valid keytype found
            return;
        }

        // trim the cleaned-up key and verify the minimum length
        key = key.trim();
        if( key.length() < 5 ) {
            // at least 5 characters is needed (KSK@x is the shortest possible key)
            return;
        }

        // if the caller has disabled Freesite inclusion, then avoid all USK/SSK Freesite links
        // that point to either no filename at all, or to a .htm/.html file, since people almost
        // never want to download those. they're very common in people's signatures, so it's best
        // that we avoid them to avoid garbage links when using "download all keys from message".
        // it still allows uniquely named files like "USK@site/#/file.csv" to be downloaded.
        // NOTE: this returns false for malformed Freesite keys (such as just a key with no slashes at all).
        // but such nameless USKs and SSKs are rejected further down instead (since those keytypes always need names).
        final boolean isFreesite = FreenetKeys.isFreesiteKey(key);
        if( !includeSites && isFreesite ) {
            return;
        }

        // extract the filename from the key, using the same method as Freenet
        String fileName = null;
        if( isFreesite ) {
            // Freesite links: (either no filename, or a htm/html file); set the filename to blank.
            // NOTE/FIXME: SSK@../foo-1 and SSK@../foo-1/ are seen as different keys; but that'd only
            // affect our ability to de-duplicate site keys AND would require someone to write two
            // identical keys but have a trailing slash on one and not the other. it's not worth fixing,
            // since it'd be complex (checking if it ends in .htm/html filename or if it needs a slash).
            // more importantly: the only time we parse for site keys is during "open selected key in
            // browser" (the clicked key), so it doesn't matter at all. for "open all images" we use
            // different logic. for key converter we only parse files. and no other parts of Frost-Next
            // parses Freesite links, so it doesn't matter that the filename is blank and that keys
            // may not be fully de-duplicated. this should only be improved if the Freesite parsing
            // feature is going to be extended in the future. in that case, derive a logical filename
            // such as "sitename-edition#", and normalize the key to end in a slash if missing.
            fileName = "";
        } else if( keyType == FreenetKeys.KEYTYPE_KSK ) {
            // KSK only: use everything after "KSK@" as the filename, since that's its identifier
            fileName = key.substring(4);
        } else {
            // CHK, USK and SSK (only non-Freesite links for the latter two): use the part after the last "/" as the filename
            final int lastSlashPos = key.lastIndexOf('/');
            if( lastSlashPos >= 0 ) {
                // NOTE: we don't have to trim() the result, since trailing whitespace has already been trimmed
                fileName = key.substring(lastSlashPos + 1);
            } else {
                // there's no slash, so do what Freenet does...
                // - USK: refuses keys without trailing slash ("No docname for USK") or with an
                //   empty name after the slash ("No suggested edition number for USK")
                // - SSK: refuses keys without trailing slash ("No document name") or with an empty
                //   name after the slash ("Invalid filename")
                // - CHK: automatically adds a trailing slash if none exists; and if there's no
                //   name after the slash, it falls back to "<the first part of the key>.bin".
                //   example: "CHK@aaa,bbb,AAMC--8" and "CHK@aaa,bbb,AAMC--8/" would both use
                //   "aaa.bin" as the filename.

                if( keyType == FreenetKeys.KEYTYPE_CHK ) {
                    // CHK: append a trailing slash, to make the key "valid" (but not really, since
                    // keys rarely ever have a "<key>.bin" filename within them)
                    // NOTE: we don't add the "<key>.bin" filename part to the key itself; we'll let
                    // Freenet deal with that.
                    key = key + "/";
                } else {
                    // USK and SSK: reject the key since non-Freesite keys are totally invalid
                    // without a filename, and therefore must contain at least one slash (which it didn't)
                    return;
                }
            }
        }
        if( !isFreesite && (fileName == null || fileName.isEmpty()) ) {
            // this is a non-Freesite link and no filename was found above; this will only happen
            // for CHK, USK and SSK (and in the latter two cases, only if they had a trailing slash
            // to begin with). we'll handle it as described above in the "do what Freenet does" section.

            if( keyType == FreenetKeys.KEYTYPE_CHK ) {
                // CHK: extract the first part of the crypto key and add ".bin", as described above
                final int firstCommaPos = key.indexOf(',');
                if( firstCommaPos > 4 ) { // make sure comma isn't the first char after "CHK@"
                    fileName = key.substring(4, firstCommaPos) + ".bin"; // end index is exclusive, so avoids the comma
                } else {
                    // didn't even find a comma for this CHK... reject the key
                    return;
                }
            } else {
                // USK and SSK: (won't happen for KSK since those have already been verified to have a name)
                // reject the key since they're invalid without a filename since this isn't a Freesite link.
                return;
            }
        }

        // alright; if we've come this far, we know that we have a filename and won't have to verify it anymore

        // EXTRA NOTE: [this is a legacy Frost node describing the download strategy that will be used for CHKs if the file fails:]
        //         On Freenet 0.7 we remember the full provided download uri as key.
        //         If the node reports download failed, error code 11 later,
        //         then we strip the filename
        //         from the uri and keep trying with chk only

        // now just check if the key is valid for this network
        // NOTE: this function only validates the crypto key length,
        // and properly ignores everything after the slash
        if( !FreenetKeys.isValidKey(key) ) {
            return;
        }

        // everything is valid, so now fill the result object
        result.key = key;
        result.fileName = fileName;
    }

    /**
     * @param {String} text - the text to parse for keys (one key will be extracted per line)
     * @param {boolean} includeSites - see parseKeyFromLine()
     * @return - a list of constructed FrostDownloadItem objects representing all keys; so only
     * use this function if that's what you want! for all other needs you should manually do similar
     * work to what we're doing below (parsing one line/key at a time into a ParseResult object).
     */
    public static ArrayList<FrostDownloadItem> parseKeys(
            final String text,
            final boolean includeSites)
    {
        ArrayList<FrostDownloadItem> frostDownloadItemList = new ArrayList<FrostDownloadItem>();

        // split the input text by newlines
        if( text == null ) { return frostDownloadItemList; }
        final String[] inputLines = text.split("\n");
        if( inputLines == null || inputLines.length == 0 ) {
            // this will never happen. even a single empty line (text length of 0) would produce a single,
            // empty array element. but let's just abort in case it happens in an alternate universe.
            return frostDownloadItemList;
        }

        // analyze all provided lines of text
        final ParseResult result = new ParseResult();
        for( final String line : inputLines ) {
            // attempt to extract a key from the current line
            parseKeyFromLine(line, includeSites, result);

            // if this line contained a valid key, add it to the list of discovered keys
            if( result.key != null ) {
                frostDownloadItemList.add(new FrostDownloadItem(result.fileName, result.key));
            }
        }

        // return the list of discovered, valid keys
        return frostDownloadItemList;
    }
}
