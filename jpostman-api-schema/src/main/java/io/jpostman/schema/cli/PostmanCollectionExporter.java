package io.jpostman.schema.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.jpostman.schema.model.ApiAuth;
import io.jpostman.schema.model.ApiAuthLocation;
import io.jpostman.schema.model.ApiAuthType;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;
import io.jpostman.schema.model.ApiSpec;

/**
 * Exports the normalized ApiSpec model as a Postman Collection v2.1 document.
 */
public class PostmanCollectionExporter {
	private static final String POSTMAN_SCHEMA = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";
	private static final ObjectMapper MAPPER = new ObjectMapper();

	/**
	 * Converts an ApiSpec into a Postman Collection document.
	 */
	public Map<String, Object> export(ApiSpec spec, String collectionName) {
		Map<String, Object> root = new LinkedHashMap<>();
		Map<String, Object> info = new LinkedHashMap<>();
		info.put("name", valueOrDefault(collectionName,
				valueOrDefault(spec == null ? null : spec.getName(), "JPostman Collection")));
		info.put("schema", POSTMAN_SCHEMA);
		root.put("info", info);
		root.put("item", items(spec));
		return root;
	}

	private List<Map<String, Object>> items(ApiSpec spec) {
		List<Map<String, Object>> result = new ArrayList<>();
		Set<ApiOperation> rendered = new LinkedHashSet<>();
		if (spec == null) {
			return result;
		}
		for (ApiFolder folder : spec.getFolders()) {
			Map<String, Object> item = folderItem(spec, folder, rendered);
			if (item != null) {
				result.add(item);
			}
		}
		for (ApiOperation operation : spec.getOperations()) {
			if (operation != null && rendered.add(operation)) {
				result.add(operationItem(spec, operation));
			}
		}
		return result;
	}

	private Map<String, Object> folderItem(ApiSpec spec, ApiFolder folder, Set<ApiOperation> rendered) {
		if (folder == null) {
			return null;
		}
		List<Map<String, Object>> children = new ArrayList<>();
		for (ApiFolder child : folder.getFolders()) {
			Map<String, Object> childItem = folderItem(spec, child, rendered);
			if (childItem != null) {
				children.add(childItem);
			}
		}
		for (ApiOperation operation : folder.getOperations()) {
			if (operation != null && rendered.add(operation)) {
				children.add(operationItem(spec, operation));
			}
		}
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("name", valueOrDefault(folder.getName(), "Default"));
		if (!isBlank(folder.getDescription())) {
			item.put("description", folder.getDescription());
		}
		item.put("item", children);
		return item;
	}

	private Map<String, Object> operationItem(ApiSpec spec, ApiOperation operation) {
		Map<String, Object> item = new LinkedHashMap<>();
		item.put("name", valueOrDefault(operation.getName(), valueOrDefault(operation.getMethodName(), "Request")));
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("method", valueOrDefault(operation.getMethod(), "GET"));
		if (!isBlank(operation.getDescription())) {
			request.put("description", operation.getDescription());
		}
		List<Map<String, Object>> headers = headers(operation);
		if (!headers.isEmpty()) {
			request.put("header", headers);
		}
		Map<String, Object> auth = auth(operation.getAuth());
		if (auth != null) {
			request.put("auth", auth);
		}
		request.put("url", url(spec, operation));
		Map<String, Object> body = body(operation.getBody());
		if (body != null) {
			request.put("body", body);
		}
		item.put("request", request);
		return item;
	}

	private List<Map<String, Object>> headers(ApiOperation operation) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (operation.getHeaders() == null) {
			return result;
		}
		for (ApiHeader header : operation.getHeaders()) {
			if (header == null || isBlank(header.getName())) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("key", header.getName());
			item.put("value", header.getValue() == null ? "" : header.getValue());
			if (!isBlank(header.getDescription())) {
				item.put("description", header.getDescription());
			}
			item.put("type", "text");
			item.put("disabled", !header.isRequired());
			result.add(item);
		}
		return result;
	}

	private Map<String, Object> auth(ApiAuth auth) {
		if (auth == null || auth.getType() == null || auth.getType() == ApiAuthType.NONE) {
			return null;
		}
		switch (auth.getType()) {
		case BEARER:
		case OAUTH2:
			return keyedAuth("bearer", "token", valueOrDefault(auth.getValue(), "{{accessToken}}"));
		case BASIC:
			Map<String, Object> basic = new LinkedHashMap<>();
			basic.put("type", "basic");
			basic.put("basic", List.of(authValue("username", "{{username}}"), authValue("password", "{{password}}")));
			return basic;
		case API_KEY:
			Map<String, Object> apiKey = new LinkedHashMap<>();
			apiKey.put("type", "apikey");
			apiKey.put("apikey",
					List.of(authValue("key", valueOrDefault(auth.getName(), "api_key")),
							authValue("value", valueOrDefault(auth.getValue(), "{{apiKey}}")),
							authValue("in", auth.getLocation() == ApiAuthLocation.QUERY ? "query" : "header")));
			return apiKey;
		default:
			return null;
		}
	}

	private Map<String, Object> keyedAuth(String type, String key, String value) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("type", type);
		result.put(type, List.of(authValue(key, value)));
		return result;
	}

	private Map<String, Object> authValue(String key, String value) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("key", key);
		result.put("value", value == null ? "" : value);
		result.put("type", "string");
		return result;
	}

	private Map<String, Object> url(ApiSpec spec, ApiOperation operation) {
		String rawPath = valueOrDefault(operation.getPath(), "/");
		String raw = rawUrl(spec, operation, rawPath);
		Map<String, Object> url = new LinkedHashMap<>();
		url.put("raw", raw);
		url.put("host", List.of("{{BASE_URL}}"));
		List<String> segments = pathSegments(rawPath);
		if (!segments.isEmpty()) {
			url.put("path", segments);
		}
		List<Map<String, Object>> query = query(operation);
		if (!query.isEmpty()) {
			url.put("query", query);
		}
		return url;
	}

	private String rawUrl(ApiSpec spec, ApiOperation operation, String rawPath) {
		String raw = rawPath;
		if (isAbsoluteUrl(raw) || raw.startsWith("{{BASE_URL}}")) {
			// keep existing value
		} else if (raw.startsWith("/")) {
			raw = "{{BASE_URL}}" + raw;
		} else {
			raw = "{{BASE_URL}}/" + raw;
		}
		String query = queryString(operation);
		if (!query.isEmpty() && !raw.contains("?")) {
			raw += "?" + query;
		}
		return raw;
	}

	private List<Map<String, Object>> query(ApiOperation operation) {
		List<Map<String, Object>> result = new ArrayList<>();
		if (operation.getQueryParams() == null) {
			return result;
		}
		for (ApiParam param : operation.getQueryParams()) {
			if (param == null || isBlank(param.getName())) {
				continue;
			}
			Map<String, Object> item = new LinkedHashMap<>();
			item.put("key", param.getName());
			item.put("value", param.getValue() == null ? "" : param.getValue());
			if (!isBlank(param.getDescription())) {
				item.put("description", param.getDescription());
			}
			item.put("disabled", !param.isRequired());
			result.add(item);
		}
		return result;
	}

	private String queryString(ApiOperation operation) {
		if (operation.getQueryParams() == null || operation.getQueryParams().isEmpty()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for (ApiParam param : operation.getQueryParams()) {
			if (param == null || isBlank(param.getName())) {
				continue;
			}
			parts.add(param.getName() + "=" + (param.getValue() == null ? "" : param.getValue()));
		}
		return String.join("&", parts);
	}

	private List<String> pathSegments(String rawPath) {
		String path = rawPath == null ? "" : rawPath.trim();
		if (path.startsWith("{{BASE_URL}}")) {
			path = path.substring("{{BASE_URL}}".length());
		}
		if (isAbsoluteUrl(path)) {
			int protocolIndex = path.indexOf("://");
			int slashIndex = protocolIndex < 0 ? -1 : path.indexOf('/', protocolIndex + 3);
			path = slashIndex < 0 ? "" : path.substring(slashIndex);
		}
		int queryIndex = path.indexOf('?');
		if (queryIndex >= 0) {
			path = path.substring(0, queryIndex);
		}
		while (path.startsWith("/")) {
			path = path.substring(1);
		}
		while (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		if (path.isBlank()) {
			return new ArrayList<>();
		}
		List<String> segments = new ArrayList<>();
		for (String segment : path.split("/")) {
			if (!segment.isBlank()) {
				segments.add(segment);
			}
		}
		return segments;
	}

	private Map<String, Object> body(ApiBody body) {
		if (body == null || body.getType() == null || body.getType() == ApiBodyType.NONE
				|| isBlank(body.getContent())) {
			return null;
		}
		Map<String, Object> result = new LinkedHashMap<>();
		if (body.getType() == ApiBodyType.GRAPHQL) {
			result.put("mode", "graphql");
			Map<String, Object> graphql = new LinkedHashMap<>();
			graphql.put("query", body.getContent());
			graphql.put("variables", "");
			result.put("graphql", graphql);
			return result;
		}
		if (body.getType() == ApiBodyType.FORM_DATA || body.getType() == ApiBodyType.X_WWW_FORM_URLENCODED) {
			String key = body.getType() == ApiBodyType.FORM_DATA ? "formdata" : "urlencoded";
			result.put("mode", key);
			result.put(key, parsePostmanListBody(body.getContent()));
			return result;
		}
		result.put("mode", "raw");
		result.put("raw", body.getContent());
		if (body.getType() == ApiBodyType.JSON) {
			Map<String, Object> raw = new LinkedHashMap<>();
			raw.put("language", "json");
			Map<String, Object> options = new LinkedHashMap<>();
			options.put("raw", raw);
			result.put("options", options);
		}
		return result;
	}

	private Object parsePostmanListBody(String content) {
		if (isBlank(content)) {
			return new ArrayList<>();
		}
		try {
			return MAPPER.readValue(content, List.class);
		} catch (Exception e) {
			return content;
		}
	}

	private static boolean isAbsoluteUrl(String value) {
		return value != null && (value.startsWith("http://") || value.startsWith("https://"));
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private static String valueOrDefault(String value, String defaultValue) {
		return isBlank(value) ? defaultValue : value;
	}
}
