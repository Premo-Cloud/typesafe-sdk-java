# Examples

| Example | Shows |
|---|---|
| [ticket-triage](ticket-triage) | Keyed questions, models listing, and API errors; a port of the JS SDK's demo |
| [typed-routing](typed-routing) | `Ask` handles and an enum `Choice`, routed by an exhaustive `switch` |
| [async-batch](async-batch) | `systemOneAsync` fan-out with per-call options, retries, and failures per item |
| [spring-boot](spring-boot) | The starter behind a REST endpoint, with API errors mapped to `ProblemDetail` |

```sh
TYPESAFE_API_KEY=your-key ./gradlew :examples:ticket-triage:run
```

Each example has a smoke test against an in-process stub server, so `./gradlew build` checks them all with no API key.
