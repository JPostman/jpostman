package io.jpostman.schema.env;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.jpostman.schema.model.ApiAuth;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;
import io.jpostman.schema.model.ApiResponse;
import io.jpostman.schema.model.ApiSpec;

/**
 * Updates environment variables in the normalized ApiSpec model. This keeps the
 * UI simple: the UI sends key/value changes, and this service returns a new
 * model where all matching {{key}} usages are updated consistently.
 */
public class ApiSpecEnvironmentUpdater {
	private static final Pattern VALID_ENV_KEY = Pattern.compile("[A-Za-z0-9_.-]+");
	private static final String NORMALIZE_ONLY_KEY = "\u0000";

	/**
	 * Applies key renames first, adds new keys second, applies value changes third,
	 * then deletes keys. Delete wins when the same key appears in adds/values and
	 * deletes.
	 */
	public ApiSpec update(ApiSpec spec, ApiSpecEnvironmentUpdateRequest request) {
		if (spec == null || request == null) {
			return spec;
		}

		Map<String, String> renames = request.getRenames();
		if (renames != null) {
			for (Map.Entry<String, String> entry : renames.entrySet()) {
				renameKey(spec, entry.getKey(), entry.getValue());
			}
		}

		Map<String, Object> adds = request.getAdds();
		if (adds != null) {
			for (Map.Entry<String, Object> entry : adds.entrySet()) {
				String key = resolveRenamedKey(entry.getKey(), renames);
				addValue(spec, key, entry.getValue());
			}
		}

		Map<String, Object> values = request.getValues();
		if (values != null) {
			for (Map.Entry<String, Object> entry : values.entrySet()) {
				String key = resolveRenamedKey(entry.getKey(), renames);
				updateValue(spec, key, entry.getValue());
			}
		}

		List<String> deletes = request.getDeletes();
		if (deletes != null) {
			for (String key : deletes) {
				deleteKey(spec, resolveRenamedKey(key, renames));
			}
		}

		normalizeAllTokenWhitespace(spec);
		return spec;
	}

	/**
	 * Renames one environment key and updates every exact {{oldKey}} token usage in
	 * the model.
	 */
	public ApiSpec renameKey(ApiSpec spec, String oldKey, String newKey) {
		if (spec == null || oldKey == null || newKey == null || oldKey.equals(newKey)) {
			return spec;
		}

		oldKey = oldKey.trim();
		newKey = newKey.trim();
		validateKey(oldKey, "old environment key");
		validateKey(newKey, "new environment key");
		ensureRenameDoesNotOverwrite(spec, oldKey, newKey);

		renameEnvMapKey(spec, oldKey, newKey);
		updateTokens(spec, oldKey, newKey);
		return spec;
	}

	/**
	 * Adds one new environment value. Token usages do not need to change for an
	 * add.
	 */
	public ApiSpec addValue(ApiSpec spec, String key, Object value) {
		if (spec == null || key == null) {
			return spec;
		}
		key = key.trim();
		validateKey(key, "environment add key");
		if (spec.getEnvs().containsKey(key)) {
			throw new IllegalArgumentException("Environment key already exists: " + key);
		}
		spec.getEnvs().put(key, value);
		return spec;
	}

	/**
	 * Updates one environment value only. Token usages do not need to change for a
	 * value update. The method intentionally keeps upsert behavior for backward
	 * compatibility with older UI requests.
	 */
	public ApiSpec updateValue(ApiSpec spec, String key, Object value) {
		if (spec == null || key == null) {
			return spec;
		}
		key = key.trim();
		validateKey(key, "environment value key");
		spec.getEnvs().put(key, value);
		return spec;
	}

	/**
	 * Deletes one environment key from the environment map only. Existing token
	 * usages are preserved so the model still shows where the deleted variable was
	 * referenced.
	 */
	public ApiSpec deleteKey(ApiSpec spec, String key) {
		if (spec == null || key == null) {
			return spec;
		}
		key = key.trim();
		validateKey(key, "environment delete key");
		spec.getEnvs().remove(key);
		return spec;
	}

	private void renameEnvMapKey(ApiSpec spec, String oldKey, String newKey) {
		Map<String, Object> source = spec.getEnvs();
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			String key = oldKey.equals(entry.getKey()) ? newKey : entry.getKey();
			Object value = replaceTokensInObject(entry.getValue(), oldKey, newKey);
			result.put(key, value);
		}
		spec.setEnvs(result);
	}

	private void updateTokens(ApiSpec spec, String oldKey, String newKey) {
		spec.setName(replaceToken(spec.getName(), oldKey, newKey));
		spec.setBaseUrl(replaceToken(spec.getBaseUrl(), oldKey, newKey));

		for (ApiOperation operation : spec.getOperations()) {
			updateOperation(operation, oldKey, newKey);
		}
		for (ApiFolder folder : spec.getFolders()) {
			updateFolder(folder, oldKey, newKey);
		}
	}

	private void updateFolder(ApiFolder folder, String oldKey, String newKey) {
		if (folder == null) {
			return;
		}
		folder.setName(replaceToken(folder.getName(), oldKey, newKey));
		folder.setDescription(replaceToken(folder.getDescription(), oldKey, newKey));
		for (ApiOperation operation : folder.getOperations()) {
			updateOperation(operation, oldKey, newKey);
		}
		for (ApiFolder child : folder.getFolders()) {
			updateFolder(child, oldKey, newKey);
		}
	}

	private void updateOperation(ApiOperation operation, String oldKey, String newKey) {
		if (operation == null) {
			return;
		}
		operation.setFolder(replaceToken(operation.getFolder(), oldKey, newKey));
		operation.setMethodName(replaceToken(operation.getMethodName(), oldKey, newKey));
		operation.setDescription(replaceToken(operation.getDescription(), oldKey, newKey));
		operation.setPath(replaceToken(operation.getPath(), oldKey, newKey));
		operation.setGraphQlOperationType(replaceToken(operation.getGraphQlOperationType(), oldKey, newKey));

		updateParams(operation.getQueryParams(), oldKey, newKey);
		updateHeaders(operation.getHeaders(), oldKey, newKey);
		updateBody(operation.getBody(), oldKey, newKey);
		updateAuth(operation.getAuth(), oldKey, newKey);
		updateExample(operation.getExample(), oldKey, newKey);
		updateResponses(operation.getResponses(), oldKey, newKey);
	}

	private void updateParams(List<ApiParam> params, String oldKey, String newKey) {
		if (params == null) {
			return;
		}
		for (ApiParam param : params) {
			if (param == null) {
				continue;
			}
			param.setDescription(replaceToken(param.getDescription(), oldKey, newKey));
			param.setValue(replaceToken(param.getValue(), oldKey, newKey));
		}
	}

	private void updateHeaders(List<ApiHeader> headers, String oldKey, String newKey) {
		if (headers == null) {
			return;
		}
		for (ApiHeader header : headers) {
			if (header == null) {
				continue;
			}
			header.setDescription(replaceToken(header.getDescription(), oldKey, newKey));
			header.setValue(replaceToken(header.getValue(), oldKey, newKey));
		}
	}

	private void updateBody(ApiBody body, String oldKey, String newKey) {
		if (body == null) {
			return;
		}
		body.setDescription(replaceToken(body.getDescription(), oldKey, newKey));
		body.setContent(replaceToken(body.getContent(), oldKey, newKey));
	}

	private void updateAuth(ApiAuth auth, String oldKey, String newKey) {
		if (auth == null) {
			return;
		}
		auth.setName(replaceToken(auth.getName(), oldKey, newKey));
		auth.setValue(replaceToken(auth.getValue(), oldKey, newKey));
	}

	private void updateExample(ApiExample example, String oldKey, String newKey) {
		if (example == null) {
			return;
		}
		example.setName(replaceToken(example.getName(), oldKey, newKey));
		example.setPath(replaceToken(example.getPath(), oldKey, newKey));
		updateParams(example.getQueryParams(), oldKey, newKey);
		updateHeaders(example.getHeaders(), oldKey, newKey);
		updateBody(example.getBody(), oldKey, newKey);
	}

	private void updateResponses(List<ApiResponse> responses, String oldKey, String newKey) {
		if (responses == null) {
			return;
		}
		for (ApiResponse response : responses) {
			if (response == null) {
				continue;
			}
			response.setDescription(replaceToken(response.getDescription(), oldKey, newKey));
			updateBody(response.getBody(), oldKey, newKey);
			updateBody(response.getExample(), oldKey, newKey);
		}
	}

	@SuppressWarnings("unchecked")
	private Object replaceTokensInObject(Object value, String oldKey, String newKey) {
		if (value instanceof String) {
			return replaceToken((String) value, oldKey, newKey);
		}
		if (value instanceof Map<?, ?>) {
			Map<String, Object> result = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
				String key = String.valueOf(entry.getKey());
				result.put(replaceToken(key, oldKey, newKey), replaceTokensInObject(entry.getValue(), oldKey, newKey));
			}
			return result;
		}
		if (value instanceof List<?>) {
			List<Object> result = new ArrayList<>();
			for (Object item : (List<Object>) value) {
				result.add(replaceTokensInObject(item, oldKey, newKey));
			}
			return result;
		}
		return value;
	}

	private String replaceToken(String value, String oldKey, String newKey) {
		if (value == null || oldKey == null || newKey == null) {
			return value;
		}
		Pattern pattern = Pattern.compile("\\{\\{\\s*" + Pattern.quote(oldKey) + "\\s*}}");
		String replaced = pattern.matcher(value).replaceAll(Matcher.quoteReplacement("{{" + newKey + "}}"));
		return normalizeEnvTokenWhitespace(replaced);
	}

	/**
	 * Normalizes {{ key }} tokens to {{key}} so the model uses one stable format
	 * after save.
	 */
	private void normalizeAllTokenWhitespace(ApiSpec spec) {
		if (spec == null) {
			return;
		}

		Map<String, Object> envs = spec.getEnvs();
		Map<String, Object> normalized = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : envs.entrySet()) {
			normalized.put(entry.getKey(),
					replaceTokensInObject(entry.getValue(), NORMALIZE_ONLY_KEY, NORMALIZE_ONLY_KEY));
		}
		spec.setEnvs(normalized);

		updateTokens(spec, NORMALIZE_ONLY_KEY, NORMALIZE_ONLY_KEY);
	}

	private String normalizeEnvTokenWhitespace(String value) {
		if (value == null) {
			return null;
		}
		Pattern pattern = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.-]+)\\s*}}");
		Matcher matcher = pattern.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + matcher.group(1) + "}}"));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private void ensureRenameDoesNotOverwrite(ApiSpec spec, String oldKey, String newKey) {
		if (spec.getEnvs().containsKey(newKey) && !oldKey.equals(newKey)) {
			throw new IllegalArgumentException("Environment key already exists: " + newKey);
		}
	}

	private String resolveRenamedKey(String key, Map<String, String> renames) {
		if (key == null) {
			return null;
		}
		String trimmed = key.trim();
		if (renames == null) {
			return trimmed;
		}
		String renamed = renames.get(trimmed);
		return renamed == null ? trimmed : renamed.trim();
	}

	private void validateKey(String key, String label) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("Blank " + label);
		}
		if (!VALID_ENV_KEY.matcher(key).matches()) {
			throw new IllegalArgumentException("Invalid " + label + ": " + key);
		}
	}
}
