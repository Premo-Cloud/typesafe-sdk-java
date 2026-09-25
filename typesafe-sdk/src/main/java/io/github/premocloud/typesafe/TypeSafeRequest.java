package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * One evaluation request: a single {@code state} plus one or more independent questions asked over it.
 *
 * <pre>{@code
 * TypeSafeRequest.of(r -> r
 *     .state(emailState)
 *     .noul("is_phishing", n -> n.instructions("Is `email` phishing?").whenTrue("...").whenFalse("..."))
 *     .choice("category", c -> c.instructions("Which category?").option("A", "...").option("B"))
 *     .score("urgency", s -> s.instructions("How urgent?").level("none").level("high")));
 * }</pre>
 *
 * @param state     any Jackson-serializable value: a String, Map, or record
 * @param model     model id, or {@code null} to use the client's default
 * @param questions keyed by caller-chosen ids that come back as the answer keys
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TypeSafeRequest(Object state, @Nullable String model, Map<String, TypeSafeQuestion> questions) {

    public static TypeSafeRequest of(Object state, Map<String, ? extends TypeSafeQuestion> questions) {
        Builder builder = builder().state(state);
        questions.forEach(builder::question);
        return builder.build();
    }

    /** {@code TypeSafeRequest.of(Map.of("email", email), URGENT, DEPT)}; read the answers back with the same asks. */
    public static TypeSafeRequest of(Object state, Ask<?> first, Ask<?>... more) {
        return builder().state(state).ask(first).ask(more).build();
    }

    public static TypeSafeRequest of(Consumer<Builder> configure) {
        Builder builder = builder();
        configure.accept(builder);
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public TypeSafeRequest withModel(String model) {
        return new TypeSafeRequest(state, model, questions);
    }

    public static final class Builder {
        private @Nullable Object state;
        private @Nullable String model;
        private final Map<String, TypeSafeQuestion> questions = new LinkedHashMap<>();
        private final Set<String> askedKeys = new HashSet<>();

        public Builder state(Object state) {
            this.state = state;
            return this;
        }

        /** Adds one named field to an object state, starting one if no state is set yet. A state already set must be a {@code Map}. */
        @SuppressWarnings("unchecked")
        public Builder state(String key, Object value) {
            if (Objects.isNull(state)) {
                state = new LinkedHashMap<String, Object>();
            }

            if (!(state instanceof Map<?, ?>)) {
                throw new IllegalStateException("state(key, value) needs an object state; call state(Map) first");
            }

            Map<String, Object> fields = new LinkedHashMap<>((Map<String, Object>) state);
            fields.put(key, value);
            state = fields;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder noul(String key, Consumer<Noul.Builder> configure) {
            return question(key, Noul.of(configure));
        }

        public Builder choice(String key, Consumer<Choice.Builder<String>> configure) {
            return question(key, Choice.of(configure));
        }

        /** A choice whose labels are constants of {@code labels}; add the ones to ask about with {@code option}. */
        public <E extends Enum<E>> Builder choice(String key, Class<E> labels, Consumer<Choice.Builder<E>> configure) {
            Choice.Builder<E> builder = Choice.builder(labels);
            configure.accept(builder);
            return question(key, builder.build());
        }

        public Builder score(String key, Consumer<Score.Builder> configure) {
            return question(key, Score.of(configure));
        }

        /**
         * Adds each ask's question under its key.
         *
         * @throws IllegalArgumentException if the request already has a question under one of the keys
         */
        public Builder ask(Ask<?>... asks) {
            for (Ask<?> ask : asks) {
                if (questions.containsKey(ask.key())) {
                    throw new IllegalArgumentException("The request already has a question '%s'".formatted(ask.key()));
                }

                questions.put(ask.key(), ask.question());
                askedKeys.add(ask.key());
            }

            return this;
        }

        /**
         * Adds a question, replacing any earlier question under the same key.
         *
         * @throws IllegalArgumentException if the key belongs to an {@link Ask}, whose answer would no longer match it
         */
        public Builder question(String key, TypeSafeQuestion question) {
            if (askedKeys.contains(key)) {
                throw new IllegalArgumentException("Question '%s' was added with ask(...) and cannot be replaced".formatted(key));
            }

            questions.put(key, question);
            return this;
        }

        public TypeSafeRequest build() {
            if (Objects.isNull(state)) {
                throw new IllegalStateException("A TypeSafe request needs state to evaluate");
            }

            if (questions.isEmpty()) {
                throw new IllegalStateException("At least one question is required");
            }

            return new TypeSafeRequest(state, model, Collections.unmodifiableMap(new LinkedHashMap<>(questions)));
        }
    }
}
