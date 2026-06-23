package io.jpostman.annotations.runtime;

final class Comparison {
	final String path;
	final String operator;
	final String expected;

	Comparison(String path, String operator, String expected) {
		this.path = path;
		this.operator = operator;
		this.expected = expected;
	}

	public static Comparison parseComparison(String expression) {
		String value = expression.trim();

		for (String operator : new String[] { ">=", "<=", "!=", "==", "=", ">", "<" }) {
			int index = value.indexOf(operator);
			if (index > 0) {
				String path = value.substring(0, index).trim();
				String expected = value.substring(index + operator.length()).trim();

				if (path.isBlank() || expected.isBlank()) {
					throw new IllegalStateException("compare rule must use path operator value: " + expression);
				}

				return new Comparison(path, operator, expected);
			}
		}

		throw new IllegalStateException("compare rule must use one of =, ==, !=, <, <=, >, >=: " + expression);
	}

	public boolean compare(Object actual, String operator, Object expected) {
		if ("=".equals(operator) || "==".equals(operator)) {
			return valuesEqual(actual, expected);
		}

		if ("!=".equals(operator)) {
			return !valuesEqual(actual, expected);
		}

		double actualNumber = number(actual);
		double expectedNumber = number(expected);

		switch (operator) {
		case "<":
			return actualNumber < expectedNumber;
		case "<=":
			return actualNumber <= expectedNumber;
		case ">":
			return actualNumber > expectedNumber;
		case ">=":
			return actualNumber >= expectedNumber;
		default:
			throw new IllegalStateException("Unsupported compare operator: " + operator);
		}
	}

	private double number(Object value) {
		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}

		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException e) {
			throw new IllegalStateException("Comparison requires numeric value but was: " + value);
		}
	}

	private boolean valuesEqual(Object actual, Object expected) {
		if (actual == null || expected == null) {
			return actual == expected;
		}

		if (actual instanceof Number || expected instanceof Number) {
			return Double.compare(number(actual), number(expected)) == 0;
		}

		return String.valueOf(actual).equals(String.valueOf(expected));
	}
}