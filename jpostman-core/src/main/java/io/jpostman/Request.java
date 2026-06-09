package io.jpostman;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents an HTTP request.
 * <p>
 * A request contains the method, URL, headers, body, authentication, and other
 * request data used by executors. It also implements {@link RequestProvider} so
 * it can be passed directly to executors that accept request providers.
 * </p>
 */
public final class Request implements RequestProvider {

	private static final Logger log = LoggerFactory.getLogger(Request.class);

	private final String name;
	private final String method;
	private final String folderName;
	private final String description;
	private final Url url;
	private final Header header;
	private final Body body;
	private final Auth auth;

	private Request(String name, String method, String folderName, String description, Url url, Header header,
			Body body, Auth auth) {
		this.name = name;
		this.method = method;
		this.url = url;
		this.folderName = folderName;
		this.description = description;
		this.header = header;
		this.body = body;
		this.auth = auth;
	}

	// -------------------------------------------------------------------------
	// Factory
	// -------------------------------------------------------------------------

	/**
	 * Build a {@link Request} from a Postman v2.1 {@code request} JSON object.
	 *
	 * @param name       the item name from the collection
	 * @param folderName parent folder name, or {@code "(root)"} for top-level items
	 * @param reqObj     the {@code request} object inside the item JSON
	 */
	public static Request from(String name, String folderName, JsonObject reqObj) {
		String method = reqObj.has("method") ? reqObj.get("method").getAsString() : "GET";
		String desc = extractDescription(reqObj);
		Url url = Url.from(reqObj);
		Header header = Header.from(reqObj);
		Body body = Body.from(reqObj);
		Auth auth = Auth.from(reqObj);

		return new Request(name, method, folderName, desc, url, header, body, auth);
	}

	/** Description may be a plain string or a {@code {content, type}} object. */
	private static String extractDescription(JsonObject reqObj) {
		if (!reqObj.has("description") || reqObj.get("description").isJsonNull()) {
			return "";
		}
		JsonElement el = reqObj.get("description");
		if (el.isJsonPrimitive()) {
			return el.getAsString();
		}
		if (el.isJsonObject()) {
			JsonObject obj = el.getAsJsonObject();
			JsonElement content = obj.get("content");
			return content != null && !content.isJsonNull() && content.isJsonPrimitive() ? content.getAsString() : "";
		}
		return "";
	}

	// -------------------------------------------------------------------------
	// Accessors
	// -------------------------------------------------------------------------

	/** @return request display name from the Postman item. */
	public String getName() {
		return name;
	}

	/** @return HTTP method, for example {@code GET} or {@code POST}. */
	public String getMethod() {
		return method;
	}

	/** @return parent folder name, or {@code "(root)"} for root-level requests. */
	public String getFolderName() {
		return folderName;
	}

	/** @return request description, or an empty string when none is defined. */
	public String getDescription() {
		return description;
	}

	/** @return resolved URL string including enabled query parameters. */
	public String toUrl() {
		return url.toString();
	}

	/** @return parsed URL object. */
	public Url getUrl() {
		return url;
	}

	/** @return parsed request headers. */
	public Header getHeader() {
		return header;
	}

	/** @return parsed request authentication configuration. */
	public Auth getAuth() {
		return auth;
	}

	/** @return parsed request body. */
	public Body getBody() {
		return body;
	}

	@Override
	public Request build() {
		return this;
	}

	/**
	 * Returns all unresolved {@code {{token}}} names used by this request.
	 *
	 * <p>
	 * The returned map preserves discovery order and initializes every token value
	 * to an empty string, so callers can fill only the values they need.
	 * </p>
	 *
	 * @return ordered token map, for example {@code {base_url="", token=""}}
	 */
	public Map<String, String> params() {
		Map<String, String> result = new LinkedHashMap<>();
		Params.addTokens(result, url.getOriginal());
		Params.addTokens(result, header.getParams());
		Params.addTokens(result, auth.getParams());
		Params.addTokens(result, body.getRaw());
		return result;
	}

	/**
	 * Returns this request's unresolved token map filled from enabled environment
	 * parameters when matching keys exist. Missing or disabled parameters remain
	 * mapped to an empty string.
	 *
	 * @param env environment used to fill matching token values; may be
	 *            {@code null}
	 * @return ordered token map with environment values applied
	 */
	public Map<String, String> resolve(Environment env) {
		Map<String, String> result = params();
		if (env != null) {
			env.resolve(result);
		}
		return result;
	}

	/**
	 * Returns a {@link RequestBuilder} pre-populated from this request. Override
	 * URL/query parameters, headers, body, or auth parameters before calling
	 * {@link RequestBuilder#build()}.
	 */
	public RequestBuilder builder() {
		return new RequestBuilder(this);
	}

	// -------------------------------------------------------------------------
	// RequestBuilder
	// -------------------------------------------------------------------------

	/**
	 * Fluent builder wrapping a copied {@link Request}. Use {@link #headers()},
	 * {@link #body()}, {@link #auth()} to enter sub-builders, then call
	 * {@code .done()} to return here, and finally {@link #build()} to get the
	 * immutable {@link Request}.
	 */
	public static class RequestBuilder {

		private final String name;
		private final String method;
		private final String folderName;
		private final String description;
		private final Params<Url> urlBuilder;
		private final Params<Header> headerBuilder;
		private final Params<Auth> authBuilder;
		private final Params<Body> bodyBuilder;

		private RequestBuilder(Request req) {
			this.name = req.name;
			this.method = req.method;
			this.folderName = req.folderName;
			this.description = req.description;
			this.urlBuilder = req.url.builder();
			this.headerBuilder = req.header.builder();
			this.authBuilder = req.auth.builder();
			this.bodyBuilder = req.body.builder();
		}

		/**
		 * Enters the URL/query-parameter builder step.
		 *
		 * <pre>{@code
		 * request.builder().url().set("limit", 25).end().build();
		 * }</pre>
		 */
		public ParamStep url() {
			return new ParamStep(urlBuilder);
		}

		/**
		 * Configures the URL/query parameters using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().url(u -> u.set("limit", 25)).build(env);
		 * }</pre>
		 *
		 * @param customizer URL customization callback
		 * @return current request builder
		 */
		public RequestBuilder url(Consumer<ParamStep> customizer) {
			customizer.accept(url());
			return this;
		}

		/**
		 * Configures auth using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().auth().set("username", "jpostman").set("password", "secret").end().build();
		 * }</pre>
		 */
		public ParamStep auth() {
			return new ParamStep(authBuilder);
		}

		/**
		 * Configures auth using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().auth(c -> c.set("username", "jpostman").set("password", "secret")).build();
		 * }</pre>
		 */
		public RequestBuilder auth(Consumer<ParamStep> customizer) {
			customizer.accept(auth());
			return this;
		}

		/**
		 * Configures headers using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().headers().set("Authorization", token).set("Content-Type", "application/json").end()
		 * 		.build();
		 * }</pre>
		 */
		public ParamStep headers() {
			return new ParamStep(headerBuilder);
		}

		/**
		 * Configures headers using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().headers(c -> c.set("Authorization", token).set("Content-Type", "application/json"))
		 * 		.build();
		 * }</pre>
		 */
		public RequestBuilder headers(Consumer<ParamStep> customizer) {
			customizer.accept(headers());
			return this;
		}

		/**
		 * Configures body using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().body().set("username", "jpostman").set("password", "secret").end().build();
		 * }</pre>
		 */
		public ParamStep body() {
			return new ParamStep(bodyBuilder);
		}

		/**
		 * Configures body using a lambda-style nested builder.
		 *
		 * <pre>{@code
		 * request.builder().body(c -> c.set("username", "jpostman").set("password", "secret")).build();
		 * }</pre>
		 */
		public RequestBuilder body(Consumer<ParamStep> customizer) {
			customizer.accept(body());
			return this;
		}

		/** Builds and returns the final {@link Request}. */
		public Request build() {
			return new Request(name, method, folderName, description, urlBuilder.end(), headerBuilder.end(),
					bodyBuilder.end(), authBuilder.end());
		}

		/**
		 * Resolves all {@code {{variable}}} tokens in URL, headers, body, and auth
		 * parameters using the given environment's enabled variable map.
		 *
		 * @param env environment used for variable substitution; may be {@code null}
		 * @return built request
		 */
		public Request build(Environment env) {
			Map<String, String> vars = env == null ? Map.of() : env.getParams();
			urlBuilder.resolve(vars);
			headerBuilder.resolve(vars);
			authBuilder.resolve(vars);
			bodyBuilder.resolve(vars);
			return build();
		}

		/**
		 * Nested builder step used for headers, queries, body, and auth.
		 */
		public class ParamStep {
			private final Params<?> delegate;

			ParamStep(Params<?> delegate) {
				this.delegate = delegate;
			}

			/** Adds or replaces a parameter without requiring it to exist first. */
			public ParamStep add(String key, Object value) {
				delegate.add(key, value);
				return this;
			}

			/** Updates an existing parameter and throws when the key is missing. */
			public ParamStep set(String key, Object value) {
				delegate.set(key, value);
				return this;
			}

			/** Returns to the parent request builder without resolving local variables. */
			public RequestBuilder end() {
				return RequestBuilder.this;
			}

			/**
			 * Resolves this request part using local key/value pairs and returns to the
			 * parent request builder.
			 *
			 * @param values alternating key/value pairs
			 * @return parent request builder
			 */
			public RequestBuilder map(Object... values) {
				delegate.map(values);
				return RequestBuilder.this;
			}

			/**
			 * Resolves this request part using local key/value pairs and returns to the
			 * parent request builder. String values are JSON-stringified.
			 *
			 * @param values alternating key/value pairs
			 * @return parent request builder
			 */
			public RequestBuilder json(Object... values) {
				delegate.json(values);
				return RequestBuilder.this;
			}

			/**
			 * Resolves this request part with local variables, then returns to the parent
			 * request builder. Values resolved here take priority over later request-level
			 * resolution from {@code build(env)}.
			 *
			 * @param vars local variables used only for this request part
			 * @return parent request builder
			 */
			public RequestBuilder end(Map<String, ?> vars) {
				delegate.end(vars);
				return RequestBuilder.this;
			}
		}
	}

	// -------------------------------------------------------------------------
	// Print
	// -------------------------------------------------------------------------

	/**
	 * Logs detailed multi-line output including description, auth, headers, and
	 * body at TRACE level.
	 */
	public void print() {
		log.trace(log());
	}

	/** Returns verbose diagnostic representation including details. */
	public String log() {
		StringBuilder sb = new StringBuilder();
		sb.append(toString());
		if (!description.isEmpty())
			sb.append("\nDescription: " + description);
		if (!auth.isNoAuth())
			sb.append("\nAuth: " + auth);
		if (!header.isEmpty())
			sb.append("\nHeaders:\n" + header);
		sb.append("\nBody: " + body);
		return sb.toString();
	}

	@Override
	public String toString() {
		return String.format("[%-6s] %-40s -> %s", method, name, url);
	}
}
