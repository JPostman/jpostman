package io.jpostman.schema.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jpostman.schema.env.ApiSpecEnvironmentUpdateRequest;
import io.jpostman.schema.env.ApiSpecEnvironmentUpdater;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParseException;
import io.jpostman.schema.parser.ApiSpecParser;
import io.jpostman.schema.parser.ApiSpecParserOptions;
import io.jpostman.schema.postman.PostmanCollectionExporter;
import io.jpostman.schema.postman.PostmanEnvironmentExporter;

/**
 * Command-line bridge used by RESTAGE Studio to parse API documents with the
 * Java ApiSpecParser and export normalized models, Postman Collections, and
 * Postman Environments.
 */
public final class ApiSchemaCli {
	private static final ObjectMapper MAPPER = new ObjectMapper()
			.setSerializationInclusion(JsonInclude.Include.NON_NULL);

	private ApiSchemaCli() {
	}

	/**
	 * CLI entry point.
	 */
	public static void main(String[] args) {
		try {
			String command = args.length == 0 ? "parse" : args[0];
			switch (command) {
			case "env-update":
				runEnvUpdate(EnvUpdateArgs.parse(args, 1));
				break;
			case "collection":
			case "postman-collection":
				runCollection(SpecOutputArgs.parse(args, 1, true));
				break;
			case "environment":
			case "postman-environment":
				runEnvironment(SpecOutputArgs.parse(args, 1, true));
				break;
			case "postman":
				runPostman(PostmanArgs.parse(args, 1));
				break;
			case "parse":
				runParse(SpecOutputArgs.parse(args, 1, false));
				break;
			default:
				runParse(SpecOutputArgs.parse(args, 0, false));
				break;
			}
		} catch (ApiSpecParseException e) {
			System.err.println(e.getUserMessage());
			System.exit(2);
		} catch (Exception e) {
			String message = e.getMessage() == null || e.getMessage().isBlank() ? e.getClass().getSimpleName()
					: e.getMessage();
			System.err.println(message);
			System.exit(1);
		}
	}

	private static void runParse(SpecOutputArgs cli) throws Exception {
		ApiSpec spec = parseSource(cli);
		writeJson(spec, cli.pretty, cli.output);
	}

	private static void runCollection(SpecOutputArgs cli) throws Exception {
		ApiSpec spec = loadSpec(cli);
		Object collection = new PostmanCollectionExporter().export(spec, cli.name);
		writeJson(collection, cli.pretty, cli.output);
	}

	private static void runEnvironment(SpecOutputArgs cli) throws Exception {
		ApiSpec spec = loadSpec(cli);
		Object environment = new PostmanEnvironmentExporter().export(spec, cli.name);
		writeJson(environment, cli.pretty, cli.output);
	}

	private static void runPostman(PostmanArgs cli) throws Exception {
		ApiSpec spec = loadSpec(cli);
		Object collection = new PostmanCollectionExporter().export(spec, cli.collectionName);
		Object environment = new PostmanEnvironmentExporter().export(spec, cli.environmentName);
		if ((cli.collectionOutput == null || cli.collectionOutput.isBlank())
				&& (cli.environmentOutput == null || cli.environmentOutput.isBlank())) {
			throw new IllegalArgumentException(
					"Use postman --collection-output <path> and/or --environment-output <path>");
		}
		if (cli.collectionOutput != null && !cli.collectionOutput.isBlank()) {
			writeJson(collection, cli.pretty, cli.collectionOutput);
		}
		if (cli.environmentOutput != null && !cli.environmentOutput.isBlank()) {
			writeJson(environment, cli.pretty, cli.environmentOutput);
		}
	}

	private static ApiSpec loadSpec(SpecInputArgs cli) throws Exception {
		if (cli.modelStdin || !isBlank(cli.modelFile)) {
			return readModel(cli);
		}
		return parseSource(cli);
	}

	private static ApiSpec readModel(SpecInputArgs cli) throws Exception {
		String modelJson = cli.modelStdin ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
				: Files.readString(Path.of(cli.modelFile), StandardCharsets.UTF_8);
		return MAPPER.readValue(modelJson, ApiSpec.class);
	}

	private static ApiSpec parseSource(SpecInputArgs cli) throws Exception {
		ApiSpecParserOptions options = ApiSpecParserOptions.defaults();
		options.setBaseUrl(cli.baseUrl);
		options.setOverrideUrl(cli.overrideUrl);

		String content = cli.stdin ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
				: Files.readString(Path.of(cli.file), StandardCharsets.UTF_8);

		return ApiSpecParser.parse(content, options);
	}

	private static void runEnvUpdate(EnvUpdateArgs cli) throws Exception {
		String updatesJson = Files.readString(Path.of(cli.updatesFile), StandardCharsets.UTF_8);
		ApiSpec spec = readModel(cli);
		ApiSpecEnvironmentUpdateRequest request = MAPPER.readValue(updatesJson, ApiSpecEnvironmentUpdateRequest.class);
		ApiSpec updated = new ApiSpecEnvironmentUpdater().update(spec, request);
		writeJson(updated, cli.pretty, cli.output);
	}

	private static DefaultPrettyPrinter newPrettyPrinter() {
		DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
		printer.indentArraysWith(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
		return printer;
	}

	private static void writeJson(Object value, boolean pretty, String output) throws Exception {
		if (output == null || output.isBlank()) {
			if (pretty) {
				MAPPER.writer(newPrettyPrinter()).writeValue(System.out, value);
			} else {
				MAPPER.writeValue(System.out, value);
			}
			return;
		}

		Path path = Path.of(output);
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		if (pretty) {
			MAPPER.writer(newPrettyPrinter()).writeValue(path.toFile(), value);
		} else {
			MAPPER.writeValue(path.toFile(), value);
		}
	}

	private static class SpecInputArgs {
		protected boolean stdin;
		protected boolean modelStdin;
		protected Boolean overrideUrl;
		protected String baseUrl;
		protected String file;
		protected String modelFile;

		protected boolean handleInputArg(String[] args, int[] index) {
			String arg = args[index[0]];
			switch (arg) {
			case "--stdin":
				stdin = true;
				return true;
			case "--file":
				file = nextValue(args, ++index[0], arg);
				return true;
			case "--model-stdin":
				modelStdin = true;
				return true;
			case "--model":
				modelFile = nextValue(args, ++index[0], arg);
				return true;
			case "--base-url":
				baseUrl = nextValue(args, ++index[0], arg);
				return true;
			case "--override-url":
				overrideUrl = Boolean.parseBoolean(nextValue(args, ++index[0], arg));
				return true;
			default:
				return false;
			}
		}

		protected void validateSpecInput(boolean allowModel) {
			if (allowModel && (modelStdin || !isBlank(modelFile))) {
				if (stdin || !isBlank(file)) {
					throw new IllegalArgumentException(
							"Use source input (--stdin/--file) or model input (--model-stdin/--model), not both.");
				}
				return;
			}
			if (!allowModel && (modelStdin || !isBlank(modelFile))) {
				throw new IllegalArgumentException("Model input is not supported for this command.");
			}
			validateSourcePresent();
		}

		private void validateSourcePresent() {
			if (!stdin && isBlank(file)) {
				throw new IllegalArgumentException("Use --stdin or --file <path>");
			}
		}
	}

	private static final class SpecOutputArgs extends SpecInputArgs {
		private boolean pretty;
		private String output;
		private String name;

		private static SpecOutputArgs parse(String[] args, int start, boolean allowModel) {
			SpecOutputArgs result = new SpecOutputArgs();
			for (int i = start; i < args.length; i++) {
				int[] index = new int[] { i };
				String arg = args[i];
				if (result.handleInputArg(args, index)) {
					i = index[0];
					continue;
				}
				switch (arg) {
				case "--pretty":
					result.pretty = true;
					break;
				case "--output":
					result.output = nextValue(args, ++i, arg);
					break;
				case "--name":
					result.name = nextValue(args, ++i, arg);
					break;
				default:
					throw new IllegalArgumentException("Unknown argument: " + arg);
				}
			}
			result.validateSpecInput(allowModel);
			return result;
		}
	}

	private static final class PostmanArgs extends SpecInputArgs {
		private boolean pretty;
		private String collectionOutput;
		private String environmentOutput;
		private String collectionName;
		private String environmentName;

		private static PostmanArgs parse(String[] args, int start) {
			PostmanArgs result = new PostmanArgs();
			for (int i = start; i < args.length; i++) {
				int[] index = new int[] { i };
				String arg = args[i];
				if (result.handleInputArg(args, index)) {
					i = index[0];
					continue;
				}
				switch (arg) {
				case "--pretty":
					result.pretty = true;
					break;
				case "--collection-output":
					result.collectionOutput = nextValue(args, ++i, arg);
					break;
				case "--environment-output":
					result.environmentOutput = nextValue(args, ++i, arg);
					break;
				case "--collection-name":
					result.collectionName = nextValue(args, ++i, arg);
					break;
				case "--environment-name":
					result.environmentName = nextValue(args, ++i, arg);
					break;
				default:
					throw new IllegalArgumentException("Unknown postman argument: " + arg);
				}
			}
			result.validateSpecInput(true);
			return result;
		}
	}

	private static final class EnvUpdateArgs extends SpecInputArgs {
		private boolean pretty;
		private String updatesFile;
		private String output;

		private static EnvUpdateArgs parse(String[] args, int start) {
			EnvUpdateArgs result = new EnvUpdateArgs();
			for (int i = start; i < args.length; i++) {
				int[] index = new int[] { i };
				String arg = args[i];
				if (result.handleInputArg(args, index)) {
					i = index[0];
					continue;
				}
				switch (arg) {
				case "--pretty":
					result.pretty = true;
					break;
				case "--updates":
					result.updatesFile = nextValue(args, ++i, arg);
					break;
				case "--output":
					result.output = nextValue(args, ++i, arg);
					break;
				default:
					throw new IllegalArgumentException("Unknown env-update argument: " + arg);
				}
			}

			if (!result.modelStdin && isBlank(result.modelFile)) {
				throw new IllegalArgumentException("Use env-update --model <api-spec.json> or --model-stdin");
			}
			if (result.updatesFile == null || result.updatesFile.isBlank()) {
				throw new IllegalArgumentException("Use env-update --updates <env-updates.json>");
			}
			if (result.stdin || !isBlank(result.file)) {
				throw new IllegalArgumentException(
						"env-update accepts model input only. Use --model or --model-stdin.");
			}
			return result;
		}
	}

	private static String nextValue(String[] args, int index, String flag) {
		if (index >= args.length) {
			throw new IllegalArgumentException("Missing value for " + flag);
		}
		return args[index];
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
