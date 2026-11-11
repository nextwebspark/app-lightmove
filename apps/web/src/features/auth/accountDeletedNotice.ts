/**
 * Says on the sign-in page that the account was just deleted. Router state cannot carry it: forgetting the session
 * lets the auth guard send the page to sign-in first, with state of its own.
 */
const NOTICE_KEY = "lm.account.deleted";

export function rememberAccountDeleted(): void {
  try {
    sessionStorage.setItem(NOTICE_KEY, "1");
  } catch {
    // A private window may refuse storage; the account is deleted all the same, just unannounced.
  }
}

export function takeAccountDeleted(): boolean {
  try {
    const deleted = sessionStorage.getItem(NOTICE_KEY) === "1";
    sessionStorage.removeItem(NOTICE_KEY);
    return deleted;
  } catch {
    return false;
  }
}
