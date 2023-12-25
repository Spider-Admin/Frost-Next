/*
  SearchMessagesDialog.java / Frost
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

import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;

import javax.swing.*;
import javax.swing.text.*;

import com.toedter.calendar.*;

import frost.*;
import frost.gui.model.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.messaging.frost.gui.*;
import frost.messaging.frost.gui.messagetreetable.MessageTreeTable;
import frost.messaging.frost.threads.*;
import frost.util.Mixed;
import frost.util.gui.*;
import frost.util.gui.translation.*;
import frost.util.gui.tristatecheckbox.*;

@SuppressWarnings("serial")
public class SearchMessagesDialog extends JFrame implements LanguageListener {

    private final Language language = Language.getInstance();

    private SearchMessagesConfig searchMessagesConfig = null;

    private HashSet<Component> previouslyEnabledComponents = new HashSet<Component>();

    private String resultCountPrefix = null;
    private String startSearchStr = null;
    private String stopSearchStr = null;

    List<Board> chosedBoardsList = new ArrayList<Board>();
    SearchMessagesThread runningSearchThread = null;
    int resultCount;

    private JLabel LresultCount = null;
    private JRadioButton date_RBall = null;
    private JPanel PbuttonsRight = null;
    private JPanel PbuttonsLeft = null;
    private JButton BopenMsg = null;
    private JButton BgotoMsg = null;
    private JPanel Pattachments = null;
    private JCheckBox attachment_CBmustContainBoards = null;
    private JCheckBox attachment_CBmustContainFiles = null;
    private JButton Bhelp = null;

    private JPanel jContentPane = null;
    private JPanel contentPanel = null;
    private JPanel Pbuttons = null;
    private JButton Bsearch = null;
    private JButton Bcancel = null;
    private JTabbedPane jTabbedPane = null;
    private JPanel Psearch = null;
    private JPanel PsearchResult = null;
    private JLabel Lsender = null;
    private JLabel Lcontent = null;
    private JTextField search_TFsender = null;
    private JTextField search_TFcontent = null;
    private JPanel Pdate = null;
    private JRadioButton date_RBdisplayed = null;
    private JRadioButton date_RBbetweenDates = null;
    private JDateChooser date_TFstartDate = null;
    private JLabel date_Lto = null;
    private JDateChooser date_TFendDate = null;
    private JRadioButton date_RBdaysBackward = null;
    private JTextField date_TFdaysBackward = null;
    private JPanel PtrustState = null;
    private JRadioButton truststate_RBdisplayed = null;
    private JRadioButton truststate_RBall = null;
    private JRadioButton truststate_RBchosed = null;
    private JPanel truststate_PtrustStates = null;
    private JCheckBox truststate_CBFRIEND = null;
    private JCheckBox truststate_CBGOOD = null;
    private JCheckBox truststate_CBNEUTRAL = null;
    private JCheckBox truststate_CBBAD = null;
    private JCheckBox truststate_CBNONE = null;
    private JCheckBox truststate_CBTAMPERED = null;
    private JPanel Parchive = null;
    private JRadioButton archive_RBkeypoolAndArchive = null;
    private JRadioButton archive_RBkeypoolOnly = null;
    private JRadioButton archive_RBarchiveOnly = null;
    private JPanel Pboards = null;
    private JRadioButton boards_RBdisplayed = null;
//    private JRadioButton boards_RBallExisting = null;
    private JRadioButton boards_RBchosed = null;
    private JButton boards_Bchoose = null;
    private JTextField boards_TFchosedBoards = null;
    private TristateCheckBox search_CBprivateMsgsOnly = null;
    private TristateCheckBox search_CBflaggedMsgsOnly = null;
    private TristateCheckBox search_CBstarredMsgsOnly = null;
    private TristateCheckBox search_CBrepliedMsgsOnly = null;
    private JLabel LsearchResult = null;
    private JScrollPane jScrollPane = null;
    private SearchMessagesResultTable searchResultTable = null;
    private SearchMessagesTableModel searchMessagesTableModel = null;  //  @jve:decl-index=0:visual-constraint="735,15"
    private ButtonGroup boards_buttonGroup = null;  //  @jve:decl-index=0:visual-constraint="755,213"
    private ButtonGroup date_buttonGroup = null;  //  @jve:decl-index=0:visual-constraint="765,261"
    private ButtonGroup truststate_buttonGroup = null;  //  @jve:decl-index=0:visual-constraint="752,302"
    private ButtonGroup archive_buttonGroup = null;  //  @jve:decl-index=0:visual-constraint="760,342"
    private JLabel Lsubject = null;
    private JTextField search_TFsubject = null;

    private final JCheckBox senderCaseCheckBox = new JCheckBox("");
    private final JCheckBox subjectCaseCheckBox = new JCheckBox("");
    private final JCheckBox contentCaseCheckBox = new JCheckBox("");

    /**
     * This is the default constructor
     */
    public SearchMessagesDialog() {
        super();
        enableEvents(AWTEvent.WINDOW_EVENT_MASK);
        initialize();
        languageChanged(null);
        loadWindowState();
        initializeWithDefaults();

        language.addLanguageListener(this);
    }

    /**
     * Start the search dialog, all boards in board tree are selected.
     */
    public void startDialog() {
        getBoards_RBdisplayed().doClick();
        updateBoardTextField(null);
        setVisible(true);
    }

    /**
     * Start the search dialog with only the specified boards preselected as boards to search into.
     */
    public void startDialog(final List<Board> l) {
        getBoards_RBchosed().doClick();
        updateBoardTextField(l);
        clearSearchResultTable();
        setVisible(true);
    }

    /**
     * This method initializes search_TFsubject
     *
     * @return javax.swing.JTextField
     */
    private JTextField getSearch_TFsubject() {
        if( search_TFsubject == null ) {
            search_TFsubject = new JTextField();
            new TextComponentClipboardMenu(search_TFsubject, language);
        }
        return search_TFsubject;
    }

    /**
     * This method initializes this
     *
     * @return void
     */
    private void initialize() {
        this.setTitle(language.getString("SearchMessages.title"));
        this.setIconImage(MiscToolkit.loadImageIcon("/data/toolbar/edit-find.png").getImage());
        this.setSize(new java.awt.Dimension(700,550));
        this.setContentPane(getJContentPane());
        // create button groups
        this.getDate_buttonGroup();
        this.getBoards_buttonGroup();
        this.getTruststate_buttonGroup();
        this.getArchive_buttonGroup();
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
            jContentPane.add(getContentPanel(), java.awt.BorderLayout.CENTER);
            jContentPane.add(getPbuttons(), java.awt.BorderLayout.SOUTH);
        }
        return jContentPane;
    }

    /**
     * This method initializes contentPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getContentPanel() {
        if( contentPanel == null ) {
            contentPanel = new JPanel();
            contentPanel.setLayout(new BorderLayout());
            contentPanel.add(getJTabbedPane(), java.awt.BorderLayout.NORTH);
            contentPanel.add(getPsearchResult(), java.awt.BorderLayout.CENTER);
        }
        return contentPanel;
    }

    /**
     * This method initializes buttonPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPbuttons() {
        if( Pbuttons == null ) {
            Pbuttons = new JPanel();
            Pbuttons.setLayout(new BorderLayout());
            Pbuttons.add(getPbuttonsRight(), java.awt.BorderLayout.WEST);
            Pbuttons.add(getJPanel(), java.awt.BorderLayout.EAST);
        }
        return Pbuttons;
    }

    /**
     * This method initializes Bsearch
     *
     * @return javax.swing.JButton
     */
    private JButton getBsearch() {
        if( Bsearch == null ) {
            Bsearch = new JButton();
            // enter key anywhere in dialog (except in table where it opens a msg) starts or stops searching
            Bsearch.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "startStopSearch");
            final Action action = new AbstractAction() {
                public void actionPerformed(final ActionEvent arg0) {
                    if( getBsearch().isEnabled() ) {
                        startOrStopSearching();
                    }
                }
            };
            Bsearch.getActionMap().put("startStopSearch", action);
            Bsearch.addActionListener(action);
        }
        return Bsearch;
    }

    /**
     * This method initializes Bcancel
     *
     * @return javax.swing.JButton
     */
    private JButton getBcancel() {
        if( Bcancel == null ) {
            Bcancel = new JButton();
            Bcancel.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    closePressed();
                }
            });
        }
        return Bcancel;
    }

    /**
     * This method initializes jTabbedPane
     *
     * @return javax.swing.JTabbedPane
     */
    private JTabbedPane getJTabbedPane() {
        if( jTabbedPane == null ) {
            jTabbedPane = new JTranslatableTabbedPane(language);
            jTabbedPane.addTab("SearchMessages.search", null, getPsearch(), null);
            jTabbedPane.addTab("SearchMessages.boards", null, getPboards(), null);
            jTabbedPane.addTab("SearchMessages.date", null, getPdate(), null);
            jTabbedPane.addTab("SearchMessages.trustState", null, getPtrustState(), null);
            jTabbedPane.addTab("SearchMessages.archive", null, getParchive(), null);
            jTabbedPane.addTab("SearchMessages.attachments", null, getPattachments(), null);
        }
        return jTabbedPane;
    }

    /**
     * This method initializes jPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPsearch() {
        if( Psearch == null ) {
            final GridBagConstraints gridBagConstraints29 = new GridBagConstraints();
            gridBagConstraints29.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints29.gridy = 1;
            gridBagConstraints29.weightx = 1.0;
            gridBagConstraints29.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints29.gridx = 1;
            gridBagConstraints29.gridwidth = 4;
            final GridBagConstraints gridBagConstraints110 = new GridBagConstraints();
            gridBagConstraints110.gridx = 0;
            gridBagConstraints110.insets = new java.awt.Insets(1,5,1,0);
            gridBagConstraints110.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints110.gridy = 1;
            Lsubject = new JLabel();

            final GridBagConstraints gridBagConstraints101 = new GridBagConstraints();
            gridBagConstraints101.gridx = 1;
            gridBagConstraints101.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints101.insets = new java.awt.Insets(1,1,1,5);
            gridBagConstraints101.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints101.weighty = 1.0;
            gridBagConstraints101.gridy = 3;

            final GridBagConstraints gridBagConstraints102 = new GridBagConstraints();
            gridBagConstraints102.gridx = 2;
            gridBagConstraints102.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints102.insets = new java.awt.Insets(1,1,1,5);
            gridBagConstraints102.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints102.weighty = 1.0;
            gridBagConstraints102.gridy = 3;

            final GridBagConstraints gridBagConstraints103 = new GridBagConstraints();
            gridBagConstraints103.gridx = 3;
            gridBagConstraints103.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints103.insets = new java.awt.Insets(1,1,1,5);
            gridBagConstraints103.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints103.weighty = 1.0;
            gridBagConstraints103.gridy = 3;

            final GridBagConstraints gridBagConstraints104 = new GridBagConstraints();
            gridBagConstraints104.gridx = 4;
            gridBagConstraints104.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints104.insets = new java.awt.Insets(1,1,1,5);
            gridBagConstraints104.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints104.weighty = 1.0;
            gridBagConstraints104.gridy = 3;

            final GridBagConstraints gridBagConstraints91 = new GridBagConstraints();
            gridBagConstraints91.gridx = -1;
            gridBagConstraints91.gridy = -1;
            final GridBagConstraints gridBagConstraints2 = new GridBagConstraints();
            gridBagConstraints2.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints2.gridy = 2;
            gridBagConstraints2.weightx = 1.0;
            gridBagConstraints2.gridwidth = 1;
            gridBagConstraints2.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints2.gridx = 1;
            gridBagConstraints2.gridwidth = 4;
            final GridBagConstraints gridBagConstraints11 = new GridBagConstraints();
            gridBagConstraints11.fill = java.awt.GridBagConstraints.HORIZONTAL;
            gridBagConstraints11.gridy = 0;
            gridBagConstraints11.weightx = 1.0;
            gridBagConstraints11.gridwidth = 1;
            gridBagConstraints11.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints11.gridx = 1;
            gridBagConstraints11.gridwidth = 4;
            final GridBagConstraints gridBagConstraints1 = new GridBagConstraints();
            gridBagConstraints1.gridx = 0;
            gridBagConstraints1.insets = new java.awt.Insets(1,5,1,0);
            gridBagConstraints1.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints1.gridy = 2;
            Lcontent = new JLabel();
            final GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.gridx = 0;
            gridBagConstraints.insets = new java.awt.Insets(1,5,1,0);
            gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints.gridy = 0;
            Lsender = new JLabel();
            Psearch = new JPanel();
            Psearch.setLayout(new GridBagLayout());
            Psearch.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            Psearch.add(Lsender, gridBagConstraints);
            Psearch.add(Lcontent, gridBagConstraints1);
            Psearch.add(getSearch_CBprivateMsgsOnly(), gridBagConstraints101);
            Psearch.add(getSearch_CBflaggedMsgsOnly(), gridBagConstraints102);
            Psearch.add(getSearch_CBstarredMsgsOnly(), gridBagConstraints103);
            Psearch.add(getSearch_CBrepliedMsgsOnly(), gridBagConstraints104);
            Psearch.add(Lsubject, gridBagConstraints110);

            JPanel dummyPanel;

            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(getSearch_TFsender());
            dummyPanel.add(senderCaseCheckBox);
            Psearch.add(dummyPanel, gridBagConstraints11);

            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(getSearch_TFcontent());
            dummyPanel.add(contentCaseCheckBox);
            Psearch.add(dummyPanel, gridBagConstraints2);

            dummyPanel = new JPanel();
            dummyPanel.setLayout(new BoxLayout(dummyPanel, BoxLayout.X_AXIS));
            dummyPanel.add(getSearch_TFsubject());
            dummyPanel.add(subjectCaseCheckBox);
            Psearch.add(dummyPanel, gridBagConstraints29);
        }
        return Psearch;
    }

    /**
     * This method initializes jPanel2
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPsearchResult() {
        if( PsearchResult == null ) {
            final GridBagConstraints gridBagConstraints6 = new GridBagConstraints();
            gridBagConstraints6.gridx = 1;
            gridBagConstraints6.anchor = java.awt.GridBagConstraints.EAST;
            gridBagConstraints6.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints6.gridy = 0;
            LresultCount = new JLabel("");
            final GridBagConstraints gridBagConstraints4 = new GridBagConstraints();
            gridBagConstraints4.fill = java.awt.GridBagConstraints.BOTH;
            gridBagConstraints4.gridy = 1;
            gridBagConstraints4.ipadx = 239;
            gridBagConstraints4.ipady = 0;
            gridBagConstraints4.weightx = 1.0;
            gridBagConstraints4.weighty = 1.0;
            gridBagConstraints4.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints4.gridwidth = 2;
            gridBagConstraints4.gridx = 0;
            final GridBagConstraints gridBagConstraints3 = new GridBagConstraints();
            gridBagConstraints3.gridx = 0;
            gridBagConstraints3.ipadx = 0;
            gridBagConstraints3.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints3.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints3.gridy = 0;
            LsearchResult = new JLabel();
            PsearchResult = new JPanel();
            PsearchResult.setLayout(new GridBagLayout());
            PsearchResult.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            PsearchResult.add(LsearchResult, gridBagConstraints3);
            PsearchResult.add(getJScrollPane(), gridBagConstraints4);
            PsearchResult.add(LresultCount, gridBagConstraints6);
        }
        return PsearchResult;
    }

    /**
     * This method initializes jTextField
     *
     * @return javax.swing.JTextField
     */
    private JTextField getSearch_TFsender() {
        if( search_TFsender == null ) {
            search_TFsender = new JTextField();
            new TextComponentClipboardMenu(search_TFsender, language);
        }
        return search_TFsender;
    }

    /**
     * This method initializes jTextField1
     *
     * @return javax.swing.JTextField
     */
    private JTextField getSearch_TFcontent() {
        if( search_TFcontent == null ) {
            search_TFcontent = new JTextField();
            new TextComponentClipboardMenu(search_TFcontent, language);
        }
        return search_TFcontent;
    }

    /**
     * This method initializes jPanel1
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPdate() {
        if( Pdate == null ) {
            final GridBagConstraints gridBagConstraints7 = new GridBagConstraints();
            gridBagConstraints7.gridx = 0;
            gridBagConstraints7.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints7.insets = new java.awt.Insets(1,5,0,5);
            gridBagConstraints7.gridy = 1;
            final GridBagConstraints gridBagConstraints15 = new GridBagConstraints();
            gridBagConstraints15.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints15.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints15.gridwidth = 3;
            gridBagConstraints15.gridx = 1;
            gridBagConstraints15.gridy = 3;
            gridBagConstraints15.weightx = 1.0;
            gridBagConstraints15.fill = java.awt.GridBagConstraints.NONE;
            final GridBagConstraints gridBagConstraints14 = new GridBagConstraints();
            gridBagConstraints14.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints14.gridy = 3;
            gridBagConstraints14.weighty = 1.0;
            gridBagConstraints14.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints14.gridx = 0;
            final GridBagConstraints gridBagConstraints13 = new GridBagConstraints();
            gridBagConstraints13.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints13.insets = new java.awt.Insets(1,5,0,5);
            gridBagConstraints13.gridx = 3;
            gridBagConstraints13.gridy = 2;
            gridBagConstraints13.weightx = 0.0;
            gridBagConstraints13.fill = java.awt.GridBagConstraints.NONE;
            final GridBagConstraints gridBagConstraints12 = new GridBagConstraints();
            gridBagConstraints12.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints12.gridx = 2;
            gridBagConstraints12.gridy = 2;
            gridBagConstraints12.insets = new java.awt.Insets(1,2,0,2);
            date_Lto = new JLabel();
            final GridBagConstraints gridBagConstraints10 = new GridBagConstraints();
            gridBagConstraints10.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints10.insets = new java.awt.Insets(1,5,0,5);
            gridBagConstraints10.gridx = 1;
            gridBagConstraints10.gridy = 2;
            gridBagConstraints10.weightx = 0.0;
            gridBagConstraints10.fill = java.awt.GridBagConstraints.NONE;
            final GridBagConstraints gridBagConstraints9 = new GridBagConstraints();
            gridBagConstraints9.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints9.gridx = 0;
            gridBagConstraints9.gridy = 2;
            gridBagConstraints9.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints5 = new GridBagConstraints();
            gridBagConstraints5.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints5.gridwidth = 4;
            gridBagConstraints5.gridx = 0;
            gridBagConstraints5.gridy = 0;
            gridBagConstraints5.insets = new java.awt.Insets(1,5,0,5);
            Pdate = new JPanel();
            Pdate.setLayout(new GridBagLayout());
            Pdate.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            Pdate.add(getDate_RBdisplayed(), gridBagConstraints5);
            Pdate.add(getDate_RBbetweenDates(), gridBagConstraints9);
            Pdate.add(getDate_TFstartDate(), gridBagConstraints10);
            Pdate.add(date_Lto, gridBagConstraints12);
            Pdate.add(getDate_TFendDate(), gridBagConstraints13);
            Pdate.add(getDate_RBdaysBackward(), gridBagConstraints14);
            Pdate.add(getDate_TFdaysBackward(), gridBagConstraints15);
            Pdate.add(getDate_RBall(), gridBagConstraints7);
        }
        return Pdate;
    }

    /**
     * This method initializes jRadioButton
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getDate_RBdisplayed() {
        if( date_RBdisplayed == null ) {
            date_RBdisplayed = new JRadioButton();
            date_RBdisplayed.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    date_RBitemStateChanged();
                }
            });
        }
        return date_RBdisplayed;
    }

    /**
     * This method initializes jRadioButton1
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getDate_RBbetweenDates() {
        if( date_RBbetweenDates == null ) {
            date_RBbetweenDates = new JRadioButton();
            date_RBbetweenDates.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    date_RBitemStateChanged();
                }
            });
        }
        return date_RBbetweenDates;
    }

    /**
     * This method initializes jTextField3
     *
     * @return javax.swing.JTextField
     */
    private JDateChooser getDate_TFstartDate() {
        if( date_TFstartDate == null ) {
            date_TFstartDate = new JDateChooser();
        }
        return date_TFstartDate;
    }

    /**
     * This method initializes jTextField4
     *
     * @return javax.swing.JTextField
     */
    private JDateChooser getDate_TFendDate() {
        if( date_TFendDate == null ) {
            date_TFendDate = new JDateChooser();
        }
        return date_TFendDate;
    }

    /**
     * This method initializes jRadioButton2
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getDate_RBdaysBackward() {
        if( date_RBdaysBackward == null ) {
            date_RBdaysBackward = new JRadioButton();
            date_RBdaysBackward.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    date_RBitemStateChanged();
                }
            });
        }
        return date_RBdaysBackward;
    }

    private void date_RBitemStateChanged() {
        if( getDate_RBdisplayed().isSelected() ) {
            getDate_TFdaysBackward().setEnabled(false);
            getDate_TFendDate().setEnabled(false);
            getDate_TFstartDate().setEnabled(false);
        } else if( getDate_RBbetweenDates().isSelected() ) {
            getDate_TFdaysBackward().setEnabled(false);
            getDate_TFendDate().setEnabled(true);
            getDate_TFstartDate().setEnabled(true);
        } else if( getDate_RBdaysBackward().isSelected() ) {
            getDate_TFdaysBackward().setEnabled(true);
            getDate_TFendDate().setEnabled(false);
            getDate_TFstartDate().setEnabled(false);
        }
    }

    /**
     * This method initializes jTextField5
     *
     * @return javax.swing.JTextField
     */
    private JTextField getDate_TFdaysBackward() {
        if( date_TFdaysBackward == null ) {
            date_TFdaysBackward = new JTextField();
            date_TFdaysBackward.setColumns(6);
            date_TFdaysBackward.setDocument(new WholeNumberDocument());
        }
        return date_TFdaysBackward;
    }

    /**
     * This method initializes jPanel4
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPtrustState() {
        if( PtrustState == null ) {
            final GridBagConstraints gridBagConstraints25 = new GridBagConstraints();
            gridBagConstraints25.anchor = java.awt.GridBagConstraints.WEST;
            gridBagConstraints25.insets = new java.awt.Insets(0,25,0,0);
            gridBagConstraints25.gridwidth = 3;
            gridBagConstraints25.gridx = 0;
            gridBagConstraints25.gridy = 3;
            gridBagConstraints25.weightx = 1.0;
            gridBagConstraints25.weighty = 1.0;
            gridBagConstraints25.fill = java.awt.GridBagConstraints.NONE;
            final GridBagConstraints gridBagConstraints18 = new GridBagConstraints();
            gridBagConstraints18.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints18.gridx = 0;
            gridBagConstraints18.gridy = 2;
            gridBagConstraints18.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints17 = new GridBagConstraints();
            gridBagConstraints17.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints17.gridx = 0;
            gridBagConstraints17.gridy = 1;
            gridBagConstraints17.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints16 = new GridBagConstraints();
            gridBagConstraints16.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints16.insets = new java.awt.Insets(1,5,0,5);
            gridBagConstraints16.gridx = 0;
            gridBagConstraints16.gridy = 0;
            gridBagConstraints16.fill = java.awt.GridBagConstraints.NONE;
            PtrustState = new JPanel();
            PtrustState.setLayout(new GridBagLayout());
            PtrustState.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            PtrustState.add(getTruststate_RBdisplayed(), gridBagConstraints16);
            PtrustState.add(getTruststate_RBall(), gridBagConstraints17);
            PtrustState.add(getTruststate_RBchosed(), gridBagConstraints18);
            PtrustState.add(getTruststate_PtrustStates(), gridBagConstraints25);
        }
        return PtrustState;
    }

    /**
     * This method initializes jRadioButton3
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getTruststate_RBdisplayed() {
        if( truststate_RBdisplayed == null ) {
            truststate_RBdisplayed = new JRadioButton();
            truststate_RBdisplayed.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    trustState_RBitemStateChanged();
                }
            });
        }
        return truststate_RBdisplayed;
    }

    private void trustState_RBitemStateChanged() {
        boolean enableTtrustStatesPanel;
        if( getTruststate_RBchosed().isSelected() ) {
            enableTtrustStatesPanel = true;
        } else {
            enableTtrustStatesPanel = false;
        }
        final Component[] comps = getTruststate_PtrustStates().getComponents();
        for( final Component element : comps ) {
            element.setEnabled(enableTtrustStatesPanel);
        }
    }

    /**
     * This method initializes jRadioButton4
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getTruststate_RBall() {
        if( truststate_RBall == null ) {
            truststate_RBall = new JRadioButton();
            truststate_RBall.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    trustState_RBitemStateChanged();
                }
            });
        }
        return truststate_RBall;
    }

    /**
     * This method initializes jRadioButton5
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getTruststate_RBchosed() {
        if( truststate_RBchosed == null ) {
            truststate_RBchosed = new JRadioButton();
            truststate_RBchosed.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    trustState_RBitemStateChanged();
                }
            });
        }
        return truststate_RBchosed;
    }

    /**
     * This method initializes jPanel5
     *
     * @return javax.swing.JPanel
     */
    private JPanel getTruststate_PtrustStates() {
        if( truststate_PtrustStates == null ) {
            final GridBagConstraints gridBagConstraints24 = new GridBagConstraints();
            gridBagConstraints24.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints24.gridx = 5;
            gridBagConstraints24.gridy = 0;
            gridBagConstraints24.weightx = 0.0;
            gridBagConstraints24.insets = new java.awt.Insets(1,5,1,5);
            final GridBagConstraints gridBagConstraints23 = new GridBagConstraints();
            gridBagConstraints23.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints23.gridy = 0;
            gridBagConstraints23.gridx = 4;
            final GridBagConstraints gridBagConstraints22 = new GridBagConstraints();
            gridBagConstraints22.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints22.gridy = 0;
            gridBagConstraints22.gridx = 3;
            final GridBagConstraints gridBagConstraints21 = new GridBagConstraints();
            gridBagConstraints21.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints21.gridy = 0;
            gridBagConstraints21.gridx = 2;
            final GridBagConstraints gridBagConstraints20 = new GridBagConstraints();
            gridBagConstraints20.insets = new java.awt.Insets(1,5,1,5);
            gridBagConstraints20.gridy = 0;
            gridBagConstraints20.gridx = 1;
            final GridBagConstraints gridBagConstraints19 = new GridBagConstraints();
            gridBagConstraints19.anchor = java.awt.GridBagConstraints.CENTER;
            gridBagConstraints19.gridx = 0;
            gridBagConstraints19.gridy = 0;
            gridBagConstraints19.insets = new java.awt.Insets(1,0,1,5);
            truststate_PtrustStates = new JPanel();
            truststate_PtrustStates.setLayout(new GridBagLayout());
            truststate_PtrustStates.add(getTruststate_CBFRIEND(), gridBagConstraints19);
            truststate_PtrustStates.add(getTruststate_CBGOOD(), gridBagConstraints20);
            truststate_PtrustStates.add(getTruststate_CBNEUTRAL(), gridBagConstraints21);
            truststate_PtrustStates.add(getTruststate_CBBAD(), gridBagConstraints22);
            truststate_PtrustStates.add(getTruststate_CBNONE(), gridBagConstraints23);
            truststate_PtrustStates.add(getTruststate_CBTAMPERED(), gridBagConstraints24);
        }
        return truststate_PtrustStates;
    }

    /**
     * This method initializes jCheckBox
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBFRIEND() {
        if( truststate_CBFRIEND == null ) {
            truststate_CBFRIEND = new JCheckBox();
        }
        return truststate_CBFRIEND;
    }

    /**
     * This method initializes jCheckBox1
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBGOOD() {
        if( truststate_CBGOOD == null ) {
            truststate_CBGOOD = new JCheckBox();
        }
        return truststate_CBGOOD;
    }

    /**
     * This method initializes jCheckBox2
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBNEUTRAL() {
        if( truststate_CBNEUTRAL == null ) {
            truststate_CBNEUTRAL = new JCheckBox();
        }
        return truststate_CBNEUTRAL;
    }

    /**
     * This method initializes jCheckBox3
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBBAD() {
        if( truststate_CBBAD == null ) {
            truststate_CBBAD = new JCheckBox();
        }
        return truststate_CBBAD;
    }

    /**
     * This method initializes jCheckBox4
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBNONE() {
        if( truststate_CBNONE == null ) {
            truststate_CBNONE = new JCheckBox();
        }
        return truststate_CBNONE;
    }

    /**
     * This method initializes jCheckBox5
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getTruststate_CBTAMPERED() {
        if( truststate_CBTAMPERED == null ) {
            truststate_CBTAMPERED = new JCheckBox();
        }
        return truststate_CBTAMPERED;
    }

    /**
     * This method initializes jPanel3
     *
     * @return javax.swing.JPanel
     */
    private JPanel getParchive() {
        if( Parchive == null ) {
            final GridBagConstraints gridBagConstraints28 = new GridBagConstraints();
            gridBagConstraints28.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints28.gridx = 0;
            gridBagConstraints28.gridy = 2;
            gridBagConstraints28.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints28.weighty = 1.0;
            gridBagConstraints28.insets = new java.awt.Insets(3,5,1,5);
            final GridBagConstraints gridBagConstraints27 = new GridBagConstraints();
            gridBagConstraints27.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints27.gridx = 0;
            gridBagConstraints27.gridy = 1;
            gridBagConstraints27.fill = java.awt.GridBagConstraints.NONE;
            gridBagConstraints27.weightx = 1.0;
            gridBagConstraints27.insets = new java.awt.Insets(3,5,1,5);
            final GridBagConstraints gridBagConstraints26 = new GridBagConstraints();
            gridBagConstraints26.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints26.insets = new java.awt.Insets(3,5,1,5);
            gridBagConstraints26.gridx = 0;
            gridBagConstraints26.gridy = 0;
            gridBagConstraints26.weighty = 0.0;
            gridBagConstraints26.fill = java.awt.GridBagConstraints.NONE;
            Parchive = new JPanel();
            Parchive.setLayout(new GridBagLayout());
            Parchive.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            Parchive.add(getArchive_RBkeypoolOnly(), gridBagConstraints27);
            Parchive.add(getArchive_RBarchiveOnly(), gridBagConstraints28);
            Parchive.add(getArchive_RBkeypoolAndArchive(), gridBagConstraints26);
        }
        return Parchive;
    }

    /**
     * This method initializes jRadioButton6
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getArchive_RBkeypoolAndArchive() {
        if( archive_RBkeypoolAndArchive == null ) {
            archive_RBkeypoolAndArchive = new JRadioButton();
        }
        return archive_RBkeypoolAndArchive;
    }

    /**
     * This method initializes jRadioButton7
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getArchive_RBkeypoolOnly() {
        if( archive_RBkeypoolOnly == null ) {
            archive_RBkeypoolOnly = new JRadioButton();
        }
        return archive_RBkeypoolOnly;
    }

    /**
     * This method initializes jRadioButton8
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getArchive_RBarchiveOnly() {
        if( archive_RBarchiveOnly == null ) {
            archive_RBarchiveOnly = new JRadioButton();
        }
        return archive_RBarchiveOnly;
    }

    /**
     * This method initializes jPanel6
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPboards() {
        if( Pboards == null ) {
            final GridBagConstraints gridBagConstraints35 = new GridBagConstraints();
            gridBagConstraints35.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints35.insets = new java.awt.Insets(1,25,1,5);
            gridBagConstraints35.gridwidth = 2;
            gridBagConstraints35.gridx = 0;
            gridBagConstraints35.gridy = 3;
            gridBagConstraints35.weightx = 1.0;
            gridBagConstraints35.weighty = 1.0;
            gridBagConstraints35.fill = java.awt.GridBagConstraints.HORIZONTAL;
            final GridBagConstraints gridBagConstraints34 = new GridBagConstraints();
            gridBagConstraints34.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints34.gridx = 1;
            gridBagConstraints34.gridy = 2;
            gridBagConstraints34.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints33 = new GridBagConstraints();
            gridBagConstraints33.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints33.gridx = 0;
            gridBagConstraints33.gridy = 2;
            gridBagConstraints33.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints32 = new GridBagConstraints();
            gridBagConstraints32.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints32.gridwidth = 2;
            gridBagConstraints32.gridx = 0;
            gridBagConstraints32.gridy = 1;
            gridBagConstraints32.insets = new java.awt.Insets(1,5,0,5);
            final GridBagConstraints gridBagConstraints31 = new GridBagConstraints();
            gridBagConstraints31.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints31.gridwidth = 2;
            gridBagConstraints31.gridx = 0;
            gridBagConstraints31.gridy = 0;
            gridBagConstraints31.insets = new java.awt.Insets(1,5,0,5);
            Pboards = new JPanel();
            Pboards.setLayout(new GridBagLayout());
            Pboards.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(3,3,3,3), javax.swing.BorderFactory.createEtchedBorder(javax.swing.border.EtchedBorder.LOWERED)));
            Pboards.add(getBoards_RBdisplayed(), gridBagConstraints31);
//            Pboards.add(getBoards_RBallExisting(), gridBagConstraints32);
            Pboards.add(getBoards_RBchosed(), gridBagConstraints33);
            Pboards.add(getBoards_Bchoose(), gridBagConstraints34);
            Pboards.add(getBoards_TFchosedBoards(), gridBagConstraints35);
        }
        return Pboards;
    }

    /**
     * This method initializes jRadioButton9
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getBoards_RBdisplayed() {
        if( boards_RBdisplayed == null ) {
            boards_RBdisplayed = new JRadioButton();
            boards_RBdisplayed.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    boards_RBitemStateChanged();
                }
            });
        }
        return boards_RBdisplayed;
    }

    /**
     * This method initializes jRadioButton10
     *
     * @return javax.swing.JRadioButton
     */
//    private JRadioButton getBoards_RBallExisting() {
//        if( boards_RBallExisting == null ) {
//            boards_RBallExisting = new JRadioButton();
//            boards_RBallExisting.setText("Search in all existing board directories");
//            boards_RBallExisting.addItemListener(new java.awt.event.ItemListener() {
//                public void itemStateChanged(java.awt.event.ItemEvent e) {
//                    boards_RBitemStateChanged();
//                }
//            });
//        }
//        return boards_RBallExisting;
//    }

    /**
     * This method initializes jRadioButton11
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getBoards_RBchosed() {
        if( boards_RBchosed == null ) {
            boards_RBchosed = new JRadioButton();
            boards_RBchosed.addItemListener(new java.awt.event.ItemListener() {
                public void itemStateChanged(final java.awt.event.ItemEvent e) {
                    boards_RBitemStateChanged();
                }
            });
        }
        return boards_RBchosed;
    }

    private void boards_RBitemStateChanged() {
        boolean enableChooseControls;
        if( getBoards_RBchosed().isSelected() ) {
            enableChooseControls = true;
        } else {
            enableChooseControls = false;
        }
        getBoards_Bchoose().setEnabled(enableChooseControls);
        getBoards_TFchosedBoards().setEnabled(enableChooseControls);
    }

    /**
     * This method initializes jButton1
     *
     * @return javax.swing.JButton
     */
    private JButton getBoards_Bchoose() {
        if( boards_Bchoose == null ) {
            boards_Bchoose = new JButton();
            boards_Bchoose.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    chooseBoards();
                }
            });
        }
        return boards_Bchoose;
    }

    /**
     * This method initializes jTextField6
     *
     * @return javax.swing.JTextField
     */
    private JTextField getBoards_TFchosedBoards() {
        if( boards_TFchosedBoards == null ) {
            boards_TFchosedBoards = new JTextField();
            boards_TFchosedBoards.setText("");
            boards_TFchosedBoards.setEditable(false);
        }
        return boards_TFchosedBoards;
    }

    /**
     * This method initializes jCheckBox6
     *
     * @return javax.swing.JCheckBox
     */
    private TristateCheckBox getSearch_CBprivateMsgsOnly() {
        if( search_CBprivateMsgsOnly == null ) {
            search_CBprivateMsgsOnly = new TristateCheckBox();
        }
        return search_CBprivateMsgsOnly;
    }

    private TristateCheckBox getSearch_CBflaggedMsgsOnly() {
        if( search_CBflaggedMsgsOnly == null ) {
            search_CBflaggedMsgsOnly = new TristateCheckBox();
        }
        return search_CBflaggedMsgsOnly;
    }

    private TristateCheckBox getSearch_CBstarredMsgsOnly() {
        if( search_CBstarredMsgsOnly == null ) {
            search_CBstarredMsgsOnly = new TristateCheckBox();
        }
        return search_CBstarredMsgsOnly;
    }

    private TristateCheckBox getSearch_CBrepliedMsgsOnly() {
        if( search_CBrepliedMsgsOnly == null ) {
            search_CBrepliedMsgsOnly = new TristateCheckBox();
        }
        return search_CBrepliedMsgsOnly;
    }

    /**
     * This method initializes jScrollPane
     *
     * @return javax.swing.JScrollPane
     */
    private JScrollPane getJScrollPane() {
        if( jScrollPane == null ) {
            jScrollPane = new JScrollPane();
            jScrollPane.setWheelScrollingEnabled(true);
            jScrollPane.setForeground(new java.awt.Color(51,51,51));
            jScrollPane.setViewportView(getSearchResultTable());
        }
        return jScrollPane;
    }

    /**
     * This method initializes jTable
     *
     * @return javax.swing.JTable
     */
    private SearchMessagesResultTable getSearchResultTable() {
        if( searchResultTable == null ) {
            searchResultTable = new SearchMessagesResultTable(getSearchMessagesTableModel());
            searchResultTable.setAutoCreateColumnsFromModel(true);
            searchResultTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

            searchResultTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(final MouseEvent e) {
                    if(SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                        openSelectedMessage(); // double click opens message
                    }
                }
            });

            // remove all default Enter assignments from table (default assignment makes Enter select the next row)
            searchResultTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).getParent().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));

            // enter should not jump to next message, but open the selected msg (if any)
            searchResultTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "openMessage");
            final Action action = new AbstractAction() {
                public void actionPerformed(final ActionEvent arg0) {
                    if( getBopenMsg().isEnabled() ) {
                        openSelectedMessage(); // enter opens message
                    }
                }
            };
            searchResultTable.getActionMap().put("openMessage", action);
        }
        return searchResultTable;
    }

    /**
     * This method initializes searchMessagesTableModel
     *
     * @return frost.gui.model.SearchMessagesTableModel
     */
    private SearchMessagesTableModel getSearchMessagesTableModel() {
        if( searchMessagesTableModel == null ) {
            searchMessagesTableModel = new SearchMessagesTableModel();
        }
        return searchMessagesTableModel;
    }

    /**
     * This method initializes buttonGroup
     *
     * @return javax.swing.ButtonGroup
     */
    private ButtonGroup getBoards_buttonGroup() {
        if( boards_buttonGroup == null ) {
            boards_buttonGroup = new ButtonGroup();
            boards_buttonGroup.add(getBoards_RBdisplayed());
            boards_buttonGroup.add(getBoards_RBchosed());
//            boards_buttonGroup.add(getBoards_RBallExisting());
        }
        return boards_buttonGroup;
    }

    /**
     * This method initializes date_buttonGroup
     *
     * @return javax.swing.ButtonGroup
     */
    private ButtonGroup getDate_buttonGroup() {
        if( date_buttonGroup == null ) {
            date_buttonGroup = new ButtonGroup();
            date_buttonGroup.add(getDate_RBbetweenDates());
            date_buttonGroup.add(getDate_RBdaysBackward());
            date_buttonGroup.add(getDate_RBdisplayed());
            date_buttonGroup.add(getDate_RBall());
        }
        return date_buttonGroup;
    }

    /**
     * This method initializes truststate_buttonGroup
     *
     * @return javax.swing.ButtonGroup
     */
    private ButtonGroup getTruststate_buttonGroup() {
        if( truststate_buttonGroup == null ) {
            truststate_buttonGroup = new ButtonGroup();
            truststate_buttonGroup.add(getTruststate_RBdisplayed());
            truststate_buttonGroup.add(getTruststate_RBall());
            truststate_buttonGroup.add(getTruststate_RBchosed());
        }
        return truststate_buttonGroup;
    }

    /**
     * This method initializes archive_buttonGroup
     *
     * @return javax.swing.ButtonGroup
     */
    private ButtonGroup getArchive_buttonGroup() {
        if( archive_buttonGroup == null ) {
            archive_buttonGroup = new ButtonGroup();
            archive_buttonGroup.add(getArchive_RBkeypoolOnly());
            archive_buttonGroup.add(getArchive_RBarchiveOnly());
            archive_buttonGroup.add(getArchive_RBkeypoolAndArchive());
        }
        return archive_buttonGroup;
    }

    private void chooseBoards() {

        // get and sort all boards
        final List<Board> allBoards = MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards();
        if (allBoards.size() == 0) {
            MiscToolkit.showMessageDialog(this,
                    language.getString("SearchMessages.errorDialogs.noBoardsToChoose"),
                    language.getString("SearchMessages.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            return;
        }
        Collections.sort(allBoards);

        final BoardsChooser bc = new BoardsChooser(this, allBoards, chosedBoardsList);

        final List<Board> resultBoards = bc.runDialog();
        if( resultBoards != null ) {
            chosedBoardsList = resultBoards;
            updateBoardTextField(chosedBoardsList);
        }
    }

    private void updateBoardTextField(final List<Board> boards) {
        final StringBuilder txt = new StringBuilder();
        if( boards != null ) {
            for(final Iterator<Board> i=boards.iterator(); i.hasNext(); ) {
                final Board b = i.next();
                txt.append(b.getName());
                if( i.hasNext() ) {
                    txt.append("; ");
                }
            }
            chosedBoardsList = boards;
        } else {
            chosedBoardsList = Collections.emptyList();
        }
        getBoards_TFchosedBoards().setText(txt.toString());
    }

    private void initializeWithDefaults() {

        getBoards_RBdisplayed().doClick();
        getDate_RBdisplayed().doClick();
        getTruststate_RBdisplayed().doClick();
        getArchive_RBkeypoolAndArchive().doClick();

        getDate_TFdaysBackward().setText("0");
    }

    private SearchMessagesConfig getSearchConfig() {

        final SearchMessagesConfig scfg = new SearchMessagesConfig();

        // visibly trim the user-provided search fields so that there's no leading/trailing
        // whitespace (they probably don't mean to do that, and it would affect the search).
        getSearch_TFsender().setText(getSearch_TFsender().getText().trim());
        getSearch_TFsubject().setText(getSearch_TFsubject().getText().trim());
        getSearch_TFcontent().setText(getSearch_TFcontent().getText().trim());

        // compile the regex patterns (each pattern becomes a non-null value if a valid
        // regex was provided for that field).
        scfg.setSearchSender(getSearch_TFsender().getText(), senderCaseCheckBox.isSelected());
        scfg.setSearchSubject(getSearch_TFsubject().getText(), subjectCaseCheckBox.isSelected());
        scfg.setSearchContent(getSearch_TFcontent().getText(), contentCaseCheckBox.isSelected());

        // validate all provided search patterns to make sure they're syntactically correct
        if( (!scfg.senderString.isEmpty() && scfg.senderPattern==null)
            || (!scfg.subjectString.isEmpty() && scfg.subjectPattern==null)
            || (!scfg.contentString.isEmpty() && scfg.contentPattern==null) ) {
            // one or more of the user-provided patterns had a string provided, but didn't get a
            // valid regex. that means that the regex compilation failed due to syntax errors,
            // so refuse to perform this search until the user has fixed their search pattern.
            // the user has already been warned via a dialog box from the regex compiler, so no
            // further error boxes are required here.
            return null; // no search config = search won't take place
        }

        scfg.searchPrivateMsgsOnly = getSearch_CBprivateMsgsOnly().getBooleanState();
        scfg.searchFlaggedMsgsOnly = getSearch_CBflaggedMsgsOnly().getBooleanState();
        scfg.searchStarredMsgsOnly = getSearch_CBstarredMsgsOnly().getBooleanState();
        scfg.searchRepliedMsgsOnly = getSearch_CBrepliedMsgsOnly().getBooleanState();

        if( getBoards_RBdisplayed().isSelected() ) {
            scfg.searchBoards = SearchMessagesConfig.BOARDS_DISPLAYED;
//        } else if( getBoards_RBallExisting().isSelected() ) {
//            scfg.searchBoards = SearchConfig.BOARDS_EXISTING_DIRS;
        } else if( getBoards_RBchosed().isSelected() ) {
            if( chosedBoardsList.size() == 0 ) {
                MiscToolkit.showMessageDialog(this,
                        language.getString("SearchMessages.errorDialogs.noBoardsChosen"),
                        language.getString("SearchMessages.errorDialogs.title"),
                        MiscToolkit.ERROR_MESSAGE);
                return null;
            }
            scfg.searchBoards = SearchMessagesConfig.BOARDS_CHOSED;
            scfg.chosedBoards = chosedBoardsList;
        }

        if( getDate_RBdisplayed().isSelected() ) {
            scfg.searchDates = SearchMessagesConfig.DATE_DISPLAYED;
        } else if( getDate_RBall().isSelected() ) {
            scfg.searchDates = SearchMessagesConfig.DATE_ALL;
        } else if( getDate_RBbetweenDates().isSelected() ) {
            scfg.searchDates = SearchMessagesConfig.DATE_BETWEEN_DATES;
            try {
                // NOTE: getDate() can be null when field is empty. NPE is catched below.
                scfg.startDate = getDate_TFstartDate().getDate().getTime();
                scfg.endDate = getDate_TFendDate().getDate().getTime();

                // check start before end
                if( scfg.startDate > scfg.endDate ) {
                    MiscToolkit.showMessageDialog(this,
                            language.getString("SearchMessages.errorDialogs.startDateIsAfterEndDate"),
                            language.getString("SearchMessages.errorDialogs.title"),
                            MiscToolkit.ERROR_MESSAGE);
                    return null;
                }
            } catch(final Exception ex) {
                MiscToolkit.showMessageDialog(this,
                        language.getString("SearchMessages.errorDialogs.invalidStartOrEndDate"),
                        language.getString("SearchMessages.errorDialogs.title"),
                        MiscToolkit.ERROR_MESSAGE);
                return null;
            }
        } else if( getDate_RBdaysBackward().isSelected() ) {
            scfg.searchDates = SearchMessagesConfig.DATE_DAYS_BACKWARD;
            try {
                scfg.daysBackward = Integer.parseInt(getDate_TFdaysBackward().getText());
            } catch(final NumberFormatException ex) { } // never happens, we allow only digits in textfield!
        }

        if( getTruststate_RBdisplayed().isSelected() ) {
            scfg.searchTruststates = SearchMessagesConfig.TRUST_DISPLAYED;
        } else if( getTruststate_RBall().isSelected() ) {
            scfg.searchTruststates = SearchMessagesConfig.TRUST_ALL;
        } else if( getTruststate_RBchosed().isSelected() ) {
            scfg.searchTruststates = SearchMessagesConfig.TRUST_CHOSED;
            scfg.trust_FRIEND = getTruststate_CBFRIEND().isSelected();
            scfg.trust_GOOD = getTruststate_CBGOOD().isSelected();
            scfg.trust_NEUTRAL = getTruststate_CBNEUTRAL().isSelected();
            scfg.trust_BAD = getTruststate_CBBAD().isSelected();
            scfg.trust_NONE = getTruststate_CBNONE().isSelected();
            scfg.trust_TAMPERED = getTruststate_CBTAMPERED().isSelected();

            if( !scfg.trust_FRIEND && !scfg.trust_GOOD && !scfg.trust_NEUTRAL &&
                !scfg.trust_BAD && !scfg.trust_NONE && !scfg.trust_TAMPERED )
            {
                MiscToolkit.showMessageDialog(this,
                        language.getString("SearchMessages.errorDialogs.noTrustStateSelected"),
                        language.getString("SearchMessages.errorDialogs.title"),
                        MiscToolkit.ERROR_MESSAGE);
                return null;
            }
        }

        if( getArchive_RBkeypoolOnly().isSelected() ) {
            scfg.searchInKeypool = true;
            scfg.searchInArchive = false;
        } else if( getArchive_RBarchiveOnly().isSelected() ) {
            scfg.searchInKeypool = false;
            scfg.searchInArchive = true;
        } else if( getArchive_RBkeypoolAndArchive().isSelected() ) {
            scfg.searchInKeypool = true;
            scfg.searchInArchive = true;
        }

        scfg.msgMustContainBoards = getAttachment_CBmustContainBoards().isSelected();
        scfg.msgMustContainFiles = getAttachment_CBmustContainFiles().isSelected();

        return scfg;
    }

    /**
     * When window is about to close, do same as if CANCEL was pressed.
     * @see java.awt.Window#processWindowEvent(java.awt.event.WindowEvent)
     */
    @Override
    protected void processWindowEvent(final WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            closePressed();
        } else {
            super.processWindowEvent(e);
        }
    }

    private SearchMessagesThread getRunningSearchThread() {
        return runningSearchThread;
    }

    private void setRunningSearchThread(final SearchMessagesThread t) {
        runningSearchThread = t;
    }

    /**
     * Disables all input panels during run of search, remembers disabled
     * components for later re-enabling.
     *
     */
    private void disableInputPanels() {
        previouslyEnabledComponents.clear();
        for(int x=0; x < getJTabbedPane().getTabCount(); x++ ) {
            final JPanel c = (JPanel)getJTabbedPane().getComponentAt(x);
            disableInputPanels(c);
        }
    }
    private void disableInputPanels(final Container c) {
        final Component[] cs = c.getComponents();
        for( final Component element : cs ) {
            if( element instanceof Container ) {
                disableInputPanels((Container)element);
            }
            if( element.isEnabled() ) {
                previouslyEnabledComponents.add(element);
                element.setEnabled(false);
            }
        }
    }

    /**
     * Re-enables the disabled input panels.
     */
    private void enableInputPanels() {
        for(int x=0; x < getJTabbedPane().getTabCount(); x++ ) {
            final JPanel c = (JPanel)getJTabbedPane().getComponentAt(x);
            enableInputPanels(c);
        }
        previouslyEnabledComponents.clear();
    }

    private void enableInputPanels(final Container c) {
        final Component[] cs = c.getComponents();
        for( final Component element : cs ) {
            if( element instanceof Container ) {
                enableInputPanels((Container)element);
            }
            if( previouslyEnabledComponents.contains(element) ) {
                element.setEnabled(true);
            }
        }
    }

    public void notifySearchThreadFinished() {
        setRunningSearchThread(null);

        enableInputPanels();

        // reset buttons
        getBcancel().setEnabled(true);
        getBsearch().setText(startSearchStr);
    }

    // stop searching or close window
    private void closePressed() {
        if( getRunningSearchThread() != null ) {
            // close not allowed, search must be stopped
            MiscToolkit.showMessageDialog(this,
                    language.getString("SearchMessages.errorDialog.stopSearchBeforeClose"),
                    language.getString("SearchMessages.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);
            return;
        }
        saveWindowState();
        language.removeLanguageListener(this);
        ((JTranslatableTabbedPane)getJTabbedPane()).close();
        setVisible(false);
    }

    private void startOrStopSearching() {

        if( getRunningSearchThread() != null ) {
            // stop search thread, final handling is done in notifySearchThreadFinished()
            getRunningSearchThread().requestStop();
            return;
        }

        searchMessagesConfig = getSearchConfig();
        if( searchMessagesConfig == null ) {
            // invalid cfg
            return;
        }

        clearSearchResultTable();

        // disable all input panels
        disableInputPanels();

        // set button states
        getBcancel().setEnabled(false);
        getBsearch().setText(stopSearchStr);

        getBopenMsg().setEnabled(false);
        getBgotoMsg().setEnabled(false);

        setRunningSearchThread(new SearchMessagesThread(this, searchMessagesConfig));
        getRunningSearchThread().setPriority(Thread.MIN_PRIORITY); // low prio
        getRunningSearchThread().start();
    }

    private void clearSearchResultTable() {
        getSearchMessagesTableModel().clearDataModel();
        resultCount = 0;
        updateResultCountLabel(resultCount);
    }

    private void updateResultCountLabel(final int rs) {
        LresultCount.setText(resultCountPrefix + rs);
    }

    /**
     * Called by SearchMessagesThread to add a found message.
     */
    public void addFoundMessage(final FrostSearchResultMessageObject msg) {
        // we were called from io thread
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                // add msg to table
                getSearchMessagesTableModel().addRow(msg);
                resultCount++;
                updateResultCountLabel(resultCount);
                if( !getBopenMsg().isEnabled() ) {
                    getBopenMsg().setEnabled(true);
                }
                if( !getBgotoMsg().isEnabled() ) {
                    getBgotoMsg().setEnabled(true);
                }
            }
        });
    }

    private class GotoSelectedMessageThread extends Thread {
        private FrostMessageObject msgObj; // the actual frost message object describing the message
        private Component dialogParentFrame; // used for parenting any error dialog boxes to the search frame
        private int resultRow; // just used to force an "update" of the result row after we're done, to show the message as read

        public GotoSelectedMessageThread(final FrostMessageObject msgObj, final Component dialogParentFrame, final int resultRow) {
            this.msgObj = msgObj;
            this.dialogParentFrame = dialogParentFrame;
            this.resultRow = resultRow;
        }

        private void cleanup() {
            // runs these jobs on the main AWT event queue, so that the GUI updates happen at the correct time
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    // re-enable the "go to message" GUI button
                    BgotoMsg.setEnabled(true);
                }
            });
        }

        public void run() {
            // get a reference to the "news" tab (the one containing the board tree, message list, etc.
            // NOTE: we grab the "Frost" message tab since Frost's search only deals with Frost
            // messages, so we don't have to worry about Freetalk at all.
            final FrostMessageTab newsTab = MainFrame.getInstance().getFrostMessageTab();

            // ensure that the board exists in the board tree and select it (if not already selected).
            // we unfortunately NEED to select the board first, so that it'll load all messages into
            // the message tree, before we can actually check if the message exists in the board.
            final int boardSelResult = newsTab.getTofTree().setSelectedBoard(msgObj.getBoard());
            if( boardSelResult < 0 ) { // board does not exist, or exists but could not be selected
                if( boardSelResult == -1 ) {
                    // the message's board isn't in their list of subscribed boards, so warn the user and abort
                    MiscToolkit.showMessageDialog(dialogParentFrame,
                            language.getString("SearchMessages.errorDialogs.gotoCannotFindBoard"),
                            language.getString("SearchMessages.errorDialogs.title"),
                            MiscToolkit.ERROR_MESSAGE);
                }
                cleanup();
                return;
            }

            // we know that the board exists and is selected. now make sure that the main Frost
            // window is displaying the "News" tab; the switch takes place in the GUI thread, and
            // therefore happens instantly as long as the GUI thread isn't busy... either way,
            // we will wait until the tab switch is complete!
            // NOTE: we can instantly call "invokeAndWait" without having to check if we're already
            // running in the main GUI thread, since the "go to message" thread is *always* an
            // independent worker-thread.
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        MainFrame.getInstance().selectTabbedPaneTab("MainFrame.tabbedPane.news");
                    }
                });
            } catch( InterruptedException e ) {
                return; // if the dispatched job was interrupted (such as the GUI shutting down), then simply abort this thread
            } catch( InvocationTargetException e ) {
                return; // same thing if any of the called functions threw a runtime exception
            }

            // if the board selection has changed (either by us, or externally), then we need to
            // give Frost some time to load the messages from disk before we try handling them.
            // NOTE: we *could* check "if boardSelResult > 0" to see if *we* triggered a board
            // switch; but it's safer to do this check at all times, so that it never tries to
            // scan for the message while *any* form of message list refresh is in progress.
            final TOF tof = TOF.getInstance();
            int msWaited = 0;
            while( tof.isLoadingMessages() ) {
                // wait for the board messages to load into the table view
                Mixed.wait(150);
                msWaited += 150;
                if( msWaited >= 15000 ) {
                    // we've waited for 15 seconds for the board to load its messages; no boards
                    // should be taking this long (usually they take ~1 second or less), so let's
                    // give up... the user can simply click the "go to message" button again
                    // when the board has finished loading, if they really want to.
                    // UPDATE: Thanks to Frost-Next's rewritten board loader, even massive boards
                    // with tens of thousands of messages now load in less than a second instead
                    // of several minutes, so this is NEVER going to reach 15s and abort! ;-)
                    cleanup();
                    return; // loading of board messages is taking way too long; abort silently...
                }
            }

            // alright the board exists, is selected and all messages are loaded...
            // now look for our message in the message tree view...

            // get a reference to the "message tree table", which is the tree within the
            // messagepanel that contains the list of all loaded messages for that board,
            // and tell it to select (and scroll to) the desired message, if possible
            final int msgSelResult = newsTab.getMessagePanel().getMessageTable().setSelectedMessage(msgObj);
            if( msgSelResult < 0 ) {
                    // a <0 result means the message does not exist (may be hidden by view-filter),
                    // or exists but could not be selected (-2; but we treat that as "not found",
                    // since it should never be able to happen).
                    // the message wasn't found in the board, most likely because of the user's
                    // filtering, so warn the user and abort.
                    // most common filtering reasons: frost is set to only "display X days" (but
                    // user has done a "search: date: search all dates" (even invisible ones)),
                    // or they've hit the "show only unread messages" toolbar button, or enabled
                    // "hide anonymous users", and things like that.
                    // NOTE: we are NOT going to change the user's board view filters for them;
                    // that's intrusive. let them decide!
                    MiscToolkit.showMessageDialog(dialogParentFrame,
                            language.getString("SearchMessages.errorDialogs.gotoCannotFindMessage"),
                            language.getString("SearchMessages.errorDialogs.title"),
                            MiscToolkit.ERROR_MESSAGE);
                cleanup();
                return;
            }

            // if the message was marked as "unread" in the search results table, then mark it as
            // read instead. this is just a nice little cosmetic bonus. note that the resultRow is
            // model-based and therefore always correct even if the user quickly re-sorts the table
            // before the update triggers.
            if( msgObj.isNew() ) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        msgObj.setNew(false);
                        getSearchMessagesTableModel().fireTableRowsUpdated(resultRow, resultRow);
                    }
                });
            }

            // we're done! the message has been selected; now just re-enable the "go to message" button!
            cleanup();
        }
    }
    private void gotoSelectedMessage() {
        // retrieve the frost message object for the selected message
        final int row = getSearchResultTable().getSelectedRow();
        if (row < 0) {
            return;
        }
        final FrostSearchResultMessageObject resultMsgObj = (FrostSearchResultMessageObject)getSearchMessagesTableModel().getRow(row);
        if( resultMsgObj == null ) {
            return;
        }
        final FrostMessageObject mo = resultMsgObj.getMessageObject();

        // disable the "go to message" button, so that the user can't start multiple "go to
        // message" jobs by spamming the button
        BgotoMsg.setEnabled(false);

        // start a separate thread to do the message selection, so that we don't lock up the main GUI
        final GotoSelectedMessageThread gotoThread = new GotoSelectedMessageThread(mo, this, row);
        gotoThread.start();
    }

    private void openSelectedMessage() {
        final int row = getSearchResultTable().getSelectedRow();
        if (row < 0) {
            return;
        }
        final FrostSearchResultMessageObject msg = (FrostSearchResultMessageObject)getSearchMessagesTableModel().getRow(row);
        if( msg == null ) {
            return;
        }
        final FrostMessageObject mo = msg.getMessageObject();
        final MessageWindow messageWindow = new MessageWindow( this, mo, this.getSize(), searchMessagesConfig );
        messageWindow.setVisible(true);
    }

    /**
     * This Document ensures that only digits can be entered into a text field.
     */
    protected class WholeNumberDocument extends PlainDocument {
        @Override
        public void insertString(final int offs, final String str, final AttributeSet a) throws BadLocationException {
            final char[] source = str.toCharArray();
            final char[] result = new char[source.length];
            int j = 0;

            for( int i = 0; i < result.length; i++ ) {
                if( Character.isDigit(source[i]) ) {
                    result[j++] = source[i];
                }
            }
            super.insertString(offs, new String(result, 0, j), a);
        }
    }

    /**
     * This method initializes date_RBall
     *
     * @return javax.swing.JRadioButton
     */
    private JRadioButton getDate_RBall() {
        if( date_RBall == null ) {
            date_RBall = new JRadioButton();
        }
        return date_RBall;
    }

    private void saveWindowState() {
        final Rectangle bounds = getBounds();
        final boolean isMaximized = ((getExtendedState() & Frame.MAXIMIZED_BOTH) != 0);

        Core.frostSettings.setValue("searchMessagesDialog.lastFrameMaximized", isMaximized);

        if (!isMaximized) { // Only save the current dimension if frame is not maximized
            Core.frostSettings.setValue("searchMessagesDialog.lastFrameHeight", bounds.height);
            Core.frostSettings.setValue("searchMessagesDialog.lastFrameWidth", bounds.width);
            Core.frostSettings.setValue("searchMessagesDialog.lastFramePosX", bounds.x);
            Core.frostSettings.setValue("searchMessagesDialog.lastFramePosY", bounds.y);
        }
    }

    private void loadWindowState() {
        // load size, location and state of window
        int lastHeight = Core.frostSettings.getIntValue("searchMessagesDialog.lastFrameHeight");
        int lastWidth = Core.frostSettings.getIntValue("searchMessagesDialog.lastFrameWidth");
        final int lastPosX = Core.frostSettings.getIntValue("searchMessagesDialog.lastFramePosX");
        final int lastPosY = Core.frostSettings.getIntValue("searchMessagesDialog.lastFramePosY");
        final boolean lastMaximized = Core.frostSettings.getBoolValue("searchMessagesDialog.lastFrameMaximized");

        if( lastHeight <= 0 || lastWidth <= 0 ) {
            // first time the user opens the search dialog; use the default search window size
            setSize(700,550);
            setLocationRelativeTo(MainFrame.getInstance());
            return;
        }

	// resize to default dimensions if the user made the window too small last time
        if (lastWidth < 200) {
            lastWidth = 700;
        }
        if (lastHeight < 200) {
            lastHeight = 550;
        }

        setBounds(lastPosX, lastPosY, lastWidth, lastHeight);

        if (lastMaximized) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }
    }

    /**
     * This method initializes jPanel
     *
     * @return javax.swing.JPanel
     */
    private JPanel getJPanel() {
        if( PbuttonsRight == null ) {
            PbuttonsRight = new JPanel();
            PbuttonsRight.add(getBsearch(), null);
            PbuttonsRight.add(getBcancel(), null);
        }
        return PbuttonsRight;
    }

    /**
     * This method initializes PbuttonsRight
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPbuttonsRight() {
        if( PbuttonsLeft == null ) {
            PbuttonsLeft = new JPanel();
            PbuttonsLeft.add(getBhelp(), null);
            PbuttonsLeft.add(getBopenMsg(), null);
            PbuttonsLeft.add(getBgotoMsg(), null);
        }
        return PbuttonsLeft;
    }

    /**
     * This method initializes BopenMsg
     *
     * @return javax.swing.JButton
     */
    private JButton getBopenMsg() {
        if( BopenMsg == null ) {
            BopenMsg = new JButton();
            BopenMsg.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    openSelectedMessage();
                }
            });
            BopenMsg.setEnabled(false);
        }
        return BopenMsg;
    }

    /**
     * This method initializes BgotoMsg
     *
     * @return javax.swing.JButton
     */
    private JButton getBgotoMsg() {
        if( BgotoMsg == null ) {
            BgotoMsg = new JButton();
            BgotoMsg.addActionListener(new java.awt.event.ActionListener() {
                public void actionPerformed(final java.awt.event.ActionEvent e) {
                    gotoSelectedMessage();
                }
            });
            BgotoMsg.setEnabled(false);
        }
        return BgotoMsg;
    }

    public void languageChanged(final LanguageEvent e) {

        resultCountPrefix = language.getString("SearchMessages.label.results") + ": ";
        startSearchStr = language.getString("SearchMessages.button.search");
        stopSearchStr = language.getString("SearchMessages.button.stopSearch");

        if( getRunningSearchThread() != null ) {
            getBsearch().setText(stopSearchStr);
        } else {
            getBsearch().setText(startSearchStr);
        }

        getBopenMsg().setText(language.getString("SearchMessages.button.openMessage"));
        getBgotoMsg().setText(language.getString("SearchMessages.button.gotoMessage"));
        getBhelp().setText(language.getString("SearchMessages.button.help"));
        getBcancel().setText(language.getString("SearchMessages.button.close"));

        Lsender.setText(language.getString("SearchMessages.search.sender"));
        Lcontent.setText(language.getString("SearchMessages.search.content"));
        Lsubject.setText(language.getString("SearchMessages.search.subject"));

        senderCaseCheckBox.setToolTipText(language.getString("SearchMessages.search.tooltip.caseSensitiv"));
        subjectCaseCheckBox.setToolTipText(language.getString("SearchMessages.search.tooltip.caseSensitiv"));
        contentCaseCheckBox.setToolTipText(language.getString("SearchMessages.search.tooltip.caseSensitiv"));

        LsearchResult.setText(language.getString("SearchMessages.label.searchResult"));
        date_Lto.setText(language.getString("SearchMessages.date.to"));

        getDate_RBbetweenDates().setText(language.getString("SearchMessages.date.searchBetweenDates"));
        getDate_RBdisplayed().setText(language.getString("SearchMessages.date.searchInMessagesThatWouldBeDisplayed"));
        getDate_RBdaysBackward().setText(language.getString("SearchMessages.date.searchNumberOfDaysBackward"));
        getDate_RBall().setText(language.getString("SearchMessages.date.searchAllDates"));

        getTruststate_RBall().setText(language.getString("SearchMessages.trustState.searchAllMessages"));
        getTruststate_RBdisplayed().setText(language.getString("SearchMessages.trustState.searchInMessagesThatWouldBeDisplayed"));
        getTruststate_RBchosed().setText(language.getString("SearchMessages.trustState.searchOnlyInMessagesWithFollowingTrustState"));

        getTruststate_CBTAMPERED().setText(language.getString("SearchMessages.trustState.TAMPERED"));
        getTruststate_CBNONE().setText(language.getString("SearchMessages.trustState.NONE"));
        getTruststate_CBBAD().setText(language.getString("SearchMessages.trustState.BAD"));
        getTruststate_CBNEUTRAL().setText(language.getString("SearchMessages.trustState.NEUTRAL"));
        getTruststate_CBGOOD().setText(language.getString("SearchMessages.trustState.GOOD"));
        getTruststate_CBFRIEND().setText(language.getString("SearchMessages.trustState.FRIEND"));

        getArchive_RBarchiveOnly().setText(language.getString("SearchMessages.archive.searchOnlyInArchive"));
        getArchive_RBkeypoolOnly().setText(language.getString("SearchMessages.archive.searchOnlyInKeypool"));
        getArchive_RBkeypoolAndArchive().setText(language.getString("SearchMessages.archive.searchInKeypoolAndArchive"));

        getBoards_RBchosed().setText(language.getString("SearchMessages.boards.searchFollowingBoards"));
        getBoards_RBdisplayed().setText(language.getString("SearchMessages.boards.searchInDisplayedBoards"));

        getSearch_CBprivateMsgsOnly().setText(language.getString("SearchMessages.search.searchPrivateMessagesOnly"));
        getSearch_CBflaggedMsgsOnly().setText(language.getString("SearchMessages.search.searchFlaggedMessagesOnly"));
        getSearch_CBstarredMsgsOnly().setText(language.getString("SearchMessages.search.searchStarredMessagesOnly"));
        getSearch_CBrepliedMsgsOnly().setText(language.getString("SearchMessages.search.searchRepliedMessagesOnly"));
        getBoards_Bchoose().setText(language.getString("SearchMessages.boards.chooseBoards")+"...");

        getAttachment_CBmustContainBoards().setText(language.getString("SearchMessages.attachments.messageMustContainBoardAttachments"));
        getAttachment_CBmustContainFiles().setText(language.getString("SearchMessages.attachments.messageMustContainFileAttachments"));
    }

    /**
     * This method initializes Pattachments
     *
     * @return javax.swing.JPanel
     */
    private JPanel getPattachments() {
        if( Pattachments == null ) {
            final GridBagConstraints gridBagConstraints30 = new GridBagConstraints();
            gridBagConstraints30.gridx = 0;
            gridBagConstraints30.insets = new java.awt.Insets(3,5,1,5);
            gridBagConstraints30.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints30.weighty = 1.0;
            gridBagConstraints30.weightx = 1.0;
            gridBagConstraints30.gridy = 1;
            final GridBagConstraints gridBagConstraints8 = new GridBagConstraints();
            gridBagConstraints8.gridx = 0;
            gridBagConstraints8.anchor = java.awt.GridBagConstraints.NORTHWEST;
            gridBagConstraints8.insets = new java.awt.Insets(3,5,1,5);
            gridBagConstraints8.gridy = 0;
            Pattachments = new JPanel();
            Pattachments.setLayout(new GridBagLayout());
            Pattachments.add(getAttachment_CBmustContainBoards(), gridBagConstraints8);
            Pattachments.add(getAttachment_CBmustContainFiles(), gridBagConstraints30);
        }
        return Pattachments;
    }

    /**
     * This method initializes attachment_CBmustContainBoards
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getAttachment_CBmustContainBoards() {
        if( attachment_CBmustContainBoards == null ) {
            attachment_CBmustContainBoards = new JCheckBox();
        }
        return attachment_CBmustContainBoards;
    }

    /**
     * This method initializes attachment_CBmustContainFiles
     *
     * @return javax.swing.JCheckBox
     */
    private JCheckBox getAttachment_CBmustContainFiles() {
        if( attachment_CBmustContainFiles == null ) {
            attachment_CBmustContainFiles = new JCheckBox();
        }
        return attachment_CBmustContainFiles;
    }

    /**
     * This method initializes Bhelp
     *
     * @return javax.swing.JButton
     */
    private JButton getBhelp() {
        if( Bhelp == null ) {
            Bhelp = new JButton();
            if( Core.isHelpHtmlSecure() == false ) {
                Bhelp.setEnabled(false);
            } else {
                Bhelp.addActionListener(new java.awt.event.ActionListener() {
                    public void actionPerformed(final java.awt.event.ActionEvent e) {
                        MainFrame.getInstance().showHtmlHelp("feature_details.html");
                    }
                });
            }
        }
        return Bhelp;
    }
    
  
}  //  @jve:decl-index=0:visual-constraint="10,10"
