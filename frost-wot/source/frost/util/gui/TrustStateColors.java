/*
 TrustStateColors.java / Frost-Next
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

import java.awt.Color;

/**
 * Unified class defining the trust state colors used everywhere in Frost's GUI.
 */
public abstract class TrustStateColors
{
    public static final Color BAD       = new Color(0xFF, 0x19, 0x00); // red with a tiny bit of soft orange
    public static final Color NEUTRAL   = new Color(0xFF, 0xB6, 0x00); // slightly muted but still shiny orange, calls attention
    public static final Color GOOD      = new Color(0x00, 0x8E, 0x00); // midtone green, neutral brightness since it's a very common state
    public static final Color FRIEND    = new Color(0x09, 0x84, 0xC8); // dark/muted sky blue, stands out without being bright
}
