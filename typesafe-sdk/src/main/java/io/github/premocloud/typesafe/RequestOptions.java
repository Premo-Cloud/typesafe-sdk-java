package io.github.premocloud.typesafe;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Per-call overrides for timeout, retry policy, and headers. Anything left unset inherits the client's setting.
 *
 * <pre>{@code
 * client.systemOne(request, RequestOptions.of(o -> o.timeout(Duration.ofSeconds(30)).maxRetries(0)));
 * client.models().list(RequestOptions.of(o -> o.header("X-Trace", traceId)));
 * }</pre>
 *
 * @param timeout     per-attempt timeout, or {@code null} for the client's
 * @param retryPolicy retry policy, or {@code null} for the client's
 * @param maxRetries  retry count applied on top of {@code retryPolicy}, or of the client's policy when that is
 *                    {@code null}; {@code null} keeps the policy's own count
 * @param headers     headers added to this call; same-named client headers are replaced
 */
public record RequestOptions(@Nullable Duration timeout, @Nullable RetryPolicy retryPolicy, @Nullable Integer maxRetries,
                             Map<String, String> headers) {

    public static final RequestOptions NONE = new RequestOptions(null, null, null, Map.of());

    public RequestOptions {
        if (Objects.nonNull(maxRetries) && maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be zero or more");
        }

        headers = Map.copyOf(headers);
    }

    /** Options without a retry-count override. */
    public RequestOptions(@Nullable Duration timeout, @Nullable RetryPolicy retryPolicy, Map<String, String> headers) {
        this(timeout, retryPolicy, null, headers);
    }

    public static RequestOptions of(Consumer<Builder> configure) {
        Builder builder = new Builder();
        configure.accept(builder);
        return builder.build();
    }

    /** The policy for this call: this call's policy or the client's, with this call's retry count applied. */
    RetryPolicy resolveRetryPolicy(RetryPolicy clientPolicy) {
        RetryPolicy policy = Objects.requireNonNullElse(retryPolicy, clientPolicy);
        return Objects.isNull(maxRetries) ? policy : policy.withMaxRetries(maxRetries);
    }

    public static final class Builder {
        private @Nullable Duration timeout;
        private @Nullable RetryPolicy retryPolicy;
        private @Nullable Integer maxRetries;
        private final Map<String, String> headers = new LinkedHashMap<>();

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

        /**
         * Shorthand for the client's policy with a different retry count; {@code 0} disables retries for this call. When
         * {@link #retryPolicy} is also set, the count is applied to that policy instead.
         */
        public Builder maxRetries(int maxRetries) {
            if (maxRetries < 0) {
                throw new IllegalArgumentException("maxRetries must be zero or more");
            }

            this.maxRetries = maxRetries;
            return this;
        }

        public Builder header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        public RequestOptions build() {
            return new RequestOptions(timeout, retryPolicy, maxRetries, headers);
        }
    }
}
