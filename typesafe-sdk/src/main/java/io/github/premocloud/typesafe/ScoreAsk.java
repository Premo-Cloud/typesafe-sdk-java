package io.github.premocloud.typesafe;

import java.util.Objects;

/** A {@link Score} under its key. Create one with {@link Ask#score}. */
public final class ScoreAsk extends Ask<ScoreAnswer> {

    private final Score question;

    ScoreAsk(String key, Score question) {
        super(key);
        this.question = Objects.requireNonNull(question, "question");
    }

    @Override
    public Score question() {
        return question;
    }

    @Override
    ScoreAnswer read(TypeSafeResponse response) {
        return response.score(key());
    }
}
