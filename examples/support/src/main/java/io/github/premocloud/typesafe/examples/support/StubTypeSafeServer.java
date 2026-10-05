package io.github.premocloud.typesafe.examples.support;

import com.sun.net.httpserver.HttpServer;
import io.github.premocloud.typesafe.RetryPolicy;
import io.github.premocloud.typesafe.TypeSafeClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/** In-process stand-in for api.typesafe.ai: answers each path with a fixed reply and records every request. */
public final class StubTypeSafeServer implements AutoCloseable {

    public record Request(String method, String path, String body) {
    }

    private record Reply(int status, String body) {
    }

    private final HttpServer server;
    private final Map<String, Reply> replies = new ConcurrentHashMap<>();
    private final List<Request> requests = new CopyOnWriteArrayList<>();

    public StubTypeSafeServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Request(exchange.getRequestMethod(), path, body));
            Reply reply = replies.getOrDefault(path, new Reply(404, "{\"error\":\"no reply for " + path + "\"}"));

            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);

            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    /** Replies 200 with {@code json} to every request for {@code path}, e.g. {@code /v1/systemone}. */
    public StubTypeSafeServer reply(String path, String json) {
        return reply(path, 200, json);
    }

    public StubTypeSafeServer reply(String path, int status, String json) {
        replies.put(path, new Reply(status, json));
        return this;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A client pointed at this server, with retries off so an error reply surfaces at once. */
    public TypeSafeClient client() {
        return TypeSafeClient.builder().apiKey("test-key").baseUrl(baseUrl()).retryPolicy(RetryPolicy.none()).build();
    }

    public List<Request> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
