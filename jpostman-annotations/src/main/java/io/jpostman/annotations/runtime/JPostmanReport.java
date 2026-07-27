package io.jpostman.annotations.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.annotations.JPostmanOutputs;

/**
 * Runtime report collected during JPostman annotation execution.
 *
 * <p>
 * The report keeps the latest execution info and simple status lists for
 * passed, failed, and skipped top-level JPostman executions. The lists are
 * public for easy inspection in user tests.
 * </p>
 */
public final class JPostmanReport implements io.jpostman.annotations.JPostman.Report {

	private boolean summaryPrinted;

	private String diagnostic = "none";
	private String failAction = "ignore";
	private boolean skipRemaining;

	private static final Logger log = LoggerFactory.getLogger(JPostmanReport.class);

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

	/** Applies options declared on @JPostman.ReportContext. */
	public JPostmanReport configure(String diagnostic, String failAction) {
		this.diagnostic = option(diagnostic, "diagnostic", "none", "short", "full", "fail");
		this.failAction = option(failAction, "fail", "ignore", "skip all", "terminate");
		return this;
	}

	private String option(String value, String name, String... allowed) {
		String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
		for (String candidate : allowed) {
			if (candidate.equals(normalized))
				return normalized;
		}
		throw new IllegalArgumentException("Invalid @JPostman.ReportContext " + name + " value: " + value
				+ ". Allowed: " + String.join(", ", allowed));
	}

	/** Returns true after fail="skip all" has observed its first failure. */
	public boolean skipRemaining() {
		return skipRemaining;
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

	/**
	 * Returns the latest execution info and prints it using trace level.
	 *
	 * @return latest execution info, or {@code null} when nothing has executed
	 */
	public JPostmanInfo info() {
		return info(true);
	}

	/**
	 * Returns the latest execution info.
	 *
	 * @param print whether to print the latest info using trace level
	 * @return latest execution info, or {@code null} when nothing has executed
	 */
	public JPostmanInfo info(boolean print) {
		if (print && info != null) {
			print();
		}
		return info;
	}

	/**
	 * Prints the latest execution info using trace level.
	 *
	 * <p>
	 * This is a convenience alias for {@link #info()}.
	 * </p>
	 */
	public void print() {
		if (info != null) {
			info.print();
		}
	}

	/**
	 * Records a passed top-level execution.
	 *
	 * @param info passed execution info
	 */
	public void passed(JPostmanInfo info) {
		record(passed, info);
	}

	/**
	 * Records a failed top-level execution.
	 *
	 * @param info failed execution info
	 */
	public void failed(JPostmanInfo info) {
		record(failed, info);
		if ("skip all".equals(failAction)) {
			skipRemaining = true;
		} else if ("terminate".equals(failAction)) {
			System.exit(1);
		}
	}

	/**
	 * Records a skipped top-level execution.
	 *
	 * @param info skipped execution info
	 */
	public void skipped(JPostmanInfo info) {
		record(skipped, info);
	}

	private void record(List<JPostmanInfo> target, JPostmanInfo info) {
		update(info);
		if (!isReportableExecution(info)) {
			return;
		}

		removeRecorded(info);
		target.add(info);
	}

	private boolean isReportableExecution(JPostmanInfo info) {
		if (info == null) {
			return false;
		}

		return isRunnerRequest(info) || isTopLevel(info);
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
		skipRemaining = false;
		completed = 0L;
		summaryPrinted = false;
	}

	/**
	 * Returns the total number of recorded top-level executions.
	 *
	 * @return passed + failed + skipped count
	 */
	public int total() {
		return passed.size() + failed.size() + skipped.size();
	}

	/**
	 * Returns total execution time in milliseconds.
	 *
	 * @return sum of positive durations from passed, failed, and skipped entries
	 */
	public long duration() {
		long end = completed > 0L ? completed : System.currentTimeMillis();
		return Math.max(0L, end - created);
	}

	/**
	 * Returns all recorded execution infos in status order.
	 *
	 * @return passed, failed, and skipped infos
	 */
	public List<JPostmanInfo> all() {
		List<JPostmanInfo> infos = new ArrayList<>();
		infos.addAll(passed);
		infos.addAll(failed);
		infos.addAll(skipped);
		return infos;
	}

	/**
	 * Builds a readable multi-line report summary.
	 *
	 * @return formatted report summary
	 */
	public String log() {
		return "===============================================" + "\nJPostman report" + "\nTotal tests run: " + total()
				+ ", Passes: " + passed.size() + ", Failures: " + failed.size() + ", Skips: " + skipped.size()
				+ ", Duration: " + JPostmanInfo.formatDuration(duration(), true)
				+ "\n===============================================";
	}

	/** Prints {@link #log()} using trace level. */
	public synchronized void summary() {
		if (summaryPrinted)
			return;
		completed = System.currentTimeMillis();
		summaryPrinted = true;
		String text = log() + diagnosticLog();
		if (!JPostmanOutputs.write(text))
			log.trace(text);
	}

	private String diagnosticLog() {
		if ("none".equals(diagnostic))
			return "";
		List<JPostmanInfo> values = "fail".equals(diagnostic) ? failed : all();
		if (values.isEmpty())
			return "";
		StringBuilder out = new StringBuilder("\n\nJPostman diagnostics\n");
		boolean first = true;
		for (JPostmanInfo value : values) {
			if (value == null)
				continue;
			if (!first)
				out.append("\n");
			first = false;
			out.append(shortDiagnostic(value));
			if ("full".equals(diagnostic) || "fail".equals(diagnostic)) {
				String request = value.requestLog();
				if (!request.isBlank()) {
					out.append("\n-------------------------------------------------").append("\n")
							.append(request.stripTrailing()).append("\n");
				}
			}
		}
		return out.toString();
	}

	private String shortDiagnostic(JPostmanInfo info) {
		StringBuilder out = new StringBuilder(topMethod(info)).append(":  {");
		List<String> scope = new ArrayList<>();
		String namespace = value(info.namespace);
		if (!isDefault(namespace))
			scope.add("namespace = " + namespace);
		String folder = value(info.folder);
		if (!folder.isBlank() && !isDefault(folder))
			scope.add("folder = " + folder);
		String request = value(info.request);
		if (!request.isBlank())
			scope.add("request = " + request);
		out.append(String.join(", ", scope)).append("}");
		if (info.statusCode() != null) {
			out.append(", statusCode=").append(info.statusCode());
		}
		out.append(", duration=").append(JPostmanInfo.formatDuration(info.duration(), false));
		String chain = methodChain(info);
		if (!chain.isBlank())
			out.append("  (").append(chain).append(")");
		return out.toString();
	}

	private String topMethod(JPostmanInfo info) {
		if (info != null && info.methods != null && !info.methods.isEmpty()) {
			return value(info.methods.get(0));
		}
		return info == null ? "" : value(info.method);
	}

	private String methodChain(JPostmanInfo info) {
		if (info == null || info.methods == null || info.methods.size() < 2)
			return "";
		List<String> chain = new ArrayList<>();
		for (String item : info.methods) {
			String method = value(item);
			if (method.isBlank() || isExecutorMethod(method, info))
				break;
			chain.add(method);
		}
		return chain.size() < 2 ? "" : String.join(" -> ", chain);
	}

	private boolean isExecutorMethod(String method, JPostmanInfo info) {
		String name = value(method);
		return name.contains("Executor(") || name.endsWith("Executor") || "defaultExecutor".equals(name);
	}

	private boolean isDefault(String value) {
		return value == null || value.isBlank() || "<default>".equalsIgnoreCase(value)
				|| "default".equalsIgnoreCase(value);
	}
}
