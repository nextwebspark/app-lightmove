import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import { homeFor } from "../homeFor";

type State = "verifying" | "success" | "failed";

/**
 * Where the emailed verification link lands.
 *
 * The link points at the SPA rather than straight at the API on purpose: the user ends up inside the
 * app, looking at a LightMove screen, rather than at a page of raw JSON.
 *
 * Redeeming returns a session, so this browser is signed in even though it is usually not the one that
 * filled in signup. Continue therefore always leads somewhere — the organisation step, for the creator
 * this gate exists for.
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user, verifyEmail, reload } = useAuth();

  const [state, setState] = useState<State>("verifying");
  const [message, setMessage] = useState("");
  const [continuing, setContinuing] = useState(false);

  /**
   * React 18+ runs effects twice in development StrictMode. The verification token is single-use, so a
   * second call would consume nothing and report "this link is not valid" over a verification that had
   * in fact just succeeded. Guarding on a ref rather than on state, because state updates are async and
   * the second invocation would win the race.
   */
  const attempted = useRef(false);

  useEffect(() => {
    if (attempted.current) {
      return;
    }
    attempted.current = true;

    const token = searchParams.get("token");
    if (!token) {
      setState("failed");
      setMessage("This link is missing its verification code.");
      return;
    }

    void (async () => {
      try {
        await verifyEmail(token);
        setState("success");
      } catch (error) {
        setState("failed");
        setMessage(
          error instanceof ApiRequestError
            ? error.problem.detail
            : "Could not verify your email. Please try again.",
        );
      }
    })();
  }, [searchParams, verifyEmail]);

  // A second click on the same link burns nothing and is the commonest way to reach the failure branch.
  // Someone already verified in this browser has done what the link was for, so say so.
  const verifiedAlready = state === "failed" && !!user?.emailVerified;
  const settled = state === "success" || verifiedAlready;

  /**
   * Re-read before routing, because this page can sit open for a long time and the wizard is normally
   * finished somewhere else.
   *
   * <p>The tab that was waiting on this link polls, so it advances the moment the link is clicked and
   * the user carries on there — organisation, invitations, into the app. All of that happens while this
   * card is still showing a `user` snapshot taken at redemption time, when there was no workspace.
   * Routing on that snapshot sent someone who already has a workspace back to the create form, which
   * then answers ALREADY_IN_WORKSPACE.
   */
  const handleContinue = async () => {
    setContinuing(true);
    // homeFor treats null as "no session" and sends them to sign in, which is the honest answer if the
    // re-read failed.
    navigate(homeFor(await reload()), { replace: true });
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[400px] max-w-[94vw] text-center [animation-delay:60ms]">
        {state === "verifying" && (
          <>
            <h1 className="text-[19px] font-semibold">Verifying your email…</h1>
            <p className="mt-2 font-mono text-xs text-text3">One moment.</p>
          </>
        )}

        {settled && (
          <>
            <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-green-dim">
              <svg className="size-5 text-green" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </div>

            <h1 className="text-[19px] font-semibold">Email verified</h1>
            <p className="mb-6 mt-2 font-mono text-xs text-text3">Your account is confirmed.</p>

            <Button className="w-full" onClick={handleContinue} disabled={continuing}>
              {continuing ? "One moment…" : "Continue"}
            </Button>
          </>
        )}

        {state === "failed" && !verifiedAlready && (
          <>
            <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-red-dim">
              <svg className="size-5 text-red" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </div>

            <h1 className="text-[19px] font-semibold">Verification failed</h1>
            <p className="mb-6 mt-2 font-mono text-xs text-text3">{message}</p>

            <Link to="/login" className="text-[12.5px] text-sky hover:underline">
              Back to sign in
            </Link>
          </>
        )}
      </Card>
    </div>
  );
}
