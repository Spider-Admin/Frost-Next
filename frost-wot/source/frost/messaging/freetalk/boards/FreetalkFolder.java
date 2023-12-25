/*
  FreetalkFolder.java / Frost
  Copyright (C) 2009  Frost Project <jtcfrost.sourceforge.net>

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
package frost.messaging.freetalk.boards;

import java.util.*;

import frost.util.VectorComparator;

/**
 * Represents a folder in the board tree.
 */
public class FreetalkFolder extends AbstractFreetalkNode {

    public FreetalkFolder(final String newName) {
        super(newName);
    }

    public void setName(final String newName) {
        name = newName;
        nameLowerCase = name.toLowerCase();
    }

    @SuppressWarnings("unchecked")
    public void sortChildren() {
        // NOTE: the underlying model unchangeably uses a plain untyped Vector<Object>, but vectors
        // implement the "List" interface, so we simply force a cast and sort using our own comparator
        Collections.sort((List<AbstractFreetalkNode>)children, new VectorComparator<AbstractFreetalkNode>(false));
    }

    @Override
    public boolean containsUnreadMessages() {
        for (int i = 0; i < getChildCount(); i++) {
            final AbstractFreetalkNode an = (AbstractFreetalkNode) getChildAt(i);
            if (an.containsUnreadMessages()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isFolder() {
        return true;
    }

    @Override
    public boolean isLeaf() {
        return false;
    }
}
