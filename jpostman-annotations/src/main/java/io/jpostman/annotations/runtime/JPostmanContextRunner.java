package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.firstNonBlank;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.loadProperties;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.propertyKey;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Properties;

import io.jpostman.Environment;
import io.jpostman.JPostman;
import io.jpostman.JPostman.Context;
import io.jpostman.Params;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanTestContext;

/**
 * Prepares and injects JPostman core and framework test contexts.
 *
 * @param <C> framework context type
 */
final class JPostmanContextRunner<C> {

	private final JPostmanFramework<C> framework;

	JPostmanContextRunner(JPostmanFramework<C> framework) {
		this.framework = framework;
	}

	PreparedContexts<C> prepare(Object testInstance) throws Exception {
		PreparedContexts<C> prepared = new PreparedContexts<>();

		prepareNamedContexts(testInstance, prepared);
		prepareActiveContexts(testInstance, prepared);

		return prepared;
	}

	private void prepareNamedContexts(Object testInstance, PreparedContexts<C> prepared) throws Exception {
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!isTestContextField(field)) {
					continue;
				}

				JPostmanTestContext annotation = field.getAnnotation(JPostmanTestContext.class);
				if (isActive(annotation)) {
					continue;
				}

				PreparedContext<C> context = createContext(annotation, testInstance.getClass(), field, testInstance,
						existingContext(testInstance, field));
				field.set(testInstance, context.context);
				prepared.put(annotation.namespace(), context);
			}
			current = current.getSuperclass();
		}
	}

	private void prepareActiveContexts(Object testInstance, PreparedContexts<C> prepared) throws Exception {
		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Field field : current.getDeclaredFields()) {
				if (!isTestContextField(field)) {
					continue;
				}

				JPostmanTestContext annotation = field.getAnnotation(JPostmanTestContext.class);
				if (!isActive(annotation)) {
					continue;
				}

				String namespace = annotation.namespace();
				PreparedContext<C> source;
				if (prepared.contains(namespace)) {
					source = prepared.resolve(namespace);
				} else {
					source = createContext(annotation, testInstance.getClass(), field, testInstance,
							existingContext(testInstance, field));
					prepared.put(namespace, source);
				}

				field.setAccessible(true);
				field.set(testInstance, source.context);
				prepared.addActive(new PreparedContext<>(source.context, source.loaded, testInstance, field));
			}
			current = current.getSuperclass();
		}
	}

	private boolean isTestContextField(Field field) {
		return field.isAnnotationPresent(JPostmanTestContext.class)
				&& framework.contextType().isAssignableFrom(field.getType());
	}

	private boolean isActive(JPostmanTestContext annotation) {
		return annotation.active();
	}

	private C existingContext(Object testInstance, Field field) throws IllegalAccessException {
		field.setAccessible(true);
		return framework.contextType().cast(field.get(testInstance));
	}

	void injectLoadedContexts(Object testInstance, PreparedContexts<C> contexts) throws Exception {
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
				field.set(testInstance, loggerContext(loaded, testClass));
			}
			current = current.getSuperclass();
		}
	}

	private Context loggerContext(Context context, Class<?> testClass) throws Exception {
		try {
			Method logger = Context.class.getMethod("logger", Class.class);
			return (Context) logger.invoke(context, testClass);
		} catch (NoSuchMethodException e) {
			return context;
		} catch (InvocationTargetException e) {
			Throwable cause = e.getCause();
			if (cause instanceof Exception) {
				throw (Exception) cause;
			}
			throw e;
		}
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

		C ctx = framework.create();
		loadEnvironmentValues(ctx, loaded.getEnvironment());

		if (!rulesLocation.isBlank()) {
			try (InputStream input = open(rulesLocation, testClass)) {
				framework.load(ctx, input);
			}
		}

		if (existingContext != null) {
			framework.copyCache(existingContext, ctx);
		}

		return new PreparedContext<>(ctx, loaded, testInstance, field);
	}

	private void loadEnvironmentValues(C ctx, Environment environment) {
		if (environment == null) {
			return;
		}

		/*
		 * Treat enabled Postman environment variables as plain values and disabled
		 * variables as secrets so they can still be resolved while remaining masked.
		 */
		environment.getParams().keySet().forEach(key -> {
			Params.Entry entry = environment.entry(key);

			if (entry.isEnabled()) {
				framework.plain(ctx, key, entry.getValue());
			} else {
				framework.secret(ctx, key, entry.getValue());
			}
		});
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
}
