package io.jpostman.annotations;

/**
 * Receives user-facing JPostman runtime output.
 *
 * <p>
 * Integrations such as ReStage can install a sink for the duration of a test
 * run and forward each message directly to their own console transport.
 * </p>
 */
@FunctionalInterface
public interface JPostmanOutput {

	/**
	 * Writes one complete JPostman output message.
	 *
	 * @param text formatted output text; may contain multiple lines and ANSI codes
	 */
	void write(String text);
}
