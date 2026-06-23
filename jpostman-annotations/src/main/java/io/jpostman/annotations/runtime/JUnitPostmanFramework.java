package io.jpostman.annotations.runtime;

import java.io.InputStream;

import io.jpostman.ApiExecutor;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.junit.JUnitContext;

/**
 * JUnit implementation of the annotation framework bridge.
 */
public final class JUnitPostmanFramework implements JPostmanFramework<JUnitContext> {

	/** {@inheritDoc} */
	@Override
	public Class<JUnitContext> contextType() {
		return JUnitContext.class;
	}

	/** {@inheritDoc} */
	@Override
	public JUnitContext create() {
		return JUnitContext.create();
	}

	/** {@inheritDoc} */
	@Override
	public void setCurrent(JUnitContext context) {
		JUnitContext.setCurrent(context);
	}

	/** {@inheritDoc} */
	@Override
	public void clearCurrent() {
		JUnitContext.clearCurrent();
	}

	/** {@inheritDoc} */
	@Override
	public void secret(JUnitContext context, Object environment) {
		context.secret((Environment) environment);
	}

	/** {@inheritDoc} */
	@Override
	public void load(JUnitContext context, InputStream rules) throws Exception {
		context.load(rules);
	}

	/** {@inheritDoc} */
	@Override
	public JUnitContext loadRules(JUnitContext context, String rule) {
		return context.loadRules(rule);
	}

	/** {@inheritDoc} */
	@Override
	public JUnitContext filter(JUnitContext context, String... paths) {
		if (paths == null || paths.length == 0) {
			return context;
		}
		return context.filter(paths);
	}

	/** {@inheritDoc} */
	@Override
	public JUnitContext request(JUnitContext context, Request request) {
		return context.request(request);
	}

	/** {@inheritDoc} */
	@Override
	public JUnitContext response(JUnitContext context, ApiExecutor executor) {
		return context.response(executor);
	}

	/** {@inheritDoc} */
	@Override
	public void verify(JUnitContext context, int statusCode, boolean soft, boolean log) {
		if (soft) {
			context.soft(log).statusCode(statusCode);
			return;
		}
		context.asserts(log).statusCode(statusCode);
	}

	/** {@inheritDoc} */
	@Override
	public Object cache(JUnitContext context, String key) {
		return context.cache(key);
	}

	/** {@inheritDoc} */
	@Override
	public void cache(JUnitContext context, String key, Object value) {
		context.cache(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public String name() {
		return "JUnit";
	}
}
