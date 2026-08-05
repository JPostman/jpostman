package io.jpostman.annotations.runtime;

import java.util.function.BiConsumer;

/** Executes an active manual call or void-executor proceed request. */
interface JPostmanRuntimeRequest<C> {

	/**
	 * Executes the current active request.
	 *
	 * @param action optional request customization callback
	 * @return framework context after response execution
	 */
	C execute(BiConsumer<C, JPostmanInfo> action) throws Exception;
}
