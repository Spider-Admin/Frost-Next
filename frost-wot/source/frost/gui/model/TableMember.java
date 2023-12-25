/*
  TableMember.java / Frost
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

public interface TableMember<T extends TableMember<T>> {
    /**
     * Returns the object representing value of column. Can be string or icon
     *
     * @param   column  Column to be displayed
     * @return  Object representing table entry.
     */
    public Comparable<?> getValueAt(int column);

    public int compareTo(T otherTableMember, int tableColumnIndex );

    /**
     * Default implementation for comparing table members. You can override this in your own
     * table member implementation, for specific per-column sorting results. And then just call
     * this default implementation for any columns you don't want to override the behavior of.
     * Alternatively, if you don't want to inherit from BaseTableMember (for this default sorting
     * possibility), then you can just implement TableMember's compareTo instead.
     *
     * For examples of extending BaseTableMember to override certain columns and default others, see:
     * source/frost/gui/ManageTrackedDownloads.java:TrackedDownloadTableMember()
     *
     * For examples of implementing every column yourself, see:
     * source/frost/gui/IdentitiesBrowser.java:InnerTableMember()
     *
     * Regardless of which approach you choose, you always need to fully implement getValueAt().
     */
    abstract public class BaseTableMember<T extends BaseTableMember<T>> implements TableMember<T> {

        @SuppressWarnings("unchecked")
        public int compareTo(T otherTableMember, int tableColumnIndex) {
            assert tableColumnIndex >= 0;

            // if the other table member object is null, just return -1 instantly so that the non-null
            // member we're comparing will be sorted above the null-row. this is just a precaution.
            if( otherTableMember == null ) { return -1; }

            // get the values in the desired column for each row (the values may be null!)
            // NOTE: this gets the actual displayed value in the cell, which due to "getValueAt"
            // is very often going to be a string!
            final Comparable<?> val1 = getValueAt(tableColumnIndex);
            final Comparable<?> val2 = otherTableMember.getValueAt(tableColumnIndex);

            // null objects are sorted higher (later/further down) than non-nulls
            int result;
            if( val1 == val2 ) { result = 0; } // compare object reference (memory address)
            else if( val1 == null ) { result = 1; }
            else if( val2 == null ) { result = -1; }
            else { // two non-null objects; compare using their own type comparators
                if( val1.getClass() != val2.getClass() ) {
                    throw new ClassCastException("Column values not of same type");
                }
                // if the objects are Strings, do a case insensitive comparison
                if( val1 instanceof String ) {
                    result = ((String) val1).compareToIgnoreCase((String) val2);
                } else {
                    // otherwise, try to use the default comparator for that object type
                    // NOTE: this may fail but the exception will be caught by the table sorter that called us.
                    result = val1.getClass().cast(val1).compareTo(val1.getClass().cast(val2));
                }
            }

            return result;
        }
    }
}
