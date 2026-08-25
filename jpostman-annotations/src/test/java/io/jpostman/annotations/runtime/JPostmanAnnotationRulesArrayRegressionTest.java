package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import io.jpostman.annotations.JPostman;
import io.jpostman.annotations.JPostmanCall;
import io.jpostman.annotations.JPostmanRequest;
import io.jpostman.annotations.JPostmanResponse;
import io.jpostman.annotations.JPostmanRunner;

class JPostmanAnnotationRulesArrayRegressionTest {

	@Test
	void secureRuleAttributesAreArraysEverywhere() throws Exception {
		assertArrayAttribute(JPostmanRunner.class, "rules");
		assertArrayAttribute(JPostmanRequest.class, "rules");
		assertArrayAttribute(JPostmanResponse.class, "rules");
		assertArrayAttribute(JPostmanCall.class, "rules");
		assertArrayAttribute(JPostman.Runner.class, "rules");
		assertArrayAttribute(JPostman.Request.class, "rules");
		assertArrayAttribute(JPostman.Response.class, "rules");
		assertArrayAttribute(JPostman.Call.class, "rules");
	}

	@Test
	void callSupportsAssertionSectionsInCompactAndStandaloneForms() throws Exception {
		assertArrayAttribute(JPostmanCall.class, "asserts");
		assertArrayAttribute(JPostman.Call.class, "asserts");

		Method compactMethod = Fixture.class.getDeclaredMethod("compactCall");
		JPostmanCall compact = JPostmanAnnotations.call(compactMethod);
		assertArrayEquals(new String[] { "default", "users" }, compact.rules());
		assertArrayEquals(new String[] { "base", "product" }, compact.asserts());

		Method standaloneMethod = Fixture.class.getDeclaredMethod("standaloneCall");
		JPostmanCall standalone = JPostmanAnnotations.call(standaloneMethod);
		assertArrayEquals(new String[] { "default", "users" }, standalone.rules());
		assertArrayEquals(new String[] { "base", "product" }, standalone.asserts());
	}

	private static void assertArrayAttribute(Class<?> annotationType, String name) throws Exception {
		Method method = annotationType.getMethod(name);
		assertEquals(String[].class, method.getReturnType());
		assertArrayEquals(new String[0], (String[]) method.getDefaultValue());
	}

	private static final class Fixture {

		@JPostman.Call(rules = { "default", "users" }, asserts = { "base", "product" })
		void compactCall() {
		}

		@JPostmanCall(rules = { "default", "users" }, asserts = { "base", "product" })
		void standaloneCall() {
		}
	}
}
