# Changelog

## 0.6.0 - 2026-09-25

- Typed answer lookup: `Ask<A>` pairs a key, a question, and its answer type. `Ask.noul`, `Ask.choice`, and `Ask.score` declare one, returning the sealed subtypes `NoulAsk`, `ChoiceAsk<E>`, and `ScoreAsk`; `client.systemOne(state, URGENT, DEPT)`, `systemOneAsync`, `TypeSafeRequest.of(state, …)`, and `TypeSafeRequest.Builder.ask(…)` ask it; `response.answer(DEPT)` reads it back as `NoulAnswer`, `ChoiceAnswer<E>`, or `ScoreAnswer`. An enum choice ask rejects a label that is not a constant of its enum when it is created. A request rejects a second question under an asked key; questions added only by key still replace each other as before (#17, #19).
- `TypeSafeRequest.Builder.state(key, value)` starts an object state when no state is set yet, instead of throwing `IllegalStateException` (#17, #19).

## 0.5.1 - 2026-09-23

- The published POM description now names Jev: "Community Java SDK for Jev and the TypeSafe System One API" (#16).
- Copyright in `LICENSE` is now held by "The authors of the Premo-Cloud/typesafe-sdk-java repository" (#16).

## 0.5.0 - 2026-09-22

- Typed choices: `Choice<E>` and `ChoiceAnswer<E>` carry the label type. `Choice.of(instructions, Dept.class)`, `Choice.builder(Dept.class)`, and `TypeSafeRequest.Builder.choice(key, Dept.class, …)` build a question from an enum's constants, and `response.choice(key, Dept.class)` reads the answer back with `choice()` as the enum and `probabilities()` keyed by it. String labels are unchanged: `Choice.of(…, String...)` infers `Choice<String>` and `response.choice(key)` returns `ChoiceAnswer<String>`. **Breaking:** `ChoiceAnswer` gained a type parameter, which changes the erased return type of `choice()`; code compiled against 0.4.0 or earlier must be recompiled, and a raw `ChoiceAnswer` needs `<String>` or `var` (#10, #15).
- A response missing its `usage` block, or a `usage` missing `input_tokens` or `output_tokens`, now fails `systemOne` with a `TypeSafeException` naming the field, instead of reading as `null` or `0` (#12, #14).
- `ScoreAnswer.legend()` is now `Map<String, Object>`, since levels are sent as any JSON value and echoed back as sent. A `Score` with an object or array level previously failed the whole response, including its other answers and usage, with `Cannot deserialize value of type java.lang.String` (#11, #13).

## 0.4.0 - 2026-09-22

- Logging through slf4j on the `io.github.premocloud.typesafe` logger: `DEBUG` for one line per request with status and elapsed, plus retries and connection failures; `TRACE` for the wire in both directions. Nothing at `INFO` or above. Credential headers are masked. Adds `org.slf4j:slf4j-api` (#6, #7).
- `TypeSafePaymentRequiredException` (402) and `TypeSafePayloadTooLargeException` (413) join the other status-specific exceptions; both statuses previously fell through to the generic `TypeSafeApiException` (#8, #9).

## 0.3.0 - 2026-09-19

- Async API: `systemOneAsync` mirroring every `systemOne` overload and `models().listAsync()` return `CompletableFuture`s driven by `HttpClient.sendAsync`, so retries back off without holding a thread, honoring the same per-call `RequestOptions` and failing with the same `TypeSafeException` subclasses as the blocking calls (#4).

## 0.2.0 - 2026-09-19

- `RequestOptions.maxRetries(n)` now applies the retry count on top of the client's retry policy, or the call's own policy when one is set, instead of replacing the policy with `RetryPolicy.DEFAULT` and losing the client's statuses and backoff (#2).
- A response whose answer is missing a required field (`noul`, `choice`, `score`, `probabilities`, `confidence`), or has it as `null`, now fails `systemOne` with a `TypeSafeException` naming the question, instead of reading as `0.0` or `null` (#1).
- A response that omits an answer for a question that was asked now fails `systemOne` with a `TypeSafeException` naming the unanswered questions, instead of surfacing later as an `IllegalArgumentException` when that key is read (#1).
- A response answering a question with a different type than was asked now fails `systemOne` with a `TypeSafeException` naming that question (#1).
- Response parse errors name the JSON path of the offending element.

## 0.1.1 - 2026-09-18

First published release. A `0.1.0` tag was cut earlier the same day but never published to Maven Central; its contents are listed here.

- Starter: a blank `typesafe.api-key`, such as `${TYPESAFE_API_KEY:}` with the variable unset, no longer attempts to create the client and fail startup; it is treated as absent.
- Starter documented in its own README; README examples use invented data.

Community library published under `io.github.premo-cloud`; not affiliated with TypeSafe AI.

- `TypeSafeClient` over `java.net.http` with a builder, `fromEnvironment()`, and `systemOne` taking either `(state, questions)` as in the other SDKs, a `TypeSafeRequest`, or a request configurer.
- Question types `Noul`, `Choice`, and `Score` with `of(instructions, criteria)` factories and `of(builder -> ...)` configurers; `Criterion` for structured descriptions; undescribed labels and optional instructions as the API allows.
- Sealed `TypeSafeAnswer` hierarchy and typed accessors on `TypeSafeResponse`.
- Retries matching the other SDKs (`RetryPolicy.DEFAULT`: 2 retries, 500 ms to 5 s backoff with jitter, on 408/429/5xx and connection failures, honoring `Retry-After`).
- Status-specific `TypeSafeApiException` subclasses with extracted messages and request ids; `TypeSafeConnectionException` and `TypeSafeTimeoutException`.
- `RequestOptions` for per-call timeout, retry, and header overrides on `systemOne` and `models().list()`.
- `client.models().list()`, SDK identification headers, extra default headers, and `TYPESAFE_BASE_URL` / `TYPESAFE_DEFAULT_MODEL` environment fallbacks.
- `CriteriaQuestionSet` for generating one question per user-defined criterion.
- Spring Boot starter exposing a `TypeSafeClient` bean from `typesafe.*` properties.
