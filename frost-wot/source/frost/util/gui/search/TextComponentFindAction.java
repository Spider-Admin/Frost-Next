/*
  TextComponentFindAction.java / Frost
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
package frost.util.gui.search;

import java.awt.Color;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Highlighter;

// @author The Kitty (complete rewrite of original garbage to make it actually freaking work)
public class TextComponentFindAction
    extends FindAction
{

    // 1. inits fSearchField with selected text
    // 2. sets a new caret on the textcomponent which renders the selection even 
    //    if the textcomponent doesn't have focus, since some L&Fs may otherwise hide it
    @Override
    protected void initSearch(
            final ActionEvent ae)
    {
        super.initSearch(ae);

        JTextComponent textComp = (JTextComponent)ae.getSource();
        String selectedText = textComp.getSelectedText();
        if( selectedText != null ) {
            fSearchField.setText(selectedText);
        }

        if( !(textComp.getCaret() instanceof AlwaysHighlightingCaret) ) {
            try {
                // back up the current caret's selection (these always return 0 or higher, even if null document)
                final int startPos = textComp.getSelectionStart();
                final int endPos = textComp.getSelectionEnd();

                // clear the current selection by setting the caret to its current location,
                // so that the current highlight is removed before we remove the old caret
                textComp.setSelectionStart(endPos); // we use end here so that the "next match" feature works
                textComp.setSelectionEnd(endPos);

                // set the new caret which ensures visibility in all L&Fs even if document doesn't have focus
                textComp.setCaret(new AlwaysHighlightingCaret());

                // restore the old selection so that the old caret's highlight is applied again
                textComp.setSelectionStart(startPos);
                textComp.setSelectionEnd(endPos);
            } catch( final Exception e ) {}
        }
    }

    @Override
    protected boolean changed(
            final JComponent aSearchComponent,
            String aSearchText,
            Position.Bias aBias)
    {
        JTextComponent textComp = (JTextComponent)aSearchComponent;
        int offset = aBias==Position.Bias.Forward ? textComp.getCaretPosition() : textComp.getCaret().getMark() - 1;

        int index = getNextMatch(textComp, aSearchText, offset, aBias);
        if(index!=-1){
            textComp.select(index, index + aSearchText.length());
            return true;
        }else{
            offset = aBias==null || aBias==Position.Bias.Forward ? 0 : textComp.getDocument().getLength();
            index = getNextMatch(textComp, aSearchText, offset, aBias);
            if(index!=-1){
                textComp.select(index, index + aSearchText.length());
                return true;
            }else{
                // no matches, so deselect the current selection using the old sel's START-position so that
                // if the user corrects their query via backspace, it'll start from that caret position
                // and select the exact same entry again (as intended), rather than skipping over it.
                try {
                    final int startPos = textComp.getSelectionStart();
                    textComp.setSelectionStart(startPos); // we use start here so that "next match" finds this again
                    textComp.setSelectionEnd(startPos);
                } catch( final Exception e ) {}
                return false;
            }
        }
    }

    protected int getNextMatch(JTextComponent textComp, String aSearchText, int startingOffset, Position.Bias aBias){
        String text = null;

        // get text from document, otherwize it won't work with JEditorPane with html
        try{
            text = textComp.getDocument().getText(0, textComp.getDocument().getLength());
        } catch(BadLocationException e){
            throw new RuntimeException("This should never happen!");
        }

        if(fIgnoreCase){
            aSearchText = aSearchText.toUpperCase();
            text = text.toUpperCase();
        }

        return aBias==null || aBias==Position.Bias.Forward
                ? text.indexOf(aSearchText, startingOffset)
                : text.lastIndexOf(aSearchText, startingOffset);
    }

    private class AlwaysHighlightingCaret extends DefaultCaret
    {
        public AlwaysHighlightingCaret()
        {
            super();
            setBlinkRate(500); // otherwise caret won't blink
        }

        @Override
        protected Highlighter.HighlightPainter getSelectionPainter()
        {
            return super.getSelectionPainter();
        }

        // this function determines if the caret's selection should be highlighted or not
        @Override
        public void setSelectionVisible(
                final boolean hasFocus)
        {
            // some L&F implementations will hide the caret's selection when focus is lost,
            // but we simply force it to always be on, so that it's always visible.
            super.setSelectionVisible(false); // quick false for compatibility's sake
            super.setSelectionVisible(true);
        }
    }
}
