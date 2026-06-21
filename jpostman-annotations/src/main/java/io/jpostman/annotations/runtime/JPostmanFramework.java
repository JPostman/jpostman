package io.jpostman.annotations.runtime;

import java.io.InputStream;

import io.jpostman.ApiExecutor;

/**
 * Small bridge between the shared annotation engine and a test framework
 * context.
 *
 * @param <C> framework context type
 */
public interface JPostmanFramework<C> {

	Class<C> contextType();

	C create();

	void setCurrent(C context);

	void clearCurrent();

	void secret(C context, Object environment);

	void load(C context, InputStream rules) throws Exception;

	C loadRules(C context, String rule);

	C filter(C context, String... paths);

	C request(C context, io.jpostman.Request request);

	C response(C context, ApiExecutor executor);

	void verify(C context, int statusCode, boolean soft, boolean log);

	Object cache(C context, String key);

	void cache(C context, String key, Object value);

	String name();
}
