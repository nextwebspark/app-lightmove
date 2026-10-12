import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { AuthLogo, Button, Card } from "../../../components/ui";
import { SendFailedNotice } from "../components/SendFailedNotice";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import { homeFor } from "../homeFor";
import { markSignupRestart } from "../signupRestart";

export const RESEND_COOLDOWN_SECONDS = 30;

/** How often the tab re-asks the server whether the link has been clicked somewhere else. */
const POLL_INTERVAL_MS = 5_000;

/**
 * Signup step 2 — the gate. Nothing after this exists until the emailed link is clicked.
 *
 * The link is usually opened by the mail client in a different browser, which finishes the wizard
 * there. This tab therefore cannot wait on a callback; it polls, and moves itself on when the answer
 * changes. The button is for the case where polling is blocked.
 */
export function SignupVerifyStepPage() {
  const { user, signOut, reload } = useAuth();
  const navigate = useNavigate();
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [resendFeedback, setResendFeedback] = useState<Feedback | null>(null);
  const [checking, setChecking] = useState(false);
  const [notYetSeen, setNotYetSeen] = useState(false);
  const [leaving, setLeaving] = useState(false);

  useEffect(() => {
    if (user?.emailVerified) navigate(homeFor(user), { replace: true });
  }, [user, navigate]);

  useEffect(() => {
    if (user?.emailVerified) return;

    const check = () => void reload();
    const timer = window.setInterval(check, POLL_INTERVAL_MS);
    // Returning to this tab is the likeliest moment for the answer to have changed, and waiting out
    // the interval there reads as the app having missed the click.
    window.addEventListener("focus", check);

    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", check);
    };
  }, [user?.emailVerified, reload]);

  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = window.setTimeout(() => setCooldown((left) => left - 1), 1_000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const handleResend = async () => {
    if (!user) return;
    setResending(true);
    setResendFeedback(null);
    setNotYetSeen(false);
    try {
      await authApi.resendVerification(user.email);
      setResendFeedback({ kind: "sent", message: `Sent to ${user.email} — it can take a minute. Check spam and promotions.` });
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch {
      setResendFeedback({ kind: "failed" });
    } finally {
      setResending(false);
    }
  };

  const handleCheck = async () => {
    setChecking(true);
    setNotYetSeen(false);
    setResendFeedback(null);
    try {
      const fresh = await reload();
      if (fresh && !fresh.emailVerified) setNotYetSeen(true);
    } finally {
      setChecking(false);
    }
  };

  // The account at the mistyped address stays unverified and unusable; signing up again is the way to
  // the right one. signOut clears this browser's session even when the server call fails.
  const handleChangeEmail = () => {
    markSignupRestart(user?.fullName ?? "");
    setLeaving(true);
    signOut().catch(() => {});
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <AuthLogo />
      <Stepper steps={SIGNUP_STEPS} current={2} />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:80ms]">
        <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-u-accent-tint">
          <svg
            className="size-5 text-u-accent"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            aria-hidden="true"
          >
            <rect x="3" y="5" width="18" height="14" rx="2" />
            <path d="m3 7 9 6 9-6" />
          </svg>
        </div>

        <h1 className="text-[19px] font-semibold leading-tight">Confirm your email</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-u-text3">Step 2 of 4 · check your inbox</p>

        <p className="mb-2 text-sm text-u-text2">
          We sent a link to <span className="font-medium text-u-text">{user?.email}</span>. Open it and
          you will be signed in and brought straight to the next step — here, or in whichever browser
          opens the link.
        </p>
        <p className="mb-6 text-note text-u-text3">
          Wrong address?{" "}
          <button
            type="button"
            onClick={handleChangeEmail}
            disabled={leaving}
            className="font-medium text-u-accent hover:underline disabled:opacity-60"
          >
            {leaving ? "Signing out…" : "Change email"}
          </button>
        </p>

        <p className="mb-6 font-mono text-xs text-u-text3">
          Your email domain is how we know which firm you work at — so we confirm it before creating
          anything in that firm&rsquo;s name.
        </p>

        <div aria-live="polite" className="text-left">
          {notYetSeen && (
            <p className="mb-4 rounded-lg bg-u-accent-tint px-3 py-2.5 font-mono text-meta text-u-accent">
              We haven&rsquo;t seen the click yet. Open the newest email from Uncava — older links stop
              working.
            </p>
          )}
          {resendFeedback?.kind === "sent" && (
            <p className="mb-4 rounded-lg bg-u-direct-tint px-3 py-2.5 font-mono text-meta text-u-direct">
              {resendFeedback.message}
            </p>
          )}
        </div>
        {resendFeedback?.kind === "failed" && (
          <div className="text-left">
            <SendFailedNotice />
          </div>
        )}

        <div className="flex flex-col gap-2">
          <Button onClick={handleCheck} disabled={checking}>
            {checking ? "Checking…" : "I've confirmed it"}
          </Button>

          <Button variant="ghost" onClick={handleResend} disabled={resending || cooldown > 0}>
            {resending ? "Sending…" : cooldown > 0 ? `Resend again in ${cooldown}s` : "Resend the link"}
          </Button>

          <Button variant="ghost" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </Card>
    </div>
  );
}

type Feedback = { kind: "sent"; message: string } | { kind: "failed" };
