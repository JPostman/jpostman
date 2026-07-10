package io.jpostman.codegen.render;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import io.jpostman.codegen.model.JPostmanAnnotationType;
import io.jpostman.codegen.model.JPostmanMethodSpec;

/**
 * Renders Java test method snippets that use JPostman annotations.
 */
public final class JavaTestMethodRenderer {

	private JavaTestMethodRenderer() {
	}

	public static String render(JPostmanMethodSpec spec) {
		List<String> attributes = annotationAttributes(spec);
		StringBuilder source = new StringBuilder();
		source.append("@Test").append(System.lineSeparator());
		source.append('@').append(spec.getType().annotationName());
		if (!attributes.isEmpty()) {
			source.append('(').append(String.join(", ", attributes)).append(')');
		}
		source.append(System.lineSeparator());
		source.append("public void ").append(spec.getMethodName()).append("() {").append(System.lineSeparator())
				.append(System.lineSeparator()).append('}').append(System.lineSeparator());
		return source.toString();
	}

	private static List<String> annotationAttributes(JPostmanMethodSpec spec) {
		List<String> attributes = new ArrayList<>();
		addArray(attributes, "tags", spec.getTags());
		addString(attributes, "id", spec.getId());
		addString(attributes, "namespace", spec.getNamespace());
		addString(attributes, "folder", spec.getFolder());
		if (spec.getType().isRequestAware()) {
			addString(attributes, "request", spec.getRequest());
		}
		if (spec.getType() == JPostmanAnnotationType.RUNNER) {
			addArray(attributes, "include", spec.getInclude());
			addArray(attributes, "exclude", spec.getExclude());
		}
		addString(attributes, "rule", spec.getRule());
		addArray(attributes, "filter", spec.getFilter());
		addArray(attributes, "dependsOn", spec.getDependsOn());
		if (spec.getType() == JPostmanAnnotationType.RUNNER || spec.getType() == JPostmanAnnotationType.RESPONSE) {
			addInteger(attributes, "verify", spec.getVerify());
		}
		addString(attributes, "executor", spec.getExecutor());
		if (spec.getType() == JPostmanAnnotationType.REQUEST || spec.getType() == JPostmanAnnotationType.RESPONSE) {
			addString(attributes, "cache", spec.getCache());
		}
		addString(attributes, "log", spec.getLog());
		if (spec.getType() == JPostmanAnnotationType.RUNNER || spec.getType() == JPostmanAnnotationType.RESPONSE) {
			addBoolean(attributes, "soft", spec.getSoft());
		}
		if (spec.getType() == JPostmanAnnotationType.RUNNER) {
			addBoolean(attributes, "lifecycle", spec.getLifecycle());
		}
		addString(attributes, "data", spec.getData());
		if (spec.getType() == JPostmanAnnotationType.RUNNER || spec.getType() == JPostmanAnnotationType.RESPONSE) {
			addArray(attributes, "asserts", spec.getAsserts());
			addBoolean(attributes, "enabled", spec.getEnabled());
		}
		addBoolean(attributes, "skip", spec.getSkip());
		return attributes;
	}

	private static void addString(List<String> attributes, String name, String value) {
		if (value != null) {
			attributes.add(name + " = " + stringLiteral(value));
		}
	}

	private static void addArray(List<String> attributes, String name, List<String> values) {
		if (values != null && !values.isEmpty()) {
			StringJoiner joiner = new StringJoiner(", ", "{", "}");
			for (String value : values) {
				joiner.add(stringLiteral(value));
			}
			attributes.add(name + " = " + joiner);
		}
	}

	private static void addInteger(List<String> attributes, String name, Integer value) {
		if (value != null) {
			attributes.add(name + " = " + value);
		}
	}

	private static void addBoolean(List<String> attributes, String name, Boolean value) {
		if (value != null) {
			attributes.add(name + " = " + value);
		}
	}

	private static String stringLiteral(String value) {
		StringBuilder result = new StringBuilder("\"");
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			switch (ch) {
			case '\\':
				result.append("\\\\");
				break;
			case '"':
				result.append("\\\"");
				break;
			case '\n':
				result.append("\\n");
				break;
			case '\r':
				result.append("\\r");
				break;
			case '\t':
				result.append("\\t");
				break;
			default:
				result.append(ch);
				break;
			}
		}
		result.append('"');
		return result.toString();
	}
}
