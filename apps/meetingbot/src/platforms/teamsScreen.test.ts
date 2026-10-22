import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { consentLine } from "../runner.js";
import { fillTeamsName, focusTeamsChat, pressTeamsButton, readTeamsCaptions, readTeamsDevices, readTeamsScreen,
  teamsChatShows, turnOffTeamsDevice } from "./teamsScreen.js";

const page = (name: string) =>
  new JSDOM(readFileSync(new URL(`./fixtures/${name}.html`, import.meta.url), "utf8")).window.document;

describe("readTeamsScreen", () => {
  it.each([
    ["teams-launcher", "launcher"],
    ["teams-prejoin", "prejoin"],
    ["teams-lobby", "lobby"],
    ["teams-blocked", "blocked"],
    ["teams-denied", "denied"],
    ["teams-in-call", "in-call"],
    ["teams-ended", "ended"],
  ])("reads %s as %s", (fixture, stage) => {
    expect(readTeamsScreen(page(fixture)).stage).toBe(stage);
  });

  it("never reads the lobby's own Leave, nor a pane's test id, as being in the call", () => {
    expect(readTeamsScreen(page("teams-lobby")).stage).toBe("lobby");
  });

  it("counts everyone in the call from the People button", () => {
    expect(readTeamsScreen(page("teams-in-call")).participants).toBe(3);
  });

  it("answers no count rather than a guess when the People button carries none", () => {
    const doc = page("teams-in-call");
    doc.querySelector("[data-tid='roster-button']")!.textContent = "";
    expect(readTeamsScreen(doc)).toEqual({ stage: "in-call", participants: null });
  });

  it("never reads what someone said or typed in the call as the call's own screen", () => {
    expect(readTeamsScreen(page("teams-in-call-ended-said"))).toEqual({ stage: "in-call", participants: 3 });
  });

  it("answers unknown for a page it cannot read", () => {
    expect(readTeamsScreen(new JSDOM("<p>Loading</p>").window.document).stage).toBe("unknown");
  });
});

describe("the pre-join screen", () => {
  it("reads whether the microphone and camera are still on", () => {
    expect(readTeamsDevices(page("teams-prejoin"))).toEqual({ microphoneOn: true, cameraOn: true });
    expect(readTeamsDevices(page("teams-prejoin-devices-off"))).toEqual({ microphoneOn: false, cameraOn: false });
  });

  it("switches off one device a call, and nothing already off", () => {
    const doc = page("teams-prejoin");
    const pressed: string[] = [];
    doc.querySelectorAll("[role='switch']").forEach((element) => element.addEventListener("click", () => {
      pressed.push(element.getAttribute("aria-label") ?? "");
      element.setAttribute("aria-checked", "false");
    }));
    expect(turnOffTeamsDevice(doc)).toBe("microphone");
    expect(turnOffTeamsDevice(doc)).toBe("camera");
    expect(turnOffTeamsDevice(doc)).toBe("none");
    expect(pressed).toEqual(["Microphone", "Camera"]);
    expect(turnOffTeamsDevice(page("teams-prejoin-devices-off"))).toBe("none");
  });

  it("types the guest name so the page notices", () => {
    const doc = page("teams-prejoin");
    let heard = "";
    doc.querySelector("input")?.addEventListener("input", (event) => {
      heard = (event.target as HTMLInputElement).value;
    });
    expect(fillTeamsName(doc, "Uncava Notetaker")).toBe(true);
    expect(heard).toBe("Uncava Notetaker");
  });

  it("does not press Join now while it is disabled, and does once it is not", () => {
    expect(pressTeamsButton(page("teams-prejoin"), ["prejoin-join-button", "join now"])).toBe(false);
    expect(pressTeamsButton(page("teams-prejoin-devices-off"), ["prejoin-join-button", "join now"])).toBe(true);
  });

  it("stays in this browser from the launcher", () => {
    const doc = page("teams-launcher");
    let chosen = "";
    doc.querySelectorAll("button").forEach((button) =>
      button.addEventListener("click", () => {
        chosen = button.textContent ?? "";
      }));
    expect(pressTeamsButton(doc, ["continue on this browser", "join on the web instead"])).toBe(true);
    expect(chosen).toBe("Continue on this browser");
  });
});

describe("in the call", () => {
  it("reads each caption line with its speaker", () => {
    expect(readTeamsCaptions(page("teams-in-call"))).toEqual([
      { speaker: "Priya Raman", text: "I led the finance transformation in Riyadh." },
      { speaker: "Yara Haddad", text: "What drew you to the group role?" },
    ]);
  });

  it("reads no captions where none are on", () => {
    expect(readTeamsCaptions(page("teams-lobby"))).toEqual([]);
  });

  it("finds the chat box, and takes the consent line as said only once the chat shows it", () => {
    const doc = page("teams-in-call");
    expect(focusTeamsChat(doc)).toBe(true);
    expect(teamsChatShows(doc, consentLine("Uncava Notetaker"))).toBe(true);
    expect(teamsChatShows(doc, "Something nobody sent")).toBe(false);
  });

  it("does not take the line as said while it is still in the box", () => {
    const doc = page("teams-in-call");
    doc.querySelector("[data-tid='message-pane-list-runway']")!.remove();
    doc.querySelector("[data-tid='ckeditor']")!.textContent = consentLine("Uncava Notetaker");
    expect(teamsChatShows(doc, consentLine("Uncava Notetaker"))).toBe(false);
  });

  it("nor while it sits unsent in a second editable box, wrapped as a message", () => {
    const doc = page("teams-in-call");
    doc.querySelector("[data-tid='message-pane-list-runway']")!.remove();
    doc.body.insertAdjacentHTML("afterbegin", "<div contenteditable='true' aria-label='Reply'></div>");
    doc.querySelector("[data-tid='ckeditor']")!.innerHTML =
      `<div role="document"><div data-tid="message-body"><p>${consentLine("Uncava Notetaker")}</p></div></div>`;
    expect(teamsChatShows(doc, consentLine("Uncava Notetaker"))).toBe(false);
  });
});
