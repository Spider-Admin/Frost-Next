/*
 MD5FileHash.java / Frost-Next
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

package frost.fileTransfer.download.HashBlocklistTypes;

import org.garret.perst.*;

/**
 * A memory-efficient representation of a file hash.
 * Uses native longs instead of an MD5 "String" object, for *vastly* lower memory/disk usage.
 *
 * This object is immutable and only has getters after construction.
 *
 * If stored in a database, a "perst.string.encoding" of "UTF-8" is recommended,
 * to roughly halve the disk usage of the filepath strings.
 */
public class MD5FileHash extends Persistent
{
    private long fTopMD5Bits;
    private long fBottomMD5Bits;
    private String fFilePath;

    /**
     * Creates an instance from a valid MD5 hash string.
     *
     * It is your responsibility to ensure that the entire input value is a legal hex string,
     * or an exception will be thrown. Always wrap this constructor in a try-catch!
     *
     * @param  aHashStr  the 32-character MD5 string; is treated as case-insensitive base16 (hex)
     * @param  aFilePath  the path to the file with this hash, allowed to be null (useful when
     *     only constructing this object to calculate longs for querying the database)
     */
    public MD5FileHash(
            final String aHashStr,
            final String aFilePath)
        throws IllegalArgumentException
    {
        if( aHashStr == null || aHashStr.length() != 32 )
            throw new IllegalArgumentException("Invalid hash provided; must be 32-character MD5 hex string.");

        try {
            fTopMD5Bits = Long.parseUnsignedLong(aHashStr.substring(0, 16), 16);
            fBottomMD5Bits = Long.parseUnsignedLong(aHashStr.substring(16), 16);
        } catch( final Exception ex ) {
            throw new IllegalArgumentException("Invalid hash provided; not a legal hex string.");
        }

        fFilePath = aFilePath;
    }

    /**
     * Alternative constructor which directly uses signed longs of the same format
     * as getTopMD5Bits() and getBottomMD5Bits().
     *
     * @param  aTopMD5Bits  the top 64-bit half of the 128-bit MD5
     * @param  aBottomMD5Bits  the bottom 64-bit half of the 128-bit MD5
     * @param  aFilePath  the path to the file with this hash, allowed to be null
     */
    public MD5FileHash(
            final long aTopMD5Bits,
            final long aBottomMD5Bits,
            final String aFilePath)
    {
        fTopMD5Bits = aTopMD5Bits;
        fBottomMD5Bits = aBottomMD5Bits;
        fFilePath = aFilePath;
    }

    /**
     * @return  the top 64-bit half of the 128-bit MD5
     */
    public long getTopMD5Bits()
    {
        return fTopMD5Bits;
    }

    /**
     * @return  the bottom 64-bit half of the 128-bit MD5
     */
    public long getBottomMD5Bits()
    {
        return fBottomMD5Bits;
    }

    /**
     * @return  the filepath; will be null if not given in constructor
     */
    public String getFilePath()
    {
        return fFilePath;
    }

    /**
     * Returns the internal hash as 32-character hex string.
     *
     * @return  the hex string, using only lowercase (0-9a-f)
     */
    public String getMD5String()
    {
        final StringBuilder sb = new StringBuilder(32);

        final String topHex = Long.toUnsignedString(fTopMD5Bits, 16);
        if( topHex.length() < 16 )
            sb.append("0000000000000000".substring(topHex.length()));
        sb.append(topHex);

        final String bottomHex = Long.toUnsignedString(fBottomMD5Bits, 16);
        if( bottomHex.length() < 16 )
            sb.append("0000000000000000".substring(bottomHex.length()));
        sb.append(bottomHex);

        return sb.toString();
    }

    /**
     * @see  #getMD5String
     */
    @Override
    public String toString()
    {
        return getMD5String();
    }

    /**
     * Returns a hashcode for sorting this MD5 object into hash-buckets, for instance.
     * For speed, we simply mask out 4 bytes (32 bit) from the middle of the 16-byte (128 bit)
     * MD5. This is safe, since every MD5 bit has a ~50% probability of being either 0 or 1,
     * so we'll get very unique hashcodes even though we only look at 1/4 of the bits.
     *
     * @return  a hash code value for this object
     */
    @Override
    public int hashCode()
    {
        // NOTE: this AND must be done with "L" (long)suffix, to avoid causing int->long
        // sign extension which would otherwise mask out the wrong bits from the long.
        return (int)(fTopMD5Bits & 0xffffffffL);
    }

    /**
     * Compares this MD5 hash to another object. The result is true if and only if the
     * argument is not null and is a MD5FileHash object representing the same MD5 hash.
     * The filepaths of the objects are ignored when comparing.
     *
     * @return  true if the objects represent the same MD5 hash; false otherwise
     */
    @Override
    public boolean equals(
            final Object obj)
    {
        if( !(obj instanceof MD5FileHash) )
            return false;
        if( obj == this )
            return true;

        final MD5FileHash other = (MD5FileHash) obj;
        return ( fTopMD5Bits == other.fTopMD5Bits &&
                 fBottomMD5Bits == other.fBottomMD5Bits );
    }
}
