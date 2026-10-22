import { describe, expect, it } from "vitest";
import { logLine, runLogger } from "./log.js";

describe("logLine", () => {
  it("is one JSON entry Cloud Logging reads, naming the run and the severity", () => {
    expect(JSON.parse(logLine("run-1", "failing: NOT_ADMITTED", "WARNING"))).toEqual({
      severity: "WARNING", component: "meetingbot", runId: "run-1", message: "failing: NOT_ADMITTED",
    });
  });

  it("writes INFO unless told otherwise", () => {
    const lines: string[] = [];
    runLogger("run-2", (line) => lines.push(line))("joined");
    expect(JSON.parse(lines[0]).severity).toBe("INFO");
  });
});
