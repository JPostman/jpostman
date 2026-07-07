package io.jpostman.annotations.runtime;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
	private final JPostmanRuntimeRequest<C> requestExecutor;

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
		this(context, namespace, contextResolver, activeContextResolver, infoSupplier, null, null);
	}

	/**
	 * Creates a runtime view with runtime option access for log helper gating.
	 *
	 * @param context               loaded core JPostman context
	 * @param namespace             fallback namespace
	 * @param contextResolver       framework-context resolver by namespace
	 * @param activeContextResolver latest active framework-context resolver
	 * @param infoSupplier          current annotation execution info supplier
	 * @param optionsSupplier       runtime options supplier
	 */
	JPostmanRuntime(JPostman.Context context, String namespace, Function<String, C> contextResolver,
			Supplier<C> activeContextResolver, Supplier<JPostmanInfo> infoSupplier,
			Supplier<JPostmanRuntimeOptions> optionsSupplier) {
		this(context, namespace, contextResolver, activeContextResolver, infoSupplier, optionsSupplier, null);
	}

	JPostmanRuntime(JPostman.Context context, String namespace, Function<String, C> contextResolver,
			Supplier<C> activeContextResolver, Supplier<JPostmanInfo> infoSupplier,
			Supplier<JPostmanRuntimeOptions> optionsSupplier, JPostmanRuntimeRequest<C> requestExecutor) {
		this.context = context;
		this.namespace = namespace == null ? "" : namespace;
		this.contextResolver = contextResolver;
		this.activeContextResolver = activeContextResolver;
		this.infoSupplier = infoSupplier;
		this.requestExecutor = requestExecutor;
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
	 * Alias for {@link #ctx()}.
	 *
	 * @return latest active framework context
	 */
	public C test() {
		return ctx();
	}

	/**
	 * Alias for {@link #ctx(String)}.
	 *
	 * @param namespace namespace to resolve, or blank for the default namespace
	 * @return framework context
	 */
	public C test(String namespace) {
		return ctx(namespace);
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

	/** {@inheritDoc} */
	public RunnerRules<C> runner() {
		return new RunnerRules<>(() -> ctx(), () -> info());
	}

	/** {@inheritDoc} */
	public io.jpostman.annotations.JPostman.Test request() {
		return request(null);
	}

	/** {@inheritDoc} */
	public io.jpostman.annotations.JPostman.Test request(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
		if (requestExecutor == null) {
			throw new IllegalStateException("No active @JPostman.Call request executor is available.");
		}
		C result;
		try {
			result = requestExecutor.execute((ctx, info) -> {
				if (action != null) {
					action.accept(ctx, info);
				}
			});
		} catch (RuntimeException | Error e) {
			throw e;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return JPostmanTestProxy.wrap(result, () -> activeContextResolver == null ? null : activeContextResolver.get());
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
	 * Fluent request-name rule builder for {@code @JPostmanRunner} test bodies.
	 *
	 * <p>
	 * The builder is intended for runner test methods that are invoked around each
	 * runner request. {@link #start(Consumer)} runs once before the first runner
	 * request, {@link #request(Consumer)} runs before every runner request,
	 * {@link #response(Consumer)} runs after every runner response,
	 * {@link #has(String...)} and {@link #any(String...)} match the current request
	 * name after execution using exact text or regular expressions, and
	 * {@link #end(Consumer)} runs only after the last executed runner request.
	 * </p>
	 *
	 * @param <C> framework context type, or {@code JPostman.Test} when using the
	 *            compact runtime facade
	 */
	public static final class RunnerRules<C> {
		private final Supplier<C> contextSupplier;
		private final Supplier<JPostmanInfo> infoSupplier;
		private boolean matched;

		private RunnerRules(Supplier<C> contextSupplier, Supplier<JPostmanInfo> infoSupplier) {
			this.contextSupplier = contextSupplier;
			this.infoSupplier = infoSupplier;
		}

		/**
		 * Creates a condition that matches only when all supplied values match the
		 * current runner request name. Each value can be exact text or a regular
		 * expression.
		 *
		 * @param values required request names or regular expressions
		 * @return pending runner condition
		 */
		public RunnerCondition<C> has(String... values) {
			completeBeforeBodyAtAfterRule();
			return new RunnerCondition<>(this, hasAll(values));
		}

		/**
		 * Creates a condition that matches when any supplied value matches the current
		 * runner request name. Each value can be exact text or a regular expression.
		 *
		 * @param values candidate request names or regular expressions
		 * @return pending runner condition
		 */
		public RunnerCondition<C> any(String... values) {
			completeBeforeBodyAtAfterRule();
			return new RunnerCondition<>(this, hasAny(values));
		}

		/**
		 * Returns the current runner request name.
		 *
		 * @return current request name, or an empty string when no runner request is
		 *         active
		 */
		public String request() {
			JPostmanInfo info = info();
			return info == null || info.request == null ? "" : info.request;
		}

		/**
		 * Runs the action once before the first executed request in the current runner.
		 *
		 * @param action setup action to run with the active context
		 * @return this runner rule builder
		 */
		public RunnerRules<C> start(Consumer<C> action) {
			if (shouldRunStart() && action != null) {
				action.accept(context());
			}
			return this;
		}

		/**
		 * Runs the action once before the first executed request in the current runner.
		 *
		 * @param action setup action to run with the active context and info
		 * @return this runner rule builder
		 */
		public RunnerRules<C> start(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			if (shouldRunStart() && action != null) {
				action.accept(context(), info());
			}
			return this;
		}

		/**
		 * Runs the action before each executed request in the current runner.
		 *
		 * @param action pre-request action to run with the active context
		 * @return this runner rule builder
		 */
		public RunnerRules<C> request(Consumer<C> action) {
			if (isBefore() && action != null) {
				action.accept(context());
			}
			return this;
		}

		/**
		 * Runs the action before each executed request in the current runner.
		 *
		 * @param action pre-request action to run with the active context and info
		 * @return this runner rule builder
		 */
		public RunnerRules<C> request(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			if (isBefore() && action != null) {
				action.accept(context(), info());
			}
			return this;
		}

		/**
		 * Runs the action after every executed response in the current runner.
		 *
		 * @param action post-response action to run with the active context
		 * @return this runner rule builder
		 */
		public RunnerRules<C> response(Consumer<C> action) {
			completeBeforeBodyAtAfterRule();
			if (isAfter() && action != null) {
				action.accept(context());
			}
			return this;
		}

		/**
		 * Runs the action after every executed response in the current runner.
		 *
		 * @param action post-response action to run with the active context and info
		 * @return this runner rule builder
		 */
		public RunnerRules<C> response(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			completeBeforeBodyAtAfterRule();
			if (isAfter() && action != null) {
				action.accept(context(), info());
			}
			return this;
		}

		/**
		 * Runs the action when no previous {@link #has(String...)} or
		 * {@link #any(String...)} condition matched in this rule chain.
		 *
		 * @param action default action to run with the active context
		 * @return this runner rule builder
		 */
		public RunnerRules<C> otherwise(Consumer<C> action) {
			completeBeforeBodyAtAfterRule();
			if (isAfter() && !matched && action != null) {
				action.accept(context());
			}
			return this;
		}

		/**
		 * Runs the action when no previous {@link #has(String...)} or
		 * {@link #any(String...)} condition matched in this rule chain.
		 *
		 * @param action default action to run with the active context and info
		 * @return this runner rule builder
		 */
		public RunnerRules<C> otherwise(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			completeBeforeBodyAtAfterRule();
			if (isAfter() && !matched && action != null) {
				action.accept(context(), info());
			}
			return this;
		}

		/**
		 * Runs the action only for the last executed request in the current runner.
		 *
		 * @param action final action to run with the active context
		 * @return this runner rule builder
		 */
		public RunnerRules<C> end(Consumer<C> action) {
			completeBeforeBodyAtAfterRule();
			boolean terminal = isAfter();
			try {
				if (terminal && isLast() && action != null) {
					action.accept(context());
				}
			} finally {
				completeAfterBodyAtTerminalRule(false);
			}
			if (terminal) {
				JPostmanRuntimeRunner.stopUserBody();
			}
			return this;
		}

		/**
		 * Runs the action only for the last executed request in the current runner.
		 *
		 * @param action final action to run with the active context and info
		 * @return this runner rule builder
		 */
		public RunnerRules<C> end(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			completeBeforeBodyAtAfterRule();
			boolean terminal = isAfter();
			try {
				if (terminal && isLast() && action != null) {
					action.accept(context(), info());
				}
			} finally {
				completeAfterBodyAtTerminalRule(false);
			}
			if (terminal) {
				JPostmanRuntimeRunner.stopUserBody();
			}
			return this;
		}

		private void completeBeforeBodyAtAfterRule() {
			if (isBefore()) {
				JPostmanRuntimeRunner.stopUserBody();
			}
		}

		private void completeAfterBodyAtTerminalRule(boolean stopBody) {
			if (isAfter()) {
				JPostmanRuntimeRunner.finishAfterRequest();
				if (stopBody) {
					JPostmanRuntimeRunner.stopUserBody();
				}
			}
		}

		private C context() {
			return contextSupplier == null ? null : contextSupplier.get();
		}

		private JPostmanInfo info() {
			return infoSupplier == null ? null : infoSupplier.get();
		}

		private boolean shouldRunStart() {
			return JPostmanRuntimeRunner.shouldRunStart();
		}

		private boolean isLast() {
			return JPostmanRuntimeRunner.isLast(info());
		}

		private boolean isBefore() {
			return JPostmanRuntimeRunner.isBeforeRequest();
		}

		private boolean isAfter() {
			return JPostmanRuntimeRunner.isAfterRequest();
		}

		private void matched() {
			this.matched = true;
		}

		private boolean hasAll(String... values) {
			if (!isAfter()) {
				return false;
			}
			String[] expected = values(values);
			if (expected.length == 0) {
				return false;
			}
			String request = request();
			for (String value : expected) {
				if (!matches(request, value)) {
					return false;
				}
			}
			return true;
		}

		private boolean hasAny(String... values) {
			if (!isAfter()) {
				return false;
			}
			String request = request();
			for (String value : values(values)) {
				if (matches(request, value)) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Pending runner condition returned by {@link RunnerRules#has(String...)} and
	 * {@link RunnerRules#any(String...)}.
	 *
	 * @param <C> framework context type
	 */
	public static final class RunnerCondition<C> {
		private final RunnerRules<C> rules;
		private final boolean matched;

		private RunnerCondition(RunnerRules<C> rules, boolean matched) {
			this.rules = rules;
			this.matched = matched;
		}

		/**
		 * Runs the action when the condition matched and returns the parent builder.
		 *
		 * @param action action to run with the active context
		 * @return parent runner rule builder for additional chained conditions
		 */
		public RunnerRules<C> then(Consumer<C> action) {
			if (matched) {
				rules.matched();
				if (action != null) {
					action.accept(rules.context());
				}
			}
			return rules;
		}

		/**
		 * Runs the action when the condition matched and passes both the active context
		 * and current execution info.
		 *
		 * @param action action to run with the active context and info
		 * @return parent runner rule builder for additional chained conditions
		 */
		public RunnerRules<C> then(BiConsumer<C, io.jpostman.annotations.JPostman.Info> action) {
			if (matched) {
				rules.matched();
				if (action != null) {
					action.accept(rules.context(), rules.info());
				}
			}
			return rules;
		}
	}

	private static boolean matches(String actual, String expected) {
		String pattern = value(expected).trim();
		if (actual == null || pattern.isBlank()) {
			return false;
		}
		if (pattern.equals(actual)) {
			return true;
		}
		try {
			return Pattern.compile(pattern).matcher(actual).matches();
		} catch (PatternSyntaxException ignored) {
			return false;
		}
	}

	private static String[] values(String[] values) {
		if (values == null) {
			return new String[0];
		}
		return Arrays.stream(values).filter(v -> !value(v).trim().isBlank()).map(String::trim).toArray(String[]::new);
	}

	private static String value(String value) {
		return value == null ? "" : value;
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
