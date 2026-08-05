package io.jpostman.schema.util;

import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiSpec;

/**
 * Applies the resolved base URL environment token when URL override mode is
 * enabled.
 */
public final class BaseUrlOverride {

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

		BaseUrlVariable.record(spec);
		if (!spec.isOverrideUrl()) {
			return;
		}

		String baseUrlToken = BaseUrlVariable.token(spec);
		for (ApiOperation operation : spec.getOperations()) {
			apply(operation, spec.getBaseUrl(), baseUrlToken);
		}
		for (ApiFolder folder : spec.getFolders()) {
			apply(folder, spec.getBaseUrl(), baseUrlToken);
		}
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	private static void apply(ApiFolder folder, String baseUrl, String baseUrlToken) {
		if (folder == null) {
			return;
		}
		for (ApiOperation operation : folder.getOperations()) {
			apply(operation, baseUrl, baseUrlToken);
		}
		for (ApiFolder child : folder.getFolders()) {
			apply(child, baseUrl, baseUrlToken);
		}
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	private static void apply(ApiOperation operation, String baseUrl, String baseUrlToken) {
		if (operation == null || operation.getPath() == null || operation.getPath().isBlank()) {
			return;
		}

		String path = operation.getPath().trim();
		if (path.startsWith(baseUrlToken)) {
			return;
		}
		if (path.startsWith(baseUrl)) {
			path = path.substring(baseUrl.length());
		}
		if (path.isBlank()) {
			operation.setPath(baseUrlToken);
			return;
		}
		if (!path.startsWith("/")) {
			path = "/" + path;
		}
		operation.setPath(baseUrlToken + path);
	}
}
