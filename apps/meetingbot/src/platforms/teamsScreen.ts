import type { CaptionEntry } from "./meetScreen.js";

/**
 * Reading Microsoft Teams' web client. Every function here runs inside the browser through `page.evaluate`, so each is
 * self-contained, as `meetScreen.ts` explains. They read labels, Teams' own `data-tid` test ids and the sentences on
 * screen, in English, for both the work client (teams.microsoft.com) and the personal one (teams.live.com), which
 * share the new web client.
 */

export type TeamsStage = "launcher" | "prejoin" | "blocked" | "lobby" | "denied" | "in-call" | "ended" | "unknown";

export interface TeamsScreen {
  stage: TeamsStage;
  /** Everyone in the call, the bot included; null when the page does not show it. */
  participants: number | null;
}

export function readTeamsScreen(doc: Document): TeamsScreen {
  // Controls only, never a container: a pane's test id (`chat-pane`, `people-pane`) is on the page in the lobby too.
  const controls = Array.from(doc.querySelectorAll("button, [role='button']"));
  const idOf = (element: Element) => (element.getAttribute("data-tid") ?? element.id ?? "").toLowerCase();
  const labelOf = (element: Element) => (element.getAttribute("aria-label") ?? "").trim().toLowerCase();
  const isControl = (element: Element, ids: string[], labels: RegExp) =>
    ids.includes(idOf(element)) || labels.test(labelOf(element));
  const hangup = (element: Element) => isControl(element, ["hangup-main-btn", "hangup-button"], /^(leave|hang up)$/);
  const chat = (element: Element) => isControl(element, ["chat-button"], /^(chat|show conversation)$/);
  const people = (element: Element) => isControl(element, ["roster-button"], /^(people|participants|show participants)\b/);

  // In the call first, and only from its own controls, as on Meet: a sentence anyone says or types is on the page
  // too. The lobby has a Leave of its own, so the call is Leave beside the call's own Chat or People button.
  if (controls.some(hangup) && controls.some((element) => chat(element) || people(element))) {
    let participants: number | null = null;
    const roster = controls.find(people);
    if (roster) {
      // The People button's badge is its own text, a bare number; a label may spell it out instead.
      const badge = (roster.textContent ?? "").trim();
      const match = /^\d+$/.test(badge) ? [badge, badge]
        : /(\d+)\s*(?:people|participants)|(?:people|participants)\D{0,3}(\d+)/.exec(labelOf(roster));
      participants = match ? Number(match[1] ?? match[2]) : null;
    }
    return { stage: "in-call", participants };
  }

  const text = (doc.body?.textContent ?? "").replace(/\s+/g, " ").replace(/[‘’]/g, "'").toLowerCase();
  const says = (...phrases: string[]) => phrases.some((phrase) => text.includes(phrase));
  if (says("the meeting has ended", "this meeting has ended", "you've been removed from this meeting",
      "you have been removed from this meeting", "someone removed you from the meeting", "you left the meeting",
      "your call has ended")) {
    return { stage: "ended", participants: null };
  }
  if (says("you were denied access", "denied your request to join", "your request to join was declined",
      "no one responded to your request", "nobody let you in")) {
    return { stage: "denied", participants: null };
  }
  if (says("anonymous users can't join", "anonymous users cannot join", "doesn't allow anonymous",
      "sign in to join this meeting", "you need to sign in", "we couldn't find this meeting",
      "this meeting link is invalid", "you can't join this meeting")) {
    return { stage: "blocked", participants: null };
  }
  if (says("should let you in soon", "we'll let people know you're waiting", "know you're waiting",
      "waiting for someone to let you in", "waiting to be admitted")) {
    return { stage: "lobby", participants: null };
  }
  if (says("continue on this browser", "join on the web instead", "how do you want to join")) {
    return { stage: "launcher", participants: null };
  }
  if (controls.some((element) => idOf(element) === "prejoin-join-button")
      || says("join now", "type your name", "enter your name")) {
    return { stage: "prejoin", participants: null };
  }
  return { stage: "unknown", participants: null };
}

/**
 * Whether the pre-join screen still has a microphone or camera on. Teams draws each as a switch whose checked state is
 * the device's, or as a button labelled by what pressing it does ("Mute mic" means the microphone is on).
 */
export function readTeamsDevices(doc: Document): { microphoneOn: boolean; cameraOn: boolean } {
  const controls = Array.from(doc.querySelectorAll("[role='switch'], input[type='checkbox'], button, [role='button']"));
  // A switch says its state by being checked, whatever its title still reads; a button by what pressing it does.
  const isOn = (element: Element, device: RegExp, pressToTurnOff: RegExp) => {
    const label = `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""}`.toLowerCase();
    const switched = element.getAttribute("role") === "switch" || (element as HTMLInputElement).type === "checkbox";
    if (switched) {
      return device.test(label)
        && (element.getAttribute("aria-checked") === "true" || (element as HTMLInputElement).checked === true);
    }
    return pressToTurnOff.test(label);
  };
  return {
    microphoneOn: controls.some((element) => isOn(element, /\bmic|microphone|audio/,
      /\bmute mic|\bmute microphone|turn off microphone|turn microphone off|turn mic off/)),
    cameraOn: controls.some((element) => isOn(element, /camera|video/,
      /turn off camera|turn camera off|stop video|turn off video/)),
  };
}

/**
 * Switches off the first device the pre-join screen still has on, one per call, and answers which ("none" when both
 * are off): the caller waits for the switch to read off before the next, so a slow redraw is never pressed twice.
 */
export function turnOffTeamsDevice(doc: Document): "microphone" | "camera" | "none" {
  const controls = Array.from(doc.querySelectorAll("[role='switch'], input[type='checkbox'], button, [role='button']"));
  const devices: ["microphone" | "camera", RegExp, RegExp][] = [
    ["microphone", /\bmic|microphone/, /\bmute mic|\bmute microphone|turn off microphone|turn microphone off|turn mic off/],
    ["camera", /camera|video/, /turn off camera|turn camera off|stop video|turn off video/],
  ];
  for (const [device, named, pressToTurnOff] of devices) {
    for (const element of controls as HTMLElement[]) {
      const label = `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""}`.toLowerCase();
      const switched = element.getAttribute("role") === "switch" || (element as HTMLInputElement).type === "checkbox";
      const on = element.getAttribute("aria-checked") === "true" || (element as HTMLInputElement).checked === true;
      if (switched ? on && named.test(label) : pressToTurnOff.test(label)) {
        element.click();
        return device;
      }
    }
  }
  return "none";
}

/**
 * Clicks the first enabled control whose label, title, test id or text holds one of these, menu items included (the
 * captions switch lives in a menu); answers whether one was found.
 */
export function pressTeamsButton(doc: Document, labels: string[]): boolean {
  const wanted = labels.map((label) => label.toLowerCase());
  const buttons = Array.from(doc.querySelectorAll("button, [role='button'], [role='menuitem'], [role='menuitemcheckbox']")) as HTMLElement[];
  for (const label of wanted) {
    const found = buttons.find((button) => {
      const own = `${button.getAttribute("aria-label") ?? ""} ${button.getAttribute("title") ?? ""} ${button.getAttribute("data-tid") ?? ""} ${button.textContent ?? ""}`
        .replace(/\s+/g, " ").trim().toLowerCase();
      return own.includes(label) && !button.hasAttribute("disabled")
        && button.getAttribute("aria-disabled") !== "true";
    });
    if (found) {
      found.click();
      return true;
    }
  }
  return false;
}

/** Types the guest name into the pre-join box, as an input event a framework will notice. */
export function fillTeamsName(doc: Document, name: string): boolean {
  const input = (doc.querySelector("input[data-tid='prejoin-display-name-input']")
    ?? doc.querySelector("input[placeholder='Type your name']")
    ?? doc.querySelector("input[aria-label='Type your name']")
    ?? doc.querySelector("input[placeholder='Enter name']")
    ?? doc.querySelector("input[type='text']")) as HTMLInputElement | null;
  if (!input) {
    return false;
  }
  const view = doc.defaultView;
  const setter = view ? Object.getOwnPropertyDescriptor(view.HTMLInputElement.prototype, "value")?.set : undefined;
  if (setter) {
    setter.call(input, name);
  } else {
    input.value = name;
  }
  input.dispatchEvent(new (view?.Event ?? Event)("input", { bubbles: true }));
  return true;
}

/**
 * The live captions on screen now, oldest first. Teams marks each line's words `closed-caption-text` and its speaker
 * `author`, a few levels up; without either, a region labelled Captions is read block by block, as on Meet.
 */
export function readTeamsCaptions(doc: Document): CaptionEntry[] {
  const entries: CaptionEntry[] = [];
  const clean = (value: string | null | undefined) => (value ?? "").replace(/\s+/g, " ").trim();
  for (const words of Array.from(doc.querySelectorAll("[data-tid='closed-caption-text']"))) {
    let holder: Element | null = words.parentElement;
    let author: Element | null = null;
    for (let level = 0; holder && level < 5 && !author; level++) {
      author = holder.querySelector("[data-tid='author']");
      holder = holder.parentElement;
    }
    const text = clean(words.textContent);
    if (text) {
      entries.push({ speaker: clean(author?.textContent) || "Unknown", text });
    }
  }
  if (entries.length > 0) {
    return entries;
  }
  const region = Array.from(doc.querySelectorAll("[role='region'], [aria-label]"))
    .find((element) => (element.getAttribute("aria-label") ?? "").toLowerCase().includes("caption"));
  for (const block of Array.from(region?.children ?? [])) {
    const pieces = Array.from(block.querySelectorAll("*"))
      .filter((element) => element.children.length === 0)
      .map((element) => clean(element.textContent))
      .filter((piece) => piece.length > 0);
    if (pieces.length >= 2) {
      entries.push({ speaker: pieces[0], text: pieces.slice(1).join(" ") });
    }
  }
  return entries;
}

/**
 * Puts the caret in the call's chat box, so the line is typed with real keystrokes: Teams' editor ignores a value set
 * from outside. Answers whether there was a box.
 */
export function focusTeamsChat(doc: Document): boolean {
  const box = (doc.querySelector("[data-tid='ckeditor'][contenteditable='true']")
    ?? doc.querySelector("[contenteditable='true'][aria-label*='message' i]")
    ?? doc.querySelector("[role='textbox'][aria-label*='message' i]")) as HTMLElement | null;
  if (!box) {
    return false;
  }
  box.focus();
  return true;
}

/**
 * Whether the chat now shows the line as sent: in the conversation's own message list, and never inside any editable
 * box — a line typed and not yet sent sits in one, and the notice is only given once anyone in the call could read it.
 */
export function teamsChatShows(doc: Document, message: string): boolean {
  const needle = message.replace(/\s+/g, " ").trim().slice(0, 60).toLowerCase();
  return Array.from(doc.querySelectorAll("[data-tid='chat-pane-message'], [data-tid='message-body']"))
    .some((element) => !element.closest("[contenteditable='true'], [role='textbox']")
      && (element.textContent ?? "").replace(/\s+/g, " ").toLowerCase().includes(needle));
}
