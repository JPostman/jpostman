package io.jpostman.codegen.builder;

import io.jpostman.codegen.model.JPostmanAnnotationType;
import io.jpostman.codegen.model.JPostmanMethodSpec;
import io.jpostman.codegen.render.JavaTestMethodRenderer;

/**
 * Fluent builder API for generating JPostman annotation source snippets.
 */
public final class JPostmanSourceBuilder {

	private JPostmanSourceBuilder() {
	}

	public static MethodBuilder runner() {
		return new MethodBuilder(JPostmanAnnotationType.RUNNER);
	}

	public static MethodBuilder request() {
		return new MethodBuilder(JPostmanAnnotationType.REQUEST);
	}

	public static MethodBuilder response() {
		return new MethodBuilder(JPostmanAnnotationType.RESPONSE);
	}

	public static final class MethodBuilder {
		private final JPostmanMethodSpec.Builder delegate;

		private MethodBuilder(JPostmanAnnotationType type) {
			this.delegate = JPostmanMethodSpec.builder(type);
		}

		public MethodBuilder method(String methodName) {
			delegate.method(methodName);
			return this;
		}

		public MethodBuilder id(String id) {
			delegate.id(id);
			return this;
		}

		public MethodBuilder tags(String... tags) {
			delegate.tags(tags);
			return this;
		}

		public MethodBuilder namespace(String namespace) {
			delegate.namespace(namespace);
			return this;
		}

		public MethodBuilder folder(String folder) {
			delegate.folder(folder);
			return this;
		}

		public MethodBuilder request(String request) {
			delegate.request(request);
			return this;
		}

		public MethodBuilder rule(String rule) {
			delegate.rule(rule);
			return this;
		}

		public MethodBuilder filter(String... filter) {
			delegate.filter(filter);
			return this;
		}

		public MethodBuilder dependsOn(String... dependsOn) {
			delegate.dependsOn(dependsOn);
			return this;
		}

		public MethodBuilder include(String... include) {
			delegate.include(include);
			return this;
		}

		public MethodBuilder exclude(String... exclude) {
			delegate.exclude(exclude);
			return this;
		}

		public MethodBuilder verify(Integer verify) {
			delegate.verify(verify);
			return this;
		}

		public MethodBuilder executor(String executor) {
			delegate.executor(executor);
			return this;
		}

		public MethodBuilder cache(String cache) {
			delegate.cache(cache);
			return this;
		}

		public MethodBuilder log(String log) {
			delegate.log(log);
			return this;
		}

		public MethodBuilder soft(Boolean soft) {
			delegate.soft(soft);
			return this;
		}

		public MethodBuilder lifecycle(Boolean lifecycle) {
			delegate.lifecycle(lifecycle);
			return this;
		}

		public MethodBuilder data(String data) {
			delegate.data(data);
			return this;
		}

		public MethodBuilder asserts(String... asserts) {
			delegate.asserts(asserts);
			return this;
		}

		public MethodBuilder enabled(Boolean enabled) {
			delegate.enabled(enabled);
			return this;
		}

		public MethodBuilder skip(Boolean skip) {
			delegate.skip(skip);
			return this;
		}

		public JPostmanMethodSpec spec() {
			return delegate.build();
		}

		public String build() {
			return JavaTestMethodRenderer.render(spec());
		}
	}
}
