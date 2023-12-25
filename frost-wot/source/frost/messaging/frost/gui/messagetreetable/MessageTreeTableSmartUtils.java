/*
 MessageTreeTableSmartUtils.java / Frost-Next
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

package frost.messaging.frost.gui.messagetreetable;

import java.util.Enumeration;

import javax.swing.JTable;
import javax.swing.JTree;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import frost.util.gui.SmartSelection;

public class MessageTreeTableSmartUtils
{
    /**
     * Calculates the total count of visible descendants at any level under the given parent path.
     * If "parent" is not currently expanded or has no children, this will return 0.
     * If you expand/collapse nodes while this is processing, then the count will not be correct.
     * Therefore you should always run this in the GUI thread so that the user cannot affect the state.
     * ALSO NOTE: Checking expansion state is a simple tree node traversal and is doable at *any* time,
     * even when the JTree hasn't yet given a row to the object.
     * @param {JTree} tree - the JTree component
     * @param {TreePath} parentPath - the JTree node for which you want to calculate descendants
     */
    public static int getVisibleDescendantsCount(
            final JTree tree,
            final TreePath parentPath)
    {
        // if the parent itself can't have any visible descendants (because the parent is collapsed
        // or within a collapsed path), then return 0 instantly (saves some processing cycles)
        if( !tree.isExpanded(parentPath) ) {
            return 0;
        }

        // begin by adding the children of the parent
        final DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode)parentPath.getLastPathComponent();
        int visibleDescendantsCount = parentNode.getChildCount();
        if( visibleDescendantsCount == 0 ) {
            return 0;
        }

        // check all expanded descendants at any depth under the parent node, and count their children
        // NOTE: this only looks at expanded descendants, meaning ones where the whole path to that
        // descendant is expanded, so it properly avoids any expanded descendants under collapsed paths.
        final Enumeration<TreePath> expandedDescendants = tree.getExpandedDescendants(parentPath);
        // NOTE: only null if the parent itself is collapsed, but won't happen since isExpanded() is checked above
        if( expandedDescendants != null ) {
            while( expandedDescendants.hasMoreElements() ) {
                // advance the iterator and grab the next expanded descendant node
                final DefaultMutableTreeNode n = (DefaultMutableTreeNode)(expandedDescendants.nextElement()).getLastPathComponent();
                // IMPORTANT NOTE: some implementations of Java are bugged and include the parent in
                // its own list of "expanded descendants", so in that case we ignore it since we've
                // already counted the parent's children.
                if( n.equals(parentNode) ) { continue; }
                // count the children of this expanded descendant
                visibleDescendantsCount += n.getChildCount();
            }
        }
        return visibleDescendantsCount;
    }

    /**
     * High-level helper function which uses features of the SmartSelection class to automatically
     * update the MessageTreeTable and intelligently preserve the user's exact message selection.
     * WARNING: This helper assumes there's a 1:1 mapping between model rows and table rows. That's
     * true for *every* table in Frost, which is why it doesn't waste time converting view-indices.
     * SERIOUS WARNING: You *MUST* call this function from the main GUI thread, at a point when the
     * JTree is ready to respond about the newly added rows. Basically, use invokeLater when the
     * tree grows so that the table won't insert rows that the tree isn't ready to talk about.
     * @param {JTable} table - the table component of the treetable
     * @param {AbstractTableModel} tableModel - the *table* (not tree) model of the treetable
     * @param {int} insertFromRow - the *table* row where the insertion began
     * @param {int} insertToRow - the *table* row where the insertion ended; same as From if only 1
     * row was inserted. these two parameters work the same way as Java's regular "fireTableRowsInserted()".
     */
    public static void smartFireTreeTableRowsInserted(
            final JTable table,
            final AbstractTableModel tableModel,
            final int insertFromRow,
            final int insertToRow)
    {
        // IMPORTANT NOTE: "smartFire" is triggered during JTree row insertion and expansion, and is
        // running on a deferred timer in both cases, so there will *often* be a desync between
        // JTree<->JTable row numbers; or in other words what the JTree thinks is selected and what
        // the JTable thinks is selected. that's just an unfortunate side-effect of the awful
        // "TreeTable" implementation used by Frost. we therefore *cannot* use methods like
        // "tree.getSelectedPaths()" or even "tree.getPathForRow()". instead, we have to work on pure
        // JTable row numbers when calculating whether the JTable correctly applied the new selection.

        // get the user's currently selected *table* rows. note that we're being called *after* the
        // new item(s) have been added to the *tree* but *before* the *table*'s "fireTableRowsInserted()",
        // so the table still has the old items and selection interval. we can therefore safely retrieve
        // the user's "current" selection interval even if the new insertion would conflict with it.
        final int[] selectedRows = table.getSelectedRows(); // empty array in case of no selection

        // if the user has a single row selected, then we don't want to risk Java's dumb
        // "fireTableRowsInserted()" causing the selection to grow and thereby needlessly triggering
        // a GUI event for an increased selection. the user may have selected a single row in the
        // message tree table; and a growth of the selection would cause the single message to be
        // unloaded since the GUI would think the user intentionally selected multiple messages.
        // sure, we'd of course re-select the single row again via our final selection correction,
        // but the message-body scrollbar position would already have been lost. so if there's only
        // one row selected, we'll temporarily force Java into "single line" mode if it isn't already;
        // which means that the selection will be properly re-calculated on its own internally in
        // Java (it won't stupidly be able to grow the selection in single-line mode), and therefore
        // won't cause the current message to de-select/re-select itself.
        //
        // NOTE: in single line mode, the currently single selected line is always maintained by Java
        // via the correct "fireTableRowsInserted()" call below, but in multiline mode, there are 2 cases
        // where Java's selection will always be wrong: 1) if the inserted node(s) happen directly
        // above the currently selected message. in that case, "fireTableRowsInserted()" expands its
        // selection. 2) same thing happens if rows are inserted within the current selection range,
        // but that's a separate issue and will be corrected via the regular selection restoration code.
        boolean usedSelectionTrick = false;
        int oldSelectionMode = -1;
        if( selectedRows.length == 1 ) {
            oldSelectionMode = table.getSelectionModel().getSelectionMode();
            if( oldSelectionMode != ListSelectionModel.SINGLE_SELECTION ) {
                table.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
                usedSelectionTrick = true;
            }
        }

        // now send the "row(s) added" event to the JTable and any other listeners, to let it know
        // that the JTree has grown and that it should visually paint the new row(s).
        // NOTE: this causes the user's current selection to be updated to what Java *thinks* is the
        // correct new range, but if the insertion happens between or *exactly before* the currently
        // selected items, it actually ends up selecting the new item(s) too; we don't care, though,
        // since we're going to manually re-build the *real* selection in that case.
        // IMPORTANT: we *MUST* tell Java the total range of rows inserted (so from -> to), for it
        // to properly maintain the selection.
        tableModel.fireTableRowsInserted(insertFromRow, insertToRow);

        // restore the previous selection mode, if the trick was used
        if( usedSelectionTrick ) {
            table.getSelectionModel().setSelectionMode(oldSelectionMode);
        }

        // if he user had a selection, then we must now check if Java has preserved it correctly
        if( selectedRows.length > 0 ) {
            // now re-calculate what table rows *should* be selected at this time, by adjusting for
            // the fact that the selected rows after/at the insertion point will have changed location;
            // we shift them by the total number of rows inserted. this call directly edits "selectedRows".
            SmartSelection.recalculateSelectionAfterContiguousInsertion(selectedRows, insertFromRow, (1 + (insertToRow - insertFromRow)));

            // if Java's own re-calculated selection is already correct, then we don't want to waste
            // CPU cycles re-applying the same selection. especially not if a single row was
            // selected, in which case a re-selection would trigger a GUI event needlessly and would
            // cause things like the currently selected message row to be reloaded (in the case of
            // the message table). so let's check Java's automatically adjusted selection for the
            // table, and see if it's already correct, so that we can avoid firing selection events!
            //
            // IMPORTANT: Java *instantly* adds the rows to the JTable during "fireTableRowsInserted()",
            // so its own perceived selection was *instantly* updated and can now be checked.
            boolean alreadyCorrect = true;
            final int[] autoSelection = table.getSelectedRows();
            if( autoSelection.length != selectedRows.length ) {
                // different number of rows selected, so Java hasn't done the right thing
                alreadyCorrect = false;
            } else {
                // the same number of rows are still selected, so we must now check if they're
                // all correct or if they need updating.
                // NOTE: in case of single-line selection mode, or 1-length multi-selections
                // temporarily forced to single via the trick, Java has done the right thing
                for( int i=0; i<selectedRows.length; ++i ) {
                    // indices are *always* returned in ascending order, so we check i:i
                    if( autoSelection[i] != selectedRows[i] ) {
                        alreadyCorrect = false;
                        break;
                    }
                }
            }

            // now just apply the re-calculated selection again, if we need to correct it
            // NOTE: the JTable.getRowCount() part of the TreeTable internally returns tree.getRowCount(),
            // so we will never get any "row index out of bounds" issues even if we select the final
            // row. that's guaranteed, since we're running in a deferred manner when the JTree has
            // definitely already reacted to the new insertion. and since we never do TreeTable row
            // deletions in Frost, we don't even have to worry about getRowCount() shrinking before we
            // process the event. whatever our recalculated selection is, it'll be within legal JTree
            // boundaries, guaranteed!
            if( !alreadyCorrect ) {
                SmartSelection.applySmartSelection(table, selectedRows);
            }
        }
    }
}
