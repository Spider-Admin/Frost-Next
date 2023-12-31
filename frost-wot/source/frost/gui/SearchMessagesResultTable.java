/*
  SearchMessagesResultTable.java / Frost
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.*;
import javax.swing.table.*;

import frost.*;
import frost.fileTransfer.common.*;
import frost.gui.model.*;
import frost.messaging.frost.*;
import frost.util.gui.TrustStateColors;
import frost.util.gui.*;

@SuppressWarnings("serial")
public class SearchMessagesResultTable
    extends SortedTable<FrostSearchResultMessageObject>
    implements PropertyChangeListener
{

    private final CellRenderer cellRenderer = new CellRenderer();
    private final BooleanCellRenderer booleanCellRenderer = new BooleanCellRenderer();

    private final ImageIcon flaggedIcon = MiscToolkit.loadImageIcon("/data/flag.png");
    private final ImageIcon starredIcon = MiscToolkit.loadImageIcon("/data/star.png");

    private final ImageIcon messageDummyIcon = MiscToolkit.loadImageIcon("/data/messagedummyicon.gif");
    private final ImageIcon messageNewIcon = MiscToolkit.loadImageIcon("/data/messagenewicon.gif");
    private final ImageIcon messageReadIcon = MiscToolkit.loadImageIcon("/data/messagereadicon.gif");
    private final ImageIcon messageNewRepliedIcon = MiscToolkit.loadImageIcon("/data/messagenewrepliedicon.gif");
    private final ImageIcon messageReadRepliedIcon = MiscToolkit.loadImageIcon("/data/messagereadrepliedicon.gif");

    private final boolean showColoredLines;

    private Color fMsgNormalColor;
    private Color fMsgPrivColor;
    private Color fMsgWithAttachmentsColor;
    private Color fMsgUnsignedColor;

    public SearchMessagesResultTable(final SearchMessagesTableModel m) {
        super(m);

        updateMessageColors();

        setCustomRenderers();

        // default for messages: sort by date descending
        sortedColumnIndex = 5;
        sortedColumnAscending = false;
        resortTable();

        initLayout();

        showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
        MainFrame.getInstance().getMessageColorManager().addPropertyChangeListener(this);
    }

    private void updateMessageColors()
    {
        fMsgNormalColor = MainFrame.getInstance().getMessageColorManager().getNormalColor();
        fMsgPrivColor = MainFrame.getInstance().getMessageColorManager().getPrivColor();
        fMsgWithAttachmentsColor = MainFrame.getInstance().getMessageColorManager().getWithAttachmentsColor();
        fMsgUnsignedColor = MainFrame.getInstance().getMessageColorManager().getUnsignedColor();
    }

    private void setCustomRenderers() {
        if( booleanCellRenderer == null ) { return; } // avoid null renderers during initial startup
        setDefaultRenderer(String.class, cellRenderer);
        setDefaultRenderer(Boolean.class, booleanCellRenderer);
    }

    @Override
    public void updateUI() {
        super.updateUI();

        // re-apply the custom cell renderers (since most L&Fs replace them with default ones)
        setCustomRenderers();
    }

    private void initLayout() {
        final TableColumnModel tcm = getColumnModel();

        // hard set sizes of icons column
        tcm.getColumn(0).setMinWidth(20);
        tcm.getColumn(0).setMaxWidth(20);
        tcm.getColumn(0).setPreferredWidth(20);
        // hard set sizes of icons column
        tcm.getColumn(1).setMinWidth(20);
        tcm.getColumn(1).setMaxWidth(20);
        tcm.getColumn(1).setPreferredWidth(20);

        // set icon table header renderer for icon columns
        tcm.getColumn(0).setHeaderRenderer(new IconTableHeaderRenderer(flaggedIcon));
        tcm.getColumn(1).setHeaderRenderer(new IconTableHeaderRenderer(starredIcon));
    }

    // override the default sort order when clicking different columns
    @Override
    public boolean getColumnDefaultAscendingState(final int col) {
        if( col == 7 ) {
            // sort date column descending by default
            return false;
        }
        return true; // all other columns: ascending
    }

    /**
     * Save the current column positions and column sizes for restore on next startup.
     *
     * @param frostSettings
     */
//    public void saveLayout(SettingsClass frostSettings) {
//        TableColumnModel tcm = getColumnModel();
//        for(int columnIndexInTable=0; columnIndexInTable < tcm.getColumnCount(); columnIndexInTable++) {
//            TableColumn tc = tcm.getColumn(columnIndexInTable);
//            int columnIndexInModel = tc.getModelIndex();
//            // save the current index in table for column with the fix index in model
//            frostSettings.setValue("messagetable.tableindex.modelcolumn."+columnIndexInModel, columnIndexInTable);
//            // save the current width of the column
//            int columnWidth = tc.getWidth();
//            frostSettings.setValue("messagetable.columnwidth.modelcolumn."+columnIndexInModel, columnWidth);
//        }
//    }

    /**
     * Load the saved column positions and column sizes.
     *
     * @param frostSettings
     */
//    public void loadLayout(SettingsClass frostSettings) {
//        TableColumnModel tcm = getColumnModel();
//
//        // load the saved tableindex for each column in model, and its saved width
//        int[] tableToModelIndex = new int[tcm.getColumnCount()];
//        int[] columnWidths = new int[tcm.getColumnCount()];
//
//        for(int x=0; x < tableToModelIndex.length; x++) {
//            String indexKey = "messagetable.tableindex.modelcolumn."+x;
//            if( frostSettings.getObjectValue(indexKey) == null ) {
//                return; // column not found, abort
//            }
//            // build array of table to model associations
//            int tableIndex = frostSettings.getIntValue(indexKey);
//            if( tableIndex < 0 || tableIndex >= tableToModelIndex.length ) {
//                return; // invalid table index value
//            }
//            tableToModelIndex[tableIndex] = x;
//
//            String widthKey = "messagetable.columnwidth.modelcolumn."+x;
//            if( frostSettings.getObjectValue(widthKey) == null ) {
//                return; // column not found, abort
//            }
//            // build array of table to model associations
//            int columnWidth = frostSettings.getIntValue(widthKey);
//            if( columnWidth <= 0 ) {
//                return; // invalid column width
//            }
//            columnWidths[x] = columnWidth;
//        }
//        // columns are currently added in model order, remove them all and save in an array
//        // while on it, set the loaded width of each column
//        TableColumn[] tcms = new TableColumn[tcm.getColumnCount()];
//        for(int x=tcms.length-1; x >= 0; x--) {
//            tcms[x] = tcm.getColumn(x);
//            tcm.removeColumn(tcms[x]);
//
//            tcms[x].setPreferredWidth(columnWidths[x]);
//        }
//        // add the columns in order loaded from settings
//        for(int x=0; x < tableToModelIndex.length; x++) {
//            tcm.addColumn(tcms[tableToModelIndex[x]]);
//        }
//    }

    /**
     * This renderer renders rows in different colors.
     * New messages gets a bold look, messages with attachments a blue color.
     * Encrypted messages get a red color, no matter if they have attachments.
     */
    private class CellRenderer extends DefaultTableCellRenderer {

        private Font boldFont = null;
        private Font normalFont = null;
        private boolean isDeleted = false;
        private final Color col_BAD       = TrustStateColors.BAD;
        private final Color col_NEUTRAL   = TrustStateColors.NEUTRAL;
        private final Color col_GOOD      = TrustStateColors.GOOD;
        private final Color col_FRIEND    = TrustStateColors.FRIEND;

        public CellRenderer() {
            final Font baseFont = SearchMessagesResultTable.this.getFont();
            normalFont = baseFont.deriveFont(Font.PLAIN);
            boldFont = baseFont.deriveFont(Font.BOLD);
        }

        @Override
        public void paintComponent (final Graphics g) {
            super.paintComponent(g);
            if(isDeleted) {
                final Dimension size = getSize();
                g.drawLine(0, size.height / 2, size.width, size.height / 2);
            }
        }

        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            boolean isSelected,
            final boolean hasFocus,
            final int row,
            int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            final SearchMessagesTableModel model = (SearchMessagesTableModel) getModel();
            final FrostSearchResultMessageObject msg = (FrostSearchResultMessageObject) model.getRow(row);

            // get the original model column index (maybe columns were reordered by user)
            final TableColumn tableColumn = getColumnModel().getColumn(column);
            column = tableColumn.getModelIndex();

            setIcon(null);
            setToolTipText(null);

            if (!isSelected) {
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                setBackground(newBackground);
            }

            // do nice things for FROM, SUBJECT, SIG and BOARD columns
            if( column == 3 ) {
                // FROM
                // first set font, bold for new msg or normal
                // NOTE: we don't make the actual "subject" column bold too, since most people will have
                // a majority of unread messages in their search results, so it's cleaner to just make
                // the "from" column bold instead
                if (msg.getMessageObject().isNew()) {
                    setFont(boldFont);
                } else {
                    setFont(normalFont);
                }
                setToolTipText(msg.getMessageObject().getFromName());

//            } else if( column == 2 ) {
//                // BOARD - gray for archived msgs
//                setFont(normalFont);
//                if (!isSelected) {
//                    if( msg.getMessageObject().isMessageArchived() ) {
//                        setForeground(Color.GRAY);
//                    } else {
//                        setForeground(Color.BLACK);
//                    }
//                }
            } else if( column == 5 ) {
                // SUBJECT
                ImageIcon icon;
                if( msg.getMessageObject().isDummy() ) {
                    icon = messageDummyIcon;
                } else if( msg.getMessageObject().isNew() ) {
                    if( msg.getMessageObject().isReplied() ) {
                        icon = messageNewRepliedIcon;
                    } else {
                        icon = messageNewIcon;
                    }
                } else {
                    if( msg.getMessageObject().isReplied() ) {
                        icon = messageReadRepliedIcon;
                    } else {
                        icon = messageReadIcon;
                    }
                }
                setIcon(icon);
                setToolTipText(msg.getMessageObject().getSubject());
            } else if( column == 6 ) {
                // SIG
                // state == BAD/NEUTRAL/GOOD/FRIEND -> bold and colored
                if( msg.getMessageObject().isMessageStatusNEUTRAL() ) {
                    setFont(boldFont);
                    setForeground(col_NEUTRAL);
                } else if( msg.getMessageObject().isMessageStatusGOOD() ) {
                    setFont(boldFont);
                    setForeground(col_GOOD);
                } else if( msg.getMessageObject().isMessageStatusFRIEND() ) {
                    setFont(boldFont);
                    setForeground(col_FRIEND);
                } else if( msg.getMessageObject().isMessageStatusBAD() ) {
                    setFont(boldFont);
                    setForeground(col_BAD);
                } else if( msg.getMessageObject().isMessageStatusTAMPERED() ) {
                    setFont(boldFont);
                    setForeground(col_BAD);
                } else {
                    setFont(normalFont);
                    if (!isSelected) {
                        setForeground(Color.BLACK);
                    }
                }
            } else {
                setFont(normalFont);
                if (!isSelected) {
                    setForeground(Color.BLACK);
                }
            }

            // now set color of the "from" and "subject" columns based on message type
            if( column == 3 || column == 5 ) {
                if( !isSelected ) {
                    // color priority: private message > has attachments > unsigned/anonymous
                    if( msg.getMessageObject().getRecipientName() != null && msg.getMessageObject().getRecipientName().length() > 0 ) {
                        setForeground(fMsgPrivColor);
                    } else if( msg.getMessageObject().containsAttachments() ) {
                        setForeground(fMsgWithAttachmentsColor);
                    } else if( !msg.getMessageObject().isSignatureStatusVERIFIED() ) {
                        setForeground(fMsgUnsignedColor);
                    } else {
                        setForeground(fMsgNormalColor);
                    }
                }
            }


            setDeleted(msg.getMessageObject().isDeleted());

            return this;
        }

        /* (non-Javadoc)
         * @see java.awt.Component#setFont(java.awt.Font)
         */
        @Override
        public void setFont(final Font font) {
            super.setFont(font);
            normalFont = font.deriveFont(Font.PLAIN);
            boldFont = font.deriveFont(Font.BOLD);
        }

        public void setDeleted(final boolean value) {
            isDeleted = value;
        }
    }

    private class BooleanCellRenderer extends JLabel implements TableCellRenderer {

        public BooleanCellRenderer() {
            super();
            setHorizontalAlignment(CENTER);
            setVerticalAlignment(CENTER);
        }

        @Override
        public void paintComponent (final Graphics g) {
            final Dimension size = getSize();
            g.setColor(getBackground());
            g.fillRect(0, 0, size.width, size.height);
            super.paintComponent(g);
        }

        public Component getTableCellRendererComponent(
                final JTable table,
                final Object value,
                boolean isSelected,
                final boolean hasFocus,
                final int row,
                int column)
        {
            final boolean val = ((Boolean)value).booleanValue();

            // get the original model column index (maybe columns were reordered by user)
            final TableColumn tableColumn = getColumnModel().getColumn(column);
            column = tableColumn.getModelIndex();

            if( column == 0 ) {
                if( val ) {
                    setIcon(flaggedIcon);
                } else {
                    setIcon(null);
                }
            } else if( column == 1 ) {
                if( val ) {
                    setIcon(starredIcon);
                } else {
                    setIcon(null);
                }
            }

            if (!isSelected) {
                final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
                setBackground(newBackground);
            } else {
                setBackground(table.getSelectionBackground());
            }
            return this;
        }
    }

    @Override
    public void createDefaultColumnsFromModel() {
        super.createDefaultColumnsFromModel();

        // set column sizes
        final int[] widths = { 20, 20, 30, 125, 80, 250, 75, 150 };
        for (int i = 0; i < widths.length; i++) {
            getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    @Override
    public void setFont(final Font font) {
        super.setFont(font);
        if (cellRenderer != null) {
            cellRenderer.setFont(font);
        }
        setRowHeight(font.getSize() + 5);
    }

    @Override
    public void propertyChange(final PropertyChangeEvent evt) {
        if( evt.getPropertyName().equals("MessageColorsChanged") ) {
            updateMessageColors();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    // executed on swing since we can't guarantee that the event came from EDT
                    // NOTE: this clears the user's selection but maintains the scroll position
                    final SearchMessagesTableModel model = (SearchMessagesTableModel) getModel();
                    model.fireTableDataChanged(); // update all visible rows
                }
            });
        }
    }
}
