package io.github.premocloud.typesafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeSafeRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void lambdaBuildersSerializeToDocumentedWireFormat() throws Exception {
        TypeSafeRequest request = TypeSafeRequest.of(r -> r
                .state("Help! My payouts have been failing for 3 days.")
                .model("jev-latest")
                .noul("is_urgent", n -> n
                        .instructions("Does this convey urgency?")
                        .whenTrue("Explicitly time-sensitive")
                        .whenFalse("No urgency expressed"))
                .choice("department", c -> c
                        .instructions("Which team should handle this?")
                        .option("billing", "Payments, invoicing, refunds")
                        .option("technical", o -> o.what("Bugs, outages, integrations").notFor("Billing disputes")))
                .score("severity", s -> s
                        .instructions("How severe is the problem?")
                        .level("Cosmetic; no impact to functionality")
                        .level(l -> l.what("Core workflow blocked").examples("Cannot log in", "Payouts failing"))));

        JsonNode expected = objectMapper.readTree("""
                {
                  "state": "Help! My payouts have been failing for 3 days.",
                  "model": "jev-latest",
                  "questions": {
                    "is_urgent": {
                      "type": "noul",
                      "instructions": "Does this convey urgency?",
                      "criteria": {"true": "Explicitly time-sensitive", "false": "No urgency expressed"}
                    },
                    "department": {
                      "type": "choice",
                      "instructions": "Which team should handle this?",
                      "criteria": {
                        "billing": "Payments, invoicing, refunds",
                        "technical": {"what": "Bugs, outages, integrations", "not_for": "Billing disputes"}
                      }
                    },
                    "severity": {
                      "type": "score",
                      "instructions": "How severe is the problem?",
                      "criteria": [
                        "Cosmetic; no impact to functionality",
                        {"what": "Core workflow blocked", "examples": ["Cannot log in", "Payouts failing"]}
                      ]
                    }
                  }
                }
                """);

        assertEquals(expected, objectMapper.valueToTree(request));
    }

    @Test
    void flatFactoriesMirrorTheOtherSdks() throws Exception {
        TypeSafeRequest request = TypeSafeRequest.of(
                Map.of("document", "I was charged twice. Please fix this ASAP."),
                Map.of("category", Choice.of("What is this ticket about?", "billing", "technical", "other"),
                        "urgent", Noul.of("Does `document` convey urgency?"),
                        "severity", Score.of("How severe?", "cosmetic", "degraded", "blocked")));

        JsonNode expected = objectMapper.readTree("""
                {
                  "state": {"document": "I was charged twice. Please fix this ASAP."},
                  "questions": {
                    "category": {"type": "choice", "instructions": "What is this ticket about?",
                                 "criteria": {"billing": null, "technical": null, "other": null}},
                    "urgent": {"type": "noul", "instructions": "Does `document` convey urgency?"},
                    "severity": {"type": "score", "instructions": "How severe?", "criteria": ["cosmetic", "degraded", "blocked"]}
                  }
                }
                """);

        JsonNode actual = objectMapper.valueToTree(request);
        assertFalse(actual.has("model"));
        assertEquals(expected.get("state"), actual.get("state"));
        assertEquals(expected.at("/questions/category"), actual.at("/questions/category"));
        assertEquals(expected.at("/questions/urgent"), actual.at("/questions/urgent"));
        assertEquals(expected.at("/questions/severity"), actual.at("/questions/severity"));
    }

    @Test
    void instructionsAreOptionalAndDescriptionsMayBeNull() throws Exception {
        Choice choice = Choice.of(c -> c.option("yes", "affirmative").option("no"));
        Noul noul = Noul.of(n -> n.whenTrue("present").whenFalse("absent"));

        JsonNode choiceJson = objectMapper.valueToTree(choice);
        assertFalse(choiceJson.has("instructions"));
        assertTrue(choiceJson.at("/criteria/no").isNull());
        assertEquals("affirmative", choiceJson.at("/criteria/yes").asText());
        assertNull(noul.instructions());
        assertEquals("present", objectMapper.valueToTree(noul).at("/criteria/true").asText());
    }

    @Test
    void structuredStateAndInstructionsPassThrough() {
        Map<String, String> instructions = Map.of("question", "Which category?", "focus", "Judge bulk vs genuine");

        TypeSafeRequest request = TypeSafeRequest.of(r -> r
                .state(Map.of("email", Map.of("subject", "URGENT")))
                .state("context", Map.of("matter_type", "dispute"))
                .noul("is_spam", n -> n.instructions(instructions)));

        JsonNode json = objectMapper.valueToTree(request);
        assertEquals("URGENT", json.at("/state/email/subject").asText());
        assertEquals("dispute", json.at("/state/context/matter_type").asText());
        assertEquals("Judge bulk vs genuine", json.at("/questions/is_spam/instructions/focus").asText());
        assertFalse(json.at("/questions/is_spam").has("criteria"));
        assertThrows(IllegalStateException.class, () -> TypeSafeRequest.builder().state("text").state("k", "v"));
    }

    enum Dept { BILLING, SHIPPING, SECURITY }

    @Test
    void enumChoicesUseTheConstantNamesAsLabelsAndKeepTheWireShape() throws Exception {
        Choice<Dept> flat = Choice.of("Which team?", Dept.class);
        Choice<Dept> described = Choice.builder(Dept.class).instructions("Which team?")
                .option(Dept.BILLING, "Invoices and refunds").option(Dept.SECURITY).build();
        TypeSafeRequest request = TypeSafeRequest.of(r -> r.state("text")
                .choice("dept", Dept.class, c -> c.instructions("Which team?").option(Dept.SHIPPING, o -> o.what("Delivery"))));

        assertEquals(List.of("BILLING", "SHIPPING", "SECURITY"), List.copyOf(flat.criteria().keySet()));
        JsonNode flatJson = objectMapper.valueToTree(flat);
        assertEquals(Set.of("type", "instructions", "criteria"), fieldNames(flatJson), "no enum metadata leaks onto the wire");
        assertEquals(objectMapper.valueToTree(Choice.of("Which team?", "BILLING", "SHIPPING", "SECURITY")), flatJson);

        JsonNode describedJson = objectMapper.valueToTree(described);
        assertEquals("Invoices and refunds", describedJson.at("/criteria/BILLING").asText());
        assertTrue(describedJson.at("/criteria/SECURITY").isNull());
        assertFalse(describedJson.at("/criteria").has("SHIPPING"));

        assertEquals("Delivery", objectMapper.valueToTree(request).at("/questions/dept/criteria/SHIPPING/what").asText());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    @Test
    void prebuiltQuestionsCanBeReused() {
        Noul shared = Noul.of("Is it urgent?");

        TypeSafeRequest request = TypeSafeRequest.of(r -> r.state("text").question("urgent", shared));

        assertSame(shared, request.questions().get("urgent"));
    }

    @Test
    void validationMatchesTheOtherSdks() {
        assertThrows(IllegalStateException.class, () -> TypeSafeRequest.of(r -> r.noul("q", n -> n.instructions("Urgent?"))));
        assertThrows(IllegalStateException.class, () -> TypeSafeRequest.of(r -> r.state("text")));
        assertThrows(IllegalStateException.class, () -> Choice.of("Pick one"));
        assertThrows(IllegalStateException.class, () -> Score.of("Rate it", "only level"));
        assertThrows(IllegalStateException.class, () -> Criterion.of(c -> c.notFor("nothing to describe")));
        assertEquals(1, Choice.of("Yes or no?", "yes").criteria().size());
        assertEquals(11, Score.of(s -> {
            s.instructions("Rate it");

            for (int i = 0; i <= 10; i++) {
                s.level("level " + i);
            }
        }).criteria().size());
    }

    @Test
    void criteriaQuestionSetPutsCriteriaInStateAndOneNoulPerEntry() {
        record Rule(String code, String text) {
        }

        List<Rule> rules = List.of(new Rule("r1", "first"), new Rule("r2", "second"));

        TypeSafeRequest request = CriteriaQuestionSet
                .over("document", Map.of("body", "..."), rules, Rule::code,
                        (rule, path) -> Noul.of("Does `document` fall under %s?".formatted(path)))
                .noul("any", n -> n.instructions("Does any rule in `criteria` apply?"))
                .build();

        JsonNode json = objectMapper.valueToTree(request);
        assertEquals("second", json.at("/state/criteria/1/text").asText());
        assertEquals("...", json.at("/state/document/body").asText());
        assertEquals(List.of("r1", "r2", "any"), List.copyOf(request.questions().keySet()));
        assertEquals("Does `document` fall under `criteria[1]`?", json.at("/questions/r2/instructions").asText());
    }
}
