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
	public void loadRules(TestNgContext context, String rule) {
		context.loadRules(rule);
	}

	@Override
	public void request(TestNgContext context, Request request) {
		context.request(request);
	}

	@Override
	public void response(TestNgContext context, ApiExecutor executor) {
		context.response(executor);
	}

	@Override
	public void verify(TestNgContext context, int statusCode) {
		context.verify(statusCode);
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
