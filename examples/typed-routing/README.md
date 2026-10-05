# Typed routing

Routes tickets with typed `Ask`s: Jev's answer is a `Team`, so an exhaustive `switch` picks the queue.

```sh
TYPESAFE_API_KEY=your-key ./gradlew :examples:typed-routing:run
```

```
ticket                                                            team          prob  queue     escalate
I was charged twice this month. Refund it today or I dispute it.  BILLING       100%  finance   yes
The export button has done nothing since yesterday's update.      TECH_SUPPORT  100%  helpdesk  no
We're a 40-person team evaluating Pro. Any volume discount?       SALES         100%  sales     no
How do I update the card on file?                                 BILLING       100%  finance   no
```
