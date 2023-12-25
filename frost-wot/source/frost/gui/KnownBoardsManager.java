/*
  KnownBoardsManager.java / Frost
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

import java.awt.Component;
import java.util.*;

import frost.messaging.frost.boards.Board;
import frost.messaging.frost.boards.DefaultBoardCollection;
import frost.storage.perst.*;
import frost.util.gui.MiscToolkit;
import frost.util.gui.translation.Language;

/**
 * Manages the access to KnownBoards and hidden board names.
 * Static class, but one instance is created (Singleton) to
 * be able to implement the Savable interface for saving of
 * the hidden board names during shutdown of Frost.
 */
public class KnownBoardsManager {

    private static KnownBoardsManager instance = null;

    private KnownBoardsManager() {}

    // we need the instance only for Exitsavable!
    public static KnownBoardsManager getInstance() {
        if( instance == null ) {
            instance = new KnownBoardsManager();
        }
        return instance;
    }

    /**
     * @return  List of KnownBoard
     */
    public static List<KnownBoard> getKnownBoardsList() {
        return FrostFilesStorage.inst().getKnownBoards();
    }

    /**
     * Called with a list of Board, adds all boards which are not already in storage
     * @param {List<Board} lst - List of Board objects to add
     * @param {boolean} forceDescriptions - if true, we'll forcibly update descriptions
     * of any boards that are already in storage (this flag should only be used by the
     * "import defaults" feature, to update people's outdated boards; so this flag is PRIVATE).
     * @return - the number of uniquely added boards (doesn't count merely updated descriptions)
     */
    private static int addNewKnownBoards(final List<Board> lst, final boolean forceDescriptions) {
        if( lst == null || lst.size() == 0 ) {
            return 0;
        }
        final int added = FrostFilesStorage.inst().addNewKnownBoards(lst, forceDescriptions);
        return added;
    }
    public static int addNewKnownBoards(final List<Board> lst) {
        return addNewKnownBoards(lst, false); // do not force description updates
    }

    /**
     * Deletes the known board from storage
     * @param b  board to delete from known boards list
     */
    public static void deleteKnownBoard(final Board b) {
        FrostFilesStorage.inst().deleteKnownBoard(b);
    }

    /**
     * Load all hidden board names.
     */
    public HashSet<String> loadHiddenBoardNames() {
        return FrostFilesStorage.inst().loadHiddenBoardNames();
    }

    /**
     * Save all hidden board names.
     */
    public void saveHiddenBoardNames(final HashSet<String> names) {
        FrostFilesStorage.inst().saveHiddenBoardNames(names);
    }

    /**
     * Count the number of default boards of a given set of types.
     * @param {int} typeMask - a bitmask; use -1 for all types. see importDefaultBoards() for bitmask usage.
     * @return - the number of boards of those type(s).
     */
    public static int getDefaultBoardsCount(
            final int typeMask)
    {
        int count = 0;
        final Map<Integer,Integer> dbc = DefaultBoardCollection.getDefaultBoardsCount();
        for( Map.Entry<Integer,Integer> entry : dbc.entrySet() ) {
            final int thisType = entry.getKey().intValue();
            final int thisCount = entry.getValue().intValue();
            if( typeMask == -1 || (typeMask & thisType) != 0 ) {
                count += thisCount;
            }
        }
        return count;
    }

    /**
     * Import a set of default boards into the "known boards" storage.
     * @param {int} typeMask - a bitmask of the types to import, or -1 to import every board.
     * example bitmask: "DefaultBoardCollection.TYPE_GENERAL | DefaultBoardCollection.TYPE_PORN"
     * @param {boolean} showStatistics - true if you want to show a message with import-statistics afterwards
     * @param {Component} parentFrame - the frame to parent the message to (must be non-null if showStatistics is true)
     * @param {boolean} alwaysOnTop - see importDefaultBoardsWithConfirmation()
     * @return - the number of unique, added boards (will be 0 if the user already knew all boards)
     */
    public static int importDefaultBoards(
            final int typeMask,
            final boolean showStatistics,
            final Component parentFrame,
            final boolean alwaysOnTop)
    {
        // filter the "all default boards" list based on the provided type-mask
        final List<Board> imports = new ArrayList<Board>();
        final List<DefaultBoardCollection.DefaultBoard> allDefaultBoards = DefaultBoardCollection.getDefaultBoards();
        for( final DefaultBoardCollection.DefaultBoard thisBoard : allDefaultBoards ) {
            // if they want all boards, or this board's type is in the "allowed types" bit mask
            if( typeMask == -1 || (typeMask & thisBoard.getType()) != 0 ) {
                imports.add(thisBoard.getBoard());
            }
        }

        // check if the filtering got rid of all boards
        if( imports.size() == 0 ) {
            return 0;
        }

        // now just add the boards and get the count of unique boards that were added
        final int added = addNewKnownBoards(imports, true); // true = force description updates if boards exist

        // display statistics to the user?
        if( showStatistics ) {
            final Language language = Language.getInstance();
            MiscToolkit.showMessageDialog(
                    parentFrame,
                    language.formatMessage("KnownBoardsFrame.defaultBoardsImported.body",
                            Integer.toString(imports.size()),
                            Integer.toString(added)),
                    language.getString("KnownBoardsFrame.defaultBoardsImported.title"),
                    MiscToolkit.INFORMATION_MESSAGE,
                    null,
                    alwaysOnTop); // whether to display "always on top"
        }

        return added; // number of unique, added boards
    }

    /**
     * This is a small helper which asks the "which set of boards do you want to import?"
     * question using your provided language strings. It also displays import statistics.
     * @param {Component} parentFrame - the frame to parent the message to (must be non-null)
     * @param {String} languageImportQuestionBody - the choice-message to display. valid values are:
     *   Known Boards Manager: "KnownBoardsFrame.confirmImportDefaultBoards.body"
     *   Frost-Next's first startup: "Core.init.ImportDefaultKnownBoardsBody"
     * @param {String} languageImportQuestionTitle - same as above, but the word ".title"/"Title" instead
     * @param {boolean} alwaysOnTop - whether to display the question/statistics dialogs "always on top";
     * you should do that for the Core.init dialog but not for the known boards manager.
     * @return - see importDefaultBoards()
     */
    public static int importDefaultBoardsWithConfirmation(
            final Component parentFrame,
            final String languageImportQuestionBody,
            final String languageImportQuestionTitle,
            final boolean alwaysOnTop)
    {
        // ask the user which set of default boards to import (all boards, non-porn boards, or cancel)
        final Language language = Language.getInstance();
        final String cancelStr = language.getString("KnownBoardsFrame.confirmImportDefaultBoards.button.cancel");
        final String importAllStr = language.getString("KnownBoardsFrame.confirmImportDefaultBoards.button.importAll");
        final String importGeneralStr = language.getString("KnownBoardsFrame.confirmImportDefaultBoards.button.importGeneral");
        final Object[] options = { cancelStr, importAllStr, importGeneralStr }; // NOTE: the L&F determines the button order
        final int answer = MiscToolkit.showOptionDialog(
                parentFrame,
                language.formatMessage(languageImportQuestionBody,
                        Integer.toString(getDefaultBoardsCount(-1)),
                        Integer.toString(getDefaultBoardsCount(DefaultBoardCollection.TYPE_GENERAL))),
                language.getString(languageImportQuestionTitle),
                MiscToolkit.DEFAULT_OPTION,
                MiscToolkit.QUESTION_MESSAGE,
                null,
                options,
                options[1], // default highlight on "Import All" button
                alwaysOnTop); // whether to display "always on top"

        // determine what board types the user wanted
        int typeMask = 0;
        if( answer == 1 ) { // user clicked "import all"
            typeMask = -1; // all types
        } else if( answer == 2 ) { // user clicked "import general"
            typeMask = DefaultBoardCollection.TYPE_GENERAL;
        } else { // they either clicked "cancel" (0) or closed the dialog (-1)
            typeMask = 0;
        }

        // abort if the user clicked "cancel" or closed the dialog
        if( typeMask == 0 ) {
            return 0;
        }

        // import the chosen board type(s) and display a statistics message
        final int added = importDefaultBoards(typeMask, true, parentFrame, alwaysOnTop);

        return added;
    }
}
