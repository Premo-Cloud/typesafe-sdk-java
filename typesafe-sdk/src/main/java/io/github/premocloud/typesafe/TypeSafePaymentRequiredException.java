package io.github.premocloud.typesafe;

import org.jspecify.annotations.Nullable;

import java.net.http.HttpHeaders;

/** HTTP 402: payment is required, e.g. the account is out of credits. */
public class TypeSafePaymentRequiredException extends TypeSafeApiException {

    TypeSafePaymentRequiredException(int status, @Nullable String body, HttpHeaders headers) {
        super(status, body, headers);
    }
}
