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
  const controls = Array.from(doc.querySelectorAll("button, [role='button'], [data-tid]"));
  const labelOf = (element: Element) =>
    `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""} ${element.getAttribute("data-tid") ?? ""}`
      .toLowerCase();
  const hasControl = (...needles: string[]) =>
    controls.some((element) => needles.some((needle) => labelOf(element).includes(needle)));

  // In the call first, and only from the controls, as on Meet: a sentence anyone says or types is on the page too.
  // The lobby has a Leave of its own, so the call is Leave beside the call's own Chat or People.
  const hangsUp = hasControl("hangup", "leave");
  if (hangsUp && hasControl("chat", "people", "roster")) {
    let participants: number | null = null;
    for (const element of controls) {
      const label = labelOf(element);
      if (!/people|participant|roster/.test(label)) {
        continue;
      }
      // The People button's badge is its own text, a bare number; a label may spell it out instead.
      const badge = (element.textContent ?? "").trim();
      const match = /^\d+$/.test(badge) ? [badge, badge]
        : /(\d+)\s*(?:people|participants)|(?:people|participants)\D{0,3}(\d+)/.exec(label);
      if (match) {
        participants = Number(match[1] ?? match[2]);
        break;
      }
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
  if (hasControl("prejoin-join-button", "join now") || says("join now", "type your name", "enter your name")) {
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
  const isOn = (element: Element, device: RegExp, pressToTurnOff: RegExp) => {
    const label = `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""}`.toLowerCase();
    if (pressToTurnOff.test(label)) {
      return true;
    }
    const switched = element.getAttribute("role") === "switch" || (element as HTMLInputElement).type === "checkbox";
    return switched && device.test(label)
      && (element.getAttribute("aria-checked") === "true" || (element as HTMLInputElement).checked === true);
  };
  return {
    microphoneOn: controls.some((element) => isOn(element, /\bmic|microphone|audio/,
      /\bmute mic|\bmute microphone|turn off microphone|turn microphone off|turn mic off/)),
    cameraOn: controls.some((element) => isOn(element, /camera|video/,
      /turn off camera|turn camera off|stop video|turn off video/)),
  };
}

/** Switches off every device the pre-join screen still has on; answers how many it pressed. */
export function turnOffTeamsDevices(doc: Document): number {
  let pressed = 0;
  const controls = Array.from(doc.querySelectorAll("[role='switch'], input[type='checkbox'], button, [role='button']"));
  for (const element of controls as HTMLElement[]) {
    const label = `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""}`.toLowerCase();
    const switched = element.getAttribute("role") === "switch" || (element as HTMLInputElement).type === "checkbox";
    const on = element.getAttribute("aria-checked") === "true" || (element as HTMLInputElement).checked === true;
    const deviceSwitchOn = switched && on && /\bmic|microphone|camera|video/.test(label);
    const pressToTurnOff = /\bmute mic|\bmute microphone|turn off microphone|turn microphone off|turn mic off|turn off camera|turn camera off|stop video|turn off video/
      .test(label);
    if (deviceSwitchOn || pressToTurnOff) {
      element.click();
      pressed++;
    }
  }
  return pressed;
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
 * Whether the chat now shows the line as sent, outside the box it was typed in: the consent notice is only taken as
 * given once anyone in the call could read it.
 */
export function teamsChatShows(doc: Document, message: string): boolean {
  const needle = message.replace(/\s+/g, " ").trim().slice(0, 60).toLowerCase();
  const box = doc.querySelector("[contenteditable='true']");
  return Array.from(doc.querySelectorAll("[data-tid*='chat-pane-message'], [data-tid*='message-body'], [role='listitem'], [role='document'] p"))
    .some((element) => !box?.contains(element)
      && (element.textContent ?? "").replace(/\s+/g, " ").toLowerCase().includes(needle));
}
