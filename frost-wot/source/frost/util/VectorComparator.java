/*
 VectorComparator.java / Frost-Next
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

package frost.util;

import java.util.Comparator;

/**
 * This class implements a comparator for Vector containers,
 * with generic type support and optional reverse ordering.
 * The input type (or one of its supertypes) must implement
 * the Comparable interface for this class to accept it.
 */
public class VectorComparator<T extends Comparable<? super T>>
    implements Comparator<T>
{
    protected boolean fSortReverse;

    /**
     * @param {boolean} aSortReverse - true ONLY if you want to invert
     * this comparator. you almost *never* want to do that!
     */
    public VectorComparator(
            final boolean aSortReverse)
    {
        fSortReverse = aSortReverse;
    }

    public int compare(
            T o1,
            T o2)
    {
        // null objects are sorted higher (later/further down) than non-nulls
        int result;
        if( o1 == o2 ) { result = 0; } // compare object reference (memory address)
        else if( o1 == null ) { result = 1; }
        else if( o2 == null ) { result = -1; }
        else { // two non-null objects; compare using their own comparator
            result = o1.compareTo(o2);
        }

        // if they want a reverse/descending sort, we'll invert the result
        if( fSortReverse ) {
            result = -result;
        }

        return result;
    }
}
