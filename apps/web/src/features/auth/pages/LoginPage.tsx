import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import * as authApi from "../api/authApi";
import { loginSchema, type LoginValues } from "../schemas";

/**
 * Sign in. A port of claude-design/Login.dc.html.
 *
 * The "Continue with SSO" button in the mockup sat behind a `showSso` prop. Here it is behind the
 * server's answer to "is Google actually configured?" — a button that leads nowhere is worse than no
 * button.
 */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();

  const [formError, setFormError] = useState<string | null>(null);
  const [googleEnabled, setGoogleEnabled] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  useEffect(() => {
    void authApi
      .providers()
      .then((available) => setGoogleEnabled(available.google))
      .catch(() => setGoogleEnabled(false));
  }, []);

  /** The Google flow redirects back here with ?error=CODE when it refuses someone. */
  useEffect(() => {
    const code = searchParams.get("error");
    if (code) {
      setFormError(messageForOAuthError(code));
    }
  }, [searchParams]);

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    try {
      const user = await signIn(values.email, values.password);
      // Someone with no workspace has not finished signing up — send them back into the wizard
      // rather than into an app they have no tenant for.
      navigate(user.workspace ? "/" : "/signup/workspace", { replace: true });
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError
          ? error.problem.detail
          : "Could not reach LightMove. Check your connection and try again.",
      );
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[400px] max-w-[94vw] [animation-delay:60ms]">
        <h1 className="text-[19px] font-semibold leading-tight">Sign in</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-text3">Executive search workspace</p>

        <FormError message={formError} />

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <Field label="Work email" error={errors.email?.message}>
            <Input
              type="email"
              autoComplete="email"
              autoFocus
              placeholder="you@firm.com"
              invalid={!!errors.email}
              {...register("email")}
            />
          </Field>

          <Field
            label="Password"
            error={errors.password?.message}
            action={
              <Link to="/forgot-password" className="text-[11.5px] font-medium text-sky hover:underline">
                Forgot?
              </Link>
            }
          >
            <Input
              type="password"
              autoComplete="current-password"
              placeholder="••••••••"
              invalid={!!errors.password}
              {...register("password")}
            />
          </Field>

          <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
            Continue
          </Button>
        </form>

        {googleEnabled && (
          <>
            <div className="my-[18px] flex items-center gap-2.5 font-mono text-[10px] font-medium uppercase tracking-[0.1em] text-text3">
              <span className="h-px flex-1 bg-line-soft" />
              <span>or</span>
              <span className="h-px flex-1 bg-line-soft" />
            </div>

            {/*
              A full page navigation, not fetch(). This is an OAuth redirect: the browser has to
              actually leave for Google's consent screen and come back. An XHR would be blocked by
              CORS and could not show the user Google's own UI even if it were not.
            */}
            <Button
              type="button"
              variant="secondary"
              className="w-full"
              onClick={() => {
                window.location.href = "/oauth2/authorization/google";
              }}
            >
              <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                <rect x="3" y="11" width="18" height="10" rx="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
              Continue with Google
            </Button>
          </>
        )}
      </Card>

      <p className="animate-fade-up text-[12.5px] text-text2 [animation-delay:120ms]">
        New to LightMove?{" "}
        <Link to="/signup" className="text-sky hover:underline">
          Create an account
        </Link>
      </p>
    </div>
  );
}

/**
 * The Google flow cannot render a form error — it is a redirect — so it hands the code back in the
 * query string and we say it here.
 */
function messageForOAuthError(code: string): string {
  switch (code) {
    case "EMAIL_NOT_WORK_ADDRESS":
      return "Please sign in with your work Google account. LightMove is for search firms.";
    case "EMAIL_NOT_VERIFIED":
      return "Google reports that address as unverified. Verify it with Google, then try again.";
    case "ACCOUNT_SUSPENDED":
      return "This account has been suspended.";
    default:
      return "Google sign-in did not complete. Try again, or use your password.";
  }
}
