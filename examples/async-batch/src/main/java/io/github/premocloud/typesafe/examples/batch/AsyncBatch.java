package io.github.premocloud.typesafe.examples.batch;

import io.github.premocloud.typesafe.Ask;
import io.github.premocloud.typesafe.NoulAsk;
import io.github.premocloud.typesafe.RequestOptions;
import io.github.premocloud.typesafe.RetryPolicy;
import io.github.premocloud.typesafe.Score;
import io.github.premocloud.typesafe.ScoreAnswer;
import io.github.premocloud.typesafe.ScoreAsk;
import io.github.premocloud.typesafe.TypeSafeApiException;
import io.github.premocloud.typesafe.TypeSafeClient;
import io.github.premocloud.typesafe.TypeSafeResponse;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Moderates a batch of reviews concurrently: one request per review, and no thread held while they are in flight. */
public final class AsyncBatch {

    record Row(String review, String spam, String tone) {

        static Row of(String review, TypeSafeResponse response) {
            double spam = response.answer(IS_SPAM).noul();
            ScoreAnswer tone = response.answer(TONE);
            return new Row(review, "%.0f%%".formatted(spam * 100),
                    "%.2f (%s)".formatted(tone.score(), TONES.get((int) Math.round(tone.score()))));
        }

        static Row failed(String review, Throwable error) {
            // Dependent stages wrap the SDK's exception
            Throwable cause = error instanceof CompletionException ? error.getCause() : error;
            String requestId = cause instanceof TypeSafeApiException api
                    ? api.requestId().map(id -> " (request " + id + ")").orElse("") : "";
            return new Row(review, "failed", cause.getMessage() + requestId);
        }
    }

    static final NoulAsk IS_SPAM = Ask.noul("is_spam", n -> n
            .instructions("Is `review` spam rather than a customer's opinion of the product?")
            .whenTrue("Advertises something else, pushes a link or a coupon, or says nothing about the product")
            .whenFalse("A real opinion, however short, rude, or badly spelled"));

    static final List<String> TONES = List.of("hostile", "disappointed", "neutral", "pleased", "glowing");

    static final ScoreAsk TONE = Ask.score("tone",
            Score.of("How does the author of `review` feel about the product?", TONES));

    static final RequestOptions OPTIONS = RequestOptions.of(o -> o.timeout(Duration.ofSeconds(20)));

    static final List<String> REVIEWS = List.of(
            "Five stars. Survived a drop onto concrete without a scratch.",
            "Battery died after a week. Absolute garbage, never again.",
            "BEST PRICES ON WATCHES >>> cheap-watch-deals.example",
            "It's fine. Does what the box says.",
            "Love it so much I bought one for my sister too!",
            "Shipped late and the strap was the wrong color.",
            "Earn $5,000 a week from home, DM me for details",
            "Decent, though the manual could be clearer.");

    static final String COLUMNS = "%-60s %6s  %s%n";

    public static void main(String[] args) {
        // Eight requests at once can trip a rate limit, so the batch waits it out more patiently than the default
        run(TypeSafeClient.builder()
                .retryPolicy(RetryPolicy.of(r -> r.maxRetries(4).backoffMax(Duration.ofSeconds(10))))
                .build());
    }

    static void run(TypeSafeClient client) {
        List<CompletableFuture<Row>> rows = REVIEWS.stream()
                .map(review -> client
                        .systemOneAsync(r -> r.state("review", review).ask(IS_SPAM, TONE), OPTIONS)
                        .thenApply(response -> Row.of(review, response))
                        .exceptionally(error -> Row.failed(review, error)))
                .toList();

        CompletableFuture.allOf(rows.toArray(CompletableFuture[]::new)).join();

        System.out.printf(COLUMNS, "review", "spam", "tone");
        rows.stream().map(CompletableFuture::join)
                .forEach(row -> System.out.printf(COLUMNS, row.review(), row.spam(), row.tone()));
    }
}
