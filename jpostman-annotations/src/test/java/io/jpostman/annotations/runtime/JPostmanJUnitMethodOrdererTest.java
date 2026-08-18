package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.jpostman.annotations.JPostman;
import io.jpostman.junit.JPostmanJUnitMethodOrderer;

class JPostmanJUnitMethodOrdererTest {

	@Test
	void junitFacadeInstallsJPostmanDefaultMethodOrderer() {
		TestMethodOrder annotation = JPostman.JUnit.class.getAnnotation(TestMethodOrder.class);
		assertNotNull(annotation);
		assertEquals(JPostmanJUnitMethodOrderer.class, annotation.value());
	}

	@Test
	void junitPlatformDefaultUsesJPostmanMethodOrderer() throws Exception {
		Properties properties = new Properties();
		try (InputStream input = JPostmanJUnitMethodOrdererTest.class
				.getResourceAsStream("/junit-platform.properties")) {
			assertNotNull(input);
			properties.load(input);
		}
		assertEquals(JPostmanJUnitMethodOrderer.class.getName(),
				properties.getProperty("junit.jupiter.testmethod.order.default"));
	}

	@Test
	void defaultRanksResponseThenCallThenPlainThenRunner() throws Exception {
		assertEquals(0, rank("response"));
		assertEquals(1, rank("call"));
		assertEquals(2, rank("plain"));
		assertEquals(3, rank("runner"));
	}

	private int rank(String methodName) throws Exception {
		Method method = Fixture.class.getDeclaredMethod(methodName);
		return JPostmanJUnitMethodOrderer.rank(method);
	}

	private static final class Fixture {
		@JPostman.Response(request = "Response")
		@Test
		void response() {
		}

		@JPostman.Call(request = "Call")
		@Test
		void call() {
		}

		@Test
		void plain() {
		}

		@JPostman.Runner(folder = "Folder")
		@Test
		void runner() {
		}
	}
}
