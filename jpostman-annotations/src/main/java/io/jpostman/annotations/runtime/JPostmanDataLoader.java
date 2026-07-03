package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.propertyKey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.jpostman.annotations.JPostmanContext;

/**
 * Loads external INI data files declared by {@link JPostmanContext#dataload()}
 * or the config properties file {@code dataload}/{@code dataload.<namespace>}.
 * <p>
 * Clean naming rule:
 * <ul>
 * <li>{@code dataload} belongs to {@code JPostmanContext} and means data file
 * location.</li>
 * <li>{@code data} belongs to {@code JPostmanRunner}, {@code JPostmanRequest},
 * and {@code JPostmanResponse} and means section name from the loaded INI
 * files.</li>
 * </ul>
 * For this reason, config properties intentionally do not support
 * {@code data=...} as a context file-location mapping.
 */
public final class JPostmanDataLoader {

	public static final String DEFAULT_CONFIG = "classpath:jpostman.properties";
	private static final Set<String> REDUNDANT_DATALOAD_WARNINGS = ConcurrentHashMap.newKeySet();
	private static final Pattern EXPRESSION = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");

	private JPostmanDataLoader() {
	}

	static <C> void apply(Object testInstance, C context, JPostmanFramework<C> framework, JPostmanInfo info,
			String data, List<String> dataloadLocations) throws Exception {
		if (testInstance == null || info == null || data == null || data.isBlank()) {
			return;
		}

		String name = data.trim();
		Map<String, DataSection> sections = load(testInstance, dataloadLocations, info);
		if (sections.isEmpty()) {
			throw new IllegalStateException(JPostmanErrors.message(info, "JPostman data section not found: " + name));
		}

		ExpressionContext<C> expressions = new ExpressionContext<>(context, framework, info);
		List<DataSection> selected = select(sections, name, expressions.httpMethod(), info);
		if (selected.isEmpty()) {
			throw new IllegalStateException(JPostmanErrors.message(info, "JPostman data section not found: " + name));
		}

		Set<String> stack = new LinkedHashSet<>();
		for (DataSection section : selected) {
			applySection(section.name, sections, stack, testInstance.getClass(), expressions, info);
		}
		info.data(name);
	}

	static List<String> resolveLocations(String[] dataload, Properties properties, String namespace,
			String configLocation, JPostmanContext annotation) {
		List<String> result = new ArrayList<>();
		Set<String> unique = new LinkedHashSet<>();

		if (dataload != null) {
			for (String value : dataload) {
				addLocations(result, unique, value, "dataload", configLocation, false, annotation);
			}
		}

		/*
		 * Clean naming rule: - dataload belongs to JPostmanContext and means INI data
		 * file location. - data belongs to
		 * JPostmanRunner/JPostmanRequest/JPostmanResponse and means section name from
		 * the loaded INI files.
		 *
		 * For this reason, config properties intentionally support only dataload and
		 * not data as a context file-location mapping.
		 */
		String dataloadKey = propertyKey("dataload", namespace);
		addLocations(result, unique, property(properties, "dataload", namespace), dataloadKey, configLocation, true,
				annotation);

		return result;
	}

	private static Map<String, DataSection> load(Object testInstance, List<String> locations, JPostmanInfo info)
			throws Exception {
		Class<?> testClass = testInstance.getClass();
		List<String> dataloadLocations = locations == null ? List.of() : locations;
		if (dataloadLocations.isEmpty()) {
			throw new IllegalStateException(JPostmanErrors.message(info, "No JPostman data files configured.",
					"Add @JPostmanContext(dataload = {...}) or config properties key dataload."));
		}

		Map<String, DataSection> result = new LinkedHashMap<>();
		Map<String, String> sources = new LinkedHashMap<>();
		for (String location : dataloadLocations) {
			Map<String, DataSection> parsed = parse(location, testClass, info);
			for (Map.Entry<String, DataSection> entry : parsed.entrySet()) {
				String section = entry.getKey();
				if (result.containsKey(section)) {
					throw new IllegalStateException(
							JPostmanErrors.message(info, "Duplicate JPostman data section: " + section,
									"The same section was found in more than one loaded data file.", "Found in:",
									"- " + sources.get(section), "- " + location,
									"Keep each section name unique across loaded data files."));
				}
				result.put(section, entry.getValue());
				sources.put(section, location);
			}
		}
		return result;
	}

	private static List<DataSection> select(Map<String, DataSection> sections, String data, String httpMethod,
			JPostmanInfo info) {
		List<DataSection> result = new ArrayList<>();
		DataSection exact = sections.get(data);

		/* data="product.mouse" means exact section mode. */
		if (data.contains(".") && exact != null) {
			result.add(exact);
			return result;
		}

		/* data="product" means group mode. Root section is safe base data. */
		if (exact != null) {
			result.add(exact);
		}

		String methodSection = value(httpMethod).toUpperCase(Locale.ROOT) + "::" + data;
		if (!httpMethod.isBlank() && sections.containsKey(methodSection)) {
			result.add(sections.get(methodSection));
		}

		List<DataSection> otherMatches = new ArrayList<>();
		List<DataSection> requestMatches = new ArrayList<>();
		String prefix = data + ".";
		for (DataSection section : sections.values()) {
			if (section.name.equals(data) || section.name.equals(methodSection) || !section.name.startsWith(prefix)) {
				continue;
			}
			if (!section.hasFilters()) {
				continue;
			}
			if (!section.matches(info, httpMethod)) {
				continue;
			}
			if (section.has("request")) {
				requestMatches.add(section);
			} else {
				otherMatches.add(section);
			}
		}

		result.addAll(otherMatches);
		/* Request-specific sections have highest override priority. */
		result.addAll(requestMatches);
		return result;
	}

	private static <C> void applySection(String name, Map<String, DataSection> sections, Set<String> stack,
			Class<?> testClass, ExpressionContext<C> expressions, JPostmanInfo info) throws IOException {
		if (name == null || name.isBlank()) {
			return;
		}
		if (!stack.add(name)) {
			throw new IllegalStateException(JPostmanErrors.message(info,
					"Circular JPostman data inheritance detected: " + String.join(" -> ", stack) + " -> " + name));
		}

		DataSection section = sections.get(name);
		if (section == null) {
			throw new IllegalStateException(JPostmanErrors.message(info, "JPostman data section not found: " + name));
		}

		for (String parent : section.extendsSections(sections.keySet())) {
			if (!parent.equals(name)) {
				applySection(parent, sections, stack, testClass, expressions, info);
			}
		}

		applyValues(info, section.values(testClass, expressions));
		stack.remove(name);
	}

	private static void applyValues(JPostmanInfo info, Map<String, Object> values) {
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			String key = value(entry.getKey());
			Object value = entry.getValue();
			if (key.isBlank() || DataSection.metaKey(key)) {
				continue;
			}
			put(info, key, value);
		}
	}

	private static void put(JPostmanInfo info, String key, Object value) {
		int dot = key.indexOf('.');
		if (dot <= 0) {
			info.body(key, value);
			return;
		}

		String type = key.substring(0, dot).trim().toLowerCase(Locale.ROOT);
		String name = key.substring(dot + 1).trim();
		if (name.isBlank()) {
			return;
		}

		switch (type) {
		case "body":
			info.body(name, value);
			break;
		case "sbody":
			info.sbody(name, value);
			break;
		case "query":
			info.query(name, value);
			break;
		case "squery":
			info.squery(name, value);
			break;
		case "headers":
		case "header":
			info.headers(name, value);
			break;
		case "sheaders":
		case "sheader":
			info.sheaders(name, value);
			break;
		case "path":
			info.path(name, value);
			break;
		case "spath":
			info.spath(name, value);
			break;
		case "auth":
			info.auth(name, value);
			break;
		case "sauth":
			info.sauth(name, value);
			break;
		default:
			info.body(key, value);
			break;
		}
	}

	private static void addLocations(List<String> result, Set<String> unique, String value, String source,
			String configLocation, boolean configSource, JPostmanContext annotation) {
		if (value == null || value.isBlank()) {
			return;
		}
		for (String part : value.split(",")) {
			String location = part.trim();
			if (location.isBlank()) {
				continue;
			}
			if (!unique.add(location)) {
				if (configSource) {
					String warningKey = configLocation + "|" + source + "|" + location;
					if (REDUNDANT_DATALOAD_WARNINGS.add(warningKey)) {
						System.err.println(JPostmanErrors.message(annotation,
								"Redundant JPostman dataload mapping ignored.",
								"The same data file is configured more than once.",
								"Using @JPostmanContext value: dataload=" + location,
								"Ignored config mapping: " + configLocation + " -> " + source + "=" + location));
					}
				}
				continue;
			}
			result.add(location);
		}
	}

	private static Map<String, DataSection> parse(String location, Class<?> testClass, JPostmanInfo info)
			throws IOException {
		Map<String, DataSection> sections = new LinkedHashMap<>();
		DataSection current = null;
		try (InputStream input = open(location, testClass);
				BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				String trimmed = line.trim();
				if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
					continue;
				}
				if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
					String name = trimmed.substring(1, trimmed.length() - 1).trim();
					if (sections.containsKey(name)) {
						throw new IllegalStateException(JPostmanErrors.message(info,
								"Duplicate JPostman data section: " + name, "Found more than once in:", "- " + location,
								"Keep each section name unique inside each data file."));
					}
					current = new DataSection(name);
					sections.put(name, current);
					continue;
				}
				if (current != null) {
					current.addLine(line);
				}
			}
		}
		return sections;
	}

	@SuppressWarnings("unused")
	private static JPostmanContext contextAnnotation(Class<?> testClass) {
		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (java.lang.reflect.Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = JPostmanAnnotations.context(field);
				if (annotation != null) {
					return annotation;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private static String resolve(String value, ExpressionContext<?> context) {
		if (value == null || context == null) {
			return value;
		}
		Matcher matcher = EXPRESSION.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			String expression = matcher.group(1).trim();
			Object resolved = context.resolveExpression(expression);
			matcher.appendReplacement(result,
					Matcher.quoteReplacement(resolved == null ? "" : String.valueOf(resolved)));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private static String value(String value) {
		return value == null ? "" : value.trim();
	}

	private static String stripInlineComment(String value) {
		if (value == null) {
			return "";
		}
		boolean quoted = false;
		char quote = 0;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if ((ch == '\'' || ch == '"') && (i == 0 || value.charAt(i - 1) != '\\')) {
				if (!quoted) {
					quoted = true;
					quote = ch;
				} else if (quote == ch) {
					quoted = false;
				}
			}
			if (!quoted && ch == '#') {
				return value.substring(0, i);
			}
			if (!quoted && ch == '/' && i + 1 < value.length() && value.charAt(i + 1) == '/') {
				return value.substring(0, i);
			}
		}
		return value;
	}

	private static Object parseScalar(String value) {
		if (value == null) {
			return "";
		}
		String text = stripInlineComment(value.trim()).trim();
		if ((text.startsWith("\"") && text.endsWith("\"")) || (text.startsWith("'") && text.endsWith("'"))) {
			return text.substring(1, text.length() - 1);
		}
		if ("true".equalsIgnoreCase(text)) {
			return Boolean.TRUE;
		}
		if ("false".equalsIgnoreCase(text)) {
			return Boolean.FALSE;
		}
		if ("null".equalsIgnoreCase(text)) {
			return null;
		}
		try {
			if (!text.contains(".")) {
				return Integer.valueOf(text);
			}
			return Double.valueOf(text);
		} catch (NumberFormatException e) {
			return text;
		}
	}

	private static final class DataSection {
		private final String name;
		private final Map<String, Object> map = new LinkedHashMap<>();

		private DataSection(String name) {
			this.name = name;
		}

		private void addLine(String line) {
			String trimmed = line.trim();
			int index = trimmed.indexOf('=');
			if (index <= 0) {
				return;
			}
			String key = trimmed.substring(0, index).trim();
			String value = trimmed.substring(index + 1).trim();
			map.put(key, parseScalar(value));
		}

		private Map<String, Object> values(Class<?> testClass, ExpressionContext<?> expressions) throws IOException {
			Map<String, Object> result = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : map.entrySet()) {
				if (metaKey(entry.getKey())) {
					continue;
				}
				Object value = entry.getValue();
				if (value instanceof String) {
					result.put(entry.getKey(), parseScalar(resolve((String) value, expressions)));
				} else {
					result.put(entry.getKey(), value);
				}
			}
			return result;
		}

		private boolean hasFilters() {
			return has("namespace") || has("folder") || has("request") || has("method") || has("methodName")
					|| has("httpMethod") || has("tags") || has("anyTags");
		}

		private boolean has(String key) {
			return map.containsKey(key);
		}

		private boolean matches(JPostmanInfo info, String httpMethod) {
			if (!matchesValue("namespace", info.namespace)) {
				return false;
			}
			if (!matchesValue("folder", info.folder)) {
				return false;
			}
			if (!matchesValue("request", info.request)) {
				return false;
			}
			if (!matchesAnyKey(new String[] { "method", "methodName" }, info.method)) {
				return false;
			}
			if (!matchesValueIgnoreCase("httpMethod", httpMethod)) {
				return false;
			}
			if (!matchesTags(info.tags)) {
				return false;
			}
			return matchesAnyTags(info.tags);
		}

		private boolean matchesValue(String key, String actual) {
			if (!map.containsKey(key)) {
				return true;
			}
			return value(String.valueOf(map.get(key))).equals(value(actual));
		}

		private boolean matchesValueIgnoreCase(String key, String actual) {
			if (!map.containsKey(key)) {
				return true;
			}
			return value(String.valueOf(map.get(key))).equalsIgnoreCase(value(actual));
		}

		private boolean matchesAnyKey(String[] keys, String actual) {
			for (String key : keys) {
				if (map.containsKey(key)) {
					return value(String.valueOf(map.get(key))).equals(value(actual));
				}
			}
			return true;
		}

		private boolean matchesTags(String[] actual) {
			if (!map.containsKey("tags")) {
				return true;
			}
			Set<String> current = tags(actual);
			if (current.isEmpty()) {
				return false;
			}
			for (String tag : split(map.get("tags"))) {
				if (!current.contains(tag)) {
					return false;
				}
			}
			return true;
		}

		private boolean matchesAnyTags(String[] actual) {
			if (!map.containsKey("anyTags")) {
				return true;
			}
			Set<String> current = tags(actual);
			if (current.isEmpty()) {
				return false;
			}
			for (String tag : split(map.get("anyTags"))) {
				if (current.contains(tag)) {
					return true;
				}
			}
			return false;
		}

		private List<String> extendsSections(Collection<String> sectionNames) {
			List<String> result = new ArrayList<>();
			if (!map.containsKey("extends")) {
				return result;
			}
			for (String item : split(map.get("extends"))) {
				if ("*".equals(item)) {
					for (String section : sectionNames) {
						if (!section.equals(name)) {
							result.add(section);
						}
					}
				} else if (item.endsWith(".*")) {
					String prefix = item.substring(0, item.length() - 1);
					for (String section : sectionNames) {
						if (!section.equals(name) && section.startsWith(prefix)) {
							result.add(section);
						}
					}
				} else {
					result.add(item);
				}
			}
			return result;
		}

		private static boolean metaKey(String key) {
			String normalized = value(key);
			return "extends".equals(normalized) || "tags".equals(normalized) || "anyTags".equals(normalized)
					|| "namespace".equals(normalized) || "folder".equals(normalized) || "request".equals(normalized)
					|| "method".equals(normalized) || "methodName".equals(normalized)
					|| "httpMethod".equals(normalized);
		}

		private static List<String> split(Object value) {
			List<String> result = new ArrayList<>();
			if (value == null) {
				return result;
			}
			for (String part : String.valueOf(value).split(",")) {
				String item = part.trim();
				if (!item.isBlank()) {
					result.add(item);
				}
			}
			return result;
		}

		private static Set<String> tags(String[] values) {
			Set<String> result = new LinkedHashSet<>();
			if (values != null) {
				for (String value : values) {
					String tag = value(value);
					if (!tag.isBlank()) {
						result.add(tag);
					}
				}
			}
			return result;
		}
	}

	private static final class ExpressionContext<C> {
		private final C context;
		private final JPostmanFramework<C> framework;
		private final JPostmanInfo info;

		private ExpressionContext(C context, JPostmanFramework<C> framework, JPostmanInfo info) {
			this.context = context;
			this.framework = framework;
			this.info = info;
		}

		private Object resolveExpression(String expression) {
			if (expression.startsWith("jpostman:")) {
				return resolveJPostman(expression.substring("jpostman:".length()).trim());
			}
			int colon = expression.indexOf(':');
			if (colon > 0) {
				String cacheKey = expression.substring(0, colon).trim();
				String path = expression.substring(colon + 1).trim();
				return resolveCachedPath(cacheKey, path);
			}
			return autoResolve(expression.trim());
		}

		private Object resolveJPostman(String expression) {
			if (expression.startsWith("[")) {
				int close = expression.indexOf(']');
				if (close < 0) {
					throw unsupported(expression);
				}
				return resolveCachedPath(expression.substring(1, close).trim(), expression.substring(close + 1).trim());
			}

			int open = expression.indexOf('[');
			int close = expression.lastIndexOf(']');
			if (open <= 0 || close <= open) {
				throw unsupported(expression);
			}
			String type = expression.substring(0, open).trim().toLowerCase(Locale.ROOT);
			String key = expression.substring(open + 1, close).trim();
			Object value = resolveTyped(type, key);
			if (value == null) {
				throw new IllegalStateException(JPostmanErrors.message(info,
						"JPostman data expression value not found: jpostman:" + type + "[" + key + "]"));
			}
			return value;
		}

		private IllegalStateException unsupported(String expression) {
			return new IllegalStateException(
					JPostmanErrors.message(info, "Unsupported JPostman data expression: jpostman:" + expression));
		}

		private Object resolveTyped(String type, String key) {
			switch (type) {
			case "auth":
				return info == null ? null : info.auth.get(key);
			case "path":
				return info == null ? null : info.path.get(key);
			case "query":
				return info == null ? null : info.query.get(key);
			case "headers":
			case "header":
				return info == null ? null : info.headers.get(key);
			case "body":
				return info == null ? null : info.body.get(key);
			case "cache":
				return framework == null || internalCacheKey(key) ? null : framework.cache(context, key);
			case "env":
			case "secret":
			case "plain":
				return framework == null ? null : framework.value(context, key);
			default:
				throw unsupported(type + "[" + key + "]");
			}
		}

		private Object autoResolve(String key) {
			if (key == null || key.isBlank()) {
				return null;
			}
			Object value = null;
			if (info != null) {
				if (info.auth.containsKey(key))
					return info.auth.get(key);
				if (info.path.containsKey(key))
					return info.path.get(key);
				if (info.query.containsKey(key))
					return info.query.get(key);
				if (info.headers.containsKey(key))
					return info.headers.get(key);
				if (info.body.containsKey(key))
					return info.body.get(key);
			}
			if (framework != null) {
				value = framework.value(context, key);
				if (value != null)
					return value;
				if (!internalCacheKey(key)) {
					value = framework.cache(context, key);
					if (value != null)
						return value;
				}
				for (String cacheKey : framework.cacheKeys(context)) {
					if (internalCacheKey(cacheKey)) {
						continue;
					}
					Object cached = framework.cache(context, cacheKey);
					value = pathValue(cached, key);
					if (value != null)
						return value;
				}
			}
			return null;
		}

		private Object resolveCachedPath(String cacheKey, String path) {
			if (framework == null || internalCacheKey(cacheKey)) {
				return null;
			}
			Object cached = framework.cache(context, cacheKey);
			if (cached == null) {
				return null;
			}
			if (path == null || path.isBlank()) {
				return cached;
			}
			return pathValue(cached, path);
		}

		private Object pathValue(Object cached, String path) {
			if (cached == null || path == null || path.isBlank()) {
				return null;
			}
			if (framework != null && framework.contextType().isInstance(cached)) {
				try {
					return framework.path(framework.contextType().cast(cached), path);
				} catch (RuntimeException | AssertionError e) {
					Object fallback = wildcardMapPath(cached, path);
					if (fallback != null)
						return fallback;
				}
			}
			try {
				Method method = cached.getClass().getMethod("path", String.class);
				if (method.getReturnType() != Void.TYPE) {
					Object value = method.invoke(cached, path);
					if (value != null)
						return value;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// Fall back to map lookup.
			}
			return mapPath(cached, path);
		}

		private String httpMethod() {
			if (context == null) {
				return "";
			}
			try {
				Method request = context.getClass().getMethod("request");
				Object value = request.invoke(context);
				if (value == null) {
					return "";
				}
				for (String methodName : new String[] { "method", "getMethod" }) {
					try {
						Method method = value.getClass().getMethod(methodName);
						Object result = method.invoke(value);
						return result == null ? "" : String.valueOf(result).toUpperCase(Locale.ROOT);
					} catch (NoSuchMethodException ignored) {
						// Try next method name.
					}
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				return "";
			}
			return "";
		}
	}

	private static boolean internalCacheKey(String key) {
		return key != null && key.startsWith("__") && key.endsWith("__");
	}

	@SuppressWarnings("unchecked")
	private static Object mapPath(Object value, String path) {
		if (!(value instanceof Map<?, ?>) || path == null || path.isBlank()) {
			return null;
		}
		Map<Object, Object> map = (Map<Object, Object>) value;
		String normalized = path.trim();
		if (normalized.startsWith("/**/")) {
			return findRecursive(map, normalized.substring(4));
		}
		if (normalized.startsWith("/")) {
			Object current = map;
			for (String part : normalized.substring(1).split("/")) {
				if (part.isBlank())
					continue;
				if (!(current instanceof Map<?, ?>))
					return null;
				current = ((Map<?, ?>) current).get(part);
			}
			return current;
		}
		return map.get(normalized);
	}

	private static Object wildcardMapPath(Object value, String path) {
		if (path == null || !path.trim().startsWith("/**/")) {
			return null;
		}
		return findRecursive(value, path.trim().substring(4));
	}

	private static Object findRecursive(Object value, String key) {
		if (key == null || key.isBlank()) {
			return null;
		}
		if (value instanceof Map<?, ?>) {
			Map<?, ?> map = (Map<?, ?>) value;
			if (map.containsKey(key)) {
				return map.get(key);
			}
			for (Object nested : map.values()) {
				Object result = findRecursive(nested, key);
				if (result != null)
					return result;
			}
		}
		if (value instanceof Iterable<?>) {
			for (Object nested : (Iterable<?>) value) {
				Object result = findRecursive(nested, key);
				if (result != null)
					return result;
			}
		}
		return null;
	}
}
