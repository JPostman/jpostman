package io.jpostman.annotations.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import io.jpostman.ApiExecutor;
import io.jpostman.Collection;
import io.jpostman.Environment;
import io.jpostman.Params;
import io.jpostman.Request;
import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanCall;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanOutputs;
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
	private final Runnable afterRunnerRequestCallback;
	private final Map<String, ApiExecutor> sessionExecutors = new LinkedHashMap<>();
	private final Deque<Integer> runnerVerifyScopes = new ArrayDeque<>();

	@FunctionalInterface
	private interface RunnerBodyCallback<T> {
		void run(T ctx, JPostmanInfo info) throws Exception;
	}

	/**
	 * Marks the final aggregate failure produced after a runner has already
	 * executed/reported its concrete requests. The outer annotation catch must not
	 * render the last request context again under the parent runner info.
	 */
	private static final class RunnerAggregateFailure extends AssertionError {
		private static final long serialVersionUID = 1L;

		RunnerAggregateFailure(String message) {
			super(message);
		}
	}

	/**
	 * Creates a runner for the supplied framework bridge.
	 *
	 * @param framework framework bridge used to perform context operations
	 */
	public JPostmanAnnotationRunner(JPostmanFramework<C> framework) {
		this(framework, null);
	}

	/**
	 * Creates a runner for the supplied framework bridge.
	 *
	 * @param framework                  framework bridge used to perform context
	 *                                   operations
	 * @param afterRunnerRequestCallback optional callback invoked before and after
	 *                                   each top-level runner request
	 */
	public JPostmanAnnotationRunner(JPostmanFramework<C> framework, Runnable afterRunnerRequestCallback) {
		this.framework = framework;
		this.contextRunner = new JPostmanContextRunner<>(framework);
		this.assertionRunner = new JPostmanAssertionRunner<>(framework);
		this.requestDiscovery = new JPostmanRequestDiscovery();
		this.afterRunnerRequestCallback = afterRunnerRequestCallback;
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
		contextRunner.activateBaseline(testInstance, prepared);
		contextRunner.activate(testInstance, prepared);
		contextRunner.injectLoadedContexts(testInstance, prepared);
		contextRunner.injectAssertContexts(testInstance, prepared);

		if (!prepared.isEmpty()) {
			C current = prepared.contains("") ? prepared.context("") : prepared.firstContext();
			framework.setCurrent(current);
		}
	}

	/**
	 * Executes a standalone/external {@code @JPostman.Response} method as a full
	 * JPostman response execution, including invoking its Java method body and
	 * caching the returned value (or full response for {@code void}).
	 *
	 * <p>
	 * This is used by the JUnit bridge for response methods that cannot be
	 * scheduled by JUnit itself, such as cache selectors returning String, Integer,
	 * Object, etc.
	 * </p>
	 *
	 * @param testInstance test instance that owns the response method
	 * @param testMethod   external response method
	 * @throws Exception when response execution or cache extraction fails
	 */
	public void runExternalResponse(Object testInstance, Method testMethod) throws Exception {
		JPostmanResponse annotation = JPostmanAnnotations.response(testMethod);
		if (annotation == null) {
			return;
		}

		// Reuse the normal top-level response path for request preparation, dependency
		// execution, HTTP execution, verification, diagnostics, and report recording.
		// IMPORTANT: run the Java response body immediately after that execution, while
		// the completed HTTP response is still installed in the injected Runtime/Test
		// context. Re-preparing the contexts first drops the active secure response and
		// makes runtime.test().path(...) fail with "Secure response is not set" even
		// though the request itself completed successfully.
		run(testInstance, testMethod);

		JPostmanReport report = report(testInstance);
		JPostmanInfo info = report == null ? null : report.execution(testMethod.getName());
		if (info == null) {
			info = info(testMethod.getName(), null, annotation, null, null);
			inheritResponseLocationFromDependencies(testInstance, annotation, info);
			applyDefaultExecutorNamespace(testInstance, info);
			info.method(testMethod.getName());
		}

		Object value;
		try {
			// The common external-response form has no parameters and reads the completed
			// response through the injected runtime, for example:
			// return runtime.test().path("accessToken");
			value = invokeAnnotated(testInstance, testMethod, null, info);
		} catch (Exception | Error error) {
			failed(report, info, null, error);
			throw error;
		}

		/*
		 * Cache into the exact prepared context that still owns the completed HTTP
		 * response. Re-preparing here creates a fresh request/response context (cache
		 * is copied, response state intentionally is not), which makes a void Response
		 * fail while snapshotting its full response with "Secure response is not set" /
		 * "Unable to snapshot JPostman response". The next top-level method will create
		 * its own fresh context and carry this cache forward normally.
		 */
		PreparedContexts<C> prepared = contextRunner.activeContexts(testInstance);
		if (prepared == null) {
			throw new IllegalStateException(
					"JPostman external Response completed without an active prepared context: " + testMethod.getName());
		}

		if (info.context == null) {
			info = info.context(prepared.resolve(info.namespace).contextAnnotation);
		}

		try {
			String cache = cacheKey(testMethod, annotation.cache(), annotation.id());
			cacheResponseDependencyResult(prepared, testMethod, info, cache, value);
			prepared.info(info);
			add(report, info);
		} catch (Exception | Error error) {
			C latest = latestContext(prepared, info.namespace, prepared.context(info.namespace));
			failed(report, info, latest, error);
			throw error;
		} finally {
			contextRunner.injectTestContexts(testInstance, prepared);
			contextRunner.injectLoadedContexts(testInstance, prepared);
			contextRunner.injectAssertContexts(testInstance, prepared);
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
		if (report != null && report.skipRemaining()) {
			throw JPostmanErrors.skip(framework, null, "JPostman test skipped.",
					"@JPostman.ReportContext(fail = \"skipAll\") stopped remaining tests after the first failure.");
		}
		PreparedContexts<C> prepared = contextRunner.prepareForRun(testInstance);
		contextRunner.activate(testInstance, prepared);
		contextRunner.injectLoadedContexts(testInstance, prepared);
		contextRunner.injectAssertContexts(testInstance, prepared);

		JPostmanRuntimeCall.clear(testInstance, framework.contextType());
		JPostmanRequest requestAnnotation = JPostmanAnnotations.request(testMethod);
		JPostmanResponse responseAnnotation = JPostmanAnnotations.response(testMethod);
		JPostmanCall callAnnotation = JPostmanAnnotations.call(testMethod);
		JPostmanRunner runnerAnnotation = JPostmanAnnotations.runner(testMethod);
		validateCallCombination(requestAnnotation, responseAnnotation, callAnnotation, runnerAnnotation, testMethod);
		if (requestAnnotation == null && responseAnnotation == null && callAnnotation == null
				&& runnerAnnotation == null) {
			framework.clearCurrent();
			return;
		}

		if (prepared.isEmpty()) {
			framework.clearCurrent();
			return;
		}

		JPostmanInfo info = info(testMethod.getName(), requestAnnotation, responseAnnotation, callAnnotation,
				runnerAnnotation);
		inheritTopLevelLocationFromDependencies(testInstance, requestAnnotation, responseAnnotation, callAnnotation,
				runnerAnnotation, info);
		applyDefaultExecutorNamespace(testInstance, info);
		validateLocalDebugs(requestAnnotation, responseAnnotation, callAnnotation, runnerAnnotation, info);
		PreparedContext<C> current = prepared.resolve(info.namespace);
		info = info.context(current.contextAnnotation);
		prepared.info(info);
		framework.setCurrent(current.context);
		List<String> stack = new ArrayList<>();
		stack.add(testMethod.getName());
		add(report, info);
		info.method(testMethod.getName());
		captureDebugContext(current, info);
		/*
		 * Do not emit annotation output during preparation. The record does not yet
		 * contain the completed request timing/response and would be duplicated by the
		 * post-execution output.
		 */

		if (callAnnotation != null) {
			validateCallSkipEnabled(callAnnotation, info);
			if (skipTopLevelCall(callAnnotation, info)) {
				skipped(report, info);
				throw JPostmanErrors.skip(framework, info, callSkipLines(callAnnotation, info));
			}
			registerJPostmanCallRuntime(testInstance, testMethod, prepared, callAnnotation, info);
			JPostmanDebugFile.call(testInstance, info, callAnnotation.debug());
			return;
		}

		try {
			if (responseAnnotation != null) {
				validateResponseSkipEnabled(responseAnnotation, info);
				if (skipTopLevelResponse(responseAnnotation, info)) {
					skipped(report, info);
					throw JPostmanErrors.skip(framework, info, responseSkipLines(responseAnnotation, info));
				}
			}

			if (runnerAnnotation != null) {
				validateRunnerSkipEnabled(runnerAnnotation, info);
				if (skipTopLevelRunner(runnerAnnotation, info)) {
					skipped(report, info);
					throw JPostmanErrors.skip(framework, info, runnerSkipLines(runnerAnnotation, info));
				}
			}

			if (requestAnnotation != null) {
				runAnnotatedRequest(testInstance, prepared, current.collection, requestAnnotation, info,
						requestAnnotation.data(), stack);
			}

			if (responseAnnotation != null) {
				boolean reusedResponse = isReusableResponseDependency(testInstance, responseAnnotation, info);
				C currentContext = runAnnotatedResponse(testInstance, prepared, current.collection, responseAnnotation,
						info, responseAnnotation.data(), stack);
				if (reusedResponse) {
					completeReusedResponse(testInstance, prepared, currentContext, responseAnnotation, info);
				} else {
					executeResponse(testInstance, prepared, currentContext, responseAnnotation, info, stack);
				}
				prepared.info(info);
				add(report, info);
			}

			if (runnerAnnotation != null) {
				beginRunnerVerifyScope(runnerAnnotation.verify());
				try {
					Method reusableRunner = runnerDependencyLauncherMethod(testInstance.getClass(), runnerAnnotation,
							info);
					if (reusableRunner != null) {
						runRunnerDependencyLauncher(testInstance, prepared, testMethod, reusableRunner,
								runnerAnnotation, info, stack);
					} else {
						prepareRunnerScope(testInstance, prepared, runnerAnnotation, info);
						String[] perRequestDependencies = runnerPerRequestDependencies(testInstance, runnerAnnotation,
								info);
						String[] setupDependencies = runnerSetupDependencies(testInstance, runnerAnnotation, info);
						runDependencies(testInstance, prepared, setupDependencies,
								info.withTags(runnerAnnotation.tags()), stack);
						executeRunner(testInstance, prepared, runnerAnnotation, info, stack,
								runnerAnnotation.lifecycle(),
								runnerAnnotation.lifecycle() && runnerUsesBeforeRequestRules(testMethod),
								perRequestDependencies);
					}
				} finally {
					endRunnerVerifyScope();
				}
			}
		} catch (Exception | Error e) {
			String localDebug = annotationDebug(requestAnnotation, responseAnnotation, callAnnotation,
					runnerAnnotation);
			C latest = latestContext(prepared, info.namespace, current.context);
			boolean concreteRunnerResult = runnerAnnotation != null && report != null
					&& report.hasRunnerRequest(info.method);
			boolean aggregateRunnerFailure = runnerAnnotation != null && e instanceof RunnerAggregateFailure;
			boolean completedRunnerResult = concreteRunnerResult || aggregateRunnerFailure;

			/*
			 * A top-level runner aggregate failure already emitted request/response debug
			 * for each concrete runner request. The parent runner info has no request name,
			 * while latest still points at the final request context. Printing failure
			 * diagnostics again here therefore makes the last HTTP request look as if it
			 * executed twice.
			 *
			 * Do not infer this only from the mutable report. During TestNG hook execution
			 * the report may not expose the runner request records at this exact outer
			 * catch point. RunnerAggregateFailure is emitted only by executeRunner after
			 * concrete request processing has finished, so it is the reliable signal that
			 * request diagnostics have already been produced.
			 */
			if (!isFrameworkSkip(e) && !completedRunnerResult) {
				debugOutputAfterFailure(testInstance, latest, info, localDebug);
			}
			String internalDiagnostic = internalDiagnosticLog(latest);
			if (isFrameworkSkip(e)) {
				if (!completedRunnerResult) {
					skipped(report, info);
				}
				JPostmanDebugFile.skipped(testInstance, info, localDebug, internalDiagnostic, e);
			} else {
				if (!completedRunnerResult) {
					failed(report, info, latest, e);
				}
				JPostmanDebugFile.failure(testInstance, info, localDebug, internalDiagnostic, e);
			}
			throw e;
		} finally {
			contextRunner.injectTestContexts(testInstance, prepared);
			contextRunner.injectLoadedContexts(testInstance, prepared);
			contextRunner.injectAssertContexts(testInstance, prepared);
		}
	}

	private boolean runnerUsesBeforeRequestRules(Method testMethod) {
		if (testMethod == null) {
			return false;
		}

		/*
		 * A @JPostmanRunner body is normally a post-response diagnostics callback.
		 * Fluent runner rule bodies need one extra before-request callback:
		 * runner().start(...) and runner().request(...) prepare the request before
		 * execution, while runner().response(...), has(...).then(...), any(...),
		 * otherwise(...), and end(...) run after the active runner response.
		 *
		 * Do not detect this by plain text like "request". User test bodies often
		 * mention info.request or request names for diagnostics, and that would make a
		 * plain after-only body run before the request too. Instead, inspect the target
		 * method bytecode and require an actual call to RunnerRules/RunnerCondition.
		 */
		byte[] bytecode = classBytes(testMethod.getDeclaringClass());
		if (bytecode == null || bytecode.length == 0) {
			return false;
		}

		try {
			return methodCallsRunnerRules(testMethod, bytecode);
		} catch (RuntimeException ignored) {
			/*
			 * Do not guess from raw class-file text. A diagnostic body may contain words
			 * such as "request" without calling RunnerRules.request(...), which would
			 * incorrectly execute the Java body twice per request.
			 */
			return false;
		}
	}

	private boolean methodCallsRunnerRules(Method target, byte[] bytes) {
		ClassFileView classFile = ClassFileView.read(bytes);
		String targetName = target.getName();
		String targetDescriptor = methodDescriptor(target);

		for (ClassFileMethod method : classFile.methods) {
			if (targetName.equals(method.name) && targetDescriptor.equals(method.descriptor)
					&& codeCallsRunnerRules(classFile, method.code)) {
				return true;
			}
		}
		return false;
	}

	private boolean codeCallsRunnerRules(ClassFileView classFile, byte[] code) {
		if (code == null || code.length == 0) {
			return false;
		}
		for (int i = 0; i < code.length; i++) {
			int opcode = code[i] & 0xff;
			if (opcode == 0xb6 || opcode == 0xb7 || opcode == 0xb8 || opcode == 0xb9) {
				if (i + 2 >= code.length) {
					continue;
				}
				ClassFileMethodRef ref = classFile.methodRef(u2(code, i + 1));
				if (isRunnerRuleMethod(ref)) {
					return true;
				}
			}
		}
		return false;
	}

	private boolean isRunnerRuleMethod(ClassFileMethodRef ref) {
		if (ref == null || ref.owner == null || ref.name == null) {
			return false;
		}
		if (!"io/jpostman/annotations/runtime/JPostmanRuntime$RunnerRules".equals(ref.owner)) {
			return false;
		}
		if ("request".equals(ref.name)) {
			return ref.descriptor != null && !ref.descriptor.startsWith("()");
		}
		return "start".equals(ref.name);
	}

	private String methodDescriptor(Method method) {
		StringBuilder descriptor = new StringBuilder("(");
		for (Class<?> parameter : method.getParameterTypes()) {
			descriptor.append(typeDescriptor(parameter));
		}
		descriptor.append(')').append(typeDescriptor(method.getReturnType()));
		return descriptor.toString();
	}

	private String typeDescriptor(Class<?> type) {
		if (type == void.class) {
			return "V";
		}
		if (type == boolean.class) {
			return "Z";
		}
		if (type == byte.class) {
			return "B";
		}
		if (type == char.class) {
			return "C";
		}
		if (type == short.class) {
			return "S";
		}
		if (type == int.class) {
			return "I";
		}
		if (type == long.class) {
			return "J";
		}
		if (type == float.class) {
			return "F";
		}
		if (type == double.class) {
			return "D";
		}
		if (type.isArray()) {
			return type.getName().replace('.', '/');
		}
		return "L" + type.getName().replace('.', '/') + ";";
	}

	private static int u1(byte[] bytes, int offset) {
		return bytes[offset] & 0xff;
	}

	private static int u2(byte[] bytes, int offset) {
		return ((bytes[offset] & 0xff) << 8) | (bytes[offset + 1] & 0xff);
	}

	private static int u4(byte[] bytes, int offset) {
		return (u2(bytes, offset) << 16) | u2(bytes, offset + 2);
	}

	private static final class ClassFileView {
		private final String[] utf8;
		private final int[] classNameIndex;
		private final int[] refClassIndex;
		private final int[] refNameAndTypeIndex;
		private final int[] nameIndex;
		private final int[] descriptorIndex;
		private final List<ClassFileMethod> methods;

		private ClassFileView(String[] utf8, int[] classNameIndex, int[] refClassIndex, int[] refNameAndTypeIndex,
				int[] nameIndex, int[] descriptorIndex, List<ClassFileMethod> methods) {
			this.utf8 = utf8;
			this.classNameIndex = classNameIndex;
			this.refClassIndex = refClassIndex;
			this.refNameAndTypeIndex = refNameAndTypeIndex;
			this.nameIndex = nameIndex;
			this.descriptorIndex = descriptorIndex;
			this.methods = methods;
		}

		private static ClassFileView read(byte[] bytes) {
			if (u4(bytes, 0) != 0xCAFEBABE) {
				throw new IllegalArgumentException("Not a class file");
			}
			int pos = 8;
			int count = u2(bytes, pos);
			pos += 2;
			String[] utf8 = new String[count];
			int[] classNameIndex = new int[count];
			int[] refClassIndex = new int[count];
			int[] refNameAndTypeIndex = new int[count];
			int[] nameIndex = new int[count];
			int[] descriptorIndex = new int[count];

			for (int i = 1; i < count; i++) {
				int tag = u1(bytes, pos++);
				switch (tag) {
				case 1: {
					int length = u2(bytes, pos);
					pos += 2;
					utf8[i] = new String(bytes, pos, length, java.nio.charset.StandardCharsets.UTF_8);
					pos += length;
					break;
				}
				case 7:
					classNameIndex[i] = u2(bytes, pos);
					pos += 2;
					break;
				case 9:
				case 10:
				case 11:
					refClassIndex[i] = u2(bytes, pos);
					refNameAndTypeIndex[i] = u2(bytes, pos + 2);
					pos += 4;
					break;
				case 12:
					nameIndex[i] = u2(bytes, pos);
					descriptorIndex[i] = u2(bytes, pos + 2);
					pos += 4;
					break;
				case 3:
				case 4:
				case 17:
				case 18:
					pos += 4;
					break;
				case 5:
				case 6:
					pos += 8;
					i++;
					break;
				case 8:
				case 16:
				case 19:
				case 20:
					pos += 2;
					break;
				case 15:
					pos += 3;
					break;
				default:
					throw new IllegalArgumentException("Unsupported constant-pool tag: " + tag);
				}
			}

			pos += 6;
			int interfaces = u2(bytes, pos);
			pos += 2 + interfaces * 2;
			pos = skipMembers(bytes, pos);
			List<ClassFileMethod> methods = readMethods(bytes, pos, utf8);
			return new ClassFileView(utf8, classNameIndex, refClassIndex, refNameAndTypeIndex, nameIndex,
					descriptorIndex, methods);
		}

		private static int skipMembers(byte[] bytes, int pos) {
			int fields = u2(bytes, pos);
			pos += 2;
			for (int i = 0; i < fields; i++) {
				pos += 6;
				int attributes = u2(bytes, pos);
				pos += 2;
				for (int a = 0; a < attributes; a++) {
					pos += 2;
					int length = u4(bytes, pos);
					pos += 4 + length;
				}
			}
			return pos;
		}

		private static List<ClassFileMethod> readMethods(byte[] bytes, int pos, String[] utf8) {
			int count = u2(bytes, pos);
			pos += 2;
			List<ClassFileMethod> methods = new ArrayList<>();
			for (int i = 0; i < count; i++) {
				pos += 2;
				String name = utf8[u2(bytes, pos)];
				String descriptor = utf8[u2(bytes, pos + 2)];
				pos += 4;
				byte[] code = null;
				int attributes = u2(bytes, pos);
				pos += 2;
				for (int a = 0; a < attributes; a++) {
					String attributeName = utf8[u2(bytes, pos)];
					int length = u4(bytes, pos + 2);
					pos += 6;
					if ("Code".equals(attributeName)) {
						int codeLength = u4(bytes, pos + 4);
						code = Arrays.copyOfRange(bytes, pos + 8, pos + 8 + codeLength);
					}
					pos += length;
				}
				methods.add(new ClassFileMethod(name, descriptor, code));
			}
			return methods;
		}

		private ClassFileMethodRef methodRef(int index) {
			if (index <= 0 || index >= refClassIndex.length) {
				return null;
			}
			int ownerIndex = refClassIndex[index];
			int nameTypeIndex = refNameAndTypeIndex[index];
			if (ownerIndex <= 0 || nameTypeIndex <= 0) {
				return null;
			}
			String owner = utf8[classNameIndex[ownerIndex]];
			String name = utf8[nameIndex[nameTypeIndex]];
			String descriptor = utf8[descriptorIndex[nameTypeIndex]];
			return new ClassFileMethodRef(owner, name, descriptor);
		}
	}

	private static final class ClassFileMethod {
		private final String name;
		private final String descriptor;
		private final byte[] code;

		private ClassFileMethod(String name, String descriptor, byte[] code) {
			this.name = name;
			this.descriptor = descriptor;
			this.code = code;
		}
	}

	private static final class ClassFileMethodRef {
		private final String owner;
		private final String name;
		private final String descriptor;

		private ClassFileMethodRef(String owner, String name, String descriptor) {
			this.owner = owner;
			this.name = name;
			this.descriptor = descriptor;
		}
	}

	private byte[] classBytes(Class<?> type) {
		if (type == null) {
			return null;
		}
		String resource = type.getName().replace('.', '/') + ".class";
		ClassLoader loader = type.getClassLoader();
		try (java.io.InputStream in = loader == null ? ClassLoader.getSystemResourceAsStream(resource)
				: loader.getResourceAsStream(resource)) {
			return in == null ? null : in.readAllBytes();
		} catch (java.io.IOException e) {
			return null;
		}
	}

	private void validateCallCombination(JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanCall callAnnotation, JPostmanRunner runnerAnnotation, Method testMethod) {
		if (callAnnotation == null) {
			return;
		}
		if (requestAnnotation != null || responseAnnotation != null || runnerAnnotation != null) {
			throw new IllegalStateException("@JPostman.Call cannot be combined with @JPostman.Request, "
					+ "@JPostman.Response, or @JPostman.Runner on the same method: " + testMethod.getName());
		}
	}

	private void registerJPostmanCallRuntime(Object testInstance, Method testMethod, PreparedContexts<C> prepared,
			JPostmanCall annotation, JPostmanInfo info) {
		JPostmanRuntimeCall
				.register(testInstance, framework.contextType(),
						(BiConsumer<C, JPostmanInfo> action) -> executeRuntimeCall(testInstance, prepared, annotation,
								info, action),
						error -> cleanRuntimeCallFailure(testInstance, testMethod, annotation, info, error));
	}

	private Throwable cleanRuntimeCallFailure(Object testInstance, Method testMethod, JPostmanCall annotation,
			JPostmanInfo info, Throwable error) {
		String localDebug = annotation == null ? "" : annotation.debug();
		Throwable root = JPostmanStackTraceCleaner.rootCause(error);
		if (root instanceof AssertionError) {
			Throwable display = runtimeCallAssertion(info, root, failureDiagnostics(testInstance, localDebug, info));
			JPostmanRuntimeOptions.from(testInstance).markFailure(display, localDebug);
			return JPostmanAnnotationEngine.cleanRuntimeFailure(testInstance, testMethod, display, localDebug);
		}
		return JPostmanAnnotationEngine.cleanThrowable(testInstance, testMethod, error, localDebug);
	}

	private AssertionError runtimeCallAssertion(JPostmanInfo info, Throwable assertion, boolean includeDiagnostics) {
		String detail = endLine(appendSuppressedMessages(
				JPostmanErrors.stripSuffix(value(assertion == null ? null : assertion.getMessage())).trim(), assertion,
				includeDiagnostics));
		AssertionError error = JPostmanErrors.usage(info, detail);
		copyFailureDetails(assertion, error, includeDiagnostics);
		if (assertion != null) {
			error.setStackTrace(assertion.getStackTrace());
		}
		return error;
	}

	private void validateLocalDebugs(JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanCall callAnnotation, JPostmanRunner runnerAnnotation, JPostmanInfo info) {
		if (requestAnnotation != null) {
			validateLocalDebug(requestAnnotation.debug(), info);
		}
		if (responseAnnotation != null) {
			validateLocalDebug(responseAnnotation.debug(), info);
		}
		if (callAnnotation != null) {
			validateLocalDebug(callAnnotation.debug(), info);
		}
		if (runnerAnnotation != null) {
			validateLocalDebug(runnerAnnotation.debug(), info);
		}
	}

	private void validateLocalDebug(String debug, JPostmanInfo info) {
		try {
			JPostmanRuntimeOptions.DebugMode.validateLocal(debug);
		} catch (IllegalArgumentException e) {
			throw JPostmanErrors.usage(info, e.getMessage());
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

		validateLocalDebug(annotation.debug(), info);

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

		validateLocalDebug(annotation.debug(), info);

		if (isReusableResponseDependency(testInstance, annotation, info)) {
			C ctx = applyRuleAndFilter(prepared.context(info.namespace), annotation.rules());
			prepared.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()),
					info.withTags(annotation.tags()), stack);
			applyData(testInstance, prepared, info, data, stack);

			C source = prepared.activeContext();
			if (source == null) {
				source = prepared.context(info.namespace);
			}
			ctx = applyResponseFilter(source, annotation.filter());
			prepared.update(info.namespace, ctx);
			framework.setCurrent(ctx);
			info.request = "";
			return ctx;
		}

		/*
		 * Resolve annotation location before the first request lookup. A response may
		 * declare the request name itself while a blank-request dependency supplies the
		 * namespace/folder. Waiting until after dependencies would make the first
		 * lookup incorrectly use the default namespace/root folder.
		 */
		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		validateResponseRequestName(info);

		String ownerNamespace = info.namespace;
		C ownerContext = prepared.context(ownerNamespace);
		C ctx = prepareRequest(ownerContext, prepared.collection(info.namespace), annotation, info, false);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);

		runDependencies(testInstance, prepared, dependencies(annotation.dependsOn()), info.withTags(annotation.tags()),
				stack);
		boolean responseDependency = hasDirectResponseDependency(testInstance, dependencies(annotation.dependsOn()),
				info);
		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		validateResponseRequestName(info);
		applyData(testInstance, prepared, info, data, stack);

		C source = prepared.context(info.namespace);
		if (responseDependency && hasResponseRequest(info) && hasFilter(annotation.filter())) {
			/*
			 * The dependency response owns its filter. The caller response owns its filter.
			 * When the caller has its own request, start from a fresh context and copy only
			 * dependency cache values; otherwise the dependency filter remains attached and
			 * the output becomes merged, for example firstName+lastName.
			 */
			source = freshContext(prepared, info.namespace, source);
		}

		ctx = prepareRequest(source, prepared.collection(info.namespace), annotation, info, true);
		prepared.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		return ctx;
	}

	private boolean isReusableResponseDependency(Object testInstance, JPostmanResponse annotation, JPostmanInfo info) {
		return annotation != null && isBlank(annotation.request())
				&& hasDirectResponseDependency(testInstance, dependencies(annotation.dependsOn()), info);
	}

	private boolean hasResponseRequest(JPostmanInfo info) {
		return info != null && info.request != null && !info.request.isBlank();
	}

	private boolean hasFilter(String... filter) {
		return filter != null && filter.length > 0;
	}

	private boolean hasDirectResponseDependency(Object testInstance, String[] dependencyNames, JPostmanInfo info) {
		if (dependencyNames == null || dependencyNames.length == 0) {
			return false;
		}

		for (String dependencyName : dependencyNames) {
			String name = value(dependencyName).trim();
			if (name.isBlank()) {
				continue;
			}

			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), name, info);
			if (JPostmanAnnotations.response(dependencyMethod) != null) {
				return true;
			}
		}

		return false;
	}

	private void completeReusedResponse(Object testInstance, PreparedContexts<C> prepared, C ctx,
			JPostmanResponse annotation, JPostmanInfo info) throws Exception {
		C latest = ctx != null ? ctx : latestContext(prepared, info.namespace, ctx);
		prepared.update(info.namespace, latest);
		framework.setCurrent(latest);
		runExecutorInterceptors(testInstance, prepared, latest, info);
		prepared.info(info);
		applyAssertions(testInstance, prepared, latest, info, annotation.asserts(), annotation.debug());
		verifyResponse(testInstance, latest, info, annotation.verify(), annotation.debug());
		debugOutput(testInstance, latest, info, annotation.debug());
		passed(report(testInstance), info);
		JPostmanTestProxy.recordResponse(testInstance, info.id, latest);
	}

	/**
	 * Requires an executable request name for a response annotation. A response may
	 * declare the request directly or inherit it from a request dependency that
	 * declares {@code request = "..."}. A blank-request dependency may still supply
	 * namespace/folder scope, but it cannot supply the executable request name.
	 */
	private void validateResponseRequestName(JPostmanInfo info) {
		if (hasResponseRequest(info)) {
			return;
		}

		throw JPostmanErrors.usage(info, "@JPostman.Response request name is missing.",
				"Set request = \"...\" on @JPostman.Response, or depend on @JPostman.Request that defines request = \"...\".");
	}

	private void validateCallRequestName(JPostmanInfo info) {
		if (hasResponseRequest(info)) {
			return;
		}

		throw JPostmanErrors.usage(info, "@JPostman.Call request name is missing.",
				"Set request = \"...\" on @JPostman.Call, or depend on @JPostman.Request that defines request = \"...\".");
	}

	private void inheritResponseLocationFromDependencies(Object testInstance, JPostmanResponse annotation,
			JPostmanInfo info) {
		if (annotation == null || info == null) {
			return;
		}

		inheritLocationFromDependencies(testInstance, dependencies(annotation.dependsOn()), info,
				new LinkedHashSet<>());
	}

	/**
	 * Fills only missing location components from request/response dependencies.
	 * Explicit values on the current annotation always win. This is important for
	 * combinations such as a response that declares {@code request = "..."} while a
	 * blank-request dependency supplies only {@code namespace} and {@code folder}.
	 */
	private void inheritLocationFromDependencies(Object testInstance, String[] dependencyNames, JPostmanInfo info,
			Set<String> visited) {
		if (dependencyNames == null || dependencyNames.length == 0 || info == null) {
			return;
		}

		for (String dependencyName : dependencyNames) {
			String name = value(dependencyName).trim();
			if (name.isBlank() || !visited.add(name)) {
				continue;
			}

			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), name, info);
			JPostmanRequest requestAnnotation = JPostmanAnnotations.request(dependencyMethod);
			if (requestAnnotation != null) {
				mergeMissingLocation(info, requestAnnotation.namespace(), folder(requestAnnotation.folder()),
						requestAnnotation.request(), requestAnnotation.id());
				inheritLocationFromDependencies(testInstance, dependencies(requestAnnotation.dependsOn()), info,
						visited);
			}

			JPostmanResponse responseAnnotation = JPostmanAnnotations.response(dependencyMethod);
			if (responseAnnotation != null) {
				mergeMissingLocation(info, responseAnnotation.namespace(), folder(responseAnnotation.folder()),
						responseAnnotation.request(), responseAnnotation.id());
				inheritLocationFromDependencies(testInstance, dependencies(responseAnnotation.dependsOn()), info,
						visited);
			}
		}
	}

	private void mergeMissingLocation(JPostmanInfo info, String namespace, String folder, String request, String id) {
		if (isBlank(info.namespace) && !isBlank(namespace)) {
			info.namespace = namespace;
		}
		if (isBlank(info.folder) && !isBlank(folder)) {
			info.folder = folder;
		}
		if (isBlank(info.request) && !isBlank(request)) {
			info.request = request;
			info.requestId(annotationId(id));
		}
	}

	private void inheritCallLocationFromDependencies(Object testInstance, JPostmanCall annotation, JPostmanInfo info) {
		if (annotation == null || info == null) {
			return;
		}

		inheritLocationFromDependencies(testInstance, dependencies(annotation.dependsOn()), info,
				new LinkedHashSet<>());
	}

	private JPostmanInfo info(String methodName, JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanCall callAnnotation, JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			JPostmanInfo info = new JPostmanInfo(requestAnnotation.tags(), requestAnnotation.executor(), methodName,
					requestAnnotation.namespace(), folder(requestAnnotation.folder()), requestAnnotation.request())
					.annotation("@JPostmanRequest").id(requestAnnotation.id()).debug(requestAnnotation.debug());
			if (!isBlank(requestAnnotation.request())) {
				info.requestId(requestAnnotation.id());
			}
			return info;
		}

		if (responseAnnotation != null) {
			return new JPostmanInfo(responseAnnotation.tags(), responseAnnotation.executor(), methodName,
					responseAnnotation.namespace(), folder(responseAnnotation.folder()), responseAnnotation.request())
					.annotation("@JPostmanResponse").id(responseAnnotation.id()).debug(responseAnnotation.debug());
		}

		if (callAnnotation != null) {
			return new JPostmanInfo(callAnnotation.tags(), callAnnotation.executor(), methodName,
					callAnnotation.namespace(), folder(callAnnotation.folder()), callAnnotation.request())
					.annotation("@JPostmanCall").id(callAnnotation.id()).debug(callAnnotation.debug());
		}

		return new JPostmanInfo(runnerAnnotation.tags(), runnerAnnotation.executor(), methodName,
				runnerAnnotation.namespace(), folder(runnerAnnotation.folder()), "").annotation("@JPostmanRunner")
				.id(annotationId(runnerAnnotation.id())).debug(runnerAnnotation.debug());
	}

	/**
	 * Resolves a runner's effective namespace and folder from a direct
	 * {@code @JPostman.Request} dependency when the runner leaves those values
	 * blank. Both blank-request and named-request dependencies may provide the
	 * runner scope. The runner's {@code include} and {@code exclude} values remain
	 * responsible for selecting which requests are executed. Explicit runner values
	 * always take precedence.
	 */
	private void prepareRunnerScope(Object testInstance, PreparedContexts<C> resolver, JPostmanRunner annotation,
			JPostmanInfo info) {
		if (annotation == null || info == null) {
			return;
		}

		boolean inheritNamespace = isBlank(annotation.namespace());
		boolean inheritFolder = isBlank(folder(annotation.folder()));
		if (!inheritNamespace && !inheritFolder) {
			return;
		}

		for (String dependencyName : dependencies(annotation.dependsOn())) {
			if (dependencyName == null || dependencyName.isBlank()) {
				continue;
			}

			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), dependencyName.trim(), info);
			JPostmanRequest request = JPostmanAnnotations.request(dependencyMethod);
			if (request == null) {
				continue;
			}

			String requestNamespace = value(request.namespace()).trim();
			String requestFolder = folder(request.folder());
			if (requestNamespace.isBlank() && requestFolder.isBlank()) {
				continue;
			}

			if (inheritNamespace && !requestNamespace.isBlank()) {
				info.namespace = requestNamespace;
			}
			if (inheritFolder && !requestFolder.isBlank()) {
				info.folder = requestFolder;
			}
			break;
		}

		PreparedContext<C> current = resolver.resolve(info.namespace);
		info.context(current.contextAnnotation);
		resolver.info(info);
		framework.setCurrent(current.context);
		captureDebugContext(current, info);
	}

	/**
	 * Returns blank-request {@code @JPostman.Request} dependencies that should be
	 * invoked once for every selected runner request. Runner lifecycle controls
	 * only the Java runner-body callback timing; it must not change request-helper
	 * scope. The active runner request supplies the missing request name, while the
	 * helper may still provide namespace/folder scope and request customizations.
	 */
	private String[] runnerPerRequestDependencies(Object testInstance, JPostmanRunner annotation, JPostmanInfo info) {
		return runnerDependencies(testInstance, annotation, info, true);
	}

	/**
	 * Returns runner dependencies that retain the existing one-time setup behavior.
	 */
	private String[] runnerSetupDependencies(Object testInstance, JPostmanRunner annotation, JPostmanInfo info) {
		return runnerDependencies(testInstance, annotation, info, false);
	}

	private String[] runnerDependencies(Object testInstance, JPostmanRunner annotation, JPostmanInfo info,
			boolean perRequest) {
		List<String> selected = new ArrayList<>();
		if (testInstance == null || annotation == null) {
			return new String[0];
		}

		for (String dependencyName : dependencies(annotation.dependsOn())) {
			if (dependencyName == null || dependencyName.isBlank()) {
				continue;
			}

			Method method = findDependencyMethod(testInstance.getClass(), dependencyName.trim(), info);
			JPostmanRequest request = JPostmanAnnotations.request(method);
			boolean blankRequestDependency = request != null && isBlank(request.request());
			if (blankRequestDependency == perRequest) {
				selected.add(dependencyName);
			}
		}

		return selected.toArray(String[]::new);
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

		String cache = cacheKey(dependencyMethod, annotation.cache(), annotation.id());
		/*
		 * Response dependencies use their own annotation location exactly. A blank
		 * namespace resolves through the selected/default void executor namespace, and
		 * a blank folder means the root folder; neither value is inherited from an
		 * unrelated explicit parent location.
		 */
		JPostmanInfo info = parentInfo
				.childExactRequestScope(dependencyMethod.getName(), new String[0], annotation.executor(), cache,
						annotation.namespace(), folder(annotation.folder()), annotation.request())
				.annotation("@JPostmanResponse").id(annotationId(annotation.id())).debug(annotation.debug());
		/*
		 * A blank dependency namespace still uses the selected/default void
		 * 
		 * @JPostman.Executor namespace. Resolve it before selecting the collection;
		 * otherwise top-level methods run in the default executor namespace while a
		 * nested response dependency incorrectly falls back to namespace "".
		 */
		applyDefaultExecutorNamespace(testInstance, info);
		info = info.context(resolver.resolve(info.namespace).contextAnnotation);
		registerCacheAlias(resolver, info.id, cache);
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

		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		validateResponseRequestName(info);
		/*
		 * Prepare the dependency request without installing this response's filter.
		 * Nested request/response dependencies must execute with their own filter
		 * state; otherwise the parent response filter leaks into a child response body.
		 */
		C ctx = prepareRequest(resolver.context(info.namespace), resolver.collection(info.namespace), annotation, info,
				false);
		resolver.update(info.namespace, ctx);
		framework.setCurrent(ctx);
		runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()), info.withTags(annotation.tags()),
				stack);
		inheritResponseLocationFromDependencies(testInstance, annotation, info);
		validateResponseRequestName(info);
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
						folder(annotation.folder()), parentInfo.request)
				.annotation("@JPostmanRunner").id(annotationId(annotation.id())).debug(annotation.debug());
		prepareRunnerScope(testInstance, resolver, annotation, info);
		JPostmanInfo runnerInfo = info.context(resolver.resolve(info.namespace).contextAnnotation);
		runnerInfo.method(dependencyMethod.getName());
		resolver.info(runnerInfo);
		applyData(testInstance, resolver, runnerInfo, annotation.data(), stack);
		JPostmanReport report = report(testInstance);
		add(report, runnerInfo);
		validateRunnerSkipEnabled(annotation, runnerInfo);
		if (skipRunner(annotation)) {
			skipped(report, runnerInfo);
			throw JPostmanErrors.skip(framework, runnerInfo, runnerSkipLines(annotation, runnerInfo));
		}
		beginRunnerVerifyScope(annotation.verify());
		try {
			String[] perRequestDependencies = runnerPerRequestDependencies(testInstance, annotation, runnerInfo);
			String[] setupDependencies = runnerSetupDependencies(testInstance, annotation, runnerInfo);
			if (annotation.lifecycle()) {
				runDependencies(testInstance, resolver, setupDependencies, runnerInfo.withTags(annotation.tags()),
						stack);
				executeRunner(testInstance, resolver, annotation, runnerInfo, stack, true,
						runnerUsesBeforeRequestRules(dependencyMethod), perRequestDependencies,
						(ctx, callbackInfo) -> invokeAnnotated(testInstance, dependencyMethod, ctx, callbackInfo));
				return;
			}

			runCachedDependency(testInstance, resolver, dependencyMethod, runnerInfo, "", () -> {
				runDependencies(testInstance, resolver, setupDependencies, runnerInfo.withTags(annotation.tags()),
						stack);
				executeRunner(testInstance, resolver, annotation, runnerInfo, stack, false, false,
						perRequestDependencies);
			});
		} finally {
			endRunnerVerifyScope();
		}
	}

	private Method runnerDependencyLauncherMethod(Class<?> type, JPostmanRunner annotation, JPostmanInfo info) {
		if (!isRunnerDependencyLauncher(annotation)) {
			return null;
		}

		String reference = dependencies(annotation.dependsOn())[0];
		Method dependencyMethod = findDependencyMethod(type, reference, info);
		return JPostmanAnnotations.runner(dependencyMethod) == null ? null : dependencyMethod;
	}

	private boolean isRunnerDependencyLauncher(JPostmanRunner annotation) {
		if (annotation == null || dependencies(annotation.dependsOn()).length != 1) {
			return false;
		}
		return isBlank(annotation.namespace()) && isBlank(folder(annotation.folder())) && isEmpty(annotation.rules())
				&& isBlank(annotation.executor()) && isBlank(annotation.data()) && isEmpty(annotation.include())
				&& isEmpty(annotation.exclude()) && isEmpty(annotation.filter()) && isEmpty(annotation.asserts())
				&& annotation.verify() == -1;
	}

	private boolean isEmpty(String[] values) {
		if (values == null || values.length == 0) {
			return true;
		}
		for (String value : values) {
			if (!isBlank(value)) {
				return false;
			}
		}
		return true;
	}

	private void runRunnerDependencyLauncher(Object testInstance, PreparedContexts<C> resolver, Method currentMethod,
			Method reusableRunner, JPostmanRunner currentAnnotation, JPostmanInfo currentInfo, List<String> stack)
			throws Exception {

		String reference = dependencies(currentAnnotation.dependsOn())[0];
		if (currentMethod != null && currentMethod.equals(reusableRunner)) {
			throw JPostmanErrors.usage(currentInfo,
					"JPostman runner cannot depend on itself as a reusable runner: " + reference,
					"Use dependsOn to reference another @JPostmanRunner method or id.");
		}

		JPostmanRunner reusableAnnotation = JPostmanAnnotations.runner(reusableRunner);
		String stackName = isIdReference(reference) ? reference : reusableRunner.getName();
		if (stack.contains(stackName)) {
			List<String> chain = new ArrayList<>(stack);
			chain.add(stackName);
			throw JPostmanErrors.usage(currentInfo, "Circular JPostman runner dependency detected.",
					"Dependency chain: " + String.join(" -> ", chain),
					"A JPostman runner cannot reuse a runner that is already running.");
		}

		stack.add(stackName);
		try {
			runReusableRunnerDependency(testInstance, resolver, currentMethod, reusableRunner, reusableAnnotation,
					currentAnnotation, currentInfo.withTags(currentAnnotation.tags()), stack);
		} finally {
			stack.remove(stack.size() - 1);
		}
	}

	private void runReusableRunnerDependency(Object testInstance, PreparedContexts<C> resolver, Method launcherMethod,
			Method reusableRunner, JPostmanRunner reusableAnnotation, JPostmanRunner launcherAnnotation,
			JPostmanInfo parentInfo, List<String> stack) throws Exception {

		JPostmanInfo info = parentInfo
				.child(reusableRunner.getName(), new String[0], reusableAnnotation.executor(), "",
						reusableAnnotation.namespace(), folder(reusableAnnotation.folder()), parentInfo.request)
				.annotation("@JPostmanRunner").id(annotationId(reusableAnnotation.id()))
				.debug(reusableAnnotation.debug());
		prepareRunnerScope(testInstance, resolver, reusableAnnotation, info);
		JPostmanInfo runnerInfo = info.context(resolver.resolve(info.namespace).contextAnnotation);
		runnerInfo.method(reusableRunner.getName());
		resolver.info(runnerInfo);
		applyData(testInstance, resolver, runnerInfo, reusableAnnotation.data(), stack);
		JPostmanReport report = report(testInstance);
		add(report, runnerInfo);
		validateRunnerSkipEnabled(reusableAnnotation, runnerInfo);
		if (skipRunner(reusableAnnotation)) {
			skipped(report, runnerInfo);
			throw JPostmanErrors.skip(framework, runnerInfo, runnerSkipLines(reusableAnnotation, runnerInfo));
		}

		String[] perRequestDependencies = runnerPerRequestDependencies(testInstance, reusableAnnotation, runnerInfo);
		String[] setupDependencies = runnerSetupDependencies(testInstance, reusableAnnotation, runnerInfo);
		runDependencies(testInstance, resolver, setupDependencies, runnerInfo.withTags(reusableAnnotation.tags()),
				stack);
		executeRunner(testInstance, resolver, reusableAnnotation, runnerInfo, stack, true,
				reusableAnnotation.lifecycle() && runnerUsesBeforeRequestRules(reusableRunner), perRequestDependencies,
				reusableRunnerBodyCallback(testInstance, reusableRunner, launcherMethod, launcherAnnotation));
	}

	private RunnerBodyCallback<C> reusableRunnerBodyCallback(Object testInstance, Method reusableRunner,
			Method launcherMethod, JPostmanRunner launcherAnnotation) {
		boolean launcherBefore = launcherAnnotation != null && launcherAnnotation.lifecycle()
				&& runnerUsesBeforeRequestRules(launcherMethod);
		return (ctx, callbackInfo) -> {
			if (JPostmanRuntimeRunner.isBeforeRequest()) {
				invokeAnnotated(testInstance, reusableRunner, ctx, callbackInfo);
				if (launcherBefore) {
					invokeRunnerLauncherBody(testInstance, launcherMethod, ctx, callbackInfo);
				}
				return;
			}

			Throwable failure = null;
			try {
				invokeAnnotated(testInstance, reusableRunner, ctx, callbackInfo);
			} catch (Exception | Error e) {
				if (!JPostmanRuntimeRunner.isRunnerBodyComplete(e)) {
					failure = e;
				}
			}

			try {
				invokeRunnerLauncherBody(testInstance, launcherMethod, ctx, callbackInfo);
			} catch (Exception | Error e) {
				if (!JPostmanRuntimeRunner.isRunnerBodyComplete(e)) {
					if (failure != null) {
						failure.addSuppressed(e);
					} else {
						failure = e;
					}
				}
			}

			if (failure instanceof Exception) {
				throw (Exception) failure;
			}
			if (failure instanceof Error) {
				throw (Error) failure;
			}
		};
	}

	/**
	 * Invokes the active reusable-runner launcher body through the framework hook
	 * when one is available.
	 *
	 * <p>
	 * TestNG requires {@code IHookCallBack#runTestMethod(...)} to be invoked for an
	 * {@code IHookable} test. Calling the launcher only through reflection executes
	 * its Java body but leaves TestNG believing the method was never invoked. JUnit
	 * uses the same callback to mark its intercepted invocation as proceeded.
	 * Direct annotation-engine callers do not provide a framework callback, so they
	 * keep the reflective fallback.
	 * </p>
	 */
	private void invokeRunnerLauncherBody(Object testInstance, Method launcherMethod, C ctx, JPostmanInfo info)
			throws Exception {
		if (afterRunnerRequestCallback != null) {
			afterRunnerRequestCallback.run();
			return;
		}
		invokeAnnotated(testInstance, launcherMethod, ctx, info);
	}

	private JPostmanInfo requestDependencyInfo(JPostmanInfo parentInfo, Method dependencyMethod,
			JPostmanRequest annotation, String cache) {
		/*
		 * A request helper that owns a request attribute gets its own exact request
		 * location. A blank namespace resolves through the selected/default void
		 * executor namespace while the helper runs. Helpers without a request continue
		 * to inherit the parent context so they can act as generic setup/tag helpers.
		 */
		JPostmanInfo info = isBlank(annotation.request())
				? parentInfo.child(dependencyMethod.getName(), new String[0], annotation.executor(), cache,
						annotation.namespace(), folder(annotation.folder()), annotation.request())
				: parentInfo.childExact(dependencyMethod.getName(), "", annotation.executor(), cache,
						annotation.namespace(), folder(annotation.folder()), annotation.request());

		info = info.annotation("@JPostmanRequest").id(annotationId(annotation.id())).debug(annotation.debug());
		if (!isBlank(annotation.request())) {
			info.requestId(annotation.id());
		}
		return info;
	}

	private void runRequestDependency(Object testInstance, PreparedContexts<C> resolver, Method dependencyMethod,
			JPostmanRequest annotation, JPostmanInfo parentInfo, List<String> stack) throws Exception {

		String cache = cacheKey(dependencyMethod, annotation.cache(), annotation.id());
		JPostmanInfo dependencyInfo = requestDependencyInfo(parentInfo, dependencyMethod, annotation, cache);
		/*
		 * Request helpers that declare their own request use an exact location. When
		 * that exact namespace is blank, it still resolves through the single/default
		 * void executor namespace before collection lookup.
		 */
		applyDefaultExecutorNamespace(testInstance, dependencyInfo);
		registerCacheAlias(resolver, dependencyInfo.id, cache);
		resolver.info(dependencyInfo);
		JPostmanReport report = report(testInstance);
		add(report, dependencyInfo);

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
		 * chain before invocation so info.print() shows method/methodIndex and the full
		 * path including the current method. This also keeps the invocation chain
		 * consistent when the current helper is skipped because its own cache key
		 * already exists.
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
			// Nested dependencies replace the resolver's current info while they run.
			// Restore this request helper before injecting JPostman.Test so runtime
			// log/print/get operations observe the helper values being added below.
			resolver.info(dependencyInfo);
			framework.setCurrent(ctx);

			try {
				C requestContext = resolver.context(contextNamespace);
				dependencyInfo.liveParams((key, value) -> framework.plain(requestContext, key, value));
				final C printBaseContext = requestContext;
				final JPostmanInfo liveInfo = dependencyInfo;
				Supplier<C> printTrueContext = () -> prepareRequest(printBaseContext,
						resolver.collection(contextNamespace), annotation, liveInfo, contextNamespace, contextFolder,
						contextRequest);
				Object value = invokeAnnotated(testInstance, dependencyMethod, requestContext, dependencyInfo,
						printTrueContext);
				/*
				 * @JPostman.Request methods prepare the next request and do not own response
				 * verification. In particular, a runner with verify = 0 must not have the
				 * context default re-applied here against the previous runner/dependency
				 * response. Response, Runner, and Call execution paths verify their own
				 * responses with the annotation's effective verify value.
				 */
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

		Object cacheValue = dependencyMethod.getReturnType() == Void.TYPE
				? JPostmanResponseSnapshot.create(resolver.context(info.namespace))
				: snapshotCacheValue(value);

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

	private Object snapshotCacheValue(Object value) {
		if (value instanceof JPostman.Test) {
			return JPostmanResponseSnapshot.create(value);
		}
		return value;
	}

	private C freshContext(PreparedContexts<C> prepared, String namespace, C cacheSource) {
		C result = framework.create();
		PreparedContext<C> preparedContext = prepared.resolve(namespace);
		if (preparedContext.loaded != null) {
			loadEnvironment(result, preparedContext.loaded.getEnvironment());
		}
		framework.copyCache(cacheSource, result);
		framework.copyRuntimeValues(cacheSource, result);
		return result;
	}

	private void loadEnvironment(C context, Environment environment) {
		if (context == null || environment == null) {
			return;
		}

		framework.secret(context, environment);
		environment.getParams().keySet().forEach(key -> {
			Params.Entry entry = environment.entry(key);
			if (entry.isEnabled()) {
				framework.plain(context, key, entry.getValue());
			} else {
				framework.secret(context, key, entry.getValue());
			}
		});
		JPostmanTestProxy.registerEnvironment(context, environment);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, JPostmanInfo info) {
		return prepareRequest(context, collection, annotation, info, info.namespace, info.folder, info.request);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRequest annotation, JPostmanInfo info,
			String namespace, String folder, String requestName) {
		C result = applyRuleAndFilter(context, annotation.rules());
		if (requestName == null || requestName.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, namespace, folder, requestName), info);
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation, JPostmanInfo info) {
		return prepareRequest(context, collection, annotation, info, true);
	}

	private C prepareRequest(C context, Collection collection, JPostmanResponse annotation, JPostmanInfo info,
			boolean includeFilter) {
		C result = includeFilter ? applyRuleAndFilter(context, annotation.rules(), annotation.filter())
				: applyRuleAndFilter(context, annotation.rules());
		if (info.request == null || info.request.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, info.namespace, info.folder, info.request), info);
	}

	private C prepareRequest(C context, Collection collection, JPostmanCall annotation, JPostmanInfo info) {
		C result = applyRuleAndFilter(context, annotation.rules(), annotation.filter());
		if (info.request == null || info.request.isBlank()) {
			return result;
		}
		return requestWithCache(result, request(collection, info.namespace, info.folder, info.request), info);
	}

	private C prepareRequest(C context, Collection collection, JPostmanRunner annotation, JPostmanInfo info,
			String requestName) {
		C result = applyRuleAndFilter(context, annotation.rules(), annotation.filter());
		return requestWithCache(result, request(collection, info.namespace, info.folder, requestName), info);
	}

	private C requestWithCache(C context, Request request, JPostmanInfo info) {
		C result = framework.request(context, request, info);
		framework.copyCache(context, result);
		framework.copyRuntimeValues(context, result);
		return result;
	}

	private C applyFilter(C context, String... filter) {
		C result = context;
		if (filter != null && filter.length > 0) {
			C previous = result;
			result = framework.filter(result, filter);
			framework.copyCache(previous, result);
			framework.copyRuntimeValues(previous, result);
		}
		return result;
	}

	private C applyResponseFilter(C context, String... filter) {
		C result = context;
		if (filter != null && filter.length > 0) {
			C previous = result;
			result = framework.filterResponse(result, filter);
			framework.copyCache(previous, result);
			framework.copyRuntimeValues(previous, result);
		}
		return result;
	}

	private C applyRuleAndFilter(C context, String[] rules, String... filter) {
		C result = context;
		if (!isEmpty(rules)) {
			C previous = result;
			result = framework.loadRules(result, rules);
			framework.copyCache(previous, result);
			framework.copyRuntimeValues(previous, result);
		}
		if (filter != null && filter.length > 0) {
			C previous = result;
			result = framework.filter(result, filter);
			framework.copyCache(previous, result);
			framework.copyRuntimeValues(previous, result);
		}
		return result;
	}

	private Request request(Collection collection, String namespace, String folder, String request) {
		captureDebugCollection(collection, folder);
		try {
			return JPostmanFolderPath.request(collection, folder, request);
		} catch (AssertionError | RuntimeException e) {
			AssertionError error = new AssertionError("Request not found: \"" + request + "\" (namespace="
					+ value(namespace) + ", folder=" + folderValue(folder) + ")");
			error.initCause(e);
			throw error;
		}
	}

	private C executeRuntimeCall(Object testInstance, PreparedContexts<C> resolver, JPostmanCall annotation,
			JPostmanInfo info, BiConsumer<C, JPostmanInfo> action) throws Exception {

		JPostmanTestProxy.clearResponse(testInstance, info.id);
		validateLocalDebug(annotation.debug(), info);
		rejectVerifyAndAsserts(annotation, info);
		inheritCallLocationFromDependencies(testInstance, annotation, info);
		validateCallRequestName(info);
		PreparedContext<C> prepared = resolver.resolve(info.namespace);
		Collection collection = prepared.collection;
		List<String> stack = new ArrayList<>();
		stack.add(info.method);
		C ctx = null;
		try {
			ctx = prepareRequest(prepared.context, collection, annotation, info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			runDependencies(testInstance, resolver, dependencies(annotation.dependsOn()),
					info.withTags(annotation.tags()), stack);
			inheritCallLocationFromDependencies(testInstance, annotation, info);
			prepared = resolver.resolve(info.namespace);
			collection = prepared.collection;
			applyData(testInstance, resolver, info, annotation.data(), stack);

			ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			if (action != null) {
				action.accept(ctx, info);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			ExecutorCall<C> executor = executorCall(testInstance, ctx, info);
			resolver.info(executor.info);
			int executorIndex = info.appendMethod(executorStep(executor.name, info));
			executor.info.methodIndex(executorIndex);
			add(report(testInstance), executor.info);

			for (String dependencyName : executor.dependsOn()) {
				runDependency(testInstance, resolver, dependencyName, executor.info, stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			Object result = executor.result(testInstance, ctx);
			verifyExecutorResult(result, executor.name, info);

			ctx = executeWithExecutorInterceptors(testInstance, resolver, ctx, info, (ApiExecutor) result,
					completed -> applyFilter(completed, annotation.filter()));
			resolver.info(info);
			// @JPostman.Call is prepared before the test body, but its response exists
			// only after runtime.call(...) executes. Apply assertion sections and status
			// verification against the completed manual-call response.
			applyAssertions(testInstance, resolver, ctx, info, annotation.asserts(), annotation.debug());
			verifyResponse(testInstance, ctx, info, annotation.verify(), annotation.debug());
			debugOutput(testInstance, ctx, info, annotation.debug());
			passed(report(testInstance), info);
			JPostmanTestProxy.recordResponse(testInstance, info.id, ctx);
			return ctx;
		} catch (Exception | Error e) {
			C latest = latestContext(resolver, info.namespace, ctx);
			debugOutputAfterFailure(testInstance, latest, info, annotation.debug());
			failed(report(testInstance), info, latest, e);
			throw executionFailure(testInstance, latest, info, e, annotation.debug());
		}
	}

	private void executeRunner(Object testInstance, PreparedContexts<C> resolver, JPostmanRunner annotation,
			JPostmanInfo info, List<String> stack, boolean notifyAfterRequest, boolean notifyBeforeRequest,
			String[] perRequestDependencies) throws Exception {
		executeRunner(testInstance, resolver, annotation, info, stack, notifyAfterRequest, notifyBeforeRequest,
				perRequestDependencies, frameworkRunnerBodyCallback());
	}

	private RunnerBodyCallback<C> frameworkRunnerBodyCallback() {
		return afterRunnerRequestCallback == null ? null : (ctx, info) -> afterRunnerRequestCallback.run();
	}

	private void executeRunner(Object testInstance, PreparedContexts<C> resolver, JPostmanRunner annotation,
			JPostmanInfo info, List<String> stack, boolean notifyAfterRequest, boolean notifyBeforeRequest,
			String[] perRequestDependencies, RunnerBodyCallback<C> runnerBodyCallback) throws Exception {

		validateLocalDebug(annotation.debug(), info);
		JPostmanReport report = report(testInstance);
		Collection collection = resolver.collection(info.namespace);
		List<String> requestNames;
		try {
			requestNames = requestDiscovery.runnerRequestNames(collection, info.folder);
		} catch (IllegalArgumentException e) {
			throw runnerFolderNotFoundError(info, e);
		}
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

		Set<Method> requestDependencyMethods = runnerRequestDependencyMethods(testInstance, annotation, info);
		List<String> executableRequestNames = runnerExecutableRequestNames(testInstance, info, requestNames, includes,
				excludes, skipped, requestDependencyMethods);

		/*
		 * A non-framework @JPostman.Response with an explicit verification value is an
		 * executable setup response for this runner. ReStage uses this for cached
		 * selector methods that return a value: JUnit requires @Test methods to be
		 * void, and TestNG ignores return-value tests by default, so the Response is
		 * deliberately not a framework @Test. Execute it once here, including its
		 * Request/Response dependencies and cache body, before the remaining folder
		 * requests. Responses that keep verify=-1 or verify=0 retain the historical
		 * runner behavior and are only filtered from the folder execution.
		 */
		try {
			executeVerifiedExternalRunnerResponses(testInstance, resolver, info, stack, requestNames, includes,
					excludes, failures);
		} catch (Exception | Error e) {
			if (report != null && report.skipRemaining()) {
				skipRemainingRunnerRequests(report, info, executableRequestNames, 0);
			}
			throw e;
		}

		if (notifyAfterRequest) {
			JPostmanRuntimeRunner.begin(executableRequestNames, annotation.lifecycle());
		}

		try {
			for (int requestIndex = 0; requestIndex < executableRequestNames.size(); requestIndex++) {
				String requestName = executableRequestNames.get(requestIndex);
				JPostmanInfo requestInfo = info.runnerRequest(requestName).annotation("@JPostmanRunner");
				resolver.info(requestInfo);
				add(report, requestInfo);
				C ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo,
						requestName);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
				applyData(testInstance, resolver, requestInfo, annotation.data(), stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo,
						requestName);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);

				executed++;
				Throwable hardRunnerBodyFailure = null;
				try {
					if (perRequestDependencies != null && perRequestDependencies.length > 0) {
						runDependencies(testInstance, resolver, perRequestDependencies,
								requestInfo.withTags(annotation.tags()), stack);
						ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo,
								requestName);
						resolver.update(info.namespace, ctx);
						framework.setCurrent(ctx);
					}

					notifyBeforeRunnerRequest(testInstance, notifyBeforeRequest, requestIndex, requestName,
							latestContext(resolver, requestInfo.namespace, ctx), annotation, requestInfo,
							runnerBodyCallback);
					ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, requestInfo,
							requestName);
					resolver.update(info.namespace, ctx);
					framework.setCurrent(ctx);

					Throwable responseFailure = null;
					try {
						executeRunnerResponse(testInstance, resolver, ctx, annotation, requestInfo, stack);
					} catch (Exception | Error e) {
						responseFailure = e;
					}

					/*
					 * lifecycle=true is a response callback contract. A completed HTTP request must
					 * invoke the runner body even when status/assert verification failed.
					 * Previously executeRunnerResponse(...) threw before this callback, which is
					 * why a 401/403 could make a simple runtime.info() body disappear entirely.
					 */
					if (responseFailure == null || requestInfo.statusCode() != null) {
						try {
							notifyAfterRunnerRequest(testInstance, notifyAfterRequest, requestIndex, requestName,
									latestContext(resolver, requestInfo.namespace, ctx), requestInfo,
									runnerBodyCallback);
						} catch (Exception | Error callbackFailure) {
							if (!JPostmanRuntimeRunner.isSoftFailure(callbackFailure)) {
								/*
								 * A hard Java runner-body assertion is not an HTTP verification failure. It
								 * must retain normal hard/fail-fast semantics even though the request already
								 * has a concrete response status.
								 */
								hardRunnerBodyFailure = callbackFailure;
								if (responseFailure != null && responseFailure != callbackFailure) {
									callbackFailure.addSuppressed(responseFailure);
								}
								throw callbackFailure;
							}
							if (responseFailure != null) {
								responseFailure.addSuppressed(callbackFailure);
							} else {
								throw callbackFailure;
							}
						}
					}
					if (responseFailure != null) {
						rethrowProceedFailure(responseFailure);
					}
				} catch (Exception | Error e) {
					C latest = latestContext(resolver, requestInfo.namespace, ctx);
					failed(report, requestInfo, latest, e);

					if (hardRunnerBodyFailure != null) {
						/* Hard runner-body assertions retain normal fail-fast semantics. */
						rethrowProceedFailure(e);
					}

					/*
					 * Request-level failures with a concrete HTTP response are collected so the
					 * runner can continue through the rest of the folder. fail="terminate" exits
					 * from JPostmanReport.failed(). fail="skipAll" is different: the request that
					 * actually failed remains FAILED, while every later request in this runner is
					 * recorded as SKIPPED without being executed. Configuration/pre-execution
					 * failures still fail fast because there is no completed request to report.
					 */
					if (requestInfo.statusCode() == null && !JPostmanRuntimeRunner.isSoftFailure(e)) {
						throw e;
					}
					failures.add(locationError(testInstance, requestInfo, e));

					if (report != null && report.skipRemaining()) {
						skipRemainingRunnerRequests(report, info, executableRequestNames, requestIndex + 1);
						break;
					}
				}
			}
		} finally {
			if (notifyAfterRequest) {
				JPostmanRuntimeRunner.clear();
			}
		}

		if (executed == 0) {
			if (!failures.isEmpty()) {
				AssertionError runnerFailure = combinedRunnerError(testInstance, failures);
				invokeRunnerCompletionBody(testInstance, annotation, notifyAfterRequest, resolver, info,
						runnerBodyCallback, runnerFailure);
				throw runnerFailure;
			}
			if (allRunnerRequestsHandledByExplicitAnnotations(requestNames, skipped)) {
				// The runner is still a successful top-level test even when every request in
				// its scope is owned by an explicit @JPostmanResponse method. No concrete
				// runner-request records exist in this case, so record the parent runner once.
				passed(report, info);
				invokeRunnerCompletionBody(testInstance, annotation, notifyAfterRequest, resolver, info,
						runnerBodyCallback, null);
				return;
			}
			throw runnerNothingExecutedError(info, requestNames, skipped);
		}

		if (!failures.isEmpty()) {
			AssertionError runnerFailure = combinedRunnerError(testInstance, failures);
			invokeRunnerCompletionBody(testInstance, annotation, notifyAfterRequest, resolver, info, runnerBodyCallback,
					runnerFailure);
			throw runnerFailure;
		}

		invokeRunnerCompletionBody(testInstance, annotation, notifyAfterRequest, resolver, info, runnerBodyCallback,
				null);
	}

	/**
	 * lifecycle=false is a whole-runner callback. Invoke the framework test body
	 * exactly once after the runner reaches its normal completion point, including
	 * the aggregate-failure completion path. Configuration/preparation failures
	 * that abort executeRunner before request processing still bypass this
	 * callback.
	 *
	 * <p>
	 * When the runner already has a failure, that failure remains primary. A body
	 * failure is attached as suppressed so diagnostics in the final callback can
	 * never replace the HTTP/verification failure that caused the runner to fail.
	 * Reusable runner dependency launchers that already own per-request callbacks
	 * keep their existing callback path and do not receive an extra completion
	 * callback.
	 * </p>
	 */
	private void invokeRunnerCompletionBody(Object testInstance, JPostmanRunner annotation,
			boolean requestCallbacksEnabled, PreparedContexts<C> resolver, JPostmanInfo runnerInfo,
			RunnerBodyCallback<C> runnerBodyCallback, Throwable runnerFailure) throws Exception {
		if (annotation == null || annotation.lifecycle() || requestCallbacksEnabled || runnerBodyCallback == null) {
			return;
		}

		JPostmanInfo completionInfo = resolver.info();
		if (completionInfo == null) {
			completionInfo = runnerInfo;
		}
		C completionContext = latestContext(resolver, completionInfo.namespace,
				resolver.context(completionInfo.namespace));

		try {
			runnerBodyCallback.run(completionContext, completionInfo);
		} catch (Exception | Error bodyFailure) {
			if (runnerFailure != null) {
				runnerFailure.addSuppressed(bodyFailure);
				return;
			}
			if (bodyFailure instanceof Exception) {
				throw (Exception) bodyFailure;
			}
			throw (Error) bodyFailure;
		}
	}

	private void skipRemainingRunnerRequests(JPostmanReport report, JPostmanInfo runnerInfo,
			List<String> executableRequestNames, int startIndex) {
		if (report == null || runnerInfo == null || executableRequestNames == null) {
			return;
		}
		for (int index = Math.max(0, startIndex); index < executableRequestNames.size(); index++) {
			String requestName = executableRequestNames.get(index);
			JPostmanInfo skippedInfo = runnerInfo.runnerRequest(requestName).annotation("@JPostmanRunner");
			skipped(report, skippedInfo);
		}
	}

	private void executeVerifiedExternalRunnerResponses(Object testInstance, PreparedContexts<C> resolver,
			JPostmanInfo runnerInfo, List<String> stack, List<String> requestNames, Set<String> includes,
			Set<String> excludes, List<Throwable> failures) throws Exception {
		Set<Method> handled = new LinkedHashSet<>();
		List<Method> responseMethods = requestDiscovery.responseMethods(testInstance.getClass());

		/*
		 * Iterate in collection order, not reflection order. A generated cache Response
		 * may leave folder/request blank and inherit them from its @JPostman.Request
		 * dependency, so resolve the effective location before deciding whether it
		 * belongs to this runner.
		 */
		for (String requestName : requestNames) {
			if (!includes.isEmpty() && !includes.contains(requestName)) {
				continue;
			}
			if (excludes.contains(requestName)) {
				continue;
			}

			for (Method method : responseMethods) {
				if (handled.contains(method)) {
					continue;
				}
				JPostmanResponse response = JPostmanAnnotations.response(method);
				if (response == null || response.verify() == -1 || response.verify() == 0
						|| hasFrameworkTestAnnotation(method)) {
					continue;
				}

				JPostmanInfo effective = info(method.getName(), null, response, null, null);
				inheritResponseLocationFromDependencies(testInstance, response, effective);
				applyDefaultExecutorNamespace(testInstance, effective);
				if (!sameRunnerLocation(effective, runnerInfo, requestName)) {
					continue;
				}

				handled.add(method);
				try {
					method.setAccessible(true);
					runResponseDependency(testInstance, resolver, method, response, runnerInfo, stack);
				} catch (Exception | Error e) {
					JPostmanReport report = report(testInstance);
					if (report != null && report.skipRemaining()) {
						throw e;
					}

					JPostmanInfo responseInfo = report == null ? null : report.execution(method.getName());
					boolean completedRequest = responseInfo != null && responseInfo.statusCode() != null;
					if (!completedRequest) {
						try {
							C active = resolver.activeContext();
							completedRequest = active != null && framework.responseStatusCode(active) != null;
						} catch (RuntimeException | LinkageError ignored) {
							// The original execution failure remains authoritative.
						}
					}

					if (!completedRequest && !JPostmanRuntimeRunner.isSoftFailure(e)) {
						throw e;
					}
					failures.add(locationError(testInstance, responseInfo == null ? effective : responseInfo, e));
				} finally {
					resolver.info(runnerInfo);
					C runnerContext = resolver.resolve(runnerInfo.namespace).context;
					framework.setCurrent(runnerContext);
				}
			}
		}
	}

	private boolean sameRunnerLocation(JPostmanInfo responseInfo, JPostmanInfo runnerInfo, String requestName) {
		if (responseInfo == null || runnerInfo == null) {
			return false;
		}
		return value(responseInfo.namespace).trim().equals(value(runnerInfo.namespace).trim())
				&& value(responseInfo.folder).trim().equals(value(runnerInfo.folder).trim())
				&& value(responseInfo.request).trim().equals(value(requestName).trim());
	}

	private boolean hasFrameworkTestAnnotation(Method method) {
		if (method == null) {
			return false;
		}
		for (java.lang.annotation.Annotation annotation : method.getDeclaredAnnotations()) {
			String name = annotation.annotationType().getName();
			if ("org.junit.jupiter.api.Test".equals(name) || "org.testng.annotations.Test".equals(name)) {
				return true;
			}
		}
		return false;
	}

	private List<String> runnerExecutableRequestNames(Object testInstance, JPostmanInfo info, List<String> requestNames,
			Set<String> includes, Set<String> excludes, List<String> skipped, Set<Method> requestDependencyMethods) {

		List<String> executable = new ArrayList<>();
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

			if (requestDiscovery.hasExplicitRequest(testInstance.getClass(), info.namespace, info.folder, requestName,
					requestDependencyMethods)) {
				skipped.add(requestName + " (handled by explicit @JPostmanRequest)");
				continue;
			}

			if (requestDiscovery.hasExplicitCall(testInstance.getClass(), info.namespace, info.folder, requestName)) {
				skipped.add(requestName + " (handled by explicit @JPostmanCall)");
				continue;
			}

			executable.add(requestName);
		}
		return executable;
	}

	/**
	 * Returns direct request dependencies used by the current runner. A request
	 * dependency is a setup/scope provider for that runner, so it must not cause
	 * the same collection request to be filtered as an independently handled
	 * request.
	 */
	private Set<Method> runnerRequestDependencyMethods(Object testInstance, JPostmanRunner annotation,
			JPostmanInfo info) {
		Set<Method> result = new LinkedHashSet<>();
		if (testInstance == null || annotation == null) {
			return result;
		}

		for (String dependencyName : dependencies(annotation.dependsOn())) {
			if (dependencyName == null || dependencyName.isBlank()) {
				continue;
			}
			Method method = findDependencyMethod(testInstance.getClass(), dependencyName.trim(), info);
			if (JPostmanAnnotations.request(method) != null) {
				result.add(method);
			}
		}
		return result;
	}

	private void notifyBeforeRunnerRequest(Object testInstance, boolean enabled, int requestIndex, String requestName,
			C ctx, JPostmanRunner annotation, JPostmanInfo info, RunnerBodyCallback<C> runnerBodyCallback) {
		if (!enabled || runnerBodyCallback == null) {
			return;
		}

		JPostmanRuntimeRunner.beforeRequest(requestIndex, requestName);
		JPostmanRuntimeRunner.beginUserBodyCallback();
		try {
			runnerBodyCallback.run(ctx, info);
		} catch (Exception | Error e) {
			if (!JPostmanRuntimeRunner.isRunnerBodyComplete(e)) {
				throw e instanceof AssertionError ? (AssertionError) e : locationError(testInstance, info, e);
			}
		} finally {
			JPostmanRuntimeRunner.endUserBodyCallback();
			JPostmanRuntimeRunner.finishBeforeRequest();
		}
	}

	private void notifyAfterRunnerRequest(Object testInstance, boolean enabled, int requestIndex, String requestName,
			C ctx, JPostmanInfo info, RunnerBodyCallback<C> runnerBodyCallback) {
		if (!enabled || runnerBodyCallback == null) {
			return;
		}

		JPostmanRuntimeRunner.afterRequest(requestIndex, requestName);
		Throwable callbackFailure = null;
		JPostmanRuntimeRunner.beginUserBodyCallback();
		try {
			runnerBodyCallback.run(ctx, info);
		} catch (Exception | Error e) {
			if (!JPostmanRuntimeRunner.isRunnerBodyComplete(e)) {
				callbackFailure = e;
			}
		} finally {
			JPostmanRuntimeRunner.endUserBodyCallback();
		}

		AssertionError injectedSoftFailure = null;
		try {
			JPostmanAnnotationEngine.verifySoftAssertContexts(testInstance);
		} catch (AssertionError error) {
			injectedSoftFailure = JPostmanRuntimeRunner.isSoftFailure(error) ? error
					: JPostmanRuntimeRunner.combineSoftFailures(error);
		}

		if (callbackFailure != null && !JPostmanRuntimeRunner.isSoftFailure(callbackFailure)) {
			AssertionError hardFailure = callbackFailure instanceof AssertionError ? (AssertionError) callbackFailure
					: locationError(testInstance, info, callbackFailure);
			AssertionError collectedSoftFailure = JPostmanRuntimeRunner.takeSoftFailure();
			if (collectedSoftFailure != null) {
				hardFailure.addSuppressed(collectedSoftFailure);
			}
			if (injectedSoftFailure != null) {
				hardFailure.addSuppressed(injectedSoftFailure);
			}
			throw hardFailure;
		}

		AssertionError callbackSoftFailure = JPostmanRuntimeRunner.softFailureCause(callbackFailure);
		AssertionError localSoftFailure = callbackSoftFailure == null ? JPostmanRuntimeRunner.takeSoftFailure() : null;
		AssertionError requestSoftFailure = JPostmanRuntimeRunner.combineSoftFailures(callbackSoftFailure,
				localSoftFailure, injectedSoftFailure);
		if (requestSoftFailure != null) {
			throw requestSoftFailure;
		}

		/*
		 * Do not call framework.verifyAssertions(ctx) here. TestNgContext.asserts()
		 * creates a new hard assertion facade, and verify() on that fresh facade
		 * performs the framework default status-code check (200). That incorrectly
		 * re-verifies runner responses after the annotation-specific verify value has
		 * already been applied, so Runner(verify = 0) fails on a valid 201 response and
		 * Runner(verify = 201) can also be rechecked against 200.
		 *
		 * Runner-body failures are already collected above through the immediate,
		 * local-soft, and injected AssertContext paths.
		 */
	}

	private boolean allRunnerRequestsHandledByExplicitAnnotations(List<String> requestNames, List<String> skipped) {
		if (requestNames == null || requestNames.isEmpty() || skipped == null
				|| skipped.size() != requestNames.size()) {
			return false;
		}

		for (String reason : skipped) {
			if (reason == null || !isExplicitAnnotationSkip(reason)) {
				return false;
			}
		}
		return true;
	}

	private boolean isExplicitAnnotationSkip(String reason) {
		return reason.contains("(handled by explicit @JPostmanResponse)")
				|| reason.contains("(handled by explicit @JPostmanRequest)")
				|| reason.contains("(handled by explicit @JPostmanCall)");
	}

	private AssertionError runnerFolderNotFoundError(JPostmanInfo info, IllegalArgumentException cause) {
		String folder = value(info == null ? "" : info.folder).trim();
		String detail = value(cause == null ? "" : cause.getMessage()).trim();
		if (detail.isBlank()) {
			detail = "Folder not found: " + (folder.isBlank() ? "<root>" : folder);
		}
		return JPostmanErrors.usage(info, "JPostman runner folder was not found.", detail,
				"Check @JPostmanRunner.folder or the @JPostman.Request dependency used as the runner scope.");
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
				"Fix the @JPostmanRunner include/exclude values or remove duplicate explicit @JPostmanResponse, @JPostmanRequest, or @JPostmanCall methods.");
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
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			for (String dependencyName : executor.dependsOn()) {
				runDependency(testInstance, resolver, dependencyName, executor.info, stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info, info.request);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			Object result = executor.result(testInstance, ctx);
			verifyExecutorResult(result, executor.name, info);

			ctx = executeWithExecutorInterceptors(testInstance, resolver, ctx, info, (ApiExecutor) result,
					completed -> applyFilter(completed, annotation.filter()));
			resolver.info(info);
			add(report, info);
			applyAssertions(testInstance, resolver, ctx, info, annotation.asserts(), annotation.debug());
			verifyResponse(testInstance, ctx, info, annotation.verify(), annotation.debug());
			debugOutput(testInstance, ctx, info, annotation.debug());
			passed(report, info);
		} catch (Exception | Error e) {
			C latest = latestContext(resolver, info.namespace, ctx);
			debugOutputAfterFailure(testInstance, latest, info, annotation.debug());
			failed(report, info, latest, e);
			throw executionFailure(testInstance, latest, info, e, annotation.debug());
		}
	}

	private void executeResponse(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanResponse annotation,
			JPostmanInfo info, List<String> stack) throws Exception {

		JPostmanTestProxy.clearResponse(testInstance, info.id);
		validateLocalDebug(annotation.debug(), info);
		rejectVerifyAndAsserts(annotation, info);

		ExecutorCall<C> executor = executorCall(testInstance, ctx, info);
		resolver.info(executor.info);
		int executorIndex = info.appendMethod(executorStep(executor.name, info));
		executor.info.methodIndex(executorIndex);
		add(report(testInstance), executor.info);

		Collection collection = resolver.collection(info.namespace);
		JPostmanReport report = report(testInstance);
		try {
			resolver.update(info.namespace, ctx);
			framework.setCurrent(ctx);

			for (String dependencyName : executor.dependsOn()) {
				runDependency(testInstance, resolver, dependencyName, executor.info, stack);
				ctx = prepareRequest(resolver.context(info.namespace), collection, annotation, info);
				resolver.update(info.namespace, ctx);
				framework.setCurrent(ctx);
			}

			Object result = executor.result(testInstance, ctx);
			verifyExecutorResult(result, executor.name, info);

			ctx = executeWithExecutorInterceptors(testInstance, resolver, ctx, info, (ApiExecutor) result,
					completed -> applyFilter(completed, annotation.filter()));
			resolver.info(info);
			add(report, info);
			applyAssertions(testInstance, resolver, ctx, info, annotation.asserts(), annotation.debug());
			verifyResponse(testInstance, ctx, info, annotation.verify(), annotation.debug());
			debugOutput(testInstance, ctx, info, annotation.debug());
			passed(report, info);
			JPostmanTestProxy.recordResponse(testInstance, info.id, ctx);
		} catch (Exception | Error e) {
			C latest = latestContext(resolver, info.namespace, ctx);
			debugOutputAfterFailure(testInstance, latest, info, annotation.debug());
			failed(report, info, latest, e);
			throw executionFailure(testInstance, latest, info, e, annotation.debug());
		}
	}

	private void inheritTopLevelLocationFromDependencies(Object testInstance, JPostmanRequest requestAnnotation,
			JPostmanResponse responseAnnotation, JPostmanCall callAnnotation, JPostmanRunner runnerAnnotation,
			JPostmanInfo info) {
		if (responseAnnotation != null) {
			inheritResponseLocationFromDependencies(testInstance, responseAnnotation, info);
		} else if (callAnnotation != null) {
			inheritCallLocationFromDependencies(testInstance, callAnnotation, info);
		} else if (runnerAnnotation != null) {
			inheritRunnerLocationFromDependencies(testInstance, runnerAnnotation, info);
		} else if (requestAnnotation != null) {
			inheritLocationFromDependencies(testInstance, dependencies(requestAnnotation.dependsOn()), info,
					new LinkedHashSet<>());
		}
	}

	/**
	 * Applies the namespace declared by the selected/default void executor before
	 * collection lookup. A method-level namespace always wins. This preserves the
	 * original executor design: a single interceptor, or the unique interceptor
	 * without an id, is the default and does not require an executor attribute.
	 */
	private void applyDefaultExecutorNamespace(Object testInstance, JPostmanInfo info) {
		if (testInstance == null || info == null || !isBlank(info.namespace)) {
			return;
		}

		Method selected = defaultExecutorInterceptor(testInstance.getClass(), info.executor, info);
		if (selected == null) {
			return;
		}

		JPostmanExecutor annotation = JPostmanAnnotations.executor(selected);
		String namespace = annotation == null ? "" : value(annotation.namespace()).trim();
		if (!namespace.isBlank()) {
			info.namespace = namespace;
		}
	}

	private Method defaultExecutorInterceptor(Class<?> type, String requestedName, JPostmanInfo info) {
		String requested = value(requestedName).trim();
		if (!requested.isBlank()) {
			return findExecutorInterceptorIgnoringNamespace(type, requested, info);
		}

		List<Method> interceptors = executorInterceptorMethods(type);
		if (interceptors.size() == 1) {
			return interceptors.get(0);
		}

		List<Method> defaults = new ArrayList<>();
		for (Method method : interceptors) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
			String id = annotation == null ? "" : annotationId(annotation.id());
			if (id.isBlank() || "default".equals(id)) {
				defaults.add(method);
			}
		}
		return defaults.size() == 1 ? defaults.get(0) : null;
	}

	private List<Method> executorInterceptorMethods(Class<?> type) {
		List<Method> methods = new ArrayList<>();
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (JPostmanAnnotations.executor(method) == null || !isExecutorInterceptor(method)) {
					continue;
				}
				method.setAccessible(true);
				methods.add(method);
			}
			current = current.getSuperclass();
		}
		return methods;
	}

	private Method findExecutorInterceptorIgnoringNamespace(Class<?> type, String requestedName, JPostmanInfo info) {
		String requested = value(requestedName).trim();
		if (requested.isBlank()) {
			return null;
		}

		boolean byId = isIdReference(requested);
		String lookup = byId ? idReferenceValue(requested) : requested;
		if (lookup.isBlank()) {
			throw JPostmanErrors.usage(info, "JPostman executor id is empty: " + requestedName,
					"Use executor = \"methodName\" for Java method names, or executor = \"#id\" for annotation ids.");
		}

		List<Method> matches = new ArrayList<>();
		for (Method method : executorInterceptorMethods(type)) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
			String id = annotation == null ? "" : annotationId(annotation.id());
			boolean matchesReference = byId ? lookup.equals(id) : lookup.equals(method.getName());
			if (matchesReference) {
				matches.add(method);
			}
		}

		if (matches.size() == 1) {
			return matches.get(0);
		}
		if (matches.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple @JPostmanExecutor interceptors match: " + requestedName);
		}
		return null;
	}

	private C executeWithExecutorInterceptors(Object testInstance, PreparedContexts<C> resolver, C ctx,
			JPostmanInfo info, ApiExecutor apiExecutor, ContextStep<C> afterResponse) throws Exception {
		RuntimeProceed<C> proceed = new RuntimeProceed<>(ctx);
		JPostmanRuntimeRequest<C> activeRequest = action -> {
			if (proceed.called) {
				throw new IllegalStateException(
						"runtime.call() may execute the current @JPostman.Executor request only once.");
			}
			proceed.called = true;
			C active = proceed.context;
			resolver.info(info);
			info.start();
			try {
				if (action != null) {
					action.accept(active, info);
				}
				C responseSource = active;
				active = framework.response(active, apiExecutor);
				framework.copyRuntimeValues(responseSource, active);
				captureResponseStatus(active, info);
				if (afterResponse != null) {
					active = afterResponse.apply(active);
				}
				completeProceedResponse(resolver, proceed, info, active);
			} catch (Exception e) {
				active = syntheticErrorResponse(resolver, proceed, info, active, e);
			} catch (Error e) {
				proceed.failure = e;
				completeProceedResponse(resolver, proceed, info, latestContext(resolver, info.namespace, active));
			} finally {
				info.end();
			}
			return proceed.context;
		};

		JPostmanRuntimeCall.activate(testInstance, framework.contextType(), activeRequest);
		try {
			runExecutorInterceptors(testInstance, resolver, ctx, info);
		} finally {
			JPostmanRuntimeCall.deactivate(testInstance, framework.contextType());
		}

		if (!proceed.called) {
			activeRequest.execute(null);
		}
		if (proceed.failure != null) {
			rethrowProceedFailure(proceed.failure);
		}
		return proceed.context;
	}

	private C syntheticErrorResponse(PreparedContexts<C> resolver, RuntimeProceed<C> proceed, JPostmanInfo info,
			C active, Exception failure) {
		JPostmanHttpErrorMapper.SyntheticResponse mapped = JPostmanHttpErrorMapper.map(failure);
		info.syntheticError(mapped.statusCode(), mapped.reason(), mapped.original(), mapped.cause());
		C latest = latestContext(resolver, info.namespace, active);
		try {
			C completed = framework.response(latest, () -> mapped.apiResponse());
			framework.copyRuntimeValues(latest, completed);
			captureResponseStatus(completed, info);
			completeProceedResponse(resolver, proceed, info, completed);
			return completed;
		} catch (Exception | Error syntheticFailure) {
			failure.addSuppressed(syntheticFailure);
			proceed.failure = failure;
			completeProceedResponse(resolver, proceed, info, latest);
			return latest;
		}
	}

	private void completeProceedResponse(PreparedContexts<C> resolver, RuntimeProceed<C> proceed, JPostmanInfo info,
			C active) {
		proceed.context = active;
		if (active != null) {
			resolver.update(info.namespace, active);
			framework.setCurrent(active);
		}
	}

	private void rethrowProceedFailure(Throwable failure) throws Exception {
		if (failure instanceof Exception) {
			throw (Exception) failure;
		}
		if (failure instanceof Error) {
			throw (Error) failure;
		}
		throw new RuntimeException(failure);
	}

	@FunctionalInterface
	private interface ContextStep<T> {
		T apply(T context) throws Exception;
	}

	private static final class RuntimeProceed<T> {
		private T context;
		private Throwable failure;
		private boolean called;

		private RuntimeProceed(T context) {
			this.context = context;
		}
	}

	private void runExecutorInterceptors(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanInfo info)
			throws Exception {
		for (Method method : executorInterceptors(testInstance.getClass(), info.namespace, info.executor, info)) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
			validateLocalDebug(annotation.debug(), info);
			JPostmanInfo interceptorInfo = info
					.childExact(method.getName(), "", info.executor, "", info.namespace, info.folder, info.request)
					.annotation("@JPostmanExecutor intercept").id(annotationId(annotation.id()))
					.debug(annotation.debug());
			interceptorInfo = interceptorInfo.context(resolver.resolve(info.namespace).contextAnnotation);
			interceptorInfo.method(method.getName());
			resolver.info(interceptorInfo);
			invokeExecutor(testInstance, method, ctx, interceptorInfo);
		}
		resolver.info(info);
	}

	private List<Method> executorInterceptors(Class<?> type, String namespace, String requestedName,
			JPostmanInfo info) {
		List<Method> exactNamespace = new ArrayList<>();
		List<Method> global = new ArrayList<>();
		String activeNamespace = value(namespace).trim();
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null || !isExecutorInterceptor(method)) {
					continue;
				}
				method.setAccessible(true);
				String configuredNamespace = value(annotation.namespace()).trim();
				if (configuredNamespace.isBlank()) {
					global.add(method);
				} else if (configuredNamespace.equals(activeNamespace)) {
					exactNamespace.add(method);
				}
			}
			current = current.getSuperclass();
		}

		String requested = value(requestedName).trim();
		if (!requested.isBlank()) {
			Method selected = findExecutorInterceptor(type, requested, activeNamespace, info);
			if (selected != null) {
				return List.of(selected);
			}
		}

		List<Method> candidates = exactNamespace.isEmpty() ? global : exactNamespace;
		if (candidates.isEmpty()) {
			return List.of();
		}
		if (candidates.size() == 1) {
			return List.of(candidates.get(0));
		}

		List<Method> defaults = new ArrayList<>();
		for (Method method : candidates) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
			String id = annotation == null ? "" : annotationId(annotation.id());
			if (id.isBlank() || "default".equals(id) || (id.isBlank() && "defaultExecutor".equals(method.getName()))) {
				defaults.add(method);
			}
		}
		if (defaults.size() == 1) {
			return List.of(defaults.get(0));
		}
		if (defaults.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple default @JPostmanExecutor interceptors found.",
					"Keep one interceptor without an id in the active namespace, and give all other interceptors unique ids.");
		}

		throw JPostmanErrors.usage(info, "Multiple named @JPostmanExecutor interceptors found.",
				"Select one with executor = \"#id\", or remove the id from the interceptor that should be the default.");
	}

	private boolean isRequestedExecutorInterceptor(Class<?> type, String requestedName, String namespace,
			JPostmanInfo info) {
		String requested = value(requestedName).trim();
		if (requested.isBlank()) {
			return false;
		}
		return findExecutorInterceptor(type, requested, value(namespace).trim(), info) != null;
	}

	private Method findExecutorInterceptor(Class<?> type, String requestedName, String namespace, JPostmanInfo info) {
		String requested = value(requestedName).trim();
		if (requested.isBlank()) {
			return null;
		}
		boolean byId = isIdReference(requested);
		String lookup = byId ? idReferenceValue(requested) : requested;
		if (lookup.isBlank()) {
			throw JPostmanErrors.usage(info, "JPostman executor id is empty: " + requestedName,
					"Use executor = \"methodName\" for Java method names, or executor = \"#id\" for annotation ids.");
		}

		List<Method> matches = new ArrayList<>();
		List<Method> namespaceMismatches = new ArrayList<>();
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
				if (annotation == null || !isExecutorInterceptor(method)) {
					continue;
				}
				String id = annotationId(annotation.id());
				boolean matchesReference = byId ? lookup.equals(id) : lookup.equals(method.getName());
				if (!matchesReference) {
					continue;
				}
				String configuredNamespace = value(annotation.namespace()).trim();
				if (!configuredNamespace.isBlank() && !configuredNamespace.equals(namespace)) {
					namespaceMismatches.add(method);
					continue;
				}
				method.setAccessible(true);
				matches.add(method);
			}
			current = current.getSuperclass();
		}

		if (matches.size() == 1) {
			return matches.get(0);
		}
		if (matches.size() > 1) {
			throw JPostmanErrors.usage(info, "Multiple @JPostmanExecutor interceptors match: " + requestedName);
		}
		if (!namespaceMismatches.isEmpty()) {
			JPostmanExecutor annotation = JPostmanAnnotations.executor(namespaceMismatches.get(0));
			throw JPostmanErrors.usage(info,
					"JPostman executor interceptor does not apply to this namespace: " + requestedName,
					"Interceptor namespace: " + value(annotation.namespace()).trim(),
					"Request namespace: " + (namespace.isBlank() ? "<default>" : namespace));
		}
		return null;
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

	private void rejectVerifyAndAsserts(JPostmanCall annotation, JPostmanInfo info) {
		if (annotation != null && shouldVerify(annotation.verify()) && hasAssertions(annotation.asserts())) {
			throw JPostmanErrors.usage(info, "Invalid JPostman verification configuration.",
					"@JPostmanCall cannot use verify and asserts together.",
					"Use verify for status-code verification, or use asserts for assertion sections.");
		}
	}

	private boolean applyAssertions(Object testInstance, PreparedContexts<C> resolver, C ctx, JPostmanInfo info,
			String[] assertions, String annotationDebug) throws Exception {
		PreparedContext<C> prepared = resolver.resolve(info.namespace);
		return assertionRunner.apply(ctx, prepared.assertionRules, assertions, info.request, false,
				failureDiagnostics(testInstance, annotationDebug, info));
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
			String annotationDebug) {

		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		AssertionError assertion = assertionFailure(info, cause,
				failureDiagnostics(testInstance, annotationDebug, info));
		if (assertion != null) {
			options.markFailure(assertion, annotationDebug);
			JPostmanDebugFile.failure(testInstance, info, annotationDebug, internalDiagnosticLog(ctx), assertion);
			return assertion;
		}

		String causeMessage = JPostmanErrors.stripSuffix(value(cause == null ? null : cause.getMessage())).trim();

		StringBuilder message = new StringBuilder();
		message.append("JPostman execution failed");

		if (!causeMessage.isBlank()) {
			message.append(JPostmanErrors.ENDL).append(causeMessage);
		}

		String diagnostic = value(failureDiagnosticLog(testInstance, ctx, info, annotationDebug)).trim();
		if (!diagnostic.isBlank()) {
			message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("JPostman diagnostic log:")
					.append(JPostmanErrors.ENDL).append(diagnostic);
		}

		AssertionError error = JPostmanErrors.execution(info, cause, message.toString());
		options.markFailure(error, annotationDebug);
		JPostmanDebugFile.failure(testInstance, info, annotationDebug, internalDiagnosticLog(ctx), error);

		return error;
	}

	private AssertionError assertionFailure(JPostmanInfo info, Throwable cause, boolean includeDiagnostics) {
		AssertionError assertion = findAssertionError(cause);
		if (assertion == null) {
			return null;
		}

		String message = value(assertion.getMessage());
		if (message.contains("(@JPostman")) {
			AssertionError error = new AssertionError(
					endLine(appendSuppressedMessages(message, assertion, includeDiagnostics)));
			copyFailureDetails(assertion, error, includeDiagnostics);
			return error;
		}

		String detail = endLine(
				appendSuppressedMessages(JPostmanErrors.stripSuffix(message).trim(), assertion, includeDiagnostics));
		AssertionError error = JPostmanErrors.usage(info, detail);
		copyFailureDetails(assertion, error, includeDiagnostics);
		return error;
	}

	private String appendSuppressedMessages(String message, Throwable error, boolean includeDiagnostics) {
		StringBuilder result = new StringBuilder(value(message).stripTrailing());
		if (includeDiagnostics) {
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

	private void copyFailureDetails(Throwable source, AssertionError target, boolean includeSuppressed) {
		if (source == null || target == null) {
			return;
		}
		try {
			target.initCause(source);
		} catch (IllegalStateException ignored) {
			// Keep the formatted assertion message even if the cause was already assigned.
		}
		if (includeSuppressed) {
			copySuppressed(source, target);
		}
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

	private void captureResponseStatus(C ctx, JPostmanInfo info) {
		if (info == null) {
			return;
		}
		Integer statusCode = framework.responseStatusCode(ctx);
		if (statusCode != null) {
			info.statusCode(statusCode);
		}
	}

	private void verifyResponse(Object testInstance, C ctx, JPostmanInfo info, int annotationVerify,
			String annotationDebug) {
		int statusCode = statusCode(testInstance, effectiveVerify(annotationVerify), info);
		if (statusCode == 1) {
			/*
			 * verify=1 has the same request/status behavior as verify=0. The request
			 * completes, diagnostics and user assertions remain available, and the
			 * framework converts the final successful test result to skipped only after the
			 * user test body has completed.
			 */
			JPostmanVerificationOutcome.requestSkip(info);
			return;
		}
		if (statusCode >= 100) {
			framework.verify(ctx, statusCode, false, failureDiagnostics(testInstance, annotationDebug, info), info,
					failureDiagnosticLog(testInstance, ctx, info, annotationDebug));
		}
	}

	/**
	 * Resolves the verification value for a concrete HTTP request.
	 *
	 * <p>
	 * A response or call that is executed while a runner is active may inherit that
	 * runner's concrete verification value when its own value is {@code -1}.
	 * Standalone {@code @JPostman.Response} and {@code @JPostman.Call} test methods
	 * are separate executions and therefore keep {@code -1}, which resolves through
	 * {@code @JPostman.Context.verifyStatusCode}.
	 * </p>
	 */
	private int effectiveVerify(int annotationVerify) {
		if (annotationVerify != -1) {
			return annotationVerify;
		}

		Integer activeRunnerVerify = activeRunnerVerify();
		return activeRunnerVerify == null ? -1 : activeRunnerVerify.intValue();
	}

	/**
	 * Returns the nearest non-default active runner verification value. A nested
	 * runner that keeps {@code -1} continues to inherit an outer runner's concrete
	 * value. No annotation lookup is performed when a runner is not currently
	 * executing.
	 */
	private Integer activeRunnerVerify() {
		for (Integer verify : runnerVerifyScopes) {
			if (verify != null && verify.intValue() != -1) {
				return verify;
			}
		}
		return null;
	}

	private int statusCode(Object testInstance, int annotationVerify, JPostmanInfo info) {
		if (annotationVerify < -1) {
			throw JPostmanErrors.usage(info,
					"verify must be -1 to use the context default, 0 to pass without status verification, "
							+ "1 to mark the completed test skipped, or between 100 and 599.");
		}
		int statusCode = JPostmanRuntimeOptions.from(testInstance).statusCode(annotationVerify);
		if ((statusCode > 1 && statusCode < 100) || statusCode > 599 || statusCode < -1) {
			throw JPostmanErrors.usage(info,
					"verify status code must be 0 to pass without status verification, "
							+ "1 to mark the completed test skipped, -1 to use the context default, "
							+ "or between 100 and 599.");
		}
		return statusCode;
	}

	private boolean shouldVerify(int verify) {
		return verify >= 100;
	}

	private void beginRunnerVerifyScope(int verify) {
		runnerVerifyScopes.push(Integer.valueOf(verify));
	}

	private void endRunnerVerifyScope() {
		if (!runnerVerifyScopes.isEmpty()) {
			runnerVerifyScopes.pop();
		}
	}

	private String cacheKey(Method method, String rawCache, String rawId) {
		String cache = value(rawCache).trim();
		if (NO_CACHE.equals(cache)) {
			return "";
		}
		if (!cache.isBlank()) {
			return cache;
		}
		String id = annotationId(rawId);
		if (!id.isBlank()) {
			return id;
		}
		return method == null ? "" : "__" + method.getName() + "__";
	}

	private void registerCacheAlias(PreparedContexts<C> resolver, String rawId, String cacheKey) {
		String aliasKey = JPostmanTestProxy.cacheAliasKey(rawId);
		if (resolver == null || aliasKey.isBlank() || cacheKey == null || cacheKey.isBlank()) {
			return;
		}
		for (C context : resolver.contexts()) {
			framework.cache(context, aliasKey, cacheKey);
		}
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

	private AssertionError locationError(Object testInstance, JPostmanInfo info, Throwable cause) {
		StringBuilder message = new StringBuilder();

		Throwable root = cause == null ? null : JPostmanStackTraceCleaner.rootCause(cause);
		String causeMessage = JPostmanRuntimeRunner.failureMessage(root == null ? cause : root).trim();
		if (causeMessage.isBlank()) {
			causeMessage = root == null ? "" : value(root.getMessage()).trim();
		}
		if (!causeMessage.isBlank()) {
			message.append(JPostmanErrors.ENDL);
			/*
			 * A test-body/assert facade failure can occur while a runner request is active
			 * even when that request's HTTP verification succeeds. Such assertion messages
			 * do not already carry an annotation suffix, so attach the active request
			 * identity before the assertion text. This keeps the failure associated with
			 * the request callback that produced it instead of leaving an unexplained
			 * location-only entry in the aggregated runner output. Execution/status
			 * failures already contain their own suffix and must not be duplicated.
			 */
			if (info != null && !containsJPostmanSuffix(causeMessage)) {
				message.append(JPostmanErrors.suffix(info)).append(JPostmanErrors.ENDL);
			}
			message.append(causeMessage);
		}

		AssertionError error = new AssertionError(message.toString());
		if (cause != null) {
			error.initCause(cause);
		}
		if (root != null && root.getStackTrace() != null && root.getStackTrace().length > 0) {
			error.setStackTrace(root.getStackTrace());
		}

		return error;
	}

	private boolean containsJPostmanSuffix(String message) {
		if (message == null || message.isBlank()) {
			return false;
		}
		return message.contains("(@JPostman");
	}

	private String failureLocation(Object testInstance, Throwable cause) {
		if (cause == null || cause.getStackTrace() == null) {
			return "";
		}
		StackTraceElement preferred = preferredTestFrame(testInstance, cause);
		if (preferred != null) {
			return "\tat " + preferred;
		}
		for (StackTraceElement element : cause.getStackTrace()) {
			if (element == null || element.getLineNumber() < 0) {
				continue;
			}
			String className = value(element.getClassName());
			if (className.startsWith("io.jpostman.") || className.startsWith("java.") || className.startsWith("jdk.")
					|| className.startsWith("org.junit.") || className.startsWith("org.testng.")
					|| className.startsWith("org.eclipse.")) {
				continue;
			}
			return "\tat " + element;
		}
		return "";
	}

	private StackTraceElement preferredTestFrame(Object testInstance, Throwable cause) {
		if (testInstance == null || cause == null || cause.getStackTrace() == null) {
			return null;
		}
		String testClassName = testInstance.getClass().getName();
		for (StackTraceElement element : cause.getStackTrace()) {
			if (element == null || element.getLineNumber() < 0) {
				continue;
			}
			String className = value(element.getClassName());
			if (className.equals(testClassName) || className.startsWith(testClassName + "$")) {
				return element;
			}
		}
		return null;
	}

	private AssertionError combinedRunnerError(Object testInstance, List<Throwable> failures) {
		StringBuilder message = new StringBuilder();

		message.append("JPostman runner failed for ").append(failures.size())
				.append(failures.size() == 1 ? " request." : " requests.");

		for (Throwable failure : failures) {
			String failureMessage = failure == null ? "" : value(failure.getMessage()).trim();

			if (!failureMessage.isBlank()) {
				message.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL);
				String location = failureLocation(testInstance, failure);
				if (!location.isBlank()) {
					message.append(location).append(JPostmanErrors.ENDL);
				}
				message.append(failureMessage);
			}
		}

		AssertionError error = new RunnerAggregateFailure(message.toString() + JPostmanErrors.ENDL);
		for (Throwable failure : failures) {
			if (failure != null && failure.getStackTrace() != null && failure.getStackTrace().length > 0) {
				error.setStackTrace(failure.getStackTrace());
				break;
			}
		}
		return error;
	}

	private boolean failureDiagnostics(Object testInstance, String annotationDebug, JPostmanInfo info) {
		return JPostmanRuntimeOptions.from(testInstance).failureDiagnostics(annotationDebug, info);
	}

	private String failureDiagnosticLog(Object testInstance, C ctx, JPostmanInfo info, String annotationDebug) {
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		if (!options.failureDiagnostics(annotationDebug, info)) {
			return "";
		}

		StringBuilder result = new StringBuilder();
		if (options.failureInfoDiagnostic(annotationDebug, info) && info != null) {
			result.append(info.log(false));
		}

		String diagnostic = framework.diagnosticLog(ctx, options.failureRequest(annotationDebug, info),
				options.failureResponse(annotationDebug, info)).trim();
		if (!diagnostic.isBlank()) {
			if (result.length() > 0) {
				result.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL);
			}
			result.append(diagnostic);
		}
		return result.toString();
	}

	private void debug(Object testInstance, JPostmanInfo info, String annotationDebug) {
		JPostmanDebugFile.info(testInstance, info, annotationDebug);
		JPostmanRuntimeOptions.from(testInstance).debug(testInstance, info, annotationDebug);
	}

	private String annotationDebug(JPostmanRequest requestAnnotation, JPostmanResponse responseAnnotation,
			JPostmanCall callAnnotation, JPostmanRunner runnerAnnotation) {
		if (requestAnnotation != null) {
			return requestAnnotation.debug();
		}
		if (responseAnnotation != null) {
			return responseAnnotation.debug();
		}
		if (callAnnotation != null) {
			return callAnnotation.debug();
		}
		if (runnerAnnotation != null) {
			return runnerAnnotation.debug();
		}
		return "debug";
	}

	private String annotationDebug(Method method) {
		if (method == null) {
			return "debug";
		}
		return annotationDebug(JPostmanAnnotations.request(method), JPostmanAnnotations.response(method),
				JPostmanAnnotations.call(method), JPostmanAnnotations.runner(method));
	}

	private void debugOutput(Object testInstance, C ctx, JPostmanInfo info, String annotationDebug) {
		if (info != null && !info.markDebugOutputEmitted()) {
			return;
		}
		try {
			captureReportDiagnostics(report(testInstance), ctx, info);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
			// Report details must never affect request execution.
		}
		JPostmanRuntimeOptions options = JPostmanRuntimeOptions.from(testInstance);
		JPostmanDebugFile.execution(testInstance, info, annotationDebug, internalDiagnosticLog(ctx));

		java.util.EnumSet<JPostmanRuntimeOptions.DebugOutput> outputs = options.automaticOutput(annotationDebug, info);
		if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.NONE)) {
			return;
		}
		printDebugOutputHeader(testInstance, info);
		if (!outputs.contains(JPostmanRuntimeOptions.DebugOutput.INFO)
				&& !outputs.contains(JPostmanRuntimeOptions.DebugOutput.ALL)
				&& (outputs.contains(JPostmanRuntimeOptions.DebugOutput.REQUEST)
						|| outputs.contains(JPostmanRuntimeOptions.DebugOutput.RESPONSE))) {
			printDebugScope(info);
		}
		if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.ALL)) {
			info.print(true);
			framework.printContext(ctx);
			return;
		}
		if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.INFO)) {
			info.print(false);
		}
		if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.REQUEST) && hasDebugOutput(ctx, true, false)) {
			printDebugSectionHeader("********** SecureRequest: **********");
			framework.printRequest(ctx);
		}
		if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.RESPONSE) && hasDebugOutput(ctx, false, true)) {
			printDebugSectionHeader("**********SecureResponse: **********");
			framework.printResponse(ctx);
		}
	}

	/**
	 * Emits ordinary debug output for a failed execution. Request, response, info,
	 * and all use the same immediate output path for passing and failing
	 * executions. The local error mode remains deferred to the report's JPostman
	 * Errors section.
	 */
	private void debugOutputAfterFailure(Object testInstance, C ctx, JPostmanInfo info, String annotationDebug) {
		try {
			java.util.EnumSet<JPostmanRuntimeOptions.DebugOutput> outputs = JPostmanRuntimeOptions.from(testInstance)
					.automaticOutput(annotationDebug, info);
			if (outputs.contains(JPostmanRuntimeOptions.DebugOutput.NONE)) {
				return;
			}
			debugOutput(testInstance, ctx, info, annotationDebug);
		} catch (RuntimeException | LinkageError ignored) {
			// Debug output must never replace the original execution failure.
		}
	}

	private String internalDiagnosticLog(C ctx) {
		if (!JPostmanDebugFile.enabled() || ctx == null) {
			return "";
		}
		return JPostmanDebugFile.diagnosticLog(ctx);
	}

	private void captureDebugContext(PreparedContext<C> current, JPostmanInfo info) {
		if (!JPostmanDebugFile.enabled() || current == null || info == null) {
			return;
		}

		try {
			JPostmanDebugFile.ENVIRONMENTS = current.loaded == null ? null : current.loaded.getEnvironment();
			captureDebugCollection(current.collection, info.folder);
		} catch (RuntimeException | LinkageError ignored) {
			// Internal diagnostics must never affect annotation execution.
		}
	}

	private void captureDebugCollection(Collection collection, String folder) {
		if (!JPostmanDebugFile.enabled() || collection == null) {
			return;
		}

		try {
			String folderName = value(folder).trim();
			String key = folderName.isBlank() ? "<root>" : folderName;
			JPostmanDebugFile.COLLECTIONS.put(key, JPostmanFolderPath.requests(collection, folderName));
		} catch (RuntimeException | LinkageError ignored) {
			// Internal diagnostics must never affect annotation execution.
		}
	}

	private boolean hasDebugOutput(C ctx, boolean request, boolean response) {
		try {
			return !value(framework.diagnosticLog(ctx, request, response)).trim().isBlank();
		} catch (RuntimeException | LinkageError ignored) {
			return false;
		}
	}

	private void printDebugScope(JPostmanInfo info) {
		String scope = JPostmanRuntimeOptions.debugScope(info);
		if (!scope.isBlank()) {
			printDebugText(scope + JPostmanErrors.ENDL);
		}
	}

	private void printDebugSectionHeader(String header) {
		printDebugText(JPostmanErrors.ENDL + value(header) + JPostmanErrors.ENDL);
	}

	private void printDebugText(String text) {
		if (text == null || text.isEmpty()) {
			return;
		}
		if (!JPostmanOutputs.write(text)) {
			System.out.print(text);
		}
	}

	private void printDebugOutputHeader(Object testInstance, JPostmanInfo info) {
		JPostmanRuntimeOptions.printMethodHeader(testInstance, info);
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
				io.jpostman.annotations.JPostmanReportContext options = JPostmanAnnotations.reportContext(field);
				if (options != null)
					report.configure(options.details(), options.fail());
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

	void recordFinalFailure(Object testInstance, Method testMethod) throws IllegalAccessException {
		recordFinalFailure(testInstance, testMethod, null);
	}

	void recordFinalFailure(Object testInstance, Method testMethod, Throwable failure) throws IllegalAccessException {
		recordFinalStatus(testInstance, testMethod, false, failure);
	}

	void recordFinalSkip(Object testInstance, Method testMethod) throws IllegalAccessException {
		recordFinalStatus(testInstance, testMethod, true, null);
	}

	private void recordFinalStatus(Object testInstance, Method testMethod, boolean skip, Throwable failure)
			throws IllegalAccessException {
		if (testInstance == null || testMethod == null) {
			return;
		}
		JPostmanReport report = report(testInstance);
		if (report == null) {
			return;
		}
		JPostmanRunner runnerAnnotation = JPostmanAnnotations.runner(testMethod);
		if (runnerAnnotation != null && report.hasRunnerRequest(testMethod.getName())) {
			/*
			 * Per-request runner results are normally final. verify=1 is the one deferred
			 * successful outcome that must convert every completed runner request from pass
			 * to skip after the framework test body has finished.
			 */
			if (skip && JPostmanVerificationOutcome.requested()) {
				report.skipRunnerRequests(testMethod.getName());
			}
			return;
		}
		JPostmanInfo info = report.execution(testMethod.getName());
		if (info == null && skip) {
			/*
			 * fail="skipAll" aborts later methods before normal annotation execution
			 * creates a JPostmanInfo object. Build a lightweight top-level record from the
			 * skipped method so the JPostman report matches the framework result.
			 */
			info = skippedExecutionInfo(testInstance, testMethod);
		}
		if (info == null) {
			return;
		}
		if (skip) {
			report.skipped(info);
		} else {
			report.failed(info, failure);
		}
	}

	private JPostmanInfo skippedExecutionInfo(Object testInstance, Method testMethod) {
		JPostmanRequest requestAnnotation = JPostmanAnnotations.request(testMethod);
		JPostmanResponse responseAnnotation = JPostmanAnnotations.response(testMethod);
		JPostmanCall callAnnotation = JPostmanAnnotations.call(testMethod);
		JPostmanRunner runnerAnnotation = JPostmanAnnotations.runner(testMethod);
		if (requestAnnotation == null && responseAnnotation == null && callAnnotation == null
				&& runnerAnnotation == null) {
			return null;
		}

		JPostmanInfo info = info(testMethod.getName(), requestAnnotation, responseAnnotation, callAnnotation,
				runnerAnnotation);
		if (responseAnnotation != null) {
			inheritResponseLocationFromDependencies(testInstance, responseAnnotation, info);
		} else if (callAnnotation != null) {
			inheritCallLocationFromDependencies(testInstance, callAnnotation, info);
		} else if (runnerAnnotation != null) {
			inheritRunnerLocationFromDependencies(testInstance, runnerAnnotation, info);
		}
		applyDefaultExecutorNamespace(testInstance, info);
		info.method(testMethod.getName());
		return info;
	}

	private void inheritRunnerLocationFromDependencies(Object testInstance, JPostmanRunner annotation,
			JPostmanInfo info) {
		if (annotation == null || info == null || testInstance == null) {
			return;
		}
		boolean inheritNamespace = isBlank(info.namespace);
		boolean inheritFolder = isBlank(info.folder);
		if (!inheritNamespace && !inheritFolder) {
			return;
		}

		for (String dependencyName : dependencies(annotation.dependsOn())) {
			String name = value(dependencyName).trim();
			if (name.isBlank()) {
				continue;
			}
			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), name, info);
			JPostmanRequest request = JPostmanAnnotations.request(dependencyMethod);
			if (request == null) {
				continue;
			}
			if (inheritNamespace && !isBlank(request.namespace())) {
				info.namespace = request.namespace().trim();
			}
			String dependencyFolder = folder(request.folder());
			if (inheritFolder && !isBlank(dependencyFolder)) {
				info.folder = dependencyFolder;
			}
			break;
		}
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

	private void failed(JPostmanReport report, JPostmanInfo info, C ctx, Throwable failure) {
		if (report != null) {
			captureReportDiagnostics(report, ctx, info);
			report.failed(info, failure);
		}
	}

	private void captureReportDiagnostics(JPostmanReport report, C ctx, JPostmanInfo info) {
		if (report == null || ctx == null || info == null) {
			return;
		}
		try {
			boolean localErrorOutput = report.localErrorOutput(info);
			if (report.failureRequest() || localErrorOutput) {
				String request = value(framework.diagnosticLog(ctx, true, false)).trim();
				if (!request.isBlank()) {
					info.requestLog(request);
				}
			}
			if (report.failureResponse() || localErrorOutput) {
				String response = value(framework.diagnosticLog(ctx, false, true)).trim();
				if (!response.isBlank()) {
					info.responseLog(response);
				}
			}
		} catch (RuntimeException | LinkageError ignored) {
			// Diagnostic capture must never replace the original test result.
		}
	}

	private void skipped(JPostmanReport report, JPostmanInfo info) {
		if (report != null) {
			report.skipped(info);
		}
	}

	private void validateResponseSkipEnabled(JPostmanResponse annotation, JPostmanInfo info) {
		if (annotation != null && annotation.enabled() && skipResponse(annotation)) {
			throw JPostmanErrors.usage(info, "Invalid JPostman skip configuration.",
					"enabled and skip cannot be defined on the same @JPostmanResponse annotation.",
					"Use enabled = true to override @JPostmanContext(skipAll = true),",
					"or use skip = true to disable this response.");
		}
	}

	private void validateCallSkipEnabled(JPostmanCall annotation, JPostmanInfo info) {
		if (annotation != null && annotation.enabled() && skipCall(annotation)) {
			throw JPostmanErrors.usage(info, "Invalid JPostman skip configuration.",
					"enabled and skip cannot be defined on the same @JPostmanCall annotation.",
					"Use enabled = true to override @JPostmanContext(skipAll = true),",
					"or use skip = true to disable this call.");
		}
	}

	private void validateRunnerSkipEnabled(JPostmanRunner annotation, JPostmanInfo info) {
		if (annotation != null && annotation.enabled() && skipRunner(annotation)) {
			throw JPostmanErrors.usage(info, "Invalid JPostman skip configuration.",
					"enabled and skip cannot be defined on the same @JPostmanRunner annotation.",
					"Use enabled = true to override @JPostmanContext(skipAll = true),",
					"or use skip = true to disable this runner.");
		}
	}

	private boolean skipTopLevelResponse(JPostmanResponse annotation, JPostmanInfo info) {
		return skipResponse(annotation) || skipAll(info) && annotation != null && !annotation.enabled();
	}

	private boolean skipTopLevelCall(JPostmanCall annotation, JPostmanInfo info) {
		return skipCall(annotation) || skipAll(info) && annotation != null && !annotation.enabled();
	}

	private boolean skipTopLevelRunner(JPostmanRunner annotation, JPostmanInfo info) {
		return skipRunner(annotation) || skipAll(info) && annotation != null && !annotation.enabled();
	}

	private boolean skipAll(JPostmanInfo info) {
		return info != null && info.context != null && info.context.skipAll();
	}

	private boolean skipResponse(JPostmanResponse annotation) {
		return annotation != null && annotation.skip();
	}

	private boolean skipCall(JPostmanCall annotation) {
		return annotation != null && annotation.skip();
	}

	private boolean skipRunner(JPostmanRunner annotation) {
		return annotation != null && annotation.skip();
	}

	private String[] responseSkipLines(JPostmanResponse annotation) {
		return responseSkipLines(annotation, null);
	}

	private String[] responseSkipLines(JPostmanResponse annotation, JPostmanInfo info) {
		if (skipAll(info) && annotation != null && !annotation.enabled()) {
			return new String[] { "JPostman response skipped.", "@JPostmanContext(skipAll = true) is enabled." };
		}
		return new String[] { "JPostman response skipped." };
	}

	private String[] callSkipLines(JPostmanCall annotation, JPostmanInfo info) {
		if (skipAll(info) && annotation != null && !annotation.enabled()) {
			return new String[] { "JPostman call skipped.", "@JPostmanContext(skipAll = true) is enabled." };
		}
		return new String[] { "JPostman call skipped." };
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

	private String folderValue(String value) {
		String folder = value(value).trim();
		return folder.isBlank() ? "<root>" : folder;
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

	private String folder(String[] levels) {
		return JPostmanFolderPath.value(levels);
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private void validateAnnotationIds(Object testInstance) {
		Map<String, List<Method>> ids = new LinkedHashMap<>();
		Map<String, List<Method>> dependencyIds = new LinkedHashMap<>();
		Map<String, List<Method>> methodsByName = new LinkedHashMap<>();

		Class<?> current = testInstance.getClass();
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (validAnnotatedMethod(method)) {
					methodsByName.computeIfAbsent(method.getName(), key -> new ArrayList<>()).add(method);
				}
				collectAnnotationId(ids, method, requestId(method));
				collectAnnotationId(ids, method, responseId(method));
				collectAnnotationId(ids, method, runnerId(method));
				collectAnnotationId(ids, method, executorId(method));
				collectAnnotationId(dependencyIds, method, requestId(method));
				collectAnnotationId(dependencyIds, method, responseId(method));
				collectAnnotationId(dependencyIds, method, runnerId(method));
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

		Map<String, List<Method>> conflictingIds = new LinkedHashMap<>();
		for (Map.Entry<String, List<Method>> entry : dependencyIds.entrySet()) {
			List<Method> namedMethods = methodsByName.get(entry.getKey());
			List<Method> differentMethods = new ArrayList<>();
			if (namedMethods != null) {
				for (Method method : namedMethods) {
					if (!entry.getValue().contains(method)) {
						differentMethods.add(method);
					}
				}
			}
			if (!differentMethods.isEmpty()) {
				List<Method> conflict = new ArrayList<>(entry.getValue());
				conflict.addAll(differentMethods);
				conflictingIds.put(entry.getKey(), conflict);
			}
		}
		if (!conflictingIds.isEmpty()) {
			throw annotationIdMethodConflictError(conflictingIds);
		}
	}

	private AssertionError annotationIdMethodConflictError(Map<String, List<Method>> conflicts) {
		StringBuilder message = new StringBuilder();
		message.append("Invalid JPostman annotation usage.").append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL)
				.append("JPostman annotation ids must not conflict with Java method names.").append(JPostmanErrors.ENDL)
				.append("An unprefixed dependsOn value resolves a method first, then an annotation id when no method exists.")
				.append(JPostmanErrors.ENDL).append("Rename the annotation id or the conflicting Java method.")
				.append(JPostmanErrors.ENDL).append(JPostmanErrors.ENDL).append("Conflicts:")
				.append(JPostmanErrors.ENDL);
		List<Method> invalid = new ArrayList<>();
		for (Map.Entry<String, List<Method>> entry : conflicts.entrySet()) {
			message.append("- id=\"").append(entry.getKey()).append("\" conflicts with method ").append(entry.getKey())
					.append("()").append(JPostmanErrors.ENDL);
			for (Method method : entry.getValue()) {
				message.append("  - ").append(signature(method)).append(JPostmanErrors.ENDL);
				invalid.add(method);
			}
		}
		AssertionError error = new AssertionError(message.toString());
		error.setStackTrace(invalid.stream().distinct().map(this::testFrame).toArray(StackTraceElement[]::new));
		return error;
	}

	private boolean onlyExecutorIds(List<Method> methods) {
		for (Method method : methods) {
			if (JPostmanAnnotations.executor(method) == null || JPostmanAnnotations.request(method) != null
					|| JPostmanAnnotations.response(method) != null || JPostmanAnnotations.runner(method) != null) {
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
				.append("Ids must be unique across @JPostmanRequest, @JPostmanResponse, @JPostmanRunner, and @JPostmanExecutor.")
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
		if (JPostmanAnnotations.runner(method) != null) {
			return "@JPostmanRunner";
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

	private String runnerId(Method method) {
		JPostmanRunner annotation = JPostmanAnnotations.runner(method);
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
			message.append("Only one default @JPostmanExecutor provider is allowed.").append(JPostmanErrors.ENDL)
					.append("A single provider is selected automatically. With multiple providers, use one unnamed provider as the default or give every provider a unique id.")
					.append(JPostmanErrors.ENDL)
					.append("Keep one default provider, and select named providers with executor = \"#id\".")
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
					.append("ApiExecutor methods configure request execution; void methods run immediately before each response execution.")
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
		JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
		debug(testInstance, info, annotation == null ? "debug" : annotation.debug());
		Class<?>[] types = method.getParameterTypes();
		if (types.length == 0) {
			return invoke(testInstance, method);
		}
		if (types.length == 1 && isContextParameter(types[0])) {
			return invoke(testInstance, method, contextArg(types[0], ctx, null, testInstance));
		}
		if (types.length == 1 && isInfoParameter(types[0])) {
			return invoke(testInstance, method, info);
		}
		return invoke(testInstance, method, contextArg(types[0], ctx, null, testInstance), info);
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
		Class<?> type = testInstance.getClass();
		String requestedExecutor = value(info.executor).trim();
		String requestedProvider = isRequestedExecutorInterceptor(type, requestedExecutor, info.namespace, info) ? ""
				: requestedExecutor;
		if (isBlank(requestedProvider) && options.hasDefaultExecutor() && !hasExecutorProviderMethods(type)) {
			String name = options.executorClass().getSimpleName();
			JPostmanInfo executorInfo = info.child(name, info.executor, info.namespace, info.folder, info.request)
					.annotation("@JPostmanContext executor").debug("debug");
			return new ExecutorCall<>(name, executorInfo, options.executorClass(), options.session());
		}

		Method method = findExecutor(type, requestedProvider, info);
		JPostmanExecutor annotation = JPostmanAnnotations.executor(method);
		validateLocalDebug(annotation.debug(), info);
		JPostmanInfo executorInfo = info
				.child(method.getName(), info.executor, info.namespace, info.folder, info.request)
				.annotation("@JPostmanExecutor").id(annotationId(annotation.id())).debug(annotation.debug());
		return new ExecutorCall<>(method, annotation, executorInfo);
	}

	private boolean hasExecutorProviderMethods(Class<?> type) {
		Class<?> current = type;
		while (current != null && current != Object.class) {
			for (Method method : current.getDeclaredMethods()) {
				if (JPostmanAnnotations.executor(method) != null && isExecutorProvider(method)) {
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
		List<Method> providers = new ArrayList<>();
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
				providers.add(method);

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

		if (providers.size() == 1) {
			return providers.get(0);
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
		if (!providers.isEmpty()) {
			throw JPostmanErrors.usage(info, "Multiple named @JPostmanExecutor providers found.",
					"Select one with executor = \"#id\", or remove the id from the provider that should be the default.");
		}

		throw framework.skipException(info, "No default @JPostmanExecutor was configured.",
				"Add one executor provider, configure @JPostman.Context(executor = ...), or specify executor = \"#id\".");
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
				return idMethod;
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
		if (response != null) {
			return annotationId(response.id());
		}
		JPostmanRunner runner = JPostmanAnnotations.runner(method);
		return runner == null ? "" : annotationId(runner.id());
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

	private Object contextArg(Class<?> type, C ctx, Supplier<?> activeContextSupplier, Object runtimeOwner) {
		return JPostman.Test.class.isAssignableFrom(type)
				? JPostmanTestProxy.wrap(ctx, activeContextSupplier, null, runtimeOwner)
				: ctx;
	}

	private boolean isInfoParameter(Class<?> type) {
		return type == JPostmanInfo.class || type == JPostman.Info.class;
	}

	private Object invokeAnnotated(Object testInstance, Method method, C ctx, JPostmanInfo info) throws Exception {
		return invokeAnnotated(testInstance, method, ctx, info, null);
	}

	private Object invokeAnnotated(Object testInstance, Method method, C ctx, JPostmanInfo info,
			Supplier<?> activeContextSupplier) throws Exception {
		/*
		 * Request helpers run their method body before execution, so trace logging here
		 * is useful. Response helpers are executed first and logged after the response
		 * is available; logging again before invoking the method body prints the same
		 * JPostmanInfo twice.
		 */
		if (info == null || info.ended() <= 0L) {
			debug(testInstance, info, annotationDebug(method));
		}

		List<JPostmanTestProxy.CacheDependency> cachedDependencies = directCachedDependencies(testInstance, method,
				info);
		try (JPostmanTestProxy.CacheScope ignored = JPostmanTestProxy.openCacheScope(cachedDependencies)) {
			return invokeAnnotatedWithCacheScope(testInstance, method, ctx, info, activeContextSupplier);
		}
	}

	private Object invokeAnnotatedWithCacheScope(Object testInstance, Method method, C ctx, JPostmanInfo info,
			Supplier<?> activeContextSupplier) throws Exception {
		Class<?>[] types = method.getParameterTypes();

		// Supports an annotated helper with no injected arguments:
		// void helper()
		if (types.length == 0) {
			return invoke(testInstance, method);
		}

		// Supports a helper that receives only the framework context:
		// void helper(TestNgContext test) or void helper(JPostman.Test test)
		// JPostman.Test is proxied with activeContextSupplier so print(true) can use
		// the latest request prepared from the current JPostmanInfo values.
		if (types.length == 1 && isContextParameter(types[0])) {
			return invoke(testInstance, method, contextArg(types[0], ctx, activeContextSupplier, testInstance));
		}

		// Supports a helper that receives only annotation execution information:
		// void helper(JPostman.Info info) or void helper(JPostmanInfo info)
		if (types.length == 1 && isInfoParameter(types[0])) {
			return invoke(testInstance, method, info);
		}

		// Supports context plus annotation execution information:
		// void helper(TestNgContext test, JPostman.Info info)
		if (types.length == 2 && isContextParameter(types[0]) && isInfoParameter(types[1])) {
			return invoke(testInstance, method, contextArg(types[0], ctx, activeContextSupplier, testInstance), info);
		}

		// Supports context plus the current annotated Java method name:
		// void helper(TestNgContext test, String method)
		if (types.length == 2 && isContextParameter(types[0]) && String.class.isAssignableFrom(types[1])) {
			return invoke(testInstance, method, contextArg(types[0], ctx, activeContextSupplier, testInstance),
					info.method);
		}

		// Supports all three injected values: context, method name, and request name:
		// void helper(TestNgContext test, String method, String request)
		if (types.length == 3 && isContextParameter(types[0]) && String.class.isAssignableFrom(types[1])
				&& String.class.isAssignableFrom(types[2])) {
			Object contextArg = contextArg(types[0], ctx, activeContextSupplier, testInstance);
			return invoke(testInstance, method, contextArg, info.method, info.request);
		}

		throw JPostmanErrors.usage(info, "Unsupported annotated method parameters: " + method.toGenericString());
	}

	private List<JPostmanTestProxy.CacheDependency> directCachedDependencies(Object testInstance, Method method,
			JPostmanInfo info) {
		List<JPostmanTestProxy.CacheDependency> result = new ArrayList<>();
		if (testInstance == null || method == null) {
			return result;
		}
		for (String reference : directDependencyReferences(method)) {
			if (reference == null || reference.isBlank()) {
				continue;
			}
			Method dependencyMethod = findDependencyMethod(testInstance.getClass(), reference, info);
			JPostmanResponse response = JPostmanAnnotations.response(dependencyMethod);
			if (response != null) {
				String key = cacheKey(dependencyMethod, response.cache(), response.id());
				if (!key.isBlank()) {
					result.add(new JPostmanTestProxy.CacheDependency(reference.trim(), key));
				}
				continue;
			}
			JPostmanRequest request = JPostmanAnnotations.request(dependencyMethod);
			if (request != null) {
				String key = cacheKey(dependencyMethod, request.cache(), request.id());
				if (!key.isBlank()) {
					result.add(new JPostmanTestProxy.CacheDependency(reference.trim(), key));
				}
			}
		}
		return result;
	}

	private String[] directDependencyReferences(Method method) {
		JPostmanResponse response = JPostmanAnnotations.response(method);
		if (response != null) {
			return dependencies(response.dependsOn());
		}
		JPostmanRequest request = JPostmanAnnotations.request(method);
		if (request != null) {
			return dependencies(request.dependsOn());
		}
		JPostmanRunner runner = JPostmanAnnotations.runner(method);
		if (runner != null) {
			return dependencies(runner.dependsOn());
		}
		JPostmanCall call = JPostmanAnnotations.call(method);
		if (call != null) {
			return dependencies(call.dependsOn());
		}
		return new String[0];
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
