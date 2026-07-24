package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.function.Supplier;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanOutputs;
import io.jpostman.annotations.JPostmanTestAssertions;
import io.jpostman.annotations.JPostmanTestSoftAssertions;

/** Framework-neutral proxy for TestNG/JUnit contexts used by JPostman.Test. */
final class JPostmanTestProxy implements InvocationHandler {

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
		return wrapAssert(null, activeContextSupplier, soft, classScopedSoft);
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
		if (isAssertProxy(target)) {
			return (JPostman.Assert) target;
		}

		/*
		 * Keep assertion objects returned by the underlying context behind the JPostman
		 * facade even when they already implement JPostman.Assert. A raw object
		 * returned from soft(true) would otherwise bypass Runner request-scoped
		 * collection and class-soft lifecycle rules.
		 */
		return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
				new Class<?>[] { JPostman.Assert.class },
				new JPostmanAssertProxy(target, activeContextSupplier, soft, classScopedSoft));
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
		if ("cache".equals(name) && args != null && args.length >= 1 && args[0] instanceof String) {
			Object value = resolveCacheExpression(target, (String) args[0]);
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
		private volatile Object lastActiveContext;

		private JPostmanAssertProxy(Object target, Supplier<?> activeContextSupplier, boolean soft,
				boolean classScopedSoft) {
			this.target = target;
			this.activeContextSupplier = activeContextSupplier;
			this.soft = soft;
			this.classScopedSoft = classScopedSoft;
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
			if ("soft".equals(name) && method.getParameterCount() == 1) {
				Object soft = invokeContext("soft", args);
				return wrapAssert(soft, activeContextSupplier, true, classScopedSoft);
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
			if (target == null && isVerifyMethod(name) && !JPostmanRuntimeRunner.active()) {
				String contextMethod = "assertAll".equals(name) ? "verify" : name;
				Object context = resolveContext();

				/*
				 * A class may inject @JPostman.AssertContext and call verify() from
				 * 
				 * @AfterAll/@AfterClass without having executed any assertions. In that case no
				 * active or remembered assertion context exists, and there is nothing to flush.
				 * Treat verify()/assertAll() as a successful no-op.
				 */
				if (context == null) {
					return adaptAssertReturn(proxy, method, null, soft);
				}

				Object result = invokeContext(context, contextMethod, args);
				return adaptAssertReturn(proxy, method, result, soft);
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
			}
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
				if (soft && !classScopedSoft && isVerifyMethod(name)) {
					throw JPostmanRuntimeRunner.softFailure(e);
				}
				throw e;
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

		private Object assertionTarget() throws Throwable {
			if (target != null) {
				return target;
			}
			return soft ? invokeContext("soft", new Object[] { Boolean.FALSE }) : invokeContext("asserts", null);
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
		String message = args.length > 1 && args[1] != null ? String.valueOf(args[1]).trim() : "Assertion failed";
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
