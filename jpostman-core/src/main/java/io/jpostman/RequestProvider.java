package io.jpostman;

/**
 * Provides a request for execution.
 *
 * <p>
 * Implementations can build a normal {@link Request} from another request
 * source, such as a secure request wrapper.
 * </p>
 */
@FunctionalInterface
public interface RequestProvider {

	/**
	 * Builds the request that should be executed.
	 *
	 * @return executable request
	 */
	Request build();
}
