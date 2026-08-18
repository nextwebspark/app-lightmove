import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Logo } from "../../../components/ui";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import { homeFor } from "../homeFor";

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
  const [resent, setResent] = useState(false);
  const [checking, setChecking] = useState(false);

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

  const handleResend = async () => {
    if (!user) return;
    setResending(true);
    try {
      await authApi.resendVerification(user.email);
      setResent(true);
    } finally {
      setResending(false);
    }
  };

  const handleCheck = async () => {
    setChecking(true);
    try {
      await reload();
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />
      <Stepper steps={SIGNUP_STEPS} current={2} />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:80ms]">
        <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-amber-dim">
          <svg
            className="size-5 text-amber"
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
        <p className="mb-6 mt-1 font-mono text-xs text-text3">Step 2 of 4 · check your inbox</p>

        <p className="mb-6 text-sm text-text2">
          We sent a link to <span className="font-medium text-text1">{user?.email}</span>. Open it and
          you will be signed in and brought straight to the next step — here, or in whichever browser
          opens the link.
        </p>

        <p className="mb-6 font-mono text-xs text-text3">
          Your email domain is how we know which firm you work at — so we confirm it before creating
          anything in that firm&rsquo;s name.
        </p>

        <div className="flex flex-col gap-2">
          <Button onClick={handleCheck} disabled={checking}>
            {checking ? "Checking…" : "I've confirmed it"}
          </Button>

          <Button variant="ghost" onClick={handleResend} disabled={resending || resent}>
            {resent ? "Link sent" : resending ? "Sending…" : "Resend the link"}
          </Button>

          <Button variant="ghost" onClick={signOut}>
            Sign out
          </Button>
        </div>
      </Card>
    </div>
  );
}
