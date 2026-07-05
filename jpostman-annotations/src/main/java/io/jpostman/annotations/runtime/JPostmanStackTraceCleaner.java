package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.opentest4j.TestAbortedException;

/**
 * Builds short, clickable stack traces for annotation-driven failures.
 */
public final class JPostmanStackTraceCleaner {
	private static final Set<String> DEFAULT_STACK_TRACE_FILTER = Set.of();
	private static final String PROPERTY_STACK_TRACE_MAX = "stacktrace.max";
	private static final String PROPERTY_STACK_TRACE_MIN = "stacktrace.min";
	private static final String PROPERTY_STACK_TRACE_SKIP_FILTER = "stacktrace.filter.skip";
	private static final String PROPERTY_STACK_TRACE_BOUNDARY_FILTER = "stacktrace.boundary";
	private static final String PROPERTY_STACK_TRACE_BOUNDARY_FILTER_ADD = "stacktrace.boundary.add";
	private static final String REFLECTION_BOUNDARY_CLASS = "jdk.internal.reflect.NativeMethodAccessorImpl";
	private static final String REFLECTION_BOUNDARY_METHOD = "invoke0";
	private static final String TESTNG_BOUNDARY_CLASS = "org.testng.internal.invokers.MethodInvocationHelper";
	private static final String JUNIT_BOUNDARY_CLASS = "org.junit.jupiter.engine.execution.MethodInvocation";
	private static final String JUNIT_BOUNDARY_METHOD = "proceed";
	private static final String JUNIT_INTERCEPTOR_BOUNDARY_CLASS = "org.junit.jupiter.engine.execution.InvocationInterceptorChain";
	private static final List<StackTraceBoundary> DEFAULT_STACK_TRACE_BOUNDARIES = Collections.unmodifiableList(
			List.of(StackTraceBoundary.of(REFLECTION_BOUNDARY_CLASS + "#" + REFLECTION_BOUNDARY_METHOD),
					StackTraceBoundary.of(TESTNG_BOUNDARY_CLASS),
					StackTraceBoundary.of(JUNIT_BOUNDARY_CLASS + "#" + JUNIT_BOUNDARY_METHOD),
					StackTraceBoundary.of(JUNIT_INTERCEPTOR_BOUNDARY_CLASS)));

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
		return cleanFailure(testClass, testMethod, error, false, true);
	}

	/**
	 * Creates a clean assertion failure with selectable verbosity.
	 *
	 * @param testClass         test class
	 * @param testMethod        test method
	 * @param error             original failure
	 * @param minimumStackTrace true to keep only the first useful user-code frame
	 * @param includeSuppressed true to append suppressed diagnostic messages
	 * @return assertion error with cleaned output
	 */
	public static AssertionError cleanFailure(Class<?> testClass, Method testMethod, Throwable error,
			boolean minimumStackTrace, boolean includeSuppressed) {
		return cleanFailure(testClass, testMethod, error, minimumStackTrace, includeSuppressed, false);
	}

	/**
	 * Creates a clean assertion failure for assertions executed from inside a
	 * runtime {@code @JPostman.Call} test body. The first stack frame should point
	 * to the actual assertion line, not the annotation line or JPostman proxy
	 * internals.
	 *
	 * @param testClass         test class
	 * @param testMethod        test method
	 * @param error             original failure
	 * @param minimumStackTrace true to keep only the first useful user-code frame
	 * @param includeSuppressed true to append suppressed diagnostic messages
	 * @return assertion error with cleaned output
	 */
	public static AssertionError cleanRuntimeFailure(Class<?> testClass, Method testMethod, Throwable error,
			boolean minimumStackTrace, boolean includeSuppressed) {
		return cleanFailure(testClass, testMethod, error, minimumStackTrace, includeSuppressed, true);
	}

	private static AssertionError cleanFailure(Class<?> testClass, Method testMethod, Throwable error,
			boolean minimumStackTrace, boolean includeSuppressed, boolean preferActualUserFrame) {
		Throwable root = rootCause(error);
		StringBuilder message = new StringBuilder();
		String rootMessage = root.getMessage();
		if (rootMessage == null || rootMessage.isBlank()) {
			rootMessage = root.getClass().getSimpleName();
		}
		message.append(rootMessage);

		if (includeSuppressed) {
			// Put secure request/response details directly into the main message.
			// Do not keep them as suppressed exceptions because the JVM may collapse their
			// stack trace with "... 1 more" or print internal JPostman frames again.
			for (Throwable suppressed : root.getSuppressed()) {
				String suppressedMessage = suppressed == null ? null : suppressed.getMessage();
				if (suppressedMessage != null && !suppressedMessage.isBlank()) {
					message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append(suppressedMessage);
				}
			}
		}

		AssertionError failure = new AssertionError(message.toString());
		failure.setStackTrace(minimumStackTrace ? minimumStack(testClass, testMethod, root)
				: cleanStack(testClass, testMethod, root, stackTraceProperties(testClass), preferActualUserFrame));
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
		return cleanThrowable(testClass, testMethod, error, false);
	}

	/**
	 * Cleans a normal throwable with selectable stack verbosity.
	 *
	 * @param testClass         test class
	 * @param testMethod        test or configuration method
	 * @param error             original failure
	 * @param minimumStackTrace true to keep only the first useful user-code frame
	 * @return copied throwable with cleaned output
	 */
	public static Throwable cleanThrowable(Class<?> testClass, Method testMethod, Throwable error,
			boolean minimumStackTrace) {
		Throwable root = rootCause(error);
		String message = root.getMessage();
		if (message == null || message.isBlank()) {
			message = root.getClass().getSimpleName();
		}

		Throwable cleaned = copyThrowable(root, message);
		cleaned.setStackTrace(minimumStackTrace ? minimumStack(testClass, testMethod, root)
				: cleanStack(testClass, testMethod, root));
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

	private static StackTraceElement[] minimumStack(Class<?> testClass, Method testMethod, Throwable root) {
		StackTraceElement testFrame = testFrame(testClass, testMethod);
		StackTraceElement userFrame = firstUserFrame(root, testFrame.getClassName());
		return new StackTraceElement[] { userFrame == null ? testFrame : userFrame };
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
		return cleanStack(testClass, testMethod, root, stackTraceProperties(testClass));
	}

	static StackTraceElement[] cleanStack(Class<?> testClass, Method testMethod, Throwable root,
			Properties properties) {
		return cleanStack(testClass, testMethod, root, properties, false);
	}

	static StackTraceElement[] cleanStack(Class<?> testClass, Method testMethod, Throwable root, Properties properties,
			boolean preferActualUserFrame) {
		List<StackTraceElement> result = new ArrayList<>();
		StackTraceElement testFrame = testFrame(testClass, testMethod);
		String testClassName = testFrame.getClassName();

		int maxStackTrace = maxStackTrace(properties);
		Set<String> filter = stackTraceFilter(properties);
		List<StackTraceBoundary> boundaries = stackTraceBoundaries(properties);

		Throwable stackSource = preferActualUserFrame ? runtimeStackSource(root, testClassName) : root;
		StackTraceElement firstUserFrame = firstUserFrame(stackSource, testClassName);
		boolean runtimeErrorTrace = preferActualUserFrame && firstUserFrame != null;
		int minimumRuntimeFrames = runtimeErrorTrace ? minStackTrace(properties) : 0;
		if (minimumRuntimeFrames > 0) {
			maxStackTrace = Math.max(maxStackTrace, minimumRuntimeFrames);
		}

		StackTraceElement[] stackTrace = stackSource.getStackTrace();
		int start = 0;
		int runtimeFramesAfterUser = 0;

		if (runtimeErrorTrace) {
			addFrame(result, firstUserFrame, maxStackTrace);
			start = indexAfter(stackTrace, firstUserFrame);
		} else {
			// For annotation-driven failures, add a clickable annotation/method hint first.
			// For dependency failures, do not replace the dependency line with the caller
			// test method. The first frame must be the actual user-code failure line.
			if (firstUserFrame == null || firstUserFrame.getMethodName().equals(testMethod.getName())) {
				addFrame(result, testFrame, maxStackTrace);
			}
		}

		for (int i = start; i < stackTrace.length; i++) {
			StackTraceElement element = stackTrace[i];
			boolean boundary = isStackTraceBoundary(element, boundaries);
			if (boundary && !keepRuntimeBoundaryFrame(runtimeErrorTrace, runtimeFramesAfterUser, properties)) {
				break;
			}

			String frameClass = element.getClassName();

			if (frameClass.equals(testClassName)) {
				boolean added = addFrame(result, element, maxStackTrace);
				if (runtimeErrorTrace && added) {
					runtimeFramesAfterUser++;
				}
				if (result.size() >= maxStackTrace) {
					break;
				}
				continue;
			}

			if (!boundary && isFiltered(frameClass, filter)) {
				continue;
			}

			boolean added = addFrame(result, element, maxStackTrace);
			if (runtimeErrorTrace && added) {
				runtimeFramesAfterUser++;
			}
			if (result.size() >= maxStackTrace) {
				break;
			}
		}

		return result.toArray(new StackTraceElement[0]);
	}

	private static Throwable runtimeStackSource(Throwable root, String testClassName) {
		Throwable best = root;
		int bestScore = runtimeStackScore(root, testClassName);

		Throwable current = root == null ? null : root.getCause();
		for (int depth = 0; current != null && current != current.getCause() && depth < 20; depth++) {
			int score = runtimeStackScore(current, testClassName);
			if (score > bestScore) {
				best = current;
				bestScore = score;
			}
			current = current.getCause();
		}

		return best == null ? root : best;
	}

	private static int runtimeStackScore(Throwable throwable, String testClassName) {
		if (throwable == null || testClassName == null || testClassName.isBlank()) {
			return 0;
		}

		StackTraceElement[] stackTrace = throwable.getStackTrace();
		if (stackTrace == null || stackTrace.length == 0) {
			return 0;
		}

		for (int i = 0; i < stackTrace.length; i++) {
			StackTraceElement element = stackTrace[i];
			if (element != null && testClassName.equals(element.getClassName())) {
				return stackTrace.length - i;
			}
		}

		return 0;
	}

	private static StackTraceElement firstUserFrame(Throwable root, String testClassName) {
		for (StackTraceElement element : root.getStackTrace()) {
			if (testClassName.equals(element.getClassName())) {
				return element;
			}
		}
		return null;
	}

	private static int indexAfter(StackTraceElement[] stackTrace, StackTraceElement frame) {
		if (stackTrace == null || frame == null) {
			return 0;
		}
		for (int i = 0; i < stackTrace.length; i++) {
			if (sameFrame(stackTrace[i], frame)) {
				return i + 1;
			}
		}
		return 0;
	}

	private static boolean keepRuntimeBoundaryFrame(boolean runtimeErrorTrace, int runtimeFramesAfterUser,
			Properties properties) {
		return runtimeErrorTrace && runtimeFramesAfterUser < minStackTrace(properties);
	}

	private static boolean addFrame(List<StackTraceElement> result, StackTraceElement frame, int maxStackTrace) {
		if (frame == null || result.size() >= maxStackTrace || containsFrame(result, frame)) {
			return false;
		}
		result.add(frame);
		return true;
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

	private static Properties stackTraceProperties(Class<?> testClass) {
		try {
			return JPostmanResourceLoader.loadProperties(JPostmanDataLoader.DEFAULT_CONFIG, testClass);
		} catch (Exception e) {
			return new Properties();
		}
	}

	private static int maxStackTrace(Properties properties) {
		try {
			String value = property(properties, PROPERTY_STACK_TRACE_MAX).trim();
			if (!value.isBlank()) {
				return Math.max(1, Integer.parseInt(value));
			}
		} catch (Exception e) {
			// Use unlimited user/application frames when the property is missing or
			// invalid.
		}

		return Integer.MAX_VALUE;
	}

	private static int minStackTrace(Properties properties) {
		try {
			String value = property(properties, PROPERTY_STACK_TRACE_MIN).trim();
			if (!value.isBlank()) {
				return Math.max(1, Integer.parseInt(value));
			}
		} catch (Exception e) {
			// Keep the default minimum runtime trace when the property is missing or
			// invalid.
		}

		return 1;
	}

	private static boolean isStackTraceBoundary(StackTraceElement element, List<StackTraceBoundary> boundaries) {
		for (StackTraceBoundary boundary : boundaries) {
			if (boundary.matches(element)) {
				return true;
			}
		}
		return false;
	}

	private static List<StackTraceBoundary> stackTraceBoundaries(Properties properties) {
		List<StackTraceBoundary> boundaries = new ArrayList<>();

		if (properties != null && properties.containsKey(PROPERTY_STACK_TRACE_BOUNDARY_FILTER)) {
			addBoundaries(boundaries, property(properties, PROPERTY_STACK_TRACE_BOUNDARY_FILTER));
		} else {
			boundaries.addAll(DEFAULT_STACK_TRACE_BOUNDARIES);
		}

		addBoundaries(boundaries, property(properties, PROPERTY_STACK_TRACE_BOUNDARY_FILTER_ADD));

		return boundaries;
	}

	private static void addBoundaries(List<StackTraceBoundary> boundaries, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		for (String item : value.split(",")) {
			String trimmed = item.trim();
			if (!trimmed.isBlank()) {
				boundaries.add(StackTraceBoundary.of(trimmed));
			}
		}
	}

	private static Set<String> stackTraceFilter(Properties properties) {
		String value = property(properties, PROPERTY_STACK_TRACE_SKIP_FILTER).trim();
		if (!value.isBlank()) {
			Set<String> filters = new LinkedHashSet<>();

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
		return DEFAULT_STACK_TRACE_FILTER;
	}

	private static String property(Properties properties, String key) {
		return properties == null ? "" : properties.getProperty(key, "");
	}

	private static boolean isFiltered(String frameClass, Set<String> filters) {
		for (String filter : filters) {
			if (frameClass.startsWith(filter)) {
				return true;
			}
		}
		return false;
	}

	private static final class StackTraceBoundary {
		private final String className;
		private final String methodName;

		private StackTraceBoundary(String className, String methodName) {
			this.className = className == null ? "" : className.trim();
			this.methodName = methodName == null ? "" : methodName.trim();
		}

		private static StackTraceBoundary of(String value) {
			String item = value == null ? "" : value.trim();
			int separator = item.indexOf('#');
			if (separator < 0) {
				separator = item.indexOf("::");
			}
			if (separator >= 0) {
				String className = item.substring(0, separator).trim();
				String methodName = item.substring(separator + (item.charAt(separator) == '#' ? 1 : 2)).trim();
				return new StackTraceBoundary(className, methodName);
			}
			if (item.contains(".")) {
				return new StackTraceBoundary(item, "");
			}
			return new StackTraceBoundary("", item);
		}

		private boolean matches(StackTraceElement element) {
			if (element == null) {
				return false;
			}
			return matchesClass(element) && matchesMethod(element);
		}

		private boolean matchesClass(StackTraceElement element) {
			return className.isBlank() || element.getClassName().equals(className)
					|| element.getClassName().startsWith(className + ".");
		}

		private boolean matchesMethod(StackTraceElement element) {
			return methodName.isBlank() || element.getMethodName().equals(methodName);
		}
	}
}
