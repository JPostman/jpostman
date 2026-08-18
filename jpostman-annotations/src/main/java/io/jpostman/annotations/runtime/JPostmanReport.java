package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.jpostman.annotations.JPostmanOutputs;

/**
 * Default report implementation injected by {@code @JPostman.ReportContext}.
 */
public final class JPostmanReport implements io.jpostman.annotations.JPostman.Report {

	private enum FailAction {
		IGNORE, SKIP_ALL, TERMINATE
	}

	private enum FailOutput {
		ERROR, REQUEST, RESPONSE, INFO, ALL
	}

	private boolean summaryPrinted;
	private boolean details;
	private FailAction failAction = FailAction.IGNORE;
	private EnumSet<FailOutput> failOutput = EnumSet.noneOf(FailOutput.class);
	private boolean skipRemaining;
	private int configurationFailures;
	private final Map<String, Throwable> failureDetails = new LinkedHashMap<>();

	/** Latest JPostman execution info. */
	private JPostmanInfo info;

	/**
	 * Timestamp when this report object was created, before
	 * user @BeforeClass/@BeforeAll.
	 */
	public final long created = System.currentTimeMillis();

	/**
	 * Timestamp captured when the class report is completed, after user teardown.
	 */
	private long completed;

	/** Passed top-level JPostman executions. */
	public final List<JPostmanInfo> passed = new ArrayList<>();

	/** Failed top-level JPostman executions. */
	public final List<JPostmanInfo> failed = new ArrayList<>();

	/** Skipped top-level JPostman executions. */
	public final List<JPostmanInfo> skipped = new ArrayList<>();

	/**
	 * Applies compact execution-detail output and composable failure behavior
	 * declared by {@code @JPostman.ReportContext}.
	 *
	 * @param details {@code true} to include compact execution details
	 * @param values  one action plus optional error/request/response/info/all
	 *                values
	 * @return this report
	 */
	public JPostmanReport configure(boolean details, String[] values) {
		this.details = details;
		return configure(values);
	}

	/**
	 * Applies the composable failure values declared by
	 * {@code @JPostman.ReportContext}. This overload keeps the current execution
	 * details setting unchanged.
	 *
	 * @param values one action plus optional error/request/response/info/all values
	 * @return this report
	 */
	public JPostmanReport configure(String... values) {
		FailAction action = null;
		EnumSet<FailOutput> outputs = EnumSet.noneOf(FailOutput.class);

		if (values != null) {
			for (String value : values) {
				if (value == null || value.isBlank()) {
					continue;
				}
				for (String part : value.split(",")) {
					String option = normalize(part);
					if (option.isEmpty()) {
						continue;
					}
					FailAction candidate = action(option);
					if (candidate != null) {
						if (action != null && action != candidate) {
							throw invalid(values, "Use only one action: ignore, skipAll, or terminate.");
						}
						action = candidate;
						continue;
					}
					FailOutput output = output(option);
					if (output == null) {
						throw invalid(values,
								"Allowed values: ignore, skipAll, terminate, error, request, response, info, all.");
					}
					outputs.add(output);
				}
			}
		}

		if (outputs.contains(FailOutput.ALL) && (outputs.contains(FailOutput.REQUEST)
				|| outputs.contains(FailOutput.RESPONSE) || outputs.contains(FailOutput.INFO))) {
			throw invalid(values, "Use all by itself instead of combining it with request, response, or info.");
		}

		this.failAction = action == null ? FailAction.IGNORE : action;
		this.failOutput = outputs;
		return this;
	}

	private String normalize(String value) {
		return value == null ? ""
				: value.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "").replace(" ", "");
	}

	private FailAction action(String value) {
		switch (value) {
		case "ignore":
			return FailAction.IGNORE;
		case "skipall":
			return FailAction.SKIP_ALL;
		case "terminate":
			return FailAction.TERMINATE;
		default:
			return null;
		}
	}

	private FailOutput output(String value) {
		switch (value) {
		case "error":
			return FailOutput.ERROR;
		case "request":
			return FailOutput.REQUEST;
		case "response":
			return FailOutput.RESPONSE;
		case "info":
			return FailOutput.INFO;
		case "all":
			return FailOutput.ALL;
		default:
			return null;
		}
	}

	private IllegalArgumentException invalid(String[] values, String detail) {
		return new IllegalArgumentException(
				"Invalid @JPostman.ReportContext fail value: " + java.util.Arrays.toString(values) + ". " + detail);
	}

	/** Returns true after fail="skipAll" has observed its first failure. */
	public boolean skipRemaining() {
		return skipRemaining;
	}

	boolean failureError() {
		return failOutput.contains(FailOutput.ERROR);
	}

	boolean failureRequest() {
		return failOutput.contains(FailOutput.REQUEST) || failOutput.contains(FailOutput.ALL);
	}

	boolean failureResponse() {
		return failOutput.contains(FailOutput.RESPONSE) || failOutput.contains(FailOutput.ALL);
	}

	boolean failureInfo() {
		return failOutput.contains(FailOutput.INFO) || failOutput.contains(FailOutput.ALL);
	}

	/**
	 * Returns whether this execution requested local failure output through
	 * {@code debug = "error"}. The mode may be combined with request, response,
	 * info, or all.
	 */
	boolean localErrorOutput(JPostmanInfo info) {
		String debug = info == null ? "" : value(info.debug);
		for (String part : debug.split(",")) {
			if ("error".equals(normalize(part))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Stores the latest execution info without changing status counters.
	 *
	 * @param info execution info to store
	 */
	public JPostmanInfo update(JPostmanInfo info) {
		this.info = info;
		return info;
	}

	/** Returns the latest execution info and prints it using trace level. */
	public JPostmanInfo info() {
		return info(true);
	}

	/** Returns the latest execution info. */
	public JPostmanInfo info(boolean print) {
		if (print && info != null) {
			print();
		}
		return info;
	}

	/** Prints the latest execution info using trace level. */
	public void print() {
		if (info != null) {
			info.print();
		}
	}

	/** Records a passed top-level execution. */
	public void passed(JPostmanInfo info) {
		record(passed, info);
	}

	/** Records a failed top-level execution. */
	public void failed(JPostmanInfo info) {
		failed(info, null);
	}

	/**
	 * Records a failed execution and defers all report output until
	 * {@link #summary()} so details always appear after the JPostman report.
	 */
	void failed(JPostmanInfo info, Throwable failure) {
		boolean reportable = record(failed, info);
		if (reportable) {
			failureDetails.put(executionKey(info), failure);
		}
		if (failAction == FailAction.SKIP_ALL) {
			skipRemaining = true;
		} else if (failAction == FailAction.TERMINATE) {
			summary();
			System.exit(1);
		}
	}

	/** Records a skipped top-level execution. */
	public void skipped(JPostmanInfo info) {
		record(skipped, info);
	}

	private boolean record(List<JPostmanInfo> target, JPostmanInfo info) {
		mergeCompletedDependencyIntoBlankTopLevel(info);
		removeCompletedDependenciesWhenOwnerExecuted(info);
		update(info);
		if (!isReportableExecution(info)) {
			return false;
		}

		removeRecorded(info);
		target.add(info);
		return true;
	}

	/**
	 * Removes intermediate response-dependency rows after the owning top-level
	 * response successfully reaches its own HTTP execution. The dependency remains
	 * visible only when it is the final completed request because the owner never
	 * produced a response. This keeps execution details to one row per test method
	 * without losing the useful dependency outcome on early failure.
	 */
	private void removeCompletedDependenciesWhenOwnerExecuted(JPostmanInfo info) {
		if (info == null || !isTopLevel(info) || info.statusCode() == null) {
			return;
		}
		/*
		 * A runtime @JPostman.Call and an executed @JPostman.Response dependency are
		 * two real Java/JPostman executions. Keep both report rows. Collapsing the
		 * dependency here hid the cached response method and made the Call appear to
		 * own the dependency request metadata.
		 */
		if ("@JPostmanCall".equals(value(info.annotation))) {
			return;
		}
		String owner = topMethod(info);
		if (owner.isBlank()) {
			return;
		}

		removeDependencies(passed, owner);
		removeDependencies(failed, owner);
		removeDependencies(skipped, owner);
	}

	private void removeDependencies(List<JPostmanInfo> values, String owner) {
		List<JPostmanInfo> removed = new ArrayList<>();
		values.removeIf(candidate -> {
			boolean match = isExecutedResponseDependency(candidate) && owner.equals(topMethod(candidate));
			if (match) {
				removed.add(candidate);
			}
			return match;
		});
		for (JPostmanInfo candidate : removed) {
			failureDetails.remove(executionKey(candidate));
		}
	}

	/**
	 * Replaces a zero-response top-level placeholder with the completed response
	 * dependency already recorded for the same test method.
	 *
	 * <p>
	 * JUnit/TestNG can report the owning test after a nested response has already
	 * completed. When the owner then fails before producing another response, both
	 * records have the same displayed top method. Preserve the dependency's status,
	 * duration, request location, and diagnostics, but keep the owner's final
	 * pass/fail/skip classification as one report entry.
	 * </p>
	 */
	private void mergeCompletedDependencyIntoBlankTopLevel(JPostmanInfo info) {
		if (info == null || !isTopLevel(info) || info.statusCode() != null) {
			return;
		}
		String owner = topMethod(info);
		if (owner.isBlank()) {
			return;
		}

		JPostmanInfo dependency = completedDependency(owner);
		if (dependency == null) {
			return;
		}

		passed.remove(dependency);
		failed.remove(dependency);
		skipped.remove(dependency);
		failureDetails.remove(executionKey(dependency));
		info.inheritExecutionOutcome(dependency);
	}

	private JPostmanInfo completedDependency(String owner) {
		JPostmanInfo result = null;
		for (JPostmanInfo candidate : all()) {
			if (isExecutedResponseDependency(candidate) && owner.equals(topMethod(candidate))) {
				result = candidate;
			}
		}
		return result;
	}

	private String executionKey(JPostmanInfo info) {
		if (info == null) {
			return "<null>";
		}
		return value(info.annotation) + '\u0000' + value(info.method) + '\u0000' + info.methodIndex + '\u0000'
				+ value(info.namespace) + '\u0000' + value(info.folder) + '\u0000' + value(info.request);
	}

	private boolean isReportableExecution(JPostmanInfo info) {
		if (info == null) {
			return false;
		}
		return isRunnerRequest(info) || isExecutedResponseDependency(info) || isTopLevel(info);
	}

	private boolean isExecutedResponseDependency(JPostmanInfo info) {
		return info != null && "@JPostmanResponse".equals(value(info.annotation)) && info.methodIndex > 0
				&& info.statusCode() != null;
	}

	boolean hasRunnerRequest(String methodName) {
		String expected = value(methodName);
		for (JPostmanInfo candidate : all()) {
			if (isRunnerRequest(candidate) && expected.equals(value(candidate.method))) {
				return true;
			}
		}
		return false;
	}

	/** Converts every completed request owned by one runner method to skipped. */
	void skipRunnerRequests(String methodName) {
		String expected = value(methodName);
		List<JPostmanInfo> matches = new ArrayList<>();
		for (JPostmanInfo candidate : all()) {
			if (isRunnerRequest(candidate) && expected.equals(value(candidate.method))) {
				matches.add(candidate);
			}
		}
		for (JPostmanInfo candidate : matches) {
			skipped(candidate);
		}
	}

	private boolean isRunnerRequest(JPostmanInfo info) {
		return info != null && "@JPostmanRunner".equals(value(info.annotation)) && !value(info.request).isBlank();
	}

	private boolean isTopLevel(JPostmanInfo info) {
		return info != null && !isRunnerRequest(info) && (info.methodIndex == 0
				|| (info.methodIndex < 0 && (info.methods == null || info.methods.isEmpty())));
	}

	private void removeRecorded(JPostmanInfo info) {
		passed.removeIf(existing -> sameExecution(existing, info));
		failed.removeIf(existing -> sameExecution(existing, info));
		skipped.removeIf(existing -> sameExecution(existing, info));
	}

	private boolean sameExecution(JPostmanInfo left, JPostmanInfo right) {
		if (left == null || right == null) {
			return false;
		}
		if (isRunnerRequest(left) || isRunnerRequest(right)) {
			return isRunnerRequest(left) && isRunnerRequest(right)
					&& value(left.annotation).equals(value(right.annotation))
					&& value(left.method).equals(value(right.method))
					&& value(left.namespace).equals(value(right.namespace))
					&& value(left.folder).equals(value(right.folder))
					&& value(left.request).equals(value(right.request));
		}
		if (isTopLevel(left) && isTopLevel(right)) {
			return value(left.method).equals(value(right.method));
		}
		return value(left.annotation).equals(value(right.annotation)) && value(left.method).equals(value(right.method))
				&& left.methodIndex == right.methodIndex && value(left.namespace).equals(value(right.namespace))
				&& value(left.folder).equals(value(right.folder)) && value(left.request).equals(value(right.request));
	}

	JPostmanInfo execution(String methodName) {
		String expected = value(methodName);
		for (JPostmanInfo candidate : all()) {
			if (candidate != null && expected.equals(value(candidate.method))) {
				return candidate;
			}
		}
		if (info != null && expected.equals(value(info.method))) {
			return info;
		}
		return null;
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	/** Clears collected execution values but keeps the same report instance. */
	public void clear() {
		info = null;
		passed.clear();
		failed.clear();
		skipped.clear();
		failureDetails.clear();
		skipRemaining = false;
		configurationFailures = 0;
		completed = 0L;
		summaryPrinted = false;
	}

	/** Returns the total number of recorded top-level executions. */
	public int total() {
		return passed.size() + failed.size() + skipped.size();
	}

	/** Returns total execution time in milliseconds. */
	public long duration() {
		long end = completed > 0L ? completed : System.currentTimeMillis();
		return Math.max(0L, end - created);
	}

	/** Returns all recorded execution infos in status order. */
	public List<JPostmanInfo> all() {
		List<JPostmanInfo> infos = new ArrayList<>();
		infos.addAll(passed);
		infos.addAll(failed);
		infos.addAll(skipped);
		return infos;
	}

	/** Builds a readable multi-line report summary. */
	public String log() {
		int combinedConfigurationFailures = Math.min(configurationFailures, passed.size());
		int displayedFailures = failed.size() + combinedConfigurationFailures;
		int displayedPasses = Math.max(0, total() - displayedFailures - skipped.size());

		return "===============================================\nJPostman report\nTotal tests run: " + total()
				+ ", Passes: " + displayedPasses + ", Failures: " + displayedFailures + ", Skips: " + skipped.size()
				+ ", Duration: " + JPostmanInfo.formatDuration(duration(), true)
				+ "\n===============================================";
	}

	/** Records a configuration failure that should be included in test totals. */
	void configurationFailed() {
		configurationFailures++;
	}

	/**
	 * Returns configuration failures waiting to be absorbed by displayed totals.
	 */
	int configurationFailures() {
		return configurationFailures;
	}

	/** Prints {@link #log()} using trace level. */
	public synchronized void summary() {
		if (summaryPrinted) {
			return;
		}
		completed = System.currentTimeMillis();
		summaryPrinted = true;
		JPostmanOutputs.writeOrTrace(log() + detailLog());
	}

	private String detailLog() {
		StringBuilder output = new StringBuilder();
		if (details) {
			output.append(executionDetailsLog());
		} else if (hasFailureOutput()) {
			output.append(failureLog());
		}
		output.append(localErrorLog());
		return output.toString();
	}

	private boolean hasFailureOutput() {
		return !failOutput.isEmpty();
	}

	private String executionDetailsLog() {
		List<JPostmanInfo> values = all();
		if (values.isEmpty()) {
			return "";
		}

		StringBuilder output = section("JPostman Execution Details:\n");
		boolean first = true;
		boolean previousHadAdditionalData = false;
		for (JPostmanInfo value : values) {
			if (value == null) {
				continue;
			}
			if (!first) {
				appendExecutionSeparator(output, previousHadAdditionalData);
			}
			first = false;
			output.append(executionDetailsLine(value, failureDetails.get(executionKey(value))));
			int detailStart = output.length();
			if (failed.contains(value)) {
				appendFailureDetails(output, value);
			}
			previousHadAdditionalData = output.length() > detailStart;
		}
		return first ? "" : output.toString();
	}

	private String failureLog() {
		if (failed.isEmpty()) {
			return "";
		}
		StringBuilder output = section("JPostman failures");
		boolean first = true;
		boolean previousHadAdditionalData = false;
		for (JPostmanInfo value : failed) {
			if (value == null) {
				continue;
			}
			if (!first) {
				appendExecutionSeparator(output, previousHadAdditionalData);
			}
			first = false;
			output.append(executionDetailsLine(value, failureDetails.get(executionKey(value))));
			int detailStart = output.length();
			appendFailureDetails(output, value);
			previousHadAdditionalData = output.length() > detailStart;
		}
		return first ? "" : output.toString();
	}

	/**
	 * Appends local {@code debug = "error"} failures after the report execution
	 * details. The local mode includes the cleaned error plus the secure request
	 * and response. ReportContext-selected details are not repeated.
	 */
	private String localErrorLog() {
		if (failed.isEmpty()) {
			return "";
		}

		StringBuilder output = null;
		boolean first = true;
		for (JPostmanInfo value : failed) {
			if (value == null || !localErrorOutput(value)) {
				continue;
			}

			boolean includeError = !failureError();
			boolean includeRequest = !failureRequest();
			boolean includeResponse = !failureResponse();
			if (!includeError && !includeRequest && !includeResponse) {
				continue;
			}

			if (output == null) {
				output = section("JPostman Errors:");
			}
			if (!first) {
				output.append(System.lineSeparator()).append(System.lineSeparator());
			}
			first = false;

			if (includeError) {
				appendError(output, value, failureDetails.get(executionKey(value)), false);
			}
			if (includeRequest) {
				appendBlock(output, value.requestLog());
			}
			if (includeResponse) {
				appendBlock(output, value.responseLog());
			}
		}
		return output == null ? "" : output.toString();
	}

	private void appendExecutionSeparator(StringBuilder output, boolean previousHadAdditionalData) {
		output.append(System.lineSeparator());
		if (previousHadAdditionalData) {
			output.append(System.lineSeparator());
		}
	}

	private StringBuilder section(String title) {
		return new StringBuilder(System.lineSeparator()).append(System.lineSeparator()).append(title)
				.append(System.lineSeparator());
	}

	private void appendFailureDetails(StringBuilder output, JPostmanInfo value) {
		if (failureError()) {
			appendError(output, value, failureDetails.get(executionKey(value)), true);
		}
		if (failureInfo()) {
			appendBlock(output, value == null ? "" : value.log(false));
		}
		if (failureRequest()) {
			appendBlock(output, value == null ? "" : value.requestLog());
		}
		if (failureResponse()) {
			appendBlock(output, value == null ? "" : value.responseLog());
		}
	}

	private void appendError(StringBuilder output, JPostmanInfo info, Throwable failure, boolean includeHeading) {
		String text = failureTrace(info, failure, includeHeading);
		if (text.isBlank()) {
			return;
		}
		// Every failed execution owns its output block. Do not suppress an error just
		// because another execution produced identical text.
		output.append(System.lineSeparator()).append(text);
	}

	private String failureTrace(JPostmanInfo info, Throwable failure, boolean includeHeading) {
		if (failure == null) {
			return "";
		}

		Throwable root = JPostmanStackTraceCleaner.rootCause(failure);
		StackTraceElement origin = failureOrigin(info, failure);
		Class<?> testClass = loadClass(origin == null ? "" : origin.getClassName());
		Method testMethod = findMethod(testClass, failureMethod(info, origin));
		Throwable display = cleanFailureTrace(testClass, testMethod, failure, root);

		String className = testClass == null ? simpleClassName(origin) : testClass.getSimpleName();
		String methodName = testMethod == null ? failureMethod(info, origin) : testMethod.getName();
		StringBuilder text = new StringBuilder();
		if (includeHeading && (!className.isBlank() || !methodName.isBlank())) {
			text.append("FAILED: ");
			if (!className.isBlank()) {
				text.append(className);
				if (!methodName.isBlank()) {
					text.append('.');
				}
			}
			text.append(methodName).append(System.lineSeparator());
		}

		text.append(display.getClass().getName()).append(": ")
				.append(value(JPostmanStackTraceCleaner.normalizeAssertionMessage(display.getMessage())));
		for (StackTraceElement element : userFailureStack(testClass, display)) {
			text.append(System.lineSeparator()).append("\tat ").append(element);
		}
		return text.toString().trim();
	}

	private StackTraceElement[] userFailureStack(Class<?> testClass, Throwable display) {
		StackTraceElement[] stack = display == null ? new StackTraceElement[0] : display.getStackTrace();
		if (testClass == null || stack.length == 0) {
			return stack;
		}
		List<StackTraceElement> user = new ArrayList<>();
		String className = testClass.getName();
		boolean started = false;
		for (StackTraceElement element : stack) {
			if (element == null) {
				continue;
			}
			if (className.equals(element.getClassName())) {
				started = true;
				user.add(element);
				continue;
			}
			if (started) {
				break;
			}
		}
		return user.isEmpty() ? stack : user.toArray(new StackTraceElement[0]);
	}

	private Throwable cleanFailureTrace(Class<?> testClass, Method testMethod, Throwable failure, Throwable root) {
		if (testClass == null || testMethod == null) {
			return root == null ? failure : root;
		}
		if (root instanceof AssertionError) {
			return JPostmanStackTraceCleaner.cleanFailure(testClass, testMethod, failure, false, true);
		}
		return JPostmanStackTraceCleaner.cleanThrowable(testClass, testMethod, failure, false);
	}

	private StackTraceElement failureOrigin(JPostmanInfo info, Throwable failure) {
		String top = topMethod(info);
		String current = info == null ? "" : value(info.method);
		Throwable value = failure;
		for (int depth = 0; value != null && depth < 32; depth++) {
			StackTraceElement fallback = null;
			for (StackTraceElement element : value.getStackTrace()) {
				if (element == null) {
					continue;
				}
				if (top.equals(element.getMethodName()) || current.equals(element.getMethodName())) {
					return element;
				}
				if (fallback == null && !frameworkClass(element.getClassName())) {
					fallback = element;
				}
			}
			if (fallback != null) {
				return fallback;
			}
			Throwable next = value.getCause();
			if (next == value) {
				break;
			}
			value = next;
		}
		return null;
	}

	private boolean frameworkClass(String className) {
		String name = value(className);
		return name.startsWith("io.jpostman.") || name.startsWith("org.junit.") || name.startsWith("org.testng.")
				|| name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("sun.");
	}

	private Class<?> loadClass(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		try {
			ClassLoader loader = Thread.currentThread().getContextClassLoader();
			return Class.forName(name, false, loader == null ? JPostmanReport.class.getClassLoader() : loader);
		} catch (LinkageError | ClassNotFoundException ignored) {
			return null;
		}
	}

	private Method findMethod(Class<?> type, String methodName) {
		if (type == null || methodName == null || methodName.isBlank()) {
			return null;
		}
		Class<?> current = type;
		while (current != null && current != Object.class) {
			Method fallback = null;
			for (Method method : current.getDeclaredMethods()) {
				if (!methodName.equals(method.getName())) {
					continue;
				}
				if (method.getParameterCount() == 0) {
					return method;
				}
				fallback = method;
			}
			if (fallback != null) {
				return fallback;
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private String failureMethod(JPostmanInfo info, StackTraceElement origin) {
		String method = topMethod(info);
		if (!method.isBlank()) {
			return method;
		}
		method = info == null ? "" : value(info.method);
		return !method.isBlank() ? method : origin == null ? "" : value(origin.getMethodName());
	}

	private String simpleClassName(StackTraceElement origin) {
		String name = origin == null ? "" : value(origin.getClassName());
		int separator = Math.max(name.lastIndexOf('.'), name.lastIndexOf('$'));
		return separator < 0 ? name : name.substring(separator + 1);
	}

	private void appendBlock(StringBuilder output, String value) {
		String text = value == null ? "" : value.trim();
		if (text.isBlank()) {
			return;
		}
		// Request/response/info data belongs to one execution. Identical requests
		// must still be printed for every failed execution that used them.
		output.append(System.lineSeparator()).append(System.lineSeparator()).append(text);
	}

	private boolean isSkipped(JPostmanInfo info) {
		if (info == null) {
			return false;
		}
		for (JPostmanInfo candidate : skipped) {
			if (candidate == info || sameExecution(candidate, info)) {
				return true;
			}
		}
		return false;
	}

	String executionDetailsLine(JPostmanInfo info) {
		return executionDetailsLine(info, null);
	}

	/**
	 * Builds one compact execution result. When execution failed before an HTTP or
	 * synthetic response was created, the failure message is printed on the next
	 * indented line so a zero-duration row explains why no status code exists.
	 */
	private String executionDetailsLine(JPostmanInfo info, Throwable failure) {
		StringBuilder out = new StringBuilder(displayMethod(info)).append(":  ");
		boolean skippedExecution = isSkipped(info);
		if (skippedExecution) {
			// verify=1 runs the HTTP request and only then converts the completed
			// execution to skipped. Preserve its response status so execution details can
			// distinguish it from a true pre-execution skip such as skipAll=true.
			if (info != null && info.statusCode() != null) {
				out.append("statusCode=").append(info.statusCode()).append(", ");
			}
			out.append("SKIPPED");
		} else {
			if (info != null && info.statusCode() != null) {
				out.append("statusCode=").append(info.statusCode()).append(", ");
			}
			out.append("duration=").append(JPostmanInfo.formatDuration(info == null ? 0L : info.duration(), false));
		}

		List<String> scope = new ArrayList<>();
		String namespace = value(info == null ? null : info.namespace);
		if (!isDefault(namespace)) {
			scope.add("namespace = " + namespace);
		}
		String folder = value(info == null ? null : info.folder);
		if (!folder.isBlank() && !isDefault(folder)) {
			scope.add("folder = " + folder);
		}
		String request = value(info == null ? null : info.request);
		if (!request.isBlank()) {
			scope.add("request = " + request);
		}
		out.append(", {").append(String.join(", ", scope)).append("}");

		if (info == null || info.statusCode() == null) {
			String message = compactFailureMessage(info, failure);
			if (!message.isBlank()) {
				out.append(System.lineSeparator()).append("\t\t ").append(message);
			}
		}

		String chain = methodChain(info);
		if (!chain.isBlank()) {
			out.append(System.lineSeparator()).append("\t\t (").append(chain).append(")");
		}
		return out.toString();
	}

	/**
	 * Returns the first available exception message as one line. Newlines, tabs,
	 * and repeated spaces are collapsed so compact report rows stay readable.
	 */
	private String compactFailureMessage(JPostmanInfo info, Throwable failure) {
		Throwable current = failure;
		for (int depth = 0; current != null && depth < 32; depth++) {
			String message = value(JPostmanStackTraceCleaner.normalizeAssertionMessage(current.getMessage()))
					.replaceAll("\\s+", " ").trim();
			if (!message.isBlank()) {
				if (message.startsWith("JPostman runner folder was not found.")) {
					return compactRunnerFolderNotFound(info);
				}
				return message;
			}
			Throwable next = current.getCause();
			if (next == current) {
				break;
			}
			current = next;
		}
		return "";
	}

	/**
	 * Builds the concise runner folder lookup message used by execution details.
	 * The full annotation usage diagnostic remains available in the failure trace.
	 */
	private String compactRunnerFolderNotFound(JPostmanInfo info) {
		String namespace = value(info == null ? null : info.namespace);
		if (isDefault(namespace)) {
			namespace = "";
		}
		String folder = value(info == null ? null : info.folder);
		if (isDefault(folder)) {
			folder = "";
		}
		return "JPostman runner folder was not found: \"" + folder + "\" (namespace=" + namespace + ", folder=" + folder
				+ ")";
	}

	private String displayMethod(JPostmanInfo info) {
		if (isExecutedResponseDependency(info)) {
			return value(info.method);
		}
		return topMethod(info);
	}

	private String topMethod(JPostmanInfo info) {
		if (info != null && info.methods != null && !info.methods.isEmpty()) {
			return value(info.methods.get(0));
		}
		return info == null ? "" : value(info.method);
	}

	private String methodChain(JPostmanInfo info) {
		if (info == null || info.methods == null || info.methods.size() < 2) {
			return "";
		}
		List<String> chain = new ArrayList<>();
		for (String item : info.methods) {
			String method = value(item);
			if (method.isBlank()) {
				break;
			}
			if (isExecutorMethod(method)) {
				String executor = executorClassName(method);
				if (!executor.isBlank() && (chain.isEmpty() || !executor.equals(chain.get(chain.size() - 1)))) {
					chain.add(executor);
				}
				break;
			}
			chain.add(method);
		}
		return chain.size() < 2 ? "" : String.join(" -> ", chain);
	}

	/**
	 * Returns the concise executor class/provider name used at the end of an
	 * execution chain. Request-selection details such as {@code (#Ref1)} or a
	 * quoted request name remain available in {@link JPostmanInfo#methods}, but are
	 * omitted from the compact report.
	 */
	private String executorClassName(String method) {
		String name = value(method).trim();
		int detail = name.indexOf('(');
		if (detail > 0) {
			name = name.substring(0, detail);
		}
		int packageSeparator = name.lastIndexOf('.');
		if (packageSeparator >= 0 && packageSeparator + 1 < name.length()) {
			name = name.substring(packageSeparator + 1);
		}
		return name.trim();
	}

	private boolean isExecutorMethod(String method) {
		String name = value(method);
		return name.contains("Executor(") || name.endsWith("Executor") || "defaultExecutor".equals(name);
	}

	private boolean isDefault(String value) {
		return value == null || value.isBlank() || "<default>".equalsIgnoreCase(value)
				|| "default".equalsIgnoreCase(value);
	}

}
