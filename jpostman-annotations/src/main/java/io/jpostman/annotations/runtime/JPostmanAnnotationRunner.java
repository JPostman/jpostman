package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import io.jpostman.ApiExecutor;
import io.jpostman.Collection;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanAssert;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanInfo;
import io.jpostman.annotations.JPostmanReport;
import io.jpostman.annotations.JPostmanReportContext;
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
		injectReportContext(testInstance);
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
		JPostmanReport report = injectReportContext(testInstance);
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

		JPostmanInfo info = info(testMethod.getName(), requestAnnotation, responseAnnotation, runnerAnnotation);
		add(report, info);
		info.methods.add(testMethod.getName());
		List<String> stack = new ArrayList<>();
		stack.add(testMethod.getName());

		PreparedContext<C> current = prepared.resolve(info.namespace);
		framework.setCurrent(current.context);

		if (requestAnnotation != null) {
			runAnnotatedRequest(testInstance, prepared, current.collection, requestAnnotation, info, stack);
		}

		if (responseAnnotation != null) {
			C currentContext = runAnnotatedResponse(testInstance, prepared, current.collection, responseAnnotation,
					info, stack);
			executeResponse(testInstance, prepared, currentContext, responseAnnotation, info, assertAnnotation, stack);
		}

		if (runnerAnnotation != null) {
			runDependencies(testInstance, prepared, dependencies(runnerAnnotation.dependsOn()), info, stack);
			executeRunner(testInstance, prepared, runnerAnnotation, info, assertAnnotation, stack);
		}
	}

	private interface DependencyAction {
		void run() throws Exception;
	}

	private void runAnnotatedRequest(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanRequest annotation, JPostmanInfo info, List<String> stack) throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), collection, annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, dependencies(annotation), info, stack);

		ctx = prepareRequest(prepared.context(info.namespace), collection, annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
	}

	private C runAnnotatedResponse(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanResponse annotation, JPostmanInfo info, List<String> stack) throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), collection, annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()), info, stack);

		ctx = prepareRequest(prepared.context(info.namespace), collection, annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		return ctx;
	}

	private JPostmanInfo info(String methodName, JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			return new JPostmanInfo(methodName, requestAnnotation.namespace(), requestAnnotation.folder(),
					requestAnnotation.request());
		}
		if (responseAnnotation != null) {
			return new JPostmanInfo(methodName, responseAnnotation.namespace(), responseAnnotation.folder(),
					responseAnnotation.request());
		}
		return new JPostmanInfo(methodName, runnerAnnotation.namespace(), runnerAnnotation.folder(), "");
	}

	private void runDependencies(Object testInstance, PreparedContexts<C> resolver, String[] dependencyNames,
			JPostmanInfo info, List<String> stack) throws Exception {
		for (String dependencyName : dependencyNames) {
			runDependency(testInstance, resolver, dependencyName, info, stack);
		}
	}

	private void runDependency(Object testInstance, PreparedContexts<C> resolver, String dependencyName,
			JPostmanInfo parentInfo, List<String> stack) throws Exception {
		if (dependencyName == null || dependencyName.isBlank()) {
			return;
		}

		String name = dependencyName.trim();
		if (stack.contains(name)) {
			throw circularDependency(stack, name);
		}

		Method dependencyMethod = findMethod(testInstance.getClass(), name);
		stack.add(name);

		try {
			JPostmanResponse responseAnnotation = dependencyMethod.getAnnotation(JPostmanResponse.class);
			if (responseAnnotation != null) {
				runResponseDependency(testInstance, resolver, dependencyMethod, responseAnnotation, parentInfo, stack);
				return;
			}

			JPostmanRunner runnerAnnotation = dependencyMethod.getAnnotation(JPostmanRunner.class);
			if (runnerAnnotation != null) {
				runRunnerDependency(testInstance, resolver, dependencyMethod, runnerAnnotation, parentInfo, stack);
				return;
			}

			JPostmanRequest requestAnnotation = dependencyMethod.getAnnotation(JPostmanRequest.class);
			if (requestAnnotation == null) {
				throw new IllegalStateException(
						"Dependency method must be annotated with @JPostmanRequest, @JPostmanResponse, or @JPostmanRunner: "
								+ name);
			}

			runRequestDependency(testInstance, resolver, dependencyMethod, requestAnnotation, parentInfo, stack);
		} finally {
			stack.remove(stack.size() - 1);
		}
	}

	private void runResponseDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanResponse annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		JPostmanInfo info = parentInfo.child(dependencyMethod.getName(), annotation.namespace(), annotation.folder(),
				annotation.request());
		add(report(testInstance), info);
		runCachedDependency(testInstance, resolver, dependencyMethod, info, annotation.cache(), () -> {
			C ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation,
					info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info, stack);
			ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation,
					info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			executeResponse(testInstance, resolver, ctx, annotation, info,
					dependencyMethod.getAnnotation(JPostmanAssert.class), stack);
		});
	}

	private void runRunnerDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRunner annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		JPostmanInfo info = parentInfo.child(dependencyMethod.getName(), annotation.namespace(), annotation.folder(),
				parentInfo.request);
		add(report(testInstance), info);
		runCachedDependency(testInstance, resolver, dependencyMethod, info, annotation.cache(), () -> {
			runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info, stack);
			executeRunner(testInstance, resolver, annotation, info,
					dependencyMethod.getAnnotation(JPostmanAssert.class), stack);
		});
	}

	private void runRequestDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRequest annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		String namespace = value(annotation.namespace());
		String folder = value(annotation.folder());
		String request = value(annotation.request());
		String cache = annotation.cache();

		if (cache != null && !cache.isBlank() && isCached(resolver.context(namespace), cache)) {
			return;
		}

		C ctx = prepareRequest(resolver.context(namespace), resolver.collection(namespace), annotation, namespace,
				folder, request);
		resolver.update(namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, resolver, dependencies(annotation), parentInfo, stack);

		ctx = prepareRequest(resolver.context(namespace), resolver.collection(namespace), annotation, namespace, folder,
				request);
		resolver.update(namespace, ctx);
		framework.setCurrent(ctx);

		/*
		 * Request helpers share the same JPostmanInfo created by the top-level test.
		 * Only method/methods and params move forward through the chain. Namespace,
		 * folder, request, and created remain the original top-level values.
		 */
		try {
			Object value = invokeAnnotated(testInstance, dependencyMethod, resolver.context(namespace), parentInfo);
			cacheDependencyResult(resolver, dependencyMethod, namespace, cache, value);
			add(report(testInstance), parentInfo);
		} catch (Exception | Error e) {
			add(report(testInstance), parentInfo);
			throw e;
		}

		parentInfo.methods.add(dependencyMethod.getName());
		parentInfo.method = dependencyMethod.getName();
		runDependency(testInstance, resolver, annotation.next(), parentInfo, stack);
	}

	private void runCachedDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanInfo info, String cache, DependencyAction action) throws Exception {

		if (cache != null && !cache.isBlank() && isCached(resolver.context(info.namespace), cache)) {
			return;
		}

		action.run();
		cacheDependencyResult(testInstance, resolver, dependencyMethod, info, cache);
	}

	private void cacheDependencyResult(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanInfo info, String cache) throws Exception {

		Object value = invokeAnnotated(testInstance, dependencyMethod, resolver.context(info.namespace), info);
		cacheDependencyResult(resolver, dependencyMethod, info, cache, value);
	}

	private void cacheDependencyResult(PreparedContexts<C> resolver, Method dependencyMethod, JPostmanInfo info,
			String cache, Object value) {
		cacheDependencyResult(resolver, dependencyMethod, info.namespace, cache, value);
	}

	private void cacheDependencyResult(PreparedContexts<C> resolver, Method dependencyMethod, String namespace,
			String cache, Object value) {
		if (cache == null || cache.isBlank()) {
			return;
		}

		Object cacheValue = dependencyMethod.getReturnType() == Void.TYPE ? VOID_DEPENDENCY_MARKER : value;
		if (cacheValue == null) {
			throw new IllegalStateException("Dependency method returned null and cannot be cached: "
					+ dependencyMethod.getName() + ". Use void for setup-only dependencies, "
					+ "or return a non-null value when another request needs the cached value.");
		}
		framework.cache(resolver.context(namespace), cache, cacheValue);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, JPostmanInfo info) {
		return prepareRequest(context, collection, annotation, info.namespace, info.folder, info.request);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, String namespace,
			String folder, String request) {
		C result = applyRuleAndFilter(context, annotation.rule());
		if (request == null || request.isBlank()) {
			return result;
		}
		return framework.request(result, request(collection, namespace, folder, request));
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation, JPostmanInfo info) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return framework.request(result, request(collection, info.namespace, info.folder, info.request));
	}

	private C prepareRequest(C context, Collection collection, JPostmanRunner annotation, JPostmanInfo info,
			String requestName) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return framework.request(result, request(collection, info.namespace, annotation.folder(), requestName));
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

	private Request request(Collection collection, String namespace, String folder, String request) {
		try {
			if (folder == null || folder.isBlank()) {
				return collection.getRequest(request);
			}
			return collection.getFolder(folder).getRequest(request);
		} catch (AssertionError | RuntimeException e) {
			AssertionError error = new AssertionError("Request not found: \"" + request + "\" (namespace="
					+ value(namespace) + ", folder=" + value(folder) + ")");
			error.initCause(e);
			throw error;
		}
	}

	private void executeRunner(Object testInstance, PreparedContexts<C> resolver, JPostmanRunner annotation,
			JPostmanInfo info, JPostmanAssert assertAnnotation, List<String> stack) throws Exception {

		JPostmanReport report = report(testInstance);
		Collection collection = resolver.collection(info.namespace);
		List<String> requestNames = requestDiscovery.runnerRequestNames(collection, info.folder);
		Set<String> includes = requestDiscovery.normalizeNames(annotation.include());
		Set<String> excludes = requestDiscovery.normalizeNames(annotation.exclude());

		for (String requestName : requestNames) {
			if (!includes.isEmpty() && !includes.contains(requestName)) {
				skipped(report, null);
				continue;
			}
			if (excludes.contains(requestName)) {
				skipped(report, null);
				continue;
			}
			if (requestDiscovery.hasExplicitResponse(testInstance.getClass(), info.namespace, info.folder,
					requestName)) {
				skipped(report, null);
				continue;
			}

			JPostmanInfo requestInfo = info.child(info.method, info.namespace, info.folder, requestName);
			add(report, requestInfo);
			C ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, requestName);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			executeRunnerResponse(testInstance, resolver, ctx, annotation, requestInfo, assertAnnotation, stack);
		}
	}

	private void executeRunnerResponse(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanRunner annotation, JPostmanInfo info, JPostmanAssert assertAnnotation, List<String> stack)
			throws Exception {

		Method executor = findExecutor(testInstance.getClass(), annotation.executor());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		Collection collection = resolver.collection(info.namespace);
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName, info, stack);
			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, info.request);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
		}

		JPostmanReport report = report(testInstance);
		try {
			Object result = invokeExecutor(testInstance, executor, ctx, info);
			verifyExecutorResult(result, executor);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			if (assertAnnotation != null) {
				assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, info.request, annotation.soft(),
						annotation.log());
			} else {
				framework.verify(ctx, annotation.verify(), annotation.soft(), annotation.log());
			}
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw e;
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			JPostmanInfo info, JPostmanAssert assertAnnotation, List<String> stack) throws Exception {

		Method executor = findExecutor(testInstance.getClass(), annotation.executor());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		Collection collection = resolver.collection(info.namespace);
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName, info, stack);
			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
		}

		JPostmanReport report = report(testInstance);
		try {
			Object result = invokeExecutor(testInstance, executor, ctx, info);
			verifyExecutorResult(result, executor);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			if (assertAnnotation != null) {
				assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, info.request, annotation.soft(),
						annotation.log());
			} else {
				framework.verify(ctx, annotation.verify(), annotation.soft(), annotation.log());
			}
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw e;
		}
	}

	private JPostmanReport injectReportContext(Object testInstance) throws IllegalAccessException {
		JPostmanReport result = null;
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (field.getAnnotation(JPostmanReportContext.class) == null) {
					continue;
				}
				if (!JPostmanReport.class.isAssignableFrom(field.getType())) {
					throw new IllegalStateException(
							"@JPostmanReportContext field must be JPostmanReport: " + field.getName());
				}
				field.setAccessible(true);
				JPostmanReport report = (JPostmanReport) field.get(testInstance);
				if (report == null) {
					report = new JPostmanReport();
					field.set(testInstance, report);
				}
				if (result == null) {
					result = report;
				}
			}
			current = current.getSuperclass();
		}
		return result;
	}

	private JPostmanReport report(Object testInstance) throws IllegalAccessException {
		return injectReportContext(testInstance);
	}

	private void add(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.add(info);
		}
	}

	private void passed(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.passed(info);
		}
	}

	private void failed(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.failed(info);
		}
	}

	private void skipped(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.skipped(info);
		}
	}

	private String value(String value) {
		return value == null ? "" : value;
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

	private Object invokeExecutor(Object testInstance, Method executor, C ctx, JPostmanInfo info) throws Exception {
		Class<?>[] types = executor.getParameterTypes();
		if (types.length == 2 && JPostmanInfo.class.isAssignableFrom(types[1])) {
			return invoke(testInstance, executor, ctx, info);
		}
		if (types.length == 3) {
			return invoke(testInstance, executor, ctx, info.method, info.request);
		}
		if (types.length == 2) {
			return invoke(testInstance, executor, ctx, info.method);
		}
		return invoke(testInstance, executor, ctx);
	}

	private void validateExecutorMethod(Method method) {
		Class<?>[] types = method.getParameterTypes();
		boolean valid = types.length == 1 && framework.contextType().isAssignableFrom(types[0]);
		valid = valid || (types.length == 2 && framework.contextType().isAssignableFrom(types[0])
				&& (JPostmanInfo.class.isAssignableFrom(types[1]) || String.class.isAssignableFrom(types[1])));
		valid = valid || (types.length == 3 && framework.contextType().isAssignableFrom(types[0])
				&& String.class.isAssignableFrom(types[1]) && String.class.isAssignableFrom(types[2]));

		if (valid) {
			return;
		}

		String contextName = framework.contextType().getSimpleName();
		throw new IllegalStateException(
				"@JPostmanExecutor method must accept " + contextName + ", " + contextName + ", JPostmanInfo, "
						+ contextName + ", String, or " + contextName + ", String, String: " + method.getName());
	}

	private Method findMethod(Class<?> type, String methodName) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (method.getName().equals(methodName) && validAnnotatedMethod(method)) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		throw new IllegalStateException("Dependency method not found: " + methodName);
	}

	private boolean validAnnotatedMethod(Method method) {
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return true;
		}
		if (types.length == 1) {
			return framework.contextType().isAssignableFrom(types[0]) || JPostmanInfo.class.isAssignableFrom(types[0]);
		}
		if (types.length == 2) {
			return framework.contextType().isAssignableFrom(types[0])
					&& (JPostmanInfo.class.isAssignableFrom(types[1]) || String.class.isAssignableFrom(types[1]));
		}
		return types.length == 3 && framework.contextType().isAssignableFrom(types[0])
				&& String.class.isAssignableFrom(types[1]) && String.class.isAssignableFrom(types[2]);
	}

	private Object invokeAnnotated(Object testInstance, Method method, C ctx, JPostmanInfo info) throws Exception {
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return invoke(testInstance, method);
		}
		if (types.length == 1 && framework.contextType().isAssignableFrom(types[0])) {
			return invoke(testInstance, method, ctx);
		}
		if (types.length == 1 && JPostmanInfo.class.isAssignableFrom(types[0])) {
			return invoke(testInstance, method, info);
		}
		if (types.length == 2 && JPostmanInfo.class.isAssignableFrom(types[1])) {
			return invoke(testInstance, method, ctx, info);
		}
		if (types.length == 2) {
			return invoke(testInstance, method, ctx, info.method);
		}
		return invoke(testInstance, method, ctx, info.method, info.request);
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

	private String[] dependencies(JPostmanRequest annotation) {
		return dependencies(annotation.dependsOn());
	}

	private String[] dependencies(String[] values) {
		return dependencies("", values);
	}

	private String[] dependencies(String value, String[] values) {
		List<String> result = new ArrayList<>();
		if (value != null && !value.isBlank()) {
			result.add(value.trim());
		}
		if (values != null) {
			Arrays.stream(values).filter(v -> v != null && !v.isBlank()).map(String::trim).forEach(result::add);
		}
		return result.toArray(String[]::new);
	}

	private IllegalStateException circularDependency(List<String> stack, String next) {
		List<String> chain = new ArrayList<>(stack);
		chain.add(next);
		return new IllegalStateException(
				"Circular JPostman dependency detected.\n\nDependency chain:\n" + String.join(" -> ", chain)
						+ "\n\nA JPostman dependency chain cannot call a method that is already running.");
	}

	private boolean isCached(C ctx, String key) {
		try {
			return framework.hasCache(ctx, key);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
