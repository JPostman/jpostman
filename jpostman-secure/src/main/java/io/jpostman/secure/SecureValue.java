package io.jpostman.secure;

/**
 * Stores one value with display masking support.
 *
 * <p>
 * The real value is available only through {@link #reveal()}. Normal string
 * rendering returns the masked display value when the value is protected.
 * </p>
 */
public final class SecureValue {

	public static final String DEFAULT_MASK = "********";
	private final String value;
	private final boolean protectedValue;
	private final String mask;

	private SecureValue(String value, boolean protectedValue, String mask) {
		this.value = value == null ? "" : value;
		this.protectedValue = protectedValue;
		this.mask = mask == null || mask.isBlank() ? DEFAULT_MASK : mask;
	}

	/**
	 * Creates a plain value.
	 *
	 * @param value value to store
	 * @return plain secure value
	 */
	public static SecureValue plain(String value) {
		return new SecureValue(value, false, DEFAULT_MASK);
	}

	/**
	 * Creates a protected value using the default mask.
	 *
	 * @param value real value to store
	 * @return protected secure value
	 */
	public static SecureValue secret(String value) {
		return new SecureValue(value, true, DEFAULT_MASK);
	}

	/**
	 * Creates a protected value using a custom mask.
	 *
	 * @param value real value to store
	 * @param mask  display mask
	 * @return protected secure value
	 */
	public static SecureValue secret(String value, String mask) {
		return new SecureValue(value, true, mask);
	}

	/** @return true when this value should be masked in output. */
	public boolean isProtected() {
		return protectedValue;
	}

	/** @return real value. */
	public String reveal() {
		return value;
	}

	/** @return configured mask. */
	public String mask() {
		return mask;
	}

	@Override
	public String toString() {
		return protectedValue ? mask : value;
	}
}
