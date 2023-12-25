/*
MessageFrame.java / Frost
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
package frost.messaging.frost.gui;

import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.ComboBoxEditor;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.swing.text.PlainDocument;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.ext.AltEdit;
import frost.ext.AltEditCallbackInterface;
import frost.gui.BoardsChooser;
import frost.gui.ScrollableBar;
import frost.gui.SmileyChooserDialog;
import frost.gui.SortedTable;
import frost.gui.model.SortedTableModel;
import frost.gui.model.TableMember;
import frost.identities.Identity;
import frost.identities.LocalIdentity;
import frost.messaging.frost.BoardAttachment;
import frost.messaging.frost.FileAttachment;
import frost.messaging.frost.FrostMessageObject;
import frost.messaging.frost.FrostUnsentMessageObject;
import frost.messaging.frost.UnsentMessagesManager;
import frost.messaging.frost.boards.Board;
import frost.messaging.frost.boards.TofTree;
import frost.messaging.frost.boards.BoardUpdateThread;
import frost.storage.perst.messages.MessageStorage;
import frost.util.DateFun;
import frost.util.FileAccess;
import frost.util.Mixed;
import frost.util.gui.CompoundUndoManager;
import frost.util.gui.ImmutableArea;
import frost.util.gui.ImmutableAreasDocument;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.SmartFileFilters;
import frost.util.gui.TextComponentClipboardMenu;
import frost.util.gui.search.FindAction;
import frost.util.gui.search.TextComponentFindAction;
import frost.util.gui.textpane.AntialiasedTextArea;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;

public class MessageFrame extends JFrame implements AltEditCallbackInterface {

    private static final Logger logger = Logger.getLogger(MessageFrame.class.getName());

    private final Language language;

    private final Listener listener = new Listener();

    private boolean initialized = false;

    private final Window parentWindow;

    private Board board;
    private String repliedMsgId;
    private final SettingsClass frostSettings;

    private MFAttachedBoardsTable boardsTable;
    private MFAttachedFilesTable filesTable;
    private MFAttachedBoardsTableModel boardsTableModel;
    private MFAttachedFilesTableModel filesTableModel;

    private JSplitPane messageSplitPane = null;
    private JSplitPane attachmentsSplitPane = null;
    private JScrollPane filesTableScrollPane;
    private JScrollPane boardsTableScrollPane;

    private JSkinnablePopupMenu attFilesPopupMenu;
    private JSkinnablePopupMenu attBoardsPopupMenu;
    private MessageBodyPopupMenu messageBodyPopupMenu;

    private final JButton Bsend = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-send.png"));
    private final JButton Bcancel = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-discard.png"));
    private final JButton BattachFile = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-attachment.png"));
    private final JButton BattachBoard = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/internet-group-chat.png"));

    private final JCheckBox sign = new JCheckBox();
    private final JCheckBox encrypt = new JCheckBox();
    private JComboBox<Identity> buddies;

    private final JLabel Lboard = new JLabel();
    private final JLabel Lfrom = new JLabel();
    private final JLabel Lsubject = new JLabel();
    private final JTextField TFboard = new JTextField(); // Board (To)
    private final JTextField subjectTextField = new JTextField(); // Subject
    private final JButton BchooseSmiley = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/face-smile.png"));

    private final AntialiasedTextArea messageTextArea = new AntialiasedTextArea(); // Text
    private ImmutableArea headerArea = null;
//    private TextHighlighter textHighlighter = null;
    private String oldSender = null;
    private String currentSignature = null;

    private CompoundUndoManager undoManager = null;

    private FrostMessageObject repliedMessage = null;

    private IDJComboBox<Object> ownIdentitiesComboBox = null;
    private RoundJLabel identityIndicator = null;

    private static int openInstanceCount = 0;
    

    public MessageFrame(final SettingsClass newSettings, final Window tparentWindow) {
        super();
        parentWindow = tparentWindow;
        this.language = Language.getInstance();
        frostSettings = newSettings;

        incOpenInstanceCount();

        final String fontName = frostSettings.getValue(SettingsClass.MESSAGE_BODY_FONT_NAME);
        final int fontStyle = frostSettings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_STYLE);
        final int fontSize = frostSettings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_SIZE);
        Font tofFont = new Font(fontName, fontStyle, fontSize);
        if (!tofFont.getFamily().equals(fontName)) {
            logger.severe("The selected font was not found in your system\n"
                    + "That selection will be changed to \"Monospaced\".");
            frostSettings.setValue(SettingsClass.MESSAGE_BODY_FONT_NAME, "Monospaced");
            tofFont = new Font("Monospaced", fontStyle, fontSize);
        }
        updateTextColor();
        Core.frostSettings.addPropertyChangeListener(SettingsClass.COLORS_MESSAGE_PRIVEDITOR, new PropertyChangeListener() {
            @Override
            public void propertyChange(final PropertyChangeEvent evt) {
                if( evt.getPropertyName().equals(SettingsClass.COLORS_MESSAGE_PRIVEDITOR) ) {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            updateTextColor();
                        }
                    });
                }
            }
        });
        messageTextArea.setFont(tofFont);
        messageTextArea.setAntiAliasEnabled(frostSettings.getBoolValue(SettingsClass.MESSAGE_BODY_ANTIALIAS));
        final ImmutableAreasDocument messageDocument = new ImmutableAreasDocument();
        headerArea = new ImmutableArea(messageDocument);
        messageDocument.addImmutableArea(headerArea); // user must not change the header of the message
        messageTextArea.setDocument(messageDocument);
        final FindAction findAction = new TextComponentFindAction();
        findAction.install(messageTextArea);
//        textHighlighter = new TextHighlighter(Color.LIGHT_GRAY);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                windowIsClosing();
            }
            @Override
            public void windowClosed(final WindowEvent e) {
                windowWasClosed();
            }
        });
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    /**
     * Colors the private message text editor according to the user's choice.
     */
    private void updateTextColor()
    {
        if( encrypt.isSelected() ) {
            messageTextArea.setForeground(Core.frostSettings.getColorValue(SettingsClass.COLORS_MESSAGE_PRIVEDITOR));
        } else {
            messageTextArea.setForeground(Color.BLACK);
        }
    }

    /**
     * Returns true if the user has written some non-whitespace content in the message or edited
     * their signature area. Otherwise false.
     */
    private boolean hasUserEdit() {
        boolean hasUserEdit = false;
        try {
            // first get all text after the header area, including all empty newlines
            final Document doc = messageTextArea.getDocument();
            final int offset = headerArea.getEndPos();
            final int length = doc.getLength() - offset;
            String userText = doc.getText(offset, length);

            // now remove the user's signature if they have one and it *hasn't* been manually edited
            if( currentSignature != null ) {
                final int sigLength = currentSignature.length();
                final int sigStartOffset = userText.length() - sigLength;
                final int sigEndOffset = sigStartOffset + sigLength;
                if( sigStartOffset >= 0 && sigEndOffset <= userText.length() ) {
                    final String checkSignature = userText.substring(sigStartOffset, sigEndOffset);
                    if( checkSignature.equals(currentSignature) ) {
                        // found an unedited signature at the end of the document; so just remove it
                        userText = userText.substring(0, sigStartOffset); // grabs to endIndex - 1
                    }
                }
            }

            // lastly, trim the remaining user-written text to see if there's any non-whitespace content
            userText = userText.trim();
            if( ! userText.isEmpty() ) {
                hasUserEdit = true;
            }
        } catch( final Exception e ) {}
        return hasUserEdit;
    }

    private void windowIsClosing() {
        boolean discardMessage = false;
        // determine if the user has edited the document, and don't prompt them to confirm
        // closing an unedited message, since there's no work to be lost by doing that
        if( ! hasUserEdit() ) {
            // they haven't edited the document, so just discard it without confirmation
            discardMessage = true;
        } else {
            // ask them to confirm throwing away their edited, unsent message
            final int answer = MiscToolkit.showConfirmDialog(
                    this,
                    language.getString("MessageFrame.discardMessage.text"),
                    language.getString("MessageFrame.discardMessage.title"),
                    MiscToolkit.YES_NO_OPTION,
                    MiscToolkit.WARNING_MESSAGE);
            if( answer == MiscToolkit.YES_OPTION ) {
                discardMessage = true;
            }
        }
        if( discardMessage ) {
            dispose();
        }
    }

    private void windowWasClosed() {
        decOpenInstanceCount();
    }

    private void attachBoards_actionPerformed(final ActionEvent e) {

        // get and sort all boards
        final List<Board> allBoards = MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards();
        if (allBoards.size() == 0) {
            return;
        }
        Collections.sort(allBoards);

        final BoardsChooser chooser = new BoardsChooser(this, allBoards);
        chooser.setLocationRelativeTo(this);
        final List<Board> chosenBoards = chooser.runDialog();
        if (chosenBoards == null || chosenBoards.size() == 0) { // nothing chosed or cancelled
            return;
        }

        for (int i = 0; i < chosenBoards.size(); i++) {
            final Board chosedBoard = chosenBoards.get(i);

            String privKey = chosedBoard.getPrivateKey();

            if (privKey != null) {
                final int answer =
                    MiscToolkit.showConfirmDialog(this,
                        language.formatMessage("MessageFrame.attachBoard.sendPrivateKeyConfirmationDialog.body", chosedBoard.getName()),
                        language.getString("MessageFrame.attachBoard.sendPrivateKeyConfirmationDialog.title"),
                        MiscToolkit.YES_NO_OPTION);
                if( answer != MiscToolkit.YES_OPTION ) {
                    privKey = null; // don't provide privkey
                }
            }
            // build a new board because maybe privKey shouldn't be uploaded
            final Board aNewBoard =
                new Board(chosedBoard.getName(), chosedBoard.getPublicKey(), privKey, chosedBoard.getDescription());
            final MFAttachedBoard ab = new MFAttachedBoard(aNewBoard);
            boardsTableModel.addRow(ab);
        }
        positionDividers();
    }

    private void attachFile_actionPerformed(final ActionEvent e) {
        final JFileChooser fc = new JFileChooser(frostSettings.getValue(SettingsClass.DIR_LAST_USED));
        fc.setDialogTitle(language.getString("MessageFrame.fileChooser.title"));
        fc.setFileHidingEnabled(true); // hide hidden files
        fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES); // accept both files and directories
        fc.setMultiSelectionEnabled(true); // accept multiple files
        SmartFileFilters.installDefaultFilters(fc, language); // install all common file filters
        // let the L&F decide the dialog width (so that all labels fit), but we'll still set a minimum
        // height and width so that the user never gets a cramped dialog
        final Dimension fcSize = fc.getPreferredSize();
        if( fcSize.width < 650 ) { fcSize.width = 650; }
        if( fcSize.height < 450 ) { fcSize.height = 450; }
        fc.setPreferredSize(fcSize);

        final int returnVal = fc.showOpenDialog(MessageFrame.this);
        if( returnVal == JFileChooser.APPROVE_OPTION ) {
            final File[] selectedFiles = fc.getSelectedFiles();
            for( final File element : selectedFiles ) {
                // for convinience remember last used directory
                frostSettings.setValue(SettingsClass.DIR_LAST_USED, element.getPath());

                // collect all choosed files + files in all choosed directories
                final List<File> allFiles = FileAccess.getAllEntries(element);
                for (int j = 0; j < allFiles.size(); j++) {
                    final File aFile = allFiles.get(j);
                    final MFAttachedFile af = new MFAttachedFile( aFile );
                    filesTableModel.addRow( af );
                }
            }
        }
        positionDividers();
    }

    /**
     * Finally called to start composing a message. Uses alternate editor if configured.
     */
    private void composeMessage(
            final Board newBoard,
            final String newSubject,
            final String inReplyTo,
            String newText,
            final boolean isReply,
            final Identity recipient,
            final LocalIdentity senderId,   // if given compose encrypted reply
            final FrostMessageObject msg) {

        repliedMessage = msg;

        if (isReply) {
            newText += "\n\n";
        }

        if (frostSettings.getBoolValue(SettingsClass.ALTERNATE_EDITOR_ENABLED)) {
            // build our transfer object that the parser will provide us in its callback
            final TransferObject to = new TransferObject();
            to.newBoard = newBoard;
            to.newSubject = newSubject;
            to.inReplyTo = inReplyTo;
            to.newText = newText;
            to.isReply = isReply;
            to.recipient = recipient;
            to.senderId = senderId;
            // create a temporary editText that is show in alternate editor
            // the editor will return only new text to us
            final DateTime now = new DateTime(DateTimeZone.UTC);
            final String date = DateFun.FORMAT_DATE_EXT.print(now)
            + " - "
            + DateFun.FORMAT_TIME_EXT.print(now);
            final String fromLine = "----- (sender) ----- " + date + " -----";
            final String editText = newText + fromLine + "\n\n";

            final AltEdit ae = new AltEdit(newSubject, editText, MainFrame.getInstance(), to, this);
            ae.start();
        } else {
            // invoke frame directly, no alternate editor
            composeMessageContinued(newBoard, newSubject, inReplyTo, newText, null, isReply, recipient, senderId);
        }
    }

    public void altEditCallback(final Object toObj, String newAltSubject, final String newAltText) {
        final TransferObject to = (TransferObject)toObj;
        if( newAltSubject == null ) {
            newAltSubject = to.newSubject; // use original subject
        }
        composeMessageContinued(
                to.newBoard,
                newAltSubject,
                to.inReplyTo,
                to.newText,
                newAltText,
                to.isReply,
                to.recipient,
                to.senderId);
    }

    /**
     * This method is either invoked by ComposeMessage OR by the callback of the AltEdit class.
     */
    private void composeMessageContinued(
        final Board newBoard,
        final String newSubject,
        final String inReplyTo,
        String newText,
        final String altEditText,
        final boolean isReply,
        final Identity recipient,       // if given compose encrypted reply
        final LocalIdentity senderId)   // if given compose encrypted reply
    {
        headerArea.setEnabled(false);
        board = newBoard;
        repliedMsgId = inReplyTo; // maybe null

        String from;
        boolean isInitializedSigned;
        if( senderId != null ) {
            // encrypted reply!
            from = senderId.getUniqueName();
            isInitializedSigned = true;
        } else {
            // use remembered sender name, maybe per board
            String userName = Core.frostSettings.getValue("userName."+board.getBoardFilename());
            if( userName == null || userName.length() == 0 ) {
                userName = Core.frostSettings.getValue(SettingsClass.LAST_USED_FROMNAME);
            }
            if( Core.getIdentities().isMySelf(userName) ) {
                // isSigned
                from = userName;
                isInitializedSigned = true;
            } else if( userName.indexOf("@") > 0 ) {
                // invalid, only LocalIdentities are allowed to contain an @
                from = "Anonymous";
                isInitializedSigned = false;
            } else {
                from = userName;
                isInitializedSigned = false;
            }
        }
        oldSender = from;

        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        try {
            initialize(newBoard, newSubject);
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Exception thrown in composeMessage(...)", e);
        }

        sign.setEnabled(false);

        final ImageIcon signedIcon = MiscToolkit.loadImageIcon("/data/toolbar/message-signed.png");
        final ImageIcon unsignedIcon = MiscToolkit.loadImageIcon("/data/toolbar/message-unsigned.png");
        sign.setDisabledSelectedIcon(signedIcon);
        sign.setDisabledIcon(unsignedIcon);
        sign.setSelectedIcon(signedIcon);
        sign.setIcon(unsignedIcon);

        sign.addItemListener(new ItemListener() {
            public void itemStateChanged(final ItemEvent e) {
                updateSignToolTip();
            }
        });

        // maybe prepare to reply to an encrypted message
        if( recipient != null ) {
            // set correct sender identity
            for(int x=0; x < getOwnIdentitiesComboBox().getItemCount(); x++) {
                final Object obj = getOwnIdentitiesComboBox().getItemAt(x);
                if( obj instanceof LocalIdentity ) {
                    final LocalIdentity li = (LocalIdentity)obj;
                    if( senderId.getUniqueName().equals(li.getUniqueName()) ) {
                        getOwnIdentitiesComboBox().setSelectedIndex(x);
                        break;
                    }
                }
            }
            getOwnIdentitiesComboBox().setEnabled(false);
            // set and lock controls (after we set the identity, the itemlistener would reset the controls!)
            sign.setSelected(true);
            encrypt.setSelected(true);
            updateTextColor();
            buddies.removeAllItems();
            buddies.addItem(recipient);
            buddies.setSelectedItem(recipient);
            // dont allow to disable signing/encryption
            encrypt.setEnabled(false);
            buddies.setEnabled(false);
        } else {
            if( isInitializedSigned ) {
                // set saved sender identity
                for(int x=0; x < getOwnIdentitiesComboBox().getItemCount(); x++) {
                    final Object obj = getOwnIdentitiesComboBox().getItemAt(x);
                    if( obj instanceof LocalIdentity ) {
                        final LocalIdentity li = (LocalIdentity)obj;
                        if( from.equals(li.getUniqueName()) ) {
                            getOwnIdentitiesComboBox().setSelectedIndex(x);
                            sign.setSelected(true);
                            getOwnIdentitiesComboBox().setEditable(false);
                            break;
                        }
                    }
                }
            } else {
                // initialized unsigned/anonymous
                getOwnIdentitiesComboBox().setSelectedIndex(0);
                getOwnIdentitiesComboBox().getEditor().setItem(from);
                sign.setSelected(false);
                getOwnIdentitiesComboBox().setEditable(true);
            }

            if( sign.isSelected() && buddies.getItemCount() > 0 ) {
                encrypt.setEnabled(true);
            } else {
                encrypt.setEnabled(false);
            }
            encrypt.setSelected(false);
            updateTextColor();
            buddies.setEnabled(false);
        }

        updateSignToolTip();

        // prepare message text
        final DateTime now = new DateTime(DateTimeZone.UTC);
        final String date = DateFun.FORMAT_DATE_EXT.print(now)
                        + " - "
                        + DateFun.FORMAT_TIME_EXT.print(now);
        final String fromLine = "----- " + from + " ----- " + date + " -----";

        final int headerAreaStart = newText.length();// begin of non-modifiable area
        newText += fromLine + "\n\n";
        final int headerAreaEnd = newText.length() - 2; // end of non-modifiable area

        if( altEditText != null ) {
            newText += altEditText; // maybe append text entered in alternate editor
        }

        // later set cursor to this position in text
        final int caretPos = newText.length();

        // set sig if msg is marked as signed
        currentSignature = null;
        if( sign.isSelected()  ) {
            // maybe append a signature
            final LocalIdentity li = (LocalIdentity)getOwnIdentitiesComboBox().getSelectedItem();
            if( li.getSignature() != null ) {
                currentSignature = "\n--\n" + li.getSignature();
                newText += currentSignature;
            }
        }

        messageTextArea.setText(newText);
        headerArea.setStartPos(headerAreaStart);
        headerArea.setEndPos(headerAreaEnd);
        headerArea.setEnabled(true);

//        textHighlighter.highlight(messageTextArea, headerAreaStart, headerAreaEnd-headerAreaStart, true);

        setVisible(true);

        // reset the splitpanes
        positionDividers();

        // Properly positions the caret (AKA cursor)
        messageTextArea.requestFocusInWindow();
        messageTextArea.getCaret().setDot(caretPos);
        messageTextArea.getCaret().setVisible(true);

        // attach the undo/redo handler (we have to do this last so that none of our internal editing above gets recorded)
        undoManager = new CompoundUndoManager(messageTextArea);
        messageTextArea.getActionMap().put("Undo", undoManager.getUndoAction());
        messageTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK, true), "Undo"); // ctrl + z
        messageTextArea.getActionMap().put("Redo", undoManager.getRedoAction());
        messageTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "Redo"); // ctrl + shift + z
        messageTextArea.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK, true), "Redo"); // ctrl + y
    }

    private void resetUndoManager() {
        if( undoManager != null ) {
            undoManager.discardAllEdits();
        }
    }

    public void composeNewMessage(final Board newBoard, final String newSubject, final String newText) {
        composeMessage(newBoard, newSubject, null, newText, false, null, null, null);
    }

    public void composeReply(
            final Board newBoard,
            final String newSubject,
            final String inReplyTo,
            final String newText,
            final FrostMessageObject msg) {
        composeMessage(newBoard, newSubject, inReplyTo, newText, true, null, null, msg);
    }

    public void composeEncryptedReply(
            final Board newBoard,
            final String newSubject,
            final String inReplyTo,
            final String newText,
            final Identity recipient,
            final LocalIdentity senderId,
            final FrostMessageObject msg) {
        composeMessage(newBoard, newSubject, inReplyTo, newText, true, recipient, senderId, msg);
    }

    @Override
    public void dispose() {
        if (initialized) {
            language.removeLanguageListener(listener);
            initialized = false;
        }
        super.dispose();
    }

    private MessageBodyPopupMenu getMessageBodyPopupMenu() {
        if (messageBodyPopupMenu == null) {
            messageBodyPopupMenu = new MessageBodyPopupMenu(messageTextArea, undoManager);
        }
        return messageBodyPopupMenu;
    }

    private void initialize(final Board targetBoard, final String subject) throws Exception {
        if (!initialized) {
            refreshLanguage();
            language.addLanguageListener(listener);

            final ImageIcon frameIcon = MiscToolkit.loadImageIcon("/data/toolbar/mail-message-new.png");
            setIconImage(frameIcon.getImage());
            setResizable(true);

            boardsTableModel = new MFAttachedBoardsTableModel();
            boardsTable = new MFAttachedBoardsTable(boardsTableModel);
            boardsTableScrollPane = new JScrollPane(boardsTable);
            boardsTableScrollPane.setWheelScrollingEnabled(true);
            boardsTable.addMouseListener(listener);

            filesTableModel = new MFAttachedFilesTableModel();
            filesTable = new MFAttachedFilesTable(filesTableModel);
            filesTableScrollPane = new JScrollPane(filesTable);
            filesTableScrollPane.setWheelScrollingEnabled(true);
            filesTable.addMouseListener(listener);

// FIXME: option to show own identities in list, or to hide them
            final List<Identity> budList = Core.getIdentities().getAllFRIENDIdentities();
            Identity id = null;
            if( repliedMessage != null ) {
                id = repliedMessage.getFromIdentity();
            }
            if( budList.size() > 0 || id != null ) {
                Collections.sort( budList, new BuddyComparator() );
                if( id != null ) {
                    if( id.isFRIEND() == true ) {
                        budList.remove(id); // remove before put to top of list
                    }
                    // add id to top of list in case the user enables 'encrypt'
                    budList.add(0, id);
                }
                buddies = new JComboBox<Identity>(new Vector<Identity>(budList));
                buddies.setSelectedItem(budList.get(0));
            } else {
                buddies = new JComboBox<Identity>();
            }
            buddies.setMaximumSize(new Dimension(300, 25)); // dirty fix for overlength combobox on linux

            MiscToolkit.configureButton(Bsend, "MessageFrame.toolbar.tooltip.sendMessage", language);
            MiscToolkit.configureButton(Bcancel, "Common.cancel", language);
            MiscToolkit.configureButton(BattachFile, "MessageFrame.toolbar.tooltip.addFileAttachments", language);
            MiscToolkit.configureButton(BattachBoard, "MessageFrame.toolbar.tooltip.addBoardAttachments", language);
            MiscToolkit.configureButton(BchooseSmiley, "MessageFrame.toolbar.tooltip.chooseSmiley", language);
            BchooseSmiley.setFocusable(false);

            TFboard.setEditable(false);
            TFboard.setText(targetBoard.getName());

            new TextComponentClipboardMenu(TFboard, language);
            new TextComponentClipboardMenu((TextComboBoxEditor)getOwnIdentitiesComboBox().getEditor(), language);
            new TextComponentClipboardMenu(subjectTextField, language);
            subjectTextField.setText(subject);
            messageTextArea.setLineWrap(true);
            messageTextArea.setWrapStyleWord(true);
            messageTextArea.addMouseListener(listener);

            sign.setOpaque(false);
            encrypt.setOpaque(false);

            //------------------------------------------------------------------------
            // Actionlistener
            //------------------------------------------------------------------------
            Bsend.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    send_actionPerformed(e);
                }
            });
            Bcancel.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    // create an event which acts exactly as if the user pressed the X, to close the window and dispose() it
                    final WindowEvent closingEvent = new WindowEvent(MessageFrame.this, WindowEvent.WINDOW_CLOSING);
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
                }
            });
            BattachFile.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    attachFile_actionPerformed(e);
                }
            });
            BattachBoard.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    attachBoards_actionPerformed(e);
                }
            });
            encrypt.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    encrypt_actionPerformed(e);
                }
            });
            BchooseSmiley.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    chooseSmiley_actionPerformed(e);
                }
            });

            //------------------------------------------------------------------------
            // Append objects
            //------------------------------------------------------------------------
            final JPanel panelMain = new JPanel(new BorderLayout()); // Main Panel

            final JPanel panelHeader = new JPanel(new BorderLayout()); // header (toolbar and textfields)
            final JPanel panelTextfields = new JPanel(new GridBagLayout());

            final JToolBar panelToolbar = new JToolBar(); // toolbar
            panelToolbar.setRollover(true);
            panelToolbar.setFloatable(false);

            final JScrollPane bodyScrollPane = new JScrollPane(messageTextArea); // Textscrollpane
            bodyScrollPane.setWheelScrollingEnabled(true);
            bodyScrollPane.setMinimumSize(new Dimension(100, 50));

            // FIXME: add a smiley chooser right beside the subject textfield!

            // text fields
            final GridBagConstraints constraints = new GridBagConstraints();
            final Insets insets = new Insets(0, 3, 0, 3);
            final Insets insets0 = new Insets(0, 0, 0, 0);
            constraints.fill = GridBagConstraints.NONE;
            constraints.anchor = GridBagConstraints.WEST;
            constraints.weighty = 0.0;
            constraints.weightx = 0.0;

            constraints.insets = insets;

            constraints.gridx = 0;
            constraints.gridy = 0;

            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.insets = insets;
            constraints.weightx = 0.0;
            panelTextfields.add(Lboard, constraints);

            constraints.gridx = 1;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridwidth = 2;
            constraints.insets = insets0;
            constraints.weightx = 1.0;
            panelTextfields.add(TFboard, constraints);

            constraints.gridx = 0;
            constraints.gridy++;

            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.insets = insets;
            constraints.weightx = 0.0;
            panelTextfields.add(Lfrom, constraints);

            constraints.gridx = 1;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridwidth = 1;
            constraints.insets = insets0;
            constraints.weightx = 1.0;
            panelTextfields.add(getOwnIdentitiesComboBox(), constraints);

            constraints.gridx = 2;

            identityIndicator = new RoundJLabel();
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.insets = insets;
            constraints.weightx = 0.0;
            panelTextfields.add(identityIndicator, constraints);
            getOwnIdentitiesComboBox().setIdentityIndicator(identityIndicator);

            constraints.gridx = 0;
            constraints.gridy++;

            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.insets = insets;
            constraints.weightx = 0.0;
            panelTextfields.add(Lsubject, constraints);

            constraints.gridx = 1;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridwidth = 1;
            constraints.insets = insets0;
            constraints.weightx = 1.0;
            panelTextfields.add(subjectTextField, constraints);

            constraints.gridx = 2;

            constraints.fill = GridBagConstraints.NONE;
            constraints.gridwidth = 1;
            constraints.insets = insets;
            constraints.weightx = 0.0;
            panelTextfields.add(BchooseSmiley, constraints);

            // toolbar
            panelToolbar.add(Bsend);
            panelToolbar.add(Bcancel);
            panelToolbar.addSeparator();
            panelToolbar.add(BattachFile);
            panelToolbar.add(BattachBoard);
            panelToolbar.addSeparator();
            panelToolbar.add(sign);
            panelToolbar.addSeparator();
            panelToolbar.add(encrypt);
            panelToolbar.add(buddies);
//            panelButtons.add(addAttachedFilesToUploadTable);

            final ScrollableBar panelButtonsScrollable = new ScrollableBar(panelToolbar);

            panelHeader.add(panelButtonsScrollable, BorderLayout.PAGE_START);
//            panelToolbar.add(panelButtons, BorderLayout.PAGE_START);
            panelHeader.add(panelTextfields, BorderLayout.CENTER);

            //Put everything together
            attachmentsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filesTableScrollPane,
                    boardsTableScrollPane);
            attachmentsSplitPane.setResizeWeight(0.5);
            attachmentsSplitPane.setDividerSize(3);
            attachmentsSplitPane.setDividerLocation(0.5);

            messageSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, bodyScrollPane,
                    attachmentsSplitPane);
            messageSplitPane.setDividerSize(0);
            messageSplitPane.setDividerLocation(1.0);
            messageSplitPane.setResizeWeight(1.0);

            panelMain.add(panelHeader, BorderLayout.NORTH);
            panelMain.add(messageSplitPane, BorderLayout.CENTER);

            getContentPane().setLayout(new BorderLayout());
            getContentPane().add(panelMain, BorderLayout.CENTER);

            initPopupMenu();

            // keyboard shortcuts for compose window (reacts regardless of which component in the window is focused)
            panelMain.getActionMap().put("CloseAction", new javax.swing.AbstractAction() {
                public void actionPerformed(final ActionEvent event) {
                    // create an event which acts exactly as if the user pressed the X, to close the window and dispose() it
                    // NOTE: if they've written something, they will be asked whether to discard unsaved work
                    final WindowEvent closingEvent = new WindowEvent(MessageFrame.this, WindowEvent.WINDOW_CLOSING);
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
                }
            });
            panelMain.getInputMap(JPanel.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_W, Toolkit.getDefaultToolkit().getMenuShortcutKeyMask(), true), "CloseAction"); // ctrl + w on Windows/Linux, cmd + w on Mac

            pack();

            // window is now packed to needed size. Check if packed width is smaller than
            // 75% of the parent frame and use the larger size.
            // pack is needed to ensure that all dialog elements are shown (was problem on linux).
            int width = getWidth();
            if( width < (int)(parentWindow.getWidth() * 0.75) ) {
                width = (int)(parentWindow.getWidth() * 0.75);
            }

            setSize( width, (int)(parentWindow.getHeight() * 0.75) ); // always set height to 75% of parent
            setLocationRelativeTo(parentWindow);

            initialized = true;
        }
    }

    protected void initPopupMenu() {
        attFilesPopupMenu = new JSkinnablePopupMenu();
        attBoardsPopupMenu = new JSkinnablePopupMenu();

        final JMenuItem removeFiles = new JMenuItem(language.getString("MessageFrame.attachmentTables.popupmenu.remove"));
        final JMenuItem removeBoards = new JMenuItem(language.getString("MessageFrame.attachmentTables.popupmenu.remove"));

        removeFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                removeSelectedItemsFromTable(filesTable);
            }
        });
        removeBoards.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                removeSelectedItemsFromTable(boardsTable);
            }
        });

        attFilesPopupMenu.add( removeFiles );
        attBoardsPopupMenu.add( removeBoards );
    }

    private void positionDividers() {
        final int attachedFiles = filesTableModel.getRowCount();
        final int attachedBoards = boardsTableModel.getRowCount();
        if (attachedFiles == 0 && attachedBoards == 0) {
            // Neither files nor boards
            messageSplitPane.setBottomComponent(null);
            messageSplitPane.setDividerSize(0);
            return;
        }
        messageSplitPane.setDividerSize(3);
        messageSplitPane.setDividerLocation(0.75);
        if (attachedFiles != 0 && attachedBoards == 0) {
            //Only files
            messageSplitPane.setBottomComponent(filesTableScrollPane);
            return;
        }
        if (attachedFiles == 0 && attachedBoards != 0) {
            //Only boards
            messageSplitPane.setBottomComponent(boardsTableScrollPane);
            return;
        }
        if (attachedFiles != 0 && attachedBoards != 0) {
            //Both files and boards
            messageSplitPane.setBottomComponent(attachmentsSplitPane);
            attachmentsSplitPane.setTopComponent(filesTableScrollPane);
            attachmentsSplitPane.setBottomComponent(boardsTableScrollPane);
        }
    }

    private void refreshLanguage() {
        setTitle(language.getString("MessageFrame.createMessage.title"));

        Bsend.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.sendMessage"));
        Bcancel.setToolTipText(language.getString("Common.cancel"));
        BattachFile.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.addFileAttachments"));
        BattachBoard.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.addBoardAttachments"));

        encrypt.setText(language.getString("MessageFrame.toolbar.encryptFor"));

        Lboard.setText(language.getString("MessageFrame.board") + ": ");
        Lfrom.setText(language.getString("MessageFrame.from") + ": ");
        Lsubject.setText(language.getString("MessageFrame.subject") + ": ");

        updateSignToolTip();
    }

    private void updateSignToolTip() {
        final boolean isSelected = sign.isSelected();
        if( isSelected ) {
            sign.setToolTipText(language.getString("MessagePane.toolbar.tooltip.isSigned"));
        } else {
            sign.setToolTipText(language.getString("MessagePane.toolbar.tooltip.isUnsigned"));
        }
    }

    protected void removeSelectedItemsFromTable( final JTable tbl ) {
        final SortedTableModel<? extends TableMember<?>> m = (SortedTableModel<? extends TableMember<?>>)tbl.getModel();
        final int[] sel = tbl.getSelectedRows();
        for(int x=sel.length-1; x>=0; x--)
        {
            m.removeRow(sel[x]);
        }
        positionDividers();
    }

    private void chooseSmiley_actionPerformed(final ActionEvent e) {
        final SmileyChooserDialog dlg = new SmileyChooserDialog(this);
        final int x = this.getX() + BchooseSmiley.getX();
        final int y = this.getY() + BchooseSmiley.getY();
        String chosedSmileyText = dlg.startDialog(x, y);
        if( chosedSmileyText != null && chosedSmileyText.length() > 0 ) {
            chosedSmileyText += " ";
            // paste into document
            try {
                final Caret caret = messageTextArea.getCaret();
                final int p0 = Math.min(caret.getDot(), caret.getMark());
                final int p1 = Math.max(caret.getDot(), caret.getMark());

                final Document document = messageTextArea.getDocument();
                
                // FIXME: maybe check for a blank before insert of smiley text???
                if (document instanceof PlainDocument) {
                    ((PlainDocument) document).replace(p0, p1 - p0, chosedSmileyText, null);
                } else {
                    if (p0 != p1) {
                        document.remove(p0, p1 - p0);
                    }
                    document.insertString(p0, chosedSmileyText, null);
                }
            } catch (final Throwable ble) {
                logger.log(Level.SEVERE, "Problem while pasting text.", ble);
            }
        }
        // finally set focus back to message window
        messageTextArea.requestFocusInWindow();
    }

    private void send_actionPerformed(final ActionEvent e) {

        LocalIdentity senderId = null;
        String from;
        if( getOwnIdentitiesComboBox().getSelectedItem() instanceof LocalIdentity ) {
            senderId = (LocalIdentity)getOwnIdentitiesComboBox().getSelectedItem();
            from = senderId.getUniqueName();
        } else {
            from = getOwnIdentitiesComboBox().getEditor().getItem().toString();
        }

        final String subject = subjectTextField.getText().trim();
        subjectTextField.setText(subject); // if a pbl occurs show the subject we checked
        final String text = messageTextArea.getText().trim();

        if( subject.equalsIgnoreCase("No subject") || subject.equalsIgnoreCase("Re: No subject") ) {
            // we do not allow "No subject" posts; warn the user and return them to the compose window
            MiscToolkit.showMessageDialog( this,
                    language.getString("MessageFrame.defaultSubjectWarning.text"),
                    language.getString("MessageFrame.defaultSubjectWarning.title"),
                    MiscToolkit.WARNING_MESSAGE);
            subjectTextField.requestFocusInWindow();
            return;
        }

        if( subject.length() == 0) {
            MiscToolkit.showMessageDialog( this,
                                language.getString("MessageFrame.noSubjectError.text"),
                                language.getString("MessageFrame.noSubjectError.title"),
                                MiscToolkit.ERROR_MESSAGE);
            subjectTextField.requestFocusInWindow();
            return;
        }
        if( from.length() == 0) {
            MiscToolkit.showMessageDialog( this,
                                language.getString("MessageFrame.noSenderError.text"),
                                language.getString("MessageFrame.noSenderError.title"),
                                MiscToolkit.ERROR_MESSAGE);
            getOwnIdentitiesComboBox().requestFocusInWindow();
            return;
        }
        final int maxTextLength = (60*1024);
        final int msgSize = text.length() + subject.length() + from.length() + ((repliedMsgId!=null)?repliedMsgId.length():0);
        if( msgSize > maxTextLength ) {
            MiscToolkit.showMessageDialog( this,
                    language.formatMessage("MessageFrame.textTooLargeError.text",
                            Integer.toString(text.length()),
                            Integer.toString(maxTextLength)),
                    language.getString("MessageFrame.textTooLargeError.title"),
                    MiscToolkit.ERROR_MESSAGE);
            messageTextArea.requestFocusInWindow();
            return;
        }
        if( ! hasUserEdit() ) { // must have at least 1 non-whitespace edit in their msg area
            MiscToolkit.showMessageDialog( this,
                    language.getString("MessageFrame.noContentError.text"),
                    language.getString("MessageFrame.noContentError.title"),
                    MiscToolkit.ERROR_MESSAGE);
            messageTextArea.requestFocusInWindow();
            return;
        }

        // verify that the user really wants to send an unsigned message
        if( senderId == null ) {
            // if the user unchecks "show this message again", they'll never be asked again
            // until they clear frost.ini.
            final int answer = MiscToolkit.showSuppressableConfirmDialog(
                    this,
                    language.getString("MessageFrame.confirmUnsignedMessage.text"),
                    language.getString("MessageFrame.confirmUnsignedMessage.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    SettingsClass.CONFIRM_SEND_UNSIGNED_MSG, // will automatically choose "yes" if this is false
                    language.getString("Common.suppressConfirmationCheckbox") );
            if( answer != JOptionPane.YES_OPTION ) {
                getOwnIdentitiesComboBox().requestFocusInWindow();
                try {
                    // attempt to select the 1st non-unsigned identity; this will always work unless
                    // they've managed to delete their last remaining signing identity via an old version
                    // of legacy Frost (which used to have a bug allowing final identity deletion).
                    if( getOwnIdentitiesComboBox().getItemCount() > 1 ) {
                        getOwnIdentitiesComboBox().setSelectedIndex(1);
                    }
                } catch( final Exception ex ) {}
                return;
            }
        }

        // for convinience set last used user
        if( from.indexOf("@") < 0 ) {
            // only save anonymous usernames
            frostSettings.setValue(SettingsClass.LAST_USED_FROMNAME, from);
        }
        frostSettings.setValue("userName."+board.getBoardFilename(), from);

        final int idLinePos = headerArea.getStartPos();
        final int idLineLen = headerArea.getEndPos() - headerArea.getStartPos();
        final FrostUnsentMessageObject newMessage = new FrostUnsentMessageObject();
        newMessage.setMessageId(Mixed.createUniqueId()); // new message, create a new unique msg id
        newMessage.setInReplyTo(repliedMsgId);
        newMessage.setBoard(board);
        newMessage.setFromName(from);
        newMessage.setSubject(subject);
        newMessage.setContent(text);
        newMessage.setIdLinePos(idLinePos);
        newMessage.setIdLineLen(idLineLen);

        // MessageUploadThread will set date + time !

        // attach all files and boards the user chosed
        if( filesTableModel.getRowCount() > 0 ) {
            for(int x=0; x < filesTableModel.getRowCount(); x++) {
                final MFAttachedFile af = (MFAttachedFile)filesTableModel.getRow(x);
                final File aChosedFile = af.getFile();
                final FileAttachment fa = new FileAttachment(aChosedFile);
                newMessage.addAttachment(fa);
            }
            newMessage.setHasFileAttachments(true);
        }
        if( boardsTableModel.getRowCount() > 0 ) {
            for(int x=0; x < boardsTableModel.getRowCount(); x++) {
                final MFAttachedBoard ab = (MFAttachedBoard)boardsTableModel.getRow(x);
                final Board aChosedBoard = ab.getBoardObject();
                final BoardAttachment ba = new BoardAttachment(aChosedBoard);
                newMessage.addAttachment(ba);
            }
            newMessage.setHasBoardAttachments(true);
        }

        Identity recipient = null;
        if( encrypt.isSelected() ) {
            recipient = (Identity)buddies.getSelectedItem();
            if( recipient == null ) {
                MiscToolkit.showMessageDialog( this,
                        language.getString("MessageFrame.encryptErrorNoRecipient.body"),
                        language.getString("MessageFrame.encryptErrorNoRecipient.title"),
                        MiscToolkit.ERROR_MESSAGE);
                return;
            }
            newMessage.setRecipientName(recipient.getUniqueName());
        }

        UnsentMessagesManager.addNewUnsentMessage(newMessage);

//        // zip the xml file and check for maximum size
//        File tmpFile = FileAccess.createTempFile("msgframe_", "_tmp");
//        tmpFile.deleteOnExit();
//        if( mo.saveToFile(tmpFile) == true ) {
//            File zipFile = new File(tmpFile.getPath() + ".zipped");
//            zipFile.delete(); // just in case it already exists
//            zipFile.deleteOnExit(); // so that it is deleted when Frost exits
//            FileAccess.writeZipFile(FileAccess.readByteArray(tmpFile), "entry", zipFile);
//            long zipLen = zipFile.length();
//            tmpFile.delete();
//            zipFile.delete();
//            if( zipLen > 30000 ) { // 30000 because data+metadata must be smaller than 32k
//                MiscToolkit.showMessageDialog( this,
//                        "The zipped message is too large ("+zipLen+" bytes, "+30000+" allowed)! Remove some text.",
//                        "Message text too large!",
//                        MiscToolkit.ERROR_MESSAGE);
//                return;
//            }
//        } else {
//            MiscToolkit.showMessageDialog( this,
//                    "Error verifying the resulting message size.",
//                    "Error",
//                    MiscToolkit.ERROR_MESSAGE);
//            return;
//        }

        // TODO: if user deletes the unsent msg then the replied state keeps (see below)
        //  We would have to set the replied state after the msg was successfully sent, because
        //  we can't remove the state later, maybe the msg was replied twice and we would remove
        //  the replied state from first reply...

        // set isReplied to replied message
        if( repliedMessage != null ) {
            if( repliedMessage.isReplied() == false ) {
                repliedMessage.setReplied(true);
                final FrostMessageObject saveMsg = repliedMessage;
                final Thread saver = new Thread() {
                    @Override
                    public void run() {
                        // save the changed isreplied state into the database
                        MessageStorage.inst().updateMessage(saveMsg);
                    }
                };
                saver.start();
            }
        }

        // "Quicksend mode" rapid message uploading:
        // - used if Freenet is online, message uploading isn't disabled (via the [Outbox] checkbox),
        //   *and* the user has enabled the quicksend option. the board must also allow updates.
        //   "allowing updates" means: it is a board, and it isn't currently marked as DoS'd.
        // - in that case, we'll instantly start a message download thread to refresh today's
        //   messages. outgoing messages are uploaded when the today-refresh is done.
        // NOTE: we don't start a new "today"-thread if one is already running. so if the user spams
        //   a board with lots of messages in a row but waits too long between a few of them, then it's
        //   possible that the current today-thread has already finished uploading the earliest batch
        //   of messages and therefore won't upload their newly queued message.
        //   solution? easy: such users should force a manual board refresh or learn to not spam.
        if( Core.isFreenetOnline()
                && !frostSettings.getBoolValue(SettingsClass.MESSAGE_UPLOAD_DISABLED)
                && frostSettings.getBoolValue(SettingsClass.MESSAGE_UPLOAD_QUICKSEND)
                && board.isManualUpdateAllowed()
        ) {
            final TofTree tree = MainFrame.getInstance().getFrostMessageTab().getTofTree();

            if( tree.getRunningBoardUpdateThreads().isThreadOfTypeRunning(board, BoardUpdateThread.MSG_DNLOAD_TODAY) == false ) {
                tree.getRunningBoardUpdateThreads().startMessageDownloadToday(
                        board,
                        frostSettings,
                        tree.getListener());
                logger.info("Starting update (MSG_TODAY) of " + board.getName());

                final long now = System.currentTimeMillis();
                board.setLastUpdateStartMillis(now);
                board.incTimesUpdatedCount();
            }
        }

        setVisible(false);
        dispose();
    }

    private void senderChanged(final LocalIdentity selectedId) {
        try {
            boolean isSigned = ( selectedId != null );
            sign.setSelected(isSigned);

            if( isSigned ) {
                if( buddies.getItemCount() > 0 ) {
                    encrypt.setEnabled(true);
                    if( encrypt.isSelected() ) {
                        buddies.setEnabled(true);
                    } else {
                        buddies.setEnabled(false);
                    }
                }

                removeSignatureFromText(currentSignature); // remove signature if existing
                currentSignature = addSignatureToText(selectedId.getSignature()); // add new signature if not existing
            } else {
                encrypt.setSelected(false);
                updateTextColor();
                encrypt.setEnabled(false);
                buddies.setEnabled(false);
                removeSignatureFromText(currentSignature); // remove signature if existing
                currentSignature = null;
            }
        } finally {
            // throw away entire undo-history to prevent user from undoing the changes to the
            // signature from switching identity. it's unfortunately the only way to solve this
            // since we cannot update the otherwise-invalid offsets of previous edits.
            resetUndoManager();
        }
    }

    private String addSignatureToText(final String sig) {
        if( sig == null ) {
            return null;
        }
        final String newSig = "\n--\n" + sig;
        if (!messageTextArea.getText().endsWith(newSig)) {
            try {
                messageTextArea.getDocument().insertString(messageTextArea.getText().length(), newSig, null);
            } catch (final BadLocationException e1) {
                logger.log(Level.SEVERE, "Error while updating the signature ", e1);
            }
        }
        return newSig;
    }

    private void removeSignatureFromText(final String sig) {
        if( sig == null ) {
            return;
        }
        if (messageTextArea.getText().endsWith(sig)) {
            try {
                messageTextArea.getDocument().remove(messageTextArea.getText().length()-sig.length(), sig.length());
            } catch (final BadLocationException e1) {
                logger.log(Level.SEVERE, "Error while updating the signature ", e1);
            }
        }
    }

    private void encrypt_actionPerformed(final ActionEvent e) {
        if( encrypt.isSelected() ) {
            buddies.setEnabled(true);
        } else {
            buddies.setEnabled(false);
        }
        updateTextColor();
    }

    protected void updateHeaderArea(final String sender) {
        if( !headerArea.isEnabled() ) {
            return; // ignore updates
        }
        if( sender == null || oldSender == null || oldSender.equals(sender) ) {
            return;
        }
        try {
            // TODO: add grey background! highlighter mit headerArea um pos zu finden
            headerArea.setEnabled(false);
            messageTextArea.getDocument().remove(headerArea.getStartPos() + 6, oldSender.length());
            messageTextArea.getDocument().insertString(headerArea.getStartPos() + 6, sender, null);
            oldSender = sender;
            headerArea.setEnabled(true);
//            textHighlighter.highlight(messageTextArea, headerArea.getStartPos(), headerArea.getEndPos()-headerArea.getStartPos(), true);
        } catch (final BadLocationException exception) {
            logger.log(Level.SEVERE, "Error while updating the message header", exception);
        } finally {
            // throw away entire undo-history to prevent user from undoing the changes to the
            // header-area from switching identity. it's unfortunately the only way to solve this
            // since we cannot update the otherwise-invalid offsets of previous edits.
            resetUndoManager();
        }
//        String s= messageTextArea.getText().substring(headerArea.getStartPos(), headerArea.getEndPos());
//        System.out.println("DBG: "+headerArea.getStartPos()+" ; "+headerArea.getEndPos()+": '"+s+"'");

// DBG: 0 ; 77: '----- blubb2@xpDZ5ZfXK9wYiHB_hkVGRCwJl54 ----- 2006.10.13 - 18:20:12GMT -----'
// DBG: 39 ; 119: '----- wegdami t@plewLcBTHKmPwpWakJNpUdvWSR8 ----- 2006.10.13 - 18:20:12GMT -----'
    }

    /**
     * Used for editing the IDJComboBox anonymous nickname.
     */
    class TextComboBoxEditor extends JTextField implements ComboBoxEditor {
        boolean isSigned;
        public TextComboBoxEditor() {
            super();
        }
        private void setupLook() {
            // the editor is only used for anonymous nicknames, so apply the unsigned color and border
            setBackground(IDJComboBox.UNSIGNEDCOLOR);
            setBorder(new javax.swing.border.EmptyBorder(2,5,2,5));
        }
        @Override
        public void updateUI() {
            super.updateUI();
            setupLook();
        }
        public Component getEditorComponent() {
            setupLook();
            return this;
        }
        public void setItem(final Object arg0) {
            if( arg0 instanceof LocalIdentity ) {
                isSigned = true;
            } else {
                isSigned = false;
            }
            setText(arg0.toString());
        }
        public Object getItem() {
            return getText();
        }
        public boolean isSigned() {
            return isSigned;
        }
    }

    /**
     * Used as an indicator for the selected identity's color, in case the L&F doesn't
     * render the background color we're setting on the IDJComboBox.
     */
    private class RoundJLabel
            extends JLabel
    {
        public RoundJLabel()
        {
            super();

            setPreferredSize(new Dimension(30,16));
            setOpaque(false); // don't render anything outside the rounded border
        }

        @Override
        protected void paintComponent(
                final Graphics g)
        {
            super.paintComponent(g);
            final Dimension arcs = new Dimension(14,14);
            final int width = getWidth();
            final int height = getHeight();
            final Graphics2D g2d = (Graphics2D)g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // paint the background and border
            g2d.setColor(getBackground());
            g2d.fillRoundRect(0, 0, width-1, height-1, arcs.width, arcs.height);
            g2d.setColor(getForeground());
            g2d.drawRoundRect(0, 0, width-1, height-1, arcs.width, arcs.height);
        }
    }

    /**
     * Custom JComboBox which renders each identity using a different background color.
     */
    private static class IDJComboBox<E>
            extends JComboBox<E>
    {
        // hover background (when the mouse is over the item in the dropdown)
        private static final Color HOVERCOLOR = new Color(75,163,249); // sky blue

        // anonymous (unsigned) color
        public static final Color UNSIGNEDCOLOR = new Color(188,188,188); // medium gray

        // light background colors for IDs (repeats if the user has more than 8 IDs)
        private static final Color[] IDCOLORS = {
            new Color(211, 250, 203), // green
            new Color(216, 248, 251), // blue
            new Color(254, 202, 247), // pink
            new Color(254, 254, 190), // yellow
            new Color(255, 159, 159), // red
            new Color(250, 225, 216), // beige
            new Color(241, 241, 241), // light gray
            new Color(252, 237, 254), // light pink
        };

        // keeps track of which colors to apply to which items
        private final Map<Object,Color> fColorMap;
        private int fColorCounter = 0;
        private RoundJLabel fIdColorIndicator = null;

        public IDJComboBox()
        {
            super();

            // the key is an object and therefore checks for object instance (reference) equality
            fColorMap = new HashMap<Object,Color>();

            // add our custom renderers
            applyRenderers();

            // update the indicator every time the user selects a different item
            addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    if( e.getStateChange() == ItemEvent.DESELECTED ) {
                        return;
                    }
                    updateIdentityIndicator();
                }
            });
        }

        public void setIdentityIndicator(
                final RoundJLabel aIdColorIndicator)
        {
            fIdColorIndicator = aIdColorIndicator;
            updateIdentityIndicator();
        }

        private void updateIdentityIndicator()
        {
            if( fIdColorIndicator != null ) {
                Color bgColor = null;
                Object value = getSelectedItem();
                if( !(value instanceof LocalIdentity) ) {
                    // an unsigned (anonymous) identity should always use the unsigned color
                    bgColor = UNSIGNEDCOLOR;
                } else {
                    // we've found an ID, so look up its color index in the color map
                    if( fColorMap != null && fColorMap.containsKey(value) ) {
                        bgColor = fColorMap.get(value);
                    } else { // fallback to white bg if not found (should never happen)
                        bgColor = Color.WHITE;
                    }
                }
                if( bgColor != null ) {
                    fIdColorIndicator.setBackground(bgColor);
                }
            }
        }

        private void applyRenderers()
        {
            // create a dummy JComboBox to analyze the preferred size, since there's a weird bug
            // in Java where calling getPreferredSize() on ourselves when the L&F changes leads
            // to a number that keeps growing each time you switch L&F. my guess is that some L&Fs
            // are trying to add some extra padding to whatever your own preferred size was, so
            // we need a clean, blank dummy element to get the basic preferred size from.
            final JComboBox dummyBox = new JComboBox();
            final Dimension prefSize = dummyBox.getPreferredSize();
            setRenderer(new IDCellColorRenderer(fColorMap, prefSize.height));
        }

        @Override
        public void updateUI()
        {
            super.updateUI();
            // make sure our custom renderers are kept even when the user switches L&F
            applyRenderers();
        }

        @Override
        public void addItem(E item)
        {
            // if this is a signed (non-anonymous) identity, we'll calculate a color for it
            if( item instanceof LocalIdentity ) {
                if( !fColorMap.containsKey(item) ) {
                    // user IDs; calculate rolling colors (if fColorCounter == 8 (same as IDCOLORS.length), then the idx is 0)
                    final int colorIdx = fColorCounter % IDCOLORS.length;
                    fColorMap.put(item, IDCOLORS[colorIdx]);
                    ++fColorCounter;
                }
            }
            super.addItem(item);
        }

        /**
        * JComboBox renderer with colored backgrounds based on the ID in that field.
        */
        private static class IDCellColorRenderer
                extends DefaultListCellRenderer
        {
            private final Map<Object,Color> fColorMap;
            private final Dimension fPrefSize;

            public IDCellColorRenderer(
                    final Map<Object,Color> aColorMap, // passed by reference
                    final int aPrefHeight) // preferred height of a JComboBox
            {
                super();

                // save the reference to the color map used by the JComboBox
                fColorMap = aColorMap;

                // save the preferred height of the list entries. otherwise the list renderer
                // uses a default size that's smaller than the L&Fs normal JComboBox.
                // NOTE: we don't have to set the width, since it scales automatically.
                fPrefSize = new Dimension(0, aPrefHeight);

                // tell Java that we'll paint everything inside our rectangle (no transparency)
                // so that we're sure to paint the entire background.
                setOpaque(true);
            }

            @Override
            public void setBackground(
                    final Color bg)
            {
                // do nothing; we don't want to allow outsiders to set the background color
            }

            private void setIDBackground(
                    final Color bg)
            {
                // used by us to set the background of the list entry
                super.setBackground(bg);
            }

            @Override
            public Component getListCellRendererComponent(
                    final JList list,
                    Object value,
                    int index,
                    boolean isSelected,
                    boolean cellHasFocus)
            {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                // our job here is to return a component that's ready for paint()'ing; so now just add the color to it
                Color bgColor = null;
                Color fgColor = null;
                if( index >= 0 && isSelected ) {
                    // the index is 0 or greater which means that it's for the dropdown list,
                    // and the item is being hovered, so make it white on blue
                    bgColor = HOVERCOLOR;
                    fgColor = Color.WHITE;
                } else {
                    // the index is either -1 (means the item's look when selected and the dropdown
                    // is closed, OR that the dropdown is shown and the item isn't being hovered)
                    // NOTE: if the index is -1, only *some* L&Fs honor the background color we set
                    // here, which is why we ALSO use a colored circle to indicate which ID is used.
                    if( !(value instanceof LocalIdentity) ) {
                        // an unsigned (anonymous) identity should always use the unsigned color
                        bgColor = UNSIGNEDCOLOR;
//                        fgColor = Color.BLACK; // do not override L&Fs foreground color
                    } else {
                        // we've found an ID, so look up its color index in the color map
                        if( fColorMap != null && fColorMap.containsKey(value) ) {
                            bgColor = fColorMap.get(value);
                        } else { // fallback to white bg if not found (should never happen)
                            bgColor = Color.WHITE;
                        }
//                        fgColor = Color.BLACK; // do not override L&Fs foreground color
                    }
                }

                // now just apply the colors
                if( bgColor != null ) {
                    setIDBackground(bgColor);
                }
                if( fgColor != null ) {
                    setForeground(fgColor);
                }

                // apply the border insets (padding) and preferred height
                setBorder(new javax.swing.border.EmptyBorder(2,5,2,5));
                setPreferredSize(fPrefSize);

                return this;
            }
        }
    }

    private IDJComboBox<Object> getOwnIdentitiesComboBox() {
        if( ownIdentitiesComboBox == null ) {
            ownIdentitiesComboBox = new IDJComboBox<Object>();
            ownIdentitiesComboBox.addItem("Anonymous");
            // sort own unique names
            final TreeMap<String,LocalIdentity> sortedIds = new TreeMap<String,LocalIdentity>();
            for( final Object element : Core.getIdentities().getLocalIdentities() ) {
                final LocalIdentity li = (LocalIdentity)element;
                sortedIds.put(li.getUniqueName(), li);
            }
            for( final Object element : sortedIds.values() ) {
                ownIdentitiesComboBox.addItem(element);
            }

            final TextComboBoxEditor editor = new TextComboBoxEditor();

            editor.getDocument().addDocumentListener(new DocumentListener() {
                public void changedUpdate(final DocumentEvent e) {
                    updateHeaderArea2();
                }
                public void insertUpdate(final DocumentEvent e) {
                    updateHeaderArea2();
                }
                public void removeUpdate(final DocumentEvent e) {
                    updateHeaderArea2();
                }
                private void updateHeaderArea2() {
                    final String sender = getOwnIdentitiesComboBox().getEditor().getItem().toString();
                    updateHeaderArea(sender);
                }
            });

            final AbstractDocument doc = (AbstractDocument) editor.getDocument();
            doc.setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(final DocumentFilter.FilterBypass fb, final int offset, String string,
                        final AttributeSet attr) throws BadLocationException
                {
                    if (((TextComboBoxEditor)getOwnIdentitiesComboBox().getEditor()).isSigned() == false ) {
                        string = string.replaceAll("@","");
                    }
                    super.insertString(fb, offset, string, attr);
                }
                @Override
                public void replace(final DocumentFilter.FilterBypass fb, final int offset, final int length, String string,
                        final AttributeSet attrs) throws BadLocationException
                {
                    if (((TextComboBoxEditor)getOwnIdentitiesComboBox().getEditor()).isSigned() == false ) {
                        string = string.replaceAll("@","");
                    }
                    super.replace(fb, offset, length, string, attrs);
                }
            });

            ownIdentitiesComboBox.setEditor(editor);

            ownIdentitiesComboBox.setEditable(true);

//            ownIdentitiesComboBox.getEditor().selectAll();
            ownIdentitiesComboBox.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    if( e.getStateChange() == ItemEvent.DESELECTED ) {
                        return;
                    }
                    LocalIdentity selectedId = null;
                    if( ownIdentitiesComboBox.getSelectedIndex() == 0 ) {
                        ownIdentitiesComboBox.setEditable(true); // original anonymous
//                        ownIdentitiesComboBox.getEditor().selectAll();
                    } else if( ownIdentitiesComboBox.getSelectedIndex() < 0 ) {
                        ownIdentitiesComboBox.setEditable(true); // own value, anonymous
//                        ownIdentitiesComboBox.getEditor().selectAll();
                    } else {
                        ownIdentitiesComboBox.setEditable(false);
                        selectedId = (LocalIdentity) ownIdentitiesComboBox.getSelectedItem();
                    }
                    final String sender = getOwnIdentitiesComboBox().getSelectedItem().toString();
                    updateHeaderArea(sender);
                    senderChanged(selectedId);
                }
            });
        }
        return ownIdentitiesComboBox;
    }

    class BuddyComparator implements Comparator<Identity> {
        public int compare(final Identity id1, final Identity id2) {
            final String s1 = id1.getUniqueName();
            final String s2 = id2.getUniqueName();
            return s1.toLowerCase().compareTo( s2.toLowerCase() );
        }
    }

    private class Listener implements MouseListener, LanguageListener {
        protected void maybeShowPopup(final MouseEvent e) {
            if (e.isPopupTrigger()) {
                if (e.getSource() == boardsTable) {
                    attBoardsPopupMenu.show(boardsTable, e.getX(), e.getY());
                }
                if (e.getSource() == filesTable) {
                    attFilesPopupMenu.show(filesTable, e.getX(), e.getY());
                }
                if (e.getSource() == messageTextArea) {
                    getMessageBodyPopupMenu().show(messageTextArea, e.getX(), e.getY());
                }
            }
        }
        public void mouseClicked(final MouseEvent event) {}
        public void mouseEntered(final MouseEvent event) {}
        public void mouseExited(final MouseEvent event) {}
        public void mousePressed(final MouseEvent event) {
            maybeShowPopup(event);
        }
        public void mouseReleased(final MouseEvent event) {
            maybeShowPopup(event);
        }
        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }
    }

    private class MessageBodyPopupMenu
        extends JSkinnablePopupMenu
        implements ActionListener, ClipboardOwner {

        private Clipboard clipboard;

        private final JTextComponent sourceTextComponent;
        private final CompoundUndoManager sourceUndoManager;

        private JMenuItem undoItem;
        private JMenuItem redoItem;
        private final JMenuItem cutItem = new JMenuItem();
        private final JMenuItem copyItem = new JMenuItem();
        private final JMenuItem pasteItem = new JMenuItem();

        public MessageBodyPopupMenu(final JTextComponent sourceTextComponent, final CompoundUndoManager sourceUndoManager) {
            super();
            this.sourceTextComponent = sourceTextComponent;
            this.sourceUndoManager = sourceUndoManager;
            if( sourceUndoManager != null ) {
                this.undoItem = new JMenuItem(sourceUndoManager.getUndoAction());
                this.redoItem = new JMenuItem(sourceUndoManager.getRedoAction());
            }
            initialize();
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == cutItem) {
                cutSelectedText();
            }
            if (e.getSource() == copyItem) {
                copySelectedText();
            }
            if (e.getSource() == pasteItem) {
                pasteText();
            }
        }

        private void copySelectedText() {
            final StringSelection selection = new StringSelection(sourceTextComponent.getSelectedText());
            clipboard.setContents(selection, this);
        }

        private void cutSelectedText() {
            final StringSelection selection = new StringSelection(sourceTextComponent.getSelectedText());
            clipboard.setContents(selection, this);

            final int start = sourceTextComponent.getSelectionStart();
            final int end = sourceTextComponent.getSelectionEnd();
            try {
                sourceTextComponent.getDocument().remove(start, end - start);
            } catch (final BadLocationException ble) {
                logger.log(Level.SEVERE, "Problem while cutting text.", ble);
            }
        }

        private void pasteText() {
            final Transferable clipboardContent = clipboard.getContents(this);
            try {
                final String text = (String) clipboardContent.getTransferData(DataFlavor.stringFlavor);

                final Caret caret = sourceTextComponent.getCaret();
                final int p0 = Math.min(caret.getDot(), caret.getMark());
                final int p1 = Math.max(caret.getDot(), caret.getMark());

                final Document document = sourceTextComponent.getDocument();

                if (document instanceof PlainDocument) {
                    ((PlainDocument) document).replace(p0, p1 - p0, text, null);
                } else {
                    if (p0 != p1) {
                        document.remove(p0, p1 - p0);
                    }
                    document.insertString(p0, text, null);
                }
            } catch (final IOException ioe) {
                logger.log(Level.SEVERE, "Problem while pasting text.", ioe);
            } catch (final UnsupportedFlavorException ufe) {
                logger.log(Level.SEVERE, "Problem while pasting text.", ufe);
            } catch (final BadLocationException ble) {
                logger.log(Level.SEVERE, "Problem while pasting text.", ble);
            }
        }

        private void initialize() {
            refreshLanguage();

            final Toolkit toolkit = Toolkit.getDefaultToolkit();
            clipboard = toolkit.getSystemClipboard();

            cutItem.addActionListener(this);
            copyItem.addActionListener(this);
            pasteItem.addActionListener(this);

            if( undoItem != null && redoItem != null ) {
                add(undoItem);
                add(redoItem);
                addSeparator();
            }
            add(cutItem);
            add(copyItem);
            add(pasteItem);
        }

        private void refreshLanguage() {
            if( undoItem != null ) { undoItem.setText(language.getString("Common.undo")); }
            if( redoItem != null ) { redoItem.setText(language.getString("Common.redo")); }
            cutItem.setText(language.getString("Common.cut"));
            copyItem.setText(language.getString("Common.copy"));
            pasteItem.setText(language.getString("Common.paste"));
        }

        public void lostOwnership(final Clipboard nclipboard, final Transferable contents) {}

        @Override
        public void show(final Component invoker, final int x, final int y) {
            if (sourceTextComponent.getSelectedText() != null) {
                cutItem.setEnabled(true);
                copyItem.setEnabled(true);
            } else {
                cutItem.setEnabled(false);
                copyItem.setEnabled(false);
            }
            final Transferable clipboardContent = clipboard.getContents(this);
            if ((clipboardContent != null) &&
                    (clipboardContent.isDataFlavorSupported(DataFlavor.stringFlavor))) {
                pasteItem.setEnabled(true);
            } else {
                pasteItem.setEnabled(false);
            }
            super.show(invoker, x, y);
        }
    }

    private class MFAttachedBoard extends TableMember.BaseTableMember<MFAttachedBoard> {
        Board aBoard;

        public MFAttachedBoard(final Board ab) {
            aBoard = ab;
        }

        public Board getBoardObject() {
            return aBoard;
        }

        public Comparable<?> getValueAt(final int column) {
            switch (column) {
                case 0 : return aBoard.getName();
                case 1 : return (aBoard.getPublicKey() == null) ? "N/A" : aBoard.getPublicKey();
                case 2 : return (aBoard.getPrivateKey() == null) ? "N/A" : aBoard.getPrivateKey();
                case 3 : return (aBoard.getDescription() == null) ? "N/A" : aBoard.getDescription();
            }
            return "*ERR*";
        }
    }

    private class MFAttachedBoardsTable extends SortedTable<MFAttachedBoard> {
        public MFAttachedBoardsTable(final MFAttachedBoardsTableModel m) {
            super(m);
            // set column sizes
            final int[] widths = {250, 80, 80};
            for (int i = 0; i < widths.length; i++) {
                getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }
            // default for sort: sort by name ascending ?
            sortedColumnIndex = 0;
            sortedColumnAscending = true;
            resortTable();
        }
    }

    private class MFAttachedBoardsTableModel extends SortedTableModel<MFAttachedBoard> {
        protected final Class<?> columnClasses[] = {
            String.class,
            String.class,
            String.class,
            String.class
        };
        protected final String columnNames[] = {
            language.getString("MessageFrame.boardAttachmentTable.boardname"),
            language.getString("MessageFrame.boardAttachmentTable.publicKey"),
            language.getString("MessageFrame.boardAttachmentTable.privateKey"),
            language.getString("MessageFrame.boardAttachmentTable.description")
        };

        public MFAttachedBoardsTableModel() {
            super();
        }
        @Override
        public Class<?> getColumnClass(final int column) {
            if( column >= 0 && column < columnClasses.length ) {
                return columnClasses[column];
            }
            return null;
        }
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        @Override
        public String getColumnName(final int column) {
            if( column >= 0 && column < columnNames.length ) {
                return columnNames[column];
            }
            return null;
        }
        @Override
        public boolean isCellEditable(final int row, final int col) {
            return false;
        }
        @Override
        public void setValueAt(final Object aValue, final int row, final int column) {}
    }

    private class MFAttachedFile extends TableMember.BaseTableMember<MFAttachedFile>  {
        File aFile;
        
        public MFAttachedFile(final File af) {
            aFile = af;
        }
        
        public File getFile() {
            return aFile;
        }
        
        public Comparable<?> getValueAt(final int column)  {
            switch(column) {
                case 0: return aFile.getName();
                case 1: return Long.toString(aFile.length());
            }
            throw new IndexOutOfBoundsException("No such column: " + Integer.toString(column));
        }

        @Override
        public int compareTo(final MFAttachedFile otherTableMember, final int tableColumnIndex) {
            // override sorting of specific columns
            if( tableColumnIndex == 1 ) { // filesize
                // if the other table member object is null, just return -1 instantly so that the non-null
                // member we're comparing will be sorted above the null-row. this is just a precaution.
                // NOTE: we could also make sure "trackDownloadKey" is non-null for each, but it always is.
                if( otherTableMember == null ) { return -1; }

                // we know that both filesize is a long, so we don't need any null-checks since longs are a basic type (never null).
                return Mixed.compareLong(aFile.length(), otherTableMember.getFile().length());
            } else {
                // all other columns use the default case-insensitive string comparator
                return super.compareTo(otherTableMember, tableColumnIndex);
            }
        }
    }

    private class MFAttachedFilesTable extends SortedTable<MFAttachedFile> {
        public MFAttachedFilesTable(final MFAttachedFilesTableModel m) {
            super(m);
            
            // set column sizes
            final int[] widths = {250, 80};
            for (int i = 0; i < widths.length; i++) {
                getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }
            
            // default for sort: sort by name ascending ?
            sortedColumnIndex = 0;
            sortedColumnAscending = true;
            resortTable();
        }
    }

    private class MFAttachedFilesTableModel extends SortedTableModel<MFAttachedFile> {
    	
        protected final Class<?> columnClasses[] = {
            String.class,
            String.class
        };
        
        protected final String columnNames[] = {
            language.getString("MessageFrame.fileAttachmentTable.filename"),
            language.getString("MessageFrame.fileAttachmentTable.size")
        };
        
        public MFAttachedFilesTableModel() {
            super();
        }
        
        @Override
        public Class<?> getColumnClass(final int column) {
            if( column >= 0 && column < columnClasses.length ) {
                return columnClasses[column];
            }
            return null;
        }
        
        @Override
        public int getColumnCount() {
            return columnNames.length;
        }
        
        @Override
        public String getColumnName(final int column) {
            if( column >= 0 && column < columnNames.length ) {
                return columnNames[column];
            }
            return null;
        }
        
        @Override
        public boolean isCellEditable(final int row, final int col) {
            return false;
        }
    }

    private class TransferObject {
        public Board newBoard;
        public String newSubject;
        public String inReplyTo;
        public String newText;
        public boolean isReply;
        public Identity recipient = null;;
        public LocalIdentity senderId = null;
    }

    public static synchronized int getOpenInstanceCount() {
        return openInstanceCount;
    }
    private static synchronized void incOpenInstanceCount() {
        openInstanceCount++;
    }
    private static synchronized void decOpenInstanceCount() {
        if( openInstanceCount > 0 ) { // paranoia
            openInstanceCount--;
        }
    }
}
