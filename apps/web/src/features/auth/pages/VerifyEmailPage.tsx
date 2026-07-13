import { useEffect, useRef, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";

type State = "verifying" | "success" | "failed";

/**
 * Where the emailed verification link lands.
 *
 * The link points at the SPA rather than straight at the API on purpose: the user ends up inside the
 * app, looking at a LightMove screen, rather than at a page of raw JSON.
 */
export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { user, reload } = useAuth();

  const [state, setState] = useState<State>("verifying");
  const [message, setMessage] = useState("");

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
        await authApi.verifyEmail(token);

        // The access token in memory still claims unverified — it was minted before the click. Reload
        // the user so the rest of the app stops showing the "please verify" banner.
        await reload();
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
  }, [searchParams, reload]);

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

        {state === "success" && (
          <>
            <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-green-dim">
              <svg className="size-5 text-green" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" aria-hidden="true">
                <path d="M20 6 9 17l-5-5" />
              </svg>
            </div>

            <h1 className="text-[19px] font-semibold">Email verified</h1>
            <p className="mb-6 mt-2 font-mono text-xs text-text3">Your account is confirmed.</p>

            <Button
              className="w-full"
              onClick={() =>
                // Where they go depends on how far through signup they got, which is exactly what the
                // reloaded user tells us.
                navigate(user?.workspace ? "/" : user ? "/signup/workspace" : "/login", {
                  replace: true,
                })
              }
            >
              Continue
            </Button>
          </>
        )}

        {state === "failed" && (
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
