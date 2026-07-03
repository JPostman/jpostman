package io.jpostman.annotations.runtime;

import java.util.function.Function;
import java.util.function.Supplier;

import io.jpostman.Collection;
import io.jpostman.Environment;
import io.jpostman.JPostman;
import io.jpostman.annotations.JPostmanContext;

/**
 * Runtime view injected by {@link JPostmanContext}.
 *
 * <p>
 * This type is provided by the annotations module and does not require changes
 * to the core {@link JPostman.Context} type. It exposes the loaded core
 * context, the active framework context, namespace-specific framework contexts,
 * and the current {@link JPostmanInfo} used by annotation execution.
 * </p>
 *
 * <p>
 * Typical TestNG usage:
 * </p>
 *
 * <pre>
 * {@code @JPostmanContext(collection = "classpath:collection.json")
 * private JPostmanRuntime<TestNgContext> jctx;
 *
 * // Compact alias:
 * private io.jpostman.annotations.JPostman.Runtime<TestNgContext> jctx;
 *
 * jctx.ctx().verify(); // latest active context
 * jctx.ctx("").print(); // default namespace context
 * jctx.ctx("product").response().print();
 * jctx.info().body("title", "Wireless Mouse");
 * }
 * </pre>
 *
 * @param <C> framework context type, for example {@code TestNgContext} or
 *            {@code JUnitContext}
 */
public class JPostmanRuntime<C> implements io.jpostman.annotations.JPostman.Runtime<C> {

	private final JPostman.Context context;
	private final String namespace;
	private final Function<String, C> contextResolver;
	private final Supplier<C> activeContextResolver;
	private final Supplier<JPostmanInfo> infoSupplier;

	/**
	 * Creates a runtime view.
	 *
	 * @param context         loaded core JPostman context
	 * @param namespace       fallback namespace used by {@link #ctx()} before an
	 *                        active context exists
	 * @param contextResolver framework-context resolver by namespace
	 * @param activeContextResolver latest active framework-context resolver
	 * @param infoSupplier current annotation execution info supplier
	 */
	public JPostmanRuntime(JPostman.Context context, String namespace, Function<String, C> contextResolver,
			Supplier<C> activeContextResolver, Supplier<JPostmanInfo> infoSupplier) {
		this.context = context;
		this.namespace = namespace == null ? "" : namespace;
		this.contextResolver = contextResolver;
		this.activeContextResolver = activeContextResolver;
		this.infoSupplier = infoSupplier;
	}

	/**
	 * Returns the loaded core JPostman context.
	 *
	 * @return core context
	 */
	public JPostman.Context context() {
		return context;
	}

	/**
	 * Returns the latest active framework context.
	 *
	 * <p>
	 * Use {@link #ctx(String)} with an empty namespace to explicitly resolve the
	 * default namespace, or with a namespace value to resolve a namespace-specific
	 * context.
	 * </p>
	 *
	 * @return latest active framework context, or the runtime default namespace
	 *         context before any annotated execution has completed
	 */
	public C ctx() {
		C active = activeContextResolver == null ? null : activeContextResolver.get();
		return active == null ? ctx(namespace) : active;
	}

	/**
	 * Returns the framework context for the supplied namespace.
	 *
	 * @param namespace namespace to resolve, or blank for the default namespace
	 * @return framework context
	 */
	public C ctx(String namespace) {
		if (contextResolver == null) {
			return null;
		}
		return contextResolver.apply(namespace == null ? "" : namespace);
	}

	/**
	 * Returns the current annotation execution information.
	 *
	 * <p>
	 * This is useful from test methods that cannot receive {@link JPostmanInfo} as
	 * an injected method argument.
	 * </p>
	 *
	 * @return current JPostman execution info, or {@code null} when no annotation
	 *         execution is active
	 */
	public JPostmanInfo info() {
		return infoSupplier == null ? null : infoSupplier.get();
	}

	/**
	 * Writes a trace message using the loaded core context logger.
	 *
	 * @param message message to write
	 */
	public void logTrace(String message) {
		if (context != null) {
			context.trace(message);
		}
	}

	/**
	 * Writes a debug message using the loaded core context logger.
	 *
	 * @param message message to write
	 */
	public void logDebug(String message) {
		if (context != null) {
			context.debug(message);
		}
	}

	/**
	 * Writes an info message using the loaded core context logger.
	 *
	 * @param message message to write
	 */
	public void logInfo(String message) {
		if (context != null) {
			context.info(message);
		}
	}

	/**
	 * Writes a warning message using the loaded core context logger.
	 *
	 * @param message message to write
	 */
	public void logWarn(String message) {
		if (context != null) {
			context.warn(message);
		}
	}

	/**
	 * Writes an error message using the loaded core context logger.
	 *
	 * @param message message to write
	 */
	public void logError(String message) {
		if (context != null) {
			context.error(message);
		}
	}

	/**
	 * Returns the loaded collection from the core context.
	 *
	 * @return loaded collection
	 */
	public Collection getCollection() {
		return context == null ? null : context.getCollection();
	}

	/**
	 * Returns the loaded environment from the core context.
	 *
	 * @return loaded environment
	 */
	public Environment getEnvironment() {
		return context == null ? null : context.getEnvironment();
	}

}
