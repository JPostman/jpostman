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

import io.jpostman.Params;

/**
 * Runtime information shared across a JPostman annotation execution chain.
 *
 * <p>
 * The same method list and parameter map are shared between parent responses,
 * dependency requests, next requests, and executors so helper methods can pass
 * state through the full chain.
 * </p>
 */
public final class JPostmanInfo {

	private static final Logger log = LoggerFactory.getLogger(JPostmanInfo.class);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());

	/**
	 * Current annotation id for this invocation.
	 *
	 * <p>
	 * This value comes from the current annotation's {@code id} attribute. It is
	 * blank when the current annotation does not define an id.
	 * </p>
	 */
	public final String id;

	/**
	 * Parent/inherited id for this invocation.
	 *
	 * <p>
	 * This preserves the id/key assigned by the caller chain. For example, a
	 * response with id {@code user} can call an executor with id {@code auth};
	 * inside the executor, {@link #id} is {@code auth} and {@link #callerId} is
	 * {@code user}.
	 * </p>
	 */
	public final String callerId;

	/**
	 * Executor selected for this invocation, or empty string for the default
	 * executor.
	 */
	public final String executor;

	/**
	 * Java method that triggered this invocation, or empty string for a top-level
	 * test/runner call.
	 */
	public final String caller;

	/**
	 * Java test/helper/executor method currently represented by this info object.
	 */
	public final String callee;

	/**
	 * JPostman annotation represented by this info object, or empty string when
	 * none.
	 */
	public String annotation;

	/**
	 * JPostman data section applied to this invocation, or empty string when none.
	 */
	public String data;

	/** Ordered Java methods visited by this JPostman execution chain. */
	public final List<String> methods;

	/** Context namespace associated with this invocation. */
	public String namespace;

	/** Postman folder associated with this invocation. */
	public String folder;

	/** Postman request associated with this invocation. */
	public String request;

	/** Mutable parameters shared by methods in the same execution chain. */
	public final Map<String, Object> params;

	/** Timestamp when the execution chain info was created. */
	public final long created;

	/** Cache key for this invocation, or empty string when caching is disabled. */
	private String cache;

	/** Timestamp when request execution started. */
	private long started;

	/** Timestamp when request execution ended. */
	private long ended;

	/** Request execution duration in milliseconds. */
	private long duration;

	/**
	 * Creates a new top-level runtime info object using the Java method name as the
	 * callee and no annotation id.
	 *
	 * @param method    Java test/helper/executor method name
	 * @param namespace context namespace
	 * @param folder    Postman folder name
	 * @param request   Postman request name
	 */
	public JPostmanInfo(String method, String namespace, String folder, String request) {
		this("", "", method, namespace, folder, request);
	}

	/**
	 * Creates a new top-level runtime info object.
	 *
	 * @param id        current annotation id, or blank when not defined
	 * @param executor  selected executor name, or empty string for default
	 * @param method    Java test/helper/executor method name
	 * @param namespace context namespace
	 * @param folder    Postman folder name
	 * @param request   Postman request name
	 */
	public JPostmanInfo(String id, String executor, String method, String namespace, String folder, String request) {
		this("", id, executor, "", "", method, namespace, folder, request, new ArrayList<>(), new LinkedHashMap<>(),
				System.currentTimeMillis());
	}

	private JPostmanInfo(String callerId, String id, String executor, String cache, String caller, String callee,
			String namespace, String folder, String request, List<String> methods, Map<String, Object> params,
			long created) {
		this.callerId = value(callerId);
		this.id = value(id);
		this.executor = value(executor);
		this.cache = value(cache);
		this.caller = value(caller);
		this.callee = value(callee);
		this.annotation = "";
		this.data = "";
		this.namespace = value(namespace);
		this.folder = value(folder);
		this.request = value(request);
		this.methods = methods;
		this.params = params;
		this.created = created;
	}

	/**
	 * Returns the best key/id inherited from this invocation for child calls.
	 *
	 * @return current id when defined, otherwise caller id
	 */
	public String key() {
		return first(id, callerId);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent.
	 *
	 * <p>
	 * The child has no current id. The parent key is preserved as
	 * {@link #callerId}.
	 * </p>
	 *
	 * @param method    child Java method name
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String namespace, String folder, String request) {
		return new JPostmanInfo(key(), "", executor, "", this.callee, method, first(namespace, this.namespace),
				first(folder, this.folder), first(request, this.request), methods, params, created);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent, while allowing id and executor to be selected
	 * for the child.
	 *
	 * <p>
	 * The current annotation id is stored in {@link #id}. The parent key is stored
	 * in {@link #callerId}.
	 * </p>
	 *
	 * @param method    child Java method name
	 * @param id        child annotation id, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String id, String executor, String namespace, String folder,
			String request) {
		return child(method, id, executor, "", namespace, folder, request);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent, while allowing id, executor, and cache to be
	 * selected for the child.
	 *
	 * @param method    child Java method name
	 * @param id        child annotation id, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param cache     child cache key, or blank to disable caching
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String id, String executor, String cache, String namespace, String folder,
			String request) {
		return new JPostmanInfo(key(), value(id), value(executor), value(cache), this.callee, method,
				first(namespace, this.namespace), first(folder, this.folder), first(request, this.request), methods,
				params, created);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values.
	 *
	 * @param method    child Java method name
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String namespace, String folder, String request) {
		return new JPostmanInfo(key(), "", executor, "", this.callee, method, value(namespace), value(folder),
				value(request), methods, params, created);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values, while allowing id and executor to be selected for the child.
	 *
	 * @param method    child Java method name
	 * @param id        child annotation id, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String id, String executor, String namespace, String folder,
			String request) {
		return childExact(method, id, executor, "", namespace, folder, request);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values, while allowing id, executor, and cache to be selected for the
	 * child.
	 *
	 * @param method    child Java method name
	 * @param id        child annotation id, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param cache     child cache key, or blank to disable caching
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String id, String executor, String cache, String namespace,
			String folder, String request) {
		return new JPostmanInfo(key(), value(id), value(executor), value(cache), this.callee, method, value(namespace),
				value(folder), value(request), methods, params, created);
	}

	/**
	 * Inherits namespace, folder, and request values from another info object only
	 * when this info object does not already define them.
	 *
	 * @param source source info object
	 * @return this info object
	 */
	public JPostmanInfo inheritLocation(JPostmanInfo source) {
		if (source == null) {
			return this;
		}
		if (isBlank(namespace) && !isBlank(source.namespace)) {
			namespace = source.namespace;
		}
		if (isBlank(folder) && !isBlank(source.folder)) {
			folder = source.folder;
		}
		if (isBlank(request) && !isBlank(source.request)) {
			request = source.request;
		}
		return this;
	}

	/**
	 * Applies namespace, folder, and request overrides when the supplied values are
	 * not blank.
	 *
	 * @param namespace namespace override
	 * @param folder    folder override
	 * @param request   request override
	 * @return this info object
	 */
	public JPostmanInfo location(String namespace, String folder, String request) {
		if (!isBlank(namespace)) {
			this.namespace = namespace;
		}
		if (!isBlank(folder)) {
			this.folder = folder;
		}
		if (!isBlank(request)) {
			this.request = request;
		}
		return this;
	}

	/**
	 * Updates the JPostman annotation represented by this invocation.
	 *
	 * @param value annotation name, such as {@code @JPostmanResponse}
	 * @return this info object
	 */
	public JPostmanInfo annotation(String value) {
		annotation = value(value);
		return this;
	}

	/**
	 * Updates the JPostman data section represented by this invocation.
	 *
	 * @param value data section name
	 * @return this info object
	 */
	public JPostmanInfo data(String value) {
		data = value(value);
		return this;
	}

	/** Updates the cache key for this invocation. */
	public void cache(String value) {
		cache = value(value);
	}

	/** Returns the cache key for this invocation, or empty string when disabled. */
	public String cache() {
		return cache;
	}

	/** Returns the timestamp when request execution started. */
	public long started() {
		return started;
	}

	/** Returns the timestamp when request execution ended. */
	public long ended() {
		return ended;
	}

	/** Returns request execution duration in milliseconds. */
	public long duration() {
		return duration;
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
		return !isBlank(value) ? value : value(fallback);
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String value(String value) {
		return value == null ? "" : value;
	}

	private static String date(long time) {
		return time > 0L ? DATE_FORMAT.format(Instant.ofEpochMilli(time)) : "";
	}

	private static void append(StringBuilder builder, String key, String value) {
		if (!isBlank(value)) {
			builder.append("\n  ").append(key).append("=").append(value);
		}
	}

	/**
	 * Adds dynamic parameters using key/value pairs.
	 *
	 * @param values key/value pairs
	 * @return this info object
	 */
	public JPostmanInfo params(Object... values) {
		params.putAll(Params.asMap(values));
		return this;
	}

	/**
	 * Adds dynamic parameters from an existing map.
	 *
	 * @param values parameters to add
	 * @return this info object
	 */
	public JPostmanInfo params(Map<String, ?> values) {
		if (values != null) {
			params.putAll(values);
		}
		return this;
	}

	/**
	 * Builds a readable multi-line log message with the current invocation values.
	 *
	 * @return formatted runtime info
	 */
	public String log() {
		StringBuilder builder = new StringBuilder();

		builder.append("JPostmanInfo {");
		append(builder, "annotation", annotation);
		append(builder, "id", id);
		append(builder, "callee", callee);
		append(builder, "callerId", callerId);
		append(builder, "caller", caller);
		builder.append("\n  methods=").append(methods);
		append(builder, "namespace", namespace);
		append(builder, "folder", folder);
		append(builder, "request", request);
		append(builder, "executor", executor);
		append(builder, "cache", cache);
		append(builder, "data", data);
		if (params != null && params.size() > 0)
			builder.append("\n  params=").append(params);
		builder.append("\n  created=").append(date(created));
		append(builder, "started", date(started));
		append(builder, "ended", date(ended));
		if (duration > 0)
			builder.append("\n  duration=").append(duration);
		builder.append("\n}");

		return builder.toString();
	}

	/** Prints {@link #log()} using trace level. */
	public void print() {
		log.trace(log());
	}
}
