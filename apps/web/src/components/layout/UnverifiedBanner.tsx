import { useState } from "react";
import * as authApi from "../../features/auth/api/authApi";

/**
 * Why the workspace is inert: an unverified user can reach the shell but no data behind it. The
 * resend link matters as much as the message — the one thing they need is an email that, by
 * definition, has not arrived. A rejection here is a rate limit or the network, never "no such
 * user", and both must give the button back.
 */
export function UnverifiedBanner({ email }: { email: string }) {
  const [state, setState] = useState<"idle" | "sending" | "sent" | "error">("idle");

  const resend = async () => {
    setState("sending");
    try {
      await authApi.resendVerification(email);
      setState("sent");
    } catch {
      setState("error");
    }
  };

  return (
    <div
      role="status"
      className="flex flex-wrap items-center justify-center gap-x-3 gap-y-1.5 bg-amber-dim px-6 py-2.5 text-center font-mono text-[11.5px] text-amber"
    >
      <span>Confirm {email} to unlock your workspace.</span>

      {state === "sent" ? (
        <span className="text-text3">Sent — check your inbox.</span>
      ) : (
        <>
          {state === "error" && <span className="text-red">Couldn’t send.</span>}
          <button
            type="button"
            onClick={() => void resend()}
            disabled={state === "sending"}
            className="font-semibold underline underline-offset-2 hover:no-underline disabled:opacity-50"
          >
            {state === "sending" ? "Sending…" : state === "error" ? "Try again" : "Resend email"}
          </button>
        </>
      )}
    </div>
  );
}
