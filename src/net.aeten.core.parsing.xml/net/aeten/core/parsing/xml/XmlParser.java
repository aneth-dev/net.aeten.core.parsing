package net.aeten.core.parsing.xml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import javax.xml.parsers.ParserConfigurationException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.aeten.core.Format;
import net.aeten.core.event.Handler;
import net.aeten.core.parsing.MarkupNode;
import net.aeten.core.parsing.Parser;
import net.aeten.core.parsing.ParsingData;
import net.aeten.core.parsing.ParsingEvent;
import net.aeten.core.parsing.ParsingException;
import net.aeten.core.spi.Provider;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * 
 * @author Thomas Pérennou
 */
@Provider(Parser.class)
@Format("xml")
public class XmlParser implements Parser<MarkupNode> {
	private class Tag {
		protected final Tag parent;
		protected final String name;

		public Tag(Tag parent, String name) {
			this.parent = parent;
			this.name = name;
		}
	}

	@Override
	public void parse(Reader reader, final Handler<ParsingData<MarkupNode>> handler) throws ParsingException {
		try {
			SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

			parser.parse(new InputSource(reader), new DefaultHandler() {
				Tag currentTag = null;

				@Override
				public void startDocument() throws SAXException {
					fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null, null);
				}

				@Override
				public void endDocument() throws SAXException {
					fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.DOCUMENT, null, null);
				}

				@Override
				public void startElement(String uri, String localName, String name, Attributes attributes) throws SAXException {
					currentTag = new Tag(currentTag, name);
					fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, name, currentTag.parent);
					if (attributes.getLength() > 0) {
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.MAP, null, currentTag);
					}
					for (int i = 0; i < attributes.getLength(); i++) {
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, attributes.getQName(i), currentTag);
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, attributes.getValue(i), currentTag);
						fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, attributes.getValue(i), currentTag);
						fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, attributes.getQName(i), currentTag);
					}
					if (attributes.getLength() > 0) {
						fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.MAP, null, currentTag);
					}
					fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.LIST, null, currentTag);
				}

				@Override
				public void endElement(String uri, String localName, String name) throws SAXException {
					fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, currentTag);
					fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, name, currentTag.parent);
					currentTag = currentTag.parent;
				}

				@Override
				public void characters(char[] ch, int start, int length) throws SAXException {
					String text = new String(ch, start, length);
					if (text.trim().length() == 0)
						return;
					fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, text, currentTag);
					fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, text, currentTag);
				}
			});
		} catch (ParserConfigurationException | SAXException | IOException exception) {
			throw new ParsingException(exception);
		}
	}

	public static void main(String[] args) throws Exception {
		final Queue<String> currentTag = Collections.asLifoQueue(new ArrayDeque<String>());
		XmlParser parser = new XmlParser();
		parser.parse(new BufferedReader(new FileReader(args[0])), new Handler<ParsingData<MarkupNode>>() {
			private int level = 0;

			@Override
			public void handleEvent(ParsingData<MarkupNode> data) {

				switch (data.getEvent()) {
				case START_NODE:
					switch (data.getNodeType()) {
					case TEXT:
						System.out.print(" \"" + data.getValue());
						break;
					case ANCHOR:
						break;
					case REFERENCE:
						break;
					case TYPE:
						break;
					case MAP:
						System.out.println();
						this.println("{");
						break;
					case LIST:
						System.out.println();
						this.println("[");
						break;
					case TAG:
						level++;
						this.print("« " + data.getValue() + ":");
						currentTag.add(data.getValue());
						break;
					case COMMENT:
						break;
					case DOCUMENT:
						break;
					default:
						break;
					}
					break;
				case END_NODE:
					switch (data.getNodeType()) {
					case TEXT:
						System.out.println("\"");
						break;
					case ANCHOR:
						break;
					case REFERENCE:
						break;
					case TYPE:
						break;
					case MAP:
						this.println("}");
						break;
					case LIST:
						this.println("]");
						break;
					case TAG:
						this.println(currentTag.poll() + " »");
						level--;
						break;
					case COMMENT:
						break;
					case DOCUMENT:
						break;
					default:
						break;
					}
					break;
				}
			}

			private void print(String text) {
				for (int i = 0; i < level; i++) {
					System.out.print('\t');
				}
				System.out.print(text);
			}

			private void println(String text) {
				this.print(text + '\n');
			}
		});
	}

	@Override
	public String getIdentifier() {
		return XmlParser.class.getName();
	}

	private void fireEvent(Handler<ParsingData<MarkupNode>> handler, ParsingEvent event, MarkupNode nodeType, String value, Tag parent) {
		handler.handleEvent(new ParsingData<MarkupNode>(this, event, nodeType, value, (parent == null) ? null : parent.name));
	}
}
