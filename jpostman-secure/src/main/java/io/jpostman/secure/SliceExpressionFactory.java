package io.jpostman.secure;

/**
 * Factory and strategy for parsing and applying slice expressions.
 */
public interface SliceExpressionFactory {

	/**
	 * Parses a slice expression.
	 *
	 * @param expression slice expression, for example {@code [-4:]}
	 * @return parsed slice expression
	 */
	SliceExpressionFactory parse(String expression);

	/**
	 * Applies the parsed slice expression.
	 *
	 * @param source value to redact
	 * @param mask   mask text
	 * @return redacted value
	 */
	String mask(String source, String mask);

	/**
	 * Applies custom masking to a parsed slice expression.
	 *
	 * <p>
	 * Override this when the default parser should be reused but the final mask
	 * behavior should be customized.
	 * </p>
	 *
	 * @param parsed parsed slice expression
	 * @param source value to redact
	 * @param mask   mask text
	 * @return redacted value
	 */
	default String mask(SliceExpressionFactory parsed, String source, String mask) {
		return parsed.mask(source, mask);
	}
}