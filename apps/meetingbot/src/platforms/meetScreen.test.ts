import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { fillMeetName, pressMeetButton, readMeetCaptions, readMeetScreen, writeMeetChat } from "./meetScreen.js";

const page = (name: string) =>
  new JSDOM(readFileSync(new URL(`./fixtures/${name}.html`, import.meta.url), "utf8")).window.document;

describe("readMeetScreen", () => {
  it.each([
    ["meet-prejoin", "prejoin"],
    ["meet-lobby", "lobby"],
    ["meet-blocked", "blocked"],
    ["meet-in-call", "in-call"],
    ["meet-ended", "ended"],
  ])("reads %s as %s", (fixture, stage) => {
    expect(readMeetScreen(page(fixture)).stage).toBe(stage);
  });

  it("counts everyone in the call from the people button", () => {
    expect(readMeetScreen(page("meet-in-call")).participants).toBe(3);
  });

  it("falls back to the tiles when no button carries a count", () => {
    const doc = page("meet-in-call");
    doc.querySelector("[aria-label='Show everyone 3']")?.remove();
    expect(readMeetScreen(doc).participants).toBe(3);
  });

  it("answers unknown for a page it cannot read, rather than guessing", () => {
    expect(readMeetScreen(new JSDOM("<p>Loading</p>").window.document).stage).toBe("unknown");
  });
});

describe("the pre-join screen", () => {
  it("types the guest name so the page notices", () => {
    const doc = page("meet-prejoin");
    let heard = "";
    doc.querySelector("input")?.addEventListener("input", (event) => {
      heard = (event.target as HTMLInputElement).value;
    });
    expect(fillMeetName(doc, "Uncava Notetaker")).toBe(true);
    expect(heard).toBe("Uncava Notetaker");
  });

  it("does not press a disabled button", () => {
    expect(pressMeetButton(page("meet-prejoin"), ["ask to join"])).toBe(false);
  });

  it("presses a button by its label", () => {
    const doc = page("meet-in-call");
    let pressed = false;
    doc.querySelector("[aria-label='Leave call']")?.addEventListener("click", () => {
      pressed = true;
    });
    expect(pressMeetButton(doc, ["leave call"])).toBe(true);
    expect(pressed).toBe(true);
  });
});

describe("in the call", () => {
  it("reads each caption block as a speaker and what they said", () => {
    expect(readMeetCaptions(page("meet-in-call"))).toEqual([
      { speaker: "Yara Haddad", text: "Thanks for making the time today." },
      { speaker: "Priya Raman", text: "Happy to. I've been at Target Group for six years" },
    ]);
  });

  it("writes the consent line in the chat box", () => {
    const doc = page("meet-in-call");
    expect(writeMeetChat(doc, "Uncava Notetaker is recording")).toBe(true);
    expect((doc.querySelector("textarea") as HTMLTextAreaElement).value).toBe("Uncava Notetaker is recording");
  });
});
