package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Token counts for one request.
 *
 * @param inputTokens  billable input tokens
 * @param outputTokens output tokens used to answer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeSafeUsage(long inputTokens, long outputTokens) {

    /** Jackson entry point: a usage block missing a count, or carrying it as null, is malformed rather than 0. */
    @JsonCreator
    TypeSafeUsage(@JsonProperty("input_tokens") Long inputTokens, @JsonProperty("output_tokens") Long outputTokens) {
        this(required(inputTokens, "input_tokens").longValue(), required(outputTokens, "output_tokens").longValue());
    }

    private static Long required(Long value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("usage is missing '%s'".formatted(field));
        }

        return value;
    }
}
