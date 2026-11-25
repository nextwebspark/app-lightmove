import type { ReactNode } from "react";
import { AuthLogo, Card } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { PRIVACY_URL, TERMS_URL } from "../../../lib/links";
import { ThemeToggle } from "../../theme/ThemeToggle";
import { useAuthProviders } from "../useAuthProviders";

/** The signed-out screens' frame: the theme switch, the lockup, one card, and the line beneath it. */
export function AuthShell({
  title,
  subtitle,
  above,
  below,
  footer,
  wide = false,
  children,
}: {
  title?: string;
  subtitle?: ReactNode;
  /** Between the lockup and the card — signup's stepper. */
  above?: ReactNode;
  /** Under the card — "New to Uncava? Create an account". */
  below?: ReactNode;
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
        {footer}
      </Card>

      {below && <p className="animate-fade-up text-note text-u-text2 [animation-delay:120ms]">{below}</p>}
    </div>
  );
}

/** The sign-in card's last line: the terms a sign-in agrees to, and the bot check when it is on. */
export function LegalFooter() {
  const humanCheckOn = Boolean(useAuthProviders().data?.humanCheckSiteKey);
  const linkClass = "text-u-text2 underline underline-offset-2 hover:text-u-text";

  return (
    <p className="mt-6 border-t border-u-border pt-4 text-center text-meta text-u-text3">
      By continuing, you agree to the{" "}
      <a href={TERMS_URL} target="_blank" rel="noopener" className={linkClass}>
        Terms of Service
      </a>{" "}
      and{" "}
      <a href={PRIVACY_URL} target="_blank" rel="noopener" className={linkClass}>
        Privacy Policy
      </a>
      .
      {humanCheckOn && (
        <>
          <br />
          This site is protected by Cloudflare Turnstile.
        </>
      )}
    </p>
  );
}
