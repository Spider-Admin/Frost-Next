/*
  MainFrame.java / Frost
  Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>
  Some changes by Stefan Majewski <e9926279@stud3.tuwien.ac.at>

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
package frost;

import java.awt.*;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.event.*;
import javax.swing.tree.*;

import org.joda.time.*;

import frost.fileTransfer.*;
import frost.fileTransfer.download.FrostDownloadItem;
import frost.gui.*;
import frost.gui.help.*;
import frost.gui.preferences.*;
import frost.messaging.freetalk.gui.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.messaging.frost.gui.*;
import frost.messaging.frost.gui.messagetreetable.*;
import frost.messaging.frost.threads.*;
import frost.storage.*;
import frost.storage.perst.filelist.*;
import frost.storage.perst.identities.*;
import frost.storage.perst.messagearchive.*;
import frost.storage.perst.messages.*;
import frost.util.*;
import frost.util.DesktopUtils;
import frost.util.SingleTaskWorker;
import frost.util.gui.*;
import frost.util.gui.KeyConversionUtility;
import frost.util.gui.translation.*;
import frost.util.translate.*;

@SuppressWarnings("serial")
public class MainFrame extends JFrame implements SettingsUpdater, LanguageListener {

    private static final Logger logger = Logger.getLogger(MainFrame.class.getName());

    private final ImageIcon frameIconDefault = MiscToolkit.loadImageIcon("/data/frost.png");
    //private final ImageIcon frameIconNewMessage = MiscToolkit.loadImageIcon("/data/newmessage.gif");

    private final FrostMessageTab frostMessageTab = new FrostMessageTab(this);
    private final FreetalkMessageTab freetalkMessageTab = new FreetalkMessageTab(this);
    private final MessageColorManager messageColorManager = new MessageColorManager();

    private HelpBrowserFrame helpBrowser = null;
    private MemoryMonitor memoryMonitor = null;
    private ManageTrackedDownloads manageTrackedDownloads = null;

    private long todaysDateMillis = 0;

    private static SettingsClass frostSettings = null;

    private static MainFrame instance = null; // set in constructor

    private long counter = 55;

    //File Menu
    private final JMenu fileMenu = new JMenu();
    private final JMenuItem fileExitMenuItem = new JMenuItem();
    private final JMenuItem fileOpenDownloadDirMenuItem = new JMenuItem();
    private final JMenuItem fileStatisticsMenuItem = new JMenuItem();

    private final JMenuItem helpAboutMenuItem = new JMenuItem();
    private final JMenuItem helpHelpMenuItem = new JMenuItem();
    private final JMenuItem helpMemMonMenuItem = new JMenuItem();
    private final JMenuItem helpKeyConversionUtilityMenuItem = new JMenuItem();

    //Help Menu
    private final JMenu helpMenu = new JMenu();

    //Language Menu
    private final JMenu languageMenu = new JMenu();

    private final Language language;

    // The main menu
    private JMenuBar menuBar;

    private MainFrameStatusBar statusBar;

    //Options Menu
    private final JMenu optionsMenu = new JMenu();
    private final JMenuItem optionsPreferencesMenuItem = new JMenuItem();
    private final JMenuItem optionsManageLocalIdentitiesMenuItem = new JMenuItem();
    private final JMenuItem optionsManageIdentitiesMenuItem = new JMenuItem();
    private final JMenuItem optionsManageTrackedDownloadsMenuItem = new JMenuItem();

    //Plugin Menu
    private final JMenu pluginMenu = new JMenu();
    private final JMenuItem pluginTranslateMenuItem = new JMenuItem();


    private JTranslatableTabbedPane tabbedPane;
    private final JLabel timeLabel = new JLabel("");

    private final JCheckBoxMenuItem tofAutomaticUpdateMenuItem = new JCheckBoxMenuItem();

    private final JMenu tofMenu = new JMenu();

    private GlassPane glassPane = null;

    private final List<JRadioButtonMenuItem> lookAndFeels = new ArrayList<JRadioButtonMenuItem>();

    public MainFrame(final SettingsClass settings, final String title) {

        instance = this;
        Core.getInstance();
        frostSettings = settings;
        language = Language.getInstance();

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        frostSettings.addUpdater(this);

        enableEvents(AWTEvent.WINDOW_EVENT_MASK);

        setIconImage(frameIconDefault.getImage());
        setResizable(true);

        setTitle(title);

        // we don't want all of our tooltips to hide after 4 seconds, they should be shown forever
        ToolTipManager.sharedInstance().setDismissDelay(Integer.MAX_VALUE);

        addWindowListener(new WindowClosingListener());
        addWindowStateListener(new WindowStateListener());
    }

    public static MainFrame getInstance() {
        return instance;
    }

    public void addPanel(final String title, final JPanel panel) {
        getTabbedPane().add(title, panel);
    }

    /**
     * This method adds a menu item to one of the menus of the menu bar of the frame.
     * It will insert it into an existing menu or into a new one. It will insert it
     * into an existing block or into a new one (where a block is a group of menu items
     * delimited by separators) at the given position.
     * If the position number exceeds the number of items in that block, the item is
     * added at the end of that block.
     * @param item the menu item to add
     * @param menuNameKey the text (as a language key) of the menu to insert the item into.
     *          If there is no menu with that text, a new one will be created at the end
     *          of the menu bar and the item will be put inside.
     * @param block the number of the block to insert the item into. If newBlock is true
     *          we will create a new block at that position. If it is false, we will use
     *          the existing one. If the block number exceeds the number of blocks in the
     *          menu, a new block is created at the end of the menu and the item is
     *          inserted there, no matter what the value of the newBlock parameter is.
     * @param position the position inside the block to insert the item at. If the position
     *          number exceeds the number of items in the block, the item is added at the
     *          end of the block.
     * @param newBlock true to insert the item in a new block. False to use an existing one.
     */
    public void addMenuItem(final JMenuItem item, final String menuNameKey, final int block, final int position, final boolean newBlock) {
        final String menuName = language.getString(menuNameKey);
        int index = 0;
        JMenu menu = null;
        while ((index < getMainMenuBar().getMenuCount()) &&
                (menu == null)) {
            final JMenu aMenu = getMainMenuBar().getMenu(index);
            if ((aMenu != null) &&
                (menuName.equals(aMenu.getText()))) {
                menu = aMenu;
            }
            index++;
        }
        if (menu == null) {
            //There isn't any menu with that name, so we create a new one.
            menu = new JMenu(menuName);
            getMainMenuBar().add(menu);
            menu.add(item);
            return;
        }
        index = 0;
        int blockCount = 0;
        while ((index < menu.getItemCount()) &&
               (blockCount < block)) {
            final Component component = menu.getItem(index);
            if (component == null) {
                blockCount++;
            }
            index++;
        }
        if (blockCount < block) {
            // Block number exceeds the number of blocks in the menu or newBlock is true.
            menu.addSeparator();
            menu.add(item);
            return;
        }
        if (newBlock) {
            // New block created and item put in there.
            menu.insertSeparator(index);
            menu.insert(item, index);
            return;
        }
        int posCount = 0;
        Component component = menu.getItem(index);
        while ((index < menu.getComponentCount()) &&
               (component != null) &&
               (posCount < position)) {
                index++;
                posCount++;
                component = menu.getItem(index);
        }
        menu.add(item, index);
    }

    private JTabbedPane getTabbedPane() {
        if (tabbedPane == null) {
            tabbedPane = new JTranslatableTabbedPane(language);
        }
        return tabbedPane;
    }

    /**
     * Selects the named top-bar tab in the main Frost window.
     * @param title - The name of the tab; should be given in the following internal name format:
     * "MainFrame.tabbedPane.news", "MainFrame.tabbedPane.sharing", etc
     */
    public void selectTabbedPaneTab(final String title) {
        final int position = getTabbedPane().indexOfTab(title);
        if (position != -1) {
            getTabbedPane().setSelectedIndex(position);
        }
    }

    public void setDisconnected() {
        getFrostMessageTab().setDisconnected();
    }
    public void setConnected() {
        getFrostMessageTab().setConnected();
    }

    /**
     * Build the menu bar.
     */
    private JMenuBar getMainMenuBar() {
        if (menuBar == null) {
            menuBar = new JMenuBar();

            final JMenu lookAndFeelMenu = getLookAndFeelMenu();

            tofAutomaticUpdateMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/mail-send-receive.png", 16, 16));

            fileExitMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/system-shutdown.png", 16, 16));
            fileOpenDownloadDirMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/folder-open.png", 16, 16));
            fileStatisticsMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/x-office-presentation.png", 16, 16));
            lookAndFeelMenu.setIcon(MiscToolkit.getScaledImage("/data/toolbar/preferences-desktop-theme.png", 16, 16));
            optionsManageIdentitiesMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/group.png", 16, 16));
            optionsManageTrackedDownloadsMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/document-properties.png", 16, 16));
            optionsManageLocalIdentitiesMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/user.png", 16, 16));
            optionsPreferencesMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/preferences-system.png", 16, 16));
            helpAboutMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/about-icon.png", 16, 16));
            pluginTranslateMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/arrow_switch.png", 16, 16));

            // add action listener
            fileExitMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    fileExitMenuItem_actionPerformed();
                }
            });
            fileOpenDownloadDirMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    fileOpenDownloadDirMenuItem_actionPerformed(e);
                }
            });
            fileStatisticsMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    fileStatisticsMenuItem_actionPerformed(e);
                }
            });
            optionsPreferencesMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    optionsPreferencesMenuItem_actionPerformed();
                }
            });
            optionsManageLocalIdentitiesMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    optionsManageLocalIdentitiesMenuItem_actionPerformed(e);
                }
            });
            optionsManageIdentitiesMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    optionsManageIdentitiesMenuItem_actionPerformed(e);
                }
            });
            optionsManageTrackedDownloadsMenuItem.addActionListener(new ActionListener() {
            	public void actionPerformed(final ActionEvent e) {
            		optionsManageTrackedDownloadsMenuItem_actionPerformed(e);
            	}
            });
            pluginTranslateMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                	getTranslationDialog().setVisible(true);
                }
            });
            helpHelpMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/help-browser.png", 16, 16));
            helpHelpMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    showHtmlHelp("index.html");
                    //HelpFrame dlg = new HelpFrame(MainFrame.this);
                    //dlg.setVisible(true);
                }
            });

            if( Core.isHelpHtmlSecure() == false ) {
                helpHelpMenuItem.setEnabled(false);
            }

            helpAboutMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    helpAboutMenuItem_actionPerformed();
                }
            });

            helpMemMonMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/utilities-system-monitor.png", 16, 16));
            helpMemMonMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    getMemoryMonitor().showDialog();
                }
            });

            helpKeyConversionUtilityMenuItem.setIcon(MiscToolkit.getScaledImage("/data/toolbar/key-utility.png", 16, 16));
            helpKeyConversionUtilityMenuItem.addActionListener(new ActionListener() {
                public void actionPerformed(final ActionEvent e) {
                    // create a new dialog every time, thus letting the user open multiple ones
                    (new KeyConversionUtility(MainFrame.this)).startDialog();
                }
            });

            // construct menu

            // File Menu
            if( DesktopUtils.canPerformOpen() ) {
                fileMenu.add(fileOpenDownloadDirMenuItem);
            }
            fileMenu.add(fileStatisticsMenuItem);
            fileMenu.addSeparator();
            fileMenu.add(fileExitMenuItem);
            // News Menu
            tofMenu.add(tofAutomaticUpdateMenuItem);
            // Options Menu
            optionsMenu.add(optionsManageLocalIdentitiesMenuItem);
            optionsMenu.add(optionsManageIdentitiesMenuItem);
            optionsMenu.add(optionsManageTrackedDownloadsMenuItem);
            optionsMenu.addSeparator();
            optionsMenu.add( lookAndFeelMenu );
            optionsMenu.addSeparator();
            optionsMenu.add(optionsPreferencesMenuItem);
            // Plugin Menu
            pluginMenu.add(pluginTranslateMenuItem);
            // Language Menu
            LanguageGuiSupport.getInstance().buildInitialLanguageMenu(languageMenu);
            // Help Menu
            helpMenu.add(helpMemMonMenuItem);
            helpMenu.add(helpKeyConversionUtilityMenuItem);
            helpMenu.add(helpHelpMenuItem);
            helpMenu.addSeparator();
            helpMenu.add(helpAboutMenuItem);

            // add all to bar
            menuBar.add(fileMenu);
            menuBar.add(tofMenu);
            menuBar.add(optionsMenu);
            menuBar.add(pluginMenu);
            menuBar.add(languageMenu);
            menuBar.add(helpMenu);

            // add time label
            menuBar.add(Box.createHorizontalGlue());
            menuBar.add(timeLabel);
            menuBar.add(Box.createRigidArea(new Dimension(3,3)));

            translateMainMenu();

            language.addLanguageListener(this);
        }
        return menuBar;
    }

    private JMenu getLookAndFeelMenu() {
        // init look and feel menu
        final UIManager.LookAndFeelInfo[] info = UIManager.getInstalledLookAndFeels();
        final JMenu lfMenu = new JMenu("Look and feel");

        final ButtonGroup group = new ButtonGroup();

        final ActionListener al = new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final String lfName = e.getActionCommand();
                try {
                    UIManager.setLookAndFeel(lfName);
                    updateComponentTreesUI();
                } catch(final Throwable t) {
                    logger.log(Level.SEVERE, "Exception changing l&f", t);
                }
            }
        };

        for( final LookAndFeelInfo element : info ) {
            final String lfClassName = element.getClassName();
            try {
                final LookAndFeel laf = (LookAndFeel) Class.forName(lfClassName).newInstance();
                if (laf.isSupportedLookAndFeel()) {
                    final JRadioButtonMenuItem rmItem = new JRadioButtonMenuItem(laf.getName()+"  ["+lfClassName+"]");
                    rmItem.setActionCommand(lfClassName);
                    rmItem.setSelected(UIManager.getLookAndFeel().getClass().getName().equals(lfClassName));
                    group.add(rmItem);
                    rmItem.addActionListener(al);
                    lfMenu.add(rmItem);
                    lookAndFeels.add(rmItem);
                }
            }
            catch(final Throwable t) {
                logger.log(Level.SEVERE, "Exception adding l&f menu", t);
            }
        }
        return lfMenu;
    }

    private MainFrameStatusBar getStatusBar() {
        if( statusBar == null ) {
            statusBar = new MainFrameStatusBar();
        }
        return statusBar;
    }

    private JTabbedPane buildMainPanel() {

        getFrostMessageTab().initialize();
        getTabbedPane().insertTab("MainFrame.tabbedPane.news", null, getFrostMessageTab().getTabPanel(), null, 0);

        // optionally show Freetalk tab
        if (frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_TAB)) {
            getFreetalkMessageTab().initialize();
            getTabbedPane().insertTab("MainFrame.tabbedPane.freetalk", null, getFreetalkMessageTab().getTabPanel(), null, 1);
        }

        getTabbedPane().setSelectedIndex(0);

        return getTabbedPane();
    }

    /**
     * save size,location and state of window
     * let save message panel layouts
     */
    public void saveLayout() {
        final Rectangle bounds = getBounds();
        final boolean isMaximized = ((getExtendedState() & Frame.MAXIMIZED_BOTH) != 0);

        frostSettings.setValue(SettingsClass.MAINFRAME_LAST_MAXIMIZED, isMaximized);

        if (!isMaximized) { // Only save the current dimension if frame is not maximized
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_HEIGHT, bounds.height);
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_WIDTH, bounds.width);
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_X, bounds.x);
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_Y, bounds.y);
        }

        for( final JRadioButtonMenuItem rbmi : lookAndFeels ) {
            if( rbmi.isSelected() ) {
                frostSettings.setValue(SettingsClass.LOOK_AND_FEEL, rbmi.getActionCommand());
            }
        }

        getFrostMessageTab().saveLayout();
        if (frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_TAB)) {
            getFreetalkMessageTab().saveLayout();
        }
    }

    /**
     * File | Exit action performed
     */
    public void fileExitMenuItem_actionPerformed() {

        // warn if create message windows are open
        if (MessageFrame.getOpenInstanceCount() > 0 || FreetalkMessageFrame.getOpenInstanceCount() > 0) {
            final int answer = MiscToolkit.showConfirmDialog(
                    this,
                    language.getString("MainFrame.openCreateMessageWindows.body"),
                    language.getString("MainFrame.openCreateMessageWindows.title"),
                    MiscToolkit.YES_NO_OPTION,
                    MiscToolkit.QUESTION_MESSAGE);
            if( answer != MiscToolkit.YES_OPTION ) {
                return;
            }
        }

        // warn if messages are currently uploading
        if (UnsentMessagesManager.getRunningMessageUploads() > 0 ) {
            final int answer = MiscToolkit.showConfirmDialog(
                    this,
                    language.getString("MainFrame.runningUploadsWarning.body"),
                    language.getString("MainFrame.runningUploadsWarning.title"),
                    MiscToolkit.YES_NO_OPTION,
                    MiscToolkit.QUESTION_MESSAGE);
            if( answer != MiscToolkit.YES_OPTION ) {
                return;
            }
        }

        saveLayout();

        System.exit(0);
    }

    /**
     * File | OpenDownloadDir action performed
     */
    private void fileOpenDownloadDirMenuItem_actionPerformed(final ActionEvent evt) {
        // attempt to open the user's default download dir (the one from the preferences;
        // not from the download panel's textfield, although they're usually identical
        // since the latter textfield is set to the default download dir at every startup).
        final File thisDir = new File(FrostDownloadItem.getDefaultDownloadDir());
        boolean success = false;
        if( thisDir.isDirectory() ) {
            success = DesktopUtils.openDirectory(thisDir);
        }

        // directory didn't exist or somehow failed to open?
        if( !success ) {
            MiscToolkit.showMessageDialog( // borrow error-translation from downloadpane
                    this,
                    language.formatMessage("DownloadPane.openDirectoryError.body",
                        thisDir.toString()),
                    language.getString("DownloadPane.openDirectoryError.title"),
                    MiscToolkit.ERROR_MESSAGE);
        }
    }

    /**
     * File | Statistics action performed
     */
    private void fileStatisticsMenuItem_actionPerformed(final ActionEvent evt) {

        activateGlassPane(); // lock gui

        new Thread() {
            @Override
            public void run() {
                try {
                    final int msgCount = MessageStorage.inst().getMessageCount();
                    final int arcMsgCount = ArchiveMessageStorage.inst().getMessageCount();
                    final int idCount = IdentitiesStorage.inst().getIdentityCount();
                    final int fileCount = FileListStorage.inst().getFileCount();
                    final int sharerCount = FileListStorage.inst().getSharerCount();
//                    final long fileSizes = FileListStorage.inst().getFileSizes();
// NOTE: file size computation scans all file list files, takes a long time, disabled for now.
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            deactivateGlassPane();
                            final StatisticsDialog dlg = new StatisticsDialog(MainFrame.this);
//                            dlg.startDialog(msgCount, arcMsgCount, idCount, sharerCount, fileCount, fileSizes);
                            dlg.startDialog(msgCount, arcMsgCount, idCount, sharerCount, fileCount, 0L);
                        }
                    });
                } finally {
                    // paranoia, don't left gui locked
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            deactivateGlassPane();
                        }
                    });
                }
            }
        }.start();
    }

    public MessagePanel getMessagePanel() {
        return getFrostMessageTab().getMessagePanel();
    }

    /**
     * Help | About action performed
     */
    private void helpAboutMenuItem_actionPerformed() {
        final AboutBox dlg = new AboutBox(this);
        dlg.setVisible(true);
    }

    public void postInitialize() {
        getFrostMessageTab().postInitialize();
    }

    public void initialize() {

        // Add components
        final JPanel contentPanel = (JPanel) getContentPane();
        contentPanel.setLayout(new BorderLayout());
        contentPanel.add(buildMainPanel(), BorderLayout.CENTER);
        contentPanel.add(getStatusBar(), BorderLayout.SOUTH);
        setJMenuBar(getMainMenuBar());

        // step through all messages on disk up to maxMessageDisplay and check if there are new messages
        TOF.getInstance().searchAllUnreadMessages(false);

        tofAutomaticUpdateMenuItem.setSelected(frostSettings.getBoolValue(SettingsClass.BOARD_AUTOUPDATE_ENABLED));

        // make sure the font size isn't too small to see
        if (frostSettings.getIntValue(SettingsClass.MESSAGE_BODY_FONT_SIZE) < 6) {
            frostSettings.setValue(SettingsClass.MESSAGE_BODY_FONT_SIZE, 6);
        }

        // load size, location and state of window
        int lastHeight = frostSettings.getIntValue(SettingsClass.MAINFRAME_LAST_HEIGHT);
        int lastWidth = frostSettings.getIntValue(SettingsClass.MAINFRAME_LAST_WIDTH);
        int lastPosX = frostSettings.getIntValue(SettingsClass.MAINFRAME_LAST_X);
        int lastPosY = frostSettings.getIntValue(SettingsClass.MAINFRAME_LAST_Y);
        final boolean lastMaximized = frostSettings.getBoolValue(SettingsClass.MAINFRAME_LAST_MAXIMIZED);

        // revert to an acceptable size if the window has been made too small during the previous
        // run (this will be overridden with the screen ratio if first run)
        if (lastWidth < 200) {
            lastWidth = 900;
        }
        if (lastHeight < 200) {
            lastHeight = 600;
        }

        // we'll center the window on the default (usually leftmost) monitor if this is the first
        // run (x and y are both -1) and resize it to a ratio of that screen's size (but clamped
        // so that it's never too big)
        if (lastPosX < 0 && lastPosY < 0) {
            final Rectangle gcfBounds = MiscToolkit.getSafeScreenRectangle( MiscToolkit.getDefaultScreen() );
            // initialize the window size to a ratio of the screen (so a 16:10 screen will see a 16:10 window)
            int newPotentialWidth = (int)Math.floor( gcfBounds.width * 0.7 ); // 70% of the screen width
            int newPotentialHeight = (int)Math.floor( gcfBounds.height * 0.7 ); // 70% of the screen height
            if( newPotentialWidth > 1300 ){ newPotentialWidth = 1300; } // make sure the values are not too big if the user has a massive screen
            if( newPotentialHeight > 820 ){ newPotentialHeight = 820; }
            lastWidth = newPotentialWidth;
            lastHeight = newPotentialHeight;
            // center the window
            lastPosX = gcfBounds.x + ( (gcfBounds.width - lastWidth) / 2 );
            lastPosY = gcfBounds.y + ( (gcfBounds.height - lastHeight) / 2 );
        }

        // make sure that no coordinates are lower than the virtual screen space (so if the
        // window has been pushed past the top or left boundary, it'll be returned to the
        // leftmost or topmost position as required)
        if (lastPosX < 0) {
            lastPosX = 0;
        }
        if (lastPosY < 0) {
            lastPosY = 0;
        }

        setBounds(lastPosX, lastPosY, lastWidth, lastHeight);

        if (lastMaximized) {
            setExtendedState(getExtendedState() | Frame.MAXIMIZED_BOTH);
        }

        validate();
    }

    /**
     * Start the ticker that makes the mainframe waggle and starts board updates.
     */
    public void startTickerThread() {
        final Thread tickerThread = new Thread("tick tack") {
            @Override
            public void run() {
                while (true) {
                    Mixed.wait(1000);
                    // TODO: refactor this method in Core. lots of work :)
                    timer_actionPerformed();
                }
            }
        };
        tickerThread.start();
    }

    /**
     * Options | Preferences action performed
     */
    private OptionsFrame optionsDlg = null;
    private void optionsPreferencesMenuItem_actionPerformed() {
        try {
            frostSettings.exitSave();
        } catch (final StorageException se) {
            logger.log(Level.SEVERE, "Error while saving the settings.", se);
        }

        optionsDlg = new OptionsFrame(this, frostSettings);
        final boolean okPressed = optionsDlg.runDialog();
        if (okPressed) {
            // check if any of the message-hiding Preferences were changed by the user
            if (optionsDlg.shouldReloadMessages()) {
                // update the new msg. count for all boards
                TOF.getInstance().searchAllUnreadMessages(true);
                // reload all messages
                tofTree_actionPerformed(null);
            }
            if( optionsDlg.shouldResetLastBackloadUpdateFinishedMillis() ) {
                // reset lastBackloadUpdatedMillis for all boards
                getFrostMessageTab().getTofTreeModel().resetLastBackloadUpdateFinishedMillis();
            }
            if( optionsDlg.shouldResetSharedFilesLastDownloaded() ) {
                // reset lastDownloaded of all shared files
                final Thread t = new Thread() {
                    @Override
                    public void run() {
                        try {
                            FileListStorage.inst().resetLastDownloaded();
                        } catch(final Throwable tt) {
                            logger.log(Level.SEVERE, "Exception during resetLastDownloaded", tt);
                        }
                    }
                };
                t.start();
            }

            // repaint whole tree, in case the update visualization was enabled or disabled (or others)
            getFrostMessageTab().getTofTree().updateTree();
        }
    }

    public OptionsFrame getVisibleOptionsFrame()
    {
        if( optionsDlg != null && optionsDlg.isVisible() )
            return optionsDlg;
        return null;
    }

    private void optionsManageLocalIdentitiesMenuItem_actionPerformed(final ActionEvent e) {
        final ManageLocalIdentitiesDialog dlg = new ManageLocalIdentitiesDialog();
        dlg.setVisible(true); // modal
        if( dlg.isIdentitiesImported() ) {
            // identities were imported, reload message table to show 'ME' for imported local identities
            tofTree_actionPerformed(null);
        }
    }

    private void optionsManageIdentitiesMenuItem_actionPerformed(final ActionEvent e) {
        new IdentitiesBrowser(this).startDialog();
    }

    private void optionsManageTrackedDownloadsMenuItem_actionPerformed(final ActionEvent e) {
		getManageTrackedDownloads().showDialog();
    }

    /**
     * Refresh the texts in MainFrame with new language.
     */
    public void languageChanged(final LanguageEvent e) {
        translateMainMenu();
        LanguageGuiSupport.getInstance().translateLanguageMenu();
    }

    public void setPanelEnabled(final String title, final boolean enabled) {
        final int position = getTabbedPane().indexOfTab(title);
        if (position != -1) {
            getTabbedPane().setEnabledAt(position, enabled);
        }
    }

    public void setTofTree(final TofTree tofTree) {
        getFrostMessageTab().setTofTree(tofTree);
    }

    public void setTofTreeModel(final TofTreeModel tofTreeModel) {
        getFrostMessageTab().setTofTreeModel(tofTreeModel);
    }

    /**
     * timer Action Listener (automatic download), gui updates
     */
    public void timer_actionPerformed() {
        // this method is called by a timer each second, so this counter counts seconds
        counter++;

        final RunningMessageThreadsInformation msgInfo = getFrostMessageTab().getRunningMessageThreadsInformation();
        final FileTransferInformation fileInfo = Core.getInstance().getFileTransferManager().getFileTransferInformation();

        //////////////////////////////////////////////////
        //   Automatic TOF update
        //////////////////////////////////////////////////
        if (Core.isFreenetOnline() &&
            counter % 15 == 0 && // check all 15 seconds if a board update could be started
            isAutomaticBoardUpdateEnabled() &&
            msgInfo.getDownloadingBoardCount() < frostSettings.getIntValue(SettingsClass.BOARD_AUTOUPDATE_CONCURRENT_UPDATES))
        {
            getFrostMessageTab().startNextBoardUpdate();
        }

        //////////////////////////////////////////////////
        //   Display time in button bar
        //////////////////////////////////////////////////
        final DateTime now = new DateTime(DateTimeZone.UTC);

        // check all 60 seconds if the day changed
        if( getTodaysDateMillis() == 0 || (counter % 60) == 0 ) {
            final long millis = now.withTimeAtStartOfDay().getMillis();
            if( getTodaysDateMillis() != millis ) {
                setTodaysDateMillis(millis);
            }
        }

        timeLabel.setText(
            new StringBuilder()
                .append(DateFun.FORMAT_DATE_VISIBLE.print(now))
                .append(" - ")
                .append(DateFun.FORMAT_TIME_VISIBLE.print(now))
                .toString());

        /////////////////////////////////////////////////
        //   Update status bar and file count in panels
        /////////////////////////////////////////////////
        //SF_EDIT
        AbstractNode selectedNode = getFrostMessageTab().getTofTreeModel().getSelectedNode();
        getStatusBar().setStatusBarInformations(fileInfo, msgInfo, selectedNode);
		//END_EDIT

        Core.getInstance().getFileTransferManager().updateWaitingCountInPanels(fileInfo);
    }

    public long getTodaysDateMillis() {
        return todaysDateMillis;
    }

    private void setTodaysDateMillis(final long v) {
        todaysDateMillis = v;
    }

    /** TOF Board selected
     * Core.getOut()
     * if e == NULL, the method is called by truster or by the reloader after options were changed
     * in this cases we usually should left select the actual message (if one) while reloading the table
     * @param e
     */
    public void tofTree_actionPerformed(final TreeSelectionEvent e) {
        getFrostMessageTab().boardTree_actionPerformed();
    }

    public void tofTree_actionPerformed(final TreeSelectionEvent e, final boolean reselectCurrentMessageFallback) {
        getFrostMessageTab().boardTree_actionPerformed(reselectCurrentMessageFallback);
    }

    private void translateMainMenu() {
        fileMenu.setText(language.getString("MainFrame.menu.file"));
        fileExitMenuItem.setText(language.getString("Common.exit"));
        fileOpenDownloadDirMenuItem.setText(language.getString("MainFrame.menu.file.openDownloadDir"));
        fileStatisticsMenuItem.setText(language.getString("MainFrame.menu.file.statistics"));
        tofMenu.setText(language.getString("MainFrame.menu.news"));
        tofAutomaticUpdateMenuItem.setText(language.getString("MainFrame.menu.news.automaticBoardUpdate"));
        optionsMenu.setText(language.getString("MainFrame.menu.options"));
        optionsPreferencesMenuItem.setText(language.getString("MainFrame.menu.options.preferences"));
        optionsManageLocalIdentitiesMenuItem.setText(language.getString("MainFrame.menu.options.manageLocalIdentities"));
        optionsManageIdentitiesMenuItem.setText(language.getString("MainFrame.menu.options.manageIdentities"));
        optionsManageTrackedDownloadsMenuItem.setText(language.getString("MainFrame.menu.options.manageTrackedDownloads"));
        pluginMenu.setText(language.getString("MainFrame.menu.plugins"));
        pluginTranslateMenuItem.setText(language.getString("MainFrame.menu.plugins.translateFrost"));
        languageMenu.setText(language.getString("MainFrame.menu.language"));
        helpMenu.setText(language.getString("MainFrame.menu.help"));
        helpMemMonMenuItem.setText(language.getString("MainFrame.menu.help.showMemoryMonitor"));
        helpKeyConversionUtilityMenuItem.setText(language.getString("MainFrame.menu.help.showKeyConversionUtility"));
        helpHelpMenuItem.setText(language.getString("MainFrame.menu.help.help"));
        helpAboutMenuItem.setText(language.getString("MainFrame.menu.help.aboutFrost"));
    }

    public void updateSettings() {
        frostSettings.setValue(SettingsClass.BOARD_AUTOUPDATE_ENABLED, tofAutomaticUpdateMenuItem.isSelected());
    }

    /**
     * Selects message icon in lower right corner
     */
    public void displayNewMessageIcon(final boolean showNewMessageIcon) {

        getStatusBar().showNewMessageIcon(showNewMessageIcon);

        if( SystraySupport.isInitialized() ) {
            if( showNewMessageIcon ) {
                SystraySupport.setIconNewMessage();
            } else {
                SystraySupport.setIconNormal();
            }
        }

        /* NOTE:XXX: commented out so that we no longer show a super ugly 24x24 pixel gif as the application
         * icon when there are new messages; the tray icon does a much cleaner job of showing new
         * messages, *and* most importantly, the "setIconImage" call can only be done once on Linux,
         * so the application icon couldn't update/switch anyway and just got stuck as the wrong one.
        final ImageIcon iconToSet;
        if (showNewMessageIcon) {
            iconToSet = frameIconNewMessage;
        } else {
            iconToSet = frameIconDefault;
        }
        setIconImage(iconToSet.getImage());
        */
    }

    /**
     * Fires a nodeChanged (redraw) for this board and updates buttons.
     */
    public void updateTofTree(final AbstractNode board) {
        getFrostMessageTab().updateTofTreeNode(board);
    }

    public void setAutomaticBoardUpdateEnabled(final boolean state) {
        tofAutomaticUpdateMenuItem.setSelected(state);
    }

    public boolean isAutomaticBoardUpdateEnabled() {
        return tofAutomaticUpdateMenuItem.isSelected();
    }

    private ManageTrackedDownloads getManageTrackedDownloads() {
        if( manageTrackedDownloads == null ) {
            manageTrackedDownloads = new ManageTrackedDownloads();
        }
        return manageTrackedDownloads;
    }

    private MemoryMonitor getMemoryMonitor() {
        if( memoryMonitor == null ) {
            memoryMonitor = new MemoryMonitor();
        }
        return memoryMonitor;
    }

    private TranslationStartDialog getTranslationDialog () {
        return new TranslationStartDialog(this);
    }

    public void showHtmlHelp(final String item) {
        if( Core.isHelpHtmlSecure() == false ) {
            return;
        }
        if( helpBrowser == null ) {
            helpBrowser = new HelpBrowserFrame(frostSettings.getValue(SettingsClass.LANGUAGE_LOCALE), Mixed.getFrostDirFile("help/help.zip", true));
        }
        // show first time or bring to front
        helpBrowser.setVisible(true);
        helpBrowser.showHelpPage(item);
    }

    /**
     * Start the search dialog with only the specified boards preselected as boards to search into.
     */
    public void startSearchMessagesDialog(final List<Board> l) {
        // show first time or bring to front
        getFrostMessageTab().getSearchMessagesDialog().startDialog(l);
    }

    public void updateMessageCountLabels(final Board board) {
        // forward to MessagePanel
        getFrostMessageTab().getMessagePanel().updateMessageCountLabels(board);
    }
    public TreeTableModelAdapter getMessageTableModel() {
        // forward to MessagePanel
        return getFrostMessageTab().getMessagePanel().getMessageTableModel();
    }
    public DefaultTreeModel getMessageTreeModel() {
        // forward to MessagePanel
        return getFrostMessageTab().getMessagePanel().getMessageTreeModel();
    }
    public MessageTreeTable getMessageTreeTable() {
        // forward to MessagePanel
        return getFrostMessageTab().getMessagePanel().getMessageTable();
    }

    private class WindowClosingListener extends WindowAdapter {
        @Override
        public void windowClosing(final WindowEvent e) {
            fileExitMenuItem_actionPerformed();
        }
    }

    private class WindowStateListener extends WindowAdapter {
        @Override
        public void windowStateChanged(final WindowEvent e) {
            if( (e.getNewState() & Frame.ICONIFIED) != 0 ) {
                // Frost window was minimized by user, minimize to tray if configured
                if ( Core.frostSettings.getBoolValue(SettingsClass.MINIMIZE_TO_SYSTRAY)
                        && SystraySupport.isInitialized())
                {
                    final boolean wasMaximized = ((e.getOldState() & Frame.MAXIMIZED_BOTH) != 0);
                    
                    // frame is minimized right now, de-minimize so it shows up next time
                    if (!wasMaximized) {
                        setExtendedState(Frame.NORMAL);
                    } else {
                        setExtendedState(Frame.MAXIMIZED_BOTH);
                    }
                    
                    SystraySupport.minimizeToTray();
                }
            }
        }
    }

    public void activateGlassPane() {
        getFrostMessageTab().showProgress();

        // Mount the glasspane on the component window
        final GlassPane aPane = GlassPane.mount(this, true);

        // keep track of the glasspane as an instance variable
        glassPane = aPane;

        if (glassPane != null) {
            // Start interception UI interactions
            glassPane.setVisible(true);
        }
    }

    public void deactivateGlassPane() {
        if (glassPane != null) {
            // Stop UI interception
            glassPane.setVisible(false);
            glassPane = null;
        }
        getFrostMessageTab().hideProgress();
    }

    /**
     *  Updates the component tree UI of all the frames and dialogs of the application
     */
    public void updateComponentTreesUI() {
        final Frame[] appFrames = Frame.getFrames();
        final JSkinnablePopupMenu[] appPopups = JSkinnablePopupMenu.getSkinnablePopupMenus();
        for( final Frame element : appFrames ) { //Loop to update all the frames
            SwingUtilities.updateComponentTreeUI(element);
            final Window[] ownedWindows = element.getOwnedWindows();
            for( final Window element2 : ownedWindows ) { //Loop to update the dialogs
                if (element2 instanceof Dialog) {
                    SwingUtilities.updateComponentTreeUI(element2);
                }
            }
        }
        for( final JSkinnablePopupMenu element : appPopups ) { //Loop to update all the popups
            SwingUtilities.updateComponentTreeUI(element);
        }
        // the panels are not all in the component tree, update them manually
        SwingUtilities.updateComponentTreeUI(getMessagePanel());
        SwingUtilities.updateComponentTreeUI(getFrostMessageTab().getSentMessagesPanel());
        SwingUtilities.updateComponentTreeUI(getFrostMessageTab().getUnsentMessagesPanel());
        repaint();
    }

    public FrostMessageTab getFrostMessageTab() {
        return frostMessageTab;
    }

    public FreetalkMessageTab getFreetalkMessageTab() {
        return freetalkMessageTab;
    }

    public MessageColorManager getMessageColorManager()
    {
        return messageColorManager;
    }

    /**
     * Takes care of keeping track of all message colors and fires a single update event when
     * all of them change.
     */
    public class MessageColorManager
            implements PropertyChangeListener
    {
        private final PropertyChangeSupport fChangeSupport;
        private SingleTaskWorker fDelayedChangeAnnouncer;

        private Color fMsgNormalColor;
        private Color fMsgPrivColor;
        private Color fMsgWithAttachmentsColor;
        private Color fMsgUnsignedColor;

        public MessageColorManager()
        {
            // sets up the ability for us to announce changes to our listeners
            fChangeSupport = new PropertyChangeSupport(this);
            fDelayedChangeAnnouncer = new SingleTaskWorker();

            // we want to listen to the core settings for changes to the colors we're interested in
            Core.frostSettings.addPropertyChangeListener(SettingsClass.COLORS_MESSAGE_NORMALMSG, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.COLORS_MESSAGE_PRIVMSG, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.COLORS_MESSAGE_WITHATTACHMENTS, this);
            Core.frostSettings.addPropertyChangeListener(SettingsClass.COLORS_MESSAGE_UNSIGNEDMSG, this);

            // read the initial color values from the settings, without announcing any changes to listeners
            updateColors(false);
        }

        // color-getters for our listeners; the color objects are immutable so they can't be modified
        public Color getNormalColor()
        {
            return fMsgNormalColor;
        }
        public Color getPrivColor()
        {
            return fMsgPrivColor;
        }
        public Color getWithAttachmentsColor()
        {
            return fMsgWithAttachmentsColor;
        }
        public Color getUnsignedColor()
        {
            return fMsgUnsignedColor;
        }

        // PropertyChangeListener deals with changes to any of the colors we're listening for
        @Override
        public void propertyChange(
                final PropertyChangeEvent evt)
        {
            if( evt.getPropertyName().equals(SettingsClass.COLORS_MESSAGE_NORMALMSG)
             || evt.getPropertyName().equals(SettingsClass.COLORS_MESSAGE_PRIVMSG)
             || evt.getPropertyName().equals(SettingsClass.COLORS_MESSAGE_WITHATTACHMENTS)
             || evt.getPropertyName().equals(SettingsClass.COLORS_MESSAGE_UNSIGNEDMSG) ) {
                updateColors(true); // announce change to listeners
            }
        }

        // reads the colors from the core settings, and optionally announces to our listeners
        private void updateColors(
                final boolean aAnnounceChange)
        {
            fMsgNormalColor = Core.frostSettings.getColorValue(SettingsClass.COLORS_MESSAGE_NORMALMSG);
            fMsgPrivColor = Core.frostSettings.getColorValue(SettingsClass.COLORS_MESSAGE_PRIVMSG);
            fMsgWithAttachmentsColor = Core.frostSettings.getColorValue(SettingsClass.COLORS_MESSAGE_WITHATTACHMENTS);
            fMsgUnsignedColor = Core.frostSettings.getColorValue(SettingsClass.COLORS_MESSAGE_UNSIGNEDMSG);

            // if we've been asked to announce the change to our listeners, we'll do it on a timer
            // so that it happens 100ms after the latest color property change. this ensures that all
            // observed colors will be updated as a single change-event instead of firing one per color.
            if( aAnnounceChange ) {
                fDelayedChangeAnnouncer.schedule(100, new Runnable() {
                    @Override
                    public void run() {
                        // this is a dummy event which says that the old value is false and the new is true,
                        // which is necessary in order to trigger a change. the important part is that
                        // people listen for the "MessageColorsChanged" property event.
                        fChangeSupport.firePropertyChange("MessageColorsChanged", false, true);
                    }
                });
            }
        }

        /*
         * Simply register a listener to be notified when one or more of the colors have changed.
         * The property change event is called "MessageColorsChanged".
         * WARNING: Your listeners may not be getting the event on the GUI thread, so always invokeLater any GUI changes!
         */
        public synchronized void addPropertyChangeListener(
                final PropertyChangeListener aListener)
        {
            if( aListener == null ) { return; }
            fChangeSupport.addPropertyChangeListener(aListener);
        }

        public synchronized void removePropertyChangeListener(
                final PropertyChangeListener aListener)
        {
            if( aListener == null ) { return; }
            fChangeSupport.removePropertyChangeListener(aListener);
        }

        public synchronized PropertyChangeListener[] getPropertyChangeListeners()
        {
            return fChangeSupport.getPropertyChangeListeners();
        }
    }
}
