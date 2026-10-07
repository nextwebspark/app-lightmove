/** Hands the browser back to the AI client: its own redirect URI, carrying the code or the error. */
export function returnToClient(redirectUri: string): void {
  window.location.assign(redirectUri);
}
