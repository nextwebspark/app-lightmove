import type { ReactNode } from "react";
import { AuthLogo, Card } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { PRIVACY_URL, TERMS_URL } from "../../../lib/links";
import { ThemeToggle } from "../../theme/ThemeToggle";

/** The signed-out screens' frame: the theme switch, the lockup, one card, and the line beneath it. */
export function AuthShell({
  title,
  subtitle,
  above,
  accountPrompt,
  footer,
  wide = false,
  children,
}: {
  title?: string;
  subtitle?: ReactNode;
  /** Between the lockup and the card — signup's stepper. */
  above?: ReactNode;
  /** The card's line to the other door — "New to Uncava? Sign up". */
  accountPrompt?: ReactNode;
  /** Inside the card, after its content — the legal line. */
  footer?: ReactNode;
  wide?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center gap-6 p-4 sm:p-6">
      <ThemeToggle className="fixed right-4 top-4 z-50" />
      <AuthLogo />
      {above}

      <Card className={cn("max-w-[94vw] [animation-delay:60ms]", wide ? "w-[480px]" : "w-[400px]")}>
        {title && <h1 className="text-title font-semibold">{title}</h1>}
        {subtitle && <p className="mb-6 mt-1 font-mono text-note text-u-text3">{subtitle}</p>}
        {children}
        {accountPrompt && <p className="mt-5 text-center text-body text-u-text2">{accountPrompt}</p>}
        {footer}
      </Card>
    </div>
  );
}

/** A secondary link on a signed-out card: "Forgot password?", "Continue with SSO", "Sign up". */
export const AUTH_LINK_CLASS = "text-body font-medium text-u-accent hover:underline";

export const UNREACHABLE_MESSAGE = "Could not reach Uncava. Check your connection and try again.";

/** A refused submit, said in one red line under its button. */
export function SubmitError({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="mt-3 text-center text-body text-u-offlimits">
      {message}
    </p>
  );
}

/** The sign-in card's last line: the terms a sign-in agrees to. */
export function LegalFooter() {
  const linkClass = "text-u-text2 underline underline-offset-2 hover:text-u-text";

  return (
    <p className="mt-6 border-t border-u-border pt-4 text-center text-meta text-u-text3">
      By continuing, you agree to the{" "}
      <a href={TERMS_URL} target="_blank" rel="noopener noreferrer" className={linkClass}>
        Terms of Service
      </a>{" "}
      and{" "}
      <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer" className={linkClass}>
        Privacy Policy
      </a>
      .
    </p>
  );
}
