/*
  AddNewDownloadsDialog.java / Frost
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

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.KeyParser;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.fileTransfer.download.DownloadModel;
import frost.gui.model.SortedTableModel;
import frost.gui.model.TableMember;
import frost.storage.perst.TrackDownloadKeys;
import frost.storage.perst.TrackDownloadKeysStorage;
import frost.util.DateFun;
import frost.util.FileAccess;
import frost.util.FormatterUtils;
import frost.util.Mixed;
import frost.util.gui.BooleanCell;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.Language;

@SuppressWarnings("serial")
public class AddNewDownloadsDialog extends javax.swing.JFrame {

	private final Language language;

	private final TrackDownloadKeysStorage trackDownloadKeysStorage;

	private AddNewDownloadsTableModel addNewDownloadsTableModel;
	private AddNewDownloadsTable addNewDownloadsTable;
	private JButton removeAlreadyDownloadedButton;
	private JButton removeAlreadyExistsButton;
	private JButton pasteMoreKeysButton;
	private JButton okButton;
	private JButton cancelButton;
	private JSkinnablePopupMenu tablePopupMenu;

	private final Frame parentFrame;

	private boolean askBeforeRedownload = true;
	private String overrideDownloadPrefix = null;
	private String overrideDownloadDir = null;
	private boolean showPasteMoreButton = false;

	public AddNewDownloadsDialog(final JFrame frame) { 
		parentFrame = frame;
		
		language = Language.getInstance();
		trackDownloadKeysStorage = TrackDownloadKeysStorage.inst();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		initGUI();
	}

	private void initGUI() {
		try {
			setTitle(language.getString("AddNewDownloadsDialog.title"));
			
			int width = (int) (parentFrame.getWidth() * 0.75);
			int height = (int) (parentFrame.getHeight() * 0.75);

			if( width < 1000 ) {
				width = 1000;
			}

			if( height < 720 ) {
				height = 720;
			}

			setSize(width, height);
			this.setResizable(true);
			
			setIconImage(MiscToolkit.loadImageIcon("/data/toolbar/document-save.png").getImage());
			
			
			// Remove already Downloaded Button
			removeAlreadyDownloadedButton = new JButton(language.getString("AddNewDownloadsDialog.button.removeAlreadyDownloadedButton"));
			removeAlreadyDownloadedButton.setToolTipText(language.getString("AddNewDownloadsDialog.buttonTooltip.removeAlreadyDownloadedButton"));
			removeAlreadyDownloadedButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					removeAlreadyDownloadedButton_actionPerformed(e);
				}
			});

			// Remove already exists Button
			removeAlreadyExistsButton = new JButton(language.getString("AddNewDownloadsDialog.button.removeAlreadyExistsButton"));
			removeAlreadyExistsButton.setToolTipText(language.getString("AddNewDownloadsDialog.buttonTooltip.removeAlreadyExistsButton"));
			removeAlreadyExistsButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					removeAlreadyExistsButton_actionPerformed(e);
				}
			});

			// Paste more keys from clipboard Button
			pasteMoreKeysButton = new JButton(language.getString("AddNewDownloadsDialog.button.pasteMoreKeysButton"));
			pasteMoreKeysButton.setToolTipText(language.getString("AddNewDownloadsDialog.buttonTooltip.pasteMoreKeysButton"));
			pasteMoreKeysButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addKeysFromClipboard();
				}
			});

			// OK Button
			okButton = new JButton(language.getString("Common.ok"));
			okButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					okButton_actionPerformed();
				}
			});

			// Cancel Button
			cancelButton = new JButton(language.getString("Common.cancel"));
			cancelButton.addActionListener( new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addNewDownloadsTableModel.clearDataModel();
					dispose();
				}
			});

			// Button row
			final JPanel buttonsPanel = new JPanel(new BorderLayout());
			buttonsPanel.setLayout( new BoxLayout( buttonsPanel, BoxLayout.X_AXIS ));

			buttonsPanel.add( removeAlreadyDownloadedButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( removeAlreadyExistsButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( pasteMoreKeysButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));

			buttonsPanel.add( Box.createHorizontalGlue() );

			buttonsPanel.add( cancelButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( okButton );
			buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));

			// Download Table
			addNewDownloadsTableModel = new AddNewDownloadsTableModel(this);
			addNewDownloadsTable = new AddNewDownloadsTable( addNewDownloadsTableModel );
			new TableFindAction().install(addNewDownloadsTable);
			addNewDownloadsTable.setRowSelectionAllowed(true);
			addNewDownloadsTable.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			addNewDownloadsTable.setRowHeight(18);
			final JScrollPane scrollPane = new JScrollPane(addNewDownloadsTable);
			scrollPane.setWheelScrollingEnabled(true);
			
			// main panel
			final JPanel mainPanel = new JPanel(new BorderLayout());
			mainPanel.add( scrollPane, BorderLayout.CENTER );
			mainPanel.add( buttonsPanel, BorderLayout.SOUTH );
			mainPanel.setBorder(BorderFactory.createEmptyBorder(5,7,7,7));

			this.getContentPane().setLayout(new BorderLayout());
			this.getContentPane().add(mainPanel, null);
			
			// Init popup menu
			this.initPopupMenu();
			
			// Init HotKeys
			this.assignHotkeys(mainPanel);
			
			// table column behavior and ratios
			addNewDownloadsTable.setAutoResizeMode( JTable.AUTO_RESIZE_NEXT_COLUMN);
			int tableWidth = getWidth();
			
			TableColumnModel headerColumnModel = addNewDownloadsTable.getTableHeader().getColumnModel();
			int defaultColumnWidthRatio[] = addNewDownloadsTableModel.getDefaultColumnWidthRatio();
			for(int i = 0 ; i < defaultColumnWidthRatio.length ; i++) {
				headerColumnModel.getColumn(i).setMinWidth(5);
				int ratio = tableWidth * defaultColumnWidthRatio[i] /100;
				headerColumnModel.getColumn(i).setPreferredWidth(ratio);
			}

			// window activity listener: triggers whenever the window gains focus (after things like
			// alt-tab or manually switching to another window on the operating system or in Frost).
			// we then update the "file exists?" check for all files. this allows people to go to the
			// on-disk folder and delete their files and then return to Frost and have it automatically
			// detect that they've deleted the files, without them having to refresh anything manually.
			// NOTE:XXX: this means we get dual "exists?" checks if they rename, add prefix, etc, since
			// all of those actions (and anything else that opens a message/dialog box, such as pasting
			// keys and getting the "skipped X duplicates" box) will cause the window to lose and regain
			// focus, so we get both *their* "exists?" update check, *and* the "focus regained" update check.
			// but it doesn't matter; the check is ultra-fast, and the user gets a slicker experience if
			// we do this refresh automatically instead of forcing the user to press some "refresh" button.
			this.addWindowListener(new WindowAdapter() {
				@Override
				public void windowActivated(final WindowEvent e) {
					// perform the "exists?" check on the event queue, just to be 100% safe
					SwingUtilities.invokeLater(new Runnable() {
						public void run() {
							final int numberOfRows = addNewDownloadsTableModel.getRowCount();
							if( numberOfRows <= 0 ) { return; } // abort if nothing to do
							try {
								for( int indexPos = 0; indexPos < numberOfRows; ++indexPos ) {
									final AddNewDownloadsTableMember addNewDownloadsTableMember = addNewDownloadsTableModel.getRow(indexPos);
									addNewDownloadsTableMember.updateExistsCheck(); // mark duplicates
								}
								addNewDownloadsTableModel.fireTableRowsUpdated(0, numberOfRows - 1); // redraw all rows without losing selection
							} catch( final Exception ex ) {}
						}
					});
				}
			});

		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	private List<FrostDownloadItem> getDownloads() {
		List<FrostDownloadItem> frostDownloadItemList = new LinkedList<FrostDownloadItem>();
		final int numberOfRows = addNewDownloadsTableModel.getRowCount();
		for( int indexPos = 0; indexPos < numberOfRows; indexPos++) {
			frostDownloadItemList.add( addNewDownloadsTableModel.getRow(indexPos).getDownloadItem() );
		}
		return frostDownloadItemList;
	}

	public void startDialog(
			final List<FrostDownloadItem> frostDownloadItemList,
			final boolean askBeforeRedownload,
			final String overrideDownloadPrefix,
			final String overrideDownloadDir,
			final boolean showPasteMoreButton)
	{
		// load data into table
		this.loadNewDownloadsIntoTable(frostDownloadItemList);
		setLocationRelativeTo(parentFrame);

		// set the "ask before redownloading already downloaded keys" behavior for this dialog instance
		// along with the optional prefix/download dir overrides for any new keys added if the
		// "paste more keys from clipboard" button is enabled (which it should ONLY be if this is called
		// from the Download Panel! it doesn't make ANY sense to "paste more keys" to a list from a msg!)
		this.askBeforeRedownload = askBeforeRedownload;
		this.overrideDownloadPrefix = overrideDownloadPrefix;
		this.overrideDownloadDir = overrideDownloadDir;
		this.showPasteMoreButton = showPasteMoreButton;

		// hide the "paste more" button if requested
		if( !this.showPasteMoreButton ) {
			pasteMoreKeysButton.setVisible(false);
		}

		// display table
		setVisible(true); // blocking!
	}

	private void addKeysFromClipboard() {
		final String clipboardText = Mixed.getClipboardText();
		if( clipboardText == null ) { return; }

		// parse plaintext to get key list
		// NOTE: we don't want Freesite keys so the 2nd argument is false
		List<FrostDownloadItem> newDownloadsList = KeyParser.parseKeys(clipboardText, false);

		// verify that the key list exists and has at least 1 item
		if( newDownloadsList == null || newDownloadsList.size() == 0 ) {
			return;
		}

		// add the appropriate download dir and filename prefix
		for( final FrostDownloadItem dlItem : newDownloadsList ) {
			// all downloads default to using Frost's "download directory" preference and
			// no default prefix. but if we're starting a dialog that should use another prefix
			// or directory, then apply them to the downloads now.
			if( overrideDownloadPrefix != null ) {
				dlItem.setFilenamePrefix(overrideDownloadPrefix);
			}
			if( overrideDownloadDir != null ) {
				dlItem.setDownloadDir(overrideDownloadDir);
			}
		}

		// now merge it with the current list of downloads
		// NOTE: we'll base the list on the current list, and then *add* the new ones to the end,
		// since the duplicate-check only adds the *first* encountered version of each key. so
		// if the user clicks "paste more keys" multiple times, we want to keep the version of the
		// key that was already in the "Add new downloads" table and may have had a special prefix/dir
		// or other settings applied to it already, which we of course want to preserve!
		List<FrostDownloadItem> newTableList = getDownloads(); // is a LinkedList, thus guaranteeing insert order
		newTableList.addAll(newDownloadsList);

		// load data into table (skips all duplicates)
		this.loadNewDownloadsIntoTable(newTableList);
	}

	private void loadNewDownloadsIntoTable(final List<FrostDownloadItem> frostDownloadItemList) {
		// delete all existing model items
		this.addNewDownloadsTableModel.clearDataModel();

		// add only the *first* instance of each unique key, to avoid duplicates
		int duplicates = 0;
		final Set<String> seenKeys = new HashSet<String>();
		for( final FrostDownloadItem dlItem : frostDownloadItemList ) {
			final String thisKey = dlItem.getKey();
			if( thisKey == null ){ continue; }

			// if this key has already been added, then increase the duplicate count and skip it
			if( seenKeys.contains( thisKey ) ) {
				++duplicates;
				continue;
			}

			// add the key to the list of seen keys, and add the item to the model
			// NOTE: the constructor checks if the file has already been downloaded/exists on disk
			seenKeys.add( thisKey );
			this.addNewDownloadsTableModel.addRow(new AddNewDownloadsTableMember(dlItem));
		}

		// reset the sort-order to the default (column 0 "Filename", Ascending) so that they don't get confused
		addNewDownloadsTable.setSortedColumn(0, true);
		updateTitle(); // correct the title's item count

		// if there were duplicate files, warn the user (this msg is shown before the dialog becomes visible)
		// centers the message around the mainframe if shown before dialog is visible, otherwise around dialog
		if( duplicates > 0 ) {
			MiscToolkit.showMessageDialog( // blocking!
					( this != null && this.isVisible() ? this : MainFrame.getInstance() ),
					language.formatMessage("AddNewDownloadsDialog.errorDialog.duplicateKeys.body", duplicates),
					language.getString("AddNewDownloadsDialog.errorDialog.duplicateKeys.title"),
					MiscToolkit.INFORMATION_MESSAGE);
		}
	}

	private void updateTitle() {
		// update the dialog title to show the download item count
		final int itemCount = addNewDownloadsTableModel.getRowCount();
		setTitle(language.getString("AddNewDownloadsDialog.title") + " (" + itemCount + ")");
	}
	
    private void assignHotkeys(final JPanel p) {

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
                addNewDownloadsTable.new SelectedItemsAction() {
                    protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
                        addNewDownloadsTableMember.getDownloadItem().setPriority(prio);
                    }
                };
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_0, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD3, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_5, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD5, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_6, 0, true), "SetPriority");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD6, 0, true), "SetPriority");
        p.getActionMap().put("SetPriority", setPriorityAction);

        // Delete - remove selected
        final Action deleteSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewDownloadsTable.removeSelected();
                updateTitle(); // correct the title's item count
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, true), "DeleteSelectedFiles");
        p.getActionMap().put("DeleteSelectedFiles", deleteSelectedAction);

        // Shift+Delete - remove but selected
        final Action deleteButSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewDownloadsTable.removeButSelected();
                updateTitle(); // correct the title's item count
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_MASK, true), "DeleteButSelectedFiles");
        p.getActionMap().put("DeleteButSelectedFiles", deleteButSelectedAction);

        // R - rename selected
        final Action renameSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                renameFile_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, true), "RenameSelectedFiles");
        p.getActionMap().put("RenameSelectedFiles", renameSelectedAction);

        // P - prefix selected
        final Action prefixSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                prefixFile_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true), "PrefixSelectedFiles");
        p.getActionMap().put("PrefixSelectedFiles", prefixSelectedAction);

        // D - change download directory
        final Action changeDownloadDirSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                changeDownloadDir_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "ChangeDownloadDirSelectedFiles");
        p.getActionMap().put("ChangeDownloadDirSelectedFiles", changeDownloadDirSelectedAction);

        // E - invert "enabled" state
        final Action invertEnabledAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewDownloadsTable.new SelectedItemsAction() {
                    protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
                        // null = invert the current state of the item
                        addNewDownloadsTableMember.getDownloadItem().setEnabled(null);
                    }
                };
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, true), "InvertEnabled");
        p.getActionMap().put("InvertEnabled", invertEnabledAction);

        // V - paste more keys from clipboard (only if the button is enabled)
        final Action pasteMoreKeysAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                if( showPasteMoreButton ) { // only true if dialog was started from "paste from clipboard" button
                    pasteMoreKeysButton.doClick();
                }
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_V, 0, true), "PasteMoreKeys");
        p.getActionMap().put("PasteMoreKeys", pasteMoreKeysAction);

        // Ctrl+Enter - Ok! (The CTRL is added to make it harder to accidentally hit)
        final Action okAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                okButton_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK, true), "OkAction");
        p.getActionMap().put("OkAction", okAction);

        // Ctrl+Esc - Cancel! (The CTRL is added to make it harder to accidentally hit)
        final Action cancelAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewDownloadsTableModel.clearDataModel();
                dispose();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, InputEvent.CTRL_MASK, true), "CancelAction");
        p.getActionMap().put("CancelAction", cancelAction);
    }

	private void initPopupMenu() {

		// Rename file
		final JMenuItem renameFile = new JMenuItem(language.getString("AddNewDownloadsDialog.button.renameFile"));
		renameFile.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				renameFile_actionPerformed();
			}
		});
		
		// prefix Filename
		final JMenuItem prefixFilename = new JMenuItem(language.getString("AddNewDownloadsDialog.button.prefixFilename"));
		prefixFilename.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				prefixFile_actionPerformed();
			}
		});

		// Change Download Directory
		final JMenuItem changeDownloadDir = new JMenuItem(language.getString("AddNewDownloadsDialog.button.changeDownloadDir"));
		changeDownloadDir.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				changeDownloadDir_actionPerformed();
			}
		});
		

		// Remove item item(s) list
		final JMenuItem removeDownload = new JMenuItem(language.getString("AddNewDownloadsDialog.button.removeDownload"));
		removeDownload.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewDownloadsTable.removeSelected();
				updateTitle(); // correct the title's item count
			}
		});

		// Remove but selected item(s) from list
		final JMenuItem removeButSelectedDownload = new JMenuItem(language.getString("AddNewDownloadsDialog.button.removeButSelectedDownload"));
		removeButSelectedDownload.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewDownloadsTable.removeButSelected();
				updateTitle(); // correct the title's item count
			}
		});

		// Change Priority
		final JMenu changePriorityMenu = new JMenu(language.getString("Common.priority.changePriority"));
		for(final FreenetPriority priority : FreenetPriority.values()) {
			JMenuItem priorityMenuItem = new JMenuItem(priority.getName());
			priorityMenuItem.addActionListener(new java.awt.event.ActionListener() {
				public void actionPerformed(final ActionEvent actionEvent) {
					addNewDownloadsTable.new SelectedItemsAction() {
						protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
							addNewDownloadsTableMember.getDownloadItem().setPriority(priority);
						}
					};
				}
			});
			changePriorityMenu.add(priorityMenuItem);
		}
		
		// Enable download
		final JMenuItem enableDownloadMenuItem = new JMenuItem(language.getString("AddNewDownloadsDialog.popupMenu.enableDownload"));
		enableDownloadMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewDownloadsTable.new SelectedItemsAction() {
					protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
						addNewDownloadsTableMember.getDownloadItem().setEnabled(true);
					}
				};
			}
		});
		
		
		// Disable download
		final JMenuItem disableDownloadMenuItem = new JMenuItem(language.getString("AddNewDownloadsDialog.popupMenu.disableDownload"));
		disableDownloadMenuItem.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewDownloadsTable.new SelectedItemsAction() {
					protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
						addNewDownloadsTableMember.getDownloadItem().setEnabled(false);
					}
				};
			}
		});
		
		// recent download directory
		final JMenu downloadDirRecentMenu = new JMenu(language.getString("DownloadPane.toolbar.downloadDirMenu.setDownloadDirTo"));
		JMenuItem item = new JMenuItem(FrostDownloadItem.getDefaultDownloadDir());
		item.addActionListener( new java.awt.event.ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				setDownloadDir(FrostDownloadItem.getDefaultDownloadDir());
			}
		});
		downloadDirRecentMenu.add(item);
		final LinkedList<String> dirs = FileTransferManager.inst().getDownloadManager().getRecentDownloadDirs();
		if( dirs.size() > 0 ) {
			downloadDirRecentMenu.addSeparator();
			final ListIterator<String> iter = dirs.listIterator(dirs.size());
			while (iter.hasPrevious()) {
				final String dir = (String) iter.previous();
				
				item = new JMenuItem(dir);
				item.addActionListener( new java.awt.event.ActionListener() {
					public void actionPerformed(final ActionEvent actionEvent) {
						setDownloadDir(dir);
					}
				});
				downloadDirRecentMenu.add(item);
			}
		}

		// Compose popup menu
		tablePopupMenu = new JSkinnablePopupMenu();
		tablePopupMenu.add(renameFile);
		tablePopupMenu.add(prefixFilename);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(changeDownloadDir);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(removeDownload);
		tablePopupMenu.add(removeButSelectedDownload);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(changePriorityMenu);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(enableDownloadMenuItem);
		tablePopupMenu.add(disableDownloadMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(downloadDirRecentMenu);
		
		this.addNewDownloadsTable.addMouseListener(new TablePopupMenuMouseListener());
	}

	private void okButton_actionPerformed() {
		// first we need to run another "exists?" check to update all files to the latest state
		boolean nameCollision = false;
		final int numberOfRows = addNewDownloadsTableModel.getRowCount();
		addNewDownloadsTable.clearSelection();
		for( int indexPos = 0; indexPos < numberOfRows; ++indexPos ) {
			final AddNewDownloadsTableMember addNewDownloadsTableMember = addNewDownloadsTableModel.getRow(indexPos);
			addNewDownloadsTableMember.updateExistsCheck(); // mark duplicates
			if( nameCollision == false && addNewDownloadsTableMember.isExists() ) {
				nameCollision = true;
			}
		}
		addNewDownloadsTableModel.fireTableDataChanged(); // redraw all rows

		// warn the user and refuse to proceed if one or more of the files already exist
		if( nameCollision ) {
			MiscToolkit.showMessageDialog(
					AddNewDownloadsDialog.this,
					language.getString("AddNewDownloadsDialog.errorDialog.nameCollision.body"),
					language.getString("AddNewDownloadsDialog.errorDialog.nameCollision.title"),
					MiscToolkit.ERROR_MESSAGE);
			return;
		}

		// now just add the files to the download queue and close the dialog)
		// NOTE: if some of our queued keys already exist in the model, this will display a message
		// dialog with the number of skipped downloads.
		FileTransferManager.inst().getDownloadManager().getModel().addDownloadItemList(getDownloads(), askBeforeRedownload);
		dispose();
	}

	private void removeAlreadyDownloadedButton_actionPerformed(final ActionEvent actionEvent) {
		final int numberOfRows = addNewDownloadsTableModel.getRowCount();
		addNewDownloadsTable.clearSelection();
		for( int indexPos = numberOfRows -1; indexPos >= 0; indexPos--) {
			final AddNewDownloadsTableMember addNewDownloadsTableMember = addNewDownloadsTableModel.getRow(indexPos);
			if( trackDownloadKeysStorage.searchItemKey( addNewDownloadsTableMember.getDownloadItem().getKey() ) ) {
				addNewDownloadsTableModel.deleteRow(addNewDownloadsTableMember);
			}
		}
		updateTitle(); // correct the title's item count
	}

	private void removeAlreadyExistsButton_actionPerformed(final ActionEvent actionEvent) {
		final int numberOfRows = addNewDownloadsTableModel.getRowCount();
		addNewDownloadsTable.clearSelection();
		for( int indexPos = numberOfRows -1; indexPos >= 0; indexPos--) {
			final AddNewDownloadsTableMember addNewDownloadsTableMember = addNewDownloadsTableModel.getRow(indexPos);
			final FrostDownloadItem frostDownloadItem = addNewDownloadsTableMember.getDownloadItem();
			if( DownloadModel.fileAlreadyExists(frostDownloadItem.getDownloadFilename()) != null ) {
				addNewDownloadsTableModel.deleteRow(addNewDownloadsTableMember);
			}
		}
		updateTitle(); // correct the title's item count
	}
	
	private String askForNewname(final String oldName) {
		return (String) MiscToolkit.showInputDialog(
				this,
				language.getString("AddNewDownloadsDialog.renameFileDialog.dialogBody"),
				language.getString("AddNewDownloadsDialog.renameFileDialog.dialogTitle"),
				MiscToolkit.QUESTION_MESSAGE,
				null,
				null,
				oldName
		);
	}
	private void changeDownloadDir_actionPerformed() {
		// attempt to start navigation from the "Download Directory" of the first selected file.
		// but if that fails (due to no selection or directory not existing), we'll start navigation
		// from the default download directory instead
		String startDir = null;
		final int[] selectedRows = addNewDownloadsTable.getSelectedRows();
		if( selectedRows.length > 0 ) {
			final int rowIdx = selectedRows[0]; // first row of selection
			if( rowIdx < addNewDownloadsTable.getRowCount() ) { // validate row index
				AddNewDownloadsTableMember addNewDownloadsTableMember = addNewDownloadsTableModel.getRow(rowIdx);
				startDir = addNewDownloadsTableMember.getDownloadItem().getDownloadDir();
			}
		}
		if( startDir != null ) {
			//startDir = startDir.trim(); // remove leading and trailing whitespace (NOTE: not needed in this case since it isn't userinput!)
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
		fc.setDialogTitle(language.getString("AddNewDownloadsDialog.changeDirDialog.title"));
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

		// set dir for selected items (this feeds them absolute paths, but they'll turn them into relative if appropriate)
		setDownloadDir(selectedFolder.toString());
	}
	
	
	private void setDownloadDir(final String downloadDir) {
		addNewDownloadsTable.new SelectedItemsAction() {
			protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
				addNewDownloadsTableMember.getDownloadItem().setDownloadDir(downloadDir);
				addNewDownloadsTableMember.updateExistsCheck(); // mark duplicates
			}
		};
	}


	private void renameFile_actionPerformed() {
		addNewDownloadsTable.new SelectedItemsAction() {
			protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
				String newName = askForNewname(addNewDownloadsTableMember.getDownloadItem().getUnprefixedFilename());
				if( newName != null ) {
					addNewDownloadsTableMember.getDownloadItem().setFileName(newName);
					addNewDownloadsTableMember.updateExistsCheck(); // mark duplicates
				}
			}
		};
	}

	private void prefixFile_actionPerformed() {
		final String prefix = MiscToolkit.showInputDialog(
				AddNewDownloadsDialog.this,
				language.getString("AddNewDownloadsDialog.prefixFilenameDialog.dialogBody"),
				language.getString("AddNewDownloadsDialog.prefixFilenameDialog.dialogTitle"),
				MiscToolkit.QUESTION_MESSAGE
				);
		if( prefix == null ) { return; } // user canceled

		addNewDownloadsTable.new SelectedItemsAction() {
			protected void action(AddNewDownloadsTableMember addNewDownloadsTableMember) {
				addNewDownloadsTableMember.getDownloadItem().setFilenamePrefix(prefix);
				addNewDownloadsTableMember.updateExistsCheck(); // mark duplicates
			}
		};
	}

	
	private static class AddNewDownloadsTableModel extends SortedTableModel<AddNewDownloadsTableMember>{
		private Language language = null;

		protected  static String columnNames[];

		private AddNewDownloadsDialog parentDialog;

		protected final static Class<?> columnClasses[] = {
			String.class, // name
			String.class, // extension
			String.class, // key
			String.class, // priority
			String.class, // download dir
			Boolean.class, // enabled
			String.class, // downloaded
			String.class, // exists
		};
		
		private final int[] defaultColumnWidthRatio = {
			35, // name
			5, // extension
			10, // key
			5, // priority
			30, // download dir
			5, // enabled
			5, // downloaded
			5, // exists
		};


		public AddNewDownloadsTableModel(AddNewDownloadsDialog parentDialog) {
			super();
			this.parentDialog = parentDialog;
			language = Language.getInstance();
			refreshLanguage(); 
			assert columnClasses.length == columnNames.length;
		}

		private void refreshLanguage() {
			columnNames = new String[] {
				language.getString("AddNewDownloadsDialog.table.name"),
				language.getString("AddNewDownloadsDialog.table.extension"),
				language.getString("AddNewDownloadsDialog.table.key"),
				language.getString("Common.priority"),
				language.getString("AddNewDownloadsDialog.table.downloadDir"),
				language.getString("Common.enabled"),
				language.getString("AddNewDownloadsDialog.table.downloaded"),
				language.getString("AddNewDownloadsDialog.table.exists"),
			};
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			switch(col){
				case 5: // "Enabled" column
					return true;
				default: // all other columns are uneditable
					return false;
			}
		}

		@Override
		public String getColumnName(int column) {
			if( column >= 0 && column < columnNames.length )
				return columnNames[column];
			return null;
		}

		@Override
		public int getColumnCount() {
			return columnNames.length;
		}

		@Override
		public Class<?> getColumnClass(int column) {
			if( column >= 0 && column < columnClasses.length )
				return columnClasses[column];
			return null;
		}
		
		@Override
		public void setValueAt(Object aValue, int row, int column) {
			switch(column){
				case 5: // "Enabled" column
					getRow(row).getDownloadItem().setEnabled((Boolean) aValue);
					// select 1st column to fix Java cell focus bug, where it otherwise selects the clicked cell and swallows all keyboard input
					parentDialog.addNewDownloadsTable.setColumnSelectionInterval(0,0);
					return;
				default: // all other columns are uneditable
					return;
			}
		}
		
		public int[] getDefaultColumnWidthRatio() {
			return defaultColumnWidthRatio;
		}
	}

	private class AddNewDownloadsTableMember extends TableMember.BaseTableMember<AddNewDownloadsTableMember> {

		private FrostDownloadItem frostDownloadItem;
		private boolean downloaded;
		private String downloadedTooltip;
		private boolean exists;
		private String existsTooltip;

		public AddNewDownloadsTableMember(final FrostDownloadItem frostDownloadItem){
			this.frostDownloadItem = frostDownloadItem;

			// first check if the file has already been downloaded (is in the tracked keys storage)
			TrackDownloadKeys trackDownloadKeys = trackDownloadKeysStorage.getItemByKey(frostDownloadItem.getKey());
			downloaded = trackDownloadKeys != null;
			if( downloaded ) {
				final long date = trackDownloadKeys.getDownloadFinishedTime();
				downloadedTooltip = new StringBuilder("<html>")
						.append(language.getString("ManageDownloadTrackingDialog.table.finished"))
						.append(": ")
						.append(DateFun.FORMAT_DATE_VISIBLE.print(date))
						.append(" ")
						.append(DateFun.FORMAT_TIME_VISIBLE.print(date))
						.append("<br />\n")
						.append(language.getString("ManageDownloadTrackingDialog.table.board"))
						.append(": ")
						.append(trackDownloadKeys.getBoardName())
						.append("<br />\n")
						.append(language.getString("ManageDownloadTrackingDialog.table.size"))
						.append(": ")
						.append(FormatterUtils.formatSize(trackDownloadKeys.getFileSize()))
						.append("</html>")
						.toString();
			} else {
				downloadedTooltip = null;
			}

			// now check if the file already exists on disk at that path
			updateExistsCheck(); // mark duplicates
		}

		@Override
		public Comparable<?> getValueAt(final int column) {
			switch( column ) {
				case 0: // name
					return frostDownloadItem.getFileName();
				case 1: // extension
					final String ext = FileAccess.getFileExtension(frostDownloadItem.getFileName());
					if( ext != null ) { return ext; }
					return "-";
				case 2: // key
					return frostDownloadItem.getKey();
				case 3: // priority
					return frostDownloadItem.getPriority().getName();
				case 4: // download dir
					return frostDownloadItem.getDownloadDir();
				case 5: // enabled
					return frostDownloadItem.isEnabled();
				case 6: // downloaded
					return downloaded ? "X" : "";
				case 7: // exists
					return exists ? "X" : "";
				default :
					throw new RuntimeException("Unknown Column pos");
			}
		}

		/* SUPER IMPORTANT: This must be called *any time* the filename, prefix or path changes. */
		public void updateExistsCheck() {
			final File existingFile = DownloadModel.fileAlreadyExists( frostDownloadItem.getDownloadFilename() );
			exists = existingFile != null;
			if( exists ) {
				final long date = existingFile.lastModified();
				existsTooltip = new StringBuilder("<html>")
				.append(language.getString("AddNewDownloadsDialog.table.lastModifiedTooltip"))
				.append(": ")
				.append(DateFun.FORMAT_DATE_VISIBLE.print(date))
				.append(" ")
				.append(DateFun.FORMAT_TIME_VISIBLE.print(date))
				.append("<br />\n")
				.append(language.getString("AddNewDownloadsDialog.table.fileSizeTooltip"))
				.append(": ")
				.append(FormatterUtils.formatSize(existingFile.length()))
				.append("</html>")
				.toString();
			} else {
				existsTooltip = null;
			}
		}

		public boolean isExists() {
			return exists;
		}
		public boolean isDownloaded() {
			return downloaded;
		}

		public FrostDownloadItem getDownloadItem(){
			return frostDownloadItem;
		}
	}

	private class TablePopupMenuMouseListener implements MouseListener {
		@Override
		public void mouseReleased(final MouseEvent event) {
			maybeShowPopup(event);
		}
		@Override
		public void mousePressed(final MouseEvent event) {
			maybeShowPopup(event);
		}
		@Override
		public void mouseClicked(final MouseEvent event) {}
		@Override
		public void mouseEntered(final MouseEvent event) {}
		@Override
		public void mouseExited(final MouseEvent event) {}

		protected void maybeShowPopup(final MouseEvent e) {
			if( e.isPopupTrigger() ) {
				if( addNewDownloadsTable.getSelectedRowCount() > 0 ) {
					tablePopupMenu.show(addNewDownloadsTable, e.getX(), e.getY());
				}
			}
		}
	}
	
	private class AddNewDownloadsTable extends SortedTable<AddNewDownloadsTableMember> {

		private CenterCellRenderer centerCellRenderer;
		
		private final String[] columnTooltips = {
			null,
			null,
			null,
			null,
			null,
			null,
			language.getString("AddNewDownloadsDialog.tableToolltip.downloaded"),
			language.getString("AddNewDownloadsDialog.tableToolltip.exists"),
		};
		
		
		public AddNewDownloadsTable(final AddNewDownloadsTableModel addNewDownloadsTableModel) {
			super(addNewDownloadsTableModel);
			this.setIntercellSpacing(new Dimension(5, 1));
			centerCellRenderer = new CenterCellRenderer();
		}

		// override the default sort order when clicking different columns
		@Override
		public boolean getColumnDefaultAscendingState(final int col) {
			if( col == 5 || col == 6 || col == 7 ) {
				// sort boolean columns ("enabled", "downloaded" and "exists") descending by default
				return false;
			}
			return true; // all other columns: ascending
		}

		@Override
		public String getToolTipText(final MouseEvent mouseEvent) {
			final java.awt.Point point = mouseEvent.getPoint();
			final int rowIndex = rowAtPoint(point);
			final int colIndex = columnAtPoint(point);
			final int realColumnIndex = convertColumnIndexToModel(colIndex);
			final AddNewDownloadsTableModel tableModel = (AddNewDownloadsTableModel) getModel();
			switch(realColumnIndex){
				case 0:
				case 1:
				case 2:
				case 3:
				case 4:
				case 5:
					return tableModel.getValueAt(rowIndex, realColumnIndex).toString();
				case 6:
					return addNewDownloadsTableModel.getRow(rowIndex).downloadedTooltip;
				case 7:
					return addNewDownloadsTableModel.getRow(rowIndex).existsTooltip;
				default:
					assert false;
			}
			return tableModel.getValueAt(rowIndex, realColumnIndex).toString();
		}
		
		@Override
		public TableCellRenderer getCellRenderer(final int rowIndex, final int columnIndex) {
			switch(columnIndex){
				case 0:
				case 1:
				case 2:
				case 3:
				case 4:
					return super.getCellRenderer(rowIndex, columnIndex);
				case 5:
					return BooleanCell.RENDERER;
				case 6:
				case 7:
					return centerCellRenderer;
				default:
					assert false;
			}
			return super.getCellRenderer(rowIndex, columnIndex);
		}
		
		@Override
		public TableCellEditor getCellEditor(final int rowIndex, final int columnIndex ) {
			switch(columnIndex){
				case 0:
				case 1:
				case 2:
				case 3:
				case 4:
				case 6:
				case 7:
					return super.getCellEditor(rowIndex, columnIndex);
				case 5:
					return BooleanCell.EDITOR;
				default:
					assert false;
			}
			return super.getCellEditor(rowIndex, columnIndex);
		}
		
		private class CenterCellRenderer extends JLabel implements TableCellRenderer {
			public Component getTableCellRendererComponent(final JTable table,
					final Object value, final boolean isSelected, final boolean hasFocus,
					final int row, final int column) {
				this.setText(value.toString());
				this.setHorizontalAlignment(SwingConstants.CENTER);
				return this;
			}
		}
		
		@Override
		protected JTableHeader createDefaultTableHeader() {
			return new JTableHeader(columnModel) {
				public String getToolTipText(final MouseEvent e) {
					final java.awt.Point p = e.getPoint();
					final int index = columnModel.getColumnIndexAtX(p.x);
					final int realIndex = columnModel.getColumn(index).getModelIndex();
					return columnTooltips[realIndex];
				}
			};
		}

	}
}
