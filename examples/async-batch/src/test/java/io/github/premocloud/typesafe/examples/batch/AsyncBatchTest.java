package io.github.premocloud.typesafe.examples.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer;
import io.github.premocloud.typesafe.examples.support.StubTypeSafeServer.Request;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncBatchTest {

    static final String REPLY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "is_spam": {"type": "noul", "noul": 0.04},
                "tone": {"type": "score", "score": 3.8, "confidence": 0.9, "probabilities": {"3": 0.2, "4": 0.8}}
              },
              "usage": {"input_tokens": 80, "output_tokens": 12}
            }
            """;

    @Test
    void sendsEachReviewOnce() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/systemone", REPLY)) {
            AsyncBatch.run(stub.client());

            assertEquals(AsyncBatch.REVIEWS.size(), stub.requests().size());
            assertTrue(stub.requests().stream().allMatch(request -> request.path().equals("/v1/systemone")));
            Set<String> sent = new HashSet<>();
            for (Request request : stub.requests()) {
                sent.add(new ObjectMapper().readTree(request.body()).at("/state/review").asText());
            }
            assertEquals(Set.copyOf(AsyncBatch.REVIEWS), sent);
        }
    }

    @Test
    void failedReviewsDoNotSinkTheBatch() throws IOException {
        try (StubTypeSafeServer stub = new StubTypeSafeServer().reply("/v1/systemone", 500, "{\"error\": \"unavailable\"}")) {
            AsyncBatch.run(stub.client());

            assertEquals(AsyncBatch.REVIEWS.size(), stub.requests().size());
        }
    }

}
