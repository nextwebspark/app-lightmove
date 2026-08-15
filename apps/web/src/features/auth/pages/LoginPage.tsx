import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import { OAuthButtons } from "../components/OAuthButtons";
import { loginSchema, type LoginValues } from "../schemas";

/** Sign in. A port of claude-design/Login.dc.html. */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();

  // Signup hands the typed address over when it turns out to already have an account, so "log in
  // instead" starts with the email filled rather than making the user type it twice.
  const prefill = (location.state as { email?: string } | null)?.email ?? "";

  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: prefill, password: "" },
  });

  /** An OAuth flow redirects back here with ?error=CODE when it refuses someone. */
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
      // Route on what is true of them: into the app, back to the inbox their held wizard waits on,
      // to the invitation waiting for them, or into the wizard they never finished.
      navigate(
        user.workspace
          ? "/"
          : user.onboardingHeld
            ? "/signup/verify"
            : user.pendingInvitation
              ? "/auth/accept-invite"
              : "/signup/workspace",
        { replace: true },
      );
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
      <ThemeToggle className="fixed right-4 top-4 z-50" />
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
              /* Carries the typed email along (watch, not getValues — these inputs are uncontrolled,
                 so a render-time getValues would capture a stale value), sparing a retype. */
              <Link
                to="/forgot-password"
                state={{ email: watch("email") }}
                className="text-[11.5px] font-medium text-sky hover:underline"
              >
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

        <OAuthButtons />
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
 * An OAuth flow cannot render a form error — it is a redirect — so it hands the code back in the
 * query string and we say it here. The sentences name no provider: which one refused is the
 * server's business, and naming one would be wrong the moment a second is configured.
 */
function messageForOAuthError(code: string): string {
  switch (code) {
    case "EMAIL_NOT_WORK_ADDRESS":
      return "Please sign in with your work account. LightMove is for search firms.";
    case "EMAIL_NOT_VERIFIED":
      return "Your provider reports that address as unverified. Verify it with them, then try again.";
    case "ACCOUNT_SUSPENDED":
      return "This account has been suspended.";
    default:
      return "Sign-in did not complete. Try again, or use your password.";
  }
}
