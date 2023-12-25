/*
 SortedModelTable.java / Frost
 Copyright (C) 2003  Frost Project <jtcfrost.sourceforge.net>

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
package frost.util.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import frost.util.gui.*;

// NOTE: This is one of the three types of sortable tables in Frost:
// - SortedTable
// - SortedModelTable: mostly used for uploads/downloads tables and sent/outbox message lists
// - The board-message lists (they're a treetable and use their own implementation)
public class SortedModelTable<T extends ModelItem<T>> extends ModelTable<T> {
	
	/**
	 * Index in the ModelTable of the column the model is 
	 * sorted by (or -1 if it is not currently sorted). 
	 */
	private int currentSortedColumnNumber = -1;
	private boolean ascending;
	
	/**
	 * @param newModel
	 * @param newTableFormat
	 */
	public SortedModelTable(SortedModel<T> newModel) {

        super(newModel.getTableFormat());
        
        newModel.setTable(this);
        
		setModel(newModel);
		initialize();
		
		getTable().setTableHeader(new SortedTableHeader(this));
	}

    // you can override this to be able to configure the default sort-order of clicked columns,
    // by checking the column number and returning true for ascending or false for descending.
    // this is used when the user clicks a different column to sort by something else. the user
    // expects logical default sorting behaviors, such as sorting dates descending.
    public boolean getColumnDefaultAscendingState(final int columnNumber) {
        return true;
    }

    // triggered when the user clicks a column header, and is the model-indexed column number.
    void columnClicked(int columnNumber) {
        // if the user clicks the same column then toggle ascending/descending,
        // if the user clicks another column then use that column's default order.
        ascending = ( columnNumber == currentSortedColumnNumber ? !ascending : getColumnDefaultAscendingState(columnNumber) );
        currentSortedColumnNumber = columnNumber;

        resortTable();
    }
    
    public void setSortedColumn(final int col, final boolean asc) {
        currentSortedColumnNumber = col;
        ascending = asc;
        resortTable();
    }
    
    public void resortTable() {
        // step 1: save the list of model items the user has selected (we don't care about what table
        // rows they're in, since it's impossible to know where they will end up after sorting)
        // NOTE: this may be null if there's no selection
        final List<T> selectedItems = getSelectedItems();

        FrostSwingWorker worker = new FrostSwingWorker(table) {

            @Override
            protected void doNonUILogic() throws RuntimeException { // step 2
                // now re-sort the model data (this sorting method means the view's row-index and model's index always corresponds 1:1)
                int index = convertColumnIndexToFormat(currentSortedColumnNumber);
                model.sort(index, ascending);
            }

            @Override
            protected void doUIUpdateLogic() throws RuntimeException { // step 3
                // repaint the table so that it shows the re-sorted row order
                table.revalidate();
                table.repaint();

                // apply the same selection again, taking all updated row offsets into account
                model.selectTheseModelItems(selectedItems);
            }

        };
        worker.start();
    }
    
    public void refreshView() {
        table.revalidate();
        table.repaint();
    }
	
	/**
	 * This method returns the number of the column the
	 * table is currently sorted by (or -1 if none).
	 * 
	 * @return the number of the column that is currently sorted. -1 if none.
	 */
	public int getSortedColumn() {
		return currentSortedColumnNumber;
	}

	/**
	 * @return  true if the sort order is ascending
	 */
	public boolean isSortedAscending() {
		return ascending;
	}
	
	/**
	 * This method returns the model item that is represented on a particular
	 * row of the table
	 * @param rowIndex the index of the row the model is represented on
	 * @return the model item (may be null)
	 */
	public T getItemAt(int rowIndex) {
		return model.getItemAt(rowIndex);
	}

	/* (non-Javadoc)
	 * @see frost.util.model.gui.ModelTable#setColumnVisible(int, boolean)
	 */
	@Override
    public void setColumnVisible(int index, boolean visible) {
		super.setColumnVisible(index, visible);
		if (!visible) {
			if (index == currentSortedColumnNumber) {
				currentSortedColumnNumber = -1;
			} else if (index < currentSortedColumnNumber) {
				currentSortedColumnNumber--;
			}
		}
	}
}
