import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
import { pendingInvite } from "../pendingInvite";
import { signupSchema, type SignupValues } from "../schemas";

/**
 * Signup step 1 — "Create your account". A port of Signup.dc.html's first step.
 *
 * On success the user exists but has no workspace, so they go straight on to step 2. The wizard's
 * steps are separate routes rather than local state: step 1 creates a real account on the server, and
 * a user who closes the tab after it should be able to sign back in and land where they left off,
 * which local state cannot do.
 */
export function SignupPage() {
  const { signUp } = useAuth();
  const navigate = useNavigate();
  const [formError, setFormError] = useState<string | null>(null);

  // Someone who arrived from an invitation link. Their address is not a free choice: acceptance checks
  // the account's email against the one invited, so letting them type another here would create an
  // account the invitation then refuses — for a rule they were never shown.
  const invite = pendingInvite.peek();

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { fullName: "", email: invite?.email ?? "", password: "" },
  });

  const onSubmit = async (values: SignupValues) => {
    setFormError(null);
    try {
      await signUp(values.fullName, values.email, values.password);

      // An invitee has a workspace waiting for them, so they must not be sent through the join-or-create
      // fork. They still have to verify first — the accept page says so.
      navigate(invite ? `/auth/accept-invite?token=${encodeURIComponent(invite.token)}` : "/signup/workspace",
          { replace: true });
    } catch (error) {
      if (!(error instanceof ApiRequestError)) {
        setFormError("Could not reach LightMove. Check your connection and try again.");
        return;
      }

      // Everything the server can reject about an email belongs on the email field, not floating
      // above the form — a consumer address, a disposable one, one already registered.
      const emailProblems = [
        "EMAIL_NOT_WORK_ADDRESS",
        "EMAIL_DISPOSABLE",
        "EMAIL_UNDELIVERABLE",
        "EMAIL_ALREADY_REGISTERED",
      ];

      if (emailProblems.includes(error.code)) {
        setError("email", { message: error.problem.detail });
        return;
      }

      // Field-level messages the server produced (its rules are the ones that actually count).
      const fieldErrors = error.fieldErrors;
      const fields = Object.keys(fieldErrors) as (keyof SignupValues)[];
      if (fields.length > 0) {
        fields.forEach((field) => setError(field, { message: fieldErrors[field] }));
        return;
      }

      setFormError(error.problem.detail);
    }
  };

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-6 p-6">
      <Logo />
      <Stepper steps={SIGNUP_STEPS} current={1} />

      <Card className="w-[480px] max-w-[94vw] [animation-delay:80ms]">
        <h1 className="text-[19px] font-semibold leading-tight">Create your account</h1>
        <p className="mb-6 mt-1 font-mono text-xs text-text3">Step 1 of 3 · your details</p>

        <FormError message={formError} />

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          <Field label="Full name" error={errors.fullName?.message}>
            <Input
              autoComplete="name"
              autoFocus
              placeholder="Yara Haddad"
              invalid={!!errors.fullName}
              {...register("fullName")}
            />
          </Field>

          <Field
            label="Work email"
            error={errors.email?.message}
            hint={
              invite
                ? "The address your invitation was sent to."
                : "Your company domain — not a personal address."
            }
          >
            <Input
              type="email"
              autoComplete="email"
              placeholder="you@firm.com"
              invalid={!!errors.email}
              readOnly={!!invite}
              className={invite ? "cursor-not-allowed text-text3" : undefined}
              {...register("email")}
            />
          </Field>

          <Field
            label="Password"
            error={errors.password?.message}
            hint="Use at least 8 characters, with one number."
          >
            <Input
              type="password"
              autoComplete="new-password"
              placeholder="8+ characters"
              invalid={!!errors.password}
              {...register("password")}
            />
          </Field>

          <p className="mb-5 text-[11.5px] leading-relaxed text-text3">
            By continuing you agree to the{" "}
            <a href="/terms" className="text-sky hover:underline">
              Terms
            </a>{" "}
            and{" "}
            <a href="/privacy" className="text-sky hover:underline">
              Privacy Policy
            </a>
            .
          </p>

          <Button type="submit" loading={isSubmitting} className="w-full">
            Continue
          </Button>
        </form>
      </Card>

      <p className="animate-fade-up text-[12.5px] text-text2 [animation-delay:120ms]">
        Already have an account?{" "}
        <Link to="/login" className="text-sky hover:underline">
          Sign in
        </Link>
      </p>
    </div>
  );
}
