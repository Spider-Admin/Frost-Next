/*
 ExtraInserts.java / Frost-Next
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

public final class ExtraInserts
{
    private ExtraInserts() {} // private constructor prevents instantiation

    public static final String EI2 = "Options.uploads.extraInserts.2";
    public static final String EI5 = "Options.uploads.extraInserts.5";
    public static final String EI7 = "Options.uploads.extraInserts.7";
    public static final String EI10 = "Options.uploads.extraInserts.10";
    public static final String DEFAULT = "Options.uploads.extraInserts.7";

    /**
     * Converts a setting string to its equivalent numerical value.
     * @param String insertSettingString - a string such as "Options.uploads.extraInserts.2".
     * @return int - the numerical representation of that setting, such as "2".
     */
    public static int getExtraInserts(
            final String insertSettingString)
    {
        if( insertSettingString.equals( EI2 ) ) {
            return 2;
        } else if( insertSettingString.equals( EI5 ) ) {
            return 5;
        } else if( insertSettingString.equals( EI10 ) ) {
            return 10;
        } else { // "7" or an invalid value will default to 7
            return 7;
        }
    }
}
