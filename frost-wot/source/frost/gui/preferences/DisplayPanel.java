/*
  DisplayPanel.java / Frost
  Copyright (C) 2003  Frost Project <jtcfrost.sourceforge.net>

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
package frost.gui.preferences;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.border.*;

import frost.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;

/**
 * Display Panel. Contains appearance options
 */
@SuppressWarnings("serial")
class DisplayPanel extends JPanel {

    public class Listener implements ActionListener {
        public void actionPerformed(final ActionEvent e) {
            if( e.getSource() == msgNormalColorButton ) {
                pickColor(msgNormalColorButton);
            } else if( e.getSource() == msgPrivColorButton ) {
                pickColor(msgPrivColorButton);
            } else if( e.getSource() == msgPrivEditorColorButton ) {
                pickColor(msgPrivEditorColorButton);
            } else if( e.getSource() == msgWithAttachmentsColorButton ) {
                pickColor(msgWithAttachmentsColorButton);
            } else if( e.getSource() == msgUnsignedColorButton ) {
                pickColor(msgUnsignedColorButton);
            } else if( e.getSource() == messageBodyButton ) {
                messageBodyButtonPressed();
            } else if( e.getSource() == messageListButton ) {
                messageListButtonPressed();
            } else if( e.getSource() == fileListButton ) {
                fileListButtonPressed();
            }
        }
    }

    private JDialog owner = null;
    private SettingsClass settings = null;
    private Language language = null;

    private final Listener listener = new Listener();

    private final JLabel fontsLabel = new JLabel();

    private final JCheckBox saveSortStatesCheckBox = new JCheckBox();
    private final JCheckBox showColoredRowsCheckBox = new JCheckBox();

    private final JCheckBox confirmMarkAllMsgsReadCheckBox = new JCheckBox();

    private final JLabel messageBodyLabel = new JLabel();
    private final JLabel fileListLabel = new JLabel();
    private final JLabel messageListLabel = new JLabel();

    private final JButton fileListButton = new JButton();
    private final JButton messageListButton = new JButton();
    private final JButton messageBodyButton = new JButton();

    private final JLabel selectedFileListFontLabel = new JLabel();
    private final JLabel selectedMessageBodyFontLabel = new JLabel();
    private final JLabel selectedMessageListFontLabel = new JLabel();

    private final JLabel colorsLabel = new JLabel();
    private JPanel colorPanel = null;
    private Color msgNormalColor = null;
    private final JButton msgNormalColorButton = new JButton();
    private final JLabel msgNormalColorTextLabel = new JLabel();
    private final JLabel msgNormalColorLabel = new JLabel();
    private Color msgPrivColor = null;
    private final JButton msgPrivColorButton = new JButton();
    private final JLabel msgPrivColorTextLabel = new JLabel();
    private final JLabel msgPrivColorLabel = new JLabel();
    private Color msgPrivEditorColor = null;
    private final JButton msgPrivEditorColorButton = new JButton();
    private final JLabel msgPrivEditorColorTextLabel = new JLabel();
    private final JLabel msgPrivEditorColorLabel = new JLabel();
    private Color msgWithAttachmentsColor = null;
    private final JButton msgWithAttachmentsColorButton = new JButton();
    private final JLabel msgWithAttachmentsColorTextLabel = new JLabel();
    private final JLabel msgWithAttachmentsColorLabel = new JLabel();
    private Color msgUnsignedColor = null;
    private final JButton msgUnsignedColorButton = new JButton();
    private final JLabel msgUnsignedColorTextLabel = new JLabel();
    private final JLabel msgUnsignedColorLabel = new JLabel();

    private Font selectedBodyFont = null;
    private Font selectedFileListFont = null;
    private Font selectedMessageListFont = null;

    private JTranslatableComboBox treeExpansionIconComboBox = null;
    private final JLabel treeExpansionIconLabel = new JLabel();

    /**
     * @param owner the JDialog that will be used as owner of any dialog that is popped up from this panel
     * @param settings the SettingsClass instance that will be used to get and store the settings of the panel
     */
    protected DisplayPanel(final JDialog owner, final SettingsClass settings) {
        super();

        this.owner = owner;
        this.language = Language.getInstance();
        this.settings = settings;

        initialize();
        loadSettings();
    }

    public void cancel() {
    }

    private void fileListButtonPressed() {
        final FontChooser fontChooser = new FontChooser(owner, language);
        fontChooser.setModal(true);
        fontChooser.setSelectedFont(selectedFileListFont);
        fontChooser.setVisible(true);
        final Font selectedFontTemp = fontChooser.getSelectedFont();
        if (selectedFontTemp != null) {
            selectedFileListFont = selectedFontTemp;
            selectedFileListFontLabel.setText(getFontLabel(selectedFileListFont));
        }
    }

    private String getFontLabel(final Font font) {
        if (font == null) {
            return "";
        } else {
            final StringBuilder returnValue = new StringBuilder();
            returnValue.append(font.getFamily());
            if (font.isBold()) {
                returnValue.append(" " + language.getString("Options.display.fontChooser.bold"));
            }
            if (font.isItalic()) {
                returnValue.append(" " + language.getString("Options.display.fontChooser.italic"));
            }
            returnValue.append(", " + font.getSize());
            return returnValue.toString();
        }
    }

    private JPanel getFontsPanel() {
        final JPanel fontsPanel = new JPanel(new GridBagLayout());
        fontsPanel.setBorder(new EmptyBorder(5, 20, 5, 5));
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.NORTHWEST;
        final Insets inset1515 = new Insets(1, 5, 1, 5);
        final Insets inset1519 = new Insets(1, 5, 1, 9);

        constraints.insets = inset1515;
        constraints.gridx = 0;
        constraints.gridy = 0;
        fontsPanel.add(messageBodyLabel, constraints);
        constraints.insets = inset1519;
        constraints.gridx = 1;
        constraints.gridy = 0;
        fontsPanel.add(messageBodyButton, constraints);
        constraints.insets = inset1515;
        constraints.gridx = 2;
        constraints.gridy = 0;
        fontsPanel.add(selectedMessageBodyFontLabel, constraints);

        constraints.insets = inset1515;
        constraints.gridx = 0;
        constraints.gridy = 1;
        fontsPanel.add(messageListLabel, constraints);
        constraints.insets = inset1519;
        constraints.gridx = 1;
        constraints.gridy = 1;
        fontsPanel.add(messageListButton, constraints);
        constraints.insets = inset1515;
        constraints.gridx = 2;
        constraints.gridy = 1;
        fontsPanel.add(selectedMessageListFontLabel, constraints);

        constraints.insets = inset1515;
        constraints.gridx = 0;
        constraints.gridy = 2;
        fontsPanel.add(fileListLabel, constraints);
        constraints.insets = inset1519;
        constraints.gridx = 1;
        constraints.gridy = 2;
        fontsPanel.add(fileListButton, constraints);
        constraints.insets = inset1515;
        constraints.gridx = 2;
        constraints.gridy = 2;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.weightx = 1;
        fontsPanel.add(selectedFileListFontLabel, constraints);

        return fontsPanel;
    }

    private JPanel getColorPanel() {
        if( colorPanel == null ) {
            colorPanel = new JPanel(new GridBagLayout());
            colorPanel.setBorder(new EmptyBorder(5, 30, 5, 5));
            final GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(0, 5, 5, 5);
            constraints.weighty = 1;
            constraints.weightx = 1;
            constraints.anchor = GridBagConstraints.NORTHWEST;
            constraints.gridy = 0;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0;
            constraints.weightx = 0.5;
            colorPanel.add(msgNormalColorTextLabel, constraints);
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 1;
            constraints.weightx = 0.2;
            colorPanel.add(msgNormalColorLabel, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridx = 2;
            constraints.weightx = 0.5;
            colorPanel.add(msgNormalColorButton, constraints);

            constraints.gridy++;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0;
            constraints.weightx = 0.5;
            colorPanel.add(msgPrivColorTextLabel, constraints);
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 1;
            constraints.weightx = 0.2;
            colorPanel.add(msgPrivColorLabel, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridx = 2;
            constraints.weightx = 0.5;
            colorPanel.add(msgPrivColorButton, constraints);

            constraints.gridy++;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0;
            constraints.weightx = 0.5;
            colorPanel.add(msgPrivEditorColorTextLabel, constraints);
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 1;
            constraints.weightx = 0.2;
            colorPanel.add(msgPrivEditorColorLabel, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridx = 2;
            constraints.weightx = 0.5;
            colorPanel.add(msgPrivEditorColorButton, constraints);

            constraints.gridy++;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0;
            constraints.weightx = 0.5;
            colorPanel.add(msgWithAttachmentsColorTextLabel, constraints);
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 1;
            constraints.weightx = 0.2;
            colorPanel.add(msgWithAttachmentsColorLabel, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridx = 2;
            constraints.weightx = 0.5;
            colorPanel.add(msgWithAttachmentsColorButton, constraints);

            constraints.gridy++;

            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridx = 0;
            constraints.weightx = 0.5;
            colorPanel.add(msgUnsignedColorTextLabel, constraints);
            constraints.fill = GridBagConstraints.VERTICAL;
            constraints.gridx = 1;
            constraints.weightx = 0.2;
            colorPanel.add(msgUnsignedColorLabel, constraints);
            constraints.fill = GridBagConstraints.NONE;
            constraints.gridx = 2;
            constraints.weightx = 0.5;
            colorPanel.add(msgUnsignedColorButton, constraints);

            msgNormalColorLabel.setFont(msgNormalColorLabel.getFont().deriveFont(Font.BOLD));
            msgPrivColorLabel.setFont(msgPrivColorLabel.getFont().deriveFont(Font.BOLD));
            msgPrivEditorColorLabel.setFont(msgPrivEditorColorLabel.getFont().deriveFont(Font.BOLD));
            msgWithAttachmentsColorLabel.setFont(msgWithAttachmentsColorLabel.getFont().deriveFont(Font.BOLD));
            msgUnsignedColorLabel.setFont(msgUnsignedColorLabel.getFont().deriveFont(Font.BOLD));
            msgNormalColorLabel.setBackground(Color.WHITE);
            msgPrivColorLabel.setBackground(Color.WHITE);
            msgPrivEditorColorLabel.setBackground(Color.WHITE);
            msgWithAttachmentsColorLabel.setBackground(Color.WHITE);
            msgUnsignedColorLabel.setBackground(Color.WHITE);
            msgNormalColorLabel.setOpaque(true);
            msgPrivColorLabel.setOpaque(true);
            msgPrivEditorColorLabel.setOpaque(true);
            msgWithAttachmentsColorLabel.setOpaque(true);
            msgUnsignedColorLabel.setOpaque(true);
            msgNormalColorLabel.setBorder(new BevelBorder(BevelBorder.LOWERED));
            msgPrivColorLabel.setBorder(new BevelBorder(BevelBorder.LOWERED));
            msgPrivEditorColorLabel.setBorder(new BevelBorder(BevelBorder.LOWERED));
            msgWithAttachmentsColorLabel.setBorder(new BevelBorder(BevelBorder.LOWERED));
            msgUnsignedColorLabel.setBorder(new BevelBorder(BevelBorder.LOWERED));
            msgNormalColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            msgPrivColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            msgPrivEditorColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            msgWithAttachmentsColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
            msgUnsignedColorLabel.setHorizontalAlignment(SwingConstants.CENTER);
        }
        return colorPanel;
    }

    /**
     * Initialize the class.
     */
    private void initialize() {
        setName("DisplayPanel");
        setLayout(new GridBagLayout());
        refreshLanguage();

        //Adds all of the components
        final GridBagConstraints constraints = new GridBagConstraints();

        final Insets inset5511 = new Insets(5, 5, 1, 1);
        final Insets insets2 = new Insets(15,5,1,1);

        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.insets = inset5511;
        add(fontsLabel, constraints);
        constraints.gridy++;
        add(getFontsPanel(), constraints);

        constraints.anchor = GridBagConstraints.WEST;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridy++;
        constraints.insets = inset5511;
        add(colorsLabel, constraints);
        constraints.gridy++;
        add(getColorPanel(), constraints);

        constraints.insets = insets2;
        constraints.gridy++;
        constraints.gridx = 0;
        add(treeExpansionIconLabel, constraints);
        constraints.gridx = 1;
        final String[] treeExpansionIconComboBoxKeys =
            { "Options.display.treeExpansionIcon.style0", "Options.display.treeExpansionIcon.style1",
                "Options.display.treeExpansionIcon.style2", "Options.display.treeExpansionIcon.style3" };
        treeExpansionIconComboBox = new JTranslatableComboBox(language, treeExpansionIconComboBoxKeys);
        add(treeExpansionIconComboBox, constraints);
        constraints.gridx = 0;

        constraints.insets = inset5511;
        constraints.gridy++;
        add(showColoredRowsCheckBox, constraints);

        constraints.insets = inset5511;
        constraints.gridy++;
        add(saveSortStatesCheckBox, constraints);

        constraints.insets = inset5511;
        constraints.gridy++;
        add(confirmMarkAllMsgsReadCheckBox, constraints);

        // glue
        constraints.gridy++;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        add(new JLabel(""), constraints);

        // add listeners
        messageBodyButton.addActionListener(listener);
        messageListButton.addActionListener(listener);
        fileListButton.addActionListener(listener);
        msgNormalColorButton.addActionListener(listener);
        msgPrivColorButton.addActionListener(listener);
        msgPrivEditorColorButton.addActionListener(listener);
        msgWithAttachmentsColorButton.addActionListener(listener);
        msgUnsignedColorButton.addActionListener(listener);
    }

    /**
     * Load the settings of this panel
     */
    private void loadSettings() {
        String fontName = settings.getValue(SettingsClass.MESSAGE_BODY_FONT_NAME);
        int fontSize = settings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_SIZE);
        int fontStyle = settings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_STYLE);
        selectedBodyFont = new Font(fontName, fontStyle, fontSize);
        selectedMessageBodyFontLabel.setText(getFontLabel(selectedBodyFont));

        fontName = settings.getValue(SettingsClass.MESSAGE_LIST_FONT_NAME);
        fontSize = settings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_SIZE);
        fontStyle = settings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_STYLE);
        selectedMessageListFont = new Font(fontName, fontStyle, fontSize);
        selectedMessageListFontLabel.setText(getFontLabel(selectedMessageListFont));

        fontName = settings.getValue(SettingsClass.FILE_LIST_FONT_NAME);
        fontSize = settings.getIntValue(SettingsClass.FILE_LIST_FONT_SIZE);
        fontStyle = settings.getIntValue(SettingsClass.FILE_LIST_FONT_STYLE);
        selectedFileListFont = new Font(fontName, fontStyle, fontSize);
        selectedFileListFontLabel.setText(getFontLabel(selectedFileListFont));

        saveSortStatesCheckBox.setSelected(settings.getBoolValue(SettingsClass.SAVE_SORT_STATES));
        showColoredRowsCheckBox.setSelected(settings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS));
        confirmMarkAllMsgsReadCheckBox.setSelected(settings.getBoolValue(SettingsClass.CONFIRM_MARK_ALL_MSGS_READ));

        treeExpansionIconComboBox.setSelectedKey(settings.getDefaultValue(SettingsClass.TREE_EXPANSION_ICON)); // set to default
        treeExpansionIconComboBox.setSelectedKey(settings.getValue(SettingsClass.TREE_EXPANSION_ICON)); // set to user-value if one exists

        // load the color values
        msgNormalColor = settings.getColorValue(SettingsClass.COLORS_MESSAGE_NORMALMSG);
        msgPrivColor = settings.getColorValue(SettingsClass.COLORS_MESSAGE_PRIVMSG);
        msgPrivEditorColor = settings.getColorValue(SettingsClass.COLORS_MESSAGE_PRIVEDITOR);
        msgWithAttachmentsColor = settings.getColorValue(SettingsClass.COLORS_MESSAGE_WITHATTACHMENTS);
        msgUnsignedColor = settings.getColorValue(SettingsClass.COLORS_MESSAGE_UNSIGNEDMSG);
        msgNormalColorLabel.setForeground(msgNormalColor);
        msgPrivColorLabel.setForeground(msgPrivColor);
        msgPrivEditorColorLabel.setForeground(msgPrivEditorColor);
        msgWithAttachmentsColorLabel.setForeground(msgWithAttachmentsColor);
        msgUnsignedColorLabel.setForeground(msgUnsignedColor);
    }

    private void messageBodyButtonPressed() {
        final FontChooser fontChooser = new FontChooser(owner, language);
        fontChooser.setModal(true);
        fontChooser.setSelectedFont(selectedBodyFont);
        fontChooser.setVisible(true);
        final Font selectedFontTemp = fontChooser.getSelectedFont();
        if (selectedFontTemp != null) {
            selectedBodyFont = selectedFontTemp;
            selectedMessageBodyFontLabel.setText(getFontLabel(selectedBodyFont));
        }
    }

    private void messageListButtonPressed() {
        final FontChooser fontChooser = new FontChooser(owner, language);
        fontChooser.setModal(true);
        fontChooser.setSelectedFont(selectedMessageListFont);
        fontChooser.setVisible(true);
        final Font selectedFontTemp = fontChooser.getSelectedFont();
        if (selectedFontTemp != null) {
            selectedMessageListFont = selectedFontTemp;
            selectedMessageListFontLabel.setText(getFontLabel(selectedMessageListFont));
        }
    }

    public void ok() {
        saveSettings();
    }

    private void pickColor(final JButton sourceButton) {
        Color oldColor = null;
        String dialogTitle = null;
        if( sourceButton == msgNormalColorButton ) {
            oldColor = msgNormalColor;
            dialogTitle = language.getString("Options.display.colorChooserDialog.title.msgNormalColor");
        } else if( sourceButton == msgPrivColorButton ) {
            oldColor = msgPrivColor;
            dialogTitle = language.getString("Options.display.colorChooserDialog.title.msgPrivColor");
        } else if( sourceButton == msgPrivEditorColorButton ) {
            oldColor = msgPrivEditorColor;
            dialogTitle = language.getString("Options.display.colorChooserDialog.title.msgPrivEditorColor");
        } else if( sourceButton == msgWithAttachmentsColorButton ) {
            oldColor = msgWithAttachmentsColor;
            dialogTitle = language.getString("Options.display.colorChooserDialog.title.msgWithAttachmentsColor");
        } else if( sourceButton == msgUnsignedColorButton ) {
            oldColor = msgUnsignedColor;
            dialogTitle = language.getString("Options.display.colorChooserDialog.title.msgUnsignedColor");
        } else {
            return; // invalid button
        }

        final Color newCol = JColorChooser.showDialog(
                getTopLevelAncestor(),
                dialogTitle,
                oldColor);
        if( newCol != null ) { // null means they aborted
            if( sourceButton == msgNormalColorButton ) {
                msgNormalColor = newCol;
                msgNormalColorLabel.setForeground(msgNormalColor);
            } else if( sourceButton == msgPrivColorButton ) {
                msgPrivColor = newCol;
                msgPrivColorLabel.setForeground(msgPrivColor);
            } else if( sourceButton == msgPrivEditorColorButton ) {
                msgPrivEditorColor = newCol;
                msgPrivEditorColorLabel.setForeground(msgPrivEditorColor);
            } else if( sourceButton == msgWithAttachmentsColorButton ) {
                msgWithAttachmentsColor = newCol;
                msgWithAttachmentsColorLabel.setForeground(msgWithAttachmentsColor);
            } else if( sourceButton == msgUnsignedColorButton ) {
                msgUnsignedColor = newCol;
                msgUnsignedColorLabel.setForeground(msgUnsignedColor);
            }
        }
    }

    private void refreshLanguage() {
        final String choose = language.getString("Options.display.choose");
        fontsLabel.setText(language.getString("Options.display.fonts"));
        messageBodyLabel.setText(language.getString("Options.display.messageBody"));
        messageBodyButton.setText(choose);
        selectedMessageBodyFontLabel.setText(getFontLabel(selectedBodyFont));
        messageListLabel.setText(language.getString("Options.display.messageList"));
        messageListButton.setText(choose);
        selectedMessageListFontLabel.setText(getFontLabel(selectedMessageListFont));
        fileListLabel.setText(language.getString("Options.display.fileList"));
        fileListButton.setText(choose);
        selectedFileListFontLabel.setText(getFontLabel(selectedFileListFont));
        saveSortStatesCheckBox.setText(language.getString("Options.display.saveSortStates"));
        showColoredRowsCheckBox.setText(language.getString("Options.display.showColoredRows"));
        confirmMarkAllMsgsReadCheckBox.setText(language.getString("Options.display.confirmMarkAllMsgsRead"));
        treeExpansionIconLabel.setText(language.getString("Options.display.treeExpansionIcon"));

        colorsLabel.setText(language.getString("Options.display.colors"));
        final String color = language.getString("Options.news.3.color");
        msgNormalColorTextLabel.setText(language.getString("Options.display.msgNormalColor"));
        msgNormalColorLabel.setText("    " + color + "    ");
        msgNormalColorButton.setText(choose);
        msgPrivColorTextLabel.setText(language.getString("Options.display.msgPrivColor"));
        msgPrivColorLabel.setText("    " + color + "    ");
        msgPrivColorButton.setText(choose);
        msgPrivEditorColorTextLabel.setText(language.getString("Options.display.msgPrivEditorColor"));
        msgPrivEditorColorLabel.setText("    " + color + "    ");
        msgPrivEditorColorButton.setText(choose);
        msgWithAttachmentsColorTextLabel.setText(language.getString("Options.display.msgWithAttachmentsColor"));
        msgWithAttachmentsColorLabel.setText("    " + color + "    ");
        msgWithAttachmentsColorButton.setText(choose);
        msgUnsignedColorTextLabel.setText(language.getString("Options.display.msgUnsignedColor"));
        msgUnsignedColorLabel.setText("    " + color + "    ");
        msgUnsignedColorButton.setText(choose);
    }

    /**
     * Save the settings of this panel
     */
    private void saveSettings() {
        if( selectedBodyFont != null ) {
            settings.setValue(SettingsClass.MESSAGE_BODY_FONT_NAME, selectedBodyFont.getFamily());
            settings.setValue(SettingsClass.MESSAGE_BODY_FONT_STYLE, selectedBodyFont.getStyle());
            settings.setValue(SettingsClass.MESSAGE_BODY_FONT_SIZE, selectedBodyFont.getSize());
        }
        if( selectedMessageListFont != null ) {
            settings.setValue(SettingsClass.MESSAGE_LIST_FONT_NAME, selectedMessageListFont.getFamily());
            settings.setValue(SettingsClass.MESSAGE_LIST_FONT_STYLE, selectedMessageListFont.getStyle());
            settings.setValue(SettingsClass.MESSAGE_LIST_FONT_SIZE, selectedMessageListFont.getSize());
        }
        if( selectedFileListFont != null ) {
            settings.setValue(SettingsClass.FILE_LIST_FONT_NAME, selectedFileListFont.getFamily());
            settings.setValue(SettingsClass.FILE_LIST_FONT_STYLE, selectedFileListFont.getStyle());
            settings.setValue(SettingsClass.FILE_LIST_FONT_SIZE, selectedFileListFont.getSize());
        }
        settings.setValue(SettingsClass.SAVE_SORT_STATES, saveSortStatesCheckBox.isSelected());
        settings.setValue(SettingsClass.SHOW_COLORED_ROWS, showColoredRowsCheckBox.isSelected());
        settings.setValue(SettingsClass.CONFIRM_MARK_ALL_MSGS_READ, confirmMarkAllMsgsReadCheckBox.isSelected());
        settings.setValue(SettingsClass.TREE_EXPANSION_ICON, treeExpansionIconComboBox.getSelectedKey());
        settings.setObjectValue(SettingsClass.COLORS_MESSAGE_NORMALMSG, msgNormalColor);
        settings.setObjectValue(SettingsClass.COLORS_MESSAGE_PRIVMSG, msgPrivColor);
        settings.setObjectValue(SettingsClass.COLORS_MESSAGE_PRIVEDITOR, msgPrivEditorColor);
        settings.setObjectValue(SettingsClass.COLORS_MESSAGE_WITHATTACHMENTS, msgWithAttachmentsColor);
        settings.setObjectValue(SettingsClass.COLORS_MESSAGE_UNSIGNEDMSG, msgUnsignedColor);
    }
}
