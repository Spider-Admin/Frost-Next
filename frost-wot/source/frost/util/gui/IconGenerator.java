/*
 IconGenerator.java / Frost-Next
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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import javax.swing.Icon;
import javax.swing.ImageIcon;

public abstract class IconGenerator
{
    private IconGenerator() {}

    /**
     * An efficient icon generator for the '+' and '-' states used by JTrees.
     */
    public static Icon generateTreeExpansionIcon(
            final int aType,
            final char aExpansionState,
            final Color aBackgroundColor,
            final Color aForegroundColor)
    {
        // determine the type of icon and what dimensions to use
        int TYPE = 0; // circle (default)
        int SIZE = 11;
        switch(aType) {
            case 0: // circle (recommended)
                TYPE = 0; // circle
                SIZE = 11;
                break;
            case 1: // very small square
            case 2: // normal square (recommended)
            case 3: // big square
                TYPE = 1; // square
                if( aType == 1 ) {
                    SIZE = 7;
                } else if( aType == 2 ) {
                    SIZE = 9;
                } else {
                    SIZE = 11;
                }
                break;
        }

        // create a rendering surface of the desired dimensions and with transparency enabled
        final BufferedImage bufferedImage = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D g2d = bufferedImage.createGraphics();

        // fill the background with transparent pixels
        // NOTE: the "Clear" composite tells it to delete the original pixels (instead of blending),
        // and that's how filling with a transparent color creates a transparent image!
        final Composite cOriginal = g2d.getComposite();
        g2d.setComposite(AlphaComposite.Clear);
        g2d.setColor(new Color(0, 0, 0, 0)); // black, fully transparent (0 alpha)
        g2d.fillRect(0, 0, SIZE, SIZE);
        g2d.setComposite(cOriginal); // back to original "blend pixels" drawing mode

        // CIRCLE?
        if( TYPE == 0 ) {
            // use the 2D apis to enable antialiased drawing (otherwise our circles will look awful),
            // and enforce a 1px line width (the default) just in case.
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setStroke(new BasicStroke(1f));

            // calculate the offset of a centered circle in the middle of the icon
            final int circleDiameter = 8;
            final int halfPoint = SIZE / 2; // halfway down/across the icon
            final int circleOffset = halfPoint - (circleDiameter / 2); // centered

            // fill the background so that we can paint a circle without worrying about other art
            // NOTE: the "+1" is to take into account our border/line width of the circle
            g2d.setColor(aBackgroundColor);
            g2d.fillOval( circleOffset, circleOffset, circleDiameter + 1, circleDiameter + 1 );

            // now draw the circle using the correct color
            g2d.setColor(aForegroundColor);
            g2d.drawOval( circleOffset, circleOffset, circleDiameter, circleDiameter );

            // if this is the "collapsed" icon, draw a non-antialiased plus symbol inside the circle
            if( aExpansionState == '+' ) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.drawLine( circleOffset + 2, halfPoint, ((SIZE - 1) - (circleOffset + 2)), halfPoint );
                g2d.drawLine( halfPoint, circleOffset + 2, halfPoint, ((SIZE - 1) - (circleOffset + 2)) );
            }
        }
        // SQUARE?
        else {
            // disable antialiasing (for crisp lines) and enforce a 1px line width (the default) just in case
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2d.setStroke(new BasicStroke(1f));

            // draw the background
            g2d.setColor(aBackgroundColor);
            g2d.fillRect(0, 0, SIZE - 1, SIZE - 1);

            // draw foreground (square, and plus or minus symbol)
            g2d.setColor(aForegroundColor);
            g2d.drawRect(0, 0, SIZE - 1, SIZE - 1);
            g2d.drawLine( 2, (SIZE / 2), (SIZE - 3), (SIZE / 2) );
            if( aExpansionState == '+' ) {
                g2d.drawLine( (SIZE / 2), 2, (SIZE / 2), (SIZE - 3) );
            }
        }

        // convert the drawn image into an icon resource, free up the memory, and return the result
        g2d.dispose(); // destroy the drawing context
        final Icon iconResult = new ImageIcon(bufferedImage); // creates an icon from the image pixels
        bufferedImage.flush(); // clear any graphics acceleration caches
        return iconResult;
    }
}
