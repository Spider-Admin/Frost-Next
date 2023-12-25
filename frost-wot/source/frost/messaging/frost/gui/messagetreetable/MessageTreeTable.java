/*
 * Copyright 1997-2000 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistribution in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials
 *   provided with the distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT OF OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THIS SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE THIS SOFTWARE, EVEN IF SUN HAS
 * BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that this software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package frost.messaging.frost.gui.messagetreetable;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.*;
import java.lang.Math;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.*;
import javax.swing.plaf.basic.*;
import javax.swing.table.*;
import javax.swing.tree.*;

import frost.*;
import frost.fileTransfer.common.*;
import frost.identities.*;
import frost.messaging.frost.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.IconGenerator;
import frost.util.gui.SmartSelection;
import frost.util.gui.TrustStateColors;

/**
 * This example shows how to create a simple JTreeTable component,
 * by using a JTree as a renderer (and editor) for the cells in a
 * particular column in the JTable.
 *
 * @version 1.2 10/27/98
 *
 * @author Philip Milne
 * @author Scott Violet
 */
@SuppressWarnings("serial")
public class MessageTreeTable extends JTable implements PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(MessageTreeTable.class.getName());

    /** A subclass of JTree. */
    protected TreeTableCellRenderer tree;
    /** A JTable model "adapter" which links the JTable to the JTree's model. */
    protected final TreeTableModelAdapter model;

    protected Border borderUnreadAndMarkedMsgsInThread = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 2, 0, 0, Color.blue),    // outside
            BorderFactory.createMatteBorder(0, 2, 0, 0, Color.green) ); // inside
    protected Border borderMarkedMsgsInThread = BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(0, 2, 0, 0),                // outside
            BorderFactory.createMatteBorder(0, 2, 0, 0, Color.green) ); // inside
    protected Border borderUnreadMsgsInThread = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 2, 0, 0, Color.blue),    // outside
            BorderFactory.createEmptyBorder(0, 2, 0, 0) );              // inside
    protected Border borderEmpty = BorderFactory.createEmptyBorder(0, 4, 0, 0);

    private final StringCellRenderer stringCellRenderer = new StringCellRenderer();
    private final BooleanCellRenderer booleanCellRenderer = new BooleanCellRenderer();

    private final ImageIcon flaggedIcon    = MiscToolkit.loadImageIcon("/data/flag.png");
    private final ImageIcon starredIcon    = MiscToolkit.loadImageIcon("/data/star.png");
    private final ImageIcon junkIcon       = MiscToolkit.loadImageIcon("/data/junk.png");
    // NOTE: junkFadedIcon is just a nice extra icon that may be useful sometime
//    private final ImageIcon junkFadedIcon  = MiscToolkit.loadImageIcon("/data/junk_faded.png");

    private final ImageIcon messageDummyIcon = MiscToolkit.loadImageIcon("/data/messagedummyicon.gif");
    private final ImageIcon messageNewIcon = MiscToolkit.loadImageIcon("/data/messagenewicon.gif");
    private final ImageIcon messageReadIcon = MiscToolkit.loadImageIcon("/data/messagereadicon.gif");
    private final ImageIcon messageNewRepliedIcon = MiscToolkit.loadImageIcon("/data/messagenewrepliedicon.gif");
    private final ImageIcon messageReadRepliedIcon = MiscToolkit.loadImageIcon("/data/messagereadrepliedicon.gif");

    public final ImageIcon receivedOneMessage = MiscToolkit.loadImageIcon("/data/bullet_red.png");
    public final ImageIcon receivedFiveMessages = MiscToolkit.loadImageIcon("/data/bullet_blue.png");

    private boolean showColoredLines;
    private boolean indicateLowReceivedMessages;
    private int indicateLowReceivedMessagesCountRed;
    private int indicateLowReceivedMessagesCountBlue;

    private Color fMsgNormalColor;
    private Color fMsgPrivColor;
    private Color fMsgWithAttachmentsColor;
    private Color fMsgUnsignedColor;

    private final int MINIMUM_ROW_HEIGHT = 20;
    private final int ROW_HEIGHT_MARGIN = 4;

    private Set<Integer> fixedsizeColumns; // in "all columns" index space, default model ordering

    public MessageTreeTable(final TreeTableModel treeTableModel) {
    	super();

        updateMessageColors();

        showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
        indicateLowReceivedMessages = Core.frostSettings.getBoolValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES);
        indicateLowReceivedMessagesCountRed = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED);
        indicateLowReceivedMessagesCountBlue = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE);

        Core.frostSettings.addPropertyChangeListener(SettingsClass.SHOW_COLORED_ROWS, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_NAME, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_SIZE, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_STYLE, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.TREE_EXPANSION_ICON, this);
        MainFrame.getInstance().getMessageColorManager().addPropertyChangeListener(this);

    	// Creates the tree. It will be used as a renderer and editor.
    	tree = new TreeTableCellRenderer(treeTableModel);

        // Installs a tableModel representing the visible rows in the tree.
        model = new TreeTableModelAdapter(treeTableModel, tree, this);
        super.setModel(model);

    	// Forces the JTable and JTree to share their row selection models.
    	final ListToTreeSelectionModelWrapper selectionWrapper = new ListToTreeSelectionModelWrapper();
    	tree.setSelectionModel(selectionWrapper);
    	setSelectionModel(selectionWrapper.getListSelectionModel());

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        // installs the tree editor renderer and editor.
        // NOTE: These use a custom class and will therefore never be overridden by look&feel changes
        setDefaultRenderer(TreeTableModel.class, tree);
        setDefaultEditor(TreeTableModel.class, new TreeTableCellEditor());

        // install table header renderer
        // NOTE: this one is not overridden by the l&f, and can only be set once or we get null pointers
        final MessageTreeTableHeader hdr = new MessageTreeTableHeader(this);
        setTableHeader(hdr);

        // install all of the other custom component renderers and settings
        setupTreeTableJTableUIStyle();

        // NOTE: this tells the JTree that *all* tree rows will be identical height,
        // and that it doesn't need to calculate (and cache) individual heights/widths,
        // which makes tree rendering about 20% faster. just be aware that we MUST
        // have used a "setRowHeight" of 1+ *before* this call, otherwise it's ignored.
        // as luck would have it, the "setup JTable" call above sets the row height of
        // both the tree and the table.
        tree.setLargeModel(true);
    }

    // retrieves the user's custom messagetype colors
    private void updateMessageColors()
    {
        fMsgNormalColor = MainFrame.getInstance().getMessageColorManager().getNormalColor();
        fMsgPrivColor = MainFrame.getInstance().getMessageColorManager().getPrivColor();
        fMsgWithAttachmentsColor = MainFrame.getInstance().getMessageColorManager().getWithAttachmentsColor();
        fMsgUnsignedColor = MainFrame.getInstance().getMessageColorManager().getUnsignedColor();
    }

    private Color getMessageTypeColor(
            final FrostMessageObject msg)
    {
        // paranoia
        if( msg == null ) {
            return fMsgNormalColor;
        }
        // color priority: private message > has attachments > unsigned/anonymous
        if( msg.getRecipientName() != null && msg.getRecipientName().length() > 0 ) {
            return fMsgPrivColor;
        } else if( msg.containsAttachments() ) {
            return fMsgWithAttachmentsColor;
        } else if( !msg.isSignatureStatusVERIFIED() ) {
            return fMsgUnsignedColor;
        } else {
            return fMsgNormalColor;
        }
    }

    // only to be called by constructor, and updateUI (when look&feel changes).
    // we *must* call this from updateUI when the L&F changes, since the change of skin will unbind our custom
    // renderers if the skin provides ones for those object types (such as Boolean.class, very commonly)
    private void setupTreeTableJTableUIStyle() {
        // the renderers will be null if called from updateUI during app startup before GUI has been fully built
        if( stringCellRenderer == null ) { return; }

        // install custom cell renderers for common column types
        // NOTE: if we don't re-apply these here, the l&f change will override Boolean.class with
        // a regular checkbox style instead of our nice, custom starred/flagged/junk icon columns.
        setDefaultRenderer(String.class, stringCellRenderer);
        setDefaultRenderer(Boolean.class, booleanCellRenderer);

        // the rest of these properties are re-applied just in case the l&f *may* affect them

    	// No grid.
    	setShowGrid(false);

    	// No intercell spacing
    	setIntercellSpacing(new Dimension(0, 0));

    	// And update the height of the tree's rows to match that of the font.
    	final int fontSize = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_SIZE);
    	setRowHeight(Math.max(fontSize + ROW_HEIGHT_MARGIN, MINIMUM_ROW_HEIGHT));
    }

    /**
     * Overwritten to forward LEFT and RIGHT cursor to the tree to allow JTree-like expand/collapse of nodes.
     */
    @Override
    protected boolean processKeyBinding(final KeyStroke ks, final KeyEvent e, final int condition, final boolean pressed) {
        if( !pressed ) {
            return super.processKeyBinding(ks, e, condition, pressed);
        }

        if( e.getKeyCode() == KeyEvent.VK_LEFT ) {
            getTree().processKeyEvent(e);
        } else if( e.getKeyCode() == KeyEvent.VK_RIGHT ) {
            getTree().processKeyEvent(e);
        } else {
            return super.processKeyBinding(ks, e, condition, pressed);
        }
        return true;
    }

    public FrostMessageObject getRootNode() {
        return (FrostMessageObject)((DefaultTreeModel)tree.getModel()).getRoot();
    }

    public void setNewRootNode(final TreeNode t) {
        ((DefaultTreeModel)tree.getModel()).setRoot(t);
    }

    // If expand is true, expands all nodes in the tree.
    // Otherwise, collapses all nodes in the tree.
    public void expandAll(final boolean expand) {
        Mixed.invokeNowOrLaterInDispatchThread(new Runnable() {
            public void run() {
                // Traverse tree from root
                final TreeNode root = (TreeNode)tree.getModel().getRoot();
                expandAll(new TreePath(root), expand);
            }
        });
    }

    public void expandRootChildren(final boolean preExpandReplies, final boolean expandUnreadThreads) {
        final FrostMessageObject root = (FrostMessageObject)tree.getModel().getRoot();

        Mixed.invokeNowOrLaterInDispatchThread(new Runnable() {
            public void run() {
                expandRootChildren(root, preExpandReplies, expandUnreadThreads);
            }
        });
    }

    public void expandThread(final boolean expand, final FrostMessageObject msg) {
        if( msg == null ) {
            return;
        }
        // find msgs rootmsg
        final FrostMessageObject threadRootMsg = msg.getThreadRootMessage();
        if( threadRootMsg == null ) {
            return;
        }
        Mixed.invokeNowOrLaterInDispatchThread(new Runnable() {
            public void run() {
                expandAll(new TreePath(threadRootMsg.getPath()), expand);
            }
        });
    }

    public void expandFirework(final FrostMessageObject node) {
        Mixed.invokeNowOrLaterInDispatchThread(new Runnable() {
            public void run() {
                // Expand all child nodes
                final Enumeration<FrostMessageObject> nodeEnum = node.depthFirstEnumeration();
                while( nodeEnum.hasMoreElements() ) {
                    FrostMessageObject frostMessageObjectTreeNode = nodeEnum.nextElement();

                    if( !frostMessageObjectTreeNode.isLeaf() )
                        continue;

                    frostMessageObjectTreeNode = (FrostMessageObject)frostMessageObjectTreeNode.getParent();
                    if( !frostMessageObjectTreeNode.isRoot() )
                        tree.expandPath(new TreePath(frostMessageObjectTreeNode.getPath()));
                }

                // Expand the node itself (via its parent if we're a leaf, otherwise ourselves)
                if( node.isLeaf() ) {
                    final FrostMessageObject n = (FrostMessageObject)node.getParent();
                    if( !n.isRoot() )
                        tree.expandPath(new TreePath(n.getPath()));
                } else {
                    tree.expandPath(new TreePath(node.getPath()));
                }
            }
        });
    }

    /*
     * EXPLANATION:
     * If you do a huge change to a JTree, such as expanding all nodes or collapsing all nodes,
     * then the JTree will fire a UI update (REPAINT) event for EVERY node you expand or collapse.
     * For just a *normal* JTree, this slowdown is massive and exponential, and is about as follows:
     * - JTree with 5k nodes: 1.5s to expand, 0.5s to collapse.
     * - JTree with 30k nodes: 70-120s to expand, 27s to collapse.
     * Further complicating things is the fact that we've got a "TreeTable" and we sync the JTable
     * to the JTree, which means that all of those expand events *also* fire table row insert/delete
     * events, which basically doubles the numbers above. For instance, a board with 5k messages
     * took me 3.8s to load/expand (over 2x of a plain JTree due to the JTable sync), and it scales
     * exponentially in Frost too, so 15k messages takes around 1-2 minutes.
     *
     * Well, that's where these two functions come in! They use some extremely clever tricks to disable
     * the GUI drawing while we do the expand/collapse, and then re-enable it afterwards. That *entirely*
     * cuts out the vast majority of the slowdown. With this, the actual expand/collapse job takes
     * only about 10ms even for a huge board! The remaining slow part is only the re-application
     * of the JTree GUI, but that's in linear time and still *very* fast (see comments below for benchs).
     *
     * The days of multiple seconds to several minutes of board loading time in Frost are over! All
     * boards now load instantly! ;-) -- Kitty
     */
    public void beforeBigChange() {
        // before any big changes to collapse/expand states, we must remove our custom
        // JTable<->JTree sync-adapter listener, otherwise it would fire table row insertions
        // and deletions constantly while we're changing the expansion/collapse states of the
        // rows. the *only* job that this listener/adapter performs is to insert/delete table rows
        // whenever things happen to the JTree model, so there's *ZERO* harm from not listening to
        // the upcoming JTree changes as long as we remember to just fire an "all table data has
        // changed" event at the end.
        model.removeListener();

        // no longer updating the JTable while doing heavy changes to the JTree is only half
        // of the equation. the JTree's expansion events are also handled by the NiceTreeUI
        // (well, the underlying BasicTreeUI), which forces the screen to paint() the various
        // rows one by one as they become visible. that's a huge waste of time. so we'll simply
        // REMOVE the GUI *completely* while we're doing the heavy changes. this no-GUI mode
        // allows a JTree to alter the expand/collapse states of tens of thousands of nodes
        // with *extremely* high performance, since *nothing* has to be rendered to screen
        // while it happens. removing the GUI removes its event listeners and its paintcache.
        // the loss of the paintcache doesn't matter, since we're using a fixed-rowsize cache
        // via the JTree's "large model" flag anyway, so all rows use the same single cache entry.
        tree.setUI(null);
    }
    public void afterBigChange() {
        // now create a new NiceTreeUI and configure it. this attaches the basic event listeners
        // again and makes the JTree render the current state/visible rows.
        // NOTE: don't even *think* about trying to getUI() a backup in the before-step and setting
        // it back here, since the old GUI's inner data self-destructs when you setUI(null)!
        //
        // creating a brand new GUI instance is super fast, so don't worry about it!
        // the GUI construction is completely linear-time, "O(n)", and CANNOT be sped up any further!
        // on a real computer it's absolutely *instant*, but in a virtual machine (slower GUI drawing) it's:
        // -- non-threaded mode: loads ~140 messages per millisecond, so a board with 5k messages
        //    renders its GUI in 35 milliseconds, and 15k messages renders in 107 milliseconds.
        //    this is as fast as switching between web browser tabs and can't even be felt!
        //    NOTE: this matches the OLD pre-fix speeds, since non-threaded mode never did any of
        //    the tree expansion work, which was what caused the insane slowdown.
        // -- threaded mode: loads ~25-100 messages per millisecond due to the heavy, extra work of drawing
        //    per-row indentation, graphics, handles/lines, expansion symbols, etc, so a board with
        //    5k messages renders its GUI in ~70-200 milliseconds, and 15k messages renders in 150-600ms.
        //    the speed completely depends on how busy your JVM's gui renderer is at the time; but
        //    most commonly I get around the ~70 messages per millisecond mark (so 70ms for 5k and
        //    215ms for 15k messages), which yet again can't even be felt!
        //    NOTE: the OLD pre-fix speeds for threaded mode would have been ~3.8s for 5k and ~1-2mins for 15k.
        tree.setupTreeTableJTreeUIStyle();

        // restore our sync-adapter listener so that future expand/insert events (such as whenever
        // new messages arrive) are processed properly again.
        model.addListener();

        // now tell the JTable to clear the user's row selection and re-render everything on screen.
        // if we don't do this, then the JTable won't properly re-render the JTree updates and won't
        // know about "dirty areas" (changed areas of the screen), so there would be graphics glitches.
        //
        // NOTE: we do NOT fire "nodeStructureChanged" on the underlying JTree model, because the
        // JTree itself already knows exactly what rows are visible/expanded. and calling that func
        // would just cause *all* tree nodes to be forcibly collapsed.
        //
        // NOTE: this clears the user's current row selection, but that doesn't matter since they won't
        // *HAVE* any selection when this event fires! that's because "before/after big change" functions
        // are only called when switching boards or view modes, so the user's selection is lost anyway.
        // furthermore, if they're just changing view mode, we actually save their selected messageid,
        // and then re-apply the same selection after "afterBigChange" has executed, if the message is
        // still visible in the new view-mode. so they *never* lose their selection when changing view
        // mode, as long as the message is still available in the new mode.
        // the only situation where they actually *do* lose their selection is if they right-click and
        // choose the "expand all threads" function, but that's okay since it's rarely used and it doesn't
        // matter whatsoever that it de-selects the message they right-clicked. and at least they won't
        // lose their scroll-offset unless the expansion causes that message to scroll away. ;)
        model.fireTableDataChanged();
    }

    /**
     * Used when loading boards (expands all messages by default), and when using the "expand all"
     * and "collapse all" right-click menu items. It's an extremely slow action, so we fix that by
     * using the GUI performance trick before and after any change that affects all nodes.
     */
    private void expandAll(final TreePath parent, boolean expand) {
        // determine which node we're affecting and if it's the root or not
        final TreeNode node = (TreeNode)parent.getLastPathComponent();
        if( node == null ) { return; }
        final boolean isRootNode = (node.getParent() == null); // NOTE: dummies are not nulls, only root is null

        try {
            // if this is a heavy change (collapse/expand all nodes under root), then we must
            // now use the performance trick to avoid taking an INSANE amount of time to load
            // the board/change the expansion state. this affects all board loads (threaded mode
            // expands all messages by default). and it also affects the "expand/collapse all"
            // right-click menu items in boards.
            if( isRootNode ) {
                beforeBigChange();
            }

            // traverse all children and expand them
            if( node.getChildCount() >= 0 ) {
                final Enumeration textNodeEnumeration = node.children();
                while( textNodeEnumeration.hasMoreElements() ) {
                    expandAll(parent.pathByAddingChild(  ((TreeNode)textNodeEnumeration.nextElement())  ), expand);
                }
            }

            // never collapse the invisible rootnode itself!
            if( isRootNode ) {
                expand = true;
            }

            // Expansion or collapse is done bottom-up
            // When expanding, we expand the parent last
            // When collapsing, we collapse the parent last
            // However, Java itself doesn't care what order we do the expansion and fires
            // treeExpanded events in a top-down fashion and opens them top-down, so even
            // if we've requested it to open a child first, the child will be opened after
            // its parents. Just be aware of that.
            if( expand ) {
                if( !tree.isExpanded(parent) ) {
                    tree.expandPath(parent);
                }
            } else {
                if( !tree.isCollapsed(parent) ) {
                    tree.collapsePath(parent);
                }
            }
        } finally {
            // restore GUI after big changes to root
            if( isRootNode ) {
                afterBigChange();
            }
        }
    }

    /**
     * This is used if the user has enabled "collapse all message threads" and a combination of
     * either "pre-expand all replies" and/or "expand unread messages". In the prior mode, it simply
     * loads a board, expands all threads, then collapses the 1st message of each thread. It makes
     * it very easy to click any thread to expand it and see everything instantly without having
     * to manually open all levels. In the latter mode (which can also be combined with the prior),
     * it opens the paths to all unread messages and all of their descendants (even if read).
     *
     * These operations carry the same huge slowness risks as the regular "expandAll", so we're using
     * the performance trick here too.
     *
     * NOTE: If they've ONLY enabled "collapse all message threads", then NOTHING is expanded when
     * the board is loaded (this function is called with two false parameters and won't do anything).
     */
    private void expandRootChildren(
            final FrostMessageObject root,
            final boolean preExpandReplies,
            final boolean expandUnreadThreads)
    {
        if( !preExpandReplies && !expandUnreadThreads ) { return; }

        if( root.getChildCount() == 0 ) {
            return;
        }

        try {
            beforeBigChange();

            // "pre-expand all replies" mode:
            // expand all nodes in the whole tree, then collapse the root-children, so that the
            // threads are closed, but as soon as they're opened the user will see all replies.
            if( preExpandReplies ) {
                final Enumeration<FrostMessageObject> e = root.children();
                while( e.hasMoreElements() ) {
                    final TreePath path = new TreePath(  (e.nextElement()).getPath()  );

                    expandAll(path, true);
                    tree.collapsePath(path);
                }
            }

            // "expand unread messages in threads" mode:
            // ensures that any unread messages are visible, as well as all of their descendants,
            // even if the descendants are already read. this means that you might not be looking at
            // the whole tree for that thread (unless you've *also* enabled "pre-expand all replies")
            if( expandUnreadThreads ) {
                final Enumeration<FrostMessageObject> en = root.depthFirstEnumeration();
                while( en.hasMoreElements() ) {
                    final FrostMessageObject msg = en.nextElement();

                    if( msg.isNew() ) {
                        // recursively expands all children of the node and then the node itself
                        expandFirework(msg);
                    }
                }
            }
        } finally {
            afterBigChange();
        }
    }

    public void expandNode(final FrostMessageObject n) {
        Mixed.invokeNowOrLaterInDispatchThread(new Runnable() {
            public void run() {
                expandAll(new TreePath(n.getPath()), true);
            }
        });
    }

    /**
     * Selects the given message object in the tree view, if the message exists in the tree.
     * If the message is already selected, this re-establishes the selection and window scroll
     * position to make sure the user can see it.
     * Recommendation: If result < 0, treat it as failure; if result >= 0, treat it as success
     * @param FrostMessageObject theMsg - the message to select
     * @return int - success: 0 if the message was already selected, 1 if the message exists and
     * was selected by us; failure: -1 if the message does not exist (could mean that it's hidden
     * by the user's view-filter settings), -2 if the message exists but couldn't be selected
     * (should never be able to happen, though)
     */
    public int setSelectedMessage(final FrostMessageObject theMsg) {
        if( theMsg == null || theMsg.getMessageId() == null ){ return -1; } // no message provided, or it's a message without an id = message doesn't exist

        // loops through all tree nodes regardless of depth, looking for the message
        // NOTE: the "breadth" enumeration looks at node depth level 0 (root), then 1 (aka root's
        // children, top to bottom ordering), then 2 (all nodes that are under a child node), etc,
        // until the max depth is reached. this is the fastest method for quickly finding messages,
        // since they're often top-level (solo messages) or very shallow nesting, so this scan
        // method ensures that we check shallow threads before searching the deepest threads!
        final FrostMessageObject root = (FrostMessageObject) getTree().getModel().getRoot();
        final Enumeration<FrostMessageObject> en = root.breadthFirstEnumeration();
        while( en.hasMoreElements() ) {
            final FrostMessageObject node = en.nextElement();
            if( node == null ) { continue; } // skip table model nodes that don't refer to any object
            final String nodeMessageId = node.getMessageId(); // may be null, so we must validate it
            if( nodeMessageId != null && nodeMessageId.equals(theMsg.getMessageId()) ) {
                // grab the tree path for the discovered message node
                final TreePath msgPath = new TreePath(node.getPath());

                // check if the message is already selected (we'll still overwrite the selection;
                // this is just for return value purposes)
                final TreePath[] currentlySelectedPaths = getTree().getSelectionPaths(); // gets all selections in case of multi-message selections
                boolean wasAlreadySelected = false;
                if( currentlySelectedPaths != null ) {
                    for( TreePath p : currentlySelectedPaths ) {
                        if( p.equals(msgPath) ) {
                            wasAlreadySelected = true;
                            break;
                        }
                    }
                }

                // we found the message! we'll need to do the actual row-selection and scrolling
                // in a new job on the AWT event queue to avoid interrupting ongoing cell rendering.
                // NOTE: if the calling thread is not the AWT event queue, then we'll need to do
                // the actual board-selection in a new job on the AWT event queue, otherwise we'll
                // fuck up any ongoing AWT rendering of the tree and cause general mayhem.
                try {
                    Mixed.invokeNowInDispatchThread(new Runnable() {
                        public void run() {
                            // expands all messages and siblings in the thread, so that *everything*
                            // will be visible to the user. NOTE: since this is all happening in the
                            // AWT thread, the GUI redraw will be handled *instantly*
                            expandThread(true, node);

                            // determine which tree row contains the found node path
                            // NOTE: if this is -1, it means some of the elements in the path are
                            // still hidden under a collapsed parent; but since we've expanded the
                            // parents above, that should never be able to happen. however, we check
                            // for it just in case...
                            final int treeRowIdx = getRowForNode(node); // internally uses "getRowForPath(msgPath)" but has extra checks for root node
                            if( treeRowIdx >= 0 ) { // the message exists at row 0 or higher (a valid, visible row was found)
                                // select that exact table row; this selects the found message in the
                                // table view. it also removes any pre-existing selection, so that Frost
                                // is guaranteed to select only the desired message
                                setRowSelectionInterval(treeRowIdx, treeRowIdx);
                                // perform an intelligent scroll to the selected row + 3, which still
                                // ensures the target row is always in view
                                SmartSelection.applySmartScroll(MessageTreeTable.this, treeRowIdx, 3);
                            }
                        }
                    });
                } catch( Exception e ) {
                    return -2; // simply return "could not select" in case there was an error
                }

                // check if the node is now selected, if so return 0 ("was already selected")
                // or 1 ("now selected") depending on what happened, otherwise return -2 ("could not select")
                final TreePath newSelectedPath = getTree().getSelectionPath();
                return ( newSelectedPath.equals(msgPath) ? ( wasAlreadySelected ? 0 : 1 ) : -2 );
            }
        }

        return -1; // the message did not exist in our view of the board (it may be hidden via filtering, etc)
    }

    /**
     * Overridden to message super and forward the method to the tree.
     * Since the tree is not actually in the component hierarchy it will
     * never receive this unless we forward it in this manner.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        if(tree != null) {
            // sets the JTree to the current look&feel (basically calls setUI and replaces our current UI)
            tree.updateUI();
            // modifies the JTree look&feel UI to get the smart background-color renderer and indentation
            tree.setupTreeTableJTreeUIStyle();

            // Do this so that the editor is referencing the current renderer
            // from the tree. The renderer can potentially change each time laf changes.
            // setDefaultEditor(TreeTableModel.class, new TreeTableCellEditor());
        }
        // Use the L&F tree's default foreground and background colors in the table.
        LookAndFeel.installColorsAndFont(this, "Tree.background", "Tree.foreground", "Tree.font");
        // modify the JTable cell renderers and other properties to preserve our custom renderers and editors
        MessageTreeTable.this.setupTreeTableJTableUIStyle();
    }

    /**
     * Workaround for BasicTableUI anomaly. Make sure the UI never tries to
     * resize the editor. The UI currently uses different techniques to
     * paint the renderers and editors; overriding setBounds() below
     * is not the right thing to do for an editor. Returning -1 for the
     * editing row in this case, ensures the editor is never painted.
     */
    @Override
    public int getEditingRow() {
        return (getColumnClass(editingColumn) == TreeTableModel.class) ? -1 :
	        editingRow;
    }

    /**
     * Returns the actual row that is editing as <code>getEditingRow</code>
     * will always return -1.
     */
    private int realEditingRow() {
        return editingRow;
    }

    /**
     * This is overridden to invoke super's implementation, and then,
     * if the receiver is editing a Tree column, the editor's bounds is
     * reset. The reason we have to do this is because JTable doesn't
     * think the table is being edited, as <code>getEditingRow</code> returns
     * -1, and therefore doesn't automatically resize the editor for us.
     */
    @Override
    public void sizeColumnsToFit(final int resizingColumn) {
        super.sizeColumnsToFit(resizingColumn);
    	if (getEditingColumn() != -1 && getColumnClass(editingColumn) == TreeTableModel.class) {
    	    final Rectangle cellRect = getCellRect(realEditingRow(), getEditingColumn(), false);
            final Component component = getEditorComponent();
            component.setBounds(cellRect);
            component.validate();
    	}
    }

    /**
     * Overridden to pass the new rowHeight to the tree.
     */
    @Override
    public void setRowHeight(final int rowHeight) {
        super.setRowHeight(rowHeight);
        if (tree != null && tree.getRowHeight() != rowHeight) {
            tree.setRowHeight(getRowHeight());
        }
    }

    /**
     * Returns the tree that is being shared between the model.
     */
    public TreeTableCellRenderer getTree() {
        return tree;
    }

    // NOTE: the root node has been made invisible via "tree.setRootVisible(false)", so if you try to query
    // the root node, we'll forcibly return 0 here (even though it's not actually shown on screen).
    // all other nodes return their proper display-index; so the first child of root would be "0", etc
    // if this returns -1, it means that one or more of the parents of the given node are collapsed.
    public int getRowForNode(final FrostMessageObject n) {
        if(n.isRoot()) {
            return 0;
        }
        final TreePath tp = new TreePath(n.getPath());
        return tree.getRowForPath(tp);
    }

    public boolean isExpanded(final TreePath path) {
        return tree.isExpanded(path);
    }

    /**
     * Overridden to invoke repaint for the particular location if
     * the column contains the tree. This is done as the tree editor does
     * not fill the bounds of the cell, we need the renderer to paint
     * the tree in the background, and then draw the editor over it.
     */
    @Override
    public boolean editCellAt(final int row, final int column, final EventObject e){
    	final boolean retValue = super.editCellAt(row, column, e);
    	if (retValue && getColumnClass(column) == TreeTableModel.class) {
    	    repaint(getCellRect(row, column, false));
    	}
    	return retValue;
    }

    /**
     * A TreeCellRenderer that displays a JTree.
     */
	public class TreeTableCellRenderer extends JTree implements TableCellRenderer {
    	/** Last table/tree row asked to renderer. */
    	protected int visibleRow;

        private Font boldFont = null;
        private Font normalFont = null;
        private boolean isDeleted = false;

        private String toolTipText = null;

        public TreeTableCellRenderer(final TreeModel model) {
            super(model);
            // disable the "double-click to expand/collapse" to avoid annoyance
            TreeTableCellRenderer.this.setToggleClickCount(0);

            // modifies the look&feel UI to get the smart background-color renderer and indentation
            setupTreeTableJTreeUIStyle();
        }

        // only to be called by constructor, and updateUI (when look&feel changes)
        // we *must* call this when the L&F changes, since the change of skin unbinds our custom renderers
        public void setupTreeTableJTreeUIStyle() {
            final Font baseFont = MessageTreeTable.this.getFont();
            normalFont = baseFont.deriveFont(Font.PLAIN);
            boldFont = baseFont.deriveFont(Font.BOLD);

            // modifies the current look&feel UI to render row backgrounds properly
            // NOTE: we always create a new GUI (and cell renderer) since the old one can have outdated layout caches
            setUI(new NiceTreeUI());

            // changes the amount of JTree indentation used by each look&feel to a uniform value
            if( getUI() instanceof NiceTreeUI ) {
                final NiceTreeUI treeUI = (NiceTreeUI)getUI();
//                System.out.println("1:"+treeUI.getLeftChildIndent()); // default 7
//                System.out.println("2:"+treeUI.getRightChildIndent());// default 13
                treeUI.setLeftChildIndent(6);
                treeUI.setRightChildIndent(10);
            }

            // custom cell renderer draws a line through deleted posts
            setCellRenderer(new OwnTreeCellRenderer());
        }

        /**
         * Updates the expansion icon via the GUI thread whenever the property changes.
         */
        public void updateExpansionIcon() {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if( !(tree.getUI() instanceof NiceTreeUI) ) { return; }
                    final NiceTreeUI treeUI = (NiceTreeUI)tree.getUI();
                    treeUI.updateExpansionIcon();
                }
            });
        }

        /**
         * Properly renders the background color for the whole JTree row instead of just under the text label.
         *
         * This fixes all of the Look&Feels (such as the GTK/Ubuntu one) that otherwise look
         * broken due to painting a white background over the tree component before painting
         * its rows. So far I've only seen this affect the GTK/Ubuntu L&F, but it needed fixing.
         *
         * The reason this fix works is that the BasicTreeUI (unlike normal UIs) *never* renders
         * any background, selection or foreground colors, so the tree itself actually has a
         * transparent background. And since it sits on top of the JTable (in the TreeTable),
         * it simply renders on top of the proper table row color. This is unlike the normal
         * JTree L&F-UI, which in some L&Fs fills the background with white before rendering
         * its own rows. This switch to a BasicTreeUI avoids the white-fill.
         * NOTE: The only time a BasicTreeUI draws a background is during "drag and drop", when
         * you drag a row and drop it somewhere else, in which case it draws a 2px tall background
         * line which indicates the drop location. That's fine and won't interfere. :-)
         *
         * This "basic" TreeUI also lacks L&F-specific details like graphics for expansion handles
         * unless the L&F has set the global UI defaults ("Tree.expandedIcon" etc), which means
         * that we must implement the expansion icons ourselves to ensure that they're visible.
         * That gave us a perfect opportunity to spice things up by *always* rendering our own,
         * custom node-lines and expansion icons unique to Frost, for a unique, sexy look!
         */
        public class NiceTreeUI extends BasicTreeUI
        {
            private Icon treeNodeCollapsedIcon = null;
            private Icon treeNodeExpandedIcon = null;

            public NiceTreeUI()
            {
                super();
                // install our custom colors and icons
                this.setCustomLook();
            }

            @Override
            public Icon getCollapsedIcon()
            {
                return this.treeNodeCollapsedIcon;
            }

            @Override
            public Icon getExpandedIcon()
            {
                return this.treeNodeExpandedIcon;
            }

            public boolean getIsLargeModel() { return isLargeModel(); }

            @Override
            protected void installDefaults()
            {
                // this is the function where the underlying BasicTreeUI's icons are set
                // to the ones from the current L&F's defaults (via "Tree.expandedIcon", etc)
                // so we simply set them here afterwards to thoroughly enforce the new icons,
                // even though we've already overridden the getters (which is technically enough).
                super.installDefaults();
                if( treeNodeCollapsedIcon == null || treeNodeExpandedIcon == null ) {
                    this.setCustomLook();
                }
                this.setCollapsedIcon(treeNodeCollapsedIcon);
                this.setExpandedIcon(treeNodeExpandedIcon);
            }

            /**
             * IMPORTANT: After the table has been created, we can ONLY call this from the GUI thread!
             * Otherwise we risk nullpointer exceptions, since the GUI may be drawing the old icons.
             */
            private void setCustomLook()
            {
                // set all connecting node-lines to a light bluish color
                // NOTE: almost every L&F draws the horizontal/vertical tree legs, but some
                //   don't (notably Nimbus). there's nothing we can do about that without
                //   totally re-implementing paint(), which is a huge task and not worth it.
                // ALSO NOTE: we aren't overriding the "Tree.hash" global UI defaults,
                //   since that would affect ALL trees.
                final Color hashColor = new Color(184, 207, 229);
                this.setHashColor(hashColor);

                // determine what type of icon to use
                final int iconType = getExpansionIconType();

                // design the '+' and '-' icons and draw them in a slightly darker bluish gray
                treeNodeCollapsedIcon = IconGenerator.generateTreeExpansionIcon(iconType, '+', Color.WHITE, hashColor.darker());
                treeNodeExpandedIcon = IconGenerator.generateTreeExpansionIcon(iconType, '-', Color.WHITE, hashColor.darker());
            }

            /**
             * Determines the integer value of the expansion icon type (falls back to 0 as default).
             */
            private int getExpansionIconType()
            {
                // parse the icon type from the setting-string (fall back to circles if invalid)
                int iconType = 0;
                String iconStr = Core.frostSettings.getValue(SettingsClass.TREE_EXPANSION_ICON);
                if( iconStr != null ) {
                    try {
                        iconStr = iconStr.replace("Options.display.treeExpansionIcon.style", "");
                        iconType = Integer.parseInt(iconStr, 10);
                    } catch( final Exception e ) {
                        iconType = 0;
                    }
                }
                if( iconType < 0 || iconType > 3 ) {
                    iconType = 0; // clamp invalid values back to 0
                }
                return iconType;
            }

            /**
             * Does a live refresh of the table. Useful when the user is switching icon types.
             */
            public void updateExpansionIcon()
            {
                // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
                Mixed.invokeNowInDispatchThread(new Runnable() {
                    public void run() {
                        // set the new icons and force the JTree and JTable to repaint
                        // NOTE: validate marks ALL contents as invalid and lays it out again, paint refreshes.
                        NiceTreeUI.this.setCustomLook();
                        if( tree != null ) {
                            tree.validate();
                            tree.repaint();
                            MessageTreeTable.this.validate();
                            MessageTreeTable.this.repaint();
                        }
                    }
                });
            }
        }

        public void fontChanged(final Font font) {
            normalFont = font.deriveFont(Font.PLAIN);
            boldFont = font.deriveFont(Font.BOLD);
        }

        @Override
        public void processKeyEvent(final KeyEvent e) {
            super.processKeyEvent(e);
        }

        class OwnTreeCellRenderer extends DefaultTreeCellRenderer {
            int treeWidth;
            public OwnTreeCellRenderer() {
                super();
                setVerticalAlignment(CENTER);
            }
            @Override
            public Component getTreeCellRendererComponent(
                    final JTree lTree,
                    final Object value,
                    final boolean sel,
                    final boolean expanded,
                    final boolean leaf,
                    final int row,
                    final boolean lHasFocus)
            {
                treeWidth = lTree.getWidth();
                return super.getTreeCellRendererComponent(lTree, value, sel, expanded, leaf, row, lHasFocus);
            }
            @Override
            public void paint(final Graphics g) {
                setSize(new Dimension(treeWidth - this.getBounds().x, this.getSize().height));
                super.paint(g);
                if(isDeleted) {
                    final Dimension size = getSize();
                    g.drawLine(0, size.height / 2, size.width, size.height / 2);
                }
            }
        }

    	/**
    	 * updateUI is overridden to set the colors of the Tree's renderer
    	 * to match that of the table.
    	 */
    	@Override
        public void updateUI() {
    	    super.updateUI();
    	    // Make the tree's cell renderer use the table's cell selection
    	    // colors.
    	    final TreeCellRenderer tcr = getCellRenderer();
    	    if (tcr instanceof DefaultTreeCellRenderer) {
        		final DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer)tcr);
        		// For 1.1 uncomment this, 1.2 has a bug that will cause an
        		// exception to be thrown if the border selection color is null.
        		// dtcr.setBorderSelectionColor(null);
        		dtcr.setTextSelectionColor(UIManager.getColor("Table.selectionForeground"));
        		dtcr.setBackgroundSelectionColor(UIManager.getColor("Table.selectionBackground"));
    	    }
    	}

        public void setDeleted(final boolean value) {
            isDeleted = value;
        }

    	/**
    	 * Sets the row height of the tree, and forwards the row height to
    	 * the table.
    	 */
    	@Override
        public void setRowHeight(final int rowHeight) {
    	    if (rowHeight > 0) {
        		super.setRowHeight(rowHeight);
        		if (MessageTreeTable.this != null &&
        		    MessageTreeTable.this.getRowHeight() != rowHeight) {
        		    MessageTreeTable.this.setRowHeight(getRowHeight());
        		}
    	    }
    	}

    	/**
    	 * This is overridden to set the height to match that of the JTable.
    	 */
    	@Override
        public void setBounds(final int x, final int y, final int w, final int h) {
    	    super.setBounds(x, 0, w, MessageTreeTable.this.getHeight());
    	}

    	/**
    	 * Sublcassed to translate the graphics such that the last visible
    	 * row will be drawn at 0,0.
    	 */
    	@Override
        public void paint(final Graphics g) {
    	    g.translate(0, -visibleRow * getRowHeight());
    	    super.paint(g);
    	}

    	/**
    	 * TreeCellRenderer method. Overridden to update the visible row.
    	 */
    	public Component getTableCellRendererComponent(final JTable table,
    						       final Object value,
    						       final boolean isSelected,
    						       final boolean hasFocus,
    						       final int row, final int column)
    	{
    	    Color background;
    	    Color foreground;

            final FrostMessageObject msg = (FrostMessageObject)MessageTreeTable.this.model.getRow(row); // NOTE: msg can be null in rare cases

            // first set font, bold for new msg or normal
            if( msg != null && msg.isNew() ) {
                setFont(boldFont);
            } else {
                setFont(normalFont);
            }

            // now set foreground color (used for the subject-JTree)
            // NOTE: this is only applied if the item isn't currently selected
            foreground = getMessageTypeColor(msg);

            if (!isSelected) {
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                background = newBackground;
            } else {
                background = table.getSelectionBackground();
                foreground = table.getSelectionForeground();
            }

            setDeleted( ( msg != null ? msg.isDeleted() : false ) );

    	    visibleRow = row;
    	    setBackground(background);

    	    final TreeCellRenderer tcr = getCellRenderer();
    	    if (tcr instanceof DefaultTreeCellRenderer) {

        		final DefaultTreeCellRenderer dtcr = ((DefaultTreeCellRenderer)tcr);
        		if (isSelected) {
        		    dtcr.setTextSelectionColor(foreground);
        		    dtcr.setBackgroundSelectionColor(background);
        		} else {
        		    dtcr.setTextNonSelectionColor(foreground);
        		    dtcr.setBackgroundNonSelectionColor(background);
        		}

                dtcr.setBorder(null);
                if( msg != null && ((FrostMessageObject)msg.getParent()).isRoot() ) {
                    final boolean[] hasUnreadOrMarked = msg.hasUnreadOrMarkedChilds();
                    final boolean hasUnread = hasUnreadOrMarked[0];
                    final boolean hasMarked = hasUnreadOrMarked[1];
                    if( hasUnread && !hasMarked ) {
                        // unread and no marked
                        dtcr.setBorder(borderUnreadMsgsInThread);
                    } else if( !hasUnread && hasMarked ) {
                        // no unread and marked
                        dtcr.setBorder(borderMarkedMsgsInThread);
                    } else if( !hasUnread && !hasMarked ) {
                        // nothing
                        dtcr.setBorder(borderEmpty);
                    } else {
                        // both
                        dtcr.setBorder(borderUnreadAndMarkedMsgsInThread);
                    }
                }

                final ImageIcon icon;
                if( msg != null && msg.isDummy() ) {
                    icon = messageDummyIcon;
//                    dtcr.setToolTipText(null);
                    if( msg.getSubject() != null && msg.getSubject().length() > 0 ) {
                        setToolTipText(msg.getSubject());
                    } else {
                        setToolTipText(null);
                    }
                } else {
                    if( msg != null && msg.isNew() ) {
                        if( msg.isReplied() ) {
                            icon = messageNewRepliedIcon;
                        } else {
                            icon = messageNewIcon;
                        }
                    } else {
                        if( msg != null && msg.isReplied() ) {
                            icon = messageReadRepliedIcon;
                        } else {
                            icon = messageReadIcon;
                        }
                    }
//                    dtcr.setToolTipText( ( msg != null ? msg.getSubject() : "" ) );
                    setToolTipText( ( msg != null ? msg.getSubject() : "" ) );
                }
                dtcr.setIcon(icon);
                dtcr.setLeafIcon(icon);
                dtcr.setOpenIcon(icon);
                dtcr.setClosedIcon(icon);
    	    }

    	    return this;
    	}
        @Override
        public void setToolTipText(final String t) {
            toolTipText = t;
        }
        /**
         * Override to always return a tooltext for the table column.
         */
        @Override
        public String getToolTipText(final MouseEvent event) {
            return toolTipText;
        }
        }

        /**
         * ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel
         * to listen for changes in the ListSelectionModel it maintains. Once
         * a change in the ListSelectionModel happens, the paths are updated
         * in the DefaultTreeSelectionModel.
         */
		class ListToTreeSelectionModelWrapper extends DefaultTreeSelectionModel {
    	/** Set to true when we are updating the ListSelectionModel. */
    	protected boolean         updatingListSelectionModel;

    	public ListToTreeSelectionModelWrapper() {
    	    super();
    	    getListSelectionModel().addListSelectionListener(createListSelectionListener());
    	}

    	/**
    	 * Returns the list selection model. ListToTreeSelectionModelWrapper
    	 * listens for changes to this model and updates the selected paths
    	 * accordingly.
    	 */
    	ListSelectionModel getListSelectionModel() {
    	    return listSelectionModel;
    	}

    	/**
    	 * This is overridden to set <code>updatingListSelectionModel</code>
    	 * and message super. This is the only place DefaultTreeSelectionModel
    	 * alters the ListSelectionModel.
    	 */
    	@Override
        public void resetRowSelection() {
    	    if(!updatingListSelectionModel) {
        		updatingListSelectionModel = true;
        		try {
    //                super.resetRowSelection();
        		}
        		finally {
        		    updatingListSelectionModel = false;
        		}
    	    }
    	    // Notice how we don't message super if
    	    // updatingListSelectionModel is true. If
    	    // updatingListSelectionModel is true, it implies the
    	    // ListSelectionModel has already been updated and the
    	    // paths are the only thing that needs to be updated.
    	}

    	/**
    	 * Creates and returns an instance of ListSelectionHandler.
    	 */
    	protected ListSelectionListener createListSelectionListener() {
    	    return new ListSelectionHandler();
    	}

    	/**
    	 * If <code>updatingListSelectionModel</code> is false, this will
    	 * reset the selected paths from the selected rows in the list
    	 * selection model.
    	 */
    	protected void updateSelectedPathsFromSelectedRows() {
    	    if(!updatingListSelectionModel) {
        		updatingListSelectionModel = true;
        		try {
        		    // This is way expensive, ListSelectionModel needs an enumerator for iterating
        		    final int min = listSelectionModel.getMinSelectionIndex();
        		    final int max = listSelectionModel.getMaxSelectionIndex();

        		    clearSelection();
        		    if(min != -1 && max != -1) {
            			for(int counter = min; counter <= max; counter++) {
            			    if(listSelectionModel.isSelectedIndex(counter)) {
                				final TreePath selPath = tree.getPathForRow(counter);
                				if(selPath != null) {
                				    addSelectionPath(selPath);
                				}
            			    }
            			}
        		    }
        		}
        		finally {
        		    updatingListSelectionModel = false;
        		}
    	    }
    	}

    	/**
    	 * Class responsible for calling updateSelectedPathsFromSelectedRows
    	 * when the selection of the list changse.
    	 */
    	class ListSelectionHandler implements ListSelectionListener {
    	    public void valueChanged(final ListSelectionEvent e) {
    	        updateSelectedPathsFromSelectedRows();
    	    }
    	}
    }

	private class BooleanCellRenderer extends JLabel implements TableCellRenderer {

        public BooleanCellRenderer() {
            super();
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
        }

        @Override
        public void paintComponent (final Graphics g) {
            final Dimension size = getSize();
            g.setColor(getBackground());
            g.fillRect(0, 0, size.width, size.height);
            super.paintComponent(g);
        }

        public Component getTableCellRendererComponent(
                final JTable table,
                final Object value,
                final boolean isSelected,
                final boolean hasFocus,
                final int row,
                int column)
        {
            // if the value is a Boolean, it means we do/don't want to have an icon for the column; however,
            // sometimes the value is a string (for all non-icon columns), so in that case we set it to false manually
            final boolean wantIcon = ( (value instanceof Boolean) ? ((Boolean)value).booleanValue() : false );

            // get the original model column index (maybe columns were reordered by user)
            final TableColumn tableColumn = getColumnModel().getColumn(column);
            column = tableColumn.getModelIndex();

            ImageIcon iconToSet = null;

            if( wantIcon == true ) {
                if( column == MessageTreeTableModel.COLUMN_INDEX_FLAGGED ) {
                    iconToSet = flaggedIcon;
                } else if( column == MessageTreeTableModel.COLUMN_INDEX_STARRED ) {
                    iconToSet = starredIcon;
                } else if( column == MessageTreeTableModel.COLUMN_INDEX_JUNK ) {
                    iconToSet =  junkIcon;
                }
            }

            setIcon(iconToSet);

            if (!isSelected) {
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                setBackground(newBackground);
            } else {
                setBackground(table.getSelectionBackground());
            }

            return this;
        }
    }

    @Override
    public void setFont(final Font font) {
        super.setFont(font);

        if( stringCellRenderer != null ) {
            stringCellRenderer.fontChanged(font);
        }
        if( tree != null ) {
            tree.fontChanged(font);
        }
        repaint();
    }

    /**
     * This renderer renders rows in different colors.
     * New messages gets a bold look, messages with attachments a blue color.
     * Encrypted messages get a red color, no matter if they have attachments.
     */
	private class StringCellRenderer extends DefaultTableCellRenderer {

        private Font boldFont;
        private Font boldItalicFont;
        private Font normalFont;
        private boolean isDeleted = false;
        private final Color col_BAD       = TrustStateColors.BAD;
        private final Color col_NEUTRAL   = TrustStateColors.NEUTRAL;
        private final Color col_GOOD      = TrustStateColors.GOOD;
        private final Color col_FRIEND    = TrustStateColors.FRIEND;
        final javax.swing.border.EmptyBorder border = new javax.swing.border.EmptyBorder(0, 0, 0, 3);

        public StringCellRenderer() {
            setVerticalAlignment(CENTER);
            final Font baseFont = MessageTreeTable.this.getFont();
            fontChanged( baseFont );
        }

        @Override
        public void paintComponent (final Graphics g) {
            super.paintComponent(g);
            if(isDeleted) {
                final Dimension size = getSize();
                g.drawLine(0, size.height / 2, size.width, size.height / 2);
            }
        }

        public void fontChanged(final Font font) {
            normalFont = font.deriveFont(Font.PLAIN);
            boldFont = font.deriveFont(Font.BOLD);
            boldItalicFont = font.deriveFont(Font.BOLD|Font.ITALIC);
        }

        // NOTE: All renderer component constructors (such as this one) are called once when the
        // cell first becomes visible on screen, and then repeatedly while hovering over the cell,
        // moving the cursor while checking its tooltip, or while the row is being updated, etc...
        // It would be pointless to try to move the tooltip generation to a deferred renderer,
        // since all of the other work below runs every time anyway. Java can easily handle a quarter
        // of a million StringBuilder operations per second on a normal computer, so this is not
        // a bottleneck. And unlike the download/upload tables (which constantly update their rows
        // and re-trigger the constructor), this message tree table doesn't run its constructor
        // very often at all.
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            int column)
        {
            super.getTableCellRendererComponent(table, value, isSelected, /*hasFocus*/ false, row, column);

            if (!isSelected) {
                // unselected rows get the alternating "zebra stripe" pattern if "show colored rows in table"
                // is enabled by the user. otherwise it just gets the plain/normal background color of that L&F.
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                setBackground(newBackground);
            } else {
                // selected rows get the regular table selection background color
                setBackground(table.getSelectionBackground());
            }

            // setup defaults
            setAlignmentY(CENTER_ALIGNMENT);
            setFont(normalFont);
            if (!isSelected) {
                setForeground(Color.BLACK); // all columns default to black text (if not selected)
            }
            setToolTipText(null);
            setBorder(null);
            setHorizontalAlignment(SwingConstants.LEFT);
            setIcon(null);

            Object obj = MessageTreeTable.this.model.getRow(row);
            if( !(obj instanceof FrostMessageObject) ) {
                return this; // paranoia
            }

            final FrostMessageObject msg = (FrostMessageObject) obj;
            obj = null;

            // get the original model column index (maybe columns were reordered by user)
            column = getColumnModel().getColumn(column).getModelIndex();

            // do nice things for FROM and SIG column
            if( column == MessageTreeTableModel.COLUMN_INDEX_FROM ) {
                // FROM
                // first set font, bold for new msg or normal
                if (msg.isNew()) {
                    setFont(boldFont);
                }
                // now set color
                if (!isSelected) {
                    setForeground(getMessageTypeColor(msg));
                }
                if( !msg.isDummy() ) {
                    if( msg.isSignatureStatusVERIFIED() ) {
                        final Identity id = msg.getFromIdentity();
                        if( id == null ) {
                            logger.severe("getFromidentity() is null for fromName: '"+msg.getFromName()+"', "+
                                    "board="+msg.getBoard().getName()+", msgDate="+msg.getDateAndTimeString()+
                                    ", index="+msg.getIndex());
                            setToolTipText((String)value);
                        } else {
                            // build informative tooltip
                            final StringBuilder sb = new StringBuilder();
                            sb.append("<html>");
                            sb.append((String)value);
                            sb.append("<br>Last seen: ");
                            sb.append(DateFun.FORMAT_DATE_VISIBLE.print(id.getLastSeenTimestamp()));
                            sb.append("  ");
                            sb.append(DateFun.FORMAT_TIME_VISIBLE.print(id.getLastSeenTimestamp()));
                            sb.append("<br>Received messages: ").append(id.getReceivedMessageCount());
                            sb.append("</html>");
                            setToolTipText(sb.toString());

                            // provide colored icons
                            if( indicateLowReceivedMessages ) {
                                final int receivedMsgCount = id.getReceivedMessageCount();
                                if( receivedMsgCount <= indicateLowReceivedMessagesCountRed ) {
                                    setIcon(receivedOneMessage);
                                } else if( receivedMsgCount <= indicateLowReceivedMessagesCountBlue ) {
                                    setIcon(receivedFiveMessages);
                                }
                            }
                        }
                    } else {
                        setToolTipText((String)value);
                    }
                }
            } else if( column == MessageTreeTableModel.COLUMN_INDEX_INDEX ) {
                // index column, right aligned
                setHorizontalAlignment(SwingConstants.RIGHT);
                // col is right aligned, give some space to next column
                setBorder(border);
            } else if( column == MessageTreeTableModel.COLUMN_INDEX_SIG ) {
                // SIG
                // state == BAD/NEUTRAL/GOOD/FRIEND -> bold and colored
                final Font f;
                if( msg.isSignatureStatusVERIFIED_V2() ) {
                    f = boldFont;
                } else {
                    f = boldItalicFont;
                }
                if( msg.isMessageStatusNEUTRAL() ) {
                    setFont(f);
                    setForeground(col_NEUTRAL);
                } else if( msg.isMessageStatusGOOD() ) {
                    setFont(f);
                    setForeground(col_GOOD);
                } else if( msg.isMessageStatusFRIEND() ) {
                    setFont(f);
                    setForeground(col_FRIEND);
                } else if( msg.isMessageStatusBAD() ) {
                    setFont(f);
                    setForeground(col_BAD);
                } else if( msg.isMessageStatusTAMPERED() ) {
                    setFont(f);
                    setForeground(col_BAD);
                }
            }

            setDeleted(msg.isDeleted());

            return this;
        }

//        public void setFont(Font font) {
//            super.setFont(font);
//            normalFont = font.deriveFont(Font.PLAIN);
//            boldFont = font.deriveFont(Font.BOLD);
//        }

        public void setDeleted(final boolean value) {
            isDeleted = value;
        }
    }

	public class TreeTableCellEditor extends DefaultCellEditor {
        public TreeTableCellEditor() {
            super(new JCheckBox());
        }

        /**
         * Overridden to determine an offset that tree would place the
         * editor at. The offset is determined from the
         * <code>getRowBounds</code> JTree method, and additionally
         * from the icon DefaultTreeCellRenderer will use.
         * <p>The offset is then set on the TreeTableTextField component
         * created in the constructor, and returned.
         */
        @Override
        public Component getTableCellEditorComponent(
                final JTable table,
                final Object value,
                final boolean isSelected,
                final int r, final int c) {
            final Component component = super.getTableCellEditorComponent(table, value, isSelected, r, c);
            final JTree t = getTree();
            final boolean rv = t.isRootVisible();
            final int offsetRow = rv ? r : r - 1;
            final Rectangle bounds = t.getRowBounds(offsetRow);
            int offset = bounds.x;
            final TreeCellRenderer tcr = t.getCellRenderer();
            if (tcr instanceof DefaultTreeCellRenderer) {
            final Object node = t.getPathForRow(offsetRow).getLastPathComponent();
            Icon icon;
            if (t.getModel().isLeaf(node)) {
                icon = ((DefaultTreeCellRenderer)tcr).getLeafIcon();
            } else if (tree.isExpanded(offsetRow)) {
                icon = ((DefaultTreeCellRenderer)tcr).getOpenIcon();
            } else {
                icon = ((DefaultTreeCellRenderer)tcr).getClosedIcon();
            }
            if (icon != null) {
                offset += ((DefaultTreeCellRenderer)tcr).getIconTextGap() +
                      icon.getIconWidth();
            }
            }
//            ((TreeTableTextField)getComponent()).offset = offset;
            return component;
        }

        /**
         * This is overridden to forward the event to the tree. This will
         * return true if the click count >= 3, or the event is null.
         */
        @Override
        public boolean isCellEditable(final EventObject e) {
            if (e instanceof MouseEvent) {
                final MouseEvent me = (MouseEvent)e;
                if (me.getModifiers() == 0 || me.getModifiers() == InputEvent.BUTTON1_MASK) {
                    for (int counter = getColumnCount() - 1; counter >= 0; counter--) {
                        if (getColumnClass(counter) == TreeTableModel.class) {
                            final MouseEvent newME = new MouseEvent(
                                    MessageTreeTable.this.tree,
                                    me.getID(),
                                    me.getWhen(),
                                    me.getModifiers(),
                                    me.getX() - getCellRect(0, counter, true).x,
                                    me.getY(),
                                    me.getClickCount(),
                                    me.isPopupTrigger());
                            MessageTreeTable.this.tree.dispatchEvent(newME);
                            break;
                        }
                    }
                }
            }
            return false;
        }
        }

    /**
     * Save the current column positions and column sizes for restore on next startup.
     */
    public void saveLayout(final SettingsClass frostSettings) {
        final TableColumnModel tcm = getColumnModel();
        for(int columnIndexInTable=0; columnIndexInTable < tcm.getColumnCount(); columnIndexInTable++) {
            final TableColumn tc = tcm.getColumn(columnIndexInTable);
            final int columnIndexInModel = tc.getModelIndex();
            // save the current index in table for column with the fix index in model
            frostSettings.setValue("MessageTreeTable.tableindex.modelcolumn."+columnIndexInModel, columnIndexInTable);
            // save the current width of the column
            final int columnWidth = tc.getWidth();
            frostSettings.setValue("MessageTreeTable.columnwidth.modelcolumn."+columnIndexInModel, columnWidth);
        }
    }

    /**
     * Load the saved column positions and column sizes.
     */
    public void loadLayout(final SettingsClass frostSettings) {
        final TableColumnModel tcm = getColumnModel();
        fixedsizeColumns = new HashSet<Integer>();

        // hard set sizes of icons column
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_FLAGGED).setMinWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_FLAGGED).setMaxWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_FLAGGED).setPreferredWidth(20);
        fixedsizeColumns.add(MessageTreeTableModel.COLUMN_INDEX_FLAGGED);
        // hard set sizes of icons column
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_STARRED).setMinWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_STARRED).setMaxWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_STARRED).setPreferredWidth(20);
        fixedsizeColumns.add(MessageTreeTableModel.COLUMN_INDEX_STARRED);
        // hard set sizes of icons column
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_JUNK).setMinWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_JUNK).setMaxWidth(20);
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_JUNK).setPreferredWidth(20);
        fixedsizeColumns.add(MessageTreeTableModel.COLUMN_INDEX_JUNK);

        // set icon table header renderer for icon columns
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_FLAGGED).setHeaderRenderer(
                new IconTableHeaderRenderer(flaggedIcon, "Flagged"));
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_STARRED).setHeaderRenderer(
                new IconTableHeaderRenderer(starredIcon, "Starred"));
        tcm.getColumn(MessageTreeTableModel.COLUMN_INDEX_JUNK).setHeaderRenderer(
                new IconTableHeaderRenderer(junkIcon, "Junk"));

        if( !loadLayout(frostSettings, tcm) ) {
            // Sets the relative widths of the columns
            final int[] widths = {
                20, // flagged (fixed size)
                20, // starred (fixed size)
                350, // subject
                160, // from
                40, // signature
                100, // date
                20, // junk (fixed size)
                30 // index
            };
            for (int i = 0; i < widths.length; i++) {
                tcm.getColumn(i).setPreferredWidth(widths[i]);
            }
        }
    }

    private boolean loadLayout(final SettingsClass frostSettings, final TableColumnModel tcm) {
        // load the saved tableindex for each column in model, and its saved width
        final int[] tableToModelIndex = new int[tcm.getColumnCount()];
        final int[] columnWidths = new int[tcm.getColumnCount()];

        for(int x=0; x < tableToModelIndex.length; x++) {
            final String indexKey = "MessageTreeTable.tableindex.modelcolumn."+x;
            if( frostSettings.getObjectValue(indexKey) == null ) {
                return false; // column not found, abort
            }
            // build array of table to model associations
            final int tableIndex = frostSettings.getIntValue(indexKey);
            if( tableIndex < 0 || tableIndex >= tableToModelIndex.length ) {
                return false; // invalid table index value
            }
            tableToModelIndex[tableIndex] = x;

            final String widthKey = "MessageTreeTable.columnwidth.modelcolumn."+x;
            if( frostSettings.getObjectValue(widthKey) == null ) {
                return false; // column not found, abort
            }
            // build array of table to model associations
            final int columnWidth = frostSettings.getIntValue(widthKey);
            if( columnWidth <= 0 ) {
                return false; // invalid column width
            }
            columnWidths[x] = columnWidth;
        }
        // columns are currently added in model order, remove them all and save in an array
        // while on it, set the loaded width of each column
        final TableColumn[] tcms = new TableColumn[tcm.getColumnCount()];
        for(int x=tcms.length-1; x >= 0; x--) {
            tcms[x] = tcm.getColumn(x);
            tcm.removeColumn(tcms[x]);
            // keep the fixed-size columns as is
            if( ! fixedsizeColumns.contains(x) ) {
                tcms[x].setPreferredWidth(columnWidths[x]);
            }
        }
        // add the columns in order loaded from settings
        for( final int element : tableToModelIndex ) {
            tcm.addColumn(tcms[element]);
        }
        return true;
    }

    /**
     * Resort table based on settings in SortStateBean
     */
    public void resortTable() {
        if( MessageTreeTableSortStateBean.isThreaded() ) {
            return;
        }
        final FrostMessageObject root = (FrostMessageObject) getTree().getModel().getRoot();
        root.resortChildren();
        ((DefaultTreeModel)getTree().getModel()).reload();
    }

    private void fontChanged() {
        final String fontName = Core.frostSettings.getValue(SettingsClass.MESSAGE_LIST_FONT_NAME);
        final int fontStyle = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_STYLE);
        final int fontSize = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_SIZE);
        Font font = new Font(fontName, fontStyle, fontSize);
        if (!font.getFamily().equals(fontName)) {
//            logger.severe(
//                "The selected font was not found in your system\n"
//                    + "That selection will be changed to \"Monospaced\".");
            Core.frostSettings.setValue(SettingsClass.MESSAGE_LIST_FONT_NAME, "Monospaced");
            font = new Font("Monospaced", fontStyle, fontSize);
        }
        // adjust row height to font size, add a margin
        setRowHeight(Math.max(fontSize + ROW_HEIGHT_MARGIN, MINIMUM_ROW_HEIGHT));

        setFont(font);
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SettingsClass.SHOW_COLORED_ROWS)) {
            showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
        } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_NAME)) {
            fontChanged();
        } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_SIZE)) {
            fontChanged();
        } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_STYLE)) {
            fontChanged();
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES)) {
            indicateLowReceivedMessages = Core.frostSettings.getBoolValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES);
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED)) {
            indicateLowReceivedMessagesCountRed = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED);
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE)) {
            indicateLowReceivedMessagesCountBlue = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE);
        } else if (evt.getPropertyName().equals(SettingsClass.TREE_EXPANSION_ICON)) {
            tree.updateExpansionIcon();
        } else if( evt.getPropertyName().equals("MessageColorsChanged") ) {
            updateMessageColors();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    // executed on swing since we can't guarantee that the event came from EDT
                    // repaints all visible rows, which forces their cell renderers to reconstruct.
                    // this method doesn't fire any "table data changed", thus preserving the user's selection.
                    if( tree != null ) {
                        tree.validate();
                        tree.repaint();
                    }
                    MessageTreeTable.this.validate();
                    MessageTreeTable.this.repaint();
                }
            });
        }
    }
}
