package io.jpostman.secure;

/**
 * Default implementation for parsing and applying slice expressions.
 *
 * <p>
 * Supported examples:
 * </p>
 *
 * <pre>
 * [0]
 * [0:1]
 * [0:-1]
 * [:-4]
 * [-4:]
 * </pre>
 *
 * <p>
 * For redaction, {@code [:-4]} and {@code [-4:]} both keep the last four
 * characters visible.
 * </p>
 */
public class DefaultSliceExpressionFactory implements SliceExpressionFactory {

	private Integer start;
	private Integer end;

	/**
	 * Parses a slice expression.
	 *
	 * @param expression slice expression, for example {@code [:-4]}
	 * @return parsed slice expression strategy
	 */
	@Override
	public SliceExpressionFactory parse(String expression) {
		if (expression == null || expression.isBlank()) {
			throw new IllegalArgumentException("slice expression cannot be blank");
		}

		String value = expression.trim();

		if (!value.startsWith("[") || !value.endsWith("]")) {
			throw new IllegalArgumentException("Invalid slice expression: " + expression);
		}

		String body = value.substring(1, value.length() - 1).trim();

		if (body.isEmpty()) {
			throw new IllegalArgumentException("Empty slice expression: " + expression);
		}

		if (!body.contains(":")) {
			int index = Integer.parseInt(body);
			return new DefaultSliceExpressionFactory(index, index + 1);
		}

		String[] parts = body.split(":", -1);

		if (parts.length != 2) {
			throw new IllegalArgumentException("Invalid slice expression: " + expression);
		}

		return new DefaultSliceExpressionFactory(parseOptionalInt(parts[0]), parseOptionalInt(parts[1]));
	}

	/**
	 * Creates an empty parser instance.
	 */
	public DefaultSliceExpressionFactory() {
	}

	private DefaultSliceExpressionFactory(Integer start, Integer end) {
		this.start = start;
		this.end = end;
	}

	/**
	 * Applies this slice expression to a value.
	 *
	 * <p>
	 * This follows Python-style slice behavior. The selected slice remains visible
	 * and everything else is replaced by the mask prefix.
	 * </p>
	 *
	 * @param source value to redact
	 * @param mask   mask text
	 * @return redacted value
	 */
	@Override
	public String mask(String source, String mask) {
		if (source == null || source.isEmpty()) {
			return mask;
		}

		int length = source.length();

		int resolvedStart = resolve(start, length, 0);
		int resolvedEnd = resolve(end, length, length);

		if (resolvedStart < 0) {
			resolvedStart = 0;
		}

		if (resolvedStart > length) {
			resolvedStart = length;
		}

		if (resolvedEnd < 0) {
			resolvedEnd = 0;
		}

		if (resolvedEnd > length) {
			resolvedEnd = length;
		}

		if (resolvedStart > resolvedEnd) {
			return mask;
		}

		String visible = source.substring(resolvedStart, resolvedEnd);

		return mask + visible;
	}

	private static int resolve(Integer value, int length, int defaultValue) {
		if (value == null) {
			return defaultValue;
		}

		return value < 0 ? length + value : value;
	}

	private static Integer parseOptionalInt(String value) {
		String trimmed = value.trim();

		if (trimmed.isEmpty()) {
			return null;
		}

		return Integer.parseInt(trimmed);
	}
}