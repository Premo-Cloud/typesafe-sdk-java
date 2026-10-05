# Spring Boot

The starter in a real application: `POST /tickets/route` asks Jev which team owns a ticket and whether to escalate it.

```sh
TYPESAFE_API_KEY=your-key ./gradlew :examples:spring-boot:run
```

```sh
curl -s localhost:8080/tickets/route -H 'Content-Type: application/json' \
  -d '{"subject": "Charged twice", "body": "My card was billed twice for March. Refund it today or I cancel."}'
```

```json
{"team":"BILLING","queue":"finance","escalate":true}
```
