package io.jpostman.annotations.runtime;

import java.util.ArrayList;
import java.util.List;

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

	private static final Logger log = LoggerFactory.getLogger(JPostmanReport.class);

	/** Latest JPostman execution info. */
	private JPostmanInfo info;

	/** Timestamp when this report object was created. */
	public final long created = System.currentTimeMillis();

	/** Passed top-level JPostman executions. */
	public final List<JPostmanInfo> passed = new ArrayList<>();

	/** Failed top-level JPostman executions. */
	public final List<JPostmanInfo> failed = new ArrayList<>();

	/** Skipped top-level JPostman executions. */
	public final List<JPostmanInfo> skipped = new ArrayList<>();

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
		long duration = 0L;
		for (JPostmanInfo info : all()) {
			if (info != null && info.duration() > 0L) {
				duration += info.duration();
			}
		}
		return duration;
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
	public void summary() {
		String text = log();
		if (!JPostmanOutputs.write(text)) {
			log.trace(text);
		}
	}
}
