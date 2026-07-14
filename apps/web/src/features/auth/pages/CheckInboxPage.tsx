import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Card, Logo } from "../../../components/ui";
import * as authApi from "../api/authApi";
import { useAuth } from "../AuthProvider";

/**
 * The end of the wizard, for someone who has not yet clicked the link in their inbox.
 *
 * They have filled in everything. Nothing has been created — no workspace on their firm's domain, no
 * request in an admin's queue, no invitation sent — because the email domain is the only evidence we
 * have that they work at the firm they say they do, and an address nobody has opened is not evidence.
 * Verifying is what turns the wizard into an organisation.
 *
 * This used to be a red box reading "That request could not be completed." The API refused step 2 and
 * the SPA had nothing to tell the user, so a signup that was one click from finished looked broken.
 */
export function CheckInboxPage() {
  const { user, signOut, reload } = useAuth();
  const navigate = useNavigate();
  const [resending, setResending] = useState(false);
  const [resent, setResent] = useState(false);
  const [checking, setChecking] = useState(false);

  // Verifying creates the workspace, so this screen is a lie the moment it happens. Nothing else moves
  // the user off it — the router is content to leave a verified user sitting here forever, staring at a
  // page telling them to do a thing they have already done.
  useEffect(() => {
    if (!user) return;
    if (user.workspace) {
      navigate("/", { replace: true });
    } else if (user.emailVerified) {
      // Verified, but no workspace: they asked to *join* one, and an admin has yet to decide.
      navigate("/signup/pending", { replace: true });
    }
  }, [user, navigate]);

  const resend = async () => {
    if (!user) return;
    setResending(true);
    try {
      await authApi.resendVerification(user.email);
      setResent(true);
    } finally {
      setResending(false);
    }
  };

  const check = async () => {
    setChecking(true);
    try {
      // The redirect is left to the effect above, so it also fires when verification arrives by another
      // route — the link opened in a second tab, say, which is exactly how people do this.
      await reload();
    } finally {
      setChecking(false);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[420px] max-w-[94vw] text-center [animation-delay:60ms]">
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

        <h1 className="text-[19px] font-semibold leading-tight">Check your inbox</h1>

        <p className="mb-6 mt-2 text-sm text-text2">
          We sent a link to <span className="font-medium text-text1">{user?.email}</span>. Your
          organization is saved and will be created the moment you confirm the address.
        </p>

        <p className="mb-6 font-mono text-xs text-text3">
          Your email domain is how we know which firm you work at — so we confirm it before creating
          anything in that firm&rsquo;s name.
        </p>

        <div className="flex flex-col gap-2">
          <Button onClick={check} disabled={checking}>
            {checking ? "Checking…" : "I've confirmed it"}
          </Button>

          <Button variant="ghost" onClick={resend} disabled={resending || resent}>
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
