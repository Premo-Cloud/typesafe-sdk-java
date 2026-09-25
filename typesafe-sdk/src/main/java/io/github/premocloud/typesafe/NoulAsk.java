package io.github.premocloud.typesafe;

import java.util.Objects;

/** A {@link Noul} under its key. Create one with {@link Ask#noul}. */
public final class NoulAsk extends Ask<NoulAnswer> {

    private final Noul question;

    NoulAsk(String key, Noul question) {
        super(key);
        this.question = Objects.requireNonNull(question, "question");
    }

    @Override
    public Noul question() {
        return question;
    }

    @Override
    NoulAnswer read(TypeSafeResponse response) {
        return response.answer(key(), NoulAnswer.class);
    }
}
