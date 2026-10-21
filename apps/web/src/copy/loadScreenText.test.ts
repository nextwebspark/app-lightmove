import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { defineScreen } from "./defineScreen";
import { loadScreenText } from "./loadScreenText";
import { applyBundle, resetScreenText, screenText } from "./store";

const greetings = defineScreen("greetings", "Greetings", {
  hello: { text: "Hello.", where: "The greeting", kind: "title" },
});

const bundle = (version: string, hello: string) => ({
  locale: "en",
  release: 1,
  version,
  entries: { "greetings.hello": hello },
});

function answer(body: unknown, ok = true) {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok, json: () => Promise.resolve(body) }));
}

beforeEach(() => window.localStorage.clear());
afterEach(() => {
  vi.unstubAllGlobals();
  resetScreenText();
  window.localStorage.clear();
});

describe("loading screen text", () => {
  it("draws the remembered bundle at once, then the published one", async () => {
    window.localStorage.setItem("lm-screen-text", JSON.stringify(bundle("r1-a", "Hi from last time.")));
    answer(bundle("r2-a", "Hi from now."));

    const loaded = loadScreenText();
    expect(screenText(greetings, "hello")).toBe("Hi from last time.");
    await loaded;
    expect(screenText(greetings, "hello")).toBe("Hi from now.");
  });

  it("keeps the defaults when the read fails", async () => {
    answer(null, false);
    await loadScreenText();
    expect(screenText(greetings, "hello")).toBe("Hello.");
  });

  it("never replaces a preview that arrived while the read was in flight", async () => {
    answer(bundle("r2-a", "Hi from now."));
    const loaded = loadScreenText();
    applyBundle(bundle("preview-r2-a-1", "Hi from the draft."), { preview: true });
    await loaded;
    expect(screenText(greetings, "hello")).toBe("Hi from the draft.");
  });
});
