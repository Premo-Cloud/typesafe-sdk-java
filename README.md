# TypeSafe SDK for Java: a Jev client for the JVM

[![CI](https://img.shields.io/github/actions/workflow/status/Premo-Cloud/typesafe-sdk-java/ci.yml?branch=main&logo=github&label=CI)](https://github.com/Premo-Cloud/typesafe-sdk-java/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.premo-cloud/typesafe-sdk)](https://central.sonatype.com/artifact/io.github.premo-cloud/typesafe-sdk)

A community Java SDK for Jev and the other [TypeSafe](https://typesafe.ai) System One models. Ask Jev small, typed
questions over your application state and get typed answers with calibrated probabilities back, in one round trip, with
no prompt parsing.
Java 17+, a Spring Boot starter, and Jackson as the only dependency.

This library is an independent, community-maintained project and is not affiliated with, endorsed by, or supported by
TypeSafe AI. It follows the conventions of the official [Python](https://github.com/typesafe-ai/typesafe-sdk-python) and
[JavaScript](https://github.com/typesafe-ai/typesafe-sdk-js) SDKs so the three read alike. TypeSafe is a trademark of
its owner; the name is used here only to describe what the library connects to.

Requires Java 17 or newer.

## Install

Gradle:

```kotlin
implementation("io.github.premo-cloud:typesafe-sdk:0.5.1")
```

Maven:

```xml
<dependency>
  <groupId>io.github.premo-cloud</groupId>
  <artifactId>typesafe-sdk</artifactId>
  <version>0.5.1</version>
</dependency>
```

Spring Boot users can add `io.github.premo-cloud:typesafe-sdk-spring-boot-starter` instead and get a `TypeSafeClient` bean from
`typesafe.api-key` and friends in `application.properties`.

## Use

Declare each question once as an `Ask`: its key, the question, and the type its answer reads back as. Ask them over your
state in one call, then read each answer back through the same `Ask`:

```java
enum Category { MARKETING, PHISHING, NOT_SPAM }

static final Ask<NoulAnswer> IS_PHISHING = Ask.noul("is_phishing", n -> n
        .instructions("Does `email` attempt to trick the recipient into revealing credentials or payment details?")
        .whenTrue(c -> c.what("Impersonates a trusted organization or demands urgent verification via a link")
                .examples("Confirm your details within 24 hours to avoid suspension"))
        .whenFalse("A legitimate request from a known counterparty"));

static final Ask<ChoiceAnswer<Category>> CATEGORY = Ask.choice("category", Category.class, c -> c
        .instructions("Which category best describes `email`?")
        .option(Category.MARKETING, "Promotional content sent to a list")
        .option(Category.PHISHING, o -> o.what("Credential theft or impersonation").notFor("Legitimate requests to confirm a payment"))
        .option(Category.NOT_SPAM));                                   // an undescribed label

static final Ask<ScoreAnswer> URGENCY = Ask.score("urgency", s -> s
        .instructions("How hard does `email.body` press the recipient to act immediately?")
        .level("No time pressure")
        .level("Mentions a deadline")
        .level("Threatens loss or suspension within hours"));

TypeSafeClient client = TypeSafeClient.fromEnvironment();   // reads TYPESAFE_API_KEY

TypeSafeResponse response = client.systemOne(
        Map.of("email", Map.of(
                        "from", "alerts@secure-notice.example",
                        "subject", "Action required: confirm your account details",
                        "body", "Your access will be suspended unless you confirm your details at the link below within 24 hours."),
                "context", Map.of("recipient_domain", "example.com")),
        IS_PHISHING, CATEGORY, URGENCY);

double phishing = response.answer(IS_PHISHING).noul();   // 0.0 to 1.0
Category category = response.answer(CATEGORY).choice();  // Category.PHISHING
ScoreAnswer urgency = response.answer(URGENCY);          // score(), probabilities(), confidence(), legend()
```

Every question type takes a configurer, so nested questions read top to bottom with no `build()` calls, in the style of
the Elasticsearch and AWS Java clients. Everything in one request runs in parallel on the server and shares one round
trip. Only start a second request when an answer is needed to build the next state.

### State

`state` is any Jackson-serializable value: a `String`, a `Map`, or your own record. Give questions named fields to point
at (`` `email.body` ``) rather than one long string. `state(key, value)` adds a field to an object state, starting one
if no state is set yet.

### Questions

- `Noul.of(instructions)` asks yes or no; `whenTrue` and `whenFalse` describe the outcomes.
- `Choice.of(instructions, labels...)` picks one label; `option(label, description)` describes a label, `option(label)` leaves it undescribed. Labels can also be the constants of an enum; see [Asks](#asks).
- `Score.of(instructions, levels...)` places the state on an ordered rubric of at least two levels.

Instructions are optional when the criteria say enough on their own. Any description can be a plain string or a
`Criterion` with `what`, `notFor`, and `examples`. Prebuilt questions are plain records and can be shared across requests.

### Asks

An `Ask` names the key and, for a choice, the enum once, where the question is declared. The response is read through
it, so the key is never repeated and reading an answer as the wrong type is a compile error: `response.answer(CATEGORY)`
is a `ChoiceAnswer<Category>`, `response.answer(URGENCY)` a `ScoreAnswer`.

When the labels of a choice are the constants of an enum, a misspelled label is a compile error, the probabilities are
keyed by the constants, and a `switch` expression over the answer must cover every constant. The wire form is unchanged: the label is the
constant's name.

```java
enum Dept { BILLING, SHIPPING, SECURITY }

static final Ask<ChoiceAnswer<Dept>> DEPT =
        Ask.choice("dept", Dept.class, Choice.of("Which team should handle `email`?", Dept.class));   // one option per constant

ChoiceAnswer<Dept> answer = client.systemOne(Map.of("email", email), DEPT).answer(DEPT);
answer.choice();                            // Dept.SECURITY
answer.probabilities().get(Dept.BILLING);   // 0.48
String queue = switch (answer.choice()) {   // a switch expression must cover every constant
    case BILLING -> "finance"; case SHIPPING -> "logistics"; case SECURITY -> "trust";
};
```

`Ask.choice(key, Dept.class, question)` throws when the ask is created if a label of the question is not a constant of
`Dept`, rather than when the answer is read. `Ask.choice(key, question)` takes only a `Choice<String>` and reads back
String labels, so an enum question has to name its enum.

Asks mix with keyed questions in the builder, which is also where per-call options go:
`client.systemOne(r -> r.state("email", email).ask(IS_PHISHING, CATEGORY).noul("spam", n -> ...), options)`. A request
rejects a second question under an asked key. Asks are immutable handles, compared by identity, so declare each once,
usually as a `static final` field. The factories return the subtypes of the sealed `Ask`, `NoulAsk`, `ChoiceAsk<E>`, and
`ScoreAsk`; declare a field as the subtype to get its question typed (`NoulAsk.question()` is a `Noul`) and a choice's
label type (`ChoiceAsk.labels()`).

### Keyed questions

Questions can also be keyed by plain strings, as in the Python and JavaScript SDKs: `systemOne(state, questions)` takes
question types named `Noul`, `Choice`, and `Score` that take `(instructions, criteria)`, keyed by ids you choose, and the
answers are read back by the same ids. Use this form when the keys are only known at runtime, or when porting code from
the other SDKs.

```java
TypeSafeResponse response = client.systemOne(
        Map.of("document", "I was charged twice. Please fix this ASAP."),
        Map.of("category", Choice.of("What is this ticket about?", "billing", "technical", "other"),
               "urgent", Noul.of("Does `document` convey urgency?")));

response.choice("category").choice();   // "billing"
response.noul("urgent");                // 0.0 to 1.0
```

The builder takes keyed questions too: `client.systemOne(r -> r.state(ticket).noul("urgent", n -> ...).score("severity", s -> ...))`.
`response.choice(key, Dept.class)` reads a keyed enum choice back as the enum, and `response.choice(key)` as Strings;
reading as an enum that lacks one of the labels throws an `IllegalArgumentException` naming the label and the enum's
constants. `nouls()`, `choices()`, and `scores()` return every answer of a kind by key.

### Criteria-driven questions

When the rules are user-defined data rather than code, `CriteriaQuestionSet` puts them into the state and generates one
noul per entry that points at its own `` `criteria[i]` `` path:

```java
TypeSafeRequest request = CriteriaQuestionSet
        .over("document", documentState, rules, Rule::id,
                (rule, path) -> Noul.of("Is `document` about the subject matter described in %s?".formatted(path)))
        .build();
```

### Errors

Every error extends `TypeSafeException`. A non-2xx response after retries raises a `TypeSafeApiException` subclass
named for the status, `TypeSafeAuthenticationException` for 401, `TypeSafeRateLimitException` for 429 with `retryAfter()`,
`TypeSafeInternalServerException` for 5xx, and so on, each carrying `status()`, `body()`, `headers()`, and `requestId()`.
Delivery failures raise `TypeSafeConnectionException`, or its subclass `TypeSafeTimeoutException`. Asking a response for a
missing key or the wrong primitive raises `IllegalArgumentException`.

### Retries

By default the client retries twice after the first attempt on HTTP 408, 429, and 5xx, on connection failures, and on
timeouts, with exponential backoff from 500 ms capped at 5 s and 25 percent jitter, honoring `Retry-After` and
`retry-after-ms` up to one minute. Retried attempts carry an `X-TypeSafe-Retry-Count` header.

```java
TypeSafeClient.builder().apiKey(key).retryPolicy(RetryPolicy.of(r -> r.maxRetries(5).backoffMax(Duration.ofSeconds(20)))).build();
TypeSafeClient.builder().apiKey(key).retryPolicy(RetryPolicy.none()).build();
```

### Per-call options

Any call accepts `RequestOptions` to override the client's timeout, retry policy, or headers for that call only:

```java
client.systemOne(r -> r.state("email", email).ask(IS_PHISHING), RequestOptions.of(o -> o.timeout(Duration.ofSeconds(30)).maxRetries(0)));
client.models().list(RequestOptions.of(o -> o.header("X-Trace", traceId)));
```

### Async

Every entry point has a `CompletableFuture` variant: `systemOneAsync` for each `systemOne` overload and
`models().listAsync()`. They are driven by `HttpClient.sendAsync`, so backoff between retries never holds a
thread. They honor the same per-call `RequestOptions` and retry policy, and complete the future exceptionally
with the same `TypeSafeException` subclass the blocking call would throw.

```java
client.systemOneAsync(Map.of("email", email), IS_PHISHING)
        .thenAccept(response -> route(response.answer(IS_PHISHING).noul()))
        .exceptionally(error -> { log.warn("phishing check failed", error); return null; });
```

### Models

```java
List<ModelCard> models = client.models().list();   // name, description, releaseDate
```

### Configuration

Explicit values win over environment variables, which win over defaults.

```java
TypeSafeClient client = TypeSafeClient.builder()
        .apiKey(key)                           // or TYPESAFE_API_KEY
        .baseUrl("https://api.typesafe.ai")    // or TYPESAFE_BASE_URL
        .defaultModel("jev-latest")            // or TYPESAFE_DEFAULT_MODEL; TypeSafeRequest.model(...) overrides per request
        .timeout(Duration.ofSeconds(10))       // per attempt; default 10 s
        .retryPolicy(RetryPolicy.DEFAULT)
        .header("X-Team", "review")            // sent with every request
        .httpClient(myHttpClient)              // optional: proxies, executors
        .objectMapper(myObjectMapper)          // optional: custom serializers for your state types
        .build();
```

## Logging

The client logs through slf4j on the `io.github.premocloud.typesafe` logger. Set its level the way you set any
library's; there is no environment variable, because slf4j has no library-side level setting and configuring the
logging environment is the application's job.

| Level | What you get |
| --- | --- |
| `DEBUG` | one line per request with its status and how long it took, plus a line per retry and per connection failure |
| `TRACE` | the above, plus the wire in both directions: method, url, headers, body |
| `INFO` and above | nothing, so a stock application sees none of this; failures are thrown, not logged |

```xml
<logger name="io.github.premocloud.typesafe" level="DEBUG"/>
```

```properties
logging.level.io.github.premocloud.typesafe=DEBUG
```

```
TRACE io.github.premocloud.typesafe - req-3f9a1c -> POST https://api.typesafe.ai/v1/systemone headers={Authorization=***, ...} body={"state":...}
DEBUG io.github.premocloud.typesafe - req-3f9a1c <- 200 in 214ms (request req_01a0...)
TRACE io.github.premocloud.typesafe - req-3f9a1c <- 200 headers={x-typesafe-request-id=req_01a0..., ...} body={"model":"jev-1.13.0",...}
```

Credential headers are masked, including any of your own containing `token` or `secret`. **Bodies are not masked**, so
`TRACE` puts the state you are classifying into the log.

## Spring Boot

Add `io.github.premo-cloud:typesafe-sdk-spring-boot-starter` and set one property:

```properties
typesafe.api-key=${TYPESAFE_API_KEY}
```

A `TypeSafeClient` bean is then available for injection. The starter creates it only when the key is set, backs off if
you define your own, and reuses the application's `ObjectMapper`. Properties, customization, testing, and troubleshooting
are covered in the [starter README](typesafe-sdk-spring-boot-starter/README.md).

## Development

```
./gradlew build
```

Tests run against an in-process stub server and need no API key.

## Contributing

Issues and pull requests are welcome at [Premo-Cloud/typesafe-sdk-java](https://github.com/Premo-Cloud/typesafe-sdk-java).
Please keep the public API aligned with the official SDKs' conventions and add a test for every behavior change.

## License

MIT, Copyright (c) 2026 The authors of the Premo-Cloud/typesafe-sdk-java repository. See [LICENSE](LICENSE).
