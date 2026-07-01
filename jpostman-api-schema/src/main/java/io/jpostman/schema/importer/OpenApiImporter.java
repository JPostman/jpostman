package io.jpostman.schema.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
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
		target.setPath(path);
		target.setMethodName(
				firstNonBlank(stringValue(operation.get("operationId"), null), toMethodName(method, path)));
		target.setDescription(firstNonBlank(stringValue(operation.get("description"), null),
				stringValue(operation.get("summary"), null)));

		List<Object> tags = asList(operation.get("tags"));
		String folderName = tags.isEmpty() ? "Default" : stringValue(tags.get(0), "Default");
		target.setFolder(folderName);

		for (Object parameterNode : asList(operation.get("parameters"))) {
			Map<String, Object> parameter = asMap(parameterNode);
			String in = stringValue(parameter.get("in"), "");
			if ("query".equalsIgnoreCase(in)) {
				target.getQueryParams().add(toSwagger2Param(parameter));
			} else if ("header".equalsIgnoreCase(in)) {
				target.getHeaders().add(toSwagger2Header(parameter));
			} else if ("body".equalsIgnoreCase(in)) {
				target.setBody(toSwagger2Body(parameter));
			}
		}

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
	private ApiParam toSwagger2Param(Map<String, Object> parameter) {
		ApiParam param = new ApiParam();
		param.setName(stringValue(parameter.get("name"), null));
		param.setRequired(Boolean.TRUE.equals(parameter.get("required")));
		param.setDescription(stringValue(parameter.get("description"), null));
		Object value = firstObject(parameter.get("example"), parameter.get("default"));
		param.setValue(value == null ? null : String.valueOf(value));
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
	private ApiBody toSwagger2Body(Map<String, Object> parameter) {
		Object example = firstObject(parameter.get("example"), parameter.get("default"));
		if (example == null) {
			Map<String, Object> schema = asMap(parameter.get("schema"));
			example = firstObject(schema.get("example"), schema.get("default"));
		}
		ApiBody body = new ApiBody(
				example instanceof Map || example instanceof List ? ApiBodyType.JSON : ApiBodyType.RAW,
				stringify(example));
		body.setDescription(stringValue(parameter.get("description"), null));
		return body;
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
			applyRawOpenApiOperationExamples(spec, path, "GET", pathItem.get("get"));
			applyRawOpenApiOperationExamples(spec, path, "POST", pathItem.get("post"));
			applyRawOpenApiOperationExamples(spec, path, "PUT", pathItem.get("put"));
			applyRawOpenApiOperationExamples(spec, path, "PATCH", pathItem.get("patch"));
			applyRawOpenApiOperationExamples(spec, path, "DELETE", pathItem.get("delete"));
			applyRawOpenApiOperationExamples(spec, path, "HEAD", pathItem.get("head"));
			applyRawOpenApiOperationExamples(spec, path, "OPTIONS", pathItem.get("options"));
		}
	}

	/**
	 * Applies raw open api operation examples to the environment map or operation model.
	 */
	private void applyRawOpenApiOperationExamples(ApiSpec spec, String path, String method, Object operationNode) {
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

			if ("path".equalsIgnoreCase(in)) {
				if (templateValue != null && String.valueOf(templateValue).contains("{{")) {
					examplePath = examplePath.replace("{{" + name + "}}", String.valueOf(realValue));
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
		ApiBody exampleBody = rawOpenApiRequestBodyExample(requestBody);
		ApiBody templateBody = rawOpenApiRequestBodyTemplate(requestBody);
		if (templateBody != null) {
			operation.setBody(templateBody);
		}
		if (exampleBody != null) {
			example.setBody(exampleBody);
			hasRealExample = true;
		}

		if (!hasRealExample && example.getQueryParams().isEmpty() && example.getHeaders().isEmpty()
				&& example.getBody() == null) {
			operation.setExample(null);
		}
	}

	/**
	 * Finds the operation.
	 */
	private ApiOperation findOperation(ApiSpec spec, String path, String method) {
		for (ApiOperation operation : spec.getOperations()) {
			if (path.equals(operation.getPath()) && method.equalsIgnoreCase(operation.getMethod())) {
				return operation;
			}
		}
		for (ApiFolder folder : spec.getFolders()) {
			for (ApiOperation operation : folder.getOperations()) {
				if (path.equals(operation.getPath()) && method.equalsIgnoreCase(operation.getMethod())) {
					return operation;
				}
			}
		}
		return null;
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
	private ApiBody rawOpenApiRequestBodyTemplate(Map<String, Object> requestBody) {
		Map<String, Object> media = firstMediaType(asMap(requestBody.get("content")));
		if (media.isEmpty()) {
			return null;
		}
		Object value = media.get("example");
		if (value == null) {
			Map<String, Object> schema = asMap(media.get("schema"));
			value = firstObject(schema.get("example"), schema.get("default"));
		}
		if (value == null) {
			return null;
		}
		return new ApiBody(ApiBodyType.JSON, stringify(value));
	}

	/**
	 * Handles raw open api request body example logic for this class.
	 */
	private ApiBody rawOpenApiRequestBodyExample(Map<String, Object> requestBody) {
		Map<String, Object> media = firstMediaType(asMap(requestBody.get("content")));
		if (media.isEmpty()) {
			return null;
		}
		Object value = firstRawOpenApiExampleValue(asMap(media.get("examples")));
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
		return schema.get("example");
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
		target.setPath(path);
		target.setMethodName(firstNonBlank(operation.getOperationId(), toMethodName(method, path)));
		target.setDescription(firstNonBlank(operation.getDescription(), operation.getSummary()));

		String folderName = operation.getTags() == null || operation.getTags().isEmpty() ? "Default"
				: operation.getTags().get(0);
		target.setFolder(folderName);

		if (operation.getParameters() != null) {
			for (Parameter parameter : operation.getParameters()) {
				if ("query".equalsIgnoreCase(parameter.getIn())) {
					target.getQueryParams().add(toParam(parameter));
				} else if ("header".equalsIgnoreCase(parameter.getIn())) {
					target.getHeaders().add(toHeader(parameter));
				}
			}
		}

		target.setBody(toBody(operation.getRequestBody()));
		target.setAuth(toAuth(openAPI, operation));
		target.setExample(toExample(operation, spec.getBaseUrl(), path));

		addToFolder(spec, folderName, target);
	}

	/**
	 * Converts source data into param.
	 */
	private ApiParam toParam(Parameter parameter) {
		ApiParam param = new ApiParam();
		param.setName(parameter.getName());
		param.setRequired(Boolean.TRUE.equals(parameter.getRequired()));
		param.setDescription(parameter.getDescription());
		param.setValue(exampleValue(firstNonNull(parameter.getExample(), schemaExample(parameter))));
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
	 * Converts source data into body.
	 */
	private ApiBody toBody(RequestBody requestBody) {
		if (requestBody == null || requestBody.getContent() == null || requestBody.getContent().isEmpty()) {
			return null;
		}
		Map.Entry<String, MediaType> entry = requestBody.getContent().entrySet().iterator().next();
		Object templateExample = entry.getValue().getExample();
		if (templateExample == null && entry.getValue().getSchema() != null) {
			templateExample = entry.getValue().getSchema().getExample();
		}
		if (templateExample == null && entry.getValue().getExamples() != null
				&& !entry.getValue().getExamples().isEmpty()) {
			Example exampleObject = entry.getValue().getExamples().values().iterator().next();
			templateExample = exampleObject.getValue();
		}
		ApiBody body = new ApiBody(resolveBodyType(entry.getKey()), stringify(templateExample));
		body.setDescription(requestBody.getDescription());
		return body;
	}

	/**
	 * Converts source data into example.
	 */
	private ApiExample toExample(Operation operation, String baseUrl, String path) {
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
		Object exampleValue = firstOpenApiExampleValue(entry.getValue().getExamples());
		if (exampleValue == null) {
			return null;
		}
		ApiBody body = new ApiBody(resolveBodyType(entry.getKey()), stringify(exampleValue));
		body.setDescription(requestBody.getDescription());
		return body;
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
		return parameter.getSchema() == null ? null : parameter.getSchema().getExample();
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
	 * Converts a value into a string representation used by the schema model.
	 */
	private String stringify(Object value) {
		if (value == null)
			return null;
		if (value instanceof String)
			return (String) value;
		try {
			return mapper.writeValueAsString(value);
		} catch (Exception e) {
			return String.valueOf(value);
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
	private String firstNonBlank(String first, String second) {
		return first != null && !first.isBlank() ? first : second;
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
