package io.jpostman.secure;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Ordered registry of plain and protected values.
 */
public final class SecureValues {

	private final Map<String, SecureValue> values;

	private SecureValues(Map<String, SecureValue> values) {
		this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
	}

	/**
	 * Creates an empty value registry.
	 *
	 * @return empty registry
	 */
	public static SecureValues empty() {
		return new Builder().build();
	}

	/**
	 * Creates a value registry builder.
	 *
	 * @return builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Returns the stored value.
	 *
	 * @param key value key
	 * @return secure value, or {@code null} when missing
	 */
	public SecureValue get(String key) {
		return values.get(key);
	}

	/**
	 * Returns display values for safe output.
	 *
	 * @return key/value map using masked values for protected entries
	 */
	public Map<String, String> asMap() {
		Map<String, String> result = new LinkedHashMap<>();
		values.forEach((key, value) -> result.put(key, value.toString()));
		return result;
	}

	/**
	 * Returns stored secure values.
	 *
	 * @return secure value map
	 */
	Map<String, SecureValue> values() {
		return values;
	}

	/**
	 * Returns a display-safe string with one key/value pair per line.
	 *
	 * @return masked string representation
	 */
	@Override
	public String toString() {
		return values().entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue())
				.collect(Collectors.joining("\n"));
	}

	/**
	 * Builder for {@link SecureValues}.
	 */
	public static final class Builder {
		private final Map<String, SecureValue> values = new LinkedHashMap<>();

		/**
		 * Adds a plain value.
		 *
		 * @param key   value key
		 * @param value plain value
		 * @return this builder
		 */
		public Builder plain(String key, Object value) {
			values.put(key, SecureValue.plain(String.valueOf(value)));
			return this;
		}

		/**
		 * Adds a protected value.
		 *
		 * @param key   value key
		 * @param value protected value
		 * @return this builder
		 */
		public Builder secret(String key, Object value) {
			values.put(key, SecureValue.secret(String.valueOf(value)));
			return this;
		}

		/**
		 * Adds protected values from a map.
		 *
		 * @param secrets protected values
		 * @return this builder
		 */
		public Builder secret(Map<String, ?> secrets) {
			if (secrets != null) {
				secrets.forEach(this::secret);
			}
			return this;
		}

		/**
		 * Adds protected values from a map.
		 *
		 * @param secrets protected values
		 * @return this builder
		 */
		public Builder secrets(Map<String, ?> secrets) {
			return secret(secrets);
		}

		/**
		 * Adds plain values from a map.
		 *
		 * @param plainValues plain values
		 * @return this builder
		 */
		public Builder plain(Map<String, ?> plainValues) {
			if (plainValues != null) {
				plainValues.forEach(this::plain);
			}
			return this;
		}

		/**
		 * Builds the immutable registry.
		 *
		 * @return secure values
		 */
		public SecureValues build() {
			return new SecureValues(values);
		}
	}
}
