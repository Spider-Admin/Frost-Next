/*
  ManageTrackedDownloads.java / Frost
  Copyright (C) 2010  Frost Project <jtcfrost.sourceforge.net>

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
package frost.gui;


import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.JFrame;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import frost.*;
import frost.MainFrame;
import frost.fcp.*;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.KeyParser;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.gui.model.*;
import frost.storage.perst.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.*;

public class ManageTrackedDownloads extends JFrame {
	private final Language language;
	private final TrackDownloadKeysStorage trackDownloadKeysStorage;

	private TrackedDownloadsModel trackedDownloadsModel;
	private TrackedDownloadsTable trackedDownloadsTable;

	private JTextField maxAgeTextField;
	private JButton maxAgeButton;
	private JTextField filterKeysTextField;
	private JButton addKeysButton;
	private JButton refreshButton;
	private JButton closeButton;

	private JSkinnablePopupMenu tablePopupMenu;

	private static final long serialVersionUID = 1L;

	private boolean isShown = false;

	// a list of all keys, needed as data source when we filter in the table
	private List<TrackedDownloadTableMember> allKnownKeysList;

	public ManageTrackedDownloads() {
		super();

		language = Language.getInstance();
		trackDownloadKeysStorage = TrackDownloadKeysStorage.inst();

		// we want the frame to stay alive even after it's closed
		// (but we'll manually unload the table data to save memory)
		setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

		initGUI();
	}

	private void initGUI() {
		try {
			setTitle(language.getString("ManageDownloadTrackingDialog.title"));
			setSize(800, 600);
			this.setResizable(true);

			// Max Age
			final JLabel maxAgeLabel = new JLabel(language.getString("ManageDownloadTrackingDialog.button.maxAge"));
			maxAgeTextField = new JTextField(6);
			new TextComponentClipboardMenu(maxAgeTextField, language);
			maxAgeTextField.setText("100");
			maxAgeTextField.setMaximumSize(new Dimension(30,25));
			maxAgeButton = new JButton(language.getString("ManageDownloadTrackingDialog.button.maxAgeButton"));
			maxAgeButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					maxAgeButton_actionPerformed();
				}
			});
			maxAgeButton.setToolTipText(language.getString("ManageDownloadTrackingDialog.buttonTooltip.maxAgeButton"));

			// Filter keys
			final JLabel filterKeysLabel = new JLabel(language.getString("ManageDownloadTrackingDialog.button.filterKeys") + ":");
			filterKeysTextField = new JTextField(20); // 20 columns preferred size
			new TextComponentClipboardMenu(filterKeysTextField, language);
			filterKeysTextField.setText("");
			filterKeysTextField.setMaximumSize(new Dimension(60,25));
			filterKeysTextField.getDocument().addDocumentListener(new DocumentListener() {
				public void changedUpdate(final DocumentEvent e) {
					filterContentChanged();
				}
				public void insertUpdate(final DocumentEvent e) {
					filterContentChanged();
				}
				public void removeUpdate(final DocumentEvent e) {
					filterContentChanged();
				}
			});

			// Load files
			addKeysButton = new JButton(language.getString("ManageDownloadTrackingDialog.button.addKeys"));
			addKeysButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addKeysButton_actionPerformed();
				}
			});
			addKeysButton.setToolTipText(language.getString("ManageDownloadTrackingDialog.buttonTooltip.addKeys"));

			// Refresh Button
			refreshButton = new JButton(language.getString("ManageDownloadTrackingDialog.button.refresh"));
			refreshButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					loadTrackedDownloadsIntoTable();
				}
			});

			// Close Button
			closeButton = new JButton(language.getString("Common.close"));
			closeButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					hideDialog();
				}
			});

			// Button panel: row 1
			final JPanel buttonsPanelRow1 = new JPanel();
			buttonsPanelRow1.setLayout(new BoxLayout( buttonsPanelRow1, BoxLayout.X_AXIS ));

			buttonsPanelRow1.add( filterKeysLabel );
			buttonsPanelRow1.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanelRow1.add( filterKeysTextField );

			buttonsPanelRow1.add( Box.createHorizontalGlue() );

			buttonsPanelRow1.add(Box.createRigidArea(new Dimension(20,3)));
			buttonsPanelRow1.add( addKeysButton );

			// Button panel: row 2
			final JPanel buttonsPanelRow2 = new JPanel();
			buttonsPanelRow2.setLayout(new BoxLayout( buttonsPanelRow2, BoxLayout.X_AXIS ));

			buttonsPanelRow2.add( maxAgeLabel );
			buttonsPanelRow2.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanelRow2.add( maxAgeTextField );
			buttonsPanelRow2.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanelRow2.add( maxAgeButton );

			buttonsPanelRow2.add( Box.createHorizontalGlue() );

			buttonsPanelRow2.add(Box.createRigidArea(new Dimension(20,3)));
			buttonsPanelRow2.add( refreshButton );
			buttonsPanelRow2.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanelRow2.add( closeButton );

			// Button panel
			final JPanel buttonsPanel = new JPanel();
			buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.Y_AXIS));
			buttonsPanel.add(buttonsPanelRow1);
			buttonsPanel.add(Box.createRigidArea(new Dimension(1,2))); // 2px between rows
			buttonsPanel.add(buttonsPanelRow2);

			// Download Table
			trackedDownloadsModel = new TrackedDownloadsModel();
			trackedDownloadsTable = new TrackedDownloadsTable( trackedDownloadsModel );
			new TableFindAction().install(trackedDownloadsTable);
			trackedDownloadsTable.setRowSelectionAllowed(true);
			trackedDownloadsTable.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			trackedDownloadsTable.setRowHeight(18);
			final JScrollPane scrollPane = new JScrollPane(trackedDownloadsTable);
			scrollPane.setWheelScrollingEnabled(true);

			// main panel
			final JPanel mainPanel = new JPanel(new BorderLayout());
			mainPanel.add( scrollPane, BorderLayout.CENTER );
			mainPanel.setBorder(BorderFactory.createEmptyBorder(5,7,7,7));

			this.getContentPane().setLayout(new BorderLayout());
			mainPanel.add( buttonsPanel, BorderLayout.SOUTH );
			this.getContentPane().add(mainPanel, null);

			// init popup menu
			this.initPopupMenu();

			// init hotkeys
			this.initHotKeyListener();

			// frame icon
			final ImageIcon frameIcon = MiscToolkit.loadImageIcon("/data/toolbar/document-properties.png");
			this.setIconImage(frameIcon.getImage());

			// when the user closes the dialog, we simply hide the window and unload all table data
			// to free up the memory
			this.addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(final WindowEvent e) {
					hideDialog();
				}
			});
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	private void hideDialog() {
		// hide the frame
		isShown = false;
		ManageTrackedDownloads.this.setVisible(false);
		// unload all table data to free all the memory currently used by the model
		ManageTrackedDownloads.this.trackedDownloadsModel.clearDataModel();
	}


	private void initHotKeyListener() {
		this.trackedDownloadsTable.addKeyListener(new KeyListener() {
			@Override
			public void keyTyped(KeyEvent e) {
				// Nothing to do
			}

			@Override
			public void keyReleased(KeyEvent e) {
				if( ! e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_DELETE) {
					// remove selected
					removeSelectedKeys();
				} else if( e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_DELETE) {
					// remove all from same board
					removeAllSameBoard();
				} else if( e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_R) {
					// refresh
					loadTrackedDownloadsIntoTable();
				} else if( ! e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_D) {
					// download selected again
					downloadSelectedAgain();
				}
			}

			@Override
			public void keyPressed(KeyEvent e) {
				// Nothing to do
			}
		});
	}

	private void initPopupMenu() {
		tablePopupMenu = new JSkinnablePopupMenu();

		// copy keys with filenames
		final JMenuItem copyKeysAndNamesMenuItem = new JMenuItem(language.getString("Common.copyToClipBoard.copyKeysWithFilenames"));
		copyKeysAndNamesMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				copySelectedKeysAndNames();
			}
		});

		// download selected keys again
		final JMenuItem downloadAgainMenuItem = new JMenuItem(language.getString("ManageDownloadTrackingDialog.button.downloadAgain"));
		downloadAgainMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				downloadSelectedAgain();
			}
		});

		// remove selected keys
		final JMenuItem removeMenuItem = new JMenuItem(language.getString("ManageDownloadTrackingDialog.button.remove"));
		removeMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				removeSelectedKeys();
			}
		});

		// remove all from same Board
		final JMenuItem removeSameBoardMenuItem = new JMenuItem(language.getString("ManageDownloadTrackingDialog.button.removeSameBoard"));
		removeSameBoardMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				removeAllSameBoard();
			}
		});

		// Compose popup menu
		tablePopupMenu.add(copyKeysAndNamesMenuItem);
		tablePopupMenu.add(downloadAgainMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(removeMenuItem);
		tablePopupMenu.add(removeSameBoardMenuItem);

		this.trackedDownloadsTable.addMouseListener(new TablePopupMenuMouseListener());
	}

	public void showDialog() {
		// now just bring the frame to the front if it's currently open
		if( isShown ) {
			toFront();
			return;
		}

		// otherwise (re)load the latest table contents, center the frame relative to Frost's main
		// window, and make the frame visible on screen.
		loadTrackedDownloadsIntoTable();
		setLocationRelativeTo( MainFrame.getInstance() );
		isShown = true;
		setVisible(true); // blocking!
	}

	/**
	 * Called every time we want to load/refresh the list of known keys.
	 *
	 * Stores a copy in "allKnownKeysList" used for filtering.
	 */
	private void loadTrackedDownloadsIntoTable() {
		allKnownKeysList = new LinkedList<TrackedDownloadTableMember>();
		this.trackedDownloadsModel.clearDataModel();
		filterKeysTextField.setText(""); // NOTE: if this had a value, we cause a 2nd model clear but nothing else since the "all keys" list is empty
		for( final TrackDownloadKeys trackDownloadkey : trackDownloadKeysStorage.getDownloadKeyList()) {
			final TrackedDownloadTableMember trackedDownloadTableMember = new TrackedDownloadTableMember(trackDownloadkey);
			this.trackedDownloadsModel.addRow(trackedDownloadTableMember);
			allKnownKeysList.add(trackedDownloadTableMember);
		}
	}

	/**
	 * Called whenever the content of the filter text field changes
	 */
	private void filterContentChanged() {
		try {
			String txt = filterKeysTextField.getDocument().getText(0, filterKeysTextField.getDocument().getLength()).trim();
			txt = txt.toLowerCase();
			// filter: show only keys that have this txt somewhere in the filename
			// NOTE: if automatic tracking is enabled, the filenames we look at have the user's rename/prefix
			this.trackedDownloadsModel.clearDataModel();
			for( final TrackedDownloadTableMember tm : allKnownKeysList ) {
				if( txt.length() > 0 ) {
					final String fileName = tm.getTrackDownloadKeys().getFileName().toLowerCase();
					if( fileName.indexOf(txt) < 0 ) {
						continue;
					}
				}
				this.trackedDownloadsModel.addRow(tm);
			}
		} catch(final Exception ex) {}
	}

	private void copySelectedKeysAndNames() {
		final int[] selectedRows = trackedDownloadsTable.getSelectedRows();

		if( selectedRows.length > 0 ) {
			final TrackDownloadKeys[] items = new TrackDownloadKeys[selectedRows.length];

			for( int i=0; i < selectedRows.length; ++i ) {
				final int rowIdx = selectedRows[i];

				if( rowIdx >= trackedDownloadsModel.getRowCount() ) {
					continue; // paranoia
				}

				final TrackedDownloadTableMember row = (TrackedDownloadTableMember) trackedDownloadsModel.getRow(rowIdx);
				items[i] = row.getTrackDownloadKeys();
			}

			// now just tell the clipboard handler to copy the keys
			CopyToClipboard.copyKeysAndFilenames(items);
		}
	}

	private void downloadSelectedAgain() {
		final int[] selectedRows = trackedDownloadsTable.getSelectedRows();
		if( selectedRows.length < 1 ) { return; }

		// we'll be building a list of download items based on the original key-names,
		// so that we ignore the possibly-prefixed/renamed user filenames.
		ArrayList<FrostDownloadItem> frostDownloadItemList = new ArrayList<FrostDownloadItem>();
		final KeyParser.ParseResult result = new KeyParser.ParseResult();

		for( int i=0; i < selectedRows.length; ++i ) {
			final int rowIdx = selectedRows[i];

			if( rowIdx >= trackedDownloadsModel.getRowCount() ) {
				continue; // paranoia
			}

			final TrackedDownloadTableMember row = (TrackedDownloadTableMember) trackedDownloadsModel.getRow(rowIdx);
			final String key = row.getTrackDownloadKeys().getKey();
			if( key != null ) {
				// attempt to parse and validate the current key and extract its real filename
				// NOTE: we don't want Freesite keys so the 2nd argument is false
				KeyParser.parseKeyFromLine(key, false, result);

				// if this key was valid, then create a new download object for it
				if( result.key != null ) {
					frostDownloadItemList.add(new FrostDownloadItem(result.fileName, result.key));
				}
			}
		}

		// now tell the download manager to open an "add new downloads" dialog, with the
		// "ask before redownloading previously downloaded keys" flag set to false, since
		// the user is willingly re-adding downloads from the "already downloaded" list!
		// we also provide null prefix/dir overrides, since we want these files to use
		// the default download directory and no default prefix.
		if( frostDownloadItemList.size() > 0 ) {
			FileTransferManager.inst().getDownloadManager().getPanel().openAddNewDownloadsDialog(frostDownloadItemList, false, null, null, false, null);
		}
	}

	private void removeSelectedKeys() {
		final int[] selectedRows = trackedDownloadsTable.getSelectedRows();
        if( selectedRows.length < 1 ) { return; }

        // confirm that they want to delete all selected keys
        final int answer = MiscToolkit.showConfirmDialog(
                ManageTrackedDownloads.this,
                language.formatMessage("ManageDownloadTrackingDialog.confirmDeleteSelected.body",
                    Integer.toString(selectedRows.length)),
                language.getString("ManageDownloadTrackingDialog.confirmDeleteSelected.title"),
                MiscToolkit.YES_NO_OPTION,
                MiscToolkit.QUESTION_MESSAGE);
        if( answer != MiscToolkit.YES_OPTION ) {
            return;
        }

        // delete all selected keys
        for( int z = selectedRows.length - 1; z > -1; z-- ) {
            final int rowIx = selectedRows[z];

            if( rowIx >= trackedDownloadsModel.getRowCount() ) {
                continue; // paranoia
            }

            final TrackedDownloadTableMember row = (TrackedDownloadTableMember) trackedDownloadsModel.getRow(rowIx);
            trackDownloadKeysStorage.removeItemByKey(row.getTrackDownloadKeys().getKey());
            trackedDownloadsModel.deleteRow(row);
        }
        trackedDownloadsTable.clearSelection();
	}

	private void removeAllSameBoard() {
        // determine what board they've selected (1st selected item)
		final int selectedRowIdx = trackedDownloadsTable.getSelectedRow();
		if( selectedRowIdx < 0 || selectedRowIdx >= trackedDownloadsModel.getRowCount()) {
			return;
		}
		final TrackedDownloadTableMember selectedRow = (TrackedDownloadTableMember) trackedDownloadsModel.getRow(selectedRowIdx);
		if( selectedRow == null ) {
			return;
		}
		final String boardName = selectedRow.getTrackDownloadKeys().getBoardName();
		if( boardName == null || boardName.length() == 0 ) {
			return; // abort if this key has no board associated with it, to avoid deleting all boardless items
		}

        // confirm that they want to delete all from this board
        final int answer = MiscToolkit.showConfirmDialog(
                ManageTrackedDownloads.this,
                language.formatMessage("ManageDownloadTrackingDialog.confirmDeleteBoard.body",
                    boardName),
                language.getString("ManageDownloadTrackingDialog.confirmDeleteBoard.title"),
                MiscToolkit.YES_NO_OPTION,
                MiscToolkit.QUESTION_MESSAGE);
        if( answer != MiscToolkit.YES_OPTION ) {
            return;
        }

        // delete all items from that board
		for( int z = trackedDownloadsModel.getRowCount() -1 ; z >= 0; z--) {
			final TrackedDownloadTableMember row = (TrackedDownloadTableMember) trackedDownloadsModel.getRow(z);
			final String thisBoardName = row.getTrackDownloadKeys().getBoardName();
			if( thisBoardName != null && boardName.compareTo(thisBoardName) == 0 ) {
				trackDownloadKeysStorage.removeItemByKey(row.getTrackDownloadKeys().getKey());
				trackedDownloadsModel.deleteRow(row);
			}
		}
		trackedDownloadsTable.clearSelection();
	}

	private void addKeysButton_actionPerformed() {
		// Open choose Directory dialog (browsing to "localdata/" by default)
		// NOTE: We do NOT save this one to the "DIR_LAST_USED" setting since we usually only
		// navigate to the "localdata/" folder here to get our own logs.
		final JFileChooser fc = new JFileChooser(Core.frostSettings.getValue(SettingsClass.DIR_LOCALDATA));
		fc.setDialogTitle(language.getString("AddNewDownloadsDialog.changeDirDialog.title"));
		fc.setMultiSelectionEnabled(false); // only allow single selections
		fc.setFileHidingEnabled(true); // hide hidden files
		fc.setFileSelectionMode(JFileChooser.FILES_ONLY); // only accept files (not directories)
		final int returnVal = fc.showOpenDialog(this);
		if( returnVal != JFileChooser.APPROVE_OPTION ) {
			return; // user canceled
		}
		final File selectedFile = fc.getSelectedFile();

		try (
			// NOTE: Java 7+ try-with-resources (autocloseable)
			final FileReader fileReader = new FileReader(selectedFile);
			final BufferedReader bufferedReader = new BufferedReader(fileReader);
		) {
			String strLine;
			final KeyParser.ParseResult result = new KeyParser.ParseResult();

			// SUPPORT FOR IMPORTING FUQID DOWNLOAD LOG FILES:
			// Fuqid has a couple of formats; the Fuqid-Keys.log and Fuqid-InsertOptions.txt files
			// have clean, non-URL encoded filenames. But the Fuqid-Downloads.log uses URL-encoded
			// keys with silly "|" pipes after each filename, such as "CHK@../file%20name.txt|file name.txt",
			// so if we detect such a line format within the first 20 lines of a file, we'll ask the
			// user if it's a Fuqid download log. we do this advanced check instead of just checking
			// the filename, since the user may have renamed their log from the default Fuqid log name.
			// note that the "20 lines" thing is just an optimization and precaution; the Fuqid download
			// log format has one name per line and will have a result on line 1, but we check 20 for safety.
			boolean treatAsFuqidDownloadLog = false;
			boolean askedUserAboutFuqidDownloadLog = false;
			long lineCount = 0;

			while( (strLine = bufferedReader.readLine()) != null ) {
				++lineCount;

				// first 20 lines: check each line to see if this is a Fuqid download log with silly pipes
				if( lineCount <= 20 && !askedUserAboutFuqidDownloadLog ) {
					// NOTE: the reason we match "[^a-zA-Z0-9]*" (optional non-alphanumeric) at the start
					// of the line is because Fuqid puts some UTF byte order marks at the start of its logs.
					// but we don't want to simply skip through any actual text letters until the first key
					// occurrence on the line, since that risks giving false positives with other logs.
					// for instance, the Frost-Next download logs always have a [<date>] timestamp prefix,
					// and the upload logs have lots of extra info, so they'll never match this even
					// if there are pipes in their filenames.
					if( strLine.matches("^[^a-zA-Z0-9]*(?:CHK|USK|SSK|KSK)@.+?\\/.+?\\|.+?") ) {
						// this seems like it might be a Fuqid log (the only other thing it could be
						// is an old legacy Frost log which happened to have a pipe in a filename),
						// so now just ask the user whether we should treat this as a Fuqid log
						final int answer = MiscToolkit.showConfirmDialog(
								ManageTrackedDownloads.this, // parented to the tracking window
								language.getString("ManageDownloadTrackingDialog.detectedFuqidLog.body"),
								language.getString("ManageDownloadTrackingDialog.detectedFuqidLog.title"),
								MiscToolkit.YES_NO_OPTION,
								MiscToolkit.QUESTION_MESSAGE);
						if( answer == MiscToolkit.YES_OPTION ) {
							treatAsFuqidDownloadLog = true;
						}

						// don't ask again while parsing the rest of this log
						askedUserAboutFuqidDownloadLog = true;
					}
				}

				// if this is a Fuqid log, they've got a pipe after every filename. this is done even
				// when the name didn't need encoding (i.e. "CHK@../example.txt|example.txt"). we'll
				// simply do exactly the same parsing the official Fuqid application does: split on
				// "|" (pipe) and grab the key to the left of the first pipe character. this is safe,
				// since there will never be any pipes in Fuqid's URL-encoded key portion of the line.
				// ALSO NOTE: the encoding of the Fuqid logs seems to be UTF-8, but it doesn't matter;
				// the key and pipe are only using the lower 128 ASCII characters.
				if( treatAsFuqidDownloadLog ) {
					final int pos = strLine.indexOf("|");
					if( pos > 0 ) { // only if the pipe exists on this line and isn't the 1st char
						strLine = strLine.substring(0, pos);
					}
				}

				// attempt to extract a key from the current line
				// NOTE: we don't want Freesite keys so the 2nd argument is false
				KeyParser.parseKeyFromLine(strLine, false, result);

				// if this line contained a valid key, add it to the list of tracked downloads
				if( result.key != null ) {
					// NOTE: we use a 0 as the "file size" parameter since we don't know the size of the logged download
					// NOTE: this will never add duplicates of existing keys, since it's indexed by key
					trackDownloadKeysStorage.storeItem(new TrackDownloadKeys(result.key, result.fileName, "", 0, System.currentTimeMillis()));
				}
			}
		} catch( final Exception e ) {
			e.printStackTrace();
		}
		loadTrackedDownloadsIntoTable();
	}

	private void maxAgeButton_actionPerformed() {
		int max_age = -1;
		try {
			max_age = Integer.parseInt(this.maxAgeTextField.getText());
		} catch( final NumberFormatException ex ) {
			return;
		}

		if( max_age <= 0) {
			return;
		}

		trackDownloadKeysStorage.cleanupTable(max_age);
		loadTrackedDownloadsIntoTable();
	}
	
	private static class TrackedDownloadsModel extends SortedTableModel<TrackedDownloadTableMember> {
		private static final long serialVersionUID = 1L;

		private Language language = null;

		protected final static String columnNames[] = new String[5];

		protected final static Class<?> columnClasses[] =  {
			String.class,
			String.class,
			String.class,
			String.class,
			String.class
		};

		public TrackedDownloadsModel() {
			super();
			assert columnClasses.length == columnNames.length;
			language = Language.getInstance();
			refreshLanguage();
		}

		private void refreshLanguage() {
			columnNames[0] = language.getString("ManageDownloadTrackingDialog.table.name");
			columnNames[1] = language.getString("ManageDownloadTrackingDialog.table.key");
			columnNames[2] = language.getString("ManageDownloadTrackingDialog.table.board");
			columnNames[3] = language.getString("ManageDownloadTrackingDialog.table.size");
			columnNames[4] = language.getString("ManageDownloadTrackingDialog.table.finished");
		}

		public boolean isCellEditable(int row, int col) {
			return false;
		}

		public String getColumnName(int column) {
			if( column >= 0 && column < columnNames.length )
				return columnNames[column];
			return null;
		}

		public int getColumnCount() {
			return columnNames.length;
		}

		public Class<?> getColumnClass(int column) {
			if( column >= 0 && column < columnClasses.length )
				return columnClasses[column];
			return null;
		}
	}
	

	private class TrackedDownloadTableMember extends TableMember.BaseTableMember<TrackedDownloadTableMember> {

		TrackDownloadKeys trackDownloadKey;

		public TrackedDownloadTableMember(final TrackDownloadKeys trackDownloadkey){
			this.trackDownloadKey = trackDownloadkey;
		}

		public Comparable<?> getValueAt(final int column) {
			switch( column ) {
				case 0:
					return trackDownloadKey.getFileName();
				case 1:
					return trackDownloadKey.getKey();
				case 2:
					return trackDownloadKey.getBoardName();
				case 3:
					return FormatterUtils.formatSize(trackDownloadKey.getFileSize());
				case 4:
					// Build a String of format yyyy.mm.dd hh:mm:ssGMT
					final long date = trackDownloadKey.getDownloadFinishedTime();
					return DateFun.getExtendedDateAndTimeFromMillis(date);
				default :
					throw new RuntimeException("Unknown Column pos");
			}
		}

		public TrackDownloadKeys getTrackDownloadKeys() {
			return this.trackDownloadKey;
		}

		@Override
		public int compareTo(final TrackedDownloadTableMember otherTableMember, final int tableColumnIndex) {
			// override sorting of specific columns
			if( tableColumnIndex == 3 || tableColumnIndex == 4 ) { // filesize or completion time
				// if the other table member object is null, just return -1 instantly so that the non-null
				// member we're comparing will be sorted above the null-row. this is just a precaution.
				// NOTE: we could also make sure "trackDownloadKey" is non-null for each, but it always is.
				if( otherTableMember == null ) { return -1; }

				// we know that both filesize and completion time are longs, so we don't need
				// any null-checks since longs are a basic type (never null).
				if( tableColumnIndex == 3 ) { // col 3: filesize
					return Mixed.compareLong(trackDownloadKey.getFileSize(), otherTableMember.getTrackDownloadKeys().getFileSize());
				} else { // col 4: completion time
					return Mixed.compareLong(trackDownloadKey.getDownloadFinishedTime(), otherTableMember.getTrackDownloadKeys().getDownloadFinishedTime());
				}
			} else {
				// all other columns use the default case-insensitive string comparator
				return super.compareTo(otherTableMember, tableColumnIndex);
			}
		}
	}

	private class TablePopupMenuMouseListener implements MouseListener {
		public void mouseReleased(final MouseEvent event) {
			maybeShowPopup(event);
		}
		public void mousePressed(final MouseEvent event) {
			maybeShowPopup(event);
		}
		public void mouseClicked(final MouseEvent event) {}
		public void mouseEntered(final MouseEvent event) {}
		public void mouseExited(final MouseEvent event) {}

		protected void maybeShowPopup(final MouseEvent e) {
			if( e.isPopupTrigger() ) {
				if( trackedDownloadsTable.getSelectedRowCount() > 0 ) {
					tablePopupMenu.show(trackedDownloadsTable, e.getX(), e.getY());
				}
			}
		}
	}
	
	
	private class TrackedDownloadsTable extends SortedTable<TrackedDownloadTableMember> {
		private static final long serialVersionUID = 1L;
		
		final TableCellRenderer sizeColumnRenderer;

		public TrackedDownloadsTable(final TrackedDownloadsModel trackDownloadsModel) {
			super(trackDownloadsModel);
			this.setIntercellSpacing(new Dimension(5, 1));
			
			sizeColumnRenderer = new SizeColumnTableCellRenderer();
		}

		// override the default sort order when clicking different columns
		@Override
		public boolean getColumnDefaultAscendingState(final int col) {
			if( col == 3 || col == 4 ) {
				// sort filesize and date columns descending by default
				return false;
			}
			return true; // all other columns: ascending
		}

		public String getToolTipText(final MouseEvent mouseEvent) {
			final java.awt.Point point = mouseEvent.getPoint();
			final int rowIndex = rowAtPoint(point);
			final int colIndex = columnAtPoint(point);
			final int realColumnIndex = convertColumnIndexToModel(colIndex);
			final TableModel tableModel = getModel();
			final Object value = tableModel.getValueAt(rowIndex, realColumnIndex);
			String tooltipString = null;
			if( value != null ) {
				try {
					tooltipString = value.toString();
				} catch( final Exception e ) {}
			}
			// show no tooltip at all if there's no value in the current column, or if it's an empty value
			return ( tooltipString == null || tooltipString.length() == 0 ? null : tooltipString );
		}
		
		public TableCellRenderer getCellRenderer(final int row, final int column) {
			if(column == 3) {
				return sizeColumnRenderer;
			}
			return super.getCellRenderer(row, column);
		}
		
		private class SizeColumnTableCellRenderer extends JLabel implements TableCellRenderer {
			private static final long serialVersionUID = 1L;

			public Component getTableCellRendererComponent(final JTable table,
					final Object value, final boolean isSelected, final boolean hasFocus,
					final int row, final int column) {
				this.setText(value.toString());
				this.setHorizontalAlignment(SwingConstants.RIGHT);
				return this;
			}
		}
	}
}
