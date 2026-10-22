# Meeting summary eval

`MeetingSummaryEval` (`@Tag("eval")`, excluded from `./mvnw test`) runs `MeetingSummariser` on golden calls and
appends one table per run below: counts only, never a word of a call. The private golden set is never committed;
`src/test/resources/eval/meeting-summary-synthetic.jsonl` is the shape and the fallback.

- **Facts carried**: motivation, package, notice and mobility the call states, found in the note's own field.
- **Points mentioned**: phrases the note must carry somewhere.
- **Inventions**: phrases the call never said that the note claims — a figure, an offer, a promise. Should be 0.
- **Competencies placed right**: evidenced where the call showed one, and listed not covered where it did not.
