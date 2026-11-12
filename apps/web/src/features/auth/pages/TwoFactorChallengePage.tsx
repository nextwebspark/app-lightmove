import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AuthLogo, Button, Card, Field, FormError, Input } from "../../../components/ui";
import { ApiRequestError } from "../../../lib/apiClient";
import { messageFor } from "../../../lib/errorCodes";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuth } from "../AuthProvider";
import { landingAfterSignIn, safeReturnTo } from "../returnTo";
import { authenticatorCodeSchema, recoveryCodeSchema, type SecondFactorCodeValues } from "../schemas";

export const TWO_FACTOR_SIGN_IN_PATH = "/login/two-factor";

/**
 * The second step of a sign-in, after a right password or reset link. The challenge it answers is an httpOnly cookie
 * the server set, so this page holds nothing but the code: opened cold, or after the challenge's few minutes, the
 * code is refused and the way back is to sign in again.
 */
export function TwoFactorChallengePage() {
  const { completeSecondFactor } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const returnTo = safeReturnTo((location.state as { from?: unknown } | null)?.from);

  const [usingRecoveryCode, setUsingRecoveryCode] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<SecondFactorCodeValues>({
    resolver: zodResolver(usingRecoveryCode ? recoveryCodeSchema : authenticatorCodeSchema),
    defaultValues: { code: "" },
  });

  const handleSwitchKind = () => {
    setUsingRecoveryCode((current) => !current);
    setFormError(null);
    reset({ code: "" });
  };

  const onSubmit = async (values: SecondFactorCodeValues) => {
    setFormError(null);
    try {
      const user = await completeSecondFactor(values.code.trim());
      navigate(landingAfterSignIn(user, returnTo), { replace: true });
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError
          ? messageFor(error)
          : "Could not reach Uncava. Check your connection and try again.",
      );
      reset({ code: "" }, { keepErrors: true });
    }
  };

  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <ThemeToggle className="fixed right-4 top-4 z-50" />
      <AuthLogo />

      <Card className="w-[400px] max-w-[94vw] [animation-delay:60ms]">
        <h1 className="type-title">Two-factor authentication</h1>
        <p className="mb-6 mt-1 font-mono text-note text-u-text3">
          {usingRecoveryCode
            ? "Enter one of the recovery codes you saved. Each works once."
            : "Enter the 6-digit code from your authenticator app."}
        </p>

        <FormError message={formError} />

        <form onSubmit={handleSubmit(onSubmit)} noValidate>
          {usingRecoveryCode ? (
            <Field key="recovery" label="Recovery code" error={errors.code?.message}>
              <Input
                autoComplete="off"
                autoCapitalize="characters"
                spellCheck={false}
                autoFocus
                placeholder="ABCD-EFGH-IJKL-MNOP"
                invalid={!!errors.code}
                {...register("code")}
              />
            </Field>
          ) : (
            <Field key="authenticator" label="Code" error={errors.code?.message}>
              <Input
                inputMode="numeric"
                autoComplete="one-time-code"
                autoFocus
                maxLength={7}
                placeholder="123456"
                invalid={!!errors.code}
                {...register("code")}
              />
            </Field>
          )}

          <Button type="submit" loading={isSubmitting} className="mt-1 w-full">
            Verify
          </Button>
        </form>

        <button
          type="button"
          onClick={handleSwitchKind}
          className="mt-4 w-full text-center text-note font-medium text-u-accent hover:underline"
        >
          {usingRecoveryCode ? "Use your authenticator app instead" : "Use a recovery code instead"}
        </button>
      </Card>

      <p className="animate-fade-up text-note text-u-text2 [animation-delay:120ms]">
        <Link to="/login" state={returnTo ? { from: returnTo } : undefined} className="text-u-accent hover:underline">
          Back to sign in
        </Link>
      </p>
    </div>
  );
}
