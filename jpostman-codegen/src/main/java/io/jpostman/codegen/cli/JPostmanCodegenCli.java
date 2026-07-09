package io.jpostman.codegen.cli;

import io.jpostman.codegen.model.JPostmanAnnotationType;
import io.jpostman.codegen.model.JPostmanMethodSpec;
import io.jpostman.codegen.render.JavaTestMethodRenderer;

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

/**
 * Command-line entry point for generating JPostman annotation source snippets.
 */
public final class JPostmanCodegenCli {

    private static final Set<String> COMMON_OPTIONS = setOf(
            "method", "id", "tags", "namespace", "folder", "request", "rule", "filter", "depends-on",
            "include", "exclude", "verify", "executor", "cache", "log", "soft", "lifecycle", "data",
            "asserts", "enabled", "skip", "output", "append", "help");

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

            String command = args[0];
            if ("help".equalsIgnoreCase(command)) {
                if (args.length > 1) {
                    printCommandHelp(JPostmanAnnotationType.fromCommand(args[1]));
                } else {
                    printGeneralHelp();
                }
                return 0;
            }

            JPostmanAnnotationType type = JPostmanAnnotationType.fromCommand(command);
            CliOptions options = CliOptions.parse(Arrays.copyOfRange(args, 1, args.length));
            if (options.has("help")) {
                printCommandHelp(type);
                return 0;
            }
            validateOptions(type, options);

            JPostmanMethodSpec spec = buildSpec(type, options);
            String source = JavaTestMethodRenderer.render(spec);
            writeOutput(source, options);
            return 0;
        } catch (IllegalArgumentException | IOException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run 'jpostman-codegen help' for usage.");
            return 2;
        }
    }

    private static JPostmanMethodSpec buildSpec(JPostmanAnnotationType type, CliOptions options) {
        JPostmanMethodSpec.Builder builder = JPostmanMethodSpec.builder(type)
                .method(options.value("method"))
                .id(options.value("id"))
                .tags(options.values("tags"))
                .namespace(options.value("namespace"))
                .folder(options.value("folder"))
                .request(options.value("request"))
                .rule(options.value("rule"))
                .filter(options.values("filter"))
                .dependsOn(options.values("depends-on"))
                .include(options.values("include"))
                .exclude(options.values("exclude"))
                .executor(options.value("executor"))
                .cache(options.value("cache"))
                .log(options.value("log"))
                .data(options.value("data"))
                .asserts(options.values("asserts"));

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
                throw new IllegalArgumentException("--" + name + " is not supported for " + type.commandName() + " command");
            }
        }
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
            Files.write(path, source.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } else {
            Files.write(path, source.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        }
    }

    private static boolean isHelp(String value) {
        return "-h".equals(value) || "--help".equals(value) || "help".equalsIgnoreCase(value);
    }

    private static void printGeneralHelp() {
        System.out.println("JPostman Codegen CLI");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  jpostman-codegen <command> --method <methodName> [options]");
        System.out.println("  java -jar target/jpostman-codegen-1.0.0-SNAPSHOT.jar <command> --method <methodName> [options]");
        System.out.println();
        System.out.println("Commands:");
        System.out.println("  runner     Generate @Test + @JPostman.Runner method");
        System.out.println("  request    Generate @Test + @JPostman.Request method");
        System.out.println("  response   Generate @Test + @JPostman.Response method");
        System.out.println();
        System.out.println("Common options:");
        System.out.println("  --method <name>             Java method name. Required.");
        System.out.println("  --namespace <name>          JPostman context namespace.");
        System.out.println("  --folder <name>             Postman collection folder.");
        System.out.println("  --request <name>            Postman request name. Request/response only.");
        System.out.println("  --tags <a,b>                Tags. Can be comma-separated or repeated.");
        System.out.println("  --depends-on <a,b>          JPostman dependencies. Use method names or #ids.");
        System.out.println("  --log <debug|none|error>    Local JPostman log mode.");
        System.out.println("  --output <file>             Write generated source to file instead of stdout.");
        System.out.println("  --append                    Append to --output instead of replacing it.");
        System.out.println();
        System.out.println("Run 'jpostman-codegen help runner', 'help request', or 'help response' for command options.");
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
        System.out.println("  --executor <executorId>");
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
        System.out.println();
        System.out.println("Output options:");
        System.out.println("  --output <file>");
        System.out.println("  --append");
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
            return normalized;
        }
    }
}
