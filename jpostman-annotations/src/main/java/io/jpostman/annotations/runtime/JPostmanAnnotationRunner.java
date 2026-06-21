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
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;

/**
 * Shared annotation execution flow for JUnit and TestNG.
 *
 * @param <C> framework context type
 */
public final class JPostmanAnnotationRunner<C> {
	private final JPostmanFramework<C> framework;

	public JPostmanAnnotationRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	public void run(Object testInstance, Method testMethod) throws Exception {
		PreparedContexts<C> prepared = prepareContexts(testInstance);
		if (prepared.isEmpty()) {
			framework.clearCurrent();
			return;
		}

		JPostmanRequest requestAnnotation = testMethod.getAnnotation(JPostmanRequest.class);
		JPostmanResponse responseAnnotation = testMethod.getAnnotation(JPostmanResponse.class);

		String currentNamespace = "";
		if (requestAnnotation != null) {
			currentNamespace = requestAnnotation.namespace();
		} else if (responseAnnotation != null) {
			currentNamespace = responseAnnotation.namespace();
		}

		PreparedContext<C> current = prepared.resolve(currentNamespace);
		framework.setCurrent(current.context);

		if (requestAnnotation != null) {
			applyRequest(current.context, current.collection, requestAnnotation);
			runDependencies(testInstance, prepared, requestAnnotation.dependsOn());
			applyRequest(current.context, current.collection, requestAnnotation);
		}

		if (responseAnnotation != null) {
			applyRequest(current.context, current.collection, responseAnnotation);
			runDependencies(testInstance, prepared, responseAnnotation.dependsOn());
			applyRequest(current.context, current.collection, responseAnnotation);
			executeResponse(testInstance, prepared, responseAnnotation, testMethod.getName());
		}
	}

	private PreparedContexts<C> prepareContexts(Object testInstance) throws Exception {
		PreparedContexts<C> prepared = new PreparedContexts<>();

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!field.isAnnotationPresent(JPostmanContext.class)
						|| !framework.contextType().isAssignableFrom(field.getType())) {
					continue;
				}

				JPostmanContext annotation = field.getAnnotation(JPostmanContext.class);
				String namespace = annotation.namespace();

				PreparedContext<C> context = createContext(annotation, testInstance.getClass(), field);
				field.setAccessible(true);
				field.set(testInstance, context.context);

				prepared.put(namespace, context);
			}
			current = current.getSuperclass();
		}

		return prepared;
	}

	private PreparedContext<C> createContext(JPostmanContext annotation, Class<?> testClass, Field field)
			throws Exception {

		Properties properties = loadProperties(annotation.config(), testClass);

		String namespace = annotation.namespace();
		String collectionLocation = firstNonBlank(annotation.collection(),
				property(properties, "collection", namespace));
		String environmentLocation = firstNonBlank(annotation.environment(),
				property(properties, "environment", namespace));
		String rulesLocation = firstNonBlank(annotation.rules(), property(properties, "rules", namespace));

		if (collectionLocation.isBlank()) {
			throw new IllegalStateException("JPostman collection is required for field " + field.getName()
					+ ". Configure @JPostmanContext(collection=...) or property "
					+ propertyKey("collection", namespace));
		}

		Context loaded = loadJPostmanContext(collectionLocation, environmentLocation, testClass);

		C ctx = framework.create();
		if (loaded.getEnvironment() != null) {
			framework.secret(ctx, loaded.getEnvironment());
		}

		if (!rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		return new PreparedContext<>(ctx, loaded.getCollection());
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

		applyRequest(ctx, collection, dependencyAnnotation);
		Object value = invoke(testInstance, dependencyMethod);

		if (dependencyMethod.getReturnType() != Void.TYPE) {
			framework.cache(ctx, cacheKey, value);
		}
	}

	private void applyRequest(C ctx, Collection collection, JPostmanRequest annotation) {
		if (!annotation.rule().isBlank()) {
			framework.loadRules(ctx, annotation.rule());
		}

		if (!annotation.folder().isBlank()) {
			framework.request(ctx, collection.getFolder(annotation.folder()).getRequest(annotation.request()));
		} else {
			framework.request(ctx, collection.getRequest(annotation.request()));
		}
	}

	private void applyRequest(C ctx, Collection collection, JPostmanResponse annotation) {
		if (!annotation.rule().isBlank()) {
			framework.loadRules(ctx, annotation.rule());
		}

		if (!annotation.folder().isBlank()) {
			framework.request(ctx, collection.getFolder(annotation.folder()).getRequest(annotation.request()));
		} else {
			framework.request(ctx, collection.getRequest(annotation.request()));
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, JPostmanResponse annotation,
			String methodName) throws Exception {

		C ctx = resolver.context(annotation.namespace());
		Collection collection = resolver.collection(annotation.namespace());

		Method executor = findExecutor(testInstance.getClass(), annotation.executor(), annotation.namespace());
		JPostmanExecutor executorAnnotation = executor.getAnnotation(JPostmanExecutor.class);

		for (String dependencyName : executorAnnotation.dependsOn()) {
			runDependency(testInstance, resolver, dependencyName);
			applyRequest(ctx, collection, annotation);
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

		framework.response(ctx, (ApiExecutor) result);
		framework.verify(ctx, annotation.verify());
	}

	private Method findExecutor(Class<?> type, String requestedName, String responseNamespace) {
		List<Method> unnamedExactNamespace = new ArrayList<>();
		List<Method> unnamedGeneric = new ArrayList<>();
		List<Method> allExactNamespace = new ArrayList<>();
		List<Method> allGeneric = new ArrayList<>();

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
				boolean namespaceExact = !annotation.namespace().isBlank()
						&& annotation.namespace().equals(responseNamespace);
				boolean namespaceGeneric = annotation.namespace().isBlank();

				if (!namespaceExact && !namespaceGeneric) {
					continue;
				}

				if (!requestedName.isBlank() && requestedName.equals(executorName)) {
					return method;
				}

				if (namespaceExact) {
					allExactNamespace.add(method);
					if (annotation.name().isBlank()) {
						unnamedExactNamespace.add(method);
					}
				} else {
					allGeneric.add(method);
					if (annotation.name().isBlank()) {
						unnamedGeneric.add(method);
					}
				}
			}
			current = current.getSuperclass();
		}

		if (!requestedName.isBlank()) {
			throw new IllegalStateException("JPostman executor not found: " + requestedName);
		}

		for (Method method : unnamedExactNamespace) {
			if ("defaultExecutor".equals(method.getName())) {
				return method;
			}
		}
		for (Method method : unnamedGeneric) {
			if ("defaultExecutor".equals(method.getName())) {
				return method;
			}
		}

		if (unnamedExactNamespace.size() == 1) {
			return unnamedExactNamespace.get(0);
		}
		if (unnamedGeneric.size() == 1) {
			return unnamedGeneric.get(0);
		}
		if (allExactNamespace.size() == 1) {
			return allExactNamespace.get(0);
		}
		if (allGeneric.size() == 1) {
			return allGeneric.get(0);
		}

		throw new IllegalStateException(
				"Default JPostman executor not found. Add @JPostmanExecutor or specify executor = name.");
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
			return framework.cache(ctx, key) != null;
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
