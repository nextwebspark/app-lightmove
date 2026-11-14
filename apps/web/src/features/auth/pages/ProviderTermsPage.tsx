import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AuthLogo, Avatar, Button, Card, FormError, Spinner } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { isMfaChallenge } from "../api/types";
import { LegalConsent, TERMS_REQUIRED_MESSAGE } from "../components/LegalConsent";
import { providerLabel } from "../components/OAuthButtons";
import { landingAfterSignIn, safeReturnTo } from "../returnTo";
import { TWO_FACTOR_SIGN_IN_PATH } from "./TwoFactorChallengePage";

export const PROVIDER_TERMS_PATH = "/signup/terms";

/**
 * A first sign-in with Google or LinkedIn (Signup.dc.html `step=terms`): the provider proved the address, but nothing
 * exists until the box is ticked. The details are the provider's, read by the httpOnly cookie the callback set; closing
 * the page creates nothing, and the hold lapses in minutes.
 */
export function ProviderTermsPage() {
  const { completeProviderSignup } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);

  const [ticked, setTicked] = useState(false);
  const [tickError, setTickError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const pending = useQuery({
    queryKey: ["auth", "pendingRegistration"],
    queryFn: authApi.pendingRegistration,
    retry: false,
    staleTime: Infinity,
  });

  const handleTick = (checked: boolean) => {
    setTicked(checked);
    setTickError(undefined);
  };

  const handleCreate = async () => {
    if (!ticked) {
      setTickError(TERMS_REQUIRED_MESSAGE);
      return;
    }
    setFormError(null);
    setSubmitting(true);
    try {
      const answer = await completeProviderSignup(true);
      if (isMfaChallenge(answer)) {
        navigate(TWO_FACTOR_SIGN_IN_PATH, { replace: true, state: returnTo ? { from: returnTo } : undefined });
        return;
      }
      navigate(landingAfterSignIn(answer, returnTo), { replace: true });
    } catch (error) {
      setSubmitting(false);
      setFormError(
        error instanceof ApiRequestError
          ? messageFor(error)
          : "Could not reach Uncava. Check your connection and try again.",
      );
    }
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <ThemeToggle className="fixed right-4 top-4 z-50" />
      <AuthLogo />

      <Card className="w-[440px] max-w-[94vw] [animation-delay:80ms]">
        {pending.isPending ? (
          <div className="grid place-items-center py-6 text-u-text3" role="status" aria-label="Loading">
            <Spinner />
          </div>
        ) : pending.isError ? (
          <>
            <h1 className="type-title">This sign-up has expired</h1>
            <p className="mb-6 mt-1 text-body text-u-text2">{messageFor(pending.error)}</p>
            <Link
              to="/signup"
              className="block text-center text-note font-medium text-u-accent hover:underline"
            >
              Back to sign up
            </Link>
          </>
        ) : (
          <>
            <h1 className="type-title">One more step</h1>
            <p className="mb-5 mt-1 font-mono text-note text-u-text3">
              Create your account with {providerLabel(pending.data.provider)}
            </p>

            <FormError message={formError} />

            <div className="mb-5 flex items-center gap-3 rounded-u-card border border-u-border bg-u-raised px-3.5 py-3">
              <Avatar id={pending.data.email} name={pending.data.fullName} size="lg" />
              <span className="min-w-0">
                <span className="block text-body font-semibold text-u-text">{pending.data.fullName}</span>
                <span className="block truncate font-mono text-note text-u-text3">{pending.data.email}</span>
              </span>
            </div>

            <LegalConsent checked={ticked} onChange={handleTick} error={tickError} />

            <Button type="button" loading={submitting} onClick={handleCreate} className="w-full">
              Create account
            </Button>
            <Link
              to="/login"
              className="mt-3.5 block text-center text-note font-medium text-u-text3 hover:text-u-text2"
            >
              Cancel
            </Link>
          </>
        )}
      </Card>
    </div>
  );
}
