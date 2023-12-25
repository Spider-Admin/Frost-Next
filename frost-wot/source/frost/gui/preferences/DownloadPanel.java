/*
  DownloadPanel.java / Frost
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
import java.io.*;

import javax.swing.*;

import frost.*;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.fileTransfer.download.HashBlocklistManager;
import frost.util.FileAccess;
import frost.util.DateFun;
import frost.util.gui.*;
import frost.util.gui.SmartFileFilters;
import frost.util.gui.translation.*;

@SuppressWarnings("serial")
class DownloadPanel extends JPanel {

    public class Listener implements ActionListener {
        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == browseDirectoryButton) {
                browseDirectoryPressed();
            } else if (e.getSource() == browseExecButton) {
                browseExecPressed();
            } else if (e.getSource() == blockHashFileBrowseButton) {
                blockHashFileBrowsePressed();
            } else if (e.getSource() == blockHashFileTextField || e.getSource() == blockHashForceRebuildButton) {
                forceRebuildHashDatabase(/*warnUser=*/true);
            }
        }
    }

    private JDialog owner = null;
    private SettingsClass settings = null;
    private Language language = null;
    private final HashBlocklistManager hbm = HashBlocklistManager.getInstance();

    private final JLabel priorityLabel = new JLabel();
    private final JTextField priorityTextField = new JTextField(6);
    private final JCheckBox enforceFrostPriorityFileDownload = new JCheckBox();

    private final JButton browseDirectoryButton = new JButton();
    private final JLabel directoryLabel = new JLabel();

    private final JTextField directoryTextField = new JTextField(20);

    private final Listener listener = new Listener();
    private final JLabel maxRetriesLabel = new JLabel();
    private final JTextField maxRetriesTextField = new JTextField(6);
    private final JTextField threadsTextField = new JTextField(6);
    private final JLabel threadsTextLabel = new JLabel();

    private final JCheckBox autoEnableDownloadsCheckBox = new JCheckBox();
    private final JCheckBox logDownloadsCheckBox = new JCheckBox();
    private final JCheckBox trackDownloadsCheckBox = new JCheckBox();

    private final JCheckBox useBoardnameDownloadSubfolderCheckBox = new JCheckBox();

    private final JLabel waitTimeLabel = new JLabel();
    private final JTextField waitTimeTextField = new JTextField(6);

    private final JButton browseExecButton = new JButton();
    private final JLabel execLabel = new JLabel();
    private final JTextField execTextField = new JTextField(20);

    private final JCheckBox blockHashCheckBox = new JCheckBox();
    private final JTextField blockHashFileTextField = new JTextField(20);
    private final JButton blockHashFileBrowseButton = new JButton();
    private final JLabel blockHashDatabaseInfoLabel = new JLabel();
    private final JButton blockHashForceRebuildButton = new JButton();

    /**
     * @param owner the JDialog that will be used as owner of any dialog that is popped up from this panel
     * @param settings the SettingsClass instance that will be used to get and store the settings of the panel
     */
    protected DownloadPanel(final JDialog owner, final SettingsClass settings) {
        super();

        this.owner = owner;
        this.language = Language.getInstance();
        this.settings = settings;

        initialize();
        loadSettings();
    }

    /**
     * browseDownloadDirectoryButton Action Listener (Downloads / Browse)
     */
    private void browseDirectoryPressed() {
        // attempt to start navigation from the "Directory" textfield path value.
        // but if that fails (due to directory not existing), we'll start navigation
        // from the default download directory instead
        String startDir = directoryTextField.getText();
        if( startDir != null ) {
            startDir = startDir.trim(); // remove leading and trailing whitespace
            if( !startDir.isEmpty() ) {
                final File startDirTest = new File(startDir);
                if( !startDirTest.isDirectory() ) { // must exist and be a directory
                    startDir = null;
                }
            }
        }
        if( startDir == null || startDir.isEmpty() ) {
            startDir = FrostDownloadItem.getDefaultDownloadDir();
        }

        // display a folder-only file chooser
        final JFileChooser fc = new JFileChooser(startDir);
        fc.setDialogTitle(language.getString("Options.downloads.filechooser.downloadDir.title"));
        fc.setMultiSelectionEnabled(false); // only allow single selections
        fc.setFileHidingEnabled(true); // hide hidden files
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY); // only show directories
        final int returnVal = fc.showOpenDialog(this);
        if( returnVal != JFileChooser.APPROVE_OPTION ) {
            return; // user canceled
        }
        final File selectedFolder = fc.getSelectedFile();

        // remember absolute path of last download dir used (this is used by various file chooser dialogs)
        settings.setValue(SettingsClass.DIR_LAST_USED, selectedFolder.toString());

        // clean up the directory and turn it into a relative path (if needed)
        final String relativeFolderStr = Core.pathRelativizer.relativize(selectedFolder.toString());
        if( relativeFolderStr == null ) { return; } // skip this directory if the path had illegal characters

        // put the chosen download directory in the text field (with a trailing slash)
        directoryTextField.setText(FileAccess.appendSeparator(relativeFolderStr));
    }

    private void browseExecPressed() {
        final JFileChooser fc = new JFileChooser(settings.getValue(SettingsClass.DIR_LAST_USED));
        fc.setDialogTitle(language.getString("Options.downloads.filechooser.execProgram.title"));
        fc.setFileHidingEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);

        final int returnVal = fc.showOpenDialog(owner);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            final File file = fc.getSelectedFile();
            settings.setValue(SettingsClass.DIR_LAST_USED, file.getParent());
            execTextField.setText(file.getPath());
        }
    }

    private void blockHashFileBrowsePressed() {
        // starts browsing from the .md5 file specified in the text-field, if the file exists, otherwise attempt its parent folder
        String startPath = blockHashFileTextField.getText();
        try {
            File startFile = null;
            if( startPath != null && !startPath.isEmpty() )
                startFile = new File(startPath);
            else
                startFile = new File("."); // current working dir
            if( !startFile.exists() )
                startFile = startFile.getParentFile();
            startPath = startFile.getPath();
        } catch( final Exception e ) {}

        final JFileChooser fc = new JFileChooser(startPath);
        fc.setDialogTitle(language.getString("Options.downloads.filechooser.hashFile.title"));
        fc.setFileHidingEnabled(true);
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.setAcceptAllFileFilterUsed(true); // enable the "All files" filter dropdown
        SmartFileFilters.SmartFileFilter md5Filter = new SmartFileFilters.SmartFileFilter(
                "MD5 Files",
                "MD5", // *.md5
                true);
        fc.addChoosableFileFilter(md5Filter);
        fc.setFileFilter(md5Filter);

        final int returnVal = fc.showOpenDialog(owner);
        if( returnVal == JFileChooser.APPROVE_OPTION ) {
            final File file = fc.getSelectedFile();
            blockHashFileTextField.setText(file.getPath());
            // rebuild saves the chosen filename and rebuilds the database from its contents
            forceRebuildHashDatabase(/*warnUser=*/false);
        }
    }

    private void initialize() {
        setName("DownloadPanel");
        setLayout(new GridBagLayout());
        refreshLanguage();

        //We create the components
        new TextComponentClipboardMenu(directoryTextField, language);
        new TextComponentClipboardMenu(maxRetriesTextField, language);
        new TextComponentClipboardMenu(threadsTextField, language);
        new TextComponentClipboardMenu(waitTimeTextField, language);
        new TextComponentClipboardMenu(execTextField, language);

        // Adds all of the components
        final GridBagConstraints constraints = new GridBagConstraints();
        final Insets insets0555 = new Insets(0, 5, 5, 5);

        constraints.gridy = 0;
        constraints.gridwidth = 1;
        constraints.insets = insets0555;
        constraints.anchor = GridBagConstraints.WEST;

        constraints.gridx = 0;
        add(directoryLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(directoryTextField, constraints);
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 2;
        constraints.weightx = 0.0;
        add(browseDirectoryButton, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        add(maxRetriesLabel, constraints);
        constraints.gridx = 1;
        add(maxRetriesTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        add(waitTimeLabel, constraints);
        constraints.gridx = 1;
        add(waitTimeTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        add(threadsTextLabel, constraints);
        constraints.gridx = 1;
        add(threadsTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        add(priorityLabel, constraints);
        constraints.gridx = 1;
        add(priorityTextField, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        add(autoEnableDownloadsCheckBox, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        add(trackDownloadsCheckBox, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        add(useBoardnameDownloadSubfolderCheckBox, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        constraints.anchor = GridBagConstraints.NORTHWEST;
        add(logDownloadsCheckBox, constraints);

        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        add(enforceFrostPriorityFileDownload, constraints);

        constraints.gridy++;
        constraints.gridwidth = 1;
        constraints.gridx = 0;
        add(execLabel, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(execTextField, constraints);
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 2;
        constraints.weightx = 0.0;
        add(browseExecButton, constraints);

        // hash blocking
        constraints.gridy++;
        constraints.gridx = 0;
        constraints.gridwidth = 3;
        constraints.insets = insets0555;
        add(blockHashCheckBox, constraints);

        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(blockHashFileTextField, constraints);
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 2;
        constraints.weightx = 0.0;
        add(blockHashFileBrowseButton, constraints);

        constraints.gridy++;
        constraints.gridwidth = 2;
        constraints.gridx = 0;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(blockHashDatabaseInfoLabel, constraints);
        constraints.gridwidth = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.gridx = 2;
        constraints.weightx = 0.0;
        add(blockHashForceRebuildButton, constraints);

        // glue
        constraints.gridy++;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1;
        constraints.weighty = 1;
        add(new JLabel(""), constraints);

        // Add listeners
        browseDirectoryButton.addActionListener(listener);
        browseExecButton.addActionListener(listener);
        blockHashFileTextField.addActionListener(listener);
        blockHashFileBrowseButton.addActionListener(listener);
        blockHashForceRebuildButton.addActionListener(listener);
    }

    /**
     * Load the settings of this panel
     */
    private void loadSettings() {
        directoryTextField.setText(FrostDownloadItem.getDefaultDownloadDir());
        threadsTextField.setText(settings.getValue(SettingsClass.DOWNLOAD_MAX_THREADS));
        maxRetriesTextField.setText("" + settings.getIntValue(SettingsClass.DOWNLOAD_MAX_RETRIES));
        waitTimeTextField.setText("" + settings.getIntValue(SettingsClass.DOWNLOAD_WAITTIME));
        autoEnableDownloadsCheckBox.setSelected(settings.getBoolValue(SettingsClass.DOWNLOAD_ENABLED_DEFAULT));
        logDownloadsCheckBox.setSelected(settings.getBoolValue(SettingsClass.LOG_DOWNLOADS_ENABLED));
        trackDownloadsCheckBox.setSelected(settings.getBoolValue(SettingsClass.TRACK_DOWNLOADS_ENABLED));
        useBoardnameDownloadSubfolderCheckBox.setSelected(settings.getBoolValue(SettingsClass.USE_BOARDNAME_DOWNLOAD_SUBFOLDER_ENABLED));
        priorityTextField.setText(settings.getValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD));
        enforceFrostPriorityFileDownload.setSelected(settings.getBoolValue(SettingsClass.FCP2_ENFORCE_FROST_PRIO_FILE_DOWNLOAD));
        execTextField.setText(settings.getValue(SettingsClass.EXEC_ON_DOWNLOAD));
        blockHashCheckBox.setSelected(settings.getBoolValue(SettingsClass.HASHBLOCKLIST_ENABLED));
        blockHashFileTextField.setText(settings.getValue(SettingsClass.HASHBLOCKLIST_MD5FILE));
    }

    public void ok() {
        saveSettings();
    }

    private void refreshLanguage() {
        final String minutes = language.getString("Options.common.minutes");

        waitTimeLabel.setText(language.getString("Options.downloads.waittimeAfterEachTry") + " (" + minutes + ")");
        maxRetriesLabel.setText(language.getString("Options.downloads.maximumNumberOfRetries"));

        autoEnableDownloadsCheckBox.setText(language.getString("Options.downloads.autoEnableDownloads"));
        logDownloadsCheckBox.setText(language.getString("Options.downloads.logDownloads"));
        trackDownloadsCheckBox.setText(language.getString("Options.downloads.trackDownloads"));
        
        useBoardnameDownloadSubfolderCheckBox.setText(language.getString("Options.downloads.useBoardnameDownloadSubfolder"));

        directoryLabel.setText(language.getString("Options.downloads.downloadDirectory"));
        browseDirectoryButton.setText(language.getString("Common.browse") + "...");
        threadsTextLabel.setText(language.getString("Options.downloads.numberOfSimultaneousDownloads") + " (3)");
        priorityLabel.setText(language.getString("Options.downloads.downloadPriority") + " (3)");
        enforceFrostPriorityFileDownload.setText(language.getString("Options.downloads.enforceFrostPriorityFileDownload"));

        execLabel.setText(language.getString("Options.downloads.downloadExec"));
        browseExecButton.setText(language.getString("Common.browse") + "...");

        blockHashCheckBox.setText(language.getString("Options.downloads.blockHash"));
        blockHashFileBrowseButton.setText(language.getString("Common.browse") + "...");
        blockHashForceRebuildButton.setText(language.getString("Options.downloads.blockHashForceRebuild"));
        showBlockHashPopulatingState();
    }

    public void showBlockHashPopulatingState() {
        if( hbm.isPopulating() ) {
            showBlockHashPopulating();
        } else {
            updateBlockHashDatabaseLabel();
            showBlockHashIdle();
        }
    }

    private void showBlockHashPopulating() {
        blockHashFileBrowseButton.setEnabled(false);
        blockHashForceRebuildButton.setEnabled(false);
        blockHashDatabaseInfoLabel.setText(language.getString("Options.downloads.blockHashForceRebuild.rebuilding") + "...");
    }

    private void showBlockHashIdle() {
        blockHashFileBrowseButton.setEnabled(true);
        blockHashForceRebuildButton.setEnabled(true);
    }

    private void updateBlockHashDatabaseLabel() {
        long md5DatabaseUpdateTime = hbm.getLastMD5UpdateTime();
        if( md5DatabaseUpdateTime == 0 ) {
            // display the current time if there are no hashes in the database
            md5DatabaseUpdateTime = System.currentTimeMillis();
        }
        blockHashDatabaseInfoLabel.setText(language.formatMessage("Options.downloads.blockHashDatabaseInfo",
                    DateFun.getExtendedDateAndTimeFromMillis(md5DatabaseUpdateTime),
                    hbm.getMD5HashCount()
        ));
    }

    private void forceRebuildHashDatabase(final boolean warnUser) {
        if( hbm.isPopulating() ) {
            return; // do nothing if a rebuild is in progress
        }
        if( warnUser ) {
            final int answer = MiscToolkit.showConfirmDialog(
                    owner,
                    language.getString("Options.downloads.blockHashForceRebuild.warning.body"),
                    language.getString("Options.downloads.blockHashForceRebuild.warning.title"),
                    MiscToolkit.YES_NO_OPTION,
                    MiscToolkit.INFORMATION_MESSAGE);
            if( answer != MiscToolkit.YES_OPTION ) {
                return;
            }
        }
        saveHashBlocklistSettings(); // necessary for rebuild to read proper file
        showBlockHashPopulating();
        new Thread("RebuildHashesThread") {
            @Override
            public void run() {
                // doesn't matter if the user somehow starts several of these threads,
                // because the rebuild function is synchronized so that only one runs
                // at a time.
                hbm.rebuildDB();
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        try {
                            // user may have closed the preferences and then re-opened them while
                            // a rebuild was still in progress, so let's update the *current* dialog
                            // instance if one exists...
                            final OptionsFrame of = MainFrame.getInstance().getVisibleOptionsFrame();
                            if( of != null ) {
                                of.threadUpdateBlockHashState();
                            }
                        } catch( final Exception ex ) {} // ignore GUI errors, if any
                    }
                });
            }
        }.start();
    }

    private void saveHashBlocklistSettings() {
        settings.setValue(SettingsClass.HASHBLOCKLIST_ENABLED, blockHashCheckBox.isSelected());
        settings.setValue(SettingsClass.HASHBLOCKLIST_MD5FILE, blockHashFileTextField.getText());
    }

    /**
     * Save the settings of this panel
     */
    private void saveSettings() {
        // clean up and validate the syntax of the text field and make sure it's relative if needed
        String downlDirTxt = directoryTextField.getText();
        if( downlDirTxt != null ) {
            // remove leading/trailing whitespace
            downlDirTxt = downlDirTxt.trim();

            if( !downlDirTxt.isEmpty() ) {
                // they've provided a custom path, so let's relativize it
                downlDirTxt = Core.pathRelativizer.relativize(downlDirTxt);
            }

            // if the relativization rejected the path, or if they've provided no path,
            // then just fall back to the current settings value (or if that's invalid
            // too, then it uses the original built-in default directory, "downloads/")
            if( downlDirTxt == null || downlDirTxt.isEmpty() ) {
                downlDirTxt = FrostDownloadItem.getDefaultDownloadDir();
            }

            // always append a fileseparator to the end of string if necessary
            // NOTE: we accept non-existent folders, since Frost automatically creates them when queuing/saving downloads
            settings.setValue(SettingsClass.DIR_DOWNLOAD, FileAccess.appendSeparator(downlDirTxt));
        }
        settings.setValue(SettingsClass.DOWNLOAD_MAX_THREADS, threadsTextField.getText());

        settings.setValue(SettingsClass.DOWNLOAD_MAX_RETRIES, maxRetriesTextField.getText());
        settings.setValue(SettingsClass.DOWNLOAD_WAITTIME, waitTimeTextField.getText());

        settings.setValue(SettingsClass.DOWNLOAD_ENABLED_DEFAULT, autoEnableDownloadsCheckBox.isSelected());
        settings.setValue(SettingsClass.LOG_DOWNLOADS_ENABLED, logDownloadsCheckBox.isSelected());
        settings.setValue(SettingsClass.TRACK_DOWNLOADS_ENABLED, trackDownloadsCheckBox.isSelected());
        settings.setValue(SettingsClass.USE_BOARDNAME_DOWNLOAD_SUBFOLDER_ENABLED, useBoardnameDownloadSubfolderCheckBox.isSelected());
        settings.setValue(SettingsClass.FCP2_DEFAULT_PRIO_FILE_DOWNLOAD, priorityTextField.getText());
        settings.setValue(SettingsClass.FCP2_ENFORCE_FROST_PRIO_FILE_DOWNLOAD, enforceFrostPriorityFileDownload.isSelected());

        settings.setValue(SettingsClass.EXEC_ON_DOWNLOAD, execTextField.getText());

        saveHashBlocklistSettings();
    }
}
