package frost.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
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
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetCompatibilityManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.upload.FrostUploadItem;
import frost.gui.model.SortedTableModel;
import frost.gui.model.TableMember;
import frost.util.FileAccess;
import frost.util.FormatterUtils;
import frost.util.Mixed;
import frost.util.gui.BooleanCell;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.SmartFileFilters;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.Language;

@SuppressWarnings("serial")
public class AddNewUploadsDialog extends JFrame {

	private final Language language;
	private static String autoCryptoKeyString; // language-specific placeholder for files without custom crypto key

	private AddNewUploadsTableModel addNewUploadsTableModel;
	private AddNewUploadsTable addNewUploadsTable;
	
	private JSkinnablePopupMenu tablePopupMenu;
	
	private final Frame parentFrame;
	
	

	/**
	 * If true, uploads in model will be added to upload list when closing the window
	 */
	
	public AddNewUploadsDialog(final JFrame frame) {
		parentFrame = frame;
		language = Language.getInstance();

		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

		initGui();
	}
	
	
	private void initGui() {
		try {
			setTitle(language.getString("AddNewUploadsDialog.title"));
			
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
			
			setIconImage(MiscToolkit.loadImageIcon("/data/toolbar/go-up.png").getImage());
			
			
			// Add Button
			final JButton addButton = new JButton(language.getString("Common.add"));
			addButton.addActionListener( new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addButton_actionPerformed();
				}
			});
			
			// Remove selected button
			final JButton removeSelectedButton = new JButton(language.getString("AddNewUploadsDialog.button.removeSelected"));
			removeSelectedButton.addActionListener( new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addNewUploadsTable.removeSelected();
					updateTitle(); // correct the title's item count
				}
			});

			// Remove but selected button
			final JButton removeButSeelctedButton = new JButton(language.getString("AddNewUploadsDialog.button.removeButSelected"));
			removeButSeelctedButton.addActionListener( new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addNewUploadsTable.removeButSelected();
					updateTitle(); // correct the title's item count
				}
			});
			
			// OK Button
			final JButton okButton = new JButton(language.getString("Common.ok"));
			okButton.addActionListener( new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					FileTransferManager.inst().getUploadManager().getModel().addUploadItemList(getUploads());
					
					dispose();
				}
			});

			// Cancel Button
			final JButton cancelButton = new JButton(language.getString("Common.cancel"));
			cancelButton.addActionListener( new ActionListener() {
				public void actionPerformed(final ActionEvent e) {
					addNewUploadsTableModel.clearDataModel();
					dispose();
				}
			});

			// Button row
			final JPanel buttonsPanel = new JPanel(new BorderLayout());
			buttonsPanel.setLayout( new BoxLayout( buttonsPanel, BoxLayout.X_AXIS ));

			buttonsPanel.add( addButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( removeSelectedButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( removeButSeelctedButton );

			buttonsPanel.add( Box.createHorizontalGlue() );

			buttonsPanel.add( cancelButton );
			buttonsPanel.add(Box.createRigidArea(new Dimension(10,3)));
			buttonsPanel.add( okButton );
			buttonsPanel.setBorder(BorderFactory.createEmptyBorder(10,0,0,0));
			
			
			// Upload Table
			addNewUploadsTableModel = new AddNewUploadsTableModel(this);
			addNewUploadsTable = new AddNewUploadsTable( addNewUploadsTableModel );
			new TableFindAction().install(addNewUploadsTable);
			addNewUploadsTable.setRowSelectionAllowed(true);
			addNewUploadsTable.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			addNewUploadsTable.setRowHeight(18);
			final JScrollPane scrollPane = new JScrollPane(addNewUploadsTable);
			scrollPane.setWheelScrollingEnabled(true);

			// Main panel
			final JPanel mainPanel = new JPanel(new BorderLayout());
			mainPanel.add( scrollPane, BorderLayout.CENTER );
			mainPanel.add( buttonsPanel, BorderLayout.SOUTH );
			mainPanel.setBorder(BorderFactory.createEmptyBorder(5,7,7,7));

			this.getContentPane().setLayout(new BorderLayout());
			this.getContentPane().add(mainPanel, null);
			
			// Init popup menu
			this.initTablePopupMenu();
			
			// Init hot key listener
			this.assignHotkeys(mainPanel);
			
			addNewUploadsTable.setAutoResizeMode( JTable.AUTO_RESIZE_NEXT_COLUMN);
			int tableWidth = getWidth();
			
			TableColumnModel headerColumnModel = addNewUploadsTable.getTableHeader().getColumnModel();
			int defaultColumnWidthRatio[] = addNewUploadsTableModel.getDefaultColumnWidthRatio();
			for(int i = 0 ; i < defaultColumnWidthRatio.length ; i++) {
				headerColumnModel.getColumn(i).setMinWidth(5);
				int ratio = tableWidth * defaultColumnWidthRatio[i] / 100;
				headerColumnModel.getColumn(i).setPreferredWidth(ratio);
			}
			
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Warns the user every time they try to enable compression for an upload,
	 * unless they've checked "don't ask me again".
	 */
	private boolean confirmEnableCompression() {
		// if the user unchecks "show this message again", they'll never be asked again
		// until they clear frost.ini.
		final int answer = MiscToolkit.showSuppressableConfirmDialog(
				this,
				language.getString("AddNewUploadsDialog.confirmEnableCompression.dialogBody"),
				language.getString("AddNewUploadsDialog.confirmEnableCompression.dialogTitle"),
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				SettingsClass.CONFIRM_ENABLE_UPLOAD_COMPRESSION, // will automatically choose "yes" if this is false
				language.getString("Common.suppressConfirmationCheckbox") );
		// true if they want to enable compression, otherwise false
		return ( answer == JOptionPane.YES_OPTION );
	}
	
	private void initTablePopupMenu() {
		// Rename
		final JMenuItem renameMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.rename"));
		renameMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewUploadsTable.new SelectedItemsAction() {
					protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
						String newName = askForNewName(addNewUploadsTableMember.getUploadItem().getUnprefixedFileName());
						if( newName != null ) {
							addNewUploadsTableMember.getUploadItem().setFileName(newName);
						}
					}
				};
			}
		});
		
		// Add Prefix
		final JMenuItem addPrefixMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.addPrefix"));
		addPrefixMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				final String fileNamePrefix = MiscToolkit.showInputDialog(
						AddNewUploadsDialog.this,
						language.getString("AddNewUploadsDialog.addPrefix.dialogBody"),
						language.getString("AddNewUploadsDialog.addPrefix.dialogTitle"),
						MiscToolkit.QUESTION_MESSAGE
					);
				if( fileNamePrefix != null ) {
					addNewUploadsTable.new SelectedItemsAction() {
						protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
							addNewUploadsTableMember.getUploadItem().setFileNamePrefix(fileNamePrefix);
						}
					};
				}
			}
		});
		
		
		// Enable compression
		final JMenuItem enableCompressionMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.enableCompression"));
		enableCompressionMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				if( ! confirmEnableCompression() ) { return; } // do nothing if they select No
				addNewUploadsTable.new SelectedItemsAction() {
					protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
						addNewUploadsTableMember.getUploadItem().setCompress(true);
					}
				};
			}
		});
		
		
		// Disable compression
		final JMenuItem disableCompressionMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.disableCompression"));
		disableCompressionMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				if( ! confirmEnableCompression() ) { return; } // do nothing if they select No
				addNewUploadsTable.new SelectedItemsAction() {
					protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
						addNewUploadsTableMember.getUploadItem().setCompress(false);
					}
				};
			}
		});
		
		
		// Freenet compatibility mode
		final JMenu changeFreenetCompatibilityModeMenu = new JMenu(language.getString("AddNewUploadsDialog.popupMenu.changeFreenetCompatibilityMode"));
		for( final String freenetCompatibilityMode : Core.getCompatManager().getAllModes() ) {
			if( freenetCompatibilityMode == "COMPAT_UNKNOWN" ) { continue; } // don't add the internal "COMPAT_UNKNOWN" value to the popup menu

			JMenuItem changeFreenetCompatibilityModeMenuItem = new JMenuItem(freenetCompatibilityMode);
			changeFreenetCompatibilityModeMenuItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent actionEvent) {
					addNewUploadsTable.new SelectedItemsAction() {
						protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
							addNewUploadsTableMember.getUploadItem().setFreenetCompatibilityMode(freenetCompatibilityMode);
						}
					};
				}
			});
			changeFreenetCompatibilityModeMenu.add(changeFreenetCompatibilityModeMenuItem);
		}


		// Crypto key menu
		final JMenu changeCryptoKeyMenu = new JMenu(language.getString("AddNewUploadsDialog.popupMenu.cryptoKeySubmenu"));
		// Crypto key: setUserKey
		JMenuItem changeCryptoKeyMenuItem_setUserKey = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.cryptoKeySubmenu.setUserKey"));
		changeCryptoKeyMenuItem_setUserKey.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				setUserCryptoKey_actionPerformed();
			}
		});
		changeCryptoKeyMenu.add(changeCryptoKeyMenuItem_setUserKey);
		// Crypto key: setRandomKey
		JMenuItem changeCryptoKeyMenuItem_setRandomKey = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.cryptoKeySubmenu.setRandomKey"));
		changeCryptoKeyMenuItem_setRandomKey.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				setRandomCryptoKey_actionPerformed();
			}
		});
		changeCryptoKeyMenu.add(changeCryptoKeyMenuItem_setRandomKey);
		// Crypto key: copyCryptoKey
		changeCryptoKeyMenu.addSeparator();
		JMenuItem changeCryptoKeyMenuItem_copyCryptoKey = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.cryptoKeySubmenu.copyCryptoKey"));
		changeCryptoKeyMenuItem_copyCryptoKey.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				copyCryptoKey_actionPerformed();
			}
		});
		changeCryptoKeyMenu.add(changeCryptoKeyMenuItem_copyCryptoKey);
		// Crypto key: clearKey
		changeCryptoKeyMenu.addSeparator();
		JMenuItem changeCryptoKeyMenuItem_clearKey = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.cryptoKeySubmenu.clearKey"));
		changeCryptoKeyMenuItem_clearKey.addActionListener(new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				clearCryptoKey_actionPerformed();
			}
		});
		changeCryptoKeyMenu.add(changeCryptoKeyMenuItem_clearKey);

		
		// Change Priority
		final JMenu changePriorityMenu = new JMenu(language.getString("Common.priority.changePriority"));
		for(final FreenetPriority priority : FreenetPriority.values()) {
			JMenuItem priorityMenuItem = new JMenuItem(priority.getName());
			priorityMenuItem.addActionListener(new ActionListener() {
				public void actionPerformed(final ActionEvent actionEvent) {
					addNewUploadsTable.new SelectedItemsAction() {
						protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
							addNewUploadsTableMember.getUploadItem().setPriority(priority);
						}
					};
				}
			});
			changePriorityMenu.add(priorityMenuItem);
		}
		
		// Enable upload
		final JMenuItem enableUploadMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.enableUpload"));
		enableUploadMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewUploadsTable.new SelectedItemsAction() {
					protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
						addNewUploadsTableMember.getUploadItem().setEnabled(true);
					}
				};
			}
		});
		
		
		// Disable upload
		final JMenuItem disableUploadMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.popupMenu.disableUpload"));
		disableUploadMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewUploadsTable.new SelectedItemsAction() {
					protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
						addNewUploadsTableMember.getUploadItem().setEnabled(false);
					}
				};
			}
		});
		
		// Remove Selected
		final JMenuItem removeSelectedMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.button.removeSelected"));
		removeSelectedMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewUploadsTable.removeSelected();
				updateTitle(); // correct the title's item count
			}
		});
		
		// Remove But Selected
		final JMenuItem removeButSelectedMenuItem = new JMenuItem(language.getString("AddNewUploadsDialog.button.removeButSelected"));
		removeButSelectedMenuItem.addActionListener( new ActionListener() {
			public void actionPerformed(final ActionEvent actionEvent) {
				addNewUploadsTable.removeButSelected();
				updateTitle(); // correct the title's item count
			}
		});
		
		tablePopupMenu = new JSkinnablePopupMenu();
		tablePopupMenu.add(renameMenuItem);
		tablePopupMenu.add(addPrefixMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(enableCompressionMenuItem);
		tablePopupMenu.add(disableCompressionMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(changeFreenetCompatibilityModeMenu);
		tablePopupMenu.add(changeCryptoKeyMenu);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(changePriorityMenu);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(enableUploadMenuItem);
		tablePopupMenu.add(disableUploadMenuItem);
		tablePopupMenu.addSeparator();
		tablePopupMenu.add(removeSelectedMenuItem);
		tablePopupMenu.add(removeButSelectedMenuItem);
		
		addNewUploadsTable.addMouseListener(new TablePopupMenuMouseListener());
	}

	private void updateTitle() {
		// update the dialog title to show the download item count
		final int itemCount = addNewUploadsTableModel.getRowCount();
		setTitle(language.getString("AddNewUploadsDialog.title") + " (" + itemCount + ")");
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
                addNewUploadsTable.new SelectedItemsAction() {
                    protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
                        addNewUploadsTableMember.getUploadItem().setPriority(prio);
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
                addNewUploadsTable.removeSelected();
                updateTitle(); // correct the title's item count
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0, true), "DeleteSelectedFiles");
        p.getActionMap().put("DeleteSelectedFiles", deleteSelectedAction);

        // Shift+Delete - remove but selected
        final Action deleteButSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewUploadsTable.removeButSelected();
                updateTitle(); // correct the title's item count
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, InputEvent.SHIFT_MASK, true), "DeleteButSelectedFiles");
        p.getActionMap().put("DeleteButSelectedFiles", deleteButSelectedAction);

        // R - rename selected
        final Action renameSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewUploadsTable.new SelectedItemsAction() {
                    protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
                        String newName = askForNewName(addNewUploadsTableMember.getUploadItem().getUnprefixedFileName());
                        if( newName != null ) {
                            addNewUploadsTableMember.getUploadItem().setFileName(newName);
                        }
                    }
                };
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0, true), "RenameSelectedFiles");
        p.getActionMap().put("RenameSelectedFiles", renameSelectedAction);

        // P - prefix selected
        final Action prefixSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                final String fileNamePrefix = MiscToolkit.showInputDialog(
                        AddNewUploadsDialog.this,
                        language.getString("AddNewUploadsDialog.addPrefix.dialogBody"),
                        language.getString("AddNewUploadsDialog.addPrefix.dialogTitle"),
                        MiscToolkit.QUESTION_MESSAGE
                        );
                if( fileNamePrefix != null ) {
                    addNewUploadsTable.new SelectedItemsAction() {
                        protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
                            addNewUploadsTableMember.getUploadItem().setFileNamePrefix(fileNamePrefix);
                        }
                    };
                }
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_P, 0, true), "PrefixSelectedFiles");
        p.getActionMap().put("PrefixSelectedFiles", prefixSelectedAction);

        // S - set custom crypto key
        final Action setUserCryptoKeySelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setUserCryptoKey_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_S, 0, true), "SetUserCryptoKeySelectedFiles");
        p.getActionMap().put("SetUserCryptoKeySelectedFiles", setUserCryptoKeySelectedAction);

        // G - generate random crypto key
        final Action setRandomCryptoKeySelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                setRandomCryptoKey_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0, true), "SetRandomCryptoKeySelectedFiles");
        p.getActionMap().put("SetRandomCryptoKeySelectedFiles", setRandomCryptoKeySelectedAction);

        // C - copy current crypto key
        final Action copyCryptoKeySelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                copyCryptoKey_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_C, 0, true), "CopyCryptoKeySelectedFiles");
        p.getActionMap().put("CopyCryptoKeySelectedFiles", copyCryptoKeySelectedAction);

        // Z - clear custom crypto key
        final Action clearCryptoKeySelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                clearCryptoKey_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, 0, true), "ClearCryptoKeySelectedFiles");
        p.getActionMap().put("ClearCryptoKeySelectedFiles", clearCryptoKeySelectedAction);

        // D - invert "compression" state
        final Action invertCompressionSelectedAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                if( ! confirmEnableCompression() ) { return; } // do nothing if they select No
                addNewUploadsTable.new SelectedItemsAction() {
                    protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
                        final FrostUploadItem ulItem = addNewUploadsTableMember.getUploadItem();
                        ulItem.setCompress( !ulItem.getCompress() ); // invert state
                    }
                };
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_D, 0, true), "InvertCompressionSelectedFiles");
        p.getActionMap().put("InvertCompressionSelectedFiles", invertCompressionSelectedAction);

        // E - invert "enabled" state
        final Action invertEnabledAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewUploadsTable.new SelectedItemsAction() {
                    protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
                        // null = invert the current state of the item
                        addNewUploadsTableMember.getUploadItem().setEnabled(null);
                    }
                };
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_E, 0, true), "InvertEnabled");
        p.getActionMap().put("InvertEnabled", invertEnabledAction);

        // A - add more files
        final Action addFilesAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addButton_actionPerformed();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_A, 0, true), "AddFiles");
        p.getActionMap().put("AddFiles", addFilesAction);

        // Ctrl+Enter - Ok! (The CTRL is added to make it harder to accidentally hit)
        final Action okAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                FileTransferManager.inst().getUploadManager().getModel().addUploadItemList(getUploads());
                dispose();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_MASK, true), "OkAction");
        p.getActionMap().put("OkAction", okAction);

        // Ctrl+Esc - Cancel! (The CTRL is added to make it harder to accidentally hit)
        final Action cancelAction = new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                addNewUploadsTableModel.clearDataModel();
                dispose();
            }
        };
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, InputEvent.CTRL_MASK, true), "CancelAction");
        p.getActionMap().put("CancelAction", cancelAction);
    }
	
	
	
	public void startDialog() {
		// Open file picker
		List<FrostUploadItem> frostUploadItemList = addFileChooser();

		if( frostUploadItemList.size() > 0 ) {
			// load data into table
			this.loadNewUploadsIntoTable(frostUploadItemList);
			setLocationRelativeTo(parentFrame);

			// display table
			setVisible(true); // blocking!
		} else {
			// user didn't pick any files, so let's kill the "add new uploads" dialog instead of annoying the user with a useless, empty table
			dispose();
		}
	}
	
	private void loadNewUploadsIntoTable(final List<FrostUploadItem> frostUploadItemList) {
		// delete all existing model items
		this.addNewUploadsTableModel.clearDataModel();

		// add only the *first* instance of each unique file path, to avoid duplicates
		int duplicates = 0;
		final Set<String> seenPaths = new HashSet<String>();
		for( final FrostUploadItem ulItem : frostUploadItemList ) {
			String thisFilePath;
			try {
				// get the canonical path of the file; which means the absolute path with
				// all symlinks and "." and ".." resolved, and other OS-dependent stuff.
				thisFilePath = ulItem.getFile().getCanonicalPath();
			} catch ( final Exception e ) {
				// filesystem query failed or had no permission, so skip this file
				thisFilePath = null;
			}
			if( thisFilePath == null ){ continue; }

			// if this file has already been added, then increase the duplicate count and skip it
			if( seenPaths.contains( thisFilePath ) ) {
				++duplicates;
				continue;
			}

			// add the filepath to the list of seen paths, and add the item to the model
			seenPaths.add( thisFilePath );
			this.addNewUploadsTableModel.addRow(new AddNewUploadsTableMember(ulItem));
		}

		// reset the sort-order to the default (column 0 "Filename", Ascending) so that they don't get confused
		addNewUploadsTable.setSortedColumn(0, true);
		updateTitle(); // correct the title's item count

		// if there were duplicate files, warn the user (this msg is shown before the dialog becomes visible)
		// centers the message around the mainframe if shown before dialog is visible, otherwise around dialog
		if( duplicates > 0 ) {
			MiscToolkit.showMessageDialog( // blocking!
					( this != null && this.isVisible() ? this : MainFrame.getInstance() ),
					language.formatMessage("AddNewUploadsDialog.errorDialog.duplicateFiles.body", duplicates),
					language.getString("AddNewUploadsDialog.errorDialog.duplicateFiles.title"),
					MiscToolkit.INFORMATION_MESSAGE);
		}
	}

	private void addButton_actionPerformed() {
		List<FrostUploadItem> newUploadsList = addFileChooser();
		// verify that the key list exists and has at least 1 item
		if( newUploadsList == null || newUploadsList.size() == 0 ) {
		   	return;
		}

		// now merge it with the current list of uploads
		// NOTE: we'll base the list on the current list, and then *add* the new ones to the end,
		// since the duplicate-check only adds the *first* encountered version of each filepath.
		// so if the user clicks "add" multiple times, we want to keep the version of the file that
		// was already in the "Add new uploads" table and may have had a special cryptokey, mode
		// or other settings applied to it already, which we of course want to preserve!
		List<FrostUploadItem> newTableList = getUploads(); // is a LinkedList, thus guaranteeing insert order
		newTableList.addAll(newUploadsList);

		// load data into table (skips all duplicates)
		this.loadNewUploadsIntoTable(newTableList);
	}

	private void setUserCryptoKey_actionPerformed() {
		// repeatedly ask for a key until the user enters a valid key or cancels
		String inputKey = "";
		while( true ) {
			// ask the user to provide a crypto key string
			inputKey = (String) MiscToolkit.showInputDialog(
					AddNewUploadsDialog.this,
					language.getString("AddNewUploadsDialog.setUserKey.dialogBody"),
					language.getString("AddNewUploadsDialog.setUserKey.dialogTitle"),
					MiscToolkit.QUESTION_MESSAGE,
					null,
					null,
					inputKey // show the user's last-written key if this is a repeated showing
					);

			// only stop asking for keys if they've hit "cancel"
			if( inputKey == null ) {
				break;
			}

			// check if the user has inadvertently pasted multiple keys (via the "copy keys" function)
			if( inputKey.contains("Key #") ) {
				MiscToolkit.showMessageDialog(
						AddNewUploadsDialog.this,
						language.getString("AddNewUploadsDialog.setUserKey.pastedMultiErrorDialogBody"),
						language.getString("AddNewUploadsDialog.setUserKey.pastedMultiErrorDialogTitle"),
						MiscToolkit.ERROR_MESSAGE
						);
				// show the original input dialog again, with their last input
				continue;
			}

			// validate the user input and display an error if it failed the check
			final String cleanKey = FrostUploadItem.validateCryptoKey(inputKey);
			if( cleanKey == null ) {
				MiscToolkit.showMessageDialog(
						AddNewUploadsDialog.this,
						language.getString("AddNewUploadsDialog.setUserKey.errorDialogBody"),
						language.getString("AddNewUploadsDialog.setUserKey.errorDialogTitle"),
						MiscToolkit.ERROR_MESSAGE
						);
				// show the original input dialog again, with their last input
				continue;
			}

			// apply the valid, clean version of the key to all of the selected items
			addNewUploadsTable.new SelectedItemsAction() {
				protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
					addNewUploadsTableMember.getUploadItem().setCryptoKey(cleanKey);
				}
			};

			// break the loop, since the user provided a valid key
			break;
		}
	}

	private void setRandomCryptoKey_actionPerformed() {
		// generate a single random key and apply the same one to all selected items
		final String randomCryptoKey = FrostUploadItem.generateRandomCryptoKey();
		addNewUploadsTable.new SelectedItemsAction() {
			protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
				addNewUploadsTableMember.getUploadItem().setCryptoKey(randomCryptoKey);
			}
		};
	}
	
	private void copyCryptoKey_actionPerformed() {
		// perform a raw read of the selected rows and their upload item properties
		final int[] selectedRows = addNewUploadsTable.getSelectedRows();
		if( selectedRows.length > 0 ) {
			final Set<String> customCryptoKeys = new LinkedHashSet<String>();

			// go through every selected row
			final int rowCount = addNewUploadsTable.getRowCount();
			for( int rowIdx : selectedRows ) {
				if( rowIdx >= rowCount ) { continue; } // avoid invalid row numbers just in case

				// retrieve the custom key (if any) for the current item
				final FrostUploadItem ulItem = addNewUploadsTableModel.getRow(rowIdx).getUploadItem();
				final String cryptoKey = ulItem.getCryptoKey();
				if( cryptoKey != null ) {
					// the LinkedHashSet de-duplicates all added keys but maintains insert-order
					customCryptoKeys.add(cryptoKey);
				}
			}

			// build a newline-separated list of all *unique* crypto keys (may be empty)
			// NOTE: the list of keys is given in the order of the user's selected items
			int keyCount = 0;
			final boolean multiKeys = ( customCryptoKeys.size() > 1 ? true : false );
			final StringBuilder sb = new StringBuilder();
			for( String cryptoKey : customCryptoKeys ) {
				++keyCount;
				sb.append("\n");
				if( multiKeys ) {
					sb.append("Key #").append(Integer.toString(keyCount)).append(": ");
				}
				sb.append(cryptoKey);
			}
			if( sb.length() > 0 ) { // found keys, so delete the leading newline
				sb.deleteCharAt(0);
			}
			final String foundKeys = sb.toString();

			// display an error if there were no custom crypto keys for any selected files
			if( keyCount == 0 ) {
				MiscToolkit.showMessageDialog(
						AddNewUploadsDialog.this,
						language.getString("AddNewUploadsDialog.copyCryptoKey.errorDialogBody"),
						language.getString("AddNewUploadsDialog.copyCryptoKey.errorDialogTitle"),
						MiscToolkit.ERROR_MESSAGE
						);
				// abort without affecting the clipboard
				return;
			}

			// put the list of keys (may be empty) in the user's clipboard
			try {
				final Clipboard c = Toolkit.getDefaultToolkit().getSystemClipboard();
				final StringSelection cData = new StringSelection(foundKeys);
				c.setContents(cData, cData);
			} catch( final Exception e ) {
				// warn the user that the keys couldn't be copied to the clipboard
				MiscToolkit.showMessageDialog(
						AddNewUploadsDialog.this,
						language.getString("AddNewUploadsDialog.copyCryptoKey.clipboardErrorDialogBody"),
						language.getString("AddNewUploadsDialog.copyCryptoKey.clipboardErrorDialogTitle"),
						MiscToolkit.ERROR_MESSAGE
						);
				// abort, since we couldn't affect the clipboard
				return;
			}

			// if multiple keys were copied, warn the user that they can't simply paste the list into other items
			if( keyCount > 1 ) {
				MiscToolkit.showMessageDialog(
						AddNewUploadsDialog.this,
						language.formatMessage("AddNewUploadsDialog.copyCryptoKey.multiDialogBody",
							keyCount),
						language.getString("AddNewUploadsDialog.copyCryptoKey.multiDialogTitle"),
						MiscToolkit.INFORMATION_MESSAGE
						);
			}
		}
	}

	private void clearCryptoKey_actionPerformed() {
		// clears the custom crypto keys for all selected items
		addNewUploadsTable.new SelectedItemsAction() {
			protected void action(AddNewUploadsTableMember addNewUploadsTableMember) {
				addNewUploadsTableMember.getUploadItem().clearCryptoKey();
			}
		};
	}

	private String askForNewName(final String oldName) {
		return (String) MiscToolkit.showInputDialog(
			this,
			language.getString("AddNewUploadsDialog.renameFileDialog.dialogBody"),
			language.getString("AddNewUploadsDialog.renameFileDialog.dialogTitle"),
			MiscToolkit.QUESTION_MESSAGE,
			null,
			null,
			oldName
		);
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
				if( addNewUploadsTable.getSelectedRowCount() > 0 ) {
					tablePopupMenu.show(addNewUploadsTable, e.getX(), e.getY());
				}
			}
		}
	}
	
	private class AddNewUploadsTableMember extends TableMember.BaseTableMember<AddNewUploadsTableMember> {
		
		FrostUploadItem frostUploadItem;
		
		public AddNewUploadsTableMember(final FrostUploadItem frostUploadItem){
			this.frostUploadItem = frostUploadItem;
		}
		
		@Override
		public Comparable<?> getValueAt(final int column) {
			try {
				switch( column ) {
					case 0: // name
						return frostUploadItem.getFileName();
					case 1: // extension
						final String ext = FileAccess.getFileExtension(frostUploadItem.getFileName());
						if( ext != null ) { return ext; }
						return "-";
					case 2: // path
						return frostUploadItem.getFile().getCanonicalPath();
					case 3: // size
						return FormatterUtils.formatSize(frostUploadItem.getFileSize());
					case 4: // compress
						return frostUploadItem.getCompress();
					case 5: // compatibility mode
						// NOTE: we save table space (and make the column much more readable) by not showing
						// the leading "COMPAT_" string, since they all begin with that; so "COMPAT_1468" is
						// simply shown as "1468" (for example). we don't use the more efficient .substring(7),
						// in the unlikely case that future Freenet versions change the name of these
						// constants to no longer have a leading "COMPAT_".
						return frostUploadItem.getFreenetCompatibilityMode().replace("COMPAT_", "");
					case 6: // crypto key
						final String cryptoKey = frostUploadItem.getCryptoKey();
						if( cryptoKey == null ) {
							return autoCryptoKeyString;
						} else {
							return cryptoKey;
						}
					case 7: // priority
						return frostUploadItem.getPriority(); 
					case 8: // enabled
						return frostUploadItem.isEnabled();
					default :
						throw new RuntimeException("Unknown Column pos");
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		public FrostUploadItem getUploadItem(){
			return frostUploadItem;
		}

		@Override
		public int compareTo(final AddNewUploadsTableMember otherTableMember, final int tableColumnIndex) {
			// override sorting of specific columns
			if( tableColumnIndex == 3 ) { // filesize
				// if the other table member object is null, just return -1 instantly so that the non-null
				// member we're comparing will be sorted above the null-row. this is just a precaution.
				// NOTE: we could also make sure "trackDownloadKey" is non-null for each, but it always is.
				if( otherTableMember == null ) { return -1; }

				// we know that filesize is a long, so we don't need any null-checks since longs are a basic type (never null).
				return Mixed.compareLong(frostUploadItem.getFileSize(), otherTableMember.getUploadItem().getFileSize());
			} else if( tableColumnIndex == 6 ) { // crypto key
				// perform a case-insensitive String comparison with null-support.
				// nulls (no crypto keys) are sorted last in ascending mode.
				return Mixed.compareStringWithNullSupport(frostUploadItem.getCryptoKey(), otherTableMember.getUploadItem().getCryptoKey(), /*ignoreCase=*/true);
			} else {
				// all other columns use the default case-insensitive string comparator
				return super.compareTo(otherTableMember, tableColumnIndex);
			}
		}
	}
	
	private List<FrostUploadItem> getUploads() {
		List<FrostUploadItem> frostUploadItemList = new LinkedList<FrostUploadItem>();
		final int numberOfRows = addNewUploadsTableModel.getRowCount();
		for( int indexPos = 0; indexPos < numberOfRows; indexPos++) {
			frostUploadItemList.add( addNewUploadsTableModel.getRow(indexPos).getUploadItem() );
		}
		return frostUploadItemList;
	}


	private List<FrostUploadItem> addFileChooser() {
		
		List<FrostUploadItem> frostUploadItemList = new ArrayList<FrostUploadItem>();
		
		final JFileChooser fc = new JFileChooser(Core.frostSettings.getValue(SettingsClass.DIR_LAST_USED));
		fc.setDialogTitle(language.getString("AddNewUploadsDialog.filechooser.title"));
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
			return frostUploadItemList; // user canceled file dialog: return our empty list
		}

		// retrieve the list of chosen files
		final File[] selectedFiles = fc.getSelectedFiles();
		if( selectedFiles == null || selectedFiles.length == 0 ) {
			return frostUploadItemList; // file dialog failed: return our empty list
		}

		final List<File> uploadFileItems = new LinkedList<File>();
		for( final File element : selectedFiles ) {
			// build a list of the paths of all chosen files + the files in all chosen directories
			uploadFileItems.addAll( FileAccess.getAllEntries(element) );
		}

		// remember last upload dir used
		if( uploadFileItems.size() > 0 ) {
			final File file = uploadFileItems.get(0);
			Core.frostSettings.setValue(SettingsClass.DIR_LAST_USED, file.getParent());
		}

		// turn all of the user's chosen files into FrostUploadItem objects, with compression DISABLED
		// by default, since it's very harmful (most uploads are already-compressed multimedia)
		for( final File file : uploadFileItems ) {
			frostUploadItemList.add( new FrostUploadItem(file, false) );
		}

		return frostUploadItemList;
	}


	private static class AddNewUploadsTableModel extends SortedTableModel<AddNewUploadsTableMember>{ 
		private Language language = null;

		protected static String columnNames[];

		private AddNewUploadsDialog parentDialog;

		protected final static Class<?> columnClasses[] = {
			String.class, // filename
			String.class, // extension
			String.class, // path
			Long.class, // filesize
			Boolean.class, // compress
			String.class, // compatibility mode
			String.class, // crypto key
			String.class, // priority
			Boolean.class, // enabled
		};
		
		private final int[] defaultColumnWidthRatio = {
			20, // filename
			5, // extension
			35, // path
			5, // filesize
			5, // compress
			10, // compatibility mode
			10, // crypto key
			5, // priority
			5, // enabled
		};

		public AddNewUploadsTableModel(AddNewUploadsDialog parentDialog) {
			super();
			
			this.parentDialog = parentDialog;
			language = Language.getInstance();
			refreshLanguage();
			
			assert columnClasses.length == columnNames.length;
		}

		private void refreshLanguage() {
			// NOTE: the auto-cryptokey string is a shared, *static* property of the parent, so that
			// we can easily access it from this static class and other children. the fact that it's
			// static is no problem. the language strings are only loaded/set when an "add new uploads"
			// dialog is created, and aren't changed after that (even if the user changes the language).
			// furthermore, it's extremely unlikely that the user will mess with language and open multiple
			// "add" dialogs *at the same time*. so for most users, this value is set once and then
			// never touched again. but even if the user causes it to reload, it's no problem.
			autoCryptoKeyString = language.getString("AddNewUploadsDialog.table.cryptoKey.autoCryptoKey");

			// load names of all table columns
			columnNames = new String[]{
				language.getString("AddNewUploadsDialog.table.name"),
				language.getString("AddNewUploadsDialog.table.extension"),
				language.getString("AddNewUploadsDialog.table.path"),
				language.getString("AddNewUploadsDialog.table.size"),
				language.getString("AddNewUploadsDialog.table.compress"),
				language.getString("AddNewUploadsDialog.table.freenetCompatibilityMode"),
				language.getString("AddNewUploadsDialog.table.cryptoKey"),
				language.getString("Common.priority"),
				language.getString("Common.enabled"),
			};
		}

		@Override
		public boolean isCellEditable(int row, int col) {
			switch(col){
				case 4: // compression on/off
				case 8: // enabled on/off
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
				case 4: // compression on/off
					if( ! parentDialog.confirmEnableCompression() ) { return; } // do nothing if they select No
					getRow(row).getUploadItem().setCompress((Boolean) aValue);
					// select 1st column to fix Java cell focus bug, where it otherwise selects the clicked cell and swallows all keyboard input
					parentDialog.addNewUploadsTable.setColumnSelectionInterval(0,0);
					return;
				case 8: // enabled on/off
					getRow(row).getUploadItem().setEnabled((Boolean) aValue);
					// select 1st column to fix Java cell focus bug, where it otherwise selects the clicked cell and swallows all keyboard input
					parentDialog.addNewUploadsTable.setColumnSelectionInterval(0,0);
					return;
				default: // all other columns are uneditable
					return;
			}
		}
		
		public int[] getDefaultColumnWidthRatio() {
			return defaultColumnWidthRatio;
		}
	}
	
	private class AddNewUploadsTable extends SortedTable<AddNewUploadsTableMember> {
		
		public AddNewUploadsTable(SortedTableModel<AddNewUploadsTableMember> model) {
			super(model);
			this.setIntercellSpacing(new Dimension(5, 1));
		}

		// override the default sort order when clicking different columns
		@Override
		public boolean getColumnDefaultAscendingState(final int col) {
			if( col == 4 || col == 8 ) {
				// sort boolean columns ("compress" and "enabled") descending by default
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
			final AddNewUploadsTableModel tableModel = (AddNewUploadsTableModel) getModel();
			
			switch(realColumnIndex){
				case 4: // compression on/off
					return ( language != null ? language.getString("AddNewUploadsDialog.table.compress.tooltip") : "" );
				case 0: // lists all other valid column #s...
				case 1:
				case 2:
				case 3:
				case 5:
				case 6:
				case 7:
				case 8:
					return tableModel.getValueAt(rowIndex, realColumnIndex).toString();
				default:
					assert false;
			}
			return tableModel.getValueAt(rowIndex, realColumnIndex).toString();
		}
		
		@Override
		public TableCellRenderer getCellRenderer(final int rowIndex, final int columnIndex) {
			switch(columnIndex){
				case 0: // filename
				case 1: // extension
				case 2: // path
				case 3: // filesize
				case 5: // compatibility mode
				case 6: // crypto key
				case 7: // priority
					return super.getCellRenderer(rowIndex, columnIndex);
				case 4: // compression on/off
				case 8: // enabled on/off
					return BooleanCell.RENDERER;
				default:
					assert false;
			}
			return super.getCellRenderer(rowIndex, columnIndex);
		}
		
		@Override
		public TableCellEditor getCellEditor(final int rowIndex, final int columnIndex ) {
			switch(columnIndex){
				case 0: // filename
				case 1: // extension
				case 2: // path
				case 3: // filesize
				case 5: // compatibility mode
				case 6: // crypto key
				case 7: // priority
					return super.getCellEditor(rowIndex, columnIndex);
				case 4: // compression on/off
				case 8: // enabled on/off
					return BooleanCell.EDITOR;
				default:
					assert false;
			}
			return super.getCellEditor(rowIndex, columnIndex);
		}
		
		
	}
}
