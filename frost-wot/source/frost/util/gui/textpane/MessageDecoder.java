/*
 MessageDecoder.java / Frost
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
package frost.util.gui.textpane;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.text.*;

import frost.fcp.*;
import frost.util.gui.*;

/**
 * Message decoder for search freenet keys and smileys,
 * append message to JeditorPane document.
 * @author ET
 */
public class MessageDecoder extends Decoder implements Smileys, MessageTypes {

    private final Logger logger = Logger.getLogger(MessageDecoder.class.getName());

    private boolean smileys = true;
    private boolean freenetKeys = true;

    private final TreeMap<Integer, String> hyperlinkedFileKeys = new TreeMap<Integer, String>();
    private final TreeSet<MessageElement> specialElements = new TreeSet<MessageElement>();

    public MessageDecoder() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void decode(final String message, final JEditorPane parent) {
        if( message == null || message.isEmpty() ) { return; }

        // determine which EditorKit to use for rendering, and swap to the correct one if needed
        if( freenetKeys ) {
            // LinkEditorKit extends WrapEditorKit (which in turn derives from StyledEditorKit) for rendering smileys and links
            if( !(parent.getEditorKit() instanceof LinkEditorKit) ) {
                parent.setEditorKit(new LinkEditorKit());
            }
        } else {
            // if people only want smileys, or neither keys nor smileys, then we'll use a plain kit that only takes care of smileys
            // NOTE: WrapEditorKit is derived from StyledEditorKit but forces line wrapping for extra-long words
            if( !(parent.getEditorKit() instanceof WrapEditorKit) ) {
                parent.setEditorKit(new WrapEditorKit());
            }
        }

        // clear any previous lists of special elements (keys/smileys) and discovered file keys
        specialElements.clear();
        hyperlinkedFileKeys.clear();

        // build new lists of special elements and discovered keys
        // NOTE: this is extremely optimized, and only takes ~1ms at most for even the longest messages
        // NOTE: the specialElements list is sorted according to element position, which means that
        // smileys that occur in key filenames will have a higher start position than the key itself.
        // NOTE: we build the list of freenet keys even if the user has disabled "hyperlinks", because
        // that allows us to avoid rendering smileys when they appear as part of a key filename whenever
        // the user has enabled smileys but disabled hyperlinks.
        processFreenetKeys(message, specialElements, hyperlinkedFileKeys);
        if( smileys ) {
            processSmileys(message, specialElements);
        }

        // now construct a document with proper attributes for all of the special elements
        final Document doc = new DefaultStyledDocument();
        int currentPos = 0;
        try {
            // the message elements are sorted (and iterated) according to their message position
            final Iterator<MessageElement> it = specialElements.iterator();
            while( it.hasNext() ) {
                final MessageElement msgElem = it.next();
                final int elemPos = msgElem.getPosition().intValue();
                final String elemStr = message.substring(elemPos, elemPos + msgElem.getLength());
                final SimpleAttributeSet attr = new SimpleAttributeSet();

                // if the next element start offset is lower than the current position, it means
                // that a smiley code has been detected as part of the current line's key, i.e.:
                // "CHK@../A cool B) file.jpg" (where "B)" is a smiley code). since the key is
                // at an earlier position, it was handled first and the current position was advanced
                // to beyond the end of the key. so if we've got a lower position (aka smiley),
                // we must now ignore it to render the key correctly.
                if( elemPos < currentPos ) {
                    continue;
                }

                // insert any text that existed before this element, using regular unstyled attributes
                if( elemPos > currentPos ) {
                    doc.insertString(doc.getLength(), message.substring(currentPos, elemPos), new SimpleAttributeSet());
                }

                // now calculate the attributes for the current element we're about to insert
                if( msgElem.getType() == SMILEY ) {
                    StyleConstants.setIcon(attr, getSmiley(msgElem.getTypeIndex()));
                } else if( freenetKeys && msgElem.getType() == FREENETKEY ) {
                    // NOTE: this style is only applied if freenetKeys==true. as a result, it ensures
                    // that we always *process* keys and refuse to render smileys inside them, even
                    // when hyperlinking is disabled. but that we don't *render* keys using this
                    // special "hyperlink" style unless the user has enabled hyperlinking.
                    attr.addAttribute(LinkEditorKit.LINK, elemStr);
                    attr.addAttribute(StyleConstants.Underline, Boolean.TRUE);
                    attr.addAttribute(StyleConstants.Foreground, Color.BLUE);
                }

                // insert the element (using its attributes) and advance the message position
                // to the next character after the element we just inserted
                doc.insertString(doc.getLength(), elemStr, attr);
                currentPos = elemPos + msgElem.getLength();
            }

            // insert any final text that existed after the last element, using regular unstyled attributes
            // NOTE: inserts an empty "" string if there's nothing after the element, but that doesn't matter
            doc.insertString(doc.getLength(), message.substring(currentPos), new SimpleAttributeSet());
        } catch (final BadLocationException e) {
            logger.log(Level.SEVERE, "Exception during construction of message", e);
        }

        // show the final, constructed document in the parent component
        parent.setDocument(doc);
    }

    /**
     * Set freenet's keys decoder acitve or not
     * @param value
     */
    public void setFreenetKeysDecode(final boolean value) {
        freenetKeys = value;
    }

    /**
     * Get status of freenet's keys decoder
     * @return true if active or false is not active
     */
    public boolean getFreenetKeysDecode() {
        return freenetKeys;
    }

    /**
     * Set smileys decoder acitve or not
     * @param value
     */
    public void setSmileyDecode(final boolean value) {
        smileys = value;
    }

    /**
     * Get status of smileys decoder
     * @return true if active or false is not active
     */
    public boolean getSmileyDecode() {
        return smileys;
    }

    // Find all keys in message
    private void processFreenetKeys(final String message, final TreeSet<MessageElement> targetElements, final TreeMap<Integer, String> fileKeys) {
        if( message == null || message.isEmpty() ) { return; }
        try { // don't die here for any reason (just a precaution even though there's nothing that can fail below)
            final int messageLength = message.length();
            for( int i = 0; i < FreenetKeys.FREENETKEYTYPES.length; ++i ) {
                int searchStartPos = 0;
                while( true ) {
                    // abort if we've gone beyond the end of the message
                    // NOTE: to clarify; pos is 0-indexed and length is 1-indexed,
                    // so an equal pos==length is beyond the end of the message
                    if( searchStartPos >= messageLength ) { break; }

                    // look for the next instance of this key type in the message
                    final int keyStartPos = message.indexOf(FreenetKeys.FREENETKEYTYPES[i], searchStartPos);
                    if( keyStartPos < 0 ) {
                        // no more keys of that type in the message; skip to next type
                        break;
                    }

                    // find the end of the current line (aka the max possible length of this key)
                    int lineEndPos = message.indexOf('\n', keyStartPos); // point at newline
                    if( lineEndPos < 0 ) {
                        // no EOL marker found on this line; that means we've reached the final line
                        // *and* that the final line didn't have a newline after it. so we then simply
                        // set the end pos to the final character of the message.
                        lineEndPos = messageLength - 1; // subtract 1 to convert length to 0-indexed
                    }

                    // determine where the key ends, by excluding all trailing whitespace (and newlines)
                    int keyEndPos = lineEndPos;
                    while( Character.isWhitespace(message.charAt(keyEndPos)) ) {
                        --keyEndPos;
                    }

                    // if the key is at least 1 character, proceed... NOTE: this is just a precaution;
                    // it will always be 4 characters ("CHK@") or more.
                    if( keyEndPos >= keyStartPos ) {
                        // extract the key from the message, so that we can validate it!
                        // NOTE: start and end are 0-indexed, and the endpos of substring() is
                        // non-inclusive, which is why we need to get "end+1".
                        final String thisKey = message.substring(keyStartPos, keyEndPos + 1);

                        // only include the key if it's valid for this network (Freenet)
                        // NOTE: this function only validates the crypto key length,
                        // and properly ignores everything after the slash.
                        if( FreenetKeys.isValidKey(thisKey) ) {
                            // add the discovered key to the list of elements to highlight
                            targetElements.add(new MessageElement(new Integer(keyStartPos), FREENETKEY, i, thisKey.length()));

                            // if this is a file link (non-freesite link), then add the key to the
                            // list of file links (used by features like "download all file keys")
                            // NOTE: this returns false for malformed Freesite keys (such as just
                            // a key with no slashes at all). but those will always be rejected by
                            // the key-parser during the "add downloads" stage, since they're invalid.
                            // we don't waste any time validating such rare/minor problems here,
                            // since it would slow down the message decoder.
                            if( ! FreenetKeys.isFreesiteKey(thisKey) ) {
                                fileKeys.put(keyStartPos, thisKey);
                            }
                        }
                    }

                    // set search start to the first character on the line after this key line,
                    // to prepare for the next iteration (which will then abort if it's "out of bounds")
                    searchStartPos = lineEndPos + 1;
                }
            }
        } catch( final Throwable e ) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Exception in processFreenetKeys", e);
        }
    }

    // Find all smileys in message
    private void processSmileys(final String message, final TreeSet<MessageElement> targetElements) {
        if( message == null || message.isEmpty() ) { return; }
        try { // don't die here for any reason (just a precaution even though there's nothing that can fail below)
            final int messageLength = message.length();
            // iterate over smiley types
            for( int i = 0; i < SMILEYS.length; ++i ) {
                // iterate over the various ways to write this particular smiley
                for( int j = 0; j < SMILEYS[i].length; ++j ) {
                    int searchStartPos = 0;
                    while( true ) {
                        // abort if we've gone beyond the end of the message
                        // NOTE: to clarify; pos is 0-indexed and length is 1-indexed,
                        // so an equal pos==length is beyond the end of the message
                        if( searchStartPos >= messageLength ) { break; }

                        // look for the next instance of this smiley code in the message
                        final int smileyStartPos = message.indexOf(SMILEYS[i][j], searchStartPos);
                        if( smileyStartPos < 0 ) {
                            // no more smiley codes of that type in the message; skip to next type
                            break;
                        }

                        // check if it's a valid smiley (must be surrounded by whitespace on both sides,
                        // unless it's at the start/end of the message which only needs one side)
                        if( isSmiley(smileyStartPos, message, SMILEYS[i][j]) ) {
                            // add the discovered smiley to the list of elements to substitute
                            targetElements.add(new MessageElement(new Integer(smileyStartPos), SMILEY, i, SMILEYS[i][j].length()));
                        }

                        // set search start to the first character after this smiley, to prepare
                        // for the next iteration (which will then abort if it's "out of bounds")
                        searchStartPos = smileyStartPos + SMILEYS[i][j].length();
                    }
                }
            }
        } catch( final Throwable e ) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "Exception in processSmileys", e);
        }
    }

    /**
     * A smiley is only recognized if there is whitespace before and after it (or it's at beginning/end of message)
     */
    private boolean isSmiley(final int pos, final String message, final String smiley) {
        // initialize the whitespace trackers to true if we're at the beginning/end of the message
        boolean wsLeft = (pos == 0);
        boolean wsRight = (message.length() == (smiley.length() + pos));
        // if we're not at the beginning/end of message, then check the adjacent character for whitespace
        char c;
        if( !wsLeft ) {
            c = message.charAt( pos - 1 );
            wsLeft = Character.isWhitespace(c);
        }
        if (!wsRight) {
            c = message.charAt( pos + smiley.length() );
            wsRight = Character.isWhitespace(c);
        }
        return (wsLeft && wsRight);
    }

    private Icon getSmiley(final int i) {
        return SmileyCache.getCachedSmiley(i);
    }

    public List<String> getHyperlinkedKeys(int fromPos) {
        return new LinkedList<String>(hyperlinkedFileKeys.tailMap(fromPos).values());
    }

    public List<String> getHyperlinkedKeys() {
        return new LinkedList<String>(hyperlinkedFileKeys.values());
    }
}
