/*
 CompoundUndoManager.java / Frost-Next
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

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import javax.swing.text.JTextComponent;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AbstractDocument.DefaultDocumentEvent;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * This class merges a user's individual Frost-Next text edits into a single, larger edit.
 * - Single characters entered/deleted sequentially will be grouped together and undone as a group.
 * - Moving the caret (text cursor) to a new location before typing will start a new group.
 * - Pastes will be a new group *if* the paste is 2+ characters long.
 * - Single-character pastes are treated as normal single-character typing and become part of the
 *   current group.
 * - Attribute changes are considered part of the current group and are undone when the group is
 *   undone, thus ensuring syntax highlighting compatibility.
 * - A new undo group is always started if the user has been inactive for 5+ seconds since last edit.
 * - Performing an undo will close the current undo-group, so that further edits start a new group.
 *
 * Fully thread-safe, with synchronization and volatile shared variables.
 *
 * Usage:
 * CompoundUndoManager undoManager = new CompoundUndoManager(textComponent);
 *
 * Buttons (which automatically manage their disabled/enabled states):
 * JButton btnUndo = new JButton(undoManager.getUndoAction());
 * JButton btnRedo = new JButton(undoManager.getRedoAction());
 * (or via theButton.setAction() if the button already exists)
 *
 * Keyboard bindings:
 * textComponent.getActionMap().put("Undo", undoManager.getUndoAction());
 * textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK, true), "Undo"); // ctrl + z
 * textComponent.getActionMap().put("Redo", undoManager.getRedoAction());
 * textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "Redo"); // ctrl + shift + z
 * textComponent.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK, true), "Redo"); // ctrl + y
 *
 * If you ever change to a different Document, you will have to reset and re-attach the event listener properly, as follows:
 * textComponent.getDocument().removeUndoableEditListener(undoManager); // stop listening to changes
 * undoManager.discardAllEdits(); // discard the entire undo history
 * textComponent.setDocument(newDocument); // change document
 * textComponent.getDocument().addUndoableEditListener(undoManager); // listen to changes in the new document
 * NOTE: The textComponent itself obviously cannot be changed, since the CompoundUndoManager is hooked into it.
 * So if you want to replace the entire component (not just its document), you'll have to construct a new undo manager too.
 */
public class CompoundUndoManager
    extends UndoManager
    implements UndoableEditListener, DocumentListener
{
    private UndoManager fUndoManager;
    private volatile CompoundEdit fCompoundEdit;
    private JTextComponent fTextComponent;
    private UndoAction fUndoAction;
    private RedoAction fRedoAction;

    // when the user has been idle for 5 seconds, we'll always start
    // a new compound edit. so if the user returns to the document
    // and types something new and then hits undo, it first undoes the
    // newly written content.
    private static final int IDLE_DELAY_MS = 5000;

    // These fields are used to help determine whether the edit is an
    // incremental edit. The offset and length should increase by 1 for
    // each character added or decrease by 1 for each character removed.
    // We also keep track of the timestamp of the last edit.
    private volatile int fLastOffset;
    private volatile int fLastLength;
    private volatile long fLastTimestamp = 0;


    /**
     * Creates a new Compound Undo Manager.
     * @param {JTextComponent} aTextComponent - the actual text component you want to attach to, such as a JTextArea
     */
    public CompoundUndoManager(
            final JTextComponent aTextComponent)
    {
        this.fTextComponent = aTextComponent;
        fUndoManager = this;
        fUndoAction = new UndoAction();
        fRedoAction = new RedoAction();
        fTextComponent.getDocument().addUndoableEditListener(this);
    }

    /**
     * Empties the undo manager and sends each edit a "die()" message, so that they
     * cannot be undone by this or any other undo manager. This is useful if you're
     * going to perform edits to the document that you don't want the user to be able
     * to undo (usually if your edits would change the document offsets, thus invalidating
     * all offsets of previous user-edits). They'll lose their entire undo history,
     * but that's usually unavoidable anyway due to the offset-issue mentioned.
     */
    @Override
    public synchronized void discardAllEdits()
    {
        if( fCompoundEdit != null ) {
            fCompoundEdit.end();
            fCompoundEdit = null;
        }
        super.discardAllEdits();
        fUndoAction.updateUndoState();
        fRedoAction.updateRedoState();
    }

    /**
     * You probably don't want to call these directly. Use the UndoAction and RedoAction instead.
     * NOTE: We temporarily attach a DocumentListener during the undo/redo,
     * which takes care of positioning the caret position correctly after each edit.
     */
    @Override
    public synchronized void undo()
    {
        fTextComponent.getDocument().addDocumentListener(this);
        super.undo();
        fTextComponent.getDocument().removeDocumentListener(this);
    }

    @Override
    public synchronized void redo()
    {
        fTextComponent.getDocument().addDocumentListener(this);
        super.redo();
        fTextComponent.getDocument().removeDocumentListener(this);
    }

    /**
     * Whenever an UndoableEdit happens, the edit will either be absorbed
     * by the current compound edit or a new compound edit will be started,
     * depending on the rules explained at the top of this class.
     */
    @Override
    public synchronized void undoableEditHappened(
            final UndoableEditEvent ev)
    {
        // calculate the time difference from the last event, and update the stored timestamp
        final long now = System.currentTimeMillis();
        final long timeDiff = now - fLastTimestamp;
        fLastTimestamp = now;

        // start a new compound edit if none exists
        if( fCompoundEdit == null ) {
            fCompoundEdit = startCompoundEdit(ev.getEdit());
            return;
        }

        // get the caret position and document length (we can't just get the length/offset of the event itself,
        // since the length of the event is always positive and won't tell us if it's a delete or addition)
        int offsetChange = fTextComponent.getCaretPosition() - fLastOffset;
        int lengthChange = fTextComponent.getDocument().getLength() - fLastLength;

        // check if this is just an attribute change, and if so merge it with the current compound edit
        AbstractDocument.DefaultDocumentEvent docEvent = (AbstractDocument.DefaultDocumentEvent)ev.getEdit();
        if( docEvent.getType().equals(DocumentEvent.EventType.CHANGE) ) {
            // if the user hasn't moved the caret, it means the attribute change was a result of things like syntax highlighting
            if( offsetChange == 0 ) {
                fCompoundEdit.addEdit(ev.getEdit());
                return;
            }
        }

        // now check for an incremental edit or backspace, which happened *WITHIN* the 5 second maxdelay.
        // the change in caret position and document length must *both* be either 1 or -1.
        // * if they mismatch (such as offset -1, length 1), it means that the user has
        // manually moved the caret and wasn't just typing/using backspace.
        // * if the user moves 1 step backwards and inserts a character, it will trigger
        // a new edit since that gives an offsetChange of 0 and isn't seen as a continuous edit.
        // * all manual moves of the caret position causes a new edit.
        if( timeDiff <= IDLE_DELAY_MS && offsetChange == lengthChange && Math.abs(offsetChange) == 1 ) {
            fCompoundEdit.addEdit(ev.getEdit());
            fLastOffset = fTextComponent.getCaretPosition();
            fLastLength = fTextComponent.getDocument().getLength();
            return;
        }

        // not incremental edit, end the previous edit and start a new one
        fCompoundEdit.end();
        fCompoundEdit = startCompoundEdit(ev.getEdit());
    }

    /**
     * Each CompoundEdit will store a group of related incremental edits
     * (ie. each character typed or backspaced is an incremental edit)
     */
    private CompoundEdit startCompoundEdit(
            final UndoableEdit anEdit)
    {
        // track caret and document length information of this new compound edit
        fLastOffset = fTextComponent.getCaretPosition();
        fLastLength = fTextComponent.getDocument().getLength();

        // the compound edit is used to store incremental edits
        fCompoundEdit = new MyCompoundEdit();
        fCompoundEdit.addEdit(anEdit);

        // the compound edit is added to the UndoManager. All incremental
        // edits stored in the compound edit will be undone/redone at once
        addEdit(fCompoundEdit);

        fUndoAction.updateUndoState();
        fRedoAction.updateRedoState();

        return fCompoundEdit;
    }


    /**
     * The Action to Undo changes to the Document.
     * The state of the Action is managed by the CompoundUndoManager
     */
    public Action getUndoAction()
    {
        return fUndoAction;
    }

    /**
     * The Action to Redo changes to the Document.
     * The state of the Action is managed by the CompoundUndoManager
     */
    public Action getRedoAction()
    {
        return fRedoAction;
    }


    /**
     * DocumentListener Implementation:
     * Updates to the Document as a result of Undo/Redo will cause the
     * caret to be repositioned to the end of the inserted text (at redo)
     * or the start of the removed text (at undo).
     * These listeners are only bound while an undo/redo is taking place.
     */
    @Override
    public void insertUpdate(
            final DocumentEvent ev)
    {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                int offset = ev.getOffset() + ev.getLength();
                offset = Math.min(offset, fTextComponent.getDocument().getLength());
                fTextComponent.setCaretPosition(offset);
            }
        });
    }

    @Override
    public void removeUpdate(
            final DocumentEvent ev)
    {
        fTextComponent.setCaretPosition(ev.getOffset());
    }

    @Override
    public void changedUpdate(
            final DocumentEvent ev)
    {
        // ignore
    }


    /**
     * We extend a regular CompoundEdit to intelligently end
     * the current compound group when the user performs an undo.
     * This ensures that a new group is started after the undo.
     */
    class MyCompoundEdit extends CompoundEdit
    {
        @Override
        public boolean isInProgress()
        {
            // in order for the canUndo() and canRedo() methods to work,
            // always assume that the compound edit is never in progress
            return false;
        }

        @Override
        public void undo() throws CannotUndoException
        {
            // end the edit so future edits don't get absorbed by this edit
            if( fCompoundEdit != null ) {
                fCompoundEdit.end();
            }

            super.undo();

            // always start a new compound edit after an undo
            fCompoundEdit = null;
        }
    }
 

    /**
     * These Undo and Redo actions can be bound to menu items, keyboard shortcuts or buttons.
     * They handle everything related to performing the action and catching any exceptions.
     */

    /**
     * Perform the Undo and update the state of the undo/redo Actions.
     */
    class UndoAction extends AbstractAction
    {
        public UndoAction()
        {
            putValue(Action.NAME, "Undo");
            putValue(Action.SHORT_DESCRIPTION, getValue(Action.NAME));
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_U));
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK, true)); // ctrl + z
            setEnabled(false);
        }

        @Override
        public void actionPerformed(
                final ActionEvent ev)
        {
            try
            {
                fUndoManager.undo();
                fTextComponent.requestFocusInWindow();
            }
            catch( CannotUndoException ex ) {}

            updateUndoState();
            fRedoAction.updateRedoState();
        }

        private void updateUndoState()
        {
            setEnabled(fUndoManager.canUndo());
        }
    }

    /**
     * Perform the Redo and update the state of the undo/redo Actions
     */
    class RedoAction extends AbstractAction
    {
        public RedoAction()
        {
            putValue(Action.NAME, "Redo");
            putValue(Action.SHORT_DESCRIPTION, getValue(Action.NAME));
            putValue(Action.MNEMONIC_KEY, new Integer(KeyEvent.VK_R));
            putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK, true)); // ctrl + y
            setEnabled(false);
        }

        @Override
        public void actionPerformed(
                final ActionEvent ev)
        {
            try
            {
                fUndoManager.redo();
                fTextComponent.requestFocusInWindow();
            }
            catch( CannotRedoException ex ) {}

            updateRedoState();
            fUndoAction.updateUndoState();
        }

        protected void updateRedoState()
        {
            setEnabled(fUndoManager.canRedo());
        }
    }
}
