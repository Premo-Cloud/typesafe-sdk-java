package io.github.premocloud.typesafe.examples.spring;

import io.github.premocloud.typesafe.Ask;
import io.github.premocloud.typesafe.ChoiceAsk;
import io.github.premocloud.typesafe.NoulAsk;
import io.github.premocloud.typesafe.TypeSafeClient;
import io.github.premocloud.typesafe.TypeSafeResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Routes a posted ticket to a team's queue, escalating when it needs a lead. */
@RestController
@RequestMapping("/tickets")
public class TicketController {

    public enum Team { BILLING, TECH_SUPPORT, SALES }

    public record Ticket(String subject, String body) {
    }

    public record Route(Team team, String queue, boolean escalate) {
    }

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

    private final TypeSafeClient typeSafe;

    public TicketController(TypeSafeClient typeSafe) {
        this.typeSafe = typeSafe;
    }

    @PostMapping("/route")
    public Route route(@RequestBody Ticket ticket) {
        TypeSafeResponse response = typeSafe.systemOne(Map.of("ticket", ticket), TEAM, ESCALATE);
        Team team = response.answer(TEAM).choice();
        return new Route(team, queue(team), response.answer(ESCALATE).noul() > 0.5);
    }

    /** Add a Team and this stops compiling until the new constant has a queue. */
    private static String queue(Team team) {
        return switch (team) {
            case BILLING -> "finance";
            case TECH_SUPPORT -> "helpdesk";
            case SALES -> "sales";
        };
    }
}
