/*
  CopyToClipboard.java / Frost
  Copyright (C) 2007  Frost Project <jtcfrost.sourceforge.net>

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

import java.awt.*;
import java.awt.datatransfer.*;

// NOTE: this lets us access extra info regarding FrostUploadItems, and importing the class definition
// is the easiest method that doesn't involve tediously extending the ICopyToClipboardItem interface
// to add extra fields for all the different types of download/upload file-items
import frost.fileTransfer.upload.FrostUploadItem;
import frost.fileTransfer.KeyParser;
import frost.util.gui.translation.*;

public class CopyToClipboard {

    private static Clipboard clipboard = null;

    private static class DummyClipboardOwner implements ClipboardOwner {
        public void lostOwnership(final Clipboard tclipboard, final Transferable contents) { }
    }

    private static DummyClipboardOwner dummyClipboardOwner = new DummyClipboardOwner();

    private static Clipboard getClipboard() {
        if (clipboard == null) {
            clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        }
        return clipboard;
    }

    public static void copyText(final String text) {
        final StringSelection selection = new StringSelection(text);
        getClipboard().setContents(selection, dummyClipboardOwner);
    }

    /**
     * This method copies *just* the CHK keys and file names of the selected items (if any) to the clipboard.
     * Each ModelItem must implement interface ICopyToClipboardItem.
     * NOTE: See getItemInformation() for info about cleanupKeys.
     */
    public static void copyKeysAndFilenames(final Object[] items) {
        internalCopyRoutine(items, false, false);
    }
    public static void copyKeysAndFilenames(final Object[] items, final boolean cleanupKeys) {
        internalCopyRoutine(items, false, cleanupKeys);
    }

    /**
     * This method copies extended information about the selected items (if any) to
     * the clipboard. That information is composed of the filename, the key and the size in bytes.
     * If it's a FrostUploadItem, it also includes compression, crypto key and compatibility mode.
     * Each ModelItem must implement interface ICopyToClipboardItem.
     * NOTE: See getItemInformation() for info about cleanupKeys.
     */
    public static void copyExtendedInfo(final Object[] items) {
        internalCopyRoutine(items, true, false);
    }
    public static void copyExtendedInfo(final Object[] items, final boolean cleanupKeys) {
        internalCopyRoutine(items, true, cleanupKeys);
    }

    /**
     * Used internally as a shared routine for the various copying modes.
     */
    private static void internalCopyRoutine(final Object[] items, final boolean extendedInformation, final boolean cleanupKeys) {
        if( items == null || items.length == 0 ) {
            return;
        }
        final StringBuilder textToCopy = new StringBuilder();
        for( final Object ditem : items ) {
            final String infoStr = getItemInformation(ditem, extendedInformation, cleanupKeys);
            if( infoStr == null ) { continue; }
            textToCopy.append(infoStr);

            // append 1 (basic) or 2 (extended) newlines after each item, except if this is a single item
            if( items.length > 1 ) {
                if( extendedInformation ) { textToCopy.append("\n\n"); }
                else { textToCopy.append("\n"); }
            }
        }

        // remove the additional \n's at the end if the input array had multiple items
        if( items.length > 1 ) {
            textToCopy.deleteCharAt(textToCopy.length() - 1); // basic & extended
            if( extendedInformation ) { textToCopy.deleteCharAt(textToCopy.length() - 1); } // extended only
        }

        copyText(textToCopy.toString());
    }

    /**
     * Generates an info-string regarding the given FrostDownloadItem/FrostUploadItem.
     * Used internally by the various "copy to clipboard" functions, but you're welcome to use it too.
     * NOTE: Specify cleanupKeys if your input is dirty (a message's file attachment list, or keys
     * that come from the content-area of a message). If cleanupKeys is false, all keys are assumed
     * to be completely clean, NOT URL-encoded (no %20 etc), and totally valid (i.e. your own
     * downloads/uploads table entries).
     * @param {Object} item - the upload or download item
     * @param {boolean} extendedInformation - if false, you get only the key; if true, you get all
     * the important information about the item (and in case of uploads, you get all insert settings).
     * @param {boolean} cleanupKeys - if true, all keys will be checked for validity and URL-decoded.
     * @return - null if the item is invalid, otherwise a string. be aware there's no trailing newline!
     */
    public static String getItemInformation(final Object item, final boolean extendedInformation, final boolean cleanupKeys) {
        // make sure the item is valid
        if( item == null || !(item instanceof CopyToClipboardItem) ) {
            return null;
        }

        // load the language-specific "key not available" string first
        final String keyNotAvailableMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.keyNotAvailableYet");

        // extract the key and filename information from the item
        final CopyToClipboardItem cbItem = (CopyToClipboardItem) item;
        String key = cbItem.getKey();
        String fileName = cbItem.getFileName();

        // if cleanup is requested, then clean up and validate the key and filename
        if( cleanupKeys ) {
            final KeyParser.ParseResult result = new KeyParser.ParseResult();

            // attempt to parse and validate the current key and extract its real filename
            // NOTE: we don't want Freesite keys so the 2nd argument is false
            KeyParser.parseKeyFromLine(key, false, result);

            // if this key was invalid, then return null
            if( result.key == null ) {
                return null;
            }

            // otherwise, simply set the key and name to the real, cleaned-up versions
            key = result.key;
            fileName = result.fileName;
        }

        // generate the key string for this item
        if( key == null ) {
            // no key; it's a shared file or an unfinished upload. if this is a non-extended
            // request, just use the filename as-is (without the key part)
            if( !extendedInformation ) {
                key = fileName;
            } else {
                // otherwise, use the "key not available yet" message in extended mode
                key = keyNotAvailableMessage;
            }
        }
        // NOTE: else{} if a non-null key existed, then we don't need to do anything else since the
        // keys in Frost-Next always contain all needed information:
        // - the strict and robust KeyParser class only accepts valid keys in the first place
        // - the Freenet node only sends us valid keys *with* filenames for our completed uploads
        // - the only other source of unchecked keys (attachments and message text) might not have
        // filenames for the keys, but then the correct behavior is to return their malformed key as-is.

        // if this is just a basic non-extended request, we just return the key now
        if( !extendedInformation ) {
            return key;
        }

        // time to build an extended info string; first load the remaining language-specific strings
        final String fileMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.file")+" ";
        final String keyMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.key")+" ";
        final String bytesMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.bytes")+" ";
        final String modeMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.mode")+" "; // only for FrostUploadItem
        final String compressMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.compress")+" "; // only for FrostUploadItem
        final String compressAutoMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.compressAuto"); // "YES/AUTO"
        final String compressDisabledMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.compressDisabled"); // "NO"
        final String cryptoKeyMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.cryptoKey")+" "; // only for FrostUploadItem
        final String cryptoKeyAutoMessage = Language.getInstance().getString("Common.copyToClipBoard.extendedInfo.cryptoKeyAuto"); // "[64-char lowercase hex key]" or "AUTO"

        // now build the extended information string
        final StringBuilder infoStr = new StringBuilder();
        String fs = "?";
        if( cbItem.getFileSize() >= 0 ) {
            fs = Long.toString(cbItem.getFileSize());
        }
        infoStr.append(fileMessage);
        infoStr.append(fileName).append("\n");
        infoStr.append(keyMessage);
        infoStr.append(key).append("\n");
        infoStr.append(bytesMessage);
        infoStr.append(fs);
        if( item instanceof FrostUploadItem ) {
            // since this is a FrostUploadItem, we will also output the mode, compression and cryptokey settings
            final FrostUploadItem ulItem = (FrostUploadItem) item;
            infoStr.append("\n").append(modeMessage);
            infoStr.append(ulItem.getFreenetCompatibilityMode()).append("\n");
            infoStr.append(compressMessage);
            infoStr.append( ( ulItem.getCompress() ? compressAutoMessage : compressDisabledMessage ) ).append("\n");
            final String cryptoKey = ulItem.getCryptoKey(); // null if no custom key
            infoStr.append(cryptoKeyMessage);
            infoStr.append( ( cryptoKey == null ? cryptoKeyAutoMessage : cryptoKey ) );
        }
        return infoStr.toString();
    }
}
