import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { codeOf, messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../AuthProvider";
import { resetPasswordSchema, type ResetPasswordValues } from "../schemas";

/**
 * Where the emailed reset link lands: choose a new password, typed twice, and walk straight into the
 * app — the server issues a full session on success, so there is no second sign-in to perform.
 *
 * No upfront token check: the form is two fields, and a preview could not promise anything anyway —
 * the token might be consumed between the look and the submit. Submit-and-see, like /auth/verify.
 */
export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { resetPassword } = useAuth();

  const token = searchParams.get("token");

  const [formError, setFormError] = useState<string | null>(null);
  const [linkDead, setLinkDead] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  const onSubmit = async (values: ResetPasswordValues) => {
    if (!token) return;
    setFormError(null);
    try {
      const user = await resetPassword(token, values.password);
      // Signed in — route exactly as login does, on what is true of them right now.
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
      const code = codeOf(error);
      if (code === "TOKEN_INVALID" || code === "TOKEN_EXPIRED") {
        // The link is spent or stale — no retry with these fields will help; they need a fresh one.
        setLinkDead(true);
      } else {
        setFormError(messageFor(error));
      }
    }
  };

  const invalidLink = !token || linkDead;

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />

      <Card className="w-[400px] max-w-[94vw] [animation-delay:60ms]">
        {invalidLink ? (
          <div className="text-center">
            <div className="mx-auto mb-4 grid size-11 place-items-center rounded-full bg-red-dim">
              <svg
                className="size-5 text-red"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2.5"
                aria-hidden="true"
              >
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </div>

            <h1 className="text-[19px] font-semibold">
              {token ? "This link has expired or was already used" : "This link is incomplete"}
            </h1>
            <p className="mb-6 mt-2 font-mono text-xs text-text3">
              Reset links work once and expire after 30 minutes. Request a fresh one — it only takes
              a moment.
            </p>

            <Button className="w-full" onClick={() => navigate("/forgot-password")}>
              Request a new link
            </Button>

            <p className="mt-4 text-[12.5px]">
              <Link to="/login" className="text-sky hover:underline">
                Back to sign in
              </Link>
            </p>
          </div>
        ) : (
          <>
            <h1 className="text-[19px] font-semibold leading-tight">Choose a new password</h1>
            <p className="mb-6 mt-1 font-mono text-xs text-text3">
              You&rsquo;ll be signed in as soon as it&rsquo;s set.
            </p>

            <FormError message={formError} />

            <form onSubmit={handleSubmit(onSubmit)} noValidate>
              <Field label="New password" error={errors.password?.message}>
                <Input
                  type="password"
                  autoComplete="new-password"
                  autoFocus
                  placeholder="8+ characters"
                  invalid={!!errors.password}
                  {...register("password")}
                />
              </Field>

              <Field label="Confirm password" error={errors.confirmPassword?.message}>
                <Input
                  type="password"
                  autoComplete="new-password"
                  placeholder="Re-enter your password"
                  invalid={!!errors.confirmPassword}
                  {...register("confirmPassword")}
                />
              </Field>

              <p className="mb-3 font-mono text-[11px] text-text3">
                Use at least 8 characters, with one number.
              </p>

              <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
                Set password and sign in
              </Button>
            </form>
          </>
        )}
      </Card>
    </div>
  );
}
