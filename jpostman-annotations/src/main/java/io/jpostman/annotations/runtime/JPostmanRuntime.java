package io.jpostman.annotations.runtime;

import java.util.Arrays;
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
	 * @param context               loaded core JPostman context
	 * @param namespace             fallback namespace used by {@link #ctx()} before
	 *                              an active context exists
	 * @param contextResolver       framework-context resolver by namespace
	 * @param activeContextResolver latest active framework-context resolver
	 * @param infoSupplier          current annotation execution info supplier
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
	 * @param args message and optional format arguments to write
	 */
	public void logTrace(Object... args) {
		if (context != null && hasLogArgs(args)) {
			if (args.length == 1) {
				context.trace(message(args));
			} else {
				context.trace(message(args), rest(args));
			}
		}
	}

	/**
	 * Writes a debug message using the loaded core context logger.
	 *
	 * @param args message and optional format arguments to write
	 */
	public void logDebug(Object... args) {
		if (context != null && hasLogArgs(args)) {
			if (args.length == 1) {
				context.debug(message(args));
			} else {
				context.debug(message(args), rest(args));
			}
		}
	}

	/**
	 * Writes an info message using the loaded core context logger.
	 *
	 * @param args message and optional format arguments to write
	 */
	public void logInfo(Object... args) {
		if (context != null && hasLogArgs(args)) {
			if (args.length == 1) {
				context.info(message(args));
			} else {
				context.info(message(args), rest(args));
			}
		}
	}

	/**
	 * Writes a warning message using the loaded core context logger.
	 *
	 * @param args message and optional format arguments to write
	 */
	public void logWarn(Object... args) {
		if (context != null && hasLogArgs(args)) {
			if (args.length == 1) {
				context.warn(message(args));
			} else {
				context.warn(message(args), rest(args));
			}
		}
	}

	/**
	 * Writes an error message using the loaded core context logger.
	 *
	 * @param args message and optional format arguments to write
	 */
	public void logError(Object... args) {
		if (context != null && hasLogArgs(args)) {
			if (args.length == 1) {
				context.error(message(args));
			} else {
				context.error(message(args), rest(args));
			}
		}
	}

	private boolean hasLogArgs(Object[] args) {
		return args != null && args.length > 0;
	}

	private String message(Object[] args) {
		return String.valueOf(args[0]);
	}

	private Object[] rest(Object[] args) {
		return Arrays.copyOfRange(args, 1, args.length);
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
