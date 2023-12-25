/*
  FreetalkMessage.java / Frost
  Copyright (C) 2009  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.freetalk;

import java.util.*;

import javax.swing.tree.*;

import org.joda.time.*;

import frost.*;
import frost.messaging.freetalk.boards.*;
import frost.messaging.freetalk.gui.messagetreetable.*;
import frost.util.*;

/**
 * A Freetalk message.
 */
@SuppressWarnings("serial")
public class FreetalkMessage extends DefaultMutableTreeNode {

    private FreetalkBoard board = null;
    private String msgId = null;
    private int msgIndex = 0;
    private String title = null;
    private String author = null;
    private long dateMillis = 0;
    private long fetchDateMillis = 0;
    private String parentMsgID = null;
    private String threadRootMsgID = null;
    private List<FreetalkFileAttachment> fileAttachments = null;

    private String content = "";

    private String dateAndTimeString = null;

    public static boolean sortThreadRootMsgsAscending;

    /**
     * Constructor for a dummy root node.
     */
    public FreetalkMessage(final boolean isRootnode) {
        board = new FreetalkBoard("(root)");
    }

    /**
     * Constructor used when a new message is received.
     */
    public FreetalkMessage(
            final FreetalkBoard board,
            final String msgId,
            final int msgIndex,
            final String title,
            final String author,
            final long dateMillis,
            final long fetchDateMillis,
            final String parentMsgID,
            final String threadRootMsgID,
            final List<FreetalkFileAttachment> fileAttachments)
    {
        super();
        this.board = board;
        this.msgId = msgId;
        this.msgIndex = msgIndex;
        this.title = title;
        this.author = author;
        this.dateMillis = dateMillis;
        this.fetchDateMillis = fetchDateMillis;
        this.parentMsgID = parentMsgID;
        this.threadRootMsgID = threadRootMsgID;
        this.fileAttachments = fileAttachments;
    }

    @Override
    public String toString() {
        return getTitle();
    }

    public FreetalkBoard getBoard() {
        return board;
    }

    public String getMsgId() {
        return msgId;
    }

    public int getMsgIndex() {
        return msgIndex;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public long getFetchDateMillis() {
        return fetchDateMillis;
    }

    public boolean isThread() {
        return parentMsgID == null;
    }

    public String getParentMsgID() {
        return parentMsgID;
    }

    public String getThreadRootMsgID() {
        return threadRootMsgID;
    }

    public List<FreetalkFileAttachment> getFileAttachments() {
        return fileAttachments;
    }

    public String getContent() {
        return content;
    }
    public void setContent(final String c) {
        if (c == null) {
            System.out.println("!!!!!!!!!!!!! prevented null content");
            return;
        }
        content = c;
    }

    public String getDateAndTimeString() {
        if (dateAndTimeString == null) {
            // Build a String of format yyyy.mm.dd hh:mm:ssGMT
            final DateTime dateTime = new DateTime(getDateMillis(), DateTimeZone.UTC); // UTC

            final String dateStr = DateFun.FORMAT_DATE_EXT.print(dateTime); // "2008.12.24"; at UTC
            final String timeStr = DateFun.FORMAT_TIME_EXT.print(dateTime); // "16:51:28GMT"; at UTC

            final StringBuilder sb = new StringBuilder(29);
            sb.append(dateStr).append(" ").append(timeStr);

            this.dateAndTimeString = sb.toString();
        }
        return dateAndTimeString;
    }

    public void resortChildren() {
        if( getChildren() == null || getChildren().size() <= 1 ) {
            return;
        }
        // choose a comparator based on settings in SortStateBean
        final Comparator<FreetalkMessage> comparator =
            FreetalkMessageTreeTableSortStateBean.getComparator(
                    FreetalkMessageTreeTableSortStateBean.getSortedColumn(), FreetalkMessageTreeTableSortStateBean.isAscending());
        if( comparator != null ) {
            Collections.sort(getChildren(), comparator);
        }
    }

    @Override
    public void add(final MutableTreeNode n) {
        // this is a helper function which always adds the nodes silently (without notifying the
        // table), thus behaving like the normal add() we're overriding
        add(n, true);
    }

    /**
     * Special add() which adds the new nodes properly sorted within their parent node.
     * NOTE: The node to add is a hierarchy of one or more children to add to the current parent object.
     * In the case of messages with dummy parents, there's a hierarchy like "dummy -> dummy -> real
     * message". Otherwise it directly refers to the real message.
     * @param {MutableTreeNode} nn - a message tree hierarchy with 1 or more messages you want to
     * add; the first node is the parent, and it can have one or more levels of children (such as
     * in the case of having constructed a tree of dummy messages)
     * @param {boolean} silent - if this is true, we assume that you are building a structure of
     * multiple nested nodes *before* adding them to the table all in one go, so we don't send any
     * table events; this must *always* be false when you actually add the message to the visible table!
     */
    public void add(final MutableTreeNode nn, final boolean silent) {
        // add the message tree at a properly sorted location, by analyzing the *first* (parent) node of the message tree we want to add
        final FreetalkMessage n = (FreetalkMessage)nn;
        int[] ixs;

        if( getChildren() == null ) {
            // adding a message tree to a node (root/msg/dummy) which had no other children means we just append it to the end (pos 0)
            super.add(n);
            ixs = new int[] { 0 };
        } else {
            // If threaded:
            //   sort first msg of a thread (child of root) descending (newest first),
            //   but inside a thread sort siblings ascending (oldest first). (thunderbird/outlook do it this way)
            // If not threaded:
            //   sort as configured in SortStateBean
            int insertPoint;
            if( FreetalkMessageTreeTableSortStateBean.isThreaded() ) {
                if( isRoot() ) {
                    // child of root, sort descending
                    if( sortThreadRootMsgsAscending ) {
                        insertPoint = Collections.binarySearch(getChildren(), n, FreetalkMessageTreeTableSortStateBean.dateComparatorAscending);
                    } else {
                        insertPoint = Collections.binarySearch(getChildren(), n, FreetalkMessageTreeTableSortStateBean.dateComparatorDescending);
                    }
                } else {
                    // inside a thread, sort ascending
                    insertPoint = Collections.binarySearch(getChildren(), n, FreetalkMessageTreeTableSortStateBean.dateComparatorAscending);
                }
            } else {
                final Comparator<FreetalkMessage> comparator = FreetalkMessageTreeTableSortStateBean.getComparator(
                        FreetalkMessageTreeTableSortStateBean.getSortedColumn(), FreetalkMessageTreeTableSortStateBean.isAscending());
                if( comparator != null ) {
                    insertPoint = Collections.binarySearch(getChildren(), n, comparator);
                } else {
                    insertPoint = 0;
                }
            }

            if( insertPoint < 0 ) {
                insertPoint++;
                insertPoint *= -1;
            }
            if( insertPoint >= getChildren().size() ) {
                super.add(n);
                ixs = new int[] { getChildren().size() - 1 };
            } else {
                super.insert(n, insertPoint);
                ixs = new int[] { insertPoint };
            }
        }
        // if silent is false, it means that the caller is actually adding the new nodetree to a
        // location within the real table, so we should now expand the tree path to the inserted
        // node, notify the table of the insertion, and expand any further extra children
        if( !silent ) {
            // first check if the path to the *parent* of the inserted nodetree is expanded (visible)
            if( MainFrame.getInstance().getFreetalkMessageTab().getMessagePanel().getMessageTable().getTree().isExpanded(new TreePath(this.getPath())) ) {
                // the parent is expanded, so we must now notify the table that the nodetree has
                // been inserted, and at what offset (within the parent) it was inserted
                MainFrame.getInstance().getFreetalkMessageTab().getMessagePanel().getMessageTreeModel().nodesWereInserted(this, ixs);
                // now expand the inserted node and all of its children, if it has children
                // (because all expansion/collapsing is handled by the JTree, and all new nodes
                // default to collapsed)
                if( n.getChildCount() > 0 ) {
                    // the parent of the added node was already expanded ("JTree.isExpanded()"),
                    // but the added node has children so make sure we expand the new node and its children too
                    // NOTE: this tells the table to draw any children that become visible as a
                    // result of the expansion, and triggers the "treeExpanded" event for them
                    MainFrame.getInstance().getFreetalkMessageTab().getMessagePanel().getMessageTable().expandNode(n);
                }
            } else {
                // the path to the parent itself wasn't expanded, so now expand the entire path
                // to our parent *and* all of its children (including us and our children)
                // NOTE: this most commonly fires when we're being added as the first child to a
                // node that didn't have any children, since that means our parent was "collapsed"
                MainFrame.getInstance().getFreetalkMessageTab().getMessagePanel().getMessageTable().expandNode(this);
            }
        }
    }
    
    @SuppressWarnings("unchecked")
    protected List<FreetalkMessage> getChildren() {
        return (List<FreetalkMessage>) children;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<FreetalkMessage> depthFirstEnumeration() {
        return super.depthFirstEnumeration();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<FreetalkMessage> breadthFirstEnumeration() {
        return super.breadthFirstEnumeration();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Enumeration<FreetalkMessage> children() {
        return super.children();
    }
}
