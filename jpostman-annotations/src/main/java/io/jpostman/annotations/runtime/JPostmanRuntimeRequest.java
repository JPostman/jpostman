package io.jpostman.annotations.runtime;

import java.util.function.BiConsumer;

/** Executes a runtime @JPostman.Call request for an injected runtime. */
interface JPostmanRuntimeRequest<C> {

	/**
	 * Executes the current runtime call.
	 *
	 * @param action optional request customization callback
	 * @return framework context after response execution
	 */
	C execute(BiConsumer<C, JPostmanInfo> action) throws Exception;
}
