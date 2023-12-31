/*
  Splashscreen.java
  Copyright (C) 2001  Frost Project <jtcfrost.sourceforge.net>

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

import javax.swing.*;

import frost.util.gui.*;

/**
 * Problem with JProgressBar: a user reported having problems when starting Frost. He was getting this stack trace:
 * Exception in thread "main" java.lang.NullPointerException
 *       at java.awt.Dimension.<init>(Unknown Source)
 *       at javax.swing.plaf.basic.BasicProgressBarUI.getPreferredSize(Unknown Source)
 *       [..]
 *       at java.awt.Window.pack(Unknown Source)
 *       at frost.gui.Splashscreen.init(Splashscreen.java:66)
 *       [..]
 * The suggested workaround was to create the nosplash.chk file to completely disable the splash screen, but it would
 * be nice to find out why the JProgressBar is causing that trouble.
 *
 * Update: the suggested workaround didn't work. It threw another strage Swing exception later, when the main frame
 * was about to be shown, so I assume it may be a problem with the gfx card drivers or a bug with the JVM itself
 * (probably the first).
 *
 * Update: it seems the problem lies on the com.sun.java.swing.plaf.windows.WindowsLookAndFeel. If the user chooses
 * another one (like Metal) from the command line options, the issue is solved.
 */
@SuppressWarnings("serial")
public class Splashscreen extends JDialog {

    private static String SPLASH_LOGO_FILENAME = "/data/frostnext.jpg";

    //Splashscreen size depends on this image.
    private final ImageIcon frostLogo = MiscToolkit.loadImageIcon(SPLASH_LOGO_FILENAME);

    //GUI Objects
    JPanel mainPanel = new JPanel(new BorderLayout());
    JLabel pictureLabel = new JLabel();
    JProgressBar progressBar = new JProgressBar(0, 100);

    private boolean noSplash;

    public Splashscreen(final boolean hideSplashScreen) {
        noSplash = hideSplashScreen;
        init();
    }

    /**Close the splashscreen*/
    public void closeMe() {
        if (!noSplash) {
            setVisible(false);
            dispose();
//          logger.info("Splashscreen: I'm gone now :-(");
        }
    }

    /**Component initialization*/
    private void init() {

        setUndecorated(true);
        setResizable(false);

        pictureLabel.setIcon(frostLogo);

        progressBar.setFont(new Font("SansSerif", Font.PLAIN, 14)); // uniform size in every L&F
        progressBar.setStringPainted(true); // tells the L&F to draw the status string on the bar
        progressBar.setString("Starting...");

        getContentPane().add(mainPanel);
        mainPanel.add(pictureLabel, BorderLayout.CENTER);
        mainPanel.add(progressBar, BorderLayout.SOUTH);

        // always display the splashscreen on top of all other desktop windows, so that it doesn't
        // get lost anymore. note that this setting is temporarily ignored while dialog boxes are
        // shown, so we fixed it in all dialogs that are shown during startup as well!
        setAlwaysOnTop(true);

	pack();

        // center this window on the default monitor
	MiscToolkit.centerWindowSafe( MiscToolkit.getDefaultScreen(), this );
    }

    /**
     * Set progress for the progressBar.
     * Default range is from 0 to 100.
     * */
    public void setProgress(final int progress) {
        if (!noSplash) {
            progressBar.setValue(progress);
        }
    }

    /**Set the text for the progressBar*/
    public void setText(final String text) {
        if (!noSplash) {
            progressBar.setString(text);
        }
    }

    /* (non-Javadoc)
     * @see java.awt.Component#setVisible(boolean)
     */
    @Override
    public void setVisible(final boolean b) {
        if (!noSplash) {
            super.setVisible(b);
        }
    }
}
