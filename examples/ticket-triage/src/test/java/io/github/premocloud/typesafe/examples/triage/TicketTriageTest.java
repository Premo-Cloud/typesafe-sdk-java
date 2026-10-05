package io.github.premocloud.typesafe.examples.triage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TicketTriageTest {

    static final String MODELS = """
            {"models": [{"name": "jev-1.13.0", "description": "Jev", "release_date": "2026-09-01"}]}
            """;

    static final String REPLY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "is_billing": {"type": "noul", "noul": 0.97},
                "tone": {"type": "choice", "choice": "frustrated", "confidence": 0.80,
                         "probabilities": {"calm": 0.05, "frustrated": 0.80, "angry": 0.15}},
                "urgency": {"type": "score", "score": 2.3, "confidence": 0.60,
                            "probabilities": {"0": 0.0, "1": 0.1, "2": 0.5, "3": 0.4},
                            "legend": {"0": "can wait", "1": "this week", "2": "today", "3": "right now"}},
                "refund_risk": {"type": "score", "score": 1.4, "confidence": 0.55,
                                "probabilities": {"0": 0.1, "1": 0.4, "2": 0.5},
                                "legend": {"0": "unlikely", "1": "possible", "2": "likely"}}
              },
              "usage": {"input_tokens": 312, "output_tokens": 48}
            }
            """;

    @Test
    void listsModelsThenAsksAllFourQuestionsInOneCall() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/models", MODELS).reply("/v1/systemone", REPLY)) {
            TicketTriage.run(stub.client());

            List<Request> requests = stub.requests();
            assertEquals(List.of("/v1/models", "/v1/systemone"), requests.stream().map(Request::path).toList());

            JsonNode body = new ObjectMapper().readTree(requests.get(1).body());
            assertEquals("Charged twice this month", body.at("/state/ticket/subject").asText());
            assertEquals("noul", body.at("/questions/is_billing/type").asText());
            assertEquals("choice", body.at("/questions/tone/type").asText());
            assertEquals("score", body.at("/questions/urgency/type").asText());
            assertEquals("score", body.at("/questions/refund_risk/type").asText());
        }
    }

    @Test
    void reportsABadKeyOnTheModelsCallInsteadOfCrashing() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/models", 401, "{\"error\": \"invalid api key\"}")) {
            assertDoesNotThrow(() -> TicketTriage.run(stub.client()));

            assertEquals(List.of("/v1/models"), stub.requests().stream().map(Request::path).toList());
        }
    }

    @Test
    void reportsAnApiErrorOnTheTriageCallInsteadOfCrashing() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/models", MODELS).reply("/v1/systemone", 500, "{\"error\": \"upstream unavailable\"}")) {
            assertDoesNotThrow(() -> TicketTriage.run(stub.client()));
        }
    }
}
