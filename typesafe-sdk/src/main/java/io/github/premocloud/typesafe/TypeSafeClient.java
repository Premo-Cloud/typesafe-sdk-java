package io.github.premocloud.typesafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Client for the TypeSafe API.
 *
 * <pre>{@code
 * TypeSafeClient client = TypeSafeClient.fromEnvironment();          // TYPESAFE_API_KEY, optionally TYPESAFE_BASE_URL and TYPESAFE_DEFAULT_MODEL
 * TypeSafeResponse response = client.systemOne(r -> r
 *     .state("Help! My payouts have been failing for 3 days.")
 *     .noul("is_urgent", n -> n.instructions("Does this convey urgency?")));
 * double urgency = response.noul("is_urgent");
 * }</pre>
 *
 * Explicit builder values take precedence over environment variables, then SDK defaults. Instances are immutable and
 * safe to share across threads. Requests are retried per {@link RetryPolicy#DEFAULT} unless configured otherwise.
 */
public final class TypeSafeClient {

    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";
    public static final String DEFAULT_MODEL = "jev-latest";
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    public static final String API_KEY_ENV = "TYPESAFE_API_KEY";
    public static final String BASE_URL_ENV = "TYPESAFE_BASE_URL";
    public static final String DEFAULT_MODEL_ENV = "TYPESAFE_DEFAULT_MODEL";

    static final String SYSTEM_ONE_PATH = "/v1/systemone";
    static final String RETRY_COUNT_HEADER = "X-TypeSafe-Retry-Count";

    private static final String SDK_NAME = "typesafe-sdk";
    private static final String VERSION = Objects.requireNonNullElse(TypeSafeClient.class.getPackage().getImplementationVersion(), "dev");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final Duration timeout;
    private final RetryPolicy retryPolicy;
    private final Map<String, String> defaultHeaders;
    private final Models models = new Models(this);

    private TypeSafeClient(Builder builder) {
        this.httpClient = Objects.requireNonNullElseGet(builder.httpClient, HttpClient::newHttpClient);
        this.objectMapper = Objects.requireNonNullElseGet(builder.objectMapper, TypeSafeClient::defaultObjectMapper);
        this.apiKey = Objects.requireNonNull(builder.apiKey, "apiKey");
        this.baseUrl = stripTrailingSlash(resolve(builder.baseUrl, BASE_URL_ENV, DEFAULT_BASE_URL));
        this.defaultModel = resolve(builder.defaultModel, DEFAULT_MODEL_ENV, DEFAULT_MODEL);
        this.timeout = builder.timeout;
        this.retryPolicy = builder.retryPolicy;
        this.defaultHeaders = Map.copyOf(builder.headers);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** @throws TypeSafeException when {@value #API_KEY_ENV} is not set */
    public static TypeSafeClient fromEnvironment() {
        return builder().build();
    }

    /** The models available to the account: {@code client.models().list()}. */
    public Models models() {
        return models;
    }

    public String defaultModel() {
        return defaultModel;
    }

    public RetryPolicy retryPolicy() {
        return retryPolicy;
    }

    /** {@code client.systemOne(state, Map.of("category", Choice.of("Which?", "billing", "technical")))}. */
    public TypeSafeResponse systemOne(Object state, Map<String, ? extends TypeSafeQuestion> questions) {
        return systemOne(TypeSafeRequest.of(state, questions), RequestOptions.NONE);
    }

    public TypeSafeResponse systemOne(Object state, Map<String, ? extends TypeSafeQuestion> questions, RequestOptions options) {
        return systemOne(TypeSafeRequest.of(state, questions), options);
    }

    /** {@code client.systemOne(r -> r.state(ticket).noul("urgent", n -> n.instructions("Is `ticket` urgent?")))}. */
    public TypeSafeResponse systemOne(Consumer<TypeSafeRequest.Builder> configure) {
        return systemOne(TypeSafeRequest.of(configure), RequestOptions.NONE);
    }

    public TypeSafeResponse systemOne(Consumer<TypeSafeRequest.Builder> configure, RequestOptions options) {
        return systemOne(TypeSafeRequest.of(configure), options);
    }

    public TypeSafeResponse systemOne(TypeSafeRequest request) {
        return systemOne(request, RequestOptions.NONE);
    }

    /**
     * Evaluates every question in the request against its state, in one round trip, retrying per the policy.
     *
     * @param options per-call timeout, retry, and header overrides; {@link RequestOptions#NONE} inherits the client's
     * @throws TypeSafeApiException        on a non-2xx status after retries; see its subclasses for specific statuses
     * @throws TypeSafeConnectionException when the request cannot be delivered after retries; {@link TypeSafeTimeoutException} on timeout
     */
    public TypeSafeResponse systemOne(TypeSafeRequest request, RequestOptions options) {
        TypeSafeRequest resolved = Objects.isNull(request.model()) ? request.withModel(defaultModel) : request;
        TypeSafeResponse response = post(SYSTEM_ONE_PATH, resolved, TypeSafeResponse.class, options);

        if (Objects.isNull(response.answers())) {
            throw new TypeSafeException("TypeSafe response has no answers");
        }

        // Every question asked must come back answered, and answered as its own type. Either gap otherwise
        // surfaces later, when the caller reads that key, as an IllegalArgumentException no catch of
        // TypeSafeException would see.
        Set<String> unanswered = new LinkedHashSet<>();
        Set<String> mistyped = new LinkedHashSet<>();

        resolved.questions().forEach((id, question) -> {
            TypeSafeAnswer answer = response.answers().get(id);

            if (Objects.isNull(answer)) {
                unanswered.add(id);
            } else if (!expectedAnswer(question).isInstance(answer)) {
                mistyped.add(id);
            }
        });

        if (!unanswered.isEmpty()) {
            throw new TypeSafeException("TypeSafe response is missing answers for %s; answered: %s"
                    .formatted(unanswered, response.answers().keySet()));
        }

        if (!mistyped.isEmpty()) {
            throw new TypeSafeException("TypeSafe response answered %s with a different type than was asked"
                    .formatted(mistyped));
        }

        return response;
    }

    <T> T get(String path, Class<T> type, RequestOptions options) {
        return send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET(), type, options);
    }

    private <T> T post(String path, Object body, Class<T> type, RequestOptions options) {
        String json;

        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new TypeSafeException("Could not serialize request: " + e.getOriginalMessage(), e);
        }

        return send(HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)), type, options);
    }

    private <T> T send(HttpRequest.Builder template, Class<T> type, RequestOptions options) {
        Duration timeout = Objects.requireNonNullElse(options.timeout(), this.timeout);
        RetryPolicy retryPolicy = options.resolveRetryPolicy(this.retryPolicy);
        Map<String, String> headers = new LinkedHashMap<>(defaultHeaders);
        headers.putAll(options.headers());
        template.timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", "application/json")
                .header("User-Agent", SDK_NAME + "/" + VERSION)
                .header("X-TypeSafe-SDK", SDK_NAME + "/" + VERSION)
                .header("X-TypeSafe-Runtime", "java/" + System.getProperty("java.version"));
        headers.forEach(template::header);

        for (int attempt = 0; ; attempt++) {
            HttpRequest httpRequest = attempt == 0 ? template.build() : template.copy().header(RETRY_COUNT_HEADER, Integer.toString(attempt)).build();
            HttpResponse<String> httpResponse;

            try {
                httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            } catch (HttpTimeoutException e) {
                if (retryPolicy.retryTimeouts() && attempt < retryPolicy.maxRetries()) {
                    pause(backoff(retryPolicy, attempt, Optional.empty()));
                    continue;
                }

                throw new TypeSafeTimeoutException(timeout, e);
            } catch (IOException e) {
                if (retryPolicy.retryConnectionErrors() && attempt < retryPolicy.maxRetries()) {
                    pause(backoff(retryPolicy, attempt, Optional.empty()));
                    continue;
                }

                throw new TypeSafeConnectionException("Connection error: " + e.getMessage(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TypeSafeConnectionException("Request interrupted", e);
            }

            int status = httpResponse.statusCode();

            if (status >= 200 && status < 300) {
                return deserialize(httpResponse.body(), type);
            }

            if (retryPolicy.retriesStatus(status) && attempt < retryPolicy.maxRetries()) {
                pause(backoff(retryPolicy, attempt, retryPolicy.respectRetryAfter() ? RetryAfter.parse(httpResponse.headers()) : Optional.empty()));
                continue;
            }

            throw TypeSafeApiException.fromResponse(status, httpResponse.body(), httpResponse.headers());
        }
    }

    /** A server-supplied delay within the cap wins; otherwise capped exponential backoff with jitter subtracted. */
    private static Duration backoff(RetryPolicy retryPolicy, int attempt, Optional<Duration> retryAfter) {
        if (retryAfter.isPresent() && retryAfter.get().compareTo(retryPolicy.maxRetryAfter()) <= 0) {
            return retryAfter.get();
        }

        long exponential = Math.min(retryPolicy.backoffInitial().toMillis() * (1L << Math.min(attempt, 30)), retryPolicy.backoffMax().toMillis());
        return Duration.ofMillis(Math.round(exponential * (1 - ThreadLocalRandom.current().nextDouble() * retryPolicy.backoffJitter())));
    }

    private static void pause(Duration delay) {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeConnectionException("Interrupted while waiting to retry", e);
        }
    }

    /** The answer type the API must return for a question of this type. */
    private static Class<? extends TypeSafeAnswer> expectedAnswer(TypeSafeQuestion question) {
        if (question instanceof Noul) {
            return NoulAnswer.class;
        }

        if (question instanceof Choice) {
            return ChoiceAnswer.class;
        }

        return ScoreAnswer.class;
    }

    private <T> T deserialize(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonMappingException e) {
            // The path names the offending question, e.g. answers -> is_fraud, which the message alone does not.
            throw new TypeSafeException("Could not read response at %s: %s".formatted(e.getPathReference(), e.getOriginalMessage()), e);
        } catch (JsonProcessingException e) {
            throw new TypeSafeException("Could not read response: " + e.getOriginalMessage(), e);
        }
    }

    private static ObjectMapper defaultObjectMapper() {
        return new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    private static String resolve(@Nullable String explicit, String envVar, String fallback) {
        if (Objects.nonNull(explicit) && !explicit.isBlank()) {
            return explicit;
        }

        String fromEnv = System.getenv(envVar);
        return Objects.nonNull(fromEnv) && !fromEnv.isBlank() ? fromEnv.trim() : fallback;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static final class Builder {
        private @Nullable String apiKey;
        private @Nullable String baseUrl;
        private @Nullable String defaultModel;
        private Duration timeout = DEFAULT_TIMEOUT;
        private RetryPolicy retryPolicy = RetryPolicy.DEFAULT;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private @Nullable HttpClient httpClient;
        private @Nullable ObjectMapper objectMapper;

        /** Falls back to {@value #API_KEY_ENV} when not set. */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        /** Falls back to {@value #BASE_URL_ENV}, then {@value #DEFAULT_BASE_URL}. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Model for requests that do not set one. Falls back to {@value #DEFAULT_MODEL_ENV}, then {@value #DEFAULT_MODEL}. */
        public Builder defaultModel(String defaultModel) {
            this.defaultModel = defaultModel;
            return this;
        }

        /** Per-attempt timeout. Defaults to 10 seconds. */
        public Builder timeout(Duration timeout) {
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }

            this.timeout = timeout;
            return this;
        }

        public Builder retryPolicy(RetryPolicy retryPolicy) {
            this.retryPolicy = retryPolicy;
            return this;
        }

        /** An extra header sent with every request. */
        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** Supply your own client for proxies, executors, or connection settings. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /** Supply your own mapper, for example one that knows your state record types' custom serializers. */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            return this;
        }

        public TypeSafeClient build() {
            apiKey = resolve(apiKey, API_KEY_ENV, "");

            if (apiKey.isBlank()) {
                throw new TypeSafeException("No API key was provided. Pass apiKey to the builder or set the " + API_KEY_ENV + " environment variable.");
            }

            return new TypeSafeClient(this);
        }
    }
}
