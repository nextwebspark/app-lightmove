/**
 * "Change email" on the verify step signs out and starts signup again. Signing out makes the route guard
 * redirect at once, and in the real router that redirect beats any navigate queued after it, so the
 * restart is recorded first and the guard itself sends the visitor to step 1. Session storage, because
 * the guard and the signup form are separate renders.
 */
const RESTART_KEY = "lm.signup.restart";

export function markSignupRestart(fullName: string): void {
  try {
    sessionStorage.setItem(RESTART_KEY, fullName);
  } catch {
    // A private window may refuse storage; signing out still happens, the form just starts blank.
  }
}

export function hasPendingSignupRestart(): boolean {
  try {
    return sessionStorage.getItem(RESTART_KEY) !== null;
  } catch {
    return false;
  }
}

export function signupRestartName(): string {
  try {
    return sessionStorage.getItem(RESTART_KEY) ?? "";
  } catch {
    return "";
  }
}

/** Once step 1 has shown it: a later visit to signup starts blank. */
export function clearSignupRestart(): void {
  try {
    sessionStorage.removeItem(RESTART_KEY);
  } catch {
    // Nothing was stored.
  }
}
