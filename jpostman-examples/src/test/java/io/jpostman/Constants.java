package io.jpostman;

import java.io.InputStream;

public class Constants {
	public static final String TEST_USERNAME  = "TEST_USER";
	public static final String ENV_TOKEN_KEY  = "ENV_TOKEN";
	public static final String PRODUCT_FOLDER = "Product";
	public static final String IMAGE_FOLDER   = "Dynamic Image";

	public static final String LOGIN_GET_TOKEN = "Login user and get tokens";
	public static final String GET_AUTH_USER   = "Get current auth user";
	public static final String ALL_PRODUCTS    = "Get all products";
	public static final String SINGLE_PRODUCT  = "Get a single product";
	public static final String GENERATE_IMAGE  = "Generate image with custom text";

	public static final String COLLECTION_FILE = "DummyJSON.postman_collection.json";
	public static final String ENVIRONMENT_FILE = "DummyJSON.postman_environment.json";

	public static final InputStream COLLECTION = Constants.class.getClassLoader().getResourceAsStream(COLLECTION_FILE);
	public static final InputStream ENVIRONMENT = Constants.class.getClassLoader().getResourceAsStream(ENVIRONMENT_FILE);
}
