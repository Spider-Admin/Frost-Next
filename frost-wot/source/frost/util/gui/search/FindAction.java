/*
  FindAction.java / Frost
  Copyright (C) 2007  Frost Project <jtcfrost.sourceforge.net>

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
package frost.util.gui.search;

import java.awt.*;
import java.awt.event.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;

import frost.util.SingleTaskWorker;

/**
  * Press Ctrl+F to start a case insensitive search or Ctrl+Shift+F
  * for a case sensitive search. While searching, press Down Arrow
  * or Ctrl+G to go to the next match. Press Up Arrow or Ctrl+Shift+G
  * to go to the previous match. Press ESC to end the search.
 */

// @author The Kitty (complete rewrite to make it actually freaking work without freezing Frost, and about 20x faster)
public abstract class FindAction
    extends AbstractAction
    implements DocumentListener, KeyListener, ActionListener
{
    // NOTE: since the search-field is a "popup menu" internally, it will vanish if the user clicks
    // outside of it. but we'll also register a handler so that escape closes it.
    private JPopupMenu fPopup;
    // this is the panel that will sit inside the popup menu, and will house the search field
    private JPanel fSearchPanel;
    // the search field is just protected, so that subclasses can read its text value
    protected JTextField fSearchField;
    // puts a small delay before performing a new search, to give the user some time to type before overloading
    private SingleTaskWorker delayedSearchWorker;

    public FindAction()
    {
        super("Incremental Search"); //NOI18N

        delayedSearchWorker = new SingleTaskWorker();

        // the panel which will house the search field and label
        fSearchPanel = new JPanel();
        fSearchPanel.setLayout(new BoxLayout(fSearchPanel, BoxLayout.X_AXIS)); // distribute all components horizontally
        fSearchPanel.setBackground(UIManager.getColor("ToolTip.background"));
        fSearchPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5)); // give the panel 5px on sides

        // label prompting the user to serach
        final JLabel label = new JLabel("Search for:");
        label.setFont(new Font("SansSerif", Font.BOLD, 12)); // for readability
        fSearchPanel.add(label);

        // the actual search field where they enter their query
        fSearchField = new JTextField();
        final Dimension fixedSize = fSearchField.getPreferredSize(); // inherit L&F height
        fixedSize.width = 200; // 200px wide; fixed size, since constantly resizing the popup is super laggy and can even cause Swing to freeze
        fSearchField.setPreferredSize(fixedSize);
        fSearchField.setMinimumSize(fixedSize);
        fSearchField.setMaximumSize(fixedSize);
        fSearchField.setFont(new Font("SansSerif", Font.PLAIN, 13)); // for readability
        fSearchField.setBorder(BorderFactory.createCompoundBorder(
                    fSearchField.getBorder(), // include L&Fs border as outer border
                    BorderFactory.createEmptyBorder(0, 3, 0, 3))); // add 3px to left and right insides
        fSearchPanel.add(fSearchField);

        // the popup menu which houses the panel
        fPopup = new JPopupMenu();
        fPopup.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        fPopup.add(fSearchPanel);

        // if the "fParentComp" has registered Esc key, then it gets sent to our parent instead
        // of telling the popupmenu to close itself. to solve that, we register an action for Esc 
        // in our textfield so that *we're* the ones that receive it if our search field has focus.
        fSearchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, true), "CloseFindAction");
        fSearchField.getActionMap().put("CloseFindAction", new AbstractAction() {
            public void actionPerformed(final ActionEvent e) {
                fPopup.setVisible(false);
            }
        });

        // now register the searchfield's listeners so that we react to the user typing
        fSearchField.addKeyListener(this);

        // lastly, register the Ctrl+G (next match) and Ctrl+Shift+G (previous match) convenience shortcuts
        // for people who don't want to use the up/down arrows. just like regular up/down, these search without delay.
        fSearchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK, true), "FindNextAction");
        fSearchField.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_G, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "FindPreviousAction");
        fSearchField.getActionMap().put("FindNextAction", new AbstractAction() {
            public void actionPerformed(final ActionEvent e) {
                changed(Position.Bias.Forward);
            }
        });
        fSearchField.getActionMap().put("FindPreviousAction", new AbstractAction() {
            public void actionPerformed(final ActionEvent e) {
                changed(Position.Bias.Backward);
            }
        });
    }

    protected JComponent fParentComp = null;
    protected boolean fIgnoreCase;

    /*-------------------------------------------------[ ActionListener ]---------------------------------------------------*/
    // --> this is the action that's executed when the user presses the Ctrl+F or Ctrl+Shift+F keybindings.
    // --> it is responsible for starting a new search.

    public void actionPerformed(
            final ActionEvent ae)
    {
        if( ae.getSource() == fSearchField ) {
            // the user pressed the search-keybinding inside of the searchfield itself;
            // this should *never* be able to trigger since we're not listening
            // for the keybinding on the searchfield. if it happens, hide search.
            fPopup.setVisible(false);
        } else {
            // the user pressed the keybinding on the parent component which has
            // had install() called on it. so just save a reference to that component,
            // which is where we'll perform all of our searches for this particular
            // searchfield.
            fParentComp = (JComponent)ae.getSource();

            // if they started the search popup with Ctrl+Shift+F (instead of Ctrl+F),
            // then we'll perform a CASE-SENSITIVE search
            fIgnoreCase = ( (ae.getModifiers() & ActionEvent.SHIFT_MASK) == 0 );

            // precaution: remove ourselves as listeners for our searchfield document before init
            // to avoid needless triggering when we initialize the searchfield.
            fSearchField.getDocument().removeDocumentListener(this);

            // prepare the initial text and style of the search field
            try {
                // wrapped in a try-statement since we don't know if we can trust the sub-implementation
                initSearch(ae);
            } catch( final Throwable t ) {}

            // now add ourselves back as listeners for our searchfield popup so that we react to changes
            fSearchField.getDocument().addDocumentListener(this);

            // place the popup above the top left corner of the parent component
            Rectangle rect = fParentComp.getVisibleRect();
            fPopup.show(fParentComp, rect.x, rect.y - fPopup.getPreferredSize().height - 2);
            // WARNING/FIXME: there are bugs in Java on Linux which may cause the focus request to
            // block all keyboard input to the rest of the application. I've gotten rid of all uses
            // of "requestFocus" here except this unavoidable one, which is required in order to set the
            // kbdfocus to the searchfield when the search-popup appears. there's still a risk that
            // this call will freeze Frost's keyboard input, but that's unavoidable and would have
            // to be fixed by Oracle in Java itself. --Kitty
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if( fPopup.isVisible() && fSearchField.isVisible() ) {
                        // --> THIS IS THE VITAL STATEMENT WHICH UNFORTUNATELY MAY FREEZE FROST'S KBD INPUT
                        fSearchField.requestFocusInWindow(); // ensure the search field has focus
                    }
                }
            });
        }
    }

    // can be overridden by subclasses to change initial search text of a new search.
    protected void initSearch(
            final ActionEvent ae)
    {
        fSearchField.setText(""); //NOI18N
        fSearchField.setForeground(Color.BLACK);
    }

    /*-------------------------------------------------[ DocumentListener ]---------------------------------------------------*/
    // --> When the user types in the search-field, we run an updated search without specifying search direction.
    // It's up to the subclass to determine what that means; but usually it just means to do a forwards-search.

    // waits for 200ms of inactivity after the last-typed letter before performing a search.
    // reduces the stress on the search-system whenever the user is trying to type a long query.
    private void performDelayedSearch()
    {
        delayedSearchWorker.schedule(200, new Runnable() {
            @Override
            public void run() {
                // we must invokeLater the search since the worker is a non-GUI thread
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        changed(null);
                    }
                });
            }
        });
    }

    public void insertUpdate(DocumentEvent e)
    {
        performDelayedSearch();
    }

    public void removeUpdate(DocumentEvent e)
    {
        performDelayedSearch();
    }

    public void changedUpdate(DocumentEvent e){} // ignore text attribute changes


    /*-------------------------------------------------[ KeyListener ]---------------------------------------------------*/
    // --> Responsible for taking care of backwards/forwards search via the up/down-arrow keys.

    public void keyPressed(
            final KeyEvent ke)
    {
        // if they want to navigate up/down in the results, we'll perform the search instantly
        switch( ke.getKeyCode() ) {
            case KeyEvent.VK_UP: // user presses up-arrow in search field; search backwards
                changed(Position.Bias.Backward);
                break;
            case KeyEvent.VK_DOWN: // user presses down-arrow in search field; search forwards
                changed(Position.Bias.Forward);
                break;
        }
    }

    public void keyTyped(KeyEvent e){}
    public void keyReleased(KeyEvent e){}


    /*-------------------------------------------------[ Search Handler ]---------------------------------------------------*/
    // --> Performs the search based on the text in the search field and the direction-bias.

    // the internal function called whenever the search terms or direction changes
    private void changed(
            final Position.Bias aBias)
    {
        // abort if the popup is hidden (can happen if we were deferred via the single-worker queue)
        if( !fPopup.isVisible() ) { return; }

        // perform the search and color the searchfield text black if match or red if no matches
        boolean found = false;
        try {
            // wrapped in a try-statement since we don't know if we can trust the sub-implementation
            found = changed(fParentComp, fSearchField.getText(), aBias);
        } catch( final Throwable t ) {}
        fSearchField.setForeground( (found ? Color.BLACK : Color.RED) );
    }

    // implement in your subclasses: should search the "search component" for given text and select
    // item and then return true if the search was successful
    protected abstract boolean changed(final JComponent aSearchComponent, String aSearchText, Position.Bias aBias);


    /*-------------------------------------------------[ Installation ]---------------------------------------------------*/
    // --> Installs the action map (AbstractAction) handler for when the user presses Ctrl+F or Ctrl+Shift+F
    // on that particular component.

    public void install(
            final JComponent aKeyListeningComp)
    {
        aKeyListeningComp.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK, true), "OpenFindAction");
        aKeyListeningComp.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true), "OpenFindAction");
        aKeyListeningComp.getActionMap().put("OpenFindAction", this);
    }
    public void deinstall(
            final JComponent aKeyListeningComp)
    {
        aKeyListeningComp.getInputMap(JComponent.WHEN_FOCUSED).remove(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK, true));
        aKeyListeningComp.getInputMap(JComponent.WHEN_FOCUSED).remove(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK | InputEvent.SHIFT_MASK, true));
        aKeyListeningComp.getActionMap().remove("OpenFindAction");
    }
}
