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

	@Override
	public Class<JUnitContext> contextType() {
		return JUnitContext.class;
	}

	@Override
	public JUnitContext create() {
		return JUnitContext.create();
	}

	@Override
	public void setCurrent(JUnitContext context) {
		JUnitContext.setCurrent(context);
	}

	@Override
	public void clearCurrent() {
		JUnitContext.clearCurrent();
	}

	@Override
	public void secret(JUnitContext context, Object environment) {
		context.secret((Environment) environment);
	}

	@Override
	public void load(JUnitContext context, InputStream rules) throws Exception {
		context.load(rules);
	}

	@Override
	public JUnitContext loadRules(JUnitContext context, String rule) {
		return context.loadRules(rule);
	}

	@Override
	public JUnitContext filter(JUnitContext context, String... paths) {
		if (paths == null || paths.length == 0) {
			return context;
		}
		return context.filter(paths);
	}

	@Override
	public JUnitContext request(JUnitContext context, Request request) {
		return context.request(request);
	}

	@Override
	public JUnitContext response(JUnitContext context, ApiExecutor executor) {
		return context.response(executor);
	}

	@Override
	public void verify(JUnitContext context, int statusCode, boolean soft, boolean log) {
		if (soft) {
			context.soft(log).statusCode(statusCode);
			return;
		}

		context.asserts(log).verify(statusCode);
	}

	@Override
	public Object cache(JUnitContext context, String key) {
		return context.cache(key);
	}

	@Override
	public void cache(JUnitContext context, String key, Object value) {
		context.cache(key, value);
	}

	@Override
	public String name() {
		return "JUnit";
	}
}
