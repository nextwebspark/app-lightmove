import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { Button, Card, Field, FormError, Input, Logo } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import { SIGNUP_STEPS, Stepper } from "../components/Stepper";
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
  // The address that turned out to already have an account — the one state with a real way forward
  // (log in), so it gets a CTA rather than a dead-end field error.
  const [registeredEmail, setRegisteredEmail] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { fullName: "", email: "", password: "", confirmPassword: "" },
  });

  const onSubmit = async (values: SignupValues) => {
    setFormError(null);
    setRegisteredEmail(null);
    try {
      await signUp(values.fullName, values.email, values.password);
      navigate("/signup/workspace", { replace: true });
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
        // Already registered is the one email problem with a way forward: log in. The CTA carries the
        // typed address to prefill the login form — and deliberately nothing more. Which workspace the
        // account belongs to is never revealed pre-auth; that would be an enumeration oracle.
        if (error.code === "EMAIL_ALREADY_REGISTERED") {
          setRegisteredEmail(values.email);
        }
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
      <ThemeToggle className="fixed right-4 top-4 z-50" />
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
            hint="Your company domain — not a personal address."
          >
            <Input
              type="email"
              autoComplete="email"
              placeholder="you@firm.com"
              invalid={!!errors.email}
              {...register("email")}
            />
          </Field>

          {registeredEmail && (
            <p className="-mt-2 mb-4 text-[12.5px] text-text2">
              <Link
                to="/login"
                state={{ email: registeredEmail }}
                className="font-medium text-sky hover:underline"
              >
                Log in instead →
              </Link>
            </p>
          )}

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

          <Field label="Confirm password" error={errors.confirmPassword?.message}>
            <Input
              type="password"
              autoComplete="new-password"
              placeholder="Re-enter your password"
              invalid={!!errors.confirmPassword}
              {...register("confirmPassword")}
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
