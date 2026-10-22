/**
 * Reading Google Meet's page. Every function here runs inside the browser through `page.evaluate`, so each is
 * self-contained — no imports and no module-level helpers, which would not exist on the other side — and takes
 * the document as an argument so the tests can hand it a saved page under jsdom.
 *
 * Meet changes its markup without notice. These read what a person reads — button labels and the sentences on
 * screen, in English (the browser is started with `--lang=en-US`) — rather than generated class names, and the
 * daily canary (#956) is what tells us when they stop matching.
 */

export type MeetStage = "prejoin" | "blocked" | "lobby" | "denied" | "in-call" | "ended" | "unknown";

export interface MeetScreen {
  stage: MeetStage;
  /** Everyone in the call, the bot included; null when the page does not show it. */
  participants: number | null;
}

export function readMeetScreen(doc: Document): MeetScreen {
  const text = (doc.body?.textContent ?? "").replace(/\s+/g, " ").toLowerCase();
  const labelled = (needle: string) =>
    Array.from(doc.querySelectorAll("[aria-label]")).some((element) =>
      (element.getAttribute("aria-label") ?? "").toLowerCase().includes(needle),
    );
  const says = (...phrases: string[]) => phrases.some((phrase) => text.includes(phrase));

  if (says("you've been removed from the meeting", "you left the meeting", "the call ended", "call has ended",
      "this meeting has ended")) {
    return { stage: "ended", participants: null };
  }
  if (says("denied your request to join", "no one responded to your request", "you were removed")) {
    return { stage: "denied", participants: null };
  }
  if (says("you can't join this video call", "you can’t join this video call", "this meeting code is invalid",
      "check your meeting code")) {
    return { stage: "blocked", participants: null };
  }
  if (labelled("leave call")) {
    let participants: number | null = null;
    for (const element of Array.from(doc.querySelectorAll("[aria-label]"))) {
      const label = element.getAttribute("aria-label") ?? "";
      const match = /(?:people|participants|show everyone)\D*(\d+)|(\d+)\s+(?:people|participants)/i.exec(label);
      if (match) {
        participants = Number(match[1] ?? match[2]);
        break;
      }
    }
    if (participants === null) {
      const tiles = new Set(Array.from(doc.querySelectorAll("[data-participant-id]"))
        .map((element) => element.getAttribute("data-participant-id")));
      participants = tiles.size > 0 ? tiles.size : null;
    }
    return { stage: "in-call", participants };
  }
  if (says("asking to be let in", "you'll join the call when someone lets you in",
      "please wait until a meeting host brings you into the call", "waiting for the host")) {
    return { stage: "lobby", participants: null };
  }
  if (says("ask to join", "join now", "what's your name")) {
    return { stage: "prejoin", participants: null };
  }
  return { stage: "unknown", participants: null };
}

/** Clicks the first enabled button whose label or text is one of these; answers whether one was found. */
export function pressMeetButton(doc: Document, labels: string[]): boolean {
  const wanted = labels.map((label) => label.toLowerCase());
  const buttons = Array.from(doc.querySelectorAll("button, [role='button']")) as HTMLElement[];
  for (const label of wanted) {
    const found = buttons.find((button) => {
      const own = `${button.getAttribute("aria-label") ?? ""} ${button.textContent ?? ""}`
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
export function fillMeetName(doc: Document, name: string): boolean {
  const input = (doc.querySelector("input[aria-label='Your name']")
    ?? doc.querySelector("input[placeholder='Your name']")
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

export interface CaptionEntry {
  speaker: string;
  text: string;
}

/**
 * The captions on screen now, oldest first. Meet draws them in a region labelled Captions, one block per turn: the
 * speaker's name, then what they are saying, which grows in place until the turn ends.
 */
export function readMeetCaptions(doc: Document): CaptionEntry[] {
  const region = Array.from(doc.querySelectorAll("[role='region']"))
    .find((element) => (element.getAttribute("aria-label") ?? "").toLowerCase().includes("caption"));
  if (!region) {
    return [];
  }
  const entries: CaptionEntry[] = [];
  for (const block of Array.from(region.children)) {
    const pieces = Array.from(block.querySelectorAll("*"))
      .filter((element) => element.children.length === 0)
      .map((element) => (element.textContent ?? "").replace(/\s+/g, " ").trim())
      .filter((piece) => piece.length > 0);
    if (pieces.length >= 2) {
      entries.push({ speaker: pieces[0], text: pieces.slice(1).join(" ") });
    }
  }
  return entries;
}

/** Writes in the call's chat: the bot says what it is before anything is recorded. */
export function writeMeetChat(doc: Document, message: string): boolean {
  const box = (doc.querySelector("textarea[aria-label='Send a message']")
    ?? doc.querySelector("textarea[placeholder='Send a message']")
    ?? doc.querySelector("[contenteditable='true'][aria-label='Send a message']")) as HTMLTextAreaElement | null;
  if (!box) {
    return false;
  }
  const view = doc.defaultView;
  const setter = view ? Object.getOwnPropertyDescriptor(view.HTMLTextAreaElement.prototype, "value")?.set : undefined;
  if (setter && box instanceof (view as Window & typeof globalThis).HTMLTextAreaElement) {
    setter.call(box, message);
  } else {
    box.textContent = message;
  }
  box.dispatchEvent(new (view?.Event ?? Event)("input", { bubbles: true }));
  return true;
}
