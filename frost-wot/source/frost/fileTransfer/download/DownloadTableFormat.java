/*
  DownloadTableFormat.java / Frost

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
package frost.fileTransfer.download;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import java.awt.Point;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JProgressBar;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import frost.Core;
import frost.SettingsClass;
import frost.fileTransfer.BlocksPerMinuteCounter;
import frost.fileTransfer.FileTransferManager;
import frost.fileTransfer.FreenetPriority;
import frost.fileTransfer.FrostFileListFileObject;
import frost.fileTransfer.PersistenceManager;
import frost.fileTransfer.common.TableBackgroundColors;
import frost.util.DateFun;
import frost.util.FormatterUtils;
import frost.util.Mixed;
import frost.util.gui.BooleanCell;
import frost.util.gui.MiscToolkit;
import frost.util.gui.translation.Language;
import frost.util.gui.translation.LanguageEvent;
import frost.util.gui.translation.LanguageListener;
import frost.util.model.ModelTable;
import frost.util.model.SortedModelTable;
import frost.util.model.SortedTableFormat;

class DownloadTableFormat extends SortedTableFormat<FrostDownloadItem> implements LanguageListener, PropertyChangeListener {

    private static final String CFGKEY_SORTSTATE_SORTEDCOLUMN = "DownloadTable.sortState.sortedColumn";
    private static final String CFGKEY_SORTSTATE_SORTEDASCENDING = "DownloadTable.sortState.sortedAscending";
    private static final String CFGKEY_COLUMN_TABLEINDEX = "DownloadTable.tableindex.modelcolumn.";
    private static final String CFGKEY_COLUMN_WIDTH = "DownloadTable.columnwidth.modelcolumn.";

    private static ImageIcon isSharedIcon = MiscToolkit.loadImageIcon("/data/shared.png");
    private static ImageIcon isRequestedIcon = MiscToolkit.loadImageIcon("/data/signal.png");
    private static ImageIcon isDDAIcon = MiscToolkit.loadImageIcon("/data/hook.png");

    private static final long CONST_32k = 32 * 1024;

    private SortedModelTable<FrostDownloadItem> modelTable = null;

    private boolean showColoredLines;
    
    private boolean fileSharingDisabled;
    
    private Map<Integer, Integer> mapCurrentColumntToPossibleColumn;
    private Set<Integer> fixedsizeColumns; // in "all columns" index space
    
    /***
     * List of all possible columns in the download table
     * @author jgerrits
     *
     */
    private enum Columns {
    	ENABLED,
    	SHARED_FILE,
    	FILE_REQUESTED,
    	FILE_NAME,
    	SIZE,
    	STATE,
    	LAST_SEEN,
    	LAST_UPLOADED,
    	BLOCKS,
    	DOWNLOAD_DIRECTORY,
    	KEY,
    	LAST_ACTIVITY,
    	TRIES,
    	DDA,
    	PRIORITY;
    	
    	private static TreeMap<Integer, Columns> numMap;
    	static {
    		numMap = new TreeMap<Integer, Columns>();
    		for (final Columns column: Columns.values()) {
    			numMap.put(new Integer(column.ordinal()), column);
    		}
    	}
    	
    	public static Columns lookup(int number) {
    		return numMap.get(number);
    	}
    }

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

                final FrostDownloadItem downloadItem = modelTable.getItemAt(row);
                if (downloadItem != null) {
                    final int itemState = downloadItem.getState();
                    if( itemState == FrostDownloadItem.STATE_DONE) {
                        newBackground = TableBackgroundColors.getBackgroundColorDone(table, row, showColoredLines);
                    } else if( itemState == FrostDownloadItem.STATE_FAILED) {
                        newBackground = TableBackgroundColors.getBackgroundColorFailed(table, row, showColoredLines);
                    } else if( !downloadItem.isEnabled() || downloadItem.getPriority() == FreenetPriority.PAUSE ) {
                        // if disabled or prio 6 (paused) in any of the other states: progress/waiting/trying/decoding
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
            final FrostDownloadItem downloadItem = modelTable.getItemAt(row); //It may be null
            setValue(calculatePercentDone(downloadItem));
            setString(value.toString());

            return this;
        }
    }

    // calculates an integer percentage from 0 to 100, for use in progress bars and sorting, etc
    private static int calculatePercentDone(final FrostDownloadItem dlItem) {
        int percentDone = 0;
        if( dlItem != null ) {
            if( dlItem.getState() == FrostDownloadItem.STATE_DONE ) {
                // items marked "Finished" are always 100%, especially important
                // since they may have finished during another Frost session,
                // in which case we don't know their block count anymore.
                percentDone = 100;
            } else {
                // calculate the percentage...
                final int totalBlocks = dlItem.getTotalBlocks();
                if( totalBlocks > 0 ) { // is only non-zero if transfer has begun
                    final int doneBlocks = dlItem.getDoneBlocks();
                    final int requiredBlocks = dlItem.getRequiredBlocks();
                    if( requiredBlocks > 0 ) { // we must know the required count to calculate percentage
                        // NOTE: because we're dealing with integer truncation, we need to multiply
                        // *before* the division; so 4/6 blocks -> 400/6 -> 66 percent.
                        percentDone = (doneBlocks * 100) / requiredBlocks;
                        if( percentDone > 100 ) {
                            percentDone = 100;
                        }
                    }
                }
            }
        }
        return percentDone;
    }

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
            // determine which row the user is hovering over; note that the table and model rows
            // correspond 1:1 even with sorting applied
            final JTable theTable = modelTable.getTable();
            final Point p = ev.getLocationOnScreen();
            SwingUtilities.convertPointFromScreen(p, theTable); // directly updates "p"
            final int row = theTable.rowAtPoint(p);
            //not used: final int col = theTable.columnAtPoint(p);
            //not used: final Object value = modelTable.getValueAt(row, col); // may be null
            final FrostDownloadItem downloadItem = modelTable.getItemAt(row); // may be null

            // dynamically generate the tooltip
            if (downloadItem != null) {
                // this row contains a download item, so build a summary tooltip for that transfer
                final StringBuilder sb = new StringBuilder();
                sb.append("<html>").append(downloadItem.getFileName());
                if( downloadItem.getDownloadAddedMillis() > 0 ) {
                    sb.append("<br>Added: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(downloadItem.getDownloadAddedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(downloadItem.getDownloadAddedMillis()));
                }
                if( downloadItem.getDownloadStartedMillis() > 0 ) {
                    sb.append("<br>Started: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(downloadItem.getDownloadStartedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(downloadItem.getDownloadStartedMillis()));
                }
                if( downloadItem.getDownloadFinishedMillis() > 0 ) {
                    sb.append("<br>Finished: ");
                    sb.append(DateFun.FORMAT_DATE_VISIBLE.print(downloadItem.getDownloadFinishedMillis()));
                    sb.append("  ");
                    sb.append(DateFun.FORMAT_TIME_VISIBLE.print(downloadItem.getDownloadFinishedMillis()));
                }
                // if this transfer has begun and is unfinished, then display various statistics such as
                // block counter, blocks-per-minute, time estimate, "elapsed" and "last activity" statistics
                if( downloadItem.getState() == FrostDownloadItem.STATE_PROGRESS ) {
                    // add the "blocks done/total percentage" display, for people whose look&feel
                    // is rendering their blocks column as a solid line instead of showing the text
                    String blocksAsString = getBlocksAsString(downloadItem);
                    if( !blocksAsString.equals("") ) { // is empty string until the transfer actually begins
                        sb.append("<br>Blocks: "+blocksAsString);
                    }


                    // if nothing has been transferred yet, or if we still need 1+ blocks, then
                    // display the blocks/minute counter and the time since last activity
                    final int doneBlocks = downloadItem.getDoneBlocks();
                    final int requiredBlocks = downloadItem.getRequiredBlocks();
                    final int neededBlocks = ( requiredBlocks - doneBlocks );
                    if( doneBlocks == 0 || neededBlocks > 0 ) {
                        // determine how long ago the current measurement was updated
                        final long millisSinceLastMeasurement = downloadItem.getMillisSinceLastMeasurement();

                        // display the blocks/minute, the size in human readable bytes, and the
                        // number of hours/minutes/seconds elapsed
                        sb.append("<br>Blocks/Minute: ");
                        if( millisSinceLastMeasurement > 60000 ) {
                            // the transfer's speed counter has not been updated in 1 minute since
                            // measurement began; treat this as a stalled download
                            sb.append("Stalled...");
                        } else {
                            // get the current average number of blocks per minute, and what that corresponds to in bytes
                            final double blocksPerMinute = downloadItem.getAverageBlocksPerMinute();
                            final long bpmInBytesPerSecond = downloadItem.getAverageBytesPerSecond();

                            if( blocksPerMinute < 0 ) {
                                // either "-1" (measuring activity), or "-2" (no recent transfer
                                // activity in the last X minutes), so no measurement is available
                                // NOTE: this will only be triggered for newly started transfers, in
                                // the time from seconds 20 to 60, before the "stalled" indicator
                                // shows up
                                if( millisSinceLastMeasurement > 20000 ) { // shown between seconds 20-60
                                    sb.append("No activity...");
                                } else { // shown between seconds 0-20 or until a measurement comes in (whichever comes first)
                                    sb.append("Measuring...");
                                }
                            } else {
                                // fantastic! there is a recent average available (less than 1 minute old); display it!
                                sb.append(String.format("%.2f", blocksPerMinute) + " (~" + Mixed.convertBytesToHuman(bpmInBytesPerSecond) + "/s)");

                                // calculate the estimated time until completion, if we know how many blocks the file contains
                                if( requiredBlocks > 0 ) {
                                    final long estimatedMillisRemaining = downloadItem.getEstimatedMillisRemaining(
                                            doneBlocks,
                                            requiredBlocks,
                                            BlocksPerMinuteCounter.TRANSFERTYPE_DOWNLOAD);

                                    // only display the time estimate when it's at least 1 second remaining
                                    if( estimatedMillisRemaining >= 1000 ) {
                                        sb.append("<br>Rough Estimate: " + Mixed.convertMillisToHMmSs(estimatedMillisRemaining, "%dh, %dm, %ds remaining"));
                                    }
                                }
                            }
                        }

                        // if we know the last activity time for this item (regardless of internal or external), then display it
                        sb.append("<br>Last Activity: ");
                        final long lastActivityMillis = downloadItem.getLastActivityMillis();
                        if( lastActivityMillis > 0 ) {
                            final long timeElapsed = ( System.currentTimeMillis() - lastActivityMillis );
                            sb.append(Mixed.convertMillisToHMmSs(timeElapsed, "%dh, %dm, %ds ago"));
                        } else {
                            // never seen any activity (or this is external so it wasn't stored).
                            // there's no good description for it, although the "Blocks/Minute" counter
                            // above it will also be stalled and make it very clear why there's no activity here.
                            sb.append("...");
                        }
                    }

                    // if this is an internal item, then show how long the transfer has been in progress.
                    // isn't shown for external items, since they don't have a known start time.
                    if( downloadItem.getDownloadStartedMillis() > 0 ) {
                        final long timeElapsed = ( System.currentTimeMillis() - downloadItem.getDownloadStartedMillis() );
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
            final FrostDownloadItem downloadItem = modelTable.getItemAt(row); //It may be null
            String errorCodeDescription = getFailureString(downloadItem, /*escapeTags=*/true);
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
            setHorizontalAlignment(SwingConstants.CENTER);
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

            final FrostDownloadItem downloadItem = modelTable.getItemAt(row); //It may be null
            if (downloadItem != null) {
                if( downloadItem.isExternal() || downloadItem.getState() == FrostDownloadItem.STATE_DONE ) {
                    setEnabled(false); // render the column in a "faded" way
                    setSelected(true); // external items and finished items should always look "enabled"
                } else {
                    setEnabled(true); // render the checkmark in a normal way
                    setSelected((
                            value != null
                            && value instanceof Boolean
                            && ((Boolean) value).booleanValue()));
                }
            }
            return this;
        }
    }

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
            final int column)
        {
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

    @SuppressWarnings("serial")
	private class IsRequestedRenderer extends BaseRenderer {
    	
        public IsRequestedRenderer() {
            super();
        }
        
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column)
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText("");
            setToolTipText(isRequestedTooltip);
            if (value instanceof Boolean) {
                final Boolean b = (Boolean)value;
                if( b.booleanValue() ) {
                    // show icon
                    setIcon(isRequestedIcon);
                } else {
                    setIcon(null);
                }
            }
            return this;
        }
    }

    @SuppressWarnings("serial")
	private class IsDDARenderer extends BaseRenderer {
    	
        public IsDDARenderer() {
            super();
        }
        
        @Override
        public Component getTableCellRendererComponent(
            final JTable table,
            final Object value,
            final boolean isSelected,
            final boolean hasFocus,
            final int row,
            final int column)
        {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setText("");
            setToolTipText(isDDATooltip);
            if (value instanceof Boolean) {
                final Boolean b = (Boolean)value;
                if( b.booleanValue() ) {
                    // show icon
                    setIcon(isDDAIcon);
                } else {
                    setIcon(null);
                }
            }
            return this;
        }
    }

    private static class ColumnComparator
            implements Comparator<FrostDownloadItem>
    {
        public static enum Type {
            // "String"-based columns
            FILENAME(0), DOWNLOAD_DIR(0), KEY(0),
            // "boolean"-based columns
            ENABLED(1), DDA(1),
            // "long"-based columns
            FILESIZE(2),
            // "int"-based columns
            STATE(3), BLOCKS(3), TRIES(3), PRIORITY(3),
            // "double"-based columns
            LAST_ACTIVITY(4);

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
         * Compares two download objects.
         */
        public int compare(
                final FrostDownloadItem dlItem1,
                final FrostDownloadItem dlItem2)
        {
            int result = 0; // init to "both items are equal"

            // compare the objects using the desired column
            switch( fType.getObjType() ) {
                case 0: // "String"
                    // load the column-appropriate String values
                    String s1 = null, s2 = null;
                    switch( fType ) {
                        case FILENAME:
                            s1 = dlItem1.getFileName(); s2 = dlItem2.getFileName();
                            break;
                        case DOWNLOAD_DIR:
                            s1 = dlItem1.getDownloadDir(); s2 = dlItem2.getDownloadDir();
                            break;
                        case KEY:
                            s1 = dlItem1.getKey(); s2 = dlItem2.getKey();
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
                            if( dlItem1.isExternal() || dlItem1.getState() == FrostDownloadItem.STATE_DONE ) {
                                b1 = true;
                            } else {
                                b1 = dlItem1.isEnabled().booleanValue();
                            }
                            if( dlItem2.isExternal() || dlItem2.getState() == FrostDownloadItem.STATE_DONE ) {
                                b2 = true;
                            } else {
                                b2 = dlItem2.isEnabled().booleanValue();
                            }
                            break;
                        case DDA:
                            // NOTE: direct means "return result directly via AllData message",
                            // so if direct is FALSE then DDA (Direct Disk Access) is used.
                            // that is why we invert both values before sorting, since the checkmark
                            // in the column is enabled if direct is false!
                            b1 = !dlItem1.isDirect(); b2 = !dlItem2.isDirect();
                            break;
                    }
                    result = Mixed.compareBool(b1, b2);
                    break;
                case 2: // "long"
                    // load the column-appropriate long values
                    long l1 = 0L, l2 = 0L;
                    switch( fType ) {
                        case FILESIZE:
                            l1 = calculateFilesizeSortValue(dlItem1); l2 = calculateFilesizeSortValue(dlItem2);
                            break;
                    }
                    result = Mixed.compareLong(l1, l2);
                    break;
                case 3: // "int"
                    // load the column-appropriate int values
                    int i1 = 0, i2 = 0;
                    switch( fType ) {
                        case STATE:
                            i1 = calculateStateSortValue(dlItem1); i2 = calculateStateSortValue(dlItem2);
                            break;
                        case BLOCKS:
                            i1 = calculateBlocksSortValue(dlItem1); i2 = calculateBlocksSortValue(dlItem2);
                            break;
                        case TRIES:
                            i1 = dlItem1.getRetries(); i2 = dlItem2.getRetries();
                            break;
                        case PRIORITY:
                            i1 = dlItem1.getPriority().getNumber(); i2 = dlItem2.getPriority().getNumber();
                            break;
                    }
                    result = Mixed.compareInt(i1, i2);
                    break;
                case 4: // "double"
                    // load the column-appropriate double values
                    double d1 = 0.0d, d2 = 0.0d;
                    switch( fType ) {
                        case LAST_ACTIVITY:
                            // since we're comparing millisecond timestamps, we want to convert them
                            // to flat seconds without millis, so that the comparison is logical (if
                            // the user sees "40 seconds" for two items, then they expect them to be
                            // treated as 40 seconds, and not internally sorted as "40.129" vs "40.283"
                            // seconds, etc). basically, we need to remove the milliseconds when comparing,
                            // otherwise the items will rapidly flicker/re-sort themselves constantly
                            // whenever the user sorts by the time-based column.
                            // by flattening it to seconds, we calm down the sorting.
                            d1 = Math.floor( ((double)dlItem1.getLastActivityMillis() / 1000) );
                            d2 = Math.floor( ((double)dlItem2.getLastActivityMillis() / 1000) );
                            break;
                    }
                    result = Mixed.compareDouble(d1, d2);
                    break;
            }

            // if the values are equal and this isn't the "Filename" column, then use Filename as tie-breaker
            if( result == 0 && fType != Type.FILENAME ) {
                // compare by case-insensitive Filename using always-ascending order.
                result = Mixed.compareStringWithNullSupport(dlItem1.getFileName(), dlItem2.getFileName(), /*ignoreCase=*/true);
                // NOTE:IMPORTANT: for this table, we achieve the "always-ascending" order by inverting
                // the filename sort results *if* this is one of the columns that default to descending order.
                // by pre-inverting our result in those cases, the tables will flip the filenames back
                // to normal order, which means that we get (for example) descending block sorting,
                // but ascending filenames. this trick only works on their first click, since we don't
                // know the sort-order of the table column, so inversion will reverse the filename order
                // too, but that's fine, since that just means the *entire* list is inverted nicely.
                switch( fType ) {
                    // NOTE:XXX: this is the same list as DownloadPanel.java:getColumnDefaultAscendingState()
                    case ENABLED:
                    case FILESIZE:
                    case STATE:
                    case BLOCKS:
                    case LAST_ACTIVITY:
                    case DDA:
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
                final FrostDownloadItem dlItem)
        {
            final int state = dlItem.getState();
            int sortValue = 0;
            switch( state ) {
                case FrostDownloadItem.STATE_DONE: // Finished
                    sortValue = 100;
                    break;
                case FrostDownloadItem.STATE_DECODING: // Decoding a 100% transfer
                    sortValue = 90;
                    break;
                case FrostDownloadItem.STATE_PROGRESS: // Downloading
                    sortValue = 80;
                    break;
                case FrostDownloadItem.STATE_TRYING: // "Get" FCP message sent in non-persistent transfer mode
                    sortValue = 70;
                    break;
                case FrostDownloadItem.STATE_WAITING: // Waiting
                    sortValue = 60;
                    break;
                case FrostDownloadItem.STATE_FAILED: // Failed
                    sortValue = 50;
                    break;
                default: // no other states exist, this is just futureproofing
                    sortValue = state;
            }
            return sortValue;
        }

        /**
         * Sorts Finished items at the top, then Downloading main-data items (1% 1/100) after that, then
         * Downloading metadata items (~10% 1/10), then all remaining items that aren't Finished/Downloading.
         */
        private int calculateBlocksSortValue(
                final FrostDownloadItem dlItem)
        {
            // invalid items (should never happen) get a very negative value to be sorted last
            if( dlItem == null ) {
                return -1000; // invalid
            }

            // if the item is Finished, we use the highest value for topmost sorting
            // NOTE: normal items that have reached 100% but aren't done yet (due to still waiting
            // for decoding) are still treated as STATE_PROGRESS and will calculate a percentage below.
            if( dlItem.getState() == FrostDownloadItem.STATE_DONE ) {
                return 1000; // finished
            }

            // if the item isn't Finished and isn't Downloading, then it's waiting/failed/whatever,
            // so give it a negative sort value so that it's below 0%-progress *running* items
            if( dlItem.getState() != FrostDownloadItem.STATE_PROGRESS ) { // PROGRESS means "Downloading"
                return -100; // not in progress
            }

            // calculate the transfer percentage
            int percentDone = calculatePercentDone(dlItem);

            // boost the value if this is the percentage for the main transfer (Finalized=true),
            // because otherwise it's just the percentage for the preparatory (~0% 0/1) metadata.
            final Boolean isFinalized = dlItem.isFinalized();
            if( isFinalized != null && isFinalized.booleanValue() == true ) {
                // add 101 to the percentage for items that have actually progressed past
                // the "downloading metadata" stage. this ensures that even a 0% done normal
                // item is sorted above a "100% done downloading metadata" item. so normal items
                // will live in the 101-201 percent value range.
                percentDone += 101; // main transfer value boost
            }

            return percentDone;
        }

        /**
         * Returns the item's filesize, or an estimate based on block count if it's not done yet.
         */
        private long calculateFilesizeSortValue(
                final FrostDownloadItem dlItem)
        {
            if( dlItem.getFileSize() >= 0 ) {
                // exact size is known
                return dlItem.getFileSize();
            } else if( dlItem.getRequiredBlocks() > 0
                    && dlItem.isFinalized() != null
                    && dlItem.isFinalized().booleanValue() == true )
            {
                // since Freenet 0.7, we can compute approximate size out of main-data block count
                final long apprSize = dlItem.getRequiredBlocks() * CONST_32k;
                return apprSize;
            } else {
                // no size is known (transfer not started)
                return -1;
            }
        }
    }

    //#DIEFILESHARING: these are old filesharing comparators, not in use anymore...
    //if filesharing ever comes back, they should be migrated to ColumnComparator format.
    private class IsSharedComparator implements Comparator<FrostDownloadItem> {
        public int compare(final FrostDownloadItem item1, final FrostDownloadItem item2) {
            return Mixed.compareBool(item1.isSharedFile(), item2.isSharedFile());
        }
    }

    private class IsRequestedComparator implements Comparator<FrostDownloadItem> {
        public int compare(final FrostDownloadItem item1, final FrostDownloadItem item2) {
            final Boolean b1 = getIsRequested(item1.getFileListFileObject());
            final Boolean b2 = getIsRequested(item2.getFileListFileObject());
            return b1.compareTo(b2);
        }
    }

    private class LastReceivedComparator implements Comparator<FrostDownloadItem> {
        public int compare(final FrostDownloadItem item1, final FrostDownloadItem item2) {
            // compare as flat seconds (see LastActivityComparator for reason).
            final double item1TimeSecs = Math.floor( ((double)item1.getLastReceived() / 1000) );
            final double item2TimeSecs = Math.floor( ((double)item2.getLastReceived() / 1000) );
            return Mixed.compareDouble(item1TimeSecs, item2TimeSecs);
        }
    }

    private class LastUploadedComparator implements Comparator<FrostDownloadItem> {
        public int compare(final FrostDownloadItem item1, final FrostDownloadItem item2) {
            // compare as flat seconds (see LastActivityComparator for reason).
            final double item1TimeSecs = Math.floor( ((double)item1.getLastUploaded() / 1000) );
            final double item2TimeSecs = Math.floor( ((double)item2.getLastUploaded() / 1000) );
            return Mixed.compareDouble(item1TimeSecs, item2TimeSecs);
        }
    }

	private final Language language;

    // with persistence we have 2 additional columns: priority and isDDA
    // if filesharing is enabled, we have 4 additional columns: shared, requested, received and uploaded
    // FIXME: since we're not using the "COLUMN_COUNT" version of the parent SortedTableFormat constructor anyway,
    // we don't need this value whatsoever. so instead of calculating the proper value, let's just comment this out
    //private final static int COLUMN_COUNT = ( PersistenceManager.isPersistenceEnabled() ? 15 : 13 );

    private String stateWaiting;
    private String stateTrying;
    private String stateFailed;
    private String stateDone;
    private String stateDecoding;
    private String stateDownloading;

    private String unknown;

    private String isSharedTooltip;
    private String isRequestedTooltip;
    private String isDDATooltip;

	public DownloadTableFormat() {
		super();

//#DIEFILESHARING		fileSharingDisabled = Core.frostSettings.getBoolValue(SettingsClass.FILESHARING_DISABLE);
		fileSharingDisabled = true;
		
		language = Language.getInstance();
		language.addLanguageListener(this);
		refreshLanguage();

		int columnCounter = 0;
		setComparator(new ColumnComparator(ColumnComparator.Type.ENABLED, true), columnCounter++);
		if( fileSharingDisabled == false ) {
			setComparator(new IsSharedComparator(), columnCounter++);
			setComparator(new IsRequestedComparator(), columnCounter++);
		}
		setComparator(new ColumnComparator(ColumnComparator.Type.FILENAME, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.FILESIZE, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.STATE, true), columnCounter++);
		if( fileSharingDisabled == false ) {
			setComparator(new LastReceivedComparator(), columnCounter++);
			setComparator(new LastUploadedComparator(), columnCounter++);
		}
		setComparator(new ColumnComparator(ColumnComparator.Type.BLOCKS, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.DOWNLOAD_DIR, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.KEY, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.LAST_ACTIVITY, true), columnCounter++);
		setComparator(new ColumnComparator(ColumnComparator.Type.TRIES, true), columnCounter++);
		if( PersistenceManager.isPersistenceEnabled() ) {
			setComparator(new ColumnComparator(ColumnComparator.Type.DDA, true), columnCounter++);
			setComparator(new ColumnComparator(ColumnComparator.Type.PRIORITY, true), columnCounter++);
		}

		showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
		Core.frostSettings.addPropertyChangeListener(this);
	}

	private void refreshLanguage() {
		int columnCounter = 0;

		setColumnName(columnCounter++, language.getString("Common.enabled"));
		if( fileSharingDisabled == false ) {
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.shared"));
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.requested"));
		}
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.filename"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.size"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.state"));
		if( fileSharingDisabled == false ) {
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.lastReceived"));
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.lastUploaded"));
		}
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.blocks"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.downloadDir"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.key"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.lastActivity"));
		setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.tries"));
		if( PersistenceManager.isPersistenceEnabled() ) {
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.isDDA"));
			setColumnName(columnCounter++, language.getString("DownloadPane.fileTable.priority"));
		}

		stateWaiting =  language.getString("DownloadPane.fileTable.states.waiting");
		stateTrying =   language.getString("DownloadPane.fileTable.states.trying");
		stateFailed =   language.getString("DownloadPane.fileTable.states.failed");
		stateDone =     language.getString("DownloadPane.fileTable.states.done");
		stateDecoding = language.getString("DownloadPane.fileTable.states.decodingSegment") + "...";
		stateDownloading = language.getString("DownloadPane.fileTable.states.downloading");

		unknown =   language.getString("DownloadPane.fileTable.states.unknown");

		isSharedTooltip = language.getString("DownloadPane.fileTable.shared.tooltip");
		isRequestedTooltip = language.getString("DownloadPane.fileTable.requested.tooltip");
		isDDATooltip = language.getString("DownloadPane.fileTable.isDDA.tooltip");

		refreshColumnNames();
	}

	public void languageChanged(final LanguageEvent event) {
		refreshLanguage();
	}

	public Object getCellValue(final FrostDownloadItem downloadItem, final int columnIndex) {
        if( downloadItem == null ) {
            return "*null*";
        }
		switch(Columns.lookup(mapCurrentColumntToPossibleColumn.get(columnIndex))) {

			case ENABLED :
				return downloadItem.isEnabled();

            case SHARED_FILE : // isShared
                return Boolean.valueOf( downloadItem.isSharedFile() );

            case FILE_REQUESTED : // isRequested
                return getIsRequested( downloadItem.getFileListFileObject() );

			case FILE_NAME : // Filename
				return downloadItem.getFileName();

			case SIZE : // Size
                if( downloadItem.getFileSize() >= 0 ) {
                    // size is set
                    return FormatterUtils.formatSize(downloadItem.getFileSize());

                } else if( downloadItem.getRequiredBlocks() > 0
                           && downloadItem.isFinalized() != null
                           && downloadItem.isFinalized().booleanValue() == true )
                {
                    // on 0.7, compute appr. size out of finalized block count
                    final long apprSize = downloadItem.getRequiredBlocks() * CONST_32k;
                    return "~" + FormatterUtils.formatSize(apprSize);
                } else {
					return unknown;
				}

            case STATE : // State
                return getStateAsString(downloadItem);

            case LAST_SEEN : // lastReceived
                if( downloadItem.getLastReceived() > 0 ) {
                    return DateFun.getExtendedDateFromMillis(downloadItem.getLastReceived());
                } else {
                    return "-";
                }

            case LAST_UPLOADED : // lastUploaded
                if( downloadItem.getLastUploaded() > 0 ) {
                    return DateFun.getExtendedDateFromMillis(downloadItem.getLastUploaded());
                } else {
                    return "-";
                }

			case BLOCKS : // Blocks
				return getBlocksAsString(downloadItem);

            case DOWNLOAD_DIRECTORY :    // Download dir
                return downloadItem.getDownloadDir();

			case KEY : // Key
				if (downloadItem.getKey() == null) {
					return " ?";
				} else {
					return downloadItem.getKey();
				}

            case LAST_ACTIVITY : // Last Activity
                final long lastActivityMillis = downloadItem.getLastActivityMillis();
                if( lastActivityMillis > 0 ) {
                    // Build a String of format yyyy.mm.dd hh:mm:ssGMT
                    return DateFun.getExtendedDateAndTimeFromMillis(lastActivityMillis);
                } else {
                    return "-";
                }

            case TRIES : // Tries
                return new Integer(downloadItem.getRetries());

            case DDA : // IsDDA
                // NOTE: direct means "return result directly via AllData message",
                // so if direct is FALSE then DDA (Direct Disk Access) is used.
                return Boolean.valueOf(!downloadItem.isDirect());

            case PRIORITY : // Priority
                final int value = downloadItem.getPriority().getNumber();
                return new Integer(value);

			default :
				return "**ERROR**";
		}
	}

    private Boolean getIsRequested(final FrostFileListFileObject flfo) {
        if( flfo == null ) {
            return Boolean.FALSE;
        }
        final long now = System.currentTimeMillis();
        final long before24hours = now - (24L * 60L * 60L * 1000L);
        if( flfo.getRequestLastReceived() > before24hours
                || flfo.getRequestLastSent() > before24hours)
        {
            return Boolean.TRUE;
        } else {
            return Boolean.FALSE;
        }
    }

    /**
     * Returns a non-null String if there is a failure reason.
     * Optionally escapes <, >, and & html/xml characters.
     */
    private String getFailureString(final FrostDownloadItem dlItem, final boolean escapeTags) {
        if( dlItem == null ) { return null; }
        final String errorCodeDescription = dlItem.getErrorCodeDescription();
        if( errorCodeDescription != null && errorCodeDescription.length() > 0 ) {
            return ( escapeTags ? Mixed.htmlSpecialChars(errorCodeDescription) : errorCodeDescription );
        } else {
            return null;
        }
    }

	private String getBlocksAsString(final FrostDownloadItem downloadItem) {

        final int totalBlocks = downloadItem.getTotalBlocks();
        final int doneBlocks = downloadItem.getDoneBlocks();
        final int requiredBlocks = downloadItem.getRequiredBlocks();
        final Boolean isFinalized = downloadItem.isFinalized();

        if( totalBlocks <= 0 ) {
            // if we don't know the block total but it's marked "DONE", it means the transfer
            // finished during a previous session, so we'll simply display 100%. on the other
            // hand, if a transfer hasn't even started yet, we simply show nothing instead.
            return ( downloadItem.getState() == FrostDownloadItem.STATE_DONE ? "100%" : "" );
        }

        // format: ~0% 0/60 [60]

        final StringBuilder sb = new StringBuilder();

        // add a tilde prefix while downloading metadata (finalized becomes false when the
        // real transfer begins, after the metadata has been fully retrieved).
        if( isFinalized != null && isFinalized.booleanValue() == false ) {
            sb.append("~");
        }

        int percentDone = calculatePercentDone(downloadItem);
        sb.append(percentDone).append("% ");
        sb.append(doneBlocks).append("/").append(requiredBlocks).append(" [").append(totalBlocks).append("]");

		return sb.toString();
	}

	private String getStateAsString(final FrostDownloadItem dlItem) {
		switch( dlItem.getState() ) {
			case FrostDownloadItem.STATE_WAITING :
				return stateWaiting;

			case FrostDownloadItem.STATE_TRYING :
				return stateTrying;

			case FrostDownloadItem.STATE_FAILED :
				final String errorCodeDescription = getFailureString(dlItem, /*escapeTags=*/false);
				if( errorCodeDescription != null ) {
					return stateFailed + ": " + errorCodeDescription; // NOTE: the table cell completely ignores newlines so we don't need to trim them
				} else {
					return stateFailed;
				}

			case FrostDownloadItem.STATE_DONE :
				return stateDone;

			case FrostDownloadItem.STATE_DECODING :
				return stateDecoding;

			case FrostDownloadItem.STATE_PROGRESS :
				return stateDownloading;

			default :
				return "**ERROR**";
		}
	}

    public void customizeTable(final ModelTable<FrostDownloadItem> lModelTable) {
		super.customizeTable(lModelTable);

        modelTable = (SortedModelTable<FrostDownloadItem>) lModelTable;
        
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
            modelTable.setSortedColumn(3, true);
        }

        lModelTable.getTable().setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);

        final BaseRenderer baseRenderer = new BaseRenderer();
        final TableColumnModel columnModel = lModelTable.getTable().getColumnModel();
        final RightAlignRenderer rightAlignRenderer = new RightAlignRenderer();
        final ShowContentTooltipRenderer showContentTooltipRenderer = new ShowContentTooltipRenderer();
        int columnCounter = 0;
        mapCurrentColumntToPossibleColumn = new HashMap<Integer, Integer>();
        fixedsizeColumns = new HashSet<Integer>();
        
        // Column "Enabled"
        columnModel.getColumn(columnCounter).setCellEditor(BooleanCell.EDITOR);
        setColumnEditable(columnCounter, true);
        columnModel.getColumn(columnCounter).setMinWidth(20);
        columnModel.getColumn(columnCounter).setMaxWidth(20);
        columnModel.getColumn(columnCounter).setPreferredWidth(20);
        columnModel.getColumn(columnCounter).setCellRenderer(new IsEnabledRenderer());
        fixedsizeColumns.add(Columns.ENABLED.ordinal());
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.ENABLED.ordinal());

        if( fileSharingDisabled == false ) {
	        // hard set sizes of icon column - Shared file
	        columnModel.getColumn(columnCounter).setMinWidth(20);
	        columnModel.getColumn(columnCounter).setMaxWidth(20);
	        columnModel.getColumn(columnCounter).setPreferredWidth(20);
	        columnModel.getColumn(columnCounter).setCellRenderer(new IsSharedRenderer());
	        fixedsizeColumns.add(Columns.SHARED_FILE.ordinal());
	        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.SHARED_FILE.ordinal());
	        
	        // hard set sizes of icon column - File requested
	        columnModel.getColumn(columnCounter).setMinWidth(20);
	        columnModel.getColumn(columnCounter).setMaxWidth(20);
	        columnModel.getColumn(columnCounter).setPreferredWidth(20);
	        columnModel.getColumn(columnCounter).setCellRenderer(new IsRequestedRenderer());
	        fixedsizeColumns.add(Columns.FILE_REQUESTED.ordinal());
	        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.FILE_REQUESTED.ordinal());
        }

        // fileName
        columnModel.getColumn(columnCounter).setCellRenderer(new ShowNameTooltipRenderer());
        columnModel.getColumn(columnCounter).setPreferredWidth(200);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.FILE_NAME.ordinal());
        
        // size
        columnModel.getColumn(columnCounter).setCellRenderer(rightAlignRenderer); 
        columnModel.getColumn(columnCounter).setPreferredWidth(60);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.SIZE.ordinal());
        
        // state
        columnModel.getColumn(columnCounter).setCellRenderer(new ShowStateContentTooltipRenderer()); // state
        columnModel.getColumn(columnCounter).setPreferredWidth(80);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.STATE.ordinal());
        
        if( fileSharingDisabled == false ) {
	        // lastSeen
	        columnModel.getColumn(columnCounter).setCellRenderer(baseRenderer); // last 
	        columnModel.getColumn(columnCounter).setPreferredWidth(20);
	        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.LAST_SEEN.ordinal());
	        
	        // lastUloaded
	        columnModel.getColumn(columnCounter).setCellRenderer(baseRenderer); // lastUploaded
	        columnModel.getColumn(columnCounter).setPreferredWidth(20);
	        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.LAST_UPLOADED.ordinal());
        }
        
        // blocks
        columnModel.getColumn(columnCounter).setCellRenderer(new BlocksProgressRenderer()); // blocks
        columnModel.getColumn(columnCounter).setPreferredWidth(80);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.BLOCKS.ordinal());
        
        // download dir
        columnModel.getColumn(columnCounter).setCellRenderer(showContentTooltipRenderer); // download dir
        columnModel.getColumn(columnCounter).setPreferredWidth(120);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.DOWNLOAD_DIRECTORY.ordinal());
        
        // key
        columnModel.getColumn(columnCounter).setCellRenderer(showContentTooltipRenderer); // key
        columnModel.getColumn(columnCounter).setPreferredWidth(80);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.KEY.ordinal());
        
        // last activity
        columnModel.getColumn(columnCounter).setCellRenderer(baseRenderer); // last activity
        columnModel.getColumn(columnCounter).setPreferredWidth(80);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.LAST_ACTIVITY.ordinal());
        
        // tries
        columnModel.getColumn(columnCounter).setCellRenderer(rightAlignRenderer); // tries
        columnModel.getColumn(columnCounter).setPreferredWidth(20);
        mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.TRIES.ordinal());
        
        if( PersistenceManager.isPersistenceEnabled() ) {
        	// hard set sizes of icon column - IsDDA 
        	columnModel.getColumn(columnCounter).setMinWidth(20);
        	columnModel.getColumn(columnCounter).setMaxWidth(20);
        	columnModel.getColumn(columnCounter).setPreferredWidth(20);
            columnModel.getColumn(columnCounter).setCellRenderer(new IsDDARenderer());
            fixedsizeColumns.add(Columns.DDA.ordinal());
            mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.DDA.ordinal());
            
            // priority
            columnModel.getColumn(columnCounter).setPreferredWidth(20);
            columnModel.getColumn(columnCounter).setCellRenderer(rightAlignRenderer); // prio
            mapCurrentColumntToPossibleColumn.put(columnCounter++, Columns.PRIORITY.ordinal());
        }

        loadTableLayout(columnModel);
	}

    public void saveTableLayout() {
        final TableColumnModel tcm = modelTable.getTable().getColumnModel();
        
        for(int columnIndexInTable=0; columnIndexInTable < tcm.getColumnCount(); columnIndexInTable++) {
            final TableColumn tc = tcm.getColumn(columnIndexInTable);
            final int columnIndexInModel = tc.getModelIndex();
            final int columnIndexAll = mapCurrentColumntToPossibleColumn.get(columnIndexInModel);
            
            // save the current index in table for column with the fix index in model
            Core.frostSettings.setValue(CFGKEY_COLUMN_TABLEINDEX + columnIndexAll, columnIndexInTable);

            // save the current width of the column
            final int columnWidth = tc.getWidth();
            Core.frostSettings.setValue(CFGKEY_COLUMN_WIDTH + columnIndexAll, columnWidth);
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

        // Reverse map
        HashMap<Integer,Integer> mapPossibleColumnToCurrentColumnt = new HashMap<Integer,Integer>();
        for( int num = 0; num < mapCurrentColumntToPossibleColumn.size(); num++) {
            // key = the index of this column within the "all columns" number space
            // num = 0 and upwards to the total number of visible columns - 1
            mapPossibleColumnToCurrentColumnt.put(mapCurrentColumntToPossibleColumn.get(num), num);
        }

        // go through all possible columns within the "all columns" number space
        for(int columnIndexAll = 0; columnIndexAll < Columns.values().length; columnIndexAll++) {

            // check if that column is currently displayed (filesharing has been disabled in Frost-Next
            // so people won't see columns SHARED_FILE, FILE_REQUESTED, LAST_SEEN and LAST_UPLOADED,
            // and some people may even have disabled persistence and won't see DDA or PRIORITY).
            if( ! mapPossibleColumnToCurrentColumnt.containsKey(columnIndexAll) ) {
                continue;
            }

            // map where the "all column" index of this column is within the number of visible columns
            final int columnIndexInModel = mapPossibleColumnToCurrentColumnt.get(columnIndexAll);

            // check if position was saved for this "all column" index
            final String indexKey = CFGKEY_COLUMN_TABLEINDEX + columnIndexAll;
            if( Core.frostSettings.getObjectValue(indexKey) == null ) {
                return false; // column not found, abort
            }

            // get saved position, which only differs if the column has been moved from its default location
            final int tableIndex = Core.frostSettings.getIntValue(indexKey);
            if( tableIndex < 0 || tableIndex >= tableToModelIndex.length ) {
                return false; // invalid table index value
            }
            // build a matrix of how to order the visible columns (i.e. [0,2,1,3,4,5,6,7,8,9]
            // as an example if the 2nd (1) and 3rd (2) columns have swapped places)
            tableToModelIndex[tableIndex] = columnIndexInModel;

            // check if width was saved for this "all column" index
            final String widthKey = CFGKEY_COLUMN_WIDTH + columnIndexAll;
            if( Core.frostSettings.getObjectValue(widthKey) == null ) {
                return false; // width not found, abort
            }

            // get saved width, which only differs from defaults if the column has been resized
            final int columnWidth = Core.frostSettings.getIntValue(widthKey);
            if( columnWidth <= 0 || columnIndexInModel < 0 || columnIndexInModel >= tableToModelIndex.length) {
                return false;  // invalid width or model index value
            }
            columnWidths[columnIndexInModel] = columnWidth;
        }

        // columns are currently added in model order, remove them all and save in an array
        // while we're at it, set the loaded width of each column
        final TableColumn[] tcms = new TableColumn[tcm.getColumnCount()];
        for(int x=tcms.length-1; x >= 0; x--) {
            tcms[x] = tcm.getColumn(x);
            tcm.removeColumn(tcms[x]);
            // keep the fixed-size columns as is
            final int columnIndexAll = mapCurrentColumntToPossibleColumn.get(x); // map current column to its "all columns" index
            if( ! fixedsizeColumns.contains(columnIndexAll) ) { // only apply size if not fixed-size
                tcms[x].setPreferredWidth(columnWidths[x]);
            }
        }

        // add the columns in order loaded from settings
        for( final int element : tableToModelIndex ) {
            tcm.addColumn(tcms[element]);
        }

        return true;
    }

	@Override
    public void setCellValue(final Object value, final FrostDownloadItem downloadItem, final int columnIndex) {
		switch (columnIndex) {

			case 0 : //Enabled
				// refuse toggling "enabled" if this is an external download or if the download is complete
				if( downloadItem.isExternal() || downloadItem.getState() == FrostDownloadItem.STATE_DONE ) {
					return;
				}
				final Boolean valueBoolean = (Boolean) value;
				downloadItem.setEnabled(valueBoolean);
				FileTransferManager.inst().getDownloadManager().notifyDownloadItemEnabledStateChanged(downloadItem);
				break;

			default :
				super.setCellValue(value, downloadItem, columnIndex);
		}
	}

    public int[] getColumnNumbers(final int fieldID) {
        return null;
    }

    public void propertyChange(final PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(SettingsClass.SHOW_COLORED_ROWS)) {
            showColoredLines = Core.frostSettings.getBoolValue(SettingsClass.SHOW_COLORED_ROWS);
            modelTable.fireTableDataChanged();
        }
    }
}
