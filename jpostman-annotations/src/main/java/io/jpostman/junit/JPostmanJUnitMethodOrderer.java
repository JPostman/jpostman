package io.jpostman.junit;

import java.lang.reflect.Method;
import java.util.Comparator;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.MethodOrdererContext;
import org.junit.jupiter.api.Order;

import io.jpostman.annotations.runtime.JPostmanAnnotations;

/**
 * Default JPostman JUnit method ordering.
 *
 * <p>
 * When the user supplies {@link Order @Order} values, JUnit's normal
 * {@link MethodOrderer.OrderAnnotation} semantics win. Otherwise JPostman runs
 * response tests first, calls second, ordinary test methods next, and runners
 * last. Ordering within the same group remains stable.
 * </p>
 *
 * <p>
 * A class-level {@code @TestMethodOrder(...)} declared by the user overrides
 * the orderer contributed by {@code @JPostman.JUnit} before this class is used.
 * </p>
 */
public final class JPostmanJUnitMethodOrderer implements MethodOrderer {

	@Override
	public void orderMethods(MethodOrdererContext context) {
		if (context.getMethodDescriptors().stream()
				.anyMatch(descriptor -> descriptor.findAnnotation(Order.class).isPresent())) {
			new MethodOrderer.OrderAnnotation().orderMethods(context);
			return;
		}

		context.getMethodDescriptors().sort(Comparator.comparingInt(descriptor -> rank(descriptor.getMethod())));
	}

	public static int rank(Method method) {
		if (JPostmanAnnotations.response(method) != null) {
			return 0;
		}
		if (JPostmanAnnotations.call(method) != null) {
			return 1;
		}
		if (JPostmanAnnotations.runner(method) != null) {
			return 3;
		}
		return 2;
	}
}
