package io.jpostman.schema.util;

import java.util.Map;

import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;

/**
 * Scans one API operation for {{KEY}} placeholders and adds them to the
 * environment map.
 */
public final class ApiOperationEnvScanner {
	/**
	 * Creates a new ApiOperationEnvScanner instance.
	 */
	private ApiOperationEnvScanner() {
	}

	/**
	 * Scans the supplied model object and records discovered environment
	 * placeholders.
	 */
	public static void scan(ApiOperation operation, Map<String, Object> envs) {
		if (operation == null || envs == null) {
			return;
		}

		EnvVarExtractor.collect(operation.getPath(), envs);
		if (operation.getQueryParams() != null) {
			for (ApiParam param : operation.getQueryParams()) {
				EnvVarExtractor.collect(param.getName(), envs);
				EnvVarExtractor.collect(param.getValue(), envs);
			}
		}
		if (operation.getHeaders() != null) {
			for (ApiHeader header : operation.getHeaders()) {
				EnvVarExtractor.collect(header.getName(), envs);
				EnvVarExtractor.collect(header.getValue(), envs);
			}
		}
		if (operation.getBody() != null) {
			EnvVarExtractor.collect(operation.getBody().getContent(), envs);
		}
		if (operation.getAuth() != null) {
			EnvVarExtractor.collect(operation.getAuth().getName(), envs);
			EnvVarExtractor.collect(operation.getAuth().getValue(), envs);
		}
		if (operation.getExample() != null) {
			ExampleEnvValueApplier.apply(operation, envs);
		}
	}
}
