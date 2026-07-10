package io.jpostman.codegen.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Framework-neutral method specification used to render JPostman source code.
 */
public final class JPostmanMethodSpec {

	/** Current version of the code-generation model contract. */
	public static final String CURRENT_VERSION = "1.0.0";

	private final String version;
	private final JPostmanAnnotationType type;
	private final String methodName;
	private final String id;
	private final List<String> tags;
	private final String namespace;
	private final String folder;
	private final String request;
	private final String rule;
	private final List<String> filter;
	private final List<String> dependsOn;
	private final List<String> include;
	private final List<String> exclude;
	private final Integer verify;
	private final String executor;
	private final String cache;
	private final String log;
	private final Boolean soft;
	private final Boolean lifecycle;
	private final String data;
	private final List<String> asserts;
	private final Boolean enabled;
	private final Boolean skip;

	private JPostmanMethodSpec(Builder builder) {
		this.version = CURRENT_VERSION;
		this.type = Objects.requireNonNull(builder.type, "type");
		this.methodName = requireMethodName(builder.methodName);
		this.id = trimToNull(builder.id);
		this.tags = copy(builder.tags);
		this.namespace = trimToNull(builder.namespace);
		this.folder = trimToNull(builder.folder);
		this.request = trimToNull(builder.request);
		this.rule = trimToNull(builder.rule);
		this.filter = copy(builder.filter);
		this.dependsOn = copy(builder.dependsOn);
		this.include = copy(builder.include);
		this.exclude = copy(builder.exclude);
		this.verify = builder.verify;
		this.executor = trimToNull(builder.executor);
		this.cache = builder.cache;
		this.log = trimToNull(builder.log);
		this.soft = builder.soft;
		this.lifecycle = builder.lifecycle;
		this.data = trimToNull(builder.data);
		this.asserts = copy(builder.asserts);
		this.enabled = builder.enabled;
		this.skip = builder.skip;
	}

	/**
	 * Returns the code-generation model version.
	 */
	public String getVersion() {
		return version;
	}

	public JPostmanAnnotationType getType() {
		return type;
	}

	public String getMethodName() {
		return methodName;
	}

	public String getId() {
		return id;
	}

	public List<String> getTags() {
		return tags;
	}

	public String getNamespace() {
		return namespace;
	}

	public String getFolder() {
		return folder;
	}

	public String getRequest() {
		return request;
	}

	public String getRule() {
		return rule;
	}

	public List<String> getFilter() {
		return filter;
	}

	public List<String> getDependsOn() {
		return dependsOn;
	}

	public List<String> getInclude() {
		return include;
	}

	public List<String> getExclude() {
		return exclude;
	}

	public Integer getVerify() {
		return verify;
	}

	public String getExecutor() {
		return executor;
	}

	public String getCache() {
		return cache;
	}

	public String getLog() {
		return log;
	}

	public Boolean getSoft() {
		return soft;
	}

	public Boolean getLifecycle() {
		return lifecycle;
	}

	public String getData() {
		return data;
	}

	public List<String> getAsserts() {
		return asserts;
	}

	public Boolean getEnabled() {
		return enabled;
	}

	public Boolean getSkip() {
		return skip;
	}

	public static Builder builder(JPostmanAnnotationType type) {
		return new Builder(type);
	}

	private static List<String> copy(List<String> values) {
		if (values == null || values.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> result = new ArrayList<>();
		for (String value : values) {
			String normalized = trimToNull(value);
			if (normalized != null) {
				result.add(normalized);
			}
		}
		return Collections.unmodifiableList(result);
	}

	private static String requireMethodName(String value) {
		String method = trimToNull(value);
		if (method == null) {
			throw new IllegalArgumentException("Method name is required. Use --method <name>.");
		}
		if (!method.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
			throw new IllegalArgumentException("Invalid Java method name: " + method);
		}
		return method;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	public static final class Builder {
		private final JPostmanAnnotationType type;
		private String methodName;
		private String id;
		private final List<String> tags = new ArrayList<>();
		private String namespace;
		private String folder;
		private String request;
		private String rule;
		private final List<String> filter = new ArrayList<>();
		private final List<String> dependsOn = new ArrayList<>();
		private final List<String> include = new ArrayList<>();
		private final List<String> exclude = new ArrayList<>();
		private Integer verify;
		private String executor;
		private String cache;
		private String log;
		private Boolean soft;
		private Boolean lifecycle;
		private String data;
		private final List<String> asserts = new ArrayList<>();
		private Boolean enabled;
		private Boolean skip;

		private Builder(JPostmanAnnotationType type) {
			this.type = Objects.requireNonNull(type, "type");
		}

		public Builder method(String methodName) {
			this.methodName = methodName;
			return this;
		}

		public Builder id(String id) {
			this.id = id;
			return this;
		}

		public Builder tags(String... tags) {
			addAll(this.tags, tags);
			return this;
		}

		public Builder namespace(String namespace) {
			this.namespace = namespace;
			return this;
		}

		public Builder folder(String folder) {
			this.folder = folder;
			return this;
		}

		public Builder request(String request) {
			this.request = request;
			return this;
		}

		public Builder rule(String rule) {
			this.rule = rule;
			return this;
		}

		public Builder filter(String... filter) {
			addAll(this.filter, filter);
			return this;
		}

		public Builder dependsOn(String... dependsOn) {
			addAll(this.dependsOn, dependsOn);
			return this;
		}

		public Builder include(String... include) {
			addAll(this.include, include);
			return this;
		}

		public Builder exclude(String... exclude) {
			addAll(this.exclude, exclude);
			return this;
		}

		public Builder verify(Integer verify) {
			this.verify = verify;
			return this;
		}

		public Builder executor(String executor) {
			this.executor = executor;
			return this;
		}

		public Builder cache(String cache) {
			this.cache = cache;
			return this;
		}

		public Builder log(String log) {
			this.log = log;
			return this;
		}

		public Builder soft(Boolean soft) {
			this.soft = soft;
			return this;
		}

		public Builder lifecycle(Boolean lifecycle) {
			this.lifecycle = lifecycle;
			return this;
		}

		public Builder data(String data) {
			this.data = data;
			return this;
		}

		public Builder asserts(String... asserts) {
			addAll(this.asserts, asserts);
			return this;
		}

		public Builder enabled(Boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder skip(Boolean skip) {
			this.skip = skip;
			return this;
		}

		public JPostmanMethodSpec build() {
			return new JPostmanMethodSpec(this);
		}

		private static void addAll(List<String> target, String... values) {
			if (values == null) {
				return;
			}
			for (String value : values) {
				if (value == null) {
					continue;
				}
				String[] split = value.split(",");
				for (String item : split) {
					String normalized = trimToNull(item);
					if (normalized != null) {
						target.add(normalized);
					}
				}
			}
		}
	}
}
