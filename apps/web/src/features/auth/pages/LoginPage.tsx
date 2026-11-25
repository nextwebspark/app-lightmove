import { zodResolver } from "@hookform/resolvers/zod";
import { type FormEvent, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Field, Input, Notice } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { isMfaChallenge } from "../api/types";
import { useAuth } from "../AuthProvider";
import { SSO_SIGN_IN_PATH } from "../authPaths";
import { AUTH_LINK_CLASS, AuthShell, LegalFooter, SubmitError, UNREACHABLE_MESSAGE } from "../components/AuthShell";
import { OAuthButtons, signInWithLabel } from "../components/OAuthButtons";
import { PasswordInput } from "../components/PasswordInput";
import { messageForOAuthError } from "../oauthErrors";
import { landingAfterSignIn, rememberReturnTo, safeReturnTo } from "../returnTo";
import { loginSchema, type LoginValues } from "../schemas";
import { takeAccountDeleted } from "../accountDeletedNotice";
import { useAuthProviders } from "../useAuthProviders";
import { TWO_FACTOR_SIGN_IN_PATH } from "./TwoFactorChallengePage";

type SignInStep = "email" | "password";

/**
 * Sign in, email first. A port of uncava-design/Login.dc.html (`login`, `login-password`). The password step follows
 * every address alike — nothing is looked up between the two — so the steps never tell who has an account.
 */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const [step, setStep] = useState<SignInStep>("email");
  const { data: providers } = useAuthProviders();

  // Signup and the SSO screen hand the typed address over, so coming here never means typing it twice.
  const prefill = (location.state as { email?: string } | null)?.email ?? "";
  // Settings → Profile's Delete my account lands here, the session already gone; said once.
  const [accountDeleted, setAccountDeleted] = useState(false);
  useEffect(() => {
    if (takeAccountDeleted()) setAccountDeleted(true);
  }, []);

  // Where a guard sent them from, honoured on submit and stashed for the OAuth round trip — which
  // leaves the SPA and so cannot carry router state. Written on every visit, cleared when there is
  // nowhere to return to.
  const returnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);
  useEffect(() => {
    rememberReturnTo(returnTo);
  }, [returnTo]);

  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    trigger,
    setFocus,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: prefill, password: "" },
  });

  /**
   * A full-page OAuth redirect lands back here with ?error=CODE when it refuses someone. The popup
   * flow reports through `OAuthButtons` instead and never reaches this.
   *
   * The parameter is stripped once read. Left in place it survives a reload and resurrects a banner
   * for an attempt that is long over.
   */
  useEffect(() => {
    const code = searchParams.get("error");
    if (!code) {
      return;
    }

    setFormError(messageForOAuthError(code));
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current);
        next.delete("error");
        return next;
      },
      { replace: true },
    );
  }, [searchParams, setSearchParams]);

  const goTo = (next: SignInStep) => {
    setFormError(null);
    setStep(next);
  };

  // The email input stays mounted across both steps, so autoFocus cannot bring focus back to it.
  const backToEmail = () => {
    goTo("email");
    setFocus("email");
  };

  const handleContinue = async (event: FormEvent) => {
    event.preventDefault();
    if (await trigger("email")) goTo("password");
  };

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    try {
      const answer = await signIn(values.email, values.password);
      if (isMfaChallenge(answer)) {
        navigate(TWO_FACTOR_SIGN_IN_PATH, { state: returnTo ? { from: returnTo } : undefined });
        return;
      }
      // Only once they are somewhere they can be: an unverified user or one mid-wizard belongs at the
      // step homeFor names, not at the page they were reaching for.
      navigate(landingAfterSignIn(answer, returnTo), { replace: true });
    } catch (error) {
      setFormError(error instanceof ApiRequestError ? messageFor(error) : UNREACHABLE_MESSAGE);
    }
  };

  // Watched, not read with getValues: these inputs are uncontrolled, so a render-time read would be stale.
  const typedEmail = watch("email");

  return (
    <AuthShell
      title="Sign in to Uncava"
      subtitle="Executive search workspace"
      footer={<LegalFooter />}
      accountPrompt={
        <>
          New to Uncava?{" "}
          <Link to="/signup" className={AUTH_LINK_CLASS}>
            Sign up
          </Link>
        </>
      }
    >
      {step === "email" && accountDeleted && <Notice>Your account was deleted.</Notice>}
      {step === "email" && <OAuthButtons onError={setFormError} placement="above" />}

      <form onSubmit={step === "email" ? handleContinue : handleSubmit(onSubmit)} noValidate>
        <Field label="Work email" error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="username"
            autoFocus={step === "email"}
            placeholder="you@firm.com"
            invalid={!!errors.email}
            {...register("email")}
          />
        </Field>

        {step === "password" && (
          <Field label="Password" error={errors.password?.message}>
            <PasswordInput
              autoComplete="current-password"
              autoFocus
              placeholder="••••••••"
              invalid={!!errors.password}
              {...register("password")}
            />
          </Field>
        )}

        <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
          {step === "email" ? "Continue" : "Sign in"}
        </Button>
        <SubmitError message={formError} />
      </form>

      {step === "email" ? (
        <div className="mt-4 text-center">
          <Link to={SSO_SIGN_IN_PATH} state={{ email: typedEmail }} className={AUTH_LINK_CLASS}>
            Continue with SSO
          </Link>
        </div>
      ) : (
        <div className="mt-4 flex flex-col items-center gap-3">
          <Link to="/forgot-password" state={{ email: typedEmail }} className={AUTH_LINK_CLASS}>
            Forgot password?
          </Link>
          <button type="button" onClick={backToEmail} className={AUTH_LINK_CLASS}>
            {signInWithLabel(providers?.providers ?? [], "SSO", "Sign in another way")}
          </button>
        </div>
      )}
    </AuthShell>
  );
}
