import { readFileSync } from "node:fs";
import { JSDOM } from "jsdom";
import { describe, expect, it } from "vitest";
import { pressSpeakerDevice } from "./speakerScreen.js";

const page = (name: string) =>
  new JSDOM(readFileSync(new URL(`../platforms/fixtures/${name}.html`, import.meta.url), "utf8")).window.document;

const pressed = (doc: Document) => {
  const clicks: string[] = [];
  doc.querySelectorAll("button, [role='switch']").forEach((element) =>
    element.addEventListener("click", () => clicks.push(element.getAttribute("aria-label") ?? "")));
  return { device: pressSpeakerDevice(doc), clicks };
};

describe("pressSpeakerDevice", () => {
  it.each([
    ["meet-prejoin", "camera", "Turn off camera"],
    ["meet-prejoin-devices-off", "microphone", "Turn on microphone"],
    ["teams-prejoin", "camera", "Camera"],
    ["teams-prejoin-devices-off", "microphone", "Microphone"],
    ["teams-in-call", "microphone", "Unmute mic"],
  ])("on %s turns the %s the right way", (fixture, device, control) => {
    expect(pressed(page(fixture))).toEqual({ device, clicks: [control] });
  });

  it("leaves a speaker with the microphone on and the camera off alone", () => {
    expect(pressed(page("meet-in-call"))).toEqual({ device: "none", clicks: [] });
  });
});
