package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.JPostmanTestAssertions;
import io.jpostman.annotations.JPostmanTestSoftAssertions;

/** Framework-neutral proxy for TestNG/JUnit contexts used by JPostman.Test. */
final class JPostmanTestProxy implements InvocationHandler {

	private static final String CACHE_ID_ALIAS_PREFIX = "__jpostman_cache_id__";
	private static final ThreadLocal<List<CacheDependency>> CACHE_DEPENDENCIES = new ThreadLocal<>();

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

	private final Object target;
	private final Supplier<?> activeContextSupplier;

	private JPostmanTestProxy(Object target) {
		this(target, null);
	}

	private JPostmanTestProxy(Object target, Supplier<?> activeContextSupplier) {
		this.target = target;
		this.activeContextSupplier = activeContextSupplier;
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
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.Test) {
			return (JPostman.Test) target;
		}
		return (JPostman.Test) Proxy.newProxyInstance(JPostman.Test.class.getClassLoader(),
				new Class<?>[] { JPostman.Test.class }, new JPostmanTestProxy(target, activeContextSupplier));
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
			return active == null ? proxy : wrap(active, activeContextSupplier);
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
		if (("cache".equals(name) || "get".equals(name)) && args != null && args.length >= 1
				&& args[0] instanceof String) {
			Object value = "get".equals(name) ? resolveGetExpression(target, (String) args[0])
					: resolveCacheExpression(target, (String) args[0]);
			if (method.getParameterCount() == 2 && args.length == 2 && args[1] instanceof Class<?>) {
				return JPostmanCacheValueConverter.convert(value, (Class<?>) args[1]);
			}
			if (method.getParameterCount() == 1) {
				return value;
			}
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
		if (("request".equals(name) || "response".equals(name)) && result != null) {
			result = JPostmanOutputProxy.wrap(result, method.getReturnType());
		}
		return adaptContextReturn(proxy, method, result, activeContextSupplier);
	}

	private static Object resolveGetExpression(Object target, String expression) throws Throwable {
		String value = expression == null ? "" : expression.trim();
		if (value.isBlank()) {
			throw new IllegalArgumentException("JPostman cache expression is required.");
		}

		int separator = value.indexOf(':');
		boolean idOnly = separator < 0 && value.startsWith("#");

		/*
		 * Preserve the pre-4.2.3 direct-key contract. A bare expression such as
		 * get("token") first means "read cache key token". Only when that exact key is
		 * absent does the 4.2.3 dependency-aware shorthand interpret the same
		 * expression as a response path on the single cached direct dependency.
		 */
		if (separator < 0 && !idOnly) {
			Object direct = readCache(target, value);
			if (direct != null) {
				return direct;
			}
		}

		String reference = separator >= 0 ? value.substring(0, separator).trim() : idOnly ? value : "";
		String path = separator >= 0 ? value.substring(separator + 1).trim() : idOnly ? "" : value;
		String cacheKey;

		if (!reference.isBlank()) {
			cacheKey = reference.startsWith("#") ? cacheKeyByAnnotationId(target, reference) : reference;
		} else {
			cacheKey = inferSingleCachedDependency(target, path);
		}

		Object cached = readCache(target, cacheKey);
		if (cached == null) {
			throw new IllegalStateException(
					"Cached dependency value not found for " + (reference.isBlank() ? cacheKey : reference) + ".");
		}
		return path.isBlank() ? cached : pathValue(cached, path);
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
			throw new IllegalStateException("No cached direct dependency is available for path \"" + path + "\".");
		}

		List<String> references = new ArrayList<>();
		for (CacheDependency dependency : available.values()) {
			references.add(dependency.reference.isBlank() ? dependency.cacheKey : dependency.reference);
		}
		throw new IllegalStateException("Cached path \"" + path + "\" is ambiguous. Cached direct dependencies: "
				+ String.join(", ", references) + ". Use an explicit reference such as test.get(\"" + references.get(0)
				+ ":" + path + "\").");
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

	private static Object resolveCacheExpression(Object target, String expression) throws Throwable {
		int separator = expression.indexOf('/');
		String key = separator > 0 ? expression.substring(0, separator) : expression;
		Method cacheMethod = findTargetMethod(target, "cache", new Object[] { key });
		Object cached = invokeTarget(cacheMethod, target, new Object[] { key });
		if (separator <= 0) {
			return cached;
		}
		String path = expression.substring(separator + 1);
		if (cached == null || path.isBlank()) {
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

			Method targetMethod = findTargetMethod(target, name, args);
			Object result = invokeTarget(targetMethod, target, args);
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
			Method targetMethod = findTargetMethod(value, name, args);
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
					? localSoftFailure(targetMethod, args, value)
					: null;
			if (localSoftFailure != null) {
				JPostmanRuntimeRunner.recordSoftFailure(localSoftFailure);
				return adaptAssertReturn(proxy, method, null, soft);
			}

			Object result;
			try {
				result = invokeTarget(targetMethod, value, args);
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

	private static Object adaptContextReturn(Object proxy, Method method, Object result,
			Supplier<?> activeContextSupplier) {
		Class<?> returnType = method.getReturnType();
		String name = method.getName();

		if (returnType == Void.TYPE) {
			return null;
		}
		if (returnType == JPostman.Assert.class) {
			return wrapAssert(result, activeContextSupplier, "soft".equals(name));
		}
		if (proxy instanceof JPostman.Test && ("asserts".equals(name) || "soft".equals(name))) {
			return wrapAssert(result, activeContextSupplier, "soft".equals(name));
		}
		if ("asserts".equals(name) || returnType == JPostmanTestAssertions.class || returnsTypeVariable(method, "A")) {
			return wrapAssertions(result);
		}
		if ("soft".equals(name) || returnType == JPostmanTestSoftAssertions.class || returnsTypeVariable(method, "S")) {
			return wrapSoftAssertions(result);
		}
		if (returnType == JPostman.Test.class || returnsTypeVariable(method, "C")) {
			return result == null ? proxy : wrap(result);
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
