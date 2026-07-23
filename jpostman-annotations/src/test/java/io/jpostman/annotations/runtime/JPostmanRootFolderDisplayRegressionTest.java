package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JPostmanRootFolderDisplayRegressionTest {

	@Test
	void blankFolderIsDisplayedAsRootWithoutChangingOtherDefaults() {
		JPostmanInfo info = new JPostmanInfo("@JPostmanRunner", "testAuthRunner", "", "", "");

		String suffix = JPostmanErrors.suffix(info);

		assertTrue(suffix.contains("folder=<root>"), suffix);
		assertTrue(suffix.contains("namespace=<default>"), suffix);
		assertTrue(suffix.contains("request=<default>"), suffix);
		assertTrue(suffix.contains("executor=<default>"), suffix);
		assertFalse(suffix.contains("folder=<default>"), suffix);
	}

	@Test
	void explicitFolderNameIsPreserved() {
		JPostmanInfo info = new JPostmanInfo("@JPostmanRunner", "testAuthRunner", "", "Auth", "");

		String suffix = JPostmanErrors.suffix(info);

		assertTrue(suffix.contains("folder=Auth"), suffix);
		assertFalse(suffix.contains("folder=<root>"), suffix);
	}
}
