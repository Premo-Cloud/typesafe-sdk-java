package io.github.premocloud.typesafe.examples.routing;

import io.github.premocloud.typesafe.Ask;
import io.github.premocloud.typesafe.ChoiceAnswer;
import io.github.premocloud.typesafe.ChoiceAsk;
import io.github.premocloud.typesafe.NoulAsk;
import io.github.premocloud.typesafe.TypeSafeApiException;
import io.github.premocloud.typesafe.TypeSafeClient;
import io.github.premocloud.typesafe.TypeSafeResponse;

import java.util.List;
import java.util.Map;

/** Routes support tickets through Asks declared once: the answer is a {@code Team}, so the queue is a switch. */
public final class TypedRouting {

    enum Team { BILLING, TECH_SUPPORT, SALES }

    static final ChoiceAsk<Team> TEAM = Ask.choice("team", Team.class, c -> c
            .instructions("Which team should handle `ticket`?")
            .option(Team.BILLING, "Invoices, charges, refunds, payment methods")
            .option(Team.TECH_SUPPORT, o -> o
                    .what("Something the customer already has is broken or confusing")
                    .notFor("Questions about plans or features they do not have yet"))
            .option(Team.SALES, o -> o
                    .what("Buying, upgrading, pricing, or evaluating the product")
                    .examples("Do you offer annual plans?", "Can we get a demo for our team?")));

    static final NoulAsk ESCALATE = Ask.noul("escalate", n -> n
            .instructions("Should `ticket` go to a team lead right away?")
            .whenTrue("Threatens to cancel, sue, or charge back; reports data loss, an outage, or a security problem")
            .whenFalse("A routine request a front-line agent can close"));

    static final List<String> TICKETS = List.of(
            "I was charged twice this month. Refund it today or I dispute it.",
            "The export button has done nothing since yesterday's update.",
            "We're a 40-person team evaluating Pro. Any volume discount?",
            "How do I update the card on file?");

    static final String COLUMNS = "%-64s  %-12s  %4s  %-8s  %s%n";

    public static void main(String[] args) {
        run(TypeSafeClient.fromEnvironment());
    }

    static void run(TypeSafeClient client) {
        System.out.printf(COLUMNS, "ticket", "team", "prob", "queue", "escalate");

        for (String ticket : TICKETS) {
            try {
                TypeSafeResponse response = client.systemOne(Map.of("ticket", ticket), TEAM, ESCALATE);

                ChoiceAnswer<Team> team = response.answer(TEAM);
                boolean escalate = response.answer(ESCALATE).noul() > 0.5;

                System.out.printf(COLUMNS, ticket, team.choice(),
                        "%.0f%%".formatted(team.probabilities().get(team.choice()) * 100),
                        queue(team.choice()), escalate ? "yes" : "no");
            } catch (TypeSafeApiException e) {
                System.out.println("TypeSafe error: " + e.getMessage()
                        + e.requestId().map(id -> " (request " + id + ")").orElse(""));
            }
        }
    }

    /** Add a Team and this stops compiling until the new constant has a queue. */
    static String queue(Team team) {
        return switch (team) {
            case BILLING -> "finance";
            case TECH_SUPPORT -> "helpdesk";
            case SALES -> "sales";
        };
    }
}
