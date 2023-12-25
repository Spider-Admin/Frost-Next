/*
  IdentitiesBrowser.java / Frost
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
package frost.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ProgressMonitor;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;

import org.joda.time.*;

import frost.Core;
import frost.MainFrame;
import frost.SettingsClass;
import frost.fileTransfer.common.TableBackgroundColors;
import frost.gui.model.SortedTableModel;
import frost.gui.model.TableMember;
import frost.identities.Identity;
import frost.messaging.frost.boards.Board;
import frost.storage.IdentitiesXmlDAO;
import frost.storage.perst.identities.IdentitiesStorage;
import frost.util.CopyToClipboard;
import frost.util.DateFun;
import frost.util.Mixed;
import frost.util.gui.FrostSwingWorker;
import frost.util.gui.JSkinnablePopupMenu;
import frost.util.gui.MiscToolkit;
import frost.util.gui.TrustStateColors;
import frost.util.gui.search.TableFindAction;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;

@SuppressWarnings("serial")
public class IdentitiesBrowser extends JDialog {

    private Language language = null;

    private JPanel jContentPane = null;
    private JScrollPane jScrollPane = null;
    private SortedTable<InnerTableMember> identitiesTable = null;
    private JPanel buttonPanel = null;
    private JPanel mainPanel = null;
    private JButton Bclose = null;
    private JButton BmarkFRIEND = null;
    private JButton BmarkGOOD = null;
    private JButton BmarkNEUTRAL = null;
    private JButton BmarkBAD = null;
    private JButton Bdelete = null;

    private InnerTableModel tableModel = null;

    private JButton Bcleanup = null;
    private final JFrame parent;

    private List<InnerTableMember> allTableMembers;

    private final boolean showColoredLines;

    private PopupMenu popupMenu = null;
    private final Listener listener = new Listener();

    private final long minCleanupTime;

    /**
     * This is the default constructor
     */
    public IdentitiesBrowser(final JFrame parent) {
        super(parent);
        this.parent = parent;
        language = Language.getInstance();
        setModal(true);
        showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
        initialize();

        minCleanupTime = getMinCleanupTime();

        setLocationRelativeTo(parent);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(final WindowEvent e) {
                dispose();
            }
            @Override
            public void windowClosed(final WindowEvent e) {
                // update messages if a board is shown
                MainFrame.getInstance().getMessagePanel().updateTableAfterChangeOfIdentityState();
            }
        });
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    }

    /**
     * This method initializes this
     */
    private void initialize() {
        this.setTitle("IdentitiesBrowser.title");
        this.setBounds(new java.awt.Rectangle(0,0,630,420));
        this.setContentPane(getJContentPane());

        getBmarkFRIEND().setText("");
        getBmarkBAD().setText("");
        getBmarkNEUTRAL().setText("");
        getBmarkGOOD().setText("");

        getBmarkFRIEND().setIcon(MiscToolkit.loadImageIcon("/data/toolbar/weather-clear.png"));
        getBmarkGOOD().setIcon(MiscToolkit.loadImageIcon("/data/toolbar/weather-few-clouds.png"));
        getBmarkNEUTRAL().setIcon(MiscToolkit.loadImageIcon("/data/toolbar/weather-overcast.png"));
        getBmarkBAD().setIcon(MiscToolkit.loadImageIcon("/data/toolbar/weather-storm.png"));

        MiscToolkit.configureButton(getBmarkFRIEND(), "MessagePane.toolbar.tooltip.setToFRIEND", language);
        MiscToolkit.configureButton(getBmarkBAD(), "MessagePane.toolbar.tooltip.setToBAD", language);
        MiscToolkit.configureButton(getBmarkNEUTRAL(), "MessagePane.toolbar.tooltip.setToNEUTRAL", language);
        MiscToolkit.configureButton(getBmarkGOOD(), "MessagePane.toolbar.tooltip.setToGOOD", language);

        setTitle(language.getString("IdentitiesBrowser.title"));
        getBdelete().setText(language.getString("IdentitiesBrowser.button.delete"));
        getBcleanup().setText(language.getString("IdentitiesBrowser.button.cleanup"));
        getBcleanup().setToolTipText(language.getString("IdentitiesBrowser.button.cleanup.tooltip"));
        getBclose().setText(language.getString("IdentitiesBrowser.button.close"));
        getBimport().setText(language.getString("IdentitiesBrowser.button.import"));
        getBexport().setText(language.getString("IdentitiesBrowser.button.export"));

        Lfilter.setText(language.getString("IdentitiesBrowser.label.filter")+":");
        Llookup.setText(language.getString("IdentitiesBrowser.label.lookup")+":");

        assignHotkeys();
    }

    private void assignHotkeys() {
        final JPanel p = getJContentPane();

        // we assign these four shortcuts in the same order as the toolbar row of state icons, to avoid confusion
        // assign 1/NUMPAD1 key - set FRIEND
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_1, 0, true), "SET_FRIEND");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1, 0, true), "SET_FRIEND");
        p.getActionMap().put("SET_FRIEND", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                getBmarkFRIEND().doClick();
            }
        });

        // assign 2/NUMPAD2 key - set GOOD
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_2, 0, true), "SET_GOOD");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD2, 0, true), "SET_GOOD");
        p.getActionMap().put("SET_GOOD", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                getBmarkGOOD().doClick();
            }
        });

        // assign 3/NUMPAD3 key - set NEUTRAL
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_3, 0, true), "SET_NEUTRAL");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD3, 0, true), "SET_NEUTRAL");
        p.getActionMap().put("SET_NEUTRAL", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                getBmarkNEUTRAL().doClick();
            }
        });

        // assign 4/NUMPAD4 key - set BAD
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_4, 0, true), "SET_BAD");
        p.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD4, 0, true), "SET_BAD");
        p.getActionMap().put("SET_BAD", new AbstractAction() {
            public void actionPerformed(final ActionEvent event) {
                getBmarkBAD().doClick();
            }
        });
    }

    private void updateTitle() {
        final int idCount = tableModel.getRowCount();
        setTitle(language.formatMessage("IdentitiesBrowser.title", Integer.toString(idCount)));
    }

    /**
     * This method initializes jContentPane
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJContentPane() {
        if( jContentPane == null ) {
            jContentPane = new JPanel();
            jContentPane.setLayout(new BorderLayout());
            jContentPane.add(getButtonPanel(), java.awt.BorderLayout.SOUTH);
            jContentPane.add(getMainPanel(), java.awt.BorderLayout.CENTER);
        }
        return jContentPane;
    }

    /**
     * This method initializes jScrollPane
     *
     * @return javax.swing.JScrollPane
     */
    private JScrollPane getJScrollPane() {
        if( jScrollPane == null ) {
            jScrollPane = new JScrollPane();
            jScrollPane.setViewportView(getIdentitiesTable());
            jScrollPane.setWheelScrollingEnabled(true);
        }
        return jScrollPane;
    }

    /**
     * Compute expire earliest time of any board.
     * Cleanup could remove BAD identities when they are not seen since than the returned value.
     *
     * @return  maximum backload days of any board
     */
    private long getMinCleanupTime() {
        // take maximum
        int minDaysOld = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_EXPIRE_DAYS) + 1;

        if( minDaysOld < Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DISPLAY) ) {
            minDaysOld = Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DISPLAY) + 1;
        }
        if( minDaysOld < Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DOWNLOAD) ) {
            minDaysOld = Core.frostSettings.getIntValue(SettingsClass.MAX_MESSAGE_DOWNLOAD) + 1;
        }

        for( final Board board : MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards() ) {

            if( board.isConfigured() ) {
                minDaysOld = Math.max(board.getMaxMessageDisplay(), minDaysOld);
                minDaysOld = Math.max(board.getMaxMessageDownload(), minDaysOld);
            }
        }

        final long time = System.currentTimeMillis() - (minDaysOld * 24L * 60L * 60L * 1000L);
        return time;
    }

    /**
     * This method initializes identitiesTable
     *
     * @return javax.swing.JTable
     */
    private SortedTable<InnerTableMember> getIdentitiesTable() {
        if( identitiesTable == null ) {
            tableModel = new InnerTableModel();
            identitiesTable = new SortedTable<InnerTableMember>(tableModel) {
                // override the default sort order when clicking different columns
                @Override
                public boolean getColumnDefaultAscendingState(final int col) {
                    if( col == 3 ) {
                        // sort "last seen" column descending by default
                        return false;
                    }
                    return true; // all other columns: ascending
                }
            };
            new TableFindAction().install(identitiesTable);
            // set column sizes
//#DIEFILESHARING            final int[] widths = { 130, 30, 30, 70, 20, 20 };
            final int[] widths = { 130, 30, 30, 70, 20 };
            for (int i = 0; i < widths.length; i++) {
                identitiesTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            }

            final ShowColoredLinesRenderer showColoredLinesRenderer = new ShowColoredLinesRenderer();
            identitiesTable.getColumnModel().getColumn(0).setCellRenderer(showColoredLinesRenderer);
            identitiesTable.getColumnModel().getColumn(1).setCellRenderer(new StringCellRenderer());
            identitiesTable.getColumnModel().getColumn(2).setCellRenderer(showColoredLinesRenderer);
            identitiesTable.getColumnModel().getColumn(3).setCellRenderer(showColoredLinesRenderer);
            identitiesTable.getColumnModel().getColumn(4).setCellRenderer(showColoredLinesRenderer);
//#DIEFILESHARING            identitiesTable.getColumnModel().getColumn(5).setCellRenderer(showColoredLinesRenderer);

            jScrollPane.addMouseListener(listener);
            identitiesTable.addMouseListener(listener);

            identitiesTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(final ListSelectionEvent e) {
                    final int[] selRows = identitiesTable.getSelectedRows();

                    // nothing selected
                    if( selRows.length == 0 ) {
                        // disable all
                        getBdelete().setEnabled(false);
                        getBdelete().setToolTipText(null);
                        updateStateButtons(false, false, false, false);
                        return;
                    }

                    if( selRows.length == 1 ) {
                        // one selected: enable FRIEND,BAD,... buttons, disable button with current state
                        final Identity id = ((InnerTableMember)tableModel.getRow(selRows[0])).getIdentity();
                        // setting all together avoids flickering buttons
                        if( id.isBAD() ) {
                            updateStateButtons(false, true, true, true);
                        } else if( id.isNEUTRAL() ) {
                            updateStateButtons(true, false, true, true);
                        } else if( id.isFRIEND() ) {
                            updateStateButtons(true, true, false, true);
                        } else if( id.isGOOD() ) {
                            updateStateButtons(true, true, true, false);
                        }
                    } else {
                        // multiple selected: enable all buttons
                        updateStateButtons(true, true, true, true);
                    }

                    // if anyone in selection has more than 0 msgs, disable delete button
                    //#DIEFILESHARING: In the past, when filesharing was enabled, the isDeleteable() function also checked if they had >0 files
                    boolean enableDelete = true;
                    for( final int element : selRows ) {
                        if( ((InnerTableMember)tableModel.getRow(element)).isDeleteable() == false ) {
                            enableDelete = false;
                            break;
                        }
                    }
                    if( enableDelete ) {
                        if( selRows.length > 1 ) {
                            getBdelete().setToolTipText(language.getString("IdentitiesBrowser.button.delete.tooltip.multiple"));
                        } else {
                            getBdelete().setToolTipText(language.getString("IdentitiesBrowser.button.delete.tooltip.single"));
                        }
                    } else {
                        getBdelete().setToolTipText(null);
                    }
                    getBdelete().setEnabled(enableDelete);
                }
            });
        }
        return identitiesTable;
    }

    private void updateStateButtons(final boolean BADState, final boolean NEUTRALState, final boolean FRIENDState, final boolean GOODState) {
        getBmarkBAD().setEnabled(BADState);
        getBmarkNEUTRAL().setEnabled(NEUTRALState);
        getBmarkFRIEND().setEnabled(FRIENDState);
        getBmarkGOOD().setEnabled(GOODState);
    }

    /**
     * This method initializes buttonPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getButtonPanel() {
        if( buttonPanel == null ) {
            Lfilter = new JLabel();
            Lfilter.setText("IdentitiesBrowser.label.filter");
            Llookup = new JLabel();
            Llookup.setText("IdentitiesBrowser.label.lookup");
            buttonPanel = new JPanel();
            buttonPanel.setLayout(new BoxLayout(getButtonPanel(), BoxLayout.X_AXIS));
            buttonPanel.add(Box.createRigidArea(new Dimension(5,3)));
            buttonPanel.add(Llookup, null);
            buttonPanel.add(Box.createRigidArea(new Dimension(5,3)));
            buttonPanel.add(getTFlookup(), null);
            buttonPanel.add(Box.createRigidArea(new Dimension(5,3)));
            buttonPanel.add(Lfilter, null);
            buttonPanel.add(Box.createRigidArea(new Dimension(5,3)));
            buttonPanel.add(getTFfilter(), null);
            buttonPanel.add( Box.createHorizontalGlue() );
            buttonPanel.add(getBclose(), null);
            buttonPanel.add(Box.createRigidArea(new Dimension(5,3)));
        }
        return buttonPanel;
    }

    /**
     * This method initializes mainPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getMainPanel() {
        if( mainPanel == null ) {
            final GridBagConstraints gridBagConstraints21 = new GridBagConstraints();
            gridBagConstraints21.gridx = 1;
            gridBagConstraints21.gridheight = 1;
            gridBagConstraints21.gridwidth = 4;
            gridBagConstraints21.anchor = java.awt.GridBagConstraints.NORTH;
            gridBagConstraints21.weighty = 1.0;
            gridBagConstraints21.insets = new java.awt.Insets(5,5,5,5);
            gridBagConstraints21.gridy = 4;
            final GridBagConstraints gridBagConstraints11 = new GridBagConstraints();
            gridBagConstraints11.gridx = 1;
            gridBagConstraints11.gridwidth = 4;
            gridBagConstraints11.insets = new java.awt.Insets(15,5,5,5);
            gridBagConstraints11.anchor = java.awt.GridBagConstraints.NORTH;
            gridBagConstraints11.gridy = 3;
            final GridBagConstraints gridBagConstraints6 = new GridBagConstraints();
            gridBagConstraints6.gridx = 1;
            gridBagConstraints6.gridwidth = 4;
            gridBagConstraints6.insets = new java.awt.Insets(15,5,0,5);
            gridBagConstraints6.weighty = 0.0;
            gridBagConstraints6.anchor = java.awt.GridBagConstraints.NORTH;
            gridBagConstraints6.gridy = 1;
            final GridBagConstraints gridBagConstraints5 = new GridBagConstraints();
            gridBagConstraints5.gridx = 1;
            gridBagConstraints5.gridwidth = 4;
            gridBagConstraints5.weighty = 0.0;
            gridBagConstraints5.anchor = java.awt.GridBagConstraints.NORTH;
            gridBagConstraints5.insets = new java.awt.Insets(15,5,5,5);
            gridBagConstraints5.gridy = 2;
            final GridBagConstraints gridBagConstraints4 = new GridBagConstraints();
            gridBagConstraints4.gridx = 4;
            gridBagConstraints4.insets = new java.awt.Insets(5,0,0,5);
            gridBagConstraints4.gridy = 0;
            final GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
            gridBagConstraints3.gridx = 3;
            gridBagConstraints3.insets = new java.awt.Insets(5,0,0,0);
            gridBagConstraints3.gridy = 0;
            final GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
            gridBagConstraints2.gridx = 2;
            gridBagConstraints2.insets = new java.awt.Insets(5,0,0,0);
            gridBagConstraints2.gridy = 0;
            final GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
            gridBagConstraints1.gridx = 1;
            gridBagConstraints1.insets = new java.awt.Insets(5,0,0,0);
            gridBagConstraints1.gridy = 0;
            final GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = 0;
            gridBagConstraints.weightx = 1.0;
            gridBagConstraints.weighty = 1.0;
            gridBagConstraints.gridheight = 5;
            gridBagConstraints.insets = new java.awt.Insets(5,5,5,5);
            mainPanel = new JPanel();
            mainPanel.setLayout(new GridBagLayout());
            mainPanel.add(getJScrollPane(), gridBagConstraints);
            mainPanel.add(getBmarkFRIEND(), gridBagConstraints1);
            mainPanel.add(getBmarkGOOD(), gridBagConstraints2);
            mainPanel.add(getBmarkNEUTRAL(), gridBagConstraints3);
            mainPanel.add(getBmarkBAD(), gridBagConstraints4);
            mainPanel.add(getBdelete(), gridBagConstraints5);
            mainPanel.add(getBcleanup(), gridBagConstraints6);
            mainPanel.add(getBimport(), gridBagConstraints11);
            mainPanel.add(getBexport(), gridBagConstraints21);
        }
        return mainPanel;
    }

    /**
     * This method initializes Bclose
     *
     * @return javax.swing.JButton
     */
    private JButton getBclose() {
        if( Bclose == null ) {
            Bclose = new JButton();
            Bclose.setText("IdentitiesBrowser.button.close");
            Bclose.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    // create an event which acts exactly as if the user pressed the X, to close the window and dispose() it
                    final WindowEvent closingEvent = new WindowEvent(IdentitiesBrowser.this, WindowEvent.WINDOW_CLOSING);
                    Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(closingEvent);
                }
            });
        }
        return Bclose;
    }

    /**
     * Allows us to perform an action on all selected identities.
     * The table is NOT re-sorted afterwards, since that's annoying and interferes
     * with the ability to rapidly change states of the same group of selected items
     * several times in a row before deciding on the proper state to use.
     */
    abstract protected class SelectedIdentitiesAction {
        abstract protected void action(final Identity id);

        public SelectedIdentitiesAction() {
            iterateSelectedIdentities();
        }

        private void iterateSelectedIdentities() {
            // we must first make a list of all objects, since modifying them may change the sorting
            // and row numbers of the subsequent items otherwise, which means we'd target the wrong items.
            final ArrayList<InnerTableMember> selITMs = getIdentitiesTable().getListOfSelectedItems();
            // now just perform the action on every selected identity
            if( selITMs.size() > 0 ) {
                for( final InnerTableMember itm : selITMs ) {
                    final Identity id = itm.getIdentity();
                    action(id);
                }

                // repaints the whole table and clears the user's selection
                tableModel.tableEntriesChanged();

                // selects the modified items at their latest row positions
                // NOTE: the positions will be the same since we didn't call something
                // like updateRow() or resortTable(). We may do that in the future, though.
                // but right now it makes no sense, since it's annoying if IDs constantly fly around.
                getIdentitiesTable().setSelectedItems(selITMs);
            }
        }
    }

    /**
     * This method initializes BmarkFRIEND
     *
     * @return javax.swing.JButton
     */
    private JButton getBmarkFRIEND() {
        if( BmarkFRIEND == null ) {
            BmarkFRIEND = new JButton();
            BmarkFRIEND.setText("F");
            BmarkFRIEND.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    new SelectedIdentitiesAction() {
                        protected void action(final Identity id) {
                            if( id.isFRIEND() == false ) {
                                id.setFRIEND();
                            }
                        }
                    };
                }
            });
        }
        return BmarkFRIEND;
    }


    /**
     * This method initializes BmarkGOOD
     *
     * @return javax.swing.JButton
     */
    private JButton getBmarkGOOD() {
        if( BmarkGOOD == null ) {
            BmarkGOOD = new JButton();
            BmarkGOOD.setText("G");
            BmarkGOOD.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    new SelectedIdentitiesAction() {
                        protected void action(final Identity id) {
                            if( id.isGOOD() == false ) {
                                id.setGOOD();
                            }
                        }
                    };
                }
            });
        }
        return BmarkGOOD;
    }

    /**
     * This method initializes BmarkNEUTRAL
     *
     * @return javax.swing.JButton
     */
    private JButton getBmarkNEUTRAL() {
        if( BmarkNEUTRAL == null ) {
            BmarkNEUTRAL = new JButton();
            BmarkNEUTRAL.setText("O");
            BmarkNEUTRAL.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    new SelectedIdentitiesAction() {
                        protected void action(final Identity id) {
                            if( id.isNEUTRAL() == false ) {
                                id.setNEUTRAL();
                            }
                        }
                    };
                }
            });
        }
        return BmarkNEUTRAL;
    }

    /**
     * This method initializes BmarkBAD
     *
     * @return javax.swing.JButton
     */
    private JButton getBmarkBAD() {
        if( BmarkBAD == null ) {
            BmarkBAD = new JButton();
            BmarkBAD.setText("B");
            BmarkBAD.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    new SelectedIdentitiesAction() {
                        protected void action(final Identity id) {
                            if( id.isBAD() == false ) {
                                id.setBAD();
                            }
                        }
                    };
                }
            });
        }
        return BmarkBAD;
    }

    /**
     * This method initializes Bdelete
     *
     * @return javax.swing.JButton
     */
    private JButton getBdelete() {
        if( Bdelete == null ) {
            Bdelete = new JButton();
            Bdelete.setText("IdentitiesBrowser.button.delete");
            Bdelete.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    final int[] selRows = getIdentitiesTable().getSelectedRows();
                    final int answer = MiscToolkit.showConfirmDialog(
                            IdentitiesBrowser.this,
                            language.formatMessage("IdentitiesBrowser.deleteDialog.body", Integer.toString(selRows.length)),
                            language.getString("IdentitiesBrowser.deleteDialog.title"),
                            MiscToolkit.YES_NO_OPTION,
                            MiscToolkit.QUESTION_MESSAGE);
                    if( answer != MiscToolkit.YES_OPTION ) {
                        return;
                    }
                    Arrays.sort(selRows); // ensure sorted, we must delete from end to begin
                    if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
                        return;
                    }
                    for( int x=selRows.length-1; x>=0; x-- ) {
                        final InnerTableMember m = (InnerTableMember)tableModel.getRow(selRows[x]);
                        final Identity id = m.getIdentity();
                        Core.getIdentities().deleteIdentity(id);
                        tableModel.removeRow(selRows[x]);
                    }
                    IdentitiesStorage.inst().endThreadTransaction();
                    updateTitle();
                }
            });
        }
        return Bdelete;
    }

    class InnerTableMember implements TableMember<InnerTableMember> {

        Identity identity;
        Integer msgCount;
//#DIEFILESHARING        Integer fileCount;
        String lastSeenStr;
        String htmlName;
        Integer receivedMsgs;

        public InnerTableMember(final Identity i, final Hashtable<String,IdentitiesStorage.IdentityMsgAndFileCount> idDatas) {
            identity = i;

            final IdentitiesStorage.IdentityMsgAndFileCount data = idDatas.get(identity.getUniqueName());
            if( data != null ) {
                msgCount = new Integer(data.getMessageCount());
//#DIEFILESHARING                fileCount = new Integer(data.getFileCount());
            } else {
                // error
                msgCount = new Integer(-1);
//#DIEFILESHARING                fileCount = new Integer(-1);
            }
            lastSeenStr = buildLastSeenString(identity.getLastSeenTimestamp());
            receivedMsgs = new Integer(identity.getReceivedMessageCount());
            htmlName = buildHtmlName(i.getUniqueName());
        }
        public Identity getIdentity() {
            return identity;
        }
        public boolean isDeleteable() {
//#DIEFILESHARING            if( msgCount.intValue() == 0 && fileCount.intValue() == 0 ) {
            if( msgCount.intValue() == 0 ) {
                return true;
            }
            return false;
        }
        public boolean isCleanupable() {
            if( !isDeleteable() ) {
                return false;
            }
            // always keep identities marked FRIEND and GOOD
            if( identity.isFRIEND() || identity.isGOOD()) {
                return false;
            }
            // keep identities marked BAD, if not expired
            if( identity.isBAD() && identity.getLastSeenTimestamp() > minCleanupTime ) {
                return false;
            }

            return true;
        }
        
        private String buildHtmlName(final String n) {
            // TODO: html mode wraps words with blanks, maybe replace blanks by &nbsp;
//            String a = n.substring(0, n.indexOf("@"));
//            String b = n.substring(n.indexOf("@"));
//            String r = "<html><b>"+a+"</b>"+b+"</html>";
//            System.out.println("r="+r);
//            return r;
            return n;
        }
        
        private String buildLastSeenString(final long lastSeen) {
            // date (days_before)
            if( lastSeen < 0 ) {
                // not set!
                return "";
            }
            String lsStr = DateFun.FORMAT_DATE_EXT.print(lastSeen);
            long days = new DateTime(DateTimeZone.UTC).withTimeAtStartOfDay().getMillis() - new DateTime(lastSeen, DateTimeZone.UTC).withTimeAtStartOfDay().getMillis(); // current midnight in UTC - last seen midnight in UTC
            days /= 1000L * 60L * 60L * 24L;
            lsStr += "  ("+days+")";

            return lsStr;
        }
        
		public Comparable<?> getValueAt(final int column) {
            switch(column) {
                case 0: return htmlName;
                case 1: return getIdentity().getStateString();
                case 2: return receivedMsgs;
                case 3: return lastSeenStr;
                case 4: return msgCount;
//#DIEFILESHARING                case 5: return fileCount;
            }
            return "*ERR*";
        }

        public int compareTo(final InnerTableMember otherTableMember, final int tableColumnIndex) {
            if( tableColumnIndex == 0 || tableColumnIndex == 1 ) {
                final String s1 = (String)getValueAt(tableColumnIndex);
                final String s2 = (String)otherTableMember.getValueAt(tableColumnIndex);
                return s1.compareToIgnoreCase(s2);
            }
            if( tableColumnIndex == 2 ) {
                final int l1 = getIdentity().getReceivedMessageCount();
                final int l2 = ((InnerTableMember)otherTableMember).getIdentity().getReceivedMessageCount();
                return Mixed.compareLong(l1, l2);
            }
            if( tableColumnIndex == 3 ) {
                final long l1 = getIdentity().getLastSeenTimestamp();
                final long l2 = ((InnerTableMember)otherTableMember).getIdentity().getLastSeenTimestamp();
                return Mixed.compareLong(l1, l2);
            }
            if( tableColumnIndex == 4 ) {
                Integer i1 = (Integer)getValueAt(tableColumnIndex);
                Integer i2 = (Integer)otherTableMember.getValueAt(tableColumnIndex);
                final int res = i1.compareTo(i2);
                /* //#DIEFILESHARING: This entire block has been disabled since filesharing was removed.
                if( res == 0) {
                    // same msgcount, compare filecount
                    i1 = (Integer)getValueAt(5);
                    i2 = (Integer)otherTableMember.getValueAt(5);
                    return i1.compareTo(i2);
                }
                */
                return res;
            }
            /* //#DIEFILESHARING: This entire block has been disabled since filesharing was removed.
            if( tableColumnIndex == 5 ) {
                Integer i1 = (Integer)getValueAt(tableColumnIndex);
                Integer i2 = (Integer)otherTableMember.getValueAt(tableColumnIndex);
                final int res = i1.compareTo(i2);
                if( res == 0) {
                    // same filecount, compare msgcount
                    i1 = (Integer)getValueAt(4);
                    i2 = (Integer)otherTableMember.getValueAt(4);
                    return i1.compareTo(i2);
                }
                return res;
            }
            */
            return 0;
        }
    }

    public class InnerTableModel extends SortedTableModel<InnerTableMember> {

//#DIEFILESHARING        protected final String columnNames[] = new String[6];
        protected final String columnNames[] = new String[5];

        protected final Class<?> columnClasses[] = {
            String.class, // name
            String.class, // state
            Integer.class, // received msgs
            String.class, // lastSeen,
            Integer.class // msgs
//#DIEFILESHARING            Integer.class  // files
        };

        public InnerTableModel() {
            super();
            setLanguage();
        }

        @Override
        public boolean isCellEditable(final int row, final int col) {
            return false;
        }

        @Override
        public String getColumnName(final int column) {
            if( column >= 0 && column < columnNames.length ) {
                return columnNames[column];
            }
            return null;
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public Class<?> getColumnClass(final int column) {
            if( column >= 0 && column < columnClasses.length ) {
                return columnClasses[column];
            }
            return null;
        }

        private void setLanguage() {
            columnNames[0] = language.getString("IdentitiesBrowser.identitiesTable.name");
            columnNames[1] = language.getString("IdentitiesBrowser.identitiesTable.state");
            columnNames[2] = language.getString("IdentitiesBrowser.identitiesTable.receivedMessages");
            columnNames[3] = language.getString("IdentitiesBrowser.identitiesTable.lastSeen");
            columnNames[4] = language.getString("IdentitiesBrowser.identitiesTable.messages");
//#DIEFILESHARING            columnNames[5] = language.getString("IdentitiesBrowser.identitiesTable.files");
        }
    }

    private class StringCellRenderer extends ShowColoredLinesRenderer {

        private Font boldFont = null;
        private Font normalFont = null;
        private final Color col_BAD       = TrustStateColors.BAD;
        private final Color col_NEUTRAL   = TrustStateColors.NEUTRAL;
        private final Color col_GOOD      = TrustStateColors.GOOD;
        private final Color col_FRIEND    = TrustStateColors.FRIEND;

        public StringCellRenderer() {
            final Font baseFont = getIdentitiesTable().getFont();
            normalFont = baseFont.deriveFont(Font.PLAIN);
            boldFont = baseFont.deriveFont(Font.BOLD);

            setVerticalAlignment(CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            setAlignmentY(CENTER_ALIGNMENT);

            final InnerTableMember tableMember = (InnerTableMember) tableModel.getRow(row);

            // get the original model column index (maybe columns were reordered by user)
            final TableColumn tableColumn = getIdentitiesTable().getColumnModel().getColumn(column);
            column = tableColumn.getModelIndex();

            // defaults
            setFont(normalFont);
            if (!isSelected) {
                setForeground(Color.BLACK);
            }
            setToolTipText(null);

            if( column == 0 ) {
                setToolTipText(tableMember.getIdentity().getUniqueName());
            } else if( column == 1 ) {
                final Identity id = tableMember.getIdentity();
                // STATE
                // state == BAD/NEUTRAL/GOOD/FRIEND -> bold and colored
                if (Core.getIdentities().isMySelf(id.getUniqueName())) {
                    if( !Core.frostSettings.getBoolValue(SettingsClass.SHOW_OWN_MESSAGES_AS_ME_DISABLED) ) {
                        setText("ME"); // this doesn't override "getValueAt", it's just for display
                    }
                    setFont(boldFont);
                    setForeground(col_FRIEND);
                } else if( id.isNEUTRAL() ) {
                    setFont(boldFont);
                    setForeground(col_NEUTRAL);
                } else if( id.isGOOD() ) {
                    setFont(boldFont);
                    setForeground(col_GOOD);
                } else if( id.isFRIEND() ) {
                    setFont(boldFont);
                    setForeground(col_FRIEND);
                } else if( id.isBAD() ) {
                    setFont(boldFont);
                    setForeground(col_BAD);
                }
            }
            return this;
        }
    }

    private ProgressMonitor progressMonitor;

    private JLabel Llookup = null;

    private JTextField TFlookup = null;

    private JLabel Lfilter = null;

    private JTextField TFfilter = null;

    private JButton Bimport = null;

    private JButton Bexport = null;

    private void startProgressMonitor(final int max) {
        final String title = language.getString("IdentitiesBrowser.progressDialog.title");
        final String msg = language.getString("IdentitiesBrowser.progressDialog.body");
        UIManager.put("ProgressMonitor.progressText", title);
        progressMonitor = new ProgressMonitor(parent, msg, null, 0, max);
//        progressMonitor.setNote(0+"/"+max);
    }

    public void startDialog() {

        startProgressMonitor(3);

        // disables mainframe
        final FrostSwingWorker worker = new FrostSwingWorker(parent) {
            @Override
            protected void doNonUILogic() throws RuntimeException {

                progressMonitor.setProgress(1);
                if( progressMonitor.isCanceled() ) {
                    return;
                }

                // query ALL data for all identities, each InnerTableMember gets its values from the complete list
                Hashtable<String,IdentitiesStorage.IdentityMsgAndFileCount> idDatas;
                if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
                    return;
                }

                idDatas = IdentitiesStorage.inst().retrieveMsgAndFileCountPerIdentity();

                IdentitiesStorage.inst().endThreadTransaction();

                progressMonitor.setProgress(2);
                if( progressMonitor.isCanceled() ) {
                    return;
                }

                allTableMembers = new LinkedList<InnerTableMember>(); // remember all table data for filter
                final List<Identity> allIdentities = Core.getIdentities().getIdentities();
                // show own identities also
                allIdentities.addAll(Core.getIdentities().getLocalIdentities());
                for( final Identity identity : allIdentities ) {
                    final InnerTableMember memb = new InnerTableMember(identity, idDatas);
                    tableModel.addRow(memb);
                    allTableMembers.add(memb);
                }

                progressMonitor.setProgress(3);

                idDatas.clear();
            }
            @Override
            protected void doUIUpdateLogic() throws RuntimeException {
                updateTitle();
                showDialog();
            }
        };
        worker.start();
    }

    private void showDialog() {
        if( progressMonitor.isCanceled() ) {
            progressMonitor.close();
        } else {
            progressMonitor.close();
            getIdentitiesTable().getSelectionModel().setSelectionInterval(0, 0);
            setVisible(true);
        }
    }

    /**
     * This method initializes jButton
     *
     * @return javax.swing.JButton
     */
    private JButton getBcleanup() {
        if( Bcleanup == null ) {
            Bcleanup = new JButton();
            Bcleanup.setText("IdentitiesBrowser.button.cleanup");
            Bcleanup.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {

                    final LinkedList<Integer> li = new LinkedList<Integer>();
                    for( int i=tableModel.getRowCount()-1; i >= 0; i-- ) {
                        final InnerTableMember m = (InnerTableMember)tableModel.getRow(i);
                        if( m.isCleanupable() ) {
                            li.add(new Integer(i));
                        }
                    }
                    if( li.size() == 0 ) {
                        MiscToolkit.showMessageDialog(
                                IdentitiesBrowser.this,
                                language.getString("IdentitiesBrowser.cleanupDialog.nothingToDelete.body"),
                                language.getString("IdentitiesBrowser.cleanupDialog.nothingToDelete.title"),
                                MiscToolkit.INFORMATION_MESSAGE);
                        return;
                    }
                    final int answer = MiscToolkit.showConfirmDialog(
                            IdentitiesBrowser.this,
                            language.formatMessage("IdentitiesBrowser.cleanupDialog.deleteIdentities.body", Integer.toString(li.size())),
                            language.getString("IdentitiesBrowser.cleanupDialog.deleteIdentities.title"),
                            MiscToolkit.YES_NO_OPTION,
                            MiscToolkit.QUESTION_MESSAGE);
                    if( answer != MiscToolkit.YES_OPTION ) {
                        return;
                    }

                    // batch delete, turn off autocommit
                    if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
                        return;
                    }
                    for( final Integer element : li ) {
                        final InnerTableMember m = (InnerTableMember)tableModel.getRow(element.intValue());
                        final Identity id = m.getIdentity();
                        Core.getIdentities().deleteIdentity(id);
                        tableModel.removeRow(element.intValue());
                    }
                    IdentitiesStorage.inst().endThreadTransaction();

                    updateTitle();
                }
            });
        }
        return Bcleanup;
    }

    /**
     * This method initializes TFlookup
     *
     * @return javax.swing.JTextField
     */
    private JTextField getTFlookup() {
        if( TFlookup == null ) {
            TFlookup = new JTextField(10);
            // force a max size, needed for BoxLayout
            TFlookup.setMaximumSize(TFlookup.getPreferredSize());
            TFlookup.getDocument().addDocumentListener(new DocumentListener() {
                    public void changedUpdate(final DocumentEvent e) {
                        lookupContentChanged();
                    }
                    public void insertUpdate(final DocumentEvent e) {
                        lookupContentChanged();
                    }
                    public void removeUpdate(final DocumentEvent e) {
                        lookupContentChanged();
                    }
                });
        }
        return TFlookup;
    }

    /**
     * This method initializes TFfilter
     *
     * @return javax.swing.JTextField
     */
    private JTextField getTFfilter() {
        if( TFfilter == null ) {
            TFfilter = new JTextField(10);
            TFfilter.setMaximumSize(TFfilter.getPreferredSize());

            TFfilter.getDocument().addDocumentListener(new DocumentListener() {
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
        }
        return TFfilter;
    }

    /**
     * Called whenever the content of the lookup text field changes
     */
    private void lookupContentChanged() {
        try {
            final String txt = TFlookup.getDocument().getText(0, TFlookup.getDocument().getLength());
            // now try to find the first board name that starts with this txt (case insensitiv),
            // if we found one set selection to it, else leave selection untouched
            for( int row=0; row < tableModel.getRowCount(); row++ ) {
                final InnerTableMember memb = (InnerTableMember)tableModel.getRow(row);
                if( memb.getIdentity().getUniqueName().toLowerCase().startsWith(txt.toLowerCase()) ) {
                    getIdentitiesTable().getSelectionModel().setSelectionInterval(row, row);
                    // now scroll to selected row, try to show it on top of table

                    // determine how many rows are visible, and scroll the view so that the found item is at the top of the table
                    // and the last visible item on screen is nicely aligned with the bottom of the scrollbar
                    final int visibleRows = (int)(getIdentitiesTable().getVisibleRect().getHeight() / getIdentitiesTable().getCellRect(row,0,true).getHeight());
                    int scrollToRow;
                    if( row + visibleRows > tableModel.getRowCount() ) {
                        scrollToRow = tableModel.getRowCount()-1;
                    } else {
                        scrollToRow = row + visibleRows - 1;
                    }
                    if( scrollToRow > row ) {
                        scrollToRow--;
                    }
                    // scroll 2 times to make sure row is displayed
                    getIdentitiesTable().scrollRectToVisible(getIdentitiesTable().getCellRect(row,0,true));
                    getIdentitiesTable().scrollRectToVisible(getIdentitiesTable().getCellRect(scrollToRow,0,true));
                    break;
                }
            }
        } catch(final Exception ex) {}
    }

    /**
     * Called whenever the content of the filter text field changes
     */
    private void filterContentChanged() {
        try {
            TFlookup.setText(""); // clear
            String txt = TFfilter.getDocument().getText(0, TFfilter.getDocument().getLength()).trim();
            txt = txt.toLowerCase();
            // filter: show all boards that have this txt in name
            tableModel.clearDataModel();
            for( final Object element : allTableMembers ) {
                final InnerTableMember tm = (InnerTableMember)element;
                if( txt.length() > 0 ) {
                    final String bn = tm.getIdentity().getUniqueName().toLowerCase();
                    if( bn.indexOf(txt) < 0 ) {
                        continue;
                    }
                }
                tableModel.addRow(tm);
            }
        } catch(final Exception ex) {}
    }

    private File chooseXmlImportFile() {

        final FileFilter myFilter = new FileFilter() {
            @Override
            public boolean accept(final File file) {
                if( file.isDirectory() ) {
                    return true;
                }
                if( file.getName().endsWith(".xml") ) {
                    return true;
                }
                return false;
            }
            @Override
            public String getDescription() {
                return "identities_export.xml";
            }
        };

        final JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(myFilter);
        final int returnVal = chooser.showOpenDialog(this);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            return chooser.getSelectedFile();
        }
        return null;
    }

    private File chooseXmlExportFile() {

        final FileFilter myFilter = new FileFilter() {
            @Override
            public boolean accept(final File file) {
                if( file.isDirectory() ) {
                    return true;
                }
                if( file.getName().endsWith(".xml") ) {
                    return true;
                }
                return false;
            }
            @Override
            public String getDescription() {
                return "identities_export.xml";
            }
        };

        final JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(myFilter);
        final int returnVal = chooser.showSaveDialog(this);
        if(returnVal == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            if( !f.getName().endsWith(".xml") ) {
                f = new File(f.getPath() + ".xml");
            }
            if( f.exists() ) {
                final int answer = MiscToolkit.showConfirmDialog(
                        this,
                        language.formatMessage("IdentitiesBrowser.exportIdentitiesConfirmXmlFileOverwrite.body", f.getName()),
                        language.getString("IdentitiesBrowser.exportIdentitiesConfirmXmlFileOverwrite.title"),
                        MiscToolkit.YES_NO_OPTION,
                        MiscToolkit.WARNING_MESSAGE);
                if( answer != MiscToolkit.YES_OPTION ) {
                    return null;
                }
            }
            return f;
        }
        return null;
    }

    /**
     * This method initializes Bimport
     *
     * @return javax.swing.JButton
     */
    private JButton getBimport() {
        if( Bimport == null ) {
            Bimport = new JButton();
            Bimport.setText("IdentitiesBrowser.button.import");
            Bimport.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    final File xmlFile = chooseXmlImportFile();
                    if( xmlFile == null ) {
                        return;
                    }

                    final List<Identity> importedIdentities = IdentitiesXmlDAO.loadIdentities(xmlFile);
                    if( importedIdentities.size() == 0 ) {
                        // nothing loaded
                        MiscToolkit.showMessageDialog(
                                IdentitiesBrowser.this,
                                language.getString("IdentitiesBrowser.noIdentityToImport.body"),
                                language.getString("IdentitiesBrowser.noIdentityToImport.title"),
                                MiscToolkit.INFORMATION_MESSAGE);
                        return;
                    }

                    final int importedCount = Core.getIdentities().importIdentities(importedIdentities);
                    final int skippedCount = importedIdentities.size() - importedCount;

                    MiscToolkit.showMessageDialog(
                            IdentitiesBrowser.this,
                            language.formatMessage(
                                    "IdentitiesBrowser.identitiesImported.body",
                                    Integer.toString(importedCount),
                                    Integer.toString(skippedCount)),
                                language.getString("IdentitiesBrowser.identitiesImported.title"),
                                MiscToolkit.INFORMATION_MESSAGE);

                    updateTitle();
                }
            });
        }
        return Bimport;
    }

    /**
     * This method initializes Bexport
     *
     * @return javax.swing.JButton
     */
    private JButton getBexport() {
        if( Bexport == null ) {
            Bexport = new JButton();
            Bexport.setText("IdentitiesBrowser.button.export");
            Bexport.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    final List<Identity> allIdentities = Core.getIdentities().getIdentities();
                    exportIdentities(allIdentities);
                }
            });
        }
        return Bexport;
    }

    private void exportIdentities(final List<Identity> ids) {
        // saves only FRIEND,GOOD,BAD
        final File xmlFile = chooseXmlExportFile();
        if( xmlFile == null ) {
            return;
        }

        final int count = IdentitiesXmlDAO.saveIdentities(xmlFile, ids);
        if( count > 0 ) {
            // 'count' identities exported
            MiscToolkit.showMessageDialog(
                    IdentitiesBrowser.this,
                    language.formatMessage("IdentitiesBrowser.identitiesExported.body", Integer.toString(count)),
                    language.getString("IdentitiesBrowser.identitiesExported.title"),
                    MiscToolkit.INFORMATION_MESSAGE);
        } else if( count < 0 ) {
            // identities export failed
            MiscToolkit.showMessageDialog(
                    IdentitiesBrowser.this,
                    language.getString("IdentitiesBrowser.identitiesExportFailed.body"),
                    language.getString("IdentitiesBrowser.identitiesExportFailed.title"),
                    MiscToolkit.ERROR_MESSAGE);
        } else {
            // no identities to export, all are NEUTRAL?
            MiscToolkit.showMessageDialog(
                    IdentitiesBrowser.this,
                    language.getString("IdentitiesBrowser.noIdentityToExport.body"),
                    language.getString("IdentitiesBrowser.noIdentityToExport.title"),
                    MiscToolkit.INFORMATION_MESSAGE);
        }
    }

    private class ShowColoredLinesRenderer extends DefaultTableCellRenderer {
        public ShowColoredLinesRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column)
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) {
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                setBackground(newBackground);
            } else {
                setBackground(table.getSelectionBackground());
            }
            return this;
        }
    }

    private PopupMenu getPopupMenu() {
        if (popupMenu == null) {
            popupMenu = new PopupMenu();
        }
        return popupMenu;
    }

    private void showIdentitiesTablePopupMenu(final MouseEvent e) {
        // select row where rightclick occurred if row under mouse is NOT selected
        final Point p = e.getPoint();
        final int y = identitiesTable.rowAtPoint(p);
        if( y < 0 ) {
            return;
        }
        if( !identitiesTable.getSelectionModel().isSelectedIndex(y) ) {
            identitiesTable.getSelectionModel().setSelectionInterval(y, y);
        }
        getPopupMenu().show(e.getComponent(), e.getX(), e.getY());
    }

    private class PopupMenu extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

        private final JMenu copyToClipboardMenu = new JMenu();

        private final JMenuItem copyUniqueName = new JMenuItem();
        private final JMenuItem copyUniqueNameAndPublicKey = new JMenuItem();

        private final JMenuItem exportSelectedIdentities = new JMenuItem();

        public PopupMenu() {
            super();
            initialize();
        }

        private void initialize() {
            refreshLanguage();

            copyToClipboardMenu.add(copyUniqueName);
            copyToClipboardMenu.add(copyUniqueNameAndPublicKey);

            copyUniqueName.addActionListener(this);
            copyUniqueNameAndPublicKey.addActionListener(this);
            exportSelectedIdentities.addActionListener(this);
        }

        private void refreshLanguage() {
            copyUniqueName.setText(language.getString("IdentitiesBrowser.popupmenu.copyUniqueName"));
            copyUniqueNameAndPublicKey.setText(language.getString("IdentitiesBrowser.popupmenu.copyUniqueNameAndPublicKey"));

            copyToClipboardMenu.setText(language.getString("Common.copyToClipBoard") + "...");

            exportSelectedIdentities.setText(language.getString("IdentitiesBrowser.popupmenu.exportSelectedIdentities"));
        }

        public void actionPerformed(final ActionEvent e) {

            final int[] selRows = getIdentitiesTable().getSelectedRows();
            final List<Identity> selectedIds = new ArrayList<Identity>();
            for( int x=selRows.length-1; x>=0; x-- ) {
                final InnerTableMember m = (InnerTableMember)tableModel.getRow(selRows[x]);
                final Identity id = m.getIdentity();
                selectedIds.add(id);
            }

            if (e.getSource() == copyUniqueName) {
                copyUniqueName(selectedIds);
            } else if (e.getSource() == copyUniqueNameAndPublicKey) {
                copyUniqueNameAndPublicKey(selectedIds);
            } else if (e.getSource() == exportSelectedIdentities) {
                exportIdentities(selectedIds);
            }
        }

        private void copyUniqueName(final List<Identity> selectedIds) {
            final StringBuilder sb = new StringBuilder();
            for( final Identity id : selectedIds) {
                sb.append(id.getUniqueName());
                sb.append("\n");
            }
            // We remove the additional \n at the end
            sb.deleteCharAt(sb.length() - 1);

            CopyToClipboard.copyText(sb.toString());
        }

        private void copyUniqueNameAndPublicKey(final List<Identity> selectedIds) {
            final StringBuilder sb = new StringBuilder();
            for( final Identity id : selectedIds) {
                sb.append(id.getUniqueName());
                sb.append("\n");
                sb.append(id.getPublicKey());
                sb.append("\n\n");
            }
            // We remove the both additional \n at the end
            sb.deleteCharAt(sb.length() - 1);
            sb.deleteCharAt(sb.length() - 1);

            CopyToClipboard.copyText(sb.toString());
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        @Override
        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            final int selectedRowCount = identitiesTable.getSelectedRowCount();

            if( selectedRowCount == 0 ) {
                return;
            }

            add(copyToClipboardMenu);
            addSeparator();
            add(exportSelectedIdentities);

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
                if ((e.getSource() == identitiesTable)
                    || (e.getSource() == jScrollPane)) {
                    showIdentitiesTablePopupMenu(e);
                }
            }
        }
        @Override
        public void mouseReleased(final MouseEvent e) {
            if ((e.getClickCount() == 1) && (e.isPopupTrigger())) {
                if ((e.getSource() == identitiesTable)
                    || (e.getSource() == jScrollPane)) {
                    showIdentitiesTablePopupMenu(e);
                }
            }
        }
    }
}  //  @jve:decl-index=0:visual-constraint="10,10"
