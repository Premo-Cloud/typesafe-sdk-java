package io.github.premocloud.typesafe;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the client logs, tiered as the official SDKs log it.
 *
 * <p>{@code INFO} is one line per request with its status and how long it took, plus a line for each
 * retry and each connection failure. {@code DEBUG} adds the wire in both directions: method, url,
 * headers and body. Nothing is logged at {@code WARN} or above, because a failure is already thrown
 * and the exception carries more than a log line would.
 *
 * <p>Set the level on the {@code io.github.premocloud.typesafe} logger, as for any library. There is
 * no environment variable: slf4j has no library-side level setting, and a library should let the
 * application configure its own logging.
 *
 * <p>Credential headers are masked. Bodies are not, so {@code DEBUG} puts the state being classified
 * into the log; both official SDKs document the same.
 */
final class Logging {

    private static final Logger LOG = LoggerFactory.getLogger("io.github.premocloud.typesafe");

    /** Masked in full. The union of what the official Python and JavaScript SDKs cover. */
    private static final Set<String> SECRET_HEADERS = Set.of(
            "authorization", "proxy-authorization", "api-key", "x-api-key", "cookie", "set-cookie");

    private Logging() {
    }

    static void request(String format, Object... arguments) {
        LOG.debug(format, arguments);
    }

    /** Guards the wire calls, so a redacted header string is never built when TRACE is off. */
    static boolean wireEnabled() {
        return LOG.isTraceEnabled();
    }

    /**
     * One direction of the exchange.
     *
     * @param arrow {@code ->} for what was sent, {@code <-} for what came back
     */
    static void wire(String tag, String arrow, String summary, HttpHeaders headers, @Nullable String body) {
        LOG.trace("{} {} {} headers={} body={}", tag, arrow, summary, redact(headers), body == null ? "" : body);
    }

    /**
     * Header names and values with credentials masked. The only place redaction happens, so no call
     * site can leak one.
     */
    private static String redact(HttpHeaders headers) {
        return headers.map().entrySet().stream()
                .map(header -> header.getKey() + "=" + value(header.getKey(), header.getValue()))
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String value(String name, List<String> values) {
        String lower = name.toLowerCase(Locale.ROOT);

        // The named set covers what is documented; the substring check catches a header this SDK
        // never anticipated, which a caller can add through Builder.header.
        if (SECRET_HEADERS.contains(lower) || lower.contains("token") || lower.contains("secret")) {
            return "***";
        }

        return values.isEmpty() ? "" : values.get(0);
    }
}
