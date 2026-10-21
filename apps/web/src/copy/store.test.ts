import { afterEach, describe, expect, it } from "vitest";
import { defineScreen } from "./defineScreen";
import { applyBundle, resetScreenText, screenText } from "./store";

const greetings = defineScreen("greetings", "Greetings", {
  hello: { text: "Hello {name}.", where: "The greeting", kind: "title" },
});

afterEach(() => resetScreenText());

describe("screen text", () => {
  it("reads the default the code ships until a bundle says otherwise", () => {
    expect(screenText(greetings, "hello", { name: "Sara" })).toBe("Hello Sara.");
  });

  it("reads a published override, filling the same placeholders", () => {
    applyBundle({ locale: "en", release: 3, version: "r3-x", entries: { "greetings.hello": "Welcome back, {name}." } });
    expect(screenText(greetings, "hello", { name: "Sara" })).toBe("Welcome back, Sara.");
  });

  it("ignores overrides for other keys", () => {
    applyBundle({ locale: "en", release: 1, version: "r1-x", entries: { "greetings.goodbye": "Bye." } });
    expect(screenText(greetings, "hello", { name: "Sara" })).toBe("Hello Sara.");
  });

  it("keeps the default when an override's placeholders no longer match it, as a bundle from before a deploy may", () => {
    applyBundle({ locale: "en", release: 2, version: "r2-x", entries: { "greetings.hello": "Welcome back." } });
    expect(screenText(greetings, "hello", { name: "Sara" })).toBe("Hello Sara.");
  });

  it("ignores an override that is not text", () => {
    applyBundle({ locale: "en", release: 2, version: "r2-x", entries: { "greetings.hello": 42 as unknown as string } });
    expect(screenText(greetings, "hello", { name: "Sara" })).toBe("Hello Sara.");
  });

  it("never fills a placeholder from an inherited property", () => {
    const tricky = defineScreen("tricky", "Tricky", { line: { text: "See {constructor}.", where: "x", kind: "help" } });
    expect(screenText(tricky, "line")).toBe("See {constructor}.");
  });
});
