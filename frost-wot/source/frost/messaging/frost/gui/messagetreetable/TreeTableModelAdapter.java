/*
 TreeTableModelAdapter.java / Frost-Next
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

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreePath;

import frost.messaging.frost.gui.messagetreetable.MessageTreeTableSmartUtils;

/**
 * This wrapper class takes a TreeTableModel and implements the table model interface, to provide
 * the synchronization between the JTree and JTable portions of the GUI. All of the event
 * dispatching support is provided by the superclass: the AbstractTableModel.
 *
 * @author Philip Milne
 * @author Scott Violet
 * @author The Kitty (complete rewrite of the extremely buggy, original mess)
 */
@SuppressWarnings("serial")
public class TreeTableModelAdapter extends AbstractTableModel
{
    final JTree tree;
    final MessageTreeTable treeTable;
    final TreeTableModel treeTableModel;

    final Listener listener;
    volatile boolean isListenerActive = false;

    public TreeTableModelAdapter(
            final TreeTableModel treeTableModel,
            final JTree tree,
            final MessageTreeTable tt)
    {
        this.tree = tree;
        this.treeTable = tt;
        this.treeTableModel = treeTableModel;

        listener = new Listener();

        // install the listener as soon as this adapter is created
        addListener();
    }

    public boolean hasListener()
    {
        return isListenerActive;
    }

    public void addListener()
    {
        if( !isListenerActive ) {
            // Install a TreeWillExpandListener and a TreeExpansionListener which updates the JTable whenever JTree paths expand or collapse.
            tree.addTreeWillExpandListener(listener);
            tree.addTreeExpansionListener(listener);

            // Install a TreeModelListener which updates the JTable whenever the JTree gets new nodes or modifies existing nodes.
            treeTableModel.addTreeModelListener(listener);

            isListenerActive = true;
        }
    }

    // allows you to remove the JTable-synchronization listener while doing heavy work on the JTree.
    // just remember to fireTableDataChanged on the JTable to repaint it afterwards!
    public void removeListener()
    {
        if( isListenerActive ) {
            tree.removeTreeWillExpandListener(listener);
            tree.removeTreeExpansionListener(listener);
            treeTableModel.removeTreeModelListener(listener);
            isListenerActive = false;
        }
    }

    // Wrappers, implementing TableModel interface.

    @Override
    public int getColumnCount()
    {
        return treeTableModel.getColumnCount();
    }

    @Override
    public String getColumnName(
            final int column)
    {
        return treeTableModel.getColumnName(column);
    }

    @Override
    public Class<?> getColumnClass(
            final int column)
    {
        return treeTableModel.getColumnClass(column);
    }

    @Override
    public int getRowCount()
    {
        // NOTE: this cleverly overrides "table.getRowCount()" so that the JTable always thinks it
        // contains the exact same number of rows as the JTree. that's good, because it ensures that
        // things like "table.setRowSelectionInterval()" will always be able to use maximum indices
        // (such as new additions at the bottom of the JTree), without having to wait until the
        // table's own row count updates. it also ensures that painting of the JTable never goes
        // beyond the last index of the underlying JTree.
        return tree.getRowCount();
    }

    // not in supertype, so no override
    protected Object nodeForRow(
            final int row)
    {
        final TreePath treePath = tree.getPathForRow(row);
        if( treePath != null ) {
            return treePath.getLastPathComponent();
        } else {
            return null;
        }
    }

    @Override
    public Object getValueAt(
            final int row,
            final int column)
    {
        // NOTE: this is constantly called by the table and the table's list selection model, when
        // it needs to retrieve the value for a table row and what's in it... however, since the
        // JTable and JTree row numbers briefly de-sync whenever we update the JTable, you need to
        // be extremely careful in any "valueChanged()" calls in your JTable selection listener.
        // it will flutter rapidly with about 0-3ms between each call, until it finally settles.
        // the best suggestion is that whenever you use a TreeTable (such as this one), you should
        // wait until like 25ms *after* the *last* call to valueChanged() before using the value.
        // that will avoid the temporary "flickers" in value while the JTree and JTable are syncing.
        return treeTableModel.getValueAt(nodeForRow(row), column);
    }

    // not in supertype, so no override
    public Object getRow(
            final int row)
    {
        return nodeForRow(row);
    }

    @Override
    public boolean isCellEditable(
            final int row,
            final int column)
    {
        return treeTableModel.isCellEditable(nodeForRow(row), column);
    }

    @Override
    public void setValueAt(
            final Object value,
            final int row,
            final int column)
    {
        treeTableModel.setValueAt(value, nodeForRow(row), column);
    }

    /**
    * This is a listener which passes tree modification events between the JTree and
    * JTable of Frost's TreeTable implementation, so that they both stay in sync.
    */
    private class Listener
            implements TreeWillExpandListener, TreeExpansionListener, TreeModelListener
    {
        private int collapsedVisibleDescendantsCount = -1;

        public Listener() {}

        // A TreeWillExpandListener and a TreeExpansionListener which updates the JTable whenever JTree paths expand or collapse.
        //
        // ** TreeWillExpandListener **
        @Override
        public void treeWillCollapse(
                final TreeExpansionEvent e)
        throws ExpandVetoException
        {
            // this event fires on the GUI thread (since only the GUI user is capable of collapsing
            // nodes), at the point when the JTree begins to react to the collapsing of a node,
            // so we must process this event instantly.

            // NOTE: this event fires *immediately* before a "treeCollapsed" event, and lets us
            // analyze the number of visible descendants *before* the tree node is collapsed.
            // intercepting this event allows us to figure out how many rows *will* be hidden,
            // so that we know the number of rows to delete from the JTable when the JTree
            // has finished collapsing this path.
            // we don't do any deferred invokeLater processing, since we want the number of
            // visible descendants immediately before it's too late.

            // grab the about-to-be-collapsed node object and its path, and reset the count
            // if we were unable to fetch the node...
            final TreePath collapsedPath = e.getPath();
            if( collapsedPath == null ) { collapsedVisibleDescendantsCount = -1; return; }
            final DefaultMutableTreeNode collapsedNode = (DefaultMutableTreeNode)collapsedPath.getLastPathComponent();
            if( collapsedNode == null ) { collapsedVisibleDescendantsCount = -1; return; }

            // check the row number and abort if the collapsed path isn't within a currently
            // expanded (visible) tree path.
            // NOTE: this function is the same as treeTable.getRowForNode(), but doesn't
            // idiotically wrap the root node in a hardcoded "0".
            // NOTE: see "treeExpanded" event for why we're allowing "-1" in the case of root.
            final int collapsedRow = tree.getRowForPath(collapsedPath);
            if( collapsedRow < 0 && !collapsedNode.isRoot() ) {
                return;
            }

            // now calculate how many visible descendants are under the node that is about
            // to be collapsed.
            collapsedVisibleDescendantsCount = MessageTreeTableSmartUtils.getVisibleDescendantsCount(tree, collapsedPath);
//                System.out.println("treeWillCollapse, collapsedRow="+collapsedRow+(collapsedRow < 0 ? " (root)" : ""));
//                System.out.println("treeWillCollapse->visibleDescendants: "+collapsedVisibleDescendantsCount);
        }

        @Override
        public void treeWillExpand(
                final TreeExpansionEvent e)
        throws ExpandVetoException
        {
            // we don't need to do anything *before* a tree node expands, so this is left blank.
        }

        // ** TreeExpansionListener **
        @Override
        public void treeCollapsed(
                final TreeExpansionEvent e)
        {
            // this event fires on the GUI thread (since only the GUI user is capable of collapsing
            // nodes), after the JTree has already reacted, so we must *NOT* defer the JTable
            // update via invokeLater! 

            // this fires *immediately* after the "treeWillCollapse" event, and signals that
            // the collapse has taken place. so the first thing we should do is check if the
            // collapsed JTree node had *at least* 1 visible descendant. if not, we can abort
            // immediately, since there's nothing to remove from the JTable.
            if( collapsedVisibleDescendantsCount <= 0 ) {
                return;
            }

            // grab the collapsed node object and its path
            final TreePath collapsedPath = e.getPath();
            if( collapsedPath == null ) { return; }
            final DefaultMutableTreeNode collapsedNode = (DefaultMutableTreeNode)collapsedPath.getLastPathComponent();
            if( collapsedNode == null ) { return; }

            // check the row number and abort if the collapsed path isn't within a currently
            // collapsed (visible) tree path.
            // NOTE: this function is the same as treeTable.getRowForNode(), but doesn't
            // idiotically wrap the root node in a hardcoded "0".
            // NOTE: see "treeExpanded" event for why we're allowing "-1" in the case of root.
            final int collapsedRow = tree.getRowForPath(collapsedPath);
            if( collapsedRow < 0 && !collapsedNode.isRoot() ) {
                return;
            }

            // calculate the range of rows we'll need to delete from the JTable
            // NOTE: "from" is the first row below the collapsed row, and "to" is the same as
            // "from" plus the number of visible descendants minus 1, since we've already taken
            // care of the "first" visible descendant in the "from" range; so in the case of a
            // single descendant, we delete the row below the collapsed row. and in case of 3
            // descendants, we delete the 3 rows below the collapsed row, and so on...
            final int deleteFromRow = collapsedRow + 1;
            final int deleteToRow = deleteFromRow + (collapsedVisibleDescendantsCount - 1);

            // now instantly delete the collapsed rows from the JTable. do not defer this job.
//                System.out.println("treeCollapsed, collapsedRow="+collapsedRow+(collapsedRow < 0 ? " (root)" : "")+", deleteFromRow="+deleteFromRow+", deleteToRow="+deleteToRow);
//                System.out.println("treeCollapsed->visibleDescendants: "+collapsedVisibleDescendantsCount);
            fireTableRowsDeleted(deleteFromRow, deleteToRow);
        }

        @Override
        public void treeExpanded(
                final TreeExpansionEvent e)
        {
            // --------------------------------------------------------------------------------
            // See "messaging/frost/gui/messagetreetable/TreeTableModelAdapter.java:treeNodesInserted()"
            // for full details of what's going on here, and the meaning behind all of the maths.
            // --------------------------------------------------------------------------------

            // this event fires pretty much instantly after "expandPath()" is called, since it's
            // a simple node object expansion. it doesn't mean that the JTree is showing the new
            // nodes yet, and it doesn't mean that the parent itself has been drawn by the JTree
            // yet. so just like insertion, we must defer this job.
            //
            // EXTREMELY IMPORTANT: when Frost inserts a new node under an expanded parent, it
            // first notifies the tree via "treeNodesInserted()", and then tells the inserted
            // node to expand (if it had children of its own). the inserted node will instantly
            // expand (internally in the tree structure), via the "expandPath()" call. and the
            // job of expanding paths is a simple "internal node tree" job of setting each node
            // to "isExpanded". so when Frost does "insert, expand", what happens is that the
            // node is inserted into the JTree (which probably takes less than a millisecond),
            // and then the expansion is performed (which again takes less than a millisecond).
            // Because we're deferring our processing of the insertion job, we *need to* defer
            // our processing of the expansion job too. otherwise we'd process the expansion
            // before the insertion (which is what Frost used to wrongly do in the past), and
            // our JTable would briefly enter a non-sensical state. but we must cache the number
            // of descendants at the time of the expansion call, so that we don't risk *further*
            // insertions of messages under our node being counted twice (both during their own
            // "treeNodesInserted" event and during our deferred expansion handling). so that's
            // all we have to do: cache the number of visible descendants, but expand it later.
            // this ensures that we stay in sync between our insertion and expansion.
            //
            // this is the execution flow for adding a new message, and expanding a tree:
            // add() -> function begins
            // add() -> fires inserted
            // adapter -> insert received (instantly), defers the actual JTable insertion
            // add() -> performs expandPath()
            // adapter -> expand received (instantly), fired once for each child that was expanded,
            //   in a top-down fashion. we cache the number of visible descendants of each, which
            //   is always 1 since it's being opened top-down, and we defer the JTable insertion
            //   (where we'll be inserting one row at a time in a top-down fashion)
            // add() -> function ends (as you can see, the insert and all expands have fired!), so
            //   if any further add() calls are performed for other messages within the same tree,
            //   then *those* will see that their parent is already expanded and will simply fire a
            //   single "inserted" event.
            // finally, our deferred insert/expand handlers will fire and will insert the rows,
            //   and will query the latest row number at the time of insertion so that it's always
            //   100% correct. and as for multiple insertions within the same tree, it would be
            //   correctly handled since we cache the number of descendants, which means that we'll
            //   always know exactly the count at the moment that expandPath() was fired, and will
            //   insert the additional child on top of that *without* accidentally counting it twice!
            //
            // note that Java's JTree implementation doesn't care about the order in which you
            // expand rows. even though Frost performs an "expand all children, and then finally
            // the parent" order, Java itself waits until a VISIBLE tree is being expanded (the
            // parent), and then triggers the actual expansion *and* "treeExpanded" events in
            // a top-down fashion for each level that became expanded, and in fact *doesn't*
            // expand the "already expanded" children UNTIL it's their turn in the hierarchy of
            // the expansion queue. so in the case of a node that has 2 children, and its first
            // child had 3 children of its own, Frost will tell the first child to expand, and
            // will then tell the parent to expand.
            // Java itself will first trigger a "treeExpanded" for the PARENT, at which point in
            // time its child has not yet been expanded (despite Frost "expanding" the child first).
            // this can be verified by seeing that "visibleDescendantsCount" of the parent will equal
            // "getChildCount()" even though there are more levels of descendants below its top-level
            // children. this means that the first child, which Frost *tried* to expand first,
            // has actually not been expanded yet. Next, Java will fire a "treeExpanded" event
            // for the first child, which will see its own 3 children (the 3 extra descendants
            // of the top-level parents). this top-down opening of a tree means that the deepest
            // descendants are *always expanded last*. you may then think "okay, so why not just
            // always getChildCount() instead of visibleDescendantsCount?", but we can't do that,
            // because when we *later* close and open some part of an ALREADY EXPANDED tree
            // hierarchy, then Java obviously won't have to collapse/expand the already-expanded
            // descendants, so at that point in time we need the actual visibleDescendantsCount.
            // so for that reason, we always use visibleDescendantCounts. during initial programmatic
            // expansion it means that we get a top-down expansion of all children. during later
            // collapsing and expansion of a single tree node, all of its descendants will *stay*
            // open/closed and will simply be hidden/shown all at once in a big chunk.

            // grab the expanded node object and its path
            final TreePath expandedPath = e.getPath();
            if( expandedPath == null ) { return; }
            final DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode)expandedPath.getLastPathComponent();
            if( expandedNode == null ) { return; }

            // if there are no children of this expanded node, then we definitely won't have to
            // fire any "insertion" event and can just abort instantly.
            if( expandedNode.getChildCount() <= 0 ) {
                return;
            }

            // now calculate how many visible descendants are under the node that has been
            // expanded. we need to grab this number *now* before we defer the JTable insertion,
            // otherwise further child insertions under this node would be counted both during
            // their own "treeNodesInserted" event AND during our deferred child counting.
            // also note the "treeNodesInserted()" comments about why we're handling
            // initially inserted, expanded rows here instead of there. basically it's because
            // we can't prevent the "expansion" event from firing even though our deferred
            // insertion event can also see the expanded nodes by the time it fires. so we will
            // *only* handle expanded rows here in order to prevent registering each child twice.
            //
            // NOTE: since the expansion event fires pretty much *immediately* when expandPath()
            // is called, we're guaranteed that this count will be taken *before* any further
            // "add()" calls for further messages, thus avoiding all race conditions with extra msgs.
            final int visibleDescendantsCount = MessageTreeTableSmartUtils.getVisibleDescendantsCount(tree, expandedPath);

            // if the expanded node doesn't have *at least* 1 visible descendant, then abort
            // since there will be no nodes to insert into the table...
            if( visibleDescendantsCount <= 0 ) {
                return;
            }

            // update the JTable in a deferred GUI job since the JTree may not have updated yet.
            // and more importantly, it ensures that our deferred "insert" job for a node triggers
            // before the "expand" job for its children, thus ensuring that we keep giving logical
            // row numbers to the JTable (i.e. "insert at row 0, (row 0 expansion) insert from row
            // 1 to 5", instead of "(row 0 expansion) insert from row 1 to 5, insert at row 0")
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    // check the row number and abort if the expanded path isn't (still) within
                    // a currently expanded (visible) tree path.
                    // NOTE: this function is the same as treeTable.getRowForNode(), but doesn't
                    // idiotically wrap the root node in a hardcoded "0".
                    // NOTE: if the node that expanded is the *root* node, the value will be "-1"
                    // (aka "invisible row"), since the hidden root is invisible. but that number
                    // is actually exactly what we *want* (since root is the first row before
                    // row 0). so treating root as "-1" will in turn correctly calculate its
                    // children as row -1 + 1 = 0, and so on. so if root is expanded (happens at
                    // first incoming msg of clear board), we'll *allow* a < 0 value.
                    final int expandedRow = tree.getRowForPath(expandedPath);
                    if( expandedRow < 0 && !expandedNode.isRoot() ) {
                        return;
                    }

                    // calculate the range of child rows (at all depth levels) that just became
                    // visible and now need to be inserted into the JTable.
                    // NOTE: "from" is the first row below the expanded row, and "to" is the
                    // same as "from" plus the number of visible descendants minus 1, since we've
                    // already taken care of the "first" visible descendant in the "from" range;
                    // so in the case of a single descendant, we insert the row below the expanded
                    // row. and in case of 3 descendants, we insert the 3 rows below the expanded
                    // row, and so on...
                    // NOTE: we use the *previously* calculated number of visible descendants,
                    // instead of the latest number, to avoid race conditions if multiple children
                    // are being inserted into us before expansion triggers. if we used the latest
                    // number we'd be counting them both here *and* during their own insertion
                    // event, which would cause extra, blank rows in the JTable.
                    final int insertFromRow = expandedRow + 1;
                    final int insertToRow = insertFromRow + (visibleDescendantsCount - 1);

                    // perform a very intelligent row update, which preserves the user's current selection
//                        System.out.println("treeExpanded, expandedRow="+expandedRow+(expandedRow < 0 ? " (root)" : "")+", insertFromRow="+insertFromRow+", insertToRow="+insertToRow);
//                        System.out.println("treeExpanded->visibleDescendants: "+visibleDescendantsCount);
                    MessageTreeTableSmartUtils.smartFireTreeTableRowsInserted(treeTable, TreeTableModelAdapter.this, insertFromRow, insertToRow);
                }
            });
        }

        // A TreeModelListener which updates the JTable whenever the JTree gets new nodes or modifies existing nodes.
        //
        // ** TreeModelListener **
        @Override
        public void treeNodesChanged(
                final TreeModelEvent e)
        {
            // --------------------------------------------------------------------------------
            // See "messaging/frost/gui/messagetreetable/TreeTableModelAdapter.java:treeNodesInserted()"
            // for full details of what's going on here, and the meaning behind all of the maths.
            // --------------------------------------------------------------------------------

            // update the JTable in a deferred GUI job since the JTree may not have re-rendered the updated row yet
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    // process *all* of the updated node objects from this update event
                    final Object[] changedNodes = e.getChildren();
                    if( changedNodes == null ) { return; }
                    for( int i=0; i<changedNodes.length; ++i ) {
                        // grab the current node object and its path
                        final DefaultMutableTreeNode thisNode = (DefaultMutableTreeNode)changedNodes[i];
                        if( thisNode == null ) { continue; }
                        final TreePath thisPath = new TreePath(thisNode.getPath());

                        // check the row number for the updated node in the tree, and abort if
                        // the node isn't within an expanded (visible) tree path.
                        // NOTE: this function is the same as treeTable.getRowForNode(), but
                        // doesn't idiotically wrap the root node in a hardcoded "0".
                        //
                        // NOTE: a "tree nodes changed" event means that a node has changed in
                        // some way, but that it *hasn't* changed location or changed the number
                        // of children/descendants, so we only need to calculate a single row.
                        int changedRow = tree.getRowForPath(thisPath);
                        if( changedRow < 0 ) {
                            return;
                        }

                        // perform a regular row update. this will never change the user's selection.
//                            System.out.println("treeNodesChanged, changedRow="+changedRow);
                        fireTableRowsUpdated(changedRow, changedRow);
                    }
                }
            });
        }

        @Override
        public void treeNodesInserted(
                final TreeModelEvent e)
        {
            // --------------------------------------------------------------------------------
            // Frost's event system is a mess when it comes to inserting table rows, so this
            // needs a looooong explanation... all deep documentation is in this function,
            // and all other functions refer here. so let's begin...
            //
            // the core issue is that Frost is using an old, buggy TreeTable implementation,
            // which is basically a hack published on a Java blog decades ago. it forces a
            // JTree into a JTable and needs to synchronize the two. if you don't know how
            // truly *awful* the TreeTable is, you just need to realize that the Eclipse IDE
            // used it in the past, and it was *the* #1 cause of bug reports in that entire,
            // massive application, until they decided to spend a few years rewriting it.
            // since I don't have time to convert Frost to Eclipse's improved TreeTable (Netbeans
            // OutlineView), I'll simply have to make Frost's old TreeTable behave instead!
            //
            // when these various "treeNodesInserted/Expanded/whatever" events fire, it actually
            // means that the *tree* has had a change applied to it. we then need to tell the
            // *table* component about the change to the tree. but by this point in time,
            // the tree itself *may not* have finished processing the event yet; in about 40%
            // of cases, it's not done processing yet. so we'll need to perform our table updates
            // on a new AWT GUI thread job (via invokeLater). but to avoid losing synchronization
            // of where the rows were inserted, we won't be able to trust the TreeModelEvent's
            // "getChildIndices()" array, since multiple model changes may happen rapidly,
            // before our scheduled GUI jobs have had a chance to process. so the solution is
            // to take the actual *nodes* that were changed (via "getChildren()") and calculate
            // their *actual* position in the JTree at the point in time when our invokeLater job
            // is being processed. this is safe against multiple add() events happening at the same
            // time, since we're processing all of them in the exact order that they were added
            // to the table, and since we're processing *all* of them, so no rows will be left
            // behind. as an example; two add() calls that both do "insert at row 5" would be
            // handled as "insert at 5" and "insert at 6", respectively, since we'll take the
            // final JTree row locations into account *after* all add() calls that have happened
            // before our deferred table update runs.
            //
            // NOTE: in an ideal world, we'd be able to *instantly* tell the JTable about the insertion
            // point. imagine that we get two "add()" calls, one which inserts at 57 and the other
            // at 5. if we instantly react, we'd insert JTable rows at 57 and at 5. but there are
            // problems with that. here are the THREE ways of synchronizing a JTree and JTable,
            // and which one was chosen:
            //
            // - Manually calculate row now (since JTree may not have finished inserting yet),
            //   and instantly tell the JTable about the insertion point:
            //   * We would tell the JTable that a row was inserted at 57, and then at 5. That's good.
            //   * When a row is inserted in the JTable, it will ask the JTree what item is at that
            //     row, and there's a TINY risk the JTree hasn't calculated the item's row # yet.
            //     That's VERY bad.
            //   * The risk of a race is low (since 60% of the time, the JTree already knows the
            //     row # by the time our treeNodesInserted()) fires, but a risk is still a risk,
            //     so we cannot use this method.
            // - Manually calculate the row now, and DEFER the JTable insertion:
            //   * We would insert at the exact row location where the insert happened in our JTree,
            //     so the user's current JTable row selection would stay correct. That's good.
            //   * By the time our deferred insertion fires, the row added at 57 would actually be
            //     at 58 in the JTree. But since we haven't pushed down/re-rendered the preceding
            //     JTable rows, we'd get a situation where row 57+ is re-rendered (thanks to the
            //     insertion), but 56 isn't re-rendered. And since the 2nd insertion happened at
            //     row 5 in the JTree, the item at 57 would actually be what's at 56 in the JTree,
            //     so we'd get a situation where 56 and 57 visually show the same item text. That's bad.
            //     But at least our row selection in the JTree would stay visually correct. That's good.
            //     But if we query the JTree what's at the row # that we've selected in the JTable,
            //     we'd get the wrong result since they're out of sync. So that's bad.
            //   * When the second deferred insertion fires, the row added at 5 in the JTree will
            //     cause our JTree to re-render all rows from 5+ and would retrieve the correct row
            //     display text. That's good.
            //     Our row selection in the JTree would stay visually correct. That's good.
            //     And since the final add() is done, we can safely query the JTree what's at our
            //     JTable row at this point in time.
            //   * Because this method inserts at the "correct" JTable location, we'll cause the
            //     user's currently selected row to be re-calculated in case anything else was
            //     inserted BEFORE the insertion point we're currently processing. And since that
            //     UNDERSHOOT can happen, we'll be querying the JTree about the data of the selected
            //     row, and we'll get the wrong answer since the JTree model data rows don't
            //     correspond to JTable rows anymore.
            //   * As you see, there's a risk that if we query the table in step 2 above (after
            //     the 1st insert), then we'd get the wrong item from the JTree. So we cannot
            //     use this method.
            // - DEFER the row calculation *and* the JTable insertion:
            //   * Our first insert (which happened at JTree row 57) would be treated as row #58
            //     since we've deferred the JTable insertion AND row calculation (since another row
            //     was add()-ed at row 5 in JTree's data model before we got to the processing).
            //     This means that we tell the JTable that a new row has been added at 58 and that
            //     rows 58+ have changed data, so they need to re-render. By that point in time,
            //     the JTree has definitely calculated all row numbers, so it would easily retrieve
            //     the text of row 58, and would return it to the table. That's great.
            //     But the user would see changes on row 58+ that should have happened at 57+, and
            //     due to the insertion at row 5, the data at row 58 will be what used to be at 
            //     57, so the user will see two rows (57 and 58) that display the same item.
            //     If they had selected the item at 57 which has now been pushed down to 58, then
            //     if we query the JTree what item is at 57, we'll get the wrong item. That's bad.
            //     The user's visually selected JTable row will still look correct to them, though.
            //   * Our second insert (at JTree row 5) would cause all rows from 5+ to re-render and
            //     would push down the user's selected row to the correct number. At that point,
            //     everything is correct.
            //   * This method basically suffers the same rendering problem as "manually calculate
            //     the row now", but the risks are MUCH lower. The "extra insertions happened prior
            //     to currently processed row" are handled by OVERSHOOTING the JTable update. So
            //     if an insert is done at JTree row 57 and 5, we'll update JTable row 58 and 5
            //     by the time our deferred processing runs. This OVERSHOOT means that we have
            //     less risk that the user's selected row "updates"/repaints in the JTable, and
            //     that means less risk that the JTable will query the JTree about what message
            //     is at that row (such as 57), and getting the wrong (out of sync) answer. There's
            //     still a risk, but it's greatly minimized.
            //     The user's correct selection is ALWAYS maintained in the table via this method,
            //     and all we'll have to do is add some deferring code in the actual "table row
            //     selection changed" callback, to make it WAIT before reloading the current
            //     message, so that it waits until the end after all current JTable inserts are done
            //     and the JTree and JTable row counts are back in sync again.
            //     That's a separate issue and has been taken care of in the message loading code.
            //
            // so as you can see, the winner was deferring both row calculation and JTable insertion,
            // to minimize the risk of querying the JTree about the wrong row. it also required
            // fixing the message loader so that it WAITS a bit before loading the message, so that
            // it doesn't trigger instantly and queries the JTree about the wrong row there either.
            // we could of course have used method #2 and done the same "delayed message body
            // loader" fix there as well, but it's *simpler* to just let the JTree decide which
            // row was updated. we know that whatever the *lowest* row we inserted in the JTree,
            // it will eventually get to that update and will repaint all JTable rows below that
            // to be correct again. so there are zero rendering issues with that method, and there
            // are less risks that we insert things at the wrong row. we basically just need to also
            // make sure that the message loader doesn't try to load anything during the brief
            // JTree<->JTable row desync periods.
            //
            // after all of the above, we still have the issue of how to preserve the *exact*
            // row selection in the JTable. we must take great care to fire the correct
            // "fireTableRowsInserted()" row range, so that the JTable can do a proper job of
            // at least *trying* to maintain the user's old selection. but that's far from
            // accurate, since a JTable is very dumb, so we're using the advanced SmartSelection
            // classes to perfectly preserve the user's selection no matter what happens.
            //
            // ALSO NOTE: the first insertion of the first child/childtree within an existing
            // childless tree node triggers "treeExpanded" instead of "treeNodesInserted" (and
            // "treeExpanded"'s use of fireTableRowsInserted() does not trigger
            // "treeNodesInserted").
            //
            // this is because of a peculiarity of how Frost's buggy TreeTable implementation is used
            // by the FrostMessageObject/FreeTalkMessage classes's "add()" functions. if we add
            // something to a collapsed tree (and all nodes *are* initially collapsed due to being
            // childless, since collapsed is the default state of a JTree node), then the add()
            // function only triggers a job to "expand the parent and all its children". it doesn't
            // fire any insertion event. so in that case, our tree expansion listener will detect
            // that the parent is being expanded, and will insert rows into the table accordingly to
            // accomodate all of the new tree rows.
            // if, however, we're being added to an *expanded* (open/non-collapsed) parent, then
            // the add() function fires an insertion event, and then checks if *we* have any children,
            // in which case it tells *us* to expand and causes a tree expansion event to fire
            // *after* the insertion event. and since we're deferring our insertion, we are also
            // deferring our handling of the expansion event (above), to make sure they are always
            // processed in the correct order. see the treeExpanded() comments for more info.
            //
            // also: normally we'd want to count the number of visible children *here* when a row
            // is being inserted, but since we'll be processing the insertion event on a delayed
            // invokeLater timer when the expansion itself has *also* taken place, we'd be able
            // to see the children during the insertion too, so we'd count the number of inserted
            // rows + children here *and* during the expansion event that also fired, which would
            // in turn make us "insert" too many table rows. so the solution is to do the unobvious
            // and *ignore* the children during any insertion events. normally that'd be wrong,
            // but we know how Frost uses the TreeTable, and have to code around that fact. so we
            // won't count any children during insertion. they'll be counted in the expansion event.
            //
            // in short; "treeNodesInserted()" fires for all top-level insertions under (root)
            // *except* for the first one, *and* for all subsequent insertions within existing
            // *expanded* tree locations that already have *other* children.
            // all other cases fire "treeExpanded" instead.
            //
            // ps: we don't need to do anything in the other tree events that fire "rows
            // updated" or "rows deleted", since Java automatically preserves/corrects the
            // selection when items are removed. likewise, we don't need to do anything in
            // the "table changed" event since that's for indicating a total table wipe (and
            // forcibly clears the selection), so there'd be nothing to correct in that case.
            // --------------------------------------------------------------------------------

            // update the JTable in a deferred GUI job since the JTree may not have updated yet
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    // process *all* of the inserted node objects from this insertion event
                    final Object[] insertedNodes = e.getChildren();
                    if( insertedNodes == null ) { return; }
                    for( int i=0; i<insertedNodes.length; ++i ) {
                        // grab the current node object and its path
                        final DefaultMutableTreeNode thisNode = (DefaultMutableTreeNode)insertedNodes[i];
                        if( thisNode == null ) { continue; }
                        final TreePath thisPath = new TreePath(thisNode.getPath());

                        // check the row number for the inserted node in the tree, and abort if
                        // the node isn't within an expanded (visible) tree path.
                        // NOTE: this function is the same as treeTable.getRowForNode(), but
                        // doesn't idiotically wrap the root node in a hardcoded "0".
                        //
                        // NOTE: it's actually possible to calculate this formula manually, by
                        // taking parent row location + 1 (because it's a child) + child index
                        // offset within the parent + the number of visible descendants in prior
                        // children. but it's faster and safer to query the tree!
                        //
                        // also NOTE: root would return -1 since it's set to invisible, but we
                        // never "insert" root (we "set" it as the tree root), so it doesn't matter
                        // that an insertion of root would be ignored here, since it can't happen.
                        int insertFromRow = tree.getRowForPath(thisPath);
                        if( insertFromRow < 0 ) {
                            // "-1" means it was inserted under a collapsed parent, so it's
                            // invisible and therefore shouldn't trigger any JTable row insertion
                            // since the JTree itself hasn't added any extra rows
                            return;
                        }

                        // due to Frost's TreeTable peculiarities, we never want to count any
                        // visible descendants during the insertion event since they'll be
                        // handled in the expansion event instead. so the "to" row must = "from".
                        //final int insertToRow = insertFromRow + visibleDescendantsCount;

                        // perform a very intelligent row update, which preserves the user's current selection
//                            System.out.println("treeNodesInserted, insertFromRow="+insertFromRow+", insertToRow="+insertFromRow+" (visibleDescendants ignored)");
                        MessageTreeTableSmartUtils.smartFireTreeTableRowsInserted(treeTable, TreeTableModelAdapter.this, insertFromRow, insertFromRow);
                    }
                }
            });
        }

        @Override
        public void treeNodesRemoved(
                final TreeModelEvent e)
        {
            // the TreeTable implementation used by Frost makes it impossible to figure out
            // the range of corresponding JTable rows of the deleted JTree node, because by the
            // time that this event fires, the nodes are already gone from the JTree, so we
            // can't get their row numbers or analyze their number of visible descendants.
            // we could get the *row* of the deleted entry via the parent location + childindices,
            // but it would be subject to lots of race conditions and wouldn't even handle
            // the removal of the visible descendants of the deleted row, so it's not even worth trying.
            // therefore we can't fire a surgical "fireTableRowsDeleted()", and will instead
            // have to tell the table that *all* rows may have changed, in order to force
            // a full refresh. the only downside to a full refresh is that the user's selection
            // is cleared and that it's a bit slower. however, no code in Frost ever deletes 
            // *individual* tree entries, so this "treeNodesRemoved" event never fires anyway.
            //
            // we'll update the JTable instantly since we don't want to run into the issue of a
            // queued insertion job being handled by the "data changed" event *and* the insertion
            // event. furthermore, the removal of JTree rows *should* have been instantly
            // processed. and of course *nothing* in Frost ever uses row deletion anyway, so it
            // doesn't matter if this row deletion code turns out to not be working properly...
            //
            // NOTE FOR THE FUTURE: a possible strategy for solving this if it's ever needed
            // in the future, is as follows:
            // * If our parentNode isRoot(), set parentRow to -1. Otherwise set our parentRow
            // to getRowForPath(parentNode).
            // * Look at childIndices to see where we existed within our parent.
            // * If we existed between two children of our parent, then deleteFromRow is the
            // row of the last child's descendant before us; that means we have to count the
            // number of visible *descendants* of ALL of our parent's children before us, plus
            // the number of children before us.
            // * If there was no child before us, then deleteFromRow is parentRow + 1.
            // * deleteToRow is always the getRowForPath() for the next childIndices after us,
            // if the parent had more children. Or if it had no more children, it's getRowForPath()
            // of the next sibling of our parent. If there were neither more children of our parent
            // nor more siblings of our parent, then deleteToRow is the final row of the JTable.
            // * That should be correct, but like I said, nothing in Frost uses this feature...
            System.out.println("**** DEVELOPER WARNING: YOU ARE TRYING TO DELETE NODES FROM A JTREE. YOU WILL HAVE TO MANUALLY CHECK IF TREENODESREMOVED() WORKS PROPERLY.");
//                System.out.println("treeNodesRemoved");
            fireTableDataChanged();
        }

        @Override
        public void treeStructureChanged(
                final TreeModelEvent e)
        {
            // this fires when the JTree has changed in a *substantial* way. in Frost's case,
            // it means that all tree rows have been deleted (such as when switching board).
            // we'll simply instantly tell the table that all of the data "may have changed".
            // we do *not* perform it on a delayed "invokeLater" job, since there's no point.
            // *all* of Frost's calls to "tree structure changed" are done on the GUI thread,
            // so we know that we're the EDT and are redrawing the table at a safe time.
            // and deferring it further would just risk race conditions when the new board data
            // starts being inserted.
//                System.out.println("treeStructureChanged");
            fireTableDataChanged();
        }
    }
}
