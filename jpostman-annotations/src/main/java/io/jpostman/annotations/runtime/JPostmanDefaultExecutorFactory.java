package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import io.jpostman.ApiExecutor;

/**
 * Creates default annotation executors from @JPostmanContext or
 * jpostman.properties.
 */
final class JPostmanDefaultExecutorFactory {

	private JPostmanDefaultExecutorFactory() {
	}

	static ApiExecutor create(Class<?> executorClass, Object ctx, boolean session) {
		if (executorClass == null || executorClass == Void.class) {
			return null;
		}

		try {
			Object result = session ? createSessionExecutor(executorClass, ctx)
					: createRequestExecutor(executorClass, ctx);
			if (result instanceof ApiExecutor) {
				return (ApiExecutor) result;
			}
			throw new IllegalStateException("JPostman default executor method must return ApiExecutor: "
					+ executorClass.getName() + (session ? ".create()" : ".apply(request)"));
		} catch (InvocationTargetException e) {
			Throwable cause = e.getTargetException();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(
					"Unable to create JPostman default executor from: " + executorClass.getName(), cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Unable to create JPostman default executor from: " + executorClass.getName(), e);
		}
	}

	static ApiExecutor request(ApiExecutor executor, Object ctx) {
		try {
			Object request = request(ctx);
			return setRequest(executor, request);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getTargetException();
			if (cause instanceof RuntimeException) {
				throw (RuntimeException) cause;
			}
			if (cause instanceof Error) {
				throw (Error) cause;
			}
			throw new IllegalStateException(
					"Unable to set request on JPostman session executor: " + executor.getClass().getName(), cause);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Unable to set request on JPostman session executor: " + executor.getClass().getName(), e);
		}
	}

	private static Object createSessionExecutor(Class<?> executorClass, Object ctx)
			throws ReflectiveOperationException {
		Method method = executorClass.getMethod("create");
		Object result = method.invoke(null);
		if (result instanceof ApiExecutor) {
			return request((ApiExecutor) result, ctx);
		}
		return result;
	}

	private static Object createRequestExecutor(Class<?> executorClass, Object ctx)
			throws ReflectiveOperationException {
		Object request = request(ctx);
		Method applyMethod = findApplyMethod(executorClass, request);
		return applyMethod.invoke(null, request);
	}

	private static Object request(Object ctx) throws ReflectiveOperationException {
		if (ctx == null) {
			throw new IllegalStateException("Cannot create JPostman default executor because context is null.");
		}
		Method requestMethod = ctx.getClass().getMethod("request");
		Object request = requestMethod.invoke(ctx);
		if (request == null) {
			throw new IllegalStateException("Cannot create JPostman default executor because ctx.request() is null.");
		}
		return request;
	}

	private static ApiExecutor setRequest(ApiExecutor executor, Object request) throws ReflectiveOperationException {

		Object result = invokeSetRequest(executor, request);
		if (result == null) {
			Object built = buildRequest(request);
			if (built != request) {
				result = invokeSetRequest(executor, built);
			}
		}

		if (result == null) {
			throw new IllegalStateException(
					"Session executor does not provide setRequest(request): " + executor.getClass().getName());
		}

		if (result instanceof ApiExecutor) {
			return (ApiExecutor) result;
		}
		return executor;
	}

	private static Object invokeSetRequest(ApiExecutor executor, Object request) throws ReflectiveOperationException {
		for (Method method : executor.getClass().getMethods()) {
			if (!"setRequest".equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			if (method.getParameterTypes()[0].isAssignableFrom(request.getClass())) {
				return method.invoke(executor, request);
			}
		}
		return null;
	}

	private static Object buildRequest(Object request) {
		try {
			Method build = request.getClass().getMethod("build");
			Object built = build.invoke(request);
			return built == null ? request : built;
		} catch (NoSuchMethodException e) {
			return request;
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(
					"Unable to build request for JPostman session executor: " + request.getClass().getName(), e);
		}
	}

	private static Method findApplyMethod(Class<?> executorClass, Object request) {
		for (Method method : executorClass.getMethods()) {
			if (!"apply".equals(method.getName()) || method.getParameterCount() != 1) {
				continue;
			}
			if (method.getParameterTypes()[0].isAssignableFrom(request.getClass())) {
				return method;
			}
		}
		throw new IllegalStateException(
				"Executor class does not provide static apply(request): " + executorClass.getName());
	}
}
