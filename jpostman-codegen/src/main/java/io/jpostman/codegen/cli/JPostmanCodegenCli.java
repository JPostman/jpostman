package io.jpostman.codegen.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.jpostman.codegen.model.JPostmanAnnotationType;
import io.jpostman.codegen.model.JPostmanMethodSpec;
import io.jpostman.codegen.render.JavaTestMethodRenderer;

/**
 * Command-line entry point for generating JPostman annotation source snippets
 * and JPostman runtime properties.
 */
public final class JPostmanCodegenCli {

	private static final Set<String> COMMON_OPTIONS = setOf("method", "id", "tags", "namespace", "folder", "request",
			"rule", "filter", "depends-on", "include", "exclude", "verify", "executor", "executor-class", "cache",
			"log", "soft", "lifecycle", "data", "asserts", "enabled", "skip", "include-test", "pom", "pom-file",
			"project-dir", "version-property", "output", "append", "help");

	private static final Set<String> PROPERTIES_OPTIONS = setOf("namespace", "executor", "executor-class", "collection",
			"environment", "output", "help");

	private JPostmanCodegenCli() {
	}

	public static void main(String[] args) {
		int exitCode = run(args);
		if (exitCode != 0) {
			System.exit(exitCode);
		}
	}

	static int run(String[] args) {
		try {
			if (args.length == 0 || isHelp(args[0])) {
				printGeneralHelp();
				return 0;
			}

			if (isVersion(args[0])) {
				System.out.println(JPostmanMethodSpec.CURRENT_VERSION);
				return 0;
			}

			String command = args[0];
			if ("help".equalsIgnoreCase(command)) {
				if (args.length > 1 && isPropertiesCommand(args[1])) {
					printPropertiesHelp();
				} else if (args.length > 1) {
					printCommandHelp(JPostmanAnnotationType.fromCommand(args[1]));
				} else {
					printGeneralHelp();
				}
				return 0;
			}

			if (isPropertiesCommand(command)) {
				CliOptions options = CliOptions.parse(Arrays.copyOfRange(args, 1, args.length));
				if (options.has("help")) {
					printPropertiesHelp();
					return 0;
				}
				writeProperties(options);
				return 0;
			}

			JPostmanAnnotationType type = JPostmanAnnotationType.fromCommand(command);
			CliOptions options = CliOptions.parse(Arrays.copyOfRange(args, 1, args.length));
			if (options.has("help")) {
				printCommandHelp(type);
				return 0;
			}
			validateOptions(type, options);

			String executorClass = executorClass(options);
			JPostmanMethodSpec spec = buildSpec(type, options);
			String source = JavaTestMethodRenderer.render(spec, options.boolOrDefault("include-test", false));
			updatePomIfRequested(options, executorClass);
			writeOutput(source, options);
			return 0;
		} catch (IllegalArgumentException | IOException e) {
			System.err.println("Error: " + e.getMessage());
			System.err.println("Run 'jpostman-codegen help' for usage.");
			return 2;
		}
	}

	private static boolean isPropertiesCommand(String command) {
		return "properties".equalsIgnoreCase(command) || "props".equalsIgnoreCase(command);
	}

	private static JPostmanMethodSpec buildSpec(JPostmanAnnotationType type, CliOptions options) {
		JPostmanMethodSpec.Builder builder = JPostmanMethodSpec.builder(type).method(options.value("method"))
				.id(options.value("id")).tags(options.values("tags")).namespace(options.value("namespace"))
				.folder(options.value("folder")).request(options.value("request")).rule(options.value("rule"))
				.filter(options.values("filter")).dependsOn(options.values("depends-on"))
				.include(options.values("include")).exclude(options.values("exclude")).cache(options.value("cache"))
				.log(options.value("log")).data(options.value("data")).asserts(options.values("asserts"));

		if (options.has("verify")) {
			builder.verify(options.integer("verify"));
		}
		if (options.has("soft")) {
			builder.soft(options.bool("soft"));
		}
		if (options.has("lifecycle")) {
			builder.lifecycle(options.bool("lifecycle"));
		}
		if (options.has("enabled")) {
			builder.enabled(options.bool("enabled"));
		}
		if (options.has("skip")) {
			builder.skip(options.bool("skip"));
		}
		return builder.build();
	}

	private static void validateOptions(JPostmanAnnotationType type, CliOptions options) {
		for (String key : options.optionNames()) {
			if (!COMMON_OPTIONS.contains(key)) {
				throw new IllegalArgumentException("Unsupported option --" + key);
			}
		}

		if (type == JPostmanAnnotationType.RUNNER) {
			reject(type, options, "request", "cache");
		}
		if (type == JPostmanAnnotationType.REQUEST) {
			reject(type, options, "include", "exclude", "verify", "soft", "lifecycle", "asserts", "enabled");
		}
		if (type == JPostmanAnnotationType.RESPONSE) {
			reject(type, options, "include", "exclude", "lifecycle");
		}
	}

	private static void reject(JPostmanAnnotationType type, CliOptions options, String... names) {
		for (String name : names) {
			if (options.has(name)) {
				throw new IllegalArgumentException(
						"--" + name + " is not supported for " + type.commandName() + " command");
			}
		}
	}

	private static String executorClass(CliOptions options) {
		return firstNonBlank(options.value("executor-class"), options.value("executor"));
	}

	private static void updatePomIfRequested(CliOptions options, String executorClass) throws IOException {
		Path pomPath = pomPath(options);
		if (pomPath == null) {
			return;
		}

		String normalizedExecutor = trimToNull(executorClass);
		if (normalizedExecutor == null) {
			return;
		}

		ensureExecutorDependency(pomPath, normalizedExecutor,
				firstNonBlank(options.value("version-property"), "jpostman.version"));
	}

	private static Path pomPath(CliOptions options) {
		String pom = firstNonBlank(options.value("pom"), options.value("pom-file"));
		if (pom != null) {
			return Paths.get(pom);
		}

		String projectDir = trimToNull(options.value("project-dir"));
		if (projectDir != null) {
			return Paths.get(projectDir).resolve("pom.xml");
		}
		return null;
	}

	private static void ensureExecutorDependency(Path pomPath, String executorClass, String versionProperty)
			throws IOException {
		if (!Files.exists(pomPath)) {
			throw new IllegalArgumentException("pom.xml was not found: " + pomPath);
		}

		String pom = Files.readString(pomPath, StandardCharsets.UTF_8);
		String artifactId = executorArtifactId(executorClass);
		if (hasDependency(pom, "io.github.jpostman", artifactId)) {
			return;
		}

		String updated = insertDependency(pom, dependencyXml(artifactId, versionProperty, lineSeparator(pom)));
		Files.write(pomPath, updated.getBytes(StandardCharsets.UTF_8), StandardOpenOption.TRUNCATE_EXISTING,
				StandardOpenOption.WRITE);
	}

	private static String executorArtifactId(String executorClass) {
		String normalized = trimToNull(executorClass);
		if (normalized == null) {
			throw new IllegalArgumentException("Executor class is required to update pom.xml.");
		}

		String[] parts = normalized.split("\\.");
		if (parts.length >= 4 && "io".equals(parts[0]) && "jpostman".equals(parts[1])) {
			return "jpostman-" + parts[2];
		}

		throw new IllegalArgumentException("Unsupported executor class for Maven dependency mapping: " + executorClass);
	}

	private static boolean hasDependency(String pom, String groupId, String artifactId) {
		String compact = pom.replaceAll("\\s+", "");
		return compact.contains("<groupId>" + groupId + "</groupId>")
				&& compact.contains("<artifactId>" + artifactId + "</artifactId>");
	}

	private static String dependencyXml(String artifactId, String versionProperty, String newline) {
		String version = versionExpression(versionProperty);
		return "\t\t<dependency>" + newline + "\t\t\t<groupId>io.github.jpostman</groupId>" + newline
				+ "\t\t\t<artifactId>" + artifactId + "</artifactId>" + newline + "\t\t\t<version>" + version
				+ "</version>" + newline + "\t\t\t<scope>test</scope>" + newline + "\t\t</dependency>" + newline;
	}

	private static String versionExpression(String versionProperty) {
		String normalized = firstNonBlank(versionProperty, "jpostman.version");
		if (normalized.startsWith("${") && normalized.endsWith("}")) {
			return normalized;
		}
		return "${" + normalized + "}";
	}

	private static String insertDependency(String pom, String dependency) {
		String newline = lineSeparator(pom);
		int dependenciesEnd = pom.lastIndexOf("</dependencies>");
		if (dependenciesEnd >= 0) {
			int insertAt = lineStart(pom, dependenciesEnd);
			return pom.substring(0, insertAt) + dependency + pom.substring(insertAt);
		}

		int projectEnd = pom.lastIndexOf("</project>");
		if (projectEnd < 0) {
			throw new IllegalArgumentException("Invalid pom.xml: missing </project>.");
		}

		String dependencies = "\t<dependencies>" + newline + dependency + "\t</dependencies>" + newline;
		return pom.substring(0, projectEnd) + dependencies + pom.substring(projectEnd);
	}

	private static int lineStart(String text, int index) {
		int position = Math.max(0, Math.min(index, text.length()));
		while (position > 0) {
			char previous = text.charAt(position - 1);
			if (previous == '\n' || previous == '\r') {
				break;
			}
			position--;
		}
		return position;
	}

	private static String lineSeparator(String text) {
		return text.contains("\r\n") ? "\r\n" : System.lineSeparator();
	}

	private static void writeOutput(String source, CliOptions options) throws IOException {
		String output = options.value("output");
		if (output == null) {
			System.out.print(source);
			return;
		}

		Path path = Paths.get(output);
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}

		if (options.boolOrDefault("append", false)) {
			Files.write(path, source.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} else {
			Files.write(path, source.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
		}
	}

	private static void writeProperties(CliOptions options) throws IOException {
		validatePropertiesOptions(options);
		String output = options.value("output");
		Map<String, String> properties = output == null ? new LinkedHashMap<>() : readProperties(Paths.get(output));
		String suffix = suffix(options.value("namespace"));

		String executor = executorClass(options);
		if (executor != null) {
			properties.put("executor" + suffix, executor);
		}
		String collection = normalizeResourcePath(options.value("collection"));
		if (collection != null) {
			properties.put("collection" + suffix, collection);
		}
		String environment = normalizeResourcePath(options.value("environment"));
		if (environment != null) {
			properties.put("environment" + suffix, environment);
		}

		String content = renderProperties(properties);
		content += "\n";
		if (output == null) {
			System.out.print(content);
			return;
		}
		Path path = Paths.get(output);
		Path parent = path.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
				StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
	}

	private static void validatePropertiesOptions(CliOptions options) {
		for (String key : options.optionNames()) {
			if (!PROPERTIES_OPTIONS.contains(key)) {
				throw new IllegalArgumentException("Unsupported properties option --" + key);
			}
		}
		if (executorClass(options) == null && options.value("collection") == null
				&& options.value("environment") == null) {
			throw new IllegalArgumentException(
					"Use properties --executor-class <class> and/or --collection <path> and/or --environment <path>.");
		}
	}

	private static Map<String, String> readProperties(Path path) throws IOException {
		Map<String, String> result = new LinkedHashMap<>();
		if (!Files.exists(path)) {
			return result;
		}
		for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
				continue;
			}
			int separator = firstSeparator(line);
			if (separator < 0) {
				result.put(trimmed, "");
				continue;
			}
			String key = line.substring(0, separator).trim();
			String value = line.substring(separator + 1).trim();
			if (!key.isEmpty()) {
				result.put(key, value);
			}
		}
		return result;
	}

	private static int firstSeparator(String line) {
		int equals = line.indexOf('=');
		int colon = line.indexOf(':');
		if (equals < 0) {
			return colon;
		}
		if (colon < 0) {
			return equals;
		}
		return Math.min(equals, colon);
	}

	private static String renderProperties(Map<String, String> properties) {
		String newline = System.lineSeparator();
		StringBuilder result = new StringBuilder();
		Set<String> rendered = new LinkedHashSet<>();

		List<String> suffixes = propertySuffixes(properties);
		for (String suffix : suffixes) {
			appendBlockSeparator(result, newline);
			appendRuntimeProperty(result, rendered, properties, "executor", suffix, newline);
			appendRuntimeProperty(result, rendered, properties, "collection", suffix, newline);
			appendRuntimeProperty(result, rendered, properties, "environment", suffix, newline);
		}

		for (Map.Entry<String, String> entry : properties.entrySet()) {
			if (rendered.contains(entry.getKey())) {
				continue;
			}
			appendBlockSeparator(result, newline);
			result.append(entry.getKey()).append('=').append(entry.getValue()).append(newline);
		}
		return result.toString();
	}

	private static List<String> propertySuffixes(Map<String, String> properties) {
		List<String> suffixes = new ArrayList<>();
		if (hasRuntimeBlock(properties, "")) {
			suffixes.add("");
		}

		for (String key : properties.keySet()) {
			String suffix = runtimePropertySuffix(key);
			if (suffix != null && !suffixes.contains(suffix) && hasRuntimeBlock(properties, suffix)) {
				suffixes.add(suffix);
			}
		}
		return suffixes;
	}

	private static boolean hasRuntimeBlock(Map<String, String> properties, String suffix) {
		return properties.containsKey("executor" + suffix) || properties.containsKey("collection" + suffix)
				|| properties.containsKey("environment" + suffix);
	}

	private static String runtimePropertySuffix(String key) {
		if (key.startsWith("executor.")) {
			return key.substring("executor".length());
		}
		if (key.startsWith("collection.")) {
			return key.substring("collection".length());
		}
		if (key.startsWith("environment.")) {
			return key.substring("environment".length());
		}
		return null;
	}

	private static void appendRuntimeProperty(StringBuilder result, Set<String> rendered,
			Map<String, String> properties, String baseName, String suffix, String newline) {
		String key = baseName + suffix;
		String value = properties.get(key);
		if (value == null) {
			return;
		}
		result.append(key).append('=').append(value).append(newline);
		rendered.add(key);
	}

	private static void appendBlockSeparator(StringBuilder result, String newline) {
		if (result.length() > 0 && !result.toString().endsWith(newline + newline)) {
			result.append(newline);
		}
	}

	private static String suffix(String namespace) {
		String normalized = trimToNull(namespace);
		return normalized == null ? "" : "." + normalized;
	}

	private static String normalizeResourcePath(String value) {
		String normalized = trimToNull(value);
		if (normalized == null) {
			return null;
		}
		String forward = normalized.replace('\\', '/');
		String marker = "src/test/resources/";
		int index = forward.lastIndexOf(marker);
		if (index >= 0) {
			String resourceName = forward.substring(index + marker.length());
			int slash = resourceName.lastIndexOf('/');
			if (slash >= 0) {
				resourceName = resourceName.substring(slash + 1);
			}
			return "classpath:" + resourceName;
		}
		return forward;
	}

	private static String firstNonBlank(String first, String second) {
		String normalized = trimToNull(first);
		return normalized == null ? trimToNull(second) : normalized;
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static boolean isVersion(String value) {
		return "-v".equals(value) || "--version".equals(value) || "version".equalsIgnoreCase(value);
	}

	private static boolean isHelp(String value) {
		return "-h".equals(value) || "--help".equals(value) || "help".equalsIgnoreCase(value);
	}

	private static void printGeneralHelp() {
		System.out.println("JPostman Codegen CLI " + JPostmanMethodSpec.CURRENT_VERSION);
		System.out.println();
		System.out.println("Usage:");
		System.out.println("  jpostman-codegen <command> --method <methodName> [options]");
		System.out.println(
				"  jpostman-codegen properties --executor-class <class> --collection <path> --environment <path> [options]");
		System.out.println("  java -jar target/jpostman-codegen-<version>.jar <command> [options]");
		System.out.println("  jpostman-codegen --version");
		System.out.println();
		System.out.println("Commands:");
		System.out.println("  request      Generate @JPostman.Request method");
		System.out.println("  response     Generate @JPostman.Response method");
		System.out.println("  runner       Generate @JPostman.Runner method");
		System.out.println("  properties   Generate or update jpostman.properties");
		System.out.println();
		System.out.println("Common options:");
		System.out.println("  --method <name>             Java method name. Required for runner/request/response.");
		System.out.println("  --namespace <name>          JPostman context namespace.");
		System.out.println("  --folder <name>             Postman collection folder.");
		System.out.println("  --request <name>            Postman request name. Request/response only.");
		System.out.println("  --tags <a,b>                Tags. Can be comma-separated or repeated.");
		System.out.println("  --depends-on <a,b>          JPostman dependencies. Use method names or #ids.");
		System.out.println("  --include-test              Include the TestNG @Test annotation.");
		System.out.println("  --log <debug|none|error>    Local JPostman log mode.");
		System.out.println("  --output <file>             Write generated output to file instead of stdout.");
		System.out.println();
		System.out
				.println("Run 'jpostman-codegen help runner', 'help request', 'help response', or 'help properties'.");
	}

	private static void printCommandHelp(JPostmanAnnotationType type) {
		System.out.println("Usage:");
		System.out.println("  jpostman-codegen " + type.commandName() + " --method <methodName> [options]");
		System.out.println();
		System.out.println("Common JPostman options:");
		System.out.println("  --id <id>");
		System.out.println("  --tags <tag1,tag2>");
		System.out.println("  --namespace <namespace>");
		System.out.println("  --folder <folder>");
		if (type.isRequestAware()) {
			System.out.println("  --request <request>");
		}
		System.out.println("  --rule <rule>");
		System.out.println("  --filter <field1,field2>");
		System.out.println("  --depends-on <methodOrId1,methodOrId2>");
		if (type == JPostmanAnnotationType.RUNNER) {
			System.out.println("  --include <request1,request2>");
			System.out.println("  --exclude <request1,request2>");
		}
		if (type == JPostmanAnnotationType.RUNNER || type == JPostmanAnnotationType.RESPONSE) {
			System.out.println("  --verify <statusCode>");
		}
		System.out.println("  --executor <executorClass>    Selected executor class for pom.xml dependency update.");
		System.out.println("  --executor-class <class>      Alias for --executor. Not rendered in annotations.");
		if (type == JPostmanAnnotationType.REQUEST || type == JPostmanAnnotationType.RESPONSE) {
			System.out.println("  --cache <cacheKey>");
		}
		System.out.println("  --log <debug|none|error>");
		if (type == JPostmanAnnotationType.RUNNER || type == JPostmanAnnotationType.RESPONSE) {
			System.out.println("  --soft <true|false>");
		}
		if (type == JPostmanAnnotationType.RUNNER) {
			System.out.println("  --lifecycle <true|false>");
		}
		System.out.println("  --data <dataSection>");
		if (type == JPostmanAnnotationType.RUNNER || type == JPostmanAnnotationType.RESPONSE) {
			System.out.println("  --asserts <section1,section2>");
			System.out.println("  --enabled <true|false>");
		}
		System.out.println("  --skip <true|false>");
		System.out.println("  --include-test [true|false]  Include the TestNG @Test annotation. Default: false.");
		System.out.println();
		System.out.println("Output options:");
		System.out.println("  --pom <pom.xml>              Add missing selected executor dependency to this pom.xml.");
		System.out.println("  --project-dir <dir>          Alias for --pom <dir>/pom.xml.");
		System.out.println("  --version-property <name>    Maven version property. Default: jpostman.version.");
		System.out.println("  --output <file>");
		System.out.println("  --append");
	}

	private static void printPropertiesHelp() {
		System.out.println("Usage:");
		System.out.println(
				"  jpostman-codegen properties --executor-class <class> --collection <path> --environment <path> --output <jpostman.properties>");
		System.out.println();
		System.out.println("Options:");
		System.out.println(
				"  --namespace <namespace>       Writes executor.<namespace>, collection.<namespace>, environment.<namespace>.");
		System.out.println("  --executor-class <class>      Fully qualified executor class.");
		System.out.println("  --executor <class>            Alias for --executor-class.");
		System.out.println(
				"  --collection <path>           Collection file path. src/test/resources files become classpath:<filename>.");
		System.out.println(
				"  --environment <path>          Environment file path. src/test/resources files become classpath:<filename>.");
		System.out.println("  --output <file>               jpostman.properties path. Existing keys are updated.");
	}

	private static Set<String> setOf(String... values) {
		return new LinkedHashSet<>(Arrays.asList(values));
	}

	private static final class CliOptions {
		private final Map<String, List<String>> options = new LinkedHashMap<>();

		static CliOptions parse(String[] args) {
			CliOptions result = new CliOptions();
			for (int i = 0; i < args.length; i++) {
				String token = args[i];
				if (!token.startsWith("--")) {
					throw new IllegalArgumentException("Unexpected argument: " + token);
				}

				String name;
				String value = null;
				int equal = token.indexOf('=');
				if (equal > 0) {
					name = token.substring(2, equal);
					value = token.substring(equal + 1);
				} else {
					name = token.substring(2);
					if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
						value = args[++i];
					}
				}

				name = normalizeName(name);
				if (name.isEmpty()) {
					throw new IllegalArgumentException("Empty option name");
				}
				if (value == null) {
					value = "true";
				}
				result.options.computeIfAbsent(name, key -> new ArrayList<>()).add(value);
			}
			return result;
		}

		boolean has(String name) {
			return options.containsKey(normalizeName(name));
		}

		String value(String name) {
			List<String> values = options.get(normalizeName(name));
			if (values == null || values.isEmpty()) {
				return null;
			}
			return values.get(values.size() - 1);
		}

		String[] values(String name) {
			List<String> values = options.get(normalizeName(name));
			if (values == null || values.isEmpty()) {
				return new String[0];
			}
			return values.toArray(new String[0]);
		}

		int integer(String name) {
			String value = value(name);
			try {
				return Integer.parseInt(value);
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("--" + normalizeName(name) + " must be an integer: " + value);
			}
		}

		boolean bool(String name) {
			String value = value(name);
			if ("true".equalsIgnoreCase(value)) {
				return true;
			}
			if ("false".equalsIgnoreCase(value)) {
				return false;
			}
			throw new IllegalArgumentException("--" + normalizeName(name) + " must be true or false: " + value);
		}

		boolean boolOrDefault(String name, boolean defaultValue) {
			return has(name) ? bool(name) : defaultValue;
		}

		Set<String> optionNames() {
			return options.keySet();
		}

		private static String normalizeName(String name) {
			if (name == null) {
				return "";
			}
			String normalized = name.trim().replace('_', '-');
			if ("dependsOn".equals(normalized)) {
				return "depends-on";
			}
			if ("depends-on-methods".equals(normalized)) {
				return "depends-on";
			}
			if ("method-name".equals(normalized)) {
				return "method";
			}
			if ("executorClass".equals(normalized)) {
				return "executor-class";
			}
			if ("pomFile".equals(normalized)) {
				return "pom-file";
			}
			if ("projectDir".equals(normalized)) {
				return "project-dir";
			}
			if ("versionProperty".equals(normalized)) {
				return "version-property";
			}
			if ("includeTest".equals(normalized)) {
				return "include-test";
			}
			return normalized;
		}
	}
}
