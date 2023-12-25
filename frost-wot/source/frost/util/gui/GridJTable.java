/*
 GridJTable.java / Frost-Next
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

import javax.swing.JTable;
import javax.swing.table.TableModel;

/**
 * This is a simple extension of a JTable which ensures that
 * the grid is always on/off as desired, even when the L&F
 * changes. Just call setMustShowGrid(true/false) immediately
 * after constructing this object and you're done!
 *
 * Why? Because normal JTables aren't good enough, since they
 * inherit their default grid on/off setting from the L&F, and
 * even if you manually "setShowGrid()" they'll forget that
 * setting whenever you change to another L&F. This solves that.
 * 
 * Usage:
 * final JTable table = new GridJTable(model);
 * ((GridJTable)table).setEnforcedShowGrid(true);
 */
public class GridJTable extends JTable
{
    private boolean fMustShowGrid = true;

    public GridJTable(
            final TableModel dm)
    {
        super(dm);
    }

    public void setEnforcedShowGrid(
            final boolean aMustShowGrid)
    {
        fMustShowGrid = aMustShowGrid;
        setShowGrid(fMustShowGrid);
    }

    @Override
    public void updateUI()
    {
        super.updateUI();
        // L&F changed; enforce the grid setting
        setShowGrid(fMustShowGrid);
    }
}
