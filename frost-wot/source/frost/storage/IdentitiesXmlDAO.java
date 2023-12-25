/*
  IdentitiesXmlDAO.java / Frost
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
package frost.storage;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import org.w3c.dom.*;
import org.xml.sax.*;

import frost.identities.*;
import frost.util.*;

public class IdentitiesXmlDAO {

    private static final Logger logger = Logger.getLogger(IdentitiesXmlDAO.class.getName());

    /**
     * Loads FRIEND, GOOD and BAD identities from xml file.
     */
    public static List<Identity> loadIdentities(final File file) {

        final LinkedList<Identity> identities = new LinkedList<Identity>();

        final Document d = XMLTools.parseXmlFile(file);
        final Element rootEl = d.getDocumentElement();

        final List<Element> lists = XMLTools.getChildElementsByTagName(rootEl, "BuddyList");
        final Iterator<Element> it = lists.iterator();

        while (it.hasNext()) {
            final Element current = it.next();
            if (current.getAttribute("type").equals("friends")) {
                final BuddyList buddyList = new BuddyList();
                try {
                    buddyList.loadXMLElement(current);
                } catch (final SAXException e) {
                    logger.log(Level.SEVERE, "Error loading FRIEND identities", e);
                }
                for( final Object element : buddyList.getAllValues() ) {
                    final Identity id = (Identity)element;
                    id.setFRIENDWithoutUpdate();
                    identities.add(id);
                }
            } else if (current.getAttribute("type").equals("enemies")) {
                final BuddyList buddyList = new BuddyList();
                try {
                    buddyList.loadXMLElement(current);
                } catch (final SAXException e) {
                    logger.log(Level.SEVERE, "Error loading BAD identities", e);
                }
                for( final Object element : buddyList.getAllValues() ) {
                    final Identity id = (Identity)element;
                    id.setBADWithoutUpdate();
                    identities.add(id);
                }
            } else if (current.getAttribute("type").equals("neutral")) {
                final BuddyList buddyList = new BuddyList();
                try {
                    buddyList.loadXMLElement(current);
                } catch (final SAXException e) {
                    logger.log(Level.SEVERE, "Error loading NEUTRAL identities", e);
                }
                for( final Object element : buddyList.getAllValues() ) {
                    final Identity id = (Identity)element;
                    id.setNEUTRALWithoutUpdate();
                    identities.add(id);
                }
            } else if (current.getAttribute("type").equals("good") || current.getAttribute("type").equals("observed")) {
                // NOTE: the "observed" type above is just the LEGACY FROST name for exported GOOD identities (which it used to call OBSERVE),
                // so we simply support either "good" (the new name) or "observed" as the GOOD type, so that very old XML files work.
                final BuddyList buddyList = new BuddyList();
                try {
                    buddyList.loadXMLElement(current);
                } catch (final SAXException e) {
                    logger.log(Level.SEVERE, "Error loading GOOD identities", e);
                }
                for( final Object element : buddyList.getAllValues() ) {
                    final Identity id = (Identity)element;
                    id.setGOODWithoutUpdate();
                    identities.add(id);
                }
            }
        }

        return identities;
    }

    /**
     * Returns -1 on error, 0 if no identity is to export and no file was created,
     * or >0 for exported identity count.
     */
    public static int saveIdentities(final File file, final List<Identity> identities) {

        final BuddyList friends = new BuddyList();
        final BuddyList good = new BuddyList();
        final BuddyList enemies = new BuddyList();

        int count = 0;

        for( final Object element : identities ) {
            final Identity id = (Identity) element;
            if( id.isFRIEND() ) {
                friends.add(id);
                count++;
            } else if( id.isGOOD() ) {
                good.add(id);
                count++;
            } else if( id.isBAD() ) {
                enemies.add(id);
                count++;
            }
            // we ignore NEUTRAL ids during export! since it is the default/neutral state
        }

        if( count == 0 ) {
            // dont create an empty file
            return count;
        }

        final Document d = XMLTools.createDomDocument();
        final Element rootElement = d.createElement("FrostIdentities");

        // then friends
        final Element friendsElement = friends.getXMLElement(d);
        friendsElement.setAttribute("type", "friends");
        rootElement.appendChild(friendsElement);
        // then enemies
        final Element enemiesElement = enemies.getXMLElement(d);
        enemiesElement.setAttribute("type", "enemies");
        rootElement.appendChild(enemiesElement);
        // then good
        final Element goodElement = good.getXMLElement(d);
        goodElement.setAttribute("type", "good");
        rootElement.appendChild(goodElement);

        d.appendChild(rootElement);

        if( XMLTools.writeXmlFile(d, file) ) {
            return count;
        } else {
            return -1;
        }
    }

    @SuppressWarnings("serial")
	private static class BuddyList implements XMLizable {

        private HashMap<String,Identity> hashMap = null;

        /**constructor*/
        public BuddyList() {
            hashMap = new HashMap<String,Identity>(100); //that sounds like a reasonable number
        }

        /**
         * adds a user to the list
         * returns false if the user exists
         */
        public synchronized boolean add(final Identity user) {
            final String str = user.getUniqueName();
            if (containsKey(str)) {
                return false;
            }
            hashMap.put(str, user);

            return true;
        }

        public boolean containsKey(final String key) {
            return hashMap.containsKey(Mixed.makeFilename(key));
        }

        public synchronized Element getXMLElement(final Document doc) {
            final Element main = doc.createElement("BuddyList");
            final Iterator<Identity> it = hashMap.values().iterator();
            while (it.hasNext()) {
                final Identity id = it.next();
                final Element el = id.getExportXMLElement(doc);
                main.appendChild(el);
            }
            return main;
        }

        public void loadXMLElement(final Element el) throws SAXException {
            if (el == null) {
                return;
            }
            for (final Element idEl : XMLTools.getChildElementsByTagName(el, "Identity") ) {
                final Identity id = Identity.createIdentityFromXmlElement( idEl );
                if( id != null ) {
                    add(id);
                }
            }
        }

        public Collection<Identity> getAllValues() {
            return hashMap.values();
        }
    }
}
