/*
  TableFindAction.java / Frost
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

import javax.swing.*;
import javax.swing.text.*;

// @author The Kitty (complete rewrite of original garbage to make it actually freaking work)
public class TableFindAction extends FindAction{ 
 
    @Override
    protected boolean changed(
            final JComponent aSearchComponent,
            String aSearchText,
            Position.Bias aBias)
    {
        JTable table = (JTable)aSearchComponent; 
        boolean startingFromSelection = true; 
        int max = table.getRowCount(); 
        int increment = 0; 
        if(aBias!=null) 
            increment = (aBias == Position.Bias.Forward) ? 1 : -1; 
        int startingRow = (table.getSelectionModel().getLeadSelectionIndex() + increment + max) % max; 
        if (startingRow < 0 || startingRow >= table.getRowCount()) { 
            startingFromSelection = false; 
            startingRow = 0; 
        } 
 
        int index = getNextMatch(table, aSearchText, startingRow, aBias); 
        if (index != -1) { 
            changeSelection(table, index); 
            return true; 
        } else if (startingFromSelection) {
            // we fall back to searching whole table since there were no matches from current row
            index = getNextMatch(table, aSearchText, 0, aBias); 
            if (index != -1) { 
                changeSelection(table, index); 
                return true; 
            } 
        } 
        return false; 
    } 
 
    protected void changeSelection(JTable table, int index){ 
        //table.addRowSelectionInterval(index, index); // TODO: maybe make it possible to "add to selection" somehow
        table.setRowSelectionInterval(index, index);
        int column = table.getSelectedColumn(); 
        if(column==-1) 
            column = 0; 
        table.scrollRectToVisible(table.getCellRect(index, column, true)); 
    } 
 
    public int getNextMatch(JTable table, String prefix, int startIndex, Position.Bias aBias){ 
        int column = table.getSelectedColumn(); 
        if(column==-1) 
            column = 0; 
        int max = table.getRowCount(); 
        if(prefix==null){ 
            throw new IllegalArgumentException(); 
        } 
        if(startIndex<0 || startIndex>= max){ 
            throw new IllegalArgumentException(); 
        } 
 
        if(fIgnoreCase) 
            prefix = prefix.toUpperCase(); 
 
        // start search from the next element after the selected element 
        int increment = (aBias==null || aBias==Position.Bias.Forward) ? 1 : -1; 
        int index = startIndex; 
        do{ 
            Object item = table.getValueAt(index, column); 
 
            if(item!=null){ 
                String text = item.toString(); 
                if(fIgnoreCase) 
                    text = text.toUpperCase(); 
//System.out.println("comp:"+prefix+","+text+","+item);
                // we search for any occurence
                if(text!=null && text.indexOf(prefix) > -1){ 
                    return index; 
                } 
//                if(text!=null && text.startsWith(prefix)){ 
//                    return index; 
//                } 
            } 
            index = (index+increment+max)%max; 
        } while(index!=startIndex); 
        return -1; 
    } 
}
