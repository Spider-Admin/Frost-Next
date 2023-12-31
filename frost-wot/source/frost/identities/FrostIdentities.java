/*
  FrostIdentities.java / Frost
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

import java.util.*;
import java.util.logging.*;

import javax.swing.*;

import frost.*;
import frost.storage.*;
import frost.storage.perst.identities.*;
import frost.util.*;
import frost.util.gui.*;
import frost.util.gui.translation.*;

/**
 * A class that maintains identity stuff.
 */
public class FrostIdentities {

    private static final Logger logger = Logger.getLogger(FrostIdentities.class.getName());

    private Hashtable<String,Identity> identities = null;
    private Hashtable<String,LocalIdentity> localIdentities = null;

    private final Object lockObject = new Object();

    Language language = Language.getInstance();

    public void initialize() throws StorageException {

        localIdentities = IdentitiesStorage.inst().loadLocalIdentities();

        // check if there is at least one identity in database, otherwise create one
        if ( localIdentities.size() == 0 ) {
            final LocalIdentity mySelf = createIdentity(true);
            if(mySelf == null) {
                logger.severe("Frost can't run without an identity.");
                System.exit(1);
            }
            addLocalIdentity(mySelf); // add and save
        }

        // load all identities
        identities = IdentitiesStorage.inst().loadIdentities();

        // remove all own identities from identities
        for(final LocalIdentity li : localIdentities.values() ) {
            identities.remove(li.getUniqueName());
        }
    }

    /**
     * Creates new local identity, and adds it to database.
     */
    public LocalIdentity createIdentity() {
        final LocalIdentity li = createIdentity(false);
        if( li != null ) {
            if( addLocalIdentity(li) == false ) {
                // double
                return null;
            }
        }
        return li;
    }

    /**
     * Creates new local identity, and adds it to database.
     */
    private LocalIdentity createIdentity(final boolean firstIdentity) {

        LocalIdentity newIdentity = null;

        // create new identitiy, get a valid username
        try {
            String nick = null;
            boolean isNickOk;
            do {
                nick = (String)MiscToolkit.showInputDialog(
                        null, // parented to the main Frost window (even though it may not be visible yet if this is called during startup)
                        language.getString("Core.loadIdentities.ChooseName"),
                        null, // automatic title choice ("Frost")
                        MiscToolkit.QUESTION_MESSAGE,
                        null, // no special choice buttons or icons...
                        null,
                        null,
                        true ); // always on top; ensures that whenever this dialog is shown during
                                // the first startup, the Splashscreen stays on top of the screen.

                if( nick == null ) {
                    break; // user cancelled
                }

                nick = nick.trim(); // not only blanks, no trailing/leading blanks

                isNickOk = true;

                // check for invalid values
                if(nick.length() < 2 || nick.length() > 32 ) {
                    isNickOk = false; // not only 1 character or more than 32 characters
                } else if (nick.indexOf("@") > -1) {
                    isNickOk = false; // @ is forbidden
                } else if( !Character.isLetter(nick.charAt(0)) ) {
                    isNickOk = false; // must start with an alphanumeric character
                } else {
                    char oldChar = 0;
                    int charCount = 0;
                    for(int x=0; x < nick.length(); x++) {
                        if( nick.charAt(x) == oldChar ) {
                            charCount++;
                        } else {
                            oldChar = nick.charAt(x);
                            charCount = 1;
                        }
                        if( charCount > 3 ) {
                            isNickOk = false; // not more than 3 occurences of the same character in a row
                            break;
                        }
                    }
                }

                if( !isNickOk ) {
                    MiscToolkit.showMessageDialog(
                            null,
                            language.getString("Core.loadIdentities.InvalidNameBody"),
                            language.getString("Core.loadIdentities.InvalidNameTitle"),
                            MiscToolkit.ERROR_MESSAGE);
                }
            } while(!isNickOk);

            if (nick == null) {
                return null; // user cancelled
            }

            do { //make sure there's no '//' in the generated sha checksum
                newIdentity = new LocalIdentity(nick);
            } while (newIdentity.getUniqueName().indexOf("//") != -1);

        } catch (final Exception e) {
            logger.severe("couldn't create new identitiy" + e.toString());
        }
        if( newIdentity != null && firstIdentity ) {
            Core.frostSettings.setValue(SettingsClass.LAST_USED_FROMNAME, newIdentity.getUniqueName());
        }

        return newIdentity;
    }

    public Identity getIdentity(final String uniqueName) {
        if( uniqueName == null ) {
            return null;
        }
        Identity identity = null;
        identity = getLocalIdentity(uniqueName);
        if( identity == null ) {
            identity = identities.get(uniqueName);
        }
        return identity;
    }

    /**
     * Adds an Identity, locks the storage.
     */
    public boolean addIdentity(final Identity id) {
        return addIdentity(id, true);
    }

    public boolean addIdentity(final Identity id, final boolean useLock) {
        if( id == null ) {
            return false;
        }
        final String key = id.getUniqueName();
        if (identities.containsKey(key)) {
            return false;
        }

        if( !isNewIdentityValid(id) ) {
            return false;
        }

        if( useLock ) {
            if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
                return false;
            }
        }
        try {
            if( !IdentitiesStorage.inst().insertIdentity(id) ) {
                return false;
            }
            identities.put(key, id);
        } finally {
            if( useLock ) {
                IdentitiesStorage.inst().endThreadTransaction();
            }
        }
        return true;
    }

    public boolean addLocalIdentity(final LocalIdentity li) {
        if( li == null ) {
            return false;
        }
        if (localIdentities.containsKey(li.getUniqueName())) {
            return false;
        }
        if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
            return false;
        }
        try {
            if( !IdentitiesStorage.inst().insertLocalIdentity(li) ) {
                return false;
            }
            localIdentities.put(li.getUniqueName(), li);
        } finally {
            IdentitiesStorage.inst().endThreadTransaction();
        }
        return true;
    }

    public boolean deleteLocalIdentity(final LocalIdentity li) {
        if( li == null ) {
            return false;
        }
        if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
            return false;
        }
        final boolean removed;
        try {
            if( !localIdentities.containsKey(li.getUniqueName()) ) {
                return false;
            }
            localIdentities.remove(li.getUniqueName());
            removed = IdentitiesStorage.inst().removeLocalIdentity(li);
        } finally {
            IdentitiesStorage.inst().endThreadTransaction();
        }
        return removed;
    }

    public boolean deleteIdentity(final Identity li) {
        if( li == null ) {
            return false;
        }
        if (!identities.containsKey(li.getUniqueName())) {
            return false;
        }
        if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
            return false;
        }
        final boolean removed;
        try {
            identities.remove(li.getUniqueName());
            removed = IdentitiesStorage.inst().removeIdentity(li);
        } finally {
            IdentitiesStorage.inst().endThreadTransaction();
        }

        return removed;
    }

    public boolean isMySelf(final String uniqueName) {
        if( getLocalIdentity(uniqueName) != null ) {
            return true;
        }
        return false;
    }

    public LocalIdentity getLocalIdentity(final String uniqueName) {
        LocalIdentity li = null;
        li = localIdentities.get(uniqueName);
//        if( li == null ) {
//            li = (LocalIdentity) localIdentities.get(Mixed.makeFilename(uniqueName));
//        }
        return li;
    }

    public List<Identity> getAllFRIENDIdentities() {
        final List<Identity> list = new ArrayList<Identity>();
        for( final Identity id : identities.values() ) {
            if( id.isFRIEND() ) {
                list.add(id);
            }
        }
        return list;
    }

    public List<LocalIdentity> getLocalIdentities() {
        return new ArrayList<LocalIdentity>(localIdentities.values());
    }

    public List<Identity> getIdentities() {
        return new ArrayList<Identity>(identities.values());
    }

    /**
     * Applies trust state of source identity to target identity.
     */
    private void takeoverTrustState(final Identity source, final Identity target) {
        if( source.isFRIEND() ) {
            target.setFRIENDWithoutUpdate();
            target.modify();
        } else if( source.isGOOD() ) {
            target.setGOODWithoutUpdate();
            target.modify();
        } else if( source.isBAD() ) {
            target.setBADWithoutUpdate();
            target.modify();
        } else if( source.isNEUTRAL() ) {
            target.setNEUTRALWithoutUpdate();
            target.modify();
        }
    }

    // TODO: merge the imported identities with the existing identities (WOT), use a mergeIdentities method
    public int importIdentities(final List<Identity> importedIdentities) {
        // for now we import new identities, and take over the trust state if our identity state is NEUTRAL
        if( !IdentitiesStorage.inst().beginExclusiveThreadTransaction() ) {
            return 0;
        }
        int importedCount = 0;
        try {
            for( final Identity newId : importedIdentities ) {
                if( !isNewIdentityValid(newId) ) {
                    // hash of public key does not match the unique name
                    // skip identity
                    continue;
                }
                final Identity oldId = getIdentity(newId.getUniqueName());
                if( oldId == null ) {
                    // add new id
                    if( addIdentity(newId, false) ) {
                        importedCount++;
                    }
                } else if( oldId.isNEUTRAL() && !newId.isNEUTRAL() ) {
                    // take over trust state
                    takeoverTrustState(newId, oldId);
                    importedCount++;
                }
            }
        } finally {
            IdentitiesStorage.inst().endThreadTransaction();
        }
        return importedCount;
    }

    /**
     * This method checks an Identity for validity.
     * Checks if the digest of this Identity matches the pubkey (digest is the part after the @ in the username)
     */
    public boolean isIdentityValid(final Identity id) {

        if( id == null ) {
            return false;
        }

        final String uName = id.getUniqueName();
        final String puKey = id.getPublicKey();

        try {
            // check if the digest matches
            final String given_digest = uName.substring(uName.indexOf("@") + 1, uName.length()).trim();

            String calculatedDigest = Core.getCrypto().digest(puKey.trim()).trim();
            calculatedDigest = Mixed.makeFilename(calculatedDigest).trim();

            // FIX: given_digest must already not contain invalid characters
//            if( !Mixed.makeFilename(given_digest).equals(calculatedDigest) ) {
            if( !given_digest.equals(calculatedDigest) ) {
                logger.severe("Warning: public key of sharer didn't match its digest:\n" +
                              "given digest :'" + given_digest + "'\n" +
                              "pubkey       :'" + puKey.trim() + "'\n" +
                              "calc. digest :'" + calculatedDigest + "'");
                return false;
            }
        } catch (final Throwable e) {
            logger.log(Level.SEVERE, "Exception during key validation", e);
            return false;
        }
        return true;
    }

    /**
     * Checks if we can accept this new identity.
     * If the public key of this identity is already assigned to another identity, then it is not valid.
     */
    public boolean isNewIdentityValid(final Identity id) {

        // check if hash matches the public key
        if( !isIdentityValid(id) ) {
            return false;
        }

        // check if the public key is known, maybe someone sends with same pubkey but different names before the @
        for( final Identity anId : getIdentities() ) {
            if( id.getPublicKey().equals(anId.getPublicKey())
                    && !id.getUniqueName().equals(anId.getUniqueName()) )
            {
                logger.severe("Rejecting new Identity because its public key is already used by another known Identity. "+
                        "newId='"+id.getUniqueName()+"', oldId='"+anId.getUniqueName()+"'");
                return false;
            }
        }

        // for sure, check own identities too
        for( final Iterator<LocalIdentity> i=getLocalIdentities().iterator(); i.hasNext(); ) {
            final Identity anId = i.next();
            if( id.getPublicKey().equals(anId.getPublicKey())
                    && !id.getUniqueName().equals(anId.getUniqueName()) )
            {
                logger.severe("Rejecting new Identity because its public key is already used by an OWN Identity. "+
                        "newId='"+id.getUniqueName()+"', oldId='"+anId.getUniqueName()+"'");
                return false;
            }
        }
        return true;
    }

    public Object getLockObject() {
        return lockObject;
    }
}
