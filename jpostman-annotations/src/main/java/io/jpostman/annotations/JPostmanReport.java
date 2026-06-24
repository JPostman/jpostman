package io.jpostman.annotations;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime report collected during JPostman annotation execution.
 *
 * <p>
 * The report is intentionally simple and mutable so tests can inspect it
 * directly after annotation-driven requests, dependencies, or runners execute.
 * </p>
 */
public final class JPostmanReport {

	private static final Logger log = LoggerFactory.getLogger(JPostmanReport.class);

	/** Latest JPostman execution info. */
	public JPostmanInfo info;

	/** Timestamp when this report object was created. */
	public final long created = System.currentTimeMillis();

	/** Total tracked execution time in milliseconds. */
	public long totalTime;

	/** Total number of tracked top-level executions. */
	public int total;

	/** Passed top-level JPostman executions. */
	public final List<JPostmanInfo> passed = new ArrayList<>();

	/** Failed top-level JPostman executions. */
	public final List<JPostmanInfo> failed = new ArrayList<>();

	/** Skipped top-level JPostman executions. */
	public final List<JPostmanInfo> skipped = new ArrayList<>();

	/**
	 * Stores the latest info without changing counters.
	 *
	 * @param info execution info to store
	 */
	public void add(JPostmanInfo info) {
		if (info != null) {
			this.info = info;
		}
	}

	/** Records a passed top-level execution. */
	public void passed(JPostmanInfo info) {
		count(info);
		passed.add(info);
	}

	/** Records a failed top-level execution. */
	public void failed(JPostmanInfo info) {
		count(info);
		failed.add(info);
	}

	/** Records a skipped top-level execution. */
	public void skipped(JPostmanInfo info) {
		count(info);
		skipped.add(info);
	}

	/** Clears collected execution values but keeps the same report instance. */
	public void clear() {
		info = null;
		totalTime = 0L;
		total = 0;
		passed.clear();
		failed.clear();
		skipped.clear();
	}

	/** Builds a readable report summary. */
	public String log() {
		return "JPostmanReport {" + "\n  total=" + total + "\n  passed=" + passed.size() + "\n  failed=" + failed.size()
				+ "\n  skipped=" + skipped.size() + "\n  totalTime=" + totalTime + "\n  latest="
				+ (info == null ? null : info.method) + "\n}";
	}

	/** Prints {@link #log()} using trace level. */
	public void print() {
		log.trace(log());
	}

	private void count(JPostmanInfo info) {
		add(info);
		total++;
		if (info != null && info.duration > 0L) {
			totalTime += info.duration;
		}
	}
}
