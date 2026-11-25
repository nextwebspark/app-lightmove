import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation } from "react-router-dom";
import { Button, Field, FormError, Input } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../api/authApi";
import { AuthShell } from "../components/AuthShell";
import { useHumanCheck } from "../components/HumanCheck";
import { forgotPasswordSchema, type ForgotPasswordValues } from "../schemas";

/**
 * "Forgot password?" from the login screen lands here: one email field, and a sent-state that reads the same
 * whether the address exists or not — the server won't say, and neither do we, because a page that
 * says "no account for that address" is an account-enumeration oracle with a nice font.
 */
export function ForgotPasswordPage() {
  const location = useLocation();

  // The login form hands the typed address over, so arriving here doesn't mean retyping it —
  // the same handoff signup already makes toward login.
  const prefill = (location.state as { email?: string } | null)?.email ?? "";

  const humanCheck = useHumanCheck("password-reset");
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: prefill },
  });

  const onSubmit = async (values: ForgotPasswordValues) => {
    setFormError(null);
    try {
      await authApi.requestPasswordReset(values.email, await humanCheck.takeToken());
      setSentTo(values.email);
    } catch (error) {
      // Only rate limiting or a network failure reach here — an unknown address is a quiet 202.
      setFormError(messageFor(error));
    }
  };

  return (
    <AuthShell
      title={sentTo ? undefined : "Reset your password"}
      subtitle={sentTo ? undefined : <>We&rsquo;ll email you a link to choose a new one.</>}
      below={
        sentTo ? undefined : (
          <>
            Remembered it?{" "}
            <Link to="/login" className="text-u-accent hover:underline">
              Back to sign in
            </Link>
          </>
        )
      }
    >
      {sentTo ? (
        <div className="text-center">
          <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-u-accent-tint">
            <svg
              className="size-5 text-u-accent"
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

          <h1 className="text-title font-semibold">Check your inbox</h1>

          <p className="mb-6 mt-2 text-sm text-u-text2">
            If an account exists for <span className="font-medium text-u-text">{sentTo}</span>, a
            reset link is on its way. It expires in 30 minutes.
          </p>

          <Link to="/login" className="text-note text-u-accent hover:underline">
            Back to sign in
          </Link>
        </div>
      ) : (
        <>
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

            {humanCheck.widget}

            <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
              Send reset link
            </Button>
          </form>
        </>
      )}
    </AuthShell>
  );
}
