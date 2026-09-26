/**
 * The one piece of assistant state that outlives a tab: which project's chat was open. Its own module
 * so the auth layer can forget it on a workspace switch without importing the provider — the project
 * it names belongs to the workspace just left.
 */
export const ASSISTANT_OPEN_KEY = "lm.assistant.open";

export function forgetOpenAssistant(): void {
  try {
    localStorage.removeItem(ASSISTANT_OPEN_KEY);
  } catch {
    // A private window refuses this, and there is nothing to forget there anyway.
  }
}
