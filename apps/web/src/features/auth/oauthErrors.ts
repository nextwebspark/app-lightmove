/**
 * Turning an OAuth refusal code into something worth showing a person.
 *
 * Shared by the login and signup screens, which offer the same buttons and so must answer the same
 * refusals the same way. The sentences name no provider: which one refused is the server's business,
 * and naming one would be wrong the moment a second is configured.
 */

/**
 * The sentence to show, or **null when there is nothing to say**.
 *
 * Null is the answer for a cancellation, and it is the point of this function having a nullable
 * return at all. Someone who reaches the consent screen and backs out has not hit an error; telling
 * them "sign-in did not complete, try again" reads as a broken button and is how this flow used to
 * behave. Say nothing and leave the button ready.
 */
export function messageForOAuthError(code: string): string | null {
  switch (code) {
    case "OAUTH_CANCELLED":
      return null;
    case "EMAIL_NOT_WORK_ADDRESS":
      return "Please sign in with your work account. LightMove is for search firms.";
    case "EMAIL_NOT_VERIFIED":
      return "Your provider reports that address as unverified. Verify it with them, then try again.";
    case "ACCOUNT_SUSPENDED":
      return "This account has been suspended.";
    case "EMAIL_DISPOSABLE":
      return "Please use your work email address.";
    case "EMAIL_UNDELIVERABLE":
      return "That address does not appear to exist. Check it with your provider, then try again.";
    default:
      // Not "or use your password": the account may well not have one — someone who has only ever
      // signed in with a provider has no password to fall back to, and the form sits directly above
      // this message anyway for anyone who does. Retrying is also the honest advice, because the
      // likeliest cause is ours: the authorisation request lives in the server's session, and a
      // restart, a 30-minute timeout, or a callback landing on a second instance loses it. A fresh
      // attempt starts a fresh request and works.
      return "Sign-in didn't complete. Please try again.";
  }
}
