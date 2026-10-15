package io.github.premocloud.typesafe.examples.triage;

import io.github.premocloud.typesafe.Choice;
import io.github.premocloud.typesafe.ChoiceAnswer;
import io.github.premocloud.typesafe.ModelCard;
import io.github.premocloud.typesafe.Noul;
import io.github.premocloud.typesafe.Score;
import io.github.premocloud.typesafe.ScoreAnswer;
import io.github.premocloud.typesafe.TypeSafeApiException;
import io.github.premocloud.typesafe.TypeSafeClient;
import io.github.premocloud.typesafe.TypeSafeResponse;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Triages one support ticket in a single call: is it about billing, the customer's tone, urgency, and refund risk. */
public final class TicketTriage {

    record Ticket(String subject, String body) {
    }

    static final Ticket TICKET = new Ticket(
            "Charged twice this month",
            "Hi, I see two charges of $49 on my card for August. I only have one account. Please fix this ASAP, I'm pretty frustrated.");

    public static void main(String[] args) {
        run(TypeSafeClient.fromEnvironment());
    }

    static void run(TypeSafeClient client) {
        try {
            List<ModelCard> models = client.models().list();
            System.out.println("Available models: " + models.stream().map(ModelCard::name).collect(Collectors.joining(", ")));

            TypeSafeResponse response = client.systemOne(Map.of("ticket", TICKET), Map.of(
                    "is_billing", Noul.of("Is `ticket` about billing?"),
                    "tone", Choice.of("What is the customer's tone in `ticket`?", "calm", "frustrated", "angry"),
                    "urgency", Score.of("How urgent is `ticket`?", "can wait", "this week", "today", "right now"),
                    "refund_risk", Score.of("How likely is the customer in `ticket` to demand a refund?", "unlikely", "possible", "likely")));

            ChoiceAnswer<String> tone = response.choice("tone");

            System.out.printf("billing?     %.0f%%%n", response.noul("is_billing") * 100);
            System.out.printf("tone         %s (%.0f%%)%n", tone.choice(), tone.probabilities().get(tone.choice()) * 100);
            System.out.printf("urgency      %s%n", level(response.score("urgency")));
            System.out.printf("refund risk  %s%n", level(response.score("refund_risk")));
            System.out.printf("tokens       %d in / %d out%n", response.usage().inputTokens(), response.usage().outputTokens());
        } catch (TypeSafeApiException e) {
            System.err.println("TypeSafe error: " + e.getMessage() + e.requestId().map(id -> " (request " + id + ")").orElse(""));
        }
    }

    static String level(ScoreAnswer s) {
        String likeliest = s.probabilities().entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return "%.2f (%s)".formatted(s.score(), s.legend().get(likeliest));
    }
}
