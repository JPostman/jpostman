package io.jpostman.schema.util;

import java.util.LinkedHashMap;
import java.util.Map;

import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiSpec;

/**
 * Scans an entire API specification for {{KEY}} placeholders and base URL
 * values.
 */
public final class ApiSpecEnvScanner {
	/**
	 * Creates a new ApiSpecEnvScanner instance.
	 */
	private ApiSpecEnvScanner() {
	}

	/**
	 * Scans the supplied model object and records discovered environment
	 * placeholders.
	 */
	public static void scan(ApiSpec spec) {
		if (spec == null) {
			return;
		}
		if (spec.getEnvs() == null) {
			spec.setEnvs(new LinkedHashMap<>());
		}

		Map<String, Object> envs = spec.getEnvs();
		if (spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank()) {
			envs.put("BASE_URL", spec.getBaseUrl());
		}

		for (ApiOperation operation : spec.getOperations()) {
			ApiOperationEnvScanner.scan(operation, envs);
		}
		for (ApiFolder folder : spec.getFolders()) {
			scanFolder(folder, envs);
		}
	}

	/**
	 * Handles scan folder logic for this class.
	 */
	private static void scanFolder(ApiFolder folder, Map<String, Object> envs) {
		if (folder == null) {
			return;
		}
		for (ApiOperation operation : folder.getOperations()) {
			ApiOperationEnvScanner.scan(operation, envs);
		}
		for (ApiFolder child : folder.getFolders()) {
			scanFolder(child, envs);
		}
	}
}
