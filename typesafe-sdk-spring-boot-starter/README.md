# TypeSafe SDK Spring Boot Starter

Auto-configures a [`TypeSafeClient`](../typesafe-sdk) bean from `typesafe.*` properties so a Spring Boot application can
inject it anywhere. Nothing else: no web endpoints, no actuator health indicator, no metrics.

Compiled against Spring Boot 3.1 and Java 17. Works with any Spring Boot 3.x; Spring Boot 4 is untested.

## Install

Gradle:

```kotlin
implementation("io.github.premo-cloud:typesafe-sdk-spring-boot-starter:0.5.1")
```

Maven:

```xml
<dependency>
  <groupId>io.github.premo-cloud</groupId>
  <artifactId>typesafe-sdk-spring-boot-starter</artifactId>
  <version>0.5.1</version>
</dependency>
```

The starter brings `typesafe-sdk` in transitively. You do not need to declare it separately.

## Configure

```properties
typesafe.api-key=${TYPESAFE_API_KEY}
```

That single line is enough. Setting the `TYPESAFE_API_KEY` environment variable with no property at all also works, because
Spring maps `TYPESAFE_API_KEY` onto `typesafe.api-key` automatically. The same holds for every property below.

| Property | Environment variable | Default | Purpose |
|---|---|---|---|
| `typesafe.api-key` | `TYPESAFE_API_KEY` | none, required | Bearer token. The bean is created only when this is non-blank, so `${TYPESAFE_API_KEY:}` is safe on machines without the variable. |
| `typesafe.base-url` | `TYPESAFE_BASE_URL` | `https://api.typesafe.ai` | API root. |
| `typesafe.default-model` | `TYPESAFE_DEFAULT_MODEL` | `jev-latest` | Model for requests that do not name one. |
| `typesafe.timeout` | `TYPESAFE_TIMEOUT` | `10s` | Per-attempt timeout, as a Spring duration such as `30s` or `PT1M`. |

Properties, YAML, environment variables, command-line arguments, and config servers all work, with Spring's usual
precedence. IDEs offer completion for these keys from the generated configuration metadata.

## Use

```java
@Service
public class TicketTriage {

    private final TypeSafeClient typeSafeClient;

    public TicketTriage(TypeSafeClient typeSafeClient) {
        this.typeSafeClient = typeSafeClient;
    }

    public String department(String ticket) {
        TypeSafeResponse response = typeSafeClient.systemOne(
                Map.of("ticket", ticket),
                Map.of("department", Choice.of("Which team should handle `ticket`?", "billing", "technical", "other")));
        return response.choice("department").choice();
    }
}
```

The client is immutable and thread-safe, so the singleton bean is the right scope. See the
[core README](../README.md) for the request and question API.

## What the auto-configuration does

- Registers a `TypeSafeClient` bean named `typeSafeClient` when `typesafe.api-key` is set.
- Backs off if the application already defines a `TypeSafeClient` bean, so a hand-built client wins.
- Reuses the application's `ObjectMapper` when one is present, so custom serializers for your state records apply. The
  SDK copies it and disables `FAIL_ON_UNKNOWN_PROPERTIES` on its copy; your mapper is not modified.
- Binds `TypeSafeProperties`, which you can inject to read the effective settings.

## Customize

Anything the properties do not cover, such as the retry policy, extra headers, a proxy, or a custom `HttpClient`, is a
matter of defining the bean yourself. The starter then steps aside.

```java
@Configuration
class TypeSafeConfig {

    @Bean
    TypeSafeClient typeSafeClient(TypeSafeProperties properties, ObjectMapper objectMapper) {
        return TypeSafeClient.builder()
                .apiKey(properties.getApiKey())
                .defaultModel(properties.getDefaultModel())
                .timeout(properties.getTimeout())
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(5).backoffMax(Duration.ofSeconds(20))))
                .header("X-Team", "support")
                .httpClient(HttpClient.newBuilder().proxy(ProxySelector.of(new InetSocketAddress("proxy", 3128))).build())
                .objectMapper(objectMapper)
                .build();
    }
}
```

Per-call overrides need no configuration at all: pass `RequestOptions.of(o -> o.timeout(...).maxRetries(0))` to any
`systemOne` call.

## Test

Unit tests of your own services should mock the client:

```java
@MockBean
TypeSafeClient typeSafeClient;
```

To test the auto-configuration itself, or a slice that depends on it, `ApplicationContextRunner` works without a real key:

```java
new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class))
        .withPropertyValues("typesafe.api-key=test")
        .run(context -> assertThat(context).hasSingleBean(TypeSafeClient.class));
```

No request leaves the JVM until `systemOne` is called, so constructing the bean with a fake key is safe.

## Troubleshooting

- **`No qualifying bean of type TypeSafeClient`.** `typesafe.api-key` is not set in any property source. Check the
  spelling and that the environment variable is visible to the JVM.
- **`TypeSafeAuthenticationException: 401 ...`.** The key is set but rejected. Confirm it is a TypeSafe key and has no
  surrounding quotes or whitespace; the SDK trims values read from the environment but not from properties.
- **`TypeSafeTimeoutException`.** Raise `typesafe.timeout` or set a per-call `RequestOptions` timeout. The default is 10 s
  per attempt, and the default policy retries twice.
- **Wrong model answered.** `typesafe.default-model` applies only to requests that do not set `TypeSafeRequest.model(...)`.

## License

MIT, Copyright (c) 2026 Garret Premo. See [LICENSE](../LICENSE).
