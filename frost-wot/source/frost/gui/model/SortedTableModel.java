/*
  SortedTableModel.java / Frost
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
package frost.gui.model;

import java.util.*;
import java.util.logging.*;

import javax.swing.table.*;

import frost.gui.*;

/**
 * This implementation is thread-safe. All methods that manipulate the data
 * are synchronized on the "LOCK" object, which ensures that only a single
 * thread can read/write to the model at a time. Basically, anything that reads
 * or manipulates the model and isn't marked "private" uses synchronization.
 * We also use the "volatile" keyword which allows concurrent access to
 * primitives without requiring us to do any regular locking.
 * You don't need to know any of these details to use the class. They're just
 * mentioned here for future reference.
 */
@SuppressWarnings("serial")
public class SortedTableModel<T extends TableMember<T>> extends DefaultTableModel
{
    private static final Object LOCK = new Object();

    private static final Logger logger = Logger.getLogger(SortedTableModel.class.getName());

    private volatile boolean bWasResized = false;
    private final ArrayList<T> rows; // model-data
    private SortedTable<T> parentTable = null;

    // we always need to hold the actual sorting comparator to allow sorted insertion
    private ColumnComparator colComparator  = new ColumnComparator(0, true); // default

    public SortedTableModel()
    {
        super();
        rows = new ArrayList<T>();
    }

    public void setParentTable(SortedTable<T> t)
    {
        this.parentTable = t;
    }

    public boolean isSortable(int col)
    {
        return true;
    }

    @Override
    public int getRowCount()
    {
        // NOTE: the super() constructor will call this before we've
        // assigned "rows" or even the LOCK, so we must check null!
        if( rows == null ) { return 0; }
        synchronized(LOCK) {
            return rows.size();
        }
    }

    public void sortModelColumn(int col, boolean ascending)
    {
        synchronized(LOCK) {
            sortColumn(col,ascending);
        }
    }
    private void sortColumn(int col, boolean ascending)
    {
        // sort this column
        colComparator = new ColumnComparator(col, ascending);
        if( rows.size() > 1 )
        {
            Collections.sort(rows, colComparator);
        }
    }

    public class ColumnComparator implements Comparator<T>
    {
        protected int index;
        protected boolean ascending;

        public ColumnComparator(int index, boolean ascending)
        {
            this.index = index;
            this.ascending = ascending;
        }

        // uses implementation in ITableMember.BaseTableMember or default impl. in abstracttreemodel
        public int compare(T tableMember1, T tableMember2)
        {
            // this is the FIRST stage of the sort where we compare the table rows (members);
            // but we then call their internal TableMember.compareTo method to compare them to each other
            // that method is in either source/frost/gui/model/TableMember.java, or in the table's overriding subclass.
            try {
                // null row objects are sorted higher (later/further down) than non-nulls
                int result;
                if( tableMember1 == tableMember2 ) { result = 0; } // compare object reference (memory address)
                else if( tableMember1 == null ) { result = 1; }
                else if( tableMember2 == null ) { result = -1; }
                else { // two non-null objects; compare using their own column comparator
                    result = tableMember1.compareTo(tableMember2, index);
                }

                // if they want a reverse/descending sort, we'll invert the result
                if( !ascending ) {
                    result = -result;
                }

                return result;
            } catch( final Exception ex ) {} // should never happen, so swallow it
            return -1; // fallback: "member 1 is more important than member 2"; used if the compareTo threw exception
        }
    }

    /**
     * Adds a new row to this model. Updates display of this table. Row will be inserted sorted
     * if set by constructor or <I>setSortingColumn</I>.
     *
     * @see #setSortingColumn
     */
    public void addRow(T member)
    {
        synchronized(LOCK) {
            // compute pos to insert and insert node sorted into table
            int insertPos = Collections.binarySearch(rows, member, colComparator);
            if( insertPos < 0 )
            {
                // compute insertion pos
                insertPos = (insertPos+1)*-1;
            }
            else
            {
                // if such an item is already contained in search column,
                // determine last element and insert after
                insertPos = Collections.lastIndexOfSubList(rows, Collections.singletonList(rows.get(insertPos)));
                insertPos++; // insert AFTER last
            }
            insertRowAt(member, insertPos);
        }
    }

    public void insertRowAt(T member, int index)
    {
        synchronized(LOCK) {
            if (index <= rows.size()) {
                rows.add(index, member);
                fireTableRowsInserted(index,index);
            }
        }
    }

    /**
     * Deletes the passed object obj.
     *
     * @param obj instance of ITableMember
     */
    public void deleteRow(T obj)
    {
        synchronized(LOCK) {
            if (obj != null) {
                int i = rows.indexOf(obj);
                if( i >= 0 ) { // -1 means doesn't exist in model; >= 0 means exists in model
                    rows.remove(obj);
                    fireTableRowsDeleted(i,i);
                }
            }
        }
    }
    
    /**
     * Deletes the the object at position rowNumber
     *
     * @param row row number
     */
    public void deleteRow(int row) {
        synchronized(LOCK) {
            if( row < 0 || row >= rows.size()) {
                return;
            }

            rows.remove(row);
            fireTableRowsDeleted(row,row);
        }
    }
    
    /**
     * Updates the passed object obj.
     *
     * @param obj instance of ITableMember
     */
    public void updateRow(T obj)
    {
        synchronized(LOCK) {
            if (obj!=null) {
                int i = rows.indexOf(obj);
                if( i >= 0 ) { // -1 means doesn't exist in model; >= 0 means exists in model
                    fireTableRowsUpdated(i,i);
                    resortTable();
                }
            }
        }
    }

    /**
     * Returns the index in this model of the first occurrence of the specified
     * item, or -1 if this model does not contain this element.
     *
     * @param obj - item to search for.
     * @return - the index in this model of the first occurrence of the specified
     * item, or -1 if this model does not contain this element.
     */
    public int indexOf(final T obj) {
        synchronized(LOCK) {
            return rows.indexOf(obj);
        }
    }

    private void resortTable()
    {
        if( parentTable != null )
        {
            parentTable.resortTable();
        }
    }

    /**
     * Returns the row at index <I>row</I>.
     *
     * @param row Index of row
     * @return Instance of ITableMember at index row. <I>null</I> if index contains
     * no ITableMember
     */
    public T getRow(int row)
    {
        synchronized(LOCK) {
            if (row<getRowCount()) {
                return rows.get(row);
            }
            return null;
        }
    }

    /**
     * Removes the row at index <I>row</I>.
     *
     * @param row Index of row
     * @return Instance of ITableMember at index row. <I>null</I> if index contains
     * no ITableMember
     */
    @Override
    public void removeRow(int row)
    {
        synchronized(LOCK) {
            if (row<getRowCount()) {
                T obj = rows.get(row);
                if (obj instanceof TableMember) {
                    deleteRow((T)obj);
                }
            }
        }
    }

    /**
     * Returns the value at <I>column</I> and <I>row</I>. Used by JTable.
     *
     * @param row Row for which the value will be returned.
     * @param column Column for which the value will be returned.
     * @return Value at <I>column</I> and <I>row</I>
     */
    @Override
    public Comparable<?> getValueAt(int row, int column)
    {
        synchronized(LOCK) {
            if (row>=getRowCount() || row<0) { return null; }

            T obj = (T)rows.get(row);
            if (obj == null) {
                return null;
            } else {
                return obj.getValueAt(column);
            }
        }
    }

    /**
     * Clears this data model.
     */
    public void clearDataModel()
    {
        synchronized(LOCK) {
            int size = rows.size();
            if (size>0) {
                rows.clear();
                fireTableRowsDeleted(0,size);
            }
        }
    }

    /**
     * Indicates that the whole table should be repainted.
     */
    public void tableEntriesChanged()
    {
        synchronized(LOCK) {
            fireTableDataChanged();
        }
    }

    /**
     * Indicates that the rows <I>from</I> to <I>to</I> should be repainted.
     *
     * @param from first line that was changed
     * @param to last line that was changed
     */
    public void tableEntriesChanged(int from, int to)
    {
        synchronized(LOCK) {
            final int rowCount = getRowCount();
            if( from >= rowCount ) { from = rowCount - 1; }
            if( to >= rowCount ) { to = rowCount - 1; }
            fireTableRowsUpdated(from,to);
        }
    }

    /**
     * Sets the value at <I>row</I> and <I>column</I> to <I>aValue</I>. This method
     * calls the <I>setValueAt</I> method of the <I>ITableMember</I> of row <I>row</I>.o
     *
     * @param aValue the new value
     * @param row Row for which the value will be changed.
     * @param column Column for which the value will be changed.
     */
    @Override
    public void setValueAt(Object aValue, int row, int column)
    {
        synchronized(LOCK) {
            System.out.println("setValueAt() - ERROR: NOT IMPLEMENTED"); // warn the programmer
            logger.severe("setValueAt() - ERROR: NOT IMPLEMENTED");
        }
    }

    /**
     * Returns true if this tableModel was resized by @see Utilities#sizeColumnWidthsToMaxMember
     *
     * return true if was resized otherwise false
     */
     public boolean wasResized()
     {
         return bWasResized;
     }

    /**
     * Sets that this model was resized or not
     *
     * @param newValue new value to be set
     */
    public void setResized(boolean newValue)
    {
        bWasResized = newValue;
    }
}


