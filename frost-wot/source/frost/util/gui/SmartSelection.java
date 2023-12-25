/*
 SmartSelection.java / Frost-Next
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;
import java.util.Arrays;

import frost.util.Mixed;

public final class SmartSelection
{
    /**
     * Selects all indexes given in the input array, as long as the table supports that many
     * simultaneous selections. Otherwise it does the best job it can do given the limitations
     * of the current table's selection mode.
     * WARNING: It is up to the *caller* to ensure that the row indexes are all valid and within
     * range. The best way to ensure that is to run all row calculations in the AWT GUI thread and
     * then call this function from the same thread, without any interruptions inbetween.
     * NOTE: If you're trying to restore the selection after *adding* something to the table, then
     * be sure to restore it *after* anything that fires "fireTableRowsInserted", otherwise the table
     * will try to adjust your corrected offsets and will select the wrong thing. This caveat doesn't
     * apply to fireTableRowsUpdated() which doesn't change the selection at all (so it's safe to
     * set the selection before the call to that function), or fireTableRowsDeleted which automatically
     * adjusts the selection to remove the deleted item and correct everything else (so no manual
     * restoration is necessary).
     * @param {JTable} table - the table where you want to select the rows
     * @param {int[]} newSelection - an array of rows to select
     */
    public static void applySmartSelection(
            final JTable table,
            final int[] newSelection)
    {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                // first clear the old selection in the table, in case it's not already clear
                table.clearSelection();

                if( newSelection.length > 0 ) {
                    // if the given array of selection indices is a jumbled mess, the job of selecting
                    // would be much slower (since it would select individual rows at a time), so first
                    // we must order the desired selection in ascending numerical order to optimize it
                    Arrays.sort(newSelection);

                    // now we must calculate and apply the new selection interval depending on the
                    // selection mode of the table
                    if(
                        // in this mode, there's no restriction on what intervals can be selected (you
                        // can have multiple intervals at different locations)
                        // NOTE: this mode is the *default* mode for all JTables, and the "SortedTable"
                        // implementation in Frost didn't expose any feature for changing mode in the past,
                        // which means that all tables that use "extends SortedModel" will automatically
                        // use the multi-mode -- and since none of them use the new feature for changing
                        // mode, it means all of them run the multi-code below. the fact that all other
                        // selection modes are also supported here was just done for future-proofing.
                        table.getSelectionModel().getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
                        // in this mode, only one contiguous interval can be selected at a time, so we'll
                        // calculate and select the *longest* still-contiguous selection
                        || table.getSelectionModel().getSelectionMode() == ListSelectionModel.SINGLE_INTERVAL_SELECTION
                      ) {
                        // if we're running in "MULTIPLE_INTERVAL_SELECTION" mode, then we'll be doing the
                        // selections directly while analyzing the array
                        boolean multiIntervalSelection = ( table.getSelectionModel().getSelectionMode() == ListSelectionModel.MULTIPLE_INTERVAL_SELECTION ? true : false );

                        // in multi-mode, we'll want to start out by directly selecting the first row of the
                        // selection (everything else will be added onto that)
                        if( multiIntervalSelection ) {
                            table.setRowSelectionInterval(newSelection[0], newSelection[0]);
                        }

                        // now analyze the array to find all contiguous chunks
                        int currentContiguousStartRow = newSelection[0];
                        int currentContiguousEndRow = newSelection[0];
                        int longestContiguousStartRow = currentContiguousStartRow;
                        int longestContiguousEndRow = currentContiguousEndRow;
                        boolean brokenSequence = false;
                        for( int i=1; i<newSelection.length; ++i ) { // start checking from pos 1 since we already grabbed 0 above
                            final int currentRow = newSelection[i];

                            // check if the sequence is broken and update the length of the current sequence
                            if( currentRow == ( currentContiguousEndRow + 1 ) ) {
                                // the next expected row in the sequence has been found
                                currentContiguousEndRow = currentRow;
                                brokenSequence = false;
                            } else {
                                // the sequence has been broken, so the current contiguous chunk has ended
                                brokenSequence = true;
                            }

                            // if the sequence has been broken, or we're at the end of the array, we need
                            // to update the longest seen chunk
                            if( brokenSequence || (i + 1) >= newSelection.length ) {
                                if(
                                      ( currentContiguousEndRow - currentContiguousStartRow )
                                    > ( longestContiguousEndRow - longestContiguousStartRow )
                                  ) {
                                      longestContiguousStartRow = currentContiguousStartRow;
                                      longestContiguousEndRow = currentContiguousEndRow;
                                  }

                                // if this is the multi-select mode, we'll now want to add the current chunk
                                // to the selection before we proceed
                                if( multiIntervalSelection ) {
                                    // begin by adding the current sequence to the selection
                                    table.addRowSelectionInterval(currentContiguousStartRow, currentContiguousEndRow);

                                    // if this was a broken sequence and we're at the end of the array, then
                                    // select the final "solo" row too. this is necessary because a *single*
                                    // non-contiguous row at the end of the array will trigger the "broken
                                    // sequence" but won't have time to build a new "currentContiguous[Start/End]Row"
                                    // interval, so we must select it independently
                                    if( brokenSequence && (i + 1) >= newSelection.length ) {
                                        table.addRowSelectionInterval(currentRow, currentRow);
                                    }
                                }
                            }

                            // if this is a broken sequence, then start a new contiguous chunk with the
                            // new sequence we just encountered
                            if( brokenSequence ) {
                                currentContiguousStartRow = currentRow;
                                currentContiguousEndRow = currentRow;
                            }
                        }

                        // if we're in "SINGLE_INTERVAL_SELECTION" mode, then just select the longest
                        // continuous interval now
                        if( !multiIntervalSelection ) {
                            // we've calculated the longest contiguous span of rows, so now just select that
                            // chunk. this skips any spill before/after this chunk, since the selection mode
                            // only supports a single continuous chunk
                            table.setRowSelectionInterval(longestContiguousStartRow, longestContiguousEndRow);
                        }
                      }
                    // in the final mode, "SINGLE_SELECTION", only a single index can be selected at a time
                    else {
                        // just select the first index from the previous selection, since it'll only contain a single row
                        table.setRowSelectionInterval(newSelection[0], newSelection[0]);
                    }
                }
            }
        });
    }

    /**
     * Re-calculates an array of selection indices by taking into account a *contiguous* insertion
     * of rows at any point in the table. The input array is directly modified.
     * NOTE/TODO/FIXME: It would be possible to write a "non-contiguous" version too, but nothing
     * in Frost currently needs something as advanced as that! So this is ONLY for *contiguous*!
     * @param {int[]} selection - an array of one or more integers representing the old selection
     * @param {int} insertFromRow - the row where the insertion began
     * @param {int} insertionCount - how many rows were inserted at the fromRow location; usually
     * just "1" if only a single row was inserted, but may be any positive number from 1 and up.
     */
    public static void recalculateSelectionAfterContiguousInsertion(
            final int[] selection,
            final int insertFromRow,
            final int insertionCount)
    {
        if( selection == null || selection.length <= 0 ) { return; }
        if( insertionCount <= 0 ) { return; }

        // re-calculate the row selections to adjust for the inserted row locations
        for( int i=0; i<selection.length; ++i ) {
            int selectedRow = selection[i];
            // check if the insertion index shifted our desired selection index
            // NOTE: this is correct, because insertion shifts anything that was *at*/after the
            // inserted index location (if the row is identical, the new item takes over that row
            // and pushes down the original item). so we only care if the insertion happened
            // before/at the selected row.
            // if the new insertion location is *after* our current selected row then we don't care
            // since that doesn't shift anything.
            if( insertFromRow <= selectedRow ) {
                // since the selected row has been pushed down by the inserted row(s),
                // we must now add the number of inserted row(s) to our offset
                selectedRow += insertionCount;
            }

            // store the re-calculated selection
            selection[i] = selectedRow;
        }
    }

    /**
     * Intelligently scrolls to the desired row and the chosen gap. A positive gap will try to display
     * that many rows after the target row, and a negative gap will try to display that many rows
     * before the target row. If the table isn't tall enough to display that many "gap" rows, then
     * it scrolls the gap to ensure the target is in view.
     * @param {JTable} table - the table to perform the scroll within
     * @param {int} tableRowIdx - which row to scroll to
     * @param {int} preferredGap - how much gap to have; can be negative, 0 or positive (see description above)
     */
    public static void applySmartScroll(
            final JTable table,
            final int tableRowIdx,
            final int preferredGap)
    {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                // perform an intelligent scroll; if there's enough visible space in the table, it will scroll
                // the view so that the selected message has "preferredGap" other messages under it (if positive
                // gap) or before it (if negative gap). this looks less cramped and helps give the user some
                // padding to be able to see the rest of the context without needing to scroll manually).
                // we always finish with a scroll to the row itself, to ensure that the selected row is in
                // view even when the scroll is performed backwards (upwards) in the table. without that,
                // backwards jumps would only scroll to the "gap" location, and forwards jumps would risk
                // overshooting the row if the table isn't very tall.
                // NOTE: all of the "scrollRect..." calls are no-ops if the target rows are already visible
                // somewhere in view.
                int scrollToIdx = -1;
                if( preferredGap > 0 ) {
                    // scrolls to the desired gap after the target, or last X rows if target row is at/near bottom of table
                    scrollToIdx = tableRowIdx + Math.min( preferredGap, ( table.getRowCount() - 1 ) - tableRowIdx );
                } else if( preferredGap < 0 ) {
                    // scrolls to the desired gap before the target, or the first row if target row is at/near top of table
                    scrollToIdx = tableRowIdx + preferredGap; // performs subtraction since gap is negative
                    if( scrollToIdx < 0 ) { scrollToIdx = 0; }
                }
                // now scroll to the calculated gap location (does nothing if preferredGap was 0,
                // and is a no-op if scrollToIdx is already in view)
                if( scrollToIdx != -1 ) { table.scrollRectToVisible(table.getCellRect(scrollToIdx, 0, true)); }
                // finish with a scroll to the actual row to ensure that it's always in view (is
                // a no-op and won't shift scrollbar if already in view)
                if( tableRowIdx != scrollToIdx ) { table.scrollRectToVisible(table.getCellRect(tableRowIdx, 0, true)); }
            }
        });
    }
}
