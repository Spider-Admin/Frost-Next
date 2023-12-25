/*
 SmartFileFilters.java / Frost-Next
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

package frost.util.gui;

import java.io.File;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;

import frost.util.FileAccess;
import frost.util.gui.translation.Language;

public abstract class SmartFileFilters
{
    public static class SmartFileFilter extends FileFilter
    {
        final String fDescription;
        final SortedSet<String> fExtensions;

        /**
         * This class provides multi-extension filter globbing and numerical wildcards for defining
         * custom JFileChooser filters.
         *
         * @param {String} aDescription - the filter description, such as "PDF Document"
         * @param {String} aExtensions - case-insensitive, semi-colon separated list of extensions
         * to accept for this filter, such as "pdf" or "JPG;JPEG". NOTE: any # symbol in the input
         * is a special character that means "any number"; so "r##" would match r00 to r99 (RAR parts).
         * @param {boolean} aShowExts - if true, all of the extensions will be added (in sorted order)
         * to the description, such as "Images (gif;jpeg;jpg;png)". the automatic sorting means that
         * you're welcome to write your filter list in a logical order, such as grouping all related
         * extensions next to each other, while still guaranteeing that the user will see a sorted list.
         */
        public SmartFileFilter(
                final String aDescription,
                final String aExtensions,
                final boolean aShowExts)
        {
            super();
            fExtensions = new TreeSet<String>(); // automatically sorts all inserted strings

            // parse the provided extensions
            final String[] extargs = aExtensions.split(";");
            for( String ext : extargs ) {
                ext = ext.trim().toLowerCase();
                if( ext.isEmpty() ) { continue; }
                fExtensions.add(ext);
            }

            // build the description string
            final StringBuilder sb = new StringBuilder();
            sb.append(aDescription);
            if( aShowExts ) {
                sb.append(" (");
                boolean firstExt = true;
                for( final String validExt : fExtensions ) {
                    if( !firstExt ) {
                        sb.append(";");
                    }
                    sb.append(validExt);
                    firstExt = false;
                }
                sb.append(")");
            }
            fDescription = sb.toString();
        }

        @Override
        public boolean accept(
                final File f)
        {
            // always accept (display) directories
            if( f.isDirectory() ) { return true; }

            // now check if the current file's extension matches this filter
            // NOTE: this automatically lowercases the extension, and returns null if there is no ext
            final String fileExt = FileAccess.getFileExtension(f.getName());
            if( fileExt == null ) { return false; } // reject files without extension
            for( final String validExt : fExtensions ) {
                // if this pattern uses the special "#" (any number) matching, then only try that pattern
                // NOTE: this is checked 1st to prevent something like "r##" from matching the literal "example.r##".
                if( validExt.indexOf('#') > -1 ) {
                    // the extension lengths must be equal, otherwise this pattern won't match
                    if( fileExt.length() != validExt.length() ) {
                        continue; // skip this filter since it didn't match
                    }

                    // now compare the file ext and the pattern character by character
                    boolean matchesPattern = true; // will be set to false at mismatch
                    char pc, fc; // pc = pattern char, fc = file char
                    for( int i=0; i<validExt.length(); ++i ) {
                        pc = validExt.charAt(i);
                        fc = fileExt.charAt(i);
                        if( pc == '#' ) {
                            // there's a # in the pattern, so the file must now have a number
                            // NOTE: we only accept the ASCII numbers 0-9, since file extensions
                            // never use unicode/arabic numbers. hex: 0 = 0x30, ..., 9 = 0x39.
                            if( fc < 0x30 || fc > 0x39 ) {
                                matchesPattern = false;
                                break;
                            }
                        } else {
                            // there's a regular character in the pattern, so the chars must be equal
                            if( fc != pc ) {
                                matchesPattern = false;
                                break;
                            }
                        }
                    }

                    // if the whole pattern matched, then accept this file
                    if( matchesPattern ) {
                        return true;
                    }
                }
                // otherwise; for plain extension patterns, just check the string
                else if( fileExt.equals(validExt) ) {
                    return true;
                }
            }

            // file didn't match any valid extensions
            return false;
        }

        @Override
        public String getDescription()
        {
            return fDescription;
        }
    }

    /**
     * Installs a default set of file filters for all common file extensions.
     * @param {JFileChooser} fc - the JFileChooser to install the filters into; note that they'll
     * be *added* onto the list, and that any other custom filters will not be removed. by default,
     * JFileChoosers have no filters except the default "All files" filter.
     * @param {Language} language - an instance of the Frost translation system
     */
    public static void installDefaultFilters(
            final JFileChooser fc,
            final Language language)
    {
        // add all file filters (NOTE: we borrow the translations from the deprecated "searchpane")
        // NOTE: most/all look&feels also allow the user to type a custom filter into the filename box,
        // such as "*cat*" for all files with case-insensitive "cat" in their filename, or "*.myext".
        fc.setAcceptAllFileFilterUsed(true); // enable the "All files" filter dropdown
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.archives"), // Archives
                    "7Z;CBR;CBZ;R##;RAR;ZIP",
                    true));
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.audio"), // Audio
                    "FLAC;M4A;MP3;OGG;WAV;WMA",
                    true));
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.documents"), // Documents
                    "TXT;RTF;CSV;NFO;LOG;HTM;HTML;XML;PDF;EPUB;DOC;DOCX;ODT",
                    true));
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.executables"), // Executables
                    "BAT;CMD;EXE;SH;JAR",
                    true));
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.images"), // Images
                    "BMP;GIF;JPEG;JPG;PNG;PSD",
                    true));
        fc.addChoosableFileFilter(new SmartFileFilter(
                    language.getString("SearchPane.fileTypes.video"), // Videos
                    "3GP;ASF;AVI;DIVX;FLV;M4V;MKV;MP4;MPEG;MPG;MOV;OGM;RM;RMVB;TS;VOB;WEBM;WMV",
                    true));
    }
}
