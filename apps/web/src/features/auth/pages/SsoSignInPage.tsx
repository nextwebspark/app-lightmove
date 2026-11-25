import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation } from "react-router-dom";
import { Button, Field, Input } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../api/authApi";
import { AUTH_LINK_CLASS, AuthShell, LegalFooter, SubmitError, UNREACHABLE_MESSAGE } from "../components/AuthShell";
import { signInWithLabel } from "../components/OAuthButtons";
import { loginEmailSchema, type LoginEmailValues } from "../schemas";
import { useAuthProviders } from "../useAuthProviders";

/** Sign in with single sign-on. A port of uncava-design/Login.dc.html (`sso`, `sso-unavailable`). */
export function SsoSignInPage() {
  const location = useLocation();
  const prefill = (location.state as { email?: string } | null)?.email ?? "";
  const [formError, setFormError] = useState<string | null>(null);
  const { data: providers } = useAuthProviders();

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
    } catch (error) {
      setFormError(error instanceof ApiRequestError ? messageFor(error) : UNREACHABLE_MESSAGE);
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
          <Link to="/signup" className={AUTH_LINK_CLASS}>
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
        <Link to="/login" state={{ email: watch("email") }} className={AUTH_LINK_CLASS}>
          {signInWithLabel(providers?.providers ?? [], "email", "Sign in with email")}
        </Link>
      </div>
    </AuthShell>
  );
}
