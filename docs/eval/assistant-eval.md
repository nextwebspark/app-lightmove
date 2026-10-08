# Assistant eval

Written by `AssistantEval` (`apps/api/src/test/java/app/lightmove/api/eval`), which asks the real
assistant the questions in `src/test/resources/eval/assistant-cases.json`: a researcher's questions,
from a vague "map 50 companies in the GCC region for this role" to "Seddiqi's competitors" and the
follow-up "what are these companies?". Every run appends one section.

Each conversation runs on a fresh position over the same seeded universe (22 Gulf retail, luxury and
distribution companies, Rivoli declined up front). Vendor lookups are the test doubles', so a company
the seed lacks comes back unverified: the eval measures how the assistant behaves, not how much of
the market it knows.

- **Playbooks**: what `ASSISTANT_ASKED` recorded — the playbooks loaded, or on a build from before
  them, the specialists asked.
- **Lists earlier**: on a follow-up about an earlier list, how many of its companies the answer named.
- **Says "card"**: whether the answer used the word the panel never shows.
- **Overlap**: the companies two questions that should agree suggested in common.

Run it with Application Default Credentials (it calls Vertex):

```bash
cd apps/api && ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=AssistantEval
```
