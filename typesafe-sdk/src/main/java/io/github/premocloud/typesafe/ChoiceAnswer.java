package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * @param <E>           the label type: {@code String} as read from the response, or an enum after {@link #as(Class)}
 * @param choice        highest-probability option
 * @param probabilities probability per option, summing to 1
 * @param confidence    0 to 1, how concentrated the distribution is
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChoiceAnswer<E>(E choice, Map<E, Double> probabilities, double confidence) implements TypeSafeAnswer {

    /** Jackson entry point: a choice answer missing any of its fields is malformed. Labels come off the wire as Strings. */
    @JsonCreator
    @SuppressWarnings("unchecked")
    ChoiceAnswer(
            @JsonProperty("choice") String choice,
            @JsonProperty("probabilities") Map<String, Double> probabilities,
            @JsonProperty("confidence") Double confidence,
            @JsonProperty("type") String ignoredType
    ) {
        this((E) TypeSafeAnswer.required(choice, "choice", "choice"),
                (Map<E, Double>) TypeSafeAnswer.required(probabilities, "choice", "probabilities"),
                TypeSafeAnswer.required(confidence, "choice", "confidence").doubleValue());
    }

    /**
     * The same answer with its labels as constants of {@code labels}, matched by name. The probabilities come back in
     * the enum's declaration order.
     *
     * @throws IllegalArgumentException if the chosen label or any probability key is not a constant of {@code labels}
     */
    public <T extends Enum<T>> ChoiceAnswer<T> as(Class<T> labels) {
        Map<T, Double> typed = new EnumMap<>(labels);
        probabilities.forEach((label, probability) -> typed.put(constant(labels, label), probability));
        return new ChoiceAnswer<>(constant(labels, choice), Collections.unmodifiableMap(typed), confidence);
    }

    private static <T extends Enum<T>> T constant(Class<T> labels, Object label) {
        String name = String.valueOf(label);

        for (T constant : labels.getEnumConstants()) {
            if (constant.name().equals(name)) {
                return constant;
            }
        }

        throw new IllegalArgumentException("Choice '%s' is not a constant of %s; expected one of %s"
                .formatted(name, labels.getSimpleName(), Arrays.toString(labels.getEnumConstants())));
    }
}
