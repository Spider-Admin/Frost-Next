/*
  UploadPanel.java / Frost
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
package frost.fileTransfer.upload;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.ext.ExecuteDocument;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.PersistenceManager;
import frost.gui.AddNewUploadsDialog;
import frost.util.CopyToClipboard;
import frost.util.DesktopUtils;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;
import frost.util.model.SortedModelTable;
import frost.util.Mixed;

@SuppressWarnings("serial")
public class UploadPanel extends JPanel {

    private PopupMenuUpload popupMenuUpload = null;

    private final Listener listener = new Listener();

    private static final Logger logger = Logger.getLogger(UploadPanel.class.getName());

    private UploadModel model = null;

    private Language language = null;

    private final JToolBar uploadToolBar = new JToolBar();
    private final JButton uploadAddFilesButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/folder-open.png"));
    private final JCheckBox removeFinishedUploadsCheckBox = new JCheckBox();
    private final JCheckBox showExternalGlobalQueueItems = new JCheckBox();
    private final JCheckBox quickhealModeCheckBox = new JCheckBox();

    private SortedModelTable<FrostUploadItem> modelTable;

    private final JLabel uploadSpeedLabel = new JLabel("", SwingConstants.RIGHT); // right-aligned
    private final JLabel uploadItemCountLabel = new JLabel("", SwingConstants.LEFT);
    private double speedBlocksPerMinute = 0;
    private long speedBpmInBytesPerSecond = 0;
    private int uploadItemCount = 0;

    private boolean initialized = false;

    public UploadPanel() {
        super();

        language = Language.getInstance();
        language.addLanguageListener(listener);
    }

    public UploadTableFormat getTableFormat() {
        return (UploadTableFormat) modelTable.getTableFormat();
    }

    public void initialize() {
        if (!initialized) {
            refreshLanguage();

            uploadToolBar.setRollover(true);
            uploadToolBar.setFloatable(false);

            removeFinishedUploadsCheckBox.setOpaque(false);
            showExternalGlobalQueueItems.setOpaque(false);
            quickhealModeCheckBox.setOpaque(false);

            // create the top toolbar panel and add the buttons and labels to it
            MiscToolkit.configureButton(uploadAddFilesButton);
            uploadToolBar.add(Box.createRigidArea(new Dimension(6, 0)));
            uploadToolBar.add(uploadAddFilesButton);
            uploadToolBar.add(Box.createRigidArea(new Dimension(8, 0)));
            uploadToolBar.add(removeFinishedUploadsCheckBox);
            if( PersistenceManager.isPersistenceEnabled() ) {
                uploadToolBar.add(Box.createRigidArea(new Dimension(8, 0)));
                uploadToolBar.add(showExternalGlobalQueueItems);
            }
            uploadToolBar.add(Box.createRigidArea(new Dimension(8, 0)));
            uploadToolBar.add(quickhealModeCheckBox);
            
            uploadToolBar.add(Box.createRigidArea(new Dimension(80, 0)));
            uploadToolBar.add(Box.createHorizontalGlue());
            uploadToolBar.add(uploadSpeedLabel);
            uploadToolBar.add(uploadItemCountLabel);

            // create the main upload panel
            modelTable = new SortedModelTable<FrostUploadItem>(model) {
                // override the default sort order when clicking different columns
                @Override
                public boolean getColumnDefaultAscendingState(final int columnNumber) {
                    if( columnNumber == 0 || columnNumber == 2 || columnNumber == 3 || columnNumber == 5 || columnNumber == 7 ) {
                        // sort enabled, filesize, state, blocks and compress descending by default
                        return false;
                    }
                    return true; // all other columns: ascending
                }
            };
            new TableFindAction().install(modelTable.getTable());
            setLayout(new BorderLayout());
            add(uploadToolBar, BorderLayout.NORTH);
            add(modelTable.getScrollPane(), BorderLayout.CENTER);
            fontChanged();

            // event listeners for the buttons and the file table's click and keyboard
            uploadAddFilesButton.addActionListener(listener);
            modelTable.getScrollPane().addMouseListener(listener);
            modelTable.getTable().addKeyListener(listener);
            modelTable.getTable().addMouseListener(listener);
            removeFinishedUploadsCheckBox.addItemListener(listener);
            showExternalGlobalQueueItems.addItemListener(listener);
            quickhealModeCheckBox.addItemListener(listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_NAME, listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_SIZE, listener);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_STYLE, listener);

            // set the state of the toolbar checkboxes to the user's current config state,
            // triggering the event listener for every enabled one (but not disabled ones)
            removeFinishedUploadsCheckBox.setSelected(Core.frostSettings.getBoolValue(SettingsClass.UPLOAD_REMOVE_FINISHED));
            showExternalGlobalQueueItems.setSelected(Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD));
            quickhealModeCheckBox.setSelected(Core.frostSettings.getBoolValue(SettingsClass.UPLOAD_QUICKHEAL_MODE));
            
            // color the quickheal checkbox based on its value
            recolorQuickhealModeCheckbox();

            assignHotkeys();

            // start the "blocks per minute" speed tracking thread
            new Thread("UploadSpeedTracker") {
                @Override
                public void run() {
                    while( true ) {
                        // run at a 10 second update interval; we only scan in-progress items, so
                        // it's very optimized. (might sound really rapid, but I've even tried it
                        // with 0.1 seconds and it still stayed responsive; but 10s is all anyone would need)
                        // and this ensures we don't put too much pressure on analyzing items.
                        Mixed.wait(10000);

                        // calculate the combined blocks per minute and bytes per minute of all ongoing transfers
                        double totalBlocksPerMinute = 0;
                        long totalBpmInBytesPerSecond = 0;

                        // copy the transfer-items into a list (safe from external modification)
                        // and process the values. note that "getItems()" is thread-safe and
                        // returns a defensive copy which means it's safe to read anytime.
                        // NOTE: the list won't include external items unless "show global queue".
                        final List<FrostUploadItem> itemList = model.getItems();
                        for( final FrostUploadItem dlItem : itemList ) {
                            if( dlItem.getState() == FrostUploadItem.STATE_PROGRESS ) {
                                // determine how long ago the current item measurement was updated
                                final long millisSinceLastMeasurement = dlItem.getMillisSinceLastMeasurement();

                                // only include measurements that have been updated in the
                                // last 4 minutes (aka non-stalled uploads)
                                if( millisSinceLastMeasurement <= 240000 ) {
                                    // get the current average number of blocks per minute
                                    double blocksPerMinute = dlItem.getAverageBlocksPerMinute();

                                    // only include actual measurements, meaning not "-1" (measuring activity)
                                    // or "-2" (no recent transfer activity in the last hard-cap minutes)
                                    if( blocksPerMinute >= 0 ) {
                                        // since this is a valid measurement, also get its corresponding "bytes per second" count
                                        long bpmInBytesPerSecond = dlItem.getAverageBytesPerSecond();

                                        // now just add the current item's statistics to the total
                                        totalBlocksPerMinute += blocksPerMinute;
                                        totalBpmInBytesPerSecond += bpmInBytesPerSecond;
                                    }
                                }
                            }
                        }

                        // we must perform the actual GUI update in the GUI thread, so queue the update now
                        final double atomicTotalBlocksPerMinute = totalBlocksPerMinute; // threads can only access these final variables
                        final long atomicTotalBpmInBytesPerSecond = totalBpmInBytesPerSecond;
                        SwingUtilities.invokeLater(new Runnable() {
                            public void run() {
                                setUploadSpeed(atomicTotalBlocksPerMinute, atomicTotalBpmInBytesPerSecond);
                            }
                        });
                    }
                }
            }.start();

            initialized = true;
        }
    }

    // colors the quickheal checkbox red (with bold font) if enabled, otherwise uses the default
    // checkbox font and color for the current Look & Feel (which may be bold in certain L&Fs).
    // this visually trains the user that they've enabled quickheal mode and should exit it as soon as possible
    // NOTE: You must also call this from updateUI when the user changes look & feel manually, to apply the new font.
    private void recolorQuickhealModeCheckbox() {
        // the checkbox will be null if called from updateUI during app startup before GUI has been fully built
        if( quickhealModeCheckBox == null ) { return; }

        // now style the checkbox
        final Font f = UIManager.getFont("CheckBox.font"); // retrieve default font for L&F
        if( quickhealModeCheckBox.isSelected() ) {
            quickhealModeCheckBox.setForeground(Color.RED); // "warning" color
            quickhealModeCheckBox.setFont(f.deriveFont(f.getStyle() | Font.BOLD)); // default font + bold
        } else {
            quickhealModeCheckBox.setForeground(UIManager.getColor("CheckBox.foreground")); // default color
            quickhealModeCheckBox.setFont(f); // default font
        }
    }

    // called at startup and whenever the user changes their look & feel manually
    @Override
    public void updateUI() {
        super.updateUI();

        // style the quickheal box's color & font based on the new L&F
        recolorQuickhealModeCheckbox();
    }

    private Dimension calculateLabelSize(final String text) {
        final JLabel dummyLabel = new JLabel(text);
        dummyLabel.doLayout();
        return dummyLabel.getPreferredSize();
    }

    private void refreshLanguage() {
        uploadAddFilesButton.setToolTipText(language.getString("UploadPane.toolbar.tooltip.browse") + "...");

        final String blocksPerMinute = language.getString("UploadPane.toolbar.blocksPerMinute");
        final Dimension bpmLabelSize = calculateLabelSize(blocksPerMinute + ": 99999.99 (~99999.99 MiB/s)");
        uploadSpeedLabel.setPreferredSize(bpmLabelSize);
        uploadSpeedLabel.setMinimumSize(bpmLabelSize);
        setUploadSpeed(0, 0);

        final String waiting = language.getString("UploadPane.toolbar.waiting");
        final Dimension itemCountLabelSize = calculateLabelSize(waiting + ": 00000");
        uploadItemCountLabel.setPreferredSize(itemCountLabelSize);
        uploadItemCountLabel.setMinimumSize(itemCountLabelSize);
        uploadItemCountLabel.setText(waiting + ": " + uploadItemCount);

        removeFinishedUploadsCheckBox.setText(language.getString("UploadPane.removeFinishedUploads"));
        showExternalGlobalQueueItems.setText(language.getString("UploadPane.showExternalGlobalQueueItems"));
        quickhealModeCheckBox.setText(language.getString("UploadPane.quickhealMode"));
        quickhealModeCheckBox.setToolTipText(language.getString("UploadPane.quickhealMode.tooltip"));
    }

    private PopupMenuUpload getPopupMenuUpload() {
        if (popupMenuUpload == null) {
            popupMenuUpload = new PopupMenuUpload();
            language.addLanguageListener(popupMenuUpload);
        }
        return popupMenuUpload;
    }

    private void uploadTable_keyPressed(final KeyEvent e) {
        if (e.getKeyChar() == KeyEvent.VK_DELETE && !modelTable.getTable().isEditing()) {
            removeSelectedFiles(true); // true = delete both internally and externally (global-only) queued items
        }
    }

    /**
     * Remove selected files from the model and global queue.
     * @param {boolean} removeExternal - if true, it also deletes externally queued (global-only)
     * items; otherwise it only deletes and de-queues things that were internally queued
     */
    private void removeSelectedFiles(final boolean removeExternal) {
        final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null ) { return; }

        final List<String> externalRequestsToRemove = new LinkedList<String>();
        final List<FrostUploadItem> requestsToRemove = new LinkedList<FrostUploadItem>();
        for( final FrostUploadItem mi : selectedItems ) {
            if( mi.isExternal() && !removeExternal ) {
                continue; // do nothing to externally queued items if we've been told to avoid those
            }
            // don't remove items whose data transfer to the node is currently in progress.
            // otherwise, we risk orphaning the upload in the global upload-queue since we haven't de-queued it properly.
            if( FileTransferManager.inst().getPersistenceManager() != null ) {
                if( FileTransferManager.inst().getPersistenceManager().isDirectTransferInProgress(mi) ) {
                    continue;
                }
            }
            requestsToRemove.add(mi);
            if( mi.isExternal() ) {
                externalRequestsToRemove.add(mi.getGqIdentifier());
            }
        }

        model.removeItems(requestsToRemove); // NOTE: if these are internal items, they'll also be de-queued from the global queue automatically

        modelTable.getTable().clearSelection();

        if( FileTransferManager.inst().getPersistenceManager() != null && externalRequestsToRemove.size() > 0 ) {
            new Thread() {
                @Override
                public void run() {
                    // de-queue the external-only items from the global queue
                    FileTransferManager.inst().getPersistenceManager().removeRequests(externalRequestsToRemove);
                }
            }.start();
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

    
    private void openFile(FrostUploadItem ulItem) {
        if (ulItem == null || ulItem.isExternal()) {
            return;
        }

        final File targetFile = ulItem.getFile();
        if (targetFile == null || !targetFile.isFile()) {
            logger.info("Executing: File not found: " + targetFile.getAbsolutePath());
            return;
        }
        logger.info("Executing: " + targetFile.getAbsolutePath());
        try {
            ExecuteDocument.openDocument(targetFile);
        } catch (final Throwable t) {
            MiscToolkit.showMessageDialog(this, "Could not open file: " + t.getMessage() + ".",
                    "Error", MiscToolkit.ERROR_MESSAGE);
        }
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

    public void setModel(final UploadModel model) {
        this.model = model;
    }

    public void setUploadSpeed(final double newSpeedBlocksPerMinute, final long newSpeedBpmInBytesPerSecond) {
        speedBlocksPerMinute = newSpeedBlocksPerMinute;
        speedBpmInBytesPerSecond = newSpeedBpmInBytesPerSecond;

        // display the total blocks/minute and the human-readable bytes/second equivalent
        final String s = new StringBuilder().append(language.getString("UploadPane.toolbar.blocksPerMinute")).append(": ")
            .append(String.format("%.2f", speedBlocksPerMinute)).append(" (~").append(Mixed.convertBytesToHuman(speedBpmInBytesPerSecond)).append("/s), ").toString();
        uploadSpeedLabel.setText(s);
    }

    public void setUploadItemCount(final int newUploadItemCount) {
        uploadItemCount = newUploadItemCount;

        final String s =
            new StringBuilder()
                .append(language.getString("UploadPane.toolbar.waiting"))
                .append(": ")
                .append(uploadItemCount)
                .toString();
        uploadItemCountLabel.setText(s);
    }
    
    public void changeItemPriorites(final List<FrostUploadItem> items, final FreenetPriority newPrio) {
        if (items == null || items.size() == 0 || FileTransferManager.inst().getPersistenceManager() == null || !PersistenceManager.isPersistenceEnabled()) {
            return;
        }
        for (final FrostUploadItem ui : items) {
            if (ui.getState() == FrostUploadItem.STATE_PROGRESS || ui.getState() == FrostUploadItem.STATE_WAITING) {
                ui.setPriority(newPrio);
                if (ui.getState() == FrostUploadItem.STATE_PROGRESS) {
                    String gqid = ui.getGqIdentifier();
                    if (gqid != null) {
                        FileTransferManager.inst().getPersistenceManager().getFcpTools().changeRequestPriority(gqid, newPrio);
                    }
                }
            }
        }
    }

    private void invertEnabledSelected() {
        final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null ) { return; }
        model.setItemsEnabled(null, selectedItems);
    }

    private void startSelectedUploadsNow() {
        final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null ) { return; }

        final List<FrostUploadItem> itemsToStart = new LinkedList<FrostUploadItem>();
        for( final FrostUploadItem ulItem : selectedItems ) {
            if( ulItem.isExternal() ) {
                continue;
            }
            if( ulItem.getState() != FrostUploadItem.STATE_WAITING ) {
                continue;
            }
            itemsToStart.add(ulItem);
        }

        for(final FrostUploadItem ulItem : itemsToStart) {
            ulItem.setEnabled(true);
            FileTransferManager.inst().getUploadManager().startUpload(ulItem);
        }
    }

    private void openSelectedUploadDirectory() {
        if( !DesktopUtils.canPerformOpen() ) { return; }
        final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
        if( selectedItems == null || selectedItems.size() < 1 ) { return; }

        // build a list of all *unique* directories within their selection
        final Map<String,File> selectedDirs = new LinkedHashMap<String,File>();
        for( final FrostUploadItem ulItem : selectedItems ) {
            // skip external items (since they never have any associated path)
            if( ulItem.isExternal() ) { continue; }

            // get the absolute parent directory path of the upload-file (doesn't check if dir exists!)
            final File uploadFile = ulItem.getFile();
            if( uploadFile == null ) { continue; } // skip empty
            final File uploadDir = uploadFile.getAbsoluteFile().getParentFile();
            if( uploadDir == null ) { continue; } // skip "has no parent" (should never happen!)

            // resolve the absolute path if it was relative (again, doesn't check if file exists!)
            final String uploadDirAbsStr = uploadDir.getAbsolutePath();

            // skip this directory if it's already been seen
            if( selectedDirs.containsKey(uploadDirAbsStr) ) { continue; }

            // now just store the File object in the map
            selectedDirs.put(uploadDirAbsStr, uploadDir);
        }

        // now attempt to open all valid (existing) directories
        for( Map.Entry<String,File> entry : selectedDirs.entrySet() ) {
            final File thisDir = entry.getValue();
            boolean success = false;
            if( thisDir.isDirectory() ) {
                success = DesktopUtils.openDirectory(thisDir);
            }

            // directory didn't exist or somehow failed to open?
            if( !success ) {
                MiscToolkit.showMessageDialog(
                        this,
                        language.formatMessage("UploadPane.openDirectoryError.body",
                            thisDir.toString()),
                        language.getString("UploadPane.openDirectoryError.title"),
                        MiscToolkit.ERROR_MESSAGE);
            }
        }
    }

    private void assignHotkeys() {

        // assign keys 0-6 - set priority of selected items
        final Action setPriorityAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                // parse the 0-6 and numpad 0-6 keys into an integer
                int prioInt = -1;
                try {
                    prioInt = new Integer(event.getActionCommand()).intValue();
                } catch( final Exception e ) {} // not a number
                if( prioInt < 0 || prioInt > 6 ) { return; }

                // now just apply the priority
                final FreenetPriority prio = FreenetPriority.getPriority(prioInt);
                final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                changeItemPriorites(selectedItems, prio);
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD3, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD5, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_6, 0, true), "SetPriority");
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD6, 0, true), "SetPriority");
        getActionMap().put("SetPriority", setPriorityAction);

        // remove all default Enter assignments from table (default assignment makes Enter select the next row)
        modelTable.getTable().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).getParent().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

        // Enter - open selected files
        final Action openFileAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                openFile(modelTable.getSelectedItem());
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "OpenFile");
        getActionMap().put("OpenFile", openFileAction);

        // Shift+D - open selected directories
        final Action openDirectoryAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                openSelectedUploadDirectory();
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.SHIFT_MASK, true), "OpenDirectory");
        getActionMap().put("OpenDirectory", openDirectoryAction);

        // Shift+S - start selected uploads now
        final Action startNowAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                startSelectedUploadsNow();
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.SHIFT_MASK, true), "StartNow");
        getActionMap().put("StartNow", startNowAction);

        // Shift+E - invert "enabled" state
        final Action invertEnabledAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                invertEnabledSelected();
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.SHIFT_MASK, true), "InvertEnabled");
        getActionMap().put("InvertEnabled", invertEnabledAction);
    }

    private class PopupMenuUpload extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

        private final JMenuItem copyKeysAndNamesItem = new JMenuItem();
        private final JMenuItem copyExtendedInfoItem = new JMenuItem();
        private final JMenuItem generateChkForSelectedFilesItem = new JMenuItem();
        private final JMenuItem removeSelectedFilesItem = new JMenuItem();
        private final JMenuItem showSharedFileItem = new JMenuItem();
        private final JMenuItem startSelectedUploadsNow = new JMenuItem();
        private final JMenuItem restartSelectedUploadsItem = new JMenuItem();

        private final JMenuItem disableAllUploadsItem = new JMenuItem();
        private final JMenuItem disableSelectedUploadsItem = new JMenuItem();
        private final JMenuItem enableAllUploadsItem = new JMenuItem();
        private final JMenuItem enableSelectedUploadsItem = new JMenuItem();
        private final JMenuItem invertEnabledAllItem = new JMenuItem();
        private final JMenuItem invertEnabledSelectedItem = new JMenuItem();

        private final JMenuItem openSelectedUploadDirItem = new JMenuItem();

        private JMenu changePriorityMenu = null;
        
        private JMenuItem removeFromGqItem = null;

        public PopupMenuUpload() {
            super();
            initialize();
        }

        private void initialize() {

        	if( PersistenceManager.isPersistenceEnabled() ) {
        		changePriorityMenu = new JMenu();
        		for(final FreenetPriority priority : FreenetPriority.values()) {
        			JMenuItem priorityMenuItem = new JMenuItem();
        			priorityMenuItem.addActionListener(new java.awt.event.ActionListener() {
        				public void actionPerformed(final ActionEvent actionEvent) {
        					final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
        					if( selectedItems == null ) { return; }
        					changeItemPriorites(selectedItems, priority);
        				}
        			});
        			changePriorityMenu.add(priorityMenuItem);
        		}

        		removeFromGqItem = new JMenuItem();
        		removeFromGqItem.addActionListener(this);
        	}

            refreshLanguage();

            copyKeysAndNamesItem.addActionListener(this);
            copyExtendedInfoItem.addActionListener(this);
            removeSelectedFilesItem.addActionListener(this);
            startSelectedUploadsNow.addActionListener(this);
            restartSelectedUploadsItem.addActionListener(this);
            generateChkForSelectedFilesItem.addActionListener(this);
            showSharedFileItem.addActionListener(this);

            enableAllUploadsItem.addActionListener(this);
            disableAllUploadsItem.addActionListener(this);
            enableSelectedUploadsItem.addActionListener(this);
            disableSelectedUploadsItem.addActionListener(this);
            invertEnabledAllItem.addActionListener(this);
            invertEnabledSelectedItem.addActionListener(this);

            openSelectedUploadDirItem.addActionListener(this);
        }

        private void refreshLanguage() {
            copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
            copyExtendedInfoItem.setText(language.getString("Common.copyToClipBoard.copyExtendedInfo"));
            generateChkForSelectedFilesItem.setText(language.getString("UploadPane.fileTable.popupmenu.startEncodingOfSelectedFiles"));
            startSelectedUploadsNow.setText(language.getString("UploadPane.fileTable.popupmenu.startSelectedUploadsNow"));
            restartSelectedUploadsItem.setText(language.getString("UploadPane.fileTable.popupmenu.restartSelectedUploads"));
            removeSelectedFilesItem.setText(language.getString("UploadPane.fileTable.popupmenu.remove.removeSelectedFiles"));
            showSharedFileItem.setText(language.getString("UploadPane.fileTable.popupmenu.showSharedFile"));

            enableAllUploadsItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.enableAllUploads"));
            disableAllUploadsItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.disableAllUploads"));
            enableSelectedUploadsItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.enableSelectedUploads"));
            disableSelectedUploadsItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.disableSelectedUploads"));
            invertEnabledAllItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.invertEnabledStateForAllUploads"));
            invertEnabledSelectedItem.setText(language.getString("UploadPane.fileTable.popupmenu.enableUploads.invertEnabledStateForSelectedUploads"));

            openSelectedUploadDirItem.setText(language.getString("UploadPane.fileTable.popupmenu.openSelectedUploadDir"));

            if( PersistenceManager.isPersistenceEnabled() ) {
                changePriorityMenu.setText(language.getString("Common.priority.changePriority"));
                
                for(int itemNum = 0; itemNum < changePriorityMenu.getItemCount() ; itemNum++) {
                	changePriorityMenu.getItem(itemNum).setText(FreenetPriority.getName(itemNum));
                }
                removeFromGqItem.setText(language.getString("UploadPane.fileTable.popupmenu.removeFromGlobalQueue"));
            }
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == copyKeysAndNamesItem) {
                final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                CopyToClipboard.copyKeysAndFilenames(selectedItems.toArray());
            } else if (e.getSource() == copyExtendedInfoItem) {
                final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
                if( selectedItems == null ) { return; }
                CopyToClipboard.copyExtendedInfo(selectedItems.toArray());
            } else if (e.getSource() == removeSelectedFilesItem) {
                removeSelectedFiles(false); // false = only delete internally queued items (not global-only items), when triggered from the right-click menu
            } else if (e.getSource() == generateChkForSelectedFilesItem) {
                generateChkForSelectedFiles();
            } else if (e.getSource() == showSharedFileItem) {
                FrostUploadItem ulItem = modelTable.getSelectedItem();
                if( ulItem.isSharedFile() ) {
                    openFile(ulItem);
                }
            } else if (e.getSource() == removeFromGqItem) {
                removeSelectedUploadsFromGlobalQueue();
            } else if (e.getSource() == enableAllUploadsItem) {
                enableAllUploads();
            } else if (e.getSource() == disableAllUploadsItem) {
                disableAllUploads();
            } else if (e.getSource() == enableSelectedUploadsItem) {
                enableSelectedUploads();
            } else if (e.getSource() == disableSelectedUploadsItem) {
                disableSelectedUploads();
            } else if (e.getSource() == invertEnabledAllItem) {
                invertEnabledAll();
            } else if (e.getSource() == invertEnabledSelectedItem) {
                invertEnabledSelected();
            } else if (e.getSource() == startSelectedUploadsNow ) {
                startSelectedUploadsNow();
            } else if (e.getSource() == restartSelectedUploadsItem ) {
                restartSelectedUploads();
            } else if (e.getSource() == openSelectedUploadDirItem ) {
                openSelectedUploadDirectory();
            }
        }

        private void removeSelectedUploadsFromGlobalQueue() {
            if( FileTransferManager.inst().getPersistenceManager() == null ) {
                return;
            }
            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            final List<String> requestsToRemove = new ArrayList<String>();
            final List<FrostUploadItem> itemsToUpdate = new ArrayList<FrostUploadItem>();
            for( final FrostUploadItem item : selectedItems ) {
                // don't de-queue items whose data transfer to the node is currently in progress.
                // otherwise, we risk orphaning the upload in the global upload-queue since we haven't de-queued it properly.
                if( FileTransferManager.inst().getPersistenceManager().isDirectTransferInProgress(item) ) {
                    continue;
                }

                if( FileTransferManager.inst().getPersistenceManager().isItemInGlobalQueue(item) ) {
                    requestsToRemove.add( item.getGqIdentifier() );
                    itemsToUpdate.add(item);
                    item.setInternalRemoveExpected(true); // tells the persistencemanager that we're expecting this item to be removed from the global queue
                }
            }
            FileTransferManager.inst().getPersistenceManager().removeRequests(requestsToRemove);
            // lastly, start a thread which will update the state of all removed items
            new Thread() {
                @Override
                public void run() {
                    // NOTE/TODO: (slightly ugly) wait until the item has been de-queued from the
                    // global queue. this wrapper thread waits for 1 second (without locking up
                    // the GUI), so that the removal requests above have a chance to execute and
                    // so that the final "SimpleProgress" messages can be processed in time. after
                    // waiting, it then starts a GUI update thread which sets the state to "FAILED".
                    // this fixes a race condition where de-queuing the item would get it stuck in
                    // an "Uploading" state but without any global queue item running anymore. it
                    // happened whenever an FCP SimpleProgress message was waiting to be processed
                    // at the exact moment that you de-queued the file... in that case, the delayed
                    // progress msg incorrectly changed the status back to "in progress" again.
                    Mixed.wait(1000);
                    // we must perform the actual GUI updates on the GUI thread, so queue the update now
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            for( final FrostUploadItem item : itemsToUpdate ) {
                                // don't change the state to "failed" if the item was already done
                                if( item.getState() != FrostUploadItem.STATE_DONE ) {
                                    // NOTE: Frost used to set this to WAITING, but then it instantly
                                    // got set to FAILED by the subsequent FCP message indicating
                                    // that the gq item had been removed, so we just jump to FAILED
                                    // immediately to avoid the ugly status flicker in the table
                                    item.setState(FrostUploadItem.STATE_FAILED);
                                    item.setErrorCodeDescription(null); // remove any existing "failure reason" error string
                                }
                                // ensures that the file won't begin uploading again unless the user explicitly restarts it
                                item.setEnabled(false);
                                // NOTE: Frost used to set the priority to 6 ("paused") after removing
                                // them from the global queue, but that makes zero sense since the
                                // file is already disabled and won't upload again without intervention,
                                // so this has been commented out...
                                //item.setPriority(FreenetPriority.PAUSE);
                                item.fireValueChanged();
                            }
                        }
                    });
                }
            }.start();
        }

        /**
         * Generate CHK for selected files
         */
        private void generateChkForSelectedFiles() {
            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            model.generateChkItems(selectedItems);
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        private void invertEnabledAll() {
            model.setAllItemsEnabled(null);
        }

        private void disableSelectedUploads() {
            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            model.setItemsEnabled(Boolean.FALSE, selectedItems);
        }

        private void enableSelectedUploads() {
            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            model.setItemsEnabled(Boolean.TRUE, selectedItems);
        }

        private void disableAllUploads() {
            model.setAllItemsEnabled(Boolean.FALSE);
        }

        private void enableAllUploads() {
            model.setAllItemsEnabled(Boolean.TRUE);
        }

        private void restartSelectedUploads() {
            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();
            if( selectedItems == null ) { return; }
            model.restartItems(selectedItems);
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            final List<FrostUploadItem> selectedItems = modelTable.getSelectedItems();

            // if no items are selected, we won't show the menu
            if( selectedItems == null || selectedItems.size() == 0 ) {
                return;
            }

            // determine what types of items we have selected
            // local = something that's in our local file table but isn't yet in the global queue
            // global = something that's in the global queue (our own files will be there too,
            //   when they're in progress/finished)
            // external = something that's *only* in the global queue (wasn't queued by us)
            // internal = something that was queued locally (and may or may not be in the global queue yet)
            // done = anything that's finished uploading
            // failed = anything that failed uploading
            // inprogress = anything that's currently uploading
            // waiting = anything that's in the "waiting" state and hasn't begun uploading via
            //   the global queue yet; these are only local items
            // canrestartupload = means that we've selected at least 1 internally queued file
            //   that isn't in an "encoding"/"encoding requested" state
            // canpreencodeupload = means that we've selected at least 1 internally queued file
            //   that's in the "waiting" state, doesn't have a key, and isn't a "my shared files" file
            boolean selectedLocal = false;
            boolean selectedGlobal = false;
            boolean selectedExternal = false;
            boolean selectedInternal = false;
            boolean selectedDone = false;
            boolean selectedFailed = false;
            boolean selectedInProgress = false;
            boolean selectedWaiting = false;
            boolean canRestartUpload = false;
            boolean canPreEncodeUpload = false;
            final PersistenceManager pm = FileTransferManager.inst().getPersistenceManager();
            for( final FrostUploadItem ulItem : selectedItems ) {
                // first determine what item states we've selected
                final int itemState = ulItem.getState();
                if( itemState == FrostUploadItem.STATE_DONE ) {
                    selectedDone = true;
                } else if( itemState == FrostUploadItem.STATE_FAILED ) {
                    selectedFailed = true;
                } else if( itemState == FrostUploadItem.STATE_PROGRESS ) {
                    selectedInProgress = true;
                } else if( itemState == FrostUploadItem.STATE_WAITING ) {
                    selectedWaiting = true;
                }

                // now determine what type of queue item it is, and check for a few special cases
                if( ulItem.isExternal() ) {
                    selectedExternal = true;
                } else {
                    selectedInternal = true;

                    // this is an internally queued item, so let's determine if it's in a state
                    // that can start re(start) uploading
                    if( itemState == FrostUploadItem.STATE_FAILED
                            || itemState == FrostUploadItem.STATE_WAITING
                            || itemState == FrostUploadItem.STATE_PROGRESS
                            || itemState == FrostUploadItem.STATE_DONE ) {
                        canRestartUpload = true;
                    }

                    // now check if this internally queued item is in a state that can start pre-encoding
                    if( itemState == FrostUploadItem.STATE_WAITING
                        && ulItem.getKey() == null
                        && !ulItem.isSharedFile() ) {
                        canPreEncodeUpload = true;
                    }
                }
                if( pm != null && PersistenceManager.isPersistenceEnabled() && pm.isItemInGlobalQueue(ulItem) ) {
                    selectedGlobal = true;
                } else {
                    selectedLocal = true;
                }
            }

            // construct the menu based on what types of items the user has selected
            // begin by always showing the "copy keys/extended information" items
            add(copyKeysAndNamesItem);
            add(copyExtendedInfoItem);
            addSeparator();

            // only show the priority menu if there are "waiting" or "in progress" items selected
            // *and* the persistence manager is instantiated
            // NOTE: we do NOT show the menu if the items are finished, failed, or "pre-encoding
            // keys", since you can't change the priority in any of those states
            if( pm != null && PersistenceManager.isPersistenceEnabled() && ( selectedWaiting || selectedInProgress ) ) {
                add(changePriorityMenu);
                addSeparator();
            }

            // only show the "enable/disable selected upload" menu if we have at least 1 "internally queued" item selected
            if( selectedInternal ) {
                final JMenu enabledSubMenu = new JMenu(language.getString("UploadPane.fileTable.popupmenu.enableUploads") + "...");
                enabledSubMenu.add(enableSelectedUploadsItem);
                enabledSubMenu.add(disableSelectedUploadsItem);
                enabledSubMenu.add(invertEnabledSelectedItem);
                enabledSubMenu.addSeparator();
                enabledSubMenu.add(enableAllUploadsItem);
                enabledSubMenu.add(disableAllUploadsItem);
                enabledSubMenu.add(invertEnabledAllItem);
                add(enabledSubMenu);
            }

            // only show "start selected uploads immediately" if we have at least 1 "waiting" (internally queued) file
            if( selectedWaiting ) {
                add(startSelectedUploadsNow);
            }
            // only show "restart selected uploads" if we have at least 1 internally queued file
            // that is in any state *except* encoding/waiting for encode
            if( canRestartUpload ) {
                add(restartSelectedUploadsItem);
            }
            // only show "pre-calculate chks for selected files" if we have at least 1 internally
            // queued file that is valid for pre-encoding
            if( canPreEncodeUpload ) {
                add(generateChkForSelectedFilesItem);
            }
            // add a separator if any of the internal/waiting/canrestart/canpreencode blocks
            // above have been added; this ensures that selections consisting entirely of global
            // queue items don't get two separators (meaning the "copy key" or priority menu's
            // separator plus this one)
            if( selectedInternal || selectedWaiting || canRestartUpload || canPreEncodeUpload ) {
                // NOTE: we only needed to check selectedInternal since all other states in the
                // list depend on that one, but this is more explicitly clear
                addSeparator();
            }
            // only show "remove selected files" if we have at least 1 "internally queued" file
            // (regardless of its state; it can for instance be waiting without being in the global
            // queue yet, or it can even be "done" in the global queue, it doesn't matter at all)
            if( selectedInternal ) {
                add(removeSelectedFilesItem);
            }
            // only show "remove from global queue" if we have at least 1 "global queue" item
            // (either something externally queued, or an internally queued file which is on the
            // global queue); this feature allows the user to abort their ongoing uploads by
            // removing them from the global queue
            if( selectedGlobal ) {
                add(removeFromGqItem);
            }
            // display the "open this upload directory" item if they've selected 1+ internally
            // queued (non-global) items. we don't do it for global items, since those only have
            // their filename as the "path", without any actual path listed for them.
            if( selectedInternal && DesktopUtils.canPerformOpen() ) {
                addSeparator();
                add(openSelectedUploadDirItem); // only show "open" if OS supports opening folders
            }

            // if exactly 1 file is selected, then add some special menu items...
            if( selectedItems.size() == 1 ) {
                // if it's a "my shared files" item, then display another menu item
                if( selectedItems.get(0).isSharedFile() ) {
                    addSeparator();
                    add(showSharedFileItem);
                }
            }

            super.show(invoker, x, y);
        }
    }

    private class Listener extends MouseAdapter
        implements LanguageListener, KeyListener, ActionListener, MouseListener, PropertyChangeListener, ItemListener
    {
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
        	if (e.getSource() == uploadAddFilesButton) {
        		new AddNewUploadsDialog(MainFrame.getInstance()).startDialog();
        	}
        }
        @Override
        public void mousePressed(final MouseEvent e) {
            if (e.getClickCount() == 2) {
                if (e.getSource() == modelTable.getTable()) {
                    // Start file from upload table. Is this a good idea?
                	openFile(modelTable.getSelectedItem());
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
        public void itemStateChanged(final ItemEvent e) {
            // remember: the "checkbox state changed" events don't trigger during Frost's initial
            // GUI startup for any settings saved as "off", since that's the default state of new
            // checkboxes. all important logic is therefore in the "on" events, for when the
            // checkboxes become enabled (both manually by the user, and via the GUI's startup
            // loading of saved "on" settings).
            if( e.getSource() == removeFinishedUploadsCheckBox ) {
                if( removeFinishedUploadsCheckBox.isSelected() ) {
                    // this setting means that UploadManager.java will automatically clear any future finished uploads
                    Core.frostSettings.setValue(SettingsClass.UPLOAD_REMOVE_FINISHED, true);
                    model.removeFinishedUploads(); // clear existing finished uploads
                } else {
                    Core.frostSettings.setValue(SettingsClass.UPLOAD_REMOVE_FINISHED, false);
                }
            }
            if( e.getSource() == showExternalGlobalQueueItems ) {
                if( showExternalGlobalQueueItems.isSelected() ) {
                    // NOTE: we don't have to do anything other than setting this setting to true;
                    // PersistenceManager.java in turn has an event-listener for when this setting
                    // becomes true, and it then re-adds the latest list of external uploads back
                    // into the file-upload table (and if the setting is false, it never adds them
                    // to the table in the first place)
                    Core.frostSettings.setValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD, true);
                } else {
                    Core.frostSettings.setValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_UPLOAD, false);
                    // now remove all file-upload table entries that came from "external uploads" (ones initiated outside of Frost)
                    model.removeExternalUploads();
                }
            }
            if( e.getSource() == quickhealModeCheckBox ) {
                // NOTE: we don't need to do anything except save the setting when this changes
                // state, since we simply read the value of this setting whenever an upload starts
                if( quickhealModeCheckBox.isSelected() ) {
                    Core.frostSettings.setValue(SettingsClass.UPLOAD_QUICKHEAL_MODE, true);
                } else {
                    Core.frostSettings.setValue(SettingsClass.UPLOAD_QUICKHEAL_MODE, false);
                }
                // color the quickheal checkbox based on its value
                recolorQuickhealModeCheckbox();
            }
        }
    }
}
