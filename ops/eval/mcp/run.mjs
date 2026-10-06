#!/usr/bin/env node
// Asks Claude Code each case in cases.json against a running Uncava MCP server and checks which tools it chose
// and how many calls it made. Not a test: a model's choices vary run to run, so it reports and never gates.
//
//   UNCAVA_API_KEY=uncava_pat_…   a key holding mcp:use and the read scopes, on the seeded workspace
//   UNCAVA_POSITION="Chief …"     the seeded position's title (npm run dev:db:seed-report)
//   UNCAVA_MCP_URL                default http://localhost:8080/api/v1/mcp
//   MODEL                         optional, passed to claude --model
//   RUNS                          optional, runs per case (default 1)
//   REPORT=1                      append the result to docs/eval/mcp-eval.md
import { spawnSync } from "node:child_process";
import { appendFileSync, mkdtempSync, readFileSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const key = process.env.UNCAVA_API_KEY;
const position = process.env.UNCAVA_POSITION;
if (!key || !position) {
  console.error("Set UNCAVA_API_KEY and UNCAVA_POSITION; see the top of this file.");
  process.exit(2);
}
const url = process.env.UNCAVA_MCP_URL ?? "http://localhost:8080/api/v1/mcp";
const runs = Number(process.env.RUNS ?? 1);
const cases = JSON.parse(readFileSync(join(here, "cases.json"), "utf8"));

const config = join(mkdtempSync(join(tmpdir(), "uncava-mcp-eval-")), "mcp.json");
writeFileSync(config, JSON.stringify({
  mcpServers: { uncava: { type: "http", url, headers: { Authorization: `Bearer ${key}` } } },
}), { mode: 0o600 });

const PREFIX = "mcp__uncava__";

function ask(prompt) {
  const args = ["-p", prompt, "--output-format", "stream-json", "--verbose", "--mcp-config", config,
    "--strict-mcp-config", "--tools", "", "--allowedTools", `${PREFIX}*`, "--max-turns", "12"];
  if (process.env.MODEL) args.push("--model", process.env.MODEL);
  const run = spawnSync("claude", args, { encoding: "utf8", maxBuffer: 64 * 1024 * 1024, timeout: 300_000 });
  const calls = [];
  let answer = "";
  for (const line of (run.stdout ?? "").split("\n")) {
    if (!line.trim()) continue;
    let event;
    try { event = JSON.parse(line); } catch { continue; }
    if (event.type === "assistant") {
      for (const part of event.message?.content ?? []) {
        if (part.type === "tool_use") calls.push(part.name.startsWith(PREFIX) ? part.name.slice(PREFIX.length) : part.name);
      }
    }
    if (event.type === "result") answer = event.result ?? "";
  }
  return { calls, answer, failed: run.status !== 0 ? (run.stderr || `exit ${run.status}`).trim() : null };
}

function judge(each, { calls, failed }) {
  const misses = [];
  if (failed) misses.push(`claude failed: ${failed.split("\n")[0]}`);
  if (each.first && calls[0] !== each.first) misses.push(`first call ${calls[0] ?? "none"}, expected ${each.first}`);
  for (const tool of each.uses ?? []) if (!calls.includes(tool)) misses.push(`never called ${tool}`);
  if (each.usesAny && !each.usesAny.some((tool) => calls.includes(tool))) {
    misses.push(`called none of ${each.usesAny.join(", ")}`);
  }
  if (calls.length > each.maxCalls) misses.push(`${calls.length} calls, at most ${each.maxCalls}`);
  return misses;
}

const rows = [];
for (const each of cases) {
  for (let run = 1; run <= runs; run++) {
    const outcome = ask(each.prompt.replaceAll("{position}", position));
    const misses = judge(each, outcome);
    rows.push({ id: each.id, run, calls: outcome.calls, misses });
    console.log(`${misses.length ? "✗" : "✓"} ${each.id}#${run}  ${outcome.calls.join(" → ") || "(no calls)"}`);
    for (const miss of misses) console.log(`    ${miss}`);
  }
}

const passed = rows.filter((row) => row.misses.length === 0).length;
const totalCalls = rows.reduce((sum, row) => sum + row.calls.length, 0);
console.log(`\n${passed}/${rows.length} passed, ${totalCalls} tool calls in all`);

if (process.env.REPORT === "1") {
  const lines = [
    `\n## ${new Date().toISOString().slice(0, 10)}\n`,
    `${passed}/${rows.length} passed, ${totalCalls} tool calls in all.\n`,
    "| Case | Run | Calls | Result |",
    "|---|---|---|---|",
    ...rows.map((row) => `| ${row.id} | ${row.run} | ${row.calls.join(" → ") || "none"} | ${row.misses.join("; ") || "pass"} |`),
  ];
  appendFileSync(join(here, "../../../docs/eval/mcp-eval.md"), lines.join("\n") + "\n");
}
process.exit(passed === rows.length ? 0 : 1);
