import type { FeedbackContext } from "../api/types";

/**
 * Everything the browser can say about itself, so the tester does not have to.
 *
 * "What were you using?" is the question a bug report most often fails to answer, and the one the
 * page already knows. None of it is trusted by the server — it is reproduction detail, escaped on the
 * way into the issue.
 */
export function collectReportContext(): FeedbackContext {
  return {
    pageUrl: safePageUrl(),
    userAgent: navigator.userAgent,
    viewport: `${window.innerWidth}x${window.innerHeight}`,
    screenSize: `${window.screen.width}x${window.screen.height}`,
    devicePixelRatio: String(window.devicePixelRatio),
    language: navigator.language,
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    theme: document.body.classList.contains("dark") ? "dark" : "light",
    reportedAt: new Date().toISOString(),
  };
}

/**
 * Query parameters that are credentials rather than state.
 *
 * The reason this list exists: the screens a tester is *most* likely to report a bug from are the
 * ones reached by clicking an emailed link — verify, reset password, accept invitation — and every
 * one of those carries a live 256-bit token in its URL. Filing that URL into a public GitHub issue
 * hands the token to anyone reading it, and a reset token is a password change.
 */
const CREDENTIAL_PARAMETERS = [
  "token",
  "code",
  "state",
  "secret",
  "signature",
  "password",
  "access_token",
  "accesstoken",
  "id_token",
  "refresh_token",
];

/** The current page, minus anything that would be a credential in someone else's hands. */
function safePageUrl(): string {
  const url = new URL(window.location.href);

  url.searchParams.forEach((_value, key) => {
    if (CREDENTIAL_PARAMETERS.includes(key.toLowerCase())) {
      url.searchParams.set(key, "[redacted]");
    }
  });

  // The whole fragment, unread: an OAuth implicit response puts its tokens there, and unlike the
  // query there is no shape to it worth preserving.
  url.hash = "";

  return `${url.pathname}${url.search}`;
}
