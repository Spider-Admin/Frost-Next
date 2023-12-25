/*
 FrenetCompatibilityManager.java / Frost-Next
 Copyright (C) 2015  "The Kitty@++U6QppAbIb1UBjFBRtcIBZg6NU"

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

package frost.fileTransfer;

import java.util.ArrayList;
import java.util.SortedSet;
import java.util.TreeSet;

import frost.SettingsClass;

public class FreenetCompatibilityManager
{

    private SettingsClass fFrostSettings;

    private final SortedSet<String> fAllModes;
    private final SortedSet<String> fInternalModesOnly;
    private final SortedSet<String> fUserModesOnly;
    
    /**
     * @param {SettingsClass} aFrostSettings - if provided, the list of user modes will be saved
     * to Frost.ini during class construction and any time that "addUserModes()" is called;
     * therefore, only *one* instance should be given the settings class; any additional instances
     * of FreenetCompatibilityManager must use a null value to avoid overwriting each other's saves!
     * @param {String} aAdditionalModes - see addUserModes(); can be null
     * Warning: If you provide a non-null aFrostSettings value, then we will automatically save the
     * current "user-provided modes" state to Frost.ini when this manager class is instantiated,
     * as well as any time that "addUserModes()" is called. So if you pass in an aFrostSettings
     * value, you *must* either pass in the current Frost.ini setting as the "aAdditionalModes"
     * parameter (get it from "SettingsClass.FREENETCOMPATMANAGER_USERMODES"), *or* load the Frost.ini
     * setting and insert it manually via "addUserModes()" later. Otherwise the user's previous
     * settings will be lost.
     */
    public FreenetCompatibilityManager(
        final SettingsClass aFrostSettings,
        final String aAdditionalModes)
    {
        this.fFrostSettings = aFrostSettings;

        // a treeset guarantees that all objects are ordered in their native sort-order (as defined
        // by String.compareTo()). furthermore, it compares the *values* (not the object ref) of
        // the objects and only inserts completely unique values this is exactly what we need for
        // the Freenet mode collection, since it guarantees we'll have a unique, naturally sorted list
        fInternalModesOnly = new TreeSet<String>(); // stores a copy of all internal (default) modes
        fUserModesOnly = new TreeSet<String>(); // stores a copy of any entries that were added by the user at runtime

        // add the default internal set of compatibility modes (we're adding them in the same order
        // that they'll be sorted, for clarity here)
        fInternalModesOnly.add("COMPAT_1250");
        fInternalModesOnly.add("COMPAT_1250_EXACT");
        fInternalModesOnly.add("COMPAT_1251");
        fInternalModesOnly.add("COMPAT_1255");
        fInternalModesOnly.add("COMPAT_1416");
        fInternalModesOnly.add("COMPAT_1468");
        fInternalModesOnly.add("COMPAT_CURRENT");
        fInternalModesOnly.add("COMPAT_UNKNOWN"); // internal usage in Freenet when it can't figure out what mode was used

        // initialize the main "all modes" object using the internal set of modes
        fAllModes = new TreeSet<String>(fInternalModesOnly); // stores all modes (both internal and user-added)

        // include any user-provided modes from the constructor
        addUserModes(aAdditionalModes);
    }

    /**
     * Simple, static helper function which returns the default mode-string used by Freenet to mean
     * "use the latest mode". Always use this instead of hardcoding the value, since the default
     * may change in the future.
     * @return - the "COMPAT_CURRENT" compatibility string
     */
    public static String getDefaultMode()
    {
        return "COMPAT_CURRENT";
    }

    /**
     * Checks whether the provided string is a mode known by this compatibility manager instance.
     * NOTE: We do no string cleanup, so "COMPAT_1468 " will not match "COMPAT_1468".
     * Clean the input first if required!
     * @param {String} aModeString - a string such as "COMPAT_1468"
     * @return - true if known, otherwise false
     */
    public boolean isKnownMode(
            final String aModeString)
    {
        if( aModeString == null ) { return false; }
        synchronized(fAllModes) {
            return fAllModes.contains(aModeString);
        }
    }

    /**
     * Returns a defensive copy of the list of all modes, in their sorted order. Fully thread-safe.
     * The defensive copy means that you can iterate/modify the returned list without worrying
     * about threading. Your changes will not be saved to this class. You'll have to use
     * "addUserModes()" for that.
     * NOTE: When using this list to provide the user with a choice of modes to use, you should
     * *always* SKIP Freenet's internal "COMPAT_UNKNOWN" entry.
     * @return - A list containing all modes currently in this compatibility manager instance.
     */
    public ArrayList<String> getAllModes()
    {
        synchronized(fAllModes) {
            return new ArrayList<String>(fAllModes);
        }
    }

    /**
     * Identical to "getAllModes()", except that it only returns the hardcoded, internal (default)
     * set of modes. Fully thread-safe.
     * @return - A list of all internal modes.
     */
    public ArrayList<String> getInternalModesOnly()
    {
        synchronized(fAllModes) {
            return new ArrayList<String>(fInternalModesOnly);
        }
    }

    /**
     * Identical to "getAllModes()", except that it only returns the modes added by the user at
     * runtime. Fully thread-safe.
     * @return - A list of all modes that were added by the user at runtime. Will be empty,
     * if no modes were added.
     */
    public ArrayList<String> getUserModesOnly()
    {
        synchronized(fAllModes) {
            return new ArrayList<String>(fUserModesOnly);
        }
    }

    /**
     * Adds a user-provided list of modes to the compatibility manager instance. Fully thread-safe.
     * The added entries will be automatically de-duplicated and sorted (using case-sensitive
     * ordering) when merged with the current list.
     * Sorting is done natively as defined by String.compareTo(), and is a per-character,
     * case-sensitive sort, so it will sort "foo115,foo2" as "foo115,foo2" since 1 is lower than 2.
     * For Freenet compatibility modes, it doesn't really matter,since the resulting sort looks good.
     * NOTE: If this manager instance was created with a non-null SettingsClass object, we will save
     * the user list to Frost.ini every time this is called.
     * NOTE: You do not have to worry about duplicates. The data is only inserted if the mode
     * is not known yet.
     * NOTE: No pre-validation of user-provided data is required; we do all of the validation here
     * and only add clean entries.
     * @param {String} aAdditionalModes - a semicolon-separated list of modes, such as "COMPAT_1500;COMPAT_1640"
     * @return - true if at least one of the modes were added (passed validation *and* was unique,
     * meaning it did not exist in the built-in modes or the current list of user modes), false if
     * all of the modes were rejected (such as because they already exist); if you only provide one
     * mode, then you know by the true/false whether it was unique or not, but it's still better to
     * use "isKnownMode()" for speed reasons before trying to blindly add a mode. this return value
     * should really only be used to check whether a dynamically discovered, unique mode passed the
     * *validation* and was added to the internal user modes list.
     */
    public boolean addUserModes(
            final String aAdditionalModes)
    {
        synchronized(fAllModes) {
            boolean addedNewMode = false;

            if( aAdditionalModes != null ) {
                // parse a semicolon-separated list of additional modes provided by the Frost user
                // this allows them to extend the list of compatibility modes in the future,
                // without requiring a new/recompiled Frost
                for( String userMode : aAdditionalModes.split(";") ) {
                    // we only do some very minor cleanup and validation; and we don't assume that
                    // the string should start with "COMPAT_" since that could change in future
                    // Freenet releases... if the user wants to provide some bad values here,
                    // then let them!
                    userMode = userMode.trim(); // remove leading and trailing whitespace
                    if( userMode.equals("") ) { continue; } // skip empty strings
                    boolean isASCII = true;
                    char c;
                    for( int i=0, len=userMode.length(); i<len; ++i ) {
                        c = userMode.charAt(i);
                        // only allows printable characters in the ASCII range (0x20 to 0x7E inclusive)
                        // NOTE: we intentionally leave out 0x9 (tab), 0xA (newline) and 0xD (carriage return)
                        if( c < 0x20 || c > 0x7E ) {
                            isASCII = false;
                            break;
                        }
                    }
                    if( !isASCII ) { continue; } // skip Unicode and other country specific character sets, illegal control bytes, etc

                    // alright, we've got a printable-ASCII, non-empty string... it may still be
                    // full of garbage like "[;<" or other weird characters; but that's up to the
                    // user... now let's simply make sure the mode isn't already known by the default
                    // (internal) set of modes, or by the current user modes.
                    if( fAllModes.contains(userMode) ) { continue; }

                    // we will now simply add this validated and unique string to the mode collection.
                    fAllModes.add(userMode);
                    fUserModesOnly.add(userMode); // adds a copy to let us keep track of which modes the user added at runtime
                    addedNewMode = true;
                }
            }

            // if we have the settings class, then we must save the current list of user modes
            // (even if empty) to Frost.ini
            if( fFrostSettings != null ) {
                saveUserModes();
            }

            return addedNewMode;
        }
    }

    /**
     * Helper function which makes it super easy to teach the instance about new modes. Fully thread-safe.
     * The most useful way to invoke this is to read the "CompatibilityMode" parameter of incoming
     * FCP messages (for uploads/downloads), and automatically learn new modes that way.
     * NOTE: The input is automatically cleaned up and validated, so you don't need to validate
     * it ahead of time. Instead, *you* should be reading the output of this function and using the
     * validated string if it passes.
     * NOTE: You should NEVER waste time running "isKnownMode()" manually before this function,
     * since we do all string cleanup and validation here in a single place, and return very
     * quickly if the mode is already known.
     * @param {String} aUserMode - the mode you want to try to add to this instance
     * @return - the *clean* version of the provided mode string *if* the mode was added *or* if it
     * was already known, otherwise returns null if the mode was rejected. you *must* always use
     * this function return value if you plan to "setCompatibilityMode()" afterwards, so that you're
     * sure to keep the clean string as opposed to your possibly-dirty input parameter.
     */
    public String learnNewUserMode(
            final String aUserMode)
    {
        if( aUserMode == null ) { return null; }            // rejected; no string provided
        if( aUserMode.contains(";") ){ return null; }       // rejected; contains multi-mode list separator but we expect a single mode
        final String userMode = aUserMode.trim();           // remove leading and trailing whitespace
        if( userMode.equals("") ) { return null; }          // rejected; we don't bother even trying with empty strings
        synchronized(fAllModes) {
            if( isKnownMode(userMode) ){ return userMode; } // accepted; already known
            final boolean addedNewMode = addUserModes(userMode);
            if( !addedNewMode ) { return null; }            // rejected; mode string did not pass "ASCII-only" validation or was not new after all
            if( fFrostSettings != null ) {                  // output the "learned" console message only if this instance is capable of saving to Frost.ini
                System.out.println("INFO: Frost has dynamically learned about a new Freenet compatibility mode: \""+userMode+"\". You can now use this mode for future uploads, via the \"Change compatibility mode\" right-click menu!");
            }
            return userMode;                                // accepted; the mode string was added to the list of user modes
        }
    }

    /**
     * Internal function which saves the current list of user-provided modes to Frost.ini.
     */
    private void saveUserModes()
    {
        if( fFrostSettings == null ) { return; }
        synchronized(fAllModes) {
            // build a semicolon-separated list of all user-provided modes (which may be empty)
            final StringBuilder sb = new StringBuilder();
            for( String thisMode : fUserModesOnly ) {
                sb.append(";").append(thisMode);
            }
            if( sb.length() > 0 ) { // found user modes, so delete the leading semicolon
                sb.deleteCharAt(0);
            }
            final String newValue = sb.toString();

            // don't save the value if it's equal to the old value (to avoid needlessly firing the "settings changed" event)
            final String oldValue = fFrostSettings.getValue(SettingsClass.FREENETCOMPATMANAGER_USERMODES);
            if( oldValue != null && newValue.equals(oldValue) ) {
                return;
            }

            // alright, save it!
            fFrostSettings.setValue(SettingsClass.FREENETCOMPATMANAGER_USERMODES, newValue);
            System.out.println("INFO: Saving the dynamic list of new Freenet compatibility modes (\""+newValue+"\").");
        }
    }
}
