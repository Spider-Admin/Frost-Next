/*
 MessagePanel.java / Frost
  Copyright (C) 2006  Frost Project <jtcfrost.sourceforge.net>
  Some changes by Stefan Majewski <e9926279@stud3.tuwien.ac.at>

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
package frost.messaging.frost.gui;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;

import frost.*;
import frost.identities.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.messaging.frost.gui.messagetreetable.*;
import frost.storage.perst.messages.*;
import frost.util.*;
import frost.util.SingleTaskWorker;
import frost.util.gui.*;
import frost.util.gui.search.*;
import frost.util.gui.translation.*;

@SuppressWarnings("serial")
public class MessagePanel extends JPanel implements PropertyChangeListener {

    private MessageTreeTable messageTable = null;
    private MessageTextPane messageTextPane = null;
    private JScrollPane messageListScrollPane = null;
    private JSplitPane msgTableAndMsgTextSplitpane = null;
    private final JLabel subjectLabel = new JLabel();
    private final JLabel subjectTextLabel = new JLabel();

    private boolean indicateLowReceivedMessages;
    private int indicateLowReceivedMessagesCountRed;
    private int indicateLowReceivedMessagesCountBlue;

    private final MainFrame mainFrame;
    private final FrostMessageTab frostMessageTab;

    public static enum IdentityState { BAD, NEUTRAL, GOOD, FRIEND };
    public static enum BooleanState { FLAGGED, STARRED, JUNK };

    private class Listener
    extends MouseAdapter
    implements
        ActionListener,
        ListSelectionListener,
        TreeSelectionListener,
        LanguageListener
    {
        private final SingleTaskWorker delayedSelectionReactor = new SingleTaskWorker();

        public Listener() {
            super();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == updateBoardButton) {
                updateButton_actionPerformed(e);
            } else if (e.getSource() == newMessageButton) {
                newMessageButton_actionPerformed();
            } else if (e.getSource() == replyButton) {
                replyButton_actionPerformed(e);
            } else if (e.getSource() == saveMessageButton) {
                getMessageTextPane().saveMessageButton_actionPerformed();
            } else if (e.getSource() == nextUnreadMessageButton) {
                selectNextUnreadMessage();
            } else if (e.getSource() == setFRIENDButton) {
                setTrustState_actionPerformed(IdentityState.FRIEND);
            } else if (e.getSource() == setBADButton) {
                setTrustState_actionPerformed(IdentityState.BAD);
            } else if (e.getSource() == setNEUTRALButton) {
                setTrustState_actionPerformed(IdentityState.NEUTRAL);
            } else if (e.getSource() == setGOODButton) {
                setTrustState_actionPerformed(IdentityState.GOOD);
            } else if (e.getSource() == toggleShowUnreadOnly) {
                toggleShowOnly_actionPerformed(e);
            } else if (e.getSource() == toggleShowFlaggedOnly) {
                toggleShowOnly_actionPerformed(e);
            } else if (e.getSource() == toggleShowStarredOnly) {
                toggleShowOnly_actionPerformed(e);
            } else if (e.getSource() == toggleShowJunkMessages) {
                toggleShowOnly_actionPerformed(e);
            } else if (e.getSource() == toggleShowThreads) {
                toggleShowThreads_actionPerformed(e);
            } else if (e.getSource() == toggleShowSmileys) {
                toggleShowSmileys_actionPerformed(e);
            } else if (e.getSource() == toggleShowHyperlinks) {
                toggleShowHyperlinks_actionPerformed(e);
            }
        }

        private void maybeShowPopup(final MouseEvent e) {
            if (e.isPopupTrigger()) {
                if (e.getComponent() == messageTable) {
                    showMessageTablePopupMenu(e);
                } else if( e.getComponent() == subjectTextLabel ) {
                    getPopupMenuSubjectText().show(e.getComponent(), e.getX(), e.getY());
                }
                // if leftbtn double click on message show this message in a new window
            } else if(SwingUtilities.isLeftMouseButton(e)) {
                // accepting only mouse pressed event as double click, otherwise it will be triggered twice
                if(e.getID() == MouseEvent.MOUSE_PRESSED ) {
                    if(e.getClickCount() == 2 && e.getComponent() == messageTable ) {
                        if( Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_DOUBLE_CLICK_SHOWS_MESSAGE) ) {
                        showCurrentMessagePopupWindow();
                        }
                    } else if(e.getClickCount() == 1 && e.getComponent() == messageTable ) {
                        // 'edit' the icon columns, toggle state flagged/starred
                        final int row = messageTable.rowAtPoint(e.getPoint());
                        final int col = messageTable.columnAtPoint(e.getPoint());
                        if( row > -1 && col > -1 ) {
                            final int modelCol = messageTable.getColumnModel().getColumn(col).getModelIndex();
                            editIconColumn(row, modelCol);
                        }
                    }
                }
            }
        }

        /**
         * Left click onto a row/col occurred. Check if the click was over an icon column and maybe toggle
         * its state (starred/flagged).
         */
        protected void editIconColumn(final int row, final int modelCol) {
            final BooleanState state;
            switch(modelCol) {
                case MessageTreeTableModel.COLUMN_INDEX_FLAGGED: state = BooleanState.FLAGGED; break;
                case MessageTreeTableModel.COLUMN_INDEX_STARRED: state = BooleanState.STARRED; break;
                case MessageTreeTableModel.COLUMN_INDEX_JUNK: state = BooleanState.JUNK; break;
                default: return;
            }

            final FrostMessageObject message = (FrostMessageObject)getMessageTableModel().getRow(row);
            if( message == null || message.isDummy() ) {
                return;
            }

            updateBooleanState(state, Collections.singletonList(message));
        }

        @Override
        public void mousePressed(final MouseEvent e) {
            maybeShowPopup(e);
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            maybeShowPopup(e);
        }

        public void valueChanged(final ListSelectionEvent e) {
            /**
             * Long comment, because this relates to intricate details about Frost's "TreeTable".
             *
             * This is absolutely vital... Frost is using an awful "TreeTable" implementation, which
             * combines a JTree and a JTable into what seems like a single table with collapsible
             * rows. However, the JTree and JTable actually do constant synchronization behind
             * the scenes whenever new rows are inserted/removed from the JTable. Whenever a row
             * is added to the JTable, the table's ListSelectionModel fires a "value changed"
             * ListSelectionEvent. But it uses the JTable's "getValueAt(row, col)" method, which
             * is internally overridden to read the values from the underlying JTree instead.
             * The problem is that when the JTree and JTable row numbers are out of sync (which
             * they always are while rows are being added), then the JTree returns the wrong data
             * for "that table row". That's what causes the "value changed" event to fire, with the
             * wrong value, as if the user had manually clicked the wrong row. A few moments later
             * the JTable and JTree will be back in sync and a new "getValueAt" will detect the
             * old (correct) row value again and fire *another* "value changed" (row selection)
             * event, which tells us the correct data, and so on... But it's actually worse than that;
             * whenever the JTable is repainting and the JTree is heavily out of sync, it will
             * actually fire *loads* of wrong "value changed" events in rapid succession, with about
             * 0-3ms of delay between each one. The longest delay when it finally settles usually
             * reaches about 6ms, although I've seen a 9ms outlier. This time doesn't grow even
             * if the table has thousands of rows, since a table only redraws the currently
             * visible rows. So, what's the solution to this "fluttering"? Simple... we filter
             * the events, by using a "SingleTaskWorker", which is a delayed worker-task thread
             * which can only have a single queued worker. Every time you add a new worker to it,
             * it kills the old one (if it's still waiting) and queues the new one instead.
             * Below, we're using a SingleTaskWorker with a 25ms wait-time before it fires its
             * job. If any new "value changed" events trigger before the 25ms have passed, then
             * the previous job gets thrown away and the latest one placed in the worker queue
             * instead. This ensures that we ignore *every* "value changed" event *until* there
             * has been 25ms of no more event-activity. That pefectly solves the problem, and
             * ensures that the "flutters" during the brief JTable<->JTree desync are ignored!
             * It's also short enough (1/40 of a second) that the user won't notice the delay
             * whatsoever when they are manually clicking on rows to view messages. This table
             * "fluttering" problem happens constantly in threaded view (because it inserts more
             * rows at a time, thus more fluttering), but is also common in non-threaded mode.
             * Oh and why was this needed? Because the brief, fluttering de-selection and
             * re-selection events caused the currently viewed message to be unloaded and reloaded,
             * which meant that the user lost their current reading-position. It's awful to be
             * reading a message and constantly lose the body scroll-position. This fixes it.
             * We do still end up triggering "row selected" again for the *original* message object
             * with this fix, but that's taken care of by the fact that "messageTable_itemSelected"
             * aborts instantly if that message is already being viewed, so the scrolling position
             * is not lost under any circumstances! :-)
             */
            delayedSelectionReactor.schedule(25, new Runnable() {
                public void run() {
                    // we must invokeLater the message loading since the worker is a non-GUI thread
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            messageTable_itemSelected(e);
                        }
                    });
                }
            });
        }

        public void valueChanged(final TreeSelectionEvent e) {
            boardsTree_actionPerformed(e);
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }
    }

	private class PopupMenuSubjectText
        extends JSkinnablePopupMenu
        implements ActionListener, LanguageListener
    {
        JMenuItem copySubjectText = new JMenuItem();

        public PopupMenuSubjectText() {
            super();
            initialize();
        }

        private void initialize() {
            refreshLanguage();
            copySubjectText.addActionListener(this);
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == copySubjectText) {
                CopyToClipboard.copyText(subjectTextLabel.getText());
            }
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();
            add(copySubjectText);
            super.show(invoker, x, y);
        }

        private void refreshLanguage() {
            copySubjectText.setText(language.getString("MessagePane.subjectText.popupmenu.copySubjectText"));
        }
        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }
    }

	private class PopupMenuMessageTable
        extends JSkinnablePopupMenu
        implements ActionListener, LanguageListener {

        private final JMenuItem markAllMessagesReadItem = new JMenuItem();
        private final JMenuItem markSelectedMessagesReadItem = new JMenuItem();
        private final JMenuItem markSelectedMessagesUnreadItem = new JMenuItem();
        private final JMenuItem markThreadReadItem = new JMenuItem();
        private final JMenuItem markMessageUnreadItem = new JMenuItem();
        private final JMenuItem setBADItem = new JMenuItem();
        private final JMenuItem setNEUTRALItem = new JMenuItem();
        private final JMenuItem setFRIENDItem = new JMenuItem();
        private final JMenuItem setGOODItem = new JMenuItem();

        private final JMenuItem deleteItem = new JMenuItem();
        private final JMenuItem undeleteItem = new JMenuItem();

        private final JMenuItem showItem = new JMenuItem();

        private final JMenuItem expandAllItem = new JMenuItem();
        private final JMenuItem collapseAllItem = new JMenuItem();

        private final JMenuItem expandThreadItem = new JMenuItem();
        private final JMenuItem collapseThreadItem = new JMenuItem();

        public PopupMenuMessageTable() {
            super();
            initialize();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == markMessageUnreadItem) {
//                markSelectedMessageUnread();
                markSelectedMessagesReadOrUnread(false);
            } else if (e.getSource() == markAllMessagesReadItem) {
                TOF.getInstance().markAllMessagesRead(frostMessageTab.getTofTreeModel().getSelectedNode());
            } else if (e.getSource() == markSelectedMessagesReadItem) {
                markSelectedMessagesReadOrUnread(true);
            } else if (e.getSource() == markSelectedMessagesUnreadItem) {
                markSelectedMessagesReadOrUnread(false);
            } else if (e.getSource() == markThreadReadItem) {
                markThreadRead();
            } else if (e.getSource() == deleteItem) {
                deleteSelectedMessage();
            } else if (e.getSource() == undeleteItem) {
                undeleteSelectedMessage();
            } else if (e.getSource() == showItem) {
                showCurrentMessagePopupWindow();
            } else if (e.getSource() == expandAllItem) {
                getMessageTable().expandAll(true);
            } else if (e.getSource() == collapseAllItem) {
                getMessageTable().expandAll(false);
            } else if (e.getSource() == expandThreadItem) {
                getMessageTable().expandThread(true, selectedMessage);
            } else if (e.getSource() == collapseThreadItem) {
                getMessageTable().expandThread(false, selectedMessage);
            } else if (e.getSource() == setFRIENDItem) {
                setTrustState_actionPerformed(IdentityState.FRIEND);
            } else if (e.getSource() == setBADItem) {
                setTrustState_actionPerformed(IdentityState.BAD);
            } else if (e.getSource() == setNEUTRALItem) {
                setTrustState_actionPerformed(IdentityState.NEUTRAL);
            } else if (e.getSource() == setGOODItem) {
                setTrustState_actionPerformed(IdentityState.GOOD);
            }
        }

        private void initialize() {
            refreshLanguage();

            markMessageUnreadItem.addActionListener(this);
            markAllMessagesReadItem.addActionListener(this);
            markSelectedMessagesReadItem.addActionListener(this);
            markSelectedMessagesUnreadItem.addActionListener(this);
            markThreadReadItem.addActionListener(this);
            setFRIENDItem.addActionListener(this);
            setBADItem.addActionListener(this);
            setNEUTRALItem.addActionListener(this);
            setGOODItem.addActionListener(this);
            deleteItem.addActionListener(this);
            undeleteItem.addActionListener(this);
            showItem.addActionListener(this);
            expandAllItem.addActionListener(this);
            collapseAllItem.addActionListener(this);
            expandThreadItem.addActionListener(this);
            collapseThreadItem.addActionListener(this);
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        private void refreshLanguage() {
            markMessageUnreadItem.setText(language.getString("MessagePane.messageTable.popupmenu.markMessageUnread"));
            markAllMessagesReadItem.setText(language.getString("MessagePane.messageTable.popupmenu.markAllMessagesRead"));
            markSelectedMessagesReadItem.setText(language.getString("MessagePane.messageTable.popupmenu.markSelectedMessagesReadItem"));
            markSelectedMessagesUnreadItem.setText(language.getString("MessagePane.messageTable.popupmenu.markSelectedMessagesUnreadItem"));
            markThreadReadItem.setText(language.getString("MessagePane.messageTable.popupmenu.markThreadRead"));
            setFRIENDItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToFRIEND"));
            setBADItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToBAD"));
            setNEUTRALItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToNEUTRAL"));
            setGOODItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToGOOD"));
            deleteItem.setText(language.getString("MessagePane.messageTable.popupmenu.deleteMessage"));
            undeleteItem.setText(language.getString("MessagePane.messageTable.popupmenu.undeleteMessage"));
            showItem.setText(language.getString("MessagePane.messageTable.popupmenu.showMessage"));
            expandAllItem.setText(language.getString("MessagePane.messageTable.popupmenu.expandAll"));
            collapseAllItem.setText(language.getString("MessagePane.messageTable.popupmenu.collapseAll"));
            expandThreadItem.setText(language.getString("MessagePane.messageTable.popupmenu.expandThread"));
            collapseThreadItem.setText(language.getString("MessagePane.messageTable.popupmenu.collapseThread"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {

            if( messageTable.getSelectedRowCount() < 1 ) {
                return;
            }

            if (frostMessageTab.getTofTreeModel().getSelectedNode().isBoard()) {

                removeAll();

                // menu shown if multiple rows are selected
                if( messageTable.getSelectedRowCount() > 1 ) {

                    add(markSelectedMessagesReadItem);
                    add(markSelectedMessagesUnreadItem);
                    addSeparator();
                    add(deleteItem);
                    add(undeleteItem);
                    addSeparator();
                    add(setFRIENDItem);
                    add(setGOODItem);
                    add(setNEUTRALItem);
                    add(setBADItem);

                    deleteItem.setEnabled(true);
                    undeleteItem.setEnabled(true);

                    setFRIENDItem.setEnabled(true);
                    setGOODItem.setEnabled(true);
                    setNEUTRALItem.setEnabled(true);
                    setBADItem.setEnabled(true);

                    super.show(invoker, x, y);
                    return;
                }

                if( Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS) ) {
                    if( messageTable.getSelectedRowCount() == 1 ) {
                        add(expandThreadItem);
                        add(collapseThreadItem);
                    }
                    add(expandAllItem);
                    add(collapseAllItem);
                    addSeparator();
                }

                boolean itemAdded = false;
                if (messageTable.getSelectedRow() > -1) {
                    add(markMessageUnreadItem);
                    itemAdded = true;
                }
                if( selectedMessage != null && selectedMessage.getBoard().getUnreadMessageCount() > 0 ) {
                    add(markAllMessagesReadItem);
                    add(markThreadReadItem);
                    itemAdded = true;
                }
                if( itemAdded ) {
                    addSeparator();
                }
                add(setFRIENDItem);
                add(setGOODItem);
                add(setNEUTRALItem);
                add(setBADItem);
                setFRIENDItem.setEnabled(false);
                setGOODItem.setEnabled(false);
                setNEUTRALItem.setEnabled(false);
                setBADItem.setEnabled(false);

                if (messageTable.getSelectedRow() > -1 && selectedMessage != null) {
                    if( identities.isMySelf(selectedMessage.getFromName()) ) {
                        // keep all off
                    } else if (selectedMessage.isMessageStatusFRIEND()) {
                        setGOODItem.setEnabled(true);
                        setNEUTRALItem.setEnabled(true);
                        setBADItem.setEnabled(true);
                    } else if (selectedMessage.isMessageStatusNEUTRAL()) {
                        setGOODItem.setEnabled(true);
                        setFRIENDItem.setEnabled(true);
                        setBADItem.setEnabled(true);
                    } else if (selectedMessage.isMessageStatusBAD()) {
                        setGOODItem.setEnabled(true);
                        setFRIENDItem.setEnabled(true);
                        setNEUTRALItem.setEnabled(true);
                    } else if (selectedMessage.isMessageStatusGOOD()) {
                        setFRIENDItem.setEnabled(true);
                        setNEUTRALItem.setEnabled(true);
                        setBADItem.setEnabled(true);
                    } else if (selectedMessage.isMessageStatusNONE()) {
                        // keep all buttons disabled
                    } else if (selectedMessage.isMessageStatusTAMPERED()) {
                        // keep all buttons disabled
                    } else {
                        logger.warning("invalid message state");
                    }
                }

                if (selectedMessage != null) {
                    addSeparator();
                    add(deleteItem);
                    add(undeleteItem);
                    deleteItem.setEnabled(false);
                    undeleteItem.setEnabled(false);
                    if(selectedMessage.isDeleted()) {
                        undeleteItem.setEnabled(true);
                    } else {
                        deleteItem.setEnabled(true);
                    }
					addSeparator();
					showItem.setEnabled(true);
                }

                super.show(invoker, x, y);
            }
        }
    }

    private final Logger logger = Logger.getLogger(MessagePanel.class.getName());

    private final SettingsClass settings;
    private final Language language  = Language.getInstance();
    private FrostIdentities identities;
    private JFrame parentFrame;

    private boolean initialized = false;

    private final Listener listener = new Listener();

    private FrostMessageObject selectedMessage;

    private PopupMenuMessageTable popupMenuMessageTable = null;
    private PopupMenuSubjectText popupMenuSubjectText = null;

    //private JButton downloadAttachmentsButton =
    //  new JButton(Mixed.loadImageIcon("/data/attachment.gif"));
    //private JButton downloadBoardsButton =
    //  new JButton(Mixed.loadImageIcon("/data/attachmentBoard.gif"));
    private final JButton newMessageButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-message-new.png"));
    private final JButton replyButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-reply-sender.png"));
    private final JButton saveMessageButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/document-save.png"));
    protected JButton nextUnreadMessageButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-goto-unread.png"));
    private final JButton updateBoardButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/view-refresh.png"));

    private final JButton setFRIENDButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/weather-clear.png"));
    private final JButton setGOODButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/weather-few-clouds.png"));
    private final JButton setNEUTRALButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/weather-overcast.png"));
    private final JButton setBADButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/weather-storm.png"));

    private final JToggleButton toggleShowUnreadOnly = new JToggleButton("");
    private final JToggleButton toggleShowFlaggedOnly = new JToggleButton("");
    private final JToggleButton toggleShowStarredOnly = new JToggleButton("");
    private final JToggleButton toggleShowJunkMessages = new JToggleButton("");

    private final JToggleButton toggleShowThreads = new JToggleButton("");
    private final JToggleButton toggleShowSmileys = new JToggleButton("");
    private final JToggleButton toggleShowHyperlinks = new JToggleButton("");

    private String allMessagesCountPrefix = "";
    private final JLabel allMessagesCountLabel = new JLabel("");

    private String unreadMessagesCountPrefix = "";
    private final JLabel unreadMessagesCountLabel = new JLabel("");

    public MessagePanel(final SettingsClass settings, final MainFrame mf, final FrostMessageTab fmt) {
        super();
        this.settings = settings;
        mainFrame = mf;
        frostMessageTab = fmt;
    }

    private JToolBar getButtonsToolbar() {
        // configure buttons
        MiscToolkit.configureButton(newMessageButton, "MessagePane.toolbar.tooltip.newMessage", language);
        MiscToolkit.configureButton(updateBoardButton, "MessagePane.toolbar.tooltip.update", language);
        MiscToolkit.configureButton(replyButton, "MessagePane.toolbar.tooltip.reply", language);
        MiscToolkit.configureButton(saveMessageButton, "MessagePane.toolbar.tooltip.saveMessage", language);
        MiscToolkit.configureButton(nextUnreadMessageButton, "MessagePane.toolbar.tooltip.nextUnreadMessage", language);
        MiscToolkit.configureButton(setFRIENDButton, "MessagePane.toolbar.tooltip.setToFRIEND", language);
        MiscToolkit.configureButton(setBADButton, "MessagePane.toolbar.tooltip.setToBAD", language);
        MiscToolkit.configureButton(setNEUTRALButton, "MessagePane.toolbar.tooltip.setToNEUTRAL", language);
        MiscToolkit.configureButton(setGOODButton, "MessagePane.toolbar.tooltip.setToGOOD", language);
        // MiscToolkit.configureButton(downloadAttachmentsButton,"Download attachment(s)","/data/attachment_rollover.gif",language);
        // MiscToolkit.configureButton(downloadBoardsButton,"Add Board(s)","/data/attachmentBoard_rollover.gif",language);

        replyButton.setEnabled(false);
        saveMessageButton.setEnabled(false);
        setFRIENDButton.setEnabled(false);
        setNEUTRALButton.setEnabled(false);
        setBADButton.setEnabled(false);
        setGOODButton.setEnabled(false);

        ImageIcon icon;

        toggleShowUnreadOnly.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_UNREAD_ONLY));
        icon = MiscToolkit.loadImageIcon("/data/newmessages.png");
        toggleShowUnreadOnly.setIcon(icon);
        toggleShowUnreadOnly.setRolloverEnabled(true);
        toggleShowUnreadOnly.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowUnreadOnly.setMargin(new Insets(0, 0, 0, 0));
        toggleShowUnreadOnly.setPreferredSize(new Dimension(24,24));
        toggleShowUnreadOnly.setFocusPainted(false);
        toggleShowUnreadOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowUnreadOnly"));

        toggleShowFlaggedOnly.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_FLAGGED_ONLY));
        icon = MiscToolkit.loadImageIcon("/data/flag.png");
        toggleShowFlaggedOnly.setIcon(icon);
        toggleShowFlaggedOnly.setRolloverEnabled(true);
        toggleShowFlaggedOnly.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowFlaggedOnly.setMargin(new Insets(0, 0, 0, 0));
        toggleShowFlaggedOnly.setPreferredSize(new Dimension(24,24));
        toggleShowFlaggedOnly.setFocusPainted(false);
        toggleShowFlaggedOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowFlaggedOnly"));

        toggleShowStarredOnly.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_STARRED_ONLY));
        icon = MiscToolkit.loadImageIcon("/data/star.png");
        toggleShowStarredOnly.setIcon(icon);
        toggleShowStarredOnly.setRolloverEnabled(true);
        toggleShowStarredOnly.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowStarredOnly.setMargin(new Insets(0, 0, 0, 0));
        toggleShowStarredOnly.setPreferredSize(new Dimension(24,24));
        toggleShowStarredOnly.setFocusPainted(false);
        toggleShowStarredOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowStarredOnly"));

        toggleShowJunkMessages.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_JUNK_MESSAGES));
        icon = MiscToolkit.loadImageIcon("/data/junk.png");
        toggleShowJunkMessages.setIcon(icon);
        toggleShowJunkMessages.setRolloverEnabled(true);
        toggleShowJunkMessages.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowJunkMessages.setMargin(new Insets(0, 0, 0, 0));
        toggleShowJunkMessages.setPreferredSize(new Dimension(24,24));
        toggleShowJunkMessages.setFocusPainted(false);
        toggleShowJunkMessages.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowJunkMessages"));

        toggleShowThreads.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS));
        icon = MiscToolkit.loadImageIcon("/data/toolbar/toggle-treeview.png");
        toggleShowThreads.setIcon(icon);
        toggleShowThreads.setRolloverEnabled(true);
        toggleShowThreads.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowThreads.setMargin(new Insets(0, 0, 0, 0));
        toggleShowThreads.setPreferredSize(new Dimension(24,24));
        toggleShowThreads.setFocusPainted(false);
        toggleShowThreads.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowThreads"));

        toggleShowSmileys.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_SMILEYS));
        icon = MiscToolkit.loadImageIcon("/data/toolbar/face-smile.png");
        toggleShowSmileys.setIcon(icon);
        toggleShowSmileys.setRolloverEnabled(true);
        toggleShowSmileys.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowSmileys.setMargin(new Insets(0, 0, 0, 0));
        toggleShowSmileys.setPreferredSize(new Dimension(24,24));
        toggleShowSmileys.setFocusPainted(false);
        toggleShowSmileys.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowSmileys"));

        toggleShowHyperlinks.setSelected(Core.frostSettings.getBoolValue(SettingsClass.SHOW_KEYS_AS_HYPERLINKS));
        icon = MiscToolkit.loadImageIcon("/data/toolbar/toggle-hyperlinks.gif");
        toggleShowHyperlinks.setIcon(icon);
        toggleShowHyperlinks.setRolloverEnabled(true);
        toggleShowHyperlinks.setRolloverIcon(MiscToolkit.createRolloverIcon(icon));
        toggleShowHyperlinks.setMargin(new Insets(0, 0, 0, 0));
        toggleShowHyperlinks.setPreferredSize(new Dimension(24,24));
        toggleShowHyperlinks.setFocusPainted(false);
        toggleShowHyperlinks.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowHyperlinks"));

        // build buttons panel
        final JToolBar buttonsToolbar = new JToolBar();
        buttonsToolbar.setRollover(true);
        buttonsToolbar.setFloatable(false);
        final Dimension blankSpace = new Dimension(3, 3);

        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(nextUnreadMessageButton);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(saveMessageButton);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(newMessageButton);
        buttonsToolbar.add(replyButton);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(updateBoardButton);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
    //  buttonsToolbar.add(Box.createRigidArea(blankSpace));
    //  buttonsToolbar.add(downloadAttachmentsButton);
    //  buttonsToolbar.add(downloadBoardsButton);
    //  buttonsToolbar.add(Box.createRigidArea(blankSpace));
    //  buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(setFRIENDButton);
        buttonsToolbar.add(setGOODButton);
        buttonsToolbar.add(setNEUTRALButton);
        buttonsToolbar.add(setBADButton);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(toggleShowUnreadOnly);
        buttonsToolbar.add(toggleShowFlaggedOnly);
        buttonsToolbar.add(toggleShowStarredOnly);
        buttonsToolbar.add(toggleShowJunkMessages);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.addSeparator();
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(toggleShowThreads);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(toggleShowSmileys);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));
        buttonsToolbar.add(toggleShowHyperlinks);

        buttonsToolbar.add(Box.createRigidArea(new Dimension(8, 0)));
        buttonsToolbar.add(Box.createHorizontalGlue());
        buttonsToolbar.add(allMessagesCountLabel);
        buttonsToolbar.add(Box.createRigidArea(new Dimension(8, 0)));
        buttonsToolbar.add(unreadMessagesCountLabel);
        buttonsToolbar.add(Box.createRigidArea(blankSpace));

        // listeners
        newMessageButton.addActionListener(listener);
        updateBoardButton.addActionListener(listener);
        replyButton.addActionListener(listener);
    //  downloadAttachmentsButton.addActionListener(listener);
    //  downloadBoardsButton.addActionListener(listener);
        saveMessageButton.addActionListener(listener);
        nextUnreadMessageButton.addActionListener(listener);
        setFRIENDButton.addActionListener(listener);
        setNEUTRALButton.addActionListener(listener);
        setBADButton.addActionListener(listener);
        setGOODButton.addActionListener(listener);
        toggleShowUnreadOnly.addActionListener(listener);
        toggleShowFlaggedOnly.addActionListener(listener);
        toggleShowStarredOnly.addActionListener(listener);
        toggleShowJunkMessages.addActionListener(listener);
        toggleShowThreads.addActionListener(listener);
        toggleShowSmileys.addActionListener(listener);
        toggleShowHyperlinks.addActionListener(listener);

        return buttonsToolbar;
    }

    private void updateLabelSize(final JLabel label) {
        final String labelText = label.getText();
        final JLabel dummyLabel = new JLabel(labelText + "00000");
        dummyLabel.doLayout();
        final Dimension labelSize = dummyLabel.getPreferredSize();
        label.setPreferredSize(labelSize);
        label.setMinimumSize(labelSize);
    }

    private PopupMenuMessageTable getPopupMenuMessageTable() {
        if (popupMenuMessageTable == null) {
            popupMenuMessageTable = new PopupMenuMessageTable();
            language.addLanguageListener(popupMenuMessageTable);
        }
        return popupMenuMessageTable;
    }

    private PopupMenuSubjectText getPopupMenuSubjectText() {
        if (popupMenuSubjectText == null) {
            popupMenuSubjectText = new PopupMenuSubjectText();
            language.addLanguageListener(popupMenuSubjectText);
        }
        return popupMenuSubjectText;
    }

    public void initialize() {
        if (!initialized) {
            refreshLanguage();
            language.addLanguageListener(listener);

            FrostMessageObject.sortThreadRootMsgsAscending = settings.getBoolValue(SettingsClass.SORT_THREADROOTMSGS_ASCENDING);

            indicateLowReceivedMessages = Core.frostSettings.getBoolValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES);
            indicateLowReceivedMessagesCountRed = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED);
            indicateLowReceivedMessagesCountBlue = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE);

            Core.frostSettings.addPropertyChangeListener(SettingsClass.SORT_THREADROOTMSGS_ASCENDING, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.MSGTABLE_MULTILINE_SELECT, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.MSGTABLE_SCROLL_HORIZONTAL, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE, this);

            // build messages list scroll pane
            final MessageTreeTableModel messageTableModel = new MessageTreeTableModel(new DefaultMutableTreeNode());
            language.addLanguageListener(messageTableModel);
            messageTable = new MessageTreeTable(messageTableModel);
            new TableFindAction().install(messageTable);
            updateMsgTableResizeMode();
            updateMsgTableMultilineSelect();
            messageTable.getSelectionModel().addListSelectionListener(listener);
            messageListScrollPane = new JScrollPane(messageTable);
            messageListScrollPane.setWheelScrollingEnabled(true);
            messageListScrollPane.getViewport().setBackground(messageTable.getBackground());

            messageTextPane = new MessageTextPane(mainFrame);

            final JPanel subjectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT,3,0));
            subjectPanel.add(subjectLabel);
            subjectPanel.add(subjectTextLabel);
            subjectPanel.setBorder(BorderFactory.createEmptyBorder(2, 3, 2, 3));

            subjectTextLabel.addMouseListener(listener);

            // load message table layout
            messageTable.loadLayout(settings);

            fontChanged();

            final JPanel dummyPanel = new JPanel(new BorderLayout());
            dummyPanel.add(subjectPanel, BorderLayout.NORTH);
            dummyPanel.add(messageTextPane, BorderLayout.CENTER);

            msgTableAndMsgTextSplitpane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, messageListScrollPane, dummyPanel);
            msgTableAndMsgTextSplitpane.setDividerSize(10);
            msgTableAndMsgTextSplitpane.setResizeWeight(0.5d);
            msgTableAndMsgTextSplitpane.setMinimumSize(new Dimension(50, 20));
            int dividerLoc = Core.frostSettings.getIntValue(SettingsClass.MSGTABLE_MSGTEXT_DIVIDER_LOCATION);
            if( dividerLoc < 10 ) {
                dividerLoc = 180;
            }
            msgTableAndMsgTextSplitpane.setDividerLocation(dividerLoc);

            // build main panel
            setLayout(new BorderLayout());
            add(getButtonsToolbar(), BorderLayout.NORTH);
            add(msgTableAndMsgTextSplitpane, BorderLayout.CENTER);

            // listeners
            messageTable.addMouseListener(listener);

            frostMessageTab.getTofTree().addTreeSelectionListener(listener);

            assignHotkeys();

            // display welcome message if no boards are available
            boardsTree_actionPerformed(null); // set initial states

            initialized = true;
        }
    }

    /**
     * This fantastically named function is explained in:
     * source/frost/messaging/frost/boards/TofTree.java:processKeyEvent()
     *
     * Basically, it just ensures that when the JTree receives an alphanumeric
     * keypress, it's forwarded to this function which in turn calls our own
     * key handler as if the key was pressed on our News tab. That's part of what
     * allows all of our keyboard shortcuts to work in the message list, message
     * viewer at the bottom of the window, AND in the JTree board list to the left.
     */
    public void forwardedAlphanumericKeyEventFromTree(final KeyEvent e) {
        processKeyEvent(e);
    }

    private void assignHotkeys() {

        // assign F5 key - start board update
        final Action boardUpdateAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                final TofTree tofTree = frostMessageTab.getTofTree();
                final Board selectedBoard = tofTree.getSelectedBoard();
                if( selectedBoard == null ) {
                    return;
                }
                tofTree.updateBoard(selectedBoard);
            }
        };
        frostMessageTab.setKeyActionForNewsTab(boardUpdateAction, "UPDATE_BOARD", KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0, true));

        // assign DELETE key - delete message and SHIFT+DELETE - undelete message
        final Action deleteMessageAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                deleteSelectedMessage();
            }
        };
        final Action undeleteMessageAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                undeleteSelectedMessage();
            }
        };
        frostMessageTab.setKeyActionForNewsTab(deleteMessageAction, "DEL_MSG", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, true));
        frostMessageTab.setKeyActionForNewsTab(deleteMessageAction, "DEL_MSG", KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0, true));
        frostMessageTab.setKeyActionForNewsTab(undeleteMessageAction, "UNDEL_MSG", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_MASK, true));
        frostMessageTab.setKeyActionForNewsTab(undeleteMessageAction, "UNDEL_MSG", KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.SHIFT_MASK, true));

        // remove all default ENTER assignments from table (default assignment makes enter select the next row)
        messageTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).getParent().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

        // assign ENTER key - open message viewer
        final Action openMessageAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                showCurrentMessagePopupWindow();
            }
        };
        frostMessageTab.setKeyActionForNewsTab(openMessageAction, "OPEN_MSG", KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true));

        // assign N key - next unread message in current board
        this.getActionMap().put("NEXT_MSG", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                selectNextUnreadMessage();
            }
        });
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, 0, true), "NEXT_MSG");

        // we assign these four shortcuts in the same order as the toolbar row of state icons, to avoid confusion
        // assign 1/NUMPAD1 key - set FRIEND
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0, true), "SET_FRIEND");
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0, true), "SET_FRIEND");
        this.getActionMap().put("SET_FRIEND", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setTrustState_actionPerformed(IdentityState.FRIEND);
            }
        });

        // assign 2/NUMPAD2 key - set GOOD
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0, true), "SET_GOOD");
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, 0, true), "SET_GOOD");
        this.getActionMap().put("SET_GOOD", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setTrustState_actionPerformed(IdentityState.GOOD);
            }
        });

        // assign 3/NUMPAD3 key - set NEUTRAL
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0, true), "SET_NEUTRAL");
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD3, 0, true), "SET_NEUTRAL");
        this.getActionMap().put("SET_NEUTRAL", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setTrustState_actionPerformed(IdentityState.NEUTRAL);
            }
        });

        // assign 4/NUMPAD4 key - set BAD
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0, true), "SET_BAD");
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, 0, true), "SET_BAD");
        this.getActionMap().put("SET_BAD", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setTrustState_actionPerformed(IdentityState.BAD);
            }
        });

        // assign F key - mark FLAGGED
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0, true), "MARK_FLAGGED");
        this.getActionMap().put("MARK_FLAGGED", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                updateBooleanState(BooleanState.FLAGGED);
            }
        });

        // assign S key - mark STARRED
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "MARK_STARRED");
        this.getActionMap().put("MARK_STARRED", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                updateBooleanState(BooleanState.STARRED);
            }
        });

        // assign J key - mark JUNK
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_J, 0, true), "MARK_JUNK");
        this.getActionMap().put("MARK_JUNK", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                updateBooleanState(BooleanState.JUNK);
            }
        });


        // --- view mode toggles: ---

        // assign Shift+N key - toggle "show only new (unread) messages"
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.SHIFT_MASK, true), "TOGGLE_SHOWONLY_UNREAD");
        this.getActionMap().put("TOGGLE_SHOWONLY_UNREAD", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clickToggleIfNotUpdating(toggleShowUnreadOnly);
            }
        });

        // assign Shift+F key - toggle "show only flagged messages"
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.SHIFT_MASK, true), "TOGGLE_SHOWONLY_FLAGGED");
        this.getActionMap().put("TOGGLE_SHOWONLY_FLAGGED", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clickToggleIfNotUpdating(toggleShowFlaggedOnly);
            }
        });

        // assign Shift+S key - toggle "show only starred messages"
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK, true), "TOGGLE_SHOWONLY_STARRED");
        this.getActionMap().put("TOGGLE_SHOWONLY_STARRED", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clickToggleIfNotUpdating(toggleShowStarredOnly);
            }
        });

        // assign Shift+J key - toggle "show junk messages too"
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_J, InputEvent.SHIFT_MASK, true), "TOGGLE_SHOW_JUNK");
        this.getActionMap().put("TOGGLE_SHOW_JUNK", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clickToggleIfNotUpdating(toggleShowJunkMessages);
            }
        });

        // assign Shift+T key - toggle "display conversations as threads"
        this.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.SHIFT_MASK, true), "TOGGLE_SHOW_THREADS");
        this.getActionMap().put("TOGGLE_SHOW_THREADS", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clickToggleIfNotUpdating(toggleShowThreads);
            }
        });
    }

    private void clickToggleIfNotUpdating(final JToggleButton btn) {
        // This is just a precaution: If the board is currently loading messages from disk,
        // it means that the glasspane is active and that we are **NOT ALLOWED** to switch the
        // viewmodes. However, the GUI thread is actually *blocked* while the messages are being
        // loaded, which means that the keyboard events actually become backlogged and are handled
        // when the loading is *complete*, so this will *never* be true. But checking the loading
        // state is a good precaution in case some Java implementation or some other detail somewhere
        // changes, where that blocking would no longer be happening. This check prevents failure.
        final boolean isUpdating = TOF.getInstance().isLoadingMessages();
        if( !isUpdating ) {
            // act exactly as if the user manually clicked the toolbar button
            btn.doClick();
        }
    }

    public void saveLayout(final SettingsClass frostSettings) {
        frostSettings.setValue(SettingsClass.MSGTABLE_MSGTEXT_DIVIDER_LOCATION,
                msgTableAndMsgTextSplitpane.getDividerLocation());

        getMessageTable().saveLayout(frostSettings);
    }

    private void fontChanged() {
        final String fontName = settings.getValue(SettingsClass.MESSAGE_LIST_FONT_NAME);
        final int fontStyle = settings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_STYLE);
        final int fontSize = settings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_SIZE);
        Font font = new Font(fontName, fontStyle, fontSize);
        if (!font.getFamily().equals(fontName)) {
            logger.severe(
                "The selected font was not found in your system\n"
                    + "That selection will be changed to \"SansSerif\".");
            settings.setValue(SettingsClass.MESSAGE_LIST_FONT_NAME, "SansSerif");
            font = new Font("SansSerif", fontStyle, fontSize);
        }
        messageTable.setFont(font);
    }

    /**
     * Gets the content of the message selected in the tofTable.
     * @param e This selectionEv ent is needed to determine if the Table is just being edited
     * @param table The tofTable
     * @param messages A Vector containing all MessageObjects that are just displayed by the table
     * @return The content of the message
     */
    private FrostMessageObject evalSelection(final ListSelectionEvent e, final JTable table, final Board board) {
//        if( (!e.getValueIsAdjusting() && !table.isEditing()) ) {
        if( !table.isEditing() ) {

            // more than 1 selected row is handled specially, only used to delete/undelete messages
            if( table.getSelectedRowCount() > 1 ) {
                return null;
            }
            final int row = table.getSelectedRow();
            if( row != -1 && row < getMessageTableModel().getRowCount() ) {

                final FrostMessageObject message = (FrostMessageObject)getMessageTableModel().getRow(row);

                if( message != null ) {

                    if( message.isNew() == false ) {
                        // its a read message, nothing more to do here ...
                        return message;
                    }

                    // this is a new message
                    message.setNew(false); // mark as read

                    getMessageTableModel().fireTableRowsUpdated(row, row);

                    // determine thread root msg of this msg
                    final FrostMessageObject threadRootMsg = message.getThreadRootMessage();

                    // update thread root to reset unread msg childs marker
                    if( threadRootMsg != message && threadRootMsg != null ) {
                        getMessageTreeModel().nodeChanged(threadRootMsg);
                    }

                    board.decUnreadMessageCount();

                    MainFrame.getInstance().updateMessageCountLabels(board);
                    MainFrame.getInstance().updateTofTree(board);

                    final Thread saver = new Thread() {
                        @Override
                        public void run() {
                            // save the changed isnew state into the database
                            MessageStorage.inst().updateMessage(message);
                        }
                    };
                    saver.start();

                    return message;
                }
            }
        }
        return null;
    }

    private void messageTable_itemSelected(final ListSelectionEvent e) {

        final AbstractNode selectedNode = frostMessageTab.getTofTreeModel().getSelectedNode();
        if (selectedNode.isFolder()) {
            setFRIENDButton.setEnabled(false);
            setNEUTRALButton.setEnabled(false);
            setBADButton.setEnabled(false);
            setGOODButton.setEnabled(false);
            replyButton.setEnabled(false);
            saveMessageButton.setEnabled(false);
            return;
        } else if(!selectedNode.isBoard()) {
            return;
        }

        final Board selectedBoard = (Board) selectedNode;

        // board selected
        final FrostMessageObject newSelectedMessage = evalSelection(e, messageTable, selectedBoard);
        if( newSelectedMessage == selectedMessage ) {
            return; // user is reading a message, selection did NOT change
        } else {
            selectedMessage = newSelectedMessage;
        }

        if (selectedMessage != null) {

            if( selectedMessage.isDummy() ) {
                getMessageTextPane().update_boardSelected();
                clearSubjectTextLabel();
                setFRIENDButton.setEnabled(false);
                setNEUTRALButton.setEnabled(false);
                setBADButton.setEnabled(false);
                setGOODButton.setEnabled(false);
                replyButton.setEnabled(false);
                saveMessageButton.setEnabled(false);
                return;
            }

            MainFrame.getInstance().displayNewMessageIcon(false);

            if (selectedBoard.isReadAccessBoard() == false) {
                replyButton.setEnabled(true);
            } else {
                replyButton.setEnabled(false);
            }

            if( identities.isMySelf(selectedMessage.getFromName()) ) {
                setFRIENDButton.setEnabled(false);
                setNEUTRALButton.setEnabled(false);
                setBADButton.setEnabled(false);
                setGOODButton.setEnabled(false);
            } else if (selectedMessage.isMessageStatusNEUTRAL()) {
                setNEUTRALButton.setEnabled(false);
                setFRIENDButton.setEnabled(true);
                setBADButton.setEnabled(true);
                setGOODButton.setEnabled(true);
            } else if (selectedMessage.isMessageStatusFRIEND()) {
                setFRIENDButton.setEnabled(false);
                setNEUTRALButton.setEnabled(true);
                setBADButton.setEnabled(true);
                setGOODButton.setEnabled(true);
            } else if (selectedMessage.isMessageStatusBAD()) {
                setBADButton.setEnabled(false);
                setFRIENDButton.setEnabled(true);
                setNEUTRALButton.setEnabled(true);
                setGOODButton.setEnabled(true);
            } else if (selectedMessage.isMessageStatusGOOD()) {
                setGOODButton.setEnabled(false);
                setFRIENDButton.setEnabled(true);
                setNEUTRALButton.setEnabled(true);
                setBADButton.setEnabled(true);
            } else {
                setFRIENDButton.setEnabled(false);
                setNEUTRALButton.setEnabled(false);
                setBADButton.setEnabled(false);
                setGOODButton.setEnabled(false);
            }

            getMessageTextPane().update_messageSelected(selectedMessage);
            updateSubjectTextLabel(selectedMessage.getSubject(), selectedMessage.getFromIdentity());

            if (selectedMessage.getContent().length() > 0) {
                saveMessageButton.setEnabled(true);
            } else {
                saveMessageButton.setEnabled(false);
            }

        } else {
            // no msg selected
            getMessageTextPane().update_boardSelected();
            clearSubjectTextLabel();
            replyButton.setEnabled(false);
            saveMessageButton.setEnabled(false);

            setFRIENDButton.setEnabled(false);
            setNEUTRALButton.setEnabled(false);
            setBADButton.setEnabled(false);
            setGOODButton.setEnabled(false);
        }
    }

    private void newMessageButton_actionPerformed() {
        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if( node == null || !node.isBoard() ) {
            return;
        }
        final Board board = (Board) node;
        composeNewMessage(board);
    }

    public void composeNewMessage(final Board targetBoard) {
        if( targetBoard == null || targetBoard.isReadAccessBoard() ) {
            return;
        }
        final MessageFrame newMessageFrame = new MessageFrame(settings, mainFrame);
        // NOTE: we now default to an empty subject (arg2) and refuse to post until the user has filled it in.
        newMessageFrame.composeNewMessage(targetBoard, "", "");
    }

    public void setTrustState_actionPerformed(final IdentityState idState) {

        final List<FrostMessageObject> selectedMessages = getSelectedMessages();
        if( selectedMessages.size() == 0 ) {
            return;
        }

        // set the trust state of all selected identities
        final Set<Identity> selectedIds = new HashSet<Identity>();
        final int[] rows = messageTable.getSelectedRows();
        for( final FrostMessageObject targetMessage : selectedMessages ) {
            final Identity id = getSelectedMessageFromIdentity(targetMessage);
            if( id != null ) {
                selectedIds.add(id);
            }
        }
        boolean idChanged = false;
        for( final Identity id : selectedIds ) {
            if( idState == IdentityState.FRIEND && !id.isFRIEND() ) {
                id.setFRIEND();
                idChanged = true;
            } else if( idState == IdentityState.GOOD && !id.isGOOD() ) {
                id.setGOOD();
                idChanged = true;
            } else if( idState == IdentityState.NEUTRAL && !id.isNEUTRAL() ) {
                id.setNEUTRAL();
                idChanged = true;
            } else if( idState == IdentityState.BAD && !id.isBAD() ) {
                id.setBAD();
                idChanged = true;
            }
        }
        // any id changed, gui update needed?
        if( idChanged ) {
            updateTableAfterChangeOfIdentityState();
            if( rows.length == 1 ) {
                // keep msg selected, change toolbar buttons
                setFRIENDButton.setEnabled( !(idState == IdentityState.FRIEND) );
                setNEUTRALButton.setEnabled( !(idState == IdentityState.NEUTRAL) );
                setBADButton.setEnabled( !(idState == IdentityState.BAD) );
                setGOODButton.setEnabled( !(idState == IdentityState.GOOD) );
            }
        }
    }

    private void toggleShowOnly_actionPerformed(final ActionEvent e) {

        // the first 3 buttons are mutually exclusive (unread only/flagged only/starred only),
        // since they determine what types of messages will be loaded from the database (all,
        // unread, flagged OR starred). we disable the other 2 buttons whenever one of those
        // three is clicked. the "show junk too" button isn't affected by this.
        if( e.getSource() == toggleShowUnreadOnly ) {
            toggleShowFlaggedOnly.setSelected(false);
            toggleShowStarredOnly.setSelected(false);
        } else if( e.getSource() == toggleShowFlaggedOnly ) {
            toggleShowUnreadOnly.setSelected(false);
            toggleShowStarredOnly.setSelected(false);
        } else if( e.getSource() == toggleShowStarredOnly ) {
            toggleShowUnreadOnly.setSelected(false);
            toggleShowFlaggedOnly.setSelected(false);
        }

        // these three are read manually by the board message loader
        Core.frostSettings.setValue(SettingsClass.SHOW_UNREAD_ONLY, toggleShowUnreadOnly.isSelected());
        Core.frostSettings.setValue(SettingsClass.SHOW_FLAGGED_ONLY, toggleShowFlaggedOnly.isSelected());
        Core.frostSettings.setValue(SettingsClass.SHOW_STARRED_ONLY, toggleShowStarredOnly.isSelected());
        // this one is instantly read via a property change listener by the message loader, and affects
        // the "junk" check during the isBlocked() call (which determines if a message should be hidden).
        // however, the new isBlocked() result is only seen during board loading, so we have to force a reload.
        Core.frostSettings.setValue(SettingsClass.SHOW_JUNK_MESSAGES, toggleShowJunkMessages.isSelected());

        // reload messages (the currently selected message will be re-selected automatically if still visible)
        MainFrame.getInstance().tofTree_actionPerformed(null, true);
    }

    private void toggleShowThreads_actionPerformed(final ActionEvent e) {
        final boolean oldValue = Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS);
        final boolean newValue = !oldValue;
        Core.frostSettings.setValue(SettingsClass.SHOW_THREADS, newValue);
        // reload messages (the currently selected message will be re-selected automatically if still visible)
        MainFrame.getInstance().tofTree_actionPerformed(null, true);
    }

    private void toggleShowSmileys_actionPerformed(final ActionEvent e) {
        final boolean oldValue = Core.frostSettings.getBoolValue(SettingsClass.SHOW_SMILEYS);
        final boolean newValue = !oldValue;
        Core.frostSettings.setValue(SettingsClass.SHOW_SMILEYS, newValue);
        // redraw is done in textpane by propertychangelistener!
    }

    private void toggleShowHyperlinks_actionPerformed(final ActionEvent e) {
        final boolean oldValue = Core.frostSettings.getBoolValue(SettingsClass.SHOW_KEYS_AS_HYPERLINKS);
        final boolean newValue = !oldValue;
        Core.frostSettings.setValue(SettingsClass.SHOW_KEYS_AS_HYPERLINKS, newValue);
        // redraw is done in textpane by propertychangelistener!
    }

    private void refreshLanguage() {
        newMessageButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.newMessage"));
        replyButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.reply"));
        saveMessageButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.saveMessage"));
        nextUnreadMessageButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.nextUnreadMessage"));
        setFRIENDButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.setToFRIEND"));
        setBADButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.setToBAD"));
        setNEUTRALButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.setToNEUTRAL"));
        setGOODButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.setToGOOD"));
        updateBoardButton.setToolTipText(language.getString("MessagePane.toolbar.tooltip.update"));
        toggleShowUnreadOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowUnreadOnly"));
        toggleShowFlaggedOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowFlaggedOnly"));
        toggleShowStarredOnly.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowStarredOnly"));
        toggleShowJunkMessages.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowJunkMessages"));
        toggleShowThreads.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowThreads"));
        toggleShowSmileys.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowSmileys"));
        toggleShowHyperlinks.setToolTipText(language.getString("MessagePane.toolbar.tooltip.toggleShowHyperlinks"));
        subjectLabel.setText(language.getString("MessageWindow.subject")+": ");

        allMessagesCountPrefix = language.getString("MessagePane.toolbar.labelAllMessageCount")+": ";
        allMessagesCountLabel.setText(allMessagesCountPrefix);
        unreadMessagesCountPrefix = language.getString("MessagePane.toolbar.labelNewMessageCount")+": ";
        unreadMessagesCountLabel.setText(unreadMessagesCountPrefix);

        updateLabelSize(allMessagesCountLabel);
        updateLabelSize(unreadMessagesCountLabel);
    }

    private void replyButton_actionPerformed(final ActionEvent e) {
        final FrostMessageObject origMessage = selectedMessage;
        composeReply(origMessage, parentFrame);
    }

    public void composeReply(final FrostMessageObject origMessage, final Window parent) {

        final Board targetBoard = frostMessageTab.getTofTreeModel().getBoardByName(origMessage.getBoard().getName());
        if( targetBoard == null ) {
            final String title = language.getString("MessagePane.missingBoardError.title");
            final String txt = language.formatMessage("MessagePane.missingBoardError.text", origMessage.getBoard().getName());
            MiscToolkit.showMessageDialog(parent, txt, title, MiscToolkit.ERROR_MESSAGE);
            return;
        }

        String subject = origMessage.getSubject();
        if (subject.startsWith("Re:") == false) {
            subject = "Re: " + subject;
        }

        // add msgId we answer to the inReplyTo list
        String inReplyTo = null;
        if( origMessage.getMessageId() != null ) {
            inReplyTo = origMessage.getInReplyTo();
            if( inReplyTo == null ) {
                inReplyTo = origMessage.getMessageId();
            } else {
                inReplyTo += ","+origMessage.getMessageId();
            }
        }

        if( origMessage.getRecipientName() != null ) {
            // this message was encrypted, reply encrypted

            Identity senderId = origMessage.getFromIdentity();
            if( senderId == null ) {
                final String title = language.getString("MessagePane.missingSenderIdentityError.title");
                final String txt = language.formatMessage("MessagePane.missingSenderIdentityError.text", origMessage.getFromName());
                MiscToolkit.showMessageDialog(parent, txt, title, MiscToolkit.ERROR_MESSAGE);
                return;
            }

            Identity recipientId = identities.getIdentity(origMessage.getRecipientName());
            if( recipientId == null ) {
                final String title = language.getString("MessagePane.missingRecipientIdentityError.title");
                final String txt = language.formatMessage("MessagePane.missingRecipientIdentityError.text", origMessage.getRecipientName());
                MiscToolkit.showMessageDialog(parent, txt, title, MiscToolkit.ERROR_MESSAGE);
                return;
            }

            if( !(recipientId instanceof LocalIdentity) ) {
                if ( !(senderId instanceof LocalIdentity) ) {
                    final String title = language.getString("MessagePane.bothIdentitiesAreNotLocalError.title");
                    final String txt = language.getString("MessagePane.bothIdentitiesAreNotLocalError.text");
                    MiscToolkit.showMessageDialog(parent, txt, title, MiscToolkit.ERROR_MESSAGE);
                    return;
                }

                // we want to reply to our own message, swap identities
                Identity swapId = senderId;
                senderId = recipientId;
                recipientId = swapId;
            }

            final MessageFrame newMessageFrame = new MessageFrame(settings, parent);
            newMessageFrame.composeEncryptedReply(
                    targetBoard,
                    subject,
                    inReplyTo,
                    origMessage.getCompleteContent(),
                    senderId,
                    (LocalIdentity)recipientId,
                    origMessage);
        } else {
            final MessageFrame newMessageFrame = new MessageFrame(settings, parent);
            newMessageFrame.composeReply(
                    targetBoard,
                    subject,
                    inReplyTo,
                    origMessage.getCompleteContent(),
                    origMessage);
        }
    }

    private void showMessageTablePopupMenu(final MouseEvent e) {
        // select row where rightclick occurred if row under mouse is NOT selected
        final Point p = e.getPoint();
        final int y = messageTable.rowAtPoint(p);
        if( y < 0 ) {
            return;
        }
        if( !messageTable.getSelectionModel().isSelectedIndex(y) ) {
            messageTable.getSelectionModel().setSelectionInterval(y, y);
        }
        // show popup menu
        getPopupMenuMessageTable().show(e.getComponent(), e.getX(), e.getY());
    }

    private void showCurrentMessagePopupWindow() {
        if( !isCorrectlySelectedMessage() ) {
            return;
        }
        final MessageWindow messageWindow = new MessageWindow( mainFrame, selectedMessage, this.getSize() );
        messageWindow.setVisible(true);
    }

    private void updateButton_actionPerformed(final ActionEvent e) {
        // restarts all finished threads if there are some long running threads
        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if (node != null && node.isBoard() ) {
            final Board b = (Board) node;
            if( b.isManualUpdateAllowed() ) {
                frostMessageTab.getTofTree().updateBoard(b);
            }
        }
    }

    private void boardsTree_actionPerformed(final TreeSelectionEvent e) {

        if (((TreeNode) frostMessageTab.getTofTreeModel().getRoot()).getChildCount() == 0) {
            // There are no boards
            getMessageTextPane().update_noBoardsFound();
            clearSubjectTextLabel();
        } else {
            // There are boards
            final AbstractNode node = (AbstractNode)frostMessageTab .getTofTree().getLastSelectedPathComponent();
            if (node != null) {
                if (node.isBoard()) {
                    // node is a board
                    // FIXME: reset message history!
                    getMessageTextPane().update_boardSelected();
                    clearSubjectTextLabel();
                    updateBoardButton.setEnabled(true);
                    saveMessageButton.setEnabled(false);
                    replyButton.setEnabled(false);
                    if (((Board)node).isReadAccessBoard()) {
                        newMessageButton.setEnabled(false);
                    } else {
                        newMessageButton.setEnabled(true);
                    }
                } else if(node.isFolder()) {
                    // node is a folder
                    newMessageButton.setEnabled(false);
                    saveMessageButton.setEnabled(false);
                    updateBoardButton.setEnabled(false);
                    getMessageTextPane().update_folderSelected();
                    clearSubjectTextLabel();
                }
            }
        }
    }

    /**
     * returns true if message was correctly selected
     * @return
     */
    private boolean isCorrectlySelectedMessage() {
        final int row = messageTable.getSelectedRow();
        if (row < 0
            || selectedMessage == null
            || frostMessageTab.getTofTreeModel().getSelectedNode() == null
            || !frostMessageTab.getTofTreeModel().getSelectedNode().isBoard()
            || selectedMessage.isDummy() )
        {
            return false;
        }
        return true;
    }

    private void markSelectedMessagesReadOrUnread(final boolean markRead) {
        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if( node == null || !node.isBoard() ) {
            return;
        }
        final Board board = (Board) node;

        final List<FrostMessageObject> selectedMessages = getSelectedMessages();
        if( selectedMessages.size() == 0 ) {
            return;
        }

        // set all selected messages unread
        final ArrayList<FrostMessageObject> saveMessages = new ArrayList<FrostMessageObject>();
        final DefaultTreeModel model = (DefaultTreeModel)MainFrame.getInstance().getMessageTreeModel();
        for(final FrostMessageObject targetMessage : selectedMessages ) {
            if( markRead ) {
                // mark read
                if( targetMessage.isNew() ) {
                    targetMessage.setNew(false);
                    board.decUnreadMessageCount();
                }
            } else {
                // mark unread
                if( !targetMessage.isNew() ) {
                    targetMessage.setNew(true);
                    board.incUnreadMessageCount();
                }
            }
            model.nodeChanged(targetMessage);
            saveMessages.add(targetMessage);
        }

        if( !markRead ) {
            messageTable.removeRowSelectionInterval(0, messageTable.getRowCount() - 1);
        }

        // update new and shown message count
        updateMessageCountLabels(board);
        mainFrame.updateTofTree(board);

        final Thread saver = new Thread() {
            @Override
            public void run() {
                // save message, we must save the changed deleted state
                for( final Object element : saveMessages ) {
                    final FrostMessageObject targetMessage = (FrostMessageObject)element;
                    MessageStorage.inst().updateMessage(targetMessage);
                }
            }
        };
        saver.start();
    }

    private void markThreadRead() {
        if( selectedMessage == null ) {
            return;
        }

        final TreeNode[] rootPath = selectedMessage.getPath();
        if( rootPath.length < 2 ) {
            return;
        }

        final FrostMessageObject levelOneMsg = (FrostMessageObject)rootPath[1];

        final DefaultTreeModel model = MainFrame.getInstance().getMessagePanel().getMessageTreeModel();
        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if( node == null || !node.isBoard() ) {
            return;
        }
        final Board board = (Board) node;
        final LinkedList<FrostMessageObject> msgList = new LinkedList<FrostMessageObject>();

        final Enumeration<FrostMessageObject> frostMessageObjectEnumeration = levelOneMsg.depthFirstEnumeration();
        while( frostMessageObjectEnumeration.hasMoreElements() ) {
            final FrostMessageObject frostMessageObject = frostMessageObjectEnumeration.nextElement();
            
            if( frostMessageObject.isNew() ) {
                msgList.add(frostMessageObject);
                frostMessageObject.setNew(false);
                // don't update row when row is not shown, this corrupts treetable layout
                if( MainFrame.getInstance().getMessagePanel().getMessageTable().getTree().isVisible(new TreePath(frostMessageObject.getPath())) ) {
                    model.nodeChanged(frostMessageObject);
                }
                board.decUnreadMessageCount();
            }
        }

        updateMessageCountLabels(board);
        mainFrame.updateTofTree(board);

        final Thread saver = new Thread() {
            @Override
            public void run() {
                // save message, we must save the changed deleted state into the database
                for( final Object element : msgList ) {
                    final FrostMessageObject mo = (FrostMessageObject) element;
                    MessageStorage.inst().updateMessage(mo);
                }
            }
        };
        saver.start();
    }

    public void deleteSelectedMessage() {

        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if( node == null || !node.isBoard() ) {
            return;
        }
        final Board board = (Board) node;

        final List<FrostMessageObject> selectedMessages = getSelectedMessages();
        if( selectedMessages.size() == 0 ) {
            return;
        }

        // set all selected messages deleted
        final ArrayList<FrostMessageObject> saveMessages = new ArrayList<FrostMessageObject>();
        final DefaultTreeModel model = (DefaultTreeModel)MainFrame.getInstance().getMessageTreeModel();
        for( final FrostMessageObject targetMessage : selectedMessages ) {
            targetMessage.setDeleted(true);
            if( targetMessage.isNew() ) {
                targetMessage.setNew(false);
                board.decUnreadMessageCount();
            }
            // we don't remove the message immediately, they are not loaded during next change to this board
            // needs repaint or the line which crosses the message isn't completely seen
            model.nodeChanged(targetMessage);
            saveMessages.add(targetMessage);
        }

        // update new and shown message count
        updateMessageCountLabels(board);
        mainFrame.updateTofTree(board);

        final Thread saver = new Thread() {
            @Override
            public void run() {
                // save message, we must save the changed deleted state
                for( final Object element : saveMessages ) {
                    final FrostMessageObject targetMessage = (FrostMessageObject)element;
                    MessageStorage.inst().updateMessage(targetMessage);
                }
            }
        };
        saver.start();
    }

    private void undeleteSelectedMessage() {
        final List<FrostMessageObject> selectedMessages = getSelectedMessages();
        if( selectedMessages.size() == 0 ) {
            return;
        }

        // set all selected messages deleted
        final ArrayList<FrostMessageObject> saveMessages = new ArrayList<FrostMessageObject>();
        final DefaultTreeModel model = (DefaultTreeModel)MainFrame.getInstance().getMessageTreeModel();
        for( final FrostMessageObject targetMessage : selectedMessages ) {
            if( !targetMessage.isDeleted() ) {
                continue;
            }
            targetMessage.setDeleted(false);

            // needs repaint or the line which crosses the message isn't completely seen
            model.nodeChanged(targetMessage);

            saveMessages.add(targetMessage);
        }

        final Thread saver = new Thread() {
            @Override
            public void run() {
                // save message, we must save the changed deleted state
                for( final Object element : saveMessages ) {
                    final FrostMessageObject targetMessage = (FrostMessageObject)element;
                    MessageStorage.inst().updateMessage(targetMessage);
                }
            }
        };
        saver.start();
    }

    /**
     * @return a list of all selected, non dummy messages; or an empty list
     */
    private java.util.List<FrostMessageObject> getSelectedMessages() {
        if( messageTable.getSelectedRowCount() <= 1 && !isCorrectlySelectedMessage() ) {
            return Collections.emptyList();
        }

        final int[] rows = messageTable.getSelectedRows();

        if( rows == null || rows.length == 0 ) {
            return Collections.emptyList();
        }

        final java.util.List<FrostMessageObject> msgs = new ArrayList<FrostMessageObject>(rows.length);
        for( final int ix : rows ) {
            final FrostMessageObject targetMessage = (FrostMessageObject)getMessageTableModel().getRow(ix);
            if( targetMessage != null && !targetMessage.isDummy() ) {
                msgs.add(targetMessage);
            }
        }
        return msgs;
    }

    public void setIdentities(final FrostIdentities identities) {
        this.identities = identities;
    }

    public void setParentFrame(final JFrame parentFrame) {
        this.parentFrame = parentFrame;
    }

    /**
     * Method that update the Msg and New counts for tof table
     * Expects that the boards messages are shown in table
     * @param board
     */
    public void updateMessageCountLabels(final AbstractNode node) {
        if (node.isFolder()) {
            allMessagesCountLabel.setText("");
            unreadMessagesCountLabel.setText("");
            nextUnreadMessageButton.setEnabled(false);
        } else if (node.isBoard()) {
            int allMessages = 0;
            final FrostMessageObject rootNode = (FrostMessageObject)MainFrame.getInstance().getMessageTreeModel().getRoot();
            
            final Enumeration<FrostMessageObject> frostMessageObjectEnumeration = rootNode.depthFirstEnumeration();
            while( frostMessageObjectEnumeration.hasMoreElements() ) {
                if( !(frostMessageObjectEnumeration.nextElement()).isDummy() ) {
                    allMessages++;
                }
            }
            allMessagesCountLabel.setText(allMessagesCountPrefix + allMessages);

            final int unreadMessages = ((Board)node).getUnreadMessageCount();
            unreadMessagesCountLabel.setText(unreadMessagesCountPrefix + unreadMessages);
            if( unreadMessages > 0 ) {
                nextUnreadMessageButton.setEnabled(true);
            } else {
                nextUnreadMessageButton.setEnabled(false);
            }
        }
    }

    private Identity getSelectedMessageFromIdentity(final FrostMessageObject msg) {
        if( msg == null ) {
            return null;
        }
        if( !msg.isSignatureStatusVERIFIED() ) {
            return null;
        }
        final Identity ident = msg.getFromIdentity();
        if(ident == null ) {
            logger.severe("no identity in list for from: "+msg.getFromName());
            return null;
        }
        if( ident instanceof LocalIdentity ) {
            logger.info("Ignored request to change my own ID state");
            return null;
        }
        return ident;
    }

    public FrostMessageObject getSelectedMessage() {
        if( !isCorrectlySelectedMessage() ) {
            return null;
        }
        return selectedMessage;
    }

    public void updateTableAfterChangeOfIdentityState() {
        // walk through shown messages and remove unneeded (e.g. if hideBAD)
        // remember selected msg and select next
        final AbstractNode node = frostMessageTab.getTofTreeModel().getSelectedNode();
        if( node == null || !node.isBoard() ) {
            return;
        }
        final Board board = (Board) node;
        // a board is selected and shown
        final DefaultTreeModel model = getMessageTreeModel();
        final FrostMessageObject rootNode = (FrostMessageObject)model.getRoot();

        for( final Enumeration<FrostMessageObject> e = rootNode.depthFirstEnumeration(); e.hasMoreElements(); ) {
            final FrostMessageObject frostMessageObject = e.nextElement();
            if( !(frostMessageObject instanceof FrostMessageObject) ) {
                logger.severe("frostMessageObject not of type FrostMessageObject");
                continue;
            }
            final int row = MainFrame.getInstance().getMessageTreeTable().getRowForNode(frostMessageObject);
            if( row >= 0 ) {
                getMessageTableModel().fireTableRowsUpdated(row, row);
            }
        }
        MainFrame.getInstance().updateMessageCountLabels(board);
    }

    /**
     * Search through all messages, find next unread message by date (earliest message in table).
     */
    public void selectNextUnreadMessage() {

        FrostMessageObject nextMessage = null;

        final DefaultTreeModel tableModel = getMessageTreeModel();

        // use a different method based on threaded or not threaded view
        if( Core.frostSettings.getBoolValue(SettingsClass.SHOW_THREADS) ) {

            final FrostMessageObject initial = getSelectedMessage();

            if (initial != null) {

            	final TreeNode[] path = initial.getPath();
            	final java.util.List<TreeNode> path_list = java.util.Arrays.asList(path);

            	for( int idx = initial.getLevel(); idx > 0 && nextMessage == null; idx-- ) {
            		final FrostMessageObject parent = (FrostMessageObject) path[idx];
            		final LinkedList<FrostMessageObject> queue = new LinkedList<FrostMessageObject>();
            		for( queue.add(parent); !queue.isEmpty() && nextMessage == null; ) {
            			final FrostMessageObject message = queue.removeFirst();
            			if( message.isNew() ) {
            				nextMessage = message;
            				break;
            			}

            			final Enumeration<FrostMessageObject> children = message.children();
            			while( children.hasMoreElements() ) {
            				final FrostMessageObject frostMessageObject = children.nextElement();
            				if( !path_list.contains(frostMessageObject) ) {
            					queue.add(frostMessageObject);
            				}
            			}
            		}
            	}
            }
        }
        if( nextMessage == null ) {
            final Enumeration<FrostMessageObject> frostMessageObjectEnumeration = ((FrostMessageObject) tableModel.getRoot()).depthFirstEnumeration();
            while( frostMessageObjectEnumeration.hasMoreElements() ) {
                final FrostMessageObject frostMessageObject = frostMessageObjectEnumeration.nextElement();
                if( frostMessageObject.isNew() ) {
                    if( nextMessage == null ) {
                        nextMessage = frostMessageObject;
                    } else {
                        if( nextMessage.getDateAndTimeString().compareTo(frostMessageObject.getDateAndTimeString()) > 0 ) {
                            nextMessage = frostMessageObject;
                        }
                    }
                }
            }
        }

        if( nextMessage == null ) {
            // code to move to next board???
        } else {
            messageTable.removeRowSelectionInterval(0, getMessageTableModel().getRowCount() - 1);
            messageTable.getTree().makeVisible(new TreePath(nextMessage.getPath()));
            final int row = messageTable.getRowForNode(nextMessage);
            if( row >= 0 ) {
                messageTable.addRowSelectionInterval(row, row);
                messageListScrollPane.getVerticalScrollBar().setValue(
                        (row == 0 ? row : row - 1) * messageTable.getRowHeight());
            }
        }
    }

    /**
     * Update one of flagged, starred, junk in all currently selected messages.
     */
    public void updateBooleanState(final BooleanState state) {
        updateBooleanState(state, getSelectedMessages());
    }

    /**
     * Update one of flagged, starred, junk in all messages in msgs.
     * The current state of the first message in list is toggled.
     */
    private void updateBooleanState(final BooleanState state, final List<FrostMessageObject> msgs) {
        if( msgs.isEmpty() ) {
            return;
        }

        final boolean doEnable;
        final FrostMessageObject firstMessage = msgs.get(0);
        switch(state) {
            case FLAGGED: doEnable = !firstMessage.isFlagged(); break;
            case STARRED: doEnable = !firstMessage.isStarred(); break;
            case JUNK:    doEnable = !firstMessage.isJunk(); break;
            default: return;
        }

        // if they're enabling "JUNK" for the message(s), and they want to mark NEUTRAL junk senders as BAD...
        final Set<Identity> identitiesToMarkBAD;
        if( state == BooleanState.JUNK
                && Core.frostSettings.getBoolValue(SettingsClass.JUNK_MARK_JUNK_IDENTITY_BAD)
                && doEnable )
        {
            // we set junk to true and we want to set all junk senders to BAD
            identitiesToMarkBAD = new HashSet<Identity>();
        } else {
            identitiesToMarkBAD = null;
        }

        for( final FrostMessageObject message : msgs ) {
            switch(state) {
                case FLAGGED: message.setFlagged(doEnable); break;
                case STARRED: message.setStarred(doEnable); break;
                case JUNK:    message.setJunk(doEnable); break;
            }

            final int row = MainFrame.getInstance().getMessageTreeTable().getRowForNode(message);
            if( row >= 0 ) {
                getMessageTableModel().fireTableRowsUpdated(row, row);
            }

            if( identitiesToMarkBAD != null ) {
                final Identity id = message.getFromIdentity();
                if( id != null
                        && id.isNEUTRAL() )
                {
                    identitiesToMarkBAD.add(id);
                }
            }

            // for flagged/starred, update marker on thread root message
            if( state != BooleanState.JUNK ) {
                // determine thread root msg of this msg
                final FrostMessageObject threadRootMsg = message.getThreadRootMessage();

                // update thread root to update the marker border
                if( threadRootMsg != message && threadRootMsg != null ) {
                    getMessageTreeModel().nodeChanged(threadRootMsg);
                }
            }
        }

        // if flagged or starred, update board markers in board tree
        if( state != BooleanState.JUNK ) {
            // update flagged/starred indicators in board tree
            boolean hasStarredWork = false;
            boolean hasFlaggedWork = false;
            
            final FrostMessageObject rootNode = (FrostMessageObject)firstMessage.getRoot();
            final Enumeration<FrostMessageObject> frostMessageObjectEnumeration = rootNode.depthFirstEnumeration();
            while( frostMessageObjectEnumeration.hasMoreElements()) {
            	
                final FrostMessageObject frostMessageObject = frostMessageObjectEnumeration.nextElement();
                if( !hasStarredWork && frostMessageObject.isStarred() ) {
                    hasStarredWork = true;
                }
                if( !hasFlaggedWork && frostMessageObject.isFlagged() ) {
                    hasFlaggedWork = true;
                }
                if( hasFlaggedWork && hasStarredWork ) {
                    break; // finished
                }
            }
            final Board board = firstMessage.getBoard();
            board.setFlaggedMessages(hasFlaggedWork);
            board.setStarredMessages(hasStarredWork);
            MainFrame.getInstance().updateTofTree(board);
        }

        // maybe set identities to BAD
        if( identitiesToMarkBAD != null
                && !identitiesToMarkBAD.isEmpty() )
        {
            for( final Identity id : identitiesToMarkBAD ) {
                id.setBAD();
            }

            updateTableAfterChangeOfIdentityState();
            if( msgs.size() == 1 ) {
                // keep msg selected, change toolbar buttons
                setFRIENDButton.setEnabled( true );
                setNEUTRALButton.setEnabled( true );
                setBADButton.setEnabled( false );
                setGOODButton.setEnabled( true );
            }
        }

        // save all changed messages
        final Thread saver = new Thread() {
            @Override
            public void run() {
                if( !MessageStorage.inst().beginExclusiveThreadTransaction() ) {
                    logger.severe("Failed to start EXCLUSIVE transaction in MessageStore!");
                    return;
                }
                try {
                    for( final FrostMessageObject message : msgs ) {
                        MessageStorage.inst().updateMessage(message, false);
                    }
                } finally {
                    MessageStorage.inst().endThreadTransaction();
                }
            }
        };
        saver.start();
    }

    private void updateSubjectTextLabel(final String newText, final Identity fromId) {
        if( newText == null ) {
            // clear
            subjectTextLabel.setText("");
        } else {
            subjectTextLabel.setText(newText);
        }
        ImageIcon iconToSet = null;
        if( fromId != null ) {
            if( indicateLowReceivedMessages ) {
                final int receivedMsgCount = fromId.getReceivedMessageCount();
                if( receivedMsgCount <= indicateLowReceivedMessagesCountRed ) {
                    iconToSet = MainFrame.getInstance().getMessageTreeTable().receivedOneMessage;
                } else if( receivedMsgCount <= indicateLowReceivedMessagesCountBlue ) {
                    iconToSet = MainFrame.getInstance().getMessageTreeTable().receivedFiveMessages;
                }
            }
        }
        subjectLabel.setIcon(iconToSet);
    }

    private void clearSubjectTextLabel() {
        subjectTextLabel.setText("");
        subjectLabel.setIcon(null);
    }

    public TreeTableModelAdapter getMessageTableModel() {
        return (TreeTableModelAdapter)getMessageTable().getModel();
    }
    public DefaultTreeModel getMessageTreeModel() {
        return (DefaultTreeModel)getMessageTable().getTree().getModel();
    }
    public MessageTreeTable getMessageTable() {
        return messageTable;
    }
    public MessageTextPane getMessageTextPane() {
        return messageTextPane;
    }

    private void updateMsgTableMultilineSelect() {
        if( Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_MULTILINE_SELECT) ) {
            messageTable.setSelectionMode(DefaultListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        } else {
            messageTable.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
        }
    }

    private void updateMsgTableResizeMode() {
        if( Core.frostSettings.getBoolValue(SettingsClass.MSGTABLE_SCROLL_HORIZONTAL) ) {
            // show horizontal scrollbar if needed
            getMessageTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        } else {
            // auto-resize columns, no horizontal scrollbar
            getMessageTable().setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        }
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SettingsClass.MSGTABLE_MULTILINE_SELECT)) {
            updateMsgTableMultilineSelect();
        } else if (evt.getPropertyName().equals(SettingsClass.MSGTABLE_SCROLL_HORIZONTAL)) {
            updateMsgTableResizeMode();
        } else if (evt.getPropertyName().equals(SettingsClass.SORT_THREADROOTMSGS_ASCENDING)) {
            FrostMessageObject.sortThreadRootMsgsAscending = settings.getBoolValue(SettingsClass.SORT_THREADROOTMSGS_ASCENDING);
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES)) {
            indicateLowReceivedMessages = Core.frostSettings.getBoolValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES);
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED)) {
            indicateLowReceivedMessagesCountRed = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED);
        } else if (evt.getPropertyName().equals(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE)) {
            indicateLowReceivedMessagesCountBlue = Core.frostSettings.getIntValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE);
        }
    }
}
