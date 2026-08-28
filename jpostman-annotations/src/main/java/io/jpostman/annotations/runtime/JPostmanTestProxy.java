package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import io.jpostman.Environment;
import io.jpostman.Params;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.JPostmanTestAssertions;
import io.jpostman.annotations.JPostmanTestSoftAssertions;

/** Framework-neutral proxy for TestNG/JUnit contexts used by JPostman.Test. */
final class JPostmanTestProxy implements InvocationHandler {

	private static final String CACHE_ID_ALIAS_PREFIX = "__jpostman_cache_id__";
	private static final ThreadLocal<List<CacheDependency>> CACHE_DEPENDENCIES = new ThreadLocal<>();
	private static final Map<Object, ValueSources> VALUE_SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
	private static final Map<Object, RuntimeValueScope> RUNTIME_VALUE_SCOPES = Collections
			.synchronizedMap(new IdentityHashMap<>());

	static final class CacheDependency {
		final String reference;
		final String cacheKey;

		CacheDependency(String reference, String cacheKey) {
			this.reference = reference == null ? "" : reference.trim();
			this.cacheKey = cacheKey == null ? "" : cacheKey.trim();
		}
	}

	static final class CacheScope implements AutoCloseable {
		private final List<CacheDependency> previous;

		private CacheScope(List<CacheDependency> dependencies) {
			this.previous = CACHE_DEPENDENCIES.get();
			CACHE_DEPENDENCIES.set(dependencies == null ? Collections.emptyList() : List.copyOf(dependencies));
		}

		@Override
		public void close() {
			if (previous == null) {
				CACHE_DEPENDENCIES.remove();
			} else {
				CACHE_DEPENDENCIES.set(previous);
			}
		}
	}

	static CacheScope openCacheScope(List<CacheDependency> dependencies) {
		return new CacheScope(dependencies);
	}

	static String cacheAliasKey(String annotationId) {
		String id = annotationId == null ? "" : annotationId.trim();
		while (id.startsWith("#")) {
			id = id.substring(1).trim();
		}
		return id.isBlank() ? "" : CACHE_ID_ALIAS_PREFIX + id;
	}

	/** Records the original Postman environment used by a framework context. */
	static void registerEnvironment(Object target, Environment environment) {
		if (target == null || environment == null) {
			return;
		}
		ValueSources sources = valueSources(target);
		synchronized (sources) {
			sources.environment.clear();
			sources.environmentProtected.clear();
			for (String key : environment.getParams().keySet()) {
				Params.Entry entry = environment.entry(key);
				if (entry == null) {
					continue;
				}
				sources.environment.put(key, entry.getValue());
				if (!entry.isEnabled()) {
					sources.environmentProtected.add(key);
				}
			}
		}
	}

	/** Test helper and framework-neutral environment registration. */
	static void registerEnvironmentValues(Object target, Map<String, ?> values) {
		if (target == null) {
			return;
		}
		ValueSources sources = valueSources(target);
		synchronized (sources) {
			sources.environment.clear();
			sources.environmentProtected.clear();
			if (values != null) {
				sources.environment.putAll(values);
			}
		}
	}

	/** Carries lookup-source metadata to a copied framework context. */
	static void copyValueSources(Object source, Object target) {
		if (source == null || target == null || source == target) {
			return;
		}
		ValueSources existing;
		synchronized (VALUE_SOURCES) {
			existing = VALUE_SOURCES.get(source);
		}
		if (existing == null) {
			return;
		}
		ValueSources copy = new ValueSources();
		synchronized (existing) {
			copy.environment.putAll(existing.environment);
			copy.environmentProtected.addAll(existing.environmentProtected);
			copy.plain.putAll(existing.plain);
			copy.secret.putAll(existing.secret);
		}
		VALUE_SOURCES.put(target, copy);
	}

	/**
	 * Returns a snapshot of plain values registered through the annotation facade.
	 */
	static Map<String, Object> plainValues(Object target) {
		return persistentValues(target, false);
	}

	/**
	 * Returns a snapshot of secret values registered through the annotation facade.
	 */
	static Map<String, Object> secretValues(Object target) {
		return persistentValues(target, true);
	}

	private static Map<String, Object> persistentValues(Object target, boolean secret) {
		if (target == null) {
			return Collections.emptyMap();
		}

		ValueSources sources;
		synchronized (VALUE_SOURCES) {
			sources = VALUE_SOURCES.get(target);
		}
		if (sources == null) {
			return Collections.emptyMap();
		}

		synchronized (sources) {
			return new LinkedHashMap<>(secret ? sources.secret : sources.plain);
		}
	}

	/** Returns class-runtime plain values owned by the supplied test instance. */
	static Map<String, Object> runtimePlainValues(Object owner) {
		return runtimeValues(owner, false);
	}

	/** Returns class-runtime secret values owned by the supplied test instance. */
	static Map<String, Object> runtimeSecretValues(Object owner) {
		return runtimeValues(owner, true);
	}

	/** Mirrors class-runtime values into a newly prepared framework context. */
	static void registerRuntimeValues(Object target, Map<String, ?> plain, Map<String, ?> secret) {
		if (target == null) {
			return;
		}
		ValueSources sources = valueSources(target);
		synchronized (sources) {
			if (plain != null) {
				plain.forEach((key, value) -> {
					if (key != null) {
						sources.plain.put(key, value);
					}
				});
			}
			if (secret != null) {
				secret.forEach((key, value) -> {
					if (key != null) {
						sources.secret.put(key, value);
					}
				});
			}
		}
	}

	/** Clears class-runtime plain/secret values after a test class completes. */
	static void clearRuntimeValues(Object owner) {
		if (owner == null) {
			return;
		}
		synchronized (RUNTIME_VALUE_SCOPES) {
			RUNTIME_VALUE_SCOPES.remove(owner);
		}
	}

	private static Map<String, Object> runtimeValues(Object owner, boolean secret) {
		if (owner == null) {
			return Collections.emptyMap();
		}
		RuntimeValueScope scope;
		synchronized (RUNTIME_VALUE_SCOPES) {
			scope = RUNTIME_VALUE_SCOPES.get(owner);
		}
		if (scope == null) {
			return Collections.emptyMap();
		}
		synchronized (scope) {
			return new LinkedHashMap<>(secret ? scope.secret : scope.plain);
		}
	}

	private static RuntimeValueScope runtimeScope(Object owner) {
		if (owner == null) {
			return null;
		}
		synchronized (RUNTIME_VALUE_SCOPES) {
			return RUNTIME_VALUE_SCOPES.computeIfAbsent(owner, ignored -> new RuntimeValueScope());
		}
	}

	private static RuntimeValueScope existingRuntimeScope(Object owner) {
		if (owner == null) {
			return null;
		}
		synchronized (RUNTIME_VALUE_SCOPES) {
			return RUNTIME_VALUE_SCOPES.get(owner);
		}
	}

	private final Object target;
	private final Supplier<?> activeContextSupplier;
	private final Supplier<? extends JPostman.Info> responseInfoSupplier;
	private final Object runtimeOwner;

	private JPostmanTestProxy(Object target) {
		this(target, null, null, null);
	}

	private JPostmanTestProxy(Object target, Supplier<?> activeContextSupplier) {
		this(target, activeContextSupplier, null, null);
	}

	private JPostmanTestProxy(Object target, Supplier<?> activeContextSupplier,
			Supplier<? extends JPostman.Info> responseInfoSupplier) {
		this(target, activeContextSupplier, responseInfoSupplier, null);
	}

	private JPostmanTestProxy(Object target, Supplier<?> activeContextSupplier,
			Supplier<? extends JPostman.Info> responseInfoSupplier, Object runtimeOwner) {
		this.target = target;
		this.activeContextSupplier = activeContextSupplier;
		this.responseInfoSupplier = responseInfoSupplier;
		this.runtimeOwner = runtimeOwner;
	}

	static JPostman.Test wrap(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.Test) {
			return (JPostman.Test) target;
		}
		return wrap(target, null);
	}

	static JPostman.Test wrap(Object target, Supplier<?> activeContextSupplier) {
		return wrap(target, activeContextSupplier, null, null);
	}

	static JPostman.Test wrap(Object target, Supplier<?> activeContextSupplier,
			Supplier<? extends JPostman.Info> responseInfoSupplier) {
		return wrap(target, activeContextSupplier, responseInfoSupplier, null);
	}

	static JPostman.Test wrap(Object target, Supplier<?> activeContextSupplier,
			Supplier<? extends JPostman.Info> responseInfoSupplier, Object runtimeOwner) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.Test && Proxy.isProxyClass(target.getClass())) {
			InvocationHandler handler = Proxy.getInvocationHandler(target);
			if (handler instanceof JPostmanTestProxy) {
				JPostmanTestProxy existing = (JPostmanTestProxy) handler;
				Supplier<?> active = activeContextSupplier == null ? existing.activeContextSupplier
						: activeContextSupplier;
				Supplier<? extends JPostman.Info> responseInfo = responseInfoSupplier == null
						? existing.responseInfoSupplier
						: responseInfoSupplier;
				Object owner = runtimeOwner == null ? existing.runtimeOwner : runtimeOwner;
				if (responseInfo == existing.responseInfoSupplier && active == existing.activeContextSupplier
						&& owner == existing.runtimeOwner) {
					return (JPostman.Test) target;
				}
				return proxy(existing.target, active, responseInfo, owner);
			}
		}
		if (target instanceof JPostman.Test && responseInfoSupplier == null && runtimeOwner == null) {
			return (JPostman.Test) target;
		}
		return proxy(target, activeContextSupplier, responseInfoSupplier, runtimeOwner);
	}

	private static JPostman.Test proxy(Object target, Supplier<?> activeContextSupplier,
			Supplier<? extends JPostman.Info> responseInfoSupplier, Object runtimeOwner) {
		return (JPostman.Test) Proxy.newProxyInstance(JPostman.Test.class.getClassLoader(),
				new Class<?>[] { JPostman.Test.class },
				new JPostmanTestProxy(target, activeContextSupplier, responseInfoSupplier, runtimeOwner));
	}

	static Object unwrap(Object value) {
		if (value == null) {
			return null;
		}
		if (!Proxy.isProxyClass(value.getClass())) {
			return value;
		}
		InvocationHandler handler = Proxy.getInvocationHandler(value);
		if (handler instanceof JPostmanTestProxy) {
			return ((JPostmanTestProxy) handler).target;
		}
		return value;
	}

	private static JPostmanTestAssertions wrapAssertions(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostmanTestAssertions) {
			return (JPostmanTestAssertions) target;
		}
		return (JPostmanTestAssertions) Proxy.newProxyInstance(JPostmanTestAssertions.class.getClassLoader(),
				new Class<?>[] { JPostmanTestAssertions.class }, new JPostmanAssertionProxy(target, false));
	}

	private static JPostmanTestSoftAssertions wrapSoftAssertions(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostmanTestSoftAssertions) {
			return (JPostmanTestSoftAssertions) target;
		}
		return (JPostmanTestSoftAssertions) Proxy.newProxyInstance(JPostmanTestSoftAssertions.class.getClassLoader(),
				new Class<?>[] { JPostmanTestSoftAssertions.class }, new JPostmanAssertionProxy(target, true));
	}

	static JPostman.Assert wrapAssert(Supplier<?> activeContextSupplier) {
		return wrapAssert(activeContextSupplier, false);
	}

	static JPostman.Assert wrapAssert(Supplier<?> activeContextSupplier, boolean soft) {
		return wrapAssert(activeContextSupplier, soft, false);
	}

	static JPostman.Assert wrapAssert(Supplier<?> activeContextSupplier, boolean soft, boolean classScopedSoft) {
		return wrapAssert(null, activeContextSupplier, soft, classScopedSoft, classScopedSoft);
	}

	static boolean isAssertProxy(Object value) {
		if (value == null || !Proxy.isProxyClass(value.getClass())) {
			return false;
		}
		InvocationHandler handler = Proxy.getInvocationHandler(value);
		return handler instanceof JPostmanAssertProxy;
	}

	private static JPostman.Assert wrapAssert(Object target, Supplier<?> activeContextSupplier, boolean soft) {
		return wrapAssert(target, activeContextSupplier, soft, false);
	}

	private static JPostman.Assert wrapAssert(Object target, Supplier<?> activeContextSupplier, boolean soft,
			boolean classScopedSoft) {
		return wrapAssert(target, activeContextSupplier, soft, classScopedSoft, classScopedSoft);
	}

	private static JPostman.Assert wrapAssert(Object target, Supplier<?> activeContextSupplier, boolean soft,
			boolean classScopedSoft, boolean classScoped) {
		if (isAssertProxy(target)) {
			return (JPostman.Assert) target;
		}

		/*
		 * Keep assertion objects returned by the underlying context behind the JPostman
		 * facade even when they already implement JPostman.Assert. A raw object
		 * returned from soft() would otherwise bypass Runner request-scoped collection
		 * and class-soft lifecycle rules.
		 */
		return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
				new Class<?>[] { JPostman.Assert.class },
				new JPostmanAssertProxy(target, activeContextSupplier, soft, classScopedSoft, classScoped));
	}

	@Override
	public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		String name = method.getName();
		if ("toString".equals(name) && method.getParameterCount() == 0) {
			return String.valueOf(target);
		}
		if ("hashCode".equals(name) && method.getParameterCount() == 0) {
			return target.hashCode();
		}
		if ("equals".equals(name) && method.getParameterCount() == 1) {
			Object other = args == null || args.length == 0 ? null : unwrap(args[0]);
			return target == other || (target != null && target.equals(other));
		}
		if ("ctx".equals(name) && method.getParameterCount() == 0) {
			Object active = activeContextSupplier == null ? null : activeContextSupplier.get();
			return active == null ? proxy : wrap(active, activeContextSupplier, null, runtimeOwner);
		}
		if ("response".equals(name) && method.getParameterCount() == 1
				&& method.getParameterTypes()[0] == BiConsumer.class) {
			if (responseInfoSupplier == null) {
				throw new IllegalStateException(
						"JPostman.Test.response(...) is available only on the result returned by runtime.call(...).");
			}
			@SuppressWarnings("unchecked")
			BiConsumer<JPostman.Test, JPostman.Info> action = args == null || args.length == 0 ? null
					: (BiConsumer<JPostman.Test, JPostman.Info>) args[0];
			if (action != null) {
				action.accept((JPostman.Test) proxy, responseInfoSupplier.get());
			}
			return proxy;
		}
		if ("print".equals(name) && JPostmanOutputs.isInstalled()) {
			boolean resolve = args != null && args.length == 1 && Boolean.TRUE.equals(args[0]);
			Object printTarget = target;

			// Resolve the latest annotation-prepared context only for print(true).
			// print(false) must continue to use the original proxy target.
			if (resolve && activeContextSupplier != null) {
				Object active = activeContextSupplier.get();
				if (active != null) {
					printTarget = active;
				}
			}

			Method logMethod = findTargetMethod(printTarget, "log", args);
			Object text = invokeTarget(logMethod, printTarget, args);
			JPostmanOutputs.write(text == null ? "" : String.valueOf(text));
			return null;
		}

		if (isGetRead(method, name, args)) {
			Object value = resolveGet(target, (String) args[0], runtimeOwner);

			if (method.getParameterCount() == 2) {
				value = JPostmanCacheValueConverter.convert(value, (Class<?>) args[1]);
			}

			if ("getRef".equals(name)) {
				return new JPostman.Ref<>(value);
			}

			return value;
		}

		if (isCacheRead(method, name, args)) {
			Object value = resolveCacheExpression(target, (String) args[0]);
			if (method.getParameterCount() == 2) {
				return JPostmanCacheValueConverter.convert(value, (Class<?>) args[1]);
			}
			return value;
		}

		Object invocationTarget = target;
		if ("print".equals(name) && args != null && args.length == 1 && Boolean.TRUE.equals(args[0])
				&& activeContextSupplier != null) {
			Object active = activeContextSupplier.get();
			if (active != null) {
				invocationTarget = active;
			}
		}

		Method targetMethod = findTargetMethod(invocationTarget, name, args);
		Object result = invokeTarget(targetMethod, invocationTarget, args);
		recordValueMutation(invocationTarget, method, name, args, runtimeOwner);
		if ("copy".equals(name) && method.getParameterCount() == 0 && result != null) {
			copyValueSources(invocationTarget, result);
		}
		if (("request".equals(name) || "response".equals(name)) && result != null) {
			result = JPostmanOutputProxy.wrap(result, method.getReturnType());
		}
		return adaptContextReturn(proxy, method, result, activeContextSupplier, runtimeOwner);
	}

	private static boolean isGetRead(Method method, String name, Object[] args) {

		if (!"get".equals(name) && !"getRef".equals(name)) {
			return false;
		}

		if (args == null || args.length == 0 || !(args[0] instanceof String)) {
			return false;
		}

		int parameterCount = method.getParameterCount();

		if (parameterCount == 1) {
			return true;
		}

		return parameterCount == 2 && args.length >= 2 && args[1] instanceof Class<?>;
	}

	private static Object resolveGet(Object target, String key, Object runtimeOwner) throws Throwable {
		String expression = key == null ? "" : key.trim();
		if (expression.isBlank()) {
			return null;
		}

		RuntimeValueScope runtimeScope = existingRuntimeScope(runtimeOwner);
		if (runtimeScope != null) {
			synchronized (runtimeScope) {
				if (runtimeScope.secret.containsKey(expression)) {
					return runtimeScope.secret.get(expression);
				}
				if (runtimeScope.plain.containsKey(expression)) {
					return runtimeScope.plain.get(expression);
				}
			}
		}

		ValueSources sources = valueSources(target);
		synchronized (sources) {
			if (sources.secret.containsKey(expression)) {
				return sources.secret.get(expression);
			}
			if (sources.plain.containsKey(expression)) {
				return sources.plain.get(expression);
			}
		}

		/*
		 * Direct framework contexts can be mutated without going through this proxy.
		 * Treat a current secure value as an explicit secret/plain override whenever it
		 * differs from the environment snapshot.
		 */
		ResolvedSecureValue current = readCurrentSecureValue(target, expression);
		EnvironmentValue environment;
		synchronized (sources) {
			environment = sources.environment.containsKey(expression)
					? new EnvironmentValue(sources.environment.get(expression),
							sources.environmentProtected.contains(expression))
					: null;
		}
		if (current.present && (environment == null || !Objects.equals(current.value, environment.value)
				|| current.protectedValue != environment.protectedValue)) {
			return current.value;
		}

		boolean responseReference = responseReferenceParts(expression) != null;
		Object cached = responseReference ? responseReferenceCache(target, expression)
				: resolveCacheExpression(target, expression);
		if (cached != null) {
			return cached;
		}

		if (environment != null) {
			return environment.value;
		}

		/* Preserve values supplied by non-annotation integrations. */
		Method getMethod = findTargetMethod(target, "get", new Object[] { expression });
		Object explicit = invokeTarget(getMethod, target, new Object[] { expression });
		if (explicit != null || !responseReference)
			return explicit;
		return responseReferenceValue(runtimeOwner, expression);
	}

	private static void recordValueMutation(Object target, Method method, String name, Object[] args,
			Object runtimeOwner) {
		if (target == null || method == null) {
			return;
		}
		if ("plain".equals(name) || "secret".equals(name)) {
			Map<String, Object> values = mutationValues(args);
			if (values.isEmpty()) {
				return;
			}
			ValueSources sources = valueSources(target);
			synchronized (sources) {
				Map<String, Object> destination = "secret".equals(name) ? sources.secret : sources.plain;
				destination.putAll(values);
			}

			RuntimeValueScope runtimeScope = runtimeScope(runtimeOwner);
			if (runtimeScope != null) {
				synchronized (runtimeScope) {
					Map<String, Object> destination = "secret".equals(name) ? runtimeScope.secret : runtimeScope.plain;
					destination.putAll(values);
				}
			}
			return;
		}
		if ("unsecret".equals(name)) {
			String[] names = stringArguments(args);
			ValueSources sources = valueSources(target);
			synchronized (sources) {
				for (String key : names) {
					if (sources.secret.containsKey(key)) {
						sources.plain.put(key, sources.secret.remove(key));
					}
				}
			}

			RuntimeValueScope runtimeScope = existingRuntimeScope(runtimeOwner);
			if (runtimeScope != null) {
				synchronized (runtimeScope) {
					for (String key : names) {
						if (runtimeScope.secret.containsKey(key)) {
							runtimeScope.plain.put(key, runtimeScope.secret.remove(key));
						}
					}
				}
			}
		}
	}

	private static Map<String, Object> mutationValues(Object[] args) {
		if (args == null || args.length == 0 || args[0] == null) {
			return Collections.emptyMap();
		}
		Object value = args.length == 1 ? args[0] : args;
		if (value instanceof Map<?, ?>) {
			Map<String, Object> result = new LinkedHashMap<>();
			((Map<?, ?>) value).forEach((key, item) -> {
				if (key != null) {
					result.put(String.valueOf(key), item);
				}
			});
			return result;
		}
		if (value instanceof Environment) {
			Map<String, Object> result = new LinkedHashMap<>();
			Environment environment = (Environment) value;
			environment.getParams().keySet().forEach(key -> result.put(key, environment.entry(key).getValue()));
			return result;
		}
		Object[] pairs = value instanceof Object[] ? (Object[]) value : args;
		Map<String, Object> result = new LinkedHashMap<>();
		for (int index = 0; index + 1 < pairs.length; index += 2) {
			if (pairs[index] != null) {
				result.put(String.valueOf(pairs[index]), pairs[index + 1]);
			}
		}
		return result;
	}

	private static String[] stringArguments(Object[] args) {
		if (args == null || args.length == 0 || args[0] == null) {
			return new String[0];
		}
		Object value = args.length == 1 ? args[0] : args;
		if (value instanceof String[]) {
			return (String[]) value;
		}
		if (value instanceof Object[]) {
			Object[] items = (Object[]) value;
			String[] result = new String[items.length];
			for (int index = 0; index < items.length; index++) {
				result[index] = String.valueOf(items[index]);
			}
			return result;
		}
		return new String[] { String.valueOf(value) };
	}

	private static ResolvedSecureValue readCurrentSecureValue(Object target, String key) {
		try {
			Method valuesMethod = findTargetMethod(target, "values", new Object[0]);
			Object values = invokeTarget(valuesMethod, target, new Object[0]);
			if (values == null) {
				return ResolvedSecureValue.missing();
			}
			Method getMethod = findTargetMethod(values, "get", new Object[] { key });
			Object secureValue = invokeTarget(getMethod, values, new Object[] { key });
			if (secureValue == null) {
				return ResolvedSecureValue.missing();
			}
			Method revealMethod = findTargetMethod(secureValue, "reveal", new Object[0]);
			Object revealed = invokeTarget(revealMethod, secureValue, new Object[0]);
			Method protectedMethod = findTargetMethod(secureValue, "isProtected", new Object[0]);
			Object protectedValue = invokeTarget(protectedMethod, secureValue, new Object[0]);
			return new ResolvedSecureValue(true, revealed, Boolean.TRUE.equals(protectedValue));
		} catch (Throwable ignored) {
			return ResolvedSecureValue.missing();
		}
	}

	private static ValueSources valueSources(Object target) {
		synchronized (VALUE_SOURCES) {
			return VALUE_SOURCES.computeIfAbsent(target, ignored -> new ValueSources());
		}
	}

	private static final class RuntimeValueScope {
		private final Map<String, Object> plain = new LinkedHashMap<>();
		private final Map<String, Object> secret = new LinkedHashMap<>();
		private final Map<String, JPostman.Test> responses = new LinkedHashMap<>();
	}

	/** Discard an older response before this producer executes again. */
	static void clearResponse(Object owner, String reference) {
		RuntimeValueScope scope = existingRuntimeScope(owner);
		if (scope != null) {
			synchronized (scope) {
				scope.responses.remove(responseId(reference));
			}
		}
	}

	/** Store response state separately from user plain/secret/cache values. */
	static void recordResponse(Object owner, String reference, Object context) {
		String id = responseId(reference);
		if (owner == null || id.isBlank())
			return;
		clearResponse(owner, reference);
		JPostman.Test snapshot;
		try {
			snapshot = JPostmanResponseSnapshot.create(context);
		} catch (IllegalStateException unavailable) {
			// Empty/non-readable responses must not break existing execution.
			return;
		}
		if (snapshot == null)
			return;
		RuntimeValueScope scope = runtimeScope(owner);
		synchronized (scope) {
			scope.responses.put(id, snapshot);
		}
	}

	private static String responseId(String reference) {
		String id = reference == null ? "" : reference.trim();
		while (id.startsWith("#"))
			id = id.substring(1);
		return id;
	}

	private static Object responseReferenceCache(Object target, String expression) throws Throwable {
		Object exact = readCache(target, expression);
		if (exact != null)
			return exact;
		String[] parts = responseReferenceParts(expression);
		String id = parts[0];
		Object alias = readCache(target, cacheAliasKey(id));
		Object cached = readCache(target, alias == null ? id : String.valueOf(alias));
		if (cached == null)
			return null;
		String path = parts[1];
		if (path.isBlank())
			return cached;
		// Scalar annotation returns are not full response bodies.
		Object targetValue = unwrap(cached);
		try {
			findTargetMethod(targetValue, "path", new Object[] { path });
		} catch (NoSuchMethodException scalar) {
			return null;
		}
		return pathValue(cached, path);
	}

	private static Object responseReferenceValue(Object owner, String expression) throws Throwable {
		String[] parts = responseReferenceParts(expression);
		String id = parts[0];
		RuntimeValueScope scope = existingRuntimeScope(owner);
		JPostman.Test snapshot = null;
		if (scope != null) {
			synchronized (scope) {
				snapshot = scope.responses.get(id);
			}
		}
		if (snapshot == null)
			throw new IllegalStateException(
					"Response for #" + id + " is unavailable. Declare dependsOn and execute that dependency first.");
		String path = parts[1];
		Object value = snapshot.path(path);
		if (value == null)
			throw new IllegalStateException("Response path not found: " + expression);
		return value;
	}

	private static String[] responseReferenceParts(String expression) {
		int separator = expression.indexOf(':');
		if (separator < 0) {
			if (!expression.startsWith("#"))
				return null;
			separator = expression.indexOf('/');
		} else if (!expression.startsWith("#") && !expression.substring(separator + 1).startsWith("/")) {
			return null;
		}
		if (separator <= 0)
			return null;
		String id = responseId(expression.substring(0, separator));
		if (id.isBlank())
			return null;
		String path = expression.substring(separator + 1);
		while (path.startsWith("/"))
			path = path.substring(1);
		return new String[] { id, path };
	}

	private static final class ValueSources {
		private final Map<String, Object> environment = new LinkedHashMap<>();
		private final Set<String> environmentProtected = new LinkedHashSet<>();
		private final Map<String, Object> plain = new LinkedHashMap<>();
		private final Map<String, Object> secret = new LinkedHashMap<>();
	}

	private static final class EnvironmentValue {
		private final Object value;
		private final boolean protectedValue;

		private EnvironmentValue(Object value, boolean protectedValue) {
			this.value = value;
			this.protectedValue = protectedValue;
		}
	}

	private static final class ResolvedSecureValue {
		private final boolean present;
		private final Object value;
		private final boolean protectedValue;

		private ResolvedSecureValue(boolean present, Object value, boolean protectedValue) {
			this.present = present;
			this.value = value;
			this.protectedValue = protectedValue;
		}

		private static ResolvedSecureValue missing() {
			return new ResolvedSecureValue(false, null, false);
		}
	}

	private static boolean isCacheRead(Method method, String name, Object[] args) {
		if (!"cache".equals(name) || args == null || args.length == 0 || method.getParameterCount() == 0
				|| method.getParameterTypes()[0] != String.class) {
			return false;
		}
		if (method.getParameterCount() == 1) {
			return true;
		}
		return method.getParameterCount() == 2 && method.getParameterTypes()[1] == Class.class;
	}

	private static Object resolveCacheExpression(Object target, String expression) throws Throwable {
		String value = expression == null ? "" : expression.trim();
		if (value.isBlank()) {
			throw new IllegalArgumentException("JPostman cache expression is required.");
		}

		String[] referenceParts = responseReferenceParts(value);
		if (value.startsWith("#") || referenceParts != null) {
			String reference = referenceParts == null ? value : "#" + referenceParts[0];
			String path = referenceParts == null ? "" : referenceParts[1];
			String cacheKey = cacheKeyByAnnotationId(target, reference);
			Object cached = readCache(target, cacheKey);
			if (cached == null) {
				throw new IllegalStateException("Cached dependency value not found for " + reference + ".");
			}
			return path.isBlank() ? cached : pathValue(cached, path);
		}

		int pathSeparator = value.indexOf('/');
		if (pathSeparator > 0) {
			String key = value.substring(0, pathSeparator).trim();
			String path = value.substring(pathSeparator + 1).trim();
			Object cached = readCache(target, key);
			return cached == null || path.isBlank() ? cached : pathValue(cached, path);
		}

		/*
		 * Preserve direct cache reads. A bare expression first means "read this exact
		 * cache key". Only when that key is absent is the same expression interpreted
		 * as a response path on the single cached direct dependency.
		 */
		Object direct = readCache(target, value);
		if (direct != null) {
			return direct;
		}

		List<CacheDependency> configured = CACHE_DEPENDENCIES.get();
		if (configured == null || configured.isEmpty()) {
			return null;
		}

		String cacheKey = inferSingleCachedDependency(target, value);
		if (cacheKey == null) {
			return null;
		}
		Object cached = readCache(target, cacheKey);
		if (cached == null) {
			return null;
		}
		return pathValue(cached, value);
	}

	private static String cacheKeyByAnnotationId(Object target, String reference) throws Throwable {
		String id = reference == null ? "" : reference.trim();
		while (id.startsWith("#")) {
			id = id.substring(1).trim();
		}
		if (id.isBlank()) {
			throw new IllegalArgumentException("JPostman annotation id is missing before ':'.");
		}
		Object alias = readCache(target, cacheAliasKey(id));
		if (alias != null && !String.valueOf(alias).isBlank()) {
			return String.valueOf(alias);
		}
		if (readCache(target, id) != null) {
			return id;
		}
		throw new IllegalStateException(
				"Dependency #" + id + " is not cached. Enable cache on that dependency before reading its response.");
	}

	private static String inferSingleCachedDependency(Object target, String path) throws Throwable {
		List<CacheDependency> configured = CACHE_DEPENDENCIES.get();
		Map<String, CacheDependency> available = new LinkedHashMap<>();
		if (configured != null) {
			for (CacheDependency dependency : configured) {
				if (dependency == null || dependency.cacheKey.isBlank()) {
					continue;
				}
				if (readCache(target, dependency.cacheKey) != null) {
					available.putIfAbsent(dependency.cacheKey, dependency);
				}
			}
		}

		if (available.size() == 1) {
			return available.values().iterator().next().cacheKey;
		}
		if (available.isEmpty()) {
			return null;
		}

		List<String> references = new ArrayList<>();
		for (CacheDependency dependency : available.values()) {
			references.add(dependency.reference.isBlank() ? dependency.cacheKey : dependency.reference);
		}
		CacheDependency first = available.values().iterator().next();
		String suggestion = first.reference.startsWith("#") ? first.reference + ":" + path
				: first.cacheKey + "/" + path;
		throw new IllegalStateException("Cached path \"" + path + "\" is ambiguous. Cached direct dependencies: "
				+ String.join(", ", references) + ". Use an explicit reference such as test.cache(\"" + suggestion
				+ "\").");
	}

	private static Object readCache(Object target, String key) throws Throwable {
		if (key == null || key.isBlank()) {
			return null;
		}
		Method cacheMethod = findTargetMethod(target, "cache", new Object[] { key });
		return invokeTarget(cacheMethod, target, new Object[] { key });
	}

	private static Object pathValue(Object cached, String path) throws Throwable {
		if (cached == null || path == null || path.isBlank()) {
			return cached;
		}
		Object cachedTarget = unwrap(cached);
		Method pathMethod = findTargetMethod(cachedTarget, "path", new Object[] { path });
		Object value = invokeTarget(pathMethod, cachedTarget, new Object[] { path });
		return JPostmanCacheValueConverter.unwrap(value);
	}

	private static final class JPostmanAssertionProxy implements InvocationHandler {

		private final Object target;
		private final boolean soft;

		private JPostmanAssertionProxy(Object target, boolean soft) {
			this.target = target;
			this.soft = soft;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			if ("toString".equals(name) && method.getParameterCount() == 0) {
				return String.valueOf(target);
			}
			if ("hashCode".equals(name) && method.getParameterCount() == 0) {
				return target.hashCode();
			}
			if ("equals".equals(name) && method.getParameterCount() == 1) {
				Object other = args == null || args.length == 0 ? null : unwrap(args[0]);
				return target == other || (target != null && target.equals(other));
			}

			Object[] invocationArgs = optionalAllMatchMessage(method, args);
			Method targetMethod = findTargetMethod(target, name, invocationArgs);
			Object result = invokeTarget(targetMethod, target, invocationArgs);
			return adaptAssertionReturn(proxy, method, result, soft);
		}
	}

	private static final class JPostmanAssertProxy implements InvocationHandler {

		private final Object target;
		private final Supplier<?> activeContextSupplier;
		private final boolean soft;
		private final boolean classScopedSoft;
		private final boolean classScoped;
		private volatile Object lastActiveContext;
		private volatile Object lastAssertionTarget;
		private volatile Method lastClassScopedAssertionMethod;

		private JPostmanAssertProxy(Object target, Supplier<?> activeContextSupplier, boolean soft,
				boolean classScopedSoft, boolean classScoped) {
			this.target = target;
			this.activeContextSupplier = activeContextSupplier;
			this.soft = soft;
			this.classScopedSoft = classScopedSoft;
			this.classScoped = classScoped;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			String name = method.getName();
			if ("toString".equals(name) && method.getParameterCount() == 0) {
				return String.valueOf(assertionTarget());
			}
			if ("hashCode".equals(name) && method.getParameterCount() == 0) {
				Object value = assertionTarget();
				return value == null ? 0 : value.hashCode();
			}
			if ("equals".equals(name) && method.getParameterCount() == 1) {
				Object other = args == null || args.length == 0 ? null : unwrap(args[0]);
				Object value = assertionTarget();
				return value == other || (value != null && value.equals(other));
			}
			if ("fail".equals(name) && method.getParameterCount() == 1) {
				Object message = args == null || args.length == 0 ? null : args[0];
				String text = message == null || String.valueOf(message).isBlank() ? "Assertion failed"
						: String.valueOf(message);
				throw new AssertionError(text);
			}
			if ("soft".equals(name) && method.getParameterCount() == 0) {
				/*
				 * soft() is a temporary method-scoped mode. It must never mutate the injected
				 * hard AssertContext facade. Instead, obtain the exact same collector used by
				 * jpostman.ctx().soft(), wrap it as a separate facade, and register that facade
				 * for method-exit verification.
				 */
				if (soft) {
					JPostman.Assert current = (JPostman.Assert) proxy;
					JPostmanAssertionCleanup.registerExplicitSoft(current);
					return current;
				}
				Object collector = invokeContext("soft", new Object[] { Boolean.FALSE });
				if (collector == null) {
					throw new IllegalStateException("JPostman soft(false) returned no assertion collector");
				}
				JPostman.Assert temporary = wrapAssert(collector, activeContextSupplier, true, false, false);
				JPostmanAssertionCleanup.registerExplicitSoft(temporary);
				return temporary;
			}

			/*
			 * The injected @JPostman.AssertContext facade has no fixed assertion target.
			 * Its verify methods must delegate to the active context rather than first
			 * calling context.asserts(). Calling asserts() would replace an existing soft
			 * collector with a new hard assertion object and reintroduce the default 200
			 * status check. Context.verify(...) flushes the active soft collector and lets
			 * TestNG/JUnit reset it after verification. Runner aggregation keeps its
			 * existing specialized verification path.
			 */
			if (target == null && isVerifyMethod(name) && !soft && classScoped) {
				/* Hard AssertContext assertions fail immediately, so verify() is a no-op. */
				return adaptAssertReturn(proxy, method, null, false);
			}

			if (target == null && isVerifyMethod(name) && soft && classScopedSoft && lastAssertionTarget == null) {
				return adaptAssertReturn(proxy, method, null, true);
			}

			if (target == null && isVerifyMethod(name) && !JPostmanRuntimeRunner.active()) {
				/*
				 * Soft AssertContext verification flushes the assertion object that was
				 * actually used by this facade. If no assertion call occurred, verification is
				 * a no-op.
				 */
				Object assertion = lastAssertionTarget;
				if (assertion == null) {
					return adaptAssertReturn(proxy, method, null, true);
				}
				String verifyMethod = "assertAll".equals(name) ? "verify" : name;
				Method targetMethod = findTargetMethod(assertion, verifyMethod, args);
				Method assertionMethod = lastClassScopedAssertionMethod;
				try {
					Object result = invokeTarget(targetMethod, assertion, args);
					return adaptAssertReturn(proxy, method, result, true);
				} catch (AssertionError error) {
					if (classScopedSoft) {
						throw withAssertionMethod(error, assertionMethod);
					}
					throw error;
				} finally {
					/*
					 * Verification consumes the collector, preventing duplicate auto-verification.
					 */
					if (classScopedSoft) {
						lastAssertionTarget = null;
						lastClassScopedAssertionMethod = null;
					}
				}
			}

			if (soft && !classScopedSoft && isVerifyMethod(name) && JPostmanRuntimeRunner.active()) {
				AssertionError failure = JPostmanRuntimeRunner.softFailure(null);
				if (failure != null) {
					throw failure;
				}
				return adaptAssertReturn(proxy, method, null, soft);
			}

			Object value = assertionTarget();
			Object[] invocationArgs = optionalAllMatchMessage(method, args);
			Method targetMethod = findTargetMethod(value, name, invocationArgs);
			if (!isVerifyMethod(name) && !"soft".equals(name)) {
				JPostmanAssertionCleanup.markCurrentMethod();
				if (classScopedSoft) {
					Method currentMethod = JPostmanAssertionCleanup.currentMethod();
					if (currentMethod != null) {
						lastClassScopedAssertionMethod = currentMethod;
					}
				}
			}
			/*
			 * A soft AssertContext collects every failure until verify() is called. The
			 * annotation lifecycle automatically calls verify() after an eligible response
			 * or runner request.
			 */

			AssertionError localSoftFailure = soft && !classScopedSoft && JPostmanRuntimeRunner.active()
					? localSoftFailure(targetMethod, invocationArgs, value)
					: null;
			if (localSoftFailure != null) {
				JPostmanRuntimeRunner.recordSoftFailure(localSoftFailure);
				return adaptAssertReturn(proxy, method, null, soft);
			}

			Object result;
			try {
				result = invokeTarget(targetMethod, value, invocationArgs);
			} catch (AssertionError e) {
				if (soft && isVerifyMethod(name) && JPostmanRuntimeRunner.active()) {
					throw JPostmanRuntimeRunner.softFailure(e);
				}
				throw e;
			} finally {
				if (classScopedSoft && isVerifyMethod(name)) {
					lastAssertionTarget = null;
					lastClassScopedAssertionMethod = null;
				}
			}
			if (soft && !classScopedSoft && isVerifyMethod(name)) {
				AssertionError failure = JPostmanRuntimeRunner.softFailure(null);
				if (failure != null) {
					throw failure;
				}
			}
			if (soft && !classScopedSoft && JPostmanRuntimeRunner.active()
					&& shouldFailFast(targetMethod, result == null ? value : result)) {
				recordSoftFailure(result == null ? value : result);
			} else if (!soft && shouldFailFast(targetMethod, result == null ? value : result)) {
				verifyNow(result == null ? value : result);
			}
			return adaptAssertReturn(proxy, method, result, soft);
		}

		private AssertionError withAssertionMethod(AssertionError error, Method origin) {
			if (!JPostmanRuntimeRunner.active() || error == null || origin == null || error.getMessage() == null
					|| error.getMessage().isBlank()) {
				return error;
			}

			String prefix = origin.getDeclaringClass().getSimpleName() + "." + origin.getName() + ": ";
			StringBuilder message = new StringBuilder();
			for (String line : error.getMessage().split("\\R", -1)) {
				String trimmed = line.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				if ("The following asserts failed:".equals(trimmed)) {
					message.append(trimmed);
				} else {
					message.append("\n\t").append(prefix).append(trimmed);
				}
			}

			AssertionError enriched = new AssertionError(message.toString());
			enriched.setStackTrace(error.getStackTrace());
			for (Throwable suppressed : error.getSuppressed()) {
				enriched.addSuppressed(suppressed);
			}
			return enriched;
		}

		private Object assertionTarget() throws Throwable {
			if (target != null) {
				lastAssertionTarget = target;
				return target;
			}

			/*
			 * An injected soft AssertContext owns its collector. Keep that collector bound
			 * to this proxy so another injected hard AssertContext cannot replace it by
			 * calling context.asserts(false). Hard facades are intentionally resolved for
			 * every active request so they continue to follow the current response.
			 */
			if (soft && classScopedSoft && lastAssertionTarget != null) {
				return lastAssertionTarget;
			}

			Object assertion = soft ? softAssertionTarget() : invokeContext("asserts", new Object[] { Boolean.FALSE });
			lastAssertionTarget = assertion;
			return assertion;
		}

		private Object softAssertionTarget() throws Throwable {
			/*
			 * The context soft(boolean) method creates/returns the actual soft assertion
			 * collector. Do not call asserts(false) afterwards: that selects the hard
			 * assertion facade again and makes statusCode(...) fail immediately.
			 *
			 * The false argument selects the normal (non-secure) response. Therefore both
			 * of these use the exact same collector:
			 *
			 * @AssertContext(soft = true) and jpostman.ctx().soft()
			 */
			Object assertion = invokeContext("soft", new Object[] { Boolean.FALSE });
			if (assertion == null) {
				throw new IllegalStateException("JPostman soft(false) returned no assertion collector");
			}
			return assertion;
		}

		private Object resolveContext() {
			Object context = activeContextSupplier == null ? null : activeContextSupplier.get();
			if (context != null) {
				lastActiveContext = context;
				return context;
			}
			return lastActiveContext;
		}

		private Object invokeContext(String name, Object[] args) throws Throwable {
			Object context = resolveContext();
			if (context == null) {
				throw new IllegalStateException("No JPostman test context is available for @JPostman.AssertContext. "
						+ "No assertion context was activated before calling " + name + "().");
			}
			return invokeContext(context, name, args);
		}

		private Object invokeContext(Object context, String name, Object[] args) throws Throwable {
			Method targetMethod = findTargetMethod(context, name, args);
			return invokeTarget(targetMethod, context, args);
		}
	}

	/**
	 * The secure TestNG/JUnit assertion implementations currently expose allMatch
	 * overloads with a message parameter. The annotations facade additionally
	 * exposes message-optional overloads. Route those calls to the existing
	 * implementation with a blank message so its standard fallback diagnostic is
	 * used.
	 */
	private static Object[] optionalAllMatchMessage(Method method, Object[] args) {
		if (method == null || !"allMatch".equals(method.getName())) {
			return args;
		}
		Class<?>[] parameterTypes = method.getParameterTypes();
		if (parameterTypes.length == 0 || parameterTypes[parameterTypes.length - 1] == String.class) {
			return args;
		}
		Object[] source = args == null ? new Object[0] : args;
		Object[] expanded = Arrays.copyOf(source, source.length + 1);
		expanded[source.length] = "";
		return expanded;
	}

	private static Object adaptContextReturn(Object proxy, Method method, Object result,
			Supplier<?> activeContextSupplier, Object runtimeOwner) {
		Class<?> returnType = method.getReturnType();
		String name = method.getName();

		if (returnType == Void.TYPE) {
			return null;
		}
		if (returnType == JPostman.Assert.class) {
			JPostman.Assert assertions = wrapAssert(result, activeContextSupplier, "soft".equals(name));
			if ("soft".equals(name)) {
				JPostmanAssertionCleanup.registerExplicitSoft(assertions);
			}
			return assertions;
		}
		if (proxy instanceof JPostman.Test && ("asserts".equals(name) || "soft".equals(name))) {
			JPostman.Assert assertions = wrapAssert(result, activeContextSupplier, "soft".equals(name));
			if ("soft".equals(name)) {
				JPostmanAssertionCleanup.registerExplicitSoft(assertions);
			}
			return assertions;
		}
		if ("asserts".equals(name) || returnType == JPostmanTestAssertions.class || returnsTypeVariable(method, "A")) {
			return wrapAssertions(result);
		}
		if ("soft".equals(name) || returnType == JPostmanTestSoftAssertions.class || returnsTypeVariable(method, "S")) {
			return wrapSoftAssertions(result);
		}
		if (returnType == JPostman.Test.class || returnsTypeVariable(method, "C")) {
			return result == null ? proxy : wrap(result, null, null, runtimeOwner);
		}
		if (result != null && returnType.isInstance(result)) {
			return result;
		}
		return result;
	}

	private static Object adaptAssertionReturn(Object proxy, Method method, Object result, boolean soft) {
		Class<?> returnType = method.getReturnType();
		String name = method.getName();

		if (returnType == Void.TYPE) {
			return null;
		}
		if ("context".equals(name) || "verify".equals(name) || "assertAll".equals(name)
				|| returnType == JPostman.Test.class || returnsTypeVariable(method, "C")) {
			return result == null ? null : wrap(result);
		}
		if (returnType == JPostmanTestAssertions.class || returnType == JPostmanTestSoftAssertions.class
				|| returnsTypeVariable(method, "A")) {
			return soft ? wrapSoftAssertions(result) : wrapAssertions(result);
		}
		if (result != null && returnType.isInstance(result)) {
			return result;
		}
		return result;
	}

	private static Object adaptAssertReturn(Object proxy, Method method, Object result, boolean soft) {
		Class<?> returnType = method.getReturnType();

		if (returnType == Void.TYPE) {
			return null;
		}
		if (returnType == JPostman.Assert.class || returnsTypeVariable(method, "A")) {
			return result == null ? proxy : wrapAssert(result, null, soft);
		}
		if (returnType == JPostman.Test.class || returnsTypeVariable(method, "C")) {
			return result == null ? null : wrap(result);
		}
		if (result != null && returnType.isInstance(result)) {
			return result;
		}
		return result;
	}

	private static AssertionError localSoftFailure(Method method, Object[] args, Object target) {
		if (method == null || args == null || args.length == 0) {
			return null;
		}

		String name = method.getName();
		if ("isTrue".equals(name) && args[0] instanceof Boolean && !((Boolean) args[0]).booleanValue()) {
			return booleanFailure(target, args, true, false);
		}
		if ("isFalse".equals(name) && args[0] instanceof Boolean && ((Boolean) args[0]).booleanValue()) {
			return booleanFailure(target, args, false, true);
		}
		return null;
	}

	private static AssertionError booleanFailure(Object target, Object[] args, boolean expected, boolean actual) {
		String defaultMessage = expected ? "Condition should be true" : "Condition should be false";
		String message = args.length > 1 && args[1] != null && !String.valueOf(args[1]).isBlank()
				? String.valueOf(args[1]).trim()
				: defaultMessage;
		String text = testNgStyle(target) ? message + " expected [" + expected + "] but found [" + actual + "]"
				: message + " ==> expected: <" + expected + "> but was: <" + actual + ">";
		return new AssertionError(text);
	}

	private static boolean testNgStyle(Object target) {
		String name = target == null ? "" : target.getClass().getName().toLowerCase();
		return name.contains("testng");
	}

	private static boolean shouldFailFast(Method method, Object result) {
		String name = method.getName();
		if (isVerifyMethod(name) || "context".equals(name)) {
			return false;
		}
		if (returnsTypeVariable(method, "A")) {
			return true;
		}
		return hasNoArgMethod(result, "verify");
	}

	private static boolean isVerifyMethod(String name) {
		return "verify".equals(name) || "assertAll".equals(name);
	}

	private static void recordSoftFailure(Object target) throws Throwable {
		try {
			verifyNow(target);
		} catch (AssertionError e) {
			JPostmanRuntimeRunner.recordSoftFailure(e);
		}
	}

	private static void verifyNow(Object target) throws Throwable {
		if (target == null) {
			return;
		}
		Method verify = findTargetMethod(target, "verify", null);
		invokeTarget(verify, target, null);
	}

	private static boolean hasNoArgMethod(Object target, String name) {
		if (target == null) {
			return false;
		}
		for (Method method : target.getClass().getMethods()) {
			if (name.equals(method.getName()) && method.getParameterCount() == 0) {
				return true;
			}
		}
		return false;
	}

	private static boolean returnsTypeVariable(Method method, String name) {
		Type type = method.getGenericReturnType();
		return type instanceof TypeVariable<?> && name.equals(((TypeVariable<?>) type).getName());
	}

	private static Object invokeTarget(Method method, Object target, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args == null ? new Object[0] : args);
		} catch (InvocationTargetException e) {
			throw JPostmanAssertionCleanup.clean(e.getTargetException());
		} catch (RuntimeException | Error e) {
			throw JPostmanAssertionCleanup.clean(e);
		}
	}

	private static Method findTargetMethod(Object target, String name, Object[] args) throws NoSuchMethodException {
		int count = args == null ? 0 : args.length;
		for (Method method : target.getClass().getMethods()) {
			if (!name.equals(method.getName()) || method.getParameterCount() != count) {
				continue;
			}
			Class<?>[] types = method.getParameterTypes();
			boolean match = true;
			for (int i = 0; i < count; i++) {
				if (args[i] != null && !box(types[i]).isAssignableFrom(args[i].getClass())) {
					match = false;
					break;
				}
			}
			if (match) {
				method.setAccessible(true);
				return method;
			}
		}
		throw new NoSuchMethodException(target.getClass().getName() + "." + name);
	}

	private static Class<?> box(Class<?> type) {
		if (!type.isPrimitive()) {
			return type;
		}
		if (type == boolean.class) {
			return Boolean.class;
		}
		if (type == int.class) {
			return Integer.class;
		}
		if (type == long.class) {
			return Long.class;
		}
		if (type == double.class) {
			return Double.class;
		}
		if (type == float.class) {
			return Float.class;
		}
		if (type == byte.class) {
			return Byte.class;
		}
		if (type == short.class) {
			return Short.class;
		}
		if (type == char.class) {
			return Character.class;
		}
		return type;
	}
}
