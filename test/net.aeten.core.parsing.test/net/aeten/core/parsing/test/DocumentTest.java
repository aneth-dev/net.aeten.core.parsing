package net.aeten.core.parsing.test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;

import net.aeten.core.parsing.Document;
import net.aeten.core.parsing.ParsingException;
import net.aeten.core.parsing.yaml.YamlParser;

public class DocumentTest {
	final static String FILE = "test.yaml";

	private static void indent(int level) {
		for (int i = 0; i < level; i++) {
			System.out.print("  ");
		}
	}

	public static class Node {
		public static void main(String[] args) throws FileNotFoundException, ParsingException {
			Document<Document.Node> document = Document.loadNodes(buildReader(FILE), new YamlParser());
			print(document.root, 0);
		}

		private static void print(Document.Node node, int level) {
			indent(level);
			level++;
			System.out.println(node.value + ((node.type == null)? "": (": " + node.type)));
			for (Document.Node child: node.getChildren()) {
				print(child, level);
			}
		}
	}

	public static class Element {
		public static void main(String[] args) throws FileNotFoundException, ParsingException {
			Document<Document.Element> document = Document.loadElements(buildReader(FILE), new YamlParser());
			print(document.root, 0);
		}

		private static void print(Document.Element element, int level) {
			print(element, level, null);
		}

		private static void print(Document.Element element, int level, String prefix) {
			switch (element.elementType) {
			case SEQUENCE:
				indent(level);
				if (prefix != null) {
					System.out.print(prefix);
				}
				System.out.println("!" + element.valueType);
				for (Document.Element child: element.asSequence()) {
					print(child, level + 1, "- ");
				}
				break;
			case MAPPING_ENTRY: {
				Document.MappingEntry tag = element.asMappingEntry();
				print(tag.getKey(), level, "? ");
				print(tag.getValue(), level, ": ");
				break;
			}
			case STRING:
				indent(level);
				if (prefix != null) {
					System.out.print(prefix);
				}
				System.out.println(((element.valueType == null)? "!null ": ("!" + element.valueType + " ")) + '"' + element.value + '"');
			default:
				break;
			}
		}
	}

	static Reader buildReader(String file) throws FileNotFoundException {
		String resource = DocumentTest.class.getPackage().getName().replace('.', '/') + "/" + file;
		resource = DocumentTest.class.getClassLoader().getResource(resource).getFile();
		return new BufferedReader(new FileReader(resource));
	}

}
