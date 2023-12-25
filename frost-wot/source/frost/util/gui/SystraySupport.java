/*
  SystraySupport.java / Frost
  Copyright (C) 2011  Frost Project <jtcfrost.sourceforge.net>

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
package frost.util.gui;

import java.awt.*;
import java.awt.event.*;
import java.util.logging.*;

import frost.*;
import frost.util.gui.translation.*;

public class SystraySupport {
    
    private static final Logger logger = Logger.getLogger(SystraySupport.class.getName());

    final private static Language language = Language.getInstance();

    private static SystemTray tray = null;
    private static TrayIcon trayIcon = null;;
    
    private static Image image_normal = null;
    private static Image image_newMessage = null;
    
    private static boolean isInitialized = false;

    public static boolean isInitialized() {
        return isInitialized;
    }
    
    public static boolean isSupported() {
        try {
            return SystemTray.isSupported();
        } catch(Throwable t) {
            logger.log(Level.SEVERE, "Could not check for systray support.", t);            
        }
        return false;
    }

    public static boolean initialize(String title) {
        
        tray = SystemTray.getSystemTray();
        final Dimension trayIconSize = tray.getTrayIconSize();

        // fix the tray icon width on Ubuntu, because it unfortunately lies about its icon width,
        // which leads to ugly cropping if we trust it. it uses 22x24px by cropping 2px from the right...
        // this resizing makes the icon slightly elongated, but it looks way better than letting Ubuntu
        // crop one side and misalign everything, since this is the elongated form ubuntu wants.
        String osn = System.getProperty("os.name").toLowerCase();
        if( osn.indexOf("windows") == -1 && osn.indexOf("mac") == -1 ) { // not windows/mac; means we must be on unix/linux
            // check if they're running the Unity (Ubuntu) window manager,
            // which is the one with the non-standard "22x24" tray icons.
            //
            // Other window managers have been checked in late 2015 and all major ones are square:
            // - Gnome 3 (possibly 1 & 2 too): square icons (i.e. 16x16, 22x22, 24x24, 48x48)
            // - Cinnamon (used in Linux Mint): square icons (i.e. 16x16, 22x22, 24x24, 48x48)
            // - MATE (used in Linux Mint): square icons (i.e. 16x16, 22x22, 24x24, 48x48)
            String xdgCurrentDesktop = null;
            try {
                // NOTE:XXX: Ubuntu added this desktop variable in 11.10 (Oct 2011). there's nothing
                // we can do about the lack of the variable in outdated operating systems. this is
                // the only correct way of identifying the Unity desktop environment.
                xdgCurrentDesktop = System.getenv("XDG_CURRENT_DESKTOP"); // null if missing from env
            } catch( final java.lang.SecurityException e ) {
                xdgCurrentDesktop = null;
            }
            if( xdgCurrentDesktop != null && xdgCurrentDesktop.equalsIgnoreCase("Unity") ) {
                // NOTE: the tray (panel) is not resizable in Unity, so 24x24 is the icon size for
                // everyone, but this check is just here as a slight futureproofing if it ever changes.
                // NOTE: it's possible that the icon size misrepresenting is actually a bug in Java
                // not knowing about the non-square Unity icons, which is why it's even more important
                // that we only resize when we're told to do 24x24 icons in Unity. if the bug is ever
                // fixed so that the OS properly reports "22x24" on Unity, then this code will correctly
                // abort, thus ensuring that we'll get correct icons no matter what happens! :)
                if( trayIconSize.width == 24 && trayIconSize.height == 24 ) {
                    // they're running Ubuntu's Unity with "24x24" icons, so we must fix the tray icon width...
                    // FIXME/TODO: if Unity ever starts using actual 24x24 icons in the future, then
                    // this line will have to be removed. but that's extremely unlikely.
                    trayIconSize.width -= 2;
                }
            }
        }

        // load the tray icons and perform smooth scaling to match the system's tray icon size
        image_normal = MiscToolkit.getScaledImage("/data/trayicon_normal.png", trayIconSize.width, trayIconSize.height).getImage();
        image_newMessage = MiscToolkit.getScaledImage("/data/trayicon_newmessages.png", trayIconSize.width, trayIconSize.height).getImage();

        final ActionListener exitListener = new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                MainFrame.getInstance().fileExitMenuItem_actionPerformed();
            }
        };
        final ActionListener showHideListener = new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                toggleMinimizeToTray();
            }
        };

        final PopupMenu popup = new PopupMenu();

        final MenuItem showHideItem = new MenuItem(language.getString("SystraySupport.showHideFrost"));
        showHideItem.addActionListener(showHideListener);
        popup.add(showHideItem);

        popup.addSeparator();

        final MenuItem defaultItem = new MenuItem(language.getString("SystraySupport.ExitFrost"));
        defaultItem.addActionListener(exitListener);
        popup.add(defaultItem);

        trayIcon = new TrayIcon(image_normal, title, popup);

        // do not use the default, jagged looking "nearest neighbor" resizer,
        // since we've already adjusted the icons to the correct dimensions.
        // NOTE: this is important; allowing autosize would make the icons "24x24" as reported by
        // Ubuntu's Unity panel, and then the panel would crop 2px from the right. Instead, we
        // downscale ourselves to 22x24, which gives us 2px of transparency to the right which
        // Unity crops. That makes the icon look proper even on Ubuntu's weird Unity window manager.
        trayIcon.setImageAutoSize(false);
        trayIcon.addMouseListener(mouseListener);

        try {
            tray.add(trayIcon);
        } catch (final AWTException e) {
            System.err.println("TrayIcon could not be added.");
            return false;
        }
        
        isInitialized = true;
        
        return true;
    }
    
    public static void setTitle(String title) {
        trayIcon.setToolTip(title);
    }
    
    public static void setIconNormal() {
        trayIcon.setImage(image_normal);
    }

    public static void setIconNewMessage() {
        trayIcon.setImage(image_newMessage);
    }
    
    public static void minimizeToTray() {
        if (MainFrame.getInstance().isVisible()) {
            MainFrame.getInstance().setVisible(false);
        }
    }
    
    public static void toggleMinimizeToTray() {
        if (MainFrame.getInstance().isVisible()) {
            MainFrame.getInstance().setVisible(false);
        } else {
            MainFrame.getInstance().setVisible(true);
        }
    }

    final private static MouseListener mouseListener = new MouseListener() {

        // we need to test on pressed and released event
        boolean isPopupTrigger = false;

        public void mouseClicked(final MouseEvent e) {
        }

        public void mouseEntered(final MouseEvent e) {
        }

        public void mouseExited(final MouseEvent e) {
        }

        public void mousePressed(final MouseEvent e) {
            isPopupTrigger = e.isPopupTrigger();
        }

        public void mouseReleased(final MouseEvent e) {

            // don't show frame when user wanted to show the popup menu
            if (!isPopupTrigger && e.isPopupTrigger()) {
                isPopupTrigger = true;
            }

            if (isPopupTrigger) {
                return;
            }
            
            toggleMinimizeToTray();
        }
    };
}
