/*
  FreetalkMessageTextPane.java / Frost
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
package frost.messaging.freetalk.gui;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

import frost.*;
import frost.fcp.FreenetKeys;
import frost.fileTransfer.*;
import frost.fileTransfer.KeyParser;
import frost.fileTransfer.download.*;
import frost.gui.*;
import frost.messaging.freetalk.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.search.*;
import frost.util.gui.textpane.*;
import frost.util.gui.translation.*;

public class FreetalkMessageTextPane extends JPanel {

    private final Language language = Language.getInstance();
    private final Logger logger = Logger.getLogger(FreetalkMessageTextPane.class.getName());

    private AntialiasedTextPane messageTextArea = null;
    private JSplitPane messageSplitPane = null;

    private FreetalkAttachedFilesTableModel attachedFilesModel;
    private JTable filesTable = null;
    private JScrollPane filesTableScrollPane;
    private JScrollPane messageBodyScrollPane;

    private PopupMenuAttachmentFile popupMenuAttachmentTable = null;
    private PopupMenuHyperLink popupMenuHyperLink = null;
    private PopupMenuTofText popupMenuTofText = null;

    private FreetalkMessage selectedMessage;

    private final MainFrame mainFrame = MainFrame.getInstance();

    private final Component parentFrame;

    private PropertyChangeListener propertyChangeListener;

    private SearchMessagesConfig searchMessagesConfig = null;
    private TextHighlighter textHighlighter = null;
    private static Color highlightColor = new Color(0x20, 0xFF, 0x20); // light green
    private static Color idLineHighlightColor = Color.LIGHT_GRAY;
    private final TextHighlighter idLineTextHighlighter = new TextHighlighter(idLineHighlightColor);

    public FreetalkMessageTextPane(final Component parentFrame) {
        this(parentFrame, null);
    }

    public FreetalkMessageTextPane(final Component parentFrame, final SearchMessagesConfig smc) {
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
    public void update_messageSelected(final FreetalkMessage msg) {

        selectedMessage = msg;

        if( textHighlighter != null ) {
            textHighlighter.removeHighlights(messageTextArea);
        }

        final List<FreetalkFileAttachment> fileAttachments = selectedMessage.getFileAttachments();
        attachedFilesModel.setData(fileAttachments);

        positionDividers(fileAttachments);

        setMessageText(selectedMessage.getContent());

        messageBodyScrollPane.getVerticalScrollBar().setValueIsAdjusting(true);
        messageBodyScrollPane.getVerticalScrollBar().setValue(0);

        // for search messages don't scroll down to begin of text
//        if( searchMessagesConfig == null ) {
//            int pos = selectedMessage.getIdLinePos();
//            final int len = selectedMessage.getIdLineLen();
//            if( pos > -1 && len > 10 ) {
//                // highlite id line if there are valid infos abpout the idline in message
//                idLineTextHighlighter.highlight(messageTextArea, pos, len, true);
//            } else {
//                // fallback
//                pos = selectedMessage.getContent().lastIndexOf("----- "+selectedMessage.getFromName()+" ----- ");
//            }
//
//            if( pos >= 0 ) {
//                // scroll to begin of reply
//                final int h = messageTextArea.getFontMetrics(messageTextArea.getFont()).getHeight();
//                final int s = textViewHeight; // messageBodyScrollPane.getViewport().getHeight();
//                final int v = s/h - 1; // how many lines are visible?
//
//                pos = calculateCaretPosition(messageTextArea, pos, v);
//
//                messageTextArea.getCaret().setDot(pos);
//            } else {
//                // scroll to end of message
//                pos = selectedMessage.getContent().length();
//                messageTextArea.getCaret().setDot(pos);
//            }
//        }

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
        decoder.setSmileyDecode(Core.frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_SMILEYS));
        decoder.setFreenetKeysDecode(Core.frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_KEYS_AS_HYPERLINKS));
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
        attachedFilesModel = new FreetalkAttachedFilesTableModel();
        filesTable = new JTable(attachedFilesModel);
        attachedFilesModel.configureTable(filesTable);
        filesTableScrollPane = new JScrollPane(filesTable);
        filesTableScrollPane.setWheelScrollingEnabled(true);

        fontChanged();

        //Put everything together
        messageSplitPane =
            new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                messageBodyScrollPane,
                null);
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
                    final List<String> allKeys = ((MessageDecoder)messageTextArea.getDecoder()).getHyperlinkedKeys();
                    final String clickedKey = e.getDescription();
                    // show menu to download this/all keys and copy this/all to clipboard
                    showHyperLinkPopupMenu(
                            e,
                            clickedKey,
                            allKeys,
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
                } else if (evt.getPropertyName().equals(SettingsClass.FREETALK_SHOW_SMILEYS)) {
                    ((MessageDecoder)messageTextArea.getDecoder()).setSmileyDecode(Core.frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_SMILEYS));
                    if( selectedMessage != null ) {
                        update_messageSelected(selectedMessage);
                    } else {
                        setMessageText(messageTextArea.getText());
                    }
                } else if (evt.getPropertyName().equals(SettingsClass.FREETALK_SHOW_KEYS_AS_HYPERLINKS)) {
                    ((MessageDecoder)messageTextArea.getDecoder()).setFreenetKeysDecode(Core.frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_KEYS_AS_HYPERLINKS));
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
        Core.frostSettings.addPropertyChangeListener(SettingsClass.FREETALK_SHOW_SMILEYS, propertyChangeListener);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.FREETALK_SHOW_KEYS_AS_HYPERLINKS, propertyChangeListener);
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

    private void positionDividers(final List<FreetalkFileAttachment> attachedFiles) {

        if (attachedFiles == null || attachedFiles.size() == 0) {
            messageSplitPane.setBottomComponent(null);
            messageSplitPane.setDividerSize(0);
            messageSplitPane.setDividerLocation(1.0);
        } else {
            messageSplitPane.setBottomComponent(filesTableScrollPane);
            messageSplitPane.setDividerSize(3);
            messageSplitPane.setDividerLocation(0.75);
        }
    }

    public void saveMessageButton_actionPerformed() {
        FileAccess.saveDialog(
            MainFrame.getInstance(),
            messageTextArea.getText(),
            Core.frostSettings.getValue(SettingsClass.DIR_LAST_USED),
            language.getString("MessagePane.messageText.saveDialog.title"));
    }

    private void showAttachedFilesPopupMenu(final MouseEvent e) {
        if (popupMenuAttachmentTable == null) {
            popupMenuAttachmentTable = new PopupMenuAttachmentFile();
            language.addLanguageListener(popupMenuAttachmentTable);
        }
        popupMenuAttachmentTable.show(e.getComponent(), e.getX(), e.getY());
    }

    private void showHyperLinkPopupMenu(final HyperlinkEvent e, final String clickedKey, final List<String> allKeys, final int x, final int y) {
        if (popupMenuHyperLink == null) {
            popupMenuHyperLink = new PopupMenuHyperLink();
            language.addLanguageListener(popupMenuHyperLink);
        }
        popupMenuHyperLink.setClickedKey(clickedKey);
        popupMenuHyperLink.setAllKeys(allKeys);

        popupMenuHyperLink.show(messageTextArea, x, y);
    }

    private void showTofTextAreaPopupMenu(final MouseEvent e) {
        if (popupMenuTofText == null) {
            popupMenuTofText = new PopupMenuTofText(messageTextArea);
            language.addLanguageListener(popupMenuTofText);
        }
        popupMenuTofText.show(e.getComponent(), e.getX(), e.getY());
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
        }

        public void languageChanged(final LanguageEvent event) {
            copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
            copyExtendedInfoItem.setText(language.getString("Common.copyToClipBoard.copyExtendedInfo"));
            copyToClipboardMenu.setText(language.getString("Common.copyToClipBoard") + "...");

            saveAttachmentsItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.downloadAttachments"));
            saveAttachmentItem.setText(language.getString("MessagePane.fileAttachmentTable.popupmenu.downloadSelectedAttachment"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            add(copyToClipboardMenu);
            addSeparator();
            add(saveAttachmentItem);
            add(saveAttachmentsItem);

            super.show(invoker, x, y);
        }

        /**
         * Adds either the selected or all files from the attachmentTable to downloads table.
         */
        private void downloadAttachments(final boolean allAttachments) {
            final Iterator<FreetalkFileAttachment> it = getAttachmentItems(allAttachments).iterator();

            // we'll be building a list of download items based on the original key-names,
            // so that we ignore the possibly-prefixed/renamed/wrong attachment filename + size.
            ArrayList<FrostDownloadItem> frostDownloadItemList = new ArrayList<FrostDownloadItem>();
            final KeyParser.ParseResult result = new KeyParser.ParseResult();

            while( it.hasNext() ) {
                final FreetalkFileAttachment fa = it.next();

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
            // null = we do not associate with freetalk messages
            FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(frostDownloadItemList, true, null, null, false, null);
        }

        /**
         * Returns a list of all items to process, either selected ones or all.
         */
        private List<FreetalkFileAttachment> getAttachmentItems(final boolean allAttachments) {
            if( allAttachments ) {
                // return all attachments
                return selectedMessage.getFileAttachments();
            } else {
                final List<FreetalkFileAttachment> attachments = selectedMessage.getFileAttachments();
                List<FreetalkFileAttachment> items = new LinkedList<FreetalkFileAttachment>();

                final int[] selectedRows = filesTable.getSelectedRows();
                if( selectedRows.length > 0 ) {
                    for( final int rowPos : selectedRows ) {
                        items.add(attachments.get(rowPos));
                    }
                }

                return items;
            }
        }
    }

    private class PopupMenuHyperLink
    extends JSkinnablePopupMenu
    implements ActionListener, LanguageListener {

        private final JMenuItem cancelItem = new JMenuItem();

        private final JMenuItem copyKeyOnlyToClipboard = new JMenuItem();

        private final JMenuItem copyFreesiteLinkToClipboard = new JMenuItem();

        private final JMenuItem copyFileLinkToClipboard = new JMenuItem();
        private final JMenuItem copyAllFileLinksToClipboard = new JMenuItem();

        private final JMenuItem downloadFile = new JMenuItem();
        private final JMenuItem downloadAllFiles = new JMenuItem();

        private String clickedKey = null;
        private List<String> allKeys = null;

        public PopupMenuHyperLink() throws HeadlessException {
            super();
            initialize();
        }

        public void setClickedKey(final String s) {
            clickedKey = s;
        }
        public void setAllKeys(final List<String> l) {
            allKeys = l;
        }

        public void actionPerformed(final ActionEvent e) {
            if( e.getSource() == copyKeyOnlyToClipboard ) {
                copyToClipboard(false);
            } else if( e.getSource() == copyFreesiteLinkToClipboard ) {
                copyToClipboard(false);
            } else if( e.getSource() == copyFileLinkToClipboard ) {
                copyToClipboard(false);
            } else if( e.getSource() == copyAllFileLinksToClipboard ) {
                copyToClipboard(true);
            } else if( e.getSource() == downloadFile ) {
                downloadItems(false);
            } else if( e.getSource() == downloadAllFiles ) {
                downloadItems(true);
            }
        }

        private void initialize() {
            languageChanged(null);

            copyKeyOnlyToClipboard.addActionListener(this);
            copyFreesiteLinkToClipboard.addActionListener(this);
            copyFileLinkToClipboard.addActionListener(this);
            copyAllFileLinksToClipboard.addActionListener(this);
            downloadFile.addActionListener(this);
            downloadAllFiles.addActionListener(this);
        }

        public void languageChanged(final LanguageEvent event) {
            copyKeyOnlyToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyKeyToClipboard"));
            copyFreesiteLinkToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyFreesiteLinkToClipboard"));
            copyFileLinkToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyFileKeyToClipboard"));
            copyAllFileLinksToClipboard.setText(language.getString("MessagePane.hyperlink.popupmenu.copyAllFileKeysToClipboard"));
            downloadFile.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadFileKey"));
            downloadAllFiles.setText(language.getString("MessagePane.hyperlink.popupmenu.downloadAllFileKeys"));

            cancelItem.setText(language.getString("Common.cancel"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            // if clickedKey contains no '/', it's only a key without file, so only allow copying this key to the clipboard.
            // if clickedKey matches the advanced freesite URL scanner, we can copy to clipboard.
            // else the clickedKey is a filelink, so simply allow to copy/download this link or ALL filelinks
            // TODO: the regular Frost message pane allows to open keys directly in the browser, perhaps port it here... but uh nobody uses Freetalk...

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
                    if( allKeys.size() > 1 ) {
                        add(copyAllFileLinksToClipboard);
                    }
                    addSeparator();
                    add(downloadFile);
                    if( allKeys.size() > 1 ) {
                        add(downloadAllFiles);
                    }
                }
            }

            addSeparator();
            add(cancelItem);

            super.show(invoker, x, y);
        }

        /**
         * Adds either the selected or all files from the attachmentTable to downloads table.
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
            // null = we do not associate with freetalk messages
            FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(frostDownloadItemList, true, null, null, false, null);
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
         * This method copies the CHK keys and file names of the selected or all items to the clipboard.
         * It decodes and validates every key, to get rid of "%20" encoding and other junk.
         */
        private void copyToClipboard(final boolean getAll) {

            final List<String> items = getMessageKeyItems(getAll);

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

    private class PopupMenuTofText
    extends JSkinnablePopupMenu
    implements ActionListener, LanguageListener {

        private final JTextComponent sourceTextComponent;

        private final JMenuItem copyItem = new JMenuItem();
        private final JMenuItem cancelItem = new JMenuItem();
        private final JMenuItem saveMessageItem = new JMenuItem();

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
            }
        }

        private void initialize() {
            languageChanged(null);

            copyItem.addActionListener(this);
            saveMessageItem.addActionListener(this);

            add(copyItem);
            addSeparator();
            add(saveMessageItem);
            addSeparator();
            add(cancelItem);
        }

        public void languageChanged(final LanguageEvent event) {
            copyItem.setText(language.getString("MessagePane.messageText.popupmenu.copy"));
            saveMessageItem.setText(language.getString("MessagePane.messageText.popupmenu.saveMessageToDisk"));
            cancelItem.setText(language.getString("Common.cancel"));
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            if ((selectedMessage != null) && (selectedMessage.getContent() != null)) {
                if (sourceTextComponent.getSelectedText() != null) {
                    copyItem.setEnabled(true);
                } else {
                    copyItem.setEnabled(false);
                }
                super.show(invoker, x, y);
            }
        }
    }

    private DownloadModel getDownloadModel() {
        return FileTransferManager.inst().getDownloadManager().getModel();
    }

    public void close() {
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_NAME, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_SIZE, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_FONT_STYLE, propertyChangeListener);
        Core.frostSettings.removePropertyChangeListener(SettingsClass.MESSAGE_BODY_ANTIALIAS, propertyChangeListener);

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
    }
    /**
     * Used by MessageWindow to detach a KeyListener for ESC.
     */
    @Override
    public void removeKeyListener(final KeyListener l) {
        super.removeKeyListener(l);
        messageTextArea.removeKeyListener(l);
        filesTable.removeKeyListener(l);
    }
}
