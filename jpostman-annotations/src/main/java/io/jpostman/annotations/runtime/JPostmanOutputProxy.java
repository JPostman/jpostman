package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.jpostman.annotations.JPostmanOutputs;

/** Routes print calls on request/response facade objects to JPostmanOutput. */
final class JPostmanOutputProxy implements InvocationHandler {

	private final Object target;

	private JPostmanOutputProxy(Object target) {
		this.target = target;
	}

	static Object wrap(Object target, Class<?> declaredType) {
		if (target == null || declaredType == null || !declaredType.isInterface()) {
			return target;
		}
		return Proxy.newProxyInstance(declaredType.getClassLoader(), new Class<?>[] { declaredType },
				new JPostmanOutputProxy(target));
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
			return target == (args == null || args.length == 0 ? null : args[0]);
		}
		if ("print".equals(name) && JPostmanOutputs.isInstalled()) {
			Method log = findMethod(target.getClass(), "log", args);
			Object text = invoke(log, args);
			JPostmanOutputs.write(text == null ? "" : String.valueOf(text));
			return null;
		}
		Method targetMethod = findMethod(target.getClass(), name, args);
		return invoke(targetMethod, args);
	}

	private Object invoke(Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(target, args);
		} catch (InvocationTargetException e) {
			throw e.getCause();
		}
	}

	private static Method findMethod(Class<?> type, String name, Object[] args) throws NoSuchMethodException {
		int count = args == null ? 0 : args.length;
		for (Method method : type.getMethods()) {
			if (method.getName().equals(name) && method.getParameterCount() == count) {
				return method;
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + name);
	}
}
