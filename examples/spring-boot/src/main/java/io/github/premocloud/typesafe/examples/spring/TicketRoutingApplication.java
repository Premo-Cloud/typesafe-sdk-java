package io.github.premocloud.typesafe.examples.spring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Serves ticket routing over HTTP with a TypeSafeClient the starter configures from properties. */
@SpringBootApplication
public class TicketRoutingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketRoutingApplication.class, args);
    }
}
