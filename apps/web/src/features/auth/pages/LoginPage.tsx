import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import { homeFor } from "../homeFor";
import { messageForOAuthError } from "../oauthErrors";
import { rememberReturnTo, safeReturnTo } from "../returnTo";
import { OAuthButtons } from "../components/OAuthButtons";
import { loginSchema, type LoginValues } from "../schemas";

/** Sign in. A port of claude-design/Login.dc.html. */
export function LoginPage() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();

  // Signup hands the typed address over when it turns out to already have an account, so "log in
  // instead" starts with the email filled rather than making the user type it twice.
  const prefill = (location.state as { email?: string } | null)?.email ?? "";

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

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    try {
      const user = await signIn(values.email, values.password);
      // Only once they are somewhere they can be: an unverified user or one mid-wizard belongs at the
      // step homeFor names, not at the page they were reaching for.
      navigate(returnTo && user.workspace ? returnTo : homeFor(user), { replace: true });
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError
          ? error.problem.detail
          : "Could not reach LightMove. Check your connection and try again.",
      );
    }
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
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

        <OAuthButtons onError={setFormError} />
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
