/** One server-sent event, as parsed off the wire. */
export interface SseEvent {
  name: string;
  data: string;
}

/**
 * A stateful chunk parser: feed it whatever the network delivered, get back the events that are
 * complete so far. An SSE frame ends with a blank line, and a chunk boundary can land anywhere —
 * mid-character is the TextDecoder's problem, mid-frame is this one's.
 */
export function createSseParser(): (chunk: string) => SseEvent[] {
  let buffered = "";
  return (chunk) => {
    buffered += chunk;
    const frames = buffered.split(/\r?\n\r?\n/);
    buffered = frames.pop() ?? "";
    return frames.map(eventOf).filter((event): event is SseEvent => event !== null);
  };
}

function eventOf(frame: string): SseEvent | null {
  let name = "message";
  const data: string[] = [];
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith(":")) continue; // a heartbeat comment carries nothing
    if (line.startsWith("event:")) {
      name = line.slice("event:".length).trim();
    } else if (line.startsWith("data:")) {
      data.push(line.slice("data:".length).trim());
    }
  }
  return data.length === 0 ? null : { name, data: data.join("\n") };
}
