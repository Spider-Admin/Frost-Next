/*
 DefaultBoardCollection.java / Frost-Next
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

package frost.messaging.frost.boards;

import java.lang.Integer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import frost.messaging.frost.boards.Board;

/**
 * Do NOT use this class directly! Use the KnownBoardsManager.getDefaultBoardsCount() and
 * KnownBoardsManager.importDefaultBoardsWithConfirmation() wrappers instead. The only reason for you to
 * ever import this class is if you want to manually use the TYPE_* types for a bitmask.
 */
public abstract class DefaultBoardCollection
{
    // board types/categories (in bitmask format)
    public static final int TYPE_GENERAL = 1;
    public static final int TYPE_PORN = (1 << 1); // NOTE: the next type would be "1 << 2", then "1 << 3", etc

    // this inner class describes a default board's type and board object
    public static class DefaultBoard
    {
        private int fType;
        private Board fBoard;

        public DefaultBoard(
                final int aType,
                final Board aBoard)
        {
            fType = aType;
            fBoard = aBoard;
        }

        public Board getBoard()
        {
            return fBoard;
        }

        public int getType()
        {
            return fType;
        }
    }

    // these let you quickly check the board counts without wasting time constructing the board list
    // NOTE: always remember to update if adding/removing boards
    private static final Map<Integer,Integer> fDefaultBoardsCount = new HashMap<Integer,Integer>();
    static {
        fDefaultBoardsCount.put(TYPE_GENERAL, 24);
        fDefaultBoardsCount.put(TYPE_PORN, 38);
    }

    /**
     * Returns a map containing the total and per-type board counts.
     * WARNING: For performance reasons, this isn't a defensive copy. Do NOT modify the map or its values!
     * @return - the various board counts
     */
    public static Map<Integer,Integer> getDefaultBoardsCount()
    {
        return fDefaultBoardsCount;
    }

    /**
     * Returns the complete default list of known boards.
     * NOTE: This list is not run through "isBoardKeyValidForFreenetVersion" from KnownBoardsXmlDAO.java.
     * That is on purpose, since that just checks if the keys are following the Freenet format,
     * and we *know* they do since we exported this list manually.
     */
    public static List<DefaultBoard> getDefaultBoards()
    {
        final ArrayList<DefaultBoard> dkB = new ArrayList<DefaultBoard>(); // short for "defaultKnownBoards"

        // this began as an import of nearly 400 boards, and was cut down using Permafrost
        // information to produce a list of all active/living boards on Freenet. the list is not
        // censored. the purpose is to give people a good starting selection of all active boards
        // on Freenet, so that they can stop asking in "boards" over and over.
        /* NOTE: Future lists can easily be built from an exported knownboards.xml, using this quick&dirty PHP script:
            <?php
            $xml = simplexml_load_file('knownboards.xml');
            $lines = array();
            foreach ($xml->Attachment as $att) {
                if($att['type'] != 'board'){ die('err'); }
                $board = array(
                        'name' => (string)$att->Name,
                        'pubKey' => (string)$att->pubKey,
                        'privKey' => (string)$att->privKey,
                        'desc' => (string)$att->description
                        );
                foreach ($board as $k => $v) { // sanitize user-made fields
                    if($k == 'name' || $k == 'desc'){
                        $board[$k] = str_replace( '"', '\"', trim( preg_replace( '/\s+/', ' ', $v ) ) );
                    }
                }
                $lines[] = '        dkB.add(new DefaultBoard(TYPE_UNKNOWN, new Board( "' . $board['name'] . '", ' . ($board['pubKey']==""?'null':'"'.$board['pubKey'].'"') . ', ' . ($board['privKey']==""?'null':'"'.$board['privKey'].'"') . ', ' . ($board['desc']==""?'null':'"'.$board['desc'].'"') . ' )));';
            }
            sort($lines, SORT_NATURAL | SORT_FLAG_CASE);
            $lines = array_unique($lines, SORT_STRING); // remove duplicates
            foreach ($lines as $l) { echo $l.PHP_EOL; }
            echo '        // list contains: ' . count($lines) . ' boards.'.PHP_EOL;
            ?>
        */
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "12-15girls", null, null, "Board for pictures and videos of 12-15 year old girls." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "14-18girls", null, null, "Board for pictures and videos of 14-18 year old girls." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "boards", null, null, "Announce your new boards here." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "boyporn", "SSK@~81CI6YyRKMJX~l4DbbtyrPLGj0RjNHWu5LWVu2P8FM,9fyPf4cC-5o~0hTnn2u6xPgkb-XTSoZQv3hbfGlq8WI,AQACAAE", "SSK@RizdWA23al5cNkSBpf2KULhazd7rZxKcNXXmjNj9JW4,9fyPf4cC-5o~0hTnn2u6xPgkb-XTSoZQv3hbfGlq8WI,AQECAAE", "Nude models, amateurs and hardcore pictures/videos (boys)." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "boys - nudist themed", null, null, "Mainstream nudist pictures and videos that focus mainly on boys." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "Candid-Girls", null, null, "Hidden camera and candid shots of underage girls." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "CG CP", null, null, "Computer generated child porn." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "child models - boys", null, null, "Board for non-nude child-modeling (boys)." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "child models - girls", null, null, "Board for non-nude child-modeling (girls)." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "cl", null, null, "Board for stuff where the children are being loved. No hurtcore!" )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "cl-philosophy", null, null, "Child Lover Discussions." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "cl-stories", null, null, "Child Lover Stories." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "cp-site-making", null, null, "Board for questions and discussions about creating Freesites for pedo content." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "CP security", null, null, "Staying safe on Frost/Freenet, how to safely download/upload, etc." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "cs.freenet", null, null, "Česká a Slovenská skupina na Freenet-e. Czech and Slovak Freenet group." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "de.freenet", null, null, "Die Anlaufstelle für alle und Deutsche. ;-)" )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "drugs", null, null, "Drug discussions, trip reports, etc." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "ebooks", null, null, "E-books, comics and audio books." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "es.freenet", null, null, "Foro de ayda en español." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "ex-child models", null, null, "A place for the more mature side of those child models we all knew and loved." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "fr.freenet", null, null, "Discuter à propos de Freenet." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "fr.pedophilie", null, null, "Discussions pour pédophiles." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "freenet", null, null, "Discussions about Freenet." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "freenet-announce", "SSK@MGJOpsf32MDiti3I3ipzdLwKAXZFumiih-YE5ABZNQE,gcUpKZ17E18pyAtXzk4FVjLPo8IgpUqiCa~yBB0RSZo,AQACAAE", null, "Announcement of new Freenet versions." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "freenet.0.7.bugs", null, null, "Report bugs and problems with Freenet." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "frost", null, null, "Discussions about Frost and Frost-Next." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "fuqid", null, null, "Discussions about fuqid." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "fuqid-announce", "SSK@qoY-E5SKRu66pmKH64xa~R~w3hXmS5ZNtqnpEGoCVww,HTVcdWChaaebfRAublHSxBSRaRFG91qCwsa3mGF3-QE,AQACAAE", null, "Announcement of new fuqid versions." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "girls.10-13", null, null, "Board for pictures and videos of 10-13 year old girls." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "hurtcore", null, null, "For all your sick, mentally ill needs." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "hussy", null, null, "Board for nude child-modeling (girls)." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "jailbait", null, null, "Board for young teens. Selfies and webcams mostly." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "jp.junk", null, null, "Japanese board for Freenet discussions." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "kidfetish", null, null, "A place to post kid porn with kinky/fetish content." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "ll-series", null, null, "Vintage magazine scans and other vintage pedo media." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "lolicam", null, null, "Board for webcam recordings of children and young teens." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "multimedia", null, null, "Movies, music and TV shows." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "news", null, null, "News, politics and recent events." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "Nudist", null, null, "The Pure, the Cure, the Beauty of God's Greatest Creation, Mankind!" )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "pedo.news", null, null, "Stories about pedos from mainstream media." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "pedomom", null, null, "Women doing sexual stuff with underage boys and/or girls." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "pl.freenet", null, null, "Polski Freenet." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "pl.pedo", null, null, "Pedofilia po polsku." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "pornography", null, null, "Pr0n. Lots of it." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "privacy", null, null, "Security and privacy discussions." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "pthc", null, null, "Preteen hardcore pictures and videos (girls)." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "public", null, null, "The public board. ;-)" )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "ru.freenet", null, null, "Freenet board for the Russian-speaking community." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "ru.pedo", null, null, "Russian pedo board." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "secret-stash", null, null, "Board dedicated to the Secret Stash Freesite." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "sexboards", null, null, "Announce your new porn boards here." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "sites", null, null, "Freesites should be announced here." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "software", null, null, "Software discussions, help and recommendations." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "Studio-13", null, null, "Discussion board for all Studio 13 related subjects, such as Eternal Nymphets/Aphrodites and Swiss Arts." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "Teencam", null, null, "Board for webcams of 14-18 year old girls." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "teen models - girls", null, null, "Girl models in their teen years (nude and non-nude)." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "teen models - boys", null, null, "Boy models in their teen years (nude and non-nude)." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "test", null, null, "The test board." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "toddler_cp", null, null, "Board for pictures and videos of 0-5 year old girls." )));
        dkB.add(new DefaultBoard(TYPE_GENERAL, new Board( "tor", null, null, "For talking about the Tor (The Onion Router, tor.eff.org) network and sharing experiences and ideas." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "tor-childporn", null, null, "Discussions about using Tor for underage porn." )));
        dkB.add(new DefaultBoard(TYPE_PORN, new Board( "vladmodels", null, null, "Pictures, videos, and discussions about Vladmodels." )));
        // list contains: 62 boards.

        return dkB;
    }
}
