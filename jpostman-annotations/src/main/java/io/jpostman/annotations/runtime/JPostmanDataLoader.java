package io.jpostman.annotations.runtime;

import static io.jpostman.annotations.runtime.JPostmanResourceLoader.open;
import static io.jpostman.annotations.runtime.JPostmanResourceLoader.property;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.jpostman.annotations.JPostmanContext;
import io.jpostman.annotations.JPostmanData;
import io.jpostman.annotations.JPostmanInfo;

/** Loads external data sections for {@link JPostmanData}. */
final class JPostmanDataLoader {

	private static final String DEFAULT_CONFIG = "classpath:jpostman.properties";
	private static final Pattern EXPRESSION = Pattern
			.compile("\\{\\{\\s*jpostman:(?:([a-zA-Z][a-zA-Z0-9_-]*)\\[([^\\]]+)]|\\[([^\\]]+)]([^}]*))\\s*}}");

	private JPostmanDataLoader() {
	}

	/**
	 * Applies data from the section selected by {@code @JPostmanData} to
	 * {@code info.params}.
	 *
	 * @param testInstance test instance that owns the annotated method
	 * @param info         current execution info
	 * @param annotation   data annotation, or {@code null}
	 * @throws Exception when data cannot be loaded or parsed
	 */
	static void apply(Object testInstance, JPostmanInfo info, JPostmanData annotation) throws Exception {
		apply(testInstance, null, null, info, annotation);
	}

	/**
	 * Applies data from the section selected by {@code @JPostmanData} to
	 * {@code info.params} and resolves supported JPostman data expressions.
	 *
	 * @param testInstance test instance that owns the annotated method
	 * @param context      active framework context, or {@code null}
	 * @param framework    framework bridge used to read cache/context values, or
	 *                     {@code null}
	 * @param info         current execution info
	 * @param annotation   data annotation, or {@code null}
	 * @throws Exception when data cannot be loaded or parsed
	 */
	static <C> void apply(Object testInstance, C context, JPostmanFramework<C> framework, JPostmanInfo info,
			JPostmanData annotation) throws Exception {
		if (testInstance == null || info == null || annotation == null) {
			return;
		}

		String section = first(annotation.section(), annotation.value(), info.id, info.callerId);
		if (section.isBlank()) {
			throw new IllegalStateException("@JPostmanData section is blank and JPostman execution name is blank.");
		}

		String namespace = value(annotation.namespace());
		DataSection data = find(testInstance, namespace, section);
		if (data == null) {
			throw new IllegalStateException("JPostman data section not found: " + label(namespace, section));
		}

		info.data(section);
		info.params(data.values(testInstance.getClass(), new ExpressionContext<>(context, framework, info)));
	}

	private static DataSection find(Object testInstance, String namespace, String section) throws Exception {
		Class<?> testClass = testInstance.getClass();
		JPostmanContext context = contextAnnotation(testClass);
		String config = context == null ? DEFAULT_CONFIG : context.config();
		String[] dataload = context == null ? new String[0] : context.dataload();

		Properties properties = JPostmanResourceLoader.loadProperties(config, testClass);
		List<String> locations = locations(dataload, properties, namespace);
		if (locations.isEmpty()) {
			throw new IllegalStateException("No JPostman data files configured. Add @JPostmanContext(dataload = {...})"
					+ " or jpostman.properties key dataload.");
		}

		String namespaced = namespace.isBlank() ? "" : namespace + "." + section;
		DataSection fallback = null;
		DataSection namespacedMatch = null;

		for (String location : locations) {
			Map<String, DataSection> sections = parse(location, testClass);
			if (!namespaced.isBlank() && sections.containsKey(namespaced)) {
				namespacedMatch = sections.get(namespaced);
			}
			if (sections.containsKey(section)) {
				fallback = sections.get(section);
			}
		}

		return namespacedMatch != null ? namespacedMatch : fallback;
	}

	private static List<String> locations(String[] dataload, Properties properties, String namespace) {
		List<String> result = new ArrayList<>();
		if (dataload != null) {
			for (String value : dataload) {
				addLocations(result, value);
			}
		}

		addLocations(result, property(properties, "dataload", namespace));
		addLocations(result, property(properties, "data", namespace));
		return result;
	}

	private static void addLocations(List<String> result, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		String[] parts = value.split(",");
		for (String part : parts) {
			String location = part.trim();
			if (!location.isBlank()) {
				result.add(location);
			}
		}
	}

	private static Map<String, DataSection> parse(String location, Class<?> testClass) throws IOException {
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
					current = new DataSection(location, name);
					sections.put(name, current);
					continue;
				}

				if (current == null) {
					continue;
				}

				current.addLine(line);
			}
		}

		return sections;
	}

	private static JPostmanContext contextAnnotation(Class<?> testClass) {
		Class<?> current = testClass;
		while (current != null && current != Object.class) {
			for (java.lang.reflect.Field field : current.getDeclaredFields()) {
				JPostmanContext annotation = field.getAnnotation(JPostmanContext.class);
				if (annotation != null) {
					return annotation;
				}
			}
			current = current.getSuperclass();
		}
		return null;
	}

	private static String first(String... values) {
		if (values != null) {
			for (String value : values) {
				if (value != null && !value.isBlank()) {
					return value.trim();
				}
			}
		}
		return "";
	}

	private static String value(String value) {
		return value == null ? "" : value.trim();
	}

	private static String label(String namespace, String section) {
		return namespace == null || namespace.isBlank() ? section : namespace + "." + section + " or " + section;
	}

	private static final class DataSection {
		private final String location;
		private final String name;
		private String type = "map";
		private String source = "";
		private final Map<String, Object> map = new LinkedHashMap<>();
		private final StringBuilder raw = new StringBuilder();

		private DataSection(String location, String name) {
			this.location = location;
			this.name = name;
		}

		private void addLine(String line) {
			String trimmed = line.trim();
			int index = trimmed.indexOf('=');
			if (index > 0 && raw.length() == 0) {
				String key = trimmed.substring(0, index).trim();
				String value = trimmed.substring(index + 1).trim();
				if ("type".equalsIgnoreCase(key)) {
					type = value.toLowerCase(Locale.ROOT);
					return;
				}
				if ("source".equalsIgnoreCase(key)) {
					source = value;
					return;
				}
				map.put(key, parseScalar(value));
				return;
			}

			raw.append(line).append(System.lineSeparator());
		}

		private Map<String, Object> values(Class<?> testClass, ExpressionContext<?> expressions) throws IOException {
			if (!source.isBlank()) {
				return fromSource(testClass, expressions);
			}

			if ("map".equals(type) || type.isBlank()) {
				return resolveMap(map, expressions);
			}

			if ("json".equals(type)) {
				String json = raw.toString().trim();
				if (json.isBlank()) {
					return map;
				}
				return new JsonObjectParser(resolve(json, expressions), location, name).parseObject();
			}

			if ("xml".equals(type)) {
				String xml = raw.toString().trim();
				if (xml.isBlank()) {
					return map;
				}
				return parseXmlObject(resolve(xml, expressions), location, name);
			}

			throw new IllegalStateException("Unsupported JPostman data type: " + type + " in section [" + name + "]");
		}

		private Map<String, Object> fromSource(Class<?> testClass, ExpressionContext<?> expressions)
				throws IOException {
			StringBuilder content = new StringBuilder();
			try (InputStream input = open(source, testClass);
					BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					content.append(line).append(System.lineSeparator());
				}
			}

			if ("json".equals(type)) {
				return new JsonObjectParser(resolve(content.toString(), expressions), source, name).parseObject();
			}
			if ("xml".equals(type)) {
				return parseXmlObject(resolve(content.toString(), expressions), source, name);
			}
			if ("map".equals(type) || type.isBlank()) {
				Map<String, Object> result = new LinkedHashMap<>();
				String[] lines = content.toString().split("\\R");
				for (String line : lines) {
					String trimmed = line.trim();
					if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith(";")) {
						continue;
					}
					int index = trimmed.indexOf('=');
					if (index <= 0) {
						continue;
					}
					result.put(trimmed.substring(0, index).trim(),
							parseScalar(resolve(trimmed.substring(index + 1).trim(), expressions)));
				}
				return result;
			}
			throw new IllegalStateException(
					"Unsupported source JPostman data type: " + type + " in section [" + name + "]");
		}
	}

	private static Map<String, Object> resolveMap(Map<String, Object> values, ExpressionContext<?> expressions) {
		Map<String, Object> result = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : values.entrySet()) {
			Object value = entry.getValue();
			if (value instanceof String) {
				result.put(entry.getKey(), parseScalar(resolve((String) value, expressions)));
			} else {
				result.put(entry.getKey(), value);
			}
		}
		return result;
	}

	private static String resolve(String value, ExpressionContext<?> context) {
		if (value == null || context == null) {
			return value;
		}

		Matcher matcher = EXPRESSION.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			Object resolved;
			if (matcher.group(3) != null) {
				String cacheKey = matcher.group(3).trim();
				String path = matcher.group(4) == null ? "" : matcher.group(4).trim();
				resolved = context.resolveCachedPath(cacheKey, path);
			} else {
				String source = matcher.group(1).trim();
				String key = matcher.group(2).trim();
				resolved = context.resolve(source, key);
			}
			matcher.appendReplacement(result,
					Matcher.quoteReplacement(resolved == null ? "" : String.valueOf(resolved)));
		}
		matcher.appendTail(result);
		return result.toString();
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

		private Object resolve(String source, String key) {
			String type = source == null ? "" : source.toLowerCase(Locale.ROOT);
			Object value;

			if (("param".equals(type) || "params".equals(type)) && info != null) {
				value = info.params.get(key);
			} else if ("cache".equals(type) && framework != null) {
				value = framework.cache(context, key);
			} else if (("env".equals(type) || "secret".equals(type) || "plain".equals(type)) && framework != null) {
				value = framework.value(context, key);
			} else {
				throw new IllegalStateException(
						"Unsupported JPostman data expression: jpostman:" + source + "[" + key + "]");
			}

			if (value == null) {
				throw new IllegalStateException(
						"JPostman data expression value not found: jpostman:" + source + "[" + key + "]");
			}
			return value;
		}

		private Object resolveCachedPath(String cacheKey, String path) {
			if (framework == null) {
				throw new IllegalStateException(
						"JPostman cached path expressions require an active framework context.");
			}

			Object cached = framework.cache(context, cacheKey);
			if (cached == null) {
				throw new IllegalStateException(
						"JPostman data expression cache value not found: jpostman:[" + cacheKey + "]" + path);
			}
			if (path == null || path.isBlank()) {
				return cached;
			}

			Object value = pathValue(cached, path);
			if (value == null) {
				throw new IllegalStateException(
						"JPostman data expression path not found: jpostman:[" + cacheKey + "]" + path);
			}
			return value;
		}

		private Object pathValue(Object cached, String path) {
			if (framework.contextType().isInstance(cached)) {
				Object value = frameworkPath(framework.contextType().cast(cached), path);
				if (value != null) {
					return value;
				}
			}

			Object reflected = invokePath(cached, path);
			if (reflected != null) {
				return reflected;
			}
			return mapPath(cached, path);
		}

		private Object frameworkPath(C cachedContext, String path) {
			try {
				return framework.path(cachedContext, path);
			} catch (RuntimeException | AssertionError e) {
				Object fallback = wildcardFallback(cachedContext, path);
				if (fallback != null) {
					return fallback;
				}
				throw e;
			}
		}

		private Object wildcardFallback(Object cached, String path) {
			String key = recursiveKey(path);
			if (key == null) {
				return null;
			}

			try {
				if (framework.contextType().isInstance(cached)) {
					return framework.path(framework.contextType().cast(cached), key);
				}
				return invokePath(cached, key);
			} catch (RuntimeException | AssertionError e) {
				return null;
			}
		}

		private Object invokePath(Object cached, String path) {
			try {
				java.lang.reflect.Method method = cached.getClass().getMethod("path", String.class);
				if (method.getReturnType() == Void.TYPE) {
					return null;
				}
				return method.invoke(cached, path);
			} catch (NoSuchMethodException e) {
				return null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				Object fallback = wildcardFallback(cached, path);
				if (fallback != null) {
					return fallback;
				}
				throw new IllegalStateException("Failed to resolve cached JPostman path: " + path, e);
			}
		}
	}

	private static String recursiveKey(String path) {
		if (path == null) {
			return null;
		}
		String normalized = path.trim();
		if (normalized.startsWith("/**/")) {
			String key = normalized.substring(4).trim();
			return key.isBlank() ? null : key;
		}
		return null;
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
				if (part.isBlank()) {
					continue;
				}
				if (!(current instanceof Map<?, ?>)) {
					return null;
				}
				current = ((Map<?, ?>) current).get(part);
			}
			return current;
		}
		return map.get(normalized);
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
				if (result != null) {
					return result;
				}
			}
		}
		if (value instanceof Iterable<?>) {
			for (Object nested : (Iterable<?>) value) {
				Object result = findRecursive(nested, key);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Parses a simple XML object into key/value pairs.
	 *
	 * <p>
	 * The root element is treated as a wrapper and each direct child element
	 * becomes a map entry. For example,
	 * {@code <product><title>Mouse</title></product>} becomes {@code title=Mouse}.
	 * Text values are converted using the same scalar parsing as map and JSON data.
	 * </p>
	 *
	 * @param xml      XML content to parse
	 * @param location source location used in error messages
	 * @param section  data section name used in error messages
	 * @return parsed key/value data
	 */
	private static Map<String, Object> parseXmlObject(String xml, String location, String section) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setIgnoringComments(true);
			factory.setCoalescing(true);
			disableExternalXmlAccess(factory);

			Document document = factory.newDocumentBuilder()
					.parse(new InputSource(new java.io.StringReader(xml == null ? "" : xml)));
			Element root = document.getDocumentElement();
			if (root == null) {
				return new LinkedHashMap<>();
			}
			return childElements(root);
		} catch (Exception e) {
			throw new IllegalStateException(
					"Failed to parse XML JPostman data section [" + section + "] from " + location, e);
		}
	}

	/**
	 * Converts direct child elements of the supplied XML element into map entries.
	 *
	 * @param parent XML parent element
	 * @return direct child element names and parsed values
	 */
	private static Map<String, Object> childElements(Element parent) {
		Map<String, Object> result = new LinkedHashMap<>();
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node.getNodeType() != Node.ELEMENT_NODE) {
				continue;
			}

			Element child = (Element) node;
			result.put(child.getTagName(), xmlValue(child));
		}
		return result;
	}

	/**
	 * Converts an XML element value into either a nested map or a scalar value.
	 *
	 * @param element XML element
	 * @return nested map when the element has child elements, otherwise parsed text
	 */
	private static Object xmlValue(Element element) {
		Map<String, Object> nested = childElements(element);
		if (!nested.isEmpty()) {
			return nested;
		}
		return parseScalar(element.getTextContent());
	}

	/**
	 * Disables external XML access when the XML parser supports those features.
	 *
	 * <p>
	 * Unsupported parser features are ignored so the loader remains compatible
	 * across different JDK XML parser implementations.
	 * </p>
	 *
	 * @param factory XML document builder factory
	 */
	private static void disableExternalXmlAccess(DocumentBuilderFactory factory) {
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
		} catch (Exception ignored) {
			// Feature is optional across XML parser implementations.
		}
		try {
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
		} catch (Exception ignored) {
			// Feature is optional across XML parser implementations.
		}
		try {
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
		} catch (Exception ignored) {
			// Feature is optional across XML parser implementations.
		}
		try {
			factory.setXIncludeAware(false);
		} catch (UnsupportedOperationException ignored) {
			// Feature is optional across XML parser implementations.
		}
		try {
			factory.setExpandEntityReferences(false);
		} catch (Exception ignored) {
			// Feature is optional across XML parser implementations.
		}
	}

	private static Object parseScalar(String value) {
		if (value == null) {
			return "";
		}
		String text = value.trim();
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

	private static final class JsonObjectParser {
		private final String json;
		private final String location;
		private final String section;
		private int index;

		private JsonObjectParser(String json, String location, String section) {
			this.json = json == null ? "" : json;
			this.location = location;
			this.section = section;
		}

		private Map<String, Object> parseObject() {
			skipWhitespace();
			expect('{');
			Map<String, Object> result = new LinkedHashMap<>();
			skipWhitespace();
			if (peek('}')) {
				index++;
				return result;
			}
			while (index < json.length()) {
				String key = parseString();
				skipWhitespace();
				expect(':');
				Object value = parseValue();
				result.put(key, value);
				skipWhitespace();
				if (peek(',')) {
					index++;
					skipWhitespace();
					continue;
				}
				expect('}');
				return result;
			}
			throw error("JSON object is not closed");
		}

		private Object parseValue() {
			skipWhitespace();
			if (peek('"')) {
				return parseString();
			}
			if (peek('{')) {
				return parseObject();
			}
			if (peek('[')) {
				return parseArray();
			}
			String token = parseToken();
			return parseScalar(token);
		}

		private List<Object> parseArray() {
			expect('[');
			List<Object> result = new ArrayList<>();
			skipWhitespace();
			if (peek(']')) {
				index++;
				return result;
			}
			while (index < json.length()) {
				result.add(parseValue());
				skipWhitespace();
				if (peek(',')) {
					index++;
					skipWhitespace();
					continue;
				}
				expect(']');
				return result;
			}
			throw error("JSON array is not closed");
		}

		private String parseString() {
			expect('"');
			StringBuilder result = new StringBuilder();
			while (index < json.length()) {
				char ch = json.charAt(index++);
				if (ch == '"') {
					return result.toString();
				}
				if (ch == '\\') {
					if (index >= json.length()) {
						throw error("Invalid escape sequence");
					}
					char escaped = json.charAt(index++);
					switch (escaped) {
					case '"':
					case '\\':
					case '/':
						result.append(escaped);
						break;
					case 'b':
						result.append('\b');
						break;
					case 'f':
						result.append('\f');
						break;
					case 'n':
						result.append('\n');
						break;
					case 'r':
						result.append('\r');
						break;
					case 't':
						result.append('\t');
						break;
					default:
						throw error("Unsupported escape sequence: \\" + escaped);
					}
					continue;
				}
				result.append(ch);
			}
			throw error("JSON string is not closed");
		}

		private String parseToken() {
			int start = index;
			while (index < json.length()) {
				char ch = json.charAt(index);
				if (Character.isWhitespace(ch) || ch == ',' || ch == '}' || ch == ']') {
					break;
				}
				index++;
			}
			if (start == index) {
				throw error("Expected JSON value");
			}
			return json.substring(start, index);
		}

		private void skipWhitespace() {
			while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
				index++;
			}
		}

		private boolean peek(char expected) {
			return index < json.length() && json.charAt(index) == expected;
		}

		private void expect(char expected) {
			skipWhitespace();
			if (!peek(expected)) {
				throw error("Expected '" + expected + "'");
			}
			index++;
		}

		private IllegalStateException error(String message) {
			return new IllegalStateException(message + " while parsing JPostman data section [" + section + "] from "
					+ location + " at position " + index);
		}
	}
}
