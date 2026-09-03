import { expect, it } from "vitest";
import { createSseParser } from "./sse";

it("parses a complete frame into its name and data", () => {
  const parse = createSseParser();

  expect(parse('event: change\ndata: {"kind":"candidate-enriched"}\n\n')).toEqual([
    { name: "change", data: '{"kind":"candidate-enriched"}' },
  ]);
});

it("holds a frame split across chunks until it completes", () => {
  const parse = createSseParser();

  expect(parse("event: chan")).toEqual([]);
  expect(parse("ge\ndata: {}")).toEqual([]);
  expect(parse("\n\n")).toEqual([{ name: "change", data: "{}" }]);
});

it("takes CRLF line endings", () => {
  const parse = createSseParser();

  expect(parse("event: connected\r\ndata: {}\r\n\r\n")).toEqual([
    { name: "connected", data: "{}" },
  ]);
});

it("swallows heartbeat comments rather than surfacing empty events", () => {
  const parse = createSseParser();

  expect(parse(":ping\n\n")).toEqual([]);
});

it("defaults an unnamed event to message and joins its data lines", () => {
  const parse = createSseParser();

  expect(parse("data: first\ndata: second\n\n")).toEqual([
    { name: "message", data: "first\nsecond" },
  ]);
});

it("returns every event completed by one chunk", () => {
  const parse = createSseParser();

  expect(parse("event: a\ndata: 1\n\nevent: b\ndata: 2\n\n")).toEqual([
    { name: "a", data: "1" },
    { name: "b", data: "2" },
  ]);
});
