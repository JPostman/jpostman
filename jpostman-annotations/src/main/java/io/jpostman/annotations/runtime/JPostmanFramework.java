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

	void loadRules(C context, String rule);

	void request(C context, io.jpostman.Request request);

	void response(C context, ApiExecutor executor);

	void verify(C context, int statusCode);

	Object cache(C context, String key);

	void cache(C context, String key, Object value);

	String name();
}
