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
import io.jpostman.annotations.JPostmanData;
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
		JPostmanData dataAnnotation = testMethod.getAnnotation(JPostmanData.class);

		if (requestAnnotation == null && responseAnnotation == null && runnerAnnotation == null) {
			framework.clearCurrent();
			return;
		}

		if (prepared.isEmpty()) {
			framework.clearCurrent();
			return;
		}

		JPostmanInfo info = info(testMethod.getName(), requestAnnotation, responseAnnotation, runnerAnnotation);
		PreparedContext<C> current = prepared.resolve(info.namespace);
		framework.setCurrent(current.context);
		List<String> stack = new ArrayList<>();
		stack.add(testMethod.getName());
		add(report, info);
		info.methods.add(testMethod.getName());
		debug(testInstance, info);

		if (requestAnnotation != null) {
			runAnnotatedRequest(testInstance, prepared, current.collection, requestAnnotation, info, dataAnnotation,
					stack);
		}

		if (responseAnnotation != null) {
			C currentContext = runAnnotatedResponse(testInstance, prepared, current.collection, responseAnnotation,
					info, dataAnnotation, stack);
			if (hasResponseExecution(responseAnnotation, info, assertAnnotation)) {
				executeResponse(testInstance, prepared, currentContext, responseAnnotation, info, assertAnnotation,
						stack);
			}
		}

		if (runnerAnnotation != null) {
			runDependencies(testInstance, prepared, dependencies(runnerAnnotation.dependsOn()), info, stack);
			applyData(testInstance, prepared, info, dataAnnotation, stack);
			executeRunner(testInstance, prepared, runnerAnnotation, info, assertAnnotation, stack);
		}
	}

	private interface DependencyAction {
		void run() throws Exception;
	}

	private void applyData(Object testInstance, PreparedContexts<C> prepared, JPostmanInfo info,
			JPostmanData annotation, List<String> stack) throws Exception {
		if (annotation == null) {
			return;
		}

		JPostmanDataLoader.apply(testInstance, prepared.context(info.namespace), framework, info, annotation);
		runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()), info, stack);
	}

	private void runAnnotatedRequest(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanRequest annotation, JPostmanInfo info, JPostmanData dataAnnotation, List<String> stack)
			throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		applyData(testInstance, prepared, info, dataAnnotation, stack);
		runDependencies(testInstance, prepared, dependencies(annotation), info, stack);

		ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
	}

	private C runAnnotatedResponse(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanResponse annotation, JPostmanInfo info, JPostmanData dataAnnotation, List<String> stack)
			throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()), info, stack);
		applyData(testInstance, prepared, info, dataAnnotation, stack);

		ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		return ctx;
	}

	private boolean hasResponseExecution(JPostmanResponse annotation, JPostmanInfo info,
			JPostmanAssert assertAnnotation) {
		if (info.request != null && !info.request.isBlank()) {
			return true;
		}

		if (assertAnnotation != null || shouldVerify(annotation.verify())
				|| !value(annotation.cache()).trim().isBlank()) {
			throw new IllegalStateException("@JPostmanResponse request is required when using response execution, "
					+ "verification, assertions, or cache. Dependencies do not provide request location. "
					+ "Set namespace, folder, and request directly on the @JPostmanResponse: " + value(info.callee));
		}

		return false;
	}

	private JPostmanInfo info(String methodName, JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			return new JPostmanInfo(value(requestAnnotation.id()), requestAnnotation.executor(), methodName,
					requestAnnotation.namespace(), requestAnnotation.folder(), requestAnnotation.request())
					.annotation("@JPostmanRequest");
		}

		if (responseAnnotation != null) {
			return new JPostmanInfo(value(responseAnnotation.id()), responseAnnotation.executor(), methodName,
					responseAnnotation.namespace(), responseAnnotation.folder(), responseAnnotation.request())
					.annotation("@JPostmanResponse");
		}

		return new JPostmanInfo(value(runnerAnnotation.id()), runnerAnnotation.executor(), methodName,
				runnerAnnotation.namespace(), runnerAnnotation.folder(), "").annotation("@JPostmanRunner");
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

		JPostmanInfo info = parentInfo.child(dependencyMethod.getName(), annotation.id(), annotation.executor(), "",
				annotation.namespace(), annotation.folder(), annotation.request()).annotation("@JPostmanResponse");
		add(report(testInstance), info);

		String cache = value(annotation.cache()).trim();
		Object cached = cachedResponseValue(resolver, info, cache);
		if (cached != null) {
			if (framework.contextType().isInstance(cached)) {
				C cachedContext = framework.contextType().cast(cached);
				resolver.update(info.namespace, cachedContext);
				framework.setCurrent(cachedContext);
			}
			return;
		}

		C ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation, info);
		resolver.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info, stack);
		applyData(testInstance, resolver, info, dependencyMethod.getAnnotation(JPostmanData.class), stack);
		ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation, info);
		resolver.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		executeResponse(testInstance, resolver, ctx, annotation, info,
				dependencyMethod.getAnnotation(JPostmanAssert.class), stack);
		Object value = invokeAnnotated(testInstance, dependencyMethod, resolver.context(info.namespace), info);
		cacheResponseDependencyResult(resolver, dependencyMethod, info, cache, value);
	}

	private void runRunnerDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRunner annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		JPostmanInfo info = parentInfo.child(dependencyMethod.getName(), "", annotation.executor(), "",
				annotation.namespace(), annotation.folder(), parentInfo.request).annotation("@JPostmanRunner");
		applyData(testInstance, resolver, info, dependencyMethod.getAnnotation(JPostmanData.class), stack);
		add(report(testInstance), info);
		runCachedDependency(testInstance, resolver, dependencyMethod, info, "", () -> {
			runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info, stack);
			executeRunner(testInstance, resolver, annotation, info,
					dependencyMethod.getAnnotation(JPostmanAssert.class), stack);
		});
	}

	private void runRequestDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRequest annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		String cache = value(annotation.cache());
		JPostmanInfo dependencyInfo = parentInfo
				.child(dependencyMethod.getName(), annotation.id(), annotation.executor(), cache,
						annotation.namespace(), annotation.folder(), annotation.request())
				.annotation("@JPostmanRequest");
		JPostmanReport report = report(testInstance);
		add(report, dependencyInfo);

		String requestNamespace = value(annotation.namespace());
		String requestFolder = value(annotation.folder());
		String requestName = value(annotation.request());
		/*
		 * Request helpers use their own annotation namespace for execution and cache
		 * storage. Blank namespace means the default @JPostmanTestContext, even when
		 * the parent response is running under another namespace such as product.
		 */
		String contextNamespace = requestNamespace;

		/*
		 * The helper receives invocation info. Add the current helper to the shared
		 * chain before invocation so info.print() shows caller/callee and the full path
		 * including the current method. This also keeps the invocation chain consistent
		 * when the current helper is skipped because its own cache key already exists.
		 */
		parentInfo.methods.add(dependencyMethod.getName());

		boolean cached = cache != null && !cache.isBlank() && isCached(resolver.context(contextNamespace), cache);
		if (!cached) {
			C ctx = prepareRequest(resolver.context(contextNamespace), resolver.collection(contextNamespace),
					annotation, dependencyInfo, requestNamespace, requestFolder, requestName);
			resolver.update(contextNamespace, ctx);
			framework.setCurrent(ctx);

			applyData(testInstance, resolver, dependencyInfo, dependencyMethod.getAnnotation(JPostmanData.class),
					stack);
			runDependencies(testInstance, resolver, dependencies(annotation), dependencyInfo, stack);

			ctx = prepareRequest(resolver.context(contextNamespace), resolver.collection(contextNamespace), annotation,
					dependencyInfo, requestNamespace, requestFolder, requestName);
			resolver.update(contextNamespace, ctx);
			framework.setCurrent(ctx);

			try {
				C requestContext = resolver.context(contextNamespace);
				Object value = invokeAnnotated(testInstance, dependencyMethod, requestContext, dependencyInfo);
				verifyRequestResponse(testInstance, requestContext);
				cacheDependencyResult(resolver, contextNamespace, dependencyMethod, dependencyInfo, cache, value);
				add(report, dependencyInfo);
			} catch (Exception | Error e) {
				add(report, dependencyInfo);
				throw e;
			}
		}

		runDependency(testInstance, resolver, annotation.next(), dependencyInfo, stack);
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

	private void cacheResponseDependencyResult(PreparedContexts<C> resolver, Method dependencyMethod, JPostmanInfo info,
			String cache, Object value) {
		if (cache == null || cache.isBlank()) {
			return;
		}

		Object cacheValue = dependencyMethod.getReturnType() == Void.TYPE ? resolver.context(info.namespace) : value;
		if (cacheValue == null) {
			throw new IllegalStateException(
					"Dependency method returned null and cannot be cached: " + dependencyMethod.getName()
							+ ". Return a non-null value, or use void to cache the response context.");
		}

		for (C context : resolver.contexts()) {
			framework.cache(context, cache, cacheValue);
		}
	}

	private void cacheDependencyResult(PreparedContexts<C> resolver, Method dependencyMethod, JPostmanInfo info,
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

		for (C context : resolver.contexts()) {
			framework.cache(context, cache, cacheValue);
		}
	}

	private void cacheDependencyResult(PreparedContexts<C> resolver, String contextNamespace, Method dependencyMethod,
			JPostmanInfo info, String cache, Object value) {
		if (cache == null || cache.isBlank()) {
			return;
		}

		Object cacheValue = dependencyMethod.getReturnType() == Void.TYPE ? VOID_DEPENDENCY_MARKER : value;
		if (cacheValue == null) {
			throw new IllegalStateException("Dependency method returned null and cannot be cached: "
					+ dependencyMethod.getName() + ". Use void for setup-only dependencies, "
					+ "or return a non-null value when another request needs the cached value.");
		}
		framework.cache(resolver.context(contextNamespace), cache, cacheValue);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, JPostmanInfo info) {
		return prepareRequest(context, collection, annotation, info, info.namespace, info.folder, info.request);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, JPostmanInfo info,
			String namespace, String folder, String requestName) {
		C result = applyRuleAndFilter(context, annotation.rule());
		if (requestName == null || requestName.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, namespace, folder, requestName));
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation, JPostmanInfo info) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		if (info.request == null || info.request.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, info.namespace, info.folder, info.request));
	}

	private C prepareRequest(C context, Collection collection, JPostmanRunner annotation, JPostmanInfo info,
			String requestName) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return requestWithCache(result, request(collection, info.namespace, annotation.folder(), requestName));
	}

	private C requestWithCache(C context, Request request) {
		C result = framework.request(context, request);
		framework.copyCache(context, result);
		return result;
	}

	private C applyRuleAndFilter(C context, String rule, String... filter) {
		C result = context;
		if (rule != null && !rule.isBlank()) {
			C previous = result;
			result = framework.loadRules(result, rule);
			framework.copyCache(previous, result);
		}
		if (filter != null && filter.length > 0) {
			C previous = result;
			result = framework.filter(result, filter);
			framework.copyCache(previous, result);
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
		List<Throwable> failures = new ArrayList<>();

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

			JPostmanInfo requestInfo = info.child(info.callee, info.namespace, info.folder, requestName)
					.annotation("@JPostmanRunner");
			add(report, requestInfo);
			C ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, requestName);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			try {
				executeRunnerResponse(testInstance, resolver, ctx, annotation, requestInfo, assertAnnotation, stack);
			} catch (Exception | Error e) {
				if (!annotation.soft()) {
					throw e;
				}
				failures.add(locationError(requestInfo, e));
			}
		}

		if (!failures.isEmpty()) {
			throw combinedRunnerError(failures);
		}
	}

	private void executeRunnerResponse(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanRunner annotation, JPostmanInfo info, JPostmanAssert assertAnnotation, List<String> stack)
			throws Exception {

		Method executor = findExecutor(testInstance.getClass(), info.executor, info);
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);
		JPostmanInfo executorInfo = info.child(executor.getName(), executorAnnotation.id(), info.executor,
				info.namespace, info.folder, info.request).annotation("@JPostmanExecutor");
		info.methods.add(executor.getName());
		add(report(testInstance), executorInfo);

		Collection collection = resolver.collection(info.namespace);
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName, executorInfo, stack);
			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, info.request);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
		}

		JPostmanReport report = report(testInstance);
		try {
			Object result = invokeExecutor(testInstance, executor, ctx, executorInfo);
			verifyExecutorResult(result, executor);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			debug(testInstance, info);
			if (assertAnnotation != null) {
				assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, info.request, annotation.soft(),
						log(testInstance, annotation.log()));
			} else {
				verifyResponse(testInstance, ctx, annotation.verify(), annotation.soft(), annotation.log());
			}
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw e;
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			JPostmanInfo info, JPostmanAssert assertAnnotation, List<String> stack) throws Exception {

		Method executor = findExecutor(testInstance.getClass(), info.executor, info);
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);
		JPostmanInfo executorInfo = info.child(executor.getName(), executorAnnotation.id(), info.executor,
				info.namespace, info.folder, info.request).annotation("@JPostmanExecutor");
		info.methods.add(executor.getName());
		add(report(testInstance), executorInfo);

		Collection collection = resolver.collection(info.namespace);
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName, executorInfo, stack);
			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
		}

		JPostmanReport report = report(testInstance);
		try {
			Object result = invokeExecutor(testInstance, executor, ctx, executorInfo);
			verifyExecutorResult(result, executor);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			debug(testInstance, info);
			if (assertAnnotation != null) {
				assertionRunner.apply(testInstance.getClass(), ctx, assertAnnotation, info.request, annotation.soft(),
						log(testInstance, annotation.log()));
			} else {
				verifyResponse(testInstance, ctx, annotation.verify(), annotation.soft(), annotation.log());
			}
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw e;
		}
	}

	private void verifyResponse(Object testInstance, C ctx, int annotationVerify, boolean soft, boolean annotationLog) {
		int statusCode = statusCode(testInstance, annotationVerify);
		if (statusCode >= 0) {
			framework.verify(ctx, statusCode, soft, log(testInstance, annotationLog));
		}
	}

	private void verifyRequestResponse(Object testInstance, C ctx) {
		int statusCode = statusCode(testInstance, -1);
		if (statusCode >= 0 && framework.hasResponse(ctx)) {
			framework.verify(ctx, statusCode, false, log(testInstance, false));
		}
	}

	private int statusCode(Object testInstance, int annotationVerify) {
		return JPostmanRuntimeOptions.from(testInstance).statusCode(annotationVerify);
	}

	private boolean shouldVerify(int verify) {
		return verify >= 0;
	}

	private Object cachedResponseValue(PreparedContexts<C> resolver, JPostmanInfo info, String cache) {
		if (cache == null || cache.isBlank()) {
			return null;
		}

		for (C context : resolver.contexts()) {
			Object value = framework.cache(context, cache);
			if (value != null) {
				return value;
			}
		}

		return null;
	}

	private AssertionError locationError(JPostmanInfo info, Throwable cause) {
		StringBuilder message = new StringBuilder();

		message.append("JPostman location: namespace=").append(value(info.namespace)).append(", folder=")
				.append(value(info.folder)).append(", request=").append(value(info.request));

		String causeMessage = cause == null ? "" : value(cause.getMessage()).trim();

		if (!causeMessage.isBlank()) {
			message.append(System.lineSeparator()).append(causeMessage);
		}

		AssertionError error = new AssertionError(message.toString());

		if (cause != null) {
			error.initCause(cause);
		}

		return error;
	}

	private AssertionError combinedRunnerError(List<Throwable> failures) {
		StringBuilder message = new StringBuilder();

		message.append("JPostman runner failed for ").append(failures.size())
				.append(failures.size() == 1 ? " request." : " requests.");

		for (Throwable failure : failures) {
			String failureMessage = failure == null ? "" : value(failure.getMessage()).trim();

			if (!failureMessage.isBlank()) {
				message.append(System.lineSeparator()).append(System.lineSeparator()).append(failureMessage);
			}
		}

		return new AssertionError(message.toString());
	}

	private boolean log(Object testInstance, boolean annotationLog) {
		return JPostmanRuntimeOptions.from(testInstance).log(annotationLog);
	}

	private void debug(Object testInstance, JPostmanInfo info) {
		JPostmanRuntimeOptions.from(testInstance).debug(testInstance, info);
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
			report.update(info);
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

	private Method findExecutor(Class<?> type, String requestedName, JPostmanInfo info) {
		String requested = value(requestedName).trim();
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

				String executorName = annotation.id();
				if (!requested.isBlank() && requested.equals(executorName)) {
					return method;
				}

				all.add(method);
				if (annotation.id().isBlank()) {
					unnamed.add(method);
				}
			}
			current = current.getSuperclass();
		}

		if (!requested.isBlank()) {
			throw new IllegalStateException("JPostman executor not found: " + requested + infoSuffix(info));
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

		throw new IllegalStateException("Default JPostman executor not found. "
				+ "Add one unnamed @JPostmanExecutor or specify executor = name." + infoSuffix(info));
	}

	private String infoSuffix(JPostmanInfo info) {
		if (info == null) {
			return "";
		}
		return " (id=" + label(info.id) + ", callerId=" + label(info.callerId) + ", caller=" + label(info.caller)
				+ ", callee=" + label(info.callee) + ", namespace=" + label(info.namespace) + ", folder="
				+ label(info.folder) + ", request=" + label(info.request) + ", executor=" + label(info.executor) + ")";
	}

	private String label(String value) {
		return value == null || value.isBlank() ? "<default>" : value;
	}

	private Object invokeExecutor(Object testInstance, Method executor, C ctx, JPostmanInfo info) throws Exception {
		debug(testInstance, info);
		Class<?>[] types = executor.getParameterTypes();
		if (types.length == 2) {
			return invoke(testInstance, executor, ctx, info);
		}
		return invoke(testInstance, executor, ctx);
	}

	private void validateExecutorMethod(Method method) {
		Class<?>[] types = method.getParameterTypes();
		boolean valid = types.length == 1 && framework.contextType().isAssignableFrom(types[0]);
		valid = valid || (types.length == 2 && framework.contextType().isAssignableFrom(types[0])
				&& JPostmanInfo.class.isAssignableFrom(types[1]));

		if (valid) {
			return;
		}

		String contextName = framework.contextType().getSimpleName();
		throw new IllegalStateException("@JPostmanExecutor method must accept " + contextName + " or " + contextName
				+ ", JPostmanInfo: " + method.getName());
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
		debug(testInstance, info);
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
			return invoke(testInstance, method, ctx, info.callee);
		}
		return invoke(testInstance, method, ctx, info.callee, info.request);
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
