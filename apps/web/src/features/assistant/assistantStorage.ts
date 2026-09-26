/** Which project's chat was open — forgotten on a switch, since that project belongs to the workspace left. */
export const ASSISTANT_OPEN_KEY = "lm.assistant.open";

export function forgetOpenAssistant(): void {
  try {
    localStorage.removeItem(ASSISTANT_OPEN_KEY);
  } catch {
    // A private window refuses this, and there is nothing to forget there anyway.
  }
}
