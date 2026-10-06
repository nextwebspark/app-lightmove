# MCP tool-choice eval

Written by `ops/eval/mcp/run.mjs` when it runs with `REPORT=1`. Each run appends one section. It records
which tools Claude Code called for each question in `ops/eval/mcp/cases.json`, and how many calls it made,
against a local stack holding `npm run dev:db:seed-report`'s fictional mandate. It records no answer text.

A case passes when the first call is the one expected, every tool it must use was called, and the calls stay
within its ceiling. A model's choices vary from run to run, so this tunes tool descriptions and never gates a
build. Run it after changing what a tool says about itself:

```bash
npm run dev                                   # with MCP_ENABLED left on (ops/dev/api.sh's default)
npm run dev:db:seed-report                    # into the position the eval names
# Settings → API keys: a personal key with projects, companies, candidates and mcp:use
UNCAVA_API_KEY=uncava_pat_… UNCAVA_POSITION="Chief Financial Officer" RUNS=3 REPORT=1 node ops/eval/mcp/run.mjs
```
