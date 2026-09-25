package io.github.premocloud.typesafe;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;

/**
 * A {@link Choice} under its key, with the type its labels read back as. Create one with {@link Ask#choice}.
 *
 * @param <E> {@code String}, or the enum whose constant names are the labels
 */
public final class ChoiceAsk<E> extends Ask<ChoiceAnswer<E>> {

    private final Class<E> labels;
    private final Choice<E> question;
    private final Function<TypeSafeResponse, ChoiceAnswer<E>> read;

    /** Checks an enum choice's labels here, since {@code Choice}'s constructor accepts any label for any {@code E}. */
    ChoiceAsk(String key, Class<E> labels, Choice<E> question, Function<TypeSafeResponse, ChoiceAnswer<E>> read) {
        super(key);
        Objects.requireNonNull(labels, "labels");
        Objects.requireNonNull(question, "question");

        if (labels.isEnum()) {
            for (String label : question.criteria().keySet()) {
                if (Arrays.stream(labels.getEnumConstants()).noneMatch(constant -> ((Enum<?>) constant).name().equals(label))) {
                    throw new IllegalArgumentException("Choice '%s' is not a constant of %s; expected one of %s"
                            .formatted(label, labels.getSimpleName(), Arrays.toString(labels.getEnumConstants())));
                }
            }
        }

        this.labels = labels;
        this.question = question;
        this.read = read;
    }

    /** {@code String.class}, or the enum the labels are constants of. */
    public Class<E> labels() {
        return labels;
    }

    @Override
    public Choice<E> question() {
        return question;
    }

    @Override
    ChoiceAnswer<E> read(TypeSafeResponse response) {
        return read.apply(response);
    }
}
