/*
 AttachmentList.java / Frost
 Copyright (C) 2003  Frost Project <jtcfrost.sourceforge.net>

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

import java.util.*;
import java.util.logging.*;

import org.w3c.dom.*;
import org.xml.sax.*;

import frost.messaging.frost.boards.Board;
import frost.util.*;

@SuppressWarnings("serial")
public class AttachmentList<T extends Attachment> extends LinkedList<T> implements XMLizable {

	private static final Logger logger = Logger.getLogger(AttachmentList.class.getName());

	/**
	 * traverses the list for attachment of given type
	 * @param type the type - BOARD, FILE, PERSON...
	 * @return another list which contains only attachments of the specified type
	 */
	public AttachmentList<T> getAllOfType(final int type) {
		assert type == Attachment.FILE ||
					type == Attachment.BOARD ||
					type == Attachment.PERSON :
					"list of unknown type of attachments requested";
		
		final Iterator<T> i = iterator();
		final AttachmentList<T> result = new AttachmentList<T>();
		while (i.hasNext()) {
			final T current = i.next();
			if (current.getType() == type) {
				switch(type) {
					case Attachment.FILE :
						assert current instanceof FileAttachment;
						break;
					case Attachment.BOARD :
						assert current instanceof BoardAttachment;
						break;
					case Attachment.PERSON :
						assert current instanceof PersonAttachment;
						break;
				}
				result.add(current);
			}
		}

		return result;
	}
	
	@SuppressWarnings("unchecked")
	public AttachmentList<FileAttachment> getAllOfTypeFile() {
		return (AttachmentList<FileAttachment>) getAllOfType(Attachment.FILE);
	}
	
	@SuppressWarnings("unchecked")
	public AttachmentList<BoardAttachment> getAllOfTypeBoard() {
		return (AttachmentList<BoardAttachment>) getAllOfType(Attachment.BOARD);
	}
	
	@SuppressWarnings("unchecked")
	public AttachmentList<PersonAttachment> getAllOfTypePerson() {
		return (AttachmentList<PersonAttachment>) getAllOfType(Attachment.PERSON);
	}

	@Override
	synchronized public Element getXMLElement(final Document d){
        if( size() == 0 ) {
            return null;
        }
		final Element el = d.createElement("AttachmentList");
		final Iterator<T> i = iterator();
		while (i.hasNext()) {
			final Attachment current = i.next();
			el.appendChild(current.getXMLElement(d));
		}
		return el;
	}

	@SuppressWarnings("unchecked")
	public void loadXMLElement(final Element el) throws SAXException {
		if( el == null ) { return; }
		final Iterator<Element> i = XMLTools.getChildElementsByTagName(el,"Attachment").iterator();
		while( i.hasNext() ) {
			final Element current = i.next();
			// If this cast fails, someone has put an item of the wrong type into
			// the list, specialized through generics.
			final T attachment = (T) T.getInstance(current);

			// when we receive new incoming messages, we will ignore the *dead* legacy Frost announce
			// board, as well as any "no subject" related board attachments, since the latter are only
			// spammed as message attachments by losers with too much time on their hands.
			// this ensures that neither of those boards show up as attachments (or color the msg blue either).
			if( attachment.getType() == Attachment.BOARD ) {
				final Board b = ((BoardAttachment)attachment).getBoardObj();
				final String boardName = b.getName();
				if( boardName != null ) {
					if( boardName.toLowerCase().equals("frost-announce") || boardName.toLowerCase().contains("no subject") ) {
						continue;
					}
				}
			}

			// the attachment is ok; just add it
			add(attachment);
		}
		if( size() == 0 ) {
			logger.info("empty attachment list upon creation");
		}
	}
}
