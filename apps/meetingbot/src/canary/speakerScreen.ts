/**
 * The canary speaker's one action, on Meet or Teams, before joining or in the call: a microphone still off is turned
 * on first, then a camera still on is turned off, one control per call so the caller can wait for the redraw.
 * Self-contained, like the screen readers: it is sent to the page as its source text.
 */
export function pressSpeakerDevice(doc: Document): "microphone" | "camera" | "none" {
  const controls = Array.from(doc.querySelectorAll<HTMLElement>(
    "[role='switch'], input[type='checkbox'], button, [role='button']"))
    .filter((element) => !element.hasAttribute("disabled") && element.getAttribute("aria-disabled") !== "true");
  const devices: ["microphone" | "camera", boolean, RegExp, RegExp][] = [
    ["microphone", false, /\bmic|microphone/,
      /turn on microphone|turn microphone on|turn mic on|unmute mic|unmute microphone/],
    ["camera", true, /camera|video/, /turn off camera|turn camera off|stop video|turn off video/],
  ];
  for (const [device, pressWhenOn, named, pressToChange] of devices) {
    for (const element of controls) {
      const label = `${element.getAttribute("aria-label") ?? ""} ${element.getAttribute("title") ?? ""}`.toLowerCase();
      const switched = element.getAttribute("role") === "switch" || (element as HTMLInputElement).type === "checkbox";
      const on = element.getAttribute("aria-checked") === "true" || (element as HTMLInputElement).checked === true;
      if (switched ? on === pressWhenOn && named.test(label) : pressToChange.test(label)) {
        element.click();
        return device;
      }
    }
  }
  return "none";
}
