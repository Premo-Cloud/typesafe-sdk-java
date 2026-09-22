package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * @param score         probability-weighted position from 0 to the top level index
 * @param probabilities probability per level index (keys are the index as a string)
 * @param confidence    0 to 1, how concentrated the distribution is
 * @param legend        level index back to the description that was sent: a String, or the Map or List it was given as
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreAnswer(
        double score,
        Map<String, Double> probabilities,
        double confidence,
        Map<String, Object> legend
) implements TypeSafeAnswer {

    /** Jackson entry point: a score answer missing its score, probabilities, or confidence is malformed. */
    @JsonCreator
    ScoreAnswer(
            @JsonProperty("score") Double score,
            @JsonProperty("probabilities") Map<String, Double> probabilities,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("legend") Map<String, Object> legend,
            @JsonProperty("type") String ignoredType
    ) {
        this(TypeSafeAnswer.required(score, "score", "score").doubleValue(),
                TypeSafeAnswer.required(probabilities, "score", "probabilities"),
                TypeSafeAnswer.required(confidence, "score", "confidence").doubleValue(),
                legend);
    }
}
