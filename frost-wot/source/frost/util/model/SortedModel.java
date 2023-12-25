/*
 SortedModel.java / Frost
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
package frost.util.model;

import javax.swing.SwingUtilities;

import java.lang.reflect.InvocationTargetException;
import java.lang.InterruptedException;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import frost.util.Mixed;
import frost.util.gui.SmartSelection;

/**
 * This class is a Model that stores ModelItems in a certain order. That does not
 * mean that it is sorted.
 *
 * This implementation is thread-safe. All methods that manipulate the data
 * are synchronized on the "LOCK" object, which ensures that only a single
 * thread can read/write to the model at a time. Basically, anything that reads
 * or manipulates the model and isn't marked "private" uses synchronization.
 * You don't need to know any of these details to use the class. They're just
 * mentioned here for future reference.
 *
 * EXTREMELY IMPORTANT DEVELOPER NOTE FOR FUTURE CHANGES TO THIS CLASS:
 * The invocations on the GUI thread (below) often synchronize on the LOCK to remain
 * thread-safe. This means that other threads must *not* synchronize and hold that lock
 * for long periods of time, since they would block the GUI thread.
 *
 * PS: The reason we're doing all the complex AWT "invokeNow" GUI thread stuff is
 * that Frost is calling this code from *all over the place* (since this is the table
 * model used by the both the uploads and downloads tables), and we need to *ensure*
 * that we never change the model while another thread (like the GUI thread itself)
 * is using it, in order to avoid null pointer exceptions, graphical bugs and crashes,
 * etc. Frost is extremely badly written and never took proper GUI threading into
 * account, so this workaround was necessary to improve stability.
 */
abstract public class SortedModel<T extends ModelItem<T>> {

    private static final Object LOCK = new Object();

    // NOTE: this is ONLY "protected" (instead of private) so that the (disabled) filesharing
    // model (which inherits from this one) will be able to access it. if *all* filesharing
    // code is ever fully removed, this should be made private.
    protected final ArrayList<T> data; // model-data

    private SortedModelListenerSupport<T> listenerSupport;

    private boolean ascending = false;
    private int columnNumber = -1;

    private final SortedTableFormat<T> tableFormat;
    private SortedModelTable<T> table = null;

    public SortedModel(final SortedTableFormat<T> newFormat) {
        super();
        data = new ArrayList<T>();
        tableFormat = newFormat;
    }

    public void setTable(final SortedModelTable<T> t) {
        table = t;
    }
    public SortedModelTable<T> getTable() {
        return table;
    }

    public SortedTableFormat<T> getTableFormat() {
        return tableFormat;
    }

    /* (non-Javadoc)
     * @see frost.util.Model#addItem(frost.util.ModelItem)
     */
    protected void addItem(final T item) {
        // NOTE: this function just calls the real addItem(),
        // which in turn ensures AWT GUI thread safety and locking
        // IMPORTANT: we do NOT take the lock here, since the lock will be
        // claimed by the function call below, which may be the GUI thread!
        if (columnNumber == -1) {
            addItem(item, -666); // special value which means "insert at the end of the model data"
        } else {
            addItem(item, getInsertionPoint(item)); // auto-calculate sorted insertion location
        }
    }

    protected void addItem(final T item, final int position) {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                synchronized(LOCK) {
                    // get the user's currently selected rows
                    final int[] selection = table.getSelectedRows();

                    // insert the new item
                    if( position == -666 ) { // "insert at the end of the model data"
                        data.add(item);
                    } else { // "insert at the specified position"
                        data.add(position, item);
                    }
                    item.setModel(SortedModel.this);

                    // now send the "item added" event to the table and any other listeners
                    // NOTE: this causes the user's current selection to be updated to what the table
                    // *thinks* is the correct new range, but if the insertion happens between current items,
                    // it actually ends up selecting the new item too; we don't care, though, since we'll
                    // manually correct that when we restore the *real* selection
                    fireItemAdded(item);

                    // apply the user's real selection again, taking all updated row offsets into account
                    if( selection.length > 0 ) {
                        // NOTE: re-calculation is unnecessary if position was "-666" (the special code for
                        // insertion after the last item in the model), since that means no other row offsets
                        // were affected
                        if( position != -666 ) {
                            // now re-calculate what table rows *should* be selected at this time, by
                            // adjusting for the fact that the selected rows after/at the insertion point
                            // will have changed location; we shift them by the total number of rows
                            // inserted. this call directly edits "selection".
                            // NOTE: we do this calculation manually (instead of doing the model-item based
                            // selection tricks), since this "add" function is called a *lot* and we want to
                            // ensure that it's as fast as possible. manual math by far the fastest method.
                            SmartSelection.recalculateSelectionAfterContiguousInsertion(selection, position, 1);
                        }

                        // now just apply the re-calculated selection again
                        SmartSelection.applySmartSelection(table.getTable(), selection);
                    }
                }
            }
        });
    }

    /**
     * Adds an OrderedModelListener to the listener list.
     * <p>
     * If listener is null, no exception is thrown and no action is performed.
     *
     * @param    listener  the OrderedModelListener to be added
     */
    public void addOrderedModelListener(final SortedModelListener<T> listener) {
        if (listener == null) {
            return;
        }
        synchronized(LOCK) {
            if (listenerSupport == null) {
                listenerSupport = new SortedModelListenerSupport<T>();
            }
            listenerSupport.addModelListener(listener);
        }
    }

    public void clear() {
        synchronized(LOCK) {
            final Iterator<T> iterator = data.iterator();
            while (iterator.hasNext()) {
                final T item = iterator.next();
                item.setModel(null);
            }
            data.clear();

            getTable().fireTableDataChanged();

            if (listenerSupport == null) {
                return;
            }
            listenerSupport.fireModelCleared();
        }
    }

    protected void fireItemAdded(final T item) {
        synchronized(LOCK) {
            final int position = data.indexOf(item);
            if( position < 0 ) {
                return; // item doesn't exist in model; abort
            }
            getTable().fireTableRowsInserted(position, position);

            if (listenerSupport == null) {
                return;
            }
            listenerSupport.fireItemAdded(item, position);
        }
    }

    public void itemChanged(final T item) {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                synchronized(LOCK) {
                    final int position = data.indexOf(item);
                    if( position < 0 ) {
                        return; // item doesn't exist in model; abort
                    }
                    if( columnNumber != -1 ) {
                        // we might want to delete and re-insert the item in a different position
                        // of the table to re-sort it, but first check if position would change.
                        boolean reinsert = true;
                        if( data.size() > 1 ) {
                            final Comparator<T> cmp = getComparator();
                            if( position == 0 ) {
                                // first item, compare with second item
                                final T compItem = data.get(1);
                                if( cmp.compare(item, compItem) <= 0 ) {
                                    // no need to resort
                                    reinsert = false;
                                }
                            } else if( position == data.size()-1 ){
                                // last item, compare with preceeding item
                                final T compItem = data.get(position-1);
                                if( cmp.compare(item, compItem) >= 0 ) {
                                    // no need to resort
                                    reinsert = false;
                                }
                            } else {
                                // middle item, compare with preceeding and following item
                                final T compItem1 = data.get(position-1);
                                final T compItem2 = data.get(position+1);
                                if( cmp.compare(item, compItem1) >= 0 ) {
                                    if( cmp.compare(item, compItem2) <= 0 ) {
                                        // no need to resort
                                        reinsert = false;
                                    }
                                }
                            }
                        } else {
                            // only 1 item in table, no need to reinsert (sort)
                            reinsert = false;
                        }

                        if( reinsert ) {
                            // get the user's currently selected rows
                            final int[] selection = table.getSelectedRows();

                            // now remove the old row and insert the new one
                            final int removedIndex = data.indexOf(item);
                            data.remove(item);
                            final int newIndex = getInsertionPoint(item);
                            data.add(newIndex, item);

                            // tell the table model that rows have been added/deleted, so that
                            // it repaints all table rows and ensures that they're all up-to-date
                            // NOTE: Frost used to stupidly do "fireTableDataChanged", but that
                            // clears the user's selection AND causes the whole table to repaint,
                            // which was responsible for a lot of the UI lag in the tables!
                            table.fireTableRowsDeleted(removedIndex, removedIndex);
                            table.fireTableRowsInserted(newIndex, newIndex);

                            // re-calculate the user's row selections to adjust for the fact that
                            // the rows may have changed location.
                            // NOTE: we do this calculation manually (instead of doing the model-item
                            // based selection tricks), since this "update" function is called a *lot*
                            // and we want to ensure that it's as fast as possible.
                            // manual math is by far the fastest method.
                            // NOTE: due to the complexity of simultaneously removing and inserting, we'll
                            // calculate this entirely manually instead of using "SmartSelection.recalculate...".
                            for( int i=0; i<selection.length; ++i ) {
                                int selectedRow = selection[i];
                                // if the user had selected the removed row index, then simply
                                // update it to its new location.
                                if( selectedRow == removedIndex ) {
                                    selectedRow = newIndex;
                                } else {
                                    // the user had selected a different row (anything that wasn't
                                    // the removed index), so we may need to adjust its position to
                                    // where it was after the deletion.
                                    if( removedIndex < selectedRow ) {
                                        // we removed an index before this row, so we need to shift
                                        // our selection down by 1. NOTE: this will never go below 0,
                                        // since we're only running this if removedIndex < selectedRow.
                                        selectedRow -= 1;
                                    }

                                    // next, we need to check if the insertion index shifted our
                                    // desired selection index *again*
                                    // NOTE: this is correct, because the ".add(index, element)"
                                    // function shifts anything *at*/after the inserted index location
                                    // by 1. so if the new position is before our selected row, then
                                    // the selection will have to add 1 since there's a new row before
                                    // our selection. and if the new position is *at* the selected row,
                                    // then the same thing applies since ".add()" moves us out of the
                                    // way. but, if the new position is *after* the selected row then
                                    // we don't care since it doesn't affect this selection.
                                    // NOTE: yes, we do this independently of the reduction above;
                                    // that's because we *first* delete the old row (so we must find
                                    // out how the position changes), and *then* we insert the
                                    // new row (which is in the new coordinate space).
                                    if( newIndex <= selectedRow ) {
                                        selectedRow += 1;
                                    }
                                }

                                // store the re-calculated selection
                                selection[i] = selectedRow;
                            }

                            // now just apply the re-calculated selection again
                            SmartSelection.applySmartSelection(table.getTable(), selection);
                        }
                    }

                    // now tell the table to repaint the updated row; this only does something if
                    // the row *wasn't* moved (re-sorted). if it was moved, it was already repainted
                    // by the delete/add calls above, so this is kinda superfluous (but harmless)
                    // in that case.
                    // NOTE: this function call still has value even if it didn't move, since it
                    // ensures that any "item changed" listeners will be notified too.
                    // NOTE: this uses "fireTableRowsUpdated" internally, which *doesn't* affect
                    // the user's selection (since it acts as if the same row statically remains
                    // in the same place, and just repaints it); so this is a rare case where we
                    // *don't* need to time things so that we apply the re-selection *after* firing
                    // the table event... (see "addItem()" for an example of where the selection
                    // actually *must* be restored *after* the event).
                    fireItemChanged(item);
                }
            }
        });
    }

    protected void fireItemChanged(final T item) {
        synchronized(LOCK) {
            final int position = data.indexOf(item);
            if( position < 0 ) {
                return; // item doesn't exist in model; abort
            }
            getTable().fireTableRowsUpdated(position, position);

            // if there are listeners subscribed to this event, then fire those now
            if (listenerSupport == null) {
                return;
            }
            listenerSupport.fireItemChanged(item, position);
        }
    }

    /**
     * Selects all given model items that still exist in the table.
     * Usage: To get a list of model items, use "final List<T> selectedItems = getSelectedItems();"
     * and restore the selection with "selectTheseModelItems(selectedItems)".
     * NOTE: The model-scanning involved in this function is slower than performing direct index
     * math, so if you're only moving an item or two, it's better to calculate the new selection
     * yourself and calling SmartSelection.applySmartSelection() manually.
     * NOTE: If you're trying to restore the selection after *adding* something to the table,
     * then be sure to restore it *after* anything that fires "fireTableRowsInserted", otherwise
     * the table will try to adjust your corrected offsets and will select the wrong thing. This
     * caveat doesn't apply to fireTableRowsUpdated() which doesn't change the selection at all
     * (so it's safe to set the selection before the call to that function), or fireTableRowsDeleted
     * which automatically adjusts the selection to remove the deleted item and correct everything
     * else (so no manual restoration is necessary).
     */
    public void selectTheseModelItems(final List<T> modelItems) {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        Mixed.invokeNowInDispatchThread(new Runnable() {
            public void run() {
                synchronized(LOCK) {
                    // clear the user's old selection before we begin
                    table.clearSelection();

                    if( modelItems != null && !modelItems.isEmpty() ) {
                        // calculate the new selection (the new row indices of the model items),
                        // skipping any previously selected items that no longer exist in the model.
                        // NOTE: we're executing on the GUI thread, so we can safely read the indexes
                        // here without worrying about them changing while we're reading them;
                        // furthermore, the rest of this SortedModel is thread-safe so that any
                        // added/removed/changed row events will fire on the GUI thread, so we can
                        // be sure that our selection won't be invalidated by the table contents
                        // changing while we're reading it.
                        final ArrayList<Integer> newSelectionList = new ArrayList<Integer>();
                        for( T element : modelItems ) {
                            final int newRow = data.indexOf(element);
                            if( newRow >= 0 ) { // -1 means doesn't exist in model; >= 0 means exists in model
                                newSelectionList.add(newRow);
                            }
                        }

                        // abort now if there's no work to do (meaning that we
                        // couldn't find any of the modelitems in the table).
                        if( newSelectionList.isEmpty() ) {
                            return;
                        }

                        // convert the selection to a regular array of basic integers
                        // (which is what's required by the select-function later).
                        final int[] newSelection = new int[newSelectionList.size()];
                        Iterator<Integer> it = newSelectionList.iterator();
                        for( int i=0; i<newSelection.length; ++i ) {
                            // NOTE: no need to check for null, since each Integer object is guaranteed non-null
                            newSelection[i] = it.next().intValue();
                        }

                        // lastly, apply the new selection to the table.
                        // NOTE: again, since we're executing on the GUI thread, the selection
                        // will not be interrupted by any other GUI events.
                        SmartSelection.applySmartSelection(table.getTable(), newSelection);
                    }
                }
            }
        });
    }

    private void fireItemsRemoved(final int[] positions, final ArrayList<T> items) {
        getTable().fireTableRowsDeleted(positions);

        if (listenerSupport == null) {
            return;
        }
        listenerSupport.fireItemsRemoved(positions, items);
    }

    public T getItemAt(final int position) {
        synchronized(LOCK) {
            if( position >= data.size() ) {
                System.out.println("SortedModel.getItemAt: position="+position+", but size="+data.size());
                new Exception().printStackTrace(System.out);
                return null;
            }
            return data.get(position);
        }
    }

    public int getItemCount() {
        synchronized(LOCK) {
            return data.size();
        }
    }

    /**
     * @return  a new List containing all items of this model, in unspecific order
     */
    public List<T> getItems() {
        synchronized(LOCK) {
            return new ArrayList<T>(data);
        }
    }

    /**
     * Returns the index in this model of the first occurrence of the specified
     * item, or -1 if this model does not contain this element.
     *
     * @param item item to search for.
     * @return the index in this model of the first occurrence of the specified
     * 	       item, or -1 if this model does not contain this element.
     */
    public int indexOf(final T item) {
        synchronized(LOCK) {
            return data.indexOf(item);
        }
    }

    public boolean removeItem(final T item) {
        // NOTE: this function just builds a list and then calls the real removeItems(),
        // which in turn ensures AWT GUI thread safety
        // IMPORTANT: we do NOT take the lock here, since the lock will be
        // claimed by the function call below, which may be the GUI thread!
        List<T> items = new ArrayList<T>();
        items.add(item);
        return removeItems(items);
    }

    /* (non-Javadoc)
     * @see frost.util.Model#removeItems(frost.util.ModelItem)
     */
    public boolean removeItems(final List<T> items) {
        // if we're not running in the AWT GUI thread, then execute there instead so that we remain thread-safe.
        // NOTE: since we need to return a value from this particular runnable, we can't use Mixed.invokeNow...,
        // but we'll use the same coding style, along with an atomic wrapper for returning the value.
        if( !SwingUtilities.isEventDispatchThread() ){
            final AtomicBoolean retVal = new AtomicBoolean(false);
            try {
                SwingUtilities.invokeAndWait(new Runnable() {
                    public void run() {
                        retVal.set( removeItems(items) );
                    }
                });
            } catch( final InterruptedException | InvocationTargetException ex ) {
                // rethrow runtime errors back to the caller, just like "Mixed.invokeNow..."
                throw new RuntimeException(ex.getCause()); // any Throwable can be wrapped!
            }
            return retVal.get();
        }

        // processing that takes place in the GUI thread...
        synchronized(LOCK) {
            //We clear the link to the model of each item
            for( final T element : items ) {
                element.setModel(null);
            }
            //We remove the first occurrence of each item from the model
            final int[] removedPositions = new int[items.size()];
            final ArrayList<T> removedItems = new ArrayList<T>(); 
            int count = 0;
            for( final T element : items ) {
                final int position = data.indexOf(element);
                if( position >= 0 ) { // -1 means doesn't exist in model; >= 0 means exists in model
                    data.remove(position);
                    removedItems.add(element);
                    removedPositions[count] = position;
                    count++;
                }
            }
            // We send an items removed event. Only the items that actually
            // were in the model and thus were removed are included in the event.
            // NOTE: We are NOT saving/restoring the user's selection, since the the "items removed"
            // table event tells Java to de-select any deleted rows, and to adjust all other selected
            // rows to still point to the correct places. So we DON'T have to manually preserve
            // the user's selection here.
            if (count != 0) {
                fireItemsRemoved(removedPositions, removedItems);
                return true;
            } else {
                return false;
            }
        }
    }

    // this function just sorts the model data, but the actual repainting happens
    // in SortedModelTable.java:resortTable(), so we don't need to force this
    // to be done on the GUI thread
    protected void sort(final int newColumnNumber, final boolean newAscending) {
        synchronized(LOCK) {
            columnNumber = newColumnNumber;
            ascending = newAscending;
            Collections.sort(data, getComparator());
        }
    }

    private Comparator<T> getComparator() {
        if (ascending) {
            return tableFormat.getComparator(columnNumber);
        } else {
            return tableFormat.getReverseComparator(columnNumber);
        }
    }

    private int getInsertionPoint(final T item) {
        // NOTE: despite this being private, we must lock on it since it's used by unsynchronized
        // public methods. this granular locking allows us to keep the public methods unlocked.
        synchronized(LOCK) {
            int position = Collections.binarySearch(data, item, getComparator());
            if (position < 0) {
                // No similar item was in the list
                position = (position + 1) * -1;
            } else {
                // There was already a similar item (or more) in the list.
                // We find out the end position of that sublist and use it as insertion point.
                position = Collections.lastIndexOfSubList(data, Collections.singletonList(data.get(position)));
                position++;
            }
            return position;
        }
    }
}
