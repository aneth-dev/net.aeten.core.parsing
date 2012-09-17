package net.aeten.core.parsing.yaml;

import java.util.regex.Matcher;

import net.aeten.core.util.RegexBuilder;

public enum Regex {
	TYPE_OR_REF_OR_ANCHOR,
	INDENTATION,
	START_WITH_SPACE,
	INDICATED;

	private static final RegexBuilder<Regex> builder = new RegexBuilder<> (Regex.class);

	public Matcher matcher(CharSequence input,
			Object... args) {
		return builder.matcher (this, input, args);
	}

	public boolean matches(CharSequence input,
			Object... args) {
		return builder.matcher (this, input, args).matches ();
	}
}
