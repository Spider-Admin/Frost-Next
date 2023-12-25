/*
  SearchMessagesConfig.java / Frost
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
package frost.gui;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import frost.messaging.frost.boards.*;
import frost.util.*;
import frost.util.gui.translation.*;
import frost.util.gui.MiscToolkit;

/**
 * This class contains all configured search options.
 */
public class SearchMessagesConfig {

    private final Language language = Language.getInstance();

    public boolean senderCaseSensitive = false; // these 3 have no effect after the pattern has been compiled
    public boolean subjectCaseSensitive = false;// ...but they can be used to look up the current state
    public boolean contentCaseSensitive = false;

    public String senderString = ""; // lets you look up the string used to compile each regex pattern
    public String subjectString = ""; // ...this is useful in order to refuse searches if !string.isEmpty() && pattern==null
    public String contentString = ""; // ...which means the user provided an invalid pattern (so search should be denied)

    public Pattern senderPattern = null; // the actual compiled regex patterns used for the searches
    public Pattern subjectPattern = null; // ...can be null objects if the patterns were invalid regexps/empty string
    public Pattern contentPattern = null; // ...so be sure to check "!=null" before use

    public Boolean searchPrivateMsgsOnly = null;
    public Boolean searchFlaggedMsgsOnly = null;
    public Boolean searchStarredMsgsOnly = null;
    public Boolean searchRepliedMsgsOnly = null;

    public static final int BOARDS_DISPLAYED  = 1;
//    public static final int BOARDS_EXISTING_DIRS = 2;
    public static final int BOARDS_CHOSED = 3;
    public int searchBoards = 0;
    public List<Board> chosedBoards = null; // list of Board objects

    public static final int DATE_DISPLAYED = 1;
    public static final int DATE_ALL = 2;
    public static final int DATE_BETWEEN_DATES = 3;
    public static final int DATE_DAYS_BACKWARD = 4;
    public int searchDates;
    public long startDate, endDate;
    public int daysBackward;

    public static final int TRUST_DISPLAYED = 1;
    public static final int TRUST_ALL = 2;
    public static final int TRUST_CHOSED = 3;
    public int searchTruststates = 0;
    public boolean trust_BAD = false;
    public boolean trust_NEUTRAL = false;
    public boolean trust_GOOD = false;
    public boolean trust_FRIEND = false;
    public boolean trust_TAMPERED = false;
    public boolean trust_NONE = false;

    public boolean searchInKeypool = false;
    public boolean searchInArchive = false;

    public boolean msgMustContainBoards = false;
    public boolean msgMustContainFiles = false;

    /**
     * Compiles and validates a pattern, and sets it to either case sensitive or case-insensitive.
     * Note that the patterns 
     * By making this a global function, we ensure that we only compile each pattern once (and
     * don't waste time doing it for each match).
     */
    private Pattern compilePattern(final String regexText, final boolean caseSensitive) {
        if( regexText.isEmpty() ){
            return null; // if they didn't provide a pattern, just return a null object instantly
        }

        Pattern p;
        try {
            if( caseSensitive ) {
                // case sensitive search, ^ and $ matches start/end of lines
                p = Pattern.compile(regexText, Pattern.MULTILINE);
            } else {
                // ASCII & Unicode case-insensitive search, ^ and $ matches start/end of lines
                p = Pattern.compile(regexText, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            }
        } catch( PatternSyntaxException e ) {
            // warn the user if they entered an invalid regex (the most common error is not
            // closing their "(" capture groups if they don't realize that it's a regex, etc)
            MiscToolkit.showMessageDialog(
                    null,
                    language.getString("SearchMessages.errorDialogs.invalidSearchPattern") + "\n\n" +
                        e.getDescription() + " near character " + Integer.toString(e.getIndex()) + ".\n" +
                        "Pattern: " + regexText,
                    language.getString("SearchMessages.errorDialogs.title"),
                    MiscToolkit.ERROR_MESSAGE);

            // this pattern cannot be used for searching, since it was invalid!
            // the search function must check the provided string; if !string.isEmpty() && pattern==null,
            // then refuse the search since the pattern was invalid.
            return null; // this pattern cannot be used for searching
        }
        return p;
    }

    public void setSearchSender(final String s, final boolean caseSensitive) {
        senderCaseSensitive = caseSensitive;
        senderString = s;
        senderPattern = compilePattern(senderString, senderCaseSensitive);
    }
    public void setSearchSubject(final String s, final boolean caseSensitive) {
        subjectCaseSensitive = caseSensitive;
        subjectString = s;
        subjectPattern = compilePattern(subjectString, subjectCaseSensitive);
    }
    public void setSearchContent(final String s, final boolean caseSensitive) {
        contentCaseSensitive = caseSensitive;
        contentString = s;
        contentPattern = compilePattern(contentString, contentCaseSensitive);
    }
}
