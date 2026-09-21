package io.github.premocloud.typesafe;

import org.jspecify.annotations.Nullable;

import java.net.http.HttpHeaders;

/** HTTP 413: the request body exceeded the API's size limit. */
public class TypeSafePayloadTooLargeException extends TypeSafeApiException {

    TypeSafePayloadTooLargeException(int status, @Nullable String body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
