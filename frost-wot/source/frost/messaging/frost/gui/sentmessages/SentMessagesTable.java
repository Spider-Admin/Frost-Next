/*
  SendMessagesTable.java / Frost
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
package frost.messaging.frost.gui.sentmessages;

import java.awt.Component;
import java.awt.Font;
import java.awt.Point;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;

import javax.swing.*;

import frost.*;
import frost.messaging.frost.*;
import frost.messaging.frost.gui.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;
import frost.util.model.*;

@SuppressWarnings("serial")
public class SentMessagesTable extends SortedModelTable<SentMessagesTableItem> implements PropertyChangeListener {

    private final SentMessagesTableModel tableModel;
    private final SentMessagesTableFormat tableFormat;

    private PopupMenuSearch popupMenuSearch = null;
    private final Language language = Language.getInstance();

    private final int MINIMUM_ROW_HEIGHT = 20;
    private final int ROW_HEIGHT_MARGIN = 4;

    public SentMessagesTable() {
        this(new SentMessagesTableModel(new SentMessagesTableFormat()));
    }

    private SentMessagesTable(final SentMessagesTableModel m) {
        super(m);
        tableModel = m;
        tableFormat = (SentMessagesTableFormat)m.getTableFormat();

        setupTableFont();
        getTable().setBorder(BorderFactory.createEmptyBorder(2,2,2,2));

        final Listener l = new Listener();
        getTable().addMouseListener(l);
        getScrollPane().addMouseListener(l);

        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_NAME, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_SIZE, this);
        Core.frostSettings.addPropertyChangeListener(SettingsClass.MESSAGE_LIST_FONT_STYLE, this);
    }

    // override the default sort order when clicking different columns
    @Override
    public boolean getColumnDefaultAscendingState(final int columnNumber) {
        if( columnNumber == 4 ) {
            // sort date descending by default
            return false;
        }
        return true; // all other columns: ascending
    }

    public void addSentMessage(final FrostMessageObject i) {
        tableModel.addFrostMessageObject(i);
        MainFrame.getInstance().getFrostMessageTab().getSentMessagesPanel().updateSentMessagesCount();
    }

    public void saveTableFormat() {
        tableFormat.saveTableLayout();
    }

    public void loadTableModel() {
        tableModel.loadTableModel();
        MainFrame.getInstance().getFrostMessageTab().getSentMessagesPanel().updateSentMessagesCount();
    }

    public void clearTableModel() {
        tableModel.clear();
    }

    private PopupMenuSearch getPopupMenuSearch() {
        if (popupMenuSearch == null) {
            popupMenuSearch = new PopupMenuSearch();
            language.addLanguageListener(popupMenuSearch);
        }
        return popupMenuSearch;
    }

    private void setupTableFont() {
        final String fontName = Core.frostSettings.getValue(SettingsClass.MESSAGE_LIST_FONT_NAME);
        final int fontStyle = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_STYLE);
        final int fontSize = Core.frostSettings.getIntValue(SettingsClass.MESSAGE_LIST_FONT_SIZE);
        Font font = new Font(fontName, fontStyle, fontSize);
        if (!font.getFamily().equals(fontName)) {
            // font not found on the system, fall back to monospaced
            Core.frostSettings.setValue(SettingsClass.MESSAGE_LIST_FONT_NAME, "Monospaced");
            font = new Font("Monospaced", fontStyle, fontSize);
        }
        // adjust row height to font size, add a margin
        getTable().setRowHeight(Math.max(fontSize + ROW_HEIGHT_MARGIN, MINIMUM_ROW_HEIGHT));
        // set the font (invalidates the table and forces a repaint)
        getTable().setFont(font);
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_NAME)) {
            setupTableFont();
        } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_SIZE)) {
            setupTableFont();
        } else if (evt.getPropertyName().equals(SettingsClass.MESSAGE_LIST_FONT_STYLE)) {
            setupTableFont();
        }
    }

    private void tableDoubleClick(final MouseEvent e) {

        final int row = getTable().rowAtPoint(e.getPoint());
        if( row > -1 ) {
            final SentMessagesTableItem item = getItemAt(row); //It may be null
            if (item != null) {
                final FrostMessageObject sm = item.getFrostMessageObject();
                final MessageWindow messageWindow = new MessageWindow(
                        MainFrame.getInstance(),
                        sm,
                        MainFrame.getInstance().getFrostMessageTab().getSentMessagesPanel().getSize(),
                        false);
                messageWindow.setVisible(true);
            }
        }
    }

    private class Listener extends MouseAdapter implements MouseListener {

        public Listener() {
            super();
        }

        @Override
        public void mousePressed(final MouseEvent e) {

            if (e.getClickCount() == 2) {
                if (e.getSource() == getTable()) {
                    tableDoubleClick(e);
                }
            } else if (e.isPopupTrigger()) {
                if ((e.getSource() == getTable())
                    || (e.getSource() == getScrollPane())) {
                    showSearchTablePopupMenu(e);
                }
            }
        }

        @Override
        public void mouseReleased(final MouseEvent e) {
            if ((e.getClickCount() == 1) && (e.isPopupTrigger())) {

                if ((e.getSource() == getTable())
                    || (e.getSource() == getScrollPane())) {
                    showSearchTablePopupMenu(e);
                }
            }
        }

        private void showSearchTablePopupMenu(final MouseEvent e) {
            // select row where rightclick occurred if row under mouse is NOT selected
            final Point p = e.getPoint();
            final int y = getTable().rowAtPoint(p);
            if( y < 0 ) {
                return;
            }
            if( !getTable().getSelectionModel().isSelectedIndex(y) ) {
                getTable().getSelectionModel().setSelectionInterval(y, y);
            }
            getPopupMenuSearch().show(e.getComponent(), e.getX(), e.getY());
        }
    }

    private class PopupMenuSearch extends JSkinnablePopupMenu implements ActionListener, LanguageListener {

        JMenuItem deleteItem = new JMenuItem();

        public PopupMenuSearch() {
            super();
            initialize();
        }

        private void initialize() {
            refreshLanguage();

            deleteItem.addActionListener(this);
        }

        private void refreshLanguage() {
            deleteItem.setText(language.getString("SentMessages.table.popup.deleteMessage"));
        }

        public void actionPerformed(final ActionEvent e) {
            if (e.getSource() == deleteItem) {
                deleteSelectedMessages();
            }
        }

        private void deleteSelectedMessages() {
            final List<SentMessagesTableItem> selectedItems = getSelectedItems();
            if( selectedItems == null || selectedItems.size() == 0 ) {
                return;
            }
            int answer;
            if( selectedItems.size() == 1 ) {
                answer = MiscToolkit.showConfirmDialog(
                        null,
                        language.getString("SentMessages.confirmDeleteOneMessageDialog.text"),
                        language.getString("SentMessages.confirmDeleteOneMessageDialog.title"),
                        MiscToolkit.YES_NO_OPTION,
                        MiscToolkit.QUESTION_MESSAGE);
            } else {
                answer = MiscToolkit.showConfirmDialog(
                        null,
                        language.formatMessage("SentMessages.confirmDeleteMessagesDialog.text", Integer.toString(selectedItems.size())),
                        language.getString("SentMessages.confirmDeleteMessagesDialog.title"),
                        MiscToolkit.YES_NO_OPTION,
                        MiscToolkit.QUESTION_MESSAGE);
            }

            if( answer != MiscToolkit.YES_OPTION ) {
                return;
            }

            tableModel.removeItems(selectedItems);
            MainFrame.getInstance().getFrostMessageTab().getSentMessagesPanel().updateSentMessagesCount();
        }

        public void languageChanged(final LanguageEvent event) {
            refreshLanguage();
        }

        public void show(final Component invoker, final int x, final int y) {
            removeAll();

            final List<SentMessagesTableItem> selectedItems = getSelectedItems();
            if( selectedItems == null || selectedItems.size() == 0 ) {
                return;
            }

            add(deleteItem);

            super.show(invoker, x, y);
        }
    }
}
