# jpostman-junit

JUnit 5 helper module for JPostman secure API tests.

## Features

- `JUnitContext` wrapper around `SecureContext`
- Hard assertions with `asserts()`
- Soft assertions with `soft()` and `assertAll()`
- `verify()` and `verify(int)` response verification
- Optional secure log context on assertion failure
- Shared cache helpers with JUnit test-abort behavior for cached failures
- Full secure delegate API, including policy loading, redaction, filtering, `filterList`, and response path helpers

## Example

```java
private String accessToken() {
	return base.cache(() -> {
		JUnitContext cxt = base.copy()
				.request(col.getRequest("Login user and get tokens"));

		cxt.response(RestAssuredExecutor.execute(cxt.request()));
		cxt.asserts().exists("accessToken", "Access token not found");
		return String.valueOf(cxt.path("accessToken"));
	});
}

@Test
void getCurrentAuthUser() {
	accessToken();

	JUnitContext cxt = base.loadRules("user")
			.filter("id", "firstName", "lastName", "gender");

	cxt.request(col.getRequest("Get current auth user"))
			.response(RestAssuredExecutor.apply(cxt.request())
					.auth().oauth2(cxt.asString("ACCESS_TOKEN")));

	cxt.verify();
}
```
