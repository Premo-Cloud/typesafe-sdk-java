package io.github.premocloud.typesafe.examples.spring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.premocloud.typesafe.examples.spring.TicketController.Route;
import io.github.premocloud.typesafe.examples.spring.TicketController.Team;
import io.github.premocloud.typesafe.examples.spring.TicketController.Ticket;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer.Request;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT)
class TicketControllerTest {

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

    static StubTypeSafeServer stub;

    @DynamicPropertySource
    static void useStub(DynamicPropertyRegistry registry) throws IOException {
        stub = new StubTypeSafeServer();
        registry.add("typesafe.base-url", stub::baseUrl);
        registry.add("typesafe.api-key", () -> "test-key");
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @Autowired
    TestRestTemplate http;

    final ObjectMapper json = new ObjectMapper();
    final Ticket ticket = new Ticket("Charged twice", "My card was billed twice for March. Refund it today or I cancel.");

    @BeforeEach
    void forgetEarlierRequests() {
        stub.requests().clear();
    }

    @Test
    void routesTicketFromAnswers() throws IOException {
        stub.reply("/v1/systemone", REPLY);

        Route route = http.postForObject("/tickets/route", ticket, Route.class);

        assertEquals(new Route(Team.BILLING, "finance", true), route);
        assertEquals(1, stub.requests().size());
        Request sent = stub.requests().get(0);
        assertEquals("/v1/systemone", sent.path());
        JsonNode body = json.readTree(sent.body());
        assertEquals("Charged twice", body.at("/state/ticket/subject").asText());
        assertEquals("choice", body.at("/questions/team/type").asText());
        assertEquals("noul", body.at("/questions/escalate/type").asText());
    }

    @Test
    void apiErrorBecomes502() {
        stub.reply("/v1/systemone", 402, """
                {"error": "Insufficient credits"}
                """);

        ResponseEntity<ProblemDetail> response = http.postForEntity("/tickets/route", ticket, ProblemDetail.class);

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("402 Insufficient credits", response.getBody().getDetail());
    }
}
