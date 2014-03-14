package net.aeten.core.parsing.test;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;

import net.aeten.core.event.Handler;
import net.aeten.core.parsing.MarkupNode;
import net.aeten.core.parsing.Parser;
import net.aeten.core.parsing.ParsingData;
import net.aeten.core.parsing.ParsingException;
import net.aeten.core.parsing.yaml.YamlParser;

public class ParsingTest {
	public static class Yaml {
		public static void main(String[] args) throws Exception {
			ParsingTest.test(new YamlParser(), "test.yaml");
		}
	}

	public static <T extends Enum<?>>void test(Parser<MarkupNode> parser, String resource) throws FileNotFoundException, ParsingException {
		resource = ParsingTest.class.getPackage().getName().replace('.', '/') + "/" + resource;
		InputStream file = ParsingTest.class.getClassLoader().getResourceAsStream(resource);
		final Queue<String> currentTag = Collections.asLifoQueue(new ArrayDeque<String>());
		parser.parse(new BufferedReader(new InputStreamReader(file)), new Handler<ParsingData<MarkupNode>>() {
			private int level = 0;
			private boolean tagKeyAppend = false;
			private boolean parentIsTag = false;
			private boolean parentIsMapOrList = false;
			private long start, end;

			@Override
			public void handleEvent(ParsingData<MarkupNode> data) {
				switch (data.getEvent()) {
				case START_NODE:
					switch (data.getNodeType()) {
					case TEXT:
						System.out.print(" \"" + data.getValue());
						if (parentIsTag && !tagKeyAppend) {
							tagKeyAppend = true;
							currentTag.add(data.getValue());
							level++;
						}
						break;
					case ANCHOR:
						break;
					case REFERENCE:
						break;
					case TYPE:
						if (parentIsTag && !tagKeyAppend) {
							System.out.print("!" + data.getValue());
						} else {
							this.print("!" + data.getValue());
							if (parentIsMapOrList && !tagKeyAppend) {
								System.out.println();
							}
						}
						break;
					case MAP:
						parentIsMapOrList = true;
						System.out.println();
						this.println("{");
						level++;
						break;
					case LIST:
						parentIsMapOrList = true;
						System.out.println();
						this.println("[");
						level++;
						break;
					case TAG:
						tagKeyAppend = false;
						parentIsTag = true;
						this.print("« ");
						break;
					case DOCUMENT:
						this.println("---");
						start = System.currentTimeMillis();
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
						level--;
						this.println("}");
						parentIsMapOrList = false;
						if (parentIsTag && !tagKeyAppend) {
							tagKeyAppend = true;
							System.out.print(": ");
						}
						break;
					case LIST:
						level--;
						this.println("]");
						parentIsMapOrList = false;
						if (parentIsTag && !tagKeyAppend) {
							tagKeyAppend = true;
							System.out.print(": ");
						}
						break;
					case TAG:
						level--;
						this.println(currentTag.poll() + " »");
						parentIsTag = false;
						break;
					case DOCUMENT:
						end = System.currentTimeMillis();
						this.println("...");
						this.println((end - start) + " ms");
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
