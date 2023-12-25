/*
  TOF.java / Frost
  Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.frost.boards;
import java.awt.*;
import java.beans.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.tree.*;

import org.joda.time.*;

import frost.*;
import frost.gui.*;
import frost.identities.*;
import frost.messaging.frost.*;
import frost.messaging.frost.gui.messagetreetable.*;
import frost.storage.*;
import frost.storage.perst.messages.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.SmartSelection;
import frost.util.gui.translation.*;

/**
 * @pattern Singleton
 */
public class TOF implements PropertyChangeListener {

    // ATTN: if a new message arrives during update of a board, the msg cannot be inserted into db because
    //       the methods are synchronized. So the add of msg occurs after the load of the board.
    //       there is no sync problem.

    private static final Logger logger = Logger.getLogger(TOF.class.getName());

    private static final Language language = Language.getInstance();

    // these two are != null whenever a "load messages into tree" thread is running
    private volatile UpdateTofFilesThread updateThread = null;
    private volatile UpdateTofFilesThread nextUpdateThread = null;
    // this is true whenever the GUI is working on rendering the newly added treetable rows
    private volatile boolean isRedrawingGui = false;

    private final TofTreeModel tofTreeModel;

    private static boolean initialized = false;

    private boolean showJunkMessages;

    /**
     * The unique instance of this class.
     */
    private static TOF instance = null;

    /**
     * Return the unique instance of this class.
     *
     * @return the unique instance of this class
     */
    public static TOF getInstance() {
        return instance;
    }

    /**
     * Prevent instances of this class from being created from the outside.
     * @param tofTreeModel this is the TofTreeModel this TOF will operate on
     */
    private TOF(final TofTreeModel tofTreeModel) {
        super();
        this.tofTreeModel = tofTreeModel;
        showJunkMessages = Core.frostSettings.getBoolValue(SettingsClass.SHOW_JUNK_MESSAGES);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.SHOW_JUNK_MESSAGES, this);
    }

    /**
     * This method initializes the TOF.
     * If it has already been initialized, this method does nothing.
     * @param tofTreeModel this is the TofTreeModel this TOF will operate on
     */
    public static void initialize(final TofTreeModel tofTreeModel) {
        if (!initialized) {
            initialized = true;
            instance = new TOF(tofTreeModel);
        }
    }

    public void markAllMessagesRead(final AbstractNode node) {
        markAllMessagesRead(node, true);
    }

    private void markAllMessagesRead(final AbstractNode node, final boolean confirm) {
        if (node == null) {
            return;
        }

        if (node.isBoard()) {
            if( confirm ) {
                final int answer = MiscToolkit.showSuppressableConfirmDialog(
                        MainFrame.getInstance(),
                        language.formatMessage("TOF.markAllReadConfirmation.board.content", node.getName()),
                        language.getString("TOF.markAllReadConfirmation.board.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        SettingsClass.CONFIRM_MARK_ALL_MSGS_READ, // will automatically choose "yes" if this is false
                        language.getString("Common.suppressConfirmationCheckbox") );
                if( answer != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            setAllMessagesRead((Board)node);
        } else if(node.isFolder()) {
            if( confirm ) {
                final int answer = MiscToolkit.showSuppressableConfirmDialog(
                        MainFrame.getInstance(),
                        language.formatMessage("TOF.markAllReadConfirmation.folder.content", node.getName()),
                        language.getString("TOF.markAllReadConfirmation.folder.title"),
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE,
                        SettingsClass.CONFIRM_MARK_ALL_MSGS_READ, // will automatically choose "yes" if this is false
                        language.getString("Common.suppressConfirmationCheckbox") );
                if( answer != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            // process all childs recursive
            final Enumeration<AbstractNode> leafs = node.children();
            while (leafs.hasMoreElements()) {
                markAllMessagesRead(leafs.nextElement(), false);
            }
        }
    }

    /**
     * Resets the NEW state to READ for all messages shown in board table.
     *
     * @param tableModel  the messages table model
     * @param board  the board to reset
     */
    private void setAllMessagesRead(final Board board) {
        // now takes care if board is changed during mark read of many boards! reloads current table if needed

        final int oldUnreadMessageCount = board.getUnreadMessageCount();

        MessageStorage.inst().setAllMessagesRead(board);

        // if this board is currently shown, update messages in table
        final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) MainFrame.getInstance().getMessageTreeModel().getRoot();

        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                if( MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getSelectedNode() == board ) {
                    for(final Enumeration e = rootNode.depthFirstEnumeration(); e.hasMoreElements(); ) {
                        final FrostMessageObject frostMessageObject = (FrostMessageObject)e.nextElement();
                        // this cast can only fail, if something other fails..
                        if( frostMessageObject instanceof FrostMessageObject ) {
                            if( frostMessageObject.isNew() ) {
                                frostMessageObject.setNew(false);
                                // fire update for visible rows in table model
                                final int row = MainFrame.getInstance().getMessageTreeTable().getRowForNode(frostMessageObject);
                                if( row >= 0 ) {
                                    MainFrame.getInstance().getMessageTableModel().fireTableRowsUpdated(row, row);
                                }
                            }
                        } else {
                        	logger.severe("error: frostMessageObject not of type FrostMessageObject");
                        }
                    }
                }
                // set for not selected boards too, by 'select folder unread' function

                // we cleared '' new messages, but don't get to negativ (maybe user selected another message during operation!)
                // but maybe a new message arrived!
                // ATTN: maybe problem if user sets another msg unread, and a new msg arrives, during time before invokeLater.
                final int diffNewMsgCount = board.getUnreadMessageCount() - oldUnreadMessageCount;
                board.setUnreadMessageCount( (diffNewMsgCount<0 ? 0 : diffNewMsgCount) );

                MainFrame.getInstance().updateMessageCountLabels(board);
                MainFrame.getInstance().updateTofTree(board);
        }});
    }

    /**
     * Add new invalid msg to database
     */
    public void receivedInvalidMessage(final Board b, final DateTime date, final int index, final String reason) {
        // first add to database, then mark slot used. this way its ok if Frost is shut down after add to db but
        // before mark of the slot.
        final FrostMessageObject invalidMsg = new FrostMessageObject(b, date, index, reason);
        invalidMsg.setNew(false);
        try {
            MessageStorage.inst().insertMessage(invalidMsg);
        } catch (final Throwable e) {
            // paranoia
            logger.log(Level.SEVERE, "Error inserting invalid message into database", e);
        }
    }

    /**
     * Add new received valid msg to database and maybe to gui.
     */
    public void receivedValidMessage(
            final MessageXmlFile currentMsg,
            Identity owner,
            final Board board,
            final int index)
    {
        if( owner != null ) {
            // owner is set, message was signed, owner is validated
            synchronized(Core.getIdentities().getLockObject()) {
                // check if owner is new
                final Identity checkOwner = Core.getIdentities().getIdentity(owner.getUniqueName());
                // if owner is new, add owner to identities list
                long lastSeenMillis = 0;
                try {
                    lastSeenMillis = currentMsg.getDateAndTime().getMillis();
                } catch(final Throwable t) {
                    logger.log(Level.SEVERE, "Error updating Identities lastSeenTime", t);
                }
                if( checkOwner == null ) {
                    owner.setLastSeenTimestampWithoutUpdate(lastSeenMillis);
                    if( !Core.getIdentities().addIdentity(owner) ) {
                        logger.severe("Core.getIdentities().addIdentity(owner) returned false for identy: "+owner.getUniqueName());
                        currentMsg.setPublicKey(null);
                        currentMsg.setSignatureStatusNONE();
                        owner = null;
                    }
                } else {
                    // use existing Identity
                    owner = checkOwner;
                    // update lastSeen for this Identity
                    if( owner.getLastSeenTimestamp() < lastSeenMillis ) {
                        owner.setLastSeenTimestamp(lastSeenMillis);
                    }
                }
            }
        }

        final FrostMessageObject newMsg = new FrostMessageObject(currentMsg, owner, board, index);
        receivedValidMessage(newMsg, board, index);
    }
    /**
     * Add new valid msg to database
     */
    public void receivedValidMessage(final FrostMessageObject newMsg, final Board board, final int index) {

        if( newMsg.isMessageFromME() && Core.frostSettings.getBoolValue(SettingsClass.HANDLE_OWN_MESSAGES_AS_NEW_DISABLED) ) {
            newMsg.setNew(false);
        } else {
            newMsg.setNew(true);
        }

        final boolean isBlocked = isBlocked(newMsg, board);
        if( isBlocked ) {
            // if message is blocked, reset new state
            newMsg.setNew(false);
        }

        final int messageInsertedRC;
        try {
            messageInsertedRC = MessageStorage.inst().insertMessage(newMsg);
        } catch (final Throwable e) {
            // paranoia
            logger.log(
                    Level.SEVERE,
                    "Error inserting new message into database. Msgid="+newMsg.getMessageId()+
                    "; Board="+board.getName()+"; Date="+newMsg.getDateAndTimeString()+"; "+"Index="+index,
                    e);
            return;
        }

        // don't add msg if it was a duplicate
        if( messageInsertedRC == MessageStorage.INSERT_DUPLICATE ) {
            logger.severe("Duplicate message, not added to storage. Msgid="+newMsg.getMessageId()+
                    "; Board="+board.getName()+"; Date="+newMsg.getDateAndTimeString()+"; "+"Index="+index);
            return; // not inserted into database, do not add to gui
        }

        // don't add msg if insert into database failed
        if( messageInsertedRC != MessageStorage.INSERT_OK ) {
            return; // not inserted into database, do not add to gui
        }

        if( newMsg.isSignatureStatusVERIFIED() && newMsg.getFromIdentity() != null ) {
            // we received a new unique message, count it
            newMsg.getFromIdentity().incReceivedMessageCount();
        }

        // after add to database
        processNewMessage(newMsg, board, isBlocked);
    }

    /**
     * Process incoming message.
     */
    private void processNewMessage(final FrostMessageObject currentMsg, final Board board, final boolean isBlocked) {

        // check if msg would be displayed (maxMessageDays)
        final DateTime min = new DateTime(DateTimeZone.UTC).minusDays(board.getMaxMessageDisplay()).withTimeAtStartOfDay();
        final DateTime msgDate = new DateTime(currentMsg.getDateAndTime(), DateTimeZone.UTC);

        if( msgDate.getMillis() > min.getMillis() ) {
            // add new message or notify of arrival
            addNewMessageToGui(currentMsg, board, isBlocked);
        } // else msg is not displayed due to maxMessageDisplay

        processAttachedBoards(currentMsg);
    }

    /**
     * Called by non-swing thread.
     */
    private void addNewMessageToGui(final FrostMessageObject message, final Board board, final boolean isBlocked) {

        // check if message is blocked
        if( isBlocked ) {
//            // add this msg if it replaces a dummy!
//            // DISCUSSION: better not, if a new FRIEND msg arrives later in reply to this BAD, the BAD is not loaded and
//            // dummy is created. this differes from behaviour of clean load from database
//            if( message.getMessageId() != null ) {
//                SwingUtilities.invokeLater( new Runnable() {
//                    public void run() {
//                        Board selectedBoard = tofTreeModel.getSelectedNode();
//                        // add only if target board is still shown
//                        if( !selectedBoard.isFolder() && selectedBoard.getName().equals( board.getName() ) ) {
//                            if( tryToFillDummyMsg(message) ) {
//                                // we filled a dummy!
//                                board.incNewMessageCount();
//                                MainFrame.getInstance().updateTofTree(board);
//                                MainFrame.displayNewMessageIcon(true);
//                                MainFrame.getInstance().updateMessageCountLabels(board);
//                            }
//                        }
//                    }
//                });
//            }
            return;
        }

        // message is not blocked
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                if( message.isNew() ) {
                    board.newMessageReceived(); // notify receive of new msg (for board update)
                    board.incUnreadMessageCount(); // increment new message count
                    MainFrame.getInstance().updateTofTree(board);
                    MainFrame.getInstance().displayNewMessageIcon(true);
                }

                final AbstractNode selectedNode = tofTreeModel.getSelectedNode();
                // add only if target board is still shown
                if( selectedNode.isBoard() && selectedNode.getName().equals( board.getName() ) ) {
                    addNewMessageToModel(message, board);
                    MainFrame.getInstance().updateMessageCountLabels(board);
                }
            }
        });
    }
    private boolean tryToFillDummyMsg(final FrostMessageObject newMessage) {
        final FrostMessageObject rootNode = (FrostMessageObject)MainFrame.getInstance().getMessageTreeModel().getRoot();
        // is there a dummy msg for this msgid?
        final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
        while(messageObjectEnumeration.hasMoreElements()){
            final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();
            
            if( frostMessageObject == rootNode ) {
                continue;
            }
            if( frostMessageObject.getMessageId() != null &&
                frostMessageObject.getMessageId().equals(newMessage.getMessageId()) &&
                frostMessageObject.isDummy()
              )
            {
                // previously missing msg arrived, fill dummy with message data
                frostMessageObject.fillFromOtherMessage(newMessage);
                final int row = MainFrame.getInstance().getMessageTreeTable().getRowForNode(frostMessageObject);
                if( row >= 0 ) {
                    MainFrame.getInstance().getMessageTableModel().fireTableRowsUpdated(row, row);
                }
                return true;
            }
        }
        return false; // no dummy found
    }

    private void addNewMessageToModel(FrostMessageObject newMessage, final Board board) {

        final FrostMessageObject rootNode = (FrostMessageObject)MainFrame.getInstance().getMessageTreeModel().getRoot();
        final MessageTreeTable treeTable = MainFrame.getInstance().getMessageTreeTable();
        final boolean expandUnread = Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_SHOW_COLLAPSED_THREADS) && Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_EXPAND_UNREAD_THREADS);

        final boolean showThreads = Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS);

        // if threading is disabled, if the message has no id, or if the message isn't
        // in reply to anything (it's the start of a thread), then add it directly to the root node
        if( showThreads == false ||
                newMessage.getMessageId() == null ||
                newMessage.getInReplyToList().size() == 0 ) {
            rootNode.add(newMessage, false);
            return;
        }

        // check if there is a dummy message with this messageid, and if so replace it with the real message contents
        if( tryToFillDummyMsg(newMessage) == true ) {
            // dummy msg filled
            return;
        }

        // try to find the direct parent of this message somewhere in the board tree,
        // and construct a message tree of dummy parents until we find the first existing parent in the list
        // NOTE: the scan is done backwards, up the tree, from the current message (added first)
        // through all the dummies (if needed), all the way to the actual parent (where we attach the dummy-tree+msg if parent is found)...
        final LinkedList<String> msgParents = new LinkedList<String>(newMessage.getInReplyToList());
        while( msgParents.size() > 0 ) {
            final String directParentId = msgParents.removeLast();

            // check all messages in the tree, depth-first, to see if they're the current parent we're looking for
            final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
            while( messageObjectEnumeration.hasMoreElements() ) {
                final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();

                // we found an existing parent message node (either a real one or a dummy), so simply add our
                // current "(possible dummies)->message" tree to that parent and stop looking for further parents
                if( frostMessageObject.getMessageId() != null &&
                        frostMessageObject.getMessageId().equals(directParentId) ) {
                    frostMessageObject.add(newMessage, false);
                    if( expandUnread ) {
                        treeTable.expandFirework(newMessage);
                    }
                    return;
                }
            }

            // we did not find the current parent we wanted, so let's create a dummy message for it instead,
            // and then add the real message/tree as a child of that dummy message; we then point newMessage
            // to the dummy message instead, so that we end up building a message tree of nested virtual messages
            // as we move up this hierarchy. NOTE: they won't be added to the actual tree here, and silent = true
            // which means that the table won't be notified of any insert event by the "add()", since we're
            // building a virtual, dummy message tree at the moment which isn't yet attached to the table...
            final FrostMessageObject dummyMsg = new FrostMessageObject(directParentId, board, null);
            dummyMsg.add(newMessage, true);

            newMessage = dummyMsg;
        }

        // no parent found was anywhere in the hierarchy (not even a dummy one), so insert a brand new
        // tree of all the dummy "in reply to" messages, with the real message at the bottom
        rootNode.add(newMessage, false);
        if (expandUnread) {
            treeTable.expandFirework(newMessage);
        }
    }

    /**
     * Clears the tofTable, reads in the messages to be displayed,
     * does check validity for each message and adds the messages to
     * table. Additionaly it returns a Vector with all MessageObjects
     * @param board The selected board.
     * @param daysToRead Maximum age of the messages to be displayed
     * @param table The tofTable.
     * @return Vector containing all MessageObjects that are displayed in the table.
     */
    public void updateTofTable(final Board board, final String prevSelectedMsgId) {
        final int daysToRead = board.getMaxMessageDisplay();

        if( updateThread != null ) {
            if( updateThread.toString().equals( board ) ) {
                // already updating
                return;
            } else {
                // stop current thread, then start new
                updateThread.cancel();
            }
        }

        // start new thread, the thread will set itself to updateThread,
        // but first it waits until the current thread is finished
        nextUpdateThread = new UpdateTofFilesThread(board, daysToRead, prevSelectedMsgId);
        MainFrame.getInstance().activateGlassPane();
        nextUpdateThread.start();
    }

    public boolean isLoadingMessages() {
        // returns true if there is a current or waiting "load board messages" thread, *or* if
        // the GUI is working on rendering (repainting) after having added a new chunk of messages.
        // the threads themselves finish almost instantly, but the redrawing of the GUI is what
        // takes a while. the GUI is white/blank and frozen (not responding to events) while it's
        // redrawing, so that's the state we're really interested in watching out for!
        return ( (updateThread != null || nextUpdateThread != null || isRedrawingGui) ? true : false );
    }

    private class UpdateTofFilesThread extends Thread {

        Board board;
        int daysToRead;
        boolean isCancelled = false;
        final String previousSelectedMsgId;

        List<FrostMessageObject> markAsReadMsgs = new ArrayList<FrostMessageObject>();

        public UpdateTofFilesThread(final Board board, final int daysToRead, final String prevSelectedMsgId) {
            this.board = board;
            this.daysToRead = daysToRead;
            this.previousSelectedMsgId = prevSelectedMsgId;
        }

        public synchronized void cancel() {
            isCancelled = true;
        }

        public synchronized boolean isCancel() {
            return isCancelled;
        }

        @Override
        public String toString() {
            return board.getName();
        }

        /**
         * Adds new messages flat to the rootnode, blocked msgs are not added.
         */
        private class FlatMessageRetrieval implements MessageCallback {

            private final FrostMessageObject rootNode;
            private final boolean blockMsgSubject;
            private final boolean blockMsgBody;
            private final boolean blockMsgBoardname;

            public FlatMessageRetrieval(final FrostMessageObject root) {
                rootNode = root;
                blockMsgSubject = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_SUBJECT_ENABLED);
                blockMsgBody = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BODY_ENABLED);
                blockMsgBoardname = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BOARDNAME_ENABLED);
            }
            public boolean messageRetrieved(final FrostMessageObject mo) {
                if( !isBlocked(mo, board, blockMsgSubject, blockMsgBody, blockMsgBoardname) ) {
                    rootNode.add(mo);
                } else {
                    // message is blocked. check if message is still new, and maybe mark as read
                    if( mo.isNew() ) {
                        markAsReadMsgs.add(mo);
                    }
                }
                return isCancel();
            }
        }

        /**
         * Adds new messages threaded to the rootnode, blocked msgs are removed if not needed for thread.
         */
        private class ThreadedMessageRetrieval implements MessageCallback {

            final FrostMessageObject rootNode;
            LinkedList<FrostMessageObject> messageList = new LinkedList<FrostMessageObject>();

            public ThreadedMessageRetrieval(final FrostMessageObject root) {
                rootNode = root;
            }
            public boolean messageRetrieved(final FrostMessageObject mo) {
                messageList.add(mo);
                return isCancel();
            }
            public void buildThreads() {
                // messageList was filled by callback

                final boolean blockMsgSubject = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_SUBJECT_ENABLED);
                final boolean blockMsgBody = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BODY_ENABLED);
                final boolean blockMsgBoardname = Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BOARDNAME_ENABLED);

                // HashSet contains a msgid if the msg was loaded OR was not existing
                HashSet<String> messageIds = new HashSet<String>();

                for(final Iterator<FrostMessageObject> i=messageList.iterator(); i.hasNext(); ) {
                    final FrostMessageObject mo = i.next();
                    if( mo.getMessageId() == null ) {
                        i.remove();
                        // old msg, maybe add to root
                        if( !isBlocked(mo, mo.getBoard(), blockMsgSubject, blockMsgBody, blockMsgBoardname) ) {
                            rootNode.add(mo);
                        } else {
                            // message is blocked. check if message is still new, and maybe mark as read
                            if( mo.isNew() ) {
                                markAsReadMsgs.add(mo);
                            }
                        }
                    } else {
                        // collect for threading
                        messageIds.add(mo.getMessageId());
                    }
                }

                // for threads, check msgrefs and load all existing msgs pointed to by refs
                final boolean showDeletedMessages = Core.frostSettings.getBoolValue(SettingsClass.SHOW_DELETED_MESSAGES);
                LinkedList<FrostMessageObject> newLoadedMsgs = new LinkedList<FrostMessageObject>();
                LinkedList<FrostMessageObject> newLoadedMsgs2 = new LinkedList<FrostMessageObject>();

                loadInReplyToMessages(messageList, messageIds, showDeletedMessages, newLoadedMsgs);

                // load all linked messages, only needed when only new msgs are shown and some msgs have invalid
                // refs (sometimes sent by other clients)
                while( loadInReplyToMessages(newLoadedMsgs, messageIds, showDeletedMessages, newLoadedMsgs2) ) {
                    messageList.addAll(newLoadedMsgs);
                    newLoadedMsgs = newLoadedMsgs2;
                    newLoadedMsgs2 = new LinkedList<FrostMessageObject>();
                }
                messageList.addAll(newLoadedMsgs);

                // help the garbage collector
                newLoadedMsgs = null;
                messageIds = null;

                // all msgs are loaded and dummies for missing msgs were created, now build the threads
                // - add msgs without msgid to rootnode
                // - add msgs with msgid and no ref to rootnode
                // - add msgs with msgid and ref to its direct parent (last refid in list)

                // first collect msgs with id into a Map for lookups
                final HashMap<String,FrostMessageObject> messagesTableById = new HashMap<String,FrostMessageObject>();
                for( final FrostMessageObject mo : messageList ) {
                    messagesTableById.put(mo.getMessageId(), mo);
                }

                // help the garbage collector
                messageList = null;

                // build the threads
                for( final FrostMessageObject mo : messagesTableById.values() ) {
                    final ArrayList<String> l = mo.getInReplyToList();
                    if( l.size() == 0 ) {
                        // a root message, no replyTo
                        rootNode.add(mo);
                    } else {
                        // add to direct parent
                        final String directParentId = l.get(l.size()-1); // getLast
                        if( directParentId == null ) {
                            logger.log(Level.SEVERE, "Should never happen: directParentId is null; msg="+mo.getMessageId()+"; parentMsg="+directParentId);
                            continue;
                        }
                        final FrostMessageObject parentMo = messagesTableById.get(directParentId);
                        if( parentMo == null ) {
                            // FIXME: happens if someone sends a faked msg with parentids from 2 different threads.
                            //  gives NPE if one of the messages is already in its own thread
                            logger.log(Level.SEVERE, "Should never happen: parentMo is null; msg="+mo.getMessageId()+"; parentMsg="+directParentId+"; irtl="+mo.getInReplyTo());
                            continue;
                        }
                        parentMo.add(mo);
                    }
                }

                // remove blocked msgs from the leafs
                final List<FrostMessageObject> itemsToRemove = new ArrayList<FrostMessageObject>();
                final Set<String> notBlockedMessageIds = new HashSet<String>();
                while(true) {
                	final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
                    
                	while(messageObjectEnumeration.hasMoreElements()){
                        final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();
                    
                        if( frostMessageObject.isLeaf() && frostMessageObject != rootNode ) {
                            if( frostMessageObject.isDummy() ) {
                                itemsToRemove.add(frostMessageObject);
                            } else if( frostMessageObject.getMessageId() == null ) {
                                if( isBlocked(frostMessageObject, frostMessageObject.getBoard(), blockMsgSubject, blockMsgBody, blockMsgBoardname) ) {
                                    itemsToRemove.add(frostMessageObject);
                                    // message is blocked. check if message is still new, and maybe mark as read
                                    if( frostMessageObject.isNew() ) {
                                        markAsReadMsgs.add(frostMessageObject);
                                    }
                                }
                            } else {
                                if( notBlockedMessageIds.contains(frostMessageObject.getMessageId()) ) {
                                    continue; // already checked, not blocked
                                }
                                // check if blocked
                                if( isBlocked(frostMessageObject, frostMessageObject.getBoard(), blockMsgSubject, blockMsgBody, blockMsgBoardname) ) {
                                    itemsToRemove.add(frostMessageObject);
                                    // message is blocked. check if message is still new, and maybe mark as read
                                    if( frostMessageObject.isNew() ) {
                                        markAsReadMsgs.add(frostMessageObject);
                                    }
                                } else {
                                    // not blocked, mark as checked to avoid the block test in next iterations
                                    notBlockedMessageIds.add(frostMessageObject.getMessageId());
                                }
                            }
                        }
                    }
                	
                    if( itemsToRemove.size() > 0 ) {
                        for( final FrostMessageObject removeMo : itemsToRemove ) {
                            removeMo.removeFromParent();
                        }
                        itemsToRemove.clear(); // clear for next run
                    } else {
                        // no more blocked leafs
                        break;
                    }
                }
                // clean up
                notBlockedMessageIds.clear();

                // apply the subject of first child message to dummy ROOT messages
                final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
                while(messageObjectEnumeration.hasMoreElements()){
                    final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();
                    
                    if( frostMessageObject.isDummy() ) {
                        // this thread root node has no subject, get subject of first valid child
                    	final Enumeration<FrostMessageObject> messageObjectEnumeration2 = frostMessageObject.depthFirstEnumeration();
                        while( messageObjectEnumeration2.hasMoreElements() ) {
                            final FrostMessageObject childMo = messageObjectEnumeration2.nextElement();
                            
                            if( !childMo.isDummy() && childMo.getSubject() != null ) {
                                final StringBuilder sb = new StringBuilder(childMo.getSubject().length() + 2);
                                sb.append("[").append(childMo.getSubject()).append("]");
                                frostMessageObject.setSubject(sb.toString());
                                break;
                            }
                        }
                    }
                }
            }

            private boolean loadInReplyToMessages(
                    final List<FrostMessageObject> messages,
                    final HashSet<String> messageIds,
                    final boolean showDeletedMessages,
                    final LinkedList<FrostMessageObject> newLoadedMsgs)
            {
                boolean msgWasMissing = false;
                for(final FrostMessageObject mo : messages ) {
                    final List<String> l = mo.getInReplyToList();
                    if( l.size() == 0 ) {
                        continue; // no msg refs
                    }

                    // try to load each referenced msgid, put tried ids into hashset msgIds
                    for(int x=l.size()-1; x>=0; x--) {
                        final String anId = l.get(x);
                        if( anId == null ) {
                            logger.log(Level.SEVERE, "Should never happen: message id is null! msgId="+mo.getMessageId());
                            continue;
                        }

                        if( messageIds.contains(anId) ) {
                            continue;
                        }

                        FrostMessageObject fmo = MessageStorage.inst().retrieveMessageByMessageId(
                                board,
                                anId,
                                false,
                                false,
                                showDeletedMessages);
                        if( fmo == null ) {
                            // for each missing msg create a dummy FrostMessageObject and add it to tree.
                            // if the missing msg arrives later, replace dummy with true msg in tree
                            final ArrayList<String> ll = new ArrayList<String>(x);
                            if( x > 0 ) {
                                for(int y=0; y < x; y++) {
                                    ll.add(l.get(y));
                                }
                            }
                            fmo = new FrostMessageObject(anId, board, ll);
                        }
                        newLoadedMsgs.add(fmo);
                        messageIds.add(anId);
                        if( !msgWasMissing ) {
                            msgWasMissing = true;
                        }
                    }
                }
                return msgWasMissing;
            }
        }

        /**
         * Start to load messages one by one.
         */
        private void loadMessages(final MessageCallback callback) {

            // NOTE: these flags define what message-iterators to use when loading from the database
            // (all, unread, flagged or starred). we do *not* implement "hide junk" checking here,
            // since that's a job for the board-loading to determine later!
            final boolean showDeletedMessages = Core.frostSettings.getBoolValue(SettingsClass.SHOW_DELETED_MESSAGES);
            final boolean showUnreadOnly = Core.frostSettings.getBoolValue(SettingsClass.SHOW_UNREAD_ONLY);
            final boolean showFlaggedOnly = Core.frostSettings.getBoolValue(SettingsClass.SHOW_FLAGGED_ONLY);
            final boolean showStarredOnly = Core.frostSettings.getBoolValue(SettingsClass.SHOW_STARRED_ONLY);
            int whatToShow = MessageStorage.SHOW_DEFAULT;

            if (showUnreadOnly) {
            	whatToShow = MessageStorage.SHOW_UNREAD_ONLY;
            } else if (showFlaggedOnly) {
            	whatToShow = MessageStorage.SHOW_FLAGGED_ONLY;
            } else if (showStarredOnly) {
            	whatToShow = MessageStorage.SHOW_STARRED_ONLY;
            }

            MessageStorage.inst().retrieveMessagesForShow(
                    board,
                    daysToRead,
                    false,
                    false,
                    showDeletedMessages,
                    whatToShow,
                    callback);
        }

        @Override
        public void run() {
            while( updateThread != null ) {
                // wait for running thread to finish
                Mixed.wait(150);
                if( nextUpdateThread != this ) {
                    // leave, there is a newer thread than us waiting
                    // NOTE: this usually means the user has changed to a different board while
                    // we were waiting for the previous update to finish, thus invalidating this
                    // thread, since there's a newer job.
                    return;
                }
            }

            // paranoia: are WE the next thread?
            if( nextUpdateThread != this ) {
                return;
            } else {
                nextUpdateThread = null; // unset us from "next" since we're starting the job!
                updateThread = this;
            }

            final FrostMessageObject rootNode = new FrostMessageObject(true);

            final boolean loadThreads = Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS);

            // update SortStateBean
            MessageTreeTableSortStateBean.setThreaded(loadThreads);

            final long l1 = System.currentTimeMillis();
            try {
                if( loadThreads  ) {
                    final ThreadedMessageRetrieval tmr = new ThreadedMessageRetrieval(rootNode);
                    loadMessages(tmr);
                    final long l2 = System.currentTimeMillis();
                    tmr.buildThreads();
                    final long l3 = System.currentTimeMillis();
                    // TODO: debug output only!
                    System.out.println("loading board "+board.getName()+" (threaded): diskFetch="+(l2-l1)+"ms, buildThreads+subRetrieve="+(l3-l2)+"ms");
                } else {
                    // load flat
                    final FlatMessageRetrieval ffr = new FlatMessageRetrieval(rootNode);
                    loadMessages(ffr);
                    final long l2 = System.currentTimeMillis();
                    System.out.println("loading board "+board.getName()+" (non-threaded): diskFetch="+(l2-l1)+"ms");
                }

                // finally mark 'new', but blocked messages as unread
                MessageStorage.inst().setMessagesRead(board, markAsReadMsgs);

            } catch (final Throwable t) {
                logger.log(Level.SEVERE, "Excpetion during thread load/build", t);
            }

            if( !isCancel() ) {
                // count new messages and check if board has flagged or starred messages
                int newMessageCountWork = 0;
                boolean hasStarredWork = false;
                boolean hasFlaggedWork = false;
                
                final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
                while(messageObjectEnumeration.hasMoreElements()){
                    final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();
                    
                    if( frostMessageObject.isNew() ) {
                        newMessageCountWork++;
                    }
                    if( !hasStarredWork && frostMessageObject.isStarred() ) {
                        hasStarredWork = true;
                    }
                    if( !hasFlaggedWork && frostMessageObject.isFlagged() ) {
                        hasFlaggedWork = true;
                    }
                }

                // set rootnode to gui and update
                final Board innerTargetBoard = board;
                final int newMessageCount = newMessageCountWork;
                final boolean newHasFlagged = hasFlaggedWork;
                final boolean newHasStarred = hasStarredWork;
                isRedrawingGui = true; // means that the messages have not yet been visibly loaded into the GUI
                SwingUtilities.invokeLater( new Runnable() {
                    public void run() {
                        final long l2 = System.currentTimeMillis();
                        innerTargetBoard.setUnreadMessageCount(newMessageCount);
                        innerTargetBoard.setFlaggedMessages(newHasFlagged);
                        innerTargetBoard.setStarredMessages(newHasStarred);
                        setNewRootNode(innerTargetBoard, rootNode, previousSelectedMsgId);
                        isRedrawingGui = false; // the messages have been loaded into the GUI and are now visible
                        final long l3 = System.currentTimeMillis();
                        System.out.println("finished loading board "+board.getName()+": guiRenderTime="+(l3-l2)+"ms, totalTime="+(l3-l1)+"ms");
                    }
                });
            } else if( nextUpdateThread == null ) {
                // if there are no more "update threads" queued while we were finishing this update, then re-activate the main window
                MainFrame.getInstance().deactivateGlassPane();
            }
            // unset us as "updatethread" since we're done with the job!
            updateThread = null;
        }

        /**
         * Set rootnode to gui and update.
         */
        private void setNewRootNode(final Board innerTargetBoard, final FrostMessageObject rootNode, final String previousSelectedMsgId) {
            if( tofTreeModel.getSelectedNode().isBoard() &&
                    tofTreeModel.getSelectedNode().getName().equals( innerTargetBoard.getName() ) )
            {
                final MessageTreeTable treeTable = MainFrame.getInstance().getMessageTreeTable();

                // give the treetable a new root node (fires "treeStructureChanged" which in turn fires
                // "tableDataChanged". this event just takes ~5ms even for big boards.
                treeTable.setNewRootNode(rootNode);
                // now expand either all nodes, or the root-level children; this is the thing that's
                // insanely slow, but uses Kitty's TreeTable performance trick to be super fast. ;-)
                if( ! Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_SHOW_COLLAPSED_THREADS) ) {
                    // normal mode: all threads and all replies are expanded
                    treeTable.expandAll(true);
                } else {
                    // collapsed mode: pre-expand replies (optional) and/or expand unread parts of threads (optional)
                    final boolean preExpandReplies = Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_EXPAND_ROOT_CHILDREN);
                    final boolean expandUnreadThreads = Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_EXPAND_UNREAD_THREADS);
                    treeTable.expandRootChildren(preExpandReplies, expandUnreadThreads);
                }

                MainFrame.getInstance().updateTofTree(innerTargetBoard);
                MainFrame.getInstance().updateMessageCountLabels(innerTargetBoard);

                MainFrame.getInstance().deactivateGlassPane();

                // do last: if the user had selected a message before the reload, we'll want to re-select
                // it as soon as the JTree<->JTable re-syncing activity has settled down. by queueing
                // an invokeLater job, we'll be sure that all of the insert/expand events have finished
                // by the time that we fire. that's because the insert/expandThread calls above also fire
                // invokeLater jobs, so we'll be adding ourselves to the *end* of that queue.
                if( previousSelectedMsgId != null ) {
                    // check if a message with the same messageId exists within the new node tree
                    final Enumeration<FrostMessageObject> messageObjectEnumeration = rootNode.depthFirstEnumeration();
                    while( messageObjectEnumeration.hasMoreElements() ){
                        final FrostMessageObject frostMessageObject = messageObjectEnumeration.nextElement();

                        if( frostMessageObject != null && frostMessageObject.getMessageId() != null && frostMessageObject.getMessageId().equals(previousSelectedMsgId) ) {
                            treeTable.expandFirework(frostMessageObject);

                            // invokeLater the row-finding, selection and scrolling jobs
                            // to make sure that the TreeTable will be ready when we query it...
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    // if the message is at row "-1" it means the message is no longer visible
                                    int treeRowIdx = treeTable.getRowForNode(frostMessageObject);
                                    if( treeRowIdx >= 0 ) {
                                        treeTable.getSelectionModel().setSelectionInterval(treeRowIdx, treeRowIdx);
                                        // perform an intelligent scroll to the selected row + 3,
                                        // which still ensures the target row is always in view
                                        SmartSelection.applySmartScroll(treeTable, treeRowIdx, 3);
                                    }
                                }
                            });
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns true if the message should not be displayed
     * @return true if message is blocked, else false
     */
    public boolean isBlocked(final FrostMessageObject message, final Board board) {
        return isBlocked(
                message,
                board,
                Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_SUBJECT_ENABLED),
                Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BODY_ENABLED),
                Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BLOCK_BOARDNAME_ENABLED));
    }

    /**
     * Returns true if the message should be hidden (aka not displayed).
     * @return true if message is blocked, else false
     */
    public boolean isBlocked(
            final FrostMessageObject message,
            final Board board,
            final boolean blockMsgSubject,
            final boolean blockMsgBody,
            final boolean blockMsgBoardname)
    {
        if( !showJunkMessages && message.isJunk() ) {
            return true;
        }
        if (board.getHideUnsigned()
            && (message.isMessageStatusNONE() || message.isMessageStatusTAMPERED()) )
        {
            return true;
        }
        if (board.getHideBAD() && message.isMessageStatusBAD()) {
            return true;
        }
        if (board.getHideNEUTRAL() && message.isMessageStatusNEUTRAL()) {
            return true;
        }
        if (board.getHideGOOD() && message.isMessageStatusGOOD()) {
            return true;
        }

        // check for block words, don't check GOOD and FRIEND
        if (!message.isMessageStatusGOOD() && !message.isMessageStatusFRIEND()) {

            if (board.getHideMessageCount() > 0 && !message.isMessageFromME()) {
                /* blahwad  bot blaster */
                Identity sender = message.getFromIdentity();
                if (sender != null && sender.getReceivedMessageCount() < board.getHideMessageCount()) {
                    if (board.getHideMessageCountExcludePrivate()) {
                        if (message.getRecipientName() == null || message.getRecipientName().length() == 0)
                            return true;
                    } else {
                        return true;
                    }
                }
            }

            // Block by subject (and rest of the header)
            if ( blockMsgSubject ) {
                final String header = message.getSubject().toLowerCase();
                final StringTokenizer blockWords =
                    new StringTokenizer(Core.frostSettings.getValue(SettingsClass.MESSAGE_BLOCK_SUBJECT), ";");
                while (blockWords.hasMoreTokens()) {
                    final String blockWord = blockWords.nextToken().trim();
                    if ((blockWord.length() > 0) && (header.indexOf(blockWord) >= 0)) {
                        return true;
                    }
                }
            }
            // Block by body
            if ( blockMsgBody ) {
                final String content = message.getContent().toLowerCase();
                final StringTokenizer blockWords =
                    new StringTokenizer(Core.frostSettings.getValue(SettingsClass.MESSAGE_BLOCK_BODY), ";");
                while (blockWords.hasMoreTokens()) {
                    final String blockWord = blockWords.nextToken().trim();
                    if ((blockWord.length() > 0) && (content.indexOf(blockWord) >= 0)) {
                        return true;
                    }
                }
            }
            // Block by attached boards
            if ( blockMsgBoardname ) {
                final AttachmentList<BoardAttachment> boardAttachmentList =  message.getAttachmentsOfTypeBoard();
                final StringTokenizer blockWords =
                    new StringTokenizer(Core.frostSettings.getValue(SettingsClass.MESSAGE_BLOCK_BOARDNAME), ";");
                
                while (blockWords.hasMoreTokens()) {
                    final String blockWord = blockWords.nextToken().trim();
                    
                    for( final BoardAttachment boardAttachment : boardAttachmentList ) {
                        if ((blockWord.length() > 0) && (boardAttachment.getBoardObj().getName().equalsIgnoreCase(blockWord))) {
                            return true;
                        }
                    }
                }
            }
        }
        // not blocked
        return false;
    }

    /**
     * Maybe add the attached board to list of known boards.
     */
    private void processAttachedBoards(final FrostMessageObject currentMsg) {
        if( currentMsg.isMessageStatusNONE() &&
            Core.frostSettings.getBoolValue(SettingsClass.KNOWNBOARDS_BLOCK_FROM_UNSIGNED) == true )
        {
            logger.info("Boards from unsigned message blocked");
        } else if( currentMsg.isMessageStatusBAD() &&
                   Core.frostSettings.getBoolValue(SettingsClass.KNOWNBOARDS_BLOCK_FROM_BAD) == true )
        {
            logger.info("Boards from BAD message blocked");
        } else if( currentMsg.isMessageStatusNEUTRAL() &&
                   Core.frostSettings.getBoolValue(SettingsClass.KNOWNBOARDS_BLOCK_FROM_NEUTRAL) == true )
        {
            logger.info("Boards from NEUTRAL message blocked");
        } else if( currentMsg.isMessageStatusGOOD() &&
                   Core.frostSettings.getBoolValue(SettingsClass.KNOWNBOARDS_BLOCK_FROM_GOOD) == true )
        {
            logger.info("Boards from GOOD message blocked");
        } else if( currentMsg.isMessageStatusTAMPERED() ) {
            logger.info("Boards from TAMPERED message blocked");
        } else {
            // either FRIEND user or not blocked by user
            final LinkedList<Board> addBoards = new LinkedList<Board>();
            for(final Iterator<BoardAttachment> i = currentMsg.getAttachmentsOfTypeBoard().iterator(); i.hasNext(); ) {
                addBoards.add(i.next().getBoardObj());
            }
            KnownBoardsManager.addNewKnownBoards(addBoards);
        }
    }

    public void searchAllUnreadMessages(final boolean runWithinThread) {
        if( runWithinThread ) {
            new Thread() {
                @Override
                public void run() {
                    searchAllUnreadMessages();
                }
            }.start();
        } else {
            searchAllUnreadMessages();
        }
    }

    public void searchUnreadMessages(final Board board) {
        new Thread() {
            @Override
            public void run() {
                searchUnreadMessagesInBoard(board);
            }
        }.start();
    }

    private void searchAllUnreadMessages() {
        final Enumeration<AbstractNode> e = tofTreeModel.getRoot().depthFirstEnumeration();
        while( e.hasMoreElements() ) {
            final AbstractNode node = e.nextElement();
            if( node.isBoard() ) {
                searchUnreadMessagesInBoard((Board)node);
            }
        }
    }

    private void searchUnreadMessagesInBoard(final Board board) {
        if( !board.isBoard() ) {
            return;
        }

        final int beforeMessages = board.getUnreadMessageCount(); // remember old val to track if new msg. arrived

        int newMessages = 0;
        newMessages = MessageStorage.inst().getUnreadMessageCount(board);

        // count new messages arrived while processing
        final int arrivedMessages = board.getUnreadMessageCount() - beforeMessages;
        if( arrivedMessages > 0 ) {
            newMessages += arrivedMessages;
        }

        board.setUnreadMessageCount(newMessages);

        // check for flagged and starred messages in board
        boolean hasFlagged = false;
        boolean hasStarred = false;
        hasFlagged = MessageStorage.inst().hasFlaggedMessages(board);
        hasStarred = MessageStorage.inst().hasStarredMessages(board);

        board.setFlaggedMessages(hasFlagged);
        board.setStarredMessages(hasStarred);

        // update the tree
        SwingUtilities.invokeLater( new Runnable() {
            public void run() {
                MainFrame.getInstance().updateTofTree(board);
            }
        });
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if( evt.getPropertyName().equals(SettingsClass.SHOW_JUNK_MESSAGES) ) {
            showJunkMessages = Core.frostSettings.getBoolValue(SettingsClass.SHOW_JUNK_MESSAGES);
        }
    }
}
