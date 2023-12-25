/*
 KeyConversionUtility.java / Frost-Next
 Copyright (C) 2015  "The Kitty@++U6QppAbIb1UBjFBRtcIBZg6NU"

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

package frost.util.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.LinkedHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

import frost.Core;
import frost.SettingsClass;
import frost.fileTransfer.KeyParser;
import frost.util.Mixed;
import frost.util.gui.CompoundUndoManager;
import frost.util.gui.TextComponentClipboardMenu;
import frost.util.gui.textpane.AntialiasedTextPane;
import frost.util.gui.textpane.WrapEditorKit;
import frost.util.gui.translation.JTranslatableComboBox;
import frost.util.gui.translation.Language;

@SuppressWarnings("serial")
public class KeyConversionUtility extends JFrame
{
    public static final String FORMAT_CLEAN = "KeyConversionUtility.format.clean";
    public static final String FORMAT_HTML = "KeyConversionUtility.format.html";
    public static final String FORMAT_SITE_A = "KeyConversionUtility.format.site_a";
    public static final String FORMAT_SITE_PRE = "KeyConversionUtility.format.site_pre";
    public static final String FORMAT_DEFAULT = "KeyConversionUtility.format.clean";

    private final Language fLanguage;

    private AntialiasedTextPane fInputTextPane;
    private CompoundUndoManager fInputUndoManager;
    private AntialiasedTextPane fOutputTextPane;
    private CompoundUndoManager fOutputUndoManager;
    private JLabel fBottomLabel;
    private JButton fProcessButton;
    private JLabel fFormatLabel;
    private JTranslatableComboBox fFormatComboBox;
    private JCheckBox fSpaceCheckBox;
    private JButton fCloseButton;

    private final Frame fParentFrame;

    public KeyConversionUtility(
            final JFrame aParentFrame)
    { 
        fParentFrame = aParentFrame;

        fLanguage = Language.getInstance();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        initGUI();
        loadSettings();
    }

    private void initGUI()
    {
        try {
            setTitle(fLanguage.getString("KeyConversionUtility.title"));

            int width = (int) (fParentFrame.getWidth() * 0.75);
            int height = (int) (fParentFrame.getHeight() * 0.75);

            if( width < 1000 ) {
                width = 1000;
            }

            if( height < 720 ) {
                height = 720;
            }

            setSize(width, height);
            this.setResizable(true);

            setIconImage(MiscToolkit.loadImageIcon("/data/toolbar/key-utility.png").getImage());

            // two text areas that each take a portion of the height of a horizontally split pane,
            // and they sit in scrollpanes so that they can be scrolled if the text is too long
            // NOTE: the input text pane is added first to the window, and is thus given input
            // focus when the window is opened.
            fInputTextPane = new AntialiasedTextPane();
            fInputTextPane.setToolTipText(fLanguage.getString("KeyConversionUtility.tooltip.inputHelp"));
            fInputTextPane.setEditorKit(new WrapEditorKit()); // forces line wrapping
            fInputTextPane.setEditable(true);
            fInputTextPane.setDoubleBuffered(true);
            fInputTextPane.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
            fInputTextPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            fInputTextPane.setAntiAliasEnabled(Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BODY_ANTIALIAS));
            new TextComponentClipboardMenu(fInputTextPane, fLanguage);
            // let's make the usage instructions tooltip super clear to newbies by also setting it as the default text value
            fInputTextPane.setText(fLanguage.getString("KeyConversionUtility.tooltip.inputHelp")+"\n\n");
            final JScrollPane scrollPaneTop = new JScrollPane(fInputTextPane);
            final JPanel panelTop = new JPanel(new BorderLayout());
            final JLabel topLabel = new JLabel(fLanguage.getString("KeyConversionUtility.label.input"));
            panelTop.add( topLabel, BorderLayout.NORTH );
            panelTop.add( scrollPaneTop, BorderLayout.CENTER );

            fOutputTextPane = new AntialiasedTextPane();
            fOutputTextPane.setEditable(true);
            // NOTE:XXX: the output pane looks clearer if we don't wrap the generated links; user can simply scroll horizontally
//            fOutputTextPane.setEditorKit(new WrapEditorKit()); // forces line wrapping
            fOutputTextPane.setDoubleBuffered(true);
            fOutputTextPane.setBorder(BorderFactory.createEmptyBorder(2,2,2,2));
            fOutputTextPane.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            fOutputTextPane.setAntiAliasEnabled(Core.frostSettings.getBoolValue(SettingsClass.MESSAGE_BODY_ANTIALIAS));
            new TextComponentClipboardMenu(fOutputTextPane, fLanguage);
                /* TRICK:XXX:
                 * Putting a JTextPane inside a JPanel completely prevents it from wrapping lines.
                 * This works because a JPanel doesn't implement the Scrollable interface, so
                 * the JScrollPane will display the JPanel at its *full* preferred width, which
                 * in turn equals the preferred (non-wrapped) width of the JTextPane.
                 * The scrollpane itself then simply adds horizontal/vertical scrollbars as needed.
                 */
                final JPanel noWrapPanel = new JPanel(new BorderLayout());
                noWrapPanel.add(fOutputTextPane);
            final JScrollPane scrollPaneBottom = new JScrollPane(noWrapPanel);
            final JPanel panelBottom = new JPanel(new BorderLayout());
            fBottomLabel = new JLabel();
            setBottomLabelCount(0);
            panelBottom.add( fBottomLabel, BorderLayout.NORTH );
            panelBottom.add( scrollPaneBottom, BorderLayout.CENTER );

            final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    panelTop, panelBottom);
            splitPane.setResizeWeight(0.2f); // 20% of the panel height for the input area, 80% for the output area
            splitPane.setOneTouchExpandable(true); // enables one-click expansion of top/bottom pane if L&F supports it

            // attach the undo/redo handlers to the input and output areas
            // NOTE: this nicely allows them to undo automatic conversion results
            fInputUndoManager = new CompoundUndoManager(fInputTextPane);
            fInputTextPane.getActionMap().put("Undo", fInputUndoManager.getUndoAction());
            fInputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK, true), "Undo"); // ctrl + z
            fInputTextPane.getActionMap().put("Redo", fInputUndoManager.getRedoAction());
            fInputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "Redo"); // ctrl + shift + z
            fInputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK, true), "Redo"); // ctrl + y
            fOutputUndoManager = new CompoundUndoManager(fOutputTextPane);
            fOutputTextPane.getActionMap().put("Undo", fOutputUndoManager.getUndoAction());
            fOutputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK, true), "Undo"); // ctrl + z
            fOutputTextPane.getActionMap().put("Redo", fOutputUndoManager.getRedoAction());
            fOutputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "Redo"); // ctrl + shift + z
            fOutputTextPane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK, true), "Redo"); // ctrl + y

            // "Convert" button
            fProcessButton = new JButton(fLanguage.getString("KeyConversionUtility.button.process"));
            fProcessButton.addActionListener( new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    processButton_actionPerformed(e);
                }
            });

            // "Format" label and dropdown
            fFormatLabel = new JLabel(fLanguage.getString("KeyConversionUtility.label.format"));
            final String[] formatComboBoxKeys =
                { FORMAT_CLEAN, FORMAT_HTML, FORMAT_SITE_A, FORMAT_SITE_PRE };
            fFormatComboBox = new JTranslatableComboBox(fLanguage, formatComboBoxKeys);
            fFormatComboBox.setMaximumSize( fFormatComboBox.getPreferredSize() ); // element size = widest text in dropdown

            // "Empty lines between keys" checkbox
            fSpaceCheckBox = new JCheckBox();
            fSpaceCheckBox.setOpaque(false);
            fSpaceCheckBox.setText(fLanguage.getString("KeyConversionUtility.label.addSpace"));

            // "Close" button
            fCloseButton = new JButton(fLanguage.getString("KeyConversionUtility.button.close"));
            fCloseButton.addActionListener( new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    // save the settings (since our "window closing" event won't fire when disposing manually)
                    saveSettings();

                    // close and dispose the frame
                    KeyConversionUtility.this.dispose();
                }
            });

            // button row
            final JPanel buttonsPanel = new JPanel(new BorderLayout());
            buttonsPanel.setLayout( new BoxLayout( buttonsPanel, BoxLayout.X_AXIS ));

            buttonsPanel.add( fProcessButton );
            buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
            buttonsPanel.add( fFormatLabel );
            buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
            buttonsPanel.add( fFormatComboBox );
            buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
            buttonsPanel.add( fSpaceCheckBox );

            buttonsPanel.add( Box.createHorizontalGlue() );

            buttonsPanel.add(Box.createRigidArea(new Dimension(20,3)));
            buttonsPanel.add( fCloseButton );
            buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));

            // main panel
            final JPanel mainPanel = new JPanel(new BorderLayout());
            mainPanel.add( splitPane, BorderLayout.CENTER );
            mainPanel.add( buttonsPanel, BorderLayout.SOUTH );
            mainPanel.setBorder(BorderFactory.createEmptyBorder(5,7,7,7));

            this.getContentPane().setLayout(new BorderLayout());
            this.getContentPane().add(mainPanel, null);

            // when the user closes the dialog manually, we need to save their format settings
            // NOTE: the "Close" button is handled separately (above)
            this.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(final WindowEvent e) {
                    saveSettings();
                }
            });
        } catch( final Exception e ) {
            e.printStackTrace();
        }
    }

    public void startDialog()
    {
        setLocationRelativeTo(fParentFrame);
        setVisible(true); // blocking!
    }

    /**
     * Load the settings of this panel
     */
    private void loadSettings()
    {
        fSpaceCheckBox.setSelected(Core.frostSettings.getBoolValue(SettingsClass.KEYCONVERSIONUTILITY_ADDSPACE));
        fFormatComboBox.setSelectedKey(Core.frostSettings.getDefaultValue(SettingsClass.KEYCONVERSIONUTILITY_FORMAT)); // set to default
        fFormatComboBox.setSelectedKey(Core.frostSettings.getValue(SettingsClass.KEYCONVERSIONUTILITY_FORMAT)); // set to user-value if one exists
    }

    /**
     * Save the settings of this panel
     */
    private void saveSettings()
    {
        Core.frostSettings.setValue(SettingsClass.KEYCONVERSIONUTILITY_ADDSPACE, fSpaceCheckBox.isSelected());
        Core.frostSettings.setValue(SettingsClass.KEYCONVERSIONUTILITY_FORMAT, fFormatComboBox.getSelectedKey());
    }

    private void setBottomLabelCount(
            final int keyCount)
    {
        if( fBottomLabel == null ) { return; }
        fBottomLabel.setText(fLanguage.formatMessage("KeyConversionUtility.label.output", keyCount));
    }

    private void processButton_actionPerformed(
            final ActionEvent ev)
    {
        final Map<String,String> foundKeys = new LinkedHashMap<String,String>();

        // split the user's input by newlines
        final String inputText = fInputTextPane.getText();
        final String[] inputLines = inputText.split("\n");
        if( inputLines == null || inputLines.length == 0 ) {
            // this will never happen. even a single empty line (text length of 0) would produce a single,
            // empty array element. but let's just abort in case it happens in an alternate universe.
            return;
        }

        // analyze all provided lines of text
        final KeyParser.ParseResult result = new KeyParser.ParseResult();
        for( final String line : inputLines ) {
            // attempt to extract a key from the current line
            // NOTE: false = do not include freesite links
            // NOTE: all keys are URL-decoded, so a "%20" in the input would be a " " in the output
            KeyParser.parseKeyFromLine(line, false, result);

            // if this line contained a valid key, add it to the list of discovered keys
            // NOTE: this linked map maintains insertion order and never inserts duplicates
            if( result.key != null ) {
                foundKeys.put(result.key, result.fileName);
            }
        }

        // determine what conversion settings to use (convert to int/bool for faster lookup during loop)
        // NOTE: all input keys are forced into the target format, even if they were of mixed formats
        // originally; i.e. "example%20file.jpg" and "example file.jpg" are detected as the same file
        // at the input parsing stage (the only difference is the name encoding). and the final keys
        // will always be output as the desired format, regardless of their original encoding format.
        final boolean extraNewlines = fSpaceCheckBox.isSelected();
        int format = 0;
        final String formatStr = fFormatComboBox.getSelectedKey();
        if( formatStr == null || formatStr.equals(FORMAT_CLEAN) ) {
            // Clean Keys
            // - Just the clean, non-encoded keys
            format = 0;
        } else if( formatStr.equals(FORMAT_HTML) ) {
            // HTML Encoded Keys
            // - Simple, URL-path encoded keys where things like spaces are replaced with "%20", etc
            format = 1;
        } else if( formatStr.equals(FORMAT_SITE_A) ) {
            // Freesite: <a> links
            // - Uses URL-path encoded keys for the inner href links (NOTE: this encodes <, >, ? and other special URL chars)
            // - Uses HTML-entity escaping of <, > and & for the displayed filename inside <a> tag
            format = 2;
        } else if( formatStr.equals(FORMAT_SITE_PRE) ) {
            // Freesite: <pre> block
            // - Uses clean, non-encoded keys, but performs HTML-entity escaping on all of them
            //   so that <, > and & in filenames aren't interpreted as HTML tags or XHTML entities
            format = 3;
        }

        // generate the string to put in the output field
        final StringBuilder sb = new StringBuilder();
        long addedKeyCount = 0;
        if( format == 3 ) { // [Format] Freesite: <pre> block
            // opening tag...
            sb.append("<pre>\n");
        }
        for( Map.Entry<String,String> entry : foundKeys.entrySet() ) {
            try {
                // NOTE: we never have to check if getKey() is null, since we know we only added non-null values
                //
                boolean lineWasAdded = false;
                if( format == 0 ) {
                    // [Format] Clean Keys
                    sb.append(entry.getKey()).append("\n");
                    lineWasAdded = true;
                    ++addedKeyCount;
                } else if( format == 3 ) {
                    // [Format] Freesite: <pre> block
                    sb.append(Mixed.htmlSpecialChars(entry.getKey())).append("\n");
                    lineWasAdded = true;
                    ++addedKeyCount;
                } else {
                    // formats 1 and 2 (both of which use URL-encoding)

                    // convert the key to URL-encoded fragments (using regular URL "path" encoding
                    // with "%20" spaces, NOT form-encoding/query-encoding (which uses "+" spaces)).
                    final String encodedKey = Mixed.rawUrlEncode(entry.getKey());
                    if( encodedKey != null ) {
                        // successfully encoded! determine which output format to use
                        if( format == 1 ) {
                            // [Format] HTML Encoded Keys
                            sb.append(encodedKey).append("\n");
                        } else { // else: format 2
                            // [Format] Freesite: <a> links

                            // first figure out the filename to display for the link
                            String fileName = entry.getValue();
                            if( fileName == null ) {
                                // use key as name if the key had no filename
                                // NOTE: this will never happen, since we're using "exclude freesite keys"
                                // in the key parser, which also makes it ignore all keys without filenames.
                                fileName = entry.getKey();
                            }

                            // now just escape all of the special HTML/XHTML tag/entity characters
                            final String escapedFileName = Mixed.htmlSpecialChars(fileName);

                            // generate the link! it points to the key using Freenet's regular "/" prefix
                            // mechanism, and displays the safely HTML-entity escaped name of the file.
                            sb.append("<a href=\"/").append(encodedKey).append("\">")
                                .append(escapedFileName)
                                .append("</a><br />\n");
                        }

                        lineWasAdded = true;
                        ++addedKeyCount;
                    }
                }

                // add an extra, blank line between each link if requested
                if( lineWasAdded && extraNewlines ) {
                    sb.append("\n");
                }
            } catch( final Exception ex ) {
                // just skip the failed item; this should never even be able to happen,
            }
        }
        if( addedKeyCount > 0 ) {
            // at least 1 key was added, so trim the trailing newline(s)
            sb.deleteCharAt(sb.length() - 1);
            if( extraNewlines ) { sb.deleteCharAt(sb.length() - 1); }
        }
        if( format == 3 ) { // [Format] Freesite: <pre> block
            // closing tag...
            if( addedKeyCount > 0 ) {
                // if there were keys, we first need to add a newline since the list doesn't have one
                sb.append("\n");
            }
            sb.append("</pre>");
        }

        // update the statistics
        setBottomLabelCount(foundKeys.size());

        // put the generated text in the output field
        fOutputTextPane.setText(sb.toString());
        fOutputTextPane.setCaretPosition(0); // jump back to top
    }
}
