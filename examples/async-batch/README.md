# Async batch

Jev moderates eight reviews at once, with a per-call timeout, patient retries, and failures that don't sink the batch.

```sh
TYPESAFE_API_KEY=your-key ./gradlew :examples:async-batch:run
```

```
review                                                         spam  tone
Five stars. Survived a drop onto concrete without a scratch.     3%  3.83 (glowing)
Battery died after a week. Absolute garbage, never again.        2%  0.11 (hostile)
BEST PRICES ON WATCHES >>> cheap-watch-deals.example            98%  3.09 (pleased)
It's fine. Does what the box says.                               6%  2.02 (neutral)
Love it so much I bought one for my sister too!                  6%  3.98 (glowing)
Shipped late and the strap was the wrong color.                  2%  1.00 (disappointed)
Earn $5,000 a week from home, DM me for details                 98%  2.79 (neutral)
Decent, though the manual could be clearer.                      2%  2.24 (neutral)
```
