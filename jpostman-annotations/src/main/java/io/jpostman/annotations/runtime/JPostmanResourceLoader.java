package io.jpostman.annotations.runtime;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Shared resource and property loading helpers for annotation runtime classes.
 */
final class JPostmanResourceLoader {

	private JPostmanResourceLoader() {
	}

	static Properties loadProperties(String location, Class<?> testClass) throws IOException {
		Properties properties = new Properties();

		if (location == null || location.isBlank()) {
			return properties;
		}

		try (InputStream input = open(location, testClass)) {
			properties.load(input);
		} catch (IOException e) {
			if (!"classpath:jpostman.properties".equals(location)) {
				throw e;
			}
		}

		return properties;
	}

	static InputStream open(String location, Class<?> testClass) throws IOException {
		String value = location == null ? "" : location.trim();
		if (value.isBlank()) {
			throw new IllegalArgumentException("Resource location must not be blank.");
		}

		if (value.startsWith("classpath:")) {
			String path = value.substring("classpath:".length());
			if (path.startsWith("/")) {
				path = path.substring(1);
			}

			InputStream input = testClass.getClassLoader().getResourceAsStream(path);
			if (input == null) {
				throw new IOException("Classpath resource not found: " + value);
			}
			return input;
		}

		try {
			return new FileInputStream(value);
		} catch (FileNotFoundException e) {
			InputStream input = testClass.getClassLoader().getResourceAsStream(value);
			if (input != null) {
				return input;
			}
			throw e;
		}
	}

	static String property(Properties properties, String key, String namespace) {
		return properties.getProperty(propertyKey(key, namespace), "");
	}

	static String propertyKey(String key, String namespace) {
		return namespace == null || namespace.isBlank() ? key : key + "." + namespace;
	}

	static String firstNonBlank(String first, String second) {
		if (first != null && !first.isBlank()) {
			return first;
		}
		return second == null ? "" : second;
	}
}
