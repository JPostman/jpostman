package io.jpostman.schema.importer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import graphql.language.ArrayValue;
import graphql.language.BooleanValue;
import graphql.language.Description;
import graphql.language.EnumTypeDefinition;
import graphql.language.EnumValue;
import graphql.language.FieldDefinition;
import graphql.language.FloatValue;
import graphql.language.InputObjectTypeDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.IntValue;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.NullValue;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.StringValue;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiProtocol;
import io.jpostman.schema.model.ApiResponse;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;

/**
 * Imports a GraphQL schema into the common JPostman API schema model.
 */
public class GraphQlImporter implements ApiSpecImporter {
	private static final int MAX_SELECTION_DEPTH = 3;
	private static final ObjectMapper JSON = new ObjectMapper();

	/**
	 * Parses the supplied document content and returns a normalized API
	 * specification.
	 */
	@Override
	public ApiSpec importSpec(String content, ApiSpecParserOptions options) {
		TypeDefinitionRegistry registry = new SchemaParser().parse(content);
		ApiSpec spec = new ApiSpec();
		spec.setName("GraphQL API");
		spec.setBaseUrl(options.getBaseUrl());
		spec.setOverrideUrl(true); // GraphQL SDL has no transport URL.

		Map<String, ObjectTypeDefinition> objectTypes = new LinkedHashMap<>();
		for (ObjectTypeDefinition type : registry.getTypes(ObjectTypeDefinition.class)) {
			objectTypes.put(type.getName(), type);
		}
		Map<String, InputObjectTypeDefinition> inputTypes = new LinkedHashMap<>();
		for (InputObjectTypeDefinition type : registry.getTypes(InputObjectTypeDefinition.class)) {
			inputTypes.put(type.getName(), type);
		}
		Map<String, EnumTypeDefinition> enumTypes = new LinkedHashMap<>();
		for (EnumTypeDefinition type : registry.getTypes(EnumTypeDefinition.class)) {
			enumTypes.put(type.getName(), type);
		}

		SchemaContext context = new SchemaContext(objectTypes, inputTypes, enumTypes);
		importRootType(spec, objectTypes.get("Query"), "GraphQL Query", "QUERY", context);
		importRootType(spec, objectTypes.get("Mutation"), "GraphQL Mutation", "MUTATION", context);
		importRootType(spec, objectTypes.get("Subscription"), "GraphQL Subscription", "SUBSCRIPTION", context);
		return spec;
	}

	private void importRootType(ApiSpec spec, ObjectTypeDefinition root, String folderName, String operationType,
			SchemaContext context) {
		if (root == null) {
			return;
		}

		ApiFolder folder = new ApiFolder();
		folder.setName(folderName);
		folder.setDescription(description(root.getDescription()));
		spec.getFolders().add(folder);

		for (FieldDefinition field : root.getFieldDefinitions()) {
			ApiOperation operation = new ApiOperation();
			operation.setProtocol(ApiProtocol.GRAPHQL);
			operation.setFolder(folderName);
			operation.setName(field.getName());
			operation.setMethodName(field.getName());
			operation.setDescription(description(field.getDescription()));
			operation.setGraphQlOperationType(operationType);
			operation.setUrlResolved(spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank());
			operation.setPath(
					spec.getBaseUrl() != null && !spec.getBaseUrl().isBlank() ? spec.getBaseUrl() : "/graphql");

			if ("QUERY".equals(operationType)) {
				operation.setMethod("POST");
				operation.setAllowedMethods(List.of("POST", "GET"));
			} else if ("MUTATION".equals(operationType)) {
				operation.setMethod("POST");
				operation.setAllowedMethods(List.of("POST"));
			} else {
				operation.setMethod("SUBSCRIPTION");
				operation.setAllowedMethods(List.of("WEBSOCKET", "SSE"));
			}

			operation.getHeaders().add(new ApiHeader("Content-Type", "application/json", true));
			ApiBody body = new ApiBody(ApiBodyType.GRAPHQL, buildGraphQlBody(operationType, field, context));
			operation.setBody(body);
			operation.setExample(toExample(operation, body));
			operation.getResponses().add(buildResponse(field, context));
			folder.getOperations().add(operation);
		}
	}

	private ApiExample toExample(ApiOperation operation, ApiBody body) {
		ApiExample example = new ApiExample();
		example.setName("GraphQL Example");
		example.setPath(operation.getPath());
		example.getHeaders().addAll(operation.getHeaders());
		example.setBody(body);
		return example;
	}

	private ApiResponse buildResponse(FieldDefinition field, SchemaContext context) {
		ApiResponse response = new ApiResponse("200", "GraphQL response");
		response.setContentType("application/json");
		Map<String, Object> root = new LinkedHashMap<>();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put(field.getName(), responseExample(field.getType(), field.getName(), context, new LinkedHashSet<>(), 0));
		root.put("data", data);
		ApiBody example = new ApiBody(ApiBodyType.JSON, prettyJson(root));
		response.setBody(example);
		response.setExample(example);
		return response;
	}

	private String buildGraphQlBody(String operationType, FieldDefinition field, SchemaContext context) {
		String opKeyword = "MUTATION".equals(operationType) ? "mutation"
				: "SUBSCRIPTION".equals(operationType) ? "subscription" : "query";

		String variablesDeclaration = field.getInputValueDefinitions().stream()
				.map(arg -> "$" + arg.getName() + ": " + typeName(arg.getType())).collect(Collectors.joining(", "));
		String arguments = field.getInputValueDefinitions().stream().map(arg -> arg.getName() + ": $" + arg.getName())
				.collect(Collectors.joining(", "));

		StringBuilder query = new StringBuilder();
		query.append(opKeyword).append(' ').append(upperCamel(field.getName()));
		if (!variablesDeclaration.isBlank()) {
			query.append('(').append(variablesDeclaration).append(')');
		}
		query.append(" { ").append(field.getName());
		if (!arguments.isBlank()) {
			query.append('(').append(arguments).append(')');
		}
		String selection = selectionSet(field.getType(), context, new LinkedHashSet<>(), 0);
		if (!selection.isBlank()) {
			query.append(' ').append(selection);
		}
		query.append(" }");

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("query", query.toString());
		Map<String, Object> variables = new LinkedHashMap<>();
		for (InputValueDefinition arg : field.getInputValueDefinitions()) {
			variables.put(arg.getName(), inputExample(arg.getType(), arg, context, new LinkedHashSet<>(), 0));
		}
		request.put("variables", variables);
		return prettyJson(request);
	}

	private String selectionSet(Type<?> type, SchemaContext context, Set<String> visited, int depth) {
		String named = namedType(type);
		ObjectTypeDefinition object = context.objectTypes.get(named);
		if (object == null || depth >= MAX_SELECTION_DEPTH || visited.contains(named)) {
			return "";
		}
		Set<String> nextVisited = new LinkedHashSet<>(visited);
		nextVisited.add(named);
		List<String> selections = new ArrayList<>();
		for (FieldDefinition field : object.getFieldDefinitions()) {
			String nested = selectionSet(field.getType(), context, nextVisited, depth + 1);
			selections.add(nested.isBlank() ? field.getName() : field.getName() + " " + nested);
		}
		return selections.isEmpty() ? "{ __typename }" : "{ " + String.join(" ", selections) + " }";
	}

	private Object inputExample(Type<?> type, InputValueDefinition definition, SchemaContext context,
			Set<String> visited, int depth) {
		if (definition != null && definition.getDefaultValue() != null) {
			return literalValue(definition.getDefaultValue());
		}
		String named = namedType(type);
		if (isList(type)) {
			Type<?> itemType = unwrapList(type);
			return List.of(inputExample(itemType, definition == null ? null
					: InputValueDefinition.newInputValueDefinition().name(definition.getName()).type(itemType).build(),
					context, visited, depth + 1));
		}
		InputObjectTypeDefinition input = context.inputTypes.get(named);
		if (input != null && depth < MAX_SELECTION_DEPTH && !visited.contains(named)) {
			Set<String> nextVisited = new LinkedHashSet<>(visited);
			nextVisited.add(named);
			Map<String, Object> value = new LinkedHashMap<>();
			for (InputValueDefinition field : input.getInputValueDefinitions()) {
				value.put(field.getName(), inputExample(field.getType(), field, context, nextVisited, depth + 1));
			}
			return value;
		}
		EnumTypeDefinition enumType = context.enumTypes.get(named);
		if (enumType != null && !enumType.getEnumValueDefinitions().isEmpty()) {
			return enumType.getEnumValueDefinitions().get(0).getName();
		}
		String env = definition == null ? "value" : envName(definition);
		switch (named) {
		case "Int":
			return numericDefault(env, 1);
		case "Float":
			return 1.0d;
		case "Boolean":
			return Boolean.TRUE;
		case "ID":
		case "String":
		default:
			return "{{" + env + "}}";
		}
	}

	private Object responseExample(Type<?> type, String fieldName, SchemaContext context, Set<String> visited,
			int depth) {
		if (isList(type)) {
			return List.of(responseExample(unwrapList(type), fieldName, context, visited, depth + 1));
		}
		String named = namedType(type);
		ObjectTypeDefinition object = context.objectTypes.get(named);
		if (object != null && depth < MAX_SELECTION_DEPTH && !visited.contains(named)) {
			Set<String> nextVisited = new LinkedHashSet<>(visited);
			nextVisited.add(named);
			Map<String, Object> value = new LinkedHashMap<>();
			for (FieldDefinition field : object.getFieldDefinitions()) {
				value.put(field.getName(),
						responseExample(field.getType(), field.getName(), context, nextVisited, depth + 1));
			}
			return value;
		}
		EnumTypeDefinition enumType = context.enumTypes.get(named);
		if (enumType != null && !enumType.getEnumValueDefinitions().isEmpty()) {
			return enumType.getEnumValueDefinitions().get(0).getName();
		}
		switch (named) {
		case "Int":
			return 1;
		case "Float":
			return 1.0d;
		case "Boolean":
			return Boolean.TRUE;
		case "ID":
			return "1";
		case "String":
		default:
			return sampleString(fieldName);
		}
	}

	private Object literalValue(Value<?> value) {
		if (value instanceof StringValue) {
			return ((StringValue) value).getValue();
		}
		if (value instanceof IntValue) {
			return ((IntValue) value).getValue();
		}
		if (value instanceof FloatValue) {
			return ((FloatValue) value).getValue();
		}
		if (value instanceof BooleanValue) {
			return ((BooleanValue) value).isValue();
		}
		if (value instanceof EnumValue) {
			return ((EnumValue) value).getName();
		}
		if (value instanceof NullValue) {
			return null;
		}
		if (value instanceof ArrayValue) {
			return ((ArrayValue) value).getValues().stream().map(this::literalValue).collect(Collectors.toList());
		}
		if (value instanceof ObjectValue) {
			Map<String, Object> result = new LinkedHashMap<>();
			for (ObjectField field : ((ObjectValue) value).getObjectFields()) {
				result.put(field.getName(), literalValue(field.getValue()));
			}
			return result;
		}
		return String.valueOf(value);
	}

	private Object numericDefault(String name, int fallback) {
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("minute")) {
			return 30;
		}
		if (lower.contains("limit")) {
			return 10;
		}
		return fallback;
	}

	private String sampleString(String fieldName) {
		String lower = fieldName == null ? "value" : fieldName.toLowerCase(Locale.ROOT);
		if (lower.contains("token"))
			return "sample-token";
		if (lower.contains("email"))
			return "user@example.com";
		if (lower.equals("status"))
			return "READY";
		if (lower.endsWith("id") || lower.equals("id"))
			return "1";
		if (lower.contains("username"))
			return "emilys";
		return "string";
	}

	private String envName(InputValueDefinition arg) {
		String lower = arg.getName().toLowerCase(Locale.ROOT);
		if ("token".equals(lower) || lower.contains("accesstoken")) {
			return "accessToken";
		}
		return arg.getName();
	}

	private boolean isList(Type<?> type) {
		return unwrapNonNull(type) instanceof ListType;
	}

	private Type<?> unwrapList(Type<?> type) {
		Type<?> current = unwrapNonNull(type);
		return current instanceof ListType ? ((ListType) current).getType() : current;
	}

	private Type<?> unwrapNonNull(Type<?> type) {
		return type instanceof NonNullType ? ((NonNullType) type).getType() : type;
	}

	private String namedType(Type<?> type) {
		Type<?> current = type;
		while (current instanceof NonNullType || current instanceof ListType) {
			current = current instanceof NonNullType ? ((NonNullType) current).getType()
					: ((ListType) current).getType();
		}
		return current instanceof TypeName ? ((TypeName) current).getName() : String.valueOf(current);
	}

	private String typeName(Type<?> type) {
		if (type instanceof NonNullType) {
			return typeName(((NonNullType) type).getType()) + "!";
		}
		if (type instanceof ListType) {
			return "[" + typeName(((ListType) type).getType()) + "]";
		}
		if (type instanceof TypeName) {
			return ((TypeName) type).getName();
		}
		return String.valueOf(type);
	}

	private String description(Description description) {
		return description == null ? null : description.getContent();
	}

	private String upperCamel(String value) {
		return value == null || value.isEmpty() ? "Operation"
				: Character.toUpperCase(value.charAt(0)) + value.substring(1);
	}

	private String prettyJson(Object value) {
		try {
			return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Unable to create GraphQL example JSON", e);
		}
	}

	private static final class SchemaContext {
		private final Map<String, ObjectTypeDefinition> objectTypes;
		private final Map<String, InputObjectTypeDefinition> inputTypes;
		private final Map<String, EnumTypeDefinition> enumTypes;

		private SchemaContext(Map<String, ObjectTypeDefinition> objectTypes,
				Map<String, InputObjectTypeDefinition> inputTypes, Map<String, EnumTypeDefinition> enumTypes) {
			this.objectTypes = objectTypes;
			this.inputTypes = inputTypes;
			this.enumTypes = enumTypes;
		}
	}
}
