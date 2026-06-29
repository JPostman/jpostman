package io.jpostman.annotations.runtime;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanExecutor;
import io.jpostman.annotations.JPostmanReportContext;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;
import io.jpostman.annotations.JPostmanTestContext;
import io.jpostman.annotations.testng.JPostmanTestNgAnnotations;

public final class JPostmanAnnotations {

	private JPostmanAnnotations() {
	}

	public static boolean hasTestNg(Class<?> type) {
		return type.isAnnotationPresent(JPostmanTestNgAnnotations.class)
				|| type.isAnnotationPresent(JPostman.TestNG.class);
	}

	public static JPostmanContext context(Field field) {
		JPostmanContext old = field.getAnnotation(JPostmanContext.class);
		if (old != null) {
			return old;
		}
		JPostman.Context compact = field.getAnnotation(JPostman.Context.class);
		return compact == null ? null : adapt(compact, JPostmanContext.class);
	}

	public static boolean hasContext(Field field) {
		return context(field) != null;
	}

	public static JPostmanTestContext testContext(Field field) {
		JPostmanTestContext old = field.getAnnotation(JPostmanTestContext.class);
		if (old != null) {
			return old;
		}
		JPostman.TestContext compact = field.getAnnotation(JPostman.TestContext.class);
		return compact == null ? null : adapt(compact, JPostmanTestContext.class);
	}

	public static boolean hasTestContext(Field field) {
		return testContext(field) != null;
	}

	public static JPostmanReportContext reportContext(Field field) {
		JPostmanReportContext old = field.getAnnotation(JPostmanReportContext.class);
		if (old != null) {
			return old;
		}
		JPostman.ReportContext compact = field.getAnnotation(JPostman.ReportContext.class);
		return compact == null ? null : adapt(compact, JPostmanReportContext.class);
	}

	public static boolean hasReportContext(Field field) {
		return reportContext(field) != null;
	}

	public static JPostmanExecutor executor(Method method) {
		JPostmanExecutor old = method.getAnnotation(JPostmanExecutor.class);
		if (old != null) {
			return old;
		}
		JPostman.Executor compact = method.getAnnotation(JPostman.Executor.class);
		return compact == null ? null : adapt(compact, JPostmanExecutor.class);
	}

	public static boolean hasExecutor(Method method) {
		return executor(method) != null;
	}

	public static JPostmanRequest request(Method method) {
		JPostmanRequest old = method.getAnnotation(JPostmanRequest.class);
		if (old != null) {
			return old;
		}
		JPostman.Request compact = method.getAnnotation(JPostman.Request.class);
		return compact == null ? null : adapt(compact, JPostmanRequest.class);
	}

	public static boolean hasRequest(Method method) {
		return request(method) != null;
	}

	public static JPostmanResponse response(Method method) {
		JPostmanResponse old = method.getAnnotation(JPostmanResponse.class);
		if (old != null) {
			return old;
		}
		JPostman.Response compact = method.getAnnotation(JPostman.Response.class);
		return compact == null ? null : adapt(compact, JPostmanResponse.class);
	}

	public static boolean hasResponse(Method method) {
		return response(method) != null;
	}

	public static JPostmanRunner runner(Method method) {
		JPostmanRunner old = method.getAnnotation(JPostmanRunner.class);
		if (old != null) {
			return old;
		}
		JPostman.Runner compact = method.getAnnotation(JPostman.Runner.class);
		return compact == null ? null : adapt(compact, JPostmanRunner.class);
	}

	public static boolean hasRunner(Method method) {
		return runner(method) != null;
	}

	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A adapt(Annotation source, Class<A> targetType) {
		InvocationHandler handler = (proxy, method, args) -> {
			String name = method.getName();
			if ("annotationType".equals(name) && method.getParameterCount() == 0) {
				return targetType;
			}
			if ("toString".equals(name) && method.getParameterCount() == 0) {
				return source.toString();
			}
			if ("hashCode".equals(name) && method.getParameterCount() == 0) {
				return source.hashCode();
			}
			if ("equals".equals(name) && method.getParameterCount() == 1) {
				return proxy == args[0];
			}
			Object special = specialValue(source, method);
			if (special != null) {
				return special;
			}
			try {
				Method sourceMethod = source.annotationType().getMethod(name);
				return sourceMethod.invoke(source);
			} catch (NoSuchMethodException e) {
				return method.getDefaultValue();
			}
		};
		return (A) Proxy.newProxyInstance(targetType.getClassLoader(), new Class<?>[] { targetType }, handler);
	}

	private static Object specialValue(Annotation source, Method targetMethod) {
		if (!(source instanceof JPostman.Context) || !"executor".equals(targetMethod.getName())
				|| targetMethod.getParameterCount() != 0 || targetMethod.getReturnType() != Class.class) {
			return null;
		}

		JPostman.Context context = (JPostman.Context) source;
		Class<?> executorClass = context.executorClass();
		if (executorClass != Void.class) {
			return executorClass;
		}

		return executorClass(context);
	}

	private static Class<?> executorClass(JPostman.Context context) {
		String executorName = context.executor();
		String name = executorName == null ? "" : executorName.trim();
		if (name.isBlank()) {
			return Void.class;
		}

		if (name.endsWith(".class")) {
			name = name.substring(0, name.length() - ".class".length()).trim();
		}

		try {
			return Class.forName(name);
		} catch (ClassNotFoundException e) {
			throw new IllegalArgumentException(compactContextMessage(context,
					"Invalid JPostman executor class: " + name, "The executor class could not be loaded.",
					"Use a fully qualified class name available on the test classpath.",
					"Example: executor = \"io.jpostman.restassured.RestAssuredExecutor\"."));
		}
	}

	private static String compactContextMessage(JPostman.Context context, String... lines) {
		StringBuilder message = new StringBuilder();
		if (lines != null) {
			for (String line : lines) {
				String value = JPostmanErrors.stripSuffix(line).trim();
				if (value.isBlank()) {
					continue;
				}
				if (message.length() > 0) {
					message.append(JPostmanErrors.ENDL);
				}
				message.append(value);
			}
		}
		if (message.length() > 0) {
			message.append(JPostmanErrors.ENDL);
		}
		message.append("(@JPostmanContext: config=").append(defaultValue(context.config()));
		message.append(", namespace=").append(defaultValue(context.namespace()));
		message.append(", collection=").append(defaultValue(context.collection()));
		message.append(", environment=").append(defaultValue(context.environment()));
		message.append(")").append(JPostmanErrors.ENDL);
		return message.toString();
	}

	private static String defaultValue(String value) {
		return value == null || value.isBlank() ? "<default>" : value;
	}
}
