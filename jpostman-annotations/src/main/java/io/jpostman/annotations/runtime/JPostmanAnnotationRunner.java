package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.jpostman.ApiExecutor;
import io.jpostman.Collection;
import io.jpostman.Request;
import io.jpostman.annotations.JPostman;
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
	private static final String NO_CACHE = JPostmanRequest.NO_CACHE;
	private static final String ID_PREFIX = "#";

	private final JPostmanFramework<C> framework;
	private final JPostmanContextRunner<C> contextRunner;
	private final JPostmanAssertionRunner<C> assertionRunner;
	private final JPostmanRequestDiscovery requestDiscovery;
	private final Map<String, ApiExecutor> sessionExecutors = new LinkedHashMap<>();

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
		validateExecutors(testInstance);
		injectReportContext(testInstance);
		PreparedContexts<C> prepared = contextRunner.prepare(testInstance);
		contextRunner.activate(testInstance, prepared);
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
		validateExecutors(testInstance);
		JPostmanReport report = injectReportContext(testInstance);
		PreparedContexts<C> prepared = contextRunner.prepare(testInstance);
		contextRunner.activate(testInstance, prepared);
		contextRunner.injectLoadedContexts(testInstance, prepared);

		JPostmanRequest requestAnnotation = JPostmanAnnotations.request(testMethod);
		JPostmanResponse responseAnnotation = JPostmanAnnotations.response(testMethod);
		JPostmanRunner runnerAnnotation = JPostmanAnnotations.runner(testMethod);
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
		info = info.context(current.contextAnnotation);
		prepared.info(info);
		framework.setCurrent(current.context);
		List<String> stack = new ArrayList<>();
		stack.add(testMethod.getName());
		add(report, info);
		info.method(testMethod.getName());
		debug(testInstance, info);

		try {
			if (responseAnnotation != null) {
				validateResponseSkipEnabled(responseAnnotation, info);
				if (skipTopLevelResponse(responseAnnotation, info)) {
					skipped(report, info);
					throw JPostmanErrors.skip(framework, info, responseSkipLines(responseAnnotation, info));
				}
			}

			if (runnerAnnotation != null && skipTopLevelRunner(runnerAnnotation, info)) {
				skipped(report, info);
				throw JPostmanErrors.skip(framework, info, runnerSkipLines(runnerAnnotation, info));
			}

			if (requestAnnotation != null) {
				runAnnotatedRequest(testInstance, prepared, current.collection, requestAnnotation, info,
						requestAnnotation.data(), stack);
			}

			if (responseAnnotation != null) {
				C currentContext = runAnnotatedResponse(testInstance, prepared, current.collection, responseAnnotation,
						info, responseAnnotation.data(), stack);
				if (hasResponseExecution(responseAnnotation, info)) {
					executeResponse(testInstance, prepared, currentContext, responseAnnotation, info, stack);
				}
				prepared.info(info);
				add(report, info);
			}

			if (runnerAnnotation != null) {
				runDependencies(testInstance, prepared, dependencies(runnerAnnotation.dependsOn()),
						info.withTags(runnerAnnotation.tags()), stack);
				executeRunner(testInstance, prepared, runnerAnnotation, info, stack);
			}
		} catch (Exception | Error e) {
			if (isFrameworkSkip(e)) {
				skippedIfMissing(report, info);
			} else {
				failedIfMissing(report, info);
			}
			throw e;
		} finally {
			contextRunner.injectTestContexts(testInstance, prepared);
			contextRunner.injectLoadedContexts(testInstance, prepared);
		}
	}

	private boolean isFrameworkSkip(Throwable throwable) {
		Throwable current = throwable;
		for (int depth = 0; current != null && depth < 20; depth++) {
			if (isFrameworkSkipClass(current)) {
				return true;
			}

			Throwable next = current instanceof InvocationTargetException
					? ((InvocationTargetException) current).getCause()
					: current.getCause();

			if (next == current) {
				break;
			}

			current = next;
		}
		return false;
	}

	private boolean isFrameworkSkipClass(Throwable throwable) {
		String name = throwable.getClass().getName();
		return "org.testng.SkipException".equals(name) || "org.opentest4j.TestAbortedException".equals(name);
	}

	private interface DependencyAction {
		void run() throws Exception;
	}

	private void applyData(Object testInstance, PreparedContexts<C> prepared, JPostmanInfo info, String data,
			List<String> stack) throws Exception {
		if (data == null || data.isBlank()) {
			return;
		}

		PreparedContext<C> current = prepared.resolve(info.namespace);
		JPostmanDataLoader.apply(testInstance, current.context, framework, info, data, current.dataloadLocations);
	}

	private void runAnnotatedRequest(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanRequest annotation, JPostmanInfo info, String data, List<String> stack) throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		applyData(testInstance, prepared, info, data, stack);
		runDependencies(testInstance, prepared, dependencies(annotation), info.withTags(annotation.tags()), stack);

		ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
	}

	private C runAnnotatedResponse(Object testInstance, PreparedContexts<C> prepared, Collection collection,
			JPostmanResponse annotation, JPostmanInfo info, String data, List<String> stack) throws Exception {

		C ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()), info.withTags(annotation.tags()),
				stack);
		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		applyData(testInstance, prepared, info, data, stack);

		ctx = prepareRequest(prepared.context(info.namespace), prepared.collection(info.namespace), annotation, info);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		return ctx;
	}

	private boolean hasResponseExecution(JPostmanResponse annotation, JPostmanInfo info) {
		if (info.request != null && !info.request.isBlank()) {
			return true;
		}

		if (hasAssertions(annotation.asserts()) || shouldVerify(annotation.verify())
				|| cacheRequested(annotation.cache())) {
			throw JPostmanErrors.usage(info, "@JPostmanResponse request is required when using response execution,",
					"verification, assertions, or cache.",
					"Set namespace, folder, and request directly on the @JPostmanResponse,",
					"or depend on a @JPostmanRequest method that defines the request location: " + value(info.method));
		}

		return false;
	}

	private void inheritResponseLocationFromDependencies(Object testInstance, JPostmanResponse annotation,
			JPostmanInfo info) {
		if (annotation == null || info == null || !isBlank(info.request)) {
			return;
		}

		inheritResponseLocationFromDependencies(testInstance, dependencies(annotation.dependsOn()), info,
				new LinkedHashSet<>());
	}

	private boolean inheritResponseLocationFromDependencies(Object testInstance, String[] dependencyNames,
			JPostmanInfo info, Set<String> visited) {
		if (dependencyNames == null || dependencyNames.length == 0) {
			return false;
		}

		for (String dependencyName : dependencyNames) {
			String name = value(dependencyName).trim();
			if (name.isBlank() || !visited.add(name)) {
				continue;
			}

			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), name, info);
			JPostmanRequest requestAnnotation = JPostmanAnnotations.request(dependencyMethod);
			if (requestAnnotation != null && !isBlank(requestAnnotation.request())) {
				info.location(requestAnnotation.namespace(), requestAnnotation.folder(), requestAnnotation.request())
						.requestId(requestAnnotation.id());
				return true;
			}

			JPostmanResponse responseAnnotation = JPostmanAnnotations.response(dependencyMethod);
			if (responseAnnotation != null) {
				if (!isBlank(responseAnnotation.request())) {
					info.location(responseAnnotation.namespace(), responseAnnotation.folder(),
							responseAnnotation.request()).requestId(responseAnnotation.id());
					return true;
				}
				if (inheritResponseLocationFromDependencies(testInstance, dependencies(responseAnnotation.dependsOn()),
						info, visited)) {
					return true;
				}
			}

		}

		return false;
	}

	private JPostmanInfo info(String methodName, JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			JPostmanInfo info = new JPostmanInfo(requestAnnotation.tags(), requestAnnotation.executor(), methodName,
					requestAnnotation.namespace(), requestAnnotation.folder(), requestAnnotation.request())
					.annotation("@JPostmanRequest").id(requestAnnotation.id()).debug(requestAnnotation.logLevel());
			if (!isBlank(requestAnnotation.request())) {
				info.requestId(requestAnnotation.id());
			}
			return info;
		}

		if (responseAnnotation != null) {
			return new JPostmanInfo(responseAnnotation.tags(), responseAnnotation.executor(), methodName,
					responseAnnotation.namespace(), responseAnnotation.folder(), responseAnnotation.request())
					.annotation("@JPostmanResponse").id(responseAnnotation.id()).debug(responseAnnotation.logLevel());
		}

		return new JPostmanInfo(runnerAnnotation.tags(), runnerAnnotation.executor(), methodName,
				runnerAnnotation.namespace(), runnerAnnotation.folder(), "").annotation("@JPostmanRunner")
				.debug(runnerAnnotation.logLevel());
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
			List<String> chain = new ArrayList<>(stack);
			chain.add(name);
			throw JPostmanErrors.usage(parentInfo, "Circular JPostman dependency detected.",
					"Dependency chain: " + String.join(" -> ", chain),
					"A JPostman dependency chain cannot call a method that is already running.");
		}

		Method dependencyMethod = findDependencyMethod(testInstance.getClass(), name, parentInfo);
		stack.add(name);

		try {
			JPostmanResponse responseAnnotation = JPostmanAnnotations.response(dependencyMethod);
			if (responseAnnotation != null) {
				runResponseDependency(testInstance, resolver, dependencyMethod, responseAnnotation, parentInfo, stack);
				return;
			}

			JPostmanRunner runnerAnnotation = JPostmanAnnotations.runner(dependencyMethod);
			if (runnerAnnotation != null) {
				runRunnerDependency(testInstance, resolver, dependencyMethod, runnerAnnotation, parentInfo, stack);
				return;
			}

			JPostmanRequest requestAnnotation = JPostmanAnnotations.request(dependencyMethod);
			if (requestAnnotation == null) {
				throw JPostmanErrors.usage(parentInfo,
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

		String cache = cacheKey(dependencyMethod, annotation.cache());
		/*
		 * Response dependencies must use their own annotation location exactly. A blank
		 * namespace/folder on @JPostmanResponse means the default context/root folder,
		 * not the parent chain's current product namespace/folder.
		 */
		JPostmanInfo info = parentInfo
				.childExact(dependencyMethod.getName(), new String[0], annotation.executor(), cache,
						annotation.namespace(), annotation.folder(), annotation.request())
				.annotation("@JPostmanResponse").id(annotationId(annotation.id())).debug(annotation.logLevel());
		info = info.context(resolver.resolve(info.namespace).contextAnnotation);
		resolver.info(info);
		JPostmanReport report = report(testInstance);

		validateResponseSkipEnabled(annotation, info);
		if (skipResponse(annotation)) {
			info.method(dependencyMethod.getName());
			add(report, info);
			skipped(report, info);
			throw JPostmanErrors.skip(framework, info, responseSkipLines(annotation));
		}

		Object cached = cachedResponseValue(resolver, info, cache);
		if (cached != null) {
			if (framework.contextType().isInstance(cached)) {
				C cachedContext = framework.contextType().cast(cached);
				resolver.update(info.namespace, cachedContext);
				framework.setCurrent(cachedContext);
			}
			resolver.info(parentInfo);
			return;
		}

		info.method(dependencyMethod.getName());
		add(report, info);

		C ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation, info);
		resolver.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info.withTags(annotation.tags()),
				stack);
		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		applyData(testInstance, resolver, info, annotation.data(), stack);
		ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation, info);
		resolver.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		executeResponse(testInstance, resolver, ctx, annotation, info, stack);
		resolver.info(info);
		add(report(testInstance), info);
		Object value = invokeAnnotated(testInstance, dependencyMethod, resolver.context(info.namespace), info);
		cacheResponseDependencyResult(resolver, dependencyMethod, info, cache, value);
		resolver.info(parentInfo);
	}

	private void runRunnerDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRunner annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		JPostmanInfo info = parentInfo
				.child(dependencyMethod.getName(), new String[0], annotation.executor(), "", annotation.namespace(),
						annotation.folder(), parentInfo.request)
				.annotation("@JPostmanRunner").debug(annotation.logLevel());
		JPostmanInfo runnerInfo = info.context(resolver.resolve(info.namespace).contextAnnotation);
		runnerInfo.method(dependencyMethod.getName());
		resolver.info(runnerInfo);
		applyData(testInstance, resolver, runnerInfo, annotation.data(), stack);
		add(report(testInstance), runnerInfo);
		runCachedDependency(testInstance, resolver, dependencyMethod, runnerInfo, "", () -> {
			runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()),
					runnerInfo.withTags(annotation.tags()), stack);
			executeRunner(testInstance, resolver, annotation, runnerInfo, stack);
		});
	}

	private JPostmanInfo requestDependencyInfo(JPostmanInfo parentInfo, Method dependencyMethod,
			JPostmanRequest annotation, String cache) {
		/*
		 * A request helper that owns a request attribute gets its own exact request
		 * location. Blank namespace on that helper means the default namespace while the
		 * helper runs. Helpers without a request continue to inherit the parent context
		 * so they can act as generic setup/tag helpers.
		 */
		JPostmanInfo info = isBlank(annotation.request())
				? parentInfo.child(dependencyMethod.getName(), new String[0], annotation.executor(), cache,
						annotation.namespace(), annotation.folder(), annotation.request())
				: parentInfo.childExact(dependencyMethod.getName(), new String[0], annotation.executor(), cache,
						annotation.namespace(), annotation.folder(), annotation.request());

		info = info.annotation("@JPostmanRequest").id(annotationId(annotation.id())).debug(annotation.logLevel());
		if (!isBlank(annotation.request())) {
			info.requestId(annotation.id());
		}
		return info;
	}

	private void runRequestDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRequest annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		String cache = cacheKey(dependencyMethod, annotation.cache());
		JPostmanInfo dependencyInfo = requestDependencyInfo(parentInfo, dependencyMethod, annotation, cache);
		resolver.info(dependencyInfo);
		JPostmanReport report = report(testInstance);
		add(report, dependencyInfo);

		if (skipRequest(annotation)) {
			/*
			 * When @JPostmanRequest/@JPostman.Request is reached through dependsOn,
			 * skip=true means skip this helper dependency only. The request name is
			 * optional in this path because the helper may only exist to select tags or
			 * block a dependency branch. Runner request skipping is handled separately by
			 * JPostmanRequestDiscovery, where request/folder/namespace are used to match
			 * collection requests.
			 */
			dependencyInfo.method(dependencyMethod.getName());
			return;
		}
		/*
		 * Request dependencies are request helpers. Blank namespace/folder/request
		 * values inherit the parent request location through dependencyInfo. Use the
		 * resolved location consistently for both the context passed into the helper
		 * method and the request prepared before/after nested dependencies.
		 */
		String contextNamespace = dependencyInfo.namespace;
		String contextFolder = dependencyInfo.folder;
		String contextRequest = dependencyInfo.request;
		dependencyInfo = dependencyInfo.context(resolver.resolve(contextNamespace).contextAnnotation);

		/*
		 * The helper receives invocation info. Add the current helper to the shared
		 * chain before invocation so info.print() shows method/methodIndex and the
		 * full path including the current method. This also keeps the invocation chain consistent
		 * when the current helper is skipped because its own cache key already exists.
		 */
		dependencyInfo.method(dependencyMethod.getName());

		boolean cached = cache != null && !cache.isBlank() && isCached(resolver.context(contextNamespace), cache);
		if (!cached) {
			C ctx = prepareRequest(resolver.context(contextNamespace), resolver.collection(contextNamespace),
					annotation, dependencyInfo, contextNamespace, contextFolder, contextRequest);
			resolver.update(contextNamespace, ctx);
			framework.setCurrent(ctx);

			applyData(testInstance, resolver, dependencyInfo, annotation.data(), stack);
			runDependencies(testInstance, resolver, dependencies(annotation),
					dependencyInfo.withTags(annotation.tags()), stack);

			ctx = prepareRequest(resolver.context(contextNamespace), resolver.collection(contextNamespace), annotation,
					dependencyInfo, contextNamespace, contextFolder, contextRequest);
			resolver.update(contextNamespace, ctx);
			framework.setCurrent(ctx);

			try {
				C requestContext = resolver.context(contextNamespace);
				Object value = invokeAnnotated(testInstance, dependencyMethod, requestContext, dependencyInfo);
				verifyRequestResponse(testInstance, requestContext, dependencyInfo);
				cacheDependencyResult(resolver, contextNamespace, dependencyMethod, dependencyInfo, cache, value);
				add(report, dependencyInfo);
			} catch (Exception | Error e) {
				add(report, dependencyInfo);
				throw e;
			}
		}

		resolver.info(parentInfo);
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
			throw JPostmanErrors.usage(info,
					"Dependency method returned null and cannot be cached: " + dependencyMethod.getName(),
					"Return a non-null value, or use void to cache the response context.");
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
			throw JPostmanErrors.usage(info,
					"Dependency method returned null and cannot be cached: " + dependencyMethod.getName(),
					"Use void for setup-only dependencies, "
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
			throw JPostmanErrors.usage(info,
					"Dependency method returned null and cannot be cached: " + dependencyMethod.getName(),
					"Use void for setup-only dependencies, "
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
		return requestWithCache(result, request(collection, namespace, folder, requestName), info);
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation, JPostmanInfo info) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		if (info.request == null || info.request.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, info.namespace, info.folder, info.request), info);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRunner annotation, JPostmanInfo info,
			String requestName) {
		C result = applyRuleAndFilter(context, annotation.rule(), annotation.filter());
		return requestWithCache(result, request(collection, info.namespace, annotation.folder(), requestName), info);
	}

	private C requestWithCache(C context, Request request, JPostmanInfo info) {
		C result = framework.request(context, request, info);
		framework.copyCache(context, result);
		return result;
	}

	private C applyFilter(C context, String... filter) {
		C result = context;
		if (filter != null && filter.length > 0) {
			C previous = result;
			result = framework.filter(result, filter);
			framework.copyCache(previous, result);
		}
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
			JPostmanInfo info, List<String> stack) throws Exception {

		JPostmanReport report = report(testInstance);
		Collection collection = resolver.collection(info.namespace);
		List<String> requestNames = requestDiscovery.runnerRequestNames(collection, info.folder);
		Set<String> includes = requestDiscovery.normalizeNames(annotation.include());
		Set<String> excludes = requestDiscovery.normalizeNames(annotation.exclude());
		List<String> skipped = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		int executed = 0;

		if (requestNames.isEmpty()) {
			warnNoRunnerRequests(testInstance, info);
			return;
		}

		// Validate the executor before walking requests. This prevents a runner from
		// silently passing when the collection has requests but no default executor.
		// Use executorCall instead of findExecutor so context/properties default
		// executors are accepted for @JPostmanRunner too.
		executorCall(testInstance, resolver.context(info.namespace), info);

		for (String requestName : requestNames) {
			if (!includes.isEmpty() && !includes.contains(requestName)) {
				skipped.add(requestName + " (not listed in include)");
				continue;
			}

			if (excludes.contains(requestName)) {
				skipped.add(requestName + " (listed in exclude)");
				continue;
			}

			if (requestDiscovery.hasExplicitResponse(testInstance.getClass(), info.namespace, info.folder,
					requestName)) {
				skipped.add(requestName + " (handled by explicit @JPostmanResponse)");
				continue;
			}

			if (requestDiscovery.hasSkippedRequest(testInstance.getClass(), info.namespace, info.folder, requestName)) {
				skipped.add(requestName + " (skipped by @JPostmanRequest)");
				continue;
			}

			JPostmanInfo requestInfo = info.child(info.method, info.namespace, info.folder, requestName)
					.annotation("@JPostmanRunner");
			resolver.info(requestInfo);
			add(report, requestInfo);
			C ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo, requestName);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			applyData(testInstance, resolver, requestInfo, annotation.data(), stack);
			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo, requestName);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			executed++;
			try {
				executeRunnerResponse(testInstance, resolver, ctx, annotation, requestInfo, stack);
			} catch (Exception | Error e) {
				if (!annotation.soft()) {
					throw e;
				}
				failures.add(locationError(requestInfo, e));
			}
		}

		if (executed == 0) {
			if (allRunnerRequestsHandledByExplicitResponses(requestNames, skipped)) {
				return;
			}
			throw runnerNothingExecutedError(info, requestNames, skipped);
		}

		if (!failures.isEmpty()) {
			throw combinedRunnerError(failures);
		}
	}

	private boolean allRunnerRequestsHandledByExplicitResponses(List<String> requestNames, List<String> skipped) {
		if (requestNames == null || requestNames.isEmpty() || skipped == null
				|| skipped.size() != requestNames.size()) {
			return false;
		}

		for (String reason : skipped) {
			if (reason == null || !reason.contains("(handled by explicit @JPostmanResponse)")) {
				return false;
			}
		}
		return true;
	}

	private AssertionError runnerNothingExecutedError(JPostmanInfo info, List<String> requestNames,
			List<String> skipped) {
		StringBuilder details = new StringBuilder();
		details.append("Discovered requests: ").append(requestNames.size());
		for (String requestName : requestNames) {
			details.append(JPostmanErrors.ENDL).append("- ").append(requestName);
		}
		if (!skipped.isEmpty()) {
			details.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Skip reasons:");
			for (String reason : skipped) {
				details.append(JPostmanErrors.ENDL).append("- ").append(reason);
			}
		}
		return JPostmanErrors.usage(info, "JPostman runner did not execute any requests.",
				"The target collection location contains requests, but every request was skipped before execution.",
				details.toString(),
				"Fix the @JPostmanRunner include/exclude values or remove duplicate explicit @JPostmanResponse methods.");
	}

	private void warnNoRunnerRequests(Object testInstance, JPostmanInfo info) {
		// Treat zero discovered requests as a framework-visible skip/warning instead of
		// returning normally. Returning normally lets the user test body run and TestNG
		// reports PASSED, which hides the fact that @JPostmanRunner did nothing.
		throw framework.skipException(info, "WARN JPostman runner found zero requests",
				"Check the namespace, folder, include/exclude values, or collection structure.");
	}

	private String executorStep(String executorName, JPostmanInfo info) {
		String detail = executorStepDetail(info);
		return detail.isBlank() ? executorName : executorName + "(" + detail + ")";
	}

	private String executorStepDetail(JPostmanInfo info) {
		if (info == null) {
			return "";
		}
		String id = value(info.id).trim();
		if (!id.isBlank()) {
			return id.startsWith(ID_PREFIX) ? id : ID_PREFIX + id;
		}
		String requestId = value(info.requestId).trim();
		if (!requestId.isBlank()) {
			return requestId.startsWith(ID_PREFIX) ? requestId : ID_PREFIX + requestId;
		}
		String request = value(info.request).trim();
		if (!request.isBlank()) {
			return "\"" + request.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}
		return "";
	}

	private void executeRunnerResponse(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanRunner annotation, JPostmanInfo info, List<String> stack) throws Exception {

		rejectVerifyAndAsserts(annotation, info);

		ExecutorCall<C> executor = executorCall(testInstance, ctx, info);
		resolver.info(executor.info);
		int executorIndex = info.appendMethod(executorStep(executor.name, info));
		executor.info.methodIndex(executorIndex);
		add(report(testInstance), executor.info);

		Collection collection = resolver.collection(info.namespace);
		JPostmanReport report = report(testInstance);
		try {
			enableSoft(testInstance, ctx, annotation.soft(), annotation.log());
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			for (String dependencyName : executor.dependsOn()) {
				runDependency(testInstance, resolver, dependencyName, executor.info, stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, info.request);
				enableSoft(testInstance, ctx, annotation.soft(), annotation.log());
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			Object result = executor.result(testInstance, ctx);
			verifyExecutorResult(result, executor.name, info);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
				ctx = applyFilter(ctx, annotation.filter());
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			runExecutorInterceptors(testInstance, resolver, ctx, info);
			resolver.info(info);
			add(report, info);
			debug(testInstance, info);
			applyAssertions(testInstance, resolver, ctx, info, annotation.asserts(), annotation.soft(),
					annotation.log());
			verifyResponse(testInstance, ctx, info, annotation.verify(), annotation.soft(), annotation.log());
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw executionFailure(testInstance, latestContext(resolver, info.namespace, ctx), info, e,
					annotation.log());
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			JPostmanInfo info, List<String> stack) throws Exception {

		rejectVerifyAndAsserts(annotation, info);

		ExecutorCall<C> executor = executorCall(testInstance, ctx, info);
		resolver.info(executor.info);
		int executorIndex = info.appendMethod(executorStep(executor.name, info));
		executor.info.methodIndex(executorIndex);
		add(report(testInstance), executor.info);

		Collection collection = resolver.collection(info.namespace);
		JPostmanReport report = report(testInstance);
		try {
			enableSoft(testInstance, ctx, annotation.soft(), annotation.log());
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			for (String dependencyName : executor.dependsOn()) {
				runDependency(testInstance, resolver, dependencyName, executor.info, stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
				enableSoft(testInstance, ctx, annotation.soft(), annotation.log());
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			Object result = executor.result(testInstance, ctx);
			verifyExecutorResult(result, executor.name, info);

			info.start();
			try {
				ctx = framework.response(ctx, (ApiExecutor) result);
				ctx = applyFilter(ctx, annotation.filter());
			} finally {
				info.end();
			}
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			runExecutorInterceptors(testInstance, resolver, ctx, info);
			resolver.info(info);
			add(report, info);
			debug(testInstance, info);
			applyAssertions(testInstance, resolver, ctx, info, annotation.asserts(), annotation.soft(),
					annotation.log());
			verifyResponse(testInstance, ctx, info, annotation.verify(), annotation.soft(), annotation.log());
			passed(report, info);
		} catch (Exception | Error e) {
			failed(report, info);
			throw executionFailure(testInstance, latestContext(resolver, info.namespace, ctx), info, e,
					annotation.log());
		}
	}

	private void runExecutorInterceptors(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanInfo info) throws Exception {
		for (Method method : executorInterceptors(testInstance.getClass(), info.namespace)) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
			JPostmanInfo interceptorInfo = info
					.childExact(method.getName(), new String[0], info.executor, "", info.namespace, info.folder,
							info.request)
					.annotation("@JPostmanExecutor intercept").id(annotationId(annotation.id()))
					.debug(annotation.logLevel());
			interceptorInfo = interceptorInfo.context(resolver.resolve(info.namespace).contextAnnotation);
			interceptorInfo.method(method.getName());
			resolver.info(interceptorInfo);
			invokeExecutor(testInstance, method, ctx, interceptorInfo);
		}
		resolver.info(info);
	}

	private List<Method> executorInterceptors(Class<?> type, String namespace) {
		List<Method> methods = new ArrayList<>();
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null || !isExecutorInterceptor(method)) {
					continue;
				}
				String configuredNamespace = value(annotation.namespace()).trim();
				if (!configuredNamespace.isBlank() && !configuredNamespace.equals(value(namespace))) {
					continue;
				}
				method.setAccessible(true);
				methods.add(method);
			}
			current = current.getSuperclass();
		}
		return methods;
	}

	private void rejectVerifyAndAsserts(JPostmanResponse annotation, JPostmanInfo info) {
		if (annotation != null && shouldVerify(annotation.verify()) && hasAssertions(annotation.asserts())) {
			throw JPostmanErrors.usage(info, "Invalid JPostman verification configuration.",
					"@JPostmanResponse cannot use verify and asserts together.",
					"Use verify for status-code verification, or use asserts for assertion sections.");
		}
	}

	private void rejectVerifyAndAsserts(JPostmanRunner annotation, JPostmanInfo info) {
		if (annotation != null && shouldVerify(annotation.verify()) && hasAssertions(annotation.asserts())) {
			throw JPostmanErrors.usage(info, "Invalid JPostman verification configuration.",
					"@JPostmanRunner cannot use verify and asserts together.",
					"Use verify for status-code verification, or use asserts for assertion sections.");
		}
	}

	private boolean applyAssertions(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanInfo info,
			String[] assertions, boolean soft, boolean annotationLog) throws Exception {
		PreparedContext<C> prepared = resolver.resolve(info.namespace);
		return assertionRunner.apply(ctx, prepared.assertionRules, assertions, info.request, soft,
				log(testInstance, annotationLog));
	}

	private boolean hasAssertions(String[] assertions) {
		if (assertions == null) {
			return false;
		}
		for (String assertion : assertions) {
			if (assertion != null && !assertion.isBlank()) {
				return true;
			}
		}
		return false;
	}

	private C latestContext(PreparedContexts<C> resolver, String namespace, C fallback) {
		try {
			return resolver.context(namespace);
		} catch (Exception e) {
			return fallback;
		}
	}

	private AssertionError executionFailure(Object testInstance, C ctx, JPostmanInfo info, Throwable cause,
			boolean annotationLog) {

		AssertionError assertion = assertionFailure(info, cause, log(testInstance, annotationLog));
		if (assertion != null) {
			return assertion;
		}

		String causeMessage = JPostmanErrors.stripSuffix(value(cause == null ? null : cause.getMessage())).trim();

		StringBuilder message = new StringBuilder();
		message.append("JPostman execution failed");

		if (!causeMessage.isBlank()) {
			message.append(JPostmanErrors.ENDL).append(causeMessage);
		}

		if (log(testInstance, annotationLog)) {
			String diagnostic = value(framework.diagnosticLog(ctx)).trim();
			if (!diagnostic.isBlank()) {
				message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("JPostman diagnostic log:")
						.append(JPostmanErrors.ENDL).append(diagnostic);
			}
		}

		AssertionError error = JPostmanErrors.execution(info, cause, message.toString());

		return error;
	}

	private AssertionError assertionFailure(JPostmanInfo info, Throwable cause, boolean includeLogs) {
		AssertionError assertion = findAssertionError(cause);
		if (assertion == null) {
			return null;
		}

		String message = value(assertion.getMessage());
		if (message.contains("(@JPostman")) {
			AssertionError error = new AssertionError(
					endLine(appendSuppressedMessages(message, assertion, includeLogs)));
			copyFailureDetails(assertion, error);
			return error;
		}

		String detail = endLine(
				appendSuppressedMessages(JPostmanErrors.stripSuffix(message).trim(), assertion, includeLogs));
		AssertionError error = JPostmanErrors.usage(info, detail);
		copyFailureDetails(assertion, error);
		return error;
	}

	private String appendSuppressedMessages(String message, Throwable error, boolean includeLogs) {
		StringBuilder result = new StringBuilder(value(message).stripTrailing());
		if (includeLogs) {
			appendSuppressedMessages(result, error);
		}
		return result.toString();
	}

	private void appendSuppressedMessages(StringBuilder message, Throwable error) {
		Throwable current = error;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				String suppressedMessage = value(suppressed == null ? null : suppressed.getMessage()).trim();
				if (suppressedMessage.isBlank() || containsMessage(message, suppressedMessage)) {
					continue;
				}
				if (message.length() > 0) {
					message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL);
				}
				message.append(suppressedMessage);
			}
			current = current.getCause();
		}
	}

	private boolean containsMessage(StringBuilder message, String value) {
		return message != null && value != null && message.indexOf(value) >= 0;
	}

	private void copyFailureDetails(Throwable source, AssertionError target) {
		if (source == null || target == null) {
			return;
		}
		try {
			target.initCause(source);
		} catch (IllegalStateException ignored) {
			// Keep the formatted assertion message even if the cause was already assigned.
		}
		copySuppressed(source, target);
	}

	private void copySuppressed(Throwable source, Throwable target) {
		Throwable current = source;
		while (current != null) {
			for (Throwable suppressed : current.getSuppressed()) {
				if (suppressed != null && suppressed != target) {
					target.addSuppressed(suppressed);
				}
			}
			current = current.getCause();
		}
	}

	private static String endLine(String value) {
		String result = value == null ? "" : value;
		return result.endsWith(JPostmanErrors.ENDL) ? result : result + JPostmanErrors.ENDL;
	}

	private AssertionError findAssertionError(Throwable cause) {
		Throwable current = cause;
		while (current != null) {
			if (current instanceof AssertionError) {
				return (AssertionError) current;
			}
			current = current.getCause();
		}
		return null;
	}

	private void enableSoft(Object testInstance, C ctx, boolean soft, boolean annotationLog) {
		if (soft) {
			framework.soft(ctx, log(testInstance, annotationLog));
		}
	}

	private void verifyResponse(Object testInstance, C ctx, JPostmanInfo info, int annotationVerify, boolean soft,
			boolean annotationLog) {
		int statusCode = statusCode(testInstance, annotationVerify, info);
		if (statusCode >= 1) {
			framework.verify(ctx, statusCode, soft, log(testInstance, annotationLog), info);
		}
	}

	private void verifyRequestResponse(Object testInstance, C ctx, JPostmanInfo info) {
		int statusCode = statusCode(testInstance, -1, info);
		if (statusCode >= 1 && framework.hasResponse(ctx)) {
			framework.verify(ctx, statusCode, false, log(testInstance, false), info);
		}
	}

	private int statusCode(Object testInstance, int annotationVerify, JPostmanInfo info) {
		int statusCode = JPostmanRuntimeOptions.from(testInstance).statusCode(annotationVerify);
		if ((statusCode > 0 && statusCode < 100) || statusCode > 599) {
			throw JPostmanErrors.usage(info,
					"verify status code must be between 100 and 599, or less than 1 to skip verification.");
		}
		return statusCode;
	}

	private boolean shouldVerify(int verify) {
		return verify > 0;
	}

	private String cacheKey(Method method, String rawCache) {
		String cache = value(rawCache);
		if (NO_CACHE.equals(cache)) {
			return "";
		}
		if (cache.isBlank()) {
			return method == null ? "" : "__" + method.getName() + "__";
		}
		return cache;
	}

	private boolean cacheRequested(String rawCache) {
		return !NO_CACHE.equals(value(rawCache));
	}

	private Object cachedResponseValue(PreparedContexts<C> resolver, JPostmanInfo info, String cache) {
		if (cache == null || cache.isBlank()) {
			return null;
		}

		C target = resolver.context(info.namespace);
		if (framework.hasCache(target, cache)) {
			return framework.cache(target, cache);
		}

		for (C context : resolver.contexts()) {
			if (framework.hasCache(context, cache)) {
				Object value = framework.cache(context, cache);
				if (!framework.contextType().isInstance(value)) {
					framework.cache(target, cache, value);
				}
				return value;
			}
		}

		return null;
	}

	private AssertionError locationError(JPostmanInfo info, Throwable cause) {
		StringBuilder message = new StringBuilder();

		String causeMessage = cause == null ? "" : value(cause.getMessage()).trim();
		if (!causeMessage.isBlank()) {
			message.append(JPostmanErrors.ENDL).append(causeMessage);
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
				message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append(failureMessage);
			}
		}

		return new AssertionError(message.toString() + JPostmanErrors.ENDL);
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
				if (!JPostmanAnnotations.hasReportContext(field)) {
					continue;
				}
				if (!field.getType().isAssignableFrom(JPostmanReport.class)) {
					throw JPostmanErrors.usage(null, "@JPostmanReportContext field must be JPostmanReport.",
							"Invalid field: " + field.getDeclaringClass().getSimpleName() + "." + field.getName());
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

	private void failedIfMissing(JPostmanReport report, JPostmanInfo info) {
		if (report != null && !isRecorded(report, info)) {
			report.failed(info);
		}
	}

	private void skipped(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.skipped(info);
		}
	}

	private void skippedIfMissing(JPostmanReport report, JPostmanInfo info) {
		if (report != null && !isRecorded(report, info)) {
			report.skipped(info);
		}
	}

	private boolean isRecorded(JPostmanReport report, JPostmanInfo info) {
		return report.passed.contains(info) || report.failed.contains(info) || report.skipped.contains(info);
	}

	private void validateResponseSkipEnabled(JPostmanResponse annotation, JPostmanInfo info) {
		if (annotation != null && annotation.enabled() && skipResponse(annotation)) {
			throw JPostmanErrors.usage(info, "Invalid JPostman skip configuration.",
					"enabled and skip cannot be defined on the same @JPostmanResponse annotation.",
					"Use enabled = true to override @JPostmanContext(skipAll = true),",
					"or use skip = true / skipReason to disable this response.");
		}
	}

	private boolean skipTopLevelResponse(JPostmanResponse annotation, JPostmanInfo info) {
		return skipResponse(annotation) || skipAll(info) && annotation != null && !annotation.enabled();
	}

	private boolean skipTopLevelRunner(JPostmanRunner annotation, JPostmanInfo info) {
		return annotation != null && skipAll(info) && !annotation.enabled();
	}

	private boolean skipAll(JPostmanInfo info) {
		return info != null && info.context != null && info.context.skipAll();
	}

	private boolean skipResponse(JPostmanResponse annotation) {
		return annotation != null && (annotation.skip() || !value(annotation.skipReason()).trim().isBlank());
	}

	private boolean skipRequest(JPostmanRequest annotation) {
		return annotation != null && (annotation.skip() || !value(annotation.skipReason()).trim().isBlank());
	}

	private String[] responseSkipLines(JPostmanResponse annotation) {
		return responseSkipLines(annotation, null);
	}

	private String[] responseSkipLines(JPostmanResponse annotation, JPostmanInfo info) {
		String reason = annotation == null ? "" : value(annotation.skipReason()).trim();
		if (!reason.isBlank()) {
			return new String[] { "JPostman response skipped.", reason };
		}
		if (skipAll(info) && annotation != null && !annotation.enabled()) {
			return new String[] { "JPostman response skipped.", "@JPostmanContext(skipAll = true) is enabled." };
		}
		return new String[] { "JPostman response skipped." };
	}

	private String[] runnerSkipLines(JPostmanRunner annotation, JPostmanInfo info) {
		if (skipAll(info) && annotation != null && !annotation.enabled()) {
			return new String[] { "JPostman runner skipped.", "@JPostmanContext(skipAll = true) is enabled." };
		}
		return new String[] { "JPostman runner skipped." };
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private String annotationId(String id) {
		String value = value(id).trim();
		return value.startsWith(ID_PREFIX) ? value.substring(ID_PREFIX.length()).trim() : value;
	}

	private boolean isIdReference(String value) {
		return value != null && value.trim().startsWith(ID_PREFIX);
	}

	private String idReferenceValue(String value) {
		String reference = value(value).trim();
		return reference.startsWith(ID_PREFIX) ? reference.substring(ID_PREFIX.length()).trim() : reference;
	}

	private String idReference(String id) {
		return ID_PREFIX + annotationId(id);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void validateAnnotationIds(Object testInstance) {
		Map<String, List<Method>> ids = new LinkedHashMap<>();

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				collectAnnotationId(ids, method, requestId(method));
				collectAnnotationId(ids, method, responseId(method));
				collectAnnotationId(ids, method, executorId(method));
			}
			current = current.getSuperclass();
		}

		Map<String, List<Method>> duplicateIds = new LinkedHashMap<>();
		for (Map.Entry<String, List<Method>> entry : ids.entrySet()) {
			if (entry.getValue().size() > 1 && !onlyExecutorIds(entry.getValue())) {
				duplicateIds.put(entry.getKey(), entry.getValue());
			}
		}

		if (!duplicateIds.isEmpty()) {
			throw annotationIdValidationError(duplicateIds);
		}
	}

	private boolean onlyExecutorIds(List<Method> methods) {
		for (Method method : methods) {
			if (JPostmanAnnotations.executor(method) == null || JPostmanAnnotations.request(method) != null
					|| JPostmanAnnotations.response(method) != null) {
				return false;
			}
		}
		return true;
	}

	private void collectAnnotationId(Map<String, List<Method>> ids, Method method, String id) {
		String value = annotationId(id);
		if (!value.isBlank()) {
			ids.computeIfAbsent(value, key -> new ArrayList<>()).add(method);
		}
	}

	private AssertionError annotationIdValidationError(Map<String, List<Method>> duplicateIds) {
		StringBuilder message = new StringBuilder();
		message.append("Invalid JPostman annotation usage.").append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL)
				.append("Duplicate JPostman annotation ids found.").append(JPostmanErrors.ENDL)
				.append("Ids must be unique across @JPostmanRequest, @JPostmanResponse, and @JPostmanExecutor.")
				.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Duplicate annotation ids:")
				.append(JPostmanErrors.ENDL);

		List<Method> invalid = new ArrayList<>();
		for (Map.Entry<String, List<Method>> entry : duplicateIds.entrySet()) {
			message.append("- id=\"").append(entry.getKey()).append("\"").append(JPostmanErrors.ENDL);
			for (Method method : entry.getValue()) {
				message.append("  - ").append(annotationLabel(method)).append(" ").append(signature(method))
						.append(JPostmanErrors.ENDL);
				invalid.add(method);
			}
		}

		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(invalid.stream().distinct().map(this::testFrame).toArray(StackTraceElement[]::new));
		return error;
	}

	private String annotationLabel(Method method) {
		if (JPostmanAnnotations.request(method) != null) {
			return "@JPostmanRequest";
		}
		if (JPostmanAnnotations.response(method) != null) {
			return "@JPostmanResponse";
		}
		if (JPostmanAnnotations.executor(method) != null) {
			return "@JPostmanExecutor";
		}
		return "@JPostman";
	}

	private String requestId(Method method) {
		JPostmanRequest annotation = JPostmanAnnotations.request(method);
		return annotation == null ? "" : annotationId(annotation.id());
	}

	private String responseId(Method method) {
		JPostmanResponse annotation = JPostmanAnnotations.response(method);
		return annotation == null ? "" : annotationId(annotation.id());
	}

	private String executorId(Method method) {
		JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
		return annotation == null ? "" : annotationId(annotation.id());
	}

	private void validateExecutors(Object testInstance) {
		validateAnnotationIds(testInstance);

		Class<?> type = testInstance.getClass();
		Map<String, List<Method>> ids = new LinkedHashMap<>();
		LinkedHashSet<Method> defaults = new LinkedHashSet<>();
		List<Method> invalidSignatures = new ArrayList<>();
		List<Method> invalidReturns = new ArrayList<>();

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null) {
					continue;
				}

				validateExecutorMethod(method, invalidSignatures, invalidReturns);

				String id = annotationId(annotation.id());
				if (!id.isBlank()) {
					ids.computeIfAbsent(id, key -> new ArrayList<>()).add(method);
				}

				if (isExecutorProvider(method) && ("default".equals(id) || id.isBlank())) {
					defaults.add(method);
				}
			}
			current = current.getSuperclass();
		}

		Map<String, List<Method>> duplicateIds = new LinkedHashMap<>();
		for (Map.Entry<String, List<Method>> entry : ids.entrySet()) {
			if (entry.getValue().size() > 1) {
				duplicateIds.put(entry.getKey(), entry.getValue());
			}
		}

		if (!duplicateIds.isEmpty() || defaults.size() > 1 || !invalidSignatures.isEmpty()
				|| !invalidReturns.isEmpty()) {
			throw executorValidationError(duplicateIds, defaults, invalidSignatures, invalidReturns);
		}
	}

	private boolean isExecutorProvider(Method method) {
		return method != null && ApiExecutor.class.isAssignableFrom(method.getReturnType());
	}

	private boolean isExecutorInterceptor(Method method) {
		return method != null && method.getReturnType() == Void.TYPE;
	}

	private void validateExecutorMethod(Method method, List<Method> invalidSignatures, List<Method> invalidReturns) {
		Class<?>[] types = method.getParameterTypes();
		boolean valid = false;

		if (types.length == 0) {
			valid = true;
		} else if (types.length == 1) {
			valid = isContextParameter(types[0]) || isInfoParameter(types[0]);
		} else if (types.length == 2) {
			valid = isContextParameter(types[0]) && isInfoParameter(types[1]);
		}

		if (!valid) {
			invalidSignatures.add(method);
		}

		if (!isExecutorProvider(method) && !isExecutorInterceptor(method)) {
			invalidReturns.add(method);
		}
	}

	private AssertionError executorValidationError(Map<String, List<Method>> duplicateIds, Set<Method> defaults,
			List<Method> invalidSignatures, List<Method> invalidReturns) {
		StringBuilder message = new StringBuilder();
		message.append("Invalid JPostman annotation usage.").append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL);

		boolean needBlank = false;

		if (!duplicateIds.isEmpty()) {
			message.append("@JPostmanExecutor ids must be unique.").append(JPostmanErrors.ENDL).append(
					"The executor attribute on @JPostmanRequest, @JPostmanResponse, and @JPostmanRunner points to this unique id.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Duplicate executor ids:")
					.append(JPostmanErrors.ENDL);
			for (Map.Entry<String, List<Method>> entry : duplicateIds.entrySet()) {
				message.append("- id=\"").append(entry.getKey()).append("\"").append(JPostmanErrors.ENDL);
				for (Method method : entry.getValue()) {
					message.append("  - ").append(signature(method)).append(JPostmanErrors.ENDL);
				}
			}
			needBlank = true;
		}

		if (defaults.size() > 1) {
			if (needBlank) {
				message.append(JPostmanErrors.ENDL);
			}
			message.append("Only one default @JPostmanExecutor is allowed.").append(JPostmanErrors.ENDL).append(
					"Default executors are @JPostmanExecutor(id = \"default\"), a method named defaultExecutor, or an unnamed @JPostmanExecutor.")
					.append(JPostmanErrors.ENDL)
					.append("Keep one default executor, and give all other executors unique ids.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Default executor methods:")
					.append(JPostmanErrors.ENDL);
			for (Method method : defaults) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				String id = annotation == null ? "" : annotationId(annotation.id());
				message.append("- ").append(signature(method));
				if (!id.isBlank()) {
					message.append(" id=\"").append(id).append("\"");
				}
				message.append(JPostmanErrors.ENDL);
			}
			needBlank = true;
		}

		if (!invalidSignatures.isEmpty()) {
			if (needBlank) {
				message.append(JPostmanErrors.ENDL);
			}
			message.append("@JPostmanExecutor methods have unsupported parameters.").append(JPostmanErrors.ENDL)
					.append("Supported signatures are: (), (context), (JPostmanInfo), or (context, JPostmanInfo).")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid executor signatures:")
					.append(JPostmanErrors.ENDL);
			for (Method method : invalidSignatures) {
				message.append("- ").append(signature(method)).append(JPostmanErrors.ENDL);
			}
			needBlank = true;
		}

		if (!invalidReturns.isEmpty()) {
			if (needBlank) {
				message.append(JPostmanErrors.ENDL);
			}
			message.append("@JPostmanExecutor methods must return ApiExecutor or void.").append(JPostmanErrors.ENDL)
					.append("ApiExecutor methods configure request execution; void methods run as post-response interceptors.")
					.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Invalid executor return methods:")
					.append(JPostmanErrors.ENDL);
			for (Method method : invalidReturns) {
				message.append("- ").append(signature(method)).append(" returns ")
						.append(method.getReturnType().getSimpleName()).append(JPostmanErrors.ENDL);
			}
		}

		List<Method> invalid = new ArrayList<>();
		for (List<Method> methods : duplicateIds.values()) {
			invalid.addAll(methods);
		}
		invalid.addAll(defaults);
		invalid.addAll(invalidSignatures);
		invalid.addAll(invalidReturns);

		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(invalid.stream().distinct().map(this::testFrame).toArray(StackTraceElement[]::new));
		return error;
	}

	private void validateExecutorMethod(Method method, JPostmanInfo info) {
		List<Method> invalidSignatures = new ArrayList<>();
		List<Method> invalidReturns = new ArrayList<>();
		validateExecutorMethod(method, invalidSignatures, invalidReturns);

		if (!invalidSignatures.isEmpty()) {
			throw JPostmanErrors.usage(info, "@JPostmanExecutor method has unsupported signature: " + method.getName(),
					"Supported signatures: (), (context), (JPostmanInfo), or (context, JPostmanInfo).");
		}

		if (!invalidReturns.isEmpty()) {
			throw JPostmanErrors.usage(info,
					"@JPostmanExecutor method must return ApiExecutor or void: " + method.getName());
		}
	}

	private String signature(Method method) {
		StringBuilder result = new StringBuilder();
		result.append(method.getDeclaringClass().getSimpleName()).append(".").append(method.getName()).append("(");
		Class<?>[] types = method.getParameterTypes();
		for (int i = 0; i < types.length; i++) {
			if (i > 0) {
				result.append(", ");
			}
			result.append(types[i].getSimpleName());
		}
		result.append(")");
		return result.toString();
	}

	private StackTraceElement testFrame(Method method) {
		Class<?> type = method.getDeclaringClass();
		String fileName = type.getSimpleName() + ".java";
		int line = JPostmanStackTraceCleaner.findSourceLine(type, method.getName());

		return new StackTraceElement(type.getName(), method.getName(), fileName, line);
	}

	private Object executorResult(Object testInstance, Method method, C ctx, JPostmanInfo info) throws Exception {
		JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
		if (annotation == null || !annotation.session()) {
			return invokeExecutor(testInstance, method, ctx, info);
		}

		String key = method.getDeclaringClass().getName() + ID_PREFIX + method.getName() + ":"
				+ annotationId(annotation.id());
		ApiExecutor cached = sessionExecutors.get(key);
		if (cached != null) {
			return cached;
		}

		Object result = invokeExecutor(testInstance, method, ctx, info);
		verifyExecutorResult(result, method, info);
		sessionExecutors.put(key, (ApiExecutor) result);
		return result;
	}

	private Object invokeExecutor(Object testInstance, Method method, C ctx, JPostmanInfo info) throws Exception {
		debug(testInstance, info);
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return invoke(testInstance, method);
		}
		if (types.length == 1 && isContextParameter(types[0])) {
			return invoke(testInstance, method, contextArg(types[0], ctx));
		}
		if (types.length == 1 && isInfoParameter(types[0])) {
			return invoke(testInstance, method, info);
		}
		return invoke(testInstance, method, contextArg(types[0], ctx), info);
	}

	private void verifyExecutorResult(Object result, Method executor, JPostmanInfo info) {
		verifyExecutorResult(result, executor.getName(), info);
	}

	private void verifyExecutorResult(Object result, String executorName, JPostmanInfo info) {
		if (result == null) {
			throw JPostmanErrors.usage(info, "JPostman executor returned null: " + executorName);
		}
		if (!(result instanceof ApiExecutor)) {
			throw JPostmanErrors.usage(info, "JPostman executor must return ApiExecutor: " + executorName);
		}
	}

	private ExecutorCall<C> executorCall(Object testInstance, C ctx, JPostmanInfo info) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		if (isBlank(info.executor) && options.hasDefaultExecutor()
				&& !hasDefaultExecutorMethod(testInstance.getClass())) {
			String name = options.executorClass().getSimpleName();
			JPostmanInfo executorInfo = info.child(name, info.executor, info.namespace, info.folder, info.request)
					.annotation("@JPostmanContext executor").debug(info.debug);
			return new ExecutorCall<>(name, executorInfo, options.executorClass(), options.session());
		}

		Method method = findExecutor(testInstance.getClass(), info.executor, info);
		JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
		JPostmanInfo executorInfo = info
				.child(method.getName(), info.executor, info.namespace, info.folder, info.request)
				.annotation("@JPostmanExecutor").id(annotationId(annotation.id())).debug(annotation.logLevel());
		return new ExecutorCall<>(method, annotation, executorInfo);
	}

	private boolean hasDefaultExecutorMethod(Class<?> type) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null) {
					continue;
				}

				String id = annotationId(annotation.id());
				if (isExecutorProvider(method) && (id.isBlank() || "default".equals(id))) {
					return true;
				}
			}
			current = current.getSuperclass();
		}
		return false;
	}

	private Method findExecutor(Class<?> type, String requestedName, JPostmanInfo info) {
		String requested = value(requestedName).trim();
		List<Method> requestedMethodMatches = new ArrayList<>();
		List<Method> defaultIdMatches = new ArrayList<>();
		List<Method> namedDefault = new ArrayList<>();
		List<Method> unnamed = new ArrayList<>();

		if (!requested.isBlank() && isIdReference(requested)) {
			String id = idReferenceValue(requested);
			if (id.isBlank()) {
				throw JPostmanErrors.usage(info, "JPostman executor id is empty: " + requestedName,
						"Use executor = \"methodName\" for Java method names, or executor = \"#id\" for annotation ids.");
			}

			Method method = findExecutorMethodById(type, id);
			if (method != null) {
				validateExecutorMethod(method, info);
				return method;
			}

			Method namedMethod = findExecutorMethodByName(type, id);
			if (namedMethod != null) {
				throw JPostmanErrors.usage(info, "JPostman executor id not found: " + requestedName,
						"Found @JPostmanExecutor method named " + signature(namedMethod) + ".",
						"Use executor = \"" + id + "\" to select by method name.");
			}

			throw JPostmanErrors.usage(info, "JPostman executor id not found: " + requestedName);
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null) {
					continue;
				}

				validateExecutorMethod(method, info);
				if (!isExecutorProvider(method)) {
					continue;
				}
				method.setAccessible(true);

				String id = annotationId(annotation.id());

				if (!requested.isBlank()) {
					if (method.getName().equals(requested)) {
						requestedMethodMatches.add(method);
					}
					continue;
				}

				if ("default".equals(id)) {
					defaultIdMatches.add(method);
				}
				if (id.isBlank() && "defaultExecutor".equals(method.getName())) {
					namedDefault.add(method);
				}
				if (id.isBlank()) {
					unnamed.add(method);
				}
			}
			current = current.getSuperclass();
		}

		if (!requested.isBlank()) {
			if (requestedMethodMatches.size() == 1) {
				return requestedMethodMatches.get(0);
			}
			if (requestedMethodMatches.size() > 1) {
				throw JPostmanErrors.usage(info,
						"Multiple @JPostmanExecutor methods found with method name: " + requested);
			}

			Method idMethod = findExecutorMethodById(type, requested);
			if (idMethod != null) {
				throw JPostmanErrors.usage(info, "Executor method not found: " + requested,
						"Found @JPostmanExecutor id \"" + requested + "\" on " + signature(idMethod) + ".",
						"Use executor = \"" + idReference(requested) + "\" to select by id.");
			}

			throw JPostmanErrors.usage(info, "JPostman executor not found: " + requested,
					"Use executor = \"methodName\" for Java method names, or executor = \"#id\" for annotation ids.");
		}

		if (defaultIdMatches.size() == 1) {
			return defaultIdMatches.get(0);
		}
		if (defaultIdMatches.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple @JPostmanExecutor(id = \"default\") methods found.",
					"Executor ids must be unique.");
		}
		if (namedDefault.size() == 1) {
			return namedDefault.get(0);
		}
		if (namedDefault.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple defaultExecutor @JPostmanExecutor methods found.",
					"Use executor = \"#id\" to select one by annotation id.");
		}
		if (unnamed.size() == 1) {
			return unnamed.get(0);
		}
		if (unnamed.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple default @JPostmanExecutor methods found.",
					"Add a unique id to non-default executors and use executor = \"#id\" to select one.");
		}

		throw framework.skipException(info, "No default @JPostmanExecutor was configured.",
				"Add one default executor, for example @JPostmanExecutor, @JPostmanExecutor(id = \"default\"), or specify executor = \"#id\".");
	}

	private final class ExecutorCall<T> {
		final Method method;
		final JPostmanExecutor annotation;
		final JPostmanInfo info;
		final String name;
		final Class<?> executorClass;
		final boolean session;

		ExecutorCall(Method method, JPostmanExecutor annotation, JPostmanInfo info) {
			this.method = method;
			this.annotation = annotation;
			this.info = info;
			this.name = method.getName();
			this.executorClass = null;
			this.session = false;
		}

		ExecutorCall(String name, JPostmanInfo info, Class<?> executorClass, boolean session) {
			this.method = null;
			this.annotation = null;
			this.info = info;
			this.name = name;
			this.executorClass = executorClass;
			this.session = session;
		}

		String[] dependsOn() {
			return annotation == null ? new String[0] : annotation.dependsOn();
		}

		@SuppressWarnings("unchecked")
		private C castContext(T ctx) {
			return (C) ctx;
		}

		Object result(Object testInstance, T ctx) throws Exception {
			if (method != null) {
				return executorResult(testInstance, method, castContext(ctx), info);
			}
			if (!session) {
				return JPostmanDefaultExecutorFactory.create(executorClass, ctx, false);
			}
			String key = testInstance.getClass().getName() + "#contextExecutor:" + executorClass.getName();
			ApiExecutor cached = sessionExecutors.get(key);
			if (cached != null) {
				return JPostmanDefaultExecutorFactory.request(cached, ctx);
			}
			ApiExecutor created = JPostmanDefaultExecutorFactory.create(executorClass, ctx, true);
			sessionExecutors.put(key, created);
			return created;
		}
	}

	private Method findDependencyMethod(Class<?> type, String dependencyName, JPostmanInfo info) {
		String value = value(dependencyName).trim();
		if (isIdReference(value)) {
			String id = idReferenceValue(value);
			if (id.isBlank()) {
				throw JPostmanErrors.usage(info, "JPostman dependency id is empty: " + dependencyName,
						"Use dependsOn = \"methodName\" for Java method names, or dependsOn = \"#id\" for annotation ids.");
			}
			Method method = findDependencyMethodById(type, id);
			if (method != null) {
				return method;
			}
			Method executor = findExecutorMethodById(type, id);
			if (executor != null) {
				throw JPostmanErrors.usage(info, "JPostman dependency id refers to an executor: " + dependencyName,
						"dependsOn can call @JPostmanRequest, @JPostmanResponse, or @JPostmanRunner dependencies.",
						"Use executor = \"" + idReference(id) + "\" to select an @JPostmanExecutor by id.");
			}
			throw JPostmanErrors.usage(info, "JPostman dependency id not found: " + dependencyName);
		}

		try {
			return findMethod(type, value, info);
		} catch (AssertionError e) {
			Method idMethod = findDependencyMethodById(type, value);
			if (idMethod != null) {
				throw JPostmanErrors.usage(info, "Dependency method not found: " + dependencyName,
						"Found JPostman annotation id \"" + value + "\" on " + signature(idMethod) + ".",
						"Use dependsOn = \"" + idReference(value) + "\" to depend by id.");
			}
			throw e;
		}
	}

	private Method findDependencyMethodById(Class<?> type, String id) {
		String requested = annotationId(id);
		if (requested.isBlank()) {
			return null;
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (validAnnotatedDependencyMethod(method) && requested.equals(annotationDependencyId(method))) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private boolean validAnnotatedDependencyMethod(Method method) {
		return validAnnotatedMethod(method) && (JPostmanAnnotations.request(method) != null
				|| JPostmanAnnotations.response(method) != null || JPostmanAnnotations.runner(method) != null);
	}

	private String annotationDependencyId(Method method) {
		JPostmanRequest request = JPostmanAnnotations.request(method);
		if (request != null) {
			return annotationId(request.id());
		}
		JPostmanResponse response = JPostmanAnnotations.response(method);
		return response == null ? "" : annotationId(response.id());
	}

	private Method findExecutorMethodByName(Class<?> type, String name) {
		String requested = value(name).trim();
		if (requested.isBlank()) {
			return null;
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (JPostmanAnnotations.executor(method) != null && isExecutorProvider(method)
						&& requested.equals(method.getName())) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private Method findExecutorMethodById(Class<?> type, String id) {
		String requested = annotationId(id);
		if (requested.isBlank()) {
			return null;
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor executor = JPostmanAnnotations.executor(method);
				if (executor != null && isExecutorProvider(method) && requested.equals(annotationId(executor.id()))) {
					method.setAccessible(true);
					return method;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private Method findMethod(Class<?> type, String methodName, JPostmanInfo info) {
		String value = value(methodName).trim();
		String className = "";
		String simpleMethodName = value;
		int separator = value.lastIndexOf('.');
		if (separator > 0 && separator < value.length() - 1) {
			className = value.substring(0, separator).trim();
			simpleMethodName = value.substring(separator + 1).trim();
		}

		Class<?> current = type;
		while (current != null && current != Object.class) {
			if (className.isBlank() || matchesClassName(current, className)) {
				for (Method method : current.getDeclaredMethods()) {
					if (method.getName().equals(simpleMethodName) && validAnnotatedMethod(method)) {
						method.setAccessible(true);
						return method;
					}
				}
			}
			current = current.getSuperclass();
		}
		throw JPostmanErrors.usage(info, "Dependency method not found: " + methodName);
	}

	private boolean matchesClassName(Class<?> type, String className) {
		return type.getSimpleName().equals(className) || type.getName().equals(className)
				|| (type.getCanonicalName() != null && type.getCanonicalName().equals(className));
	}

	private boolean validAnnotatedMethod(Method method) {
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return true;
		}
		if (types.length == 1) {
			return isContextParameter(types[0]) || isInfoParameter(types[0]);
		}
		if (types.length == 2) {
			return isContextParameter(types[0])
					&& (isInfoParameter(types[1]) || String.class.isAssignableFrom(types[1]));
		}
		return types.length == 3 && isContextParameter(types[0]) && String.class.isAssignableFrom(types[1])
				&& String.class.isAssignableFrom(types[2]);
	}

	private boolean isContextParameter(Class<?> type) {
		return type != null
				&& (framework.contextType().isAssignableFrom(type) || JPostman.Test.class.isAssignableFrom(type));
	}

	private Object contextArg(Class<?> type, C ctx) {
		return JPostman.Test.class.isAssignableFrom(type) ? JPostmanTestProxy.wrap(ctx) : ctx;
	}

	private boolean isInfoParameter(Class<?> type) {
		return type == JPostmanInfo.class || type == JPostman.Info.class;
	}

	private Object invokeAnnotated(Object testInstance, Method method, C ctx, JPostmanInfo info) throws Exception {
		/*
		 * Request helpers run their method body before execution, so trace logging here
		 * is useful. Response helpers are executed first and logged after the response
		 * is available; logging again before invoking the method body prints the same
		 * JPostmanInfo twice.
		 */
		if (info == null || info.ended() <= 0L) {
			debug(testInstance, info);
		}
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return invoke(testInstance, method);
		}
		if (types.length == 1 && isContextParameter(types[0])) {
			return invoke(testInstance, method, contextArg(types[0], ctx));
		}
		if (types.length == 1 && isInfoParameter(types[0])) {
			return invoke(testInstance, method, info);
		}
		if (types.length == 2 && isInfoParameter(types[1])) {
			return invoke(testInstance, method, contextArg(types[0], ctx), info);
		}
		if (types.length == 2) {
			return invoke(testInstance, method, contextArg(types[0], ctx), info.method);
		}
		Object contextArg = contextArg(types[0], ctx);
		return invoke(testInstance, method, contextArg, info.method, info.request);
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

	private boolean isCached(C ctx, String key) {
		try {
			return framework.hasCache(ctx, key);
		} catch (RuntimeException e) {
			return false;
		}
	}
}
