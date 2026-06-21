package io.jpostman.annotations.runtime;

import java.io.InputStream;

import io.jpostman.ApiExecutor;
import io.jpostman.Environment;
import io.jpostman.Request;
import io.jpostman.testng.TestNgContext;

/**
 * TestNG implementation of the annotation framework bridge.
 */
public final class TestNgPostmanFramework implements JPostmanFramework<TestNgContext> {

	@Override
	public Class<TestNgContext> contextType() {
		return TestNgContext.class;
	}

	@Override
	public TestNgContext create() {
		return TestNgContext.create();
	}

	@Override
	public void setCurrent(TestNgContext context) {
		TestNgContext.setCurrent(context);
	}

	@Override
	public void clearCurrent() {
		TestNgContext.clearCurrent();
	}

	@Override
	public void secret(TestNgContext context, Object environment) {
		context.secret((Environment) environment);
	}

	@Override
	public void load(TestNgContext context, InputStream rules) throws Exception {
		context.load(rules);
	}

	@Override
	public TestNgContext loadRules(TestNgContext context, String rule) {
		return context.loadRules(rule);
	}

	@Override
	public TestNgContext filter(TestNgContext context, String... paths) {
		if (paths == null || paths.length == 0) {
			return context;
		}
		return context.filter(paths);
	}

	@Override
	public TestNgContext request(TestNgContext context, Request request) {
		return context.request(request);
	}

	@Override
	public TestNgContext response(TestNgContext context, ApiExecutor executor) {
		return context.response(executor);
	}

	@Override
	public void verify(TestNgContext context, int statusCode, boolean soft, boolean log) {
		if (soft) {
			context.soft(log).statusCode(statusCode);
			return;
		}
		context.verify().asserts(log).verify(statusCode);
	}

	@Override
	public Object cache(TestNgContext context, String key) {
		return context.cache(key);
	}

	@Override
	public void cache(TestNgContext context, String key, Object value) {
		context.cache(key, value);
	}

	@Override
	public String name() {
		return "TestNG";
	}
}
