package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.jpostman.annotations.JPostman;

/** Framework-neutral proxy for TestNG/JUnit contexts used by JPostman.Test. */
final class JPostmanTestProxy implements InvocationHandler {

	private final Object target;

	private JPostmanTestProxy(Object target) {
		this.target = target;
	}

	static JPostman.Test wrap(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.Test) {
			return (JPostman.Test) target;
		}
		return (JPostman.Test) Proxy.newProxyInstance(JPostman.Test.class.getClassLoader(),
				new Class<?>[] { JPostman.Test.class }, new JPostmanTestProxy(target));
	}

	private static JPostman.Assertions wrapAssertions(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.Assertions) {
			return (JPostman.Assertions) target;
		}
		return (JPostman.Assertions) Proxy.newProxyInstance(JPostman.Assertions.class.getClassLoader(),
				new Class<?>[] { JPostman.Assertions.class }, new JPostmanAssertionProxy(target));
	}

	private static JPostman.SoftAssertions wrapSoftAssertions(Object target) {
		if (target == null) {
			return null;
		}
		if (target instanceof JPostman.SoftAssertions) {
			return (JPostman.SoftAssertions) target;
		}
		return (JPostman.SoftAssertions) Proxy.newProxyInstance(JPostman.SoftAssertions.class.getClassLoader(),
				new Class<?>[] { JPostman.SoftAssertions.class }, new JPostmanAssertionProxy(target));
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
			return proxy == args[0];
		}

		Method targetMethod = findTargetMethod(target, name, args);
		Object result = invokeTarget(targetMethod, target, args);
		return adaptReturn(proxy, method.getReturnType(), result);
	}

	private static final class JPostmanAssertionProxy implements InvocationHandler {

		private final Object target;

		private JPostmanAssertionProxy(Object target) {
			this.target = target;
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
				return proxy == args[0];
			}

			Method targetMethod = findTargetMethod(target, name, args);
			Object result = invokeTarget(targetMethod, target, args);
			return adaptReturn(proxy, method.getReturnType(), result);
		}
	}

	private static Object adaptReturn(Object proxy, Class<?> returnType, Object result) {
		if (returnType == Void.TYPE) {
			return null;
		}
		if (returnType == JPostman.Test.class) {
			return result == null ? proxy : wrap(result);
		}
		if (returnType == JPostman.SoftAssertions.class) {
			return result == null ? proxy : wrapSoftAssertions(result);
		}
		if (returnType == JPostman.Assertions.class) {
			return result == null ? proxy : wrapAssertions(result);
		}
		if (returnType.isInstance(result)) {
			return result;
		}
		return result;
	}

	private static Object invokeTarget(Method method, Object target, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args == null ? new Object[0] : args);
		} catch (InvocationTargetException e) {
			throw e.getTargetException();
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
