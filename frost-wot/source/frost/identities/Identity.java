/*
  Identity.java / Frost
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
package frost.identities;

import java.util.logging.*;

import org.garret.perst.*;
import org.w3c.dom.*;
import org.xml.sax.*;

import frost.*;
import frost.messaging.frost.*;
import frost.storage.perst.identities.*;
import frost.util.*;

/**
 * Represents a user identity, should be immutable.
 */
public class Identity extends Persistent implements XMLizable {

    private static transient final Logger logger = Logger.getLogger(Identity.class.getName());

    private static transient final int BAD       = 10;
    private static transient final int NEUTRAL   = 11;
    private static transient final int GOOD      = 12;
    private static transient final int FRIEND    = 13;

    private static transient final String BAD_STRING       = "BAD";
    private static transient final String NEUTRAL_STRING   = "NEUTRAL";
    private static transient final String GOOD_STRING      = "GOOD";
    private static transient final String FRIEND_STRING    = "FRIEND";

    private String uniqueName;
    private long lastSeenTimestamp = -1;
    private int receivedMessageCount = 0;

    /**
     * OLD, LEGACY FROST DATABASES USED VALUES IN THE RANGE OF 1-4 TO DENOTE THE STATE;
     * IF WE DETECT SUCH A VALUE ON LOAD, WE'LL MIGRATE THE STATE TO THE FROST-NEXT RANGE
     * WHICH STARTS AT 10 AND ABOVE.
     */
    private int state = NEUTRAL;

    private transient String publicKey;

    private PerstIdentityPublicKey pPublicKey;

    public Identity() {}

    //if this was C++ LocalIdentity wouldn't work
    //fortunately we have virtual construction so loadXMLElement will be called
    //for the inheriting class ;-)
    protected Identity(final Element el) throws Exception {
        try {
            loadXMLElement(el);
        } catch (final SAXException e) {
            logger.log(Level.SEVERE, "Exception thrown in constructor", e);
        }
    }

    /**
     * we use this constructor whenever we have all the info
     */
    protected Identity(final String name, final String key) {
        this.publicKey = key;
        this.uniqueName = name;
    }

    /**
     * Only used for migration.
     */
    public Identity(final String uname, final String pubkey, final long lseen, final int s) {
        uniqueName = uname;
        publicKey = pubkey;
        lastSeenTimestamp = lseen;
        setUpgradedState(s); // ensures we use FROST-NEXT state numbers

        uniqueName = Mixed.makeFilename(uniqueName);
    }

    /**
     * If a LocalIdentity is deleted, we create a FRIEND Identity for the deleted LocalIdentity
     * (the FRIEND state is set in source/frost/gui/ManageLocalIdentitiesDialog.java)
     */
    public Identity(final LocalIdentity li) {
        uniqueName = li.getUniqueName();
        publicKey = li.getPublicKey();
        lastSeenTimestamp = li.getLastSeenTimestamp();
        receivedMessageCount = li.getReceivedMessageCount();
    }

    /**
     * Create a new Identity from the specified uniqueName and publicKey.
     * If uniqueName does not contain an '@', this method creates a new digest
     * for the publicKey and appends it to the uniqueName.
     * Finally Mixed.makeFilename() is called for the uniqueName.
     */
    protected Identity(String name, final String key, final boolean createNew) {
        if( name.indexOf("@") < 0 ) {
            name = name + "@" + Core.getCrypto().digest(key);
        }
        name = Mixed.makeFilename(name);

        this.publicKey = key;
        this.uniqueName = name;
    }

    ////////////////////////////////////////////////////////////////////
    // FACTORY METHODS /////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////

    /**
     * Create a new Identity from the specified uniqueName and publicKey.
     * Does not convert the specified uniqueName using Mixed.makeFilename() !
     *
     * @return null if the Identity cannot be created
     */
    public static Identity createIdentityFromExactStrings(final String name, final String key) {
        return new Identity(name, key);
    }

    /**
     * Create a new Identity, read from the specified XML element.
     * Calls Mixed.makeFilename() on read uniqueName.
     *
     * @param el  the XML element containing the Identity information
     * @return    the new Identity, or null if Identity cannot be created (invalid input)
     */
    public static Identity createIdentityFromXmlElement(final Element el) {
        try {
            return new Identity(el);
        } catch (final Exception e) {
            return null;
        }
    }

    ////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////

    @Override
    public boolean recursiveLoading() {
        return false;
    }

    @Override
    public void deallocate() {
        if( pPublicKey != null ) {
            pPublicKey.deallocate();
            pPublicKey = null;
        }
        super.deallocate();
    }

    class PerstIdentityPublicKey extends Persistent {
        private String perstPublicKey;
        public PerstIdentityPublicKey() {}
        public PerstIdentityPublicKey(final String pk) {
            perstPublicKey = pk;
        }
        public String getPublicKey() {
            return perstPublicKey;
        }
//        public void onLoad() {
//            System.out.println("load pubkey");
//        }
        @Override
        public boolean recursiveLoading() {
            return false; // load publicKey on demand
        }
    }

    @Override
    public void onStore() {
        if( pPublicKey == null && publicKey != null ) {
            // link public key
            pPublicKey = new PerstIdentityPublicKey(publicKey);
        }
    }

    @Override
    public void onLoad() {
        // MIGRATION: if we're loading an old legacy Frost state (range 1-4), then we need to
        // upgrade it to a Frost-Next state (range 10+). we do NOT need to write anything
        // to the database, since database writes will happen automatically if the user
        // changes their state, or if any ID properties (such as received message count)
        // are updated. so we can simply load + convert the old state, and then let Frost
        // lazy-save the new state whenever some aspect of the identity changes.
        if( state < 10 ) {
            setUpgradedState(state);
        }
    }

    /**
     * Upgrades a legacy Frost state (range 1-4) to a Frost-Next state (10+).
     * Doesn't write anything to the database, since that choice is up to the caller.
     */
    private void setUpgradedState(final int oldState) {
        int newState = NEUTRAL;
        if( oldState == 4 ) { // LEGACY FROST: "BAD"
            newState = BAD;
        } else if( oldState == 2 ) { // LEGACY FROST: "CHECK"
            newState = NEUTRAL;
        } else if( oldState == 3 ) { // LEGACY FROST: "OBSERVE"
            newState = GOOD;
        } else if( oldState == 1 ) { // LEGACY FROST: "GOOD"
            newState = FRIEND;
        }
        state = newState;
    }

    public Element getXMLElement(final Document doc)  {

        final Element el = doc.createElement("Identity");

        Element element = doc.createElement("name");
        CDATASection cdata = doc.createCDATASection(getUniqueName());
        element.appendChild( cdata );
        el.appendChild( element );

        element = doc.createElement("key");
        cdata = doc.createCDATASection(getPublicKey());
        element.appendChild( cdata );
        el.appendChild( element );

        return el;
    }

    public Element getXMLElement_old(final Document doc)  {

        final Element el = doc.createElement("MyIdentity");

        Element element = doc.createElement("name");
        CDATASection cdata = doc.createCDATASection(getUniqueName());
        element.appendChild( cdata );
        el.appendChild( element );

        element = doc.createElement("key");
        cdata = doc.createCDATASection(getPublicKey());
        element.appendChild( cdata );
        el.appendChild( element );

        return el;
    }

    public Element getExportXMLElement(final Document doc)  {
        final Element el = getXMLElement(doc);

        if( getLastSeenTimestamp() > -1 ) {
            final Element element = doc.createElement("lastSeen");
            final Text txt = doc.createTextNode(Long.toString(getLastSeenTimestamp()));
            element.appendChild( txt );
            el.appendChild( element );
        }
        if( getReceivedMessageCount() > -1 ) {
            final Element element = doc.createElement("messageCount");
            final Text txt = doc.createTextNode(Long.toString(getReceivedMessageCount()));
            element.appendChild( txt );
            el.appendChild( element );
        }
        return el;
    }

    public void loadXMLElement(final Element e) throws SAXException {
        uniqueName = XMLTools.getChildElementsCDATAValue(e, "name");
        publicKey =  XMLTools.getChildElementsCDATAValue(e, "key");

        String lastSeenStr = XMLTools.getChildElementsTextValue(e,"lastSeen");
        if( lastSeenStr != null && ((lastSeenStr=lastSeenStr.trim())).length() > 0 ) {
            lastSeenTimestamp = Long.parseLong(lastSeenStr);
        } else {
            // not yet set, init with current timestamp
            lastSeenTimestamp = System.currentTimeMillis();
        }

        String msgCount = XMLTools.getChildElementsTextValue(e,"messageCount");
        if( msgCount != null && ((msgCount=msgCount.trim())).length() > 0 ) {
            receivedMessageCount = Integer.parseInt(msgCount);
        } else {
            receivedMessageCount = 0;
        }
//        uniqueName = Mixed.makeFilename(uniqueName);
    }

    /**
     * @return  the public key of this Identity
     */
    public String getPublicKey() {
        if( publicKey == null && pPublicKey != null ) {
            pPublicKey.load();
            return pPublicKey.getPublicKey();
        }
        return publicKey;
    }

    public String getUniqueName() {
        return uniqueName;
    }

    public void correctUniqueName() {
        uniqueName = Mixed.makeFilename(uniqueName);
    }

    // dont't store BoardAttachment with pubKey=SSK@...
    public static boolean isForbiddenBoardAttachment(final BoardAttachment ba) {
        if( ba != null &&
            ba.getBoardObj().getPublicKey() != null &&
            ba.getBoardObj().getPublicKey().startsWith("SSK@") )
        {
            return true; // let delete SSK pubKey board
        } else {
            return false;
        }
    }

    public long getLastSeenTimestamp() {
        return lastSeenTimestamp;
    }

    public void setLastSeenTimestamp(final long v) {
        lastSeenTimestamp = v;
        updateIdentitiesStorage();
    }

    public void setLastSeenTimestampWithoutUpdate(final long v) {
        lastSeenTimestamp = v;
    }

    public int getState() {
        return state;
    }

    @Override
    public String toString() {
        return getUniqueName();
    }

    public boolean isBAD() {
        return state==BAD;
    }
    public boolean isNEUTRAL() {
        return state==NEUTRAL;
    }
    public boolean isGOOD() {
        return state==GOOD;
    }
    public boolean isFRIEND() {
        return state==FRIEND;
    }

    public void setBAD() {
        state=BAD;
        updateIdentitiesStorage();
    }
    public void setNEUTRAL() {
        state=NEUTRAL;
        updateIdentitiesStorage();
    }
    public void setGOOD() {
        state=GOOD;
        updateIdentitiesStorage();
    }
    public void setFRIEND() {
        state=FRIEND;
        updateIdentitiesStorage();
    }

    public void setBADWithoutUpdate() {
        state=BAD;
    }
    public void setNEUTRALWithoutUpdate() {
        state=NEUTRAL;
    }
    public void setGOODWithoutUpdate() {
        state=GOOD;
    }
    public void setFRIENDWithoutUpdate() {
        state=FRIEND;
    }

    public String getStateString() {
        // check states in order of popularity, to return quickly
        if( isNEUTRAL() ) {
            return NEUTRAL_STRING;
        } else if( isGOOD() ) {
            return GOOD_STRING;
        } else if( isFRIEND() ) {
            return FRIEND_STRING;
        } else if( isBAD() ) {
            return BAD_STRING;
        } else {
            return "*ERR*";
        }
    }

    protected boolean updateIdentitiesStorage() {
        if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
            return false;
        }
        modify();
        IdentitiesStorage.inst().endThreadTransaction();
        return true;
    }

    public int getReceivedMessageCount() {
        return receivedMessageCount;
    }

    public void setReceivedMessageCount(final int i) {
        receivedMessageCount = i;
    }

    public void incReceivedMessageCount() {
        this.receivedMessageCount++;
        updateIdentitiesStorage();
    }
}
