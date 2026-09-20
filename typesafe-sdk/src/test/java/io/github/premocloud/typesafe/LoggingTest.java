package io.github.premocloud.typesafe;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the client logs, at which level, and what it never puts in a log line. */
class LoggingTest {

    private static final String API_KEY = "apik-secret-value-1234";

    private static final String RESPONSE_JSON = """
            {"model": "jev-1.13.0",
             "answers": {"q": {"type": "noul", "noul": 0.93}},
             "usage": {"input_tokens": 12, "output_tokens": 3}}
            """;

    private StubTypeSafeServer server;
    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger logger;

    @BeforeEach
    void setUp() throws IOException {
        server = new StubTypeSafeServer();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        logger = context.getLogger("io.github.premocloud.typesafe");
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        // Capture only: without this the events also reach logback's default console appender and
        // every test run prints the request and response bodies.
        logger.setAdditive(false);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        logger.setLevel(null);
        logger.setAdditive(true);
        server.close();
    }

    @Test
    void logsOneInfoLinePerRequestWithStatusAndElapsed() {
        server.reply(200, RESPONSE_JSON);

        client().systemOne("state", Map.of("q", Noul.of("Yes?")));

        List<String> info = messagesAt(Level.INFO);
        assertEquals(1, info.size(), info.toString());
        assertTrue(info.get(0).matches(".*<- 200 in \\d+ms.*"), info.get(0));
    }

    @Test
    void logsTheWireInBothDirectionsAtDebug() {
        server.reply(200, RESPONSE_JSON);

        client().systemOne("state", Map.of("q", Noul.of("Yes?")));

        List<String> debug = messagesAt(Level.DEBUG);
        assertEquals(2, debug.size(), debug.toString());
        assertTrue(debug.get(0).contains("-> POST"), debug.get(0));
        assertTrue(debug.get(1).contains("<-"), debug.get(1));
        assertTrue(debug.get(1).contains("jev-1.13.0"), "response body is logged: " + debug.get(1));
    }

    @Test
    void neverLogsTheApiKey() {
        server.reply(200, RESPONSE_JSON);

        client().systemOne("state", Map.of("q", Noul.of("Yes?")));

        String everything = String.join("\n", messagesAt(null));
        assertFalse(everything.contains(API_KEY), "the key leaked: " + everything);
        assertTrue(everything.contains("Authorization=***"), everything);
    }

    @Test
    void logsARetryLineAtInfo() {
        server.reply(429, "{}", Map.of("retry-after-ms", "1"));
        server.reply(200, RESPONSE_JSON);

        client().systemOne("state", Map.of("q", Noul.of("Yes?")));

        assertTrue(messagesAt(Level.INFO).stream().anyMatch(m -> m.contains("retrying in")),
                messagesAt(Level.INFO).toString());
    }

    @Test
    void logsNothingWhenTheLevelIsAboveInfo() {
        logger.setLevel(Level.WARN);
        server.reply(200, RESPONSE_JSON);

        client().systemOne("state", Map.of("q", Noul.of("Yes?")));

        assertEquals(List.of(), messagesAt(null));
    }

    private TypeSafeClient client() {
        return TypeSafeClient.builder().apiKey(API_KEY).baseUrl(server.baseUrl())
                .timeout(Duration.ofSeconds(5)).build();
    }

    /** Formatted messages at one level, or every level when {@code level} is null. */
    private List<String> messagesAt(Level level) {
        return appender.list.stream()
                .filter(event -> level == null || event.getLevel().equals(level))
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
