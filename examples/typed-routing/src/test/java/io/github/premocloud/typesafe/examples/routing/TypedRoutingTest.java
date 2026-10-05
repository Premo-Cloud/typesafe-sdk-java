package io.github.premocloud.typesafe.examples.routing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TypedRoutingTest {

    static final String REPLY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "team": {
                  "type": "choice",
                  "choice": "BILLING",
                  "probabilities": {"BILLING": 0.91, "TECH_SUPPORT": 0.06, "SALES": 0.03},
                  "confidence": 0.88
                },
                "escalate": {"type": "noul", "noul": 0.82}
              },
              "usage": {"input_tokens": 120, "output_tokens": 8}
            }
            """;

    @Test
    void asksBothQuestionsOncePerTicket() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/systemone", REPLY)) {
            TypedRouting.run(stub.client());

            assertEquals(TypedRouting.TICKETS.size(), stub.requests().size());

            for (Request request : stub.requests()) {
                assertEquals("/v1/systemone", request.path());

                JsonNode sent = new ObjectMapper().readTree(request.body());
                assertEquals("choice", sent.at("/questions/team/type").asText());
                assertEquals("noul", sent.at("/questions/escalate/type").asText());
                assertEquals(TypedRouting.Team.values().length, sent.at("/questions/team/criteria").size());
            }
        }
    }
}
