package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Answers keyed by the question ids from the request. Read one answer with {@link #noul}, {@link #choice}, or
 * {@link #score}, or all answers of a kind with {@link #nouls()}, {@link #choices()}, or {@link #scores()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TypeSafeResponse(String model, Map<String, TypeSafeAnswer> answers, TypeSafeUsage usage) {

    /** A response without its usage block is malformed; reading it later as {@code null} would be the first sign. */
    public TypeSafeResponse {
        if (Objects.isNull(usage)) {
            throw new IllegalArgumentException("response is missing 'usage'");
        }
    }

    /** @return probability that the yes/no question answered yes, 0 to 1 */
    public double noul(String key) {
        return answer(key, NoulAnswer.class).noul();
    }

    /** @return the choice answer with its labels as Strings, as they came off the wire */
    @SuppressWarnings("unchecked")
    public ChoiceAnswer<String> choice(String key) {
        return answer(key, ChoiceAnswer.class);
    }

    /**
     * @return the choice answer with its labels as constants of {@code labels}, for a question built from that enum
     * @throws IllegalArgumentException if a label in the answer is not a constant of {@code labels}
     */
    public <E extends Enum<E>> ChoiceAnswer<E> choice(String key, Class<E> labels) {
        return choice(key).as(labels);
    }

    public ScoreAnswer score(String key) {
        return answer(key, ScoreAnswer.class);
    }

    public Map<String, NoulAnswer> nouls() {
        return answersOf(NoulAnswer.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, ChoiceAnswer<String>> choices() {
        return (Map) answersOf(ChoiceAnswer.class);
    }

    public Map<String, ScoreAnswer> scores() {
        return answersOf(ScoreAnswer.class);
    }

    private <T extends TypeSafeAnswer> T answer(String key, Class<T> expected) {
        TypeSafeAnswer answer = answers.get(key);

        if (Objects.isNull(answer)) {
            throw new IllegalArgumentException("No answer for question '%s'; answered: %s".formatted(key, answers.keySet()));
        }

        if (!expected.isInstance(answer)) {
            throw new IllegalArgumentException("Answer '%s' is a %s, not a %s"
                    .formatted(key, answer.getClass().getSimpleName(), expected.getSimpleName()));
        }

        return expected.cast(answer);
    }

    private <T extends TypeSafeAnswer> Map<String, T> answersOf(Class<T> kind) {
        Map<String, T> matching = new LinkedHashMap<>();

        answers.forEach((key, answer) -> {
            if (kind.isInstance(answer)) {
                matching.put(key, kind.cast(answer));
            }
        });

        return Collections.unmodifiableMap(matching);
    }
}
