package io.jpostman.schema.importer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import graphql.language.Description;
import graphql.language.FieldDefinition;
import graphql.language.InputValueDefinition;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.ObjectTypeDefinition;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import io.jpostman.schema.model.ApiBody;
import io.jpostman.schema.model.ApiBodyType;
import io.jpostman.schema.model.ApiExample;
import io.jpostman.schema.model.ApiFolder;
import io.jpostman.schema.model.ApiHeader;
import io.jpostman.schema.model.ApiOperation;
import io.jpostman.schema.model.ApiProtocol;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParserOptions;

/**
 * Imports a GraphQL schema into the common JPostman API schema model.
 */
public class GraphQlImporter implements ApiSpecImporter {
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
		spec.setOverrideUrl(true); // GraphQL schema has no URL. UI should show checked + disabled.

		Map<String, ObjectTypeDefinition> objectTypes = new LinkedHashMap<>();
		for (ObjectTypeDefinition type : registry.getTypes(ObjectTypeDefinition.class)) {
			objectTypes.put(type.getName(), type);
		}
		importRootType(spec, objectTypes.get("Query"), "GraphQL Query", "QUERY");
		importRootType(spec, objectTypes.get("Mutation"), "GraphQL Mutation", "MUTATION");
		importRootType(spec, objectTypes.get("Subscription"), "GraphQL Subscription", "SUBSCRIPTION");

		return spec;
	}

	/**
	 * Imports root type into the normalized model.
	 */
	private void importRootType(ApiSpec spec, ObjectTypeDefinition root, String folderName, String operationType) {
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
			ApiBody body = new ApiBody(ApiBodyType.GRAPHQL, buildGraphQlBody(operationType, field));
			operation.setBody(body);
			operation.setExample(toExample(operation, body));
			folder.getOperations().add(operation);
		}
	}

	/**
	 * Builds a GraphQL request example from the generated body.
	 */
	private ApiExample toExample(ApiOperation operation, ApiBody body) {
		ApiExample example = new ApiExample();
		example.setName("GraphQL Example");
		example.setPath(operation.getPath());
		example.getHeaders().addAll(operation.getHeaders());
		example.setBody(body);
		return example;
	}

	/**
	 * Builds the graph ql body.
	 */
	private String buildGraphQlBody(String operationType, FieldDefinition field) {
		String opKeyword;
		if ("MUTATION".equals(operationType)) {
			opKeyword = "mutation";
		} else if ("SUBSCRIPTION".equals(operationType)) {
			opKeyword = "subscription";
		} else {
			opKeyword = "query";
		}

		String variablesDeclaration = field.getInputValueDefinitions().stream()
				.map(arg -> "$" + arg.getName() + ": " + typeName(arg.getType())).collect(Collectors.joining(", "));

		String arguments = field.getInputValueDefinitions().stream().map(arg -> arg.getName() + ": $" + arg.getName())
				.collect(Collectors.joining(", "));

		String variablesJson = field.getInputValueDefinitions().stream()
				.map(arg -> "    \"" + arg.getName() + "\": \"{{" + envName(arg) + "}}\"")
				.collect(Collectors.joining(",\n"));

		StringBuilder query = new StringBuilder();
		query.append(opKeyword).append(' ').append(field.getName());
		if (!variablesDeclaration.isBlank()) {
			query.append('(').append(variablesDeclaration).append(')');
		}
		query.append(" { ").append(field.getName());
		if (!arguments.isBlank()) {
			query.append('(').append(arguments).append(')');
		}
		query.append(" { __typename } }");

		return "{\n" + "  \"query\": \"" + escapeJson(query.toString()) + "\",\n" + "  \"variables\": {\n"
				+ variablesJson + "\n" + "  }\n" + "}";
	}

	/**
	 * Handles env name logic for this class.
	 */
	private String envName(InputValueDefinition arg) {
		if ("token".equalsIgnoreCase(arg.getName()) || arg.getName().toLowerCase().contains("token")) {
			return "accessToken";
		}
		return arg.getName();
	}

	/**
	 * Handles type name logic for this class.
	 */
	private String typeName(Type<?> type) {
		if (type instanceof NonNullType) {
			NonNullType nonNullType = (NonNullType) type;
			return typeName(nonNullType.getType()) + "!";
		}
		if (type instanceof ListType) {
			ListType listType = (ListType) type;
			return "[" + typeName(listType.getType()) + "]";
		}
		if (type instanceof TypeName) {
			TypeName typeName = (TypeName) type;
			return typeName.getName();
		}
		return String.valueOf(type);
	}

	/**
	 * Handles description logic for this class.
	 */
	private String description(Description description) {
		return description == null ? null : description.getContent();
	}

	/**
	 * Handles escape json logic for this class.
	 */
	private String escapeJson(String value) {
		return value == null ? null : value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
