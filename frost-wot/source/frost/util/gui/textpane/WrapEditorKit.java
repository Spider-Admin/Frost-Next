/*
 WrapEditorKit.java / Frost-Next
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

package frost.util.gui.textpane;

import javax.swing.*;
import javax.swing.text.*;

/*
 * Solves the Java 1.7+ line wrapping issue once and for all,
 * by forcing breaks of extremely long words on character
 * boundaries instead of on whitespace boundaries.
 *
 * In Java 7, the default text behavior changed from "wrap on
 * whitespace, or on characters if the words are too long",
 * to merely "wrap on whitespace" (refusing to break long words).
 *
 * This fix implements a custom renderer which tells Java to break
 * on whitespace as usual, but to break on characters too if the
 * words are extremely long.
 *
 * That improves the rendering of Freenet links, which are single
 * words without any whitespace. They will now properly line-wrap.
 *
 * - The Kitty
 */
public class WrapEditorKit extends StyledEditorKit
{
    private final ViewFactory fDefaultFactory = new WrapColumnFactory();

    @Override
    public ViewFactory getViewFactory()
    {
        return fDefaultFactory;
    }

    private class WrapColumnFactory implements ViewFactory
    {
        @Override
        public View create(
                final Element aElem)
        {
            final String kind = aElem.getName();
            if( kind != null ) {
                if( kind.equals(AbstractDocument.ContentElementName) ) {
                    return new WrapLabelView(aElem);
                } else if( kind.equals(AbstractDocument.ParagraphElementName) ) {
                    return new ParagraphView(aElem);
                } else if( kind.equals(AbstractDocument.SectionElementName) ) {
                    return new BoxView(aElem, View.Y_AXIS);
                } else if( kind.equals(StyleConstants.ComponentElementName) ) {
                    return new ComponentView(aElem);
                } else if( kind.equals(StyleConstants.IconElementName) ) {
                    return new IconView(aElem);
                }
            }

            // Default to text display.
            return new LabelView(aElem);
        }
    }

    private class WrapLabelView extends LabelView
    {
        public WrapLabelView(
                final Element aElem)
        {
            super(aElem);
        }

        @Override
        public float getMinimumSpan(
                final int aAxis)
        {
            switch( aAxis ) {
                case View.X_AXIS:
                    // this is what forces lines to be wrapped to the viewport's exact width (X-Axis)
                    // even for extremely long words (i.e. Freenet links) that don't contain any
                    // whitespace. this fixes Java 1.7+'s behavior of refusing to break long words.
                    // we now force-break on characters if a word is too long.
                    //
                    // the way it works is that the scrollbar asks the content pane (such as a TextPane/TextArea,
                    // using this WrapEditorKit as its EditorKit) what width to use, and we reply
                    // that our width is 0 (= viewport width), which means we disable horizontal
                    // scrollbars and instead force Java to always break extra-long words.
                    return 0;
                case View.Y_AXIS:
                    return super.getMinimumSpan(aAxis);
                default:
                    throw new IllegalArgumentException("Invalid axis: " + aAxis);
            }
        }
    }
}
