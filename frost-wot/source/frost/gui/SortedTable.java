/*
  SortedTable.java / Frost
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
package frost.gui;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import javax.swing.JTable;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import frost.gui.model.SortedTableModel;
import frost.gui.model.TableMember;
import frost.util.gui.GridJTable;

// NOTE: This is one of the three types of sortable tables in Frost:
// - SortedTable
// - SortedModelTable: mostly used for uploads/downloads tables and sent/outbox message lists
// - The board-message lists (they're a treetable and use their own implementation)
public class SortedTable<T extends TableMember<T>> extends GridJTable
{
    protected int sortedColumnIndex = 0;
    protected boolean sortedColumnAscending = true;

    private SortHeaderRenderer columnHeadersRenderer = new SortHeaderRenderer();

    public SortedTable(SortedTableModel<T> model)
    {
        super(model);
        setEnforcedShowGrid(true); // GridJTable: always show grid even when L&F changes

        model.setParentTable(this);

        initSortHeader();
        
    }

    /**
     * Sorts by the given column index and order, and tells the table to remember
     * the setting, so that further clicks on the table headings will act properly
     * (such as a subsequent click on the same column inverting the sort order).
     */
    public void setSortedColumn( int val, boolean val2 )
    {
        if( !(getModel() instanceof SortedTableModel<?> ))
            return;
        sortedColumnIndex = val;
        sortedColumnAscending = val2;
        sortColumn(sortedColumnIndex, sortedColumnAscending);
        getModel().tableEntriesChanged();
    }

    /**
     * Sorts by a specific column but doesn't remember the settings and doesn't notify the
     * table model that the entries need to be re-rendered due to the sorting.
     * Not recommended for external use!
     */
    public void sortColumn(int col, boolean ascending)
    {
        SortedTableModel<T> model = getModel();

        // get the list of selected items
        ArrayList<T> list = getListOfSelectedItems();
        clearSelection();

        // sort this column
        model.sortModelColumn( col, ascending );

        // reselect the selected items
        setSelectedItems( list );
    }

    /**
     * Re-applies the user's current sorting.
     */
    public void resortTable()
    {
        sortColumn( sortedColumnIndex, sortedColumnAscending );
        getModel().tableEntriesChanged();
    }

    protected void setSelectedItems( List<T> items )
    {
        if( !(getModel() instanceof SortedTableModel<?> ))
            return;

        getSelectionModel().clearSelection();
        SortedTableModel<T> model = getModel();
        for( final T itm : items ) {
            final int rowIdx = model.indexOf(itm);
            if( rowIdx >= 0 ) {
                getSelectionModel().addSelectionInterval(rowIdx, rowIdx);
            }
        }
    }

    protected ArrayList<T> getListOfSelectedItems()
    {
        // build a list containing all selected items
        ArrayList<T> lst = new ArrayList<T>();
        if( !(getModel() instanceof SortedTableModel<?> ))
            return lst;

        SortedTableModel<T> model = (SortedTableModel<T>)getModel();
        int numberOfRows = getRowCount();
        int selectedRows[] = getSelectedRows();
        for( int x=0; x<selectedRows.length; x++ )
        {
            final int rowIdx = selectedRows[x];
            if( rowIdx >= numberOfRows ) { continue; } // paranoia
            lst.add( model.getRow( rowIdx ) );
        }
        return lst;
    }

    /**
     * This method sets a SortHeaderRenderer as the renderer of the headers
     * of all columns.
     *
     * The default renderer of the JTableHeader is not touched, because when
     * skinks are enabled, they change that renderer. In that case, the renderers
     * of the headers of the columns (SortHeaderRenderers) paint the
     * arrows (if necessary) and then call the JTableHeader default renderer (the
     * one put by the skin) for it to finish the job.
     */
    protected void initSortHeader() {
        Enumeration<TableColumn> enumeration = getColumnModel().getColumns();
        while (enumeration.hasMoreElements()) {
            TableColumn column = enumeration.nextElement();
            column.setHeaderRenderer(columnHeadersRenderer);
        }
        getTableHeader().addMouseListener(new HeaderMouseListener());
    }

    public int getSortedColumnIndex()
    {
        return sortedColumnIndex;
    }

    public boolean isSortedColumnAscending()
    {
        return sortedColumnAscending;
    }

    // used by TablePopupMenuMouseListener
    public SortedTable<T> instance()
    {
        return this;
    }
    
    @SuppressWarnings("unchecked")
	public SortedTableModel<T> getModel() {
    	return (SortedTableModel<T>) super.getModel();
    }
    
    public void setModel(TableModel tableModel) {
    	if( !(tableModel instanceof SortedTableModel<?>)) {
    		throw new IllegalArgumentException("TableModel must be of type SortedTableModel<?>");
    	}
    	super.setModel(tableModel);
    }
    

    // you can override this to be able to configure the default sort-order of clicked columns,
    // by checking the column number and returning true for ascending or false for descending.
    // this is used when the user clicks a different column to sort by something else. the user
    // expects logical default sorting behaviors, such as sorting dates descending.
    public boolean getColumnDefaultAscendingState(final int col) {
        return true;
    }

    class HeaderMouseListener implements MouseListener
    {
        public void mouseReleased(MouseEvent event) {}
        public void mousePressed(MouseEvent event) {}
        public void mouseClicked(MouseEvent event)
        {
            if( event.isPopupTrigger() == true )
                return; // dont allow right click sorts

            TableColumnModel colModel = getColumnModel();
            int index = colModel.getColumnIndexAtX(event.getX());
            int modelIndex = colModel.getColumn(index).getModelIndex();

            SortedTableModel<T> model = getModel();

            boolean isSortable = false;
            if( model != null && model.isSortable(modelIndex) )
                isSortable = true;
            if( isSortable )
            {
                // if the user clicks the same column then toggle ascending/descending,
                // if the user clicks another column then use that column's default order.
                sortedColumnAscending = ( modelIndex == sortedColumnIndex ? !sortedColumnAscending : getColumnDefaultAscendingState(modelIndex) );
                sortedColumnIndex = modelIndex;

                sortColumn(modelIndex, sortedColumnAscending);
            }
        }
        public void mouseEntered(MouseEvent event) {}
        public void mouseExited(MouseEvent event) {}
    }
    
    public void removeSelected() {
		final int[] selectedRows = getSelectedRows();

		if( selectedRows.length > 0 ) {
			SortedTableModel<T> sortedTableModel = getModel();
			final int rowCount = sortedTableModel.getRowCount();
			for( int z = selectedRows.length - 1; z > -1; z-- ) {
				final int rowIx = selectedRows[z];

				if( rowIx >= rowCount ) {
					continue; // paranoia
				}

				sortedTableModel.deleteRow( rowIx );
			}
			clearSelection();
		}
	}
    
    public void removeButSelected() {
		final int[] selectedRows = getSelectedRows();

		if( selectedRows.length > 0 ) {
			// Sort - needed for binary search!
			java.util.Arrays.sort( selectedRows );
			SortedTableModel<T> sortedTableModel = getModel();
			
			// Traverse all entries and look if they are not in the list of selected items
			for( int z = sortedTableModel.getRowCount() - 1 ; z > -1 ; z--) {
				if( java.util.Arrays.binarySearch(selectedRows, z) < 0) {
					sortedTableModel.deleteRow(z);
				}
			}
		}
	}

    abstract protected class SelectedItemsAction {
        abstract protected void action(T t);

        public SelectedItemsAction() {
            iterateSelectedItems();
        }

        private void iterateSelectedItems() {
            // we must first make a list of all objects, since modifying them may change the sorting
            // and row numbers of the subsequent items otherwise, which means we'd target the wrong items.
            final List<T> selObjects = getListOfSelectedItems();
            // now just perform the actions on each object and repaint the table (doesn't clear their
            // selection, so you may want to fix their row selection manually).
            if( selObjects.size() > 0 ) {
                for( final T obj : selObjects ) {
                    action(obj);
                }
                repaint();
            }
        }
    }
}

