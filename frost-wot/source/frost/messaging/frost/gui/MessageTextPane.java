
/*
  MessageTextPane.java / Frost
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.HeadlessException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.Utilities;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.fcp.FreenetKeys;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.KeyParser;
import frost.fileTransfer.download.DownloadManager;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.gui.KnownBoardsManager;
import frost.gui.SearchMessagesConfig;
import frost.gui.TargetFolderChooser;
import frost.messaging.frost.AttachmentList;
import frost.messaging.frost.BoardAttachment;
import frost.messaging.frost.FileAttachment;
import frost.messaging.frost.FrostMessageObject;
import frost.messaging.frost.boards.Board;
import frost.messaging.frost.boards.Folder;
import frost.util.CopyToClipboard;
import frost.util.DesktopUtils;
import frost.util.FileAccess;
import frost.util.Mixed;
import frost.util.gui.MiscToolkit;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.SmileyCache;
import frost.util.gui.TextHighlighter;
import frost.util.gui.search.FindAction;
import frost.util.gui.search.TextComponentFindAction;
import frost.util.gui.textpane.AntialiasedTextPane;
import frost.util.gui.textpane.MessageDecoder;
import frost.util.gui.textpane.MouseHyperlinkEvent;
import frost.util.gui.textpane.TextPane;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;

@SuppressWarnings("serial")
public class MessageTextPane extends JPanel {

    private final Language language = Language.getInstance();
    private final Logger logger = Logger.getLogger(MessageTextPane.class.getName());

    private AntialiasedTextPane messageTextArea = null;
    private JSplitPane messageSplitPane = null;
    private JSplitPane attachmentsSplitPane = null;

    private AttachedFilesTableModel attachedFilesModel;
    private AttachedBoardTableModel attachedBoardsModel;
    private JTable filesTable = null;
    private JTable boardsTable = null;
    private JScrollPane filesTableScrollPane;
    private JScrollPane boardsTableScrollPane;
    private JScrollPane messageBodyScrollPane;

    private PopupMenuAttachmentBoard popupMenuAttachmentBoard = null;
    private PopupMenuAttachmentFile popupMenuAttachmentTable = null;
    private PopupMenuHyperLink popupMenuHyperLink = null;
    private PopupMenuTofText popupMenuTofText = null;

    private FrostMessageObject selectedMessage;

    private final MainFrame mainFrame = MainFrame.getInstance();

    private final Component parentFrame;

    private PropertyChangeListener propertyChangeListener;

    private SearchMessagesConfig searchMessagesConfig = null;
    private TextHighlighter textHighlighter = null;
    private static Color highlightColor = new Color(0x20, 0xFF, 0x20); // light green
    private static Color idLineHighlightColor = Color.LIGHT_GRAY;
    private final TextHighlighter idLineTextHighlighter = new TextHighlighter(idLineHighlightColor);

    public MessageTextPane(final Component parentFrame) {
        this(parentFrame, null);
    }

    public MessageTextPane(final Component parentFrame, final SearchMessagesConfig smc) {
        super();
        this.parentFrame = parentFrame;
        this.searchMessagesConfig = smc;
        initialize();
    }

    /**
     * Called if there are no boards in the board list.
     */
    public void update_noBoardsFound() {
        messageSplitPane.setBottomComponent(null);
        messageSplitPane.setDividerSize(0);
        setMessageText(language.getString("MessagePane.defaultText.welcomeMessage"));
    }

    /**
     * Called if a board is selected, but no message in message table.
     */
    public void update_boardSelected() {
        messageSplitPane.setBottomComponent(null);
        messageSplitPane.setDividerSize(0);
        setMessageText(language.getString("MessagePane.defaultText.noMessageSelected"));
    }

    /**
     * Called if a folder is selected.
     */
    public void update_folderSelected() {
        messageSplitPane.setBottomComponent(null);
        messageSplitPane.setDividerSize(0);
        setMessageText(language.getString("MessagePane.defaultText.noBoardSelected"));
    }

    private void setMessageText(final String txt) {
        idLineTextHighlighter.removeHighlights(messageTextArea);
        SmileyCache.clearCachedSmileys();
        messageTextArea.setText(txt);
    }

    public TextPane getTextArea() {
        return messageTextArea;
    }

    /**
     * Find the offset in text where the caret must be positioned to
     * show the line at 'offset' on top of visible text.
     * Scans through the visible text and counts 'linesDown' visible lines (maybe wrapped!).
     */
    private int calculateCaretPosition(final JTextComponent c, int offset, int linesDown) {
        final int len = c.getDocument().getLength();
        try {
            while (offset < len) {
                int end = Utilities.getRowEnd(c, offset);
                if (end < 0) {
                    break;
                }

                // Include the last character on the line
                end = Math.min(end+1, len);

                offset = end;
                linesDown--;
                if( linesDown == 0 ) {
                    return offset;
                }
            }
        } catch (final BadLocationException e) {
        }
        return len;
    }

    /**
     * Called if a message is selected.
     */
    public void update_messageSelected(final FrostMessageObject msg) {

        selectedMessage = msg;

        if( textHighlighter != null ) {
            textHighlighter.removeHighlights(messageTextArea);
        }

        final AttachmentList<FileAttachment> fileAttachments = selectedMessage.getAttachmentsOfTypeFile();
        final AttachmentList<BoardAttachment> boardAttachments = selectedMessage.getAttachmentsOfTypeBoard();
        
        attachedFilesModel.setData(fileAttachments);
        attachedBoardsModel.setData(boardAttachments);

        final int textViewHeight = positionDividers(fileAttachments.size(), boardAttachments.size());

        setMessageText(selectedMessage.getContent());

        messageBodyScrollPane.getVerticalScrollBar().setValueIsAdjusting(true);
        messageBodyScrollPane.getVerticalScrollBar().setValue(0);

        // for search messages don't scroll down to begin of text
        if( searchMessagesConfig == null ) {
            int pos = selectedMessage.getIdLinePos();
            final int len = selectedMessage.getIdLineLen();
            if( pos > -1 && len > 10 ) {
                // highlite id line if there are valid infos abpout the idline in message
                idLineTextHighlighter.highlight(messageTextArea, pos, len, true);
            } else {
                // fallback
                pos = selectedMessage.getContent().lastIndexOf("----- "+selectedMessage.getFromName()+" ----- ");
            }

            if( pos >= 0 ) {
                // scroll to begin of reply
                final int h = messageTextArea.getFontMetrics(messageTextArea.getFont()).getHeight();
                final int s = textViewHeight; // messageBodyScrollPane.getViewport().getHeight();
                final int v = s/h - 1; // how many lines are visible?

                pos = calculateCaretPosition(messageTextArea, pos, v);

                messageTextArea.getCaret().setDot(pos);
            } else {
                // scroll to end of message
                pos = selectedMessage.getContent().length();
                messageTextArea.getCaret().setDot(pos);
            }
        }

        messageBodyScrollPane.getVerticalScrollBar().setValueIsAdjusting(false);

        if( searchMessagesConfig != null &&
            searchMessagesConfig.contentPattern != null )
        {
            // highlight words in content that the user searched for; only triggered if the message is opened from the search pane
            if( textHighlighter == null ) {
                textHighlighter = new TextHighlighter(highlightColor, true); // true = case-insensitive highlighting
            }
            textHighlighter.highlight(messageTextArea, searchMessagesConfig.contentPattern, false); // use the regex pattern for highlighting
        }
    }

    private void initialize() {

        setLayout(new BorderLayout());

        final MessageDecoder decoder = new MessageDecoder();
        decoder.setSmileyDecode(Core.frostSettings.getBoolValue(SettingsClass.SHOW_SMILEYS));
        decoder.setFreenetKeysDecode(Core.frostSettings.getBoolValue(SettingsClass.SHOW_KEYS_AS_HYPERLINKS));
        messageTextArea = new AntialiasedTextPane(decoder);
        messageTextArea.setEditable(false);
        messageTextArea.setDoubleBuffered(true);
        messageTextArea.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
//        messageTextArea.setLineWrap(true);
//        messageTextArea.setWrapStyleWord(true);

        messageTextArea.setAntiAliasEnabled(Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BODY_ANTIALIAS));

        messageBodyScrollPane = new JScrollPane(messageTextArea);
        messageBodyScrollPane.setWheelScrollingEnabled(true);

        // build attached files scroll pane
        attachedFilesModel = new AttachedFilesTableModel();
        filesTable = new JTable(attachedFilesModel);
        attachedFilesModel.configureTable(filesTable);
        filesTableScrollPane = new JScrollPane(filesTable);
        filesTableScrollPane.setWheelScrollingEnabled(true);

        // build attached boards scroll pane
        attachedBoardsModel = new AttachedBoardTableModel();
        boardsTable = new JTable(attachedBoardsModel) {
            DescColumnRenderer descColRenderer = new DescColumnRenderer();
            @Override
            public TableCellRenderer getCellRenderer(final int row, final int column) {
                if( column == 2 ) {
                    return descColRenderer;
                }
                return super.getCellRenderer(row, column);
            }
            // renderer that show a tooltip text, used for the description column
            class DescColumnRenderer extends DefaultTableCellRenderer {
                @Override
                public Component getTableCellRendererComponent(
                    final JTable table,
                    final Object value,
                    final boolean isSelected,
                    final boolean hasFocus,
                    final int row,
                    final int column)
                {
                    super.getTableCellRendererComponent(
                        table,
                        value,
                        isSelected,
                        hasFocus,
                        row,
                        column);

                    final String sval = (String)value;
                    if( sval != null &&
                        sval.length() > 0 )
                    {
                        setToolTipText(sval);
                    } else {
                        setToolTipText(null);
                    }
                    return this;
                }
            }
        };
        boardsTableScrollPane = new JScrollPane(boardsTable);
        boardsTableScrollPane.setWheelScrollingEnabled(true);

        fontChanged();

        //Put everything together
        attachmentsSplitPane =
            new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                filesTableScrollPane,
                boardsTableScrollPane);
        attachmentsSplitPane.setResizeWeight(0.5);
        attachmentsSplitPane.setDividerSize(3);
        attachmentsSplitPane.setDividerLocation(0.5);

        messageSplitPane =
            new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                messageBodyScrollPane,
                attachmentsSplitPane);
        messageSplitPane.setDividerSize(0);
        messageSplitPane.setDividerLocation(1.0);
        messageSplitPane.setResizeWeight(1.0);

        add(messageSplitPane, BorderLayout.CENTER);

        messageTextArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTofTextAreaPopupMenu(e);
                }
            }
            @Override
            public void mouseReleased(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTofTextAreaPopupMenu(e);
                }
            }
        });
        messageTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(final KeyEvent e) {
                if( e == null ) {
                    return;
                } else if(e.getKeyChar() == KeyEvent.VK_DELETE && parentFrame == mainFrame ) {
                    mainFrame.getMessagePanel().deleteSelectedMessage();
                }
            }
        });

        final FindAction findAction = new TextComponentFindAction();
        findAction.install(messageTextArea);

        messageTextArea.addHyperlinkListener(new HyperlinkListener() {
            public void hyperlinkUpdate(final HyperlinkEvent evt) {
                if( !(evt instanceof MouseHyperlinkEvent) ) {
                    logger.severe("INTERNAL ERROR, hyperlinkevent is wrong object!");
                    return;
                }
                final MouseHyperlinkEvent e = (MouseHyperlinkEvent) evt;
                if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    // user clicked on 'clickedKey', List 'allKeys' contains all keys from entire message (and quoted history)
                    // NOTE: The all/current lists do NOT include Freesite keys (ones matching FreenetKeys.isFreesiteKey()).
                    final List<String> allKeys = ((MessageDecoder)messageTextArea.getDecoder()).getHyperlinkedKeys();
                    final List<String> currentMessageKeys = ((MessageDecoder)messageTextArea.getDecoder()).getHyperlinkedKeys(selectedMessage.getIdLinePos());
                    final String clickedKey = e.getDescription();
                    // show menu to download this/all keys and copy this/all to clipboard
                    showHyperLinkPopupMenu(
                            e,
                            clickedKey,
                            allKeys,
                            currentMessageKeys,
                            e.getMouseEvent().getX(),
                            e.getMouseEvent().getY());
                }
            }
        });
        filesTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAttachedFilesPopupMenu(e);
                }
            }
            @Override
            public void mouseReleased(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAttachedFilesPopupMenu(e);
                }
            }
        });
        boardsTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAttachedBoardsPopupMenu(e);
                }
            }
            @Override
            public void mouseReleased(final MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showAttachedBoardsPopupMenu(e);
                }
            }
        });

        propertyChangeListener = new PropertyChangeListener() {
            public void propertyChange(final PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(SettingsClass.MESSAGE_BODY_ANTIALIAS)) {
                    messageTextArea.setAntiAliasEnabled(Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BODY_ANTIALIAS));
                } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_BODY_FONT_NAME)) {
                    fontChanged();
                } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_BODY_FONT_SIZE)) {
                    fontChanged();
                } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_BODY_FONT_STYLE)) {
                    fontChanged();
                } else if (evt.getPropertyName().equals(SettingsClass.SHOW_SMILEYS)) {
                    ((MessageDecoder)messageTextArea.getDecoder()).setSmileyDecode(Core.frostSettings.getBoolValue(SettingsClass.SHOW_SMILEYS));
                    if( selectedMessage != null ) {
                        update_messageSelected(selectedMessage);
                    } else {
                        setMessageText(messageTextArea.getText());
                    }
                } else if (evt.getPropertyName().equals(SettingsClass.SHOW_KEYS_AS_HYPERLINKS)) {
                    ((MessageDecoder)messageTextArea.getDecoder()).setFreenetKeysDecode(Core.frostSettings.getBoolValue(SettingsClass.SHOW_KEYS_AS_HYPERLINKS));
                    if( selectedMessage != null ) {
                        update_messageSelected(selectedMessage);
                    } else {
                        setMessageText(messageTextArea.getText());
                    }
                }
            }
        };

        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_NAME, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_SIZE, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_STYLE, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_BODY_ANTIALIAS, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.SHOW_SMILEYS, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.SHOW_KEYS_AS_HYPERLINKS, propertyChangeListener);
    }

    private void fontChanged() {
        final String fontName = Core.frostSettings.getValue(SettingsClass.MESSAGE_BODY_FONT_NAME);
        final int fontStyle = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_STYLE);
        final int fontSize = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_SIZE);
        Font font = new Font(fontName, fontStyle, fontSize);
        if (!font.getFamily().equals(fontName)) {
            logger.severe(
                "The selected font was not found in your system\n"
                    + "That selection will be changed to \"Monospaced\".");
            Core.frostSettings.setValue(SettingsClass.MESSAGE_BODY_FONT_NAME, "Monospaced");
            font = new Font("Monospaced", fontStyle, fontSize);
        }
        messageTextArea.setFont(font);
    }

    private int positionDividers(final int attachedFiles, final int attachedBoards) {

        if (attachedFiles == 0 && attachedBoards == 0) {
            // Neither files nor boards
            messageSplitPane.setBottomComponent(null);
            messageSplitPane.setDividerSize(0);
            messageSplitPane.setDividerLocation(1.0);
            return messageSplitPane.getDividerLocation();
        }

        messageSplitPane.setDividerSize(3);
        messageSplitPane.setDividerLocation(0.75);

        if (attachedFiles != 0 && attachedBoards == 0) {
            // Only files
            attachmentsSplitPane.setTopComponent(null);
            attachmentsSplitPane.setBottomComponent(null);

            messageSplitPane.setBottomComponent(filesTableScrollPane);
            return messageSplitPane.getDividerLocation();
        }
        if (attachedFiles == 0 && attachedBoards != 0) {
            // Only boards
            attachmentsSplitPane.setTopComponent(null);
            attachmentsSplitPane.setBottomComponent(null);

            messageSplitPane.setBottomComponent(boardsTableScrollPane);
            return messageSplitPane.getDividerLocation();
        }
        if (attachedFiles != 0 && attachedBoards != 0) {
            // Both files and boards
            attachmentsSplitPane.setTopComponent(filesTableScrollPane);
            attachmentsSplitPane.setBottomComponent(boardsTableScrollPane);

            messageSplitPane.setBottomComponent(attachmentsSplitPane);
        }
        return messageSplitPane.getDividerLocation();
    }

    public void saveMessageButton_actionPerformed() {
        FileAccess.saveDialog(
            MainFrame.getInstance(),
            messageTextArea.getText(),
            Core.frostSettings.getValue(SettingsClass.DIR_LAST_USED),
            language.getString("MessagePane.messageText.saveDialog.title"));
    }

    private void addBoardsToKnownBoards() {
        int[] selectedRows = boardsTable.getSelectedRows();

        if (selectedRows.length == 0) {
            // add all rows
            boardsTable.selectAll();
            selectedRows = boardsTable.getSelectedRows();
            if (selectedRows.length == 0) {
                return;
            }
        }
        final AttachmentList<BoardAttachment> boards = selectedMessage.getAttachmentsOfTypeBoard();
        final LinkedList<Board> addBoards = new LinkedList<Board>();
        for( final int element : selectedRows ) {
            addBoards.add(boards.get(element).getBoardObj());
        }

        KnownBoardsManager.addNewKnownBoards(addBoards);
    }
    
    /**
     * Adds all boards from the attachedBoardsTable to board list.
     * If targetFolder is null the boards are added to the root folder.
     */
    private void downloadBoards(final Folder targetFolder) {
        logger.info("adding boards");
        int[] selectedRows = boardsTable.getSelectedRows();

        if (selectedRows.length == 0) {
            // add all rows
            boardsTable.selectAll();
            selectedRows = boardsTable.getSelectedRows();
            if (selectedRows.length == 0) {
                return;
            }
        }
        final AttachmentList<BoardAttachment> boardAttachmentList = selectedMessage.getAttachmentsOfTypeBoard();
        for( final int element : selectedRows ) {
            final Board fbo = boardAttachmentList.get(element).getBoardObj();
            final String boardName = fbo.getName();

            // search board in exising boards list
            final Board board = mainFrame.getFrostMessageTab().getTofTreeModel().getBoardByName(boardName);

            //ask if we already have the board
            if (board != null) {
                if (MiscToolkit.showConfirmDialog(
                        this,
                        "You already have a board named " + boardName + ".\n" +
                            "Are you sure you want to add this one over it?",
                        "Board already exists",
                        MiscToolkit.YES_NO_OPTION) != MiscToolkit.YES_OPTION)
                {
                    continue; // next row of table / next attached board
                } else {
                    // change existing board keys to keys of new board
                    board.setPublicKey(fbo.getPublicKey());
                    board.setPrivateKey(fbo.getPrivateKey());
                    mainFrame.updateTofTree(board);
                }
            } else {
                // its a new board
                if(targetFolder == null) {
                    mainFrame.getFrostMessageTab().getTofTreeModel().addNodeToTree(fbo);
                } else {
                    mainFrame.getFrostMessageTab().getTofTreeModel().addNodeToTree(fbo, targetFolder);
                }
            }
        }
    }

    private void showAttachedBoardsPopupMenu(final MouseEvent e) {
        if (popupMenuAttachmentBoard == null) {
            popupMenuAttachmentBoard = new PopupMenuAttachmentBoard();
            language.addLanguageListener(popupMenuAttachmentBoard);
        }
        popupMenuAttachmentBoard.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showAttachedFilesPopupMenu(final MouseEvent e) {
        if (popupMenuAttachmentTable == null) {
            popupMenuAttachmentTable = new PopupMenuAttachmentFile();
            language.addLanguageListener(popupMenuAttachmentTable);
        }
        popupMenuAttachmentTable.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showHyperLinkPopupMenu(final HyperlinkEvent e, final String clickedKey, final List<String> allKeys, final List<String> currentMessageKeys, final int x, final int y) {
        if (popupMenuHyperLink == null) {
            popupMenuHyperLink = new PopupMenuHyperLink();
            language.addLanguageListener(popupMenuHyperLink);
        }
        popupMenuHyperLink.setClickedKey(clickedKey);
        popupMenuHyperLink.setAllKeys(allKeys);
        popupMenuHyperLink.setCurrentMessageKeys(currentMessageKeys);

        popupMenuHyperLink.show(messageTextArea, x, y);
    }

    private void showTofTextAreaPopupMenu(final MouseEvent e) {
        if (popupMenuTofText == null) {
            popupMenuTofText = new PopupMenuTofText(messageTextArea);
            language.addLanguageListener(popupMenuTofText);
        }
        popupMenuTofText.show(e.getComponent(), e.getX(), e.getY());
    }
    
    
    private void addKeysInText(final String text) {
        // start "add new downloads" dialog
        // NOTE: text = find keys in this text, true = ask before redownloading keys,
        // null, null = do not override prefix or download dir, false = no "paste more" button,
        // msg = associate found keys with originating message
        FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(text, true, null, null, false, selectedMessage);
    }
    
    private void addKeysOfCurrentMessage() {
    	// get message content
    	String threadMessageContent = selectedMessage.getContent();
    	
    	// get start of current message
    	int pos = selectedMessage.getIdLinePos();
    	String currentMessageText; 
    	if( pos > -1 ) {
    		currentMessageText = threadMessageContent.substring(pos);
    	} else {
    		currentMessageText = threadMessageContent;
    	}

    	addKeysInText(currentMessageText);
    }
   
    private class PopupMenuAttachmentBoard
    extends JSkinnablePopupMenu
    implements ActionListener, LanguageListener {

//        private JMenuItem cancelItem = new JMenuItem();
        private final JMenuItem saveBoardsItem = new JMenuItem();
        private final JMenuItem saveBoardsToFolderItem = new JMenuItem();
        private final JMenuItem addBoardsToKnownBoards = new JMenuItem();

        public PopupMenuAttachmentBoard() {
            super();
            initialize();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == saveBoardsItem) {
                downloadBoards(null);
            } else if (e.getSource() == saveBoardsToFolderItem) {
                final TargetFolderChooser tfc = new TargetFolderChooser(mainFrame.getFrostMessageTab().getTofTreeModel());
                final Folder targetFolder = tfc.startDialog();
                if( targetFolder != null ) {
                    downloadBoards(targetFolder);
                }
            } else if( e.getSource() == addBoardsToKnownBoards ) {
                addBoardsToKnownBoards();
            }
        }

        private void initialize() {
            languageChanged(null);

            saveBoardsItem.addActionListener(this);
            saveBoardsToFolderItem.addActionListener(this);
            addBoardsToKnownBoards.addActionListener(this);
        }

        public void languageChanged(final LanguageEvent event) {
            saveBoardsItem.setText(language.getString("MessagePane.boardAttachmentTable.popupmenu.addBoards"));
            saveBoardsToFolderItem.setText(language.getString("MessagePane.boardAttachmentTable.popupmenu.addBoardsToFolder")+" ...");
            addBoardsToKnownBoards.setText(language.getString("MessagePane.boardAttachmentTable.popupmenu.addBoardsToKnownBoards"));
//            cancelItem.setText(language.getString("Common.cancel"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            add(saveBoardsItem);
            add(saveBoardsToFolderItem);
            add(addBoardsToKnownBoards);
//            addSeparator();
//            add(cancelItem);

            super.show(invoker, x, y);
        }
    }

    private class PopupMenuAttachmentFile
        extends JSkinnablePopupMenu
        implements ActionListener, LanguageListener {

//        private JMenuItem cancelItem = new JMenuItem();
        private final JMenuItem saveAttachmentItem = new JMenuItem();
        private final JMenuItem saveAttachmentsItem = new JMenuItem();

        private final JMenu copyToClipboardMenu = new JMenu();
        private final JMenuItem copyKeysAndNamesItem = new JMenuItem();
        private final JMenuItem copyExtendedInfoItem = new JMenuItem();

        private final JMenuItem openFileInBrowserItem = new JMenuItem();
        private final JMenuItem openAllImageAttachmentsInBrowserItem = new JMenuItem();

        public PopupMenuAttachmentFile() throws HeadlessException {
            super();
            initialize();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == saveAttachmentItem) {
                downloadAttachments(false);
            } else if (e.getSource() == saveAttachmentsItem) {
                downloadAttachments(true);
            } else if (e.getSource() == copyKeysAndNamesItem) {
                CopyToClipboard.copyKeysAndFilenames( getAttachmentItems(false).toArray(), true ); // true = cleanup keys
            } else if (e.getSource() == copyExtendedInfoItem) {
                CopyToClipboard.copyExtendedInfo( getAttachmentItems(false).toArray(), true ); // true = cleanup keys
            } else if (e.getSource() == openFileInBrowserItem) {
            	openFileInBrowser_actionWrapper( getAttachmentItems(false), false );
            } else if (e.getSource() == openAllImageAttachmentsInBrowserItem) {
            	openFileInBrowser_actionWrapper( getAttachmentItems(true), true );
            }
        }

        private void initialize() {
            languageChanged(null);

            copyToClipboardMenu.add(copyKeysAndNamesItem);
            copyToClipboardMenu.add(copyExtendedInfoItem);

            copyKeysAndNamesItem.addActionListener(this);
            copyExtendedInfoItem.addActionListener(this);

            saveAttachmentsItem.addActionListener(this);
            saveAttachmentItem.addActionListener(this);
            
            openFileInBrowserItem.addActionListener(this);
            openAllImageAttachmentsInBrowserItem.addActionListener(this);
        }

        public void languageChanged(final LanguageEvent event) {
            copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
            copyExtendedInfoItem.setText(language.getString("Common.copyToClipBoard.copyExtendedInfo"));
            copyToClipboardMenu.setText(language.getString("Common.copyToClipBoard") + "...");

            saveAttachmentsItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.downloadAttachments"));
            saveAttachmentItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.downloadSelectedAttachment"));

            openFileInBrowserItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.openAttachmentInBrowser"));
            openAllImageAttachmentsInBrowserItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.openAllImageAttachmentsInBrowser"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            add(copyToClipboardMenu);
            addSeparator();
            add(saveAttachmentItem);
            add(saveAttachmentsItem);

            String browserAddress = Core.frostSettings.getValue(SettingsClass.BROWSER_ADDRESS);
            if( browserAddress != null && browserAddress.length() > 0 ) {
                addSeparator();
                add(openFileInBrowserItem);
                add(openAllImageAttachmentsInBrowserItem);
            }

            super.show(invoker, x, y);
        }

        /**
         * Adds either the selected or all files from the attachmentTable to downloads table.
         */
        private void downloadAttachments(final boolean allAttachments) {
            final Iterator<FileAttachment> it = getAttachmentItems(allAttachments).iterator();

            // we'll be building a list of download items based on the original key-names,
            // so that we ignore the possibly-prefixed/renamed/wrong attachment filename + size.
            ArrayList<FrostDownloadItem> frostDownloadItemList = new ArrayList<FrostDownloadItem>();
            final KeyParser.ParseResult result = new KeyParser.ParseResult();

            while( it.hasNext() ) {
                final FileAttachment fa = it.next();

                final String key = fa.getKey();
                if( key != null ) {
                    // attempt to parse and validate the current key and extract its real filename
                    // NOTE: we don't want Freesite keys so the 2nd argument is false
                    KeyParser.parseKeyFromLine(key, false, result);

                    // if this key was valid, then create a new download object for it
                    if( result.key != null ) {
                        frostDownloadItemList.add(new FrostDownloadItem(result.fileName, result.key));
                    }
                }
            }

            // start "add new downloads" dialog
            // NOTE: 1st arg = the list of keys to queue, true = ask before redownloading keys,
            // null, null = do not override prefix or download dir, false = no "paste more" button,
            // msg = associate found keys with originating message
            FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(frostDownloadItemList, true, null, null, false, selectedMessage);
        }

        /**
         * Returns a list of all items to process, either selected ones or all.
         */
        private AttachmentList<FileAttachment> getAttachmentItems(final boolean allAttachments) {
            if( allAttachments ) {
                // return all attachments
                return selectedMessage.getAttachmentsOfTypeFile();
            } else {
                final AttachmentList<FileAttachment> attachments = selectedMessage.getAttachmentsOfTypeFile();
                AttachmentList<FileAttachment> items = new AttachmentList<FileAttachment>();

                final int[] selectedRows = filesTable.getSelectedRows();
                if( selectedRows.length > 0 ) {
                    for( final int rowPos : selectedRows ) {
                        items.add(attachments.get(rowPos));
                    }
                }

                return items;
            }
        }
        
        private void openFileInBrowser_actionWrapper(final List<FileAttachment> fileAttachmentList, final boolean imagesOnly) {
        	List<String> keys = new LinkedList<String>();
        	for(final FileAttachment fileAttachment : fileAttachmentList) {
        		keys.add(fileAttachment.getKey());
            }
        	openFileInBrowser_action(keys, imagesOnly);
        }
    }

	private class PopupMenuHyperLink
    extends JSkinnablePopupMenu
    implements ActionListener, LanguageListener {

//        private final JMenuItem cancelItem = new JMenuItem();

        private final JMenuItem copyKeyOnlyToClipboard = new JMenuItem();

        private final JMenuItem copyFreesiteLinkToClipboard = new JMenuItem();

        private final JMenuItem copyFileLinkToClipboard = new JMenuItem();
        private final JMenuItem copyAllFileLinksToClipboard = new JMenuItem();
        private final JMenuItem copyAllFileLinksOfMessageToClipboard = new JMenuItem();

        private final JMenuItem downloadFile = new JMenuItem();
        private final JMenuItem downloadAllFiles = new JMenuItem();
        private final JMenuItem downloadAllFilesOfMessage = new JMenuItem();

        private final JMenuItem openFileInBrowser = new JMenuItem();
        private final JMenuItem openAllImageFilesInBrowser = new JMenuItem();
        private final JMenuItem openAllImageFilesOfMessageInBrowser = new JMenuItem();

        private String clickedKey = null;
        private List<String> allKeys = null;
        private List<String> currentMessageKeys = null;

        public PopupMenuHyperLink() throws HeadlessException {
            super();
            initialize();
        }

        public void setClickedKey(final String s) {
            clickedKey = s;
        }
        public void setAllKeys(final List<String> listAllKeys) {
            allKeys = listAllKeys;
        }
        public void setCurrentMessageKeys(final List<String> listCurrentMessageKeys) {
            currentMessageKeys = listCurrentMessageKeys;
        }

        public void actionPerformed(final ActionEvent e) {
            if( e.getSource() == copyKeyOnlyToClipboard ) {
                copyToClipboard(Collections.singletonList(clickedKey));
            } else if( e.getSource() == copyFreesiteLinkToClipboard ) {
                copyToClipboard(Collections.singletonList(clickedKey));
            } else if( e.getSource() == copyFileLinkToClipboard ) {
                copyToClipboard(Collections.singletonList(clickedKey));
            } else if( e.getSource() == copyAllFileLinksToClipboard ) {
                copyToClipboard(allKeys);
            } else if( e.getSource() == copyAllFileLinksOfMessageToClipboard ) {
                copyToClipboard(currentMessageKeys);
            } else if( e.getSource() == downloadFile ) {
                downloadItems(false);
            } else if( e.getSource() == downloadAllFiles ) {
                downloadItems(true);
            } else if( e.getSource() == downloadAllFilesOfMessage ) {
                addKeysOfCurrentMessage();
            } else if( e.getSource() == openFileInBrowser ) {
                openFileInBrowser_action(Collections.singletonList(clickedKey), false);
            } else if( e.getSource() == openAllImageFilesInBrowser ) {
                openFileInBrowser_action(allKeys, true);
            } else if( e.getSource() == openAllImageFilesOfMessageInBrowser ) {
                openFileInBrowser_action(currentMessageKeys, true);
            }
        }

        private void initialize() {
            languageChanged(null);

            copyKeyOnlyToClipboard.addActionListener(this);
            copyFreesiteLinkToClipboard.addActionListener(this);
            copyFileLinkToClipboard.addActionListener(this);
            copyAllFileLinksToClipboard.addActionListener(this);
            copyAllFileLinksOfMessageToClipboard.addActionListener(this);
            downloadFile.addActionListener(this);
            downloadAllFiles.addActionListener(this);
            downloadAllFilesOfMessage.addActionListener(this);
            
            openFileInBrowser.addActionListener(this);
            openAllImageFilesInBrowser.addActionListener(this);
            openAllImageFilesOfMessageInBrowser.addActionListener(this);
        }

        public void languageChanged(final LanguageEvent event) {
            copyKeyOnlyToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyKeyToClipboard"));
            copyFreesiteLinkToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyFreesiteLinkToClipboard"));
            copyFileLinkToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyFileKeyToClipboard"));
            copyAllFileLinksToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyAllFileKeysToClipboard"));
            copyAllFileLinksOfMessageToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyAllFileKeysOfMessageToClipboard"));
            downloadFile.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadFileKey"));
            downloadAllFiles.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadAllFileKeys"));
            downloadAllFilesOfMessage.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadAllFileKeysOfMessage"));
            openFileInBrowser.setText(language.getString("MessagePane.hyperlink.popupmenu.openFileInBrowser"));
            openAllImageFilesInBrowser.setText(language.getString("MessagePane.hyperlink.popupmenu.openAllImageFilesInBrowser"));
            openAllImageFilesOfMessageInBrowser.setText(language.getString("MessagePane.hyperlink.popupmenu.openAllImageFilesOfMessageInBrowser"));

//            cancelItem.setText(language.getString("Common.cancel"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            // if clickedKey contains no '/', it's only a key without file, so only allow copying this key to the clipboard.
            // if clickedKey matches the advanced freesite URL scanner, we can either copy to clipboard or browse (if a browser is configured).
            // else the clickedKey is a filelink, so simply allow to copy/download this link or ALL filelinks, or browsing to them (if browser).

            if( clickedKey.indexOf("/") < 0 ) {
                // key only
                add(copyKeyOnlyToClipboard);
            } else {
                // NOTE: this returns false for malformed Freesite keys (such as just a key with
                // no slashes at all).
                if( FreenetKeys.isFreesiteKey(clickedKey) ) {
                    // freesite link
                    add(copyFreesiteLinkToClipboard);
                } else {
                    // file key
                    add(copyFileLinkToClipboard);
                    if( currentMessageKeys.size() > 0 ) {
                        add(copyAllFileLinksOfMessageToClipboard);
                    }
                    if( allKeys.size() > 1 ) {
                        add(copyAllFileLinksToClipboard);
                    }
                    addSeparator();
                    add(downloadFile);
                    if( currentMessageKeys.size() > 0 ) {
                        add(downloadAllFilesOfMessage);
                    }
                    if( allKeys.size() > 1 ) {
                        add(downloadAllFiles);
                    }
                }

                // "open key in browser" should be visible for both freesites and file keys
                String browserAddress = Core.frostSettings.getValue(SettingsClass.BROWSER_ADDRESS);
                if( browserAddress != null && browserAddress.length() > 0 ) {
                    addSeparator();
                    add(openFileInBrowser);
                    if( currentMessageKeys.size() > 0 ) {
                        add(openAllImageFilesOfMessageInBrowser);
                    }
                    if( allKeys.size() > 1 ) {
                        add(openAllImageFilesInBrowser);
                    }
                }
            }

//            addSeparator();
//            add(cancelItem);

            super.show(invoker, x, y);
        }

        /**
         * Adds either the selected or all files from the message to downloads table.
         */
        private void downloadItems(final boolean getAll) {
            final List<String> items = getMessageKeyItems(getAll);
            if( items == null ) {
                return;
            }

            // we'll be building a list of download items based on the original key-names
            ArrayList<FrostDownloadItem> frostDownloadItemList = new ArrayList<FrostDownloadItem>();
            final KeyParser.ParseResult result = new KeyParser.ParseResult();

            for( final String key : items ) {
                if( key != null ) {
                    // attempt to parse and validate the current key and extract its real filename
                    // NOTE: we don't want Freesite keys so the 2nd argument is false
                    KeyParser.parseKeyFromLine(key, false, result);

                    // if this key was valid, then create a new download object for it
                    if( result.key != null ) {
                        frostDownloadItemList.add(new FrostDownloadItem(result.fileName, result.key));
                    }
                }
            }

            // start "add new downloads" dialog
            // NOTE: 1st arg = the list of keys to queue, true = ask before redownloading keys,
            // null, null = do not override prefix or download dir, false = no "paste more" button,
            // msg = associate found keys with originating message
            FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(frostDownloadItemList, true, null, null, false, selectedMessage);
        }
        
        
        private List<String> getMessageKeyItems(final boolean getAll) {
            List<String> items;
            if( getAll ) {
                items = allKeys;
            } else {
                items = Collections.singletonList(clickedKey);
            }
            if( items == null || items.size() == 0 ) {
                return null;
            }
            return items;
        }

        /**
         * This method copies the keys and file names of the items to the clipboard.
         * It decodes and validates every key, to get rid of "%20" encoding and other junk.
         */
        private void copyToClipboard(final List<String> items) {

            if( items == null || items.isEmpty() ) {
                return;
            }

            // build a string of all valid file or freesite keys from the input list
            final StringBuilder textToCopy = new StringBuilder();
            final KeyParser.ParseResult result = new KeyParser.ParseResult();
            for( final String key : items ) {
                // the input keys are raw, so first we must parse a clean, validated key
                // NOTE: we DO want Freesite keys, so the 2nd argument is true
                KeyParser.parseKeyFromLine(key, true, result);
                if( result.key == null ) { continue; } // skip invalid key

                // add the clean version of the validated key to the string builder
                textToCopy.append(result.key).append("\n");
            }

            // remove the additional \n at the end if the input array had valid items
            if( textToCopy.length() > 0 ) {
                textToCopy.deleteCharAt(textToCopy.length() - 1);
            }

            // now just put it in the clipboard
            CopyToClipboard.copyText(textToCopy.toString());
        }
        
    }

    private void openFileInBrowser_action(final List<String> rawItems, final boolean imagesOnly) {
		if( rawItems == null ) { return; }

		if( !DesktopUtils.canPerformBrowse() ) {
			return;
		}

		final String browserAddress = Core.frostSettings
				.getValue(SettingsClass.BROWSER_ADDRESS);
		if( browserAddress.length() == 0 ) {
			System.out.println("DEBUG - Browser address not configured");
			return;
		}

		// build a list of unique keys from the input (and possibly only image-keys)
		final List<String> items = new LinkedList<String>();
		for( String key : rawItems ) {
			if( key == null ) { continue; }

			// if "images only" mode, do a case-insensitive search for the allowed file endings
			boolean addThisKey = true;
			if( imagesOnly ) {
				if( ! key.matches("(?i)^.*?\\.(?:BMP|GIF|JPE?G|PNG)$") ) {
					addThisKey = false;
				}
			}

			// only add this key if it's unique (not seen yet)
			// NOTE: this will see "example%20file" and "example file" as two different keys,
			// because we haven't yet decoded the keys. but that's not a problem since nobody
			// ever posts differently encoded duplicates of the exact same key. and the only
			// drawback to a duplicate slipping through is an extra, useless web tab.
			if( addThisKey && !items.contains(key) ) {
				items.add(key);
			}
		}
		if( items == null || items.size() < 1 ) {
			// show msg if we're in "images only" mode and all of the input has been filtered away...
			// NOTE: if the user suppresses this message via the checkbox, they'll never see it
			// again unless they clear frost.ini. I preferred doing this so that users see it
			// at least once and know why nothing appears, but have the option to not be annoyed
			// by a constant popup telling them nothing was found in case they use the feature a lot.
			if( imagesOnly && rawItems.size() > 0 ) {
				MiscToolkit.showSuppressableConfirmDialog(
						MainFrame.getInstance(),
						language.getString("MessagePane.foundNoImageKeys.text"),
						language.getString("MessagePane.foundNoImageKeys.title"),
						MiscToolkit.SUPPRESSABLE_OK_BUTTON, // only show an "Ok" button
						JOptionPane.INFORMATION_MESSAGE,
						SettingsClass.WARN_NO_IMAGES_TO_OPEN, // will automatically choose "yes" if this is false
						language.getString("Common.suppressConfirmationCheckbox") );
			}

			return;
		}

		// now start a thread which will open all of the requested keys in the system's default browser
		// NOTE: the thread ensures that we can put some delay between spawning the processes, to avoid flooding
		new Thread("OpenInBrowserThread") {
			@Override
			public void run() {
				final KeyParser.ParseResult result = new KeyParser.ParseResult();
				int openedTabs = 0;
				int remainingItems = items.size();
				for( String key : items ) {
					try {
						--remainingItems;

						// the input keys are the raw text, so first we must parse a clean, validated key
						// NOTE: we DO want Freesite keys, so the 2nd argument is true
						KeyParser.parseKeyFromLine(key, true, result);
						if( result.key == null ) { continue; } // skip invalid key

						// now determine which key to use. if this is a Freesite, we must remove/strip everything
						// after the # symbol (anchor), since Freenet will think "index.html#home" is the key
						// if we encode "#" as part of the filename, which leads to "Not in archive" errors.
						// but if it's a file, we DO keep the # symbols (and encode them), i.e. "foo#1.jpg".
						if( FreenetKeys.isFreesiteKey(result.key) ) {
							// freesite; strip everything after the first # if one exists
							final int pos = result.key.indexOf('#');
							if( pos >= 0 ) {
								key = result.key.substring(0, pos); // end-offset is non-inclusive
							} else {
								key = result.key;
							}
						} else {
							// regular file; use whole filename
							key = result.key;
						}

						// now build the web URL
						final URI browserURI = new URI(browserAddress);
						final URI uri = new URI(
								browserURI.getScheme(), // scheme: http
								browserURI.getAuthority(), // host: 127.0.0.1:8888 (the authority is the host + optionally port and user:pass@, if specified)
								"/" + key, // path: "/CHK@..." (spaces and special chars become raw-urlencoded)
								null, // query (this would use form-urlencoding (spaces become +), which is why we don't use it)
								null); // fragment
						DesktopUtils.browse(uri);
						++openedTabs;

						if( remainingItems > 0 ) {
							// pause 500ms after every tab (that's 2 tabs per second), since each tab
							// spawns another process on the system and can overwhelm/deadlock certain
							// browsers like Firefox if we don't give it time to breathe.
							Mixed.wait(500);
						}
					} catch (URISyntaxException e) {
						e.printStackTrace();
					}
				}
			}
		}.start();
	}

    private class PopupMenuTofText extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

		private static final long serialVersionUID = 1L;

		private final JTextComponent sourceTextComponent;

        private final JMenuItem copyItem = new JMenuItem();
        private final JMenuItem saveMessageItem = new JMenuItem();
        private final JMenuItem downloadKeys = new JMenuItem();
        private final JMenuItem downloadAllFilesOfMessage = new JMenuItem();

        public PopupMenuTofText(final JTextComponent sourceTextComponent) {
            super();
            this.sourceTextComponent = sourceTextComponent;
            initialize();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == saveMessageItem) {
                saveMessageButton_actionPerformed();
                
            } else if (e.getSource() == copyItem) {
                // copy selected text
                final String text = sourceTextComponent.getSelectedText();
                CopyToClipboard.copyText(text);
                
            } else if(e.getSource() == downloadKeys ) {
            	addKeysInText(sourceTextComponent.getSelectedText());
            	
            } else if(e.getSource() == downloadAllFilesOfMessage ) {
            	addKeysOfCurrentMessage();
            }
        }

        private void initialize() {
            languageChanged(null);

            copyItem.addActionListener(this);
            saveMessageItem.addActionListener(this);
            downloadKeys.addActionListener(this);
            downloadAllFilesOfMessage.addActionListener(this);

            add(copyItem);
            addSeparator();
            add(saveMessageItem);
            addSeparator();
            add(downloadKeys);
            add(downloadAllFilesOfMessage);
        }

        public void languageChanged(final LanguageEvent event) {
            copyItem.setText(language.getString("MessagePane.messageText.popupmenu.copy"));
            saveMessageItem.setText(language.getString("MessagePane.messageText.popupmenu.saveMessageToDisk"));
            downloadKeys.setText(language.getString("MessagePane.messageText.popupmenu.downloadKeys"));
            downloadAllFilesOfMessage.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadAllFileKeysOfMessage"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            if ((selectedMessage != null) && (selectedMessage.getContent() != null)) {
                if (sourceTextComponent.getSelectedText() != null) {
                    copyItem.setEnabled(true);
                    downloadKeys.setEnabled(true);
                } else {
                    copyItem.setEnabled(false);
                    downloadKeys.setEnabled(false);
                }
                super.show(invoker, x, y);
            }
        }
    }

    protected void close() {
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_NAME, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_SIZE, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_STYLE, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_ANTIALIAS, propertyChangeListener);

        if (popupMenuAttachmentBoard != null) {
            language.removeLanguageListener(popupMenuAttachmentBoard);
        }
        if (popupMenuAttachmentTable != null) {
            language.removeLanguageListener(popupMenuAttachmentTable);
        }
        if (popupMenuTofText != null) {
            language.removeLanguageListener(popupMenuTofText);
        }
    }

    /**
     * Used by MessageWindow to attach a KeyListener for ESC.
     */
    @Override
    public void addKeyListener(final KeyListener l) {
        super.addKeyListener(l);
        messageTextArea.addKeyListener(l);
        filesTable.addKeyListener(l);
        boardsTable.addKeyListener(l);
    }
    /**
     * Used by MessageWindow to detach a KeyListener for ESC.
     */
    @Override
    public void removeKeyListener(final KeyListener l) {
        super.removeKeyListener(l);
        messageTextArea.removeKeyListener(l);
        filesTable.removeKeyListener(l);
        boardsTable.removeKeyListener(l);
    }
}
