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
package frost.messaging.freetalk.gui;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.KeyStroke;
import javax.swing.event.*;
import javax.swing.text.*;

import org.joda.time.*;

import frost.*;
import frost.ext.*;
import frost.gui.*;
import frost.gui.model.*;
import frost.identities.*;
import frost.messaging.freetalk.*;
import frost.messaging.freetalk.boards.*;
import frost.messaging.freetalk.identities.*;
import frost.messaging.frost.boards.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.CompoundUndoManager;
import frost.util.gui.SmartFileFilters;
import frost.util.gui.search.FindAction;
import frost.util.gui.search.TextComponentFindAction;
import frost.util.gui.textpane.*;
import frost.util.gui.translation.*;

public class FreetalkMessageFrame extends JFrame implements AltEditCallbackInterface{

    private static final Logger logger = Logger.getLogger(FreetalkMessageFrame.class.getName());

    private final Language language;

    private final Listener listener = new Listener();

    private boolean initialized = false;

    private final Window parentWindow;

    private FreetalkBoard board;
    private String repliedMsgId;
    private final SettingsClass frostSettings;

    private MFAttachedFilesTable filesTable;
    private MFAttachedFilesTableModel filesTableModel;

    private JSplitPane messageSplitPane = null;
    private JSplitPane attachmentsSplitPane = null;
    private JScrollPane filesTableScrollPane;

    private JSkinnablePopupMenu attFilesPopupMenu;
    private MessageBodyPopupMenu messageBodyPopupMenu;

    private final JButton Bsend = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-send.png"));
    private final JButton Bcancel = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-discard.png"));
    private final JButton BattachFile = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/mail-attachment.png"));
//    private final JButton BattachBoard = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/internet-group-chat.png"));

//    private final JCheckBox sign = new JCheckBox();
//    private final JCheckBox encrypt = new JCheckBox();
//    private JComboBox<Identity> buddies;

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

    private FreetalkMessage repliedMessage = null;

    private JComboBox<Object> ownIdentitiesComboBox = null;

    private static int openInstanceCount = 0;

    public FreetalkMessageFrame(final SettingsClass newSettings, final Window tparentWindow) {
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
        messageTextArea.setForeground(Color.BLACK);
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

//    private void attachBoards_actionPerformed(final ActionEvent e) {
//
//        // get and sort all boards
//        final List<Board> allBoards = MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards();
//        if (allBoards.size() == 0) {
//            return;
//        }
//        Collections.sort(allBoards);
//
//        final BoardsChooser chooser = new BoardsChooser(this, allBoards);
//        chooser.setLocationRelativeTo(this);
//        final List<Board> chosenBoards = chooser.runDialog();
//        if (chosenBoards == null || chosenBoards.size() == 0) { // nothing chosed or cancelled
//            return;
//        }
//
//        for (int i = 0; i < chosenBoards.size(); i++) {
//            final Board chosedBoard = chosenBoards.get(i);
//
//            String privKey = chosedBoard.getPrivateKey();
//
//            if (privKey != null) {
//                final int answer =
//                    MiscToolkit.showConfirmDialog(this,
//                        language.formatMessage("MessageFrame.attachBoard.sendPrivateKeyConfirmationDialog.body", chosedBoard.getName()),
//                        language.getString("MessageFrame.attachBoard.sendPrivateKeyConfirmationDialog.title"),
//                        MiscToolkit.YES_NO_OPTION);
//                if( answer != MiscToolkit.YES_OPTION ) {
//                    privKey = null; // don't provide privkey
//                }
//            }
//            // build a new board because maybe privKey shouldn't be uploaded
//            final Board aNewBoard =
//                new Board(chosedBoard.getName(), chosedBoard.getPublicKey(), privKey, chosedBoard.getDescription());
//            final MFAttachedBoard ab = new MFAttachedBoard(aNewBoard);
//            boardsTableModel.addRow(ab);
//        }
//        positionDividers();
//    }

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

        final int returnVal = fc.showOpenDialog(FreetalkMessageFrame.this);
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
            final FreetalkBoard newBoard,
            final String newSubject,
            final String inReplyTo,
            String newText,
            final boolean isReply,
            final FreetalkOwnIdentity senderId,   // if given compose encrypted reply
            final FreetalkMessage msg) {

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
            composeMessageContinued(newBoard, newSubject, inReplyTo, newText, null, isReply, senderId);
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
                to.senderId);
    }

    /**
     * This method is either invoked by ComposeMessage OR by the callback of the AltEdit class.
     */
    private void composeMessageContinued(
        final FreetalkBoard newBoard,
        final String newSubject,
        final String inReplyTo,
        String newText,
        final String altEditText,
        final boolean isReply,
        final FreetalkOwnIdentity senderId)   // if given compose encrypted reply
    {
        headerArea.setEnabled(false);
        board = newBoard;
        repliedMsgId = inReplyTo; // maybe null

        String from;
        if( senderId != null ) {
            // encrypted reply!
            from = senderId.getFreetalkAddress();
        } else {
            // use remembered sender name, maybe per board
            String userName = Core.frostSettings.getValue("freetalkAddress."+board.getName());
            if( userName == null || userName.length() == 0 ) {
                userName = Core.frostSettings.getValue(SettingsClass.LAST_USED_FROMNAME);
            }
            if( FreetalkManager.getInstance().isOwnIdentity(userName) ) {
                // isSigned
                from = userName;
            } else {
                from = FreetalkManager.getInstance().getOwnIdentities().get(0).getFreetalkAddress();
            }
        }
        oldSender = from;

        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        try {
            initialize(newBoard, newSubject);
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Exception thrown in composeMessage(...)", e);
        }

//        sign.setEnabled(false);
//
//        final ImageIcon signedIcon = MiscToolkit.loadImageIcon("/data/toolbar/message-signed.png");
//        final ImageIcon unsignedIcon = MiscToolkit.loadImageIcon("/data/toolbar/message-unsigned.png");
//        sign.setDisabledSelectedIcon(signedIcon);
//        sign.setDisabledIcon(unsignedIcon);
//        sign.setSelectedIcon(signedIcon);
//        sign.setIcon(unsignedIcon);
//
//        sign.addItemListener(new ItemListener() {
//            public void itemStateChanged(final ItemEvent e) {
//                updateSignToolTip();
//            }
//        });

        // maybe prepare to reply to an encrypted message
//        if( recipient != null ) {
//            // set correct sender identity
//            for(int x=0; x < getOwnIdentitiesComboBox().getItemCount(); x++) {
//                final Object obj = getOwnIdentitiesComboBox().getItemAt(x);
//                if( obj instanceof LocalIdentity ) {
//                    final LocalIdentity li = (LocalIdentity)obj;
//                    if( senderId.getUniqueName().equals(li.getUniqueName()) ) {
//                        getOwnIdentitiesComboBox().setSelectedIndex(x);
//                        break;
//                    }
//                }
//            }
//            getOwnIdentitiesComboBox().setEnabled(false);
//            // set and lock controls (after we set the identity, the itemlistener would reset the controls!)
//            sign.setSelected(true);
//            encrypt.setSelected(true);
//            buddies.removeAllItems();
//            buddies.addItem(recipient);
//            buddies.setSelectedItem(recipient);
//            // dont allow to disable signing/encryption
//            encrypt.setEnabled(false);
//            buddies.setEnabled(false);
//        } else {
                // set saved sender identity
            for(int x=0; x < getOwnIdentitiesComboBox().getItemCount(); x++) {
                final Object obj = getOwnIdentitiesComboBox().getItemAt(x);
                if( obj instanceof FreetalkOwnIdentity ) {
                    final FreetalkOwnIdentity li = (FreetalkOwnIdentity)obj;
                    if( from.equals(li.getFreetalkAddress()) ) {
                        getOwnIdentitiesComboBox().setSelectedIndex(x);
                        getOwnIdentitiesComboBox().setEditable(false);
                        break;
                    }
                }
            }

//            if( sign.isSelected() && buddies.getItemCount() > 0 ) {
//                encrypt.setEnabled(true);
//            } else {
//                encrypt.setEnabled(false);
//            }
//            encrypt.setSelected(false);
//            buddies.setEnabled(false);
//        }

//        updateSignToolTip();

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
//        if( sign.isSelected()  ) {
//            // maybe append a signature
//            final LocalIdentity li = (LocalIdentity)getOwnIdentitiesComboBox().getSelectedItem();
//            if( li.getSignature() != null ) {
//                currentSignature = "\n--\n" + li.getSignature();
//                newText += currentSignature;
//            }
//        }

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

    public void composeNewMessage(final FreetalkBoard newBoard, final String newSubject, final String newText) {
        composeMessage(newBoard, newSubject, null, newText, false, null, null);
    }

    public void composeReply(
            final FreetalkBoard newBoard,
            final String newSubject,
            final String inReplyTo,
            final String newText,
            final FreetalkMessage msg) {
        composeMessage(newBoard, newSubject, inReplyTo, newText, true, null, msg);
    }

//    public void composeEncryptedReply(
//            final Board newBoard,
//            final String newSubject,
//            final String inReplyTo,
//            final String newText,
//            final LocalIdentity senderId,
//            final FreetalkMessage msg) {
//        composeMessage(newBoard, newSubject, inReplyTo, newText, true, senderId, msg);
//    }

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

    private void initialize(final FreetalkBoard targetBoard, final String subject) throws Exception {
        if (!initialized) {
            refreshLanguage();
            language.addLanguageListener(listener);

            final ImageIcon frameIcon = MiscToolkit.loadImageIcon("/data/toolbar/mail-message-new.png");
            setIconImage(frameIcon.getImage());
            setResizable(true);

            filesTableModel = new MFAttachedFilesTableModel();
            filesTable = new MFAttachedFilesTable(filesTableModel);
            filesTableScrollPane = new JScrollPane(filesTable);
            filesTableScrollPane.setWheelScrollingEnabled(true);
            filesTable.addMouseListener(listener);

// FIXME: option to show own identities in list, or to hide them
//            final List<Identity> budList = Core.getIdentities().getAllFRIENDIdentities();
//            Identity id = null;
//            if( repliedMessage != null ) {
//                id = repliedMessage.getAuthor();
//            }
//            if( budList.size() > 0 || id != null ) {
//                Collections.sort( budList, new BuddyComparator() );
//                if( id != null ) {
//                    if( id.isFRIEND() == true ) {
//                        budList.remove(id); // remove before put to top of list
//                    }
//                    // add id to top of list in case the user enables 'encrypt'
//                    budList.add(0, id);
//                }
//                buddies = new JComboBox<Identity>(new Vector<Identity>(budList));
//                buddies.setSelectedItem(budList.get(0));
//            } else {
//                buddies = new JComboBox<Identity>();
//            }
//            buddies.setMaximumSize(new Dimension(300, 25)); // dirty fix for overlength combobox on linux

            MiscToolkit.configureButton(Bsend, "MessageFrame.toolbar.tooltip.sendMessage", language);
            MiscToolkit.configureButton(Bcancel, "Common.cancel", language);
            MiscToolkit.configureButton(BattachFile, "MessageFrame.toolbar.tooltip.addFileAttachments", language);
//            MiscToolkit.configureButton(BattachBoard, "MessageFrame.toolbar.tooltip.addBoardAttachments", language);
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

//            sign.setOpaque(false);
//            encrypt.setOpaque(false);

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
                    final WindowEvent closingEvent = new WindowEvent(FreetalkMessageFrame.this, WindowEvent.WINDOW_CLOSING);
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
                }
            });
            BattachFile.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    attachFile_actionPerformed(e);
                }
            });
//            BattachBoard.addActionListener(new java.awt.event.ActionListener() {
//                public void actionPerformed(final ActionEvent e) {
//                    attachBoards_actionPerformed(e);
//                }
//            });
//            encrypt.addActionListener(new java.awt.event.ActionListener() {
//                public void actionPerformed(final ActionEvent e) {
//                    encrypt_actionPerformed(e);
//                }
//            });
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
            constraints.gridwidth = 2;
            constraints.insets = insets0;
            constraints.weightx = 1.0;
            panelTextfields.add(getOwnIdentitiesComboBox(), constraints);

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
//            panelToolbar.add(BattachBoard);
            panelToolbar.addSeparator();
//            panelToolbar.add(sign);
//            panelToolbar.addSeparator();
//            panelToolbar.add(encrypt);
//            panelToolbar.add(buddies);
//            panelButtons.add(addAttachedFilesToUploadTable);

            final ScrollableBar panelButtonsScrollable = new ScrollableBar(panelToolbar);

            panelHeader.add(panelButtonsScrollable, BorderLayout.PAGE_START);
//            panelToolbar.add(panelButtons, BorderLayout.PAGE_START);
            panelHeader.add(panelTextfields, BorderLayout.CENTER);

            //Put everything together
            attachmentsSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filesTableScrollPane,
                    null);
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
                    final WindowEvent closingEvent = new WindowEvent(FreetalkMessageFrame.this, WindowEvent.WINDOW_CLOSING);
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

        final JMenuItem removeFiles = new JMenuItem(language.getString("MessageFrame.attachmentTables.popupmenu.remove"));

        removeFiles.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                removeSelectedItemsFromTable(filesTable);
            }
        });

        attFilesPopupMenu.add( removeFiles );
    }

    private void positionDividers() {
        final int attachedFiles = filesTableModel.getRowCount();
        if (attachedFiles == 0) {
            // Neither files nor boards
            messageSplitPane.setBottomComponent(null);
            messageSplitPane.setDividerSize(0);
            return;
        }
        messageSplitPane.setDividerSize(3);
        messageSplitPane.setDividerLocation(0.75);
        messageSplitPane.setBottomComponent(filesTableScrollPane);
    }

    private void refreshLanguage() {
        setTitle(language.getString("MessageFrame.createMessage.title"));

        Bsend.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.sendMessage"));
        Bcancel.setToolTipText(language.getString("Common.cancel"));
        BattachFile.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.addFileAttachments"));
//        BattachBoard.setToolTipText(language.getString("MessageFrame.toolbar.tooltip.addBoardAttachments"));

//        encrypt.setText(language.getString("MessageFrame.toolbar.encryptFor"));

        Lboard.setText(language.getString("MessageFrame.board") + ": ");
        Lfrom.setText(language.getString("MessageFrame.from") + ": ");
        Lsubject.setText(language.getString("MessageFrame.subject") + ": ");

//        updateSignToolTip();
    }

//    private void updateSignToolTip() {
//        final boolean isSelected = sign.isSelected();
//        if( isSelected ) {
//            sign.setToolTipText(language.getString("MessagePane.toolbar.tooltip.isSigned"));
//        } else {
//            sign.setToolTipText(language.getString("MessagePane.toolbar.tooltip.isUnsigned"));
//        }
//    }

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

        FreetalkOwnIdentity senderId = null;
        String from;
        senderId = (FreetalkOwnIdentity)getOwnIdentitiesComboBox().getSelectedItem();
        from = senderId.getFreetalkAddress();

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

        if( ! hasUserEdit() ) { // must have at least 1 non-whitespace edit in their msg area
            MiscToolkit.showMessageDialog( this,
                    language.getString("MessageFrame.noContentError.text"),
                    language.getString("MessageFrame.noContentError.title"),
                    MiscToolkit.ERROR_MESSAGE);
            messageTextArea.requestFocusInWindow();
            return;
        }

        // for convinience set last used user
        if( from.indexOf("@") < 0 ) {
            // only save anonymous usernames
            frostSettings.setValue(SettingsClass.LAST_USED_FROMNAME, from);
        }
        frostSettings.setValue("freetalkAddress."+board.getName(), from);

        final int idLinePos = headerArea.getStartPos();
        final int idLineLen = headerArea.getEndPos() - headerArea.getStartPos();
//        final FrostUnsentMessageObject newMessage = new FrostUnsentMessageObject();
//        newMessage.setMessageId(Mixed.createUniqueId()); // new message, create a new unique msg id
//        newMessage.setInReplyTo(repliedMsgId);
//        newMessage.setBoard(board);
//        newMessage.setFromName(from);
//        newMessage.setSubject(subject);
//        newMessage.setContent(text);
//        newMessage.setIdLinePos(idLinePos);
//        newMessage.setIdLineLen(idLineLen);
//
//        // MessageUploadThread will set date + time !
//
//        // attach all files and boards the user chosed
//        if( filesTableModel.getRowCount() > 0 ) {
//            for(int x=0; x < filesTableModel.getRowCount(); x++) {
//                final MFAttachedFile af = (MFAttachedFile)filesTableModel.getRow(x);
//                final File aChosedFile = af.getFile();
//                final FileAttachment fa = new FileAttachment(aChosedFile);
//                newMessage.addAttachment(fa);
//            }
//            newMessage.setHasFileAttachments(true);
//        }
//        if( boardsTableModel.getRowCount() > 0 ) {
//            for(int x=0; x < boardsTableModel.getRowCount(); x++) {
//                final MFAttachedBoard ab = (MFAttachedBoard)boardsTableModel.getRow(x);
//                final Board aChosedBoard = ab.getBoardObject();
//                final BoardAttachment ba = new BoardAttachment(aChosedBoard);
//                newMessage.addAttachment(ba);
//            }
//            newMessage.setHasBoardAttachments(true);
//        }

//        Identity recipient = null;
//        if( encrypt.isSelected() ) {
//            recipient = (Identity)buddies.getSelectedItem();
//            if( recipient == null ) {
//                MiscToolkit.showMessageDialog( this,
//                        language.getString("MessageFrame.encryptErrorNoRecipient.body"),
//                        language.getString("MessageFrame.encryptErrorNoRecipient.title"),
//                        MiscToolkit.ERROR_MESSAGE);
//                return;
//            }
//            newMessage.setRecipientName(recipient.getUniqueName());
//        }

        // FIXME: upload attachments! Set keys!

        final FreetalkUnsentMessage msg = new FreetalkUnsentMessage(
                (repliedMessage!=null)?repliedMessage.getMsgId():null,
                senderId,
                board,
                Collections.singletonList(board),
                subject,
                text,
                null);

        MainFrame.getInstance().getFreetalkMessageTab().sendFreetalkCommandPutMessage(msg);

//        UnsentMessagesManager.addNewUnsentMessage(newMessage);

        // TODO: if user deletes the unsent msg then the replied state keeps (see below)
        //  We would have to set the replied state after the msg was successfully sent, because
        //  we can't remove the state later, maybe the msg was replied twice and we would remove
        //  the replied state from first reply...

        // set isReplied to replied message
//        if( repliedMessage != null ) {
//            if( repliedMessage.isReplied() == false ) {
//                repliedMessage.setReplied(true);
//                final FrostMessageObject saveMsg = repliedMessage;
//                final Thread saver = new Thread() {
//                    @Override
//                    public void run() {
//                        // save the changed isreplied state into the database
//                        MessageStorage.inst().updateMessage(saveMsg);
//                    }
//                };
//                saver.start();
//            }
//        }

        setVisible(false);
        dispose();
    }

    private void senderChanged(final FreetalkOwnIdentity selectedId) {
//        try {
//            boolean isSigned = ( selectedId != null );
//            sign.setSelected(isSigned);
//
//            if( isSigned ) {
//                if( buddies.getItemCount() > 0 ) {
//                    encrypt.setEnabled(true);
//                    if( encrypt.isSelected() ) {
//                        buddies.setEnabled(true);
//                    } else {
//                        buddies.setEnabled(false);
//                    }
//                }
//
//                removeSignatureFromText(currentSignature); // remove signature if existing
//                currentSignature = addSignatureToText(selectedId.getSignature()); // add new signature if not existing
//            } else {
//                encrypt.setSelected(false);
//                encrypt.setEnabled(false);
//                buddies.setEnabled(false);
//                removeSignatureFromText(currentSignature); // remove signature if existing
//                currentSignature = null;
//            }
//        } finally {
//            // throw away entire undo-history to prevent user from undoing the changes to the
//            // signature from switching identity. it's unfortunately the only way to solve this
//            // since we cannot update the otherwise-invalid offsets of previous edits.
//            resetUndoManager();
//        }
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

//    private void encrypt_actionPerformed(final ActionEvent e) {
//        if( encrypt.isSelected() ) {
//            buddies.setEnabled(true);
//        } else {
//            buddies.setEnabled(false);
//        }
//    }

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

    class TextComboBoxEditor extends JTextField implements ComboBoxEditor {
        public TextComboBoxEditor() {
            super();
        }
        public Component getEditorComponent() {
            return this;
        }
        public void setItem(final Object arg0) {
            setText(arg0.toString());
        }
        public Object getItem() {
            return getText();
        }
    }

    private JComboBox<Object> getOwnIdentitiesComboBox() {
        if( ownIdentitiesComboBox == null ) {
            ownIdentitiesComboBox = new JComboBox<Object>();
            // sort own unique names
            final TreeMap<String,FreetalkOwnIdentity> sortedIds = new TreeMap<String,FreetalkOwnIdentity>();
            for (final Object element : FreetalkManager.getInstance().getOwnIdentities()) {
                final FreetalkOwnIdentity li = (FreetalkOwnIdentity)element;
                sortedIds.put(li.getFreetalkAddress(), li);
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

            ownIdentitiesComboBox.setEditor(editor);

            ownIdentitiesComboBox.setEditable(false);

            ownIdentitiesComboBox.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    if( e.getStateChange() == ItemEvent.DESELECTED ) {
                        return;
                    }
                    final String sender = getOwnIdentitiesComboBox().getSelectedItem().toString();
                    updateHeaderArea(sender);
//                    senderChanged(selectedId);
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

        public Comparable getValueAt(final int column) {
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
        public FreetalkBoard newBoard;
        public String newSubject;
        public String inReplyTo;
        public String newText;
        public boolean isReply;
        public FreetalkOwnIdentity senderId = null;
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
