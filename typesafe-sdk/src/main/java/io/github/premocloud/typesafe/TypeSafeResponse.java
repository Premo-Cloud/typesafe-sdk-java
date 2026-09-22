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

    public ChoiceAnswer choice(String key) {
        return answer(key, ChoiceAnswer.class);
    }

    public ScoreAnswer score(String key) {
        return answer(key, ScoreAnswer.class);
    }

    public Map<String, NoulAnswer> nouls() {
        return answersOf(NoulAnswer.class);
    }

    public Map<String, ChoiceAnswer> choices() {
        return answersOf(ChoiceAnswer.class);
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
