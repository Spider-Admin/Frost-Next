/*
 ArgumentTokenizer.java / Frost-Next
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

package frost.util;

import java.lang.Character;
import java.lang.IllegalStateException;
import java.lang.StringBuilder;
import java.util.List;
import java.util.LinkedList;

/**
 * [ORIGINAL: edu.rice.cs.util.ArgumentTokenizer, cleaned up and reformatted for use in Frost]
 *
 * Utility class which can tokenize a String into a list of String arguments,
 * with behavior similar to Bash parsing command line arguments for a program.
 * Quoted Strings are treated as single arguments, and escaped characters
 * are translated so that the tokenized arguments have the same meaning.
 * Since all methods are static, the class is declared abstract to prevent
 * instantiation.
 */
public abstract class ArgumentTokenizer
{
    private static final int NO_TOKEN_STATE = 0;
    private static final int NORMAL_TOKEN_STATE = 1;
    private static final int SINGLE_QUOTE_STATE = 2;
    private static final int DOUBLE_QUOTE_STATE = 3;

    /**
     * Tokenizes the given String into String tokens
     * @param arguments A String containing one or more command-line style arguments to be tokenized.
     * @return An unescaped list of all parsed arguments.
     */
    public static List<String> tokenize(
            final String arguments)
    {
        return tokenize(arguments, false);
    }

    /**
     * Tokenizes the given String into String tokens.
     * @param arguments A String containing one or more command-line style arguments to be tokenized.
     * @param escapeArgs If true, we'll escape all the program name and all parameters and put them
     * all in double quotes, so that 'foo"bar' -> '"foo\"bar"'. If false, you'll get clean strings
     * (so if the input was '"foo\"bar"', you will get 'foo"bar'). False is always the desired behavior
     * if you're going to pass the arguments to a ProcessBuilder (which will take care of escaping
     * in a system-appropriate way). If you're going to use the (ANCIENT, DANGEROUS) "getEnvironment().exec()"
     * method of launching programs instead, you'll want True so that everything is properly escaped.
     * @return A list of parsed and properly escaped arguments.
     */
    public static List<String> tokenize(
            final String arguments,
            final boolean escapeArgs)
    {
        LinkedList<String> argList = new LinkedList<String>();
        StringBuilder currArg = new StringBuilder();
        boolean escaped = false;
        int state = NO_TOKEN_STATE;  // start in the NO_TOKEN_STATE
        int len = arguments.length();

        // Loop over each character in the string
        for( int i = 0; i < len; ++i ) {
            char c = arguments.charAt(i);
            if( escaped ) {
                // Escaped state: just append the next character to the current arg.
                escaped = false;
                currArg.append(c);
            } else {
                switch( state ) {
                    case SINGLE_QUOTE_STATE:
                        if( c == '\'' ) {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE;
                        } else {
                            currArg.append(c);
                        }
                        break;
                    case DOUBLE_QUOTE_STATE:
                        if( c == '"' ) {
                            // Seen the close quote; continue this arg until whitespace is seen
                            state = NORMAL_TOKEN_STATE;
                        } else if (c == '\\') {
                            // Look ahead, and only escape quotes or backslashes
                            i++;
                            char next = arguments.charAt(i);
                            if( next == '"' || next == '\\' ) {
                                currArg.append(next);
                            } else {
                                currArg.append(c);
                                currArg.append(next);
                            }
                        } else {
                            currArg.append(c);
                        }
                        break;
                    case NO_TOKEN_STATE:
                    case NORMAL_TOKEN_STATE:
                        switch( c ) {
                            case '\\':
                                escaped = true;
                                state = NORMAL_TOKEN_STATE;
                                break;
                            case '\'':
                                state = SINGLE_QUOTE_STATE;
                                break;
                            case '"':
                                state = DOUBLE_QUOTE_STATE;
                                break;
                            default:
                                if( !Character.isWhitespace(c) ) {
                                    currArg.append(c);
                                    state = NORMAL_TOKEN_STATE;
                                } else if( state == NORMAL_TOKEN_STATE ) {
                                    // Whitespace ends the token; start a new one
                                    argList.add(currArg.toString());
                                    currArg = new StringBuilder();
                                    state = NO_TOKEN_STATE;
                                }
                        }
                        break;
                    default:
                        throw new IllegalStateException("ArgumentTokenizer state " + state + " is invalid!");
                }
            }
        }

        // If we're still escaped, put in the backslash
        if( escaped ) {
            currArg.append('\\');
            argList.add(currArg.toString());
        }
        // Close the last argument if we haven't yet
        else if( state != NO_TOKEN_STATE ) {
            argList.add(currArg.toString());
        }
        // Format each argument if we've been told to escape them
        if( escapeArgs ) {
            for( int i = 0; i < argList.size(); ++i ) {
                argList.set(i, "\"" + _escapeQuotesAndBackslashes(argList.get(i)) + "\"");
            }
        }
        return argList;
    }

    /**
     * Inserts backslashes before any occurrences of a backslash or
     * quote in the given string. Also converts any special characters
     * appropriately.
     */
    protected static String _escapeQuotesAndBackslashes(
            final String s)
    {
        final StringBuilder buf = new StringBuilder(s);

        // Walk backwards, looking for quotes or backslashes.
        //  If we see any, insert an extra backslash into the buffer at
        //  the same index.  (By walking backwards, the index into the buffer
        //  will remain correct as we change the buffer.)
        for( int i = s.length()-1; i >= 0; --i ) {
            char c = s.charAt(i);
            if( (c == '\\') || (c == '"') ) {
                buf.insert(i, '\\');
            }
            // Replace any special characters with escaped versions
            else if( c == '\n' ) {
                buf.deleteCharAt(i);
                buf.insert(i, "\\n");
            }
            else if( c == '\t' ) {
                buf.deleteCharAt(i);
                buf.insert(i, "\\t");
            }
            else if( c == '\r' ) {
                buf.deleteCharAt(i);
                buf.insert(i, "\\r");
            }
            else if( c == '\b' ) {
                buf.deleteCharAt(i);
                buf.insert(i, "\\b");
            }
            else if( c == '\f' ) {
                buf.deleteCharAt(i);
                buf.insert(i, "\\f");
            }
        }
        return buf.toString();
    }
}
