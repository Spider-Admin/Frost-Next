/*
  Core.java / Frost
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
package frost;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.Timer;
import java.util.logging.*;

import javax.swing.*;

import frost.fcp.*;
import frost.fcp.fcp07.*;
import frost.fileTransfer.*;
import frost.fileTransfer.download.HashBlocklistManager;
import frost.fileTransfer.FreenetCompatibilityManager;
import frost.gui.*;
import frost.gui.help.*;
import frost.identities.*;
import frost.messaging.freetalk.*;
import frost.messaging.frost.*;
import frost.messaging.frost.boards.*;
import frost.messaging.frost.threads.*;
import frost.storage.*;
import frost.storage.perst.*;
import frost.storage.perst.filelist.*;
import frost.storage.perst.identities.*;
import frost.storage.perst.messagearchive.*;
import frost.storage.perst.messages.*;
import frost.util.*;
import frost.util.PathRelativizer;
import frost.util.Logging;
import frost.util.gui.*;
import frost.util.gui.translation.*;

/**
 * Class hold the more non-gui parts of Frost.
 * @pattern Singleton
 * @version $Id$
 */
public class Core {

    private static final Logger logger = Logger.getLogger(Core.class.getName());

    // Core instanciates itself, frostSettings must be created before instance=Core() !
    public static final SettingsClass frostSettings = new SettingsClass();

    // we need a single, global path relativizer to hold all calculated paths for this session
    public static final PathRelativizer pathRelativizer = new PathRelativizer();

    private static java.util.List<StartupMessage> queuedStartupMessages = new LinkedList<StartupMessage>();

    private static Core instance = null;

    private static final FrostCrypt crypto = new FrostCrypt();

    private static boolean isHelpHtmlSecure = false;

    private Language language = null;

    private static boolean freenetIsOnline = false;
    private static boolean freetalkIsTalkable = false;

    private final Timer timer = new Timer(true);

    private MainFrame mainFrame;
    private BoardsManager boardsManager;
    private FileTransferManager fileTransferManager;

    private static FrostIdentities identities;

    private static FreenetCompatibilityManager compatManager = null; // will be init with the known list of user modes during startup

    private Core() {
        initializeLanguage();
    }

    /**
     * This methods parses the list of available nodes (and converts it if it is in
     * the old format). If there are no available nodes, it shows a Dialog warning the
     * user of the situation and returns false.
     * @return boolean false if no nodes are available. True otherwise.
     */
    private boolean initializeConnectivity(final Splashscreen splashscreen) {

        // determine configured freenet version
        final int freenetVersion = frostSettings.getIntValue(SettingsClass.FREENET_VERSION); // only 7 is supported
        if( freenetVersion != 7 ) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    language.getString("Core.init.UnsupportedFreenetVersionBody")+": "+freenetVersion,
                    language.getString("Core.init.UnsupportedFreenetVersionTitle"),
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            return false;
        }

        // get the list of available nodes
        String nodesUnparsed = frostSettings.getValue(SettingsClass.FREENET_FCP_ADDRESS);
        if (nodesUnparsed == null || nodesUnparsed.length() == 0) {
            frostSettings.setValue(SettingsClass.FREENET_FCP_ADDRESS, "127.0.0.1:9481");
            nodesUnparsed = frostSettings.getValue(SettingsClass.FREENET_FCP_ADDRESS);
        }

        final List<String> nodes = new ArrayList<String>();

        // earlier we supported multiple nodes, so check if there is more than one node
        if( nodesUnparsed != null ) {
            final String[] _nodes = nodesUnparsed.split(",");
            for( final String element : _nodes ) {
                nodes.add(element);
            }
        }

        // paranoia, should never happen
        if (nodes.size() == 0) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    "Not a single Freenet node configured. Frost cannot start.",
                    "No Freenet nodes are configured",
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            return false;
        }

        if (nodes.size() > 1) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    "Frost doesn't support multiple Freenet nodes and will use the first configured node.",
                    "Warning: Using first configured node",
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            frostSettings.setValue(SettingsClass.FREENET_FCP_ADDRESS, nodes.get(0));
        }

        // init the factory with configured node
        try {
            FcpHandler.initializeFcp(nodes.get(0));
        } catch(final Exception ex) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    ex.getMessage(),
                    language.getString("Core.init.UnsupportedFreenetVersionTitle"),
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            return false;
        }

        // install our security manager that only allows connections to the configured FCP hosts
        System.setSecurityManager(new FrostSecurityManager());

        // check if node is online and if we run on 0.7 testnet
        setFreenetOnline(false);

        if( Frost.isOfflineMode() ) {
            // keep offline
            return true;
        }

        // We warn the user when he connects to a 0.7 testnet node
        // this also tries to connect to a configured node and sets 'freenetOnline'
        boolean runningOnTestnet = false;
        try {
            final FcpConnection fcpConn = new FcpConnection(FcpHandler.inst().getFreenetNode());
            final NodeMessage nodeMessage = fcpConn.getNodeInfo();

            // node answered, freenet is online
            setFreenetOnline(true);

            if (nodeMessage.getBoolValue("Testnet")) {
                runningOnTestnet = true;
            }

            final boolean freetalkTalkable = fcpConn.checkFreetalkPlugin();
            setFreetalkTalkable (freetalkTalkable);

            if (freetalkTalkable) {
                System.out.println("**** Freetalk is Talkable. ****");
            } else {
                System.out.println("**** Freetalk is NOT Talkable. ****");
            }

            fcpConn.close();

        } catch (final Exception e) {
            logger.log(Level.SEVERE, "Exception thrown in initializeConnectivity", e);
        }

        if (runningOnTestnet) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    language.getString("Core.init.TestnetWarningBody"),
                    language.getString("Core.init.TestnetWarningTitle"),
                    MiscToolkit.WARNING_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
        }

        // We warn the user if there aren't any running nodes
        if (!isFreenetOnline()) {
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    language.getString("Core.init.NodeNotRunningBody"),
                    language.getString("Core.init.NodeNotRunningTitle"),
                    MiscToolkit.WARNING_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
        } else {
            // maybe start a single message connection
            FcpHandler.inst().goneOnline();
        }

        /* //#DIEFILESHARING: This entire block has been commented out since filesharing is removed in Frost-Next.
         * We don't even need to check the flag, since it's been permanently forced to TRUE (disabled)
         * in the frost.ini settings reader, and everything in the GUI is now hardcoded to disable it
         * regardless of the value of this setting.
         * If filesharing ever makes a comeback in the future, then search the source code for all
         * "#DIEFILESHARING" comments and restore the changes noted in those locations.
         * But for now, this setting is dead. Nobody ever used it and it was a huge source of confusion
         * for newbies, and enabling it lead to massive risks of Denial of Service attacks flooding your
         * filesharing database with fake files until Frost crashed. Oh and did I mention: NOBODY USED IT.
         * In fact, if you tried enabling it, Frost screamed a huge warning message at you on every startup.
         * That should give some indication of how awfully broken and ill-thought-out the "feature" was.
         * -- Kitty ;)
        if (!frostSettings.getBoolValue(SettingsClass.FILESHARING_DISABLE)) {
            // FIXME: this dialog type cannot be made "always on top" so it would be shown behind splashscreen.
            MiscToolkit.showSuppressableConfirmDialog(
                    splashscreen,
                    language.getString("Core.init.FileSharingEnabledBody"),
                    language.getString("Core.init.FileSharingEnabledTitle"),
                    MiscToolkit.SUPPRESSABLE_OK_BUTTON, // only show an "Ok" button
                    JOptionPane.WARNING_MESSAGE,
                    SettingsClass.CONFIRM_FILESHARING_IS_ENABLED,
                    language.getString("Common.suppressConfirmationCheckbox") );
        }
        */

        return true;
    }

    public static void setFreenetOnline(final boolean v) {
        freenetIsOnline = v;
    }
    public static boolean isFreenetOnline() {
        return freenetIsOnline;
    }

    public static void setFreetalkTalkable(final boolean v) {
        freetalkIsTalkable = v;
    }
    public static boolean isFreetalkTalkable() {
        return freetalkIsTalkable;
    }

    public static FrostCrypt getCrypto() {
        return crypto;
    }

    public static void schedule(final TimerTask task, final long delay) {
        getInstance().timer.schedule(task, delay);
    }

    public static void schedule(final TimerTask task, final long delay, final long period) {
        getInstance().timer.schedule(task, delay, period);
    }

    /**
     * @return pointer to the live core
     */
    public static Core getInstance() {
        if( instance == null ) {
            instance = new Core();
        }
        return instance;
    }

    private void showFirstStartupDialog() {
        // clean startup, ask user which freenet version to use, set correct default availableNodes
        // NOTE: the "first startup" dialog is unparented but "always on top" so that it shows up above the splash
        final FirstStartupDialog startdlg = new FirstStartupDialog();
        final boolean exitChoosed = startdlg.startDialog();
        if( exitChoosed ) {
            System.exit(1);
        }

        // first startup, no migrate needed
        frostSettings.setValue(SettingsClass.MIGRATE_VERSION, 5);

        // set used version
        final int freenetVersion = 7;
        frostSettings.setValue(SettingsClass.FREENET_VERSION, freenetVersion);
        // init availableNodes with correct port
        if( startdlg.getOwnHostAndPort() != null ) {
            // user set own host:port
            frostSettings.setValue(SettingsClass.FREENET_FCP_ADDRESS, startdlg.getOwnHostAndPort());
        } else {
            // 0.7 darknet
            frostSettings.setValue(SettingsClass.FREENET_FCP_ADDRESS, "127.0.0.1:9481");
        }
    }

    /**
     * Enqueue a message that is shown at the end of the splashscreen, right before the mainframe
     * becomes visible. Alerts the user about problems during loading (e.g. missing files).
     */
    public static void enqueueStartupMessage(final StartupMessage sm) {
        queuedStartupMessages.add( sm );
    }

    /**
     * Show the enqueued messages and finally clear the messages queue.
     * NOTE: Startup-messages are *only* created/queued up by the Perst database initialization,
     * and they deal with things like "An upload table file has vanished since you last ran this
     * application". This function to display the messages is executed a single time at startup
     * (via Core.java), before the main GUI appears. We use the splashscreen as the dialog parent
     * to get the "always on top" dialog box style.
     */
    public void showStartupMessages(final Splashscreen splashscreen) {
        for( final StartupMessage sm : queuedStartupMessages ) {
            sm.display(splashscreen);
        }
        // cleanup
        StartupMessage.cleanup();
        queuedStartupMessages.clear();
        queuedStartupMessages = null;
    }

    private void compactPerstStorages(final Splashscreen splashscreen) throws Exception {
        try {
            long savedBytes = 0;
            savedBytes += compactStorage(splashscreen, IndexSlotsStorage.inst());
            savedBytes += compactStorage(splashscreen, FrostFilesStorage.inst());
            savedBytes += compactStorage(splashscreen, IdentitiesStorage.inst());
            savedBytes += compactStorage(splashscreen, SharedFilesCHKKeyStorage.inst());
            savedBytes += compactStorage(splashscreen, MessageStorage.inst());
            savedBytes += compactStorage(splashscreen, MessageContentStorage.inst());
            savedBytes += compactStorage(splashscreen, FileListStorage.inst());
            savedBytes += compactStorage(splashscreen, ArchiveMessageStorage.inst());

            final NumberFormat nf = NumberFormat.getInstance();
            logger.warning("Finished compact of storages, released "+nf.format(savedBytes)+" bytes.");
        } catch(final Exception ex) {
            logger.log(Level.SEVERE, "Error compacting perst storages", ex);
            ex.printStackTrace();
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    "Error compacting perst storages, compact did not complete: "+ex.getMessage(),
                    "Error compacting perst storages",
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            throw ex;
        }
    }

    private long compactStorage(final Splashscreen splashscreen, final AbstractFrostStorage storage) throws Exception {
        splashscreen.setText("Compacting storage file '"+storage.getStorageFilename()+"'...");
        return storage.compactStorage();
    }

    private void exportStoragesToXml(final Splashscreen splashscreen) throws Exception {
        try {
            exportStorage(splashscreen, IndexSlotsStorage.inst());
            exportStorage(splashscreen, FrostFilesStorage.inst());
            exportStorage(splashscreen, IdentitiesStorage.inst());
            exportStorage(splashscreen, SharedFilesCHKKeyStorage.inst());
            exportStorage(splashscreen, MessageStorage.inst());
            exportStorage(splashscreen, MessageContentStorage.inst());
            exportStorage(splashscreen, FileListStorage.inst());
            exportStorage(splashscreen, ArchiveMessageStorage.inst());
            logger.warning("Finished export to XML");
        } catch(final Exception ex) {
            logger.log(Level.SEVERE, "Error exporting perst storages", ex);
            ex.printStackTrace();
            MiscToolkit.showMessageDialog(
                    splashscreen,
                    "Error exporting perst storages, export did not complete: "+ex.getMessage(),
                    "Error exporting perst storages",
                    MiscToolkit.ERROR_MESSAGE,
                    null,
                    true); // always-on-top just like the splash screen
            throw ex;
        }
    }

    private void exportStorage(final Splashscreen splashscreen, final AbstractFrostStorage storage) throws Exception {
        splashscreen.setText("Exporting storage file '"+storage.getStorageFilename()+"'...");
        storage.exportToXml();
    }

    /**
     * Initialize, show splashscreen.
     */
    public void initialize() throws Exception {

        final Splashscreen splashscreen = new Splashscreen(frostSettings.getBoolValue(SettingsClass.DISABLE_SPLASHSCREEN));
        splashscreen.setVisible(true);

        splashscreen.setText(language.getString("Splashscreen.message.1"));
        splashscreen.setProgress(20);

        //Initializes the logging and skins
        new Logging(frostSettings);

        {
            StringBuilder sb = new StringBuilder();
            sb.append("***** Starting Frost-Next *****\n");
            for( final String s : Frost.getEnvironmentInformation() ) {
                sb.append(s).append("\n");
            }
            logger.severe(sb.toString());
            sb = null;
        }

        // Initializes the Freenet Compatibility Mode Manager using the default set of internal
        // modes, plus any previous list of user-modes from Frost.ini.
        // This happens before Frost's main GUI, before the connection to Freenet, and even before
        // the databases are loaded, thus ensuring early availability.
        compatManager = new FreenetCompatibilityManager(frostSettings, frostSettings.getValue(SettingsClass.FREENETCOMPATMANAGER_USERMODES));

        // CLEANS TEMP DIR! START NO INSERTS BEFORE THIS HAS FINISHED EXECUTING
        Startup.startupCheck(frostSettings);

        // check if they've properly upgraded (they can't just move frost.jar; they must update
        // all libraries, etc). it doesn't matter which package we check, as long as it's one
        // that was updated compared to the old release
        final Package checkPackage = Package.getPackage("org.bouncycastle.crypto");
        final String expectedVersion = "1.52.0"; // current version we're using as of 2015-11
        if( checkPackage == null || !checkPackage.getImplementationVersion().equals(expectedVersion) ) {
            System.out.println("*** User has upgraded incorrectly from an older version of Frost. Exiting...");
            ImageIcon frostIcon = null;
            try {
                frostIcon = MiscToolkit.loadImageIcon("/data/frost.png");
            } catch( final Exception e ) {}
            MiscToolkit.showMessageDialog( // NOTE: this isn't worth making translatable since it'll only hit a few impatient users
                    splashscreen,
                    "Hi there, and welcome to Frost-Next!\n\n" +
                        "It's fun that you're eager to try the new version,\n" +
                        "but you must follow the instructions in UPGRADING.txt\n" +
                        "to correctly upgrade from your previous version.\n\n" +
                        "This program will now exit...",
                    "Please upgrade correctly...",
                    MiscToolkit.ERROR_MESSAGE,
                    frostIcon,
                    true); // always-on-top just like the splash screen
            System.exit(4);
        }

        // if first startup ask user for freenet version to use
        if( frostSettings.getIntValue(SettingsClass.FREENET_VERSION) == 0 ) {
            showFirstStartupDialog();
        }

        // we must be at migration level 2 (no mckoi)!!!
        if( frostSettings.getIntValue(SettingsClass.MIGRATE_VERSION) < 2 ) {
            final String errText = "Error: You must update this Frost version from version 11-Dec-2007 !!!";
            logger.log(Level.SEVERE, errText);
            System.out.println(errText);
            System.exit(8);
        }

        // before opening the storages, maybe compact them
        if( frostSettings.getBoolValue(SettingsClass.PERST_COMPACT_STORAGES) ) {
            compactPerstStorages(splashscreen);
            frostSettings.setValue(SettingsClass.PERST_COMPACT_STORAGES, false);
        }

        // one time: change cleanup settings to new default, they were way to high
        if( frostSettings.getIntValue(SettingsClass.MIGRATE_VERSION) < 3 ) {
            frostSettings.setValue(SettingsClass.DB_CLEANUP_REMOVEOFFLINEFILEWITHKEY, true);
            if (frostSettings.getIntValue(SettingsClass.DB_CLEANUP_OFFLINEFILESMAXDAYSOLD) > 30) {
                frostSettings.setValue(SettingsClass.DB_CLEANUP_OFFLINEFILESMAXDAYSOLD, 30);
            }

            // run cleanup now
            frostSettings.setValue(SettingsClass.DB_CLEANUP_FORCESTART, true);
            // run compact during next startup (after the cleanup)
            frostSettings.setValue(SettingsClass.PERST_COMPACT_STORAGES, true);
            // migration is done
            frostSettings.setValue(SettingsClass.MIGRATE_VERSION, 3);
        }

        // one time: switch some of the previous defaults to the new frost-next defaults to ensure a smooth upgrade experience
        if( frostSettings.getIntValue(SettingsClass.MIGRATE_VERSION) < 5 ) {
            System.out.println("INFO: Migrating your configuration from legacy Frost to Frost-Next.");

            // we now back up the volatile database twice per hour (used to be once per hour). the volatile
            // database is the list of subscribed boards, your transfer queue, and your own identities.
            frostSettings.setValue(SettingsClass.AUTO_SAVE_INTERVAL, "30");

            // messages are now considered expired after 365 days (instead of just 90 days).
            // the expiration feature is disabled by default, so this isn't really used by most people.
            frostSettings.setValue(SettingsClass.MESSAGE_EXPIRE_DAYS, "365");

            // set Frost-Next to display 9999 days of messages; this solves a serious problem.
            // by default, Frost will show any message that is unread regardless of age, but
            // will hide read messages older than the number of days to display. this confuses the
            // users, since they'll read a message, then switch between threaded/non-threaded, which
            // causes a board reload and therefore hides the message since it's now both old AND
            // has been read. that means they lose the message they were reading. so by setting
            // the limit to ~27 years, we never hide any messages. the user can manually go lower
            // if they have a super slow computer and want to improve performance.
            frostSettings.setValue(SettingsClass.MAX_MESSAGE_DISPLAY, "9999");

            // enable "multiline selections in message table" by default now, since almost every Frost
            // user keeps asking about that feature, and it just makes sense to have it enabled by default!
            frostSettings.setValue(SettingsClass.MSGTABLE_MULTILINE_SELECT, true);

            // we now use stricter message counts for removing the "red" and "blue" circle indicators.
            // in the past, they only needed 2 messages to no longer be red, and only 6 to no longer
            // be marked blue either. someone could spam a few messages and no longer have the "new
            // user" circle. these new defaults of 10 and 20 are much more sane.
            frostSettings.setValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_RED, "10");
            frostSettings.setValue(SettingsClass.INDICATE_LOW_RECEIVED_MESSAGES_COUNT_BLUE, "20");

            // to make the "remove already downloaded" feature work in the "add new downloads" table,
            // we want to migrate all users to having download tracking enabled by default
            frostSettings.setValue(SettingsClass.TRACK_DOWNLOADS_ENABLED, true);

            // raise the default message upload priority from 2 -> 0 instead, to make message
            // uploading fast even while the user has lots of ongoing regular uploads
            frostSettings.setValue(SettingsClass.FCP2_DEFAULT_PRIO_MESSAGE_UPLOAD, "0");

            // we no longer auto-accept attached boards from anonymous/unsigned users by default,
            // since they're the least likely to create real boards and the most likely to troll
            frostSettings.setValue(SettingsClass.KNOWNBOARDS_BLOCK_FROM_UNSIGNED, true);

            // default to the most common Freenet node address, so that the "open in browser" feature works
            frostSettings.setValue(SettingsClass.BROWSER_ADDRESS, "http://127.0.0.1:8888/");

            // frost-next uses larger memory buffers for Perst, which greatly improves the
            // database performance; all sizes are in kilobytes
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_FILES,              2048); // Legacy Frost: 512
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_INDEXSLOTS,         1024); // 512
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_SHAREDFILESCHKKEYS, 1024); // 1024
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_TRACKDOWNLOADKEYS,  1024); // Legacy Frost: Did not exist, used "SHAREDFILESCHKKEYS" value
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_FILELIST,           1024); // 1024
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_IDENTITIES,         2048); // 1024
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_MESSAGEARCHIVE,     2048); // 1024
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_MESSAGES,           12288); // 6144 - extremely important for fast board re-opening
            frostSettings.setValue(SettingsClass.PERST_PAGEPOOLSIZE_MESSAGECONTENTS,    4096); // 1024

            // frost-next has brand new, greatly optimized & revised column orders and widths
            // by default - so we'll ignore the user's previous setting!
            // NOTE: why -1? because when the download/upload/msg tables try to load the previous
            // layout (width and position of columns), they abort if any column position is < 0;
            // so we simply set the first column's index to -1 to skip the user's old column layout.
            frostSettings.setValue("DownloadTable.tableindex.modelcolumn.0", -1);
            frostSettings.setValue("UploadTable.tableindex.modelcolumn.0", -1);
            frostSettings.setValue("MessageTreeTable.tableindex.modelcolumn.0", -1); // takes care of both freetalk & frost

            // next, we'll ignore the user's previous main-window size/position/maximize state;
            // that's because frost-next has a new method of calculating the initial size of the
            // window, which uses a ratio of the user's screen resolution and centers the window
            // nicely. so in the interest of the best possible first-launch experience, we'll
            // reset them to the new default window size/pos.
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_X, -1); // if both X and Y are < 0, it triggers the
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_Y, -1); // ...calculation of a new window size at startup
            frostSettings.setValue(SettingsClass.MAINFRAME_LAST_MAXIMIZED, false); // make sure the window isn't maximized

            // lastly, we will ensure that the vertical "board list / message tree" and horizontal
            // "message tree / message contents" splits are reset to their new, wider/taller offsets
            // NOTE: whenever any of these values are < 10, it triggers the default divider offset for that panel
            if( frostSettings.getObjectValue("MainFrame.treeAndTabbedPaneSplitpaneDividerLocation") != null ) {
                frostSettings.setValue("MainFrame.treeAndTabbedPaneSplitpaneDividerLocation", -1);
            }
            if( frostSettings.getObjectValue("FreetalkTab.treeAndTabbedPaneSplitpaneDividerLocation") != null ) {
                frostSettings.setValue("FreetalkTab.treeAndTabbedPaneSplitpaneDividerLocation", -1);
            }
            if( frostSettings.getObjectValue(SettingsClass.MSGTABLE_MSGTEXT_DIVIDER_LOCATION) != null ) {
                frostSettings.setValue(SettingsClass.MSGTABLE_MSGTEXT_DIVIDER_LOCATION, -1);
            }
            if( frostSettings.getObjectValue(SettingsClass.FREETALK_MSGTABLE_MSGTEXT_DIVIDER_LOCATION) != null ) {
                frostSettings.setValue(SettingsClass.FREETALK_MSGTABLE_MSGTEXT_DIVIDER_LOCATION, -1);
            }

            // migration is done
            frostSettings.setValue(SettingsClass.MIGRATE_VERSION, 5);
        }

        // maybe export perst storages to XML
        if( frostSettings.getBoolValue(SettingsClass.PERST_EXPORT_STORAGES) ) {
            exportStoragesToXml(splashscreen);
            frostSettings.setValue(SettingsClass.PERST_EXPORT_STORAGES, false);
        }

        // initialize perst storages
        IndexSlotsStorage.inst().initStorage();
        SharedFilesCHKKeyStorage.inst().initStorage();
        FrostFilesStorage.inst().initStorage();
        MessageStorage.inst().initStorage();
        MessageContentStorage.inst().initStorage();
        ArchiveMessageStorage.inst().initStorage();
        IdentitiesStorage.inst().initStorage();
        FileListStorage.inst().initStorage();
        TrackDownloadKeysStorage.inst().initStorage();
        HashBlocklistManager.getInstance().initStorage();

        splashscreen.setText(language.getString("Splashscreen.message.2"));
        splashscreen.setProgress(40);

        // check if help.zip contains only secure files (no http or ftp links at all)
        {
            final CheckHtmlIntegrity chi = new CheckHtmlIntegrity();
            isHelpHtmlSecure = chi.scanZipFile(Mixed.getFrostDirFile("help/help.zip", true));
        }

        splashscreen.setText(language.getString("Splashscreen.message.3"));
        splashscreen.setProgress(60);

        // sets the freenet version, initializes identities
        if (!initializeConnectivity(splashscreen)) {
            System.exit(1);
        }

        getIdentities().initialize();

        String title = "Frost-Next";

        if( !isFreenetOnline() ) {
            title += " (offline mode)";
        }

        // Main frame
        mainFrame = new MainFrame(frostSettings, title);
        getBoardsManager().initialize();

        getFileTransferManager().initialize();
        UnsentMessagesManager.initialize();

        if (frostSettings.getBoolValue(SettingsClass.FREETALK_SHOW_TAB)) {
            FreetalkManager.initialize();
        }

        splashscreen.setText(language.getString("Splashscreen.message.4"));
        splashscreen.setProgress(70);

        // Display the tray icon (do this before mainframe initializes)
        if (frostSettings.getBoolValue(SettingsClass.SHOW_SYSTRAY_ICON) == true && SystraySupport.isSupported()) {
            try {
                if (!SystraySupport.initialize(title)) {
                    logger.log(Level.SEVERE, "Could not create systray icon.");
                }
            } catch(final Throwable t) {
                logger.log(Level.SEVERE, "Could not create systray icon.", t);
            }
        }

        mainFrame.initialize();

        // cleanup gets the expiration mode from settings, and the interval
        // NOTE: message expiration cleanup only happens at Frost startup, if enough days have passed
        // or if they've checked the "cleanup during next startup" box
        CleanUp.runExpirationTasks(splashscreen, MainFrame.getInstance().getFrostMessageTab().getTofTreeModel().getAllBoards());

        // now that the perst databases and mainframe are initialized, it's safe to import the
        // default "known boards" list (if the user hasn't been asked already)
        if( frostSettings.getBoolValue(SettingsClass.STARTUP_OFFERED_DEFAULT_BOARDS) != true ) {
            splashscreen.setText("Waiting for user input...");

            // ask the user which board types to import and display statistics
            final int added = KnownBoardsManager.importDefaultBoardsWithConfirmation(
                    splashscreen, // parent the popups to the splash screen so that they inherit always-on-top
                    "Core.init.ImportDefaultKnownBoardsBody",
                    "Core.init.ImportDefaultKnownBoardsTitle",
                    true); // render the dialogs "always on top" just like the splashscreen itself,
                           // so that the splash stays on top of the desktop even while the dialog is up

            // the user has been offered the choice once; so don't show it to them again during subsequent startups
            frostSettings.setValue(SettingsClass.STARTUP_OFFERED_DEFAULT_BOARDS, true);
        }

        // Show enqueued startup messages before showing the mainframe,
        // otherwise the glasspane used during load of board messages could corrupt the modal message dialog!
        showStartupMessages(splashscreen); // inherits "always on top"

        // After expiration, select previously selected board tree row.
        // NOTE: This loads the message table!!!
        mainFrame.postInitialize();

        splashscreen.setText(language.getString("Splashscreen.message.5"));
        splashscreen.setProgress(80);

        // Show the main GUI now and wait until it's visible
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                mainFrame.setVisible(true);
                System.out.println("INFO: Frost-Next's main GUI loaded.");
            }
        });

        // Get rid of the splash screen
        splashscreen.closeMe();

        // boot up the machinery ;)
        initializeTasks(mainFrame);
    }

    public static FreenetCompatibilityManager getCompatManager() {
        return compatManager; // NOTE: will never be null, since it's instantiated before *anything* else in Frost starts
    }

    public FileTransferManager getFileTransferManager() {
        if (fileTransferManager == null) {
            fileTransferManager = FileTransferManager.inst();
        }
        return fileTransferManager;
    }

    public MainFrame getMainFrame(){
    	return mainFrame;
    }

    private BoardsManager getBoardsManager() {
        if (boardsManager == null) {
            boardsManager = new BoardsManager(frostSettings);
            boardsManager.setMainFrame(mainFrame);
        }
        return boardsManager;
    }

    /**
     * @param parentFrame the frame that will be the parent of any
     *          dialog that has to be shown in case an error happens
     *          in one of those tasks
     */
    private void initializeTasks(final MainFrame mainframe) {
        // initialize the task that frees memory
        TimerTask cleaner = new TimerTask() {
            @Override
            public void run() {
                logger.info("freeing memory");
                System.gc();
            }
        };
        final long gcMinutes = 10;
        timer.schedule(cleaner, gcMinutes * 60L * 1000L, gcMinutes * 60L * 1000L);
        cleaner = null;

        // initialize the task that saves data
        final StorageManager saver = new StorageManager(frostSettings);

        // auto savables
        saver.addAutoSavable(getBoardsManager().getTofTree());
        saver.addAutoSavable(getFileTransferManager());
        saver.addAutoSavable(new IdentityAutoBackupTask());

        // exit savables, must run before the perst storages are closed
        saver.addExitSavable(new IdentityAutoBackupTask());
        saver.addExitSavable(getBoardsManager().getTofTree());
        saver.addExitSavable(getFileTransferManager());

        saver.addExitSavable(frostSettings);

        // close perst Storages
        saver.addExitSavable(IndexSlotsStorage.inst());
        saver.addExitSavable(SharedFilesCHKKeyStorage.inst());
        saver.addExitSavable(FrostFilesStorage.inst());
        saver.addExitSavable(MessageStorage.inst());
        saver.addExitSavable(MessageContentStorage.inst());
        saver.addExitSavable(ArchiveMessageStorage.inst());
        saver.addExitSavable(IdentitiesStorage.inst());
        saver.addExitSavable(FileListStorage.inst());
        saver.addExitSavable(TrackDownloadKeysStorage.inst());
        saver.addExitSavable(HashBlocklistManager.getInstance());

        // invoke the mainframe ticker (board updates, clock, ...)
        mainframe.startTickerThread();

        // start file attachment uploads
        FileAttachmentUploadThread.getInstance().start();

        // start all filetransfer tickers
        getFileTransferManager().startTickers();

        /* //#DIEFILESHARING: This entire block has been commented out since filesharing is removed in Frost-Next.
        // after X seconds, start filesharing threads if enabled
        if( isFreenetOnline() && !frostSettings.getBoolValue(SettingsClass.FILESHARING_DISABLE)) {
            final Thread t = new Thread() {
                @Override
                public void run() {
                    Mixed.wait(10000);
                    FileSharingManager.startFileSharing();
                }
            };
            t.start();
        }
        */

        // the main frame is visible, so let's tell the user how to find information about all of the
        // new features and enhancements in Frost-Next, by directing them to the in-app help.
        if( frostSettings.getBoolValue(SettingsClass.STARTUP_SHOWN_INAPP_HELP_GUIDE) != true ) {
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    ImageIcon frostIcon = null;
                    try {
                        frostIcon = MiscToolkit.loadImageIcon("/data/frost.png");
                    } catch( final Exception e ) {}
                    MiscToolkit.showMessageDialog(
                        mainframe,
                        language.getString("Core.init.inAppHelpGuideBody"),
                        language.getString("Core.init.inAppHelpGuideTitle"),
                        MiscToolkit.INFORMATION_MESSAGE,
                        frostIcon,
                        true); // always-on-top until dismissed
                }
            });

            // don't show this message during subsequent startups
            frostSettings.setValue(SettingsClass.STARTUP_SHOWN_INAPP_HELP_GUIDE, true);
        }
    }

    public static FrostIdentities getIdentities() {
        if (identities == null) {
            identities = new FrostIdentities();
        }
        return identities;
    }

    /**
     * This method returns the language resource to get internationalized messages
     * from. That language resource is initialized the first time this method is called.
     * In that case, if the locale field has a value, it is used to select the
     * LanguageResource. If not, the locale value in frostSettings is used for that.
     */
    private void initializeLanguage() {
        if( Frost.getCmdLineLocaleFileName() != null ) {
            // external bundle specified on command line (overrides config setting)
            final File f = new File(Frost.getCmdLineLocaleFileName());
            Language.initializeWithFile(f);
        } else if (Frost.getCmdLineLocaleName() != null) {
            // use locale specified on command line (overrides config setting)
            Language.initializeWithName(Frost.getCmdLineLocaleName());
        } else {
            // use config file parameter (format: de or de;ext
            final String lang = frostSettings.getValue(SettingsClass.LANGUAGE_LOCALE);
            final String langIsExternal = frostSettings.getValue("localeExternal");
            if( lang == null || lang.length() == 0 || lang.equals("default") ) {
                // for default or if not set at all
                frostSettings.setValue(SettingsClass.LANGUAGE_LOCALE, "default");
                Language.initializeWithName(null);
            } else {
                boolean isExternal;
                if( langIsExternal == null || langIsExternal.length() == 0 || !langIsExternal.equals("true")) {
                    isExternal = false;
                } else {
                    isExternal = true;
                }
                Language.initializeWithName(lang, isExternal);
            }
        }
        language = Language.getInstance();
    }

    public void showAutoSaveError(final Exception exception) {
        try (
            // NOTE: Java 7+ try-with-resources (autocloseable)
            final StringWriter stringWriter = new StringWriter();
            final PrintWriter printWriter = new PrintWriter(stringWriter);
        ) {
            exception.printStackTrace(printWriter);

            final String detailsMsg = stringWriter.toString();
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    if (mainFrame != null) {
                        JDialogWithDetails.showErrorDialog(
                                mainFrame,
                                language.getString("Saver.AutoTask.title"),
                                language.getString("Saver.AutoTask.message"),
                                detailsMsg);
                        System.exit(3);
                    }
                }
            });
        } catch( final Exception e ) {}
    }

    public static boolean isHelpHtmlSecure() {
        return isHelpHtmlSecure;
    }
}
