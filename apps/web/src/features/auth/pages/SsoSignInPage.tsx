import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation } from "react-router-dom";
import { Button, Field, Input } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../api/authApi";
import { AuthShell, LegalFooter, SubmitError } from "../components/AuthShell";
import { loginEmailSchema, type LoginEmailValues } from "../schemas";

/**
 * Sign in with single sign-on. A port of uncava-design/Login.dc.html (`sso`, `sso-unavailable`). Until it ships
 * every address gets the same answer, so the screen never says whether an organisation or an account exists.
 */
const SSO_NOT_SET_UP =
  "We couldn't find an SSO account for that email. Sign in with your email and password, or sign up.";

export function SsoSignInPage() {
  const location = useLocation();
  const prefill = (location.state as { email?: string } | null)?.email ?? "";
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<LoginEmailValues>({
    resolver: zodResolver(loginEmailSchema),
    defaultValues: { email: prefill },
  });

  const onSubmit = async (values: LoginEmailValues) => {
    setFormError(null);
    try {
      await authApi.discoverSso(values.email);
      setFormError(SSO_NOT_SET_UP);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === "SSO_NOT_CONFIGURED") {
        setFormError(SSO_NOT_SET_UP);
        return;
      }
      setFormError(
        error instanceof ApiRequestError
          ? messageFor(error)
          : "Could not reach Uncava. Check your connection and try again.",
      );
    }
  };

  return (
    <AuthShell
      title="Sign in with SSO"
      subtitle="Enter your work email. We'll take you to your organisation's sign-in page."
      footer={<LegalFooter />}
      accountPrompt={
        <>
          New to Uncava?{" "}
          <Link to="/signup" className="font-medium text-u-accent hover:underline">
            Sign up
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate>
        <Field label="Work email" error={errors.email?.message}>
          <Input
            type="email"
            autoComplete="username"
            autoFocus
            placeholder="you@firm.com"
            invalid={!!errors.email}
            {...register("email")}
          />
        </Field>

        <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
          Continue
        </Button>
        <SubmitError message={formError} />
      </form>

      <div className="mt-4 text-center">
        <Link to="/login" state={{ email: watch("email") }} className="text-note font-medium text-u-accent hover:underline">
          Sign in with LinkedIn, Google or email
        </Link>
      </div>
    </AuthShell>
  );
}
