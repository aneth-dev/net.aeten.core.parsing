package net.aeten.core.parsing.yaml;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.aeten.core.Format;
import net.aeten.core.event.Handler;
import net.aeten.core.logging.LogLevel;
import net.aeten.core.logging.Logger;
import net.aeten.core.parsing.AbstractParser;
import net.aeten.core.parsing.MarkupNode;
import net.aeten.core.parsing.Parser;
import net.aeten.core.parsing.ParsingData;
import net.aeten.core.parsing.ParsingEvent;
import net.aeten.core.parsing.ParsingException;
import net.aeten.core.spi.Provider;

/**
 *
 * @author Thomas Pérennou
 */
@Provider(Parser.class)
@Format("yaml")
public class YamlParser extends AbstractParser<MarkupNode> {
	private static final Pattern TYPE_OR_REF_OR_ANCHOR_PATTERN = Pattern.compile("[!&*](\\p{Graph}+)(\\p{Blank})*([^#]*)(.*)");
	private static final Pattern INDENTATION_PATTERN = Pattern.compile("^\\s+");

	@Override
	public void parse(Reader reader, Handler<ParsingData<MarkupNode>> handler) throws ParsingException {
		BufferedReader bufferedReader = new BufferedReader(reader);
		String line, indentation = null;
		int currentLevel = -1, previousLevel = -1;
		Tag current = null;
		boolean documentOpened = false, previousValueRaised = false, previousTypeRaised = false;
		try {
			LINE: while ((line = bufferedReader.readLine()) != null) {
				String trimed = line.trim();
				if ("".equals(trimed) || line.startsWith("#")) {
					continue;
				}
				try {

					if (line.startsWith("---")) {
						if (documentOpened) {
							closeDocument(handler, current, currentLevel);
						}
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null, null);
						trimed = trimed.substring(3).trim();
						documentOpened = true;
					} else if (line.startsWith("...")) {
						closeDocument(handler, current, currentLevel);
						trimed = trimed.substring(3);
						documentOpened = false;
						previousLevel = currentLevel;
						currentLevel = -1;
					} else {
						previousLevel = currentLevel;
						currentLevel = 0;
						if ((indentation == null) && line.matches("^\\s.*")) {
							Matcher matcher = INDENTATION_PATTERN.matcher(line);
							matcher.find();
							indentation = matcher.group();
						}
						if (indentation != null) {
							while (line.startsWith(indentation)) {
								currentLevel++;
								line = line.substring(indentation.length());
							}
						}
					}

					line = trimed;

					String key;
					String value;
					MarkupNode enclosingType;
					int separatorIndex = line.indexOf(':');
					if (separatorIndex != -1) {
						enclosingType = MarkupNode.MAP;
						key = line.substring(0, separatorIndex).trim();
						value = line.substring(separatorIndex + 1).trim();
					} else {
						key = null;
						if (line.charAt(0) != '-') {
							if (!line.startsWith("#") && !TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher(line).matches()) throw new ParsingException("", line, 0);
							value = line;
							enclosingType = null;
						} else {
							enclosingType = MarkupNode.LIST;
							value = line.substring(1).trim(); // List, starts with '-'
							Matcher matcher = TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher(value);
							if (!matcher.matches() || !matcher.group(3).trim().isEmpty()) {
								if (currentLevel < previousLevel) {
									current = close(handler, current, previousLevel, currentLevel);
									previousLevel = currentLevel;
									enclosingType = null;
								}
								currentLevel++;
							}
						}
					}
					if (!documentOpened) {
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null, null);
						documentOpened = true;
					}

					if (currentLevel > previousLevel) {
						if (enclosingType != null) {
							if (current == null) {
								current = new Tag(null, null);
							}
							if (current.childrenNodeType == null) {
								current.childrenNodeType = enclosingType;
							} else if (current.childrenNodeType != enclosingType) { throw new ParsingException("Find " + enclosingType + " element when " + current.childrenNodeType + " was expected", line, 0); }
							if (current.childrenType == null) {
								current.childrenType = (current.childrenNodeType == MarkupNode.MAP ? Map.class : List.class).getName();
								fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, current.childrenType, current.parent);
								fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, current.childrenType, current.parent);
							}
							fireEvent(handler, ParsingEvent.START_NODE, current.childrenNodeType, null, current);
						}
						current = new Tag(current, key);
						if (key != null) {
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, null, current.parent);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, key, current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, key, current);
						}
					} else if (currentLevel < previousLevel) {
						if (!previousValueRaised) {
							if (!previousTypeRaised) {
								fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, Void.class.getName(), current.parent);
								fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, Void.class.getName(), current.parent);
							}
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, "", current.parent);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, "", current.parent);
						}
						Tag parent = close(handler, current, previousLevel, currentLevel);
						current = new Tag((current == null) ? null : parent, key);
						if (current.name != null) {
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, null, current.parent);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, key, current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, key, current);
						}
					} else {
						if (!previousValueRaised && current != null) {
							if (!previousTypeRaised) {
								fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, Void.class.getName(), current.parent);
								fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, Void.class.getName(), current.parent);
							}
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, "", current.parent);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, "", current.parent);
						}
						if (current != null && current.name != null) {
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, null, current.parent);
						}
						current = new Tag((current == null) ? null : current.parent, key);
						if (current.name != null) {
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TAG, null, current.parent);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, String.class.getName(), current);
							fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, key, current);
							fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, key, current);
						}
					}

					previousValueRaised = previousTypeRaised = false;

					if (value.startsWith("#")) {
						continue;
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
						Matcher matcher = TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher(value);
						switch (node) {
						case TYPE:
						case REFERENCE:
						case ANCHOR:
							if (!matcher.matches()) { throw new ParsingException("Node " + node + " error", value, 1); }
							value = matcher.group(1);
							if (node == MarkupNode.TYPE) {
								switch (value) {
								case "!str":
									value = String.class.getName();
									break;
								case "!bool":
									value = boolean.class.getName();
									break;
								case "!int":
									value = int.class.getName();
									break;
								case "!float":
									value = float.class.getName();
									break;
								case "!seq":
									value = List.class.getName();
									break;
								case "!set":
									value = Set.class.getName();
									break;
								case "!oset":
									value = LinkedHashSet.class.getName();
									break;
								case "!map":
									value = Map.class.getName();
									break;
								case "!omap":
									value = LinkedHashMap.class.getName();
									break;
								case "!binary":
									value = byte[].class.getName();
									break;
								default:
									current.childrenType = value;
									break;
								}
								previousTypeRaised = true;
							} else {
								if (!matcher.group(3).isEmpty()) {
									autoType(handler, current, matcher.group(3), null);
								}
							}
							fireEvent(handler, ParsingEvent.START_NODE, node, value, current.parent);
							fireEvent(handler, ParsingEvent.END_NODE, node, value, current.parent);
							if (matcher.group(3).isEmpty()) {
								continue LINE;
							}
							value = matcher.group(3);
							break;
						default:
							autoType(handler, current, value, String.class.getName());
							break;
						}
						if (value.startsWith("#")) {
							continue;
						}
						fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TEXT, value, current.parent);
						fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TEXT, value, current.parent);
						previousValueRaised = true;
					}
				} catch (Throwable exception) {
					throw new ParsingException("Error", line, 0, exception);
				}
			}
		} catch (IOException exception) {
			Logger.log(this, LogLevel.ERROR, exception);
		}
		closeDocument(handler, current, currentLevel);
	}

	private void autoType(Handler<ParsingData<MarkupNode>> handler, Tag current, String value, String defaultType) {
		final String type;
		switch (value) {
		case "true":
		case "false":
		case "True":
		case "False":
		case "TRUE":
		case "FALSE":
			type = boolean.class.getName();
			break;
		case "":
			type = Void.class.getName();
			break;
		default:
			type = defaultType;
		}
		if (type != null) {
			fireEvent(handler, ParsingEvent.START_NODE, MarkupNode.TYPE, type, current.parent);
			fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TYPE, type, current.parent);
		}
	}

	private Tag close(Handler<ParsingData<MarkupNode>> handler, Tag current, int currentLevel, int newLevel) {
		if (current.name == null && current.parent != null && current.parent.childrenNodeType == MarkupNode.LIST) {
			currentLevel--;
		}
		for (int i = currentLevel; i >= newLevel; i--) {
			if (current.name != null) {
				fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
			}
			current = current.parent;
			if (current == null) {
				break;
			}
			if (newLevel != i) {
				fireEvent(handler, ParsingEvent.END_NODE, current.childrenNodeType, null, current);
			}
		}
		return current;
	}

	private void closeDocument(Handler<ParsingData<MarkupNode>> handler, Tag current, int currentLevel) {
		close(handler, current, currentLevel, -1);
		fireEvent(handler, ParsingEvent.END_NODE, MarkupNode.DOCUMENT, null, null);
	}
}
