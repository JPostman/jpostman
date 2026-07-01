package io.jpostman.schema.util;

import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiSpec;

/**
 * Applies {{BASE_URL}} replacement when URL override mode is enabled.
 */
public final class BaseUrlOverride {
	private static final String BASE_URL_TOKEN = "{{BASE_URL}}";

	/**
	 * Creates a new BaseUrlOverride instance.
	 */
	private BaseUrlOverride() {
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	public static void apply(ApiSpec spec) {
		if (spec == null || spec.getBaseUrl() == null || spec.getBaseUrl().isBlank()) {
			return;
		}

		spec.getEnvs().put("BASE_URL", spec.getBaseUrl());
		if (!spec.isOverrideUrl()) {
			return;
		}

		for (ApiOperation operation : spec.getOperations()) {
			apply(operation, spec.getBaseUrl());
		}
		for (ApiFolder folder : spec.getFolders()) {
			apply(folder, spec.getBaseUrl());
		}
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	private static void apply(ApiFolder folder, String baseUrl) {
		if (folder == null) {
			return;
		}
		for (ApiOperation operation : folder.getOperations()) {
			apply(operation, baseUrl);
		}
		for (ApiFolder child : folder.getFolders()) {
			apply(child, baseUrl);
		}
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	private static void apply(ApiOperation operation, String baseUrl) {
		if (operation == null || operation.getPath() == null || operation.getPath().isBlank()) {
			return;
		}

		String path = operation.getPath().trim();
		if (path.startsWith(BASE_URL_TOKEN)) {
			return;
		}
		if (path.startsWith(baseUrl)) {
			path = path.substring(baseUrl.length());
		}
		if (path.isBlank()) {
			operation.setPath(BASE_URL_TOKEN);
			return;
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		operation.setPath(BASE_URL_TOKEN + path);
	}
}
