/*
  SearchModel.java / Frost
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
package frost.fileTransfer.search;

import java.util.List;
import java.util.logging.*;

import frost.fileTransfer.*;
import frost.fileTransfer.download.*;
import frost.util.Mixed;
import frost.util.model.*;

public class SearchModel extends SortedModel<FrostSearchItem> {

    final static Logger logger = Logger.getLogger(SearchModel.class.getName());

    public SearchModel(final SortedTableFormat<FrostSearchItem> f) {
        super(f);
    }

    public void addSearchItem(final FrostSearchItem searchItem) {
        addItem(searchItem);
    }

    public void addItemsToDownloadTable(final List<FrostSearchItem> selectedItems) {

        if( selectedItems == null ) {
            return;
        }

        final DownloadModel downloadModel = FileTransferManager.inst().getDownloadManager().getModel();

        for (int i = selectedItems.size() - 1; i >= 0; i--) {
            final FrostFileListFileObject flf = selectedItems.get(i).getFrostFileListFileObject();
            String filename = flf.getDisplayName();
            // convert any html escape sequences (e.g. "%2c" -> "," and "%40" -> "@" ), to get the real filename
            // FIXME/TODO: The filesharing feature is removed from Next since it's dangerous and
            // nobody used it. But if ever re-implemented, we should rawUrlDecode() the KEY too,
            // so that the keys are consistent with keys added via other methods.
            filename = Mixed.rawUrlDecode(filename);
            final FrostDownloadItem dlItem = new FrostDownloadItem(flf, filename);
            downloadModel.addDownloadItem(dlItem, true); // true = ask to redownload duplicates
            selectedItems.get(i).updateState();
        }
    }
}
