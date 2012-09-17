package net.aeten.core.parsing.yaml;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;

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

enum Indicator {
	SEQUENCE_ENTRY('-'),
	MAPPING_KEY('?'),
	MAPPING_VALUE(':'),
	COLLECT_ENTRY(','),
	SEQUENCE_START('['),
	SEQUENCE_END(']'),
	MAPPING_START('{'),
	MAPPING_END('}'),
	COMMENT('#'),
	ANCHOR('&'),
	ALIAS('*'),
	TYPE('!'),
	LITERAL('|'),
	FOLDED('>'),
	SINGLE_QUOTE('\''),
	DOUBLE_QUOTE('"'),
	DIRECTIVE('%'),
	NONE,
	RESERVED('@',
			'`');

	private final char[] indicators;

	Indicator(char... indicators) {
		this.indicators = indicators;
	}

	boolean has(char indicator) {
		for (char c : indicators) {
			if (c == indicator) {
				return true;
			}
		}
		return false;
	}

	static boolean isIndicator(char candidate) {
		return get (candidate) != NONE;
	}

	char[] get() {
		return Arrays.copyOf (indicators, indicators.length);
	}

	static Indicator get(char candidate) {
		for (Indicator indicator : Indicator.values ()) {
			if (indicator.has (candidate)) {
				return indicator;
			}
		}
		return NONE;
	}
}

class Next {
	private final Indicator expected;
	private final boolean optional;

	Next(Indicator expected,
			boolean optional) {
		this.expected = expected;
		this.optional = optional;
	}

	boolean isAny() {
		return expected == null;
	}

	Indicator getExpected() {
		return expected;
	}

	boolean isOptional() {
		return optional;
	}
}

class YamlParserImpl extends
		AbstractParser.ParserImplementationHelper {

	String indentation = null;
	int currentLevel = -1, previousLevel = -1;
	private final Queue<MarkupNode> level = Collections.asLifoQueue (new ArrayDeque<MarkupNode> ()); // LIFO Queue<MAP|LIST>
	private final Queue<Tag<MarkupNode>> path = Collections.asLifoQueue (new ArrayDeque<Tag<MarkupNode>> ());
	private final Queue<MarkupNode> inlineStack = Collections.asLifoQueue (new ArrayDeque<MarkupNode> ()); // Explicit sequence ({} or [])
	private String[] type = {};
	Tag<MarkupNode> current = null;

	boolean documentOpened = false;
	boolean raiseType = false;
	boolean sequenceEntry = false;

	boolean previousTypeRaised, previousValueRaised; // TODO: remove

	protected YamlParserImpl(Parser<MarkupNode> parser,
			Reader reader,
			Handler<ParsingData<MarkupNode>> handler) {
		super (parser, reader, handler, true);
	}

	protected void parse()
			throws ParsingException {
		super.parseText (new InitialPredicate ());
		closeDocument (handler, current, currentLevel);
	}

	protected void parse(String entry)
			throws ParsingException {
//		System.out.println (entry);
//		if (true) {
//			return;
//		}
//		System.out.println (">>>>>>>>>>>>>>>> " + entry);
		String trimed = entry.trim ();
		if (trimed.isEmpty ()) {
			return;
		}
		Indicator indicator = Indicator.get (trimed.charAt (0));
		if (inlineStack.isEmpty () && currentLevel == -1) {
			switch (indicator) {
			case NONE:
			case SEQUENCE_ENTRY:
				if ("---".equals (trimed)) {
					break;
				}
			case MAPPING_KEY:
				level (entry);
				if (currentLevel < previousLevel) {
					level.poll ();
					close (handler, current, previousLevel, currentLevel);
				} else if (currentLevel > previousLevel) {
					final MarkupNode node;
					switch (indicator) {
					case NONE:
						node = trimed.charAt (trimed.length () - 1) == ':' ? MarkupNode.MAP : MarkupNode.LIST;
						break;
					case SEQUENCE_ENTRY:
						node = MarkupNode.LIST;
						break;
					case MAPPING_KEY:
						node = MarkupNode.MAP;
						break;
					default:
						node = null;
						break;
					}
					level.add (node);
					closeTag (current);
					fireEvent (ParsingEvent.START_NODE, node, null);
				} else {
					closeTag (current);
				}
				previousLevel = currentLevel;
				currentLevel = -1;
				break;
			default:
				break;
			}
		}
		switch (indicator) {
		case SEQUENCE_ENTRY:
			if (entry.startsWith ("---")) {
				if (documentOpened) {
					closeDocument (handler, current, currentLevel);
				}
				current = new Tag<MarkupNode> (null, null);
				current.childrenNodeType = MarkupNode.LIST;
				fireEvent (ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null);
				documentOpened = true;
				raiseType = true;
				break;
			} else {
				sequenceEntry = true;
			}
		case MAPPING_START:
		case SEQUENCE_START:
		case MAPPING_END:
		case SEQUENCE_END:
			parse (indicator, null);
			break;
		case MAPPING_KEY:
		case MAPPING_VALUE:
		case COMMENT:
			parse (indicator, trimed.substring (1).trim ());
			break;
		case DIRECTIVE:
		case ALIAS:
		case ANCHOR:
		case TYPE:
			parse (indicator, trimed.substring (1));
			break;
		case DOUBLE_QUOTE:
		case SINGLE_QUOTE:
			parse (indicator, trimed.substring (1, trimed.length () - 1));
			break;
		case FOLDED:
			// TODO
			break;
		case LITERAL:
			parse (indicator, entry.substring (1));
			break;
		case NONE:
			if (entry.startsWith ("...")) {
				closeDocument (handler, current, currentLevel);
				documentOpened = false;
				previousLevel = currentLevel;
				currentLevel = -1;
			} else if (trimed.charAt (trimed.length () - 1) == ':') {
				parse (Indicator.MAPPING_KEY, trimed.substring (0, trimed.length ()).trim ());
			} else {
				parse (Indicator.DOUBLE_QUOTE, trimed);
			}
			break;
		case RESERVED:
			error ("Reserved indicator");
			break;
		case COLLECT_ENTRY: // Not raised
			break;
		default:
			break;
		}
		/*
					if (currentLevel > previousLevel) {
						if (sequenceStack.peek () != null) {
							if (current == null) {
								current = new Tag<MarkupNode> (null, null);
							}
							if (current.childrenNodeType == null) {
								current.childrenNodeType = sequenceStack.peek ();
							} else if (current.childrenNodeType != sequenceStack.peek ()) {
								error ("Find " + sequenceStack.peek () + " element when " + current.childrenNodeType + " was expected");
							}
							if (current.childrenType == null) {
								current.childrenType = (current.childrenNodeType == MarkupNode.MAP ? Map.class : List.class).getName ();
								type (current.childrenType, current.parent);
							}
							fireEvent (ParsingEvent.START_NODE, current.childrenNodeType, null, current);
						} else {
							if (current.childrenNodeType == null) {
								current.childrenNodeType = MarkupNode.MAP;
							}
							openSequence (current.childrenNodeType, false);
						}
						current = openTag (trimed, current);

					} else if (currentLevel < previousLevel) {
		//				if (!previousValueRaised) {
		//					if (!previousTypeRaised) {
		//						type (Void.class.getName (), current.parent);
		//					}
		//					text ("", current.parent);
		//				}
						current = openTag (trimed, close (handler, current, previousLevel, currentLevel));
					} else {
		//				if (!previousValueRaised && current != null) {
		//					if (!previousTypeRaised) {
		//						type (Void.class.getName (), current.parent);
		//					}
		//					text ("", current.parent);
		//				}
						current = openTag (trimed, closeTag (current));
					}

					previousValueRaised = previousTypeRaised = false;
				}
		*/}

	private void parse(Indicator indicator,
			String value)
			throws ParsingException {
		if (!documentOpened) {
			fireEvent (ParsingEvent.START_NODE, MarkupNode.DOCUMENT, null);
			documentOpened = true;
		}
		switch (indicator) {
		case ALIAS:
			break;
		case ANCHOR:
			break;
		case COLLECT_ENTRY:
			break;
		case COMMENT:
			comment (value);
			break;
		case DIRECTIVE:
			break;
		case DOUBLE_QUOTE:
			if (!previousTypeRaised) {
				if (current.childrenType != null)
					type (current.childrenType);
				else autoType (handler, current, value, String.class.getName ());
				previousTypeRaised = true;
			}
			text (value);
			break;
		case FOLDED:
			break;
		case LITERAL:
			break;
		case MAPPING_END:
			closeSequence (MarkupNode.MAP);
			break;
		case MAPPING_KEY:
			current = openTag (value, current);
			break;
		case MAPPING_START:
			inlineStack.add (MarkupNode.MAP);
			openSequence (MarkupNode.MAP, true);
		case MAPPING_VALUE:
			LENGTH: switch (value.length ()) {
			case 0:
				break;
			case 1:
				Indicator indicatorCandidate = Indicator.get (value.charAt (0));
				if (indicatorCandidate != null) {
					switch (indicatorCandidate) {
					case MAPPING_START:
					case SEQUENCE_START:
						parse (indicatorCandidate, null);
						break LENGTH;
					default:
						break;
					}
				}
			default:
				if (!value.isEmpty ()) {
					if (!previousTypeRaised) {
						autoType (handler, current, value, String.class.getName ());
					}
					text (value);
					previousValueRaised = true;
				}
			}
			break;
		case RESERVED:
			break;
		case SEQUENCE_END:
			closeSequence (MarkupNode.LIST);
			break;
		case SEQUENCE_ENTRY:
			current.childrenNodeType = MarkupNode.LIST;
			if (currentLevel - previousLevel > 1) {
				openSequence (MarkupNode.LIST, false);
			} else if (currentLevel < previousLevel) {
				current = close (handler, current, previousLevel, currentLevel);
				previousLevel = currentLevel;
			}
			break;
		case SEQUENCE_START:
			inlineStack.add (MarkupNode.LIST);
			openSequence (MarkupNode.LIST, true);
			break;
		case SINGLE_QUOTE:
			break;
		case TYPE:
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
				break;
			}
			current.childrenType = value;
			if (raiseType) {
				raiseType = false;
				type (value);
				previousTypeRaised = true;
			} else {
				previousTypeRaised = false;
			}
			break;
		case NONE:
		default:
			break;
		}
	}

	private int level(String entry) {
		if (sequenceEntry) {
			currentLevel++;
			sequenceEntry = false;
		} else {
			currentLevel = computeLevel (entry);
		}
		return currentLevel;
	}

	private int computeLevel(String entry) {
		int level = 0;
		if ((indentation == null) && Regex.START_WITH_SPACE.matches (entry)) {
			Matcher matcher = Regex.INDENTATION.matcher (entry);
			matcher.find ();
			indentation = matcher.group ();
		}
		if (indentation != null) {
			while (entry.startsWith (indentation)) {
				level++;
				entry = entry.substring (indentation.length ());
			}
//			if (entry.charAt (0) == '-') {
//				level++;
//			}
		}
		return level;
	}

	private void openSequence(MarkupNode node,
			boolean incrementLevel) {
		switch (node) {
		case LIST:
		case MAP:
			previousLevel = currentLevel;
			if (incrementLevel) {
				currentLevel++;
			}
			if (!previousTypeRaised) {
				type (node == MarkupNode.LIST ? List.class.getName () : Map.class.getName ());
			}
			current = new Tag<MarkupNode> (current, null);
			current.childrenNodeType = node;
			level.add (node);
			fireEvent (ParsingEvent.START_NODE, node, null);
			break;
		default:
			throw new IllegalArgumentException ();
		}
	}

	private void closeSequence(MarkupNode node)
			throws ParsingException {
		if (inlineStack.poll () != node) {
			error ("Unexpected closure " + node);
		}
		previousLevel = currentLevel;
		fireEvent (ParsingEvent.END_NODE, node, null);
		current = current.parent;
		currentLevel--;
	}

	private Tag<MarkupNode> openTag(String name,
			Tag<MarkupNode> parent) {
		final Tag<MarkupNode> tag;
//		if (level.size () != currentLevel - 1) {
//			openSequence (name == null ? MarkupNode.LIST : MarkupNode.MAP, false);
//		}
//		System.out.println ("LEVEL = " + currentLevel);
		if (name == null) {
			tag = new Tag<> (parent, name);
		} else {
			name = name.substring (0, name.length () - 1);
			tag = new Tag<> (parent, name);
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TAG, null);
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TYPE, String.class.getName ());
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TYPE, String.class.getName ());
			fireEvent (ParsingEvent.START_NODE, MarkupNode.TEXT, name);
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TEXT, name);
		}
		return tag;
	}

	private Tag<MarkupNode> closeTag(Tag<MarkupNode> tag) {
		if (tag != null && tag.name != null) {
			fireEvent (ParsingEvent.END_NODE, MarkupNode.TAG, null);
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
			type (type);
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
				fireEvent (ParsingEvent.END_NODE, MarkupNode.TAG, current.name);
			}
			current = current.parent;
			if (current == null) {
				break;
			}
			if (newLevel != i) {
				fireEvent (ParsingEvent.END_NODE, current.childrenNodeType, null);
			}
		}
		return current;
	}

	private void closeDocument(Handler<ParsingData<MarkupNode>> handler,
			Tag<MarkupNode> current,
			int currentLevel) {
		close (handler, current, currentLevel, -1);

		fireEvent (ParsingEvent.END_NODE, MarkupNode.DOCUMENT, null);
	}

	private final Queue<Predicate<EntryUnderConstruction>> expectedClosureStack = Collections.asLifoQueue (new ArrayDeque<Predicate<EntryUnderConstruction>> ());

	class InitialPredicate implements
			Predicate<EntryUnderConstruction> {

		@Override
		public boolean evaluate(EntryUnderConstruction element) {
			char last = element.peek ();
			switch (last) {
			case '{':
				expectedClosureStack.add (new BracePredicate ());
				return true;
			case '[':
				expectedClosureStack.add (new BracketPredicate ());
				return true;
			case '"':
				expectedClosureStack.add (new TextPredicate ());
				element.restore (last);
				return true;
			case '?':
			case ':':
				element.restore (last);
				return true;
			case '-':
				if (element.peekPrevious () == '-') {
					if ("---".equals (element.peek (3))) {
						return true;
					}
					return false;
				} else if (element.checkNext ('-')) {
					return false;
				}
				return true;
			case ',':
				element.pop ();
				return true;
			case '!':
			case '*':
			case '&':
				expectedClosureStack.add (new EndOfIdPredicate ());
				element.restore (element.pop ());
				return true;
			case '#':
				expectedClosureStack.add (END_OF_LINE);
				element.restore (element.pop ());
				return true;
			default:
				break;
			}
			Predicate<EntryUnderConstruction> expectedClosure = expectedClosureStack.peek ();
			if (expectedClosure == null) {
				return END_OF_LINE.evaluate (element);
			}
			if (expectedClosure.evaluate (element)) {
				expectedClosureStack.poll ();
				return true;
			}
			return false;
		}
	}

	class ExpectedCharPredicate implements
			Predicate<EntryUnderConstruction> {
		private final char expected;

		ExpectedCharPredicate(char expected) {
			this.expected = expected;
		}

		@Override
		public boolean evaluate(EntryUnderConstruction element) {
			if (element.peek () == expected) {
				element.restore (element.pop ());
				return true;
			}
			return false;
		}
	}

	class SequencePredicate extends
			ExpectedCharPredicate {
		SequencePredicate(char expected) {
			super (expected);
		}
	};

	class BracketPredicate extends
			SequencePredicate {
		BracketPredicate() {
			super (']');
		}

	};

	class BracePredicate extends
			SequencePredicate {
		BracePredicate() {
			super ('}');
		}
	};

	class EndOfIdPredicate implements
			Predicate<EntryUnderConstruction> {

		@Override
		public boolean evaluate(EntryUnderConstruction element) {
			switch (element.peek ()) {
			case ' ':
			case '\t':
			case '\n':
			case '#':
				return true;
			default:
				return false;
			}
		}
	};

	class TextPredicate extends
			ExpectedCharPredicate {
		private boolean escaped = false;

		TextPredicate() {
			super ('"');
		}

		@Override
		public boolean evaluate(EntryUnderConstruction element) {
			if (escaped) {
				escaped = true;
				switch (element.peek ()) {
				// TODO: convert YAML escaped characters to Java
				default:
					break;
				}
				return false;
			}
			if (element.peek () == '\\') {
				escaped = true;
				return false;
			}
			return super.evaluate (element);
		}
	};

}