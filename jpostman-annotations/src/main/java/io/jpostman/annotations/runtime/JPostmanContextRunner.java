package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.firstNonBlank;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.loadProperties;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.propertyKey;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.propertyOrDefault;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.jpostman.Environment;
import io.jpostman.JPostman;
import io.jpostman.JPostman.Context;
import io.jpostman.Params;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanTestContext;

/**
 * Prepares and injects JPostman core and framework test contexts.
 *
 * @param <C> framework context type
 */
final class JPostmanContextRunner<C> {

	private final JPostmanFramework<C> framework;
	private static final Set<String> REDUNDANT_CONTEXT_WARNINGS = new LinkedHashSet<>();

	private static final ThreadLocal<Map<ContextKey, PreparedContexts<?>>> CURRENT_CONTEXTS = ThreadLocal
			.withInitial(LinkedHashMap::new);

	JPostmanContextRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	PreparedContexts<C> prepare(Object testInstance) throws Exception {
		PreparedContexts<C> previous = currentContexts(testInstance);
		PreparedContexts<C> prepared = new PreparedContexts<>();

		/*
		 * Build @JPostmanContext values first. Plain @JPostmanTestContext fields should
		 * reuse these contexts unless they explicitly configure their own
		 * collection/environment/rules/config.
		 */
		prepareImplicitContexts(testInstance, prepared);
		prepared.missingContextFactory(namespace -> createMissingNamespaceContext(testInstance, prepared, namespace));
		prepareNamedContexts(testInstance, prepared);
		prepareActiveContexts(testInstance, prepared);
		preservePreviousCaches(prepared, previous);

		return prepared;
	}

	void activate(Object testInstance, PreparedContexts<C> contexts) {
		if (testInstance != null && contexts != null) {
			CURRENT_CONTEXTS.get().put(contextKey(testInstance), contexts);
		}
	}

	@SuppressWarnings("unchecked")
	private PreparedContexts<C> currentContexts(Object testInstance) {
		if (testInstance == null) {
			return null;
		}
		PreparedContexts<?> current = CURRENT_CONTEXTS.get().get(contextKey(testInstance));
		return current == null ? null : (PreparedContexts<C>) current;
	}

	private ContextKey contextKey(Object testInstance) {
		return new ContextKey(testInstance, framework.contextType());
	}

	private void preservePreviousCaches(PreparedContexts<C> prepared, PreparedContexts<C> previous) {
		if (prepared == null || previous == null) {
			return;
		}

		for (String namespace : prepared.namespaces()) {
			copyPreviousCache(previous, prepared, namespace);
		}
	}

	private void copyPreviousCache(PreparedContexts<C> previous, PreparedContexts<C> prepared, String namespace) {
		if (previous == null || prepared == null || !previous.contains(namespace) || !prepared.contains(namespace)) {
			return;
		}

		C previousContext = previous.context(namespace);
		PreparedContext<C> preparedContext = prepared.resolve(namespace);
		framework.copyCache(previousContext, preparedContext.context);

		/*
		 * Keep teardown-visible namespace responses across fresh TestNG method
		 * preparations. A later execution in the same namespace will replace this
		 * context through PreparedContexts.update(...).
		 */
		if (hasResponse(previousContext) && !hasResponse(preparedContext.context)) {
			preparedContext.context = previousContext;
		}
	}

	private C copyPreviousCache(PreparedContexts<C> previous, String namespace, C target) {
		if (previous == null || target == null || !previous.contains(namespace)) {
			return target;
		}

		C previousContext = previous.context(namespace);
		framework.copyCache(previousContext, target);
		return hasResponse(previousContext) && !hasResponse(target) ? previousContext : target;
	}

	private void prepareNamedContexts(Object testInstance, PreparedContexts<C> prepared) throws Exception {
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!isTestContextField(field)) {
					continue;
				}

				JPostmanTestContext annotation = JPostmanAnnotations.testContext(field);
				if (isActive(annotation)) {
					continue;
				}

				PreparedContext<C> context;
				if (!hasOwnContextConfig(annotation)) {
					/*
					 * A plain @JPostmanTestContext field is now only a mirror/backward-compatible
					 * handle. It must not create a separate namespace context, because that can
					 * load a different collection/config and shadow the runtime context created
					 * from @JPostmanContext. Let PreparedContexts resolve/create the namespace
					 * using the runtime missing-context factory instead.
					 */
					context = prepared.resolve(annotation.namespace());
					C existing = existingContext(testInstance, field);
					if (existing != null) {
						framework.copyCache(existing, context.context);

						/*
						 * Preserve the old inactive-context behavior. An inactive
						 * 
						 * @JPostmanTestContext is a namespace mirror, not a latest-current mirror. When
						 * the framework prepares a new TestNG method, do not replace an existing mirror
						 * that already contains that namespace's last response with a fresh empty
						 * context. The next execution for the same namespace will still update this
						 * prepared context normally.
						 */
						if (hasResponse(existing)) {
							context.context = existing;
						}
					}
					setTestContextField(testInstance, field, context.context);
					context.addMirror(testInstance, field);
				} else {
					context = createContext(annotation, testInstance.getClass(), field, testInstance,
							existingContext(testInstance, field));
					setTestContextField(testInstance, field, context.context);
					prepared.put(annotation.namespace(), context);
				}
			}
			current = current.getSuperclass();
		}
	}

	private void prepareImplicitContexts(Object testInstance, PreparedContexts<C> prepared) throws Exception {
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = JPostmanAnnotations.context(field);
				if (annotation == null) {
					continue;
				}

				if (!isContextField(field)) {
					throw new IllegalStateException(
							"@JPostman.Context/@JPostmanContext field must be io.jpostman.JPostman.Context, JPostmanRuntime, or JPostman.Runtime: "
									+ field.getName());
				}

				if (prepared.contains(annotation.namespace())) {
					continue;
				}

				PreparedContext<C> context = createContext(annotation, testInstance.getClass());
				prepared.put(annotation.namespace(), context);
			}
			current = current.getSuperclass();
		}
	}

	private void prepareActiveContexts(Object testInstance, PreparedContexts<C> prepared) throws Exception {
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!isTestContextField(field)) {
					continue;
				}

				JPostmanTestContext annotation = JPostmanAnnotations.testContext(field);
				if (!isActive(annotation)) {
					continue;
				}

				String namespace = annotation.namespace();
				PreparedContext<C> source;
				C fieldContext;

				if (!hasOwnContextConfig(annotation)) {
					source = prepared.resolve(namespace);
					fieldContext = source.context;

					C existing = existingContext(testInstance, field);
					if (existing != null) {
						framework.copyCache(existing, source.context);

						/*
						 * active = true is a latest-active mirror, not a namespace mirror. Do not
						 * replace the previous active field with the annotation namespace context just
						 * because that namespace already has a response, such as the cached login
						 * response in the default context. The active mirror must keep the previous
						 * latest active response until the next annotation execution calls
						 * PreparedContexts.update(...).
						 */
						if (hasResponse(existing)) {
							fieldContext = existing;
						}
					}
				} else if (prepared.contains(namespace)) {
					source = prepared.resolve(namespace);
					fieldContext = source.context;
				} else {
					source = createContext(annotation, testInstance.getClass(), field, testInstance,
							existingContext(testInstance, field));
					prepared.put(namespace, source);
					fieldContext = source.context;
				}

				setTestContextField(testInstance, field, fieldContext);
				prepared.addActive(new PreparedContext<>(fieldContext, source.loaded, source.contextAnnotation,
						source.dataloadLocations, source.assertionRules, testInstance, field));
			}
			current = current.getSuperclass();
		}
	}

	private PreparedContext<C> createMissingNamespaceContext(Object testInstance, PreparedContexts<C> prepared,
			String namespace) throws Exception {

		if (prepared.isEmpty()) {
			return null;
		}

		PreparedContexts<C> previous = currentContexts(testInstance);
		PreparedContext<C> source = prepared.contains("") ? prepared.resolve("") : prepared.firstPreparedContext();

		/*
		 * Runtime-only namespaces must behave the same way as the old
		 * 
		 * @JPostmanTestContext(namespace = "...") fields. That means a namespace such
		 * as "product" must first try namespace-specific properties such as
		 * collection.product, environment.product, and rules.product from the same
		 * 
		 * @JPostmanContext config file. If no namespace-specific collection exists, we
		 * fall back to the already-loaded source collection/context.
		 */
		if (source.contextAnnotation != null) {
			PreparedContext<C> created = createNamespaceContext(source.contextAnnotation, testInstance.getClass(),
					namespace, source);
			created.context = copyPreviousCache(previous, namespace, created.context);
			return created;
		}

		C ctx = framework.create();
		if (source.loaded != null) {
			loadEnvironment(ctx, source.loaded.getEnvironment());
		}
		ctx = copyPreviousCache(previous, namespace, ctx);
		return new PreparedContext<>(ctx, source.loaded, source.contextAnnotation, source.dataloadLocations,
				source.assertionRules);
	}

	private boolean hasOwnContextConfig(JPostmanTestContext annotation) {
		return !blank(annotation.collection()) || !blank(annotation.environment()) || !blank(annotation.rules())
				|| explicitConfig(annotation.config());
	}

	private boolean explicitConfig(String config) {
		return !blank(config) && !JPostmanDataLoader.DEFAULT_CONFIG.equals(config.trim());
	}

	private boolean blank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private String collectionPropertyHint(String namespace) {
		String key = propertyKey("collection", namespace);
		return "collection".equals(key) ? key : key + " or collection";
	}

	private String resolveContextLocation(JPostmanContext annotation, String key, String namespace,
			String annotationValue, String propertyValue) {
		boolean hasAnnotationValue = !blank(annotationValue);
		boolean hasPropertyValue = !blank(propertyValue);

		if (hasAnnotationValue && hasPropertyValue) {
			String annotationLocation = annotationValue.trim();
			String propertyLocation = propertyValue.trim();
			String propertyName = propertyKey(key, namespace);

			if (annotationLocation.equals(propertyLocation)) {
				String configLocation = annotation.config();
				String warningKey = configLocation + "|" + namespace + "|" + key + "|" + annotationLocation;

				if (REDUNDANT_CONTEXT_WARNINGS.add(warningKey)) {
					System.err.println(
							JPostmanErrors.message(annotation, "Redundant JPostman " + key + " mapping ignored.",
									"The same field is configured in @JPostmanContext and config properties file.",
									"Using @JPostmanContext value: " + key + "=" + annotationLocation,
									"Ignored config mapping: " + configLocation + " -> " + propertyName + "="
											+ propertyLocation));
				}
			}
		}

		return firstNonBlank(annotationValue, propertyValue);
	}

	private boolean isTestContextField(Field field) {
		return JPostmanAnnotations.hasTestContext(field) && (framework.contextType().isAssignableFrom(field.getType())
				|| io.jpostman.annotations.JPostman.Test.class.isAssignableFrom(field.getType()));
	}

	private boolean isContextField(Field field) {
		return JPostman.Context.class.isAssignableFrom(field.getType())
				|| JPostmanRuntime.class.isAssignableFrom(field.getType())
				|| io.jpostman.annotations.JPostman.Runtime.class.isAssignableFrom(field.getType());
	}

	private boolean isActive(JPostmanTestContext annotation) {
		return annotation.active();
	}

	private C existingContext(Object testInstance, Field field) throws IllegalAccessException {
		field.setAccessible(true);
		Object value = JPostmanTestProxy.unwrap(field.get(testInstance));
		if (value == null || !framework.contextType().isInstance(value)) {
			return null;
		}
		return framework.contextType().cast(value);
	}

	private void setTestContextField(Object testInstance, Field field, C context) throws IllegalAccessException {
		field.setAccessible(true);
		field.set(testInstance, testContextFieldValue(field, context));
	}

	private Object testContextFieldValue(Field field, C context) {
		if (io.jpostman.annotations.JPostman.Test.class.isAssignableFrom(field.getType())) {
			return JPostmanTestProxy.wrap(context);
		}
		return context;
	}

	void injectTestContexts(Object testInstance, PreparedContexts<C> contexts) throws Exception {
		if (contexts == null) {
			return;
		}

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!isTestContextField(field)) {
					continue;
				}

				JPostmanTestContext annotation = JPostmanAnnotations.testContext(field);
				C context;

				if (isActive(annotation)) {
					/*
					 * active = true is a latest-active mirror. Prefer the field value because
					 * PreparedContexts.update(...) changes the active mirror directly during
					 * execution. Fall back to the tracked active context, then to the annotated
					 * namespace only when nothing has run yet.
					 */
					context = existingContext(testInstance, field);
					if (!hasResponse(context)) {
						C activeContext = contexts.activeContext();
						if (hasResponse(activeContext) || context == null) {
							context = activeContext;
						}
					}
					if (context == null) {
						context = contexts.resolve(annotation.namespace()).context;
					}
				} else {
					PreparedContext<C> prepared = contexts.resolve(annotation.namespace());
					context = prepared.context;

					/*
					 * active = false is a namespace mirror. If the injected field already contains
					 * a response for that namespace, keep it and write it back into
					 * PreparedContexts so jpostman.ctx(namespace) sees the same response.
					 */
					C existing = existingContext(testInstance, field);
					if (hasResponse(existing)) {
						framework.copyCache(context, existing);
						prepared.context = existing;
						context = existing;
					}
				}

				setTestContextField(testInstance, field, context);
			}
			current = current.getSuperclass();
		}
	}

	private boolean hasResponse(C context) {
		return context != null && framework.hasResponse(context);
	}

	void injectLoadedContexts(Object testInstance, PreparedContexts<C> contexts) throws Exception {
		Class<?> testClass = testInstance.getClass();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = JPostmanAnnotations.context(field);
				if (annotation == null) {
					continue;
				}

				if (!isContextField(field)) {
					throw new IllegalStateException(
							"@JPostman.Context/@JPostmanContext field must be io.jpostman.JPostman.Context, JPostmanRuntime, or JPostman.Runtime: "
									+ field.getName());
				}

				JPostman.Context loaded;

				if (contexts.contains(annotation.namespace())) {
					loaded = contexts.resolve(annotation.namespace()).loaded;
				} else {
					Properties properties = loadProperties(annotation.config(), testClass, annotation);
					String namespace = annotation.namespace();
					String collectionLocation = firstNonBlank(annotation.collection(),
							propertyOrDefault(properties, "collection", namespace));
					String environmentLocation = firstNonBlank(annotation.environment(),
							property(properties, "environment", namespace));

					if (collectionLocation.isBlank()) {
						throw new IllegalStateException(JPostmanErrors.message(annotation,
								"JPostman collection is required for field " + field.getName() + ".",
								"Configure @JPostmanContext(collection=...) or property "
										+ collectionPropertyHint(namespace) + "."));
					}

					loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);
				}

				field.setAccessible(true);
				Context loggerContext = loggerContext(loaded, testClass);
				if (JPostmanRuntime.class.isAssignableFrom(field.getType())
						|| io.jpostman.annotations.JPostman.Runtime.class.isAssignableFrom(field.getType())) {
					field.set(testInstance, runtime(testInstance, loggerContext, annotation.namespace(), contexts,
							compactTestRuntime(field)));
				} else {
					field.set(testInstance, loggerContext);
				}
			}
			current = current.getSuperclass();
		}
	}

	private JPostmanRuntime<?> runtime(Object testInstance, Context context, String namespace,
			PreparedContexts<C> contexts, boolean compactTestRuntime) {
		if (compactTestRuntime) {
			return new JPostmanRuntime<>(context, namespace,
					name -> JPostmanTestProxy.wrap(activeContexts(testInstance, contexts).context(name)),
					() -> activeContexts(testInstance, contexts).info());
		}
		return new JPostmanRuntime<>(context, namespace, name -> activeContexts(testInstance, contexts).context(name),
				() -> activeContexts(testInstance, contexts).info());
	}

	private boolean compactTestRuntime(Field field) {
		Type type = field.getGenericType();
		if (!(type instanceof ParameterizedType)) {
			return false;
		}
		ParameterizedType parameterized = (ParameterizedType) type;
		Type[] arguments = parameterized.getActualTypeArguments();
		return arguments.length == 1 && arguments[0] == io.jpostman.annotations.JPostman.Test.class;
	}

	private PreparedContexts<C> activeContexts(Object testInstance, PreparedContexts<C> fallback) {
		PreparedContexts<C> active = currentContexts(testInstance);
		return active == null ? fallback : active;
	}

	private static final class ContextKey {
		private final Object owner;
		private final Class<?> contextType;

		private ContextKey(Object owner, Class<?> contextType) {
			this.owner = owner;
			this.contextType = contextType;
		}

		@Override
		public int hashCode() {
			return 31 * System.identityHashCode(owner) + (contextType == null ? 0 : contextType.hashCode());
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof ContextKey)) {
				return false;
			}
			ContextKey other = (ContextKey) obj;
			return owner == other.owner && contextType == other.contextType;
		}
	}

	private Context loggerContext(Context context, Class<?> testClass) throws Exception {
		try {
			Method logger = Context.class.getMethod("logger", Class.class);
			return (Context) logger.invoke(context, testClass);
		} catch (NoSuchMethodException e) {
			return context;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
	}

	private PreparedContext<C> createContext(JPostmanContext annotation, Class<?> testClass) throws Exception {

		Properties properties = loadProperties(annotation.config(), testClass, annotation);

		String namespace = annotation.namespace();
		String collectionLocation = resolveContextLocation(annotation, "collection", namespace, annotation.collection(),
				propertyOrDefault(properties, "collection", namespace));
		String environmentLocation = resolveContextLocation(annotation, "environment", namespace,
				annotation.environment(), property(properties, "environment", namespace));
		String rulesLocation = resolveContextLocation(annotation, "rules", namespace, annotation.rules(),
				property(properties, "rules", namespace));
		List<String> dataloadLocations = JPostmanDataLoader.resolveLocations(annotation.dataload(), properties,
				namespace, annotation.config(), annotation);
		List<String> assertionLocations = JPostmanAssertionRunner.resolveLocations(annotation.assertions(), properties,
				namespace, annotation.config(), annotation);

		if (collectionLocation.isBlank()) {
			throw new IllegalStateException(JPostmanErrors.message(annotation,
					"JPostman collection is required for @JPostmanContext.",
					"Configure @JPostmanContext(collection = ...), or provide a valid config file with property collection."));
		}

		validateDefaultExecutor(annotation);

		Context loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);

		C ctx = framework.create();
		loadEnvironment(ctx, loaded.getEnvironment());

		if (!rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		return new PreparedContext<>(ctx, loaded, annotation, dataloadLocations,
				JPostmanAssertionRunner.loadAssertionRules(testClass, assertionLocations));
	}

	private void validateDefaultExecutor(JPostmanContext annotation) {
		/*
		 * Compact @JPostman.Context supports executor as a class-name string. Force
		 * resolution during setup so invalid values fail with the standard formatted
		 * context error before a test method starts running.
		 */
		annotation.executor();
	}

	private PreparedContext<C> createNamespaceContext(JPostmanContext annotation, Class<?> testClass, String namespace,
			PreparedContext<C> fallback) throws Exception {

		Properties properties = loadProperties(annotation.config(), testClass, annotation);

		String key = namespace == null ? "" : namespace;
		String collectionLocation = property(properties, "collection", key);
		String environmentLocation = property(properties, "environment", key);
		String rulesLocation = property(properties, "rules", key);
		List<String> dataloadLocations = JPostmanDataLoader.resolveLocations(annotation.dataload(), properties, key,
				annotation.config(), annotation);
		List<String> assertionLocations = JPostmanAssertionRunner.resolveLocations(annotation.assertions(), properties,
				key, annotation.config(), annotation);

		/*
		 * For the annotation's own namespace, explicit annotation attributes still win.
		 * For other runtime namespaces, namespace-specific properties win and blank
		 * values fall back to the already-loaded source context.
		 */
		if (key.equals(annotation.namespace())) {
			collectionLocation = firstNonBlank(annotation.collection(), collectionLocation);
			environmentLocation = firstNonBlank(annotation.environment(), environmentLocation);
			rulesLocation = firstNonBlank(annotation.rules(), rulesLocation);
		}

		Context loaded = fallback == null ? null : fallback.loaded;
		if (!collectionLocation.isBlank()) {
			loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);
		}

		C ctx = framework.create();
		if (loaded != null) {
			loadEnvironment(ctx, loaded.getEnvironment());
		}

		if (!rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		return new PreparedContext<>(ctx, loaded, annotation, dataloadLocations,
				JPostmanAssertionRunner.loadAssertionRules(testClass, assertionLocations));
	}

	private PreparedContext<C> createContext(JPostmanTestContext annotation, Class<?> testClass, Field field,
			Object testInstance, C existingContext) throws Exception {

		Properties properties = loadProperties(annotation.config(), testClass, annotation);

		String namespace = annotation.namespace();
		String collectionLocation = firstNonBlank(annotation.collection(),
				propertyOrDefault(properties, "collection", namespace));
		String environmentLocation = firstNonBlank(annotation.environment(),
				property(properties, "environment", namespace));
		String rulesLocation = firstNonBlank(annotation.rules(), property(properties, "rules", namespace));
		List<String> assertionLocations = JPostmanAssertionRunner.resolveLocations(new String[0], properties, namespace,
				annotation.config(), null);

		if (collectionLocation.isBlank()) {
			throw new IllegalStateException(JPostmanErrors.message(annotation,
					"JPostman collection is required for field " + field.getName() + ".",
					"Configure @JPostmanTestContext(collection=...) or property " + collectionPropertyHint(namespace)
							+ "."));
		}

		Context loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);

		C ctx = framework.create();
		loadEnvironment(ctx, loaded.getEnvironment());

		if (!rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		if (existingContext != null) {
			framework.copyCache(existingContext, ctx);
		}

		return new PreparedContext<>(ctx, loaded, null, java.util.Collections.emptyList(),
				JPostmanAssertionRunner.loadAssertionRules(testClass, assertionLocations), testInstance, field);
	}

	private void loadEnvironment(C ctx, Environment environment) {
		if (environment == null) {
			return;
		}

		/*
		 * Keep the original Postman Environment on the secure context. This is the same
		 * setup users normally write manually with:
		 *
		 * TestNgContext.create().secret(loaded.getEnvironment())
		 *
		 * Request resolution/building depends on this object being available. Without
		 * it, placeholders such as {{base_url}}, {{username}}, and {{password}} can
		 * remain unresolved when an executor runs.
		 */
		framework.secret(ctx, environment);

		/*
		 * Also expose individual values as plain/secret entries so annotations, data,
		 * and helper methods can resolve them directly from the framework context.
		 */
		environment.getParams().keySet().forEach(key -> {
			Params.Entry entry = environment.entry(key);

			if (entry.isEnabled()) {
				framework.plain(ctx, key, entry.getValue());
			} else {
				framework.secret(ctx, key, entry.getValue());
			}
		});
	}

	private Context loadJPostmanContext(String collectionLocation, String environmentLocation, Class<?> testClass)
			throws Exception {

		try (InputStream collection = open(collectionLocation, testClass)) {
			if (environmentLocation.isBlank()) {
				return loadCollectionOnly(collection);
			}

			try (InputStream environment = open(environmentLocation, testClass)) {
				return JPostman.load(collection, environment);
			}
		}
	}

	private Context loadCollectionOnly(InputStream collection) throws Exception {
		try {
			Method load = JPostman.class.getMethod("load", InputStream.class);
			return (Context) load.invoke(null, collection);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(
					"jpostman.environment is optional only when JPostman.load(InputStream) is available. "
							+ "Configure environment or add collection-only load support.",
					e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
	}
}
