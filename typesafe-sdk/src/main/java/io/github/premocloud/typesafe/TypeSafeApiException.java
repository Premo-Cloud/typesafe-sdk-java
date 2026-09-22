package io.github.premocloud.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.Nullable;

import java.net.http.HttpHeaders;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A non-2xx response from the API. {@link #fromResponse} picks the status-specific subclass. The message is
 * {@code "<status> <detail>"}, where the detail is drawn from the body's {@code error}, {@code message}, or
 * {@code detail} field when present.
 */
public class TypeSafeApiException extends TypeSafeException {

    public static final String REQUEST_ID_HEADER = "x-typesafe-request-id";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_RAW_BODY_IN_MESSAGE = 200;

    private final int status;
    private final @Nullable String body;
    private final HttpHeaders headers;

    protected TypeSafeApiException(int status, @Nullable String body, HttpHeaders headers) {
        super(describe(status, body));
        this.status = status;
        this.body = body;
        this.headers = headers;
    }

    public static TypeSafeApiException fromResponse(int status, @Nullable String body, HttpHeaders headers) {
        return switch (status) {
            case 400 -> new TypeSafeBadRequestException(status, body, headers);
            case 401 -> new TypeSafeAuthenticationException(status, body, headers);
            case 402 -> new TypeSafePaymentRequiredException(status, body, headers);
            case 403 -> new TypeSafePermissionDeniedException(status, body, headers);
            case 404 -> new TypeSafeNotFoundException(status, body, headers);
            case 413 -> new TypeSafePayloadTooLargeException(status, body, headers);
            case 422 -> new TypeSafeUnprocessableEntityException(status, body, headers);
            case 429 -> new TypeSafeRateLimitException(status, body, headers);
            default -> status >= 500 ? new TypeSafeInternalServerException(status, body, headers) : new TypeSafeApiException(status, body, headers);
        };
    }

    public int status() {
        return status;
    }

    /** @return the raw response body, or {@code null} when it was empty */
    public @Nullable String body() {
        return body;
    }

    public HttpHeaders headers() {
        return headers;
    }

    public Optional<String> requestId() {
        return headers.firstValue(REQUEST_ID_HEADER);
    }

    static String describe(int status, @Nullable String body) {
        if (Objects.isNull(body) || body.isEmpty()) {
            return status + " status code (no body)";
        }

        String detail = extractMessage(body);

        if (Objects.nonNull(detail)) {
            return status + " " + detail;
        }

        return status + " " + (body.length() > MAX_RAW_BODY_IN_MESSAGE ? body.substring(0, MAX_RAW_BODY_IN_MESSAGE) + "…" : body);
    }

    private static @Nullable String extractMessage(String body) {
        JsonNode node;

        try {
            node = JSON.readTree(body);
        } catch (Exception e) {
            return null;
        }

        if (node.isTextual()) {
            return node.asText().isEmpty() ? null : node.asText();
        }

        if (!node.isObject()) {
            return null;
        }

        JsonNode error = node.path("error");

        if (error.isTextual()) {
            return error.asText();
        }

        if (error.path("message").isTextual()) {
            return error.path("message").asText();
        }

        if (node.path("message").isTextual()) {
            return node.path("message").asText();
        }

        JsonNode detail = node.path("detail");

        if (detail.isTextual()) {
            return detail.asText();
        }

        if (detail.path("message").isTextual()) {
            return detail.path("message").asText();
        }

        if (detail.isArray()) {
            return describeValidationErrors(detail);
        }

        return null;
    }

    /** Formats validation errors as semicolon-separated {@code path: message} entries. */
    private static @Nullable String describeValidationErrors(JsonNode errors) {
        List<String> parts = new ArrayList<>();

        for (JsonNode error : errors) {
            if (!error.path("msg").isTextual()) {
                continue;
            }

            List<String> location = new ArrayList<>();

            for (JsonNode segment : error.path("loc")) {
                if (!"body".equals(segment.asText())) {
                    location.add(segment.asText());
                }
            }

            parts.add(location.isEmpty() ? error.path("msg").asText() : String.join(".", location) + ": " + error.path("msg").asText());
        }

        return parts.isEmpty() ? null : String.join("; ", parts);
    }
}
