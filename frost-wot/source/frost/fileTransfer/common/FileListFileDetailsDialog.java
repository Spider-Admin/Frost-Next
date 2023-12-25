/*
 SearchItemPropertiesDialog.java / Frost
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
package frost.fileTransfer.common;

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

import javax.swing.*;

import frost.*;
import frost.fileTransfer.*;
import frost.fileTransfer.search.*;
import frost.identities.*;
import frost.messaging.frost.gui.MessagePanel.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;
import frost.util.model.*;

@SuppressWarnings("serial")
public class FileListFileDetailsDialog extends JDialog {

    Language language = Language.getInstance();

    private JPanel jContentPane = null;
    private JPanel buttonPanel = null;
    private JPanel mainPanel = null;
    private JButton Bclose = null;

    private SortedModelTable<FileListFileDetailsItem> modelTable = null;
    private FileListFileDetailsTableModel model = null;
    private FileListFileDetailsTableFormat tableFormat = null;

    private PopupMenu popupMenu = null;
    private final Listener listener = new Listener();

    private final boolean isOwnerSearchAllowed;

    public FileListFileDetailsDialog(final Frame owner) {
        this(owner, false);
    }

    public FileListFileDetailsDialog(final Frame owner, final boolean allowOwnerSearch) {
        super(owner);
        initialize(owner);
        isOwnerSearchAllowed = allowOwnerSearch;
    }

    /**
     * This method initializes this
     */
    private void initialize(final Frame owner) {
        this.setContentPane(getJContentPane());
        this.setTitle(language.getString("FileListFileDetailsDialog.title"));
        loadLayout();
        setLocationRelativeTo(owner);
    }

    private PopupMenu getPopupMenu() {
        if (popupMenu == null) {
            popupMenu = new PopupMenu();
        }
        return popupMenu;
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
        getPopupMenu().show(e.getComponent(), e.getX(), e.getY());
    }

    private void loadLayout() {

        int lastHeight = Core.frostSettings.getIntValue("FileListFileDetailsDialog.height");
        int lastWidth = Core.frostSettings.getIntValue("FileListFileDetailsDialog.width");

	// resize to default dimensions if the user made the window too small last time, or if this is the first launch
        if (lastWidth < 200) {
            lastWidth = 600;
        }
        if (lastHeight < 200) {
            lastHeight = 370;
        }

        // NOTE: we don't set any x/y coordinates, since we'll be centering this dialog relative to the parent frame
        setSize(lastWidth, lastHeight);
    }

    private void saveLayout() {
        // dialog size
        final Rectangle bounds = getBounds();
        Core.frostSettings.setValue("FileListFileDetailsDialog.height", bounds.height);
        Core.frostSettings.setValue("FileListFileDetailsDialog.width", bounds.width);

        tableFormat.saveTableLayout(getModelTable());
    }

    /**
     * This method initializes jContentPane
     */
    private JPanel getJContentPane() {
        if( jContentPane == null ) {
            jContentPane = new JPanel();
            jContentPane.setLayout(new BorderLayout());
            jContentPane.add(getButtonPanel(), BorderLayout.SOUTH);
            jContentPane.add(getMainPanel(), BorderLayout.CENTER);
        }
        return jContentPane;
    }

    /**
     * This method initializes buttonPanel
     */
    private JPanel getButtonPanel() {
        if( buttonPanel == null ) {
            final FlowLayout flowLayout = new FlowLayout();
            flowLayout.setAlignment(FlowLayout.RIGHT);
            buttonPanel = new JPanel();
            buttonPanel.setLayout(flowLayout);
            buttonPanel.add(getBclose(), null);
        }
        return buttonPanel;
    }

    /**
     * This method initializes mainPanel
     */
    private JPanel getMainPanel() {
        if( mainPanel == null ) {
            mainPanel = new JPanel();
            mainPanel.setLayout(new BorderLayout());
            mainPanel.add( getModelTable().getScrollPane(), BorderLayout.CENTER);
        }
        return mainPanel;
    }

    private SortedModelTable<FileListFileDetailsItem> getModelTable() {
        if( modelTable == null ) {
            tableFormat = new FileListFileDetailsTableFormat();
            model = new FileListFileDetailsTableModel(tableFormat);
            modelTable = new SortedModelTable<FileListFileDetailsItem>(model);

            modelTable.getScrollPane().addMouseListener(listener);
            modelTable.getTable().addMouseListener(listener);
        }
        return modelTable;
    }

    /**
     * This method initializes Bok
     */
    private JButton getBclose() {
        if( Bclose == null ) {
            Bclose = new JButton();
            Bclose.setText(language.getString("FileListFileDetailsDialog.button.close"));
            Bclose.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    saveLayout();
                    setVisible(false);
                }
            });
        }
        return Bclose;
    }

    public void startDialog(final FrostFileListFileObject fileObject) {
        for( final Iterator<FrostFileListFileObjectOwner> i = fileObject.getFrostFileListFileObjectOwnerIterator(); i.hasNext(); ) {
            final FrostFileListFileObjectOwner o = i.next();
            final FileListFileDetailsItem item = new FileListFileDetailsItem(o);
            model.addPropertiesItem(item);
        }
        setVisible(true);
    }

    private class PopupMenu extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

        private final JMenuItem copyKeysAndNamesItem = new JMenuItem();

        private final JMenu copyToClipboardMenu = new JMenu();

        private final JMenuItem showOwnerFilesItem = new JMenuItem();

        private final JMenuItem setBADItem = new JMenuItem();
        private final JMenuItem setNEUTRALItem = new JMenuItem();
        private final JMenuItem setFRIENDItem = new JMenuItem();
        private final JMenuItem setGOODItem = new JMenuItem();

        public PopupMenu() {
            super();
            initialize();
        }

        private void initialize() {
            refreshLanguage();

            copyToClipboardMenu.add(copyKeysAndNamesItem);

            copyKeysAndNamesItem.addActionListener(this);
            showOwnerFilesItem.addActionListener(this);
            setFRIENDItem.addActionListener(this);
            setBADItem.addActionListener(this);
            setNEUTRALItem.addActionListener(this);
            setGOODItem.addActionListener(this);
        }

        private void refreshLanguage() {
            copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));

            copyToClipboardMenu.setText(language.getString("Common.copyToClipBoard") + "...");

            showOwnerFilesItem.setText(language.getString("FileListFileDetailsDialog.popupmenu.searchFilesOfOwner"));
            setFRIENDItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToFRIEND"));
            setBADItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToBAD"));
            setNEUTRALItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToNEUTRAL"));
            setGOODItem.setText(language.getString("MessagePane.messageTable.popupmenu.setToGOOD"));
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == copyKeysAndNamesItem) {
                final List<FileListFileDetailsItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                CopyToClipboard.copyKeysAndFilenames(selectedItems.toArray());
            } else if (e.getSource() == showOwnerFilesItem) {
                searchFilesOfOwner();
            } else if (e.getSource() == setFRIENDItem) {
                changeTrustState(IdentityState.FRIEND);
            } else if (e.getSource() == setBADItem) {
                changeTrustState(IdentityState.BAD);
            } else if (e.getSource() == setNEUTRALItem) {
                changeTrustState(IdentityState.NEUTRAL);
            } else if (e.getSource() == setGOODItem) {
                changeTrustState(IdentityState.GOOD);
            }
        }

        private void changeTrustState(final IdentityState is) {
            final List<FileListFileDetailsItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            if (selectedItems.size() == 1) {
                final FileListFileDetailsItem item =  selectedItems.get(0);
                if( is == IdentityState.FRIEND ) {
                    item.getOwnerIdentity().setFRIEND();
                } else if( is == IdentityState.NEUTRAL ) {
                    item.getOwnerIdentity().setNEUTRAL();
                } else if( is == IdentityState.GOOD ) {
                    item.getOwnerIdentity().setGOOD();
                } else if( is == IdentityState.BAD ) {
                    item.getOwnerIdentity().setBAD();
                }
                modelTable.fireTableRowsUpdated(0, modelTable.getRowCount()-1);
                // also update message panel to reflect the identity change
                MainFrame.getInstance().getMessagePanel().updateTableAfterChangeOfIdentityState();
            }
        }

        private void searchFilesOfOwner() {
            final List<FileListFileDetailsItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            if (selectedItems.size() == 1) {
                final FileListFileDetailsItem item = selectedItems.get(0);
                final String owner = item.getOwnerIdentity().getUniqueName();

                final SearchParameters sp = new SearchParameters(false);
                sp.setOwnerString(owner);
                FileTransferManager.inst().getSearchManager().getPanel().startNewSearch(sp);
            }
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            final List<FileListFileDetailsItem> selectedItems = modelTable.getSelectedItems();

            if( selectedItems == null || selectedItems.size() == 0 ) {
                return;
            }

            // if at least 1 item is selected
            add(copyToClipboardMenu);

            if (selectedItems.size() == 1) {

                addSeparator();

                add(setFRIENDItem);
                add(setGOODItem);
                add(setNEUTRALItem);
                add(setBADItem);
                setFRIENDItem.setEnabled(false);
                setGOODItem.setEnabled(false);
                setNEUTRALItem.setEnabled(false);
                setBADItem.setEnabled(false);

                final FileListFileDetailsItem item =  selectedItems.get(0);
                final Identity ownerId = item.getOwnerIdentity();

                if( ownerId instanceof LocalIdentity ) {
                    // keep all off
                } else if (ownerId.isFRIEND()) {
                    setGOODItem.setEnabled(true);
                    setNEUTRALItem.setEnabled(true);
                    setBADItem.setEnabled(true);
                } else if (ownerId.isNEUTRAL()) {
                    setGOODItem.setEnabled(true);
                    setFRIENDItem.setEnabled(true);
                    setBADItem.setEnabled(true);
                } else if (ownerId.isBAD()) {
                    setGOODItem.setEnabled(true);
                    setFRIENDItem.setEnabled(true);
                    setNEUTRALItem.setEnabled(true);
                } else if (ownerId.isGOOD()) {
                    setFRIENDItem.setEnabled(true);
                    setNEUTRALItem.setEnabled(true);
                    setBADItem.setEnabled(true);
                } else {
                    // keep all off
                }
            }

            if( isOwnerSearchAllowed && selectedItems.size() == 1 ) {
                addSeparator();
                add(showOwnerFilesItem);
            }

            super.show(invoker, x, y);
        }
    }

    private class Listener extends MouseAdapter implements MouseListener {
        public Listener() {
            super();
        }
        @Override
        public void mousePressed(final MouseEvent e) {
            if (e.isPopupTrigger()) {
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
    }

}  //  @jve:decl-index=0:visual-constraint="10,10"
