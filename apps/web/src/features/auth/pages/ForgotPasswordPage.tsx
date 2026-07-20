import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../api/authApi";
import { forgotPasswordSchema, type ForgotPasswordValues } from "../schemas";

/**
 * "Forgot?" from the login screen lands here: one email field, and a sent-state that reads the same
 * whether the address exists or not — the server won't say, and neither do we, because a page that
 * says "no account for that address" is an account-enumeration oracle with a nice font.
 */
export function ForgotPasswordPage() {
  const [formError, setFormError] = useState<string | null>(null);
  const [sentTo, setSentTo] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ForgotPasswordValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const onSubmit = async (values: ForgotPasswordValues) => {
    setFormError(null);
    try {
      await authApi.requestPasswordReset(values.email);
      setSentTo(values.email);
    } catch (error) {
      // Only rate limiting or a network failure reach here — an unknown address is a quiet 202.
      setFormError(messageFor(error));
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[400px] max-w-[94vw] [animation-delay:60ms]">
        {sentTo ? (
          <div className="text-center">
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
              If an account exists for <span className="font-medium text-text1">{sentTo}</span>, a
              reset link is on its way. It expires in 30 minutes.
            </p>

            <Link to="/login" className="text-[12.5px] text-sky hover:underline">
              Back to sign in
            </Link>
          </div>
        ) : (
          <>
            <h1 className="text-[19px] font-semibold leading-tight">Reset your password</h1>
            <p className="mb-6 mt-1 font-mono text-xs text-text3">
              We&rsquo;ll email you a link to choose a new one.
            </p>

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

              <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
                Send reset link
              </Button>
            </form>
          </>
        )}
      </Card>

      {!sentTo && (
        <p className="animate-fade-up text-[12.5px] text-text2 [animation-delay:120ms]">
          Remembered it?{" "}
          <Link to="/login" className="text-sky hover:underline">
            Back to sign in
          </Link>
        </p>
      )}
    </div>
  );
}
