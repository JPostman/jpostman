package io.jpostman.annotations;

import java.util.Objects;

/**
 * Installs a scoped output sink for JPostman-generated messages.
 *
 * <p>
 * The sink is inherited by child threads. When no sink is installed, JPostman
 * keeps using its normal SLF4J logging behavior.
 * </p>
 */
public final class JPostmanOutputs {

	private static final InheritableThreadLocal<JPostmanOutput> CURRENT = new InheritableThreadLocal<>();

	private JPostmanOutputs() {
	}

	/**
	 * Installs an output sink until the returned scope is closed.
	 *
	 * @param output destination for JPostman output
	 * @return scope that restores the previously installed sink
	 */
	public static Scope use(JPostmanOutput output) {
		Objects.requireNonNull(output, "output");
		JPostmanOutput previous = CURRENT.get();
		CURRENT.set(output);
		return new Scope(previous);
	}

	/** Returns whether a custom output sink is active on the current thread. */
	public static boolean isInstalled() {
		return CURRENT.get() != null;
	}

	/**
	 * Sends text to the installed sink.
	 *
	 * @return true when a sink received the text; false when no sink is installed
	 */
	public static boolean write(String text) {
		JPostmanOutput output = CURRENT.get();
		if (output == null || text == null || text.isEmpty()) {
			return false;
		}
		output.write(text);
		return true;
	}

	/**
	 * Sends a leveled message to the installed sink.
	 *
	 * <p>
	 * SLF4J-style <code>{}</code> placeholders are expanded before the message is
	 * forwarded. This keeps runtime log helpers useful when the hosting integration
	 * bypasses the core logger.
	 * </p>
	 *
	 * @param level   output level such as TRACE, DEBUG, INFO, WARN, or ERROR
	 * @param message message template
	 * @param args    optional placeholder values
	 * @return true when a sink received the message
	 */
	public static boolean write(String level, String message, Object... args) {
		if (!isInstalled()) {
			return false;
		}
		String text = format(message, args);
		String prefix = level == null || level.isBlank() ? "" : level.trim().toUpperCase() + " ";
		return write(prefix + text);
	}

	private static String format(String message, Object[] args) {
		String template = String.valueOf(message);
		if (args == null || args.length == 0) {
			return template;
		}
		StringBuilder result = new StringBuilder();
		int offset = 0;
		int index = 0;
		while (index < args.length) {
			int placeholder = template.indexOf("{}", offset);
			if (placeholder < 0) {
				break;
			}
			result.append(template, offset, placeholder);
			result.append(String.valueOf(args[index++]));
			offset = placeholder + 2;
		}
		result.append(template.substring(offset));
		while (index < args.length) {
			result.append(' ').append(String.valueOf(args[index++]));
		}
		return result.toString();
	}

	/** Scoped sink installation. */
	public static final class Scope implements AutoCloseable {
		private final JPostmanOutput previous;
		private boolean closed;

		private Scope(JPostmanOutput previous) {
			this.previous = previous;
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (previous == null) {
				CURRENT.remove();
			} else {
				CURRENT.set(previous);
			}
		}
	}
}
