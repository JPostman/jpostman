# JPostman REST Assured Executor

Use this when your project already uses REST Assured or you want REST Assured request execution.

Repository: https://github.com/JPostman/jpostman/tree/main/jpostman-restassured

## Maven Dependencies

Use `jpostman-annotations` plus this executor module. The annotations module loads and coordinates tests. The executor module sends the actual HTTP requests.

```xml
<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-annotations</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>io.github.jpostman</groupId>
    <artifactId>jpostman-restassured</artifactId>
    <version>REPLACE_WITH_LATEST_VERSION</version>
    <scope>test</scope>
</dependency>
```

## Minimal Runner

```java
import io.jpostman.annotations.JPostman;

@JPostman.TestNG
public class ApiRunnerTest {

    @JPostman.Context(
            collection = "classpath:DummyJSON.all_product_collection.json",
            executor = "io.jpostman.restassured.RestAssuredExecutor"
    )
    private JPostman.Runtime<JPostman.Test> jpostman;

    @org.testng.annotations.Test
    @JPostman.Runner
    public void runCollection() {
    }
}
```

## Session Mode

Use `session = true` when the executor supports reusable state, such as cookies or authentication state.

```java
@JPostman.Context(
        collection = "classpath:DummyJSON.all_product_collection.json",
        executor = "io.jpostman.restassured.RestAssuredExecutor",
        session = true
)
private JPostman.Runtime<JPostman.Test> jpostman;
```
