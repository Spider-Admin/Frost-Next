/*
 MiscToolkit.java / Frost
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
package frost.util.gui;

import java.awt.*;
import java.awt.image.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.util.*;

import javax.swing.*;

import frost.*;
import frost.util.*;
import frost.util.gui.translation.*;

/**
 * This a gui related utilities class.
 * @author $Author$
 * @version $Revision$
 */
public class MiscToolkit {

//	private static final Logger logger = Logger.getLogger(MiscToolkit.class.getName());

	/**
	 * Prevent instances of this class from being created.
	 */
	private MiscToolkit() {
	}


	/**
	 * Gives you a Rectangle describing the x+y start offsets, the width and the height of the given
	 * monitor within the Java virtual screenspace. It also takes into account the OS taskbar/dock,
	 * so that you only get the coordinates of the monitor's safely usable area.
	 * @param GraphicsDevice gd - the monitor you want to analyze.
	 * @return Rectangle - Coordinate x = offset of the leftmost 0-coordinate of the monitor,
	 * y = offset of the top 0-coordinate of the monitor, width = the width of the usable area
	 * of the monitor, and height = the height of the usable area.
	 */
	public static Rectangle getSafeScreenRectangle( final GraphicsDevice gd ) {
		// find the monitor dimensions and its x+y starting locations within the virtual Java
		// screenspace (which includes all screens)
		final GraphicsConfiguration gcf = gd.getDefaultConfiguration(); // describes the details of the given monitor
		final Rectangle gcfBounds = gcf.getBounds(); // the total resolution of the monitor, and its x+y virtual offsets

		// adjust the offsets and dimensions to only include the area in which it's safe to
		// show content (taking into account taskbar/etc)
		final Insets gcfInsets = Toolkit.getDefaultToolkit().getScreenInsets( gcf ); // describes the size of the taskbar/dock of the current OS
		gcfBounds.x += gcfInsets.left;
		gcfBounds.y += gcfInsets.top;
		gcfBounds.width -= ( gcfInsets.left + gcfInsets.right );
		gcfBounds.height -= ( gcfInsets.top + gcfInsets.bottom );

		return gcfBounds;
	}

	/**
	 * Gives you the default monitor (usually the leftmost one in a multi-monitor setup).
	 * Should be combined with getSafeScreenRectangle() to get the safely usable
	 * coordinates of that monitor.
	 * @return GraphicsDevice.
	 */
	public static GraphicsDevice getDefaultScreen() {
		// find the default monitor
		final GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		final GraphicsDevice gd = ge.getDefaultScreenDevice(); // usually the leftmost monitor
		
		return gd;
	}

	/**
	 * Tells you the X and Y coordinates that would center your window within the safe screen
	 * area of the given monitor.
	 * @param GraphicsDevice gd - the monitor you want to use
	 * @param Dimension wd - a Dimension object describing the size of your window; you can
	 * get it by calling .getSize() on your window
	 * @return Point - the x and y locations that will center your window; apply it
	 * by calling .setLocation() on your window
	 */
	public static Point getSafeCenteringLocation( final GraphicsDevice gd, final Dimension wd ) {
	        final Rectangle gcfBounds = getSafeScreenRectangle( gd );
		final Point centerLocation = new Point(
			(int)Math.floor( gcfBounds.x + ( (gcfBounds.width - wd.width) / 2 ) ),
			(int)Math.floor( gcfBounds.y + ( (gcfBounds.height - wd.height) / 2 ) )
		);
		return centerLocation;
	}

	/**
	 * Centers a window within the safe area of the given monitor. If you want to center
	 * something relative to the main Frost window instead, just import frost.* or
	 * frost.MainFrame and then run myWindow.setLocationRelativeTo( MainFrame.getInstance() );
	 * @param GraphicsDevice gd - the monitor you want to use
	 * @param Window w - your window; make sure that you've first set it to the proper size
	 */
	public static void centerWindowSafe( final GraphicsDevice gd, final Window w ) {
		final Dimension wd = w.getSize();
		final Point centerLocation = getSafeCenteringLocation( gd, wd );
		w.setLocation( centerLocation );
	}


    /**
     * Configures a button, setting its tooltip text, rollover icon and some other
     * default properties.
     * @param button the button to configure
     * @param toolTipKey language resource key to extract its tooltip text with
     * @param language language to extract the tooltip text from
     */
	public static void configureButton(
		final JButton button,
		final String toolTipKey,
		final Language language)
	{
		button.setToolTipText(language.getString(toolTipKey));
		configureButton(button);
	}

	/**
	 * Configures a button to be a default icon button, setting its rollover icon
	 * and some other default properties.
	 * @param button the button to configure
	 */
	public static void configureButton(final JButton button) {
	    if( button.getIcon() instanceof ImageIcon ) {
	        button.setRolloverIcon(createRolloverIcon((ImageIcon)button.getIcon()));
	    }
	    button.setMargin(new Insets(0, 0, 0, 0));
	    button.setPreferredSize(new Dimension(30,25));
	    button.setBorderPainted(false);
	    button.setFocusPainted(false);
	    button.setOpaque(false);
	}

	/**
	 * Create a rollover icon for the source icon. Currently, this method gives
	 * the source icon a yellow touch. Maybe this needs to be changed when a
	 * yellow look&feel is used ;)
	 * @param icon  source icon
	 * @return  an icon that can be used as rollover icon for source icon
	 */
	public static ImageIcon createRolloverIcon(final ImageIcon icon) {
		// color increase values
		final int RED_INCREASE = 50;
		final int GREEN_INCREASE = 40;
//		final int BLUE_INCREASE = 50;

		final int width = icon.getIconWidth();
		final int height = icon.getIconHeight();

		final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		image.createGraphics().drawImage(icon.getImage(), 0, 0, new JPanel());
		final Raster rasterSource = image.getRaster();
		final WritableRaster rasterDest = image.getRaster();

		// iterate over all pixels in the picture
		for ( int x = 0; x < width; x++) {
			for ( int y = 0; y < height; y++) {
				// Get the source pixels
				final int[] srcPixels = new int[4];
				rasterSource.getPixel(x,y,srcPixels);
				// Ignore transparent pixels
				if (srcPixels[3] != 0){
					// increase red and green to achieve more yellow
					srcPixels[0] = srcPixels[0] + RED_INCREASE;
					// prevent color crash
					srcPixels[0] = Math.min(srcPixels[0], 255);

					srcPixels[1] = srcPixels[1] + GREEN_INCREASE;
					// prevent color crash
					srcPixels[1] = Math.min(srcPixels[1], 255);

					// prepared code for change of look & feel
//					srcPixels[2] = srcPixels[2] +  BLUE_INCREASE;
//					// prevent color crash
//					srcPixels[2] = Math.min(srcPixels[2], 255);
					rasterDest.setPixel(x,y,srcPixels);
				}
			}
		}
		return new ImageIcon(image);
	}

	/**
	 * This method loads an image from the given resource path and scales it to
	 * the dimensions passed as parameters.
	 * @param imgPath resource path to load de image from.
	 * @param width width to scale the image to.
	 * @param height height to scale the image to.
	 * @return an ImageIcon containing the image.
	 */
	public static ImageIcon getScaledImage(final String imgPath, final int width, final int height) {
		ImageIcon icon = MiscToolkit.loadImageIcon(imgPath);
		icon = new ImageIcon(icon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH));
		return icon;
	}

	/**
	 * This method enables/disables the subcomponents of a container.
	 * If the container contains other containers, the subcomponents of those
	 * are enabled/disabled recursively.
	 * @param container
	 * @param enabled
	 */
	public static void setContainerEnabled(final Container container, final boolean enabled) {
		final int componentCount = container.getComponentCount();
		for (int x = 0; x < componentCount; x++) {
			final Component component = container.getComponent(x);
			if (component instanceof Container) {
				setContainerEnabledInner((Container) component, enabled);
			} else {
				component.setEnabled(enabled);
			}
		}
	}

	/**
	 * This method enables/disables the subcomponents of a container.
	 * If the container contains other containers, the components of those
	 * are enabled/disabled recursively.
	 * All of the components in the exceptions collection are ignored in this process.
	 * @param container
	 * @param enabled
	 * @param exceptions the components to ignore
	 */
	public static void setContainerEnabled(
	        final Container container,
	        final boolean enabled,
	        final Collection<Component> exceptions)
	{
		final int componentCount = container.getComponentCount();
		for (int x = 0; x < componentCount; x++) {
			final Component component = container.getComponent(x);
			if (!exceptions.contains(component)) {
				if (component instanceof Container) {
					setContainerEnabledInner((Container) component, enabled, exceptions);
				} else {
					component.setEnabled(enabled);
				}
			}
		}
	}

	/**
	 * @param container
	 * @param enabled
	 */
	private static void setContainerEnabledInner(final Container container, final boolean enabled) {
		final int componentCount = container.getComponentCount();
		for (int x = 0; x < componentCount; x++) {
			final Component component = container.getComponent(x);
			if (component instanceof Container) {
				setContainerEnabledInner((Container) component, enabled);
			} else {
				component.setEnabled(enabled);
			}
		}
		container.setEnabled(enabled);
	}

	/**
	 * @param container
	 * @param enabled
	 * @param exceptions
	 */
	private static void setContainerEnabledInner(
		final Container container,
		final boolean enabled,
		final Collection<Component> exceptions)
	{
		final int componentCount = container.getComponentCount();
		for (int x = 0; x < componentCount; x++) {
			final Component component = container.getComponent(x);
			if (!exceptions.contains(component)) {
				if (component instanceof Container) {
					setContainerEnabledInner((Container) component, enabled, exceptions);
				} else {
					component.setEnabled(enabled);
				}
			}
		}
		container.setEnabled(enabled);
	}

	/**** JOptionPane implementations: ****/

	// message types
	public static int ERROR_MESSAGE = JOptionPane.ERROR_MESSAGE;
	public static int INFORMATION_MESSAGE = JOptionPane.INFORMATION_MESSAGE;
	public static int WARNING_MESSAGE = JOptionPane.WARNING_MESSAGE;
	public static int QUESTION_MESSAGE = JOptionPane.QUESTION_MESSAGE;
	public static int PLAIN_MESSAGE = JOptionPane.PLAIN_MESSAGE; // no icon used

	// option types for showConfirmDialog
	public static int DEFAULT_OPTION = JOptionPane.DEFAULT_OPTION;
	public static int YES_NO_OPTION = JOptionPane.YES_NO_OPTION;
	public static int YES_NO_CANCEL_OPTION = JOptionPane.YES_NO_CANCEL_OPTION;
	public static int OK_CANCEL_OPTION = JOptionPane.OK_CANCEL_OPTION;

	// possible return values from the showXxxDialog methods that return an integer
	public static int YES_OPTION = JOptionPane.YES_OPTION;
	public static int NO_OPTION = JOptionPane.NO_OPTION;
	public static int CANCEL_OPTION = JOptionPane.CANCEL_OPTION;
	public static int OK_OPTION = JOptionPane.OK_OPTION;
	public static int CLOSED_OPTION = JOptionPane.CLOSED_OPTION; // if the user closes the window without selecting anything


	/**
	 * This internal, low-level method shows a dialog to the user and optionally requests input.
	 * The dialog is modal (blocks input to all other windows from the application), and won't let
	 * you click any other Frost windows until you dismiss it. It also stays on top of all other
	 * windows on the user's desktop, thanks to a custom JDialog implementation.
	 * Implements a similar interface compared to other JOptionPane functions, but isn't 1:1 compatible
	 * since it has a couple of extra parameters to specify the exact behavior you want. Also be
	 * aware that the order of messageType and optionType is swapped compared to the other
	 * higher-level generic functions. That's to stay consistent with JOptionPane's constructor order.
	 * @param parentComponent - which window to center the dialog around; if null, it uses the main
	 * Frost window (the normal JOptionPane would make it parentless and place it randomly on the screen)
	 * @param message - the message to show
	 * @param title - the title to use; if null, the default title of "Frost" will be used
	 * @param messageType - what type of message (affects the automatic icon choice if no
	 * custom icon was provided)
	 * @param optionType - what option buttons to display to the user (such as OK/Cancel); will
	 * be overridden by non-null selectionValues if selectionsAsDropdown is false
	 * @param icon - what icon to use, can be null to let the Look&Feel auto-choose one based on message type
	 * @param selectionValues - a list of possible values to choose from, can be null for nothing;
	 * if provided, they override the default optionType buttons unless selectionsAsDropdown=true
	 * @param initialSelectionValue - the initially selected value; if selectionValues is null,
	 * this is treated as a string which will be in the input box by default (but ONLY if wantsTextInput
	 * is true), otherwise it refers to the object that you want pre-selected from the selectionValues
	 * list; can be null for no initial selection
	 * @param wantsTextInput - set to true if you want to display a text input field (however, it
	 * will not show up if you specify non-null selectionValues + selectionsAsDropdown = true; but
	 * it *will* show up if you specify selectionValues + selectionsAsDropdown = false, since the
	 * selections will then be shown as buttons under the text input field - just be aware that if
	 * you *also* provide an initialSelectionValue then you're in weird territory and doing something
	 * that the JOptionPane wasn't meant to handle; the button whose string-value corresponds to that
	 * string will be pre-selected *and* the input field will have that string in it. So if you want
	 * the selections as custom buttons along with an input field then do NOT provide an initialSelectionValue!
	 * it might work properly if your initial value doesn't match any of the options, but it's still
	 * at your own risk!)
	 * @param selectionsAsDropdown - set to true if you have provided selectionValues and you want
	 * them displayed as a dropdown instead of as buttons
	 * @param alwaysOnTop - set to true if you want the dialog to stay on top of the entire OS;
	 * this is usually not what you want, since the dialog is already parented to its parentComponent
	 * and won't disappear behind it. but if you've got an "always on top" window *then* you'll
	 * want "always on top" dialog boxes too, otherwise the parent window temporarily loses top-status
	 * while the dialog is displayed
	 * @return after the user has made their choice (or closed the dialog), it returns the finished
	 * joptionpane object where you can read the button clicks, selection and input text string
	 * via its getValue() and getInputValue().
	 * - getInputValue(): if wantsTextInput was true, then this returns the String that was typed
	 *   into the field. if selectionValues was != null, then this returns the Object the user specified.
	 *   otherwise it returns JOptionPane.UNINITIALIZED_VALUE, since the user wasn't asked for any
	 *   custom text/button choices.
	 * - getValue(): returns the value the user has selected. JOptionPane.UNINITIALIZED_VALUE implies
	 *   the user hasn't made a choice, null means the user closed the window without choosing anything,
	 *   otherwise the return value will be an Object specifying one of the selectionValues the user
	 *   had to choose from, *or* if you're using a default button set it will be an Integer object
	 *   specifying the ID of the clicked button.
	 * See the real-world usage in the higher-level functions for more specifics.
	 */
	private static JOptionPane onTopJOptionPane(final Component parentComponent, final Object message, final String title, final int messageType, final int optionType, final Icon icon, final Object[] selectionValues, final Object initialSelectionValue, final boolean wantsTextInput, final boolean selectionsAsDropdown, final boolean alwaysOnTop) {
		// begin by manually creating a JOptionPane (instead of lazily using the show###Dialog methods);
		// that way we can customize the dialog and make it always on top!
		final JOptionPane optionPane = new JOptionPane(
			// an object whose toString() represents the message that will be displayed in the dialog
			message,
			// the type of message; this affects the default icon selection if no icon was provided
			messageType,
			// what buttons to show to the user (such as OK/Cancel); will be overridden with the selectionValues instead in certain cases
			optionType,
			// custom icon; if null, it will let the Look&Feel choose an icon based on the messageType
			icon,
			// if selectionsAsDropdown=false and selectionValues!=null, the "optionType" buttons
			// will be replaced with the strings representing the selectionValues choices instead
			// (and we won't show any dropdown menu).
			(selectionsAsDropdown ? null : selectionValues),
			// if selectionsAsDropdown=false and initialSelectionValue!=null, the given button
			// will be highlighted when the dialog opens.
			(selectionsAsDropdown ? null : initialSelectionValue));

		// if the user wants either a dropdown of options, or a typable input field, then enable that now
		if( selectionsAsDropdown || wantsTextInput ) {
			// enables the text-input field; but if we provide a list of selection values below, it will use a dropdown instead
			optionPane.setWantsInput(true);
		}

		// now add a dropdown with the user's list of chosen options, if they wanted the options
		// displayed as a dropdown and provided values
		if( selectionsAsDropdown && selectionValues != null ) {
			optionPane.setSelectionValues( selectionValues );
		}

		// pre-selects the desired option (if selectionValues were provided), or sets the
		// initial text of the textfield otherwise
		if( initialSelectionValue != null && ( wantsTextInput || selectionValues != null ) ) {
			// a selection is provided, *and* the caller either enabled text-input mode
			// or provided a list of selection values
			optionPane.setInitialSelectionValue( initialSelectionValue );
		}

		// now it's time to create the actual dialog that we'll be displaying the optionpane within
		final JDialog dialog = optionPane.createDialog(
			// centers the dialog over the provided parent window or the main Frost window
			( parentComponent != null ? parentComponent : MainFrame.getInstance() ),
			// the titlebar of the dialog
			( title != null ? title : "Frost" ));
		if( alwaysOnTop ) {
			// makes sure our dialog is always on top of everything else on the desktop
			dialog.setAlwaysOnTop(true);
		}
		// blocks input to all other windows in the application while the dialog is shown; available since Java 1.6
		dialog.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
		// automatically dispose() the dialog after it's closed to free its memory
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		// resize the dialog to make sure all content fits
		dialog.pack();

		// now display the dialog. note that this operation always blocks the current thread until
		// the user closes the dialog again, which is exactly the behavior we need! that, combined
		// with the modality, ensures that even if the GUI had been multi-threaded it wouldn't
		// resume until the dialog is closed!
		dialog.setVisible(true);

		// return the finished joptionpane for analysis (the getValue and getInputValue methods
		// will return the user's selection/input)
		return optionPane;
	}


	/**
	 * This method shows a confirmation dialog to the user. The dialog is modal, stays on top of its
	 * parent window (optionally all windows on the desktop), and won't let you click any other
	 * Frost windows until you dismiss it.
	 * Implements the exact same interfaces, behaviors and return values as JOptionPane.showConfirmDialog(),
	 * except for the additional alwaysOnTop interface.
	 * @param parentComponent - which window to center the dialog around; if null, it uses the main
	 * Frost window (the normal JOptionPane would make it parentless and place it randomly on the screen)
	 * @param message - the message to show
	 * @param title - the title to use; if null, the default title of "Frost" will be used (the
	 * normal JOptionPane would use "Select an Option")
	 * @param optionType - what buttons to display
	 * @param messageType - what type of message
	 * @param icon - what icon to use, can be null to let the Look&Feel auto-choose one based on message type
	 * @param alwaysOnTop - set to true if you want the dialog to stay on top of the entire OS;
	 * this is usually not what you want, since the dialog is already parented to its parentComponent
	 * and won't disappear behind it. but if you've got an "always on top" window *then* you'll want
	 * "always on top" dialog boxes too, otherwise the parent window temporarily loses top-status
	 * while the dialog is displayed
	 */
	public static int showConfirmDialog(final Component parentComponent, final Object message, final String title, final int optionType, final int messageType, final Icon icon, final boolean alwaysOnTop) {
		final JOptionPane optionPane = onTopJOptionPane( parentComponent, message, title, messageType, optionType, icon, null, null, false, false, alwaysOnTop );

		Object selectedValue = optionPane.getValue();
		if( selectedValue == null ) { // they closed the dialog without clicking any button
			return MiscToolkit.CLOSED_OPTION;
		}
		if( selectedValue instanceof Integer ) { // user clicked a button
			return ((Integer)selectedValue).intValue(); // return the int representing the clicked button
		} else { // non-standard return values; should not be able to happen
			return MiscToolkit.CLOSED_OPTION;
		}
	}
	/* replicas of the generic functions: */
	public static int showConfirmDialog(final Component parentComponent, final Object message) {
		return showConfirmDialog( parentComponent, message, null, MiscToolkit.YES_NO_CANCEL_OPTION, MiscToolkit.QUESTION_MESSAGE, null, false );
	}
	public static int showConfirmDialog(final Component parentComponent, final Object message, final String title, final int optionType) {
		return showConfirmDialog( parentComponent, message, title, optionType, MiscToolkit.QUESTION_MESSAGE, null, false );
	}
	public static int showConfirmDialog(final Component parentComponent, final Object message, final String title, final int optionType, final int messageType) {
		return showConfirmDialog( parentComponent, message, title, optionType, messageType, null, false );
	}
	public static int showConfirmDialog(final Component parentComponent, final Object message, final String title, final int optionType, final int messageType, final Icon icon) {
		return showConfirmDialog( parentComponent, message, title, optionType, messageType, icon, false );
	}


	/**
	 * This method shows a dialog requesting input from the user.  The dialog is modal, stays on
	 * top of its parent window (optionally all windows on the desktop), and won't let you click
	 * any other Frost windows until you dismiss it.
	 * Implements the exact same interfaces, behaviors and return values as JOptionPane.showInputDialog(),
	 * except for the additional alwaysOnTop interface.
	 * @param parentComponent - which window to center the dialog around; if null, it uses the main
	 * Frost window (the normal JOptionPane would make it parentless and place it randomly on the screen)
	 * @param message - the message to show
	 * @param title - the title to use; if null, the default title of "Frost" will be used (the
	 * normal JOptionPane would use "Input")
	 * @param messageType - what type of message, you probably want MiscToolkit.QUESTION_MESSAGE
	 * (which is the default)
	 * @param icon - what icon to use, can be null to let the Look&Feel auto-choose one based on message type
	 * @param selectionValues - a list of possible values to choose from, can be null for nothing
	 * @param initialSelectionValue - the initially selected value; if selectionValues is null,
	 * this string will be in the input box by default
	 * @param alwaysOnTop - set to true if you want the dialog to stay on top of the entire OS;
	 * this is usually not what you want, since the dialog is already parented to its parentComponent
	 * and won't disappear behind it. but if you've got an "always on top" window *then* you'll want
	 * "always on top" dialog boxes too, otherwise the parent window temporarily loses top-status
	 * while the dialog is displayed
	 * @return the text/choice the user has entered
	 */
	/*internal handler:*/private static Object showInputDialog(final Component parentComponent, final Object message, final String title, final int messageType, final Icon icon, final Object[] selectionValues, final Object initialSelectionValue, final boolean wantsTextInput, final boolean selectionsAsDropdown, final boolean alwaysOnTop) {
		final JOptionPane optionPane = onTopJOptionPane( parentComponent, message, title, messageType, MiscToolkit.OK_CANCEL_OPTION, icon, selectionValues, initialSelectionValue, wantsTextInput, selectionsAsDropdown, alwaysOnTop );

		Object inputValue = optionPane.getInputValue();
		if( inputValue == JOptionPane.UNINITIALIZED_VALUE ) {
			return null; // user closed the dialog or hit cancel
		}
		return inputValue; // a String object if textinput or another Object if chosen from a dropdown
	}
	public static Object showInputDialog(final Component parentComponent, final Object message, final String title, final int messageType, final Icon icon, final Object[] selectionValues, final Object initialSelectionValue, final boolean alwaysOnTop) {
		// this needs a bit of explanation: if selectionValues is null, it enables the text-input
		// field and disables the selection dropdown; any initialSelectionValue will be applied
		// to the input field. however, if selectionValues != null, it disables the text-input
		// field and enables a dropdown with all of the possible selections. this dynamic choice
		// between a dropdown or an input field is consistent with how the official
		// showInputDialog() handles those situations
		return showInputDialog( parentComponent, message, title, messageType, icon, selectionValues, initialSelectionValue, (selectionValues==null ? true : false), (selectionValues==null ? false : true), alwaysOnTop );
	}
	/* replicas of the generic functions: */
	public static String showInputDialog(final Component parentComponent, final Object message) {
		return (String) showInputDialog( parentComponent, message, null, MiscToolkit.QUESTION_MESSAGE, null, null, null, false );
	}
	public static String showInputDialog(final Component parentComponent, final Object message, Object initialSelectionValue) {
		return (String) showInputDialog( parentComponent, message, null, MiscToolkit.QUESTION_MESSAGE, null, null, initialSelectionValue, false );
	}
	public static String showInputDialog(final Component parentComponent, final Object message, final String title, final int messageType) {
		return (String) showInputDialog( parentComponent, message, title, messageType, null, null, null, false );
	}
	public static Object showInputDialog(final Component parentComponent, final Object message, final String title, final int messageType, final Icon icon, final Object[] selectionValues, final Object initialSelectionValue) {
		return showInputDialog(parentComponent, message, title, messageType, icon, selectionValues, initialSelectionValue, false);
	}
	public static String showInputDialog(final Object message) {
		return (String) showInputDialog( null, message, null, MiscToolkit.QUESTION_MESSAGE, null, null, null, false );
	}
	public static String showInputDialog(final Object message, Object initialSelectionValue) {
		return (String) showInputDialog( null, message, null, MiscToolkit.QUESTION_MESSAGE, null, null, initialSelectionValue, false );
	}


	/**
	 * This method shows a message to the user. The dialog is modal, stays on top of its parent
	 * window (optionally all windows on the desktop), and won't let you click any other Frost
	 * windows until you dismiss it.
	 * Implements the exact same interfaces, behaviors and return values as JOptionPane.showMessageDialog(),
	 * except for the additional alwaysOnTop interface.
	 * @param parentComponent - which window to center the dialog around; if null, it uses the main
	 * Frost window (the normal JOptionPane would make it parentless and place it randomly on the screen)
	 * @param message - the message to show
	 * @param title - the title to use; if null, the default title of "Frost" will be used (the
	 * normal JOptionPane would use "Message")
	 * @param messageType - what type of message, you probably want MiscToolkit.INFORMATION_MESSAGE
	 * (which is the default)
	 * @param icon - what icon to use, can be null to let the Look&Feel auto-choose one based on message type
	 * @param alwaysOnTop - set to true if you want the dialog to stay on top of the entire OS;
	 * this is usually not what you want, since the dialog is already parented to its parentComponent
	 * and won't disappear behind it. but if you've got an "always on top" window *then* you'll want
	 * "always on top" dialog boxes too, otherwise the parent window temporarily loses top-status
	 * while the dialog is displayed
	 */
	public static void showMessageDialog(final Component parentComponent, final Object message, final String title, final int messageType, final Icon icon, final boolean alwaysOnTop) {
		// note that we need no return value validation, since message dialogs don't have any
		// return values, and also that "DEFAULT_OPTION" provides a single "OK" button.
		onTopJOptionPane( parentComponent, message, title, messageType, MiscToolkit.DEFAULT_OPTION, icon, null, null, false, false, alwaysOnTop );
	}
	/* replicas of the generic functions: */
	public static void showMessageDialog(final Component parentComponent, final Object message) {
		showMessageDialog( parentComponent, message, null, MiscToolkit.INFORMATION_MESSAGE, null, false );
	}
	public static void showMessageDialog(final Component parentComponent, final Object message, final String title, final int messageType) {
		showMessageDialog( parentComponent, message, title, messageType, null, false );
	}
	public static void showMessageDialog(final Component parentComponent, final Object message, final String title, final int messageType, final Icon icon) {
		showMessageDialog( parentComponent, message, title, messageType, icon, false );
	}


	/**
	 * This method shows a dialog requesting the user to click on one of the option-buttons. The
	 * dialog is modal, stays on top of its parent window (optionally all windows on the desktop),
	 * and won't let you click any other Frost windows until you dismiss it.
	 * Implements the exact same interfaces, behaviors and return values as JOptionPane.showOptionDialog(),
	 * except for the additional alwaysOnTop interface.
	 * @param parentComponent - which window to center the dialog around; if null, it uses the main
	 * Frost window (the normal JOptionPane would make it parentless and place it randomly on the screen)
	 * @param message - the message to show
	 * @param title - the title to use; if null, the default title of "Frost" will be used (the
	 * normal JOptionPane would use a blank title)
	 * @param optionType - what buttons to display; not used if selectionValues is non-null!
	 * @param messageType - what type of message, you probably want MiscToolkit.QUESTION_MESSAGE
	 * (which is the default)
	 * @param icon - what icon to use, can be null to let the Look&Feel auto-choose one based on message type
	 * @param options - a list of possible buttons to choose from, can be null to use the
	 * optionType-provided buttons instead
	 * @param initialValue - the initially selected button, can be null; if options is null, this value is ignored
	 * @param alwaysOnTop - set to true if you want the dialog to stay on top of the entire OS; this
	 * is usually not what you want, since the dialog is already parented to its parentComponent and
	 * won't disappear behind it. but if you've got an "always on top" window *then* you'll want
	 * "always on top" dialog boxes too, otherwise the parent window temporarily loses top-status
	 * while the dialog is displayed
	 * @return an integer representing the button pressed by the user, or CLOSED_OPTION if the user
	 * closed the dialog. if you use NULL for options, this integer corresponds with buttons such
	 * as YES_OPTION. if you provided an options list, the integer is the array offset into that
	 * list for the chosen option.
	 */
	public static int showOptionDialog(final Component parentComponent, final Object message, final String title, final int optionType, final int messageType, final Icon icon, final Object[] options, final Object initialValue, final boolean alwaysOnTop) {
		// NOTE: "options" sets the buttons displayed on the dialog; if options is null, then
		// initialvalue has no meaning either, and it'll use the optionType buttons instead
		final JOptionPane optionPane = onTopJOptionPane(parentComponent, message, title, messageType, optionType, icon, options, initialValue, false, false, alwaysOnTop);

		Object selectedValue = optionPane.getValue();
		if( selectedValue == null ) { // they closed the dialog without clicking any button
			return MiscToolkit.CLOSED_OPTION;
		}
		if( options == null ) { // no options were provided, so the button objects are Integers corresponding to YES_OPTION etc
			if( selectedValue instanceof Integer ) { // user clicked an "optionType"-provided button
				return ((Integer)selectedValue).intValue(); // return the int representing the clicked button
			}
			return MiscToolkit.CLOSED_OPTION; // non-standard return values; should not be able to happen
		}
		for( int counter=0, maxCounter=options.length; // there was a user-provided array of option-buttons
		     counter < maxCounter; ++counter ) {
			if( options[counter].equals( selectedValue ) ) {
				return counter; // we found the Object for that button, so return its array index
			}
		}
		return MiscToolkit.CLOSED_OPTION; // the object was not found in the options array; should not be able to happen
	}
	/* replicas of the generic functions: */
	public static int showOptionDialog(final Component parentComponent, final Object message, final String title, final int optionType, final int messageType, final Icon icon, final Object[] options, final Object initialValue) {
		return showOptionDialog(parentComponent, message, title, optionType, messageType, icon, options, initialValue, false);
	}

	/*** end of JOptionPane implementations; there are like 3-4 places in Frost that still use the
	 * raw JOptionPane (the instances of using showSuppressableConfirmDialog, which hasn't been
	 * rewritten); but those instances have been verified to all specify a proper parent window
	 * for centering, so it would be a waste of time to update them to generic methods here, and
	 * we don't need the "always on top" functionality either for those dialogs since that's only
	 * necessary for the splash screen! ***/

    public static ImageIcon loadImageIcon(final String resourcePath) {
        return new ImageIcon(MiscToolkit.class.getResource(resourcePath));
    }

    /**
     * Shows a confirmation dialog which can be suppressed by the user.
     * All parameters are like for JOptionPane.showConfirmDialog().
     *
     * @param frostSettingName  The name of the boolean setting, like 'confirm.markAllMessagesRead'
     * @param checkboxText      The text for the checkbox, like 'Show this dialog the next time'
     * @return  JOptionPane.YES_OPTION if dialog is suppressed or user answered with YES. else !=YES_OPTION
     */
    public static int SUPPRESSABLE_OK_BUTTON = -999; // special button, gives only "Ok" choice
    public static int showSuppressableConfirmDialog(
            final Component parentComponent,
            final Object message,
            final String title,
            final int optionType,
            final int messageType,
            final String frostSettingName,
            final String checkboxText)
    {
        final boolean showConfirmDialog = Core.frostSettings.getBoolValue(frostSettingName);
        if( !showConfirmDialog ) {
            // no confirmation, always YES
            return JOptionPane.YES_OPTION;
        }

        final JOptionPane op = new JOptionPane(message, messageType);
        if( optionType != SUPPRESSABLE_OK_BUTTON ) { // if "Ok" button is requested, we simply don't set any type
            op.setOptionType(optionType);
        }

        final JDialog dlg = op.createDialog(parentComponent, title);

        final JCheckBox cb = new JCheckBox(checkboxText);
        cb.setSelected(true);
        dlg.getContentPane().add(cb, BorderLayout.SOUTH);
        dlg.pack();

        dlg.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dlg.setModal(true);
        dlg.setVisible(true);

        if( !cb.isSelected() ) {
            // user wants to suppress this dialog in the future
            Core.frostSettings.setValue(frostSettingName, false);
        }

        dlg.dispose();

        final Object selectedValue = op.getValue();
        if( selectedValue == null ) {
            return JOptionPane.CLOSED_OPTION;
        }
        if( selectedValue instanceof Integer ) {
            return ((Integer) selectedValue).intValue();
        }
        return JOptionPane.CLOSED_OPTION;
    }
}
