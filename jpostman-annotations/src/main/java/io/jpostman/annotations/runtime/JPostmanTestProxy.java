package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.function.Supplier;

import io.jpostman.annotations.JPostman;
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
		return wrapAssert(null, activeContextSupplier);
	}

	private static JPostman.Assert wrapAssert(Object target, Supplier<?> activeContextSupplier) {
		if (target instanceof JPostman.Assert) {
			return (JPostman.Assert) target;
		}
		return (JPostman.Assert) Proxy.newProxyInstance(JPostman.Assert.class.getClassLoader(),
				new Class<?>[] { JPostman.Assert.class }, new JPostmanAssertProxy(target, activeContextSupplier));
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

		Method targetMethod = findTargetMethod(target, name, args);
		Object result = invokeTarget(targetMethod, target, args);
		return adaptContextReturn(proxy, method, result);
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

		private JPostmanAssertProxy(Object target, Supplier<?> activeContextSupplier) {
			this.target = target;
			this.activeContextSupplier = activeContextSupplier;
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
				return wrapAssert(soft, activeContextSupplier);
			}

			Object value = assertionTarget();
			Method targetMethod = findTargetMethod(value, name, args);
			Object result = invokeTarget(targetMethod, value, args);
			return adaptAssertReturn(proxy, method, result);
		}

		private Object assertionTarget() throws Throwable {
			if (target != null) {
				return target;
			}
			return invokeContext("asserts", null);
		}

		private Object invokeContext(String name, Object[] args) throws Throwable {
			Object context = activeContextSupplier == null ? null : activeContextSupplier.get();
			if (context == null) {
				throw new IllegalStateException(
						"No active JPostman test context is available for @JPostman.AssertContext.");
			}
			Method targetMethod = findTargetMethod(context, name, args);
			return invokeTarget(targetMethod, context, args);
		}
	}

	private static Object adaptContextReturn(Object proxy, Method method, Object result) {
		Class<?> returnType = method.getReturnType();
		String name = method.getName();

		if (returnType == Void.TYPE) {
			return null;
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

	private static Object adaptAssertReturn(Object proxy, Method method, Object result) {
		Class<?> returnType = method.getReturnType();

		if (returnType == Void.TYPE) {
			return null;
		}
		if (returnType == JPostman.Assert.class || returnsTypeVariable(method, "A")) {
			return result == null ? proxy : wrapAssert(result, null);
		}
		if (returnType == JPostman.Test.class || returnsTypeVariable(method, "C")) {
			return result == null ? null : wrap(result);
		}
		if (result != null && returnType.isInstance(result)) {
			return result;
		}
		return result;
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
