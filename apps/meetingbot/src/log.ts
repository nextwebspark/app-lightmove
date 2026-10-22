/**
 * One JSON line per event on stdout, which Cloud Logging reads as a structured entry: its severity, the run it is
 * about and the message. A line names states and errors only — never a word of the call, a caption or the run token.
 */
export type Severity = "INFO" | "WARNING" | "ERROR";

export function logLine(runId: string, message: string, severity: Severity = "INFO"): string {
  return JSON.stringify({ severity, component: "meetingbot", runId, message });
}

export function runLogger(runId: string, write: (line: string) => void = console.log) {
  return (message: string, severity: Severity = "INFO") => write(logLine(runId, message, severity));
}
