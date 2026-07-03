package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.opentest4j.TestAbortedException;


/**
 * Builds short, clickable stack traces for annotation-driven failures.
 */
public final class JPostmanStackTraceCleaner {
	private static final Set<String> DEFAULT_STACK_TRACE_FILTER = Set.of();
	private static final String REFLECTION_BOUNDARY_CLASS = "jdk.internal.reflect.NativeMethodAccessorImpl";
	private static final String REFLECTION_BOUNDARY_METHOD = "invoke0";

	private JPostmanStackTraceCleaner() {
	}

	/**
	 * Creates a clean assertion failure for a test method.
	 *
	 * @param testClass  test class
	 * @param testMethod test method
	 * @param error      original failure
	 * @return assertion error with a short stack trace
	 */
	public static AssertionError cleanFailure(Class<?> testClass, Method testMethod, Throwable error) {
		Throwable root = rootCause(error);
		StringBuilder message = new StringBuilder();
		String rootMessage = root.getMessage();
		if (rootMessage == null || rootMessage.isBlank()) {
			rootMessage = root.getClass().getSimpleName();
		}
		message.append(rootMessage);

		// Put secure request/response details directly into the main message.
		// Do not keep them as suppressed exceptions because the JVM may collapse their
		// stack trace with "... 1 more" or print internal JPostman frames again.
		for (Throwable suppressed : root.getSuppressed()) {
			String suppressedMessage = suppressed.getMessage();
			if (suppressedMessage != null && !suppressedMessage.isBlank()) {
				message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append(suppressedMessage);
			}
		}

		AssertionError failure = new AssertionError(message.toString());
		failure.setStackTrace(cleanStack(testClass, testMethod, root));
		return failure;
	}

	/**
	 * Cleans a normal test-method or configuration-method failure without mutating
	 * the original throwable.
	 *
	 * <p>
	 * TestNG can reuse the same configuration failure for skipped test results. If
	 * we mutate the original throwable stack trace, the original configuration
	 * failure can later point to the wrong test method. This method creates a
	 * display copy instead.
	 * </p>
	 *
	 * @param testClass  test class
	 * @param testMethod test or configuration method
	 * @param error      original failure
	 * @return copied throwable with a short stack trace
	 */
	public static Throwable cleanThrowable(Class<?> testClass, Method testMethod, Throwable error) {
		Throwable root = rootCause(error);
		String message = root.getMessage();
		if (message == null || message.isBlank()) {
			message = root.getClass().getSimpleName();
		}

		Throwable cleaned = copyThrowable(root, message);
		cleaned.setStackTrace(cleanStack(testClass, testMethod, root));
		return cleaned;
	}

	/**
	 * Returns true when the throwable represents a JUnit skipped/aborted test.
	 *
	 * @param throwable throwable to inspect
	 * @return {@code true} for JUnit abort/skip exceptions
	 */
	public static boolean isJUnitSkip(Throwable throwable) {
		return throwable instanceof TestAbortedException;
	}

	private static Throwable copyThrowable(Throwable root, String message) {
		if (root instanceof TestAbortedException) {
			return new TestAbortedException(message);
		}
		if (root instanceof AssertionError) {
			return new AssertionError(message);
		}
		if (root instanceof NullPointerException) {
			return new NullPointerException(message);
		}
		if (root instanceof IllegalStateException) {
			return new IllegalStateException(message);
		}
		if (root instanceof IllegalArgumentException) {
			return new IllegalArgumentException(message);
		}
		if (root instanceof RuntimeException) {
			return new RuntimeException(message);
		}
		return new Exception(message);
	}

	/**
	 * Returns the root cause, unwrapping reflection wrappers.
	 *
	 * @param throwable original throwable
	 * @return root throwable
	 */
	public static Throwable rootCause(Throwable throwable) {
		Throwable current = throwable;

		while (current instanceof InvocationTargetException
				&& ((InvocationTargetException) current).getCause() != null) {
			current = ((InvocationTargetException) current).getCause();
		}

		while (current.getCause() != null && current.getCause() != current && !(current instanceof AssertionError)) {
			current = current.getCause();
		}

		return current;
	}

	/**
	 * Builds a cleaned stack trace for a framework throwable.
	 *
	 * @param testClass  test class
	 * @param testMethod test or configuration method
	 * @param root       throwable to clean
	 * @return cleaned stack trace
	 */
	public static StackTraceElement[] cleanStack(Class<?> testClass, Method testMethod, Throwable root) {
		List<StackTraceElement> result = new ArrayList<>();
		StackTraceElement testFrame = testFrame(testClass, testMethod);
		String testClassName = testFrame.getClassName();

		int maxStackTrace = maxStackTrace(testClass);
		Set<String> filter = stackTraceFilter(testClass);
		StackTraceElement firstUserFrame = firstUserFrame(root, testClassName);

		// For a failure thrown directly inside the currently executing test method, add
		// a clickable annotation/method hint first, then keep the real assertion line.
		// For a dependency failure, do not replace the dependency line with the caller
		// test method. The first frame must be the actual user-code failure line.
		if (firstUserFrame == null || firstUserFrame.getMethodName().equals(testMethod.getName())) {
			addFrame(result, testFrame, maxStackTrace);
		}

		for (StackTraceElement element : root.getStackTrace()) {
			if (isReflectionBoundary(element)) {
				break;
			}

			String frameClass = element.getClassName();

			if (frameClass.equals(testClassName)) {
				addFrame(result, element, maxStackTrace);
				if (result.size() >= maxStackTrace) {
					break;
				}
				continue;
			}

			if (isFiltered(frameClass, filter)) {
				continue;
			}

			addFrame(result, element, maxStackTrace);
			if (result.size() >= maxStackTrace) {
				break;
			}
		}

		return result.toArray(new StackTraceElement[0]);
	}

	private static StackTraceElement firstUserFrame(Throwable root, String testClassName) {
		for (StackTraceElement element : root.getStackTrace()) {
			if (testClassName.equals(element.getClassName())) {
				return element;
			}
		}
		return null;
	}

	private static void addFrame(List<StackTraceElement> result, StackTraceElement frame, int maxStackTrace) {
		if (frame == null || result.size() >= maxStackTrace || containsFrame(result, frame)) {
			return;
		}
		result.add(frame);
	}

	private static boolean containsFrame(List<StackTraceElement> result, StackTraceElement frame) {
		for (StackTraceElement existing : result) {
			if (sameFrame(existing, frame)) {
				return true;
			}
		}
		return false;
	}

	private static boolean sameFrame(StackTraceElement left, StackTraceElement right) {
		return left.getLineNumber() == right.getLineNumber() && left.getClassName().equals(right.getClassName())
				&& left.getMethodName().equals(right.getMethodName()) && left.getFileName().equals(right.getFileName());
	}

	private static StackTraceElement testFrame(Class<?> testClass, Method testMethod) {
		String className = testClass.getName();
		String methodName = testMethod.getName();
		String fileName = testClass.getSimpleName() + ".java";
		int lineNumber = findSourceLine(testClass, methodName);

		return new StackTraceElement(className, methodName, fileName, lineNumber);
	}

	/**
	 * Finds the source line number for a method in the local source tree.
	 *
	 * <p>
	 * This is a best-effort helper used to make cleaned assertion failures point at
	 * the user's test method. It searches common Maven source roots from the
	 * current working directory upward.
	 * </p>
	 *
	 * @param testClass  test class that owns the method
	 * @param methodName Java method name to locate
	 * @return one-based source line number, or {@code -1} when the source line
	 *         cannot be found
	 */
	public static int findSourceLine(Class<?> testClass, String methodName) {
		String relativePath = testClass.getName().replace('.', java.io.File.separatorChar) + ".java";
		String[] sourceRoots = { "src/test/java", "src/main/java" };

		java.nio.file.Path current = java.nio.file.Paths.get("").toAbsolutePath();
		for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
			for (String sourceRoot : sourceRoots) {
				java.nio.file.Path source = current.resolve(sourceRoot).resolve(relativePath);
				int line = findMethodLine(source, methodName);
				if (line > 0) {
					return line;
				}
			}
		}

		return -1;
	}

	private static int findMethodLine(java.nio.file.Path source, String methodName) {
		if (!java.nio.file.Files.exists(source)) {
			return -1;
		}

		try {
			List<String> lines = java.nio.file.Files.readAllLines(source);
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i);
				if (line.contains(methodName + "(")) {
					int annotationLine = findJPostmanAnnotationLine(lines, i);
					return annotationLine > 0 ? annotationLine : i + 1;
				}
			}
		} catch (Exception e) {
			return -1;
		}

		return -1;
	}

	private static int findJPostmanAnnotationLine(List<String> lines, int methodIndex) {
		for (int i = methodIndex - 1; i >= 0; i--) {
			String trimmed = lines.get(i).trim();
			if (trimmed.isBlank() || trimmed.startsWith("//")) {
				continue;
			}
			if (trimmed.startsWith("@JPostman")) {
				return i + 1;
			}
			if (trimmed.startsWith("@") || trimmed.startsWith("}") || trimmed.startsWith(")")) {
				continue;
			}
			break;
		}
		return -1;
	}

	private static int maxStackTrace(Class<?> testClass) {
		try {
			Properties properties = JPostmanResourceLoader.loadProperties(JPostmanDataLoader.DEFAULT_CONFIG, testClass);
			String value = properties.getProperty("max.stacktrace", "").trim();
			if (!value.isBlank()) {
				return Math.max(1, Integer.parseInt(value));
			}
		} catch (Exception e) {
			// Use unlimited user/application frames when the file or property is missing or invalid.
		}

		return Integer.MAX_VALUE;
	}

	private static boolean isReflectionBoundary(StackTraceElement element) {
		return REFLECTION_BOUNDARY_CLASS.equals(element.getClassName())
				&& REFLECTION_BOUNDARY_METHOD.equals(element.getMethodName());
	}

	private static Set<String> stackTraceFilter(Class<?> testClass) {
		try {
			Properties properties = JPostmanResourceLoader.loadProperties(JPostmanDataLoader.DEFAULT_CONFIG, testClass);
			String value = properties.getProperty("stacktrace.filter", "").trim();
			if (!value.isBlank()) {
				Set<String> filters = new java.util.LinkedHashSet<>();

				for (String item : value.split(",")) {
					String trimmed = item.trim();
					if (!trimmed.isBlank()) {
						filters.add(trimmed);
					}
				}

				if (!filters.isEmpty()) {
					return filters;
				}
			}
		} catch (Exception e) {
			// Use default when the file or property is missing or invalid.
		}
		return DEFAULT_STACK_TRACE_FILTER;
	}

	private static boolean isFiltered(String frameClass, Set<String> filters) {
		for (String filter : filters) {
			if (frameClass.startsWith(filter)) {
				return true;
			}
		}
		return false;
	}
}
