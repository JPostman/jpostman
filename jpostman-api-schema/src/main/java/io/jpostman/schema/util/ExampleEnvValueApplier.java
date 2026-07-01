package io.jpostman.schema.util;

import java.net.URI;
import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;

/**
 * Applies real example values back into the environment map for matching
 * placeholders.
 */
public final class ExampleEnvValueApplier {
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Creates a new ExampleEnvValueApplier instance.
	 */
	private ExampleEnvValueApplier() {
	}

	/**
	 * Applies derived values to the supplied operation or specification.
	 */
	public static void apply(ApiOperation operation, Map<String, Object> envs) {
		if (operation == null || operation.getExample() == null || envs == null) {
			return;
		}

		applyPathExample(operation, envs);
		applyParamExamples(operation, envs);
		applyHeaderExamples(operation, envs);
		applyBodyExample(operation, envs);
	}

	/**
	 * Applies path example to the environment map or operation model.
	 */
	private static void applyPathExample(ApiOperation operation, Map<String, Object> envs) {
		String template = operation.getPath();
		String example = operation.getExample().getPath();
		if (template == null || example == null) {
			return;
		}

		String key = EnvVarExtractor.singleKey(template);
		if (key != null && isRealExampleValue(example)) {
			envs.put(key, example);
			return;
		}

		applyPathSegmentExamples(template, normalizePath(example), envs);
	}

	/**
	 * Applies path segment examples to the environment map or operation model.
	 */
	private static void applyPathSegmentExamples(String templatePath, String examplePath, Map<String, Object> envs) {
		if (templatePath == null || examplePath == null) {
			return;
		}
		String[] templateParts = stripSlashes(templatePath).split("/");
		String[] exampleParts = stripSlashes(examplePath).split("/");
		if (templateParts.length != exampleParts.length) {
			return;
		}
		for (int i = 0; i < templateParts.length; i++) {
			String key = EnvVarExtractor.singleKey(templateParts[i]);
			if (key != null && isRealExampleValue(exampleParts[i])) {
				envs.put(key, exampleParts[i]);
			}
		}
	}

	/**
	 * Handles normalize path logic for this class.
	 */
	private static String normalizePath(String value) {
		if (value == null) {
			return null;
		}
		try {
			URI uri = URI.create(value);
			if (uri.getPath() != null && !uri.getPath().isBlank()) {
				return uri.getPath();
			}
		} catch (Exception ignored) {
			// Not a full URI; treat value as a path.
		}
		return value;
	}

	/**
	 * Handles strip slashes logic for this class.
	 */
	private static String stripSlashes(String value) {
		String result = value == null ? "" : value;
		while (result.startsWith("/")) {
			result = result.substring(1);
		}
		while (result.endsWith("/")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	/**
	 * Applies param examples to the environment map or operation model.
	 */
	private static void applyParamExamples(ApiOperation operation, Map<String, Object> envs) {
		if (operation.getQueryParams() == null || operation.getExample().getQueryParams() == null) {
			return;
		}
		for (ApiParam param : operation.getQueryParams()) {
			String key = EnvVarExtractor.singleKey(param.getValue());
			if (key == null) {
				continue;
			}
			operation.getExample().getQueryParams().stream()
					.filter(example -> param.getName() != null && param.getName().equals(example.getName()))
					.map(ApiParam::getValue).filter(ExampleEnvValueApplier::isRealExampleValue).findFirst()
					.ifPresent(value -> envs.put(key, value));
		}
	}

	/**
	 * Applies header examples to the environment map or operation model.
	 */
	private static void applyHeaderExamples(ApiOperation operation, Map<String, Object> envs) {
		if (operation.getHeaders() == null || operation.getExample().getHeaders() == null) {
			return;
		}
		for (ApiHeader header : operation.getHeaders()) {
			String key = EnvVarExtractor.singleKey(header.getValue());
			if (key == null) {
				continue;
			}
			operation.getExample().getHeaders().stream()
					.filter(example -> header.getName() != null && header.getName().equalsIgnoreCase(example.getName()))
					.map(ApiHeader::getValue).filter(ExampleEnvValueApplier::isRealExampleValue).findFirst()
					.ifPresent(value -> envs.put(key, value));
		}
	}

	/**
	 * Applies body example to the environment map or operation model.
	 */
	private static void applyBodyExample(ApiOperation operation, Map<String, Object> envs) {
		if (operation.getBody() == null || operation.getExample().getBody() == null) {
			return;
		}
		String template = operation.getBody().getContent();
		String example = operation.getExample().getBody().getContent();
		if (template == null || example == null) {
			return;
		}
		try {
			JsonNode templateNode = MAPPER.readTree(template);
			JsonNode exampleNode = MAPPER.readTree(example);
			scanJson(templateNode, exampleNode, envs);
		} catch (Exception ignored) {
			// Non-JSON examples are still allowed; env extraction just falls back to empty
			// values.
		}
	}

	/**
	 * Handles scan json logic for this class.
	 */
	private static void scanJson(JsonNode template, JsonNode example, Map<String, Object> envs) {
		if (template == null || example == null || example.isNull()) {
			return;
		}

		if (template.isTextual()) {
			String key = EnvVarExtractor.singleKey(template.asText());
			if (key != null && !example.isNull()) {
				Object value = jsonValue(example);
				if (isRealExampleValue(value)) {
					envs.put(key, value);
				}
			}
			return;
		}

		if (template.isObject() && example.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = template.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				scanJson(field.getValue(), example.get(field.getKey()), envs);
			}
		} else if (template.isArray() && example.isArray()) {
			int count = Math.min(template.size(), example.size());
			for (int i = 0; i < count; i++) {
				scanJson(template.get(i), example.get(i), envs);
			}
		}
	}

	/**
	 * Returns whether real example value is enabled or true.
	 */
	private static boolean isRealExampleValue(Object value) {
		if (value == null) {
			return false;
		}
		String text = String.valueOf(value);
		return !text.isBlank() && !EnvVarExtractor.containsKey(text);
	}

	/**
	 * Handles json value logic for this class.
	 */
	private static Object jsonValue(JsonNode node) {
		if (node.isTextual()) {
			return node.asText();
		}
		if (node.isNumber()) {
			return node.numberValue();
		}
		if (node.isBoolean()) {
			return node.asBoolean();
		}
		return node.toString();
	}
}
