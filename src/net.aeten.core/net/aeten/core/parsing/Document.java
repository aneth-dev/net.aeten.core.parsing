package net.aeten.core.parsing;

import java.io.Reader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import net.aeten.core.event.Handler;
import net.aeten.core.parsing.Document.Element.ElementType;

/**
 * @author Thomas Pérennou
 */
public class Document<T> {
	public final Map<String, T> anchors = new HashMap<>();
	public T root;
	<Y extends T>Document(Y root) {
		this.root = root;
	}
	
	public static class Node implements
	      Cloneable {
		private List<Node> children;
		public Node parent;
		public String type;
		public String value;

		public Node() {
			this(null);
		}

		public Node(Node parent) {
			this.parent = parent;
		}

		@Override
		public String toString() {
			String string = (value == null || value.isEmpty()) ? "" : (value + ": ");
			string += (type == null) ? "" : (type);
			return string + (getChildren().isEmpty() ? "" : (string.isEmpty() ? children : " " + children));
		}

		public void addChild(Node child) {
			if (children == null) {
				children = new ArrayList<>();
			}
			children.add(child);
		}

		public Node getChild() {
			if (children == null) { throw new IllegalStateException(this + " has no children"); }
			if (children.size() > 1) { throw new IllegalStateException(this + " has more than one child"); }
			return children.get(0);
		}

		public List<Node> getChildren() {
			if (children == null) { return Collections.<Node> emptyList(); }
			return children;
		}

		@Override
		public Node clone() {
			return clone(new Node(parent), this);
		}
		
		static Node clone(Node clone, Node ref) {
			clone.type = ref.type;
			clone.value = ref.value;
			if (ref.children != null) {
				for (Node child : ref.children) {
					clone.addChild(child.clone());
				}
			}
			return clone;
		}
	}
	public static class Tag implements Map.Entry<Element, Element> {
		Element key;
		Element value;
		
		Tag() {}
		
		@Override
		public Element getKey() {
			return key;
		}
		
		void setKey(Element key) {
			this.key = key;
		}

		@Override
		public Element getValue() {
			return value;
		}

		@Override
		public Element setValue(Element value) {
			Element old = value;
			this.value = value;
			return old;
		}
		
		@Override
		public String toString() {
			// TODO Auto-generated method stub
			return key + ": " + value;
		}
	}

	public static class Element {
		public static enum ElementType {
			STRING, // String
			COLLECTION, // Deque<Element>
			TAG // Map.Entry<Element, Element>
		}
		
		final public Element parent;
		final public ElementType elementType;
		public String valueType;
		public Object value;
		
		Element(Element parent, ElementType type) {
			this(parent, type, null, null);
		}
		Element(Element parent, ElementType type, Object value) {
			this(parent, type, value, null);
		}
		Element(Element parent, ElementType type, Object value, String valueType) {
			if (type == null) {
				throw new Error("Unable to create Element without type");
			}
			this.parent=parent;
			this.elementType=type;
			this.value=value;
			this.valueType=valueType;
			switch (type) {
			case COLLECTION:
				if (value != null) {
					throw new IllegalArgumentException("Value must be null for " + type + ". " + value + " is given.");
				}
				this.value = new ArrayDeque<>();
				break;
			case TAG:
				this.value = new Tag();
				break;
			case STRING:
			default:
				this.value=value;
				break;
			}
		}
		
		public String asString() {
			assert (elementType == ElementType.STRING);
			return (String) value;
		}
		@SuppressWarnings("unchecked")
		public Deque<Element> asCollection() {
			assert (elementType == ElementType.COLLECTION);
			return (Deque<Element>) value;
		}
		public Tag asTag() {
			assert (elementType == ElementType.TAG);
			return (Tag) value;
		}
		
		public String toString() {
			switch (elementType) {
			case COLLECTION:
				return  "!" + valueType + " " + value;
			case STRING:
				return  "!" + valueType + " \"" + value + '"';
			case TAG:
				return "{" + value.toString() + "}";
			default:
				return "";
			}
		}
	}

	public static Document<Element> loadElements(Reader reader, Parser<MarkupNode> parser) throws ParsingException {
		final Document<Element> document = new Document<>(null);
		parser.parse(reader, new Handler<ParsingData<MarkupNode>>() {
			private Element entry = null;
			private String type = null;
			private final Queue<Document.Element> stack = Collections.asLifoQueue(new ArrayDeque<Document.Element>());

			void append(Element element) throws Error {
				Element parent = stack.peek();
				if (parent == null) {
					document.root = element;
				} else {
				switch (parent.elementType) {
				case COLLECTION:
					parent.asCollection().add(element);
					break;
				case TAG: {
					Tag tag = parent.asTag();
					if (tag.getKey() == null) {
						tag.setKey(element);
					} else {
						if (tag.getValue() != null) {
							throw new Error(String.format("Unable to insert element %s in tag. Key (%s) and value (%s) already defined", element.value, tag.getKey(), tag.getValue()));
						}
						tag.setValue(element);
					} 
					break;
				}
				case STRING:
					throw new Error(String.format("Unable to insert element %s inside a text node", element.value));
				default:
					break;
				}
				}
				stack.add(element);
				type = null;						
			}
			
			@Override
			public void handleEvent(ParsingData<MarkupNode> data) {
				switch (data.getEvent()) {
				case START_NODE:
					switch (data.getNodeType()) {
					case DOCUMENT:
						break;
					case TEXT:
						append(new Element(stack.peek(), ElementType.STRING, data.getValue(), type));
						break;
					case TAG:
						append(new Element(stack.peek(), ElementType.TAG, null, type));
						break;
					case LIST:
					case MAP:
						append(new Element(stack.peek(), ElementType.COLLECTION, null, type));
						break;
					case TYPE:
						type = data.getValue();
						break;
					case ANCHOR:
						document.anchors.put(data.getValue(), entry);
						break;
					case REFERENCE:
//						TODO Element.clone(entry, document.anchors.get(data.getValue()));
						break;
					default:
						break;
					}
					break;
				case END_NODE:
					switch (data.getNodeType()) {
					case LIST:
					case MAP:
//					case DOCUMENT:
					case TAG:
					case TEXT:
						type = stack.poll().valueType;
						break;
					default:
						break;
					}
					break;
				}
			}
		});
		return document;
	}
	

	public static Document<Node> loadNodes(Reader reader, Parser<MarkupNode> parser) throws ParsingException {
		final Document<Node> document = new Document<>(new Node());

		parser.parse(reader, new Handler<ParsingData<MarkupNode>>() {
			private Node node = null;
			private String type = null;
			private final Queue<Document.Node> stack = Collections.asLifoQueue(new ArrayDeque<Document.Node>());

			@Override
			public void handleEvent(ParsingData<MarkupNode> data) {
				switch (data.getEvent()) {
				case START_NODE:
					switch (data.getNodeType()) {
					case DOCUMENT:
						document.root.type = type;
						stack.add(document.root);
						type = null;
						node = document.root;
						break;
					case TEXT:
					case TAG:
						node = new Node(stack.peek());
						node.value = data.getValue();
						if (node.parent != null) node.parent.addChild(node);
						node.type = type;
						stack.add(node);
						type = null;
						break;
					case LIST:
					case MAP:
						node.type = type;
						node = new Node(stack.peek());
						if (node.parent != null) node.parent.addChild(node);
						node.value = "";
						stack.add(node);
						type = null;
						break;
//					case MAP:
//						node.type = type;
////						// do not append to parent
////						stack.add(stack.peek());
//						type = null;
//						break;
					case TYPE:
						type = data.getValue();
						break;
					case ANCHOR:
						document.anchors.put(data.getValue(), node);
						break;
					case REFERENCE:
						Node.clone(node, document.anchors.get(data.getValue()));
						break;
					default:
						break;
					}
					break;
				case END_NODE:
					switch (data.getNodeType()) {
					case LIST:
//						stack.poll();
//						type = null;
						type = stack.poll().type;
						break;
					case MAP:
//						Node polled = stack.poll();
////						stack.peek().children = polled.children;
////						for (Node child: polled.children) {
////							child.parent = stack.peek();
////						}
//						type = null;
						type = stack.poll().type;
						break;
					case DOCUMENT:
					case TAG:
					case TEXT:
						stack.poll();
						type = null;
						break;
					default:
						break;
					}
					break;
				}
			}
		});
		return document;
	}
}