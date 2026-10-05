package io.github.premocloud.typesafe.examples.spring;

import io.github.premocloud.typesafe.TypeSafeApiException;
import io.github.premocloud.typesafe.TypeSafeException;
import io.github.premocloud.typesafe.TypeSafeTimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Turns a failed call to Jev into a gateway error, so no route is ever guessed. */
@RestControllerAdvice
class TypeSafeErrorHandler {

    @ExceptionHandler
    ProblemDetail timedOut(TypeSafeTimeoutException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    /** Only an API reply carries a request id; connection failures never reached the API. */
    @ExceptionHandler
    ProblemDetail failed(TypeSafeException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());

        if (e instanceof TypeSafeApiException api) {
            api.requestId().ifPresent(id -> problem.setProperty("typesafeRequestId", id));
        }

        return problem;
    }
}
