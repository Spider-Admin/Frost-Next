/*
  BoardSettingsFrame.java / Frost
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
import java.awt.event.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

import javax.swing.*;
import javax.swing.border.*;

import frost.*;
import frost.fcp.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;

/**
 * Settingsdialog for a single Board or a folder.
 */
@SuppressWarnings("serial")
public class BoardSettingsFrame extends JDialog {

//  private static final Logger logger = Logger.getLogger(BoardSettingsFrame.class.getName());

    private class Listener implements ActionListener {
        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == publicBoardRadioButton) { // Public board radio button
                radioButton_actionPerformed(e);
            } else if (e.getSource() == secureBoardRadioButton) { // Private board radio button
                radioButton_actionPerformed(e);
            } else if (e.getSource() == generateKeyButton) { // Generate key
                generateKeyButton_actionPerformed(e);
            } else if (e.getSource() == okButton) { // Ok
                okButton_actionPerformed(e);
            } else if (e.getSource() == cancelButton) { // Cancel
                cancelButton_actionPerformed(e);
            } else if (e.getSource() == overrideSettingsCheckBox) { // Override settings
                overrideSettingsCheckBox_actionPerformed(e);
            }
        }
    }

    private class TypingListener implements KeyListener {
        public void keyReleased(final KeyEvent e) {
            overrideTextField_keyReleased(e);
        }
        public void keyTyped(final KeyEvent e) {
        }
        public void keyPressed(final KeyEvent e) {
        }
    }

    private final Language language;
    private final AbstractNode node;
    private final JFrame parentFrame;

    private final Listener listener = new Listener();
    private final TypingListener typingListener = new TypingListener();

    //SF_EDIT    
    private final JTextField startDayToDownload_value = new JTextField(6);
    private final JLabel startDayToDownloadLabel = new JLabel();
    private final JRadioButton startDayToDownload_default = new JRadioButton();
    private final JRadioButton startDayToDownload_set = new JRadioButton();
    //END_EDIT
    
    private final JCheckBox autoUpdateEnabled = new JCheckBox();
    private final JButton cancelButton = new JButton();
    private boolean exitState;
    private final JButton generateKeyButton = new JButton();

    private final JRadioButton storeSentMessages_default = new JRadioButton();
    private final JRadioButton storeSentMessages_false = new JRadioButton();
    private final JRadioButton storeSentMessages_true = new JRadioButton();
    private final JLabel storeSentMessagesLabel = new JLabel();

    private final JRadioButton hideUnsigned_default = new JRadioButton();
    private final JRadioButton hideUnsigned_false = new JRadioButton();
    private final JRadioButton hideUnsigned_true = new JRadioButton();

    private final JRadioButton hideBAD_default = new JRadioButton();
    private final JRadioButton hideBAD_false = new JRadioButton();
    private final JRadioButton hideBAD_true = new JRadioButton();
    private final JLabel hideBADMessagesLabel = new JLabel();

    private final JRadioButton hideNEUTRAL_default = new JRadioButton();
    private final JRadioButton hideNEUTRAL_false = new JRadioButton();
    private final JRadioButton hideNEUTRAL_true = new JRadioButton();
    private final JLabel hideNEUTRALMessagesLabel = new JLabel();

    private final JRadioButton hideGOOD_default = new JRadioButton();
    private final JRadioButton hideGOOD_false = new JRadioButton();
    private final JRadioButton hideGOOD_true = new JRadioButton();
    private final JLabel hideGOODMessagesLabel = new JLabel();
    private final JLabel hideUnsignedMessagesLabel = new JLabel();

    private final JRadioButton hideMessageCount_default = new JRadioButton();
    private final JRadioButton hideMessageCount_set = new JRadioButton();
    private final JTextField hideMessageCount_value = new JTextField(6);
    private final JLabel hideMessageCountLabel = new JLabel();

    private final JRadioButton maxMessageDisplay_default = new JRadioButton();
    private final JRadioButton maxMessageDisplay_set = new JRadioButton();
    private final JTextField maxMessageDisplay_value = new JTextField(6);
    private final JLabel maxMessageDisplayDaysLabel = new JLabel();

    private final JRadioButton maxMessageDownload_default = new JRadioButton();
    private final JRadioButton maxMessageDownload_set = new JRadioButton();
    private final JTextField maxMessageDownload_value = new JTextField(6);
    private final JLabel maxMessageDownloadDaysLabel = new JLabel();

    private final JButton okButton = new JButton();

    private final JCheckBox overrideSettingsCheckBox = new JCheckBox();
    private final JLabel privateKeyLabel = new JLabel();

    private final JTextField privateKeyTextField = new JTextField();

    private final JRadioButton publicBoardRadioButton = new JRadioButton();

    private final JLabel publicKeyLabel = new JLabel();
    private final JTextField publicKeyTextField = new JTextField();

    private final JRadioButton secureBoardRadioButton = new JRadioButton();

    JPanel settingsPanel = new JPanel(new GridBagLayout());

// NOTE:XXX: the "Description:" text label had to be removed to get more window space
//    private final JLabel descriptionLabel = new JLabel();
    private final JTextArea descriptionTextArea = new JTextArea(3, 40); // 3 rows, at least 40 cols
    private JScrollPane descriptionScrollPane;

    /**
     * @param parentFrame
     * @param board
     */
    public BoardSettingsFrame(final JFrame parentFrame, final AbstractNode node) {
        super(parentFrame);

        this.parentFrame = parentFrame;
        this.node = node;
        this.language = Language.getInstance();

        setModal(true);
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        initialize();

        // pack the frame to the optimal size that fits 3 lines of description and all (long) labels
        pack();
        setLocationRelativeTo(parentFrame); // center relative to main window
    }

    /**
     * Close window and do not save settings
     */
    private void cancel() {
        exitState = false;
        dispose();
    }

    /**
     * cancelButton Action Listener (Cancel)
     * @param e
     */
    private void cancelButton_actionPerformed(final ActionEvent e) {
        cancel();
    }

    /**
     * generateKeyButton Action Listener (OK)
     * @param e
     */
    private void generateKeyButton_actionPerformed(final ActionEvent e) {
        try {
            final BoardKeyPair kp = FcpHandler.inst().generateBoardKeyPair();
            if( kp != null ) {
                privateKeyTextField.setText(kp.getPrivateBoardKey());
                publicKeyTextField.setText(kp.getPublicBoardKey());
            }
        } catch (final Throwable ex) {
            MiscToolkit.showMessageDialog(parentFrame, ex.toString(), // message
                    language.getString("BoardSettings.generateKeyPairErrorDialog.title"), MiscToolkit.WARNING_MESSAGE);
        }
    }

    private void overrideSettingsCheckBox_actionPerformed(final ActionEvent e) {
        setPanelEnabled(settingsPanel, overrideSettingsCheckBox.isSelected());
    }

    //------------------------------------------------------------------------

    /**Return exitState
     * @return
     */
    public boolean getExitState() {
        return exitState;
    }

    private JPanel getSettingsPanel() {
        settingsPanel.setBorder(new CompoundBorder(new EtchedBorder(), new EmptyBorder(5,5,5,5)));
        settingsPanel.setLayout(new GridBagLayout());

        final ButtonGroup bg9 = new ButtonGroup();
        bg9.add(startDayToDownload_default);
        bg9.add(startDayToDownload_set);
        final ButtonGroup bg2 = new ButtonGroup();
        bg2.add(maxMessageDisplay_default);
        bg2.add(maxMessageDisplay_set);
        final ButtonGroup bg1 = new ButtonGroup();
        bg1.add(maxMessageDownload_default);
        bg1.add(maxMessageDownload_set);
        final ButtonGroup bg3 = new ButtonGroup();
        bg3.add(hideUnsigned_default);
        bg3.add(hideUnsigned_false);
        bg3.add(hideUnsigned_true);
        final ButtonGroup bg4 = new ButtonGroup();
        bg4.add(hideBAD_default);
        bg4.add(hideBAD_true);
        bg4.add(hideBAD_false);
        final ButtonGroup bg5 = new ButtonGroup();
        bg5.add(hideNEUTRAL_default);
        bg5.add(hideNEUTRAL_true);
        bg5.add(hideNEUTRAL_false);
        final ButtonGroup bg6 = new ButtonGroup();
        bg6.add(hideGOOD_default);
        bg6.add(hideGOOD_true);
        bg6.add(hideGOOD_false);
        final ButtonGroup bg7 = new ButtonGroup();
        bg7.add(storeSentMessages_default);
        bg7.add(storeSentMessages_true);
        bg7.add(storeSentMessages_false);
        final ButtonGroup bg8 = new ButtonGroup();
        bg8.add(hideMessageCount_default);
        bg8.add(hideMessageCount_set);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = new Insets(5, 5, 5, 5);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;

        constraints.gridwidth = 3;
        settingsPanel.add(overrideSettingsCheckBox, constraints);
        constraints.gridy++;
        constraints.insets = new Insets(5, 25, 0, 5);
        settingsPanel.add(autoUpdateEnabled, constraints);
        constraints.gridy++;
        
        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(maxMessageDownloadDaysLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(maxMessageDownload_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(maxMessageDownload_set, constraints);
        constraints.gridx = 2;
        settingsPanel.add(maxMessageDownload_value, constraints);
        constraints.gridy++;

        //SF_EDIT
        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(startDayToDownloadLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(startDayToDownload_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(startDayToDownload_set, constraints);
        constraints.gridx = 2;
        settingsPanel.add(startDayToDownload_value, constraints);
        constraints.gridy++;
        //END_EDIT

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(maxMessageDisplayDaysLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(maxMessageDisplay_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(maxMessageDisplay_set, constraints);
        constraints.gridx = 2;
        settingsPanel.add(maxMessageDisplay_value, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(hideUnsignedMessagesLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(hideUnsigned_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(hideUnsigned_true, constraints);
        constraints.gridx = 2;
        settingsPanel.add(hideUnsigned_false, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(hideBADMessagesLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(hideBAD_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(hideBAD_true, constraints);
        constraints.gridx = 2;
        settingsPanel.add(hideBAD_false, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(hideNEUTRALMessagesLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(hideNEUTRAL_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(hideNEUTRAL_true, constraints);
        constraints.gridx = 2;
        settingsPanel.add(hideNEUTRAL_false, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(hideGOODMessagesLabel, constraints);
        constraints.insets = new Insets(0, 35, 5, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(hideGOOD_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(hideGOOD_true, constraints);
        constraints.gridx = 2;
        settingsPanel.add(hideGOOD_false, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(hideMessageCountLabel, constraints);
        constraints.insets = new Insets(0, 35, 0, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(hideMessageCount_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(hideMessageCount_set, constraints);
        constraints.gridx = 2;
        settingsPanel.add(hideMessageCount_value, constraints);
        constraints.gridy++;

        constraints.gridwidth = 3;
        constraints.gridx = 0;
        constraints.insets = new Insets(3, 25, 0, 5);
        settingsPanel.add(storeSentMessagesLabel, constraints);
        constraints.insets = new Insets(0, 35, 5, 5);
        constraints.gridwidth = 1;
        constraints.gridy++;
        constraints.gridx = 0;
        settingsPanel.add(storeSentMessages_default, constraints);
        constraints.insets = new Insets(0, 0, 0, 5);
        constraints.gridx = 1;
        settingsPanel.add(storeSentMessages_true, constraints);
        constraints.gridx = 2;
        settingsPanel.add(storeSentMessages_false, constraints);

        // Adds listeners
        overrideSettingsCheckBox.addActionListener(listener);
        // Keyboard listeners for the override textfields
        startDayToDownload_value.addKeyListener(typingListener);
        maxMessageDisplay_value.addKeyListener(typingListener);
        maxMessageDownload_value.addKeyListener(typingListener);
        hideMessageCount_value.addKeyListener(typingListener);

        setPanelEnabled(settingsPanel, (node.isBoard())?((Board)node).isConfigured():false);

        return settingsPanel;
    }

    private void overrideTextField_keyReleased(final KeyEvent e) {
        // when the user types, we check what's in the text field. if it's empty,
        // we set it back to the "Default" radio button, otherwise we set it to
        // the "Set to:" radio button. this makes it easier for the user to change
        // values without having to fiddle with the radio buttons manually.
        // NOTE: this event fires in the GUI thread, after the new text is fetchable,
        // so we don't need to invokeLater anything here...
        
        // "Days backwards to start at"
        if( e.getSource() == startDayToDownload_value ) {
            final String text = startDayToDownload_value.getText();
            if( text == null || text.equals("") ) {
                if( !startDayToDownload_default.isSelected() ) { startDayToDownload_default.doClick(); }
            } else {
                if( !startDayToDownload_set.isSelected() ) { startDayToDownload_set.doClick(); }
            }
        }
        // "Number of days to display"
        else if( e.getSource() == maxMessageDisplay_value ) {
            final String text = maxMessageDisplay_value.getText();
            if( text == null || text.equals("") ) {
                if( !maxMessageDisplay_default.isSelected() ) { maxMessageDisplay_default.doClick(); }
            } else {
                if( !maxMessageDisplay_set.isSelected() ) { maxMessageDisplay_set.doClick(); }
            }
        }
        // "Number of days to download backwards"
        else if( e.getSource() == maxMessageDownload_value ) {
            final String text = maxMessageDownload_value.getText();
            if( text == null || text.equals("") ) {
                if( !maxMessageDownload_default.isSelected() ) { maxMessageDownload_default.doClick(); }
            } else {
                if( !maxMessageDownload_set.isSelected() ) { maxMessageDownload_set.doClick(); }
            }
        }
        // "Hide messages from identities with fewer than X messages"
        else if( e.getSource() == hideMessageCount_value ) {
            final String text = hideMessageCount_value.getText();
            if( text == null || text.equals("") ) {
                if( !hideMessageCount_default.isSelected() ) { hideMessageCount_default.doClick(); }
            } else {
                if( !hideMessageCount_set.isSelected() ) { hideMessageCount_set.doClick(); }
            }
        }
    }

    private void initialize() {
        final JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(6,6,6,6));
        setContentPane(contentPanel);
        contentPanel.setLayout(new GridBagLayout());
        refreshLanguage();

        // Adds all of the components
        new TextComponentClipboardMenu(startDayToDownload_value, language); //SF_EDIT
        new TextComponentClipboardMenu(maxMessageDisplay_value, language);
        new TextComponentClipboardMenu(maxMessageDownload_value, language);
        new TextComponentClipboardMenu(privateKeyTextField, language);
        new TextComponentClipboardMenu(publicKeyTextField, language);
        new TextComponentClipboardMenu(descriptionTextArea, language);
        new TextComponentClipboardMenu(hideMessageCount_value, language);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(3, 3, 3, 3);
        constraints.gridwidth = 3;

        constraints.weightx = 2;
        constraints.gridx = 0;
        constraints.gridy = 0;
        contentPanel.add(getKeysPanel(), constraints);

//        constraints.gridx = 0;
//        constraints.gridy = 1;
//        contentPanel.add(descriptionLabel, constraints);
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.weightx = 1;
        constraints.weighty = 1;
        descriptionScrollPane = new JScrollPane(descriptionTextArea);
        contentPanel.add(descriptionScrollPane, constraints);
        constraints.weightx = 0;
        constraints.weighty = 0;

        constraints.gridx = 0;
        constraints.gridy = 3;
        contentPanel.add(getSettingsPanel(), constraints);

        constraints.insets = new Insets(3, 3, 0, 3);
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        constraints.gridwidth = 1;
        constraints.weightx = 2;
        constraints.gridx = 0;
        constraints.gridy = 4;
        contentPanel.add(okButton, constraints);
        constraints.weightx = 0;
        constraints.gridx = 1;
        constraints.gridy = 4;
        contentPanel.add(cancelButton, constraints);

//        descriptionLabel.setEnabled(false);
        descriptionTextArea.setEnabled(false);
        publicBoardRadioButton.setSelected(true);
        privateKeyTextField.setEnabled(false);
        publicKeyTextField.setEnabled(false);
        generateKeyButton.setEnabled(false);

        // Adds listeners
        okButton.addActionListener(listener);
        cancelButton.addActionListener(listener);
        
        loadKeypair();
        loadBoardSettings();
    }

    private JPanel getKeysPanel() {
        final JPanel keysPanel = new JPanel();
        keysPanel.setLayout(new GridBagLayout());

        final ButtonGroup isSecureGroup = new ButtonGroup();
        isSecureGroup.add(publicBoardRadioButton);
        isSecureGroup.add(secureBoardRadioButton);

        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 3, 0);
        constraints.weighty = 1;

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.weightx = 0.2;
        keysPanel.add(publicBoardRadioButton, constraints);
        constraints.insets = new Insets(3, 0, 3, 0);

        constraints.weightx = 0.2;
        constraints.gridy = 1;
        constraints.gridwidth = 2;
        keysPanel.add(secureBoardRadioButton, constraints);

        constraints.gridx = 2;
        constraints.gridwidth = 1;
        constraints.weightx = 0.8;
        constraints.fill = GridBagConstraints.NONE;
        constraints.anchor = GridBagConstraints.EAST;
        keysPanel.add(generateKeyButton, constraints);

        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 1;
        constraints.weightx = 0.0;
        keysPanel.add(privateKeyLabel, constraints);
        constraints.insets = new Insets(3, 3, 3, 0);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 0.8;
        keysPanel.add(privateKeyTextField, constraints);

        constraints.insets = new Insets(3, 0, 3, 0);
        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.weightx = 0.0;
        keysPanel.add(publicKeyLabel, constraints);
        constraints.insets = new Insets(3, 3, 3, 0);
        constraints.gridx = 1;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1.0;
        keysPanel.add(publicKeyTextField, constraints);

        // Adds listeners
        publicBoardRadioButton.addActionListener(listener);
        secureBoardRadioButton.addActionListener(listener);
        generateKeyButton.addActionListener(listener);

        return keysPanel;
    }

    /**
     * Set initial values for board settings.
     */
    private void loadBoardSettings() {
        if( node.isFolder() ) {

//            descriptionLabel.setEnabled(false);
            descriptionTextArea.setEnabled(false);
            overrideSettingsCheckBox.setSelected(false);

        } else if( node.isBoard() ) {
            final Board board = (Board)node;
//            descriptionLabel.setEnabled(true);
            descriptionTextArea.setEnabled(true);
            // its a single board
            if (board.getDescription() != null) {
                descriptionTextArea.setText(board.getDescription());
            }

            overrideSettingsCheckBox.setSelected(board.isConfigured());

            if (!board.isConfigured() || board.getStartDaysBackObj() == null) {
                startDayToDownload_default.setSelected(true);
            } else {
                startDayToDownload_set.setSelected(true);
                startDayToDownload_value.setText("" + board.getStartDaysBack());
            }
            
            if (!board.isConfigured() || board.getMaxMessageDisplayObj() == null) {
                maxMessageDisplay_default.setSelected(true);
            } else {
                maxMessageDisplay_set.setSelected(true);
                maxMessageDisplay_value.setText("" + board.getMaxMessageDisplay());
            }

            if (!board.isConfigured() || board.getMaxMessageDownloadObj() == null) {
                maxMessageDownload_default.setSelected(true);
            } else {
                maxMessageDownload_set.setSelected(true);
                maxMessageDownload_value.setText("" + board.getMaxMessageDownload());
            }

            if (!board.isConfigured()) {
                autoUpdateEnabled.setSelected(true); // default
            } else if (board.getAutoUpdateEnabled()) {
                autoUpdateEnabled.setSelected(true);
            } else {
                autoUpdateEnabled.setSelected(false);
            }

            if (!board.isConfigured() || board.getHideUnsignedObj() == null) {
                hideUnsigned_default.setSelected(true);
            } else if (board.getHideUnsigned()) {
                hideUnsigned_true.setSelected(true);
            } else {
                hideUnsigned_false.setSelected(true);
            }

            if (!board.isConfigured() || board.getHideBADObj() == null) {
                hideBAD_default.setSelected(true);
            } else if (board.getHideBAD()) {
                hideBAD_true.setSelected(true);
            } else {
                hideBAD_false.setSelected(true);
            }

            if (!board.isConfigured() || board.getHideNEUTRALObj() == null) {
                hideNEUTRAL_default.setSelected(true);
            } else if (board.getHideNEUTRAL()) {
                hideNEUTRAL_true.setSelected(true);
            } else {
                hideNEUTRAL_false.setSelected(true);
            }

            if (!board.isConfigured() || board.getHideGOODObj() == null) {
                hideGOOD_default.setSelected(true);
            } else if (board.getHideGOOD()) {
                hideGOOD_true.setSelected(true);
            } else {
                hideGOOD_false.setSelected(true);
            }

            if (!board.isConfigured() || board.getHideMessageCountObj() == null) {
                hideMessageCount_default.setSelected(true);
            } else {
                hideMessageCount_set.setSelected(true);
                hideMessageCount_value.setText("" + board.getHideMessageCount());
            }

            if (!board.isConfigured() || board.getStoreSentMessagesObj() == null) {
                storeSentMessages_default.setSelected(true);
            } else if (board.getStoreSentMessages()) {
                storeSentMessages_true.setSelected(true);
            } else {
                storeSentMessages_false.setSelected(true);
            }
        }
    }

    /**
     * Loads keypair
     */
    private void loadKeypair() {

        if( node.isFolder() ) {
            privateKeyTextField.setEnabled(false);
            publicKeyTextField.setEnabled(false);
            generateKeyButton.setEnabled(false);
            publicBoardRadioButton.setEnabled(false);
            secureBoardRadioButton.setEnabled(false);

        } else if( node.isBoard() ) {
            final Board board = (Board)node;
            final String privateKey = board.getPrivateKey();
            final String publicKey = board.getPublicKey();

            if (privateKey != null) {
                privateKeyTextField.setText(privateKey);
            } else {
                privateKeyTextField.setText(language.getString("BoardSettings.text.keyNotAvailable"));
            }

            if (publicKey != null) {
                publicKeyTextField.setText(publicKey);
            } else {
                publicKeyTextField.setText(language.getString("BoardSettings.text.keyNotAvailable"));
            }

            if (board.isWriteAccessBoard() || board.isReadAccessBoard()) {
                privateKeyTextField.setEnabled(true);
                publicKeyTextField.setEnabled(true);
                generateKeyButton.setEnabled(true);
                secureBoardRadioButton.setSelected(true);
            } else { // its a public board
                privateKeyTextField.setEnabled(false);
                publicKeyTextField.setEnabled(false);
                generateKeyButton.setEnabled(false);
                publicBoardRadioButton.setSelected(true);
            }
        }
    }


    /**
     * Close window and save settings
     */
    private void ok() {

        if( node.isBoard() ) {
            // if board was secure before and now its public, ask user if ok to remove the keys
            if( publicBoardRadioButton.isSelected() && ((Board)node).isPublicBoard() == false ) {
                final int answer = MiscToolkit.showConfirmDialog(
                        this,
                        language.getString("BoardSettings.looseKeysWarningDialog.body"),
                        language.getString("BoardSettings.looseKeysWarningDialog.title"),
                        MiscToolkit.YES_NO_OPTION,
                        MiscToolkit.WARNING_MESSAGE);
                if( answer != MiscToolkit.YES_OPTION ) {
                    return;
                }
            }
            applySettingsToBoard((Board)node);
        } else if(node.isFolder()) {
            // apply settings to all boards in a folder
            applySettingsToFolder(node);
        }

        // finally update all involved boards before we close the dialog
        updateBoard(node); // board or folder

        exitState = true;
        dispose();
    }

    private void applySettingsToFolder(final AbstractNode b) {

        // process all childs recursiv
        if( b.isFolder() ) {
            for(int x=0; x < b.getChildCount(); x++) {
                final AbstractNode b2 = (AbstractNode)b.getChildAt(x);
                applySettingsToFolder(b2);
            }
            return;
        }

        if( !(b instanceof Board) ) {
            return;
        }

        final Board board = (Board)b;
        // apply set settings to the board, unset options are not changed
        if (overrideSettingsCheckBox.isSelected()) {
            board.setConfigured(true);

            board.setAutoUpdateEnabled(autoUpdateEnabled.isSelected());

            if( startDayToDownload_default.isSelected() || startDayToDownload_set.isSelected() ) {
                if (startDayToDownload_default.isSelected() == false) {
                    int check = new Integer(startDayToDownload_value.getText());
                    // the valid range is 1(today) to the max number of days to download
                    if( check < 1 )
                        check = 1;
                    else if( check > board.getMaxMessageDownload() )
                        check = board.getMaxMessageDownload();
                    board.setStartDaysBack(check);
                } else {
                    board.setStartDaysBack(null);
                }
            }
            
            if( maxMessageDisplay_default.isSelected() || maxMessageDisplay_set.isSelected() ) {
                if (maxMessageDisplay_default.isSelected() == false) {
                    board.setMaxMessageDays(new Integer(maxMessageDisplay_value.getText()));
                } else {
                    board.setMaxMessageDays(null);
                }
            }
            if( maxMessageDownload_default.isSelected() || maxMessageDownload_set.isSelected() ) {
                if (maxMessageDownload_default.isSelected() == false) {
                    board.setMaxMessageDownload(new Integer(maxMessageDownload_value.getText()));
                } else {
                    board.setMaxMessageDownload(null);
                }
            }
            if( hideUnsigned_default.isSelected() || hideUnsigned_true.isSelected() || hideUnsigned_false.isSelected() ) {
                if (hideUnsigned_default.isSelected() == false) {
                    board.setHideUnsigned(Boolean.valueOf(hideUnsigned_true.isSelected()));
                } else {
                    board.setHideUnsigned(null);
                }
            }
            if( hideBAD_default.isSelected() || hideBAD_true.isSelected() || hideBAD_false.isSelected() ) {
                if (hideBAD_default.isSelected() == false) {
                    board.setHideBAD(Boolean.valueOf(hideBAD_true.isSelected()));
                } else {
                    board.setHideBAD(null);
                }
            }
            if( hideNEUTRAL_default.isSelected() || hideNEUTRAL_true.isSelected() || hideNEUTRAL_false.isSelected() ) {
                if (hideNEUTRAL_default.isSelected() == false) {
                    board.setHideNEUTRAL(Boolean.valueOf(hideNEUTRAL_true.isSelected()));
                } else {
                    board.setHideNEUTRAL(null);
                }
            }
            if( hideGOOD_default.isSelected() || hideGOOD_true.isSelected() || hideGOOD_false.isSelected() ) {
                if (hideGOOD_default.isSelected() == false) {
                    board.setHideGOOD(Boolean.valueOf(hideGOOD_true.isSelected()));
                } else {
                    board.setHideGOOD(null);
                }
            }
            if( hideMessageCount_default.isSelected() || hideMessageCount_set.isSelected() ) {
                if (hideMessageCount_default.isSelected() == false) {
                    board.setHideMessageCount(new Integer(hideMessageCount_value.getText()));
                } else {
                    board.setHideMessageCount(null);
                }
            }
            if( storeSentMessages_default.isSelected() || storeSentMessages_true.isSelected() || storeSentMessages_false.isSelected() ) {
                if (storeSentMessages_default.isSelected() == false) {
                    board.setStoreSentMessages(Boolean.valueOf(storeSentMessages_true.isSelected()));
                } else {
                    board.setStoreSentMessages(null);
                }
            }
        } else {
            board.setConfigured(false);
        }
    }

    private void applySettingsToBoard(final Board board) {
        final String desc = descriptionTextArea.getText().trim();
        if( desc.length() > 0 ) {
            board.setDescription(desc);
        } else {
            board.setDescription(null);
        }

        if (secureBoardRadioButton.isSelected()) {
            final String privateKey = privateKeyTextField.getText();
            final String publicKey = publicKeyTextField.getText();
            if (publicKey.startsWith("SSK@")) {
                board.setPublicKey(publicKey);
            } else {
                board.setPublicKey(null);
            }
            if (privateKey.startsWith("SSK@")) {
                board.setPrivateKey(privateKey);
            } else {
                board.setPrivateKey(null);
            }
        } else {
            board.setPublicKey(null);
            board.setPrivateKey(null);
        }

        if (overrideSettingsCheckBox.isSelected()) {
            board.setConfigured(true);
            board.setAutoUpdateEnabled(autoUpdateEnabled.isSelected());
            
            if (startDayToDownload_default.isSelected() == false) {
                board.setStartDaysBack(new Integer(startDayToDownload_value.getText()));
            } else {
                board.setStartDaysBack(null);
            }
            
            if (maxMessageDisplay_default.isSelected() == false) {
                board.setMaxMessageDays(new Integer(maxMessageDisplay_value.getText()));
            } else {
                board.setMaxMessageDays(null);
            }
            if (maxMessageDownload_default.isSelected() == false) {
                board.setMaxMessageDownload(new Integer(maxMessageDownload_value.getText()));
            } else {
                board.setMaxMessageDownload(null);
            }
            if (hideUnsigned_default.isSelected() == false) {
                board.setHideUnsigned(Boolean.valueOf(hideUnsigned_true.isSelected()));
            } else {
                board.setHideUnsigned(null);
            }
            if (hideBAD_default.isSelected() == false) {
                board.setHideBAD(Boolean.valueOf(hideBAD_true.isSelected()));
            } else {
                board.setHideBAD(null);
            }
            if (hideNEUTRAL_default.isSelected() == false) {
                board.setHideNEUTRAL(Boolean.valueOf(hideNEUTRAL_true.isSelected()));
            } else {
                board.setHideNEUTRAL(null);
            }
            if (hideGOOD_default.isSelected() == false) {
                board.setHideGOOD(Boolean.valueOf(hideGOOD_true.isSelected()));
            } else {
                board.setHideGOOD(null);
            }
            if (hideMessageCount_default.isSelected() == false) {
                board.setHideMessageCount(new Integer(hideMessageCount_value.getText()));
            } else {
                board.setHideMessageCount(null);
            }
            if (storeSentMessages_default.isSelected() == false) {
                board.setStoreSentMessages(Boolean.valueOf(storeSentMessages_true.isSelected()));
            } else {
                board.setStoreSentMessages(null);
            }
        } else {
            board.setConfigured(false);
        }
    }

    private void updateBoard(final AbstractNode b) {
        if( b.isBoard() ) {
            MainFrame.getInstance().updateTofTree(b);
            // update the new msg. count for board
            TOF.getInstance().searchUnreadMessages((Board)b);

            if (b == MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getSelectedNode()) {
                // reload all messages if board is shown
                MainFrame.getInstance().tofTree_actionPerformed(null);
            }
        } else if( b.isFolder() ) {
            for(int x=0; x < b.getChildCount(); x++) {
                final AbstractNode b2 = (AbstractNode)b.getChildAt(x);
                updateBoard(b2);
            }
        }
    }

    /**
     * okButton Action Listener (OK)
     * @param e
     */
    private void okButton_actionPerformed(final ActionEvent e) {
        ok();
    }

    /* (non-Javadoc)
     * @see java.awt.Window#processWindowEvent(java.awt.event.WindowEvent)
     */
    @Override
    protected void processWindowEvent(final WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            dispose();
        }
        super.processWindowEvent(e);
    }

    /**
     * radioButton Action Listener (OK)
     * @param e
     */
    private void radioButton_actionPerformed(final ActionEvent e) {
        if (publicBoardRadioButton.isSelected()) {
            privateKeyTextField.setEnabled(false);
            publicKeyTextField.setEnabled(false);
            generateKeyButton.setEnabled(false);
        } else {
            privateKeyTextField.setEnabled(true);
            publicKeyTextField.setEnabled(true);
            generateKeyButton.setEnabled(true);
        }
    }

    private void refreshLanguage() {
        if( node.isFolder() ) {
            setTitle(language.getString("BoardSettings.title.folderSettings") + " '" + node.getName() + "'");
        } else if( node.isBoard() ) {
            setTitle(language.getString("BoardSettings.title.boardSettings") + " '" + node.getName() + "'");
        }

        publicBoardRadioButton.setText(language.getString("BoardSettings.label.publicBoard"));
        secureBoardRadioButton.setText(language.getString("BoardSettings.label.secureBoard"));
        okButton.setText(language.getString("Common.ok"));
        cancelButton.setText(language.getString("Common.cancel"));
        generateKeyButton.setText(language.getString("BoardSettings.button.generateNewKeypair"));

        overrideSettingsCheckBox.setText(language.getString("BoardSettings.label.overrideDefaultSettings"));
        final String useDefault = language.getString("BoardSettings.label.useDefault");
        final String yes = language.getString("BoardSettings.label.yes");
        final String no  = language.getString("BoardSettings.label.no");
        
        startDayToDownload_default.setText(useDefault); //SF_EDIT
        startDayToDownload_set.setText(language.getString("BoardSettings.label.setTo") + ":");
        startDayToDownload_default.setText(useDefault);
        startDayToDownload_set.setText(language.getString("BoardSettings.label.setTo") + ":");
        
        maxMessageDisplay_default.setText(useDefault);
        maxMessageDisplay_set.setText(language.getString("BoardSettings.label.setTo") + ":");
        maxMessageDownload_default.setText(useDefault);
        maxMessageDownload_set.setText(language.getString("BoardSettings.label.setTo") + ":");
        hideUnsigned_default.setText(useDefault);
        hideUnsigned_true.setText(yes);
        hideUnsigned_false.setText(no);
        hideBAD_default.setText(useDefault);
        hideBAD_true.setText(yes);
        hideBAD_false.setText(no);
        hideNEUTRAL_default.setText(useDefault);
        hideNEUTRAL_true.setText(yes);
        hideNEUTRAL_false.setText(no);
        hideGOOD_default.setText(useDefault);
        hideGOOD_true.setText(yes);
        hideGOOD_false.setText(no);
        hideMessageCount_default.setText(useDefault);
        hideMessageCount_set.setText(language.getString("BoardSettings.label.setTo") + ":");
        storeSentMessages_default.setText(useDefault);
        storeSentMessages_true.setText(yes);
        storeSentMessages_false.setText(no);
        autoUpdateEnabled.setText(language.getString("BoardSettings.label.enableAutomaticBoardUpdate"));

        publicKeyLabel.setText(language.getString("BoardSettings.label.publicKey") + " :");
        privateKeyLabel.setText(language.getString("BoardSettings.label.privateKey") + " :");
        maxMessageDisplayDaysLabel.setText(language.getString("BoardSettings.label.maximumMessageDisplay"));
        maxMessageDownloadDaysLabel.setText(language.getString("BoardSettings.label.maximumMessageDownload"));
        hideUnsignedMessagesLabel.setText(language.getString("BoardSettings.label.hideNONEMessages"));
        hideBADMessagesLabel.setText(language.getString("BoardSettings.label.hideBADMessages"));
        hideNEUTRALMessagesLabel.setText(language.getString("BoardSettings.label.hideNEUTRALMessages"));
        hideGOODMessagesLabel.setText(language.getString("BoardSettings.label.hideGOODMessages"));
        hideMessageCountLabel.setText(language.getString("BoardSettings.label.hideMessageCountDisplay"));
        storeSentMessagesLabel.setText(language.getString("BoardSettings.label.storeSentMessages"));
        
        startDayToDownloadLabel.setText(language.getString("BoardSettings.label.startDay"));
        
//        descriptionLabel.setText(language.getString("BoardSettings.label.description"));
    }

    public boolean runDialog() {
        setModal(true); // paranoia
        setVisible(true);
        return exitState;
    }

    private void setPanelEnabled(final JPanel panel, final boolean enabled) {
        final int componentCount = panel.getComponentCount();
        for (int x = 0; x < componentCount; x++) {
            final Component c = panel.getComponent(x);
            if (c != overrideSettingsCheckBox) {
                c.setEnabled(enabled);
            }
        }
    }
}
