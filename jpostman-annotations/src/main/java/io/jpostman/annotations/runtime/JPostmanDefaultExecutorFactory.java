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
				ApiExecutor executor = (ApiExecutor) result;
				applyRequestAuthentication(executor, request(ctx));
				return executor;
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
			ApiExecutor configured = setRequest(executor, request);
			applyRequestAuthentication(configured, request);
			return configured;
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
			return setRequest((ApiExecutor) result, request(ctx));
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

	/**
	 * Applies authentication already resolved on the final request through the
	 * executor's common fluent auth API. All supported executors expose
	 * {@code auth().oauth2(token)}, so the annotation module can configure the
	 * selected executor class without depending on RestAssured, Playwright,
	 * Unirest, or HttpClient implementation classes.
	 */
	private static void applyRequestAuthentication(ApiExecutor executor, Object request)
			throws ReflectiveOperationException {
		if (executor == null || request == null) {
			return;
		}

		Object built = buildRequest(request);
		Object auth = invokeNoArg(built, "getAuth", "auth");
		if (auth == null) {
			return;
		}

		String type = stringValue(invokeNoArg(auth, "getType", "type"));
		Object paramsValue = invokeNoArg(auth, "getParams", "params");
		if (!(paramsValue instanceof java.util.Map<?, ?>)) {
			return;
		}

		java.util.Map<?, ?> params = (java.util.Map<?, ?>) paramsValue;
		if (!"bearer".equalsIgnoreCase(type) && !"oauth2".equalsIgnoreCase(type)) {
			return;
		}

		String token = stringValue(params.get("token"));
		if (token == null || token.isBlank()) {
			return;
		}

		Method authMethod;
		try {
			authMethod = executor.getClass().getMethod("auth");
		} catch (NoSuchMethodException ignored) {
			// Preserve compatibility with custom executors that do not expose auth().
			return;
		}
		Object authentication = authMethod.invoke(executor);
		if (authentication == null) {
			return;
		}

		Method oauth2;
		try {
			oauth2 = authentication.getClass().getMethod("oauth2", String.class);
		} catch (NoSuchMethodException ignored) {
			return;
		}
		oauth2.invoke(authentication, token);
	}

	private static Object invokeNoArg(Object target, String... methodNames) throws ReflectiveOperationException {
		if (target == null) {
			return null;
		}
		for (String methodName : methodNames) {
			try {
				return target.getClass().getMethod(methodName).invoke(target);
			} catch (NoSuchMethodException ignored) {
				// Try the next supported accessor name.
			}
		}
		return null;
	}

	private static String stringValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

}
