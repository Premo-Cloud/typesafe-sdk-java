package io.github.premocloud.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeSafeClientTest {

    private static final String API_KEY = "apik-test";

    private static final String RESPONSE_JSON = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "is_phishing": {"type": "noul", "noul": 0.93},
                "spam_category": {
                  "type": "choice",
                  "choice": "PHISHING",
                  "probabilities": {"PHISHING": 0.9, "MARKETING": 0.1},
                  "confidence": 0.88
                },
                "urgency": {
                  "type": "score",
                  "score": 1.7,
                  "confidence": 0.61,
                  "legend": {"0": "none", "1": "soft", "2": "threatening"},
                  "probabilities": {"0": 0.0, "1": 0.3, "2": 0.7},
                  "some_future_field": true
                }
              },
              "usage": {"input_tokens": 312, "output_tokens": 48},
              "some_future_top_level_field": {}
            }
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubTypeSafeServer server;
    private TypeSafeClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubTypeSafeServer();
        client = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl() + "/").timeout(Duration.ofSeconds(5))
                .retryPolicy(RetryPolicy.none()).build();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void systemOnePostsBearerAuthenticatedJsonAndReturnsTypedAnswers() throws Exception {
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = client.systemOne(spamRequest());

        StubTypeSafeServer.Recorded recorded = server.recorded().get(0);
        assertEquals("POST", recorded.method());
        assertEquals("/v1/systemone", recorded.path());
        assertEquals("Bearer " + API_KEY, recorded.headers().getFirst("Authorization"));
        assertEquals("application/json", recorded.headers().getFirst("Content-type"));
        JsonNode sent = objectMapper.readTree(recorded.body());
        assertEquals("jev-latest", sent.at("/model").asText());
        assertEquals("URGENT", sent.at("/state/email/subject").asText());
        assertEquals("noul", sent.at("/questions/is_phishing/type").asText());
        assertEquals("Credential theft", sent.at("/questions/spam_category/criteria/PHISHING").asText());
        assertEquals("threatening", sent.at("/questions/urgency/criteria/2").asText());

        assertEquals("jev-1.13.0", response.model());
        assertEquals(0.93, response.noul("is_phishing"));
        ChoiceAnswer<String> category = response.choice("spam_category");
        assertEquals("PHISHING", category.choice());
        assertEquals(0.9, category.probabilities().get("PHISHING"));
        assertEquals(0.88, category.confidence());
        ScoreAnswer urgency = response.score("urgency");
        assertEquals(1.7, urgency.score());
        assertEquals("threatening", urgency.legend().get("2"));
        assertEquals(312, response.usage().inputTokens());
    }

    @Test
    void systemOneKeepsAnExplicitModel() throws Exception {
        server.reply(200, RESPONSE_JSON);

        client.systemOne(r -> r.state("text").model("jev-1.12.0").noul("is_phishing", n -> n.instructions("Yes?")));

        assertEquals("jev-1.12.0", objectMapper.readTree(server.recorded().get(0).body()).at("/model").asText());
    }

    @Test
    void systemOneAcceptsStateAndQuestionsDirectly() throws Exception {
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = client.systemOne(
                Map.of("document", "I was charged twice."),
                Map.of("spam_category", Choice.of("What is this about?", "PHISHING", "MARKETING")));

        JsonNode sent = objectMapper.readTree(server.recorded().get(0).body());
        assertEquals("I was charged twice.", sent.at("/state/document").asText());
        assertTrue(sent.at("/questions/spam_category/criteria/PHISHING").isNull());
        assertEquals("PHISHING", response.choices().get("spam_category").choice());
        assertEquals(1, response.nouls().size());
        assertEquals(1.7, response.scores().get("urgency").score());
    }

    @Test
    void typedAccessorsRejectWrongPrimitiveAndUnknownKey() {
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = client.systemOne(spamRequest());

        IllegalArgumentException wrongType = assertThrows(IllegalArgumentException.class, () -> response.noul("spam_category"));
        assertTrue(wrongType.getMessage().contains("spam_category"));
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> response.choice("nope"));
        assertTrue(missing.getMessage().contains("nope"));
    }

    @Test
    void sendsSdkIdentificationHeaders() {
        server.reply(200, RESPONSE_JSON);

        TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl()).header("X-Team", "review").retryPolicy(RetryPolicy.none()).build()
                .systemOne(spamRequest());

        StubTypeSafeServer.Recorded recorded = server.recorded().get(0);
        assertTrue(recorded.headers().getFirst("User-Agent").startsWith("typesafe-sdk/"));
        assertTrue(recorded.headers().getFirst("X-TypeSafe-SDK").startsWith("typesafe-sdk/"));
        assertTrue(recorded.headers().getFirst("X-TypeSafe-Runtime").startsWith("java/"));
        assertEquals("review", recorded.headers().getFirst("X-Team"));
        assertNull(recorded.headers().getFirst("X-TypeSafe-Retry-Count"));
    }

    @Test
    void errorsMapToStatusSpecificExceptionsWithExtractedMessages() {
        server.reply(401, "{\"error\":\"invalid api key\"}", Map.of("x-typesafe-request-id", "req_123"));

        TypeSafeAuthenticationException exception = assertThrows(TypeSafeAuthenticationException.class, () -> client.systemOne(spamRequest()));

        assertEquals(401, exception.status());
        assertEquals("401 invalid api key", exception.getMessage());
        assertEquals("{\"error\":\"invalid api key\"}", exception.body());
        assertEquals("req_123", exception.requestId().orElseThrow());

        server.reply(422, "{\"detail\":[{\"loc\":[\"body\",\"questions\",\"q\"],\"msg\":\"criteria required\"}]}");
        assertEquals("422 questions.q: criteria required",
                assertThrows(TypeSafeUnprocessableEntityException.class, () -> client.systemOne(spamRequest())).getMessage());

        server.reply(400, "");
        assertEquals("400 status code (no body)", assertThrows(TypeSafeBadRequestException.class, () -> client.systemOne(spamRequest())).getMessage());

        server.reply(402, "{\"error\":\"out of credits\"}");
        assertEquals("402 out of credits",
                assertThrows(TypeSafePaymentRequiredException.class, () -> client.systemOne(spamRequest())).getMessage());

        server.reply(413, "{\"error\":\"payload too large\"}");
        assertEquals("413 payload too large",
                assertThrows(TypeSafePayloadTooLargeException.class, () -> client.systemOne(spamRequest())).getMessage());

        server.reply(503, "upstream down");
        assertEquals("503 upstream down", assertThrows(TypeSafeInternalServerException.class, () -> client.systemOne(spamRequest())).getMessage());

        server.reply(418, "{\"message\":\"teapot\"}");
        TypeSafeApiException generic = assertThrows(TypeSafeApiException.class, () -> client.systemOne(spamRequest()));
        assertEquals(TypeSafeApiException.class, generic.getClass());
        assertEquals("418 teapot", generic.getMessage());
    }

    @Test
    void rateLimitExposesRetryAfter() {
        server.reply(429, "{\"error\":\"slow down\"}", Map.of("retry-after", "7"));

        TypeSafeRateLimitException exception = assertThrows(TypeSafeRateLimitException.class, () -> client.systemOne(spamRequest()));

        assertEquals(Duration.ofSeconds(7), exception.retryAfter().orElseThrow());
    }

    @Test
    void retriesRetryableStatusesWithRetryCountHeaderAndHonorsRetryAfterMs() {
        TypeSafeClient retrying = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl())
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(2).backoffInitial(Duration.ofMillis(1)).backoffMax(Duration.ofMillis(2)))).build();
        server.reply(500, "{\"error\":\"boom\"}");
        server.reply(429, "{\"error\":\"slow\"}", Map.of("retry-after-ms", "5"));
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = retrying.systemOne(spamRequest());

        assertEquals(0.93, response.noul("is_phishing"));
        assertEquals(3, server.recorded().size());
        assertNull(server.recorded().get(0).headers().getFirst("X-TypeSafe-Retry-Count"));
        assertEquals("1", server.recorded().get(1).headers().getFirst("X-TypeSafe-Retry-Count"));
        assertEquals("2", server.recorded().get(2).headers().getFirst("X-TypeSafe-Retry-Count"));
    }

    @Test
    void givesUpAfterMaxRetriesAndDoesNotRetryClientErrors() {
        TypeSafeClient retrying = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl())
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(1).backoffInitial(Duration.ofMillis(1)))).build();
        server.reply(500, "one");
        server.reply(500, "two");

        assertEquals("500 two", assertThrows(TypeSafeInternalServerException.class, () -> retrying.systemOne(spamRequest())).getMessage());
        assertEquals(2, server.recorded().size());

        server.reply(400, "bad");
        assertThrows(TypeSafeBadRequestException.class, () -> retrying.systemOne(spamRequest()));
        assertEquals(3, server.recorded().size());
    }

    @Test
    void timeoutsAndConnectionFailuresAreTyped() {
        TypeSafeClient impatient = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl()).timeout(Duration.ofMillis(200))
                .retryPolicy(RetryPolicy.none()).build();
        server.replyAfter(1500, 200, RESPONSE_JSON);

        TypeSafeTimeoutException timeout = assertThrows(TypeSafeTimeoutException.class, () -> impatient.systemOne(spamRequest()));
        assertEquals(Duration.ofMillis(200), timeout.timeout());

        TypeSafeClient unreachable = TypeSafeClient.builder().apiKey(API_KEY).baseUrl("http://127.0.0.1:1").retryPolicy(RetryPolicy.none()).build();
        TypeSafeConnectionException connection = assertThrows(TypeSafeConnectionException.class, () -> unreachable.systemOne(spamRequest()));
        assertEquals(TypeSafeConnectionException.class, connection.getClass());
    }

    @Test
    void perCallOptionsOverrideTimeoutRetryAndHeaders() {
        TypeSafeClient retrying = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl()).header("X-Team", "review")
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(2).backoffInitial(Duration.ofMillis(1)))).build();
        server.reply(500, "no retry please");

        assertThrows(TypeSafeInternalServerException.class,
                () -> retrying.systemOne(spamRequest(), RequestOptions.of(o -> o.maxRetries(0).header("X-Team", "spike").header("X-Trace", "t1"))));

        assertEquals(1, server.recorded().size());
        assertEquals("spike", server.recorded().get(0).headers().getFirst("X-Team"));
        assertEquals("t1", server.recorded().get(0).headers().getFirst("X-Trace"));

        server.replyAfter(1500, 200, RESPONSE_JSON);
        TypeSafeTimeoutException timeout = assertThrows(TypeSafeTimeoutException.class,
                () -> retrying.systemOne(spamRequest(), RequestOptions.of(o -> o.timeout(Duration.ofMillis(200)).maxRetries(0))));
        assertEquals(Duration.ofMillis(200), timeout.timeout());

        server.reply(200, "{\"models\":[]}");
        assertEquals(List.of(), retrying.models().list(RequestOptions.of(o -> o.header("X-Trace", "t2"))));
        assertEquals("t2", server.recorded().get(2).headers().getFirst("X-Trace"));
        assertEquals("review", server.recorded().get(2).headers().getFirst("X-Team"));
        assertThrows(IllegalArgumentException.class, () -> RequestOptions.of(o -> o.timeout(Duration.ZERO)));
    }

    @Test
    void perCallMaxRetriesKeepsTheRestOfTheClientPolicy() {
        TypeSafeClient onlyRetries503 = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl())
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(0).httpStatuses(Set.of(503)).backoffInitial(Duration.ofMillis(1)))).build();

        server.reply(500, "not retryable for this client");
        assertThrows(TypeSafeInternalServerException.class,
                () -> onlyRetries503.systemOne(spamRequest(), RequestOptions.of(o -> o.maxRetries(2))));
        assertEquals(1, server.recorded().size());

        server.reply(503, "busy");
        server.reply(503, "still busy");
        assertThrows(TypeSafeInternalServerException.class,
                () -> onlyRetries503.systemOne(spamRequest(), RequestOptions.of(o -> o.maxRetries(1))));
        assertEquals(3, server.recorded().size());

        RetryPolicy callPolicy = RetryPolicy.of(r -> r.maxRetries(0).httpStatuses(Set.of(500)).backoffInitial(Duration.ofMillis(1)));
        server.reply(500, "one");
        server.reply(200, RESPONSE_JSON);
        onlyRetries503.systemOne(spamRequest(), RequestOptions.of(o -> o.retryPolicy(callPolicy).maxRetries(1)));
        assertEquals(5, server.recorded().size());

        assertThrows(IllegalArgumentException.class, () -> RequestOptions.of(o -> o.maxRetries(-1)));
    }

    @Test
    void systemOneRejectsNoulAnswerMissingItsValue() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"is_fraud": {"type": "noul"}}, "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("noul"), exception.getMessage());
        assertTrue(exception.getMessage().contains("is_fraud"), exception.getMessage());
    }

    @Test
    void systemOneRejectsNoulAnswerWithExplicitNullValue() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"is_fraud": {"type": "noul", "noul": null}}, "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("noul"), exception.getMessage());
    }

    /** Declared in the opposite order to the response's probabilities, to show the typed map follows the enum. */
    enum SpamCategory { MARKETING, PHISHING }

    enum Urgency { LOW, HIGH }

    @Test
    void systemOneReadsAChoiceAnswerAsAnEnum() {
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = client.systemOne(spamRequest());

        ChoiceAnswer<SpamCategory> category = response.choice("spam_category", SpamCategory.class);
        assertEquals(SpamCategory.PHISHING, category.choice());
        assertEquals(0.9, category.probabilities().get(SpamCategory.PHISHING));
        assertEquals(0.1, category.probabilities().get(SpamCategory.MARKETING));
        assertEquals(0.88, category.confidence());
        assertEquals(List.of(SpamCategory.MARKETING, SpamCategory.PHISHING), List.copyOf(category.probabilities().keySet()));
        assertEquals("PHISHING", response.choice("spam_category").choice(), "the String read is unchanged");
        assertEquals("PHISHING", response.choices().get("spam_category").choice());
    }

    @Test
    void systemOneRejectsAChoiceLabelThatIsNotAConstantOfTheEnum() {
        server.reply(200, RESPONSE_JSON);
        TypeSafeResponse response = client.systemOne(spamRequest());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> response.choice("spam_category", Urgency.class));

        assertTrue(exception.getMessage().contains("PHISHING"), exception.getMessage());
        assertTrue(exception.getMessage().contains("[LOW, HIGH]"), exception.getMessage());
    }

    @Test
    void systemOneRejectsChoiceAnswerMissingItsChoice() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"category": {"type": "choice", "probabilities": {"a": 1.0}, "confidence": 1.0}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("choice"), exception.getMessage());
    }

    @Test
    void systemOneRejectsScoreAnswerMissingItsScore() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"urgency": {"type": "score", "probabilities": {"0": 1.0}, "confidence": 1.0, "legend": {"0": "calm"}}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("score"), exception.getMessage());
    }

    @Test
    void systemOneReadsAScoreLegendWithObjectAndArrayLevels() {
        // Levels are sent as any JSON value and echoed back as sent, so the legend holds whatever was asked (#11).
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {
                   "is_phishing": {"type": "noul", "noul": 0.93},
                   "spam_category": {"type": "choice", "choice": "PHISHING", "probabilities": {"PHISHING": 1.0}, "confidence": 1.0},
                   "urgency": {"type": "score", "score": 1.0, "probabilities": {"0": 0.2, "1": 0.5, "2": 0.3}, "confidence": 0.5,
                               "legend": {"0": "none", "1": {"what": "Threatens loss within hours", "examples": ["final notice"]}, "2": ["a", "b"]}}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeResponse response = client.systemOne(spamRequest());

        Map<String, Object> legend = response.score("urgency").legend();
        assertEquals("none", legend.get("0"));
        assertEquals(Map.of("what", "Threatens loss within hours", "examples", List.of("final notice")), legend.get("1"));
        assertEquals(List.of("a", "b"), legend.get("2"));
        assertEquals(0.93, response.noul("is_phishing"));
        assertEquals(1, response.usage().inputTokens());
    }

    @Test
    void systemOneRejectsAResponseMissingAnAnswerForAQuestionThatWasAsked() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"is_phishing": {"type": "noul", "noul": 0.93}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("spam_category"), exception.getMessage());
    }

    @Test
    void systemOneRejectsAnAnswerOfADifferentTypeThanTheQuestionAsked() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {
                   "is_phishing": {"type": "choice", "choice": "PHISHING", "probabilities": {"PHISHING": 1.0}, "confidence": 1.0},
                   "spam_category": {"type": "choice", "choice": "PHISHING", "probabilities": {"PHISHING": 1.0}, "confidence": 1.0},
                   "urgency": {"type": "score", "score": 1.0, "probabilities": {"1": 1.0}, "confidence": 1.0, "legend": {"1": "x"}}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("is_phishing"), exception.getMessage());
    }

    @Test
    void systemOneRejectsAResponseMissingItsUsage() {
        // Without this the response reads with usage() == null and the first caller to read a count gets an NPE (#12).
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {
                   "is_phishing": {"type": "noul", "noul": 0.93},
                   "spam_category": {"type": "choice", "choice": "PHISHING", "probabilities": {"PHISHING": 1.0}, "confidence": 1.0},
                   "urgency": {"type": "score", "score": 1.0, "probabilities": {"1": 1.0}, "confidence": 1.0, "legend": {"1": "x"}}}}
                """);

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().contains("usage"), exception.getMessage());
    }

    @Test
    void systemOneRejectsUsageMissingACount() {
        // A missing or null count previously read as 0, the same silent default 0.2.0 removed from the answers (#12).
        for (String usage : List.of("{\"input_tokens\": 1}", "{\"input_tokens\": 1, \"output_tokens\": null}")) {
            server.reply(200, """
                    {"model": "jev-1.13.0", "answers": {
                       "is_phishing": {"type": "noul", "noul": 0.93},
                       "spam_category": {"type": "choice", "choice": "PHISHING", "probabilities": {"PHISHING": 1.0}, "confidence": 1.0},
                       "urgency": {"type": "score", "score": 1.0, "probabilities": {"1": 1.0}, "confidence": 1.0, "legend": {"1": "x"}}},
                     "usage": %s}
                    """.formatted(usage));

            TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()), usage);

            assertTrue(exception.getMessage().contains("output_tokens"), exception.getMessage());
        }
    }

    @Test
    void systemOneRejectsUnreadableBody() {
        server.reply(200, "not json");

        TypeSafeException exception = assertThrows(TypeSafeException.class, () -> client.systemOne(spamRequest()));

        assertTrue(exception.getMessage().startsWith("Could not read response"));
    }

    @Test
    void modelsListsAvailableModels() {
        server.reply(200, "{\"models\":[{\"name\":\"jev-1.13.0\",\"description\":\"Jev\",\"release_date\":\"2026-09-01\",\"extra\":1}]}");

        List<ModelCard> models = client.models().list();

        assertEquals("GET", server.recorded().get(0).method());
        assertEquals("/v1/models", server.recorded().get(0).path());
        assertEquals(List.of(new ModelCard("jev-1.13.0", "Jev", "2026-09-01")), models);
    }

    @Test
    void builderRequiresApiKeyAndValidatesTimeout() {
        assertTrue(assertThrows(TypeSafeException.class, () -> TypeSafeClient.builder().apiKey(" ").build()).getMessage().contains("TYPESAFE_API_KEY"));
        assertThrows(IllegalArgumentException.class, () -> TypeSafeClient.builder().timeout(Duration.ZERO));
        assertEquals(RetryPolicy.DEFAULT, TypeSafeClient.builder().apiKey(API_KEY).build().retryPolicy());
    }

    @Test
    void systemOneAsyncReturnsTypedAnswers() throws Exception {
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = client.systemOneAsync(spamRequest()).get(5, TimeUnit.SECONDS);

        assertEquals("jev-1.13.0", response.model());
        assertEquals(0.93, response.noul("is_phishing"));
        assertEquals("PHISHING", response.choice("spam_category").choice());
        assertEquals(1.7, response.score("urgency").score());
    }

    @Test
    void systemOneAsyncFailsWithStatusSpecificException() {
        server.reply(401, "{\"error\":\"invalid api key\"}", Map.of("x-typesafe-request-id", "req_123"));

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> client.systemOneAsync(spamRequest()).get(5, TimeUnit.SECONDS));

        TypeSafeAuthenticationException exception = assertInstanceOf(TypeSafeAuthenticationException.class, failure.getCause());
        assertEquals(401, exception.status());
        assertEquals("401 invalid api key", exception.getMessage());
        assertEquals("req_123", exception.requestId().orElseThrow());
    }

    @Test
    void systemOneAsyncRetriesWithRetryCountHeader() throws Exception {
        TypeSafeClient retrying = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl())
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(2).backoffInitial(Duration.ofMillis(1)).backoffMax(Duration.ofMillis(2)))).build();
        server.reply(500, "{\"error\":\"boom\"}");
        server.reply(429, "{\"error\":\"slow\"}", Map.of("retry-after-ms", "5"));
        server.reply(200, RESPONSE_JSON);

        TypeSafeResponse response = retrying.systemOneAsync(spamRequest()).get(5, TimeUnit.SECONDS);

        assertEquals(0.93, response.noul("is_phishing"));
        assertEquals(3, server.recorded().size());
        assertNull(server.recorded().get(0).headers().getFirst("X-TypeSafe-Retry-Count"));
        assertEquals("1", server.recorded().get(1).headers().getFirst("X-TypeSafe-Retry-Count"));
        assertEquals("2", server.recorded().get(2).headers().getFirst("X-TypeSafe-Retry-Count"));
    }

    @Test
    void systemOneAsyncHonorsPerCallOptions() {
        TypeSafeClient retrying = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl()).header("X-Team", "review")
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(2).backoffInitial(Duration.ofMillis(1)))).build();
        server.reply(500, "no retry please");

        ExecutionException failure = assertThrows(ExecutionException.class, () -> retrying
                .systemOneAsync(spamRequest(), RequestOptions.of(o -> o.maxRetries(0).header("X-Team", "spike").header("X-Trace", "t1")))
                .get(5, TimeUnit.SECONDS));

        assertInstanceOf(TypeSafeInternalServerException.class, failure.getCause());
        assertEquals(1, server.recorded().size());
        assertEquals("spike", server.recorded().get(0).headers().getFirst("X-Team"));
        assertEquals("t1", server.recorded().get(0).headers().getFirst("X-Trace"));

        server.replyAfter(1500, 200, RESPONSE_JSON);
        ExecutionException timed = assertThrows(ExecutionException.class, () -> retrying
                .systemOneAsync(spamRequest(), RequestOptions.of(o -> o.timeout(Duration.ofMillis(200)).maxRetries(0)))
                .get(5, TimeUnit.SECONDS));

        assertEquals(Duration.ofMillis(200), assertInstanceOf(TypeSafeTimeoutException.class, timed.getCause()).timeout());
    }

    @Test
    void systemOneAsyncRejectsAResponseMissingAnAnswerForAQuestionThatWasAsked() {
        server.reply(200, """
                {"model": "jev-1.13.0", "answers": {"is_phishing": {"type": "noul", "noul": 0.93}},
                 "usage": {"input_tokens": 1, "output_tokens": 1}}
                """);

        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> client.systemOneAsync(spamRequest()).get(5, TimeUnit.SECONDS));

        TypeSafeException exception = assertInstanceOf(TypeSafeException.class, failure.getCause());
        assertEquals(TypeSafeException.class, exception.getClass());
        assertTrue(exception.getMessage().contains("spam_category"), exception.getMessage());
    }

    @Test
    void systemOneAsyncThrowsRequestSetupErrorsSynchronously() {
        assertThrows(IllegalStateException.class, () -> client.systemOneAsync(r -> r.noul("is_phishing", n -> n.instructions("Yes?"))));
        assertThrows(IllegalStateException.class, () -> client.systemOneAsync(Map.of("email", "text"), Map.of()));
    }

    @Test
    void modelsListAsyncReturnsModels() throws Exception {
        server.reply(200, "{\"models\":[{\"name\":\"jev-1.13.0\",\"description\":\"Jev\",\"release_date\":\"2026-09-01\",\"extra\":1}]}");

        List<ModelCard> models = client.models().listAsync().get(5, TimeUnit.SECONDS);

        assertEquals("GET", server.recorded().get(0).method());
        assertEquals("/v1/models", server.recorded().get(0).path());
        assertEquals(List.of(new ModelCard("jev-1.13.0", "Jev", "2026-09-01")), models);
    }

    @Test
    void modelsListAsyncHonorsPerCallHeaders() throws Exception {
        client = TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl()).header("X-Team", "review").retryPolicy(RetryPolicy.none()).build();
        server.reply(200, "{\"models\":[]}");

        assertEquals(List.of(), client.models().listAsync(RequestOptions.of(o -> o.header("X-Trace", "t2"))).get(5, TimeUnit.SECONDS));

        assertEquals("t2", server.recorded().get(0).headers().getFirst("X-Trace"));
        assertEquals("review", server.recorded().get(0).headers().getFirst("X-Team"));
    }

    private static TypeSafeRequest spamRequest() {
        return TypeSafeRequest.of(r -> r
                .state(Map.of("email", Map.of("subject", "URGENT")))
                .noul("is_phishing", n -> n.instructions("Is `email` phishing?"))
                .choice("spam_category", c -> c
                        .instructions("Which category?")
                        .option("PHISHING", "Credential theft")
                        .option("MARKETING", "Promotions"))
                .score("urgency", s -> s
                        .instructions("How urgent?")
                        .level("none")
                        .level("soft")
                        .level("threatening")));
    }
}
