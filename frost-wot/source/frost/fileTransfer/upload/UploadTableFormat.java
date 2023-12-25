/*
  UploadTableFormat.java / Frost
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
package frost.fileTransfer.upload;

import java.awt.*;
import java.beans.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.*;
import javax.swing.SwingUtilities;
import javax.swing.table.*;

import frost.*;
import frost.fileTransfer.*;
import frost.fileTransfer.BlocksPerMinuteCounter;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.common.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;
import frost.util.model.*;

class UploadTableFormat extends SortedTableFormat<FrostUploadItem> implements LanguageListener, PropertyChangeListener {

    private static final String CFGKEY_SORTSTATE_SORTEDCOLUMN = "UploadTable.sortState.sortedColumn";
    private static final String CFGKEY_SORTSTATE_SORTEDASCENDING = "UploadTable.sortState.sortedAscending";
    private static final String CFGKEY_COLUMN_TABLEINDEX = "UploadTable.tableindex.modelcolumn.";
    private static final String CFGKEY_COLUMN_WIDTH = "UploadTable.columnwidth.modelcolumn.";

//#DIEFILESHARING    private static ImageIcon isSharedIcon = MiscToolkit.loadImageIcon("/data/shared.png");

    private SortedModelTable<FrostUploadItem> modelTable = null;

    private boolean showColoredLines;

    private Set<Integer> fixedsizeColumns; // in "all columns" index space, default model ordering

    /**
     * Renders DONE with green background, FAILED with red background and PAUSED with blue background.
     */
    @SuppressWarnings("serial")
	private class BaseRenderer extends DefaultTableCellRenderer {
        public BaseRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {

            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

            if( !isSelected ) {
                Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);

                final FrostUploadItem uploadItem = modelTable.getItemAt(row);
                if (uploadItem != null) {
                    final int itemState = uploadItem.getState();
                    if( itemState == FrostUploadItem.STATE_DONE) {
                        newBackground = TableBackgroundColors.getBackgroundColorDone(table, row, showColoredLines);
                    } else if( itemState == FrostUploadItem.STATE_FAILED) {
                        newBackground = TableBackgroundColors.getBackgroundColorFailed(table, row, showColoredLines);
                    } else if( !uploadItem.isEnabled() || uploadItem.getPriority() == FreenetPriority.PAUSE ) {
                        // if disabled or prio 6 (paused) in any of the other states: progress/waiting/encoding/encode requested
                        newBackground = TableBackgroundColors.getBackgroundColorPaused(table, row, showColoredLines);
                    }
                }
                setBackground(newBackground);
                setForeground(Color.black);
            }
            return this;
        }
    }

    @SuppressWarnings("serial")
	private class BlocksProgressRenderer extends JProgressBar implements TableCellRenderer {
        public BlocksProgressRenderer() {
            super();
            setMinimum(0);
            setMaximum(100);

            // we must now apply the user's L&F font and set it on the progress bar, otherwise
            // some L&Fs won't render the string despite "setStringPainted(true)". we must also
            // sanitize the font size so that it cannot go above 20, since some L&Fs refuse to
            // render progress bars with huge fonts (i.e. GTK L&F doesn't work above 22).
            // we also always subtract 1pt from the value, since the progressbar refuses to render
            // certain fonts if we don't do this (probably because they don't fit the rowheight).
            final String fontName = Core.frostSettings.getValue(SettingsClass.FILE_LIST_FONT_NAME);
            int fontSize = Core.frostSettings.getIntValue(SettingsClass.FILE_LIST_FONT_SIZE);
            fontSize -= 1;
            if( fontSize < 1 ) { fontSize = 1; }
            else if( fontSize > 20 ) { fontSize = 20; }
            Font font = new Font(fontName, Font.PLAIN, fontSize);
            if( !font.getFamily().equals(fontName) ) {
                // if the user's font wasn't found, fall back to the regular OS-dependent sans-serif font
                font = new Font("SansSerif", Font.PLAIN, fontSize);
            }

            // NOTE/FIXME: we only set this on initial construction at Frost startup, NOT when the user
            // changes their font setting, because setFont() updates on the table do not propagate to cell
            // renderers. would be annoyingly complicated to fix. the only workaround is to restart Frost.
            setFont(font);

            // now just enable string painting so that the user will see the block count.
            setStringPainted(true);
            setBorderPainted(false);
        }
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {

            final Color newBackground = TableBackgroundColors.getBackgroundColor(table, row, showColoredLines);
            setBackground(newBackground);

            // format: ~0% 0/60 [60]
            final FrostUploadItem uploadItem = modelTable.getItemAt(row);
            setValue(calculatePercentDone(uploadItem));
            setString(value.toString());

            return this;
        }
    }

    // calculates an integer percentage from 0 to 100, for use in progress bars and sorting, etc
    private static int calculatePercentDone(final FrostUploadItem ulItem) {
        int percentDone = 0;
        if( ulItem != null ) {
            if( ulItem.getState() == FrostUploadItem.STATE_DONE ) {
                // items marked "Finished" are always 100%, especially important
                // since they may have finished during another Frost session,
                // in which case we don't know their block count anymore.
                percentDone = 100;
            } else {
                // calculate the percentage...
                final int totalBlocks = ulItem.getTotalBlocks();
                if( totalBlocks > 0 ) { // is only non-zero if transfer has begun
                    final int doneBlocks = ulItem.getDoneBlocks();
                    // NOTE: because we're dealing with integer truncation, we need to multiply
                    // *before* the division; so 4/6 blocks -> 400/6 -> 66 percent.
                    percentDone = (doneBlocks * 100) / totalBlocks;
                    if( percentDone > 100 ) {
                        percentDone = 100;
                    }
                }
            }
        }
        return percentDone;
    }

    @SuppressWarnings("serial")
	private class ShowContentTooltipRenderer extends BaseRenderer {
        public ShowContentTooltipRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String tooltip = null;
            if( value != null ) {
                tooltip = value.toString();
                if( tooltip.length() == 0 ) {
                    tooltip = null;
                }
            }
            setToolTipText(tooltip);
            return this;
        }
    }

    @SuppressWarnings("serial")
	private class ShowNameTooltipRenderer extends BaseRenderer {

        public ShowNameTooltipRenderer() {
            super();
        }

        // deferred tooltip generation: this avoids re-generating the tooltip every time we receive
        // an FCP message, since the regular cell renderer constructor is called every time the row
        // updates. we instead only generate the tooltips when the user actually hovers over the cell.
        @Override
        public String getToolTipText(final MouseEvent ev) {
            // determine which row the user is hovering over; note that the table and model rows correspond 1:1 even with sorting applied
            final JTable theTable = modelTable.getTable();
            final Point p = ev.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(p, theTable); // directly updates "p"
            final int row = theTable.rowAtPoint(p);
            //not used: final int col = theTable.columnAtPoint(p);
            //not used: final Object value = modelTable.getValueAt(row, col); // may be null
            final FrostUploadItem uploadItem = modelTable.getItemAt(row); // may be null

            // dynamically generate the tooltip
            if (uploadItem != null) {
                // this row contains a download item, so build a summary tooltip for that transfer
                final StringBuilder sb = new StringBuilder();
                sb.append("<html>").append(uploadItem.getFileName());
                if( uploadItem.getUploadAddedMillis() > 0 ) {
                    sb.append("<br>Added: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(uploadItem.getUploadAddedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(uploadItem.getUploadAddedMillis()));
                }
                if( uploadItem.getUploadStartedMillis() > 0 ) {
                    sb.append("<br>Started: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(uploadItem.getUploadStartedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(uploadItem.getUploadStartedMillis()));
                }
                if( uploadItem.getUploadFinishedMillis() > 0 ) {
                    sb.append("<br>Finished: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(uploadItem.getUploadFinishedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(uploadItem.getUploadFinishedMillis()));
                }
                // if this transfer has begun and is unfinished, then display various statistics such as
                // block counter, blocks-per-minute, time estimate, "elapsed" and "last activity" statistics
                if( uploadItem.getState() == FrostUploadItem.STATE_PROGRESS ) {
                    // add the "blocks done/total percentage" display, for people whose look&feel
                    // is rendering their blocks column as a solid line instead of showing the text
                    String blocksAsString = getBlocksAsString(uploadItem);
                    if( !blocksAsString.equals("") ) { // is empty string until the transfer actually begins
                        sb.append("<br>Blocks: "+blocksAsString);
                    }

                    // if nothing has been transferred yet, or if we still need 1+ blocks, then
                    // display the blocks/minute counter and the time since last activity
                    final int doneBlocks = uploadItem.getDoneBlocks();
                    final int totalBlocks = uploadItem.getTotalBlocks();
                    final int neededBlocks = ( totalBlocks - doneBlocks );
                    if( doneBlocks == 0 || neededBlocks > 0 ) {
                        // determine how long ago the current measurement was updated
                        final long millisSinceLastMeasurement = uploadItem.getMillisSinceLastMeasurement();

                        // display the blocks/minute, the size in human readable bytes, and the
                        // number of hours/minutes/seconds elapsed
                        sb.append("<br>Blocks/Minute: ");
                        if( millisSinceLastMeasurement > 240000 ) {
                            // the transfer's speed counter has not been updated in 4 minutes since
                            // measurement began; treat this as a stalled upload
                            sb.append("Stalled...");
                        } else {
                            // get the current average number of blocks per minute, and what that corresponds to in bytes
                            final double blocksPerMinute = uploadItem.getAverageBlocksPerMinute();
                            final long bpmInBytesPerSecond = uploadItem.getAverageBytesPerSecond();

                            if( blocksPerMinute < 0 ) {
                                // either "-1" (measuring activity), or "-2" (no recent transfer
                                // activity in the last X minutes), so no measurement is available
                                // NOTE: this will only be triggered for newly started transfers, in
                                // the time from 20 seconds to 4 minutes, before the "stalled"
                                // indicator shows up
                                if( millisSinceLastMeasurement > 20000 ) { // shown between seconds 20-60
                                    sb.append("No activity...");
                                } else { // shown between seconds 0-20 or until a measurement comes in (whichever comes first)
                                    sb.append("Measuring...");
                                }
                            } else {
                                // fantastic! there is a recent average available (less than 4 minutes old); display it!
                                sb.append(String.format("%.2f", blocksPerMinute) + " (~" + Mixed.convertBytesToHuman(bpmInBytesPerSecond) + "/s)");

                                // calculate the estimated time until completion, if we know how many blocks the file contains
                                if( totalBlocks > 0 ) {
                                    final long estimatedMillisRemaining = uploadItem.getEstimatedMillisRemaining(
                                            doneBlocks,
                                            totalBlocks,
                                            BlocksPerMinuteCounter.TRANSFERTYPE_UPLOAD);

                                    // only display the time estimate when it's at least 1 second remaining
                                    if( estimatedMillisRemaining >= 1000 ) {
                                        sb.append("<br>Rough Estimate: " + Mixed.convertMillisToHMmSs(estimatedMillisRemaining, "%dh, %dm, %ds remaining"));
                                    }
                                }
                            }
                        }

                        // if we know the last activity time for this item (regardless of internal or external), then display it
                        sb.append("<br>Last Activity: ");
                        final long lastActivityMillis = uploadItem.getLastActivityMillis();
                        if( lastActivityMillis > 0 ) {
                            final long timeElapsed = ( System.currentTimeMillis() - lastActivityMillis );
                            sb.append(Mixed.convertMillisToHMmSs(timeElapsed, "%dh, %dm, %ds ago"));
                        } else {
                            // never seen any activity (or this is external so it wasn't stored, and none seen this session).
                            // there's no good description for it, although the "Blocks/Minute" counter
                            // above it will also be stalled and make it very clear why there's no activity here.
                            sb.append("...");
                        }
                    }

                    // if this is an internal item, then show how long the transfer has been in progress.
                    // isn't shown for external items, since they don't have a known start time.
                    if( uploadItem.getUploadStartedMillis() > 0 ) {
                        final long timeElapsed = ( System.currentTimeMillis() - uploadItem.getUploadStartedMillis() );
                        sb.append("<br>Elapsed: " + Mixed.convertMillisToHMmSs(timeElapsed, "%dh, %dm, %ds"));
                    }
                }
                sb.append("</html>");
                return sb.toString();
            }
            return null; // no tooltip
        }
    }

    @SuppressWarnings("serial")
	private class ShowStateContentTooltipRenderer extends BaseRenderer {
        public ShowStateContentTooltipRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String tooltip = null;
            final FrostUploadItem uploadItem = modelTable.getItemAt(row); //It may be null
            String errorCodeDescription = getFailureString(uploadItem, /*escapeTags=*/true);
            if( errorCodeDescription != null ) {
                // if this *isn't* a Frost-MD5 failure reason, then break the possibly-long error code sentences into separate lines
                if( errorCodeDescription.indexOf("[MD5]") < 0 ) {
                    errorCodeDescription = errorCodeDescription
                        .replaceAll("[\\r\\n]+", "<br>") // 1 or more line separators
                        .replaceAll("([.?!])\\s+", "$1<br>"); // end-of-sentence punctuation followed by whitespace
                }
                tooltip = "<html><b>Latest Error:</b><br>"+errorCodeDescription+"</html>";
            }
            setToolTipText(tooltip);
            return this;
        }
    }

    @SuppressWarnings("serial")
	private class IsEnabledRenderer extends JCheckBox implements TableCellRenderer {
        public IsEnabledRenderer() {
            super();
        }
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column)
        {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }

            final FrostUploadItem uploadItem = modelTable.getItemAt(row); //It may be null
            if (uploadItem != null) {
                if( uploadItem.isExternal() || uploadItem.getState() == FrostUploadItem.STATE_DONE ) {
                    setEnabled(false); // render the column in a "faded" way
                    setSelected(true); // external items and finished items should always look "enabled"
                } else {
                    setEnabled(true); // render the checkmark in a normal way
                    setSelected((value != null && value instanceof Boolean && ((Boolean) value).booleanValue()));
                }
            }
            return this;
        }
    }

    @SuppressWarnings("serial")
	private class IsCompressRenderer extends JCheckBox implements TableCellRenderer {
        public IsCompressRenderer() {
            super();
            setHorizontalAlignment(JLabel.CENTER); // we want the checkboxes in the middle of the column
        }
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column)
        {
            if (isSelected) {
                setForeground(table.getSelectionForeground());
                super.setBackground(table.getSelectionBackground());
            } else {
                setForeground(table.getForeground());
                setBackground(table.getBackground());
            }

            final FrostUploadItem uploadItem = modelTable.getItemAt(row); //It may be null
            if (uploadItem != null) {
                setEnabled(false); // render the "compress" column in a faded way, since Freenet
                                   // won't let you change compression of ongoing uploads.
                setSelected((value != null && value instanceof Boolean && ((Boolean) value).booleanValue()));
            }
            return this;
        }
    }

    /* //#DIEFILESHARING: This entire block has been commented out since filesharing is removed in Frost-Next.
    @SuppressWarnings("serial")
	private class IsSharedRenderer extends BaseRenderer {
        public IsSharedRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText("");
            setToolTipText(isSharedTooltip);
            if (value instanceof Boolean) {
                final Boolean b = (Boolean)value;
                if( b.booleanValue() ) {
                    // show shared icon
                    setIcon(isSharedIcon);
                } else {
                    setIcon(null);
                }
            }
            return this;
        }
    }
    */

    /**
     * This inner class implements the renderer for the column "FileSize"
     */
    @SuppressWarnings("serial")
	private class RightAlignRenderer extends BaseRenderer {
        final javax.swing.border.EmptyBorder border = new javax.swing.border.EmptyBorder(0, 0, 0, 3);
        public RightAlignRenderer() {
            super();
        }
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.RIGHT);
            // col is right aligned, give some space to next column
            setBorder(border);
            return this;
        }
    }

    private static class ColumnComparator
            implements Comparator<FrostUploadItem>
    {
        public static enum Type {
            // "String"-based columns
            FILENAME(0), PATH(0), KEY(0), COMPAT_MODE(0), CRYPTOKEY(0),
            // "boolean"-based columns
            ENABLED(1), COMPRESS(1),
            // "long"-based columns
            FILESIZE(2),
            // "int"-based columns
            STATE(3), BLOCKS(3), TRIES(3), PRIORITY(3);

            private final int fObjType;
            Type(int aObjType)
            {
                fObjType = aObjType;
            }

            /**
             * 0 = String, 1 = boolean, 2 = long, 3 = int, 4 = double
             */
            public int getObjType()
            {
                return fObjType;
            }
        }

        /* Instance variables */
        private Type fType;
        private boolean fAscending;

        /**
         * @param {Type} aType - the type of comparator column
         * @param {boolean} aAscending - set to false if you want descending order instead
         * IMPORTANT: THE DOWNLOAD/UPLOAD TABLES DO NOT SUPPORT INVERTED COMPARATORS,
         * SO THIS MUST ALWAYS BE TRUE. To change sort order, override the table's "default
         * ascension state for column" getter instead (which we do!).
         */
        public ColumnComparator(
                final Type aType,
                final boolean aAscending)
        {
            fType = aType;
            fAscending = aAscending;
        }

        /**
         * Compares two upload objects.
         */
        public int compare(
                final FrostUploadItem ulItem1,
                final FrostUploadItem ulItem2)
        {
            int result = 0; // init to "both items are equal"

            // compare the objects using the desired column
            switch( fType.getObjType() ) {
                case 0: // "String"
                    // load the column-appropriate String values
                    String s1 = null, s2 = null;
                    switch( fType ) {
                        case FILENAME:
                            s1 = ulItem1.getFileName(); s2 = ulItem2.getFileName();
                            break;
                        case PATH:
                            s1 = ulItem1.getFile().getPath(); s2 = ulItem2.getFile().getPath();
                            break;
                        case KEY:
                            // nulls (no keys) are sorted last in ascending mode
                            s1 = ulItem1.getKey(); s2 = ulItem2.getKey();
                            break;
                        case COMPAT_MODE:
                            s1 = ulItem1.getFreenetCompatibilityMode(); s2 = ulItem2.getFreenetCompatibilityMode();
                            break;
                        case CRYPTOKEY:
                            // nulls (no crypto keys) are sorted last in ascending mode
                            s1 = ulItem1.getCryptoKey(); s2 = ulItem2.getCryptoKey();
                            break;
                    }
                    // perform a case-insensitive String comparison with null-support
                    result = Mixed.compareStringWithNullSupport(s1, s2, /*ignoreCase=*/true);
                    break;
                case 1: // "boolean"
                    // load the column-appropriate boolean values
                    boolean b1 = false, b2 = false;
                    switch( fType ) {
                        case ENABLED:
                            // external items and finished items are *always* rendered as "enabled", so sort them like that too
                            if( ulItem1.isExternal() || ulItem1.getState() == FrostUploadItem.STATE_DONE ) {
                                b1 = true;
                            } else {
                                b1 = ulItem1.isEnabled().booleanValue();
                            }
                            if( ulItem2.isExternal() || ulItem2.getState() == FrostUploadItem.STATE_DONE ) {
                                b2 = true;
                            } else {
                                b2 = ulItem2.isEnabled().booleanValue();
                            }
                            break;
                        case COMPRESS:
                            b1 = ulItem1.getCompress(); b2 = ulItem2.getCompress();
                            break;
                    }
                    result = Mixed.compareBool(b1, b2);
                    break;
                case 2: // "long"
                    // load the column-appropriate long values
                    long l1 = 0L, l2 = 0L;
                    switch( fType ) {
                        case FILESIZE:
                            l1 = ulItem1.getFileSize(); l2 = ulItem2.getFileSize();
                            break;
                    }
                    result = Mixed.compareLong(l1, l2);
                    break;
                case 3: // "int"
                    // load the column-appropriate int values
                    int i1 = 0, i2 = 0;
                    switch( fType ) {
                        case STATE:
                            i1 = calculateStateSortValue(ulItem1); i2 = calculateStateSortValue(ulItem2);
                            break;
                        case BLOCKS:
                            i1 = calculateBlocksSortValue(ulItem1); i2 = calculateBlocksSortValue(ulItem2);
                            break;
                        case TRIES:
                            i1 = ulItem1.getRetries(); i2 = ulItem2.getRetries();
                            break;
                        case PRIORITY:
                            i1 = ulItem1.getPriority().getNumber(); i2 = ulItem2.getPriority().getNumber();
                            break;
                    }
                    result = Mixed.compareInt(i1, i2);
                    break;
                case 4: // "double"
                    // load the column-appropriate double values
                    double d1 = 0.0d, d2 = 0.0d;
                    switch( fType ) {
                        /* nothing in this table uses doubles */
                    }
                    result = Mixed.compareDouble(d1, d2);
                    break;
            }

            // if the values are equal and this isn't the "Filename" column, then use Filename as tie-breaker
            if( result == 0 && fType != Type.FILENAME ) {
                // compare by case-insensitive Filename using always-ascending order.
                result = Mixed.compareStringWithNullSupport(ulItem1.getFileName(), ulItem2.getFileName(), /*ignoreCase=*/true);
                // NOTE:IMPORTANT: for this table, we achieve the "always-ascending" order by inverting
                // the filename sort results *if* this is one of the columns that default to descending order.
                // by pre-inverting our result in those cases, the tables will flip the filenames back
                // to normal order, which means that we get (for example) descending block sorting,
                // but ascending filenames. this trick only works on their first click, since we don't
                // know the sort-order of the table column, so inversion will reverse the filename order
                // too, but that's fine, since that just means the *entire* list is inverted nicely.
                switch( fType ) {
                    // NOTE:XXX: this is the same list as UploadPanel.java:getColumnDefaultAscendingState()
                    case ENABLED:
                    case FILESIZE:
                    case STATE:
                    case BLOCKS:
                    case COMPRESS:
                        result = -result;
                        break;
                }
                return result;
            } else {
                // if they want a reverse/descending sort, we'll invert the result
                return ( fAscending ? result : -result );
            }
        }

        /**
         * Sort the current state of the transfers, with the highest importance
         * given to the highest states of completion.
         */
        private int calculateStateSortValue(
                final FrostUploadItem ulItem)
        {
            final int state = ulItem.getState();
            int sortValue = 0;
            switch( state ) {
                case FrostUploadItem.STATE_DONE: // Finished
                    sortValue = 100;
                    break;
                case FrostUploadItem.STATE_PROGRESS: // Uploading
                    sortValue = 90;
                    break;
                case FrostUploadItem.STATE_ENCODING: // Pre-calculating key
                    sortValue = 80;
                    break;
                case FrostUploadItem.STATE_ENCODING_REQUESTED: // Waiting for pre-encode
                    sortValue = 70;
                    break;
                case FrostUploadItem.STATE_WAITING: // Waiting
                    sortValue = 60;
                    break;
                case FrostUploadItem.STATE_FAILED: // Failed
                    sortValue = 50;
                    break;
                default: // no other states exist, this is just futureproofing
                    sortValue = state;
            }
            return sortValue;
        }

        /**
         * Sorts Finished items at the top, then Uploading main-data items (1% 1/100) after that, then
         * Uploading metadata items (~10% 1/10), then all remaining items that aren't Finished/Uploading.
         */
        private int calculateBlocksSortValue(
                final FrostUploadItem ulItem)
        {
            // invalid items (should never happen) get a very negative value to be sorted last
            if( ulItem == null ) {
                return -1000; // invalid
            }

            // if the item is Finished, we use the highest value for topmost sorting
            // NOTE: normal items that have reached 100% but aren't done yet (due to still finishing
            // the final blocks) are still treated as STATE_PROGRESS and will calculate a percentage below.
            if( ulItem.getState() == FrostUploadItem.STATE_DONE ) {
                return 1000; // finished
            }

            // if the item isn't Finished and isn't Uploading, then it's waiting/failed/whatever,
            // so give it a negative sort value so that it's below 0%-progress *running* items
            if( ulItem.getState() != FrostUploadItem.STATE_PROGRESS ) { // PROGRESS means "Uploading"
                return -100; // not in progress
            }

            // calculate the transfer percentage
            int percentDone = calculatePercentDone(ulItem);

            // boost the value if this is the percentage for the main transfer (Finalized=true),
            // because otherwise it's just the percentage for the preparatory (~0% 0/1) metadata.
            // NOTE: to be honest... this code is just a precaution. upload-items never live in the
            // "non-finalized" stage; they simply tell the node to upload the item and instantly
            // get an accurate "done + total" block count in response, since they don't have to
            // wait for any metadata. this code is just here for correctness.
            final Boolean isFinalized = ulItem.isFinalized();
            if( isFinalized != null && isFinalized.booleanValue() == true ) {
                // add 101 to the percentage for items that have actually progressed past
                // the "uploading metadata" stage. this ensures that even a 0% done normal
                // item is sorted above a "100% done uploading metadata" item. so normal items
                // will live in the 101-201 percent value range.
                percentDone += 101; // main transfer value boost
            }

            return percentDone;
        }
    }

    /* //#DIEFILESHARING: This entire block has been commented out since filesharing is removed in Frost-Next.
    //if filesharing ever comes back, this should be migrated to ColumnComparator format.
    private class IsSharedComparator implements Comparator<FrostUploadItem> {
        public int compare(final FrostUploadItem item1, final FrostUploadItem item2) {
            return Mixed.compareBool(item1.isSharedFile(), item2.isSharedFile());
        }
    }
    */

    private final Language language;

    // with persistence we have 1 additional column: priority
//#DIEFILESHARING    private final static int COLUMN_COUNT = ( PersistenceManager.isPersistenceEnabled() ? 13 : 12 );
    private final static int COLUMN_COUNT = ( PersistenceManager.isPersistenceEnabled() ? 12 : 11 );

    private String stateDone;
    private String stateFailed;
    private String stateUploading;
    private String stateEncodingRequested;
    private String stateEncoding;
    private String stateWaiting;

    private String unknownKeyString; // language-specific placeholder: files without CHK keys yet
    private String autoCryptoKeyString; // ...: files without custom crypto key

//#DIEFILESHARING    private String isSharedTooltip;

    public UploadTableFormat() {
        super(COLUMN_COUNT);

        language = Language.getInstance();
        language.addLanguageListener(this);
        refreshLanguage();

        setComparator(new ColumnComparator(ColumnComparator.Type.ENABLED, true), 0);
//#DIEFILESHARING        setComparator(new IsSharedComparator(), 1); // NOTE: all other columns have been reordered with this removal
        setComparator(new ColumnComparator(ColumnComparator.Type.FILENAME, true), 1);
        setComparator(new ColumnComparator(ColumnComparator.Type.FILESIZE, true), 2);
        setComparator(new ColumnComparator(ColumnComparator.Type.STATE, true), 3);
        setComparator(new ColumnComparator(ColumnComparator.Type.PATH, true), 4);
        setComparator(new ColumnComparator(ColumnComparator.Type.BLOCKS, true), 5);
        setComparator(new ColumnComparator(ColumnComparator.Type.KEY, true), 6);
        setComparator(new ColumnComparator(ColumnComparator.Type.COMPRESS, true), 7);
        setComparator(new ColumnComparator(ColumnComparator.Type.COMPAT_MODE, true), 8);
        setComparator(new ColumnComparator(ColumnComparator.Type.CRYPTOKEY, true), 9);
        setComparator(new ColumnComparator(ColumnComparator.Type.TRIES, true), 10);
        if( PersistenceManager.isPersistenceEnabled() ) {
            setComparator(new ColumnComparator(ColumnComparator.Type.PRIORITY, true), 11);
        }

        showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
        Core.frostSettings.addPropertyChangeListener(this);
    }

    private void refreshLanguage() {
        setColumnName(0, language.getString("Common.enabled"));
//#DIEFILESHARING        setColumnName(1, language.getString("UploadPane.fileTable.shared")); // NOTE: all other columns have been reordered with this removal
        setColumnName(1, language.getString("UploadPane.fileTable.filename"));
        setColumnName(2, language.getString("UploadPane.fileTable.size"));
        setColumnName(3, language.getString("UploadPane.fileTable.state"));
        setColumnName(4, language.getString("UploadPane.fileTable.path"));
        setColumnName(5, language.getString("UploadPane.fileTable.blocks"));
        setColumnName(6, language.getString("UploadPane.fileTable.key"));
        setColumnName(7, language.getString("UploadPane.fileTable.compress"));
        setColumnName(8, language.getString("UploadPane.fileTable.freenetCompatibilityMode"));
        setColumnName(9, language.getString("UploadPane.fileTable.cryptoKey"));
        setColumnName(10, language.getString("UploadPane.fileTable.tries"));
        if( PersistenceManager.isPersistenceEnabled() ) {
            setColumnName(11, language.getString("UploadPane.fileTable.priority"));
        }

        stateDone =               language.getString("UploadPane.fileTable.state.done");
        stateFailed =             language.getString("UploadPane.fileTable.state.failed");
        stateUploading =          language.getString("UploadPane.fileTable.state.uploading");
        stateEncodingRequested =  language.getString("UploadPane.fileTable.state.encodeRequested");
        stateEncoding =           language.getString("UploadPane.fileTable.state.encodingFile") + "...";
        stateWaiting =            language.getString("UploadPane.fileTable.state.waiting");
        unknownKeyString =        language.getString("UploadPane.fileTable.state.unknown");
        autoCryptoKeyString =     language.getString("UploadPane.fileTable.cryptoKey.autoCryptoKey");

//#DIEFILESHARING        isSharedTooltip = language.getString("UploadPane.fileTable.shared.tooltip");

        refreshColumnNames();
    }

    @Override
    public void setCellValue(final Object value, final FrostUploadItem uploadItem, final int columnIndex) {
        switch (columnIndex) {

            case 0 : //Enabled
                // refuse toggling "enabled" if this is an external upload or if the upload is complete
                if( uploadItem.isExternal() || uploadItem.getState() == FrostUploadItem.STATE_DONE ) {
                    return;
                }
                final Boolean valueBoolean = (Boolean) value;
                uploadItem.setEnabled(valueBoolean);
                FileTransferManager.inst().getUploadManager().notifyUploadItemEnabledStateChanged(uploadItem);
                break;

            default :
                super.setCellValue(value, uploadItem, columnIndex);
        }
    }

    public Object getCellValue(final FrostUploadItem uploadItem, final int columnIndex) {
        if( uploadItem == null ) {
            return "*null*";
        }
        switch (columnIndex) {

            case 0 : //Enabled
                return uploadItem.isEnabled();

//#DIEFILESHARING            case 1 :    // isShared // NOTE: all other columns have been reordered with this removal
//#DIEFILESHARING                return Boolean.valueOf(uploadItem.isSharedFile());

            case 1 :    //Filename
                return uploadItem.getFileName();

            case 2 :    //Size
                return FormatterUtils.formatSize(uploadItem.getFileSize());

            case 3 :    // state
                return getStateAsString(uploadItem);

            case 4 :    //Path
                return uploadItem.getFile().getPath();

            case 5 :    //blocks
                return getBlocksAsString(uploadItem);

            case 6 :    //Key
                if (uploadItem.getKey() == null) {
                    return unknownKeyString;
                } else {
                    return uploadItem.getKey();
                }

            case 7 :    //Compress
                return uploadItem.getCompress();

            case 8 :    //Compatibility mode
                // NOTE: we save table space (and make the column much more readable) by not showing
                // the leading "COMPAT_" string, since they all begin with that; so "COMPAT_1468" is
                // simply shown as "1468" (for example). we don't use the more efficient .substring(7),
                // in the unlikely case that future Freenet versions change the name of these
                // constants to no longer have a leading "COMPAT_".
                return uploadItem.getFreenetCompatibilityMode().replace("COMPAT_", "");

            case 9 :    //Crypto Key
                if (uploadItem.getCryptoKey() == null) {
                    return autoCryptoKeyString;
                } else {
                    return uploadItem.getCryptoKey();
                }

            case 10 :   //Tries
                return new Integer(uploadItem.getRetries());

            case 11 : // Priority
            	// FIXME: handle native type, not int
                final int value = uploadItem.getPriority().getNumber();
                if( value < 0 ) {
                    return "-";
                } else {
                    return new Integer(value);
                }

            default:
                return "**ERROR**";
        }
    }

    private String getStateAsString(final FrostUploadItem ulItem) {
        switch( ulItem.getState() ) {

            case FrostUploadItem.STATE_PROGRESS :
                return stateUploading;

            case FrostUploadItem.STATE_ENCODING_REQUESTED :
                return stateEncodingRequested;

            case FrostUploadItem.STATE_ENCODING :
                return stateEncoding;

            case FrostUploadItem.STATE_FAILED :
                final String errorCodeDescription = getFailureString(ulItem, /*escapeTags=*/false);
                if( errorCodeDescription != null ) {
                    return stateFailed + ": " + errorCodeDescription; // NOTE: the table cell completely ignores newlines so we don't need to trim them
                } else {
                    return stateFailed;
                }

            case FrostUploadItem.STATE_DONE :
                return stateDone;

            case FrostUploadItem.STATE_WAITING :
                return stateWaiting;

            default :
                return "**ERROR**";
        }
    }

    /**
     * Returns a non-null String if there is a failure reason.
     * Optionally escapes <, >, and & html/xml characters.
     */
    private String getFailureString(final FrostUploadItem ulItem, final boolean escapeTags) {
        if( ulItem == null ) { return null; }
        final String errorCodeDescription = ulItem.getErrorCodeDescription();
        if( errorCodeDescription != null && errorCodeDescription.length() > 0 ) {
            return ( escapeTags ? Mixed.htmlSpecialChars(errorCodeDescription) : errorCodeDescription );
        } else {
            return null;
        }
    }

    private String getBlocksAsString(final FrostUploadItem uploadItem) {

        final int totalBlocks = uploadItem.getTotalBlocks();
        final int doneBlocks = uploadItem.getDoneBlocks();
        final Boolean isFinalized = uploadItem.isFinalized();

        if( totalBlocks <= 0 ) {
            // if we don't know the block total but it's marked "DONE", it means the transfer
            // finished during a previous session, so we'll simply display 100%. on the other
            // hand, if a transfer hasn't even started yet, we simply show nothing instead.
            return ( uploadItem.getState() == FrostUploadItem.STATE_DONE ? "100%" : "" );
        }

        // format: ~0% 0/60 [60]

        final StringBuilder sb = new StringBuilder();

        // add a tilde prefix while downloading metadata (finalized becomes false when the
        // real transfer begins, after the metadata has been fully retrieved).
        if( isFinalized != null && isFinalized.booleanValue() == false ) {
            sb.append("~");
        }

        int percentDone = calculatePercentDone(uploadItem);
        sb.append(percentDone).append("% ");
        sb.append(doneBlocks).append("/").append(totalBlocks).append(" [").append(totalBlocks).append("]");

        return sb.toString();
    }

    public void customizeTable(ModelTable<FrostUploadItem> lModelTable) {
        super.customizeTable(lModelTable);

        modelTable = (SortedModelTable<FrostUploadItem>) lModelTable;

        if( Core.frostSettings.getBoolValue(SettingsClass.SAVE_SORT_STATES)
                && Core.frostSettings.getObjectValue(CFGKEY_SORTSTATE_SORTEDCOLUMN) != null
                && Core.frostSettings.getObjectValue(CFGKEY_SORTSTATE_SORTEDASCENDING) != null )
        {
            final int sortedColumn = Core.frostSettings.getIntValue(CFGKEY_SORTSTATE_SORTEDCOLUMN);
            final boolean isSortedAsc = Core.frostSettings.getBoolValue(CFGKEY_SORTSTATE_SORTEDASCENDING);
            if( sortedColumn > -1 && sortedColumn < modelTable.getTable().getColumnModel().getColumnCount() ) {
                modelTable.setSortedColumn(sortedColumn, isSortedAsc);
            }
        } else {
//#DIEFILESHARING            modelTable.setSortedColumn(2, true);
            modelTable.setSortedColumn(1, true);
        }

        lModelTable.getTable().setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);

        final TableColumnModel columnModel = lModelTable.getTable().getColumnModel();
        fixedsizeColumns = new HashSet<Integer>();

        // Column "Enabled"
        columnModel.getColumn(0).setCellRenderer(BooleanCell.RENDERER);
        columnModel.getColumn(0).setCellEditor(BooleanCell.EDITOR);
        setColumnEditable(0, true);
        columnModel.getColumn(0).setCellRenderer(new IsEnabledRenderer());
        // hard set sizes of "enabled" checkbox column
        columnModel.getColumn(0).setMinWidth(20);
        columnModel.getColumn(0).setMaxWidth(20);
        columnModel.getColumn(0).setPreferredWidth(20);
        fixedsizeColumns.add(0);
        /* //#DIEFILESHARING: This entire block has been commented out since filesharing is removed in Frost-Next. And all other columns have been re-numbered below.
        // hard set sizes of icon column
        columnModel.getColumn(1).setMinWidth(20);
        columnModel.getColumn(1).setMaxWidth(20);
        columnModel.getColumn(1).setPreferredWidth(20);
        columnModel.getColumn(1).setCellRenderer(new IsSharedRenderer());
        fixedsizeColumns.add(1);
        */

        final RightAlignRenderer numberRightRenderer = new RightAlignRenderer();
        final ShowContentTooltipRenderer showContentTooltipRenderer = new ShowContentTooltipRenderer();

        columnModel.getColumn(1).setCellRenderer(new ShowNameTooltipRenderer()); // filename
        columnModel.getColumn(2).setCellRenderer(numberRightRenderer); // filesize
        columnModel.getColumn(3).setCellRenderer(new ShowStateContentTooltipRenderer()); // state
        columnModel.getColumn(4).setCellRenderer(showContentTooltipRenderer); // path
        columnModel.getColumn(5).setCellRenderer(new BlocksProgressRenderer()); // blocks
        columnModel.getColumn(6).setCellRenderer(showContentTooltipRenderer); // key
        // custom renderer which disables editing of the values in the "compress" column (since they can't be changed after uploads have begun)
        columnModel.getColumn(7).setCellRenderer(new IsCompressRenderer()); // compress
        columnModel.getColumn(8).setCellRenderer(showContentTooltipRenderer); // compatibility mode
        columnModel.getColumn(9).setCellRenderer(showContentTooltipRenderer); // crypto key
        columnModel.getColumn(10).setCellRenderer(numberRightRenderer); // tries
        if( PersistenceManager.isPersistenceEnabled() ) {
            columnModel.getColumn(11).setCellRenderer(numberRightRenderer); // priority
        }

        if( !loadTableLayout(columnModel) ) {
            // Sets the relative widths of the columns
            int[] widths;
            if( PersistenceManager.isPersistenceEnabled() ) {
//#DIEFILESHARING                final int[] newWidths = { 20, 20, 200, 65, 30, 60, 50, 70, 30, 60, 60, 20, 20 };
                final int[] newWidths = { 20, 200, 65, 30, 60, 50, 70, 30, 60, 60, 20, 20 };
                widths = newWidths;
            } else {
//#DIEFILESHARING                final int[] newWidths = { 20, 20, 200, 65, 30, 60, 50, 70, 30, 60, 60, 20 };
                final int[] newWidths = { 20, 200, 65, 30, 60, 50, 70, 30, 60, 60, 20 };
                widths = newWidths;
            }

            for (int i = 0; i < widths.length; i++) {
                columnModel.getColumn(i).setPreferredWidth(widths[i]);
            }
        }
    }

    public void saveTableLayout() {
        final TableColumnModel tcm = modelTable.getTable().getColumnModel();
        for(int columnIndexInTable=0; columnIndexInTable < tcm.getColumnCount(); columnIndexInTable++) {
            final TableColumn tc = tcm.getColumn(columnIndexInTable);
            final int columnIndexInModel = tc.getModelIndex();
            // save the current index in table for column with the fix index in model
            Core.frostSettings.setValue(CFGKEY_COLUMN_TABLEINDEX + columnIndexInModel, columnIndexInTable);
            // save the current width of the column
            final int columnWidth = tc.getWidth();
            Core.frostSettings.setValue(CFGKEY_COLUMN_WIDTH + columnIndexInModel, columnWidth);
        }

        if( Core.frostSettings.getBoolValue(SettingsClass.SAVE_SORT_STATES) && modelTable.getSortedColumn() > -1 ) {
            final int sortedColumn = modelTable.getSortedColumn();
            final boolean isSortedAsc = modelTable.isSortedAscending();
            Core.frostSettings.setValue(CFGKEY_SORTSTATE_SORTEDCOLUMN, sortedColumn);
            Core.frostSettings.setValue(CFGKEY_SORTSTATE_SORTEDASCENDING, isSortedAsc);
        }
    }

    private boolean loadTableLayout(final TableColumnModel tcm) {

        // load the saved tableindex for each column in model, and its saved width
        final int[] tableToModelIndex = new int[tcm.getColumnCount()];
        final int[] columnWidths = new int[tcm.getColumnCount()];

        for(int x=0; x < tableToModelIndex.length; x++) {
            final String indexKey = CFGKEY_COLUMN_TABLEINDEX + x;
            if( Core.frostSettings.getObjectValue(indexKey) == null ) {
                return false; // column not found, abort
            }
            // build array of table to model associations
            final int tableIndex = Core.frostSettings.getIntValue(indexKey);
            if( tableIndex < 0 || tableIndex >= tableToModelIndex.length ) {
                return false; // invalid table index value
            }
            tableToModelIndex[tableIndex] = x;

            final String widthKey = CFGKEY_COLUMN_WIDTH + x;
            if( Core.frostSettings.getObjectValue(widthKey) == null ) {
                return false; // column not found, abort
            }
            // build array of table to model associations
            final int columnWidth = Core.frostSettings.getIntValue(widthKey);
            if( columnWidth <= 0 ) {
                return false; // invalid column width
            }
            columnWidths[x] = columnWidth;
        }
        // columns are currently added in model order, remove them all and save in an array
        // while we're at it, set the loaded width of each column
        final TableColumn[] tcms = new TableColumn[tcm.getColumnCount()];
        for(int x=tcms.length-1; x >= 0; x--) {
            tcms[x] = tcm.getColumn(x);
            tcm.removeColumn(tcms[x]);
            // keep the fixed-size columns as is
            if( ! fixedsizeColumns.contains(x) ) {
                tcms[x].setPreferredWidth(columnWidths[x]);
            }
        }
        // add the columns in order loaded from settings
        for( final int element : tableToModelIndex ) {
            tcm.addColumn(tcms[element]);
        }
        return true;
    }


    public int[] getColumnNumbers(final int fieldID) {
        return new int[] {};
    }

    public void languageChanged(final LanguageEvent event) {
        refreshLanguage();
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SettingsClass.SHOW_COLORED_ROWS)) {
            showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
            modelTable.fireTableDataChanged();
        }
    }
}
