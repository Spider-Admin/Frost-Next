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

import javax.swing.*;
import javax.swing.text.*;

/**
 * Message decoder for search freenet keys and smileys,
 * append message to JeditorPane document.
 * @author ET
 */
public class MessageDecoder extends Decoder implements FreenetKeys, Smileys, MessageTypes {

	public MessageDecoder() {
	}
	
	private boolean smileys = true;
	private boolean freenetKeys = true;
    private List hyperlinkedKeys = new LinkedList();

	/**
	 * {@inheritDoc}
	 */
	public void decode(String message, JEditorPane parent) {
		int begin = 0;
		TreeSet elements = new TreeSet();
        hyperlinkedKeys.clear();
		
		if (smileys) {
			// Verify EditorKit for correct render set EditorKit if not good
			if (!(parent.getEditorKit() instanceof StyledEditorKit)) {
				parent.setEditorKit(new StyledEditorKit());
			}
			processSmileys(message, elements);
		}
		if (freenetKeys) {
			// Verify EditorKit for correct render set EditorKit if not good
			// LinkEditorKit extends StyledEditorKit for render smileys
			if (!(parent.getEditorKit() instanceof LinkEditorKit)) {
				parent.setEditorKit(new LinkEditorKit());
			}
			processFreenetKeys(message, elements);
		}
        // FIXME: how to clear document without instanciating a new one?
        Document doc = new DefaultStyledDocument();
//        doc.remove(1,2);
        parent.setDocument(doc);
		try {
            Iterator it = elements.iterator();
			while(it.hasNext()) {
				MessageElement me = (MessageElement)it.next();
				String s = message.substring(me.getPosition().intValue(), me.getPosition().intValue() + me.getLength());
				SimpleAttributeSet at = new SimpleAttributeSet();

				// insert text before element
				doc.insertString(doc.getLength(),message.substring(begin, me.getPosition().intValue()), new SimpleAttributeSet());
				if(me.getType() == SMILEY) {
					StyleConstants.setIcon(at,getSmiley(me.getTypeIndex()));
				} else if (me.getType() == FREENETKEY) {
			        at.addAttribute(LinkEditorKit.LINK, s);
			        at.addAttribute(StyleConstants.Underline, Boolean.TRUE);
			        at.addAttribute(StyleConstants.Foreground, Color.BLUE);
				}

				// insert element
		        doc.insertString(doc.getLength(), s, at);
				begin = me.getPosition().intValue() + me.getLength();
			}

			// insert text after last element
			doc.insertString(doc.getLength(),message.substring(begin), new SimpleAttributeSet());
		} catch (BadLocationException e) {
			// TODO , ignore or throws
		}
	}

	/**
	 * Set freenet's keys decoder acitve or not
	 * @param value
	 */
	public void setFreenetKeysDecode(boolean value) {
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
	public void setSmileyDecode(boolean value) {
		smileys = value;
	}
	
	/**
	 * Get status of smileys decoder
	 * @return true if active or false is not active
	 */
	public boolean getSmileyDecode() {
		return smileys;
	}

    private void processFreenetKeys(String message, TreeSet elements) {
		for (int i = 0; i < FREENETKEYS.length; i++) {
			int offset = 0;
			String testMessage = new String(message);
			while(true) {
				int pos = testMessage.indexOf(FREENETKEYS[i]);
				if(pos > -1) {
                    int length = testMessage.indexOf("\n", pos);
                    if( length < 0 ) {
                        length = testMessage.length() - pos;
                    } else {
                        length -= pos;
                    }
                    // we add all file links (last char of link must not be a '/' or similar) to list of links;
                    // file links and freesite links will be hyperlinked
                    elements.add(new MessageElement(new Integer(pos + offset),FREENETKEY, i, length));
                    
                    if( Character.isLetterOrDigit(testMessage.charAt(pos+length-1)) ) {
                        // file link must contain at least one '/'
                        String aFileLink = testMessage.substring(pos, pos+length);
                        if( aFileLink.indexOf("/") > 0 ) {
                            hyperlinkedKeys.add(aFileLink);
                        }
                    }
					offset += pos + length;
					testMessage = testMessage.substring(pos + length); // FIXME: no substring, remember pos?!
				} else {
					break;
				}
			}
		}
	}

	private void processSmileys(String message, TreeSet elements) {
		// Find all smileys in message
		for (int i = 0; i < SMILEYS.length; i++) {
			for (int j = 0; j < SMILEYS[i].length; j++) {
				int offset = 0;
				String testMessage = new String(message);
				while(true) {
					int pos = testMessage.indexOf(SMILEYS[i][j]);
					if(pos > -1) {
						elements.add(new MessageElement(new Integer(pos + offset),SMILEY, i, SMILEYS[i][j].length()));
						offset += pos + SMILEYS[i][j].length();
						testMessage = testMessage.substring(pos + SMILEYS[i][j].length());
					} else {
						break;
					}
				}
			}
		}
	}

	private Icon getSmiley(int i) {
        return getCachedSmiley(i, getClass().getClassLoader());
	}

    protected static Hashtable smileyCache = new Hashtable();
    
    protected static synchronized ImageIcon getCachedSmiley(int i, ClassLoader cl) {
        String si = ""+i;
        ImageIcon ii = (ImageIcon)smileyCache.get(si);
        if( ii == null ) {
            // no classloader in static, we use classloader from smileyCache
            ii = new ImageIcon(cl.getResource("data/smileys/"+i+".gif"));
            smileyCache.put(si, ii);
        }
        return ii;
    }
    
    public List getHyperlinkedKeys() {
        return hyperlinkedKeys;
    }
}
