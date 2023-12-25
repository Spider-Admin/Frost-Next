/*
  SortStateBean.java / Frost
  Copyright (C) 2006  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.freetalk.gui.messagetreetable;

import java.util.*;

import frost.messaging.freetalk.*;
import frost.util.Mixed;

public class FreetalkMessageTreeTableSortStateBean {

    private static boolean isThreaded;

    private final static int defaultSortedColumn = FreetalkMessageTreeTableModel.COLUMN_INDEX_DATE; // default: date
    private final static boolean defaultIsAscending = false; // default: descending

    private static int sortedColumn = FreetalkMessageTreeTableModel.COLUMN_INDEX_DATE; // default: date
    private static boolean isAscending = false; // default: descending

    public static boolean isAscending() {
        return isAscending;
    }
    public static void setAscending(final boolean isAscending) {
        FreetalkMessageTreeTableSortStateBean.isAscending = isAscending;
    }
    public static boolean isThreaded() {
        return isThreaded;
    }
    public static void setThreaded(final boolean isThreaded) {
        FreetalkMessageTreeTableSortStateBean.isThreaded = isThreaded;
    }
    public static int getSortedColumn() {
        return sortedColumn;
    }
    public static void setSortedColumn(final int sortedColumn) {
        FreetalkMessageTreeTableSortStateBean.sortedColumn = sortedColumn;
    }
    public static void setDefaults() {
        setSortedColumn(defaultSortedColumn);
        setAscending(defaultIsAscending);
    }

    public static Comparator<FreetalkMessage> getComparator(final int column, final boolean ascending) {
        if( ascending ) {
            return ascendingComparators[column];
        } else {
            return descendingComparators[column];
        }
    }

    // sorting for flat (non-threaded) view
    // NOTE: the date comparator is public so that the message objects can use it to sort their insert points in the model
    private static MessageColumnComparator flaggedComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.FLAGGED, true);
    private static MessageColumnComparator flaggedComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.FLAGGED, false);

    private static MessageColumnComparator starredComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.STARRED, true);
    private static MessageColumnComparator starredComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.STARRED, false);

    private static MessageColumnComparator subjectComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.SUBJECT, true);
    private static MessageColumnComparator subjectComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.SUBJECT, false);

    private static MessageColumnComparator fromComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.FROM, true);
    private static MessageColumnComparator fromComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.FROM, false);

    private static MessageColumnComparator trustStateComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.TRUSTSTATE, true);
    private static MessageColumnComparator trustStateComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.TRUSTSTATE, false);

    public static MessageColumnComparator dateComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.DATE, true);
    public static MessageColumnComparator dateComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.DATE, false);

    private static MessageColumnComparator junkComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.JUNK, true);
    private static MessageColumnComparator junkComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.JUNK, false);

    private static MessageColumnComparator indexComparatorAscending = new MessageColumnComparator(MessageColumnComparator.Type.INDEX, true);
    private static MessageColumnComparator indexComparatorDescending = new MessageColumnComparator(MessageColumnComparator.Type.INDEX, false);

    @SuppressWarnings("unchecked")
    private static Comparator<FreetalkMessage>[] ascendingComparators = new Comparator[] {
        flaggedComparatorAscending,
        starredComparatorAscending,
        subjectComparatorAscending,
        fromComparatorAscending,
        trustStateComparatorAscending,
        dateComparatorAscending,
        junkComparatorAscending,
        indexComparatorAscending
    };
    @SuppressWarnings("unchecked")
    private static Comparator<FreetalkMessage>[] descendingComparators = new Comparator[] {
        flaggedComparatorDescending,
        starredComparatorDescending,
        subjectComparatorDescending,
        fromComparatorDescending,
        trustStateComparatorDescending,
        dateComparatorDescending,
        junkComparatorDescending,
        indexComparatorDescending
    };
    // defines whether a column defaults to ascending (true) or descending (false) when clicked
    public static boolean[] columnDefaultAscendingStates = new boolean[] {
        false, //flagged (all boolean columns must use descending to show booleans at top)
        false, //starred (boolean)
        true, //subject
        true, //from
        true, //trustState
        false, //date (always sort the newest dates at the top by default)
        false, //junk (boolean)
        true, //index
    };

    private static class MessageColumnComparator
            implements Comparator<FreetalkMessage>
    {
        public static enum Type {
            // "String"-based columns
            SUBJECT(0), FROM(0), TRUSTSTATE(0),
            // "boolean"-based columns
            FLAGGED(1), STARRED(1), JUNK(1),
            // "long"-based columns
            DATE(2),
            // "int"-based columns
            INDEX(3);

            private final int fObjType;
            Type(int aObjType)
            {
                fObjType = aObjType;
            }

            /**
             * 0 = String, 1 = boolean, 2 = long, 3 = int
             */
            public int getObjType()
            {
                return fObjType;
            }
        }

        /* Instance variables */
        private Type fType;
        private boolean fAscending;

        /**
         * @param {Type} aType - the type of comparator column
         * @param {boolean} aAscending - set to false if you want descending order instead
         */
        public MessageColumnComparator(
                final Type aType,
                final boolean aAscending)
        {
            fType = aType;
            fAscending = aAscending;
        }

        /**
         * Compares two message objects.
         */
        public int compare(
                final FreetalkMessage msg1,
                final FreetalkMessage msg2)
        {
            int result = 0; // init to "both items are equal"

            // compare the messages using the desired column
            switch( fType.getObjType() ) {
                case 0: // "String"
                    // load the column-appropriate String values
                    String s1 = null, s2 = null;
                    switch( fType ) {
                        case SUBJECT:
                            s1 = msg1.getTitle(); s2 = msg2.getTitle();
                            // when comparing subjects, we ignore the "Re: " prefix
                            if( s1 != null && s1.indexOf("Re: ") == 0 ) { s1 = s1.substring(4); }
                            if( s2 != null && s2.indexOf("Re: ") == 0 ) { s2 = s2.substring(4); }
                            break;
                        case FROM:
                            s1 = msg1.getAuthor(); s2 = msg2.getAuthor();
                            break;
                        case TRUSTSTATE: // Signature
                            return 0;
                            /* COMMENTED OUT: FREETALK MESSAGES DO NOT HAVE TRUST STATE STRINGS
                            s1 = msg1.getMessageStatusString(); s2 = msg2.getMessageStatusString();
                            break;
                            */
                    }
                    // perform a case-insensitive String comparison with null-support
                    result = Mixed.compareStringWithNullSupport(s1, s2, /*ignoreCase=*/true);
                    break;
                case 1: // "boolean"
                    return 0;
                    /* COMMENTED OUT: FREETALK MESSAGES DO NOT HAVE FLAGGED/STARRED/JUNK STATES
                    // load the column-appropriate boolean values
                    boolean b1 = false, b2 = false;
                    switch( fType ) {
                        case FLAGGED:
                            b1 = msg1.isFlagged(); b2 = msg2.isFlagged();
                            break;
                        case STARRED:
                            b1 = msg1.isStarred(); b2 = msg2.isStarred();
                            break;
                        case JUNK:
                            b1 = msg1.isJunk(); b2 = msg2.isJunk();
                            break;
                    }
                    result = Mixed.compareBool(b1, b2);
                    break;
                    */
                case 2: // "long"
                    // load the column-appropriate long values
                    long l1 = 0L, l2 = 0L;
                    switch( fType ) {
                        case DATE:
                            l1 = msg1.getDateMillis(); l2 = msg2.getDateMillis();
                            break;
                    }
                    result = Mixed.compareLong(l1, l2);
                    break;
                case 3: // "int"
                    // load the column-appropriate int values
                    int i1 = 0, i2 = 0;
                    switch( fType ) {
                        case INDEX:
                            i1 = msg1.getMsgIndex(); i2 = msg2.getMsgIndex();
                            break;
                    }
                    result = Mixed.compareInt(i1, i2);
                    break;
            }

            // if the values are equal and this isn't the "Date" column, then use Date as tie-breaker
            if( result == 0 && fType != Type.DATE ) {
                // compare by Date using always-ascending order (by not inverting it)
                return Mixed.compareLong(msg1.getDateMillis(), msg2.getDateMillis());
            } else {
                // if they want a reverse/descending sort, we'll invert the result
                return ( fAscending ? result : -result );
            }
        }
    }
}
