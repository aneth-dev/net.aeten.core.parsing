package net.aeten.core.parsing.yaml;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.aeten.core.Format;
import net.aeten.core.event.Handler;
import net.aeten.core.logging.LogLevel;
import net.aeten.core.logging.Logger;
import net.aeten.core.parsing.*;
import net.aeten.core.spi.Provider;

/**
 *
 * @author Thomas Pérennou
 */
@Provider(Parser.class)
@Format("yaml")
public class YamlParser extends AbstractParser<MarkupNode> {

	@Override
	public void parse(Reader reader, Handler<ParsingData<MarkupNode>> handler) {
		BufferedReader bufferedReader = new BufferedReader(reader);
		String line, indentation = null;
		int currentLevel = 0, previousLevel = 0;
		Tag current = null;
		this.fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.LIST, null, null);
		try {
			while ((line = bufferedReader.readLine()) != null) {
				String trimed = line.trim();
				if ("".equals(trimed) || trimed.startsWith("#")) {
					continue;
				}

				previousLevel = currentLevel;
				currentLevel = 0;
				if ((indentation == null) && line.matches("^\\s.*")) {
					Pattern pattern = Pattern.compile("^\\s+");
					Matcher matcher = pattern.matcher(line);
					matcher.find();
					indentation = matcher.group();
				}
				if (indentation != null) {
					while (line.startsWith(indentation)) {
						currentLevel++;
						line = line.substring(indentation.length());
					}
				}

				line = trimed;

				String key;
				String value;
				int separatorIndex = line.indexOf(':');
				if (separatorIndex != -1) {
					key = line.substring(0, separatorIndex).trim();
					value = line.substring(separatorIndex + 1).trim();
				} else {
					key = null;
					value = line.substring(1).trim(); // List, starts with '-'
				}
				if (currentLevel > previousLevel) {
					this.fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.LIST, null, current);
					current = new Tag(current, key);
					this.fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
				} else if (currentLevel < previousLevel) {
					Tag parent = current;
					for (int i = previousLevel; i >= currentLevel; i--) {
						this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, parent.name, parent.parent);
						if (currentLevel != i) {
							this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, parent.parent);
						}
						parent = parent.parent;
					}
					current = new Tag((current == null) ? null : parent, key);
					this.fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
				} else {
					if (current != null) {
						this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
					}
					current = new Tag((current == null) ? null : current.parent, key);
					this.fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, current.name, current.parent);
				}
				if (!"".equals(value)) {
					MarkupNode node;
					switch (value.charAt(0)) {
						case '!':
							node = MarkupNode.TYPE;
							break;
						case '&':
							node = MarkupNode.REFERENCE;
							break;
						case '*':
							node = MarkupNode.ANCHOR;
							break;
						default:
							node = MarkupNode.TEXT;
							break;
					}
					switch (node) {
						case TYPE:
						case REFERENCE:
						case ANCHOR:
							Pattern pattern = Pattern.compile("[!&*](\\p{Graph}+)(\\p{Blank})*(.*)");
							Matcher matcher = pattern.matcher(value);
							if (!matcher.matches()) {
								System.out.println(value);
							}
							value = matcher.group(1);
							this.fireEvent(handler, ParsingEvent.START_NODE, node, value, current.parent);
							this.fireEvent(handler, ParsingEvent.END_NODE, node, value, current.parent);
							if (matcher.group(3).isEmpty()) {
								break;
							}
							value = matcher.group(3);
						default:
							node = MarkupNode.TEXT;
							break;
					}
					this.fireEvent(handler, ParsingEvent.START_NODE, node, value, current.parent);
					this.fireEvent(handler, ParsingEvent.END_NODE, node, value, current.parent);
				}
			}
		} catch (IOException exception) {
			Logger.log(this, LogLevel.ERROR, exception);
		}
		while (current != null) {
			this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
			current = current.parent;
			if (current != null) {
				this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, current);
			}

		}
		this.fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.LIST, null, null);
	}

	public static void main(String[] args) throws Exception {
		final Queue<String> currentTag = Collections.asLifoQueue(new ArrayDeque<String>());
		YamlParser parser = new YamlParser();
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
}
