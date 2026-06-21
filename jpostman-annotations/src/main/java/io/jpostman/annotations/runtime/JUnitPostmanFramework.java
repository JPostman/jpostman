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
	public void loadRules(JUnitContext context, String rule) {
		context.loadRules(rule);
	}

	@Override
	public void request(JUnitContext context, Request request) {
		context.request(request);
	}

	@Override
	public void response(JUnitContext context, ApiExecutor executor) {
		context.response(executor);
	}

	@Override
	public void verify(JUnitContext context, int statusCode) {
		context.verify(statusCode);
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
