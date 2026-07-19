package io.jpostman.schema.export;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.jpostman.schema.model.ApiSpec;

/**
 * Exports normalized ApiSpec environment values as Postman Environment JSON.
 */
public class PostmanEnvironmentExporter {

	/**
	 * Converts the ApiSpec environment map into a Postman Environment document.
	 */
	public Map<String, Object> export(ApiSpec spec, String environmentName) {
		Map<String, Object> root = new LinkedHashMap<>();
		root.put("id", UUID.randomUUID().toString());
		root.put("name", valueOrDefault(environmentName,
				valueOrDefault(spec == null ? null : spec.getName(), "JPostman") + " Environment"));
		root.put("values", values(spec));
		root.put("_postman_variable_scope", "environment");
		root.put("_postman_exported_at", Instant.now().toString());
		root.put("_postman_exported_using", "JPostman API Schema");
		return root;
	}

	private List<Map<String, Object>> values(ApiSpec spec) {
		List<Map<String, Object>> values = new ArrayList<>();
		if (spec == null || spec.getEnvs() == null) {
			return values;
		}
		for (Map.Entry<String, Object> entry : spec.getEnvs().entrySet()) {
			if (entry.getKey() == null || entry.getKey().isBlank()) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("key", entry.getKey());
			item.put("value", entry.getValue() == null ? "" : entry.getValue());
			item.put("type", "default");
			item.put("enabled", true);
			values.add(item);
		}
		return values;
	}

	private static String valueOrDefault(String value, String defaultValue) {
		return value == null || value.isBlank() ? defaultValue : value;
	}
}
