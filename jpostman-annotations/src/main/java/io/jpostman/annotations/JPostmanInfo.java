package io.jpostman.annotations;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime information shared across a JPostman annotation execution chain.
 *
 * <p>
 * The object is intentionally simple: fields are public for convenient access
 * in user helper methods, while collection references are final so the same
 * chain state is passed between dependencies and executors.
 * </p>
 */
public final class JPostmanInfo {

	private static final Logger log = LoggerFactory.getLogger(JPostmanInfo.class);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());

	/** Current Java test/helper method that owns this invocation. */
	public String method;

	/** Java test/helper methods visited by the current JPostman execution chain. */
	public final List<String> methods;

	/** Context namespace used for the current invocation. */
	public final String namespace;

	/** Postman folder used for the current invocation. */
	public final String folder;

	/** Postman request name used for the current invocation. */
	public final String request;

	/** Mutable parameters shared by methods in the same dependency chain. */
	public final Map<String, Object> params;

	/** Timestamp when this info object was created. */
	public final long created;

	/** Timestamp when request execution starts. */
	public long started;

	/** Timestamp when request execution ends. */
	public long ended;

	/** Request execution duration in milliseconds. */
	public long duration;

	/**
	 * Creates a new runtime info object.
	 *
	 * @param method    current Java method name
	 * @param namespace context namespace
	 * @param folder    Postman folder name
	 * @param request   Postman request name
	 */
	public JPostmanInfo(String method, String namespace, String folder, String request) {
		this(method, namespace, folder, request, new ArrayList<>(), new LinkedHashMap<>());
	}

	private JPostmanInfo(String method, String namespace, String folder, String request, List<String> dependsOn,
			Map<String, Object> params) {
		this(method, namespace, folder, request, dependsOn, params, System.currentTimeMillis());
	}

	private JPostmanInfo(String method, String namespace, String folder, String request, List<String> dependsOn,
			Map<String, Object> params, long created) {
		this.method = value(method);
		this.namespace = value(namespace);
		this.folder = value(folder);
		this.request = value(request);
		this.methods = dependsOn;
		this.params = params;
		this.created = created;
	}

	/**
	 * Creates child invocation info that shares the same dependency list and params
	 * map.
	 *
	 * @param method    child method name
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String namespace, String folder, String request) {
		return new JPostmanInfo(method, first(namespace, this.namespace), first(folder, this.folder),
				first(request, this.request), methods, params, created);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values.
	 *
	 * <p>
	 * Use this when an annotation's blank value means the default
	 * context/folder/request, not the caller's value. The method chain and params
	 * map are still shared.
	 * </p>
	 *
	 * @param method    child method name
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String namespace, String folder, String request) {
		return new JPostmanInfo(method, value(namespace), value(folder), value(request), methods, params, created);
	}

	/** Marks request execution start time. */
	public void start() {
		started = System.currentTimeMillis();
	}

	/** Marks request execution end time and calculates duration. */
	public void end() {
		ended = System.currentTimeMillis();
		duration = started > 0L ? ended - started : 0L;
	}

	private static String first(String value, String fallback) {
		return value != null && !value.isBlank() ? value : value(fallback);
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	private static String date(long time) {
		return time > 0L ? DATE_FORMAT.format(Instant.ofEpochMilli(time)) : "";
	}

	/**
	 * Builds a readable multi-line log message with the current invocation values.
	 *
	 * @return formatted runtime info
	 */
	public String log() {
		StringBuilder builder = new StringBuilder();

		builder.append("JPostmanInfo {").append("\n  method=").append(method).append("\n  methods=").append(methods)
				.append("\n  namespace=").append(namespace).append("\n  folder=").append(folder).append("\n  request=")
				.append(request).append("\n  params=").append(params).append("\n  created=").append(date(created))
				.append("\n  started=").append(date(started)).append("\n  ended=").append(date(ended))
				.append("\n  duration=").append(duration).append("\n}");

		return builder.toString();
	}

	/** Prints {@link #log()} using trace level. */
	public void print() {
		log.trace(log());
	}
}
