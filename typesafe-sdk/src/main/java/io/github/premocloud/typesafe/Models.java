package io.github.premocloud.typesafe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** The models available to the account, reached through {@link TypeSafeClient#models()}. */
public final class Models {

    static final String PATH = "/v1/models";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Wire(List<ModelCard> models) {
    }

    private final TypeSafeClient client;

    Models(TypeSafeClient client) {
        this.client = client;
    }

    public List<ModelCard> list() {
        return list(RequestOptions.NONE);
    }

    public List<ModelCard> list(RequestOptions options) {
        return TypeSafeClient.blocking(listAsync(options));
    }

    /**
     * The async counterpart of {@link #list()}. A setup error in {@link RequestOptions} throws synchronously,
     * exactly as in the blocking call. Everything else the blocking call reports completes the returned future
     * exceptionally with the same {@link TypeSafeException} subclass.
     */
    public CompletableFuture<List<ModelCard>> listAsync() {
        return listAsync(RequestOptions.NONE);
    }

    public CompletableFuture<List<ModelCard>> listAsync(RequestOptions options) {
        return client.getAsync(PATH, Wire.class, options).thenCompose(wire -> {
            if (Objects.isNull(wire.models())) {
                return CompletableFuture.<List<ModelCard>>failedFuture(new TypeSafeException("Models response did not contain a models list"));
            }

            return CompletableFuture.completedFuture(List.copyOf(wire.models()));
        });
    }
}
