package net.aeten.core.parsing.properties;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;

import net.aeten.core.Format;
import net.aeten.core.event.Handler;
import net.aeten.core.logging.LogLevel;
import net.aeten.core.logging.Logger;
import net.aeten.core.parsing.MarkupNode;
import net.aeten.core.parsing.Parser;
import net.aeten.core.parsing.ParsingData;
import net.aeten.core.parsing.ParsingEvent;
import net.aeten.core.spi.Provider;

/**
 * 
 * @author Thomas Pérennou
 */
@Provider(Parser.class)
@Format("properties")
public class PropertiesParser implements Parser<MarkupNode> {
	private class Tag {
		protected final Tag parent;
		protected final String name;

		public Tag(Tag parent, String name) {
			this.parent = parent;
			this.name = name;
		}
	}

	public void parse(Reader reader, Handler<ParsingData<MarkupNode>> handler) {
		Properties properties = new Properties();
		Tag current = null;
		try {
			properties.load(reader);
			List<String> keys = new LinkedList<String>(properties.stringPropertyNames());
			Collections.sort(keys);

			int currentLevel = 0, previousLevel = 0;
			fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.LIST, null, null);
			String[] previousPath, path = new String[0];
			for (String keyPath : keys) {
				previousPath = path;
				path = keyPath.split("\\.");

				int begin = -1;
				for (int i = 0; i < path.length && i < previousPath.length; i++) {
					if (path.length + 1 < i) {
						break;
					}
					if (path[i].equals(previousPath[i])) {
						begin = i;
					} else
						break;
				}
				begin++;
				for (int keyIndex = begin; keyIndex < path.length; previousLevel = keyIndex++) {
					currentLevel = keyIndex;
					if ((path.length > currentLevel + 1) && path[currentLevel + 1].matches("^\\d$")) {
						continue;
					}
					String key = path[path[keyIndex].matches("^\\d$") ? keyIndex - 1 : keyIndex];
					String value;
					if (currentLevel == path.length - 1) {
						value = properties.getProperty(keyPath);
					} else {
						value = "";
					}
					if (path[keyIndex].matches("^\\d$")) {
						currentLevel--;
					}
					try {
						if (currentLevel > previousLevel) {
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.LIST, null, current);
							current = new Tag(current, key);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
						} else if (currentLevel < previousLevel) {
							Tag parent = current;
							for (int i = previousLevel; i >= keyIndex; i--) {
								fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, parent.name, parent.parent);
								if (keyIndex != i) {
									fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, parent.parent);
								}
								parent = parent.parent;
							}
							current = new Tag((current == null) ? null : parent, key);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
						} else {
							if (current != null) {
								fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
							}
							current = new Tag((current == null) ? null : current.parent, key);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
						}
						if (!"".equals(value)) {
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, value, current.parent);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, value, current.parent);
						}
					} catch (Throwable error) {
						Logger.log(this, LogLevel.ERROR, error);
					}

				}
			}
		} catch (IOException exception) {
			Logger.log(this, LogLevel.ERROR, exception);
		}
		while (current != null) {
			fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
			current = current.parent;
			if (current != null) {
				fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, current);
			}

		}
		fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, null);
	}

	public static void main(String[] args) throws Exception {
		final Queue<String> currentTag = Collections.asLifoQueue(new ArrayDeque<String>());
		PropertiesParser parser = new PropertiesParser();
		parser.parse(new BufferedReader(new FileReader(args[0])), new Handler<ParsingData<MarkupNode>>() {
			private int level = 0;

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
						println("{");
						break;
					case LIST:
						System.out.println();
						println("[");
						break;
					case TAG:
						level++;
						print("{ " + data.getValue() + ":");
						currentTag.add(data.getValue());
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
						println("}");
						break;
					case LIST:
						println("]");
						break;
					case TAG:
						println(currentTag.poll() + " }");
						level--;
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
				print(text + '\n');
			}
		});
	}

	public String getIdentifier() {
		return PropertiesParser.class.getName();
	}

	private void fireEvent(Handler<ParsingData<MarkupNode>> handler, ParsingEvent event, MarkupNode nodeType, String value, Tag parent) {
		handler.handleEvent(new ParsingData<MarkupNode>(this, event, nodeType, value, (parent == null) ? null : parent.name));
	}

}
