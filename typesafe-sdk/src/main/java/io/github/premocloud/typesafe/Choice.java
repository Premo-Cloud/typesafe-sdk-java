package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Pick one option from a named set. The answer carries a probability per option.
 *
 * <p>Labels are plain strings, or the constants of an enum. An enum choice reads back typed through
 * {@link TypeSafeResponse#choice(String, Class)}, so a misspelled label is a compile error and a switch over the
 * answer is exhaustive. The wire form is the same either way: the label is the constant's name.
 *
 * <pre>{@code
 * Choice.of("What is this ticket about?", "billing", "technical", "other")       // undescribed labels
 * Choice.of("Which team should handle this?", Dept.class)                         // one option per constant
 * Choice.builder(Dept.class).instructions("Which team should handle this?")
 *         .option(Dept.BILLING, "Invoices, refunds, payment methods")
 *         .option(Dept.SECURITY, o -> o.what("Credential theft").notFor("Legitimate requests"))
 *         .build()
 * Choice.of(c -> c.instructions("Which category?")
 *         .option("MARKETING", "Promotional content sent to a list")
 *         .option("PHISHING", o -> o.what("Credential theft").notFor("Legitimate requests"))
 *         .option("OTHER"))
 * }</pre>
 *
 * @param <E>          the label type: {@code String}, or an enum whose constant names are the labels
 * @param instructions the question, or {@code null}
 * @param criteria     labels mapped to a description, a {@link Criterion}, or {@code null} for an undescribed label
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Choice<E>(@Nullable Object instructions, Map<String, @Nullable Object> criteria) implements TypeSafeQuestion {

    /** Labels without descriptions, as in {@code choice("Which?", {billing: null, technical: null})}. */
    public static Choice<String> of(Object instructions, String... labels) {
        Builder<String> builder = builder().instructions(instructions);

        for (String label : labels) {
            builder.option(label);
        }

        return builder.build();
    }

    public static Choice<String> of(Object instructions, Map<String, ?> criteria) {
        Builder<String> builder = builder().instructions(instructions);
        criteria.forEach(builder::option);
        return builder.build();
    }

    /** One undescribed option per constant of {@code labels}, in declaration order. */
    public static <E extends Enum<E>> Choice<E> of(Object instructions, Class<E> labels) {
        Builder<E> builder = builder(labels).instructions(instructions);

        for (E constant : labels.getEnumConstants()) {
            builder.option(constant);
        }

        return builder.build();
    }

    public static Choice<String> of(Consumer<Builder<String>> configure) {
        Builder<String> builder = builder();
        configure.accept(builder);
        return builder.build();
    }

    public static Builder<String> builder() {
        return new Builder<>(Function.identity());
    }

    /** A builder whose options are constants of {@code labels}; add the ones to ask about with {@code option}. */
    public static <E extends Enum<E>> Builder<E> builder(Class<E> labels) {
        return new Builder<>(Enum::name);
    }

    public static final class Builder<E> {
        private final Function<E, String> label;
        private @Nullable Object instructions;
        private final Map<String, @Nullable Object> options = new LinkedHashMap<>();

        private Builder(Function<E, String> label) {
            this.label = label;
        }

        public Builder<E> instructions(Object instructions) {
            this.instructions = instructions;
            return this;
        }

        /** An undescribed label. */
        public Builder<E> option(E label) {
            return option(label, (Object) null);
        }

        public Builder<E> option(E label, @Nullable Object description) {
            options.put(this.label.apply(label), description);
            return this;
        }

        public Builder<E> option(E label, Consumer<Criterion.Builder> configure) {
            return option(label, Criterion.of(configure));
        }

        public Choice<E> build() {
            if (options.isEmpty()) {
                throw new IllegalStateException("A choice question needs at least one option");
            }

            return new Choice<>(instructions, Collections.unmodifiableMap(new LinkedHashMap<>(options)));
        }
    }
}
