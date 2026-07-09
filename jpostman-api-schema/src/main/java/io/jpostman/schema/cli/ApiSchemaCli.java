package io.jpostman.schema.cli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jpostman.schema.env.ApiSpecEnvironmentUpdateRequest;
import io.jpostman.schema.env.ApiSpecEnvironmentUpdater;
import io.jpostman.schema.model.ApiSpec;
import io.jpostman.schema.parser.ApiSpecParseException;
import io.jpostman.schema.parser.ApiSpecParser;
import io.jpostman.schema.parser.ApiSpecParserOptions;

/**
 * Command-line bridge used by RESTAGE Studio to parse and update API documents
 * with the Java ApiSpecParser instead of duplicating parser logic in the VS
 * Code webview.
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
			if (args.length > 0 && "env-update".equals(args[0])) {
				runEnvUpdate(EnvUpdateArgs.parse(args, 1));
			} else {
				runParse(ParseArgs.parse(args, args.length > 0 && "parse".equals(args[0]) ? 1 : 0));
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

	private static void runParse(ParseArgs cli) throws Exception {
		ApiSpecParserOptions options = ApiSpecParserOptions.defaults();
		options.setBaseUrl(cli.baseUrl);
		options.setOverrideUrl(cli.overrideUrl);

		String content = cli.stdin ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
				: Files.readString(Path.of(cli.file), StandardCharsets.UTF_8);

		ApiSpec spec = ApiSpecParser.parse(content, options);
		writeSpec(spec, cli.pretty);
	}

	private static void runEnvUpdate(EnvUpdateArgs cli) throws Exception {
		String modelJson = cli.modelStdin ? new String(System.in.readAllBytes(), StandardCharsets.UTF_8)
				: Files.readString(Path.of(cli.modelFile), StandardCharsets.UTF_8);
		String updatesJson = Files.readString(Path.of(cli.updatesFile), StandardCharsets.UTF_8);

		ApiSpec spec = MAPPER.readValue(modelJson, ApiSpec.class);
		ApiSpecEnvironmentUpdateRequest request = MAPPER.readValue(updatesJson, ApiSpecEnvironmentUpdateRequest.class);
		ApiSpec updated = new ApiSpecEnvironmentUpdater().update(spec, request);
		writeSpec(updated, cli.pretty);
	}

	private static void writeSpec(ApiSpec spec, boolean pretty) throws Exception {
		if (pretty) {
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(System.out, spec);
		} else {
			MAPPER.writeValue(System.out, spec);
		}
	}

	private static final class ParseArgs {
		private boolean stdin;
		private boolean pretty;
		private Boolean overrideUrl;
		private String baseUrl;
		private String file;

		private static ParseArgs parse(String[] args, int start) {
			ParseArgs result = new ParseArgs();
			for (int i = start; i < args.length; i++) {
				String arg = args[i];
				switch (arg) {
				case "--stdin":
					result.stdin = true;
					break;
				case "--pretty":
					result.pretty = true;
					break;
				case "--file":
					result.file = nextValue(args, ++i, arg);
					break;
				case "--base-url":
					result.baseUrl = nextValue(args, ++i, arg);
					break;
				case "--override-url":
					result.overrideUrl = Boolean.parseBoolean(nextValue(args, ++i, arg));
					break;
				default:
					throw new IllegalArgumentException("Unknown argument: " + arg);
				}
			}

			if (!result.stdin && (result.file == null || result.file.isBlank())) {
				throw new IllegalArgumentException("Use --stdin or --file <path>");
			}
			return result;
		}
	}

	private static final class EnvUpdateArgs {
		private boolean modelStdin;
		private boolean pretty;
		private String modelFile;
		private String updatesFile;

		private static EnvUpdateArgs parse(String[] args, int start) {
			EnvUpdateArgs result = new EnvUpdateArgs();
			for (int i = start; i < args.length; i++) {
				String arg = args[i];
				switch (arg) {
				case "--model-stdin":
					result.modelStdin = true;
					break;
				case "--pretty":
					result.pretty = true;
					break;
				case "--model":
					result.modelFile = nextValue(args, ++i, arg);
					break;
				case "--updates":
					result.updatesFile = nextValue(args, ++i, arg);
					break;
				default:
					throw new IllegalArgumentException("Unknown env-update argument: " + arg);
				}
			}

			if (!result.modelStdin && (result.modelFile == null || result.modelFile.isBlank())) {
				throw new IllegalArgumentException("Use env-update --model <api-spec.json> or --model-stdin");
			}
			if (result.updatesFile == null || result.updatesFile.isBlank()) {
				throw new IllegalArgumentException("Use env-update --updates <env-updates.json>");
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
}
