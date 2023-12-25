/*
AltEdit.java / Frost
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

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;

import frost.*;
import frost.ext.Execute;
import frost.ext.ExecResult;
import frost.util.*;
import frost.util.ArgumentTokenizer;
import frost.util.gui.translation.*;
import frost.util.gui.MiscToolkit;

/**
 * Class provides alternate editor functionality.
 *
 * @author bback
 */
public class AltEdit extends Thread {

    private static final Logger logger = Logger.getLogger(AltEdit.class.getName());

    private final Language language = Language.getInstance();

    private final Frame parentFrame;
    private final String linesep = System.getProperty("line.separator");

    private final String oldSubject;
    private final String oldText;

    private final String SUBJECT_MARKER = language.getString("AltEdit.markerLine.subject");
    private final String TEXT_MARKER = language.getString("AltEdit.markerLine.text");

    private final Object transferObject;
    private final AltEditCallbackInterface callbackTarget;

    public AltEdit(final String subject, final String text, final Frame parentFrame, final Object transferObject, final AltEditCallbackInterface callbackTarget) {
        this.parentFrame = parentFrame;
        this.oldSubject = subject;
        this.oldText = text;
        this.transferObject = transferObject;
        this.callbackTarget = callbackTarget;
    }

    private void callbackMessageFrame(final String newSubject, final String newText) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                callbackTarget.altEditCallback(transferObject, newSubject, newText);
            }
        });
    }

    @Override
    public void run() {

        // paranoia
        if( !Core.frostSettings.getBoolValue(SettingsClass.ALTERNATE_EDITOR_ENABLED) ) {
            callbackMessageFrame(null, null);
            return;
        }

        // grab the editor command and make sure it contains a non-empty value
        final String editorCmd = Core.frostSettings.getValue(SettingsClass.ALTERNATE_EDITOR_COMMAND);
        if( editorCmd == null || editorCmd.length() == 0 ) {
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.noAlternateEditorConfigured"),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }

        // now parse the editor command into individual arguments
        final List<String> argList = ArgumentTokenizer.tokenize(editorCmd, false);
        final String[] args = argList.toArray(new String[argList.size()]);

        // find the array index which contains the '%f' placeholder
        // NOTE: the user is allowed to have it within the string, like 'gedit "foo %f"',
        // in which case we'll see arg2 ("foo %f") and replace that with ("foo <filename>")
        // NOTE: we start at 1, since they can't use the placeholder as the first argument (0).
        int placeholderIdx = -1;
        for( int i=1; i<args.length; ++i ) {
            if( args[i].indexOf("%f") > -1 ) {
                placeholderIdx = i;
                break;
            }
        }

        // if there wasn't any placeholder, then warn the user and abort
        if( placeholderIdx < 0 ) {
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.missingPlaceholder"),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }

        // create the temporary file and tell Java to delete it when the JVM exits
        final File editFile = FileAccess.createTempFile("frostmsg", ".txt");
        editFile.deleteOnExit();

        // build the temporary message that we'll give to the external editor
        StringBuilder sb = new StringBuilder();
        sb.append(language.getString("AltEdit.textFileMessage.1")).append(linesep);
        sb.append(language.getString("AltEdit.textFileMessage.2")).append(linesep);
        sb.append(language.getString("AltEdit.textFileMessage.3")).append(linesep).append(linesep);
        sb.append(SUBJECT_MARKER).append(linesep);
        sb.append(
                // NOTE: We default to "No subject" if they're using external editors, to make it
                // clear where to put the subject. But "No subject" will never be accepted by the
                // final post-message frame, so they'll be forced to change it before sending. ;)
                ( oldSubject == null || oldSubject.isEmpty() ? "No subject" : oldSubject )
        ).append(linesep).append(linesep);
        sb.append(oldText).append(linesep); // contains new from-header-line
        sb.append(TEXT_MARKER).append(linesep);

        // attempt to write the temporary message to our temp file
        if( FileAccess.writeFile(sb.toString(), editFile, "UTF-8") == false ) {
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.couldNotCreateMessageFile")+": "+editFile.getPath(),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }
        sb = null; // mark the temp msg for garbage collection

        // update the calculated command arguments to point to the temporary file
        args[placeholderIdx] = args[placeholderIdx].replaceFirst("%f", editFile.getPath());

        // execute the external editor and wait for it to finish (exit) before proceeding
        final ExecResult res = Execute.run_wait(args, "UTF-8", false);
        if( res.error != null ) {
            // NOTE: these exceptions only indicate general problems like "failed to launch";
            // we *do not* look at the returnCode, since even Unix apps can have non-zero exit
            // codes for certain (still-valid) states. so we only care about failure to launch.
            editFile.delete();
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.couldNotStartEditorUsingCommand")+": ["+editorCmd+"]\n"+res.error.getMessage(),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }

        // read all the lines from the temporary file that they've edited
        final List<String> lines = FileAccess.readLines(editFile, "UTF-8");

        // do some quick validation to make sure they've got the minimum number of lines
        if( lines.size() < 4 ) { // subject marker, subject, from line, text marker
            editFile.delete();
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.invalidReturnedMessageFile"),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }

        // now just parse and validate the entire returned message file
        String newSubject = null;
        final StringBuilder newTextSb = new StringBuilder();

        boolean inNewText = false;
        for( final Iterator<String> it=lines.iterator(); it.hasNext(); ) {
            String line = it.next();

            if( inNewText ) {
                newTextSb.append(line).append(linesep);
                continue;
            }

            if( line.equals(SUBJECT_MARKER) ) {
                // next line is the new subject
                if( it.hasNext() == false ) {
                    editFile.delete();
                    MiscToolkit.showMessageDialog(parentFrame,
                            language.getString("AltEdit.errorDialog.invalidReturnedMessageFile"),
                            language.getString("AltEdit.errorDialogs.title"),
                            MiscToolkit.ERROR_MESSAGE);
                    callbackMessageFrame(null, null);
                    return;
                }
                line = it.next();
                if( line.equals(TEXT_MARKER) ) {
                    editFile.delete();
                    MiscToolkit.showMessageDialog(parentFrame,
                            language.getString("AltEdit.errorDialog.invalidReturnedMessageFile"),
                            language.getString("AltEdit.errorDialogs.title"),
                            MiscToolkit.ERROR_MESSAGE);
                    callbackMessageFrame(null, null);
                    return;
                }
                newSubject = line.trim();
                continue;
            }

            if( line.equals(TEXT_MARKER) ) {
                // text begins
                inNewText = true;
            }
        }

        // we must have at least found the subject line marker
        if( newSubject == null ) {
            editFile.delete();
            MiscToolkit.showMessageDialog(parentFrame,
                    language.getString("AltEdit.errorDialog.invalidReturnedMessageFile"),
                    language.getString("AltEdit.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            callbackMessageFrame(null, null);
            return;
        }

        // finished, we have a newSubject and a newText now, so just display
        // a message frame with the constructed message for further editing/sending
        callbackMessageFrame(newSubject, newTextSb.toString());
    }
}
