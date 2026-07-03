package io.jpostman.annotations.runtime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jpostman.Params;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanContext;

/**
 * Runtime information shared across a JPostman annotation execution chain.
 *
 * <p>
 * A single request execution chain shares its method list and request parameter
 * maps between parent responses, dependency requests/responses, and executors
 * so helper methods can pass state through that chain. Runner executions fork
 * this state for each collection request so one request cannot leak method
 * history, headers, auth, timing, or response-specific info into the next
 * request.
 * </p>
 */
public final class JPostmanInfo implements io.jpostman.annotations.JPostman.Info {

	private static final String ID_PREFIX = "#";

	private static final Logger log = LoggerFactory.getLogger(JPostmanInfo.class);

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
			.withZone(ZoneId.systemDefault());

	/**
	 * Current annotation tags for this invocation.
	 *
	 * <p>
	 * This value comes from the current annotation's {@code tags} attribute. It is
	 * empty when the current annotation does not define tags.
	 * </p>
	 */
	public final String[] tags;

	/**
	 * @JPostmanContext annotation associated with this invocation, or null when not
	 *                  available.
	 */
	public final JPostmanContext context;

	/**
	 * Executor selected for this invocation, or empty string for the default
	 * executor.
	 */
	public final String executor;

	/** Java test/helper/executor method represented by this info object. */
	public final String method;

	/**
	 * JPostman annotation represented by this info object, or empty string when
	 * none.
	 */
	public String annotation;

	/**
	 * Optional annotation id associated with this invocation, or empty string when
	 * none. Dependency declarations can reference ids with dependsOn = "#id".
	 */
	public String id;

	/**
	 * JPostman data section applied to this invocation, or empty string when none.
	 */
	public String data;

	/** Ordered Java methods visited by this JPostman execution chain. */
	public final List<String> methods;

	/**
	 * Zero-based index of the current invocation entry inside {@link #methods}, or
	 * {@code -1} when this invocation has not been added to the method chain.
	 */
	public int methodIndex;

	/** Context namespace associated with this invocation. */
	public String namespace;

	/** Postman folder associated with this invocation. */
	public String folder;

	/** Postman request associated with this invocation. */
	public String request;

	/**
	 * Annotation id that originally selected the current Postman request, or empty
	 * when the request was selected without an annotation id. This is used for
	 * readable executor-chain logging without replacing the current invocation id.
	 */
	public String requestId;

	/**
	 * Mutable body values shared by methods in the same execution chain.
	 *
	 * <p>
	 * These values are applied to the Postman request body before the selected
	 * executor is invoked.
	 * </p>
	 */
	public final Map<String, Object> body;

	/** Query string values applied to the request URL. */
	public final Map<String, Object> query;

	/** Header values applied to the request. */
	public final Map<String, Object> headers;

	/** Body values that should be added instead of set/resolved. */
	public final Map<String, Object> bodyAdd;

	/** Query values that should be added instead of set/resolved. */
	public final Map<String, Object> queryAdd;

	/** Header values that should be added instead of set/resolved. */
	public final Map<String, Object> headersAdd;

	/** True when the next body/query/header call should use add semantics. */
	private boolean addNext;

	/**
	 * Path variable values applied to the request when supported by the request
	 * builder.
	 */
	public final Map<String, Object> path;

	/**
	 * Auth-related values applied to the request when supported by the request
	 * builder.
	 */
	public final Map<String, Object> auth;

	/** Timestamp when the execution chain info was created. */
	public final long created;

	/**
	 * Local debug override for this invocation, or empty string to inherit context
	 * debug.
	 */
	public String debug;

	/** Cache key for this invocation, or empty string when caching is disabled. */
	private String cache;

	/** Timestamp when request execution started. */
	private long started;

	/** Timestamp when request execution ended. */
	private long ended;

	/** Request execution duration in milliseconds. */
	private long duration;

	/**
	 * Creates a new top-level runtime info object using the Java method name and no
	 * annotation tags.
	 *
	 * @param method    Java test/helper/executor method name
	 * @param namespace context namespace
	 * @param folder    Postman folder name
	 * @param request   Postman request name
	 */
	public JPostmanInfo(String method, String namespace, String folder, String request) {
		this("", method, namespace, folder, request);
	}

	public JPostmanInfo(String annotation, String method, String namespace, String folder, String request) {
		this(new String[0], "", "", method, namespace, folder, request, new ArrayList<>(), new LinkedHashMap<>(),
				new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
				new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), System.currentTimeMillis(), null);
		this.annotation = annotation;
	}

	public JPostmanInfo(String[] tags, String executor, String method, String namespace, String folder,
			String request) {
		this(tags, executor, "", method, namespace, folder, request, new ArrayList<>(), new LinkedHashMap<>(),
				new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
				new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>(), System.currentTimeMillis(), null);
	}

	private JPostmanInfo(String[] tags, String executor, String cache, String method, String namespace, String folder,
			String request, List<String> methods, Map<String, Object> body, Map<String, Object> query,
			Map<String, Object> headers, Map<String, Object> bodyAdd, Map<String, Object> queryAdd,
			Map<String, Object> headersAdd, Map<String, Object> path, Map<String, Object> auth, long created,
			JPostmanContext context) {
		this.tags = tags(tags);
		this.context = context;
		this.executor = value(executor);
		this.cache = value(cache);
		this.method = value(method);
		this.annotation = "";
		this.id = "";
		this.data = "";
		this.debug = "";
		this.namespace = value(namespace);
		this.folder = value(folder);
		this.request = value(request);
		this.requestId = "";
		this.methods = methods;
		this.methodIndex = -1;
		this.body = body;
		this.query = query;
		this.headers = headers;
		this.bodyAdd = bodyAdd;
		this.queryAdd = queryAdd;
		this.headersAdd = headersAdd;
		this.addNext = false;
		this.path = path;
		this.auth = auth;
		this.created = created;
	}

	/**
	 * Returns {@code true} when any requested tag exists in {@link #tags}.
	 *
	 * @param values one or more tags to search for
	 * @return true when at least one requested tag is present
	 */
	public boolean hasTag(String... values) {
		return !isBlank(findTag(tags, values));
	}

	/**
	 * Returns this full runtime info object when callers use the compact
	 * {@code JPostman.Info} facade.
	 *
	 * @return this runtime info object
	 */
	@Override
	public JPostmanInfo attr() {
		return this;
	}

	@Override
	public <T> JPostman.Ref<T> ref() {
		return new JPostman.Ref<>();
	}

	@Override
	public <T> JPostman.Ref<T> ref(T value) {
		return new JPostman.Ref<>(value);
	}

	/**
	 * Appends a method/executor display entry to the shared method chain and
	 * returns the zero-based index assigned to that entry.
	 *
	 * @param method method or executor display name
	 * @return zero-based index inside {@link #methods}
	 */
	public int appendMethod(String method) {
		methods.add(value(method));
		return methods.size() - 1;
	}

	/**
	 * Marks this info object as the current invocation for the supplied method
	 * entry.
	 *
	 * @param method method or executor display name
	 * @return this info object
	 */
	public JPostmanInfo method(String method) {
		this.methodIndex = appendMethod(method);
		return this;
	}

	/**
	 * Sets the current invocation index without appending another method entry.
	 *
	 * @param index zero-based method-chain index
	 * @return this info object
	 */
	public JPostmanInfo methodIndex(int index) {
		this.methodIndex = index;
		return this;
	}

	/**
	 * Starts fluent tag-conditional actions for this invocation.
	 *
	 * <p>
	 * This is useful when a request helper needs to apply different parameters
	 * based on the current tag chain. Each condition returns the same rule builder
	 * after {@code then(...)} so multiple tag rules can be chained fluently.
	 * </p>
	 *
	 * <pre>
	 * info.tags().has("mouse").then(i -> i.body("title", "Wireless Mouse")).any("mouse", "shoes")
	 * 		.then(i -> i.body("discount", 15));
	 * </pre>
	 *
	 * @return fluent tag rule builder for this info object
	 */
	@Override
	public TagRules tags() {
		return new TagRules(this);
	}

	/**
	 * Fluent tag rule builder used by {@link JPostmanInfo#tags()}.
	 */
	public static final class TagRules {
		private final JPostmanInfo info;
		private boolean matched;

		private TagRules(JPostmanInfo info) {
			this.info = info;
		}

		/**
		 * Creates a condition that matches only when all supplied tags are present.
		 *
		 * @param values required tags
		 * @return pending tag condition
		 */
		public TagCondition has(String... values) {
			return new TagCondition(this, hasAll(values));
		}

		/**
		 * Creates a condition that matches when any supplied tag is present.
		 *
		 * @param values candidate tags
		 * @return pending tag condition
		 */
		public TagCondition any(String... values) {
			return new TagCondition(this, hasAny(values));
		}

		/**
		 * Creates a condition that matches when any current tag equals, contains, or
		 * matches the supplied values as regular expressions.
		 *
		 * @param values exact tags, text fragments, or regular expressions
		 * @return pending tag condition
		 */
		public TagCondition contains(String... values) {
			return new TagCondition(this, containsAny(values));
		}

		/**
		 * Returns a tag value by key. Plain tags return themselves, while key/value
		 * tags such as {@code product=myMouse} return only the value part.
		 *
		 * @param key plain tag name or key from a {@code key=value} tag
		 * @return tag value, or null when the tag/key is missing
		 */
		public String get(String key) {
			return info.tagValue(key);
		}

		/**
		 * Runs the action when no previous {@link #has(String...)},
		 * {@link #any(String...)}, or {@link #contains(String...)} condition matched in
		 * this rule chain.
		 *
		 * @param action default action to run with the current {@link JPostmanInfo}
		 * @return this tag rule builder
		 */
		public TagRules otherwise(Consumer<JPostmanInfo> action) {
			if (!matched && action != null) {
				action.accept(info);
			}
			return this;
		}

		private void matched() {
			this.matched = true;
		}

		private boolean hasAll(String... values) {
			String[] expected = JPostmanInfo.tags(values);
			if (expected.length == 0) {
				return false;
			}
			for (String value : expected) {
				if (!info.hasTag(value)) {
					return false;
				}
			}
			return true;
		}

		private boolean hasAny(String... values) {
			return info.hasTag(values);
		}

		private boolean containsAny(String... values) {
			return !isBlank(findMatchingTag(info.tags, values));
		}
	}

	/**
	 * Pending tag condition returned by {@link TagRules#has(String...)} and
	 * {@link TagRules#any(String...)}.
	 */
	public static final class TagCondition {
		private final TagRules rules;
		private final boolean matched;

		private TagCondition(TagRules rules, boolean matched) {
			this.rules = rules;
			this.matched = matched;
		}

		/**
		 * Runs the action when the condition matched and returns the parent builder.
		 *
		 * @param action action to run with the current {@link JPostmanInfo}
		 * @return parent tag rule builder for additional chained conditions
		 */
		public TagRules then(Consumer<JPostmanInfo> action) {
			if (matched) {
				rules.matched();
				if (action != null) {
					action.accept(rules.info);
				}
			}
			return rules;
		}
	}

	/**
	 * Creates a new independent info chain for one {@code @JPostmanRunner}
	 * collection request.
	 *
	 * <p>
	 * Runner-level dependencies may prepare shared request customizations such as
	 * auth, headers, body, query, and path values. Those values are copied into
	 * each request info object, but the per-request info then owns its own maps and
	 * method list. This prevents one runner request from leaking executor steps,
	 * timing, response metadata, or request-specific mutations into the next runner
	 * request.
	 * </p>
	 *
	 * @param requestName collection request name for this runner step
	 * @return independent info object for the runner request
	 */
	public JPostmanInfo runnerRequest(String requestName) {
		JPostmanInfo copy = new JPostmanInfo(this.tags, executor, cache, method, namespace, folder, value(requestName),
				new ArrayList<>(methods), copy(body), copy(query), copy(headers), copy(bodyAdd), copy(queryAdd),
				copy(headersAdd), copy(path), copy(auth), System.currentTimeMillis(), context);
		copy.annotation = this.annotation;
		copy.id = this.id;
		copy.data = this.data;
		copy.debug = this.debug;
		copy.requestId = this.requestId;
		copy.methodIndex = copiedMethodIndex(copy.methods);
		return copy;
	}

	private int copiedMethodIndex(List<String> copiedMethods) {
		if (methodIndex >= 0 && methodIndex < copiedMethods.size() && method.equals(copiedMethods.get(methodIndex))) {
			return methodIndex;
		}
		return copiedMethods.indexOf(method);
	}

	private static Map<String, Object> copy(Map<String, Object> source) {
		return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
	}

	/**
	 * Creates a copy of this info object associated with the @JPostmanContext
	 * annotation.
	 *
	 * @param value @JPostmanContext annotation, or null when not available
	 * @return copied info object with the supplied context
	 */
	public JPostmanInfo context(JPostmanContext value) {
		return copyMeta(new JPostmanInfo(this.tags, executor, cache, method, namespace, folder, request, methods, body,
				query, headers, bodyAdd, queryAdd, headersAdd, path, auth, created, value));
	}

	/**
	 * Creates a copy of this info object with a local log output override.
	 *
	 * <p>
	 * Blank values are ignored and keep the current inherited log output value.
	 * Non-blank values override the class-level {@link JPostmanContext#logOutput()}
	 * setting for this invocation and its children.
	 * </p>
	 *
	 * @param values local log output modes, or empty to keep the current value
	 * @return copied info object with the supplied log output override when
	 *         non-blank
	 */
	public JPostmanInfo debug(String... values) {
		JPostmanInfo copy = copyMeta(new JPostmanInfo(this.tags, executor, cache, method, namespace, folder, request,
				methods, body, query, headers, bodyAdd, queryAdd, headersAdd, path, auth, created, context));
		String value = join(values);
		if (!value.isBlank()) {
			copy.debug = value;
		}
		return copy;
	}

	private static String join(String... values) {
		if (values == null || values.length == 0) {
			return "";
		}
		return Arrays.stream(values).filter(value -> value != null && !value.isBlank()).map(String::trim)
				.reduce((left, right) -> left + "," + right).orElse("");
	}

	/**
	 * Creates a copy of this info object with additional tags appended to the
	 * current tag chain. Existing tags are preserved and duplicate tags are
	 * ignored.
	 *
	 * @param values tags to append
	 * @return copied info object with merged tags
	 */
	public JPostmanInfo withTags(String... values) {
		return copyMeta(new JPostmanInfo(mergeTags(this.tags, values), executor, cache, method, namespace, folder,
				request, methods, body, query, headers, bodyAdd, queryAdd, headersAdd, path, auth, created, context));
	}

	private JPostmanInfo copyMeta(JPostmanInfo copy) {
		copy.annotation = this.annotation;
		copy.id = this.id;
		copy.data = this.data;
		copy.debug = this.debug;
		copy.requestId = this.requestId;
		copy.methodIndex = this.methodIndex;
		return copy;
	}

	/**
	 * Returns the first tag in the current tag chain.
	 *
	 * @return first tag, or empty string when no tags are defined
	 */
	public String key() {
		return tag(tags);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent.
	 *
	 * <p>
	 * The child preserves the current tag chain.
	 * </p>
	 *
	 * @param method    child Java method name
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String namespace, String folder, String request) {
		return new JPostmanInfo(this.tags, executor, "", method, first(namespace, this.namespace),
				first(folder, this.folder), first(request, this.request), methods, body, query, headers, bodyAdd,
				queryAdd, headersAdd, path, auth, created, context).debug(this.debug);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent, while allowing tags and executor to be
	 * selected for the child.
	 *
	 * <p>
	 * The supplied tags are appended to the current tag chain.
	 * </p>
	 *
	 * @param method    child Java method name
	 * @param executor  child executor, or blank for the default executor
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String executor, String namespace, String folder, String request) {
		return child(method, executor, "", namespace, folder, request);
	}

	public JPostmanInfo child(String method, String[] tags, String executor, String namespace, String folder,
			String request) {
		return child(method, tags, executor, "", namespace, folder, request);
	}

	/**
	 * Creates child invocation info that inherits blank namespace, folder, and
	 * request values from the parent, while allowing tags, executor, and cache to
	 * be selected for the child.
	 *
	 * @param method    child Java method name
	 * @param executor  child executor, or blank for the default executor
	 * @param cache     child cache key, or blank to disable caching
	 * @param namespace child namespace, or blank to inherit
	 * @param folder    child folder, or blank to inherit
	 * @param request   child request, or blank to inherit
	 * @return child info object
	 */
	public JPostmanInfo child(String method, String executor, String cache, String namespace, String folder,
			String request) {
		return child(method, new String[0], executor, cache, namespace, folder, request);
	}

	public JPostmanInfo child(String method, String[] tags, String executor, String cache, String namespace,
			String folder, String request) {
		return new JPostmanInfo(mergeTags(this.tags, tags), value(executor), value(cache), method,
				first(namespace, this.namespace), first(folder, this.folder), first(request, this.request), methods,
				body, query, headers, bodyAdd, queryAdd, headersAdd, path, auth, created, context).debug(this.debug);
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
		return new JPostmanInfo(this.tags, executor, "", method, value(namespace), value(folder), value(request),
				methods, body, query, headers, bodyAdd, queryAdd, headersAdd, path, auth, created, context)
				.debug(this.debug);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values, while allowing tags and executor to be selected for the
	 * child.
	 *
	 * @param method    child Java method name
	 * @param tag       child annotation tag, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String tag, String executor, String namespace, String folder,
			String request) {
		return childExact(method, tags(tag), executor, "", namespace, folder, request);
	}

	public JPostmanInfo childExact(String method, String[] tags, String executor, String namespace, String folder,
			String request) {
		return childExact(method, tags, executor, "", namespace, folder, request);
	}

	/**
	 * Creates child invocation info without inheriting blank namespace, folder, or
	 * request values, while allowing tags, executor, and cache to be selected for
	 * the child.
	 *
	 * @param method    child Java method name
	 * @param tag       child annotation tag, or blank when not defined
	 * @param executor  child executor, or blank for the default executor
	 * @param cache     child cache key, or blank to disable caching
	 * @param namespace child namespace
	 * @param folder    child folder
	 * @param request   child request
	 * @return child info object
	 */
	public JPostmanInfo childExact(String method, String tag, String executor, String cache, String namespace,
			String folder, String request) {
		return childExact(method, tags(tag), executor, cache, namespace, folder, request);
	}

	public JPostmanInfo childExact(String method, String[] tags, String executor, String cache, String namespace,
			String folder, String request) {
		return new JPostmanInfo(mergeTags(this.tags, tags), value(executor), value(cache), method, value(namespace),
				value(folder), value(request), methods, body, query, headers, bodyAdd, queryAdd, headersAdd, path, auth,
				created, context).debug(this.debug);
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
	 * Updates the annotation id represented by this invocation.
	 *
	 * @param value annotation id
	 * @return this info object
	 */
	public JPostmanInfo id(String value) {
		id = annotationId(value);
		return this;
	}

	/**
	 * Updates the annotation id that owns the current Postman request location.
	 *
	 * @param value request-owner annotation id
	 * @return this info object
	 */
	public JPostmanInfo requestId(String value) {
		requestId = annotationId(value);
		return this;
	}

	static String annotationId(String value) {
		String id = value(value).trim();
		return id.startsWith(ID_PREFIX) ? id.substring(ID_PREFIX.length()).trim() : id;
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

	private String tagValue(String key) {
		return tagValue(tags, key);
	}

	private static String findTag(String[] source, String... requested) {
		if (source == null || requested == null) {
			return "";
		}
		for (String value : requested) {
			String expected = value(value).trim();
			if (isBlank(expected)) {
				continue;
			}
			for (String actual : source) {
				if (expected.equals(actual)) {
					return actual;
				}
			}
		}
		return "";
	}

	private static String findMatchingTag(String[] source, String... requested) {
		if (source == null || requested == null) {
			return "";
		}
		for (String value : requested) {
			String expected = value(value).trim();
			if (isBlank(expected)) {
				continue;
			}
			for (String actual : source) {
				if (matchesTag(actual, expected)) {
					return actual;
				}
			}
		}
		return "";
	}

	private static boolean matchesTag(String actual, String expected) {
		if (actual == null) {
			return false;
		}
		if (expected.equals(actual)) {
			return true;
		}
		try {
			return Pattern.compile(expected).matcher(actual).find();
		} catch (PatternSyntaxException ignored) {
			return false;
		}
	}

	private static String tagValue(String[] source, String key) {
		String expected = value(key).trim();
		if (source == null || isBlank(expected)) {
			return null;
		}
		for (String actual : source) {
			String tag = value(actual).trim();
			if (expected.equals(tag)) {
				return tag;
			}
			int index = tag.indexOf('=');
			if (index > 0 && expected.equals(tag.substring(0, index).trim())) {
				return tag.substring(index + 1).trim();
			}
		}
		return null;
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

	private static String[] tags(String value) {
		return isBlank(value) ? new String[0] : new String[] { value.trim() };
	}

	private static String[] tags(String[] values) {
		if (values == null) {
			return new String[0];
		}
		return Arrays.stream(values).filter(v -> !isBlank(v)).map(String::trim).toArray(String[]::new);
	}

	private static String[] mergeTags(String[] current, String[] values) {
		List<String> result = new ArrayList<>();
		for (String tag : tags(current)) {
			if (!result.contains(tag)) {
				result.add(tag);
			}
		}
		for (String tag : tags(values)) {
			if (!result.contains(tag)) {
				result.add(tag);
			}
		}
		return result.toArray(new String[0]);
	}

	private static String tag(String[] values) {
		return values != null && values.length > 0 ? values[0] : "";
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
	 * Makes the next body/query/header customization use add semantics instead of
	 * set/resolve semantics.
	 *
	 * <p>
	 * This flag is consumed by the next {@code body(...)}, {@code sbody(...)},
	 * {@code query(...)}, {@code squery(...)}, {@code headers(...)}, or
	 * {@code sheaders(...)} call. Later calls return to the default set/resolve
	 * behavior.
	 * </p>
	 *
	 * @return this info object
	 */
	public JPostmanInfo add() {
		addNext = true;
		return this;
	}

	/**
	 * Adds request body values using key/value pairs.
	 *
	 * <p>
	 * These values are applied to the Postman request body before the selected
	 * executor is invoked.
	 * </p>
	 *
	 * @param values key/value pairs
	 * @return this info object
	 */
	public JPostmanInfo body(Object... values) {
		put(target(body, bodyAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret request body values using key/value pairs. */
	public JPostmanInfo sbody(Object... values) {
		put(target(body, bodyAdd), valuesMap(true, values));
		return this;
	}

	/** Adds request body values from an existing map. */
	public JPostmanInfo body(Map<String, ?> values) {
		put(target(body, bodyAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret request body values from an existing map. */
	public JPostmanInfo sbody(Map<String, ?> values) {
		put(target(body, bodyAdd), valuesMap(true, values));
		return this;
	}

	/** Adds query string values using key/value pairs. */
	public JPostmanInfo query(Object... values) {
		put(target(query, queryAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret query string values using key/value pairs. */
	public JPostmanInfo squery(Object... values) {
		put(target(query, queryAdd), valuesMap(true, values));
		return this;
	}

	/** Adds query string values from an existing map. */
	public JPostmanInfo query(Map<String, ?> values) {
		put(target(query, queryAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret query string values from an existing map. */
	public JPostmanInfo squery(Map<String, ?> values) {
		put(target(query, queryAdd), valuesMap(true, values));
		return this;
	}

	/**
	 * Adds request headers for the current JPostman execution.
	 *
	 * <p>
	 * This is the recommended portable way to apply authentication headers when the
	 * selected executor does not provide special handling for
	 * {@link #auth(Object...)}.
	 * </p>
	 *
	 * <pre>
	 * info.headers("Authorization", "Bearer " + ctx.cache("token"));
	 * info.headers("x-api-key", apiKey);
	 * </pre>
	 *
	 * @param values alternating header names and values
	 * @return this {@link JPostmanInfo} instance
	 */
	public JPostmanInfo headers(Object... values) {
		put(target(headers, headersAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret request headers using key/value pairs. */
	public JPostmanInfo sheaders(Object... values) {
		put(target(headers, headersAdd), valuesMap(true, values));
		return this;
	}

	/** Adds request header values from an existing map. */
	public JPostmanInfo headers(Map<String, ?> values) {
		put(target(headers, headersAdd), valuesMap(false, values));
		return this;
	}

	/** Adds secret request header values from an existing map. */
	public JPostmanInfo sheaders(Map<String, ?> values) {
		put(target(headers, headersAdd), valuesMap(true, values));
		return this;
	}

	/** Adds request path variable values using key/value pairs. */
	public JPostmanInfo path(Object... values) {
		consumeAddMode();
		path.putAll(valuesMap(false, values));
		return this;
	}

	/** Adds secret request path variable values using key/value pairs. */
	public JPostmanInfo spath(Object... values) {
		consumeAddMode();
		path.putAll(valuesMap(true, values));
		return this;
	}

	/** Adds request path variable values from an existing map. */
	public JPostmanInfo path(Map<String, ?> values) {
		consumeAddMode();
		put(path, valuesMap(false, values));
		return this;
	}

	/** Adds secret request path variable values from an existing map. */
	public JPostmanInfo spath(Map<String, ?> values) {
		consumeAddMode();
		put(path, valuesMap(true, values));
		return this;
	}

	/**
	 * Adds authentication values for the current JPostman execution.
	 *
	 * <p>
	 * This method is intended for request-level authentication metadata that can be
	 * applied before the executor runs. The exact behavior depends on the executor
	 * implementation. For example, an executor may translate these values into an
	 * {@code Authorization} header, RestAssured authentication, or another
	 * framework-specific authentication mechanism.
	 * </p>
	 *
	 * <p>
	 * Common examples:
	 * </p>
	 *
	 * <pre>
	 * info.auth("oauth2", token);
	 * info.auth("bearer", token);
	 * info.auth("basic", username, password);
	 * info.auth("apiKey", "x-api-key", apiKey);
	 * </pre>
	 *
	 * <p>
	 * When using a generic executor that does not interpret {@code auth(...)}
	 * directly, prefer {@link #headers(Object...)} for portable behavior:
	 * </p>
	 *
	 * <pre>
	 * info.headers("Authorization", "Bearer " + token);
	 * </pre>
	 *
	 * @param values authentication key/value data for the current request
	 * @return this {@link JPostmanInfo} instance
	 */
	public JPostmanInfo auth(Object... values) {
		consumeAddMode();
		auth.putAll(valuesMap(false, values));
		return this;
	}

	/** Adds secret authentication values using key/value pairs. */
	public JPostmanInfo sauth(Object... values) {
		consumeAddMode();
		auth.putAll(valuesMap(true, values));
		return this;
	}

	/** Adds auth-related request values from an existing map when supported. */
	public JPostmanInfo auth(Map<String, ?> values) {
		consumeAddMode();
		put(auth, valuesMap(false, values));
		return this;
	}

	/**
	 * Adds secret auth-related request values from an existing map when supported.
	 */
	public JPostmanInfo sauth(Map<String, ?> values) {
		consumeAddMode();
		put(auth, valuesMap(true, values));
		return this;
	}

	/** Returns true when request customization values were added. */
	public boolean hasRequestValues() {
		return !body.isEmpty() || !query.isEmpty() || !headers.isEmpty() || !bodyAdd.isEmpty() || !queryAdd.isEmpty()
				|| !headersAdd.isEmpty() || !path.isEmpty() || !auth.isEmpty();
	}

	private Map<String, Object> target(Map<String, Object> normal, Map<String, Object> add) {
		return consumeAddMode() ? add : normal;
	}

	private boolean consumeAddMode() {
		boolean result = addNext;
		addNext = false;
		return result;
	}

	private static void put(Map<String, Object> target, Map<String, ?> values) {
		if (target != null && values != null) {
			target.putAll(values);
		}
	}

	private static Map<String, Object> valuesMap(boolean secret, Object... values) {
		return valuesMap(secret, Params.asMap(values));
	}

	private static Map<String, Object> valuesMap(boolean secret, Map<String, ?> values) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (values == null || values.isEmpty()) {
			return result;
		}
		for (Map.Entry<String, ?> entry : values.entrySet()) {
			if (entry.getKey() != null) {
				result.put(entry.getKey(), secret ? serverValue(entry.getValue()) : entry.getValue());
			}
		}
		return result;
	}

	private static Object serverValue(Object value) {
		if (value == null || isServerValue(value)) {
			return value;
		}

		for (String type : new String[] { "io.jpostman.ServerValue", "io.jpostman.SecretValue",
				"io.jpostman.SecureValue" }) {
			Object wrapped = serverValue(type, value);
			if (wrapped != value) {
				return wrapped;
			}
		}
		return new SecretRuntimeValue(value);
	}

	private static boolean isServerValue(Object value) {
		if (value instanceof SecretRuntimeValue) {
			return true;
		}
		String name = value.getClass().getName();
		return name.equals("io.jpostman.ServerValue") || name.equals("io.jpostman.SecretValue")
				|| name.equals("io.jpostman.SecureValue");
	}

	/**
	 * Returns true when the supplied value was added through a secret JPostmanInfo
	 * method.
	 */
	public static boolean isSecretValue(Object value) {
		return value != null && isServerValue(value);
	}

	/** Returns the real value stored behind a secret JPostmanInfo value wrapper. */
	public static Object reveal(Object value) {
		if (value instanceof SecretRuntimeValue) {
			return ((SecretRuntimeValue) value).reveal();
		}
		return value;
	}

	private static final class SecretRuntimeValue {
		private final Object value;

		private SecretRuntimeValue(Object value) {
			this.value = value;
		}

		private Object reveal() {
			return value;
		}

		@Override
		public String toString() {
			return String.valueOf(value);
		}
	}

	private static Object serverValue(String type, Object value) {
		try {
			Class<?> clazz = Class.forName(type);
			for (String method : new String[] { "of", "value", "secret", "secure" }) {
				Object wrapped = invokeServerValueFactory(clazz, method, Object.class, value);
				if (wrapped != value) {
					return wrapped;
				}
				wrapped = invokeServerValueFactory(clazz, method, String.class, String.valueOf(value));
				if (wrapped != value) {
					return wrapped;
				}
			}
			try {
				return clazz.getConstructor(Object.class).newInstance(value);
			} catch (ReflectiveOperationException ignored) {
				return clazz.getConstructor(String.class).newInstance(String.valueOf(value));
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return value;
		}
	}

	private static Object invokeServerValueFactory(Class<?> clazz, String method, Class<?> parameterType,
			Object value) {
		try {
			return clazz.getMethod(method, parameterType).invoke(null, value);
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			return value;
		}
	}

	private static Map<String, Object> masked(Map<String, Object> values, boolean sensitiveSection) {
		Map<String, Object> result = new LinkedHashMap<>();
		if (values == null || values.isEmpty()) {
			return result;
		}
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			result.put(key, shouldMask(key, value, sensitiveSection) ? "********" : value);
		}
		return result;
	}

	private static boolean shouldMask(String key, Object value, boolean sensitiveSection) {
		if (value != null && isServerValue(value)) {
			return true;
		}
		String name = key == null ? "" : key.toLowerCase();
		return sensitiveSection || name.contains("authorization") || name.contains("token") || name.contains("secret")
				|| name.contains("password") || name.contains("apikey") || name.contains("api-key")
				|| name.equals("oauth2") || name.equals("bearer");
	}

	/**
	 * Returns raw values that were added through sbody/squery/sheaders/spath/sauth.
	 */
	public Map<String, Object> secretValues() {
		Map<String, Object> result = new LinkedHashMap<>();
		collectSecrets(result, body);
		collectSecrets(result, query);
		collectSecrets(result, headers);
		collectSecrets(result, bodyAdd);
		collectSecrets(result, queryAdd);
		collectSecrets(result, headersAdd);
		collectSecrets(result, path);
		collectSecrets(result, auth);
		return result;
	}

	/** Returns header names whose values were added through sheaders(...). */
	public String[] secretHeaders() {
		List<String> result = new ArrayList<>();
		collectSecretHeaders(result, headers);
		collectSecretHeaders(result, headersAdd);
		return result.toArray(new String[0]);
	}

	private static void collectSecretHeaders(List<String> result, Map<String, Object> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		values.forEach((key, value) -> {
			if (key != null && isSecretValue(value) && !result.contains(key)) {
				result.add(key);
			}
		});
	}

	private static void collectSecrets(Map<String, Object> result, Map<String, Object> values) {
		if (values == null || values.isEmpty()) {
			return;
		}
		values.forEach((key, value) -> {
			if (key != null && isSecretValue(value)) {
				result.put(key, reveal(value));
			}
		});
	}

	/**
	 * Builds a readable multi-line log message with full current invocation values.
	 *
	 * @return formatted runtime info
	 */
	public String log() {
		return log(true);
	}

	/**
	 * Builds a readable multi-line log message with the current invocation values.
	 *
	 * @param includeAll {@code true} to include method chain and timestamps
	 * @return formatted runtime info
	 */
	public String log(boolean includeAll) {
		StringBuilder builder = new StringBuilder();

		builder.append("JPostmanInfo {");
		append(builder, "annotation", annotation);
		append(builder, "id", id);
		if (tags.length > 0)
			builder.append("\n  tags=").append(Arrays.toString(tags));
		append(builder, "method", method);
		if (includeAll) {
			if (methodIndex >= 0)
				builder.append("\n  methodIndex=").append(methodIndex);
			builder.append("\n  methods=").append(methods);
		}
		append(builder, "namespace", namespace);
		concatenate(builder, "folder", folder);
		concatenate(builder, "request", request);
		concatenate(builder, "executor", executor);
		append(builder, "cache", cache);
		append(builder, "data", data);
		if (body != null && body.size() > 0)
			builder.append("\n  body=").append(masked(body, false));
		if (query != null && query.size() > 0)
			builder.append("\n  query=").append(masked(query, false));
		if (headers != null && headers.size() > 0)
			builder.append("\n  headers=").append(masked(headers, true));
		if (bodyAdd != null && bodyAdd.size() > 0)
			builder.append("\n  bodyAdd=").append(masked(bodyAdd, false));
		if (queryAdd != null && queryAdd.size() > 0)
			builder.append("\n  queryAdd=").append(masked(queryAdd, false));
		if (headersAdd != null && headersAdd.size() > 0)
			builder.append("\n  headersAdd=").append(masked(headersAdd, true));
		if (path != null && path.size() > 0)
			builder.append("\n  path=").append(masked(path, false));
		if (auth != null && auth.size() > 0)
			builder.append("\n  auth=").append(masked(auth, true));
		if (includeAll) {
			builder.append("\n  created=").append(date(created));
			append(builder, "started", date(started));
			append(builder, "ended", date(ended));
		}
		if (duration > 0)
			builder.append("\n  duration=").append(JPostmanInfo.formatDuration(duration, false));
		builder.append("\n}");

		return builder.toString();
	}

	private static void concatenate(StringBuilder builder, String key, String value) {
		if (!isBlank(value)) {
			builder.append(", ").append(key).append("=").append(value);
		}
	}

	/** Prints {@link #log()} using trace level. */
	public void print() {
		print(true);
	}

	/** Prints {@link #log(boolean)} using trace level. */
	public void print(boolean includeAll) {
		log.trace(log(includeAll));
	}

	public static String formatDuration(long millis, boolean includeHours) {
		if (millis < 0) {
			millis = 0;
		}

		long hours = millis / 3_600_000;
		long minutes = (millis % 3_600_000) / 60_000;
		long totalMinutes = millis / 60_000;
		long seconds = (millis % 60_000) / 1_000;
		long ms = millis % 1_000;

		if (includeHours) {
			return String.format("%02d:%02d:%02d.%03d", hours, minutes, seconds, ms);
		}

		return String.format("%02d:%02d.%03d", totalMinutes, seconds, ms);
	}
}
