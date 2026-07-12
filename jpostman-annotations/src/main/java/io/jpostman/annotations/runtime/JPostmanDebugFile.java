package io.jpostman.annotations.runtime;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import io.jpostman.Environment;
import io.jpostman.Request;

/**
 * Writes internal JPostman diagnostics to the file selected by the
 * {@code JPOSTMAN_DEBUG} environment variable.
 *
 * <p>
 * This channel is intentionally independent from the public annotation debug
 * options. Console output continues to follow {@code debug()} and
 * {@code log()}, while this file receives full diagnostic records whenever the
 * environment variable is set, including when user debug output is
 * {@code none}.
 * </p>
 *
 * <p>
 * The same name is also accepted as a JVM system property to simplify embedded
 * integrations and regression tests. The environment variable takes precedence.
 * Any file-system error is ignored so internal diagnostics can never change a
 * test result.
 * </p>
 */
final class JPostmanDebugFile {

	static final String NAME = "JPOSTMAN_DEBUG";

	/** Last environment loaded by the active annotation execution. */
	public static volatile Environment ENVIRONMENTS;

	/** Root or folder requests selected by annotation execution. */
	public static final Map<String, List<Request>> COLLECTIONS = Collections.synchronizedMap(new LinkedHashMap<>());

	private static final String BEGIN = "===== JPOSTMAN_INTERNAL_DEBUG =====";
	private static final String END = "===== END_JPOSTMAN_INTERNAL_DEBUG =====";
	private static final int MAX_ERROR_STACK_FRAMES = 5;
	private static final String[] ERROR_STACK_FRAME_PREFIXES = { "io.jpostman.annotations.runtime.", "org.testng.",
			"org.junit.", "org.junit.jupiter.", "org.junit.platform.", "java.lang.reflect.", "jdk.internal.reflect.",
			"sun.reflect." };
	private static final Object WRITE_LOCK = new Object();
	private static final Map<Throwable, Boolean> CAPTURED_FAILURES = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Set<String> ANNOTATION_LOGS = Collections.synchronizedSet(new LinkedHashSet<>());
	private static final Map<JPostmanInfo, String> PENDING_CALL_RECORDS = Collections
			.synchronizedMap(new WeakHashMap<>());

	private JPostmanDebugFile() {
	}

	static boolean enabled() {
		return path() != null;
	}

	/** Captures shared annotation settings without creating an event block. */
	static void info(Object testInstance, JPostmanInfo info, String annotationLog) {
		write("", testInstance, null, annotationLog, "", null);
	}

	/**
	 * Records an active {@code @JPostman.Call} method before its test body runs. If
	 * the runtime call is executed later, the placeholder record is replaced by the
	 * completed request/response record instead of producing a duplicate block.
	 */
	static void call(Object testInstance, JPostmanInfo info, String annotationLog) {
		write("EXECUTION", testInstance, info, annotationLog, "", null, true);
	}

	static void execution(Object testInstance, JPostmanInfo info, String annotationLog, String diagnostic) {
		write("EXECUTION", testInstance, info, annotationLog, diagnostic, null, false);
	}

	static void skipped(Object testInstance, JPostmanInfo info, String annotationLog, String diagnostic,
			Throwable error) {
		writeFailureOnce("SKIPPED", testInstance, info, annotationLog, diagnostic, error);
	}

	static void failure(Object testInstance, JPostmanInfo info, String annotationLog, String diagnostic,
			Throwable error) {
		writeFailureOnce("FAILURE", testInstance, info, annotationLog, diagnostic, error);
	}

	private static void writeFailureOnce(String event, Object testInstance, JPostmanInfo info, String annotationLog,
			String diagnostic, Throwable error) {
		if (error != null) {
			synchronized (CAPTURED_FAILURES) {
				if (CAPTURED_FAILURES.containsKey(error)) {
					return;
				}
				CAPTURED_FAILURES.put(error, Boolean.TRUE);
			}
		}
		write(event, testInstance, info, annotationLog, diagnostic, error, false);
	}

	private static void write(String event, Object testInstance, JPostmanInfo info, String annotationLog,
			String diagnostic, Throwable error) {
		write(event, testInstance, info, annotationLog, diagnostic, error, false);
	}

	private static void write(String event, Object testInstance, JPostmanInfo info, String annotationLog,
			String diagnostic, Throwable error, boolean pendingCall) {
		Path target = path();
		if (target == null) {
			return;
		}

		try {
			captureAnnotationLog(annotationLog);
			String record = event == null || event.isBlank() ? ""
					: record(event, testInstance, info, diagnostic, error);
			String replacedRecord = pendingRecord(info, record, pendingCall);
			Path parent = target.toAbsolutePath().normalize().getParent();
			if (parent != null) {
				Files.createDirectories(parent);
			}

			synchronized (WRITE_LOCK) {
				try (FileChannel channel = FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.READ,
						StandardOpenOption.WRITE); FileLock ignored = channel.lock()) {
					String existing = read(channel);
					String document = document(existing, record, replacedRecord);
					byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
					channel.truncate(0);
					channel.position(0);
					ByteBuffer buffer = ByteBuffer.wrap(bytes);
					while (buffer.hasRemaining()) {
						channel.write(buffer);
					}
				}
			}
		} catch (Exception | LinkageError ignored) {
			// Internal diagnostics must never affect test execution or console output.
		}
	}

	private static String pendingRecord(JPostmanInfo info, String record, boolean pendingCall) {
		if (info == null || record == null || record.isBlank()) {
			return "";
		}
		synchronized (PENDING_CALL_RECORDS) {
			if (pendingCall) {
				PENDING_CALL_RECORDS.put(info, record);
				return "";
			}
			String pending = PENDING_CALL_RECORDS.remove(info);
			return pending == null ? "" : pending;
		}
	}

	private static boolean sameRecord(String left, String right) {
		if (left == null || right == null || right.isBlank()) {
			return false;
		}
		return normalize(left).equals(normalize(right));
	}

	private static String normalize(String value) {
		return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').strip();
	}

	private static void captureAnnotationLog(String annotationLog) {
		if (annotationLog != null && !annotationLog.isBlank()) {
			ANNOTATION_LOGS.add(annotationLog.trim());
		}
	}

	private static String read(FileChannel channel) throws java.io.IOException {
		long size = channel.size();
		if (size <= 0 || size > Integer.MAX_VALUE) {
			return "";
		}

		ByteBuffer buffer = ByteBuffer.allocate((int) size);
		channel.position(0);
		while (buffer.hasRemaining() && channel.read(buffer) > 0) {
			// Continue until the complete document has been read.
		}
		buffer.flip();
		return StandardCharsets.UTF_8.decode(buffer).toString();
	}

	/**
	 * Keeps execution records first and rebuilds annotation settings, environments,
	 * and collections once at the physical end of the document.
	 */
	private static String document(String existing, String record, String replacedRecord) {
		StringBuilder result = new StringBuilder();
		appendBlock(result, records(existing, replacedRecord));
		appendBlock(result, record);
		appendBlock(result, contextFooter());
		return result.toString();
	}

	private static void appendBlock(StringBuilder result, String block) {
		if (block == null || block.isBlank()) {
			return;
		}
		if (result.length() > 0) {
			result.append(System.lineSeparator()).append(System.lineSeparator());
		}
		result.append(block.strip());
	}

	/**
	 * Extracts only complete event records from an existing document. This also
	 * migrates output produced by earlier implementations by removing old header or
	 * footer summaries and obsolete per-record metadata.
	 */
	private static String records(String existing, String replacedRecord) {
		if (existing == null || existing.isBlank()) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		int from = 0;
		while (true) {
			int begin = existing.indexOf(BEGIN, from);
			if (begin < 0) {
				break;
			}
			int end = existing.indexOf(END, begin + BEGIN.length());
			if (end < 0) {
				break;
			}
			String block = sanitizeRecord(existing.substring(begin, end + END.length()));
			if (!sameRecord(block, replacedRecord)) {
				appendBlock(result, block);
			} else {
				replacedRecord = "";
			}
			from = end + END.length();
		}
		return result.toString();
	}

	private static String sanitizeRecord(String record) {
		StringBuilder result = new StringBuilder();
		boolean skipSummarySection = false;
		String[] lines = record.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
		for (String line : lines) {
			if (line.startsWith("--- ") && line.endsWith(" ---")) {
				String section = line.substring(4, line.length() - 4).trim();
				skipSummarySection = "ENVIRONMENTS".equals(section) || "COLLECTIONS".equals(section);
				if (skipSummarySection) {
					continue;
				}
			}

			if (skipSummarySection) {
				if (END.equals(line)) {
					skipSummarySection = false;
				} else {
					continue;
				}
			}

			if (line.startsWith("annotationLog=") || line.startsWith("process=") || line.startsWith("thread=")) {
				continue;
			}
			result.append(line).append(System.lineSeparator());
		}
		return result.toString().stripTrailing();
	}

	private static String contextFooter() {
		StringBuilder result = new StringBuilder();

		String annotationLogs = annotationLogs();
		if (!annotationLogs.isBlank()) {
			appendBlock(result, "annotationLog=" + annotationLogs);
		}

		Environment environments = ENVIRONMENTS;
		if (environments != null) {
			appendBlock(result, sectionLog("ENVIRONMENTS", String.valueOf(environments).stripTrailing()));
		}

		String collections = collectionsLog();
		if (!collections.isBlank()) {
			appendBlock(result, sectionLog("COLLECTIONS", collections));
		}
		return result.toString().stripTrailing();
	}

	private static String sectionLog(String name, String value) {
		StringBuilder result = new StringBuilder();
		section(result, name, value);
		return result.toString().stripTrailing();
	}

	private static String annotationLogs() {
		synchronized (ANNOTATION_LOGS) {
			return String.join(",", ANNOTATION_LOGS);
		}
	}

	private static String collectionsLog() {
		StringBuilder result = new StringBuilder();
		synchronized (COLLECTIONS) {
			for (Map.Entry<String, List<Request>> entry : COLLECTIONS.entrySet()) {
				if (result.length() > 0) {
					result.append(System.lineSeparator());
				}
				result.append("folder=").append(value(entry.getKey())).append(System.lineSeparator());
				List<?> requests = entry.getValue();
				if (requests == null) {
					continue;
				}
				for (Object request : requests) {
					if (request != null) {
						result.append(String.valueOf(request).strip()).append(System.lineSeparator());
					}
				}
			}
		}
		return result.toString().stripTrailing();
	}

	private static String record(String event, Object testInstance, JPostmanInfo info, String diagnostic,
			Throwable error) {
		StringBuilder result = new StringBuilder();
		result.append(BEGIN).append(System.lineSeparator());
		result.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
		result.append("event=").append(value(event)).append(System.lineSeparator());
		result.append("testClass=").append(testClass(testInstance)).append(System.lineSeparator());
		result.append("method=").append(info == null ? "" : value(info.method)).append(System.lineSeparator());

		String infoValue = infoLog(info);
		if (!infoValue.isBlank()) {
			section(result, "INFO", infoValue);
		}
		if (diagnostic != null && !diagnostic.isBlank()) {
			result.append(diagnostic.stripTrailing()).append(System.lineSeparator());
		}
		if (error != null) {
			section(result, "ERROR", stackTrace(testInstance, info, error));
		}
		result.append(END);
		return result.toString();
	}

	/**
	 * Returns unresolved and resolved request diagnostics plus the response, when
	 * available. Request logging uses {@code log(false)} and {@code log(true)};
	 * response logging uses {@code log(true)}.
	 */
	static String diagnosticLog(Object context) {
		if (context == null) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		appendOwnerLog(context, "request", false, "REQUEST_UNRESOLVE", result);
		appendOwnerLog(context, "request", true, "REQUEST_RESOLVE", result);
		appendOwnerLog(context, "response", true, "RESPONSE", result);
		return result.toString();
	}

	private static void appendOwnerLog(Object context, String ownerName, boolean full, String sectionName,
			StringBuilder result) {
		try {
			Method ownerMethod = context.getClass().getMethod(ownerName);
			if (ownerMethod.getReturnType() == Void.TYPE) {
				return;
			}

			Object owner = ownerMethod.invoke(context);
			if (owner == null) {
				return;
			}

			Object logged;
			try {
				Method log = owner.getClass().getMethod("log", boolean.class);
				logged = log.getReturnType() == Void.TYPE ? null : log.invoke(owner, full);
			} catch (NoSuchMethodException ignored) {
				Method log = owner.getClass().getMethod("log");
				logged = log.getReturnType() == Void.TYPE ? null : log.invoke(owner);
			}

			if (logged == null || String.valueOf(logged).isBlank()) {
				return;
			}
			section(result, sectionName, String.valueOf(logged));
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			// Internal diagnostics must never affect test execution.
		}
	}

	private static void section(StringBuilder result, String name, String value) {
		result.append("--- ").append(name).append(" ---").append(System.lineSeparator());
		result.append(value).append(System.lineSeparator());
	}

	private static String infoLog(JPostmanInfo info) {
		if (info == null) {
			return "";
		}
		try {
			return value(info.log(true));
		} catch (RuntimeException | LinkageError ignored) {
			return "";
		}
	}

	/**
	 * Builds a compact internal failure trace. The assertion message is preserved,
	 * but framework plumbing is omitted because it does not help codegen or plugin
	 * diagnostics. At most a few user/application frames are retained.
	 */
	private static String stackTrace(Object testInstance, JPostmanInfo info, Throwable error) {
		if (error == null) {
			return "";
		}

		try {
			StringBuilder result = new StringBuilder();
			result.append(error.getClass().getName());
			String message = value(error.getMessage()).stripTrailing();
			if (!message.isBlank()) {
				result.append(": ").append(message);
			}

			List<StackTraceElement> frames = compactFrames(testInstance, info, error);
			for (StackTraceElement frame : frames) {
				result.append(System.lineSeparator()).append("\tat ").append(frame);
			}

			int omitted = Math.max(0, stackFrameCount(error) - frames.size());
			if (omitted > 0) {
				result.append(System.lineSeparator()).append("\t... ").append(omitted).append(" stack frame")
						.append(omitted == 1 ? "" : "s").append(" omitted");
			}
			return result.toString().stripTrailing();
		} catch (RuntimeException | LinkageError ignored) {
			return error.getClass().getName() + ": " + value(error.getMessage());
		}
	}

	private static List<StackTraceElement> compactFrames(Object testInstance, JPostmanInfo info, Throwable error) {
		java.util.ArrayList<StackTraceElement> result = new java.util.ArrayList<>();
		String testClass = testInstance == null ? "" : testInstance.getClass().getName();
		String method = info == null ? "" : value(info.method);

		Throwable current = error;
		for (int depth = 0; current != null && current != current.getCause() && depth < 20
				&& result.size() < MAX_ERROR_STACK_FRAMES; depth++) {
			StackTraceElement[] stack = current.getStackTrace();
			if (stack != null) {
				for (StackTraceElement frame : stack) {
					if (frame == null || containsFrame(result, frame)) {
						continue;
					}
					boolean testMethodFrame = !testClass.isBlank() && frame.getClassName().equals(testClass)
							&& (method.isBlank() || frame.getMethodName().equals(method));
					if (!testMethodFrame && internalFrame(frame.getClassName())) {
						continue;
					}
					if (!testClass.isBlank() && frame.getClassName().equals(testClass) && !method.isBlank()
							&& !frame.getMethodName().equals(method)) {
						continue;
					}
					result.add(frame);
					if (result.size() >= MAX_ERROR_STACK_FRAMES) {
						break;
					}
				}
			}
			current = current.getCause();
		}

		if (result.isEmpty() && testInstance != null && !method.isBlank()) {
			result.add(new StackTraceElement(testClass, method, testInstance.getClass().getSimpleName() + ".java", -1));
		}
		return result;
	}

	private static boolean internalFrame(String className) {
		String value = className == null ? "" : className;
		for (String prefix : ERROR_STACK_FRAME_PREFIXES) {
			if (value.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	private static boolean containsFrame(List<StackTraceElement> frames, StackTraceElement candidate) {
		for (StackTraceElement frame : frames) {
			if (frame.equals(candidate)) {
				return true;
			}
		}
		return false;
	}

	private static int stackFrameCount(Throwable error) {
		int count = 0;
		Throwable current = error;
		for (int depth = 0; current != null && current != current.getCause() && depth < 20; depth++) {
			StackTraceElement[] stack = current.getStackTrace();
			count += stack == null ? 0 : stack.length;
			current = current.getCause();
		}
		return count;
	}

	private static String testClass(Object testInstance) {
		return testInstance == null ? "" : testInstance.getClass().getName();
	}

	private static Path path() {
		String configured = null;
		try {
			configured = System.getenv(NAME);
		} catch (SecurityException ignored) {
			// Fall through to the system-property integration option.
		}
		if (configured == null || configured.isBlank()) {
			try {
				configured = System.getProperty(NAME);
			} catch (SecurityException ignored) {
				return null;
			}
		}
		if (configured == null || configured.isBlank()) {
			return null;
		}
		try {
			return Paths.get(configured.trim());
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	/** Clears process-local diagnostic state. Intended for regression isolation. */
	static void reset() {
		ENVIRONMENTS = null;
		COLLECTIONS.clear();
		ANNOTATION_LOGS.clear();
		CAPTURED_FAILURES.clear();
		PENDING_CALL_RECORDS.clear();
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}
}
