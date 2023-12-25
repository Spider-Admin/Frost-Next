/*
 AbstractMessageStatusProvider.java / Frost
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
package frost.messaging.frost;

import java.util.Vector;
import java.util.logging.*;

import javax.swing.tree.*;

import frost.*;
import frost.identities.*;

@SuppressWarnings("serial")
public abstract class AbstractMessageStatusProvider extends DefaultMutableTreeNode {

    private static final Logger logger = Logger.getLogger(AbstractMessageStatusProvider.class.getName());

    // the message states
    private static final int xBAD      = 1;
    private static final int xNEUTRAL  = 2;
    private static final int xGOOD     = 3;
    private static final int xFRIEND   = 4;
    private static final int xTAMPERED = 5;
    private static final int xNONE     = 6;

    private static String[] messageStateStrings = {
        "*err*",
        "BAD",
        "NEUTRAL",
        "GOOD",
        "FRIEND",
        "FAKE", // tampered
        "NONE" // unsigned
    };

    private static final int SIGNATURESTATUS_UNSET    = 0; // status not set
    private static final int SIGNATURESTATUS_TAMPERED = 1; // wrong signature
    private static final int SIGNATURESTATUS_NONE     = 2; // no signature
    private static final int SIGNATURESTATUS_VERIFIED = 3; // signature was OK
    private static final int SIGNATURESTATUS_VERIFIED_V1 = 4; // signature was OK
    private static final int SIGNATURESTATUS_VERIFIED_V2 = 5; // signature was OK

    private static final String SIGNATURESTATUS_TAMPERED_STR = "TAMPERED"; // wrong signature
    private static final String SIGNATURESTATUS_NONE_STR     = "NONE";     // no signature
    private static final String SIGNATURESTATUS_VERIFIED_STR = "VERIFIED"; // signature was OK

    private boolean isFromIdentityInitialized = false;
    private Identity fromIdentity = null;

    private String fromName = "";
    private String publicKey  = "";

    private int signatureStatus = SIGNATURESTATUS_UNSET;

    public Identity getFromIdentity() {
        if( isFromIdentityInitialized == false ) {
            if( getFromName() == null || getFromName().length() == 0 ) {
                // unsigned message
                fromIdentity = null;
            } else {
                // set Identity for FROM, or null
                synchronized( Core.getIdentities().getLockObject() ) {
                    fromIdentity = Core.getIdentities().getIdentity(getFromName());
                    // if identity was NOT found, add it. maybe it was deleted by the user,
                    // but we still have a msg from this identity
                    if( fromIdentity == null && getPublicKey() != null && getPublicKey().length() > 0 ) {
                        final Identity newFromIdentity = Identity.createIdentityFromExactStrings(getFromName(), getPublicKey());
                        if( !Core.getIdentities().addIdentity(newFromIdentity) ) {
                            logger.severe("Core.getIdentities().addIdentity(owner) returned false for identy: "+newFromIdentity.getUniqueName());
                            setSignatureStatusNONE();
                        } else {
                            fromIdentity = newFromIdentity;
                        }
                    }
                }
            }
            isFromIdentityInitialized = true;
        }
        return fromIdentity;
    }

    public void setFromIdentity(final Identity id) {
        if( isFromIdentityInitialized ) {
            logger.severe("Already initialized, old="+fromIdentity+"; new="+id);
        } else {
            fromIdentity = id;
            isFromIdentityInitialized = true;
        }
    }

    public String getFromName() {
        return fromName;
    }
    public void setFromName(final String from) {
        this.fromName = from;
    }

    /**
     * Converts the signature status string contained in local XML message file
     * into the internal constant.
     * Only used for imports!
     */
    public boolean setSignatureStatusFromString(final String sigStatusStr) {
        if( sigStatusStr.equalsIgnoreCase(SIGNATURESTATUS_VERIFIED_STR) ) {
            setSignatureStatusVERIFIED_V1();
            return true;
        } else if( sigStatusStr.equalsIgnoreCase(SIGNATURESTATUS_NONE_STR) ) {
            setSignatureStatusNONE();
            return true;
        } else if( sigStatusStr.equalsIgnoreCase(SIGNATURESTATUS_TAMPERED_STR) ) {
            setSignatureStatusTAMPERED();
            return true;
        }
        return false;
    }

    private int getMessageStatus(final Identity fromIdent) {
        if( isSignatureStatusVERIFIED() ) {
            // get state of user (checked in order of popularity, for speed)
            if( fromIdent == null ) {
                return xNONE;
            }
            if( fromIdent.isNEUTRAL() ) { // the default state is most popular
                return xNEUTRAL;
            }
            if( fromIdent.isGOOD() ) {
                return xGOOD;
            }
            if( fromIdent.isFRIEND() ) {
                return xFRIEND;
            }
            if( fromIdent.isBAD() ) {
                return xBAD;
            }
        } else if( isSignatureStatusNONE() ) {
            // no signature
            return xNONE;
        } else if( isSignatureStatusTAMPERED() ) {
            // invalid signature
            return xTAMPERED;
        }
        // signature status unset
        return xNONE;
    }

    private int getMessageStatus() {
        return getMessageStatus(getFromIdentity());
    }

    public String getMessageStatusString() {
    	final Identity i = getFromIdentity();
    	if (i instanceof LocalIdentity && !Core.frostSettings.getBoolValue(SettingsClass.SHOW_OWN_MESSAGES_AS_ME_DISABLED)) {
			return "ME";
        } else {
            return messageStateStrings[getMessageStatus(i)];
        }
    }

    public boolean isMessageFromME() {
        final Identity i = getFromIdentity();
        if (i instanceof LocalIdentity) {
            return true;
        } else {
            return false;
        }
    }

    public boolean isMessageStatusBAD() {
        return (getMessageStatus() == xBAD );
    }
    public boolean isMessageStatusNEUTRAL() {
        return (getMessageStatus() == xNEUTRAL );
    }
    public boolean isMessageStatusGOOD() {
        return (getMessageStatus() == xGOOD );
    }
    public boolean isMessageStatusFRIEND() {
        return (getMessageStatus() == xFRIEND );
    }
    public boolean isMessageStatusTAMPERED() {
        return (getMessageStatus() == xTAMPERED );
    }
    public boolean isMessageStatusNONE() {
        return (getMessageStatus() == xNONE );
    }

    public boolean isSignatureStatusVERIFIED() {
        if( getSignatureStatus() == SIGNATURESTATUS_VERIFIED
                || getSignatureStatus() == SIGNATURESTATUS_VERIFIED_V1
                || getSignatureStatus() == SIGNATURESTATUS_VERIFIED_V2
          )
        {
            return true;
        }
        return false;
    }
    // utility method
    public static boolean isSignatureStatusVERIFIED(final int sigstat) {
        if( sigstat == SIGNATURESTATUS_VERIFIED
                || sigstat == SIGNATURESTATUS_VERIFIED_V1
                || sigstat == SIGNATURESTATUS_VERIFIED_V2
          )
        {
            return true;
        }
        return false;
    }
    public boolean isSignatureStatusVERIFIED_V1() {
        return (getSignatureStatus() == SIGNATURESTATUS_VERIFIED_V1);
    }
    public boolean isSignatureStatusVERIFIED_V2() {
        return (getSignatureStatus() == SIGNATURESTATUS_VERIFIED_V2);
    }
    public boolean isSignatureStatusNONE() {
        return (getSignatureStatus() == SIGNATURESTATUS_NONE);
    }
    public boolean isSignatureStatusTAMPERED() {
        return (getSignatureStatus() == SIGNATURESTATUS_TAMPERED);
    }

    public void setSignatureStatusVERIFIED_V1() {
        signatureStatus = SIGNATURESTATUS_VERIFIED_V1;
    }
    public void setSignatureStatusVERIFIED_V2() {
        signatureStatus = SIGNATURESTATUS_VERIFIED_V2;
    }
    public void setSignatureStatusNONE() {
        signatureStatus = SIGNATURESTATUS_NONE;
    }
    public void setSignatureStatusTAMPERED() {
        signatureStatus = SIGNATURESTATUS_TAMPERED;
    }

    public int getSignatureStatus() {
        return signatureStatus;
    }
    public void setSignatureStatus(final int s) {
        signatureStatus = s;
    }

    public String getPublicKey() {
        return publicKey;
    }
    public void setPublicKey(final String pk) {
        publicKey = pk;
    }
}
