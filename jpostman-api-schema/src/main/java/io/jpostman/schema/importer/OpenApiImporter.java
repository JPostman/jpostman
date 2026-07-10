package io.jpostman.schema.importer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.jpostman.schema.model.ApiAuth;
import io.jpostman.schema.model.ApiAuthLocation;
import io.jpostman.schema.model.ApiAuthType;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiParam;
import io.jpostman.schema.model.ApiProtocol;
import io.jpostman.schema.model.ApiResponse;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;

/**
 * Imports OpenAPI 3.x and Swagger 2.0 documents into the common JPostman API schema model.
 */
public class OpenApiImporter implements ApiSpecImporter {
	private final ObjectMapper mapper = new ObjectMapper();
	private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

	/**
	 * Parses the supplied document content and returns a normalized API specification.
	 */
	@Override
	public ApiSpec importSpec(String content, ApiSpecParserOptions options) {
		ParseOptions parseOptions = new ParseOptions();
		parseOptions.setResolve(true);
		parseOptions.setResolveFully(true);

		if (isSwagger2(content)) {
			return importSwagger2(content, options);
		}

		SwaggerParseResult result = new OpenAPIV3Parser().readContents(content, null, parseOptions);
		OpenAPI openAPI = result.getOpenAPI();
		if (openAPI == null) {
			throw new IllegalArgumentException("Invalid OpenAPI/Swagger document: " + result.getMessages());
		}

		ApiSpec spec = new ApiSpec();
		spec.setName(openAPI.getInfo() == null ? "OpenAPI" : openAPI.getInfo().getTitle());
		spec.setBaseUrl(resolveBaseUrl(openAPI, options));
		spec.setOverrideUrl(options.getOverrideUrl() != null && options.getOverrideUrl());

		Paths paths = openAPI.getPaths();
		if (paths == null) {
			throw new IllegalArgumentException("OpenAPI paths section is missing or invalid");
		}

		for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
			String path = entry.getKey();
			PathItem pathItem = entry.getValue();
			addOperation(spec, openAPI, path, "GET", pathItem.getGet());
			addOperation(spec, openAPI, path, "POST", pathItem.getPost());
			addOperation(spec, openAPI, path, "PUT", pathItem.getPut());
			addOperation(spec, openAPI, path, "PATCH", pathItem.getPatch());
			addOperation(spec, openAPI, path, "DELETE", pathItem.getDelete());
			addOperation(spec, openAPI, path, "HEAD", pathItem.getHead());
			addOperation(spec, openAPI, path, "OPTIONS", pathItem.getOptions());
		}

		applyRawOpenApiExamples(spec, content);

		return spec;
	}

	/**
	 * Returns whether swagger2 is enabled or true.
	 */
	private boolean isSwagger2(String content) {
		if (content == null) {
			return false;
		}
		String trimmed = content.trim();
		return trimmed.contains("swagger:") || trimmed.contains("\"swagger\"");
	}

	/**
	 * Imports swagger2 into the normalized model.
	 */
	private ApiSpec importSwagger2(String content, ApiSpecParserOptions options) {
		Map<String, Object> root;
		try {
			root = yamlMapper.readValue(content, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid Swagger 2.0 document", e);
		}

		ApiSpec spec = new ApiSpec();
		Map<String, Object> info = asMap(root.get("info"));
		spec.setName(stringValue(info.get("title"), "Swagger API"));
		spec.setBaseUrl(resolveSwagger2BaseUrl(root, options));
		spec.setOverrideUrl(options.getOverrideUrl() != null && options.getOverrideUrl());

		Object rawPaths = root.get("paths");
		if (!(rawPaths instanceof Map)) {
			throw new IllegalArgumentException("Swagger 2.0 paths section is missing or invalid");
		}
		Map<String, Object> paths = asMap(rawPaths);
		for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
			String path = pathEntry.getKey();
			Map<String, Object> pathItem = asMap(pathEntry.getValue());
			addSwagger2Operation(spec, root, path, "GET", pathItem.get("get"));
			addSwagger2Operation(spec, root, path, "POST", pathItem.get("post"));
			addSwagger2Operation(spec, root, path, "PUT", pathItem.get("put"));
			addSwagger2Operation(spec, root, path, "PATCH", pathItem.get("patch"));
			addSwagger2Operation(spec, root, path, "DELETE", pathItem.get("delete"));
			addSwagger2Operation(spec, root, path, "HEAD", pathItem.get("head"));
			addSwagger2Operation(spec, root, path, "OPTIONS", pathItem.get("options"));
		}
		return spec;
	}

	/**
	 * Resolves the swagger2 base url.
	 */
	private String resolveSwagger2BaseUrl(Map<String, Object> root, ApiSpecParserOptions options) {
		if (options.getBaseUrl() != null && !options.getBaseUrl().isBlank()) {
			return trimTrailingSlash(options.getBaseUrl());
		}
		String host = stringValue(root.get("host"), null);
		if (host == null || host.isBlank()) {
			return null;
		}
		List<Object> schemes = asList(root.get("schemes"));
		String scheme = schemes.isEmpty() ? "https" : stringValue(schemes.get(0), "https");
		String basePath = stringValue(root.get("basePath"), "");
		if (!basePath.isBlank() && !basePath.startsWith("/")) {
			basePath = "/" + basePath;
		}
		return trimTrailingSlash(scheme + "://" + host + basePath);
	}

	/**
	 * Adds swagger2 operation to the normalized model.
	 */
	private void addSwagger2Operation(ApiSpec spec, Map<String, Object> root, String path, String method,
			Object operationNode) {
		Map<String, Object> operation = asMap(operationNode);
		if (operation.isEmpty()) {
			return;
		}

		ApiOperation target = new ApiOperation();
		target.setProtocol(ApiProtocol.REST);
		target.setMethod(method);
		target.setAllowedMethods(List.of(method));
		String summary = stringValue(operation.get("summary"), null);
		target.setName(firstNonBlank(summary, stringValue(operation.get("operationId"), null), method + " " + path));
		target.setMethodName(
				firstNonBlank(stringValue(operation.get("operationId"), null), toMethodName(method, path)));

		List<Object> tags = asList(operation.get("tags"));
		String folderName = tags.isEmpty() ? "Default" : stringValue(tags.get(0), "Default");
		String envPrefix = envPrefix(folderName, path);
		target.setFolder(folderName);
		target.setPath(applySwagger2PathParameterEnv(spec, path, envPrefix, asList(operation.get("parameters"))));

		for (Object parameterNode : asList(operation.get("parameters"))) {
			Map<String, Object> parameter = asMap(parameterNode);
			String in = stringValue(parameter.get("in"), "");
			if ("query".equalsIgnoreCase(in)) {
				target.getQueryParams().add(toSwagger2Param(spec, envPrefix, parameter));
			} else if ("header".equalsIgnoreCase(in)) {
				target.getHeaders().add(toSwagger2Header(parameter));
			} else if ("body".equalsIgnoreCase(in)) {
				target.setBody(toSwagger2Body(root, parameter));
			}
		}

		target.setResponses(toSwagger2Responses(root, operation));
		target.setDescription(resolveOperationDescription(stringValue(operation.get("description"), null),
				firstResponseDescription(target.getResponses()), bodyDescription(target.getBody())));
		target.setAuth(toSwagger2Auth(root, operation));
		if (target.getBody() != null) {
			ApiExample example = new ApiExample();
			example.setName("Swagger Example");
			example.setPath(spec.getBaseUrl() == null ? path : spec.getBaseUrl() + path);
			example.setBody(target.getBody());
			target.setExample(example);
		}
		addToFolder(spec, folderName, target);
	}

	/**
	 * Converts source data into swagger2 param.
	 */
	private ApiParam toSwagger2Param(ApiSpec spec, String envPrefix, Map<String, Object> parameter) {
		ApiParam param = new ApiParam();
		String name = stringValue(parameter.get("name"), null);
		param.setName(name);
		param.setRequired(Boolean.TRUE.equals(parameter.get("required")));
		param.setDescription(stringValue(parameter.get("description"), null));
		param.setValue(parameterTemplateValue(spec, envPrefix, name, swagger2ParameterExampleValue(parameter)));
		return param;
	}

	/**
	 * Converts source data into swagger2 header.
	 */
	private ApiHeader toSwagger2Header(Map<String, Object> parameter) {
		ApiHeader header = new ApiHeader();
		header.setName(stringValue(parameter.get("name"), null));
		header.setRequired(Boolean.TRUE.equals(parameter.get("required")));
		header.setDescription(stringValue(parameter.get("description"), null));
		Object value = firstObject(parameter.get("example"), parameter.get("default"));
		header.setValue(value == null ? null : String.valueOf(value));
		return header;
	}

	/**
	 * Converts source data into swagger2 body.
	 */
	private ApiBody toSwagger2Body(Map<String, Object> root, Map<String, Object> parameter) {
		Object example = firstObject(parameter.get("example"), parameter.get("default"));
		if (example == null) {
			Map<String, Object> schema = resolveRawSchema(root, asMap(parameter.get("schema")));
			example = rawSchemaExample(root, schema);
		}
		ApiBody body = new ApiBody(
				example instanceof Map || example instanceof List ? ApiBodyType.JSON : ApiBodyType.RAW,
				stringify(example));
		body.setDescription(stringValue(parameter.get("description"), null));
		return body;
	}

	/**
	 * Converts source data into swagger2 responses.
	 */
	private List<ApiResponse> toSwagger2Responses(Map<String, Object> root, Map<String, Object> operation) {
		List<ApiResponse> result = new ArrayList<>();
		Map<String, Object> responses = asMap(operation.get("responses"));
		for (Map.Entry<String, Object> entry : responses.entrySet()) {
			Map<String, Object> source = asMap(entry.getValue());
			ApiResponse response = new ApiResponse();
			response.setCode(entry.getKey());
			response.setDescription(stringValue(source.get("description"), null));
			Map<String, Object> schema = resolveRawSchema(root, asMap(source.get("schema")));
			Object value = firstObject(source.get("example"), rawSchemaExample(root, schema));
			if (value != null) {
				ApiBody body = new ApiBody(value instanceof Map || value instanceof List ? ApiBodyType.JSON : ApiBodyType.RAW,
						stringify(value));
				body.setDescription(response.getDescription());
				response.setBody(body);
				response.setExample(body);
			}
			result.add(response);
		}
		return result;
	}

	/**
	 * Resolves a raw schema $ref when possible.
	 */
	private Map<String, Object> resolveRawSchema(Map<String, Object> root, Map<String, Object> schema) {
		String ref = stringValue(schema.get("$ref"), null);
		if (ref == null || !ref.startsWith("#/")) {
			return schema;
		}
		Object current = root;
		for (String part : ref.substring(2).split("/")) {
			current = asMap(current).get(part);
			if (current == null) {
				return schema;
			}
		}
		Map<String, Object> resolved = asMap(current);
		return resolved.isEmpty() ? schema : resolved;
	}

	/**
	 * Builds an example from raw OpenAPI/Swagger schema data.
	 */
	private Object rawSchemaExample(Map<String, Object> root, Map<String, Object> schema) {
		return rawSchemaExample(root, schema, new HashSet<>());
	}

	/**
	 * Builds an example from raw OpenAPI/Swagger schema data.
	 */
	@SuppressWarnings("unchecked")
	private Object rawSchemaExample(Map<String, Object> root, Map<String, Object> schema, Set<String> visitedRefs) {
		if (schema == null || schema.isEmpty()) {
			return null;
		}
		String ref = stringValue(schema.get("$ref"), null);
		if (ref != null && ref.startsWith("#/")) {
			if (!visitedRefs.add(ref)) {
				return null;
			}
			return rawSchemaExample(root, resolveRawSchema(root, schema), visitedRefs);
		}
		Object example = firstObject(schema.get("example"), schema.get("default"));
		if (example != null) {
			return example;
		}
		List<Object> enumValues = asList(schema.get("enum"));
		if (!enumValues.isEmpty()) {
			return enumValues.get(0);
		}
		Map<String, Object> merged = new LinkedHashMap<>();
		for (String key : List.of("allOf", "anyOf", "oneOf")) {
			for (Object item : asList(schema.get(key))) {
				Object value = rawSchemaExample(root, asMap(item), visitedRefs);
				if (value instanceof Map) {
					merged.putAll((Map<String, Object>) value);
				}
			}
		}
		Map<String, Object> properties = asMap(schema.get("properties"));
		for (Map.Entry<String, Object> entry : properties.entrySet()) {
			Object value = rawSchemaExample(root, asMap(entry.getValue()), visitedRefs);
			if (value != null) {
				merged.put(entry.getKey(), value);
			}
		}
		if (!merged.isEmpty()) {
			return merged;
		}
		if ("array".equals(stringValue(schema.get("type"), null))) {
			Object item = rawSchemaExample(root, asMap(schema.get("items")), visitedRefs);
			return item == null ? List.of() : List.of(item);
		}
		String type = stringValue(schema.get("type"), null);
		if ("integer".equals(type)) {
			return 0;
		}
		if ("number".equals(type)) {
			return 0.0;
		}
		if ("boolean".equals(type)) {
			return false;
		}
		if ("string".equals(type)) {
			return "string";
		}
		return null;
	}

	/**
	 * Converts source data into swagger2 auth.
	 */
	private ApiAuth toSwagger2Auth(Map<String, Object> root, Map<String, Object> operation) {
		List<Object> security = asList(operation.get("security"));
		if (security.isEmpty()) {
			security = asList(root.get("security"));
		}
		if (security.isEmpty()) {
			return null;
		}
		Map<String, Object> requirement = asMap(security.get(0));
		if (requirement.isEmpty()) {
			return null;
		}
		String schemeName = requirement.keySet().iterator().next();
		Map<String, Object> definitions = asMap(root.get("securityDefinitions"));
		Map<String, Object> scheme = asMap(definitions.get(schemeName));
		if (scheme.isEmpty()) {
			return null;
		}

		ApiAuth auth = new ApiAuth();
		String type = stringValue(scheme.get("type"), "");
		String name = stringValue(scheme.get("name"), "Authorization");
		String in = stringValue(scheme.get("in"), "header");
		auth.setName(name);
		auth.setValue("{{accessToken}}");
		if ("basic".equalsIgnoreCase(type)) {
			auth.setType(ApiAuthType.BASIC);
		} else if ("apiKey".equalsIgnoreCase(type)) {
			auth.setType("Authorization".equalsIgnoreCase(name) ? ApiAuthType.BEARER : ApiAuthType.API_KEY);
		} else if ("oauth2".equalsIgnoreCase(type)) {
			auth.setType(ApiAuthType.OAUTH2);
		}
		if ("query".equalsIgnoreCase(in)) {
			auth.setLocation(ApiAuthLocation.QUERY);
		}
		return auth;
	}

	/**
	 * Converts a value to a map when possible.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> asMap(Object value) {
		if (value instanceof Map) {
			return (Map<String, Object>) value;
		}
		return Map.of();
	}

	/**
	 * Converts a value to list when possible.
	 */
	@SuppressWarnings("unchecked")
	private List<Object> asList(Object value) {
		if (value instanceof List) {
			return (List<Object>) value;
		}
		return List.of();
	}

	/**
	 * Converts a value into a string representation used by the schema model.
	 */
	private String stringValue(Object value, String defaultValue) {
		return value == null ? defaultValue : String.valueOf(value);
	}

	/**
	 * Returns the first usable value from the supplied candidates.
	 */
	private Object firstObject(Object first, Object second) {
		return first != null ? first : second;
	}


	/**
	 * Applies OpenAPI path parameter placeholders and records example/default values.
	 */
	private String applyOpenApiPathParameterEnv(ApiSpec spec, String path, String envPrefix, List<Parameter> parameters) {
		String result = path;
		if (parameters == null || parameters.isEmpty()) {
			return result;
		}
		for (Parameter parameter : parameters) {
			if (!"path".equalsIgnoreCase(parameter.getIn()) || parameter.getName() == null) {
				continue;
			}
			String name = parameter.getName();
			Object value = parameterExampleValue(parameter);
			String key = envKey(envPrefix, name);
			if (containsEnvTokenForName(result, name)) {
				putEnvValue(spec, name, value);
			} else {
				result = replaceOpenApiPathToken(result, name, envToken(key));
				putEnvValue(spec, key, value);
			}
		}
		return result;
	}

	/**
	 * Applies Swagger 2 path parameter placeholders and records example/default values.
	 */
	private String applySwagger2PathParameterEnv(ApiSpec spec, String path, String envPrefix, List<Object> parameters) {
		String result = path;
		if (parameters == null || parameters.isEmpty()) {
			return result;
		}
		for (Object parameterNode : parameters) {
			Map<String, Object> parameter = asMap(parameterNode);
			if (!"path".equalsIgnoreCase(stringValue(parameter.get("in"), ""))) {
				continue;
			}
			String name = stringValue(parameter.get("name"), null);
			if (name == null) {
				continue;
			}
			Object value = swagger2ParameterExampleValue(parameter);
			String key = envKey(envPrefix, name);
			if (containsEnvTokenForName(result, name)) {
				putEnvValue(spec, name, value);
			} else {
				result = replaceOpenApiPathToken(result, name, envToken(key));
				putEnvValue(spec, key, value);
			}
		}
		return result;
	}

	/**
	 * Creates a parameter value like {{product_limit}} and stores its real value.
	 */
	private String parameterTemplateValue(ApiSpec spec, String envPrefix, String name, Object value) {
		if (name == null || name.isBlank()) {
			return value == null ? null : String.valueOf(value);
		}
		if (isSingleEnvToken(value)) {
			return String.valueOf(value);
		}
		String key = envKey(envPrefix, name);
		putEnvValue(spec, key, value);
		return envToken(key);
	}

	/**
	 * Returns the preferred example/default value for an OpenAPI parameter.
	 */
	private Object parameterExampleValue(Parameter parameter) {
		if (parameter == null) {
			return null;
		}
		Object value = firstNonNull(parameter.getExample(), firstOpenApiExampleValue(parameter.getExamples()));
		if (value == null) {
			value = schemaExample(parameter);
		}
		if (value == null) {
			value = defaultValueFromSchema(parameter.getSchema());
		}
		return value;
	}

	/**
	 * Returns the preferred example/default value for a Swagger 2 parameter.
	 */
	private Object swagger2ParameterExampleValue(Map<String, Object> parameter) {
		Object value = firstObject(parameter.get("example"), parameter.get("default"));
		if (value == null) {
			value = firstRawOpenApiExampleValue(asMap(parameter.get("examples")));
		}
		if (value != null) {
			return value;
		}

		Map<String, Object> schema = asMap(parameter.get("schema"));
		if (schema.isEmpty()) {
			schema = new LinkedHashMap<>();
			copyIfPresent(parameter, schema, "type");
			copyIfPresent(parameter, schema, "format");
			copyIfPresent(parameter, schema, "enum");
			copyIfPresent(parameter, schema, "items");
		}
		return rawSchemaExample(Map.of(), schema);
	}

	/**
	 * Copies one raw map value when present.
	 */
	private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
		if (source.containsKey(key)) {
			target.put(key, source.get(key));
		}
	}

	/**
	 * Returns a default value from schema type/enum when no explicit example exists.
	 */
	private Object defaultValueFromSchema(Schema<?> schema) {
		if (schema == null) {
			return null;
		}
		if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
			return schema.getEnum().get(0);
		}
		String type = schema.getType();
		if ("integer".equals(type)) {
			return 0;
		}
		if ("number".equals(type)) {
			return 0.0;
		}
		if ("boolean".equals(type)) {
			return false;
		}
		if ("string".equals(type)) {
			return "string";
		}
		return null;
	}

	/**
	 * Stores a real env value without overwriting an existing value.
	 */
	private void putEnvValue(ApiSpec spec, String key, Object value) {
		if (spec == null || key == null || key.isBlank()) {
			return;
		}
		Object stored = isSingleEnvToken(value) || value == null ? "" : value;
		spec.getEnvs().putIfAbsent(key, stored);
	}

	/**
	 * Builds a stable prefix from the tag/folder or first path segment.
	 */
	private String envPrefix(String folderName, String path) {
		String source = firstNonBlank(folderName, firstPathSegment(path), "api");
		String key = envKeyPart(source, true);
		return singularize(key);
	}

	/**
	 * Returns the first non-template path segment.
	 */
	private String firstPathSegment(String path) {
		if (path == null) {
			return null;
		}
		for (String part : path.split("/")) {
			String cleaned = part.trim();
			if (!cleaned.isBlank() && !cleaned.startsWith("{") && !cleaned.startsWith("{{")) {
				return cleaned;
			}
		}
		return null;
	}

	/**
	 * Builds a unique environment key for a parameter.
	 */
	private String envKey(String prefix, String name) {
		String safePrefix = firstNonBlank(prefix, "api");
		String safeName = envKeyPart(name, false);
		if (safeName.isBlank()) {
			return safePrefix;
		}
		return safePrefix + "_" + safeName;
	}

	/**
	 * Normalizes one environment-key part.
	 */
	private String envKeyPart(String value, boolean lowerCase) {
		if (value == null) {
			return "";
		}
		StringBuilder builder = new StringBuilder();
		boolean previousSeparator = false;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (Character.isLetterOrDigit(ch)) {
				builder.append(lowerCase ? Character.toLowerCase(ch) : ch);
				previousSeparator = false;
			} else if (!previousSeparator && builder.length() > 0) {
				builder.append('_');
				previousSeparator = true;
			}
		}
		while (builder.length() > 0 && builder.charAt(builder.length() - 1) == '_') {
			builder.deleteCharAt(builder.length() - 1);
		}
		return builder.toString();
	}

	/**
	 * Simple singular form for resource prefixes like Products -> product.
	 */
	private String singularize(String value) {
		if (value == null || value.length() <= 1) {
			return firstNonBlank(value, "api");
		}
		if (value.endsWith("ies") && value.length() > 3) {
			return value.substring(0, value.length() - 3) + "y";
		}
		if (value.endsWith("s") && !value.endsWith("ss")) {
			return value.substring(0, value.length() - 1);
		}
		return value;
	}

	/**
	 * Returns {{key}}.
	 */
	private String envToken(String key) {
		return "{{" + key + "}}";
	}

	/**
	 * Returns whether a value is exactly one {{KEY}} placeholder.
	 */
	private boolean isSingleEnvToken(Object value) {
		if (value == null) {
			return false;
		}
		return String.valueOf(value).trim().matches("\\{\\{\\s*[A-Za-z0-9_.-]+\\s*}}");
	}

	/**
	 * Returns whether a path already contains a placeholder for this parameter name.
	 */
	private boolean containsEnvTokenForName(String path, String name) {
		if (path == null || name == null) {
			return false;
		}
		return path.contains("{{" + name + "}}") || path.contains("{{ " + name + " }}");
	}

	/**
	 * Replaces {name} style OpenAPI template tokens.
	 */
	private String replaceOpenApiPathToken(String path, String name, String replacement) {
		if (path == null || name == null || replacement == null) {
			return path;
		}
		return path.replace("{" + name + "}", replacement);
	}

	/**
	 * Applies raw open api examples to the environment map or operation model.
	 */
	private void applyRawOpenApiExamples(ApiSpec spec, String content) {
		Map<String, Object> root;
		try {
			root = yamlMapper.readValue(content, new TypeReference<Map<String, Object>>() {
			});
		} catch (Exception ignored) {
			return;
		}

		Map<String, Object> paths = asMap(root.get("paths"));
		for (Map.Entry<String, Object> pathEntry : paths.entrySet()) {
			String path = pathEntry.getKey();
			Map<String, Object> pathItem = asMap(pathEntry.getValue());
			applyRawOpenApiOperationExamples(spec, root, path, "GET", pathItem.get("get"));
			applyRawOpenApiOperationExamples(spec, root, path, "POST", pathItem.get("post"));
			applyRawOpenApiOperationExamples(spec, root, path, "PUT", pathItem.get("put"));
			applyRawOpenApiOperationExamples(spec, root, path, "PATCH", pathItem.get("patch"));
			applyRawOpenApiOperationExamples(spec, root, path, "DELETE", pathItem.get("delete"));
			applyRawOpenApiOperationExamples(spec, root, path, "HEAD", pathItem.get("head"));
			applyRawOpenApiOperationExamples(spec, root, path, "OPTIONS", pathItem.get("options"));
		}
	}

	/**
	 * Applies raw open api operation examples to the environment map or operation model.
	 */
	private void applyRawOpenApiOperationExamples(ApiSpec spec, Map<String, Object> root, String path, String method, Object operationNode) {
		Map<String, Object> operationNodeMap = asMap(operationNode);
		if (operationNodeMap.isEmpty()) {
			return;
		}
		ApiOperation operation = findOperation(spec, path, method);
		if (operation == null) {
			return;
		}

		ApiExample example = operation.getExample();
		if (example == null) {
			example = new ApiExample();
			example.setName("OpenAPI Example");
			example.setPath(spec.getBaseUrl() == null ? path : spec.getBaseUrl() + path);
			operation.setExample(example);
		} else if (example.getPath() == null) {
			example.setPath(spec.getBaseUrl() == null ? path : spec.getBaseUrl() + path);
		}

		String examplePath = example.getPath();
		String envPrefix = envPrefix(operation.getFolder(), path);
		boolean hasRealExample = false;

		for (Object parameterNode : asList(operationNodeMap.get("parameters"))) {
			Map<String, Object> parameter = asMap(parameterNode);
			String in = stringValue(parameter.get("in"), "");
			String name = stringValue(parameter.get("name"), null);
			Object templateValue = firstObject(parameter.get("example"), schemaExample(parameter));
			Object realValue = firstRawOpenApiExampleValue(asMap(parameter.get("examples")));

			if (name == null || realValue == null) {
				continue;
			}

			String tokenKey = envKeyFromSingleToken(templateValue);
			String parameterEnvKey = firstNonBlank(tokenKey, envKey(envPrefix, name));
			putEnvExampleValue(spec, parameterEnvKey, realValue);

			if ("path".equalsIgnoreCase(in)) {
				if (templateValue != null && String.valueOf(templateValue).contains("{{")) {
					examplePath = examplePath.replace("{{" + name + "}}", String.valueOf(realValue));
					examplePath = examplePath.replace("{{" + parameterEnvKey + "}}", String.valueOf(realValue));
					hasRealExample = true;
				}
			} else if ("query".equalsIgnoreCase(in)) {
				ensureQueryParamTemplate(operation, name, templateValue);

				ApiParam param = new ApiParam();
				param.setName(name);
				param.setValue(String.valueOf(realValue));
				example.getQueryParams().add(param);

				hasRealExample = true;
			} else if ("header".equalsIgnoreCase(in)) {
				ensureHeaderTemplate(operation, name, templateValue);

				ApiHeader header = new ApiHeader();
				header.setName(name);
				header.setValue(String.valueOf(realValue));
				example.getHeaders().add(header);

				hasRealExample = true;
			}
		}

		example.setPath(examplePath);

		Map<String, Object> requestBody = asMap(operationNodeMap.get("requestBody"));
		Map<String, Object> requestMedia = firstMediaType(asMap(requestBody.get("content")));
		Object bodyTemplateValue = firstObject(requestMedia.get("example"),
				rawSchemaExample(root, asMap(requestMedia.get("schema"))));
		Object bodyRealValue = firstRawOpenApiExampleValue(asMap(requestMedia.get("examples")));
		if (bodyRealValue != null) {
			putEnvExampleValuesFromTemplate(spec, bodyTemplateValue, bodyRealValue);
		}

		ApiBody exampleBody = rawOpenApiRequestBodyExample(root, requestBody);
		ApiBody templateBody = rawOpenApiRequestBodyTemplate(root, requestBody);
		if (templateBody != null) {
			operation.setBody(templateBody);
		}
		if (exampleBody != null) {
			example.setBody(exampleBody);
			hasRealExample = true;
		}

		applyRawOpenApiResponses(root, operation, operationNodeMap);
		operation.setName(firstNonBlank(stringValue(operationNodeMap.get("summary"), null), operation.getName(),
				operation.getMethod() + " " + path));
		operation.setDescription(resolveOperationDescription(stringValue(operationNodeMap.get("description"), null),
				firstResponseDescription(operation.getResponses()), bodyDescription(operation.getBody())));

		if (!hasRealExample && example.getQueryParams().isEmpty() && example.getHeaders().isEmpty()
				&& example.getBody() == null) {
			operation.setExample(null);
		}
	}

	/**
	 * Applies raw OpenAPI response descriptions and examples.
	 */
	private void applyRawOpenApiResponses(Map<String, Object> root, ApiOperation operation,
			Map<String, Object> operationNodeMap) {
		Map<String, Object> responses = asMap(operationNodeMap.get("responses"));
		if (responses.isEmpty()) {
			return;
		}
		for (Map.Entry<String, Object> entry : responses.entrySet()) {
			Map<String, Object> source = asMap(entry.getValue());
			ApiResponse response = findOrCreateResponse(operation, entry.getKey());
			if (response.getDescription() == null || response.getDescription().isBlank()) {
				response.setDescription(stringValue(source.get("description"), null));
			}
			Map.Entry<String, Object> contentEntry = firstContentEntry(asMap(source.get("content")));
			if (contentEntry == null) {
				continue;
			}
			response.setContentType(contentEntry.getKey());
			Map<String, Object> media = asMap(contentEntry.getValue());
			Object value = firstRawOpenApiExampleValue(asMap(media.get("examples")));
			if (value == null) {
				value = firstObject(media.get("example"), rawSchemaExample(root, asMap(media.get("schema"))));
			}
			if (value != null) {
				ApiBody body = new ApiBody(resolveBodyType(contentEntry.getKey()), stringify(value));
				body.setDescription(response.getDescription());
				response.setBody(body);
				response.setExample(body);
			}
		}
	}

	/**
	 * Finds or creates a response by status code.
	 */
	private ApiResponse findOrCreateResponse(ApiOperation operation, String code) {
		for (ApiResponse response : operation.getResponses()) {
			if (code.equals(response.getCode())) {
				return response;
			}
		}
		ApiResponse response = new ApiResponse();
		response.setCode(code);
		operation.getResponses().add(response);
		return response;
	}

	/**
	 * Returns the first content map entry.
	 */
	private Map.Entry<String, Object> firstContentEntry(Map<String, Object> content) {
		if (content == null || content.isEmpty()) {
			return null;
		}
		return content.entrySet().iterator().next();
	}

	/**
	 * Finds the operation.
	 */
	private ApiOperation findOperation(ApiSpec spec, String path, String method) {
		for (ApiOperation operation : spec.getOperations()) {
			if (pathTemplatesMatch(path, operation.getPath()) && method.equalsIgnoreCase(operation.getMethod())) {
				return operation;
			}
		}
		for (ApiFolder folder : spec.getFolders()) {
			for (ApiOperation operation : folder.getOperations()) {
				if (pathTemplatesMatch(path, operation.getPath()) && method.equalsIgnoreCase(operation.getMethod())) {
					return operation;
				}
			}
		}
		return null;
	}



	/**
	 * Matches raw OpenAPI paths like /products/{id} to normalized paths like /products/{{product_id}}.
	 */
	private boolean pathTemplatesMatch(String left, String right) {
		if (left == null || right == null) {
			return false;
		}
		if (left.equals(right)) {
			return true;
		}
		return normalizeTemplatePath(left).equals(normalizeTemplatePath(right));
	}

	/**
	 * Normalizes OpenAPI/Postman template tokens for path matching.
	 */
	private String normalizeTemplatePath(String value) {
		return value.replaceAll("\\{\\{[^}]+}}", "{}").replaceAll("\\{[^}]+}", "{}");
	}

	/**
	 * Stores a real example env value, overriding placeholder/empty values.
	 */
	private void putEnvExampleValue(ApiSpec spec, String key, Object value) {
		if (spec == null || key == null || key.isBlank() || value == null) {
			return;
		}
		spec.getEnvs().put(key, value);
	}

	/**
	 * Returns key from a single {{key}} token.
	 */
	private String envKeyFromSingleToken(Object value) {
		if (!isSingleEnvToken(value)) {
			return null;
		}
		String text = String.valueOf(value).trim();
		return text.substring(2, text.length() - 2).trim();
	}

	/**
	 * Copies real example values into env vars based on template placeholders.
	 */
	@SuppressWarnings("unchecked")
	private void putEnvExampleValuesFromTemplate(ApiSpec spec, Object templateValue, Object realValue) {
		String tokenKey = envKeyFromSingleToken(templateValue);
		if (tokenKey != null) {
			putEnvExampleValue(spec, tokenKey, realValue);
			return;
		}

		if (templateValue instanceof Map<?, ?> && realValue instanceof Map<?, ?>) {
			Map<String, Object> templateMap = (Map<String, Object>) templateValue;
			Map<String, Object> realMap = (Map<String, Object>) realValue;
			for (Map.Entry<String, Object> entry : templateMap.entrySet()) {
				if (realMap.containsKey(entry.getKey())) {
					putEnvExampleValuesFromTemplate(spec, entry.getValue(), realMap.get(entry.getKey()));
				}
			}
			return;
		}

		if (templateValue instanceof List<?> && realValue instanceof List<?>) {
			List<?> templateList = (List<?>) templateValue;
			List<?> realList = (List<?>) realValue;
			int size = Math.min(templateList.size(), realList.size());
			for (int i = 0; i < size; i++) {
				putEnvExampleValuesFromTemplate(spec, templateList.get(i), realList.get(i));
			}
		}
	}

	/**
	 * Handles ensure query param template logic for this class.
	 */
	private void ensureQueryParamTemplate(ApiOperation operation, String name, Object templateValue) {
		if (templateValue == null) {
			return;
		}
		for (ApiParam param : operation.getQueryParams()) {
			if (name.equals(param.getName())) {
				if (param.getValue() == null || !param.getValue().contains("{{")) {
					param.setValue(String.valueOf(templateValue));
				}
				return;
			}
		}
		ApiParam param = new ApiParam();
		param.setName(name);
		param.setValue(String.valueOf(templateValue));
		operation.getQueryParams().add(param);
	}

	/**
	 * Handles ensure header template logic for this class.
	 */
	private void ensureHeaderTemplate(ApiOperation operation, String name, Object templateValue) {
		if (templateValue == null) {
			return;
		}
		for (ApiHeader header : operation.getHeaders()) {
			if (name.equalsIgnoreCase(header.getName())) {
				if (header.getValue() == null || !header.getValue().contains("{{")) {
					header.setValue(String.valueOf(templateValue));
				}
				return;
			}
		}
		ApiHeader header = new ApiHeader();
		header.setName(name);
		header.setValue(String.valueOf(templateValue));
		operation.getHeaders().add(header);
	}

	/**
	 * Handles raw open api request body template logic for this class.
	 */
	private ApiBody rawOpenApiRequestBodyTemplate(Map<String, Object> root, Map<String, Object> requestBody) {
		Map<String, Object> media = firstMediaType(asMap(requestBody.get("content")));
		if (media.isEmpty()) {
			return null;
		}
		Object value = firstObject(media.get("example"), firstRawOpenApiExampleValue(asMap(media.get("examples"))));
		if (value == null) {
			value = rawSchemaExample(root, asMap(media.get("schema")));
		}
		if (value == null) {
			return null;
		}
		return new ApiBody(ApiBodyType.JSON, stringify(value));
	}

	/**
	 * Handles raw open api request body example logic for this class.
	 */
	private ApiBody rawOpenApiRequestBodyExample(Map<String, Object> root, Map<String, Object> requestBody) {
		Map<String, Object> media = firstMediaType(asMap(requestBody.get("content")));
		if (media.isEmpty()) {
			return null;
		}
		Object value = firstRawOpenApiExampleValue(asMap(media.get("examples")));
		if (value == null) {
			value = firstObject(media.get("example"), rawSchemaExample(root, asMap(media.get("schema"))));
		}
		if (value == null) {
			return null;
		}
		return new ApiBody(ApiBodyType.JSON, stringify(value));
	}

	/**
	 * Returns the first media type object from an OpenAPI content map.
	 */
	private Map<String, Object> firstMediaType(Map<String, Object> content) {
		if (content.isEmpty()) {
			return Map.of();
		}
		Object media = content.values().iterator().next();
		return asMap(media);
	}

	/**
	 * Returns the first usable value from the supplied candidates.
	 */
	private Object firstRawOpenApiExampleValue(Map<String, Object> examples) {
		if (examples == null || examples.isEmpty()) {
			return null;
		}
		Object exampleNode = examples.values().iterator().next();
		Map<String, Object> example = asMap(exampleNode);
		if (!example.isEmpty() && example.containsKey("value")) {
			return example.get("value");
		}
		return exampleNode;
	}

	/**
	 * Handles schema example logic for this class.
	 */
	private Object schemaExample(Map<String, Object> parameter) {
		Map<String, Object> schema = asMap(parameter.get("schema"));
		return firstObject(schema.get("example"), schema.get("default"));
	}

	/**
	 * Resolves the base url.
	 */
	private String resolveBaseUrl(OpenAPI openAPI, ApiSpecParserOptions options) {
		if (options.getBaseUrl() != null && !options.getBaseUrl().isBlank()) {
			return trimTrailingSlash(options.getBaseUrl());
		}
		if (openAPI.getServers() != null && !openAPI.getServers().isEmpty()
				&& openAPI.getServers().get(0).getUrl() != null) {
			return trimTrailingSlash(openAPI.getServers().get(0).getUrl());
		}
		return null;
	}

	/**
	 * Adds operation to the normalized model.
	 */
	private void addOperation(ApiSpec spec, OpenAPI openAPI, String path, String method, Operation operation) {
		if (operation == null) {
			return;
		}

		ApiOperation target = new ApiOperation();
		target.setProtocol(ApiProtocol.REST);
		target.setMethod(method);
		target.setAllowedMethods(List.of(method));
		target.setName(firstNonBlank(operation.getSummary(), operation.getOperationId(), method + " " + path));
		target.setMethodName(firstNonBlank(operation.getOperationId(), toMethodName(method, path)));

		String folderName = operation.getTags() == null || operation.getTags().isEmpty() ? "Default"
				: operation.getTags().get(0);
		String envPrefix = envPrefix(folderName, path);
		target.setFolder(folderName);
		target.setPath(applyOpenApiPathParameterEnv(spec, path, envPrefix, operation.getParameters()));

		if (operation.getParameters() != null) {
			for (Parameter parameter : operation.getParameters()) {
				if ("query".equalsIgnoreCase(parameter.getIn())) {
					target.getQueryParams().add(toParam(spec, envPrefix, parameter));
				} else if ("header".equalsIgnoreCase(parameter.getIn())) {
					target.getHeaders().add(toHeader(parameter));
				}
			}
		}

		target.setBody(toBody(operation.getRequestBody()));
		target.setResponses(toResponses(operation.getResponses()));
		target.setDescription(resolveOperationDescription(operation.getDescription(),
				firstResponseDescription(target.getResponses()), bodyDescription(target.getBody())));
		target.setAuth(toAuth(openAPI, operation));
		target.setExample(toExample(operation, spec.getBaseUrl(), target.getPath(), envPrefix));

		addToFolder(spec, folderName, target);
	}

	/**
	 * Converts source data into param.
	 */
	private ApiParam toParam(ApiSpec spec, String envPrefix, Parameter parameter) {
		ApiParam param = new ApiParam();
		param.setName(parameter.getName());
		param.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
		param.setDescription(parameter.getDescription());
		param.setValue(parameterTemplateValue(spec, envPrefix, parameter.getName(), parameterExampleValue(parameter)));
		return param;
	}

	/**
	 * Converts source data into header.
	 */
	private ApiHeader toHeader(Parameter parameter) {
		ApiHeader header = new ApiHeader();
		header.setName(parameter.getName());
		header.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
		header.setDescription(parameter.getDescription());
		header.setValue(exampleValue(firstNonNull(parameter.getExample(), schemaExample(parameter))));
		return header;
	}

	/**
	 * Converts source data into response models.
	 */
	private List<ApiResponse> toResponses(ApiResponses responses) {
		List<ApiResponse> result = new ArrayList<>();
		if (responses == null || responses.isEmpty()) {
			return result;
		}
		for (Map.Entry<String, io.swagger.v3.oas.models.responses.ApiResponse> entry : responses.entrySet()) {
			io.swagger.v3.oas.models.responses.ApiResponse source = entry.getValue();
			ApiResponse response = new ApiResponse();
			response.setCode(entry.getKey());
			if (source != null) {
				response.setDescription(source.getDescription());
				if (source.getContent() != null && !source.getContent().isEmpty()) {
					Map.Entry<String, MediaType> media = source.getContent().entrySet().iterator().next();
					response.setContentType(media.getKey());
					response.setBody(bodyFromMedia(media.getKey(), media.getValue(), source.getDescription()));
					Object exampleValue = exampleFromMedia(media.getValue());
					if (exampleValue != null) {
						response.setExample(new ApiBody(resolveBodyType(media.getKey()), stringify(exampleValue)));
					}
				}
			}
			result.add(response);
		}
		return result;
	}

	/**
	 * Converts source data into body.
	 */
	private ApiBody toBody(RequestBody requestBody) {
		if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
			return null;
		}
		Map.Entry<String, MediaType> entry = requestBody.getContent().entrySet().iterator().next();
		ApiBody body = bodyFromMedia(entry.getKey(), entry.getValue(), requestBody.getDescription());
		return body;
	}

	/**
	 * Converts source data into example.
	 */
	private ApiExample toExample(Operation operation, String baseUrl, String path, String envPrefix) {
		ApiExample example = new ApiExample();
		example.setName("OpenAPI Example");
		String templateExamplePath = baseUrl == null ? path : baseUrl + path;
		String examplePath = templateExamplePath;

		if (operation.getParameters() != null) {
			for (Parameter parameter : operation.getParameters()) {
				Object exampleValue = firstOpenApiExampleValue(parameter.getExamples());
				if (exampleValue == null) {
					continue;
				}
				if ("path".equalsIgnoreCase(parameter.getIn())) {
					String envKey = envKey(envPrefix, parameter.getName());
					examplePath = examplePath.replace("{{" + envKey + "}}", String.valueOf(exampleValue));
					examplePath = examplePath.replace("{{" + parameter.getName() + "}}", String.valueOf(exampleValue));
				} else if ("query".equalsIgnoreCase(parameter.getIn())) {
					ApiParam param = new ApiParam();
					param.setName(parameter.getName());
					param.setValue(String.valueOf(exampleValue));
					example.getQueryParams().add(param);
				} else if ("header".equalsIgnoreCase(parameter.getIn())) {
					ApiHeader header = new ApiHeader();
					header.setName(parameter.getName());
					header.setValue(String.valueOf(exampleValue));
					example.getHeaders().add(header);
				}
			}
		}
		example.setPath(examplePath);

		ApiBody exampleBody = toRequestBodyExample(operation.getRequestBody());
		if (exampleBody != null) {
			example.setBody(exampleBody);
		}

		boolean hasExamples = !templateExamplePath.equals(examplePath) || !example.getQueryParams().isEmpty()
				|| !example.getHeaders().isEmpty() || example.getBody() != null;
		return hasExamples ? example : null;
	}

	/**
	 * Converts source data into request body example.
	 */
	private ApiBody toRequestBodyExample(RequestBody requestBody) {
		if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
			return null;
		}
		Map.Entry<String, MediaType> entry = requestBody.getContent().entrySet().iterator().next();
		Object exampleValue = exampleFromMedia(entry.getValue());
		if (exampleValue == null) {
			return null;
		}
		ApiBody body = new ApiBody(resolveBodyType(entry.getKey()), stringify(exampleValue));
		body.setDescription(requestBody.getDescription());
		return body;
	}

	/**
	 * Converts a media type into a body using explicit examples, schema examples,
	 * or generated examples from schema property examples.
	 */
	private ApiBody bodyFromMedia(String contentType, MediaType media, String description) {
		Object value = exampleFromMedia(media);
		ApiBody body = new ApiBody(resolveBodyType(contentType), stringify(value));
		body.setDescription(description);
		return body;
	}

	/**
	 * Returns the best available example from a media type.
	 */
	private Object exampleFromMedia(MediaType media) {
		if (media == null) {
			return null;
		}
		Object value = firstOpenApiExampleValue(media.getExamples());
		if (value == null) {
			value = media.getExample();
		}
		if (value == null) {
			value = schemaExample(media.getSchema());
		}
		return value;
	}

	/**
	 * Returns an example generated from schema example/default values.
	 */
	private Object schemaExample(Schema<?> schema) {
		return schemaExample(schema, new HashSet<>());
	}

	/**
	 * Returns an example generated from schema example/default values.
	 */
	private Object schemaExample(Schema<?> schema, Set<Schema<?>> visited) {
		if (schema == null || visited.contains(schema)) {
			return null;
		}
		visited.add(schema);

		if (schema.getExample() != null) {
			return schema.getExample();
		}
		if (schema.getDefault() != null) {
			return schema.getDefault();
		}
		if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
			return schema.getEnum().get(0);
		}

		if (schema instanceof ComposedSchema) {
			Map<String, Object> merged = new LinkedHashMap<>();
			ComposedSchema composed = (ComposedSchema) schema;
			mergeSchemaExamples(merged, composed.getAllOf(), visited);
			mergeSchemaExamples(merged, composed.getAnyOf(), visited);
			mergeSchemaExamples(merged, composed.getOneOf(), visited);
			if (!merged.isEmpty()) {
				return merged;
			}
		}

		if (schema instanceof ArraySchema) {
			Object item = schemaExample(((ArraySchema) schema).getItems(), visited);
			return item == null ? List.of() : List.of(item);
		}

		Map<String, Schema<?>> properties = schemaProperties(schema);
		if (properties != null && !properties.isEmpty()) {
			Map<String, Object> object = new LinkedHashMap<>();
			for (Map.Entry<String, Schema<?>> entry : properties.entrySet()) {
				Object value = schemaExample(entry.getValue(), visited);
				if (value != null) {
					object.put(entry.getKey(), value);
				}
			}
			if (!object.isEmpty()) {
				return object;
			}
		}

		String type = schema.getType();
		if ("integer".equals(type)) {
			return 0;
		}
		if ("number".equals(type)) {
			return 0.0;
		}
		if ("boolean".equals(type)) {
			return false;
		}
		if ("string".equals(type)) {
			return "string";
		}
		return null;
	}

	/**
	 * Merges generated examples from composed schemas.
	 */
	@SuppressWarnings("unchecked")
	private void mergeSchemaExamples(Map<String, Object> target, List<?> schemas, Set<Schema<?>> visited) {
		if (schemas == null) {
			return;
		}
		for (Object item : schemas) {
			if (!(item instanceof Schema<?>)) {
				continue;
			}
			Schema<?> schema = (Schema<?>) item;
			Object value = schemaExample(schema, visited);
			if (value instanceof Map<?, ?>) {
				target.putAll((Map<String, Object>) value);
			}
		}
	}
	
	/**
	 * Returns schema properties without exposing Swagger Parser's raw Schema type in this class.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Schema<?>> schemaProperties(Schema<?> schema) {
		return (Map<String, Schema<?>>) (Map<?, ?>) schema.getProperties();
	}

	/**
	 * Returns the first usable value from the supplied candidates.
	 */
	private Object firstOpenApiExampleValue(Map<String, Example> examples) {
		if (examples == null || examples.isEmpty()) {
			return null;
		}
		Example example = examples.values().iterator().next();
		return example == null ? null : example.getValue();
	}

	/**
	 * Handles schema example logic for this class.
	 */
	private Object schemaExample(Parameter parameter) {
		if (parameter.getSchema() == null) {
			return null;
		}
		return firstNonNull(parameter.getSchema().getExample(), parameter.getSchema().getDefault());
	}

	/**
	 * Returns the first usable value from the supplied candidates.
	 */
	private Object firstNonNull(Object first, Object second) {
		return first != null ? first : second;
	}

	/**
	 * Converts source data into auth.
	 */
	private ApiAuth toAuth(OpenAPI openAPI, Operation operation) {
		List<SecurityRequirement> requirements = operation.getSecurity();
		if ((requirements == null || requirements.isEmpty()) && openAPI.getSecurity() != null) {
			requirements = openAPI.getSecurity();
		}
		if (requirements == null || requirements.isEmpty() || openAPI.getComponents() == null
				|| openAPI.getComponents().getSecuritySchemes() == null) {
			return null;
		}

		String schemeName = requirements.get(0).keySet().stream().findFirst().orElse(null);
		if (schemeName == null) {
			return null;
		}
		SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(schemeName);
		if (scheme == null) {
			return null;
		}

		ApiAuth auth = new ApiAuth();
		auth.setName(scheme.getName() == null ? "Authorization" : scheme.getName());
		auth.setValue("{{accessToken}}");
		if (scheme.getType() == SecurityScheme.Type.HTTP && "bearer".equalsIgnoreCase(scheme.getScheme())) {
			auth.setType(ApiAuthType.BEARER);
		} else if (scheme.getType() == SecurityScheme.Type.HTTP && "basic".equalsIgnoreCase(scheme.getScheme())) {
			auth.setType(ApiAuthType.BASIC);
		} else if (scheme.getType() == SecurityScheme.Type.APIKEY) {
			auth.setType(ApiAuthType.API_KEY);
			auth.setName(scheme.getName());
			if ("query".equalsIgnoreCase(scheme.getIn() == null ? null : scheme.getIn().toString())) {
				auth.setLocation(ApiAuthLocation.QUERY);
			}
		} else if (scheme.getType() == SecurityScheme.Type.OAUTH2) {
			auth.setType(ApiAuthType.OAUTH2);
		}
		return auth;
	}

	/**
	 * Resolves operation description using UI-friendly precedence.
	 */
	private String resolveOperationDescription(String description, String responseDescription,
			String requestDescription) {
		return firstNonBlank(description, responseDescription, requestDescription);
	}

	/**
	 * Returns the first response description.
	 */
	private String firstResponseDescription(List<ApiResponse> responses) {
		if (responses == null) {
			return null;
		}
		for (ApiResponse response : responses) {
			String description = response == null ? null : response.getDescription();
			if (description != null && !description.isBlank()) {
				return description;
			}
		}
		return null;
	}

	/**
	 * Returns a request body description.
	 */
	private String bodyDescription(ApiBody body) {
		return body == null ? null : body.getDescription();
	}

	/**
	 * Resolves the body type.
	 */
	private ApiBodyType resolveBodyType(String contentType) {
		if (contentType == null) {
			return ApiBodyType.RAW;
		}
		String lower = contentType.toLowerCase();
		if (lower.contains("json"))
			return ApiBodyType.JSON;
		if (lower.contains("form-data"))
			return ApiBodyType.FORM_DATA;
		if (lower.contains("x-www-form-urlencoded"))
			return ApiBodyType.X_WWW_FORM_URLENCODED;
		return ApiBodyType.RAW;
	}

	/**
	 * Adds to folder to the normalized model.
	 */
	private void addToFolder(ApiSpec spec, String folderName, ApiOperation operation) {
		ApiFolder folder = spec.getFolders().stream().filter(item -> folderName.equals(item.getName())).findFirst()
				.orElseGet(() -> {
					ApiFolder created = new ApiFolder();
					created.setName(folderName);
					spec.getFolders().add(created);
					return created;
				});
		folder.getOperations().add(operation);
	}

	/**
	 * Converts a value into a pretty string representation used by the schema model.
	 */
	private String stringify(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String) {
			return prettyJsonString((String) value);
		}
		try {
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (Exception e) {
			return String.valueOf(value);
		}
	}

	/**
	 * Pretty prints JSON object/array strings, leaving non-JSON text untouched.
	 */
	private String prettyJsonString(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
			return value;
		}
		try {
			Object json = mapper.readValue(trimmed, Object.class);
			return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
		} catch (Exception e) {
			return value;
		}
	}

	/**
	 * Handles example value logic for this class.
	 */
	private String exampleValue(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	/**
	 * Returns the first usable value from the supplied candidates.
	 */
	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				return value;
			}
		}
		return null;
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

	/**
	 * Converts source data into method name.
	 */
	private String toMethodName(String method, String path) {
		String[] parts = path.replaceAll("[{}]", "").split("/");
		List<String> tokens = new ArrayList<>();
		tokens.add(method.toLowerCase());
		for (String part : parts) {
			if (!part.isBlank())
				tokens.add(part);
		}
		StringBuilder builder = new StringBuilder(tokens.get(0));
		for (int i = 1; i < tokens.size(); i++) {
			String token = tokens.get(i).replaceAll("[^A-Za-z0-9]", " ").trim();
			if (!token.isBlank()) {
				builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
			}
		}
		return builder.toString();
	}
}
