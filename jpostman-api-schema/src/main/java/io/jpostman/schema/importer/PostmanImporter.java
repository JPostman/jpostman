package io.jpostman.schema.importer;

import java.net.URI;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jpostman.schema.model.ApiAuth;
import io.jpostman.schema.model.ApiAuthType;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;
import io.jpostman.schema.model.ApiProtocol;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;

/**
 * Imports Postman Collection documents into the common JPostman API schema
 * model.
 */
public class PostmanImporter implements ApiSpecImporter {
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Parses the supplied document content and returns a normalized API
	 * specification.
	 */
	@Override
	public ApiSpec importSpec(String content, ApiSpecParserOptions options) {
		try {
			JsonNode root = mapper.readTree(content);
			if (root == null || !root.isObject()) {
				throw new IllegalArgumentException("Postman Collection must be a JSON object");
			}
			if (!root.has("info") || !root.has("item")) {
				throw new IllegalArgumentException("Postman Collection must contain info and item sections");
			}
			ApiSpec spec = new ApiSpec();
			spec.setName(text(root.at("/info/name"), "Postman Collection"));
			spec.setOverrideUrl(options.getOverrideUrl() != null && options.getOverrideUrl());

			if (options.getBaseUrl() != null && !options.getBaseUrl().isBlank()) {
				spec.setBaseUrl(trimTrailingSlash(options.getBaseUrl()));
			}

			JsonNode items = root.get("item");
			if (items != null && items.isArray()) {
				for (JsonNode item : items) {
					importItem(spec, null, item);
				}
			}

			if (spec.getBaseUrl() == null) {
				spec.setBaseUrl(findFirstBaseUrl(spec));
			}
			return spec;
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid Postman Collection document", e);
		}
	}

	/**
	 * Imports item into the normalized model.
	 */
	private void importItem(ApiSpec spec, ApiFolder parent, JsonNode item) {
		if (item == null) {
			return;
		}

		JsonNode children = item.get("item");
		if (children != null && children.isArray()) {
			ApiFolder folder = new ApiFolder();
			folder.setName(text(item.get("name"), "Folder"));
			folder.setDescription(description(item.get("description")));

			if (parent == null) {
				spec.getFolders().add(folder);
			} else {
				parent.getFolders().add(folder);
			}

			for (JsonNode child : children) {
				importItem(spec, folder, child);
			}
			return;
		}

		JsonNode request = item.get("request");
		if (request == null || request.isMissingNode()) {
			return;
		}

		ApiOperation operation = new ApiOperation();
		operation.setProtocol(ApiProtocol.REST);
		operation.setFolder(parent == null ? "Default" : parent.getName());
		operation.setMethodName(text(item.get("name"), "request"));
		operation.setDescription(description(request.get("description")));
		operation.setMethod(text(request.get("method"), "GET").toUpperCase());
		operation.setAllowedMethods(List.of(operation.getMethod()));
		if (spec.getBaseUrl() == null) {
			spec.setBaseUrl(extractBaseUrl(request.get("url")));
		}
		operation.setPath(resolvePath(request.get("url")));
		operation.getQueryParams().addAll(resolveQueryParams(request.get("url")));
		operation.getHeaders().addAll(resolveHeaders(request.get("header")));
		operation.setBody(resolveBody(request.get("body")));
		operation.setAuth(resolveAuth(request.get("auth")));
		operation.setExample(resolveExample(item, request));

		if (parent == null) {
			spec.getOperations().add(operation);
		} else {
			parent.getOperations().add(operation);
		}
	}

	/**
	 * Resolves the query params.
	 */
	private List<ApiParam> resolveQueryParams(JsonNode url) {
		java.util.ArrayList<ApiParam> params = new java.util.ArrayList<>();
		JsonNode query = url == null ? null : url.get("query");
		if (query != null && query.isArray()) {
			for (JsonNode item : query) {
				ApiParam param = new ApiParam();
				param.setName(text(item.get("key"), null));
				param.setValue(text(item.get("value"), null));
				param.setDescription(description(item.get("description")));
				param.setRequired(!item.path("disabled").asBoolean(false));
				params.add(param);
			}
		}
		return params;
	}

	/**
	 * Resolves the headers.
	 */
	private List<ApiHeader> resolveHeaders(JsonNode headerNode) {
		java.util.ArrayList<ApiHeader> headers = new java.util.ArrayList<>();
		if (headerNode != null && headerNode.isArray()) {
			for (JsonNode item : headerNode) {
				if (item.path("disabled").asBoolean(false)) {
					continue;
				}
				ApiHeader header = new ApiHeader();
				header.setName(text(item.get("key"), null));
				header.setValue(text(item.get("value"), null));
				header.setDescription(description(item.get("description")));
				header.setRequired(true);
				headers.add(header);
			}
		}
		return headers;
	}

	/**
	 * Resolves the body.
	 */
	private ApiBody resolveBody(JsonNode bodyNode) {
		if (bodyNode == null || bodyNode.isNull()) {
			return null;
		}
		String mode = text(bodyNode.get("mode"), null);
		if (mode == null) {
			return null;
		}
		if ("raw".equals(mode)) {
			return new ApiBody(ApiBodyType.JSON, text(bodyNode.get("raw"), null));
		}
		if ("formdata".equals(mode)) {
			return new ApiBody(ApiBodyType.FORM_DATA,
					bodyNode.get("formdata") == null ? null : bodyNode.get("formdata").toString());
		}
		if ("urlencoded".equals(mode)) {
			return new ApiBody(ApiBodyType.X_WWW_FORM_URLENCODED,
					bodyNode.get("urlencoded") == null ? null : bodyNode.get("urlencoded").toString());
		}
		return new ApiBody(ApiBodyType.RAW, bodyNode.toString());
	}

	/**
	 * Resolves the auth.
	 */
	private ApiAuth resolveAuth(JsonNode authNode) {
		if (authNode == null || authNode.isNull()) {
			return null;
		}
		String type = text(authNode.get("type"), null);
		if (type == null || "noauth".equalsIgnoreCase(type)) {
			return null;
		}
		ApiAuth auth = new ApiAuth();
		switch (type.toLowerCase()) {
		case "bearer":
			auth.setType(ApiAuthType.BEARER);
			auth.setName("Authorization");
			auth.setValue(readAuthValue(authNode.get("bearer"), "token", "{{accessToken}}"));
			break;
		case "basic":
			auth.setType(ApiAuthType.BASIC);
			auth.setName("Authorization");
			auth.setValue(readAuthValue(authNode.get("basic"), "password", null));
			break;
		case "apikey":
			auth.setType(ApiAuthType.API_KEY);
			auth.setName(readAuthValue(authNode.get("apikey"), "key", "X-API-Key"));
			auth.setValue(readAuthValue(authNode.get("apikey"), "value", null));
			break;
		case "oauth2":
			auth.setType(ApiAuthType.OAUTH2);
			auth.setName("Authorization");
			auth.setValue(readAuthValue(authNode.get("oauth2"), "accessToken", "{{accessToken}}"));
			break;
		default:
			auth.setType(ApiAuthType.NONE);
			break;
		}
		return auth.getType() == ApiAuthType.NONE ? null : auth;
	}

	/**
	 * Reads the auth value.
	 */
	private String readAuthValue(JsonNode array, String keyName, String defaultValue) {
		if (array != null && array.isArray()) {
			for (JsonNode item : array) {
				if (keyName.equals(text(item.get("key"), null))) {
					return text(item.get("value"), defaultValue);
				}
			}
		}
		return defaultValue;
	}

	/**
	 * Resolves the example.
	 */
	private ApiExample resolveExample(JsonNode item, JsonNode request) {
		JsonNode responses = item == null ? null : item.get("response");
		if (responses == null || !responses.isArray() || responses.size() == 0) {
			return null;
		}

		JsonNode response = responses.get(0);
		JsonNode originalRequest = response.get("originalRequest");
		if (originalRequest == null || originalRequest.isNull()) {
			return null;
		}

		ApiExample example = new ApiExample();
		example.setName(text(response.get("name"), "Postman Example"));
		example.setPath(resolvePath(originalRequest.get("url")));
		example.getQueryParams().addAll(resolveQueryParams(originalRequest.get("url")));
		example.getHeaders().addAll(resolveHeaders(originalRequest.get("header")));
		example.setBody(resolveBody(originalRequest.get("body")));

		boolean hasExamples = example.getPath() != null || !example.getQueryParams().isEmpty()
				|| !example.getHeaders().isEmpty() || example.getBody() != null;
		return hasExamples ? example : null;
	}

	/**
	 * Resolves the path.
	 */
	private String resolvePath(JsonNode urlNode) {
		if (urlNode == null || urlNode.isNull()) {
			return null;
		}
		if (urlNode.isTextual()) {
			return pathFromRaw(urlNode.asText());
		}
		JsonNode path = urlNode.get("path");
		if (path != null && path.isArray()) {
			StringBuilder builder = new StringBuilder();
			for (JsonNode part : path) {
				builder.append('/').append(part.asText());
			}
			return builder.length() == 0 ? "/" : builder.toString();
		}
		return pathFromRaw(text(urlNode.get("raw"), null));
	}

	/**
	 * Handles path from raw logic for this class.
	 */
	private String pathFromRaw(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			URI uri = URI.create(raw.replace("{{", "__L__").replace("}}", "__R__"));
			return uri.getPath() == null || uri.getPath().isBlank() ? "/"
					: uri.getPath().replace("__L__", "{{").replace("__R__", "}}");
		} catch (Exception ignored) {
			int schemeIndex = raw.indexOf("://");
			if (schemeIndex >= 0) {
				int slashIndex = raw.indexOf('/', schemeIndex + 3);
				return slashIndex >= 0 ? raw.substring(slashIndex).split("\\?")[0] : "/";
			}
			return raw.split("\\?")[0];
		}
	}

	/**
	 * Finds the first base url.
	 */
	private String findFirstBaseUrl(ApiSpec spec) {
		return null;
	}

	/**
	 * Extracts the base url.
	 */
	private String extractBaseUrl(JsonNode urlNode) {
		if (urlNode == null || urlNode.isNull()) {
			return null;
		}
		String raw = urlNode.isTextual() ? urlNode.asText() : text(urlNode.get("raw"), null);
		if (raw != null) {
			try {
				URI uri = URI.create(raw.replace("{{", "__L__").replace("}}", "__R__"));
				if (uri.getScheme() != null && uri.getHost() != null) {
					int port = uri.getPort();
					String base = uri.getScheme() + "://" + uri.getHost() + (port > -1 ? ":" + port : "");
					return trimTrailingSlash(base.replace("__L__", "{{").replace("__R__", "}}"));
				}
			} catch (Exception ignored) {
				int schemeIndex = raw.indexOf("://");
				if (schemeIndex >= 0) {
					int slashIndex = raw.indexOf('/', schemeIndex + 3);
					return trimTrailingSlash(slashIndex >= 0 ? raw.substring(0, slashIndex) : raw);
				}
			}
		}
		String protocol = text(urlNode.get("protocol"), null);
		JsonNode host = urlNode.get("host");
		if (protocol != null && host != null && host.isArray()) {
			StringBuilder builder = new StringBuilder(protocol).append("://");
			for (int i = 0; i < host.size(); i++) {
				if (i > 0)
					builder.append('.');
				builder.append(host.get(i).asText());
			}
			return trimTrailingSlash(builder.toString());
		}
		return null;
	}

	/**
	 * Handles text logic for this class.
	 */
	private String text(JsonNode node, String defaultValue) {
		return node == null || node.isNull() ? defaultValue : node.asText(defaultValue);
	}

	/**
	 * Handles description logic for this class.
	 */
	private String description(JsonNode node) {
		if (node == null || node.isNull()) {
			return null;
		}
		if (node.isTextual()) {
			return node.asText();
		}
		return text(node.get("content"), null);
	}

	/**
	 * Trims the trailing slash.
	 */
	private String trimTrailingSlash(String value) {
		if (value == null)
			return null;
		while (value.endsWith("/") && value.length() > 1) {
			value = value.substring(0, value.length() - 1);
		}
		return value;
	}
}
