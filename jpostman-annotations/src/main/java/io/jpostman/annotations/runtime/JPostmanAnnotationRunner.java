package io.jpostman.annotations.runtime;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import io.jpostman.ApiExecutor;
import io.jpostman.Collection;
import io.jpostman.JPostman;
import io.jpostman.JPostman.Context;
import io.jpostman.Request;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanTestContext;

/**
 * Shared annotation execution flow for JUnit and TestNG.
 *
 * @param <C> framework context type
 */
public final class JPostmanAnnotationRunner<C> {
	private static final Object VOID_DEPENDENCY_MARKER = new Object();

	private final JPostmanFramework<C> framework;

	/**
	 * Creates a runner for the supplied framework bridge.
	 *
	 * @param framework framework bridge used to perform context operations
	 */
	public JPostmanAnnotationRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	/**
	 * Prepares a test instance before framework lifecycle methods run.
	 *
	 * <p>
	 * This injects {@code @JPostmanContext} and {@code @JPostmanTestContext} fields
	 * and sets the current framework context when one is available.
	 * </p>
	 *
	 * @param testInstance test instance to prepare
	 * @throws Exception when context preparation or field injection fails
	 */
	public void setup(Object testInstance) throws Exception {
		PreparedContexts<C> prepared = prepareContexts(testInstance);
		injectLoadedContexts(testInstance, prepared);

		if (!prepared.isEmpty()) {
			C current = prepared.contains("") ? prepared.context("") : prepared.firstContext();
			framework.setCurrent(current);
		}
	}

	/**
	 * Runs JPostman annotations for a single test method.
	 *
	 * <p>
	 * This handles request preparation, response execution, dependencies, named
	 * executors, verification, filtering, cache updates, and current context
	 * management for the supplied test method.
	 * </p>
	 *
	 * @param testInstance test instance that owns the method
	 * @param testMethod   test method to process
	 * @throws Exception when annotation execution fails
	 */
	public void run(Object testInstance, Method testMethod) throws Exception {
		PreparedContexts<C> prepared = prepareContexts(testInstance);

		// Inject loaded core contexts even when the test class only uses
		// @JPostmanContext and does not declare a JUnitContext/TestNgContext.
		injectLoadedContexts(testInstance, prepared);

		JPostmanRequest requestAnnotation = testMethod.getAnnotation(JPostmanRequest.class);
		JPostmanResponse responseAnnotation = testMethod.getAnnotation(JPostmanResponse.class);

		// Plain test method.
		// The test may only use @JPostmanContext to access
		// collection/environment.
		// In that case, injectLoadedContexts(...) already did the work, so do not
		// resolve a JUnit/TestNG context.
		if (requestAnnotation == null && responseAnnotation == null) {
			framework.clearCurrent();
			return;
		}

		if (prepared.isEmpty()) {
			framework.clearCurrent();
			return;
		}

		String currentNamespace = "";
		if (requestAnnotation != null) {
			currentNamespace = requestAnnotation.namespace();
		} else {
			currentNamespace = responseAnnotation.namespace();
		}

		PreparedContext<C> current = prepared.resolve(currentNamespace);
		C currentContext = current.context;
		framework.setCurrent(currentContext);

		if (requestAnnotation != null) {
			currentContext = applyRequest(currentContext, current.collection, requestAnnotation);
			prepared.update(currentNamespace, currentContext);
			framework.setCurrent(currentContext);
			runDependencies(testInstance, prepared, requestAnnotation.dependsOn());
			currentContext = applyRequest(prepared.context(currentNamespace), current.collection, requestAnnotation);
			prepared.update(currentNamespace, currentContext);
			framework.setCurrent(currentContext);
		}

		if (responseAnnotation != null) {
			currentContext = applyRequest(currentContext, current.collection, responseAnnotation);
			prepared.update(currentNamespace, currentContext);
			framework.setCurrent(currentContext);
			runDependencies(testInstance, prepared, responseAnnotation.dependsOn());
			currentContext = applyRequest(prepared.context(currentNamespace), current.collection, responseAnnotation);
			prepared.update(currentNamespace, currentContext);
			framework.setCurrent(currentContext);
			executeResponse(testInstance, prepared, currentContext, responseAnnotation, testMethod.getName());
		}
	}

	private PreparedContexts<C> prepareContexts(Object testInstance) throws Exception {
		PreparedContexts<C> prepared = new PreparedContexts<>();

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!field.isAnnotationPresent(JPostmanTestContext.class)
						|| !framework.contextType().isAssignableFrom(field.getType())) {
					continue;
				}

				JPostmanTestContext annotation = field.getAnnotation(JPostmanTestContext.class);
				String namespace = annotation.namespace();

				field.setAccessible(true);
				C existingContext = framework.contextType().cast(field.get(testInstance));

				PreparedContext<C> context = createContext(annotation, testInstance.getClass(), field, testInstance,
						existingContext);
				field.set(testInstance, context.context);

				prepared.put(namespace, context);
			}
			current = current.getSuperclass();
		}

		return prepared;
	}

	private PreparedContext<C> createContext(JPostmanTestContext annotation, Class<?> testClass, Field field,
			Object testInstance, C existingContext) throws Exception {

		Properties properties = loadProperties(annotation.config(), testClass);

		String namespace = annotation.namespace();
		String collectionLocation = firstNonBlank(annotation.collection(),
				property(properties, "collection", namespace));
		String environmentLocation = firstNonBlank(annotation.environment(),
				property(properties, "environment", namespace));
		String rulesLocation = firstNonBlank(annotation.rules(), property(properties, "rules", namespace));

		if (collectionLocation.isBlank()) {
			throw new IllegalStateException("JPostman collection is required for field " + field.getName()
					+ ". Configure @JPostmanTestContext(collection=...) or property "
					+ propertyKey("collection", namespace));
		}

		Context loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);

		C ctx = existingContext == null ? framework.create() : existingContext;
		if (existingContext == null && loaded.getEnvironment() != null) {
			framework.secret(ctx, loaded.getEnvironment());
		}

		if (existingContext == null && !rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		return new PreparedContext<>(ctx, loaded, testInstance, field);
	}

	private void injectLoadedContexts(Object testInstance, PreparedContexts<C> contexts) throws Exception {
		Class<?> testClass = testInstance.getClass();

		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = field.getAnnotation(JPostmanContext.class);
				if (annotation == null) {
					continue;
				}

				if (!JPostman.Context.class.isAssignableFrom(field.getType())) {
					throw new IllegalStateException(
							"@JPostmanContext field must be JPostman.Context: " + field.getName());
				}

				JPostman.Context loaded;

				if (contexts.contains(annotation.namespace())) {
					loaded = contexts.resolve(annotation.namespace()).loaded;
				} else {
					Properties properties = loadProperties(annotation.config(), testClass);
					String namespace = annotation.namespace();
					String collectionLocation = firstNonBlank(annotation.collection(),
							property(properties, "collection", namespace));
					String environmentLocation = firstNonBlank(annotation.environment(),
							property(properties, "environment", namespace));

					if (collectionLocation.isBlank()) {
						throw new IllegalStateException("JPostman collection is required for field " + field.getName()
								+ ". Configure @JPostmanContext(collection=...) or property "
								+ propertyKey("collection", namespace));
					}

					loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);
				}

				field.setAccessible(true);
				field.set(testInstance, loaded);
			}
			current = current.getSuperclass();
		}
	}

	private Context loadJPostmanContext(String collectionLocation, String environmentLocation, Class<?> testClass)
			throws Exception {

		try (InputStream collection = open(collectionLocation, testClass)) {
			if (environmentLocation.isBlank()) {
				return loadCollectionOnly(collection);
			}

			try (InputStream environment = open(environmentLocation, testClass)) {
				return JPostman.load(collection, environment);
			}
		}
	}

	private Context loadCollectionOnly(InputStream collection) throws Exception {
		try {
			Method load = JPostman.class.getMethod("load", InputStream.class);
			return (Context) load.invoke(null, collection);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(
					"jpostman.environment is optional only when JPostman.load(InputStream) is available. "
							+ "Configure environment or add collection-only load support.",
					e);
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
	}

	private Properties loadProperties(String location, Class<?> testClass) throws IOException {
		Properties properties = new Properties();

		if (location == null || location.isBlank()) {
			return properties;
		}

		try (InputStream input = open(location, testClass)) {
			properties.load(input);
		} catch (IOException e) {
			if (!"classpath:jpostman.properties".equals(location)) {
				throw e;
			}
		}

		return properties;
	}

	private InputStream open(String location, Class<?> testClass) throws IOException {
		String value = location == null ? "" : location.trim();
		if (value.isBlank()) {
			throw new IllegalArgumentException("Resource location must not be blank.");
		}

		if (value.startsWith("classpath:")) {
			String path = value.substring("classpath:".length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}

			InputStream input = testClass.getClassLoader().getResourceAsStream(path);
			if (input == null) {
				throw new IOException("Classpath resource not found: " + value);
			}
			return input;
		}

		return new FileInputStream(value);
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
		JPostmanRequest dependencyAnnotation = dependencyMethod.getAnnotation(JPostmanRequest.class);

		if (dependencyAnnotation == null) {
			throw new IllegalStateException(
					"Dependency method must be annotated with @JPostmanRequest: " + dependencyName);
		}

		String namespace = dependencyAnnotation.namespace();
		C ctx = resolver.context(namespace);
		Collection collection = resolver.collection(namespace);

		String cacheKey = cacheKey(dependencyMethod, dependencyAnnotation);
		if (isCached(ctx, cacheKey)) {
			return;
		}

		ctx = applyRequest(ctx, collection, dependencyAnnotation);
		resolver.update(namespace, ctx);
		framework.setCurrent(ctx);
		Object value = invoke(testInstance, dependencyMethod);

		Object cacheValue;
		if (dependencyMethod.getReturnType() == Void.TYPE) {
			cacheValue = VOID_DEPENDENCY_MARKER;
		} else {
			cacheValue = value;
		}

		if (cacheValue == null) {
			throw new IllegalStateException("Dependency method returned null and cannot be cached: "
					+ dependencyMethod.getName() + ". Use void for setup-only dependencies, "
					+ "or return a non-null value when another request needs the cached value.");
		}

		// Store a non-null marker for void dependencies. ConcurrentHashMap does not
		// allow null values, and dependency reuse is checked by key existence.
		framework.cache(resolver.context(namespace), cacheKey, cacheValue);
	}

	private C applyRequest(C context, Collection collection, JPostmanRequest annotation) {
		C result = context;

		if (!annotation.rule().isBlank()) {
			result = framework.loadRules(result, annotation.rule());
		}

		return framework.request(result, request(collection, annotation.folder(), annotation.request()));
	}

	private C applyRequest(C context, Collection collection, JPostmanResponse annotation) {
		C result = context;

		if (!annotation.rule().isBlank()) {
			result = framework.loadRules(result, annotation.rule());
		}

		if (annotation.filter().length > 0) {
			result = framework.filter(result, annotation.filter());
		}

		return framework.request(result, request(collection, annotation.folder(), annotation.request()));
	}

	private Request request(Collection collection, String folder, String request) {
		if (folder == null || folder.isBlank()) {
			return collection.getRequest(request);
		}
		return collection.getFolder(folder).getRequest(request);
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			String methodName) throws Exception {

		Method executor = findExecutor(testInstance.getClass(), annotation.executor());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		Collection collection = resolver.collection(annotation.namespace());
		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName);
			ctx = applyRequest(resolver.context(annotation.namespace()), collection, annotation);
			resolver.update(annotation.namespace(), ctx);
			framework.setCurrent(ctx);
		}

		Object result;
		if (executor.getParameterCount() == 2) {
			result = invoke(testInstance, executor, ctx, methodName);
		} else {
			result = invoke(testInstance, executor, ctx);
		}

		if (result == null) {
			throw new IllegalStateException("JPostman executor returned null: " + executor.getName());
		}

		if (!(result instanceof ApiExecutor)) {
			throw new IllegalStateException("JPostman executor must return ApiExecutor: " + executor.getName());
		}

		ctx = framework.response(ctx, (ApiExecutor) result);
		resolver.update(annotation.namespace(), ctx);
		framework.setCurrent(ctx);
		framework.verify(ctx, annotation.verify(), annotation.soft(), annotation.log());
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

	private void validateExecutorMethod(Method method) {
		Class<?>[] parameterTypes = method.getParameterTypes();

		boolean validOneArg = parameterTypes.length == 1 && framework.contextType().isAssignableFrom(parameterTypes[0]);

		boolean validTwoArgs = parameterTypes.length == 2 && framework.contextType().isAssignableFrom(parameterTypes[0])
				&& String.class.isAssignableFrom(parameterTypes[1]);

		if (!validOneArg && !validTwoArgs) {
			throw new IllegalStateException(
					"@JPostmanExecutor method must accept either " + framework.contextType().getSimpleName() + " or "
							+ framework.contextType().getSimpleName() + ", String: " + method.getName());
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

	private String cacheKey(Method method, JPostmanRequest annotation) {
		return annotation.cache().isBlank() ? method.getName() : annotation.cache();
	}

	private boolean isCached(C ctx, String key) {
		try {
			return framework.hasCache(ctx, key);
		} catch (RuntimeException e) {
			return false;
		}
	}

	private String property(Properties properties, String key, String namespace) {
		return properties.getProperty(propertyKey(key, namespace), "");
	}

	private String propertyKey(String key, String namespace) {
		return namespace == null || namespace.isBlank() ? key : key + "." + namespace;
	}

	private String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second == null ? "" : second;
	}
}
