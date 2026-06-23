package io.jpostman.annotations.testng;

import java.io.InputStream;

import io.jpostman.ApiExecutor;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.annotations.runtime.JPostmanFramework;
import io.jpostman.testng.TestNgContext;

/**
 * TestNG implementation of the annotation framework bridge.
 */
public final class TestNgPostmanFramework implements JPostmanFramework<TestNgContext> {

	/** {@inheritDoc} */
	@Override
	public Class<TestNgContext> contextType() {
		return TestNgContext.class;
	}

	/** {@inheritDoc} */
	@Override
	public TestNgContext create() {
		return TestNgContext.create();
	}

	/** {@inheritDoc} */
	@Override
	public void setCurrent(TestNgContext context) {
		TestNgContext.setCurrent(context);
	}

	/** {@inheritDoc} */
	@Override
	public void clearCurrent() {
		TestNgContext.clearCurrent();
	}

	/** {@inheritDoc} */
	@Override
	public void secret(TestNgContext context, Object environment) {
		context.secret((Environment) environment);
	}

	/** {@inheritDoc} */
	@Override
	public void load(TestNgContext context, InputStream rules) throws Exception {
		context.load(rules);
	}

	/** {@inheritDoc} */
	@Override
	public TestNgContext loadRules(TestNgContext context, String rule) {
		return context.loadRules(rule);
	}

	/** {@inheritDoc} */
	@Override
	public TestNgContext filter(TestNgContext context, String... paths) {
		if (paths == null || paths.length == 0) {
			return context;
		}
		return context.filter(paths);
	}

	/** {@inheritDoc} */
	@Override
	public TestNgContext request(TestNgContext context, Request request) {
		return context.request(request);
	}

	/** {@inheritDoc} */
	@Override
	public TestNgContext response(TestNgContext context, ApiExecutor executor) {
		return context.response(executor);
	}

	/** {@inheritDoc} */
	@Override
	public void verify(TestNgContext context, int statusCode, boolean soft, boolean log) {
		if (soft) {
			context.soft(log).statusCode(statusCode);
			return;
		}
		context.asserts(log).statusCode(statusCode);
	}

	/** {@inheritDoc} */
	@Override
	public Object cache(TestNgContext context, String key) {
		return context.cache(key);
	}

	/** {@inheritDoc} */
	@Override
	public void cache(TestNgContext context, String key, Object value) {
		context.cache(key, value);
	}

	/** {@inheritDoc} */
	@Override
	public String name() {
		return "TestNG";
	}
}
