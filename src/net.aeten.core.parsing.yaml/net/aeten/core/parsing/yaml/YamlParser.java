package net.aeten.core.parsing.yaml;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.aeten.core.Format;
import net.aeten.core.Predicate;
import net.aeten.core.event.Handler;
import net.aeten.core.parsing.AbstractParser;
import net.aeten.core.parsing.AbstractParser.Tag;
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
public class YamlParser extends
		AbstractParser<MarkupNode> {
	@Override
	public void parse(Reader reader,
			Handler<ParsingData<MarkupNode>> handler)
			throws ParsingException {
		new YamlParserImpl (this, reader, handler).parse ();
	}
}

class YamlParserImpl extends
		AbstractParser.ParserImplementationHelper {
	private static final Pattern TYPE_OR_REF_OR_ANCHOR_PATTERN = Pattern.compile ("[!&*](\\p{Graph}+)(\\p{Blank})*([^#]*)(.*)");
	private static final Pattern INDENTATION_PATTERN = Pattern.compile ("^\\s+");

	String indentation = null;
	int currentLevel = -1, previousLevel = -1;
	Tag<MarkupNode> current = null;
	boolean documentOpened = false, previousValueRaised = false, previousTypeRaised = false;

	protected YamlParserImpl(Parser<MarkupNode> parser,
			Reader reader,
			Handler<ParsingData<MarkupNode>> handler) {
		super (parser, reader, handler, true);
	}

	int open = -1;
	boolean indented = false;
	boolean coma = false;

	protected void parse()
			throws ParsingException {
		super.parseText (new Predicate<EntryUnderConstruction> () {
			@Override
			public boolean evaluate(EntryUnderConstruction element) {
				boolean closure;
				boolean opening = false;
				int level;
				char last = element.getLastChar ();
				switch (open) {
				case '{':
				case '[':
					switch (last) {
					case '\n':
						element.removeLastChar ();
						return false;
					case ' ':
						if (!indented) {
							element.removeLastChar ();
							return false;
						}
					}
				}
				if (coma) {
					coma = false;
					level = open == '[' ? currentLevel - 1 : currentLevel;
				} else {
					level = currentLevel + 1;
				}
				switch (open) {
				case '{':
					coma = last == ',';
					closure = last == '}';
					break;
				case '[':
					coma = last == ',';
					closure = last == ']';
					break;
				case '"':
					closure = last == '"';
					break;
				default:
					closure = false;
					switch (last) {
					case '{':
						open = '{';
						indented = false;
						opening = true;
						break;
					case '[':
						open = '[';
						indented = false;
						opening = true;
						break;
					case '"':
						open = '"';
						opening = true;
						break;
					default:
						break;
					}
					break;
				}
				if (last == '\n' && (open == '{' || open == '[')) {
					element.removeLastChar ();
					return false;
				}
				if (closure) {
					open = -1;
					element.removeLastChar ();
					return true;
				} else if (opening) {
					element.removeLastChar ();
					return true;
				} else if (open != -1 && !indented) {
					if (open == '[') {
						element.input.insert (0, "- ");
					}
					for (int i = 0; i < level; i++) {
						element.input.insert (0, indentation);
					}
					indented = true;
				}
				if (coma) {
					element.removeLastChar ();
					indented = false;
					return true;
				}
				return END_OF_LINE.evaluate (element);
			}
		});
		closeDocument (handler, current, currentLevel);
	}

	protected void parse(String line)
			throws ParsingException {
		String trimed = line.trim ();
		if ("".equals (trimed) || trimed.startsWith ("#")) {
			return;
		}
		if (line.startsWith ("---")) {
			if (documentOpened) {
				closeDocument (handler, current, currentLevel);
			}
			fireEvent (ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null, null);
			trimed = trimed.substring (3).trim ();
			documentOpened = true;
		} else if (line.startsWith ("...")) {
			closeDocument (handler, current, currentLevel);
			trimed = trimed.substring (3);
			documentOpened = false;
			previousLevel = currentLevel;
			currentLevel = -1;
		} else {
			previousLevel = currentLevel;
			currentLevel = 0;
			if ((indentation == null) && line.matches ("^\\s.*")) {
				Matcher matcher = INDENTATION_PATTERN.matcher (line);
				matcher.find ();
				indentation = matcher.group ();
			}
			if (indentation != null) {
				while (line.startsWith (indentation)) {
					currentLevel++;
					line = line.substring (indentation.length ());
				}
			}
		}

		line = trimed;

		String key;
		String value;
		MarkupNode enclosingType;
		int separatorIndex = line.indexOf (':');
		if (separatorIndex != -1) {
			enclosingType = MarkupNode.MAP;
			key = line.substring (0, separatorIndex).trim ();
			value = line.substring (separatorIndex + 1).trim ();
		} else {
			key = null;
			if (line.charAt (0) != '-') {
				if (!line.startsWith ("#") && !TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher (line).matches ()) {
					throw new ParsingException ("", line, 0);
				}
				value = line;
				enclosingType = null;
			} else {
				enclosingType = MarkupNode.LIST;
				value = line.substring (1).trim (); // List, starts with '-'
				Matcher matcher = TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher (value);
				if (!matcher.matches () || !matcher.group (3).trim ().isEmpty ()) {
					if (currentLevel < previousLevel) {
						current = close (handler, current, previousLevel, currentLevel);
						previousLevel = currentLevel;
						enclosingType = null;
					}
					currentLevel++;
				}
			}
		}
		if (!documentOpened) {
			fireEvent (ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null, null);
			documentOpened = true;
		}

		if (currentLevel > previousLevel) {
			if (enclosingType != null) {
				if (current == null) {
					current = new Tag<MarkupNode> (null, null);
				}
				if (current.childrenNodeType == null) {
					current.childrenNodeType = enclosingType;
				} else if (current.childrenNodeType != enclosingType) {
					error ("Find " + enclosingType + " element when " + current.childrenNodeType + " was expected");
				}
				if (current.childrenType == null) {
					current.childrenType = (current.childrenNodeType == MarkupNode.MAP ? Map.class : List.class).getName ();
					type (current.childrenType, current.parent);
				}
				fireEvent (ParsingEvent.START_NODE, current.childrenNodeType, null, current);
			}
			current = openTag (key, current);

		} else if (currentLevel < previousLevel) {
			if (!previousValueRaised) {
				if (!previousTypeRaised) {
					type (Void.class.getName (), current.parent);
				}
				text ("", current.parent);
			}
			current = openTag (key, close (handler, current, previousLevel, currentLevel));
		} else {
			if (!previousValueRaised && current != null) {
				if (!previousTypeRaised) {
					type (Void.class.getName (), current.parent);
				}
				text ("", current.parent);
			}
			current = openTag (key, closeTag (current));
		}

		previousValueRaised = previousTypeRaised = false;

		if (value.startsWith ("#")) {
			return;
		}
		if (!"".equals (value)) {
			MarkupNode node;
			switch (value.charAt (0)) {
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
			Matcher matcher = TYPE_OR_REF_OR_ANCHOR_PATTERN.matcher (value);
			switch (node) {
			case TYPE:
			case REFERENCE:
			case ANCHOR:
				if (!matcher.matches ()) {
					throw new ParsingException ("Node " + node + " error", value, 1);
				}
				value = matcher.group (1);
				if (node == MarkupNode.TYPE) {
					switch (value) {
					case "!str":
						value = String.class.getName ();
						break;
					case "!bool":
						value = boolean.class.getName ();
						break;
					case "!int":
						value = int.class.getName ();
						break;
					case "!float":
						value = float.class.getName ();
						break;
					case "!seq":
						value = List.class.getName ();
						break;
					case "!set":
						value = Set.class.getName ();
						break;
					case "!oset":
						value = LinkedHashSet.class.getName ();
						break;
					case "!map":
						value = Map.class.getName ();
						break;
					case "!omap":
						value = LinkedHashMap.class.getName ();
						break;
					case "!binary":
						value = byte[].class.getName ();
						break;
					default:
						current.childrenType = value;
						break;
					}
					previousTypeRaised = true;
				} else {
					if (!matcher.group (3).isEmpty ()) {
						autoType (handler, current, matcher.group (3), null);
					}
				}
				fireEvent (ParsingEvent.START_NODE, node, value, current.parent);
				fireEvent (ParsingEvent.END_NODE, node, value, current.parent);
				if (matcher.group (3).isEmpty ()) {
					return;
				}
				value = matcher.group (3);
				break;
			default:
				autoType (handler, current, value, String.class.getName ());
				break;
			}
			if (value.startsWith ("#")) {
				return;
			}
			text (value, current.parent);
			previousValueRaised = true;
		}
	}

	private Tag<MarkupNode> openTag(String name,
			Tag<MarkupNode> parent) {
		Tag<MarkupNode> tag = new Tag<MarkupNode> (parent, name);
		if (name != null) {
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TAG, null, tag.parent);
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TYPE, String.class.getName (), tag);
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TYPE, String.class.getName (), tag);
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TEXT, name, tag);
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TEXT, name, tag);
		}
		return tag;
	}

	private Tag<MarkupNode> closeTag(Tag<MarkupNode> tag) {
		if (tag != null && tag.name != null) {
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TAG, null, tag.parent);
		}
		return tag == null ? null : tag.parent;
	}

	private void autoType(Handler<ParsingData<MarkupNode>> handler,
			Tag<MarkupNode> current,
			String value,
			String defaultType) {
		final String type;
		switch (value) {
		case "true":
		case "false":
		case "True":
		case "False":
		case "TRUE":
		case "FALSE":
			type = boolean.class.getName ();
			break;
		case "":
			type = Void.class.getName ();
			break;
		default:
			type = defaultType;
		}
		if (type != null) {
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TYPE, type, current.parent);
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TYPE, type, current.parent);
		}
	}

	private Tag<MarkupNode> close(Handler<ParsingData<MarkupNode>> handler,
			Tag<MarkupNode> current,
			int currentLevel,
			int newLevel) {
		if (current.name == null && current.parent != null && current.parent.childrenNodeType == MarkupNode.LIST) {
			currentLevel--;
		}
		for (int i = currentLevel; i >= newLevel; i--) {
			if (current.name != null) {
				fireEvent (ParsingEvent.END_NODE, MarkupNode.TAG, current.name, current.parent);
			}
			current = current.parent;
			if (current == null) {
				break;
			}
			if (newLevel != i) {
				fireEvent (ParsingEvent.END_NODE, current.childrenNodeType, null, current);
			}
		}
		return current;
	}

	private void closeDocument(Handler<ParsingData<MarkupNode>> handler,
			Tag<MarkupNode> current,
			int currentLevel) {
		close (handler, current, currentLevel, -1);

		fireEvent (ParsingEvent.END_NODE, MarkupNode.DOCUMENT, null, null);
	}
}