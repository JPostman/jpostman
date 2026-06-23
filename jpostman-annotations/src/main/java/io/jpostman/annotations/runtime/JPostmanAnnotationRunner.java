package io.jpostman.annotations.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.jpostman.ApiExecutor;
import io.jpostman.Collection;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanAssert;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;

/**
 * Shared annotation execution flow for JUnit and TestNG.
 *
 * @param <C> framework context type
 */
public final class JPostmanAnnotationRunner<C> {
	private static final Object VOID_DEPENDENCY_MARKER = new Object();

	private final JPostmanFramework<C> framework;
	private final JPostmanContextRunner<C> contextRunner;
	private final JPostmanAssertionRunner<C> assertionRunner;
	private final JPostmanRequestDiscovery requestDiscovery;

	/**
	 * Creates a runner for the supplied framework bridge.
	 *
	 * @param framework framework bridge used to perform context operations
	 */
	public JPostmanAnnotationRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
		this.contextRunner = new JPostmanContextRunner<>(framework);
		this.assertionRunner = new JPostmanAssertionRunner<>(framework);
		this.requestDiscovery = new JPostmanRequestDiscovery();
	}

	/**
	 * Prepares a test instance before framework lifecycle methods run.
	 *
	 * @param testInstance test instance to prepare
	 * @throws Exception when context preparation or field injection fails
	 */
	public void setup(Object testInstance) throws Exception {
		PreparedContexts<C> prepared = contextRunner.prepare(testInstance);
		contextRunner.injectLoadedContexts(testInstance, prepared);

		if (!prepared.isEmpty()) {
			C current = prepared.contains("") ? prepared.context("") : prepared.firstContext();
			framework.setCurrent(current);
		}
	}

	/**
	 * Runs JPostman annotations for a single test method.
	 *
	 * @param testInstance test instance that owns the method
	 * @param testMethod   test method to process
	 * @throws Exception when annotation execution fails
	 */
	public void run(Object testInstance, Method testMethod) throws Exception {
		PreparedContexts<C> prepared = contextRunner.prepare(testInstance);
		contextRunner.injectLoadedContexts(testInstance, prepared);

		JPostmanRequest requestAnnotation = testMethod.getAnnotation(JPostmanRequest.class);
		JPostmanResponse responseAnnotation = testMethod.getAnnotation(JPostmanResponse.class);
		JPostmanRunner runnerAnnotation = testMethod.getAnnotation(JPostmanRunner.class);
		JPostmanAssert assertAnnotation = testMethod.getAnnotation(JPostmanAssert.class);

		if (requestAnnotation == null && responseAnnotation == null && runnerAnnotation == null) {
			framework.clearCurrent();
			return;
		}

		if (prepared.isEmpty()) {
			framework.clearCurrent();
			return;
		}

		String currentNamespace = namespace(requestAnnotation, responseAnnotation, runnerAnnotation);
		PreparedContext<C> current = prepared.resolve(currentNamespace);
		framework.setCurrent(current.context);

		if (requestAnnotation != null) {
			runAnnotatedRequest(testInstance, prepared, currentNamespace, current.collection, requestAnnotation);
		}

		if (responseAnnotation != null) {
			C currentContext = runAnnotatedResponse(testInstance, prepared, currentNamespace, current.collection,
					responseAnnotation);
			executeResponse(testInstance, prepared, currentContext, responseAnnotation, testMethod.getName(),
					assertAnnotation);
		}

		if (runnerAnnotation != null) {
			runDependencies(testInstance, prepared, runnerAnnotation.dependsOn());
			executeRunner(testInstance, prepared, runnerAnnotation, testMethod.getName(), assertAnnotation);
		}
	}

	private interface DependencyAction {
		void run() throws Exception;
	}

	private void runAnnotatedRequest(Object testInstance, PreparedContexts<C> prepared, String namespace,
			Collection collection, JPostmanRequest annotation) throws Exception {

		C ctx = prepareRequest(prepared.context(namespace), collection, annotation);
		prepared.update(namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, annotation.dependsOn());

		ctx = prepareRequest(prepared.context(namespace), collection, annotation);
		prepared.update(namespace, ctx);
		framework.setCurrent(ctx);
	}

	private C runAnnotatedResponse(Object testInstance, PreparedContexts<C> prepared, String namespace,
			Collection collection, JPostmanResponse annotation) throws Exception {

		C ctx = prepareRequest(prepared.context(namespace), collection, annotation);
		prepared.update(namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, annotation.dependsOn());

		ctx = prepareRequest(prepared.context(namespace), collection, annotation);
		prepared.update(namespace, ctx);
		framework.setCurrent(ctx);
		return ctx;
	}

	private String namespace(JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			return requestAnnotation.namespace();
		}
		if (responseAnnotation != null) {
			return responseAnnotation.namespace();
		}
		return runnerAnnotation.namespace();
	}

	private void runDependencies(Object testInstance, PreparedContexts<C> resolver, String[] dependencyNames)
			throws Exception {
		for (String dependencyName : dependencyNames) {
			runDependency(testInstance, resolver, dependencyName);
		}
	}

	private void runDependency(Object testInstance, PreparedContexts<C> resolver, String dependencyName)
			throws Exception {
		if (dependencyName == null || dependencyName.isBlank()) {
			return;
		}

		Method dependencyMethod = findNoArgMethod(testInstance.getClass(), dependencyName.trim());

		JPostmanResponse responseAnnotation = dependencyMethod.getAnnotation(JPostmanResponse.class);
		if (responseAnnotation != null) {
			runResponseDependency(testInstance, resolver, dependencyMethod, responseAnnotation);
			return;
		}

		JPostmanRunner runnerAnnotation = dependencyMethod.getAnnotation(JPostmanRunner.class);
		if (runnerAnnotation != null) {
			runRunnerDependency(testInstance, resolver, dependencyMethod, runnerAnnotation);
			return;
		}

		JPostmanRequest requestAnnotation = dependencyMethod.getAnnotation(JPostmanRequest.class);
		if (requestAnnotation == null) {
			throw new IllegalStateException(
					"Dependency method must be annotated with @JPostmanRequest, @JPostmanResponse, or @JPostmanRunner: "
							+ dependencyName);
		}

		runRequestDependency(testInstance, resolver, dependencyMethod, requestAnnotation);
	}

	private void runResponseDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanResponse annotation) throws Exception {

		String namespace = annotation.namespace();
		runCachedDependency(testInstance, resolver, dependencyMethod, namespace, annotation.cache(), () -> {
			C ctx = prepareRequest(resolver.context(namespace), resolver.collection(namespace), annotation);
			resolver.update(namespace, ctx);
			framework.setCurrent(ctx);
			executeResponse(testInstance, resolver, ctx, annotation, dependencyMethod.getName(),
					dependencyMethod.getAnnotation(JPostmanAssert.class));
		});
	}

	private void runRunnerDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRunner annotation) throws Exception {

		String namespace = annotation.namespace();
		runCachedDependency(testInstance, resolver, dependencyMethod, namespace, annotation.cache(),
				() -> executeRunner(testInstance, resolver, annotation, dependencyMethod.getName(),
						dependencyMethod.getAnnotation(JPostmanAssert.class)));
	}

	private void runRequestDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRequest annotation) throws Exception {

		String namespace = annotation.namespace();
		runCachedDependency(testInstance, resolver, dependencyMethod, namespace, annotation.cache(), () -> {
			C ctx = prepareRequest(resolver.context(namespace), resolver.collection(namespace), annotation);
			resolver.update(namespace, ctx);
			framework.setCurrent(ctx);
		});
	}

	private void runCachedDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			String namespace, String cache, DependencyAction action) throws Exception {

		String cacheKey = cacheKey(dependencyMethod, cache);
		if (isCached(resolver.context(namespace), cacheKey)) {
			return;
		}

		action.run();
		cacheDependencyResult(testInstance, resolver, dependencyMethod, namespace, cacheKey);
	}

	private void cacheDependencyResult(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			String namespace, String cacheKey) throws Exception {

		Object value = invoke(testInstance, dependencyMethod);
		Object cacheValue = dependencyMethod.getReturnType() == Void.TYPE ? VOID_DEPENDENCY_MARKER : value;
		if (cacheValue == null) {
			throw new IllegalStateException("Dependency method returned null and cannot be cached: "
					+ dependencyMethod.getName() + ". Use void for setup-only dependencies, "
					+ "or return a non-null value when another request needs the cached value.");
		}
		framework.cache(resolver.context(namespace), cacheKey, cacheValue);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation) {
		C result = applyRuleAndFilter(context, annotation.rule());
		return framework.request(result, request(collection, annotation.folder(), annotation.request()));
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return framework.request(result, request(collection, annotation.folder(), annotation.request()));
	}

	private C prepareRequest(C context, Collection collection, JPostmanRunner annotation, String requestName) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return framework.request(result, request(collection, annotation.folder(), requestName));
	}

	private C applyRuleAndFilter(C context, String rule, String... filter) {
		C result = context;
		if (rule != null && !rule.isBlank()) {
			result = framework.loadRules(result, rule);
		}
		if (filter != null && filter.length > 0) {
			result = framework.filter(result, filter);
		}
		return result;
	}

	private Request request(Collection collection, String folder, String request) {
		if (folder == null || folder.isBlank()) {
			return collection.getRequest(request);
		}
		return collection.getFolder(folder).getRequest(request);
	}

	private void executeRunner(Object testInstance, PreparedContexts<C> resolver, JPostmanRunner annotation,
			String methodName, JPostmanAssert assertAnnotation) throws Exception {

		String namespace = annotation.namespace();
		Collection collection = resolver.collection(namespace);
		List<String> requestNames = requestDiscovery.runnerRequestNames(collection, annotation.folder());
		Set<String> includes = requestDiscovery.normalizeNames(annotation.include());
		Set<String> excludes = requestDiscovery.normalizeNames(annotation.exclude());

		for (String requestName : requestNames) {
			if (!includes.isEmpty() && !includes.contains(requestName)) {
				continue;
			}
			if (excludes.contains(requestName)) {
				continue;
			}
			if (requestDiscovery.hasExplicitResponse(testInstance.getClass(), namespace, annotation.folder(),
					requestName)) {
				continue;
			}

			C ctx = prepareRequest(resolver.context(namespace), collection, annotation, requestName);
			resolver.update(namespace, ctx);
			framework.setCurrent(ctx);
			executeRunnerResponse(testInstance, resolver, ctx, annotation, methodName, requestName, assertAnnotation);
		}
	}

	private void executeRunnerResponse(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanRunner annotation, String methodName, String requestName, JPostmanAssert assertAnnotation)
			throws Exception {

		Method executor = findExecutor(testInstance.getClass(), annotation.executor());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		Collection collection = resolver.collection(annotation.namespace());
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName);
			ctx = prepareRequest(resolver.context(annotation.namespace()), collection, annotation, requestName);
			resolver.update(annotation.namespace(), ctx);
			framework.setCurrent(ctx);
		}

		Object result = invokeExecutor(testInstance, executor, ctx, methodName, requestName);
		verifyExecutorResult(result, executor);

		ctx = framework.response(ctx, (ApiExecutor) result);
		resolver.update(annotation.namespace(), ctx);
		framework.setCurrent(ctx);
		if (assertAnnotation != null) {
			assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, requestName, annotation.soft(),
					annotation.log());
		} else {
			framework.verify(ctx, annotation.verify(), annotation.soft(), annotation.log());
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			String methodName, JPostmanAssert assertAnnotation) throws Exception {

		Method executor = findExecutor(testInstance.getClass(), annotation.executor());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		Collection collection = resolver.collection(annotation.namespace());
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName);
			ctx = prepareRequest(resolver.context(annotation.namespace()), collection, annotation);
			resolver.update(annotation.namespace(), ctx);
			framework.setCurrent(ctx);
		}

		Object result = invokeExecutor(testInstance, executor, ctx, methodName, annotation.request());
		verifyExecutorResult(result, executor);

		ctx = framework.response(ctx, (ApiExecutor) result);
		resolver.update(annotation.namespace(), ctx);
		framework.setCurrent(ctx);
		if (assertAnnotation != null) {
			assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, annotation.request(),
					annotation.soft(), annotation.log());
		} else {
			framework.verify(ctx, annotation.verify(), annotation.soft(), annotation.log());
		}
	}

	private void verifyExecutorResult(Object result, Method executor) {
		if (result == null) {
			throw new IllegalStateException("JPostman executor returned null: " + executor.getName());
		}
		if (!(result instanceof ApiExecutor)) {
			throw new IllegalStateException("JPostman executor must return ApiExecutor: " + executor.getName());
		}
	}

	private Method findExecutor(Class<?> type, String requestedName) {
		List<Method> unnamed = new ArrayList<>();
		List<Method> all = new ArrayList<>();

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = method.getAnnotation(JPostmanExecutor.class);
				if (annotation == null) {
					continue;
				}

				validateExecutorMethod(method);
				method.setAccessible(true);

				String executorName = annotation.name().isBlank() ? method.getName() : annotation.name();
				if (!requestedName.isBlank() && requestedName.equals(executorName)) {
					return method;
				}

				all.add(method);
				if (annotation.name().isBlank()) {
					unnamed.add(method);
				}
			}
			current = current.getSuperclass();
		}

		if (!requestedName.isBlank()) {
			throw new IllegalStateException("JPostman executor not found: " + requestedName);
		}

		for (Method method : unnamed) {
			if ("defaultExecutor".equals(method.getName())) {
				return method;
			}
		}
		if (unnamed.size() == 1) {
			return unnamed.get(0);
		}
		if (all.size() == 1) {
			return all.get(0);
		}

		throw new IllegalStateException(
				"Default JPostman executor not found. Add one unnamed @JPostmanExecutor or specify executor = name.");
	}

	private Object invokeExecutor(Object testInstance, Method executor, C ctx, String methodName, String requestName)
			throws Exception {
		if (executor.getParameterCount() == 3) {
			return invoke(testInstance, executor, ctx, methodName, requestName);
		}
		if (executor.getParameterCount() == 2) {
			return invoke(testInstance, executor, ctx, methodName);
		}
		return invoke(testInstance, executor, ctx);
	}

	private void validateExecutorMethod(Method method) {
		Class<?>[] parameterTypes = method.getParameterTypes();
		boolean validOneArg = parameterTypes.length == 1 && framework.contextType().isAssignableFrom(parameterTypes[0]);
		boolean validTwoArgs = parameterTypes.length == 2 && framework.contextType().isAssignableFrom(parameterTypes[0])
				&& String.class.isAssignableFrom(parameterTypes[1]);
		boolean validThreeArgs = parameterTypes.length == 3
				&& framework.contextType().isAssignableFrom(parameterTypes[0])
				&& String.class.isAssignableFrom(parameterTypes[1]) && String.class.isAssignableFrom(parameterTypes[2]);

		if (!validOneArg && !validTwoArgs && !validThreeArgs) {
			String contextName = framework.contextType().getSimpleName();
			throw new IllegalStateException("@JPostmanExecutor method must accept " + contextName + ", " + contextName
					+ ", String, or " + contextName + ", String, String: " + method.getName());
		}
	}

	private Method findNoArgMethod(Class<?> type, String methodName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (method.getName().equals(methodName) && method.getParameterCount() == 0) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		throw new IllegalStateException("Dependency method not found: " + methodName);
	}

	private Object invoke(Object testInstance, Method method, Object... args) throws Exception {
		try {
			return method.invoke(testInstance, args);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
	}

	private String cacheKey(Method method, String cache) {
		return cache.isBlank() ? method.getName() : cache;
	}

	private boolean isCached(C ctx, String key) {
		try {
			return framework.hasCache(ctx, key);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
