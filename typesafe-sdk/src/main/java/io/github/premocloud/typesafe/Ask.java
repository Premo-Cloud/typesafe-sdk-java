package io.github.premocloud.typesafe;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * A question under its key, typed by its answer. Declare it once, ask it, and read the answer back through it, so the
 * key and the label type are not repeated at the response and a mismatch is a compile error.
 *
 * <pre>{@code
 * static final Ask<NoulAnswer> URGENT = Ask.noul("urgent", n -> n.instructions("Does `email` need a reply today?"));
 * static final Ask<ChoiceAnswer<Dept>> DEPT = Ask.choice("dept", Dept.class, c -> c
 *         .instructions("Which team should handle `email`?")
 *         .option(Dept.BILLING, "Invoices, refunds, payment methods")
 *         .option(Dept.SECURITY, o -> o.what("Credential theft").notFor("Legitimate requests")));
 *
 * TypeSafeResponse response = client.systemOne(Map.of("email", email), URGENT, DEPT);
 * double urgent = response.answer(URGENT).noul();
 * Dept dept = response.answer(DEPT).choice();
 * }</pre>
 *
 * The subtypes mirror the question types, {@link NoulAsk}, {@link ChoiceAsk}, and {@link ScoreAsk}, so a {@code switch}
 * over an {@code Ask} is exhaustive on Java 21 and later. Only these factories create them. Asks are immutable and can
 * be shared across requests. A request rejects a second question under an asked key.
 *
 * <p>Asks are handles, not values: they compare by identity, so two asks built alike are not {@code equals}. Declare
 * each once, usually as a {@code static final} field, and reuse it.
 *
 * @param <A> what {@link TypeSafeResponse#answer(Ask)} returns: {@link NoulAnswer}, {@code ChoiceAnswer<E>}, or {@link ScoreAnswer}
 */
public abstract sealed class Ask<A> permits NoulAsk, ChoiceAsk, ScoreAsk {

    private final String key;

    Ask(String key) {
        this.key = Objects.requireNonNull(key, "key");
    }

    public static NoulAsk noul(String key, Noul question) {
        return new NoulAsk(key, question);
    }

    public static NoulAsk noul(String key, Consumer<Noul.Builder> configure) {
        return noul(key, Noul.of(configure));
    }

    /** Reads back with String labels. An enum choice goes through {@link #choice(String, Class, Choice)} instead. */
    public static ChoiceAsk<String> choice(String key, Choice<String> question) {
        return new ChoiceAsk<>(key, String.class, question, response -> response.choice(key));
    }

    public static ChoiceAsk<String> choice(String key, Consumer<Choice.Builder<String>> configure) {
        return choice(key, Choice.of(configure));
    }

    /**
     * Reads back with labels as constants of {@code labels}, as {@link TypeSafeResponse#choice(String, Class)} does.
     *
     * @throws IllegalArgumentException if a label of {@code question} is not a constant of {@code labels}
     */
    public static <E extends Enum<E>> ChoiceAsk<E> choice(String key, Class<E> labels, Choice<E> question) {
        return new ChoiceAsk<>(key, labels, question, response -> response.choice(key, labels));
    }

    public static <E extends Enum<E>> ChoiceAsk<E> choice(String key, Class<E> labels, Consumer<Choice.Builder<E>> configure) {
        Choice.Builder<E> builder = Choice.builder(labels);
        configure.accept(builder);
        return choice(key, labels, builder.build());
    }

    public static ScoreAsk score(String key, Score question) {
        return new ScoreAsk(key, question);
    }

    public static ScoreAsk score(String key, Consumer<Score.Builder> configure) {
        return score(key, Score.of(configure));
    }

    /** The question id the answer comes back under. */
    public String key() {
        return key;
    }

    public abstract TypeSafeQuestion question();

    abstract A read(TypeSafeResponse response);

    @Override
    public String toString() {
        return "%s[key=%s, question=%s]".formatted(getClass().getSimpleName(), key, question());
    }
}
