/*
  SharedFilesPanel.java / Frost
  Copyright (C) 2006  Frost Project <jtcfrost.sourceforge.net>

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
package frost.fileTransfer.sharing;

import java.awt.*;
import java.awt.event.*;
import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;

import frost.*;
import frost.MainFrame;
import frost.fileTransfer.filelist.*;
import frost.storage.perst.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.SmartFileFilters;
import frost.util.gui.search.*;
import frost.util.gui.translation.*;
import frost.util.model.*;

@SuppressWarnings("serial")
public class SharedFilesPanel extends JPanel {

    private PopupMenu popupMenuUpload = null;

    private final Listener listener = new Listener();

    private static final Logger logger = Logger.getLogger(SharedFilesPanel.class.getName());

    private SharedFilesModel model = null;

    private Language language = null;

    private final JToolBar sharedFilesToolBar = new JToolBar();
    private final JButton addSharedFilesButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/folder-open.png"));

    private int sharedFilesCount = 0;
    private final JLabel sharedFilesCountLabel = new JLabel();

    private SortedModelTable<FrostSharedFileItem> modelTable;

    private boolean initialized = false;

    public SharedFilesPanel() {
        super();

        language = Language.getInstance();
        language.addLanguageListener(listener);
    }

    public void initialize() {
        if (!initialized) {
            refreshLanguage();

            // create the top panel
            MiscToolkit.configureButton(addSharedFilesButton);

            sharedFilesToolBar.setRollover(true);
            sharedFilesToolBar.setFloatable(false);

            sharedFilesToolBar.add(addSharedFilesButton);
            sharedFilesToolBar.add(Box.createRigidArea(new Dimension(80, 0)));
            sharedFilesToolBar.add(Box.createHorizontalGlue());
            sharedFilesToolBar.add(sharedFilesCountLabel);

            // create the main upload panel
            modelTable = new SortedModelTable<FrostSharedFileItem>(model);
            new TableFindAction().install(modelTable.getTable());
            setLayout(new BorderLayout());
            add(sharedFilesToolBar, BorderLayout.NORTH);
            add(modelTable.getScrollPane(), BorderLayout.CENTER);
            fontChanged();

            // listeners
            addSharedFilesButton.addActionListener(listener);
            modelTable.getScrollPane().addMouseListener(listener);
            modelTable.getTable().addKeyListener(listener);
            modelTable.getTable().addMouseListener(listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_NAME, listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_SIZE, listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_STYLE, listener);

            initialized = true;
        }
    }

    public SharedFilesTableFormat getTableFormat() {
        return (SharedFilesTableFormat) modelTable.getTableFormat();
    }

    public ModelTable<FrostSharedFileItem> getModelTable() {
        return modelTable;
    }

    public void setAddFilesButtonEnabled(final boolean enabled) {
        addSharedFilesButton.setEnabled(enabled);
    }

    private Dimension calculateLabelSize(final String text) {
        final JLabel dummyLabel = new JLabel(text);
        dummyLabel.doLayout();
        return dummyLabel.getPreferredSize();
    }

    private void refreshLanguage() {
        addSharedFilesButton.setToolTipText(language.getString("SharedFilesPane.toolbar.tooltip.browse") + "...");

        final String waiting = language.getString("SharedFilesPane.toolbar.files");
        final Dimension labelSize = calculateLabelSize(waiting + ": 00000  (9876MB)");
        sharedFilesCountLabel.setPreferredSize(labelSize);
        sharedFilesCountLabel.setMinimumSize(labelSize);
        sharedFilesCountLabel.setText(waiting + ": " + sharedFilesCount);

    }

    private PopupMenu getPopupMenuUpload() {
        if (popupMenuUpload == null) {
            popupMenuUpload = new PopupMenu();
            language.addLanguageListener(popupMenuUpload);
        }
        return popupMenuUpload;
    }

    private void uploadTable_keyPressed(final KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_DELETE && !modelTable.getTable().isEditing()) {
            removeSelectedFiles();
        }
    }

    private void removeSelectedFiles() {
        final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null ) { return; }
        model.removeItems(selectedItems);

        modelTable.getTable().clearSelection();

        // currently running upload items are removed during next startup

        // notify list upload thread that user changed something
        FileListUploadThread.getInstance().userActionOccured();
    }

    public void uploadAddFilesButton_actionPerformed(final ActionEvent e) {

        final JFileChooser fc = new JFileChooser(Core.frostSettings.getValue(SettingsClass.DIR_LAST_USED));
        fc.setDialogTitle(language.getString("SharedFilesPane.filechooser.title"));
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

        // NOTE: this check guarantees that we center the first "add files" picker over the mainframe,
        // and subsequent ones over the "add new files" window (if they click its "Add [more]" button)
        final int returnVal = fc.showOpenDialog(
                ( this != null && this.isVisible() ? this : MainFrame.getInstance() )
        );
        if( returnVal != JFileChooser.APPROVE_OPTION ) {
            return; // user canceled file dialog
        }

        // retrieve the list of chosen files
        final File[] selectedFiles = fc.getSelectedFiles();
        if( selectedFiles == null || selectedFiles.length == 0 ) {
            return; // file dialog failed
        }

        // ask for owner to use
        final SharedFilesOwnerDialog dlg =
            new SharedFilesOwnerDialog(MainFrame.getInstance(), language.getString("SharedFilesOwnerDialog.title"));
        if( dlg.showDialog() == SharedFilesOwnerDialog.CANCEL ) {
            return;
        }
        final String owner = dlg.getChoosedIdentityName();
        final boolean replacePathIfFileExists = dlg.isReplacePathIfFileExists();

        // start external thread to check the filelength and add NewUploadFile objects
        new AddNewSharedFilesThread(selectedFiles, owner, replacePathIfFileExists).start();
    }

    /**
     * Thread reads a list of new shared files, creates NewUploadFile objects and adds them to the NewUploadFilesManager.
     */
    private class AddNewSharedFilesThread extends Thread {
        private final File[] selectedFiles;
        private final String owner;
        private final boolean replacePathIfFileExists;
        public AddNewSharedFilesThread(final File[] selectedFiles, final String owner, final boolean replacePath) {
            super();
            this.selectedFiles = selectedFiles;
            this.owner = owner;
            this.replacePathIfFileExists = replacePath;
        }
        @Override
        public void run() {
            final List<File> uploadFileItems = new LinkedList<File>();
            for( final File element : selectedFiles ) {
                // collect all choosed files + files in all choosed directories
                uploadFileItems.addAll( FileAccess.getAllEntries(element) );
            }

            // remember last upload dir
            if (uploadFileItems.size() > 0) {
                final File file = uploadFileItems.get(0);
                Core.frostSettings.setValue(SettingsClass.DIR_LAST_USED, file.getParent());
            }

            // create list of NewUploadFile objects
            final List<NewUploadFile> uploadItems = new LinkedList<NewUploadFile>();
            for( final File file : uploadFileItems ) {
                final NewUploadFile nuf = new NewUploadFile(file, owner, replacePathIfFileExists);
                uploadItems.add(nuf);
            }

            // notify list upload thread about a change in the filelist
            FileListUploadThread.getInstance().userActionOccured();

            Core.getInstance().getFileTransferManager().getNewUploadFilesManager().addNewUploadFiles(uploadItems);
        }
    }

    private void showUploadTablePopupMenu(final MouseEvent e) {
        // select row where rightclick occurred if row under mouse is NOT selected
        final Point p = e.getPoint();
        final int y = modelTable.getTable().rowAtPoint(p);
        if( y < 0 ) {
            return;
        }
        if( !modelTable.getTable().getSelectionModel().isSelectedIndex(y) ) {
            modelTable.getTable().getSelectionModel().setSelectionInterval(y, y);
        }
        getPopupMenuUpload().show(e.getComponent(), e.getX(), e.getY());
    }

    private void fontChanged() {
        final String fontName = Core.frostSettings.getValue(SettingsClass.FILE_LIST_FONT_NAME);
        final int fontStyle = Core.frostSettings.getIntValue(SettingsClass.FILE_LIST_FONT_STYLE);
        final int fontSize = Core.frostSettings.getIntValue(SettingsClass.FILE_LIST_FONT_SIZE);
        Font font = new Font(fontName, fontStyle, fontSize);
        if (!font.getFamily().equals(fontName)) {
            logger.severe("The selected font was not found in your system\n" +
                           "That selection will be changed to \"SansSerif\".");
            Core.frostSettings.setValue(SettingsClass.FILE_LIST_FONT_NAME, "SansSerif");
            font = new Font("SansSerif", fontStyle, fontSize);
        }
        modelTable.setFont(font);
    }

    public void setModel(final SharedFilesModel model) {
        this.model = model;

        model.addOrderedModelListener(new SortedModelListener<FrostSharedFileItem>() {
            public void modelCleared() {
                updateSharedFilesItemCount();
            }
            public void itemAdded(final int position, final FrostSharedFileItem item) {
                updateSharedFilesItemCount();
            }
            public void itemChanged(final int position, final FrostSharedFileItem item) {
            }
            public void itemsRemoved( int[] positions, List<FrostSharedFileItem> items) {
                updateSharedFilesItemCount();
            }
        });
    }

    private void showProperties() {
        final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null || selectedItems.size() == 0 ) {
            return;
        }

        final List<FrostSharedFileItem> items = new LinkedList<FrostSharedFileItem>();
        for( final FrostSharedFileItem item : selectedItems ) {
            items.add(item);
        }
        final FrostSharedFileItem defaultItem = items.get(0);
        final SharedFilesPropertiesDialog dlg = new SharedFilesPropertiesDialog(MainFrame.getInstance());

        String singleFilename = null;
        int fileCount = 0;

        if( items.size() == 1 ) {
            singleFilename = defaultItem.getFile().getName();
        } else {
            fileCount = items.size();
        }

        final boolean okClicked = dlg.startDialog(singleFilename, fileCount, defaultItem);
        if( !okClicked ) {
            return;
        }

        for( final FrostSharedFileItem item : items ) {
            // check if item was really changed, calling a setter will mark the item changed
            String oldStr, newStr;

            oldStr = item.getComment();
            newStr = dlg.getComment();
            if( !stringsEqual(oldStr, newStr) ) {
                item.setComment( dlg.getComment() );
            }

            oldStr = item.getKeywords();
            newStr = dlg.getKeywords();
            if( !stringsEqual(oldStr, newStr) ) {
                item.setKeywords( dlg.getKeywords() );
            }

            if( item.getRating() != dlg.getRating() ) {
                item.setRating( dlg.getRating() );
            }
        }
    }

    private void updateSharedFilesItemCount() {
        sharedFilesCount = model.getItemCount();
        long sharedFilesSize = 0;
        for( final FrostSharedFileItem sfi : model.getItems() ) {
            sharedFilesSize += sfi.getFileSize();
        }

        final String s =
            new StringBuilder()
                .append(language.getString("SharedFilesPane.toolbar.files"))
                .append(": ")
                .append(sharedFilesCount)
                .append("  (")
                .append(FormatterUtils.formatSize(sharedFilesSize))
                .append(")")
                .toString();
        sharedFilesCountLabel.setText(s);
    }

    private boolean stringsEqual(final String oldStr, final String newStr) {
        if( oldStr == null && newStr != null ) {
            return false;
        }
        if( oldStr != null && newStr == null ) {
            return false;
        }
        if( oldStr == null && newStr == null ) {
            return true;
        }
        if( oldStr.equals(newStr) ) {
            return true;
        } else {
            return false;
        }
    }

    private class PopupMenu extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

        private final JMenuItem copyKeysAndNamesItem = new JMenuItem();
        private final JMenuItem copyExtendedInfoItem = new JMenuItem();
        private final JMenuItem uploadSelectedFilesItem = new JMenuItem();
        private final JMenuItem removeSelectedFilesItem = new JMenuItem();
        private final JMenuItem propertiesItem = new JMenuItem();

        private final JMenu copyToClipboardMenu = new JMenu();

        public PopupMenu() {
            super();
            initialize();
        }

        private void initialize() {
            refreshLanguage();

            copyToClipboardMenu.add(copyKeysAndNamesItem);
            copyToClipboardMenu.add(copyExtendedInfoItem);

            copyKeysAndNamesItem.addActionListener(this);
            copyExtendedInfoItem.addActionListener(this);
            removeSelectedFilesItem.addActionListener(this);
            uploadSelectedFilesItem.addActionListener(this);
            propertiesItem.addActionListener(this);
        }

        private void refreshLanguage() {
            propertiesItem.setText(language.getString("Common.properties"));
            copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
            copyExtendedInfoItem.setText(language.getString("Common.copyToClipBoard.copyExtendedInfo"));
            uploadSelectedFilesItem.setText(language.getString("SharedFilesPane.fileTable.popupmenu.uploadSelectedFiles"));
            removeSelectedFilesItem.setText(language.getString("SharedFilesPane.fileTable.popupmenu.removeSelectedFiles"));

            copyToClipboardMenu.setText(language.getString("Common.copyToClipBoard") + "...");
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == copyKeysAndNamesItem) {
                final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                CopyToClipboard.copyKeysAndFilenames(selectedItems.toArray());
            }
            if (e.getSource() == copyExtendedInfoItem) {
                final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                CopyToClipboard.copyExtendedInfo(selectedItems.toArray());
            }
            if (e.getSource() == removeSelectedFilesItem) {
                removeSelectedFiles();
            }
            if (e.getSource() == uploadSelectedFilesItem) {
                uploadSelectedFiles();
            }
            if( e.getSource() == propertiesItem ) {
                showProperties();
            }
        }

        /**
         * Reload selected files
         */
        private void uploadSelectedFiles() {
            final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            model.requestItems(selectedItems);
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            final List<FrostSharedFileItem> selectedItems = modelTable.getSelectedItems();

            if( selectedItems == null || selectedItems.size() == 0 ) {
                return;
            }

            // if all selected items are valid, then show long menu
            boolean allValid = true;
            for( final FrostSharedFileItem sfItem : selectedItems ) {
                if( !sfItem.isValid() ) {
                    allValid = false;
                    break;
                }
            }
            if( allValid ) {
                add(copyToClipboardMenu);
                addSeparator();
                add(removeSelectedFilesItem);
                addSeparator();
                add(uploadSelectedFilesItem);
                addSeparator();
                add(propertiesItem);
            } else {
                // we have either valid+invalid items selected, or multiple invalid items
                // allow removal
                add(removeSelectedFilesItem);
            }
            super.show(invoker, x, y);
        }
    }

    private class Listener
        extends MouseAdapter
        implements LanguageListener, KeyListener, ActionListener, MouseListener, PropertyChangeListener {

        public Listener() {
            super();
        }
        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }
        public void keyPressed(final KeyEvent e) {
            if (e.getSource() == modelTable.getTable()) {
                uploadTable_keyPressed(e);
            }
        }
        public void keyReleased(final KeyEvent e) {
            // Nothing here
        }
        public void keyTyped(final KeyEvent e) {
            // Nothing here
        }
        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == addSharedFilesButton) {
                uploadAddFilesButton_actionPerformed(e);
            }
        }
        @Override
        public void mousePressed(final MouseEvent e) {
            if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) {
                if (e.getSource() == modelTable.getTable()) {
                    // Start file from download table. Is this a good idea?
                    showProperties();
                }
            } else if (e.isPopupTrigger()) {
                if ((e.getSource() == modelTable.getTable())
                    || (e.getSource() == modelTable.getScrollPane())) {
                    showUploadTablePopupMenu(e);
                }
            }
        }
        @Override
        public void mouseReleased(final MouseEvent e) {
            if ((e.getClickCount() == 1) && (e.isPopupTrigger())) {
                if ((e.getSource() == modelTable.getTable())
                    || (e.getSource() == modelTable.getScrollPane())) {
                    showUploadTablePopupMenu(e);
                }
            }
        }
        public void propertyChange(final PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(SettingsClass.FILE_LIST_FONT_NAME)) {
                fontChanged();
            }
            if (evt.getPropertyName().equals(SettingsClass.FILE_LIST_FONT_SIZE)) {
                fontChanged();
            }
            if (evt.getPropertyName().equals(SettingsClass.FILE_LIST_FONT_STYLE)) {
                fontChanged();
            }
        }
    }
}
