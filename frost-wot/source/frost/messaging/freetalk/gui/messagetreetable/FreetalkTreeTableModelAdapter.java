/*
 FreetalkTreeTableModelAdapter.java / Frost-Next
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

package frost.messaging.freetalk.gui.messagetreetable;

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

// --------------------------------------------------------------------------------
// This class has been completely stripped of all comments, since it is an exact
// duplicate of the code used for the non-Freetalk adapter. The only thing that
// differs is the word "Freetalk" being added to some of the variable types here!
// Frost was unfortunately written in a way where code-duplication is a fact of life.
//
// See "messaging/frost/gui/messagetreetable/TreeTableModelAdapter.java" for very
// detailed comments and full information about what's going on here.
// --------------------------------------------------------------------------------
@SuppressWarnings("serial")
public class FreetalkTreeTableModelAdapter extends AbstractTableModel
{
    final JTree tree;
    final FreetalkMessageTreeTable treeTable;
    final FreetalkTreeTableModel treeTableModel;

    final Listener listener;
    volatile boolean isListenerActive = false;

    public FreetalkTreeTableModelAdapter(
            final FreetalkTreeTableModel treeTableModel,
            final JTree tree,
            final FreetalkMessageTreeTable tt)
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

        @Override
        public void treeWillCollapse(
                final TreeExpansionEvent e)
        throws ExpandVetoException
        {
            final TreePath collapsedPath = e.getPath();
            if( collapsedPath == null ) { collapsedVisibleDescendantsCount = -1; return; }
            final DefaultMutableTreeNode collapsedNode = (DefaultMutableTreeNode)collapsedPath.getLastPathComponent();
            if( collapsedNode == null ) { collapsedVisibleDescendantsCount = -1; return; }

            final int collapsedRow = tree.getRowForPath(collapsedPath);
            if( collapsedRow < 0 && !collapsedNode.isRoot() ) {
                return;
            }

            collapsedVisibleDescendantsCount = MessageTreeTableSmartUtils.getVisibleDescendantsCount(tree, collapsedPath);
//                System.out.println("treeWillCollapse, collapsedRow="+collapsedRow+(collapsedRow < 0 ? " (root)" : ""));
//                System.out.println("treeWillCollapse->visibleDescendants: "+collapsedVisibleDescendantsCount);
        }

        @Override
        public void treeWillExpand(
                final TreeExpansionEvent e)
        throws ExpandVetoException
        {
        }

        @Override
        public void treeCollapsed(
                final TreeExpansionEvent e)
        {
            if( collapsedVisibleDescendantsCount <= 0 ) {
                return;
            }

            final TreePath collapsedPath = e.getPath();
            if( collapsedPath == null ) { return; }
            final DefaultMutableTreeNode collapsedNode = (DefaultMutableTreeNode)collapsedPath.getLastPathComponent();
            if( collapsedNode == null ) { return; }

            final int collapsedRow = tree.getRowForPath(collapsedPath);
            if( collapsedRow < 0 && !collapsedNode.isRoot() ) {
                return;
            }

            final int deleteFromRow = collapsedRow + 1;
            final int deleteToRow = deleteFromRow + (collapsedVisibleDescendantsCount - 1);

//                System.out.println("treeCollapsed, collapsedRow="+collapsedRow+(collapsedRow < 0 ? " (root)" : "")+", deleteFromRow="+deleteFromRow+", deleteToRow="+deleteToRow);
//                System.out.println("treeCollapsed->visibleDescendants: "+collapsedVisibleDescendantsCount);
            fireTableRowsDeleted(deleteFromRow, deleteToRow);
        }

        @Override
        public void treeExpanded(
                final TreeExpansionEvent e)
        {
            final TreePath expandedPath = e.getPath();
            if( expandedPath == null ) { return; }
            final DefaultMutableTreeNode expandedNode = (DefaultMutableTreeNode)expandedPath.getLastPathComponent();
            if( expandedNode == null ) { return; }

            if( expandedNode.getChildCount() <= 0 ) {
                return;
            }

            final int visibleDescendantsCount = MessageTreeTableSmartUtils.getVisibleDescendantsCount(tree, expandedPath);

            if( visibleDescendantsCount <= 0 ) {
                return;
            }

            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    final int expandedRow = tree.getRowForPath(expandedPath);
                    if( expandedRow < 0 && !expandedNode.isRoot() ) {
                        return;
                    }

                    final int insertFromRow = expandedRow + 1;
                    final int insertToRow = insertFromRow + (visibleDescendantsCount - 1);

//                        System.out.println("treeExpanded, expandedRow="+expandedRow+(expandedRow < 0 ? " (root)" : "")+", insertFromRow="+insertFromRow+", insertToRow="+insertToRow);
//                        System.out.println("treeExpanded->visibleDescendants: "+visibleDescendantsCount);
                    MessageTreeTableSmartUtils.smartFireTreeTableRowsInserted(treeTable, FreetalkTreeTableModelAdapter.this, insertFromRow, insertToRow);
                }
            });
        }

        @Override
        public void treeNodesChanged(
                final TreeModelEvent e)
        {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    final Object[] changedNodes = e.getChildren();
                    if( changedNodes == null ) { return; }
                    for( int i=0; i<changedNodes.length; ++i ) {
                        final DefaultMutableTreeNode thisNode = (DefaultMutableTreeNode)changedNodes[i];
                        if( thisNode == null ) { continue; }
                        final TreePath thisPath = new TreePath(thisNode.getPath());

                        int changedRow = tree.getRowForPath(thisPath);
                        if( changedRow < 0 ) {
                            return;
                        }

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
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    final Object[] insertedNodes = e.getChildren();
                    if( insertedNodes == null ) { return; }
                    for( int i=0; i<insertedNodes.length; ++i ) {
                        final DefaultMutableTreeNode thisNode = (DefaultMutableTreeNode)insertedNodes[i];
                        if( thisNode == null ) { continue; }
                        final TreePath thisPath = new TreePath(thisNode.getPath());

                        int insertFromRow = tree.getRowForPath(thisPath);
                        if( insertFromRow < 0 ) {
                            return;
                        }

//                            System.out.println("treeNodesInserted, insertFromRow="+insertFromRow+", insertToRow="+insertFromRow+" (visibleDescendants ignored)");
                        MessageTreeTableSmartUtils.smartFireTreeTableRowsInserted(treeTable, FreetalkTreeTableModelAdapter.this, insertFromRow, insertFromRow);
                    }
                }
            });
        }

        @Override
        public void treeNodesRemoved(
                final TreeModelEvent e)
        {
            System.out.println("**** DEVELOPER WARNING: YOU ARE TRYING TO DELETE NODES FROM A JTREE. YOU WILL HAVE TO MANUALLY CHECK IF TREENODESREMOVED() WORKS PROPERLY.");
//                System.out.println("treeNodesRemoved");
            fireTableDataChanged();
        }

        @Override
        public void treeStructureChanged(
                final TreeModelEvent e)
        {
//                System.out.println("treeStructureChanged");
            fireTableDataChanged();
        }
    }
}
