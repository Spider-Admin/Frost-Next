/*
  DownloadPanel.java / Frost

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
package frost.fileTransfer.download;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import java.util.ListIterator;
import java.util.Map;
import java.util.logging.Logger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import javax.swing.tree.TreePath;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.SettingsUpdater;
import frost.ext.ExecuteDocument;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.KeyParser;
import frost.fileTransfer.PersistenceManager;
import frost.fileTransfer.common.FileListFileDetailsDialog;
import frost.gui.AddNewDownloadsDialog;
import frost.messaging.frost.FrostMessageObject;
import frost.messaging.frost.boards.Board;
import frost.messaging.frost.boards.TofTree;
import frost.util.CopyToClipboard;
import frost.util.DesktopUtils;
import frost.util.FileAccess;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.TextComponentClipboardMenu;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;
import frost.util.model.SortedModelTable;
import frost.util.Mixed;

@SuppressWarnings("serial")
public class DownloadPanel extends JPanel implements SettingsUpdater {

	private PopupMenuDownload popupMenuDownload = null;

	private final Listener listener = new Listener();

	private static final Logger logger = Logger.getLogger(DownloadPanel.class.getName());

	private DownloadModel model = null;

	private Language language = null;

	private final JToolBar downloadToolBar = new JToolBar();
	private final JButton downloadPasteButton = new JButton(MiscToolkit.loadImageIcon("/data/toolbar/edit-paste.png"));
	private final JButton submitDownloadTextfieldButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/document-save.png"));
	private final JButton downloadActivateButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/media-playback-start.png"));
	private final JButton downloadPauseButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/media-playback-pause.png"));
	private final JButton downloadPrefixApplyButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/document-update.png"));
	private final JButton downloadDirSelectButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/folder-open.png"));
	private final JButton downloadDirApplyButton = new JButton(MiscToolkit
			.loadImageIcon("/data/toolbar/document-update.png"));
	private final JMenu downloadDirRecentMenu = new JMenu();
	private final JTextField downloadPrefixTextField = new JTextField(30);
	private final JTextField downloadDirTextField = new JTextField(30);
	private final JTextField downloadTextField = new JTextField(30);
	private final JLabel downloadSpeedLabel = new JLabel("", SwingConstants.RIGHT); // right-aligned
	private final JLabel downloadItemCountLabel = new JLabel("", SwingConstants.LEFT);
	private final JLabel downloadQuickloadLabel = new JLabel();
	private final JLabel downloadPrefixLabel = new JLabel();
	private final JLabel downloadDirLabel = new JLabel();
	private final JCheckBox removeFinishedDownloadsCheckBox = new JCheckBox();
	private final JCheckBox showExternalGlobalQueueItems = new JCheckBox();
	private SortedModelTable<FrostDownloadItem> modelTable;

	private boolean initialized = false;

	private boolean downloadingActivated = false;
	private double speedBlocksPerMinute = 0;
	private long speedBpmInBytesPerSecond = 0;
	private int downloadItemCount = 0;

	public DownloadPanel() {
		super();
		Core.frostSettings.addUpdater(this);

		language = Language.getInstance();
		language.addLanguageListener(listener);
	}

	public DownloadTableFormat getTableFormat() {
		return (DownloadTableFormat) modelTable.getTableFormat();
	}

	/**
	 * This Document changes all newlines in the text into " @SEPARATOR@ ", and is used
	 * for the Quickload field. This is necessary if the user tries to paste
	 * multiple newline-separated keys into the box. They are only supposed to
	 * enter a single key in the quickload box and to use the "paste from
	 * clipboard" button for multiple keys, but there's no guarantee what users
	 * will do, so this is just a precaution to transparently take care of them.
	 */
	protected class HandleMultiLineKeysDocument extends PlainDocument {
		@Override
		public void insertString(final int offs, String str, final AttributeSet a) throws BadLocationException {
			str = str.replace("\r", ""); // remove windows carriage returns
			str = str.replaceAll("(?:^\n+|\n+$)", ""); // remove all leading and trailing newlines
			str = str.replaceAll("\n+", " @SEPARATOR@ "); // replace sequences of 1+ mac/win/linux newlines with separator
			super.insertString(offs, str, a);
		}
	}

	public void initialize() {
		if (!initialized) {
			refreshLanguage();

			downloadToolBar.setRollover(true);
			downloadToolBar.setFloatable(false);

			removeFinishedDownloadsCheckBox.setOpaque(false);
			showExternalGlobalQueueItems.setOpaque(false);


			MiscToolkit.configureButton(downloadPasteButton);
			MiscToolkit.configureButton(submitDownloadTextfieldButton);
			MiscToolkit.configureButton(downloadPrefixApplyButton);
			MiscToolkit.configureButton(downloadDirSelectButton);
			MiscToolkit.configureButton(downloadDirApplyButton);

			MiscToolkit.configureButton(downloadActivateButton); // play_rollover
			MiscToolkit.configureButton(downloadPauseButton); // pause_rollover

			new TextComponentClipboardMenu(downloadTextField, language);
			new TextComponentClipboardMenu(downloadPrefixTextField, language);
			final TextComponentClipboardMenu tcmenu = new TextComponentClipboardMenu(downloadDirTextField, language);

			final JPopupMenu menu = tcmenu.getPopupMenu();

			menu.addSeparator();
			menu.add(downloadDirRecentMenu);
			downloadDirRecentMenu.addMenuListener(listener);



			// Toolbar
			downloadToolBar.add(downloadActivateButton);
			downloadToolBar.add(downloadPauseButton);
			downloadToolBar.add(Box.createRigidArea(new Dimension(8, 0)));
			downloadToolBar.add(removeFinishedDownloadsCheckBox);
			if (PersistenceManager.isPersistenceEnabled()) {
				downloadToolBar.add(Box.createRigidArea(new Dimension(8, 0)));
				downloadToolBar.add(showExternalGlobalQueueItems);
			}
			downloadToolBar.add(Box.createHorizontalGlue());
			downloadToolBar.add(downloadSpeedLabel);
			downloadToolBar.add(downloadItemCountLabel);

			final GridBagConstraints gridBagConstraints = new GridBagConstraints();
			final JPanel gridBagLayout = new JPanel(new GridBagLayout());

			gridBagConstraints.anchor = GridBagConstraints.WEST;
			gridBagConstraints.fill = GridBagConstraints.NONE;
			gridBagConstraints.insets = new Insets(0, 3, 0, 3);
			gridBagConstraints.weightx = 0.0;
			gridBagConstraints.weighty = 0.0;
			gridBagConstraints.gridwidth = 1;
			gridBagConstraints.gridheight = 1;

			// Quickload
			gridBagConstraints.fill = GridBagConstraints.NONE;
			gridBagConstraints.weightx = 0.0;
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 0;
			gridBagLayout.add(downloadQuickloadLabel, gridBagConstraints);
			gridBagConstraints.gridx = 1;
			gridBagConstraints.gridy = 0;
			gridBagLayout.add(downloadTextField, gridBagConstraints);
			gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
			gridBagConstraints.weightx = 1.0;
			gridBagConstraints.gridx = 2;
			gridBagConstraints.gridy = 0;
			{
				JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
				p.add(submitDownloadTextfieldButton);
				p.add(downloadPasteButton);
				gridBagLayout.add(p, gridBagConstraints);
			}

			// Prefix
			gridBagConstraints.fill = GridBagConstraints.NONE;
			gridBagConstraints.weightx = 0.0;
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 1;
			gridBagLayout.add(downloadPrefixLabel, gridBagConstraints);
			gridBagConstraints.gridx = 1;
			gridBagConstraints.gridy = 1;
			gridBagLayout.add(downloadPrefixTextField, gridBagConstraints);
			gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
			gridBagConstraints.weightx = 1.0;
			gridBagConstraints.gridx = 2;
			gridBagConstraints.gridy = 1;
			{
				JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
				p.add(downloadPrefixApplyButton);
				gridBagLayout.add(p, gridBagConstraints);
			}

			// Download directory
			gridBagConstraints.fill = GridBagConstraints.NONE;
			gridBagConstraints.weightx = 0.0;
			gridBagConstraints.gridx = 0;
			gridBagConstraints.gridy = 2;
			gridBagLayout.add(downloadDirLabel, gridBagConstraints);
			gridBagConstraints.gridx = 1;
			gridBagConstraints.gridy = 2;
			gridBagLayout.add(downloadDirTextField, gridBagConstraints);
			gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
			gridBagConstraints.weightx = 1.0;
			gridBagConstraints.gridx = 2;
			gridBagConstraints.gridy = 2;
			{
				JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
				p.add(downloadDirSelectButton);
				p.add(downloadDirApplyButton);
				gridBagLayout.add(p, gridBagConstraints);
			}

			downloadTextField.setMinimumSize(downloadTextField.getPreferredSize());
			downloadPrefixTextField.setMinimumSize(downloadTextField.getPreferredSize());
			downloadDirTextField.setMinimumSize(downloadTextField.getPreferredSize());

			downloadTextField.setDocument(new HandleMultiLineKeysDocument());
			downloadDirTextField.setText(FrostDownloadItem.getDefaultDownloadDir()); // inits to default download dir on each startup
			cleanupDownloadDirString();

			// create the main download panel
			modelTable = new SortedModelTable<FrostDownloadItem>(model) {
				// override the default sort order when clicking different columns
				@Override
				public boolean getColumnDefaultAscendingState(final int columnNumber) {
					//#DIEFILESHARING: since filesharing is permanently disabled in Frost-Next,
					//we do not take the filesharing columns into account. if it's ever re-added,
					//this needs to be modified to shift numbers if sharing columns are visible.
					if( columnNumber == 0 || columnNumber == 2 || columnNumber == 3 || columnNumber == 4 || columnNumber == 7 || columnNumber == 9 ) {
						// sort enabled, filesize, state, blocks, lastactivity and "isDDA" descending by default
						// NOTE: the isDDA column (9) is only added if persistent global queue
						// is enabled in Frost, but it's safe to check its number this way since
						// no other columns are added *after* it if persistence is disabled,
						// so the existence/lack of that column doesn't affect any column numbers.
						return false;
					}
					return true; // all other columns: ascending
				}
			};
			new TableFindAction().install(modelTable.getTable());
			setLayout(new BorderLayout());

			final JPanel panelHeader = new JPanel(new BorderLayout());
			panelHeader.add(downloadToolBar, BorderLayout.PAGE_START);
			panelHeader.add(gridBagLayout, BorderLayout.CENTER);

			add(panelHeader, BorderLayout.NORTH);
			add(modelTable.getScrollPane(), BorderLayout.CENTER);
			fontChanged();

			// event listeners for the buttons and the file table's click and keyboard
			downloadTextField.addActionListener(listener);
			downloadPasteButton.addActionListener(listener);
			submitDownloadTextfieldButton.addActionListener(listener);
			downloadActivateButton.addActionListener(listener);
			downloadPauseButton.addActionListener(listener);
			modelTable.getScrollPane().addMouseListener(listener);
			modelTable.getTable().addKeyListener(listener);
			modelTable.getTable().addMouseListener(listener);
			removeFinishedDownloadsCheckBox.addItemListener(listener);
			showExternalGlobalQueueItems.addItemListener(listener);
			downloadPrefixApplyButton.addActionListener(listener);
			downloadPrefixTextField.addFocusListener(listener);
			downloadDirTextField.addFocusListener(listener);
			downloadDirSelectButton.addActionListener(listener);
			downloadDirApplyButton.addActionListener(listener);
			Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_NAME, listener);
			Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_SIZE, listener);
			Core.frostSettings.addPropertyChangeListener(SettingsClass.FILE_LIST_FONT_STYLE, listener);

			// set the state of the toolbar checkboxes to the user's current config state,
			// triggering the event listener for every enabled one (but not disabled ones)
			removeFinishedDownloadsCheckBox.setSelected(Core.frostSettings.getBoolValue(SettingsClass.DOWNLOAD_REMOVE_FINISHED));
			showExternalGlobalQueueItems.setSelected(Core.frostSettings.getBoolValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD));
			setDownloadingActivated(Core.frostSettings.getBoolValue(SettingsClass.DOWNLOADING_ACTIVATED));

			assignHotkeys();

			// start the "blocks per minute" speed tracking thread
			new Thread("DownloadSpeedTracker") {
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
						final List<FrostDownloadItem> itemList = model.getItems();
						for( final FrostDownloadItem dlItem : itemList ) {
							if( dlItem.getState() == FrostDownloadItem.STATE_PROGRESS ) {
								// determine how long ago the current item measurement was updated
								final long millisSinceLastMeasurement = dlItem.getMillisSinceLastMeasurement();

								// only include measurements that have been updated in the
								// last minute (aka non-stalled downloads)
								if( millisSinceLastMeasurement <= 60000 ) {
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
								setDownloadSpeed(atomicTotalBlocksPerMinute, atomicTotalBpmInBytesPerSecond);
							}
						});
					}
				}
			}.start();

			initialized = true;
		}
	}

	private Dimension calculateLabelSize(final String text) {
		final JLabel dummyLabel = new JLabel(text);
		dummyLabel.doLayout();
		return dummyLabel.getPreferredSize();
	}

	private void refreshLanguage() {
		downloadPasteButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.pasteKeys"));
		submitDownloadTextfieldButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.downloadKeys"));
		downloadActivateButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.activateDownloading"));
		downloadPauseButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.pauseDownloading"));
		removeFinishedDownloadsCheckBox.setText(language.getString("DownloadPane.removeFinishedDownloads"));
		showExternalGlobalQueueItems.setText(language.getString("DownloadPane.showExternalGlobalQueueItems"));

		downloadTextField.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.addKeys"));
		downloadPrefixTextField.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.downloadPrefix"));
		downloadDirTextField.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.downloadDir"));

		downloadPrefixApplyButton
				.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.applyDownloadPrefix"));
		downloadDirSelectButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.selectDownloadDir"));
		downloadDirApplyButton.setToolTipText(language.getString("DownloadPane.toolbar.tooltip.applyDownloadDir"));

		downloadDirRecentMenu.setText(language.getString("DownloadPane.toolbar.downloadDirMenu.setDownloadDirTo"));

		downloadQuickloadLabel.setText(language.getString("DownloadPane.toolbar.label.downloadQuickload") + ": ");
		downloadPrefixLabel.setText(language.getString("DownloadPane.toolbar.label.downloadPrefix") + ": ");
		downloadDirLabel.setText(language.getString("DownloadPane.toolbar.label.downloadDir") + ": ");

		final String blocksPerMinute = language.getString("DownloadPane.toolbar.blocksPerMinute");
		final Dimension bpmLabelSize = calculateLabelSize(blocksPerMinute + ": 99999.99 (~99999.99 MiB/s)");
		downloadSpeedLabel.setPreferredSize(bpmLabelSize);
		downloadSpeedLabel.setMinimumSize(bpmLabelSize);
		setDownloadSpeed(0, 0);

		final String waiting = language.getString("DownloadPane.toolbar.waiting");
		final Dimension itemCountLabelSize = calculateLabelSize(waiting + ": 00000");
		downloadItemCountLabel.setPreferredSize(itemCountLabelSize);
		downloadItemCountLabel.setMinimumSize(itemCountLabelSize);
		downloadItemCountLabel.setText(waiting + ": " + downloadItemCount);
	}

	public void setModel(final DownloadModel model) {
		this.model = model;
	}

	private void cleanupDownloadPrefixString() {
		String prefix = downloadPrefixTextField.getText();
		if( prefix != null && prefix.length() > 0 ) {
			prefix = Mixed.makeFilename(prefix); // replace illegal characters with underscores
			prefix = prefix.trim();
			downloadPrefixTextField.setText(prefix);
		}
	}

	private final String getDownloadPrefix() {
		cleanupDownloadPrefixString();
		final String prefix = downloadPrefixTextField.getText();

		if( prefix == null || prefix.length() == 0 ) {
			return null;
		} else {
			return prefix;
		}
	}

	private void cleanupDownloadDirString() {
		String downlDirTxt = downloadDirTextField.getText();
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
			downloadDirTextField.setText(FileAccess.appendSeparator(downlDirTxt));
		}
	}

	private final String getDownloadDir() {
		cleanupDownloadDirString();
		final String dir = downloadDirTextField.getText();

		if( dir == null || dir.length() == 0 ) {
			return null;
		} else {
			return dir;
		}
	}

	private void downloadDirSelectButton_actionPerformed(final ActionEvent e) {
		// attempt to start navigation from the "Directory" textfield path value.
		// but if that fails (due to directory not existing), we'll start navigation
		// from the default download directory instead
		String startDir = getDownloadDir();
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
		Core.frostSettings.setValue(SettingsClass.DIR_LAST_USED, selectedFolder.toString());

		// put the chosen download directory in the text field
		downloadDirTextField.setText(selectedFolder.toString()); // sets an absolute path
		cleanupDownloadDirString(); // cleans it up to a relative path (if needed) and adds trailing slash
	}

	private void applyDownloadPrefixToSelectedDownloads() {
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null ) { return; }
		if( this.model == null ) { return; } // refuse to change anything if download model is not loaded

		for( final FrostDownloadItem dlItem : selectedItems ) {
			// only apply the changes to non-external items, and only if they're *not* already finished
			if( !dlItem.isExternal() && dlItem.getState() != FrostDownloadItem.STATE_DONE ) {
				dlItem.setFilenamePrefix(getDownloadPrefix());
				// add or remove "(frost_#)" filename prefix as required to make name unique
				this.model.ensureUniqueFilename(dlItem);

				dlItem.fireValueChanged();
			}
		}
	}

	private void applyDownloadDirToSelectedDownloads() {
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null ) { return; }
		if( this.model == null ) { return; } // refuse to change anything if download model is not loaded

		for( final FrostDownloadItem dlItem : selectedItems ) {
			// only apply the changes to non-external items, and only if they're *not* already finished
			if( !dlItem.isExternal() && dlItem.getState() != FrostDownloadItem.STATE_DONE ) {
				dlItem.setDownloadDir(getDownloadDir());
				// add or remove "(frost_#)" filename prefix as required to make name unique
				this.model.ensureUniqueFilename(dlItem);

				dlItem.fireValueChanged();
			}
		}
	}

	private void downloadDirApplyButton_actionPerformed(final ActionEvent e) {
		applyDownloadDirToSelectedDownloads();
	}

	private void downloadPrefixApplyButton_actionPerformed(final ActionEvent e) {
		applyDownloadPrefixToSelectedDownloads();
	}

	/**
	 * downloadTextField Action Listener (Download/Quickload) The textfield can
	 * contain 1 key to download or multiple keys separated by " @SEPARATOR@ ".
	 */
	private void downloadTextField_actionPerformed(final ActionEvent e) {
		String keyText = downloadTextField.getText();
		if( keyText != null && keyText.length() > 0 ) {
			// decode the case-insensitive separator into newlines again; surrounding spaces are optional
			keyText = keyText.replaceAll("(?i) ?@SEPARATOR@ ?", "\n");
			// 1st true = ask to redownload duplicates, 2nd true = enable "paste more" button
			openAddNewDownloadsDialog(keyText, true, getDownloadPrefix(), getDownloadDir(), true, null);
		}
	}

	/**
	 * Get keyTyped for downloadTable
	 */
	private void downloadTable_keyPressed(final KeyEvent e) {
		final char key = e.getKeyChar();
		if (key == KeyEvent.VK_DELETE && !modelTable.getTable().isEditing()) {
			removeSelectedFiles(true); // true = delete both internally and externally (global-only) queued items
		}
	}

	/**
	 * Remove selected files from the model and global queue.
	 * @param {boolean} removeExternal - if true, it also deletes externally queued (global-only)
	 * items; otherwise it only deletes and de-queues things that were internally queued
	 */
	private void removeSelectedFiles(final boolean removeExternal) {
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null ) { return; }

		final List<String> externalRequestsToRemove = new LinkedList<String>();
		final List<FrostDownloadItem> requestsToRemove = new LinkedList<FrostDownloadItem>();
		for( final FrostDownloadItem mi : selectedItems ) {
			if( mi.isExternal() && !removeExternal ) {
				continue; // do nothing to externally queued items if we've been told to avoid those
			}
			// don't restart items whose data transfer from the node is currently in progress.
			// otherwise, we risk partially written or orphaned files, inconsistent dlItem states, etc.
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

	public boolean isDownloadingActivated() {
		return downloadingActivated;
	}

	public void setDownloadingActivated(final boolean b) {
		downloadingActivated = b;

		downloadActivateButton.setEnabled(!downloadingActivated);
		downloadPauseButton.setEnabled(downloadingActivated);

		// forces the "DOWNLOADING_ACTIVATED" setting to update instantly (instead of just on
		// Frost shutdown and forced settings updates (such as when the preferences panel opens)).
		// this is important, since the setting is read by the download-threads to determine
		// if it should start downloading the next file(s) or not.
		updateSettings();
	}

	public void setDownloadSpeed(final double newSpeedBlocksPerMinute, final long newSpeedBpmInBytesPerSecond) {
		speedBlocksPerMinute = newSpeedBlocksPerMinute;
		speedBpmInBytesPerSecond = newSpeedBpmInBytesPerSecond;

		// display the total blocks/minute and the human-readable bytes/second equivalent
		final String s = new StringBuilder().append(language.getString("DownloadPane.toolbar.blocksPerMinute")).append(": ")
			.append(String.format("%.2f", speedBlocksPerMinute)).append(" (~").append(Mixed.convertBytesToHuman(speedBpmInBytesPerSecond)).append("/s), ").toString();
		downloadSpeedLabel.setText(s);
	}

	public void setDownloadItemCount(final int newDownloadItemCount) {
		downloadItemCount = newDownloadItemCount;

		final String s = new StringBuilder().append(language.getString("DownloadPane.toolbar.waiting")).append(": ")
				.append(downloadItemCount).toString();
		downloadItemCountLabel.setText(s);
	}

	private PopupMenuDownload getPopupMenuDownload() {
		if (popupMenuDownload == null) {
			popupMenuDownload = new PopupMenuDownload();
			language.addLanguageListener(popupMenuDownload);
		}
		return popupMenuDownload;
	}

	private void showDownloadTablePopupMenu(final MouseEvent e) {
		// select row where rightclick occurred if row under mouse is NOT
		// selected
		final Point p = e.getPoint();
		final int y = modelTable.getTable().rowAtPoint(p);
		if (y < 0) {
			return;
		}
		if (!modelTable.getTable().getSelectionModel().isSelectedIndex(y)) {
			modelTable.getTable().getSelectionModel().setSelectionInterval(y, y);
		}
		getPopupMenuDownload().show(e.getComponent(), e.getX(), e.getY());
	}

	private void fontChanged() {
		final String fontName = Core.frostSettings.getValue(SettingsClass.FILE_LIST_FONT_NAME);
		final int fontStyle = Core.frostSettings.getIntValue(SettingsClass.FILE_LIST_FONT_STYLE);
		final int fontSize = Core.frostSettings.getIntValue(SettingsClass.FILE_LIST_FONT_SIZE);
		Font font = new Font(fontName, fontStyle, fontSize);
		if (!font.getFamily().equals(fontName)) {
			logger.severe("The selected font was not found in your system\n"
					+ "That selection will be changed to \"SansSerif\".");
			Core.frostSettings.setValue(SettingsClass.FILE_LIST_FONT_NAME, "SansSerif");
			font = new Font("SansSerif", fontStyle, fontSize);
		}
		modelTable.setFont(font);
	}

	private void downloadPasteButtonPressed(final ActionEvent e) {
		final String clipboardText = Mixed.getClipboardText();
		if( clipboardText == null ) { return; }
		// 1st true = ask to redownload duplicates, 2nd true = enable "paste more" button
		openAddNewDownloadsDialog(clipboardText, true, getDownloadPrefix(), getDownloadDir(), true, null);
	}

	/**
	 * Parses all keys in a string and opens an "add new downloads" dialog.
	 * @param {List} frostDownloadItemList - a list of FrostDownloadItem objects to download
	 * @param {boolean} askBeforeRedownload - if true, it will ask before redownloading keys
	 * that you've already downloaded before (matched via the download tracker).
	 * @param {String} overrideDownloadPrefix - if null, uses default prefix (none), otherwise uses
	 * this string. should ONLY be specified when downloads come from the Download Panel (via the
	 * clipboard or quickload buttons)
	 * @param {String} overrideDownloadDir - if null, uses default download directory (from the
	 * Frost preferences), otherwise this directory is used for the downloads. same as prefix,
	 * this should ONLY be specified when the downloads are coming from the Download Panel.
	 * @param {boolean} showPasteMoreButton - if true, the "Add new downloads" dialog will have
	 * a "paste more keys from clipboard" button. any keys you paste using the button will use
	 * the "override" prefix/dir (if non-null). WARNING: FOR UI CONSISTENCY, THIS SETTING IS ONLY
	 * ALLOWED TO BE TRUE WHEN IT *MAKES SENSE*: ONLY WHEN THE "ADD NEW DOWNLOADS" DIALOG IS STARTED
	 * FROM THE DOWNLOAD PANEL VIA THE QUICKLOAD OR "PASTE FROM CLIPBOARD" BUTTONS! IT MUST *NEVER*
	 * BE ENABLED WHEN THE USER JUST ADDS KEYS FROM A FROST MESSAGE! THAT WOULDN'T MAKE SENSE!
	 * @param {FrostMessageObject} associatedFrostMessageObject - can be null; if provided,
	 * the board and message id will be associated with the download item(s), so that the "View
	 * associated message" popup menu function works, and so that the "Save files into subfolders
	 * named after the boards they came from" feature works.
	 */
	public void openAddNewDownloadsDialog(
			final List<FrostDownloadItem> frostDownloadItemList,
			final boolean askBeforeRedownload,
			final String overrideDownloadPrefix,
			final String overrideDownloadDir,
			final boolean showPasteMoreButton,
			final FrostMessageObject associatedFrostMessageObject)
	{
		// verify that the key list exists and has at least 1 item
		if( frostDownloadItemList == null || frostDownloadItemList.size() == 0 ) {
			return;
		}

		// add the appropriate download dir and filename prefix
		for( final FrostDownloadItem frostDownloadItem : frostDownloadItemList ) {
			// all downloads default to using Frost's "download directory" preference and
			// no default prefix. but if we're starting a dialog that should use another prefix
			// or directory, then apply them to the downloads now.
			if( overrideDownloadPrefix != null ) {
				frostDownloadItem.setFilenamePrefix(overrideDownloadPrefix);
			}
			if( overrideDownloadDir != null ) {
				frostDownloadItem.setDownloadDir(overrideDownloadDir);
			}

			// now let's associate any Frost Message Object and board path with this download
			if( associatedFrostMessageObject != null ) {
				// makes the "View associated message" popup menu item work for this download
				frostDownloadItem.associateWithFrostMessageObject(associatedFrostMessageObject);

				// if the user has enabled "Save files into subfolders named after the boards they
				// came from" then let's add the board subfolder to the download path
				if( Core.frostSettings.getBoolValue(SettingsClass.USE_BOARDNAME_DOWNLOAD_SUBFOLDER_ENABLED) ){
					// NOTE: we don't need to add any separators since the download dir setter/getter always adds one
					frostDownloadItem.setDownloadDir(frostDownloadItem.getDownloadDir().concat(frostDownloadItem.getAssociatedBoardName()));
				}
			}
		}

		// open dialog - blocking
		new AddNewDownloadsDialog(MainFrame.getInstance()).startDialog(frostDownloadItemList, askBeforeRedownload, overrideDownloadPrefix, overrideDownloadDir, showPasteMoreButton);
	}

	/**
	 * This is just a convenience function, with one small difference in the 1st parameter:
	 *
	 * @param {String} text - the string to parse for keys, with one key per line
	 */
	public void openAddNewDownloadsDialog(
			final String text,
			final boolean askBeforeRedownload,
			final String overrideDownloadPrefix,
			final String overrideDownloadDir,
			final boolean showPasteMoreButton,
			final FrostMessageObject associatedFrostMessageObject)
	{
		// parse plaintext to get key list
		// NOTE: we don't want Freesite keys so the 2nd argument is false
		List<FrostDownloadItem> frostDownloadItemList = KeyParser.parseKeys(text, false);
		openAddNewDownloadsDialog(frostDownloadItemList, askBeforeRedownload, overrideDownloadPrefix, overrideDownloadDir, showPasteMoreButton, associatedFrostMessageObject);
	}

	private void downloadActivateButtonPressed(final ActionEvent e) {
		setDownloadingActivated(true);
	}

	private void downloadPauseButtonPressed(final ActionEvent e) {
		setDownloadingActivated(false);
	}

	private void openFile(FrostDownloadItem dlItem) {
		if (dlItem == null || dlItem.isExternal()) {
			return;
		}
		
		final File targetFile = new File(dlItem.getDownloadFilename());
		if (!targetFile.isFile()) {
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

	/*
	 * (non-Javadoc)
	 * 
	 * @see frost.SettingsUpdater#updateSettings()
	 */
	public void updateSettings() {
		Core.frostSettings.setValue(SettingsClass.DOWNLOADING_ACTIVATED, isDownloadingActivated());
	}

	public void changeItemPriorites(final List<FrostDownloadItem> items, final FreenetPriority newPrio) {
		if (items == null || items.size() == 0 || FileTransferManager.inst().getPersistenceManager() == null || !PersistenceManager.isPersistenceEnabled()) {
			return;
		}
		for (final FrostDownloadItem di : items) {
			if (di.getState() == FrostDownloadItem.STATE_PROGRESS || di.getState() == FrostDownloadItem.STATE_WAITING) {
				di.setPriority(newPrio);
				if (di.getState() == FrostDownloadItem.STATE_PROGRESS) {
					String gqid = di.getGqIdentifier();
					if (gqid != null) {
						FileTransferManager.inst().getPersistenceManager().getFcpTools().changeRequestPriority(gqid, newPrio);
					}
				}
			}
		}
	}

	private void invertEnabledSelected() {
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null ) { return; }
		model.setItemsEnabled(null, selectedItems);
	}

	private void startSelectedDownloadsNow() {
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null ) { return; }

		final List<FrostDownloadItem> itemsToStart = new LinkedList<FrostDownloadItem>();
		for( final FrostDownloadItem dlItem : selectedItems ) {
			if( dlItem.isExternal() ) {
			    continue;
			}
			if( dlItem.getState() != FrostDownloadItem.STATE_WAITING ) {
			    continue;
			}
			if( dlItem.getKey() == null ) {
				continue;
			}
			itemsToStart.add(dlItem);
		}

		for( final FrostDownloadItem dlItem : itemsToStart ) {
			dlItem.setEnabled(true);
			FileTransferManager.inst().getDownloadManager().startDownload(dlItem);
		}
	}

	private void openSelectedDownloadDirectory() {
		if( !DesktopUtils.canPerformOpen() ) { return; }
		final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
		if( selectedItems == null || selectedItems.size() < 1 ) { return; }

		// build a list of all *unique* directories within their selection
		final Map<String,File> selectedDirs = new LinkedHashMap<String,File>();
		for( final FrostDownloadItem dlItem : selectedItems ) {
			// NOTE: for downloads, we don't care if they've selected global items since
			// they still have a guessed "downloads/" path (default dir) the user may want.

			// convert the download directory string to a File object
			final String downloadDirStr = dlItem.getDownloadDir();
			if( downloadDirStr == null ) { continue; } // skip empty
			File downloadDir = new File(downloadDirStr);

			// resolve the absolute path if it was relative (doesn't check if file exists!)
			final String downloadDirAbsStr = downloadDir.getAbsolutePath();

			// skip this directory if it's already been seen
			if( selectedDirs.containsKey(downloadDirAbsStr) ) { continue; }

			// now just store the File object in the map
			selectedDirs.put(downloadDirAbsStr, downloadDir);
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
						language.formatMessage("DownloadPane.openDirectoryError.body",
							thisDir.toString()),
						language.getString("DownloadPane.openDirectoryError.title"),
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
                final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
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
                openSelectedDownloadDirectory();
            }
        };
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.SHIFT_MASK, true), "OpenDirectory");
        getActionMap().put("OpenDirectory", openDirectoryAction);

        // Shift+S - start selected downloads now
        final Action startNowAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                startSelectedDownloadsNow();
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

	private class PopupMenuDownload extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

		private final JMenuItem detailsItem = new JMenuItem();
		private final JMenuItem copyKeysAndNamesItem = new JMenuItem();
		private final JMenuItem copyExtendedInfoItem = new JMenuItem();
		private final JMenuItem disableAllDownloadsItem = new JMenuItem();
		private final JMenuItem disableSelectedDownloadsItem = new JMenuItem();
		private final JMenuItem enableAllDownloadsItem = new JMenuItem();
		private final JMenuItem enableSelectedDownloadsItem = new JMenuItem();
		private final JMenuItem invertEnabledAllItem = new JMenuItem();
		private final JMenuItem invertEnabledSelectedItem = new JMenuItem();
		private final JMenuItem removeSelectedDownloadsItem = new JMenuItem();
		private final JMenuItem restartSelectedDownloadsItem = new JMenuItem();

		private final JMenuItem startSelectedDownloadsNow = new JMenuItem();

		private final JMenuItem useThisDownloadDirItem = new JMenuItem();
		private final JMenuItem openSelectedDownloadDirItem = new JMenuItem();
		private final JMenuItem jumpToAssociatedMessage = new JMenuItem();

		private JMenu changePriorityMenu = null;
		private JMenuItem removeFromGqItem = null;

		private JMenuItem retrieveDirectExternalDownloads = null;

		public PopupMenuDownload() {
			super();
			initialize();
		}

		private void initialize() {

			if (PersistenceManager.isPersistenceEnabled()) {
				changePriorityMenu = new JMenu();
				for(final FreenetPriority priority : FreenetPriority.values()) {
					JMenuItem priorityMenuItem = new JMenuItem();
					priorityMenuItem.addActionListener(new java.awt.event.ActionListener() {
						public void actionPerformed(final ActionEvent actionEvent) {
							final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
							if( selectedItems == null ) { return; }
							changeItemPriorites(selectedItems, priority);
						}
					});
					changePriorityMenu.add(priorityMenuItem);
				}
				
				removeFromGqItem = new JMenuItem();

				removeFromGqItem.addActionListener(this);

				retrieveDirectExternalDownloads = new JMenuItem();
				retrieveDirectExternalDownloads.addActionListener(this);
			}

			refreshLanguage();

			// TODO: implement cancel of downloads

			copyKeysAndNamesItem.addActionListener(this);
			copyExtendedInfoItem.addActionListener(this);
			restartSelectedDownloadsItem.addActionListener(this);
			removeSelectedDownloadsItem.addActionListener(this);
			enableAllDownloadsItem.addActionListener(this);
			disableAllDownloadsItem.addActionListener(this);
			enableSelectedDownloadsItem.addActionListener(this);
			disableSelectedDownloadsItem.addActionListener(this);
			invertEnabledAllItem.addActionListener(this);
			invertEnabledSelectedItem.addActionListener(this);
			detailsItem.addActionListener(this);
			startSelectedDownloadsNow.addActionListener(this);
			useThisDownloadDirItem.addActionListener(this);
			openSelectedDownloadDirItem.addActionListener(this);
			jumpToAssociatedMessage.addActionListener(this);
		}

		private void refreshLanguage() {
			detailsItem.setText(language.getString("Common.details"));
			copyKeysAndNamesItem.setText(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
			copyExtendedInfoItem.setText(language.getString("Common.copyToClipBoard.copyExtendedInfo"));
			restartSelectedDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.restartSelectedDownloads"));
			removeSelectedDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.remove.removeSelectedDownloads"));
			enableAllDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.enableDownloads.enableAllDownloads"));
			disableAllDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.enableDownloads.disableAllDownloads"));
			enableSelectedDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.enableDownloads.enableSelectedDownloads"));
			disableSelectedDownloadsItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.enableDownloads.disableSelectedDownloads"));
			invertEnabledAllItem.setText(language
					.getString("DownloadPane.fileTable.popupmenu.enableDownloads.invertEnabledStateForAllDownloads"));
			invertEnabledSelectedItem
					.setText(language
							.getString("DownloadPane.fileTable.popupmenu.enableDownloads.invertEnabledStateForSelectedDownloads"));
			startSelectedDownloadsNow.setText(language
					.getString("DownloadPane.fileTable.popupmenu.startSelectedDownloadsNow"));
			useThisDownloadDirItem.setText(language.getString("DownloadPane.fileTable.popupmenu.useThisDownloadDir"));
			openSelectedDownloadDirItem.setText(language.getString("DownloadPane.fileTable.popupmenu.openSelectedDownloadDir"));
			jumpToAssociatedMessage.setText(language
					.getString("DownloadPane.fileTable.popupmenu.jumpToAssociatedMessage"));

			if (PersistenceManager.isPersistenceEnabled()) {
				changePriorityMenu.setText(language.getString("Common.priority.changePriority"));
				
				for(int itemNum = 0; itemNum < changePriorityMenu.getItemCount() ; itemNum++) {
                	changePriorityMenu.getItem(itemNum).setText(FreenetPriority.getName(itemNum));
                }
				
				removeFromGqItem.setText(language.getString("DownloadPane.fileTable.popupmenu.removeFromGlobalQueue"));

				retrieveDirectExternalDownloads.setText(language
						.getString("DownloadPane.fileTable.popupmenu.retrieveDirectExternalDownloads"));
			}
		}

		public void actionPerformed(final ActionEvent e) {
			if (e.getSource() == copyKeysAndNamesItem) {
				final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
				if( selectedItems == null ) { return; }
				CopyToClipboard.copyKeysAndFilenames(selectedItems.toArray());
			} else if (e.getSource() == copyExtendedInfoItem) {
				final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
				if( selectedItems == null ) { return; }
				CopyToClipboard.copyExtendedInfo(selectedItems.toArray());
			} else if (e.getSource() == restartSelectedDownloadsItem) {
				restartSelectedDownloads();
			} else if (e.getSource() == useThisDownloadDirItem) {
				useThisDownloadDirectory();
			} else if (e.getSource() == openSelectedDownloadDirItem) {
				openSelectedDownloadDirectory();
			} else if (e.getSource() == jumpToAssociatedMessage) {
				jumpToAssociatedMessage();
			} else if (e.getSource() == removeSelectedDownloadsItem) {
				removeSelectedFiles(false); // false = only delete internally queued items (not global-only items), when triggered from the right-click menu
			} else if (e.getSource() == enableAllDownloadsItem) {
				enableAllDownloads();
			} else if (e.getSource() == disableAllDownloadsItem) {
				disableAllDownloads();
			} else if (e.getSource() == enableSelectedDownloadsItem) {
				enableSelectedDownloads();
			} else if (e.getSource() == disableSelectedDownloadsItem) {
				disableSelectedDownloads();
			} else if (e.getSource() == invertEnabledAllItem) {
				invertEnabledAll();
			} else if (e.getSource() == invertEnabledSelectedItem) {
				invertEnabledSelected();
			} else if (e.getSource() == detailsItem) {
				showDetails();
			} else if (e.getSource() == removeFromGqItem) {
				removeSelectedDownloadsFromGlobalQueue();
			} else if (e.getSource() == retrieveDirectExternalDownloads) {
				retrieveDirectExternalDownloads();
			} else if (e.getSource() == startSelectedDownloadsNow) {
				startSelectedDownloadsNow();
			}
		}

		private void removeSelectedDownloadsFromGlobalQueue() {
			if( FileTransferManager.inst().getPersistenceManager() == null ) {
				return;
			}
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }

			final List<String> requestsToRemove = new ArrayList<String>();
			final List<FrostDownloadItem> itemsToUpdate = new ArrayList<FrostDownloadItem>();
			for( final FrostDownloadItem item : selectedItems ) {
				// don't de-queue items whose data transfer from the node is currently in progress.
				// otherwise, we risk partially written or orphaned files, inconsistent dlItem states, etc.
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
					// a "Downloading" state but without any global queue item running anymore. it
					// happened whenever an FCP SimpleProgress message was waiting to be processed
					// at the exact moment that you de-queued the file... in that case, the delayed
					// progress msg incorrectly changed the status back to "in progress" again.
					Mixed.wait(1000);
					// we must perform the actual GUI updates on the GUI thread, so queue the update now
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							for( final FrostDownloadItem item : itemsToUpdate ) {
								// don't change the state to "failed" if the item was already done
								if( item.getState() != FrostDownloadItem.STATE_DONE ) {
									// NOTE: Frost used to set this to WAITING, but that was inconsistent
									// with the uploads table; it's better to set it to FAILED just like
									// the uploads (which makes sense, since the file was removed from the
									// global queue, so it's "failed"), and then let the user manually
									// right-click and restart the download if they feel like it later
									item.setState(FrostDownloadItem.STATE_FAILED);
									item.setErrorCodeDescription(null); // remove any existing "failure reason" error string
								}
								// ensures that the file won't begin downloading again unless the user explicitly restarts it
								item.setEnabled(false);
								// NOTE: Frost used to set the priority to 6 ("paused") after removing
								// them from the global queue, but that makes zero sense since the
								// file is already disabled and won't download again without intervention,
								// so this has been commented out...
								//item.setPriority(FreenetPriority.PAUSE);
								item.fireValueChanged();
							}
						}
					});
				}
			}.start();
		}

		private void retrieveDirectExternalDownloads() {
			if (FileTransferManager.inst().getPersistenceManager() == null || !PersistenceManager.isPersistenceEnabled()) {
				return;
			}
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }

			for (final FrostDownloadItem item : selectedItems) {
				if (item.isExternal() && item.isDirect() && item.getState() == FrostDownloadItem.STATE_DONE) {
					final long expectedFileSize = item.getFileSize(); // set
					// from
					// global
					// queue
					FileTransferManager.inst().getPersistenceManager().maybeEnqueueDirectGet(item, expectedFileSize);
				}
			}
		}

		private void showDetails() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }

			if (selectedItems.size() != 1) {
				return;
			}
			if (!selectedItems.get(0).isSharedFile()) {
				return;
			}
			new FileListFileDetailsDialog(MainFrame.getInstance()).startDialog(selectedItems.get(0).getFileListFileObject());
		}

		private void invertEnabledAll() {
			model.setAllItemsEnabled(null);
		}

		private void disableSelectedDownloads() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }
			model.setItemsEnabled(Boolean.FALSE, selectedItems);
		}

		private void enableSelectedDownloads() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }
			model.setItemsEnabled(Boolean.TRUE, selectedItems);
		}

		private void disableAllDownloads() {
			model.setAllItemsEnabled(Boolean.FALSE);
		}

		private void enableAllDownloads() {
			model.setAllItemsEnabled(Boolean.TRUE);
		}

		private void restartSelectedDownloads() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }
			model.restartItems(selectedItems);
		}

		private void useThisDownloadDirectory() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }

			if( selectedItems.size() > 0 ) {
				downloadDirTextField.setText(selectedItems.get(0).getDownloadDir());
				cleanupDownloadDirString();
			}
		}

		// this very old Frost function performs an extremely simplistic jump without any
		// error checking. it forces the board selection tree to clear its selection (which
		// unloads all board messages); next, it forcibly writes the parent message's id as
		// the "previously selected message" in the tree. then it selects the board again
		// (which causes a sloooow message reload of the whole board). finally, it switches
		// to the "news" tab. this definitely *works*, but it's horribly written on many levels.
		// TODO: there is no error checking against missing messages (such as being hidden
		// due to the age of the message or the user's view-filters), and it will select the
		// wrong board in case multiple ones use the same name but different keys, and it
		// causes a very slow board message reload every time you try to jump to a message.
		// it would be much better to rewrite this to use the advanced "jump to message" thread
		// from the search dialog instead. but it's such a low-priority fix that I'll leave it
		// like this for now... these drawbacks (such as the very slow board reload) don't
		// matter as much here, since people very rarely use the "view message associated with
		// the download item" feature; whereas they use the "go to search result" feature constantly.
		private void jumpToAssociatedMessage() {
			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();
			if( selectedItems == null ) { return; }

			if( selectedItems.size() > 0 ) {
				final FrostDownloadItem item = selectedItems.get(0);
				final String boardName = item.getAssociatedBoardName();
				final String messageId = item.getAssociatedMessageId();

				if (boardName != null && messageId != null) {
					final Board board = MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getBoardByName(
							boardName);
					final TofTree t = MainFrame.getInstance().getFrostMessageTab().getTofTree();

					if (board != null && t != null) {
						t.clearSelection();
						MainFrame.getInstance().getFrostMessageTab().forceSelectMessageId(messageId);
						t.setSelectionPath(new TreePath(board.getPath()));
						MainFrame.getInstance().selectTabbedPaneTab("MainFrame.tabbedPane.news");
					}
				}
			}
		}

		public void languageChanged(final LanguageEvent event) {
			refreshLanguage();
		}

		@Override
		public void show(final Component invoker, final int x, final int y) {
			removeAll();

			final List<FrostDownloadItem> selectedItems = modelTable.getSelectedItems();

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
			// done = anything that's finished downloading
			// failed = anything that failed downloading
			// inprogress = anything that's currently downloading
			// waiting = anything that's in the "waiting" state and hasn't begun downloading via
			//   the global queue yet; these are only local items
			// canrestartdownload = means that we've selected at least 1 internally queued file
			//   that isn't in a "trying"/"decoding" state
			// canretrievedirectexternal = means that we've selected at least 1 external (globally
			//   queued), *finished*, direct download
			boolean selectedLocal = false;
			boolean selectedGlobal = false;
			boolean selectedExternal = false;
			boolean selectedInternal = false;
			boolean selectedDone = false;
			boolean selectedFailed = false;
			boolean selectedInProgress = false;
			boolean selectedWaiting = false;
			boolean canRestartDownload = false;
			boolean canRetrieveDirectExternal = false;
			final PersistenceManager pm = FileTransferManager.inst().getPersistenceManager();
			for( final FrostDownloadItem dlItem : selectedItems ) {
				// first determine what item states we've selected
				final int itemState = dlItem.getState();
				if( itemState == FrostDownloadItem.STATE_DONE ) {
					selectedDone = true;
				} else if( itemState == FrostDownloadItem.STATE_FAILED ) {
					selectedFailed = true;
				} else if( itemState == FrostDownloadItem.STATE_PROGRESS ) {
					selectedInProgress = true;
				} else if( itemState == FrostDownloadItem.STATE_WAITING ) {
					selectedWaiting = true;
				}

				// now determine what type of queue item it is, and check for a few special cases
				if( dlItem.isExternal() ) {
					selectedExternal = true;

					// this is an externally queued item, so let's determine if it's in a state
					// that can perform a direct external retrieval into frost
					if( pm != null && PersistenceManager.isPersistenceEnabled() && dlItem.isDirect() && itemState == FrostDownloadItem.STATE_DONE ) {
						canRetrieveDirectExternal = true;
					}
				} else {
					selectedInternal = true;

					// this is an internally queued item, so let's determine if it's in a state
					// that can start re(start) downloading
					if( itemState == FrostDownloadItem.STATE_FAILED
							|| itemState == FrostDownloadItem.STATE_WAITING
							|| itemState == FrostDownloadItem.STATE_PROGRESS
							|| itemState == FrostDownloadItem.STATE_DONE ) {
						canRestartDownload = true;
					}
				}
				if( pm != null && PersistenceManager.isPersistenceEnabled() && pm.isItemInGlobalQueue(dlItem) ) {
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
			// NOTE: we do NOT show the menu if the items are finished, failed or decoding, since
			// you can't change the priority in any of those states
			if( pm != null && PersistenceManager.isPersistenceEnabled() && ( selectedWaiting || selectedInProgress ) ) {
				add(changePriorityMenu);
				addSeparator();
			}

			// only show the "enable/disable selected download" menu if we have at least 1 "internally queued" item selected
			if( selectedInternal ) {
				final JMenu enabledSubMenu = new JMenu(language.getString("DownloadPane.fileTable.popupmenu.enableDownloads") + "...");
				enabledSubMenu.add(enableSelectedDownloadsItem);
				enabledSubMenu.add(disableSelectedDownloadsItem);
				enabledSubMenu.add(invertEnabledSelectedItem);
				enabledSubMenu.addSeparator();
				enabledSubMenu.add(enableAllDownloadsItem);
				enabledSubMenu.add(disableAllDownloadsItem);
				enabledSubMenu.add(invertEnabledAllItem);
				add(enabledSubMenu);
			}

			// only show "start selected downloads immediately" if we have at least 1 "waiting" (internally queued) file
			if( selectedWaiting ) {
				add(startSelectedDownloadsNow);
			}
			// only show "restart selected downloads" if we have at least 1 internally queued
			// file that is in any state *except* "trying"/decoding
			if( canRestartDownload ) {
				add(restartSelectedDownloadsItem);
			}
			// only show "retrieve direct external downloads into download directory" if we have
			// at least 1 direct-mode external download
			if( canRetrieveDirectExternal ) {
				add(retrieveDirectExternalDownloads);
			}
			// add a separator if any of the internal/waiting/canrestart/canretrievedirect blocks
			// above have been added; this ensures that selections consisting entirely of global
			// queue items don't get two separators (meaning the "copy key" or priority menu's
			// separator plus this one)
			if( selectedInternal || selectedWaiting || canRestartDownload || canRetrieveDirectExternal ) {
				addSeparator();
			}
			// only show "remove selected files" if we have at least 1 "internally queued" file
			// (regardless of its state; it can for instance be waiting without being in the global
			// queue yet, or it can even be "done" in the global queue, it doesn't matter at all)
			if( selectedInternal ) {
				add(removeSelectedDownloadsItem);
			}
			// only show "remove from global queue" if we have at least 1 "global queue" item
			// (either something externally queued, or an internally queued file which is on the
			// global queue); this feature allows the user to abort their ongoing downloads by
			// removing them from the global queue
			if( selectedGlobal ) {
				add(removeFromGqItem);
			}

			// if exactly 1 file is selected, then add some special menu items...
			if( selectedItems.size() == 1 ) {
				addSeparator();
				// add the "[use/open] this download directory" items; note that we show then even
				// if they've selected a global item - that's because even global items have a "guessed"
				// (almost always wrong) download folder in Frost, and it's nice to let the user
				// copy/open that folder name too, if they want to use it for whatever reason
				add(useThisDownloadDirItem);
				if( DesktopUtils.canPerformOpen() ) {
					add(openSelectedDownloadDirItem); // only show "open" if OS supports opening folders
				}
				// if it's a "my shared files" item, then display another menu item
				final FrostDownloadItem item = selectedItems.get(0);
				if (item.isSharedFile()) {
					addSeparator();
					add(detailsItem);
				}
				// if the key came from a message, then add the "jump" item
				if (item.getAssociatedMessageId() != null) {
					addSeparator();
					add(jumpToAssociatedMessage);
				}
			} else {
				// they have selected multiple items, so let's at least show the "open directory" feature
				if( DesktopUtils.canPerformOpen() ) {
					addSeparator();
					add(openSelectedDownloadDirItem); // only show "open" if OS supports opening folders
				}
			}

			super.show(invoker, x, y);
		}
	}

	private class Listener extends MouseAdapter implements LanguageListener, ActionListener, KeyListener,
			MouseListener, PropertyChangeListener, ItemListener, FocusListener, MenuListener {

		public Listener() {
			super();
		}

		public void languageChanged(final LanguageEvent event) {
			refreshLanguage();
		}

		public void actionPerformed(final ActionEvent e) {
			if (e.getSource() == downloadDirSelectButton) {
				downloadDirSelectButton_actionPerformed(e);
			} else if (e.getSource() == downloadPrefixApplyButton) {
				downloadPrefixApplyButton_actionPerformed(e);
			} else if (e.getSource() == downloadDirApplyButton) {
				downloadDirApplyButton_actionPerformed(e);
			} else if (e.getSource() == submitDownloadTextfieldButton) {
				downloadTextField_actionPerformed(e);
			} else if (e.getSource() == downloadTextField) {
				downloadTextField_actionPerformed(e);
			} else if (e.getSource() == downloadPasteButton) {
				downloadPasteButtonPressed(e);
			} else if (e.getSource() == downloadActivateButton) {
				downloadActivateButtonPressed(e);
			} else if (e.getSource() == downloadPauseButton) {
				downloadPauseButtonPressed(e);
			} else {
				for (int i = 0; i < downloadDirRecentMenu.getItemCount(); i++) {
					final JMenuItem item = downloadDirRecentMenu.getItem(i);
					if (e.getSource() == item) {
						downloadDirTextField.setText(item.getText());
						cleanupDownloadDirString();
					}
				}
			}
		}

		public void keyPressed(final KeyEvent e) {
			if (e.getSource() == modelTable.getTable()) {
				downloadTable_keyPressed(e);
			}
		}

		public void keyReleased(final KeyEvent e) {
		}

		public void keyTyped(final KeyEvent e) {
		}

		public void focusGained(final FocusEvent e) {
		}

		public void focusLost(final FocusEvent e) {
			if( e.getSource() == downloadDirTextField ) {
				cleanupDownloadDirString();
			} else if( e.getSource() == downloadPrefixTextField ) {
				cleanupDownloadPrefixString();
			}
		}

		@Override
		public void mousePressed(final MouseEvent e) {
			if (e.getClickCount() == 2) {
				if (e.getSource() == modelTable.getTable()) {
					// Start file from download table. Is this a good idea?
					openFile(modelTable.getSelectedItem());
				}
			} else if (e.isPopupTrigger()) {
				if ((e.getSource() == modelTable.getTable()) || (e.getSource() == modelTable.getScrollPane())) {
					showDownloadTablePopupMenu(e);
				}
			}
		}

		@Override
		public void mouseReleased(final MouseEvent e) {
			if ((e.getClickCount() == 1) && (e.isPopupTrigger())) {

				if ((e.getSource() == modelTable.getTable()) || (e.getSource() == modelTable.getScrollPane())) {
					showDownloadTablePopupMenu(e);
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
			if( e.getSource() == removeFinishedDownloadsCheckBox ) {
				if( removeFinishedDownloadsCheckBox.isSelected() ) {
					// this setting means that DownloadManager.java will automatically clear any future finished downloads
					Core.frostSettings.setValue(SettingsClass.DOWNLOAD_REMOVE_FINISHED, true);
					model.removeFinishedDownloads(); // clear existing finished downloads
				} else {
					Core.frostSettings.setValue(SettingsClass.DOWNLOAD_REMOVE_FINISHED, false);
				}
			}
			if( e.getSource() == showExternalGlobalQueueItems ) {
				if( showExternalGlobalQueueItems.isSelected() ) {
					// NOTE: we don't have to do anything other than setting this setting to true;
					// PersistenceManager.java in turn has an event-listener for when this setting
					// becomes true, and it then re-adds the latest list of external downloads back
					// into the file-download table (and if the setting is false, it never adds them
					// to the table in the first place)
					Core.frostSettings.setValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD, true);
				} else {
					Core.frostSettings.setValue(SettingsClass.GQ_SHOW_EXTERNAL_ITEMS_DOWNLOAD, false);
					// now remove all file-download table entries that came from "external downloads" (ones initiated outside of Frost)
					model.removeExternalDownloads();
				}
			}
		}

		public void menuCanceled(MenuEvent e) {
		}

		public void menuDeselected(MenuEvent e) {
		}

		public void menuSelected(MenuEvent e) {
			if (e.getSource() == downloadDirRecentMenu) {
				JMenuItem item;

				downloadDirRecentMenu.removeAll();

				item = new JMenuItem(FrostDownloadItem.getDefaultDownloadDir());
				downloadDirRecentMenu.add(item);
				item.addActionListener(this);

				final LinkedList<String> dirs = FileTransferManager.inst().getDownloadManager().getRecentDownloadDirs();
				if( dirs.size() > 0 ) {
					downloadDirRecentMenu.addSeparator();
					
					final ListIterator<String> iter = dirs.listIterator(dirs.size());
					while (iter.hasPrevious()) {
						final String dir = (String) iter.previous();
						
						item = new JMenuItem(dir);
						downloadDirRecentMenu.add(item);
						item.addActionListener(this);
					}
				}
			}
		}
	}
}
