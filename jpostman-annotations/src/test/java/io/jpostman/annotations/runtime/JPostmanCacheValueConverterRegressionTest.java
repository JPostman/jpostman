package io.jpostman.annotations.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class JPostmanCacheValueConverterRegressionTest {

	@Test
	public void unwrapsPrivateFieldSecretWrapperBeforeSnapshotSerialization() {
		Object value = JPostmanCacheValueConverter.unwrap(new FakeServerValue("refresh-secret"));
		assertEquals("refresh-secret", value);
	}

	@Test
	public void unwrapsNonPublicAccessorSecretWrapperBeforeSnapshotSerialization() {
		Object value = JPostmanCacheValueConverter.unwrap(new FakeSecretValue("access-secret"));
		assertEquals("access-secret", value);
	}

	private static final class FakeServerValue {
		@SuppressWarnings("unused")
		private final Object value;

		private FakeServerValue(Object value) {
			this.value = value;
		}
	}

	private static final class FakeSecretValue {
		@SuppressWarnings("unused")
		private final Object secret;

		private FakeSecretValue(Object secret) {
			this.secret = secret;
		}
	}
}
